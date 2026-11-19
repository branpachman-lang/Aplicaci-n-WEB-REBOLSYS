package pe.com.rebolsysBackend.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.com.rebolsysBackend.entity.TipoDocumento;
import pe.com.rebolsysBackend.exception.GlobalExceptionHandler;
import pe.com.rebolsysBackend.repository.TipoDocumentoRepository;
import pe.com.rebolsysBackend.validation.DocumentoIdentidadValidator;
import tools.jackson.databind.json.JsonMapper;
import pe.com.rebolsysBackend.dto.auth.LoginRequestDTO;
import pe.com.rebolsysBackend.dto.auth.CambioPasswordRequestDTO;

class DtoValidacionTest {
  private static ValidatorFactory factory;
  private static Validator validator;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private static final String PRODUCTO = """
      {"nombreProducto":"Agua sin gas","precioActual":2.50,
       "idCategoria":2,"estadoProducto":"ACTIVO"}
      """;
  private static final String PERSONA = """
      {"idTipoDocumento":1,"numeroDocumento":"48273619","empresa":false,
       "nombres":"Lucía","apellidos":"Rojas Medina","fechaNacimiento":"1992-03-14"}
      """;

  @BeforeAll
  static void prepararValidador() {
    var tipos = mock(TipoDocumentoRepository.class);
    var dni = new TipoDocumento();
    dni.setNombreDocumento("DNI");
    dni.setCodigoSunat("1");
    var ruc = new TipoDocumento();
    ruc.setNombreDocumento("RUC");
    ruc.setCodigoSunat("6");
    when(tipos.findById(1L)).thenReturn(Optional.of(dni));
    when(tipos.findById(6L)).thenReturn(Optional.of(ruc));
    var configuracion = Validation.byDefaultProvider().configure();
    var delegado = configuracion.getDefaultConstraintValidatorFactory();
    factory = configuracion.constraintValidatorFactory(new ConstraintValidatorFactory() {
      @Override
      public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> tipo) {
        return tipo == DocumentoIdentidadValidator.class
            ? tipo.cast(new DocumentoIdentidadValidator(tipos)) : delegado.getInstance(tipo);
      }

      @Override
      public void releaseInstance(ConstraintValidator<?, ?> instancia) {
        delegado.releaseInstance(instancia);
      }
    }).buildValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void cerrarValidador() {
    factory.close();
  }

  @Test
  void solicitudesDeAutenticacionConservanValidacionYNoRevelanClavesEnToString() {
    var login = mapper.readValue("{\"usuario\":\"\",\"contrasena\":\"\"}", LoginRequestDTO.class);
    assertThat(validator.validate(login)).extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("usuario", "contrasena");
    var cambio = mapper.readValue("{\"actual\":\"\",\"nueva\":\"" + "x".repeat(129) + "\"}", CambioPasswordRequestDTO.class);
    assertThat(validator.validate(cambio)).extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("actual", "nueva");
    assertThat(new LoginRequestDTO("usuario@example.test", "Clave confidencial").toString())
        .doesNotContain("Clave confidencial", "usuario@example.test");
    assertThat(new CambioPasswordRequestDTO("Anterior confidencial", "Nueva confidencial").toString())
        .doesNotContain("Anterior confidencial", "Nueva confidencial");
  }

  @Test
  void omitirStockConservaLosCerosPredeterminados() {
    var dto = mapper.readValue(PRODUCTO, ProductoRequestDTO.class);
    assertThat(dto.stock()).isZero();
    assertThat(dto.stockMinimo()).isZero();
    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void stockExplicitoNuloSigueSiendoInvalido() {
    var json = mapper.readTree(PRODUCTO).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) json).putNull("stock").putNull("stockMinimo");
    var dto = mapper.treeToValue(json, ProductoRequestDTO.class);
    assertThat(validator.validate(dto)).extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("stock", "stockMinimo");
  }

  @Test
  void productoRechazaImportesNegativosYUnaImagenInvalida() {
    var dto = mapper.readValue("""
        {"nombreProducto":"Agua","precioActual":-1,"stock":-1,"stockMinimo":-1,
         "idCategoria":2,"estadoProducto":"ACTIVO","imagenRuta":"https://externo.test/imagen.png"}
        """, ProductoRequestDTO.class);
    assertThat(validator.validate(dto)).extracting(v -> v.getPropertyPath().toString())
        .contains("precioActual", "stock", "stockMinimo", "imagenRuta");
  }

  @Test
  void validaDocumentoYDatosDePersonaNatural() {
    assertThat(validator.validate(mapper.readValue(PERSONA, ClienteRegistroRequestDTO.class))).isEmpty();
    var invalido = mapper.readValue(PERSONA.replace("48273619", "123").replace("Lucía", ""),
        ClienteRegistroRequestDTO.class);
    assertThat(validator.validate(invalido)).extracting(v -> v.getPropertyPath().toString())
        .contains("numeroDocumento", "identidadValida");
  }

  @Test
  void empresaExigeRazonSocial() {
    var dto = mapper.readValue("""
        {"idTipoDocumento":6,"numeroDocumento":"20601234567","empresa":true}
        """, ClienteRegistroRequestDTO.class);
    assertThat(validator.validate(dto)).extracting(v -> v.getPropertyPath().toString())
        .containsExactly("identidadValida");
  }

  @Test
  void conservaValidacionAnidadaAlGuardarCliente() {
    var dto = mapper.readValue("{\"persona\":" + PERSONA.replace("48273619", "123") + "}",
        ClienteGuardarDTO.class);
    assertThat(validator.validate(dto)).extracting(v -> v.getPropertyPath().toString())
        .contains("persona.numeroDocumento");
  }

  @Test
  void campoCalculadoStockBajoMantieneElLimiteInclusivo() {
    var json = """
        {"idProducto":1,"stock":5,"stockMinimo":5}
        """;
    var dto = mapper.readValue(json, ProductoResponseDTO.class);
    assertThat(mapper.valueToTree(dto).get("stockBajo").asBoolean()).isTrue();
    var sinStock = mapper.readValue(json.replace("\"stock\":5", "\"stock\":null"), ProductoResponseDTO.class);
    assertThat(mapper.valueToTree(sinStock).get("stockBajo").asBoolean()).isFalse();
  }

  @Test
  void solicitudHttpInvalidaConservaLosErroresPorCampo() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new Productos())
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    mvc.perform(post("/dto/productos").contentType(MediaType.APPLICATION_JSON)
            .content(PRODUCTO.replace("\"precioActual\":2.50", "\"precioActual\":-1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.precioActual").value("precioActual debe ser mayor o igual a 0"));
    mvc.perform(post("/dto/productos").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO))
        .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(0));
  }

  @RestController
  static class Productos {
    @PostMapping("/dto/productos")
    public ProductoRequestDTO producto(@Valid @RequestBody ProductoRequestDTO dto) {
      return dto;
    }
  }
}
