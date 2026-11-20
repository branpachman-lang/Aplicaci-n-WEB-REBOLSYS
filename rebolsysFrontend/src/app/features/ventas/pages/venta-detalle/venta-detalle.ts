import { ElementRef } from '@angular/core';
import { ImpresionService } from '../../../../core/services/impresion-service';
import { ComprobanteDialog } from '../../../reservas/components/comprobante-dialog/comprobante-dialog';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { Catalogos } from '../../../../core/models/catalogo.model';
import { PermisosUi } from '../../../../core/services/permisos-ui';
import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { Component, inject, input, signal } from "@angular/core";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { RouterLink } from "@angular/router";
import { VentaService } from "../../services/venta-service";
import { Venta } from "../../models/venta.model";
import { VentaTicket } from "../../components/venta-ticket/venta-ticket";
import { Icon } from "../../../../shared/components/icono/icono";
import { mensajeError } from "../../../../core/utils/http-error";
@Component({
  selector: "app-venta-detalle",
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon, VentaTicket, ComprobanteDialog],
  templateUrl: "./venta-detalle.html",
  styleUrl: "./venta-detalle.css",
})
export class VentaDetalle {
  id = input.required<string>();
  readonly acceso = inject(PermisosUi);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly impresion = inject(ImpresionService);
  private readonly catalogoService = inject(CatalogoService);
  catalogos = signal<Catalogos | null>(null);
  comprobanteAbierto = signal(false);
  private servicio = inject(VentaService);
  venta = signal<Venta | null>(null);
  error = signal("");
  private readonly alertaError = notificarCambios(this.error, 'error');
  ngOnInit() {
    this.cargar();
  }
  cargar() {
    this.servicio.obtener(this.id()).subscribe({
      next: (v) => this.venta.set(v),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }
  abrirComprobante() {
    this.catalogoService.listar().subscribe({
      next: c => { this.catalogos.set(c); this.comprobanteAbierto.set(true); },
      error: e => this.error.set(mensajeError(e)),
    });
  }
  imprimir() {
    void this.impresion.imprimir(this.elemento.nativeElement.querySelector('app-venta-ticket [data-ticket]'))
      .catch(e => this.error.set(e.message));
  }
}
