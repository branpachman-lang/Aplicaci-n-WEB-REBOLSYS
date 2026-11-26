package pe.com.rebolsysBackend.service.impl.reservas.yape;

import static pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeDatos.*;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;

/** Persistencia de intentos y conciliaciones de pago con Yape en la tabla PAGO_YAPE. */
@Repository
@RequiredArgsConstructor
class PagoYapeRegistro {
  private final JdbcTemplate jdbc;

  private static final RowMapper<Intento> INTENTO =
      (rs, i) ->
          new Intento(
              UUID.fromString(rs.getString("claveOperacion")),
              rs.getLong("idReserva"),
              rs.getLong("idUsuario"),
              rs.getLong("idTurno"),
              rs.getBigDecimal("importe"),
              rs.getString("telefonoEnmascarado"),
              Modo.valueOf(rs.getString("modo")),
              Ambiente.valueOf(rs.getString("ambiente")),
              Estado.valueOf(rs.getString("estado")),
              rs.getString("ordenProveedor"),
              rs.getObject("idPago") == null ? null : rs.getLong("idPago"),
              rs.getString("mensajeProveedor"),
              rs.getTimestamp("creado").toInstant());

  Optional<Intento> buscar(UUID clave, long idReserva) {
    return ejecutar(
        () ->
            jdbc.query(
                    "select * from pago_yape where claveOperacion = ? and idReserva = ?",
                    INTENTO,
                    clave.toString(),
                    idReserva)
                .stream()
                .findFirst());
  }

  Optional<Intento> buscar(UUID clave) {
    return ejecutar(
        () ->
            jdbc.query(
                    "select * from pago_yape where claveOperacion = ?",
                    INTENTO,
                    clave.toString())
                .stream()
                .findFirst());
  }

  Optional<Intento> buscarPorPago(long idPago) {
    return ejecutar(
        () ->
            jdbc.query("select * from pago_yape where idPago = ?", INTENTO, idPago)
                .stream()
                .findFirst());
  }

  Optional<Intento> ultimo(long idReserva) {
    return ejecutar(
        () ->
            jdbc.query(
                    """
                    select * from pago_yape where idReserva = ?
                    order by creado desc, claveOperacion fetch first 1 rows only
                    """,
                    INTENTO,
                    idReserva)
                .stream()
                .findFirst());
  }

  boolean tienePendientes(long idReserva) {
    Integer pendientes =
        ejecutar(
            () ->
                jdbc.queryForObject(
                    """
                    select case when count(*) > 0 then 1 else 0 end from pago_yape
                    where idReserva = ? and estado in ('ENVIANDO', 'PENDIENTE', 'REVISION')
                    """,
                    Integer.class,
                    idReserva));
    return Integer.valueOf(1).equals(pendientes);
  }

  void crear(Intento i) {
    ejecutar(
        () ->
            jdbc.update(
                """
                insert into pago_yape (claveOperacion, idReserva, idUsuario, idTurno, importe,
                  telefonoEnmascarado, modo, ambiente, estado, mensajeProveedor, creado)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                i.claveOperacion().toString(),
                i.idReserva(),
                i.idUsuario(),
                i.idTurno(),
                i.importe(),
                i.telefonoEnmascarado(),
                i.modo().name(),
                i.ambiente().name(),
                i.estado().name(),
                recortar(i.mensajeProveedor()),
                Timestamp.from(i.creado())));
  }

  void actualizar(Intento i, Estado estado, String orden, Long idPago, String mensaje) {
    ejecutar(
        () ->
            jdbc.update(
                """
                update pago_yape set estado = ?, ordenProveedor = ?, idPago = ?, mensajeProveedor = ?
                where claveOperacion = ?
                """,
                estado.name(),
                orden,
                idPago,
                recortar(mensaje),
                i.claveOperacion().toString()));
  }

  private static String recortar(String texto) {
    if (texto == null) return null;
    String limpio = texto.trim();
    return limpio.length() > 250 ? limpio.substring(0, 250) : limpio;
  }

  private <T> T ejecutar(Supplier<T> consulta) {
    try {
      return consulta.get();
    } catch (InvalidDataAccessResourceUsageException ex) {
      throw new ReglaNegocioException(
          "Aplica V23__pago_yape.sql en la base de datos para habilitar pagos con Yape");
    }
  }
}
