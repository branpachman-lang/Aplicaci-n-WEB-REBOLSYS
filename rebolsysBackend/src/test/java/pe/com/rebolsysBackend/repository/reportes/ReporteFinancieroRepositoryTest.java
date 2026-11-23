package pe.com.rebolsysBackend.repository.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.com.rebolsysBackend.service.impl.reportes.ReportesService;

class ReporteFinancieroRepositoryTest {
  @Test void sumaMovimientosVerificadosEIngresosManualesDentroDeUnRangoInclusivo() {
    var fuente = new JdbcDataSource();
    fuente.setURL("jdbc:h2:mem:reporte-financiero;MODE=Oracle;DB_CLOSE_DELAY=-1");
    var jdbc = new JdbcTemplate(fuente);
    jdbc.execute("create table forma_pago (idFormaPago number primary key, nombrePago varchar(30))");
    jdbc.execute("create table comprobante (idComprobante number primary key, idReserva number, idVenta number, numeroComprobante varchar(30))");
    jdbc.execute("create table pago (idPago number primary key, fechaPago timestamp, idComprobante number, idFormaPago number, monto decimal(12,2), verificado number, numeroOperacion varchar(50))");
    jdbc.execute("create table tipo_movimiento_caja (idTipoMovimiento number primary key, signo number, nombreMovimiento varchar(30))");
    jdbc.execute("create table flujo_caja (idFlujo number primary key, fechaHora timestamp, concepto varchar(100), idTipoMovimiento number, idPago number, monto decimal(12,2))");

    jdbc.update("insert into forma_pago values (1, 'EFECTIVO')");
    jdbc.update("insert into comprobante values (1, 7, null, 'B001')");
    jdbc.update("insert into comprobante values (2, null, 9, 'V001')");
    jdbc.update("insert into comprobante values (3, null, 10, 'V002')");
    jdbc.update("insert into pago values (1, ?, 1, 1, 25, 1, null)", LocalDateTime.of(2026, 11, 1, 9, 0));
    jdbc.update("insert into pago values (2, ?, 2, 1, 20, 1, null)", LocalDateTime.of(2026, 11, 30, 23, 59));
    jdbc.update("insert into pago values (3, ?, 3, 1, 90, 0, null)", LocalDateTime.of(2026, 11, 15, 12, 0));
    jdbc.update("insert into tipo_movimiento_caja values (1, 1, 'INGRESO_MANUAL')");
    jdbc.update("insert into tipo_movimiento_caja values (2, -1, 'GASTO')");
    jdbc.update("insert into flujo_caja values (1, ?, 'Pago asociado', 1, 1, 25)", LocalDateTime.of(2026, 11, 1, 9, 0));
    jdbc.update("insert into flujo_caja values (2, ?, 'Ingreso manual', 1, null, 10)", LocalDateTime.of(2026, 11, 20, 10, 0));
    jdbc.update("insert into flujo_caja values (3, ?, 'Compra', 2, null, 4)", LocalDateTime.of(2026, 11, 22, 14, 0));
    jdbc.update("insert into flujo_caja values (4, ?, 'Fuera del rango', 1, null, 100)", LocalDateTime.of(2026, 12, 1, 0, 0));

    var reportes = new ReportesService(new ReporteRepository(new NamedParameterJdbcTemplate(fuente)));
    var resultado = reportes.financiero(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "gerente@example.test");

    assertThat(resultado.generadoPor()).isEqualTo("gerente@example.test");
    assertThat(resultado.filas()).hasSize(4);
    assertThat(resultado.cobrosReservas()).isEqualByComparingTo(new BigDecimal("25.00"));
    assertThat(resultado.cobrosVentas()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(resultado.ingresosManuales()).isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(resultado.egresos()).isEqualByComparingTo(new BigDecimal("4.00"));
    assertThat(resultado.neto()).isEqualByComparingTo(new BigDecimal("51.00"));
  }
}
