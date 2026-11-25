import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { Component, OnInit, inject, signal } from "@angular/core";
import { PermisosUi } from "../../../../core/services/permisos-ui";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { VentaService } from "../../services/venta-service";
import { Venta } from "../../models/venta.model";
import { Pagina } from "../../../../core/models/pagina-response";
import { mensajeError } from "../../../../core/utils/http-error";
import { Icon } from "../../../../shared/components/icono/icono";
@Component({
  selector: "app-venta-list",
  imports: [CurrencyPipe, DatePipe, RouterLink, Icon],
  templateUrl: "./venta-list.html",
  styleUrl: "./venta-list.css",
})
export class VentaList implements OnInit {
  readonly acceso = inject(PermisosUi);
  private servicio = inject(VentaService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  respuesta = signal<Pagina<Venta> | null>(null);
  error = signal("");
  private readonly alertaError = notificarCambios(this.error, 'error');
  cargando = signal(true);
  pagina = this.paginaInicial();
  tamanio = 10;

  private paginaInicial(): number {
    const raw = Number(this.ruta.snapshot.queryParamMap.get("pagina"));
    return Number.isInteger(raw) && raw >= 0 ? raw : 0;
  }

  ngOnInit() {
    this.cargar(false);
  }

  cargar(sincronizar = true) {
    if (sincronizar) {
      void this.router.navigate([], {
        relativeTo: this.ruta,
        queryParams: { pagina: this.pagina > 0 ? this.pagina : null },
        queryParamsHandling: "merge",
        replaceUrl: true,
      });
    }
    this.error.set("");
    this.cargando.set(true);
    this.servicio.listar(this.pagina, this.tamanio).subscribe({
      next: (r) => {
        this.respuesta.set(r);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }
}
