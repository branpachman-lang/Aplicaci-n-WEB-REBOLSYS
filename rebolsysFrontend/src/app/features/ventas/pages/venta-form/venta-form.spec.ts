import { CatalogoService } from '../../../../core/services/catalogo-service';
import { TestBed } from "@angular/core/testing";
import { provideRouter } from "@angular/router";
import { of, throwError } from "rxjs";
import { VentaForm } from "./venta-form";
import { ProductoService } from "../../../productos/services/producto-service";
import { CategoriaService } from "../../../categorias/services/categoria-service";
import { VentaService } from "../../services/venta-service";
import { ReservaService } from '../../../reservas/services/reserva-service';

describe("Venta de la tiendita", () => {
  const producto = {
    idProducto: 1,
    nombreProducto: "Agua",
    precioActual: 3.5,
    stock: 2,
    stockMinimo: 1,
    idCategoria: 1,
    categoriaNombre: "Bebidas",
    estadoProducto: "ACTIVO" as const,
    imagenRuta: null,
  };
  const reserva = {
    idReserva: 7,
    idDetalleReserva: 10,
    referencia: "G7",
    numero: "0000007",
    habitacion: "202",
    clienteNombre: "Titular",
    idCliente: 7,
    huespedes: ["Huésped de prueba"],
    estadoDetalle: 'EN_CURSO' as const,
    fechaIngreso: '2026-11-03T12:00:00',
    fechaSalida: '2026-11-04T12:00:00',
  };
  const registrar = vi.fn();
  const actualizar = vi.fn();
  const ingresar = vi.fn();
  async function fixture(original?: any,falloReservas=false,falloCatalogo=false, opciones:{confirmada?:boolean;admiteCargos?:boolean;fallarCuenta?:boolean}={}) {
    registrar.mockReset();
    actualizar.mockReset();
    ingresar.mockReset();
    actualizar.mockReturnValue(
      throwError(() => ({ error: { message: "No se pudo guardar" } })),
    );
    registrar.mockReturnValue(
      throwError(() => ({ error: { message: "Fallo de prueba" } })),
    );
    await TestBed.configureTestingModule({
      imports: [VentaForm],
      providers: [
        provideRouter([]),
        { provide: CatalogoService, useValue: { listar: () => of({documentos: [], pagos: [{id:1,nombre:'Efectivo'}], ocupantes: [], usuarios: [{id:1,nombre:'Recepción'}], turnosAbiertos: [{idTurno:1,idUsuario:1,usuario:'Recepción',fechaApertura:''}]}) } },
        {
          provide: ProductoService,
          useValue: { catalogo: () => falloCatalogo?throwError(()=>({status:503,error:{message:'Catálogo no disponible'}})):of([producto]) },
        },
        {
          provide: CategoriaService,
          useValue: {
            listar: () => of([{ idCategoria: 1, nombreCategoria: "Bebidas" }]),
          },
        },
        {
          provide: ReservaService,
          useValue: {
            cuenta: () => opciones.fallarCuenta ? throwError(()=>({error:{message:'Cuenta no disponible'}})) : of({idReserva:7,admiteConsumosConsolidados:opciones.admiteCargos??true}),
            ingresar,
          },
        },
        {
          provide: VentaService,
          useValue: {
            reservas: () => falloReservas?throwError(()=>({status:400,error:{message:'No se pudieron consultar las estancias'}})):of([{...reserva,estadoDetalle:opciones.confirmada?'CONFIRMADA':'EN_CURSO'}]),
            registrar,
            obtener: () => of(original),
            actualizar,
          },
        },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(VentaForm);
    if (original) f.componentRef.setInput("id", "5");
    f.detectChanges();
    return f;
  }
  const pendiente = {
    idVenta: 5,
    editable: true,
    idDetalleReserva: 10,
    habitacion: "202",
    compradorNombre: "Titular",
    detalles: [
      {
        idProducto: 1,
        cantidad: 2,
        precioUnitario: 3,
        nombreProducto: "Agua",
        stockRestante: 2,
        imagenRuta: null,
      },
    ],
  };
  it('un fallo al consultar estancias no oculta los productos para venta externa',async()=>{
    const c=(await fixture(undefined,true)).componentInstance;
    expect(c.productos()).toHaveLength(1);expect(c.error()).toBe('');expect(c.errorReservas()).toContain('estancias');
    c.agregar(c.productos()[0]);expect(c.total()).toBe(3.5);
  });
  it('muestra la reserva confirmada y exige ingreso explícito antes de vender',async()=>{
    const f=await fixture(undefined,false,false,{confirmada:true}),c=f.componentInstance;
    c.cambiarComprador('HOSPEDAJE');c.elegirReserva(10);c.agregar(producto);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Confirmada · ingreso pendiente');
    c.confirmarVenta();expect(c.error()).toContain('Registra primero el ingreso');expect(registrar).not.toHaveBeenCalled();expect(c.pagoAbierto()).toBe(false);
    c.abrirIngreso();f.detectChanges();expect(f.nativeElement.querySelector('[role="dialog"]')).not.toBe(null);expect(ingresar).not.toHaveBeenCalled();
    ingresar.mockReturnValue(throwError(()=>({error:{message:'El ingreso debe estar dentro del periodo reservado'}})));
    c.registrarIngreso();expect(c.errorIngreso()).toContain('periodo reservado');expect(c.seleccionada()?.estadoDetalle).toBe('CONFIRMADA');expect(c.carrito()).toHaveLength(1);
    ingresar.mockReturnValue(of({idDetalleReserva:10,estadoDetalle:'EN_CURSO',fechaIngreso:reserva.fechaIngreso,fechaSalida:reserva.fechaSalida,ocupantes:[{nombre:'Huésped de prueba'}]}));
    c.registrarIngreso();expect(ingresar).toHaveBeenCalledWith(10);expect(c.seleccionada()?.estadoDetalle).toBe('EN_CURSO');expect(c.ingresoAbierto()).toBe(null);expect(registrar).not.toHaveBeenCalled();
  });
  it('una reserva pagada permite cobrar aparte y conserva su referencia',async()=>{
    const c=(await fixture(undefined,false,false,{admiteCargos:false})).componentInstance;
    c.cambiarComprador('HOSPEDAJE');c.elegirReserva(10);expect(c.modoVenta()).toBe('SEPARADA');
    c.agregar(producto);c.confirmarVenta();expect(c.pagoAbierto()).toBe(true);c.registrar(10);
    expect(registrar).toHaveBeenCalledWith(expect.objectContaining({modoVenta:'SEPARADA',idDetalleReserva:10}));
  });
  it('la venta del huésped se cobra aunque la consulta de su antigua cuenta no esté disponible',async()=>{
    const c=(await fixture(undefined,false,false,{fallarCuenta:true})).componentInstance;
    c.cambiarComprador('HOSPEDAJE');c.elegirReserva(10);c.agregar(producto);c.confirmarVenta();
    expect(registrar).not.toHaveBeenCalled();expect(c.pagoAbierto()).toBe(true);expect(c.carrito()).toHaveLength(1);
  });
  it('un fallo del catálogo se muestra como error con reintento y no como catálogo vacío',async()=>{
    const f=await fixture(undefined,false,true);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Reintentar carga');expect(f.nativeElement.textContent).not.toContain('El catálogo está vacío');
  });
  it("edita un borrador respetando el stock que todavía no se ha descontado", async () => {
    const c = (await fixture(pendiente)).componentInstance;
    expect(c.cantidad(1)).toBe(2);
    expect(c.total()).toBe(6);
    c.agregar(c.productos()[0]);
    expect(c.cantidad(1)).toBe(2);
    c.confirmarVenta();
    expect(actualizar).toHaveBeenCalledWith(
      "5",
      expect.objectContaining({
        detalles: [{ idProducto: 1, cantidad: 2, precioEsperado: 3 }],
      }),
    );
    expect(registrar).not.toHaveBeenCalled();
    expect(c.pagoAbierto()).toBe(false);
    expect(c.carrito()).toHaveLength(1);
  });
  it("bloquea la edición si el servidor ya informa un comprobante o cobro", async () => {
    const c = (await fixture({ ...pendiente, editable: false }))
      .componentInstance;
    expect(c.error()).toContain("No se puede editar");
    c.agregar(producto);
    c.confirmarVenta();
    expect(actualizar).not.toHaveBeenCalled();
    expect(registrar).not.toHaveBeenCalled();
  });
  it("respeta stock y calcula cantidades y total en céntimos", async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);
    c.agregar(producto);
    c.agregar(producto);
    expect(c.cantidad(1)).toBe(2);
    expect(c.total()).toBe(7);
    c.restar(1);
    expect(c.total()).toBe(3.5);
    c.quitar(1);
    expect(c.unidades()).toBe(0);
  });
  it("filtra categorías y nombres y permite recuperar el catálogo", async () => {
    const c = (await fixture()).componentInstance;
    c.buscar.set("agua");
    expect(c.visibles()).toHaveLength(1);
    c.categoria.set(99);
    expect(c.visibles()).toHaveLength(0);
    c.categoria.set(null);
    c.buscar.set("");
    expect(c.visibles()).toHaveLength(1);
  });
  it("no permite un cargo sin seleccionar habitación", async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);
    c.cambiarComprador("HOSPEDAJE");
    c.confirmarVenta();
    expect(registrar).not.toHaveBeenCalled();
    expect(c.error()).toContain("Selecciona");
  });
  it("la venta externa abre el pago y no registra al cerrar", async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);
    c.confirmarVenta();
    expect(c.pagoAbierto()).toBe(true);
    expect(registrar).not.toHaveBeenCalled();
    c.pagoAbierto.set(false);
    expect(c.carrito()).toHaveLength(1);
  });
  it('carga el consumo a la reserva y conserva el carrito si falla', async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);c.cambiarComprador('HOSPEDAJE');c.elegirReserva(10);c.confirmarVenta();
    expect(c.pagoAbierto()).toBe(false);
    expect(registrar).toHaveBeenCalledWith(expect.objectContaining({modoVenta:'CARGO_RESERVA',idDetalleReserva:10,efectivoRecibido:null}));
    expect(c.carrito()).toHaveLength(1);
    const clave = registrar.mock.calls[0][0].claveRegistro;
    c.confirmarVenta();expect(registrar.mock.calls[1][0].claveRegistro).toBe(clave);
  });
  it("cobrar aparte conserva la referencia a la reserva sin consolidar su cuenta", async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);
    c.cambiarComprador("HOSPEDAJE");
    c.elegirReserva(10);
    c.modoVenta.set("SEPARADA");
    c.confirmarVenta();
    expect(c.pagoAbierto()).toBe(true);
    c.registrar(10);
    expect(registrar).toHaveBeenCalledWith(
      expect.objectContaining({
        modoVenta: "SEPARADA",
        idDetalleReserva: 10,
        idCliente: 7,
        efectivoRecibido: 10,
      }),
    );
  });
  it("un huésped con ticket propio no se convierte en el titular", async () => {
    const c = (await fixture()).componentInstance;
    c.agregar(producto);
    c.cambiarComprador("HOSPEDAJE");
    c.elegirReserva(10);
    c.compradorNombre.set("Huésped de prueba");
    c.modoVenta.set("SEPARADA");
    c.confirmarVenta();
    c.registrar(10);
    expect(registrar).toHaveBeenCalledWith(
      expect.objectContaining({
        idCliente: null,
        compradorNombre: "Huésped de prueba",
      }),
    );
  });
});
