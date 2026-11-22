import { Component, signal, viewChild } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, Header, Sidebar],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.css',
})
export class MainLayout {
  protected readonly menuColapsado = signal(true);
  private readonly sidebar = viewChild.required(Sidebar);

  alternarMenu(): void {
    this.sidebar().cerrarConfiguracion();
    this.menuColapsado.update((actual) => !actual);
  }

  cerrarMenu(evento?: Event): void {
    const submenuCerrado = this.sidebar().cerrarConfiguracion();
    if (evento && submenuCerrado) {
      evento.preventDefault();
      evento.stopPropagation();
      return;
    }
    this.menuColapsado.set(true);
  }
}
