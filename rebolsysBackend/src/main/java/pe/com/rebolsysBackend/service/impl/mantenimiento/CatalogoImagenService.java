package pe.com.rebolsysBackend.service.impl.mantenimiento;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.exigir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.com.rebolsysBackend.dto.mantenimiento.ImagenCatalogoResponseDTO;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ItemRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ProductoRepository;
import pe.com.rebolsysBackend.service.impl.comun.ImagenEvidencia;

@Service
public class CatalogoImagenService {
  public static final String RUTA = "/api/v1/catalogo/imagenes";
  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final Set<String> CATEGORIAS = Set.of("habitaciones", "mobiliario", "productos");
  private static final List<String> EXTENSIONES_LECTURA = List.of("png", "jpg", "jpeg", "webp", "gif");
  private static final String NOMBRE_VALIDO = "^[a-z0-9._-]{1,120}\\.(png|jpe?g|webp|gif)$";

  private final Path directorio;
  private final HabitacionRepository habitaciones;
  private final ItemRepository items;
  private final ProductoRepository productos;

  public CatalogoImagenService(
      @Value("${app.catalogo.directorio-imagenes:.local/imagenes-catalogo}") String directorio,
      HabitacionRepository habitaciones,
      ItemRepository items,
      ProductoRepository productos) {
    this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    this.habitaciones = habitaciones;
    this.items = items;
    this.productos = productos;
  }

  public String resolverHabitacion(Long id, String numeroCuarto, String nombreTipo) {
    List<String> bases = new ArrayList<>();
    if (id != null) bases.add("id-" + id);
    for (String c : candidatosSlug(numeroCuarto)) bases.add("cuarto-" + c);
    for (String t : candidatosSlug(nombreTipo)) bases.add("tipo-" + t);
    return buscarPrimera("habitaciones", bases);
  }

  public String resolverMobiliario(Long id, String nombreTipo) {
    List<String> bases = new ArrayList<>();
    if (id != null) bases.add("id-" + id);
    for (String t : candidatosSlug(nombreTipo)) bases.add("tipo-" + t);
    return buscarPrimera("mobiliario", bases);
  }

  public String resolverProducto(Long id, String imagenRutaDb, String nombreProducto, String nombreCategoria) {
    if (imagenRutaDb != null && !imagenRutaDb.isBlank()) {
      String limpia = imagenRutaDb.trim();
      String prefijoLocal = RUTA + "/productos/";
      if (limpia.startsWith(prefijoLocal)) {
        String archivo = limpia.substring(prefijoLocal.length());
        if (archivo.matches(NOMBRE_VALIDO) && Files.isRegularFile(directorio.resolve("productos").resolve(archivo))) {
          return limpia;
        }
      } else {
        return limpia;
      }
    }
    List<String> bases = new ArrayList<>();
    if (id != null) bases.add("id-" + id);
    for (String p : candidatosSlug(nombreProducto)) bases.add(p);
    for (String c : candidatosSlug(nombreCategoria)) bases.add("cat-" + c);
    return buscarPrimera("productos", bases);
  }

  @Transactional
  public ImagenCatalogoResponseDTO guardar(String categoria, Long id, MultipartFile archivo) {
    String cat = validarCategoria(categoria);
    exigir(archivo != null && !archivo.isEmpty() && archivo.getSize() <= MAX_BYTES,
        "Selecciona una imagen JPEG o PNG de hasta 5 MB");
    verificarEntidadExiste(cat, id);
    try {
      byte[] contenido = archivo.getBytes();
      String extension = ImagenEvidencia.extensionImagen(contenido);
      Path dirCategoria = directorio.resolve(cat).normalize();
      Files.createDirectories(dirCategoria);
      eliminarPreviosPorId(dirCategoria, id);
      String nombre = "id-" + id + "." + extension;
      Path destino = dirCategoria.resolve(nombre).normalize();
      Path temporal = Files.createTempFile(dirCategoria, "catalogo-", ".tmp");
      try {
        Files.write(temporal, contenido);
        Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporal);
      }
      String ruta = RUTA + "/" + cat + "/" + nombre;
      if ("productos".equals(cat)) {
        var prod = productos.bloquear(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("El producto no existe"));
        prod.setImagenRuta(ruta);
        productos.saveAndFlush(prod);
      }
      return new ImagenCatalogoResponseDTO(ruta);
    } catch (IOException ex) {
      throw new UncheckedIOException("No se pudo guardar la imagen de catálogo", ex);
    }
  }

  @Transactional
  public void eliminar(String categoria, Long id) {
    String cat = validarCategoria(categoria);
    verificarEntidadExiste(cat, id);
    Path dirCategoria = directorio.resolve(cat).normalize();
    try {
      eliminarPreviosPorId(dirCategoria, id);
    } catch (IOException ex) {
      throw new UncheckedIOException("No se pudo eliminar la imagen de catálogo", ex);
    }
    if ("productos".equals(cat)) {
      var prod = productos.bloquear(id)
          .orElseThrow(() -> new RecursoNoEncontradoException("El producto no existe"));
      if (prod.getImagenRuta() != null && prod.getImagenRuta().startsWith(RUTA + "/productos/id-" + id + ".")) {
        prod.setImagenRuta(null);
        productos.saveAndFlush(prod);
      }
    }
  }

  public FileSystemResource obtener(String categoria, String nombre) {
    if (categoria == null || !CATEGORIAS.contains(categoria)
        || nombre == null || !nombre.matches(NOMBRE_VALIDO)) {
      throw new RecursoNoEncontradoException("La imagen no existe");
    }
    Path dirCategoria = directorio.resolve(categoria).normalize();
    Path destino = dirCategoria.resolve(nombre).normalize();
    if (!destino.startsWith(dirCategoria) || !Files.isRegularFile(destino)) {
      throw new RecursoNoEncontradoException("La imagen no existe");
    }
    return new FileSystemResource(destino);
  }

  private void verificarEntidadExiste(String categoria, Long id) {
    exigir(id != null && id > 0, "El identificador del recurso es obligatorio");
    boolean existe = switch (categoria) {
      case "habitaciones" -> habitaciones.existsById(id);
      case "mobiliario" -> items.existsById(id);
      case "productos" -> productos.existsById(id);
      default -> false;
    };
    if (!existe) {
      throw new RecursoNoEncontradoException("El registro solicitado no existe");
    }
  }

  private String validarCategoria(String categoria) {
    exigir(categoria != null && CATEGORIAS.contains(categoria), "Categoría de imagen no admitida");
    return categoria;
  }

  private void eliminarPreviosPorId(Path dirCategoria, Long id) throws IOException {
    if (!Files.isDirectory(dirCategoria)) return;
    for (String ext : EXTENSIONES_LECTURA) {
      Files.deleteIfExists(dirCategoria.resolve("id-" + id + "." + ext));
    }
  }

  private String buscarPrimera(String categoria, List<String> bases) {
    Path dirCategoria = directorio.resolve(categoria).normalize();
    if (!Files.isDirectory(dirCategoria)) return null;
    for (String base : bases) {
      if (base.isEmpty()) continue;
      for (String ext : EXTENSIONES_LECTURA) {
        String nombre = base + "." + ext;
        if (Files.isRegularFile(dirCategoria.resolve(nombre))) {
          return RUTA + "/" + categoria + "/" + nombre;
        }
      }
    }
    return null;
  }

  public static String slug(String texto) {
    if (texto == null) return "";
    String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-");
    int inicio = 0;
    int fin = normalizado.length();
    while (inicio < fin && normalizado.charAt(inicio) == '-') {
      inicio++;
    }
    while (fin > inicio && normalizado.charAt(fin - 1) == '-') {
      fin--;
    }
    return inicio >= fin ? "" : normalizado.substring(inicio, fin);
  }

  private static List<String> candidatosSlug(String texto) {
    String base = slug(texto);
    if (base.isEmpty()) return List.of();
    if (texto != null && texto.contains("?")) {
      String conEnie = slug(texto.replace('?', 'n'));
      if (!conEnie.equals(base)) return List.of(base, conEnie);
    }
    return List.of(base);
  }
}
