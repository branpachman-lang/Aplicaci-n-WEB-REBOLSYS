package pe.com.rebolsysBackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reserva_salida_operacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReservaSalidaOperacion {
  @Id
  @Column(name = "claveOperacion", length = 64)
  private String claveOperacion;

  @Column(name = "idDetalleReserva", nullable = false, unique = true, columnDefinition = "NUMBER")
  private Long idDetalleReserva;

  @Column(name = "idUsuario", nullable = false, columnDefinition = "NUMBER")
  private Long idUsuario;

  @Column(name = "firmaSolicitud", nullable = false, length = 64)
  private String firmaSolicitud;

  @Column(name = "idPago", columnDefinition = "NUMBER")
  private Long idPago;

  @Column(name = "idComprobante", columnDefinition = "NUMBER")
  private Long idComprobante;
}
