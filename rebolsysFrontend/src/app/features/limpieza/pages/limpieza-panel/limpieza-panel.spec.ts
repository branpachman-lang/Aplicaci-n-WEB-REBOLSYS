import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { PermisosUi } from "../../../../core/auth/permisos-ui";
import { InformeDetalle } from "../../services/limpieza-api";
import { LimpiezaPanel } from "./limpieza-panel";

const base = "/api/v1/limpieza";
const habitacion = {
  idHabitacion: 11, numeroHabitacion: "101", idTipoHabitacion: 1, tipoHabitacion: "Doble",
  fechaSalida: "2026-10-26T10:30:00", estado: "POR_LIMPIAR",
  responsable: null, puedeIniciarLimpieza: true, puedeRetomarBorrador: false,
};
const evidencia = {
  evidenciaSha256: "a".repeat(64), evidenciaRuta: "/api/v1/limpieza/evidencias/" + "a".repeat(64) + ".png",
};
const pendiente: InformeDetalle = {
  informe: { idInformeRevision: 32, nombre: "INF-32", idHabitacion: 11, numeroHabitacion: "101",
    fechaEnvio: "2026-10-26T11:00:00", estado: "POR_APROBAR", responsable: { usuario: "limpieza" },
    tieneIncidencias: true, incidenciasPendientes: 1, tieneTickets: false,
    puedeAprobar: false, puedeEditar: true, puedeEliminar: true },
  observaciones: "Revisión del turno", tickets: [],
  items: [{ idDetalleInforme: 48, idItem: 9, item: "Mesa",
    estadoEncontrado: "DANADO", estadoActualItem: "DANADO", observaciones: "Pata suelta",
    evidencia, idTicket: null, puedeCrearTicket: true }],
};

describe("Informes y habitaciones", () => {
  function preparar(permisos: string[], gerente = false, roles: string[] = []) {
    TestBed.configureTestingModule({ imports: [LimpiezaPanel], providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(PermisosUi).actual.set({ idUsuario: 3, usuario: "limpieza", modo: "ACTIVA", gerente,
      idTurno: null, idFormaPagoEfectivo: null, permisos, roles });
    const fixture = TestBed.createComponent(LimpiezaPanel);
    const http = TestBed.inject(HttpTestingController);
    const raiz = fixture.nativeElement as HTMLElement;
    const boton = (nombre: string) => Array.from(raiz.querySelectorAll("button"))
      .find(b => b.textContent?.trim() === nombre);
    const refrescar = () => fixture.detectChanges();
    return { fixture, componente: fixture.componentInstance, http, raiz, boton, refrescar };
  }

  it("exige evidencia de la incidencia y habilita la aprobación al registrar el ticket", () => {
    const permisos = [
      "VER_INICIO", "VER_HABITACIONES", "VER_INFORMES", "VER_TICKETS", "INICIAR",
      "CARGAR_EVIDENCIA", "VER_EVIDENCIA", "ENVIAR_INFORME", "CREAR_TICKET", "APROBAR_INFORME",
    ].map(accion => "LIMPIEZA:" + accion);
    const { componente, http, raiz, boton, refrescar } = preparar(permisos, false, ["MANTENIMIENTO"]);
    http.expectOne(base + "/inicio").flush({ idTurno: 1, informesEnviados: 0, informesPorAprobar: 0,
      informesAprobados: 0, ticketsGenerados: 0 });
    http.expectOne("/api/v1/recepcion/catalogos").flush({ tiposHabitacion: [{ id: 1, nombre: "Doble" }] });
    refrescar();
    boton("Habitaciones")!.click();
    http.expectOne(base + "/habitaciones").flush([{ ...habitacion, estado: "DISPONIBLE" }]);
    refrescar();
    boton("Iniciar revisión")!.click();
    http.expectOne({ method: "POST", url: base + "/habitaciones/11/iniciar" }).flush({
      idInformeRevision: 32, habitacion: { ...habitacion, estado: "EN_LIMPIEZA" },
      items: [{ idItem: 9, item: "Mesa", estado: null }],
    });
    refrescar();
    expect(boton("Enviar informe")!.disabled).toBe(true);
    boton("Dañado")!.click();
    refrescar();
    expect(raiz.textContent).toContain("Foto requerida");
    expect(boton("Enviar informe")!.disabled).toBe(true);
    const foto = new File(["ejemplo"], "mesa.png", { type: "image/png" });
    const entrada = raiz.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(entrada, "files", { value: [foto] });
    entrada.dispatchEvent(new Event("change"));
    const subida = http.expectOne({ method: "POST", url: base + "/evidencias" });
    expect(subida.request.body.get("archivo").name).toBe("mesa.png");
    subida.flush(evidencia);
    refrescar();
    expect(boton("Enviar informe")!.disabled).toBe(false);
    componente.guardarInforme();
    const envio = http.expectOne({ method: "POST", url: base + "/borradores/32/enviar" });
    expect(envio.request.body.items[0]).toMatchObject({ estadoEncontrado: "DANADO", evidencia });
    envio.flush(pendiente);
    refrescar();
    expect(boton("Aprobar informe")!.disabled).toBe(true);
    boton("Tickets")!.click();
    http.expectOne(req => req.url === base + "/tickets").flush({ contenido: [], totalElementos: 0 });
    refrescar();
    boton("Informes pendientes")!.click();
    http.expectOne(req => req.url === base + "/informes" && req.params.get("estado") === "POR_APROBAR")
      .flush({ contenido: [pendiente.informe], totalElementos: 1 });
    refrescar();
    boton("Ver y atender")!.click();
    http.expectOne(base + "/informes/32").flush(pendiente);
    refrescar();
    boton("Atender incidencia")!.click();
    componente.cambiarAccion("REPARACION");
    refrescar();
    expect(raiz.querySelector('input[name="costo"]')).not.toBeNull();
    componente.costo = 25;
    componente.cambiarAccion("REPOSICION");
    refrescar();
    expect(raiz.querySelector('input[name="costo"]')).toBeNull();
    componente.evidenciaTicket.set(evidencia);
    componente.guardarTicket();
    const ticket = http.expectOne({ method: "POST", url: base + "/informes/32/items/48/ticket" });
    expect(ticket.request.body.accion).toMatchObject({ tipoAccion: "REPOSICION", costoReparacion: null });
    ticket.flush({ ticket: { idTicket: 18 } });
    http.expectOne(base + "/informes/32").flush({
      ...pendiente, informe: { ...pendiente.informe, incidenciasPendientes: 0, puedeAprobar: true, tieneTickets: true },
      items: [{ ...pendiente.items[0], estadoActualItem: "BUENO", idTicket: 18, puedeCrearTicket: false }],
    });
    refrescar();
    expect(boton("Aprobar informe")!.disabled).toBe(false);
    http.verify();
  });

  it("oculta la atención y la aprobación a Limpieza aunque tenga los permisos asignados", () => {
    const { componente, http, boton, refrescar } = preparar(
      ["LIMPIEZA:VER_INFORMES", "LIMPIEZA:APROBAR_INFORME", "LIMPIEZA:CREAR_TICKET"], false, ["LIMPIEZA"],
    );
    http.expectOne(req => req.url === base + "/informes")
      .flush({ contenido: [pendiente.informe], totalElementos: 1 });
    componente.panel.set({ tipo: "informe", datos: {
      ...pendiente, informe: { ...pendiente.informe, puedeAprobar: true, incidenciasPendientes: 0 },
    } });
    refrescar();
    expect(boton("Atender incidencia")).toBeUndefined();
    expect(boton("Aprobar informe")).toBeUndefined();
    http.verify();
  });

  it("muestra la consulta de habitación sin ofrecer operaciones fuera de sus permisos", () => {
    const { http, boton, refrescar } = preparar(["LIMPIEZA:VER_HABITACIONES"]);
    http.expectOne(base + "/habitaciones").flush([habitacion]);
    http.expectOne("/api/v1/recepcion/catalogos").flush({ tiposHabitacion: [] });
    refrescar();
    expect(boton("Ver habitación")).toBeDefined();
    expect(boton("Iniciar limpieza")).toBeUndefined();
    expect(boton("Informes")).toBeUndefined();
    expect(boton("Tickets")).toBeUndefined();
    http.verify();
  });

  it("muestra al gerente el resumen general sin pedir un turno", () => {
    const { http, raiz, refrescar } = preparar([], true);
    http.expectOne(base + "/inicio").flush({ idTurno: null, fechaApertura: null, alcance: "GENERAL",
      informesEnviados: 13, informesPorAprobar: 4, informesAprobados: 9, ticketsGenerados: 6 });
    http.expectOne("/api/v1/recepcion/catalogos").flush({ tiposHabitacion: [] });
    refrescar();
    expect(Array.from(raiz.querySelectorAll('.indicadores article')).map(indicador =>
      indicador.textContent?.trim())).toEqual([
      'Informes enviados13', 'Por aprobar4', 'Informes aprobados9', 'Tickets generados6',
    ]);
    expect(raiz.textContent).not.toContain("Abre tu turno");
    http.verify();
  });

  it("muestra la actividad diaria del personal de limpieza sin turno", () => {
    const { http, raiz, refrescar } = preparar(["LIMPIEZA:VER_INICIO"]);
    http.expectOne(base + "/inicio").flush({ idTurno: null, fechaApertura: "2026-11-08T00:00:00", alcance: "DIA",
      informesEnviados: 2, informesPorAprobar: 1, informesAprobados: 1, ticketsGenerados: 1 });
    refrescar();
    expect(raiz.textContent).toContain("Tu actividad de hoy");
    expect(raiz.textContent).not.toContain("Abre tu turno");
    http.verify();
  });
});
