package pe.com.rebolsysBackend.service.impl.limpieza;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;

class PoliticaAccionesLimpiezaTest {
  @Test void mantenimientoNecesitaRolYPermiso() {
    for (String accion : List.of("APROBAR_INFORME", "CREAR_TICKET", "EDITAR_TICKET", "ELIMINAR_TICKET")) {
      var autorizado = cuenta(List.of("MANTENIMIENTO"), List.of("LIMPIEZA:" + accion), false, false);
      var sinRol = cuenta(List.of("LIMPIEZA"), autorizado.permisos(), false, false);
      var sinPermiso = cuenta(List.of("MANTENIMIENTO"), List.of(), false, false);
      assertThat(PoliticaAccionesLimpieza.permite(autorizado, accion)).isTrue();
      PoliticaAccionesLimpieza.exigirPermiso(autorizado, accion);
      for (var denegado : List.of(sinRol, sinPermiso)) {
        assertThat(PoliticaAccionesLimpieza.permite(denegado, accion)).isFalse();
        assertThatThrownBy(() -> PoliticaAccionesLimpieza.exigirPermiso(denegado, accion))
            .isInstanceOf(AccessDeniedException.class);
      }
    }
  }

  @Test void gerenciaSinMantenimientoNoPuedeAprobar() {
    assertThat(PoliticaAccionesLimpieza.permite(cuenta(List.of("GERENTE"), List.of(), true, false),
        "APROBAR_INFORME")).isFalse();
    assertThat(PoliticaAccionesLimpieza.permite(cuenta(List.of("GERENTE", "MANTENIMIENTO"),
        List.of(), true, false), "APROBAR_INFORME")).isTrue();
  }

  @Test void cambioDeClavePendienteImpideAccionesAunqueTengaRolYPermiso() {
    var actual = cuenta(List.of("MANTENIMIENTO"), List.of("LIMPIEZA:CREAR_TICKET"), false, true);
    assertThat(PoliticaAccionesLimpieza.permite(actual, "CREAR_TICKET")).isFalse();
  }

  @Test void accionesDeLimpiezaRespetanElPermisoSinExigirMantenimiento() {
    var actual = cuenta(List.of("LIMPIEZA"), List.of("LIMPIEZA:EDITAR_INFORME"), false, false);
    assertThat(PoliticaAccionesLimpieza.permite(actual, "EDITAR_INFORME")).isTrue();
    assertThat(PoliticaAccionesLimpieza.permite(actual, "ELIMINAR_INFORME")).isFalse();
  }

  private SesionActualDTO cuenta(List<String> roles, List<String> permisos, boolean gerente, boolean cambio) {
    return new SesionActualDTO(1L, "usuario", "ACTIVA", null, null, cambio, gerente, roles, permisos);
  }
}
