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
  const doc:CuentaDocumento={idCuenta:9,idReserva:1,idVenta:null,tipo:'BOLETA',estado:'EMITIDO',serie:'BINT',numero:'1',idClienteReceptor:1,total:118,subtotal:100,igv:18,pagado:118,saldo:0,lineas:[{idDetalleReserva:1,idDetalleVenta:null,descripcion:'Habitación 201',cantidad:1,total:118}],pagos:[]};
  const emision:Emision={idEmision:1,idComprobante:9,idUsuario:130,ambiente:'BETA',estado:'PREPARADO',serie:'B001',correlativo:1,subtotal:100,igv:18,total:118,codigoRespuesta:null,mensajeRespuesta:null,xmlDisponible:true,cdrDisponible:false};
  async function fixture(emitido=false,saldo=0,venta=false){
    const cuenta={idReserva:1,numeroReserva:'R1',totalAlojamiento:118,totalCargos:118,totalPagado:118-saldo,saldoPendiente:saldo,admiteConsumosConsolidados:false,cuentas:[{...doc,estado:emitido?'EMITIDO':'BORRADOR',pagado:118-saldo,saldo}]};
    const api={simular:vi.fn(),cuentaVenta:vi.fn(),emitirVenta:vi.fn(),configuracion:vi.fn(()=>of({ambiente:'BETA',habilitada:true,configuracionPendiente:[]})),estado:vi.fn(()=>throwError(()=>new HttpErrorResponse({status:404}))),emitir:vi.fn(()=>of(doc)),preparar:vi.fn(()=>of(emision)),enviar:vi.fn(()=>of({...emision,estado:'ACEPTADO'})),consultar:vi.fn(()=>of({...emision,estado:'ACEPTADO'}))};
    api['simular']=vi.fn(()=>of({...emision,codigoRespuesta:'SIMULACION_LOCAL',xmlDisponible:false}));
    api['cuentaVenta']=vi.fn(()=>of({...cuenta.cuentas[0],idVenta:4}));
    api['emitirVenta']=vi.fn(()=>of({...doc,idVenta:4}));
    const clientes={obtener:vi.fn(()=>of({idCliente:1,persona})),registrar:vi.fn(()=>of({idCliente:2,persona})),buscarPorDocumento:vi.fn().mockReturnValue(throwError(()=>new HttpErrorResponse({status:404})))};
    const reservas={cuenta:vi.fn(()=>of(cuenta)),crear:vi.fn()};
    await TestBed.configureTestingModule({imports:[ComprobanteDialog],providers:[provideHttpClient(),{provide:ComprobanteService,useValue:api},{provide:ReservaService,useValue:reservas},{provide:ClienteService,useValue:clientes}]}).overrideComponent(ComprobanteDialog,{remove:{imports:[EntregaComprobante]},add:{imports:[EntregaStub]}}).compileComponents();
    const f=TestBed.createComponent(ComprobanteDialog);
    if (venta) f.componentRef.setInput('venta',{idVenta:4,idCliente:1});
    else f.componentRef.setInput('reserva',{idReserva:1,idCliente:1,idUsuario:130,titular:'Cliente Simulado',detalles:[],cuenta});
    f.componentRef.setInput('catalogos',{documentos:[{id:1,nombre:'DNI',codigoSunat:'1'},{id:2,nombre:'RUC',codigoSunat:'6'}],pagos:[],ocupantes:[],usuarios:[{id:130,nombre:'Recepción'}],turnosAbiertos:[{idTurno:101,idUsuario:130,usuario:'Recepción'}]});
    f.detectChanges();return {f,c:f.componentInstance,api,clientes,reservas};
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
    expect(clientes.registrar).toHaveBeenCalledWith(expect.objectContaining({idTipoDocumento:2,numeroDocumento:'20000000001',empresa:true,razonSocial:'Empresa simulada',nombres:null,apellidos:null}));
  });
  it('al elegir factura mantiene al titular con DNI y abre un receptor identificado con RUC',async()=>{
    const {c,f}=await fixture();
    c.tipo='FACTURA';c.cambiarTipo();f.detectChanges();
    expect(c.idCliente).toBeNull();expect(c.receptor.idTipoDocumento).toBe(2);
    expect(c.receptor.numeroDocumento).toBe('');expect(c.titular()?.persona.numeroDocumento).toBe('12345678');
    expect(c.reserva()?.idCliente).toBe(1);expect(c.documentosReceptor().map(d=>d.codigoSunat)).toEqual(['6']);
    expect(f.nativeElement.textContent).toContain('Titular de la reserva');
    expect(f.nativeElement.textContent).toContain('Datos del receptor de la factura');
  });
  it('reutiliza un RUC existente al emitir sin crear otro cliente, reserva o pago',async()=>{
    const {c,api,clientes,reservas}=await fixture();const original=structuredClone(c.reserva());
    c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');
    c.receptor.razonSocial='Nombre ingresado';c.receptor.direccionFiscal='Av. Los Andes 123';
    clientes.buscarPorDocumento.mockReturnValue(of({idCliente:22,datos:{...c.receptor,razonSocial:'Empresa registrada SAC'}}));
    c.emitir();
    expect(clientes.buscarPorDocumento).toHaveBeenCalledWith(2,'20123456789');
    expect(clientes.registrar).not.toHaveBeenCalled();expect(reservas.crear).not.toHaveBeenCalled();
    expect(api.emitir).toHaveBeenCalledWith(1,expect.objectContaining({tipo:'FACTURA',idClienteReceptor:22}));
    expect(c.receptor.razonSocial).toBe('Empresa registrada SAC');expect(c.reserva()).toEqual(original);
  });
  it('distingue al contribuyente con RUC 10 y conserva los nombres cuando contiene el DNI del titular',async()=>{
    const {c}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('10123456789');
    expect(c.receptor.empresa).toBe(false);expect(c.receptor.nombres).toBe(persona.nombres);
    expect(c.receptor.apellidos).toBe(persona.apellidos);expect(c.receptor.fechaNacimiento).toBe(persona.fechaNacimiento);
    expect(c.validarReceptor()).toBe('');expect(c.idCliente).toBeNull();
    c.cambiarNumero('10987654321');expect(c.receptor.nombres).toBe('');expect(c.receptor.fechaNacimiento).toBeNull();
  });
  it('al cambiar de RUC limpia los datos fiscales anteriores y reconoce persona jurídica',async()=>{
    const {c}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');
    expect(c.receptor.empresa).toBe(true);c.receptor.razonSocial='Receptor anterior';c.receptor.direccionFiscal='Dirección anterior';
    c.cambiarNumero('20987654321');
    expect(c.receptor.razonSocial).toBe('');expect(c.receptor.direccionFiscal).toBe('');
    expect(c.titular()?.persona).toEqual(persona);
  });
  it('volver a boleta recupera el DNI original del titular',async()=>{
    const {c}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');
    c.tipo='BOLETA';c.cambiarTipo();
    expect(c.idCliente).toBe(1);expect(c.receptor).toEqual(persona);
  });
  it('un RUC incompleto no dispara búsquedas ni emisiones',async()=>{
    const {c,clientes,api}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123');c.buscar();c.emitir();
    expect(c.error()).toContain('11');expect(clientes.buscarPorDocumento).not.toHaveBeenCalled();
    expect(clientes.registrar).not.toHaveBeenCalled();expect(api.emitir).not.toHaveBeenCalled();
  });
  it.each([{tipo:1,limite:8,entrada:'12a345678999',esperado:'12345678'},{tipo:2,limite:11,entrada:'20a123456789999',esperado:'20123456789'}])('limita documento $tipo al escribir o pegar',async({tipo,limite,entrada,esperado})=>{
    const {c,f}=await fixture();c.otroReceptor();c.receptor.idTipoDocumento=tipo;f.detectChanges();
    const input=f.nativeElement.querySelector('[aria-describedby="error-documento"]') as HTMLInputElement;
    expect(input.maxLength).toBe(limite);expect(input.inputMode).toBe('numeric');
    input.value=entrada;input.dispatchEvent(new Event('input'));f.detectChanges();await f.whenStable();
    expect(input.value).toBe(esperado);expect(c.receptor.numeroDocumento).toBe(esperado);
  });
  it('muestra y limpia el error debajo de cada campo requerido',async()=>{
    const {c,f,api}=await fixture();c.otroReceptor();c.emitir();f.detectChanges();
    for(const id of ['error-documento','error-nombres','error-apellidos']) {
      const mensaje=f.nativeElement.querySelector('#'+id);
      expect(mensaje).not.toBeNull();expect(mensaje.parentElement.querySelector('input')).not.toBeNull();
    }
    expect(api.emitir).not.toHaveBeenCalled();
    c.cambiarNumero('12345678');c.receptor.nombres='María';c.receptor.apellidos='Quispe';f.detectChanges();
    expect(f.nativeElement.querySelector('#error-documento')).toBeNull();
    expect(f.nativeElement.querySelector('#error-nombres')).toBeNull();
    expect(f.nativeElement.querySelector('#error-apellidos')).toBeNull();
  });
  it('muestra razón social y dirección obligatorias debajo de sus campos',async()=>{
    const {c,f,api}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');c.emitir();f.detectChanges();
    expect(f.nativeElement.querySelector('#error-razon-social')?.textContent).toContain('razón social');
    expect(f.nativeElement.querySelector('#error-direccion-receptor')?.textContent).toContain('dirección fiscal');
    expect(api.emitir).not.toHaveBeenCalled();
  });
  it('presenta los errores de validación del backend junto a los campos',async()=>{
    const {c,f,clientes}=await fixture();c.otroReceptor();c.cambiarNumero('12345678');c.receptor.nombres='María';c.receptor.apellidos='Quispe';
    clientes.registrar.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:400,error:{validationErrors:{nombres:'Revisa los nombres del receptor'}}})));
    c.emitir();f.detectChanges();
    expect(f.nativeElement.querySelector('#error-nombres')?.textContent).toContain('Revisa los nombres');
  });
  it('muestra el correlativo de ocho dígitos en el comprobante',async()=>{
    const {c,f}=await fixture(true);
    c.documento.set({...doc,tipo:'FACTURA',serie:'F002',numero:'3848'});f.detectChanges();
    expect(f.nativeElement.querySelector('.factura-numero').textContent).toContain('F002-00003848');
  });
  it('un fallo al buscar el receptor no se trata como un receptor nuevo',async()=>{
    const {c,clientes,api}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');c.receptor.razonSocial='Empresa';c.receptor.direccionFiscal='Av. Los Andes 123';
    clientes.buscarPorDocumento.mockReturnValue(throwError(()=>new HttpErrorResponse({status:503})));
    c.emitir();expect(clientes.registrar).not.toHaveBeenCalled();expect(api.emitir).not.toHaveBeenCalled();
    expect(c.ocupado()).toBe(false);expect(c.emisionFijada()).toBe(false);
  });
  it('una confirmación incierta conserva también al receptor y bloquea su sustitución',async()=>{
    const {c,api}=await fixture();api.emitir.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503})));
    c.emitir();c.otroReceptor();c.cambiarNumero('20123456789');
    expect(c.idCliente).toBe(1);expect(c.receptor).toEqual(persona);c.emitir();
    expect(api.emitir.mock.calls[1]).toEqual(api.emitir.mock.calls[0]);
  });
  it('reintentar una factura para un receptor recién registrado no repite su registro',async()=>{
    const {c,api,clientes}=await fixture();c.tipo='FACTURA';c.cambiarTipo();c.cambiarNumero('20123456789');
    c.receptor.razonSocial='Empresa nueva';c.receptor.direccionFiscal='Av. Los Andes 123';api.emitir.mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503})));
    c.emitir();expect(c.idCliente).toBe(2);expect(c.emisionFijada()).toBe(true);c.emitir();
    expect(clientes.registrar).toHaveBeenCalledTimes(1);expect(clientes.buscarPorDocumento).toHaveBeenCalledTimes(1);
    expect(api.emitir.mock.calls[1]).toEqual(api.emitir.mock.calls[0]);
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
  it.each(['BOLETA','FACTURA'])('muestra IGV de 18% en %s antes de preparar la emisión',async(tipo)=>{
    const {c,f,api}=await fixture(true);
    c.cuentas.set([{...doc,tipo}]); f.detectChanges();
    const totales=f.nativeElement.querySelector('.comprobante-totales').textContent;
    expect(totales).toContain('IGV (18%)'); expect(totales).toContain('100.00');
    expect(totales).toContain('18.00'); expect(totales).toContain('118.00');
    expect(f.nativeElement.querySelector('input[type="number"][max="100"]')).toBeNull();
    expect(api.preparar).not.toHaveBeenCalled(); expect(api.enviar).not.toHaveBeenCalled();
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
    c.preparar();const envio=api.preparar.mock.calls[0];c.direccion='Otro domicilio';c.preparar();
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
