import { Component, input } from '@angular/core';
import { EntregaComprobante } from '../../../../shared/components/entrega-comprobante/entrega-comprobante';
@Component({selector:'app-entrega-comprobante',template:''})
class EntregaStub { idComprobante = input.required<number>(); }
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse,provideHttpClient } from '@angular/common/http';
import { of,throwError } from 'rxjs';
import { ComprobanteDialog } from './comprobante-dialog';
import { ComprobanteService } from '../../services/comprobante-service';
import { ReservaService } from '../../services/reserva-service';
import { ClienteService } from '../../../clientes/services/cliente-service';
import { CuentaDocumento } from '../../models/reserva-api.model';
import { Emision } from '../../models/emision.model';

describe('Comprobantes de reservas',()=>{
  const persona={idTipoDocumento:1,numeroDocumento:'12345678',empresa:false,nombres:'Cliente',apellidos:'Simulado',fechaNacimiento:'1990-01-01',razonSocial:null,direccionFiscal:null,correo:null,telefono:null};
  const doc:CuentaDocumento={idCuenta:9,idReserva:1,idVenta:null,tipo:'BOLETA',estado:'EMITIDO',serie:'BINT',numero:'1',idClienteReceptor:1,total:118,pagado:118,saldo:0,lineas:[{idDetalleReserva:1,idDetalleVenta:null,descripcion:'Habitación 201',cantidad:1,total:118}],pagos:[]};
  const emision:Emision={idEmision:1,idComprobante:9,idUsuario:130,ambiente:'BETA',estado:'PREPARADO',serie:'B001',correlativo:1,subtotal:100,igv:18,total:118,codigoRespuesta:null,mensajeRespuesta:null,xmlDisponible:true,cdrDisponible:false};
  async function fixture(emitido=false,saldo=0,venta=false){
    const cuenta={idReserva:1,numeroReserva:'R1',totalAlojamiento:118,totalCargos:118,totalPagado:118-saldo,saldoPendiente:saldo,admiteConsumosConsolidados:false,cuentas:[{...doc,estado:emitido?'EMITIDO':'BORRADOR',pagado:118-saldo,saldo}]};
    const api={simular:vi.fn(),cuentaVenta:vi.fn(),emitirVenta:vi.fn(),configuracion:vi.fn(()=>of({ambiente:'BETA',habilitada:true,configuracionPendiente:[]})),estado:vi.fn(()=>throwError(()=>new HttpErrorResponse({status:404}))),emitir:vi.fn(()=>of(doc)),preparar:vi.fn(()=>of(emision)),enviar:vi.fn(()=>of({...emision,estado:'ACEPTADO'})),consultar:vi.fn(()=>of({...emision,estado:'ACEPTADO'}))};
    api['simular']=vi.fn(()=>of({...emision,codigoRespuesta:'SIMULACION_LOCAL',xmlDisponible:false}));
    api['cuentaVenta']=vi.fn(()=>of({...cuenta.cuentas[0],idVenta:4}));
    api['emitirVenta']=vi.fn(()=>of({...doc,idVenta:4}));
    const clientes={obtener:vi.fn(()=>of({idCliente:1,persona})),registrar:vi.fn(()=>of({idCliente:2,persona})),buscarPorDocumento:vi.fn()};
    await TestBed.configureTestingModule({imports:[ComprobanteDialog],providers:[provideHttpClient(),{provide:ComprobanteService,useValue:api},{provide:ReservaService,useValue:{cuenta:()=>of(cuenta)}},{provide:ClienteService,useValue:clientes}]}).overrideComponent(ComprobanteDialog,{remove:{imports:[EntregaComprobante]},add:{imports:[EntregaStub]}}).compileComponents();
    const f=TestBed.createComponent(ComprobanteDialog);
    if (venta) f.componentRef.setInput('venta',{idVenta:4,idCliente:1});
    else f.componentRef.setInput('reserva',{idReserva:1,idCliente:1,idUsuario:130,titular:'Cliente Simulado',detalles:[],cuenta});
    f.componentRef.setInput('catalogos',{documentos:[{id:1,nombre:'DNI',codigoSunat:'1'},{id:2,nombre:'RUC',codigoSunat:'6'}],pagos:[],ocupantes:[],usuarios:[{id:130,nombre:'Recepción'}],turnosAbiertos:[{idTurno:101,idUsuario:130,usuario:'Recepción'}]});
    f.detectChanges();return {f,c:f.componentInstance,api,clientes};
  }
  it('emite boleta al titular sin registrar otro pago ni crear otro cliente',async()=>{
    const {c,api,clientes}=await fixture();c.emitir();
    expect(api.emitir).toHaveBeenCalledWith(1,{idUsuario:130,idTurno:101,tipo:'BOLETA',idClienteReceptor:1});
    expect(clientes.registrar).not.toHaveBeenCalled();expect(c.documento()?.serie).toBe('BINT');
    expect(api.enviar).not.toHaveBeenCalled();expect(api.preparar).not.toHaveBeenCalled();
  });
  it('exige RUC para factura y admite receptor distinto al titular',async()=>{
    const {c,api,clientes}=await fixture();c.tipo='FACTURA';c.emitir();
    expect(api.emitir).not.toHaveBeenCalled();expect(c.error()).toContain('RUC');
    c.otroReceptor();c.receptor={...persona,idTipoDocumento:2,numeroDocumento:'20000000001',empresa:true,razonSocial:'Empresa simulada',nombres:null,apellidos:null,direccionFiscal:'Dirección de prueba'};c.emitir();
    expect(clientes.registrar).toHaveBeenCalledTimes(1);expect(api.emitir).toHaveBeenCalledWith(1,expect.objectContaining({tipo:'FACTURA',idClienteReceptor:2}));
  });
  it('reutiliza la misma cuenta y receptor después de perder la confirmación interna',async()=>{
    const {c,api}=await fixture();api.emitir.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503})));
    c.emitir();expect(c.emisionFijada()).toBe(true);const solicitud=api.emitir.mock.calls[0];
    c.emitir();expect(api.emitir.mock.calls[1]).toEqual(solicitud);expect(c.documento()?.estado).toBe('EMITIDO');
  });
  it('consulta un documento existente sin volver a emitirlo',async()=>{
    const {c,api}=await fixture(true);expect(c.documento()?.idCuenta).toBe(9);expect(c.estadoConsultado()).toBe(true);
    expect(api.estado).toHaveBeenCalledWith(9);expect(api.emitir).not.toHaveBeenCalled();
  });
  it('las cuotas cubren exactamente el saldo y el impuesto se desglosa del total',async()=>{
    const {c,api}=await fixture(true,68);c.cuotas=[{fechaPago:c.hoy,monto:60}];c.preparar();
    expect(api.preparar).not.toHaveBeenCalled();expect(c.error()).toContain('exactamente');
    c.cuotas=[{fechaPago:c.hoy,monto:68}];c.preparar();
    expect(api.preparar).toHaveBeenCalledWith(9,expect.objectContaining({tasaIgv:18,cuotas:[{fechaPago:c.hoy,monto:68}]}));
    expect(c.emision()?.total).toBe(118);expect(api.enviar).not.toHaveBeenCalled();
  });
  it('conserva clave y datos de preparación cuando la respuesta no confirma el resultado',async()=>{
    const {c,api}=await fixture(true);api.preparar.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503})));
    c.preparar();const envio=api.preparar.mock.calls[0];c.tasaIgv=20;c.preparar();
    expect(api.preparar.mock.calls[1]).toEqual(envio);
  });
  it('un estado incierto permite consultar, sin volver a enviar',async()=>{
    const {c,api,f}=await fixture(true);c.emision.set({...emision,estado:'INCIERTO'});f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Consultar respuesta beta');
    expect(f.nativeElement.textContent).not.toContain('Enviar a APIsPerú beta');c.enviar();
    expect(api.enviar).not.toHaveBeenCalled();c.consultar();expect(api.consultar).toHaveBeenCalledWith(9,130);
  });
  it('bloquea la preparación si no se pudo consultar una emisión preexistente',async()=>{
    const {c,api}=await fixture(true);api.estado.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503})));c.cargarEstado(9);c.preparar();
    expect(c.estadoConsultado()).toBe(false);expect(api.preparar).not.toHaveBeenCalled();
  });
  it('una cuenta de consumo separado envía su idVenta',async()=>{
    const {c,api}=await fixture();c.cuentas.update(cs=>[...cs,{...doc,idCuenta:10,idVenta:20,estado:'BORRADOR'}]);c.seleccion.set(20);c.cambiarCuenta();c.emitir();
    expect(api.emitir).toHaveBeenCalledWith(1,expect.objectContaining({idVenta:20}));
  });
  it('una factura a crédito exige dirección antes de preparar',async()=>{
    const {c,api}=await fixture(true,68);c.documento.update(d=>({...d!,tipo:'FACTURA'}));c.cuotas=[{fechaPago:c.hoy,monto:68}];c.preparar();
    expect(c.error()).toContain('dirección');expect(api.preparar).not.toHaveBeenCalled();
  });
  it('simula sin configuración del proveedor y bloquea el envío fiscal de esa simulación',async()=>{
    const {c,api,f}=await fixture(true);
    c.configuracion.set({ambiente:'BETA',habilitada:false,configuracionPendiente:['credencial']});
    f.detectChanges();expect(f.nativeElement.textContent).toContain('Simular factura o boleta localmente');
    c.preparar(true);expect(api.simular).toHaveBeenCalledWith(9,expect.objectContaining({tasaIgv:18}));
    expect(api.preparar).not.toHaveBeenCalled();c.enviar();expect(api.enviar).not.toHaveBeenCalled();
  });
  it('genera el comprobante de una venta directa con la misma pantalla',async()=>{
    const {c,api}=await fixture(false,0,true);c.emitir();
    expect(api.cuentaVenta).toHaveBeenCalledWith(4);
    expect(api.emitirVenta).toHaveBeenCalledWith(4,expect.objectContaining({tipo:'BOLETA',idClienteReceptor:1}));
    expect(api.emitir).not.toHaveBeenCalled();expect(c.documento()?.idVenta).toBe(4);
  });
});
