import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { spawnSync, execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

// Genera archivos locales: no abre conexiones a bases de datos ni ejecuta SQL.
const directorio = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(directorio, '../..');
const repositorio = path.resolve(backend, '..');
const salida = path.join(backend, '.local/datos-prueba-operacion');
const archivoSql = path.join(salida, 'DATOS_PRUEBA_OPERACION.sql');
const archivoAccesos = path.join(salida, 'ACCESOS_PRIVADOS.txt');
const archivoResumen = path.join(salida, 'RESUMEN.json');
const lote = 'SEMILLA-RB-OPERACION-01';

const clientes = [
  ['Lucía Elena','Paredes Salazar','1992-02-17'], ['Marco Antonio','Rojas Cárdenas','1988-08-21'],
  ['Camila Andrea','Mendoza Silva','1997-04-09'], ['Jorge Luis','Benavides Rivas','1983-11-25'],
  ['Daniela Isabel','Vargas Torres','1995-06-12'], ['Andrés Felipe','Quispe Navarro','1990-03-04'],
  ['Natalia Belén','Araya Morales','1994-12-19'], ['Hugo Sebastián','Medina Castro','1986-09-02'],
  ['Paola Fernanda','Gutiérrez Ríos','1993-05-16'], ['Mateo Gabriel','Ramírez Duarte','1991-01-28'],
  ['Elena Victoria','Soto Méndez','1998-07-23'], ['Carlos Eduardo','Llanos Fernández','1987-10-08'],
  ['Gabriela Sofía','Cruz Valdivia','1996-03-22'], ['Renato Alonso','Tapia Cáceres','1990-11-14'],
  ['Fiorella Alejandra','Núñez Delgado','1994-08-06'], ['Nicolás Javier','Huanca López','1989-12-11'],
  ['María Fernanda','Vega Huamán','1993-04-18'], ['Rodrigo Esteban','Salas Ponce','1985-06-30'],
  ['Valeria Antonella','Aguilar Cueva','1999-09-07'], ['José Antonio','Cárdenas Ruelas','1982-02-26'],
  ['Álvaro Enrique','Torres Huerta','1991-07-03'], ['Ana Milagros','Campos Mamani','1995-01-15'],
  ['Luis Fernando','Velásquez Chura','1984-05-29'], ['Carolina Isabel','Bravo Alarcón','1992-10-21'],
  ['Miguel Ángel','Flores Apaza','1988-03-12'], ['Verónica Pilar','Linares Durán','1990-06-09'],
  ['Sergio Alejandro','Castillo Neyra','1996-11-24'], ['Patricia Luciana','Zegarra Muñoz','1987-12-02'],
  ['Daniel Arturo','Escobar Palomino','1993-07-19'], ['Claudia Beatriz','Valencia Ochoa','1981-09-27'],
  ['Ricardo Manuel','Puma Salcedo','1986-04-05'], ['Adriana Marisol','Ramos Pacheco','1998-08-16'],
  ['Emma Valentina','Mendoza Vargas','2022-05-17'], ['Leonardo Gabriel','Huanca López','2012-10-04'],
  ['Juliana Carolina','Restrepo Molina','1991-03-11'], ['Sebastián Ignacio','Fuentes Herrera','1989-08-20'],
  ['Catalina Paz','Riquelme Soto','1996-02-06'], ['Juan Pablo','Gómez Acosta','1985-12-13'],
  ['Mariana Lucía','Castro Pereira','1994-06-24'], ['Tomás Agustín','Fernández Suárez','1992-09-18'],
  ['Valentina Elena','Herrera Muñoz','1993-11-10'], ['Alejandro Nicolás','Morales Vidal','1987-01-29'],
  ['Sofía Camila','Ortiz Romero','1998-04-26'], ['Federico Andrés','Pérez Aguirre','1990-07-31'],
  ['Roxana Elizabeth','Cáceres Huillca','1983-10-16'], ['Óscar Alberto','García Mendoza','1980-03-08'],
  ['Olga Mercedes','Condori Salas','1986-12-05'],
];
const personal = [
  ['Mariana Isabel','Quispe Valdez','1987-05-12','GERENTE','mariana.quispe','2019-03-04'],
  ['Diego Alonso','Huamán Rojas','1994-02-23','RECEPCIONISTA','diego.huaman','2022-07-01'],
  ['Valeria Fernanda','Torres Salas','1997-09-14','RECEPCIONISTA','valeria.torres','2023-01-16'],
  ['Andrés Felipe','Medina Cárdenas','1992-11-07','RECEPCIONISTA','andres.medina','2021-08-02'],
  ['Rosa Elena','Mamani Flores','1989-04-19','LIMPIEZA','rosa.mamani','2020-06-15'],
  ['Patricia Milagros','Condori Apaza','1993-12-03','LIMPIEZA','patricia.condori','2022-02-07'],
  ['Luis Alberto','Paredes Gutiérrez','1985-08-28','MANTENIMIENTO','luis.paredes','2020-10-05'],
  ['Óscar Martín','Rivera Soto','1991-06-11','MANTENIMIENTO','oscar.rivera','2023-04-10'],
];
const mercaderia = [
  ['Bebidas','Agua Cielo sin gas 625 ml',2.5,48,12], ['Bebidas','Agua Cielo sin gas 1.5 L',4.5,24,6],
  ['Bebidas','Agua San Luis sin gas 625 ml',3,36,10], ['Bebidas','Agua San Mateo con gas 600 ml',3.5,18,6],
  ['Bebidas','Agua San Mateo sin gas 2.5 L',6,12,4], ['Bebidas','Inca Kola personal 500 ml',4,30,8],
  ['Bebidas','Coca-Cola original 500 ml',4,30,8], ['Bebidas','Coca-Cola sin azúcar 500 ml',4,12,4],
  ['Bebidas','Sprite personal 500 ml',3.8,16,5], ['Bebidas','Guaraná personal 450 ml',3.5,20,6],
  ['Bebidas','Gatorade frutas tropicales 500 ml',5.5,4,5], ['Bebidas','Frugos durazno 300 ml',3.5,18,5],
  ['Snacks','Papas Lay\'s clásicas 45 g',3.5,24,7], ['Snacks','Lay\'s ondas naturales 40 g',3.5,16,5],
  ['Snacks','Doritos nacho 45 g',3.5,20,6], ['Snacks','Chifles Inka Chips 50 g',3,18,5],
  ['Snacks','Maní salado embolsado 50 g',3,15,5], ['Snacks','Galletas Oreo original 36 g',2.5,30,8],
  ['Snacks','Galletas Casino vainilla 43 g',2,24,6], ['Snacks','Galletas Soda San Jorge 40 g',1.8,24,6],
  ['Snacks','Galletas Club Social original 26 g',2,18,5], ['Snacks','Chocolate Sublime clásico 30 g',3,20,6],
  ['Snacks','Chocolate Princesa 30 g',3,12,4], ['Snacks','Barra de cereal con miel 30 g',3.5,3,5],
  ['Higiene personal','Pasta dental Colgate de viaje 30 g',4.5,16,5], ['Higiene personal','Cepillo dental Oral-B suave',6,12,4],
  ['Higiene personal','Jabón Protex avena 90 g',4,20,6], ['Higiene personal','Jabón Lux suave 80 g',3.5,16,5],
  ['Higiene personal','Shampoo Head & Shoulders 75 ml',7.5,12,4], ['Higiene personal','Shampoo Sedal restauración 75 ml',6.5,12,4],
  ['Higiene personal','Pañuelos Elite bolsillo 10 unidades',2,30,8], ['Higiene personal','Toallitas húmedas 20 unidades',5,15,5],
  ['Higiene personal','Papel higiénico Suave rollo individual',2.5,24,8], ['Higiene personal','Gel de manos 70% de 60 ml',4.5,10,4],
  ['Botiquín','Paracetamol 500 mg tira de 10 tabletas',4,10,3], ['Botiquín','Sales de rehidratación oral sobre',3,8,3],
  ['Botiquín','Curitas adhesivas caja de 10 unidades',5,12,4], ['Botiquín','Gasa estéril paquete de 5 unidades',4,10,3],
  ['Botiquín','Venda elástica de 5 cm',6,8,3], ['Botiquín','Alcohol medicinal 70% de 100 ml',5,12,4],
  ['Accesorios de viaje','Cargador USB de pared 20 W',25,8,3], ['Accesorios de viaje','Cable USB-C de 1 metro',12,10,4],
  ['Accesorios de viaje','Adaptador universal de enchufe',18,0,2], ['Accesorios de viaje','Almohada de viaje para cuello',20,5,2],
  ['Accesorios de viaje','Tapones para oídos par con estuche',4,2,3],
  ['Abarrotes','Nescafé frasco pequeño 50 g',12,8,3], ['Abarrotes','Té McColin\'s caja de 10 filtrantes',5,12,4],
  ['Abarrotes','Azúcar rubia caja de 50 sobres',10,6,2],
  ['Artículos personales','Bolsa reutilizable de tela',5,15,5], ['Artículos personales','Mascarilla KN95 individual',2.5,0,5],
];

function ruc(base) {
  const pesos = [5,4,3,2,7,6,5,4,3,2];
  const n = 11 - [...base].reduce((s,c,i) => s + Number(c) * pesos[i], 0) % 11;
  return base + (n === 10 ? 0 : n === 11 ? 1 : n);
}
const personas = clientes.map(([nombres,apellidos,nacimiento], i) => {
  const id = i + 1;
  const codigoDocumento = id <= 34 ? '1' : id <= 40 ? '4' : id <= 44 ? '7' : '6';
  const documento = codigoDocumento === '1' ? String(93420000 + id)
    : codigoDocumento === '4' ? String(9340000 + id).padStart(9,'0')
    : codigoDocumento === '7' ? 'PA' + String(628400 + id) : ruc('10' + String(93420000 + id));
  return { id, codigoDocumento, documento, nombres, apellidos, nacimiento,
    correo: `huesped.${String(id).padStart(2,'0')}@clientes.rebolsys.example.test`,
    telefono: '947600' + String(id).padStart(3,'0'), esCliente: 1, juridica: 0 };
});
for (const [i,razonSocial,direccion] of [
  [48,'Transportes Altiplano del Sur SAC','Jr. Los Andes 458, Juliaca'],
  [49,'Comercial Las Orquídeas EIRL','Av. Los Álamos 632, Arequipa'],
  [50,'Servicios Logísticos Horizonte SAC','Jr. Independencia 274, Puno'],
]) personas.push({id:i,codigoDocumento:'6',documento:ruc('20934200'+String(i)),razonSocial,direccion,
  correo:`administracion.${i}@clientes.rebolsys.example.test`,telefono:'947600'+String(i).padStart(3,'0'),esCliente:1,juridica:1});
for (const [i,p] of personal.entries()) {
  const [nombres,apellidos,nacimiento,rol,alias,contratacion] = p;
  personas.push({id:101+i,codigoDocumento:'1',documento:String(93430001+i),nombres,apellidos,nacimiento,
    correo:alias+'@personal.example.test',usuario:alias+'@personal.example.test',telefono:'953840'+String(i+1).padStart(3,'0'),
    esCliente:0,juridica:0,rol,contratacion});
}

const controladores = path.join(backend,'src/main/java/pe/com/rebolsysBackend/controller');
const permisos = [...new Set(fs.readdirSync(controladores).filter(f => f.endsWith('.java')).flatMap(f =>
  [...fs.readFileSync(path.join(controladores,f),'utf8').matchAll(/'([A-Z_]+:[A-Z_]+)'/g)].map(m => m[1])))].sort();
const recepcion = permisos.filter(p => /^(RESERVAS|VENTAS|CLIENTES|CAJA|GASTOS|FACTURACION):/.test(p)
  || ['HABITACIONES:VER','PRODUCTOS:VER','PRODUCTOS:VER_MOVIMIENTOS'].includes(p));
const limpieza = ['VER_INICIO','VER_HABITACIONES','VER_INFORMES','INICIAR','EDITAR_INFORME','ENVIAR_INFORME',
  'CANCELAR_BORRADOR','ELIMINAR_INFORME','CARGAR_EVIDENCIA','VER_EVIDENCIA','VER_TICKETS'].map(p => 'LIMPIEZA:'+p);
const mantenimiento = ['VER_INICIO','VER_HABITACIONES','VER_INFORMES','VER_TICKETS','APROBAR_INFORME',
  'CREAR_TICKET','EDITAR_TICKET','ELIMINAR_TICKET','CARGAR_EVIDENCIA','VER_EVIDENCIA'].map(p => 'LIMPIEZA:'+p)
  .concat(['MOBILIARIO:VER','MOBILIARIO:VER_HISTORIAL','MOBILIARIO:CONSERVACION','MOBILIARIO:ASIGNAR']);
for (const p of [...recepcion,...limpieza,...mantenimiento]) if (!permisos.includes(p)) throw Error('Permiso ajeno al código actual: '+p);

const inventario = JSON.parse(fs.readFileSync(path.join(backend,'api/fixtures/habitaciones-real-bolognesi.json'),'utf8'));
const habitaciones = inventario.habitaciones.map(h => {
  const muebles = [];
  for (let i=1;i<=h.camas;i++) muebles.push({tipo:'Cama',nombre:`Cama de ${h.medidaCama} ${i} - Hab. ${h.numero}`});
  muebles.push({tipo:'Televisor',nombre:`Televisor LED 32 pulgadas - Hab. ${h.numero}`},
    {tipo:'Ropero',nombre:`Ropero compacto - Hab. ${h.numero}`},
    {tipo:'Mesa',nombre:`Mesa ${h.tipo.startsWith('Matrimonial') ? 'grande' : 'auxiliar'} - Hab. ${h.numero}`});
  if (['201','303','401','403','502','506'].includes(h.numero)) muebles.push({tipo:'Decoración',nombre:`Florero artificial - Hab. ${h.numero}`});
  const plazas = h.camas * h.plazasPorCama;
  return {numero:h.numero,tipo:h.numero==='303'?'Matrimonial triple con baño':h.tipo,
    capacidad:plazas+1,plazasCama:plazas,maxMenoresSinCama:1,precioPorNoche:h.precioPorNoche,muebles};
});
const reservas = Array.from({length:30}, (_,i) => {
  const id=i+1;
  const cliente=id<=20?id:id<=24?35+id-21:id<=27?41+id-25:id===28?45:id===29?48:49;
  const huesped=id===29?21:id===30?22:cliente;
  return {id,cliente,huesped,forma:['EFECTIVO','YAPE','PLIN','TARJETA'][i%4]};
});
const datos = {
  documentos:[{codigo:'1',nombre:'DNI'},{codigo:'4',nombre:'CARNET EXTRANJERIA'},{codigo:'7',nombre:'PASAPORTE'},{codigo:'6',nombre:'RUC'}],
  roles:[{nombre:'GERENTE',permisos},{nombre:'RECEPCIONISTA',permisos:recepcion},{nombre:'LIMPIEZA',permisos:limpieza},{nombre:'MANTENIMIENTO',permisos:mantenimiento}],
  tiposComprobante:[{codigo:'CUENTA',nombre:'Cuenta interna',sunat:null,serie:'CTA'},
    {codigo:'BOLETA',nombre:'Boleta interna',sunat:'03',serie:'BINT'},{codigo:'FACTURA',nombre:'Factura interna',sunat:'01',serie:'FINT'}],
  movimientosAlmacen:[{codigo:'AJ_ENT',nombre:'Entrada por abastecimiento',signo:1},{codigo:'AJ_SAL',nombre:'Salida por ajuste',signo:-1},
    {codigo:'VENTA',nombre:'Salida por venta',signo:-1},{codigo:'DEV_VENTA',nombre:'Devolución de venta',signo:1},
    {codigo:'ASIG_ITEM',nombre:'Asignación de mobiliario',signo:0},{codigo:'RET_ITEM',nombre:'Retiro de mobiliario',signo:0},{codigo:'EST_ITEM',nombre:'Cambio de condición',signo:0}],
  movimientosCaja:[{nombre:'COBRO_RESERVA',signo:1},{nombre:'COBRO_VENTA',signo:1},{nombre:'INGRESO_MANUAL',signo:1},{nombre:'GASTO_MANUAL',signo:-1}],
  formasPago:['EFECTIVO','YAPE','PLIN','TARJETA'],personas,habitaciones,reservas,
  productos:mercaderia.map(([categoria,nombre,precio,stock,minimo],i) => ({id:i+1,categoria,nombre,
    descripcion:`${nombre}. Presentación individual para venta en la tienda del hospedaje.`,precio,stock,minimo})),
};

function comprobarDatos() {
  if (clientes.length!==47 || personas.filter(p=>p.esCliente).length!==50 || personal.length!==8 || datos.productos.length!==50)
    throw Error('Las cantidades del catálogo no coinciden');
  for (const clave of ['id','documento','correo','telefono']) if(new Set(personas.map(p=>p[clave])).size!==personas.length)
    throw Error('Identidad duplicada: '+clave);
  for (const p of personas) {
    if(!/^\d{9}$/.test(p.telefono)||!p.correo.endsWith('.test'))throw Error('Contacto ficticio inválido');
    if(p.codigoDocumento==='6'&&!/^\d{11}$/.test(p.documento))throw Error('RUC inválido');
  }
  for (const h of habitaciones) if(Buffer.byteLength(h.tipo,'utf8')>30||h.capacidad<h.plazasCama)throw Error('Tipo/capacidad inválidos');
  if(new Set(reservas.map(r=>r.huesped)).size!==30)throw Error('Huésped adulto repetido entre reservas');
}
comprobarDatos();
const plantilla = fs.readFileSync(path.join(directorio,'operacion.template.sql'),'utf8');
const huella = createHash('sha256').update(JSON.stringify(datos)+plantilla).digest('hex');
function generarSql(carga) {
  let trozos=[],actual='';
  for(const c of JSON.stringify(carga)){
    if(Buffer.byteLength(actual+c,'utf8')>3000){trozos.push(actual);actual='';}actual+=c;
  }
  if(actual)trozos.push(actual);
  if(trozos.some(t=>t.includes("~'")))throw Error('Delimitador SQL presente en el catálogo');
  const sql=plantilla.replace('@CARGA_JSON@',trozos.map(t=>`TO_CLOB(q'~${t}~')`).join('\n    || '))
    .replace('-- Plantilla: generar primero DATOS_PRUEBA_OPERACION.sql; no ejecutar este archivo.',
      '-- Archivo generado y listo para ejecutar con F5. Contiene hashes privados de cuentas ficticias.');
  if (/\b(CREATE|ALTER|DROP|TRUNCATE)\s+(TABLE|INDEX|PROCEDURE|FUNCTION|TRIGGER|SEQUENCE|USER)\b/i.test(sql))
    throw Error('La carga no puede incluir DDL');
  return sql;
}
fs.mkdirSync(salida,{recursive:true});
execFileSync('git',['check-ignore','--quiet','--no-index',archivoSql],{cwd:repositorio});
if ([archivoSql,archivoAccesos,archivoResumen].some(f=>fs.existsSync(f))) {
  if (![archivoSql,archivoAccesos,archivoResumen].every(f=>fs.existsSync(f))) throw Error('La salida privada está incompleta; no se sobrescribe.');
  const anterior=JSON.parse(fs.readFileSync(archivoResumen,'utf8'));
  if (anterior.huella!==huella) {
    if(!process.argv.includes('--actualizar-plantilla'))throw Error('La plantilla cambió. Usa --actualizar-plantilla para conservar las credenciales si solo cambió el SQL.');
    const carga=JSON.parse([...fs.readFileSync(archivoSql,'utf8').matchAll(/TO_CLOB\(q'~([\s\S]*?)~'\)/g)].map(m=>m[1]).join(''));
    const catalogo=structuredClone(carga);
    for(const p of catalogo.personas)delete p.hash;
    if(JSON.stringify(catalogo)!==JSON.stringify(datos))throw Error('El catálogo cambió; no se sobrescriben datos ni accesos de un lote anterior.');
    fs.writeFileSync(archivoSql,generarSql(carga),'utf8');
    anterior.huella=huella;
    fs.writeFileSync(archivoResumen,JSON.stringify(anterior,null,2)+'\n','utf8');
  }
  console.log(JSON.stringify({sql:archivoSql,accesosPrivados:archivoAccesos,estado:'Archivos anteriores conservados; no se regeneraron contraseñas.'}));
  process.exit(0);
}

function jar(ruta,nombre) {
  const versiones=fs.readdirSync(ruta).sort((a,b)=>b.localeCompare(a,undefined,{numeric:true}));
  for(const v of versiones){const f=path.join(ruta,v,`${nombre}-${v}.jar`);if(fs.existsSync(f))return f;}
  throw Error('Falta '+nombre+' en Maven; instala las dependencias del backend.');
}
const maven=path.join(os.homedir(),'.m2/repository');
const crypto=jar(path.join(maven,'org/springframework/security/spring-security-crypto'),'spring-security-crypto');
const logging=jar(path.join(maven,'commons-logging/commons-logging'),'commons-logging');
let java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
if (process.platform==='win32'&&!process.env.JAVA_HOME) {
  const jdks=path.join(os.homedir(),'.jdks');
  if(fs.existsSync(jdks))for(const v of fs.readdirSync(jdks).filter(v=>v.includes('21')).sort().reverse()){
    const f=path.join(jdks,v,'bin/java.exe');if(fs.existsSync(f)){java=f;break;}
  }
}
const empleados=personas.filter(p=>p.rol);
const generado=spawnSync(java,['--class-path',[crypto,logging].join(path.delimiter),path.join(directorio,'GenerarCredenciales.java')],
  {input:empleados.map(p=>p.usuario).join('\n')+'\n',encoding:'utf8',maxBuffer:1024*1024});
if(generado.status!==0)throw Error('No se pudieron generar las credenciales con Java y BCrypt. Verifica JAVA_HOME y las dependencias Maven.');
const accesos=generado.stdout.trim().split(/\r?\n/).map(l=>l.split('\t'));
if(accesos.length!==8)throw Error('No se obtuvieron ocho credenciales');
for(const [usuario,clave,hash]of accesos){
  const p=empleados.find(p=>p.usuario===usuario);
  if(!p||clave.length<15||!/^\$2[aby]\$12\$.{53}$/.test(hash))throw Error('Credencial generada inválida');
  p.hash=hash;
}
fs.writeFileSync(archivoSql,generarSql(datos),'utf8');
fs.writeFileSync(archivoAccesos,'ACCESOS PRIVADOS DE DATOS FICTICIOS\nLote: '+lote+'\nCambiar la contraseña al primer ingreso. Vence 24 horas después de ejecutar el SQL.\n\n'
  +'ROL\tUSUARIO\tCONTRASEÑA TEMPORAL\n'+accesos.map(([u,c])=>`${empleados.find(p=>p.usuario===u).rol}\t${u}\t${c}`).join('\n')
  +'\n\nNo compartir este archivo en GitHub. El SQL no restablece contraseñas de cuentas existentes.\n','utf8');
const resumen={lote,huella,clientes:50,empleados:8,usuarios:8,roles:datos.roles.map(r=>r.nombre),productos:50,
  movimientosProductos:52,habitacionesReferencia:20,mueblesSiHabitacionesNuevas:habitaciones.reduce((s,h)=>s+h.muebles.length,0),
  reservas:{finalizadasAntiguas:10,checkoutReciente:5,enCurso:5,checkinPendienteHoy:5,futuras:5},
  passwords:'BCrypt 12; temporales, cambio obligatorio y vencimiento a las 24 h de la carga',ejecutado:false};
fs.writeFileSync(archivoResumen,JSON.stringify(resumen,null,2)+'\n','utf8');
console.log(JSON.stringify({sql:archivoSql,accesosPrivados:archivoAccesos,resumen:archivoResumen,ejecutado:false}));
