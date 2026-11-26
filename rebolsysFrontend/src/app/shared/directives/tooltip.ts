import { DOCUMENT } from '@angular/common';
import { AfterViewInit, Directive, ElementRef, HostListener, OnDestroy, Renderer2, inject, input } from '@angular/core';

let siguienteTooltip = 0;

@Directive({ selector: '[appTooltip]' })
export class Tooltip implements AfterViewInit, OnDestroy {
  readonly appTooltip = input.required<string>();
  readonly tooltipAyuda = input(false);

  private readonly documento = inject(DOCUMENT);
  private readonly renderer = inject(Renderer2);
  private readonly disparador = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly id = 'tooltip-' + ++siguienteTooltip;
  private globo?: HTMLElement;
  private visible = false;
  private cierre?: ReturnType<typeof setTimeout>;
  private limpiarEventos: (() => void)[] = [];
  private limpiarEventosVisibles: (() => void)[] = [];

  ngAfterViewInit() {
    const padre = this.renderer.parentNode(this.disparador);
    if (!padre) return;
    const globo: HTMLElement = this.renderer.createElement('span');
    this.globo = globo;
    this.renderer.addClass(globo, 'app-tooltip');
    this.renderer.setAttribute(globo, 'id', this.id);
    this.renderer.setAttribute(globo, 'role', 'tooltip');
    this.renderer.setAttribute(globo, 'popover', 'manual');
    this.renderer.setProperty(globo, 'hidden', true);
    this.renderer.setProperty(globo, 'textContent', this.appTooltip());
    this.renderer.insertBefore(padre, globo, this.renderer.nextSibling(this.disparador));
    const anterior = this.disparador.getAttribute('aria-describedby');
    this.renderer.setAttribute(this.disparador, 'aria-describedby', [anterior, this.id].filter(Boolean).join(' '));
    this.limpiarEventos = [
      this.renderer.listen(globo, 'pointerenter', () => clearTimeout(this.cierre)),
      this.renderer.listen(globo, 'pointerleave', () => this.programarCierre()),
    ];
  }

  @HostListener('pointerenter', ['$event'])
  entrar(evento: PointerEvent) {
    if (evento.pointerType !== 'touch') this.mostrar();
  }

  @HostListener('focusin')
  enfocar() {
    if (this.disparador.matches(':focus-visible')) this.mostrar();
  }

  @HostListener('click')
  activar() {
    if (this.tooltipAyuda()) this.mostrar();
    else this.ocultar();
  }

  @HostListener('pointerleave')
  programarCierre() {
    clearTimeout(this.cierre);
    if (!this.disparador.matches(':focus-visible')) {
      this.cierre = setTimeout(() => this.ocultar(), 180);
    }
  }

  @HostListener('focusout')
  salirDelFoco() {
    this.ocultar();
  }

  private mostrar() {
    clearTimeout(this.cierre);
    const globo = this.globo;
    const texto = this.appTooltip().trim();
    if (!globo || !texto || this.disparador.matches(':disabled')) return;
    this.renderer.setProperty(globo, 'textContent', texto);
    if (!this.visible) {
      this.visible = true;
      this.renderer.setProperty(globo, 'hidden', false);
      globo.showPopover?.();
      this.limpiarEventosVisibles = [
        this.renderer.listen(this.documento, 'scroll', () => this.posicionar(), { capture: true, passive: true }),
        this.renderer.listen('window', 'resize', () => this.posicionar()),
        this.renderer.listen(this.documento, 'pointerdown', (evento: PointerEvent) => {
          if (!evento.composedPath().includes(this.disparador) && !evento.composedPath().includes(globo)) this.ocultar();
        }),
        this.renderer.listen(this.documento, 'keydown', (evento: KeyboardEvent) => {
          if (evento.key !== 'Escape') return;
          evento.preventDefault();
          evento.stopPropagation();
          this.ocultar();
        }, { capture: true }),
      ];
    }
    this.posicionar();
  }

  private posicionar() {
    if (!this.visible || !this.globo) return;
    const objetivo = this.disparador.getBoundingClientRect();
    const { width, height } = this.globo.getBoundingClientRect();
    const { clientWidth, clientHeight } = this.documento.documentElement;
    const margen = 8;
    if (objetivo.bottom < 0 || objetivo.top > clientHeight || this.disparador.matches(':disabled')) {
      this.ocultar();
      return;
    }
    const debajo = objetivo.bottom + margen + height <= clientHeight - margen;
    const x = Math.max(margen, Math.min(objetivo.left + (objetivo.width - width) / 2, clientWidth - width - margen));
    const y = debajo ? objetivo.bottom + margen : Math.max(margen, objetivo.top - height - margen);
    this.renderer.setStyle(this.globo, 'left', `${x}px`);
    this.renderer.setStyle(this.globo, 'top', `${y}px`);
  }

  private ocultar() {
    clearTimeout(this.cierre);
    if (!this.visible || !this.globo) return;
    this.visible = false;
    this.globo.hidePopover?.();
    this.renderer.setProperty(this.globo, 'hidden', true);
    this.limpiarEventosVisibles.forEach(limpiar => limpiar());
    this.limpiarEventosVisibles = [];
  }

  ngOnDestroy() {
    this.ocultar();
    this.limpiarEventos.forEach(limpiar => limpiar());
    const padre = this.globo && this.renderer.parentNode(this.globo);
    if (padre) this.renderer.removeChild(padre, this.globo);
    const ids = this.disparador.getAttribute('aria-describedby')?.split(/\s+/).filter(id => id !== this.id);
    if (ids?.length) this.renderer.setAttribute(this.disparador, 'aria-describedby', ids.join(' '));
    else this.renderer.removeAttribute(this.disparador, 'aria-describedby');
  }
}
