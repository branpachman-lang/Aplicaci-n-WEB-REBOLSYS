package pe.com.rebolsysBackend.service.impl;

import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.exigir;
import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.limpiar;
import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.patronBusqueda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.ReportesDTO.*;
import pe.com.rebolsysBackend.enums.EstadoInforme;
import pe.com.rebolsysBackend.repository.ReporteRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportesService {
  private final ReporteRepository reportes;

  public Pagina<Incidencia> incidencias(LocalDate desde, LocalDate hasta, String habitacion, EstadoInforme estado,
      int pagina, int tamanio) {
    var periodo = periodo(desde, hasta);
    return reportes.incidencias(periodo.desde(), periodo.hasta(), patronBusqueda(habitacion),
        estado == null ? null : estado.name(), pagina, tamanio);
  }

  public Financiero financiero(LocalDate desde, LocalDate hasta, String generadoPor) {
    var periodo = periodo(desde, hasta);
    var filas = reportes.movimientosFinancieros(periodo.desde(), periodo.hasta());
    var reservas = BigDecimal.ZERO;
    var ventas = BigDecimal.ZERO;
    var manuales = BigDecimal.ZERO;
    var egresos = BigDecimal.ZERO;
    for (var fila : filas) {
      switch (fila.tipo()) {
        case "RESERVAS" -> reservas = reservas.add(fila.ingreso());
        case "VENTAS" -> ventas = ventas.add(fila.ingreso());
        case "INGRESOS_MANUALES" -> manuales = manuales.add(fila.ingreso());
        case "EGRESOS" -> egresos = egresos.add(fila.egreso());
        default -> throw new IllegalStateException("Tipo de movimiento financiero no reconocido");
      }
    }
    return new Financiero(filas, generadoPor, reservas, ventas, manuales, egresos,
        reservas.add(ventas).add(manuales).subtract(egresos));
  }

  public Pagina<Cierre> cierres(LocalDate desde, LocalDate hasta, String usuario, int pagina, int tamanio) {
    var periodo = periodo(desde, hasta);
    return reportes.cierres(periodo.desde(), periodo.hasta(), patronBusqueda(usuario), pagina, tamanio);
  }

  public Pagina<Existencia> productos(String buscar, boolean bajo, int pagina, int tamanio) {
    return reportes.productos(patronBusqueda(buscar), bajo, pagina, tamanio);
  }

  public Pagina<Mueble> mobiliario(String buscar, String habitacion, int pagina, int tamanio) {
    return reportes.mobiliario(patronBusqueda(buscar),
        limpiar(habitacion) == null ? null : patronBusqueda(habitacion), pagina, tamanio);
  }

  private static Periodo periodo(LocalDate desde, LocalDate hasta) {
    exigir(desde == null || hasta == null || !desde.isAfter(hasta), "La fecha inicial debe ser anterior o igual a la final");
    exigir(hasta == null || hasta.getYear() < 9999, "La fecha final está fuera del rango permitido");
    return new Periodo(desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay());
  }

  private record Periodo(LocalDateTime desde, LocalDateTime hasta) {}
}
