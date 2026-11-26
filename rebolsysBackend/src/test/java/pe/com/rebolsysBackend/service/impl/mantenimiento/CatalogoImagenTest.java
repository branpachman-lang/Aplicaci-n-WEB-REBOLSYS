package pe.com.rebolsysBackend.service.impl.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import pe.com.rebolsysBackend.entity.Categoria;
import pe.com.rebolsysBackend.entity.Empleado;
import pe.com.rebolsysBackend.entity.Habitacion;
import pe.com.rebolsysBackend.entity.Item;
import pe.com.rebolsysBackend.entity.Perfil;
import pe.com.rebolsysBackend.entity.PersonaNatural;
import pe.com.rebolsysBackend.entity.Producto;
import pe.com.rebolsysBackend.entity.Rol;
import pe.com.rebolsysBackend.entity.TipoDocumento;
import pe.com.rebolsysBackend.entity.TipoHabitacion;
import pe.com.rebolsysBackend.entity.TipoItem;
import pe.com.rebolsysBackend.entity.Usuario;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.enums.EstadoItem;
import pe.com.rebolsysBackend.enums.EstadoProducto;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ItemRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ProductoRepository;
import pe.com.rebolsysBackend.security.AccesosService;
import pe.com.rebolsysBackend.security.CredencialesService;
import pe.com.rebolsysBackend.security.JwtService;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.ProveedorFacturacionClient;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalogoimg;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "springdoc.api-docs.version=OPENAPI_3_0"
})
@ActiveProfiles("catalogo-img-test")
@AutoConfigureMockMvc
class CatalogoImagenTest {
  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void registrarDir(DynamicPropertyRegistry registry) {
    registry.add("app.catalogo.directorio-imagenes", () -> tempDir.toString());
  }

  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired AccesosService accesos;
  @Autowired CredencialesService credenciales;
  @Autowired JwtService jwt;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient facturacion;

  @Test
  void resuelvePorIdCuartoTipoYCategoriaYRechazaPathTraversal(@TempDir Path dirUnitario) throws Exception {
    var habRepo = mock(HabitacionRepository.class);
    var itemRepo = mock(ItemRepository.class);
    var prodRepo = mock(ProductoRepository.class);
    var servicio = new CatalogoImagenService(dirUnitario.toString(), habRepo, itemRepo, prodRepo);

    Files.createDirectories(dirUnitario.resolve("habitaciones"));
    Files.createDirectories(dirUnitario.resolve("mobiliario"));
    Files.createDirectories(dirUnitario.resolve("productos"));

    byte[] png = pngBytes();
    Files.write(dirUnitario.resolve("habitaciones/tipo-matrimonial-con-bano.png"), png);
    Files.write(dirUnitario.resolve("habitaciones/cuarto-201.jpg"), png);
    Files.write(dirUnitario.resolve("habitaciones/id-5.webp"), png);

    assertThat(servicio.resolverHabitacion(5L, "201", "Matrimonial con baño"))
        .isEqualTo("/api/v1/catalogo/imagenes/habitaciones/id-5.webp");
    assertThat(servicio.resolverHabitacion(6L, "201", "Matrimonial con baño"))
        .isEqualTo("/api/v1/catalogo/imagenes/habitaciones/cuarto-201.jpg");
    assertThat(servicio.resolverHabitacion(7L, "202", "Matrimonial con ba?o"))
        .isEqualTo("/api/v1/catalogo/imagenes/habitaciones/tipo-matrimonial-con-bano.png");

    Files.write(dirUnitario.resolve("mobiliario/tipo-televisores.png"), png);
    assertThat(servicio.resolverMobiliario(10L, "Televisores"))
        .isEqualTo("/api/v1/catalogo/imagenes/mobiliario/tipo-televisores.png");

    Files.write(dirUnitario.resolve("productos/cat-bebidas.png"), png);
    assertThat(servicio.resolverProducto(99L, null, "Agua mineral", "Bebidas"))
        .isEqualTo("/api/v1/catalogo/imagenes/productos/cat-bebidas.png");

    assertThatThrownBy(() -> servicio.obtener("habitaciones", "../secret.png"))
        .isInstanceOf(RecursoNoEncontradoException.class);
    assertThatThrownBy(() -> servicio.obtener("otros", "id-1.png"))
        .isInstanceOf(RecursoNoEncontradoException.class);
    when(habRepo.existsById(999L)).thenReturn(false);
    var archivo = new MockMultipartFile("archivo", "foto.png", "image/png", png);
    assertThatThrownBy(() -> servicio.guardar("habitaciones", 999L, archivo))
        .isInstanceOf(RecursoNoEncontradoException.class);
  }

  @Test
  void subeDescargaYEliminaImagenesYExponeAliasMobiliarioEItems() throws Exception {
    var datos = tx.execute(s -> {
      var tipoDoc = new TipoDocumento(null, "DNI", "1");
      em.persist(tipoDoc);
      var perfil = new Perfil(null, tipoDoc, "77778888", "gerente.img@example.test", null);
      em.persist(perfil);
      var persona = new PersonaNatural(null, perfil, "Gerente", "Catalogo", null);
      em.persist(persona);
      var empleado = new Empleado(null, persona, LocalDate.of(2025, 1, 1), null);
      em.persist(empleado);
      var usuario = new Usuario(null, empleado, "gerente_catalogo", EstadoUsuario.ACTIVO);
      em.persist(usuario);
      var rol = new Rol(null, AccesosService.GERENTE);
      em.persist(rol);
      em.flush();
      accesos.permisos(rol.getIdRol(), Set.of("HABITACIONES:VER", "MOBILIARIO:VER"));
      accesos.vincular(usuario.getIdUsuario(), rol.getIdRol());
      String tempPass = credenciales.emitir(usuario).contrasenaTemporal();

      var th = new TipoHabitacion(null, "Suite Test", 2, new BigDecimal("100.00"));
      em.persist(th);
      var hab = new Habitacion();
      hab.setNumeroCuarto("901");
      hab.setTipoHabitacion(th);
      hab.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE);
      hab.setCapacidadPersonas(2);
      hab.setPlazasCama(1);
      em.persist(hab);

      var tm = new TipoItem(null, "Sillones");
      em.persist(tm);
      var mob = new Item();
      mob.setNombreItem("Sillón individual");
      mob.setTipoItem(tm);
      mob.setEstadoItem(EstadoItem.ACTIVO);
      em.persist(mob);

      var cat = new Categoria();
      cat.setNombreCategoria("Bebidas");
      em.persist(cat);
      var prod = new Producto();
      prod.setNombreProducto("Agua Test");
      prod.setPrecioActual(new BigDecimal("3.00"));
      prod.setStock(10);
      prod.setStockMinimo(2);
      prod.setCategoria(cat);
      prod.setEstadoProducto(EstadoProducto.ACTIVO);
      em.persist(prod);

      return new Object[] { hab.getIdHabitacion(), mob.getIdItem(), prod.getIdProducto(), tempPass };
    });

    Long idHab = (Long) datos[0];
    Long idMob = (Long) datos[1];
    Long idProd = (Long) datos[2];
    String tempPass = (String) datos[3];
    var temporal = credenciales.iniciar("gerente_catalogo", tempPass);
    var principal = credenciales.cambiar(temporal, tempPass, "Clave definitiva catalogo test");
    String bearer = "Bearer " + jwt.emitir(principal, 30).token();

    byte[] png = pngBytes();
    var foto = new MockMultipartFile("archivo", "foto.png", "image/png", png);

    mvc.perform(multipart("/api/v1/catalogo/imagenes/habitaciones/" + idHab).file(foto)
            .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenRuta").value("/api/v1/catalogo/imagenes/habitaciones/id-" + idHab + ".png"));

    mvc.perform(multipart("/api/v1/catalogo/imagenes/mobiliario/" + idMob).file(foto)
            .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenRuta").value("/api/v1/catalogo/imagenes/mobiliario/id-" + idMob + ".png"));

    mvc.perform(multipart("/api/v1/catalogo/imagenes/productos/" + idProd).file(foto)
            .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenRuta").value("/api/v1/catalogo/imagenes/productos/id-" + idProd + ".png"));

    mvc.perform(get("/api/v1/catalogo/imagenes/habitaciones/id-" + idHab + ".png")
            .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(content().bytes(png));

    mvc.perform(get("/api/v1/items/" + idMob).header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenRuta").value("/api/v1/catalogo/imagenes/mobiliario/id-" + idMob + ".png"));
    mvc.perform(get("/api/v1/tipos-item").header("Authorization", bearer)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/habitaciones/" + idHab + "/items").header("Authorization", bearer))
        .andExpect(status().isOk());

    var invalido = new MockMultipartFile("archivo", "malo.txt", "text/plain", "no-es-imagen".getBytes());
    mvc.perform(multipart("/api/v1/catalogo/imagenes/productos/" + idProd).file(invalido)
            .header("Authorization", bearer))
        .andExpect(status().isConflict());

    mvc.perform(delete("/api/v1/catalogo/imagenes/productos/" + idProd).header("Authorization", bearer))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/catalogo/imagenes/productos/id-" + idProd + ".png").header("Authorization", bearer))
        .andExpect(status().isNotFound());
  }

  private static byte[] pngBytes() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}
