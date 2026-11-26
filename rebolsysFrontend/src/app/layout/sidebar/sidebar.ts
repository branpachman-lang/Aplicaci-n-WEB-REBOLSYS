import { Component, DestroyRef, ElementRef, HostListener, computed, inject, output, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, finalize } from 'rxjs';
import { MENU, MenuItem } from '../../core/config/menu';
import { SesionService } from '../../core/auth/sesion-service';
import { PermisosUi } from '../../core/auth/permisos-ui';
import { Icon } from '../../shared/components/icono/icono';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Icon],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class Sidebar {
  protected readonly sesion = inject(SesionService);
  private readonly acceso = inject(PermisosUi);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  private readonly submenu = viewChild<ElementRef<HTMLElement>>('submenu');
  protected readonly menu = computed(() => MENU.flatMap(item => {
    if (!item.hijos) {
      if (!this.visible(item)) return [];
      if (item.ruta === '/ventas' && !this.sesion.permite('VENTAS:CREAR_Y_CONFIRMAR'))
        return [{ ...item, ruta: '/ventas/historial' }];
      if (item.ruta === '/reservas' && !this.sesion.permite('RESERVAS:VER'))
        return [{ ...item, ruta: '/reservas/nueva' }];
      return [item];
    }
    const hijos = item.hijos.filter(hijo => this.visible(hijo));
    return hijos.length ? [{ ...item, hijos }] : [];
  }));
  protected readonly configuracion = computed(() => this.menu().find(item => item.hijos));
  readonly navegar = output<void>();
  readonly configuracionAbierta = signal(false);
  protected readonly configuracionSeleccionada = signal(this.router.url.startsWith('/configuraciones'));
  protected readonly submenuTop = signal(12);
  protected readonly cerrandoSesion = signal(false);
  protected readonly errorSalida = signal('');

  constructor() {
    this.router.events.pipe(
      filter(evento => evento instanceof NavigationEnd), takeUntilDestroyed(),
    ).subscribe(() => {
      this.configuracionSeleccionada.set(this.router.url.startsWith('/configuraciones'));
      this.cerrarConfiguracion();
    });
  }

  protected posicionarSubmenu(boton: HTMLElement) {
    const alto = Math.min(76 + (this.configuracion()?.hijos?.length ?? 0) * 48, window.innerHeight - 24);
    this.submenuTop.set(Math.max(12, Math.min(boton.getBoundingClientRect().top, window.innerHeight - alto - 12)));
  }

  protected cambioSubmenu(evento: Event) {
    this.configuracionAbierta.set((evento as ToggleEvent).newState === 'open');
  }

  @HostListener('window:resize')
  cerrarConfiguracion(): boolean {
    const panel = this.submenu()?.nativeElement;
    if (!panel || typeof panel.hidePopover !== 'function' || !panel.matches(':popover-open')) return false;
    panel.hidePopover();
    this.configuracionAbierta.set(false);
    return true;
  }

  protected salirDelMenu() {
    this.cerrarConfiguracion();
    this.navegar.emit();
  }

  protected reintentarSesion() {
    this.sesion.cargar().subscribe({ error: () => {} });
  }

  protected cerrarSesion() {
    if (this.cerrandoSesion()) return;
    this.cerrarConfiguracion();
    this.errorSalida.set('');
    this.cerrandoSesion.set(true);
    this.sesion.cerrar().pipe(
      takeUntilDestroyed(this.destroy), finalize(() => this.cerrandoSesion.set(false)),
    ).subscribe({
      next: () => { this.navegar.emit(); void this.router.navigateByUrl('/login'); },
      error: () => this.errorSalida.set('No se pudo cerrar la sesión. Inténtalo nuevamente.'),
    });
  }

  private visible(item: MenuItem) {
    return (!item.soloActiva || this.sesion.actual()?.modo === 'ACTIVA')
      && (!item.soloGerencia || this.acceso.esGerente())
      && (!item.permisos || this.sesion.permite(...item.permisos));
  }
}
