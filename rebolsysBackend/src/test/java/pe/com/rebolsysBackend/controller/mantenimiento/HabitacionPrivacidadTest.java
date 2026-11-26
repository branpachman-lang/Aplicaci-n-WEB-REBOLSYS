package pe.com.rebolsysBackend.controller.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionEstadoResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.HabitacionReservaResponseDTO;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.service.service.mantenimiento.HabitacionService;

class HabitacionPrivacidadTest {
  private final LocalDate desde = LocalDate.of(2026, 11, 19);
  private final LocalDate hasta = desde.plusDays(2);
  private HabitacionController controller;
  private MockHttpServletRequest peticion;
  private HabitacionEstadoResponseDTO habitacion;

  @BeforeEach
  void preparar() {
    var reserva = new HabitacionReservaResponseDTO("RES-12", "Lucía Rojas Medina",
        desde.atTime(14, 0), hasta.atTime(12, 0), "EN_CURSO");
    habitacion = new HabitacionEstadoResponseDTO(10L, "201", "Matrimonial", 2, 2, 0,
        new BigDecimal("80.00"), EstadoHabitacion.OCUPADO, true, false, List.of(reserva), reserva.salida());
    var servicio = mock(HabitacionService.class);
    when(servicio.estado(desde, hasta)).thenReturn(List.of(habitacion));
    controller = new HabitacionController(servicio);
    peticion = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
  }

  @AfterEach
  void limpiarPeticion() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void ocultaElClienteSinModificarLosDatosOriginales() {
    sesion(List.of("HABITACIONES:VER"));
    var resultado = controller.estado(desde, hasta).getFirst();
    assertThat(resultado.reservas().getFirst().cliente()).isEqualTo("Ocupación reservada");
    assertThat(resultado.reservas().getFirst().referencia()).isEqualTo("RES-12");
    assertThat(resultado.reservas().getFirst().ingreso()).isEqualTo(desde.atTime(14, 0));
    assertThat(resultado.precio()).isEqualByComparingTo("80.00");
    assertThat(habitacion.reservas().getFirst().cliente()).isEqualTo("Lucía Rojas Medina");
  }

  @Test
  void conservaElClienteConPermisoDeVerReservas() {
    sesion(List.of("HABITACIONES:VER", "RESERVAS:VER"));
    assertThat(controller.estado(desde, hasta).getFirst().reservas().getFirst().cliente())
        .isEqualTo("Lucía Rojas Medina");
  }

  private void sesion(List<String> permisos) {
    peticion.setAttribute(SesionOperacionService.ATRIBUTO,
        new SesionActualDTO(7L, "personal.prueba", "SESION", null, null,
            false, false, List.of("LIMPIEZA"), permisos));
  }
}
