-- Datos ficticios para el DDL actual de REBOLSYS; Oracle 19c o posterior.
-- Plantilla: generar primero DATOS_PRUEBA_OPERACION.sql; no ejecutar este archivo.
-- Ejecutar con F5 en SQL Developer, en una conexión dedicada a pruebas.
-- No crea objetos de esquema. Se confirma únicamente al superar las verificaciones.
-- La fecha de referencia se calcula al ejecutar, en America/Lima.
SET DEFINE OFF
SET SERVEROUTPUT ON

DECLARE
  c_lote CONSTANT VARCHAR2(35) := 'SEMILLA-RB-OPERACION-01';
  v_datos CLOB := @CARGA_JSON@;
  v_ahora TIMESTAMP := CAST(SYSTIMESTAMP AT TIME ZONE 'America/Lima' AS TIMESTAMP);
  v_hoy DATE := TRUNC(CAST(SYSTIMESTAMP AT TIME ZONE 'America/Lima' AS DATE));
  TYPE numeros IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  TYPE identificadores IS TABLE OF NUMBER INDEX BY VARCHAR2(100);
  v_perfiles numeros; v_huespedes numeros; v_usuarios numeros; v_cuartos numeros;
  v_roles identificadores; v_roles_nuevos identificadores; v_documentos identificadores;
  v_turnos identificadores;
  v_n NUMBER; v_reservas NUMBER; v_productos NUMBER; v_personas NUMBER; v_cuentas NUMBER;
  v_id NUMBER; v_tipo NUMBER; v_categoria NUMBER; v_habitacion NUMBER; v_item NUMBER;
  v_precio NUMBER; v_capacidad NUMBER; v_camas NUMBER; v_menores NUMBER;
  v_nuevo NUMBER; v_cantidad NUMBER; v_stock NUMBER; v_entrada NUMBER; v_retiro NUMBER; v_asignacion NUMBER;
  v_tipo_cuenta NUMBER; v_tipo_password NUMBER; v_tipo_cobro NUMBER; v_operador NUMBER;
  v_reserva NUMBER; v_detalle NUMBER; v_cuenta NUMBER; v_pago NUMBER; v_forma NUMBER; v_turno NUMBER;
  v_rol NUMBER; v_modulo NUMBER; v_permiso NUMBER; v_cliente NUMBER; v_adulto NUMBER; v_responsable NUMBER;
  v_costo NUMBER(12,2); v_noches NUMBER; v_clave VARCHAR2(64); v_dia VARCHAR2(100); v_estado VARCHAR2(20);
  v_ingreso TIMESTAMP; v_salida TIMESTAMP; v_ingreso_real TIMESTAMP; v_salida_real TIMESTAMP;
  v_registro TIMESTAMP; v_fecha_pago TIMESTAMP;
  v_intentos NUMBER; v_edad NUMBER; v_total_muebles NUMBER := 0;
BEGIN
  SAVEPOINT inicio_semilla;
  IF SYS_CONTEXT('USERENV','CURRENT_SCHEMA') IN ('SYS','SYSTEM')
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> USER THEN
    RAISE_APPLICATION_ERROR(-20101, 'Usa una conexión del propietario del esquema de pruebas REBOLSYS.');
  END IF;

  -- Una segunda ejecución del mismo lote conserva datos y contraseñas existentes.
  SELECT COUNT(*) INTO v_reservas FROM reserva WHERE claveRegistro LIKE c_lote || '-R-%';
  SELECT COUNT(*) INTO v_productos FROM movimiento_inventario WHERE claveOperacion LIKE c_lote || '-PROD-%';
  SELECT COUNT(*) INTO v_personas FROM perfil p JOIN tipo_documento t ON t.idTipoDocumento=p.idTipoDocumento
  JOIN JSON_TABLE(v_datos, '$.personas[*]' COLUMNS (
    codigo VARCHAR2(2) PATH '$.codigoDocumento', documento VARCHAR2(50) PATH '$.documento'
  )) j ON j.codigo=t.codigoSunat AND j.documento=p.numeroDocumento;
  SELECT COUNT(*) INTO v_cuentas FROM usuario u JOIN JSON_TABLE(v_datos, '$.personas[*]' COLUMNS (
    login VARCHAR2(50) PATH '$.usuario'
  )) j ON LOWER(j.login)=LOWER(u.username);
  IF v_reservas=30 AND v_productos=50 AND v_personas=58 AND v_cuentas=8 THEN
    DBMS_OUTPUT.PUT_LINE('El lote ya está completo; no se modificó ningún registro.');
    RETURN;
  END IF;
  IF v_reservas+v_productos+v_personas+v_cuentas>0 THEN
    RAISE_APPLICATION_ERROR(-20102, 'Existe una carga parcial o una identidad coincidente. Revisa el lote antes de repetir.');
  END IF;

  -- Catálogos mínimos. Los roles y permisos previamente configurados se conservan.
  FOR d IN (SELECT * FROM JSON_TABLE(v_datos,'$.documentos[*]' COLUMNS (
    codigo VARCHAR2(2) PATH '$.codigo', nombre VARCHAR2(30) PATH '$.nombre'
  ))) LOOP
    INSERT INTO tipo_documento(nombreDocumento,codigoSunat)
    SELECT d.nombre,d.codigo FROM dual WHERE NOT EXISTS (SELECT 1 FROM tipo_documento WHERE codigoSunat=d.codigo);
    SELECT MIN(idTipoDocumento) INTO v_documentos(d.codigo) FROM tipo_documento WHERE codigoSunat=d.codigo;
  END LOOP;
  INSERT INTO tipo_credencial(nombreCredencial) SELECT 'PASSWORD' FROM dual
  WHERE NOT EXISTS (SELECT 1 FROM tipo_credencial WHERE nombreCredencial='PASSWORD');
  SELECT idTipoCredencial INTO v_tipo_password FROM tipo_credencial WHERE nombreCredencial='PASSWORD';

  FOR r IN (SELECT * FROM JSON_TABLE(v_datos,'$.roles[*]' COLUMNS (
    nombre VARCHAR2(30) PATH '$.nombre', permisos VARCHAR2(4000) FORMAT JSON PATH '$.permisos'
  ))) LOOP
    SELECT COUNT(*) INTO v_n FROM rol WHERE nombreRol=r.nombre;
    IF v_n=0 THEN
      INSERT INTO rol(nombreRol) VALUES(r.nombre) RETURNING idRol INTO v_rol;
      v_roles_nuevos(r.nombre):=1;
    ELSE
      SELECT idRol INTO v_rol FROM rol WHERE nombreRol=r.nombre;
    END IF;
    v_roles(r.nombre):=v_rol;
    IF v_roles_nuevos.EXISTS(r.nombre) THEN
      FOR p IN (SELECT codigo FROM JSON_TABLE(r.permisos,'$[*]' COLUMNS(codigo VARCHAR2(100) PATH '$'))) LOOP
        INSERT INTO modulo(nombreModulo)
        SELECT SUBSTR(p.codigo,1,INSTR(p.codigo,':')-1) FROM dual
        WHERE NOT EXISTS (SELECT 1 FROM modulo WHERE nombreModulo=SUBSTR(p.codigo,1,INSTR(p.codigo,':')-1));
        SELECT idModulo INTO v_modulo FROM modulo WHERE nombreModulo=SUBSTR(p.codigo,1,INSTR(p.codigo,':')-1);
        INSERT INTO permiso(idModulo,accion)
        SELECT v_modulo,SUBSTR(p.codigo,INSTR(p.codigo,':')+1) FROM dual
        WHERE NOT EXISTS (SELECT 1 FROM permiso WHERE idModulo=v_modulo AND accion=SUBSTR(p.codigo,INSTR(p.codigo,':')+1));
        SELECT idPermiso INTO v_permiso FROM permiso WHERE idModulo=v_modulo AND accion=SUBSTR(p.codigo,INSTR(p.codigo,':')+1);
        INSERT INTO rol_permiso(idRol,idPermiso) VALUES(v_rol,v_permiso);
      END LOOP;
    END IF;
  END LOOP;

  FOR t IN (SELECT * FROM JSON_TABLE(v_datos,'$.tiposComprobante[*]' COLUMNS (
    codigo VARCHAR2(20) PATH '$.codigo', nombre VARCHAR2(30) PATH '$.nombre', sunat VARCHAR2(2) PATH '$.sunat', serie VARCHAR2(10) PATH '$.serie'
  ))) LOOP
    INSERT INTO tipo_comprobante(codigo,nombreComprobante,codigoSunat)
    SELECT t.codigo,t.nombre,t.sunat FROM dual WHERE NOT EXISTS(SELECT 1 FROM tipo_comprobante WHERE codigo=t.codigo);
    SELECT idTipoComprobante INTO v_tipo FROM tipo_comprobante WHERE codigo=t.codigo;
    INSERT INTO serie_comprobante(idTipoComprobante,serie,siguienteNumero)
    SELECT v_tipo,t.serie,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM serie_comprobante WHERE idTipoComprobante=v_tipo AND serie=t.serie);
  END LOOP;
  SELECT idTipoComprobante INTO v_tipo_cuenta FROM tipo_comprobante WHERE codigo='CUENTA';
  FOR m IN (SELECT * FROM JSON_TABLE(v_datos,'$.movimientosAlmacen[*]' COLUMNS (
    codigo VARCHAR2(10) PATH '$.codigo', nombre VARCHAR2(50) PATH '$.nombre', signo NUMBER PATH '$.signo'
  ))) LOOP
    INSERT INTO tipo_movimiento_almacen(codigo,nombreMovimiento,signo)
    SELECT m.codigo,m.nombre,m.signo FROM dual WHERE NOT EXISTS(SELECT 1 FROM tipo_movimiento_almacen WHERE codigo=m.codigo);
    SELECT COUNT(*) INTO v_n FROM tipo_movimiento_almacen WHERE codigo=m.codigo AND signo=m.signo;
    IF v_n<>1 THEN RAISE_APPLICATION_ERROR(-20103,'Revisa el signo de '||m.codigo); END IF;
  END LOOP;
  SELECT idTipoMovimientoAlmacen INTO v_entrada FROM tipo_movimiento_almacen WHERE codigo='AJ_ENT';
  SELECT idTipoMovimientoAlmacen INTO v_retiro FROM tipo_movimiento_almacen WHERE codigo='AJ_SAL';
  SELECT idTipoMovimientoAlmacen INTO v_asignacion FROM tipo_movimiento_almacen WHERE codigo='ASIG_ITEM';
  FOR m IN (SELECT * FROM JSON_TABLE(v_datos,'$.movimientosCaja[*]' COLUMNS (
    nombre VARCHAR2(30) PATH '$.nombre', signo NUMBER PATH '$.signo'
  ))) LOOP
    INSERT INTO tipo_movimiento_caja(nombreMovimiento,signo)
    SELECT m.nombre,m.signo FROM dual WHERE NOT EXISTS(SELECT 1 FROM tipo_movimiento_caja WHERE nombreMovimiento=m.nombre);
  END LOOP;
  SELECT idTipoMovimiento INTO v_tipo_cobro FROM tipo_movimiento_caja WHERE nombreMovimiento='COBRO_RESERVA' AND signo=1;
  FOR f IN (SELECT nombre FROM JSON_TABLE(v_datos,'$.formasPago[*]' COLUMNS(nombre VARCHAR2(30) PATH '$'))) LOOP
    INSERT INTO forma_pago(nombrePago) SELECT f.nombre FROM dual WHERE NOT EXISTS(SELECT 1 FROM forma_pago WHERE UPPER(TRIM(nombrePago))=f.nombre);
  END LOOP;

  -- Identidades, clientes y personal. Se omiten todos los IDENTITY en los INSERT.
  FOR p IN (SELECT * FROM JSON_TABLE(v_datos,'$.personas[*]' COLUMNS (
    idLocal NUMBER PATH '$.id', codigo VARCHAR2(2) PATH '$.codigoDocumento', documento VARCHAR2(50) PATH '$.documento',
    correo VARCHAR2(100) PATH '$.correo', telefono VARCHAR2(15) PATH '$.telefono', esCliente NUMBER PATH '$.esCliente',
    juridica NUMBER PATH '$.juridica', nombres VARCHAR2(100) PATH '$.nombres', apellidos VARCHAR2(100) PATH '$.apellidos',
    nacimiento VARCHAR2(10) PATH '$.nacimiento', razonSocial VARCHAR2(250) PATH '$.razonSocial', direccion VARCHAR2(200) PATH '$.direccion',
    login VARCHAR2(50) PATH '$.usuario', rol VARCHAR2(30) PATH '$.rol', hash VARCHAR2(255) PATH '$.hash', contratacion VARCHAR2(10) PATH '$.contratacion'
  )) ORDER BY idLocal) LOOP
    INSERT INTO perfil(idTipoDocumento,numeroDocumento,correo,telefono,esCliente)
    VALUES(v_documentos(p.codigo),p.documento,p.correo,p.telefono,p.esCliente) RETURNING idPerfil INTO v_id;
    v_perfiles(p.idLocal):=v_id;
    IF p.juridica=1 THEN
      INSERT INTO persona_juridica(idPerfil,razonSocial,direccionFiscal) VALUES(v_id,p.razonSocial,p.direccion);
    ELSE
      INSERT INTO persona_natural(idPerfil,nombres,apellidos,fechaNacimiento)
      VALUES(v_id,p.nombres,p.apellidos,TO_DATE(p.nacimiento,'YYYY-MM-DD'));
      IF p.esCliente=1 THEN
        INSERT INTO huesped(idPerfil) VALUES(v_id) RETURNING idHuesped INTO v_id;
        v_huespedes(p.idLocal):=v_id;
      END IF;
    END IF;
    IF p.rol IS NOT NULL THEN
      INSERT INTO empleado(idPerfil,fechaContratacion) VALUES(v_perfiles(p.idLocal),TO_DATE(p.contratacion,'YYYY-MM-DD'))
      RETURNING idEmpleado INTO v_id;
      INSERT INTO usuario(idEmpleado,username,estadoUsuario) VALUES(v_id,p.login,'ACTIVO') RETURNING idUsuario INTO v_id;
      v_usuarios(p.idLocal):=v_id;
      INSERT INTO usuario_rol(idUsuario,idRol) VALUES(v_id,v_roles(p.rol));
      INSERT INTO usuario_credencial(idUsuario,idTipoCredencial,valorHash,credencialTemporal,intentosFallidos,fechaBloqueo,fechaVencimiento)
      VALUES(v_id,v_tipo_password,p.hash,1,0,NULL,v_ahora+INTERVAL '1' DAY);
    END IF;
  END LOOP;

  -- Las habitaciones anteriores se completan solo cuando faltan; no se cambian tarifas existentes.
  FOR h IN (SELECT * FROM JSON_TABLE(v_datos,'$.habitaciones[*]' COLUMNS (
    numero VARCHAR2(10) PATH '$.numero', tipo VARCHAR2(30) PATH '$.tipo', capacidad NUMBER PATH '$.capacidad',
    camas NUMBER PATH '$.plazasCama', menores NUMBER PATH '$.maxMenoresSinCama', tarifa NUMBER PATH '$.precioPorNoche',
    muebles VARCHAR2(4000) FORMAT JSON PATH '$.muebles'
  )) ORDER BY numero) LOOP
    SELECT COUNT(*) INTO v_n FROM habitacion WHERE numeroCuarto=h.numero;
    IF v_n=0 THEN
      INSERT INTO tipo_habitacion(nombreTipo,capacidadMax,precioBase)
      SELECT h.tipo,h.capacidad,h.tarifa FROM dual WHERE NOT EXISTS(SELECT 1 FROM tipo_habitacion WHERE nombreTipo=h.tipo);
      SELECT idTipoHabitacion,capacidadMax INTO v_tipo,v_capacidad FROM tipo_habitacion WHERE nombreTipo=h.tipo;
      IF v_capacidad<h.capacidad THEN RAISE_APPLICATION_ERROR(-20104,'Revisa la capacidad del tipo '||h.tipo); END IF;
      INSERT INTO habitacion(numeroCuarto,idTipo,estadoHabitacion,capacidadPersonas,plazasCama,maxMenoresSinCama,precioPorNoche)
      VALUES(h.numero,v_tipo,'DISPONIBLE',h.capacidad,h.camas,h.menores,h.tarifa) RETURNING idHabitacion INTO v_habitacion;
      FOR m IN (SELECT * FROM JSON_TABLE(h.muebles,'$[*]' COLUMNS (
        nombre VARCHAR2(100) PATH '$.nombre', tipo VARCHAR2(30) PATH '$.tipo'
      ))) LOOP
        INSERT INTO tipo_item(nombreItem) SELECT m.tipo FROM dual WHERE NOT EXISTS(SELECT 1 FROM tipo_item WHERE nombreItem=m.tipo);
        SELECT idTipoItem INTO v_tipo FROM tipo_item WHERE nombreItem=m.tipo;
        INSERT INTO item(nombreItem,idTipoItem,idHabitacion,estadoItem,condicionItem)
        VALUES(m.nombre,v_tipo,v_habitacion,'ACTIVO','OPTIMO') RETURNING idItem INTO v_item;
        INSERT INTO movimiento_inventario(idItem,idHabitacion,idTipoMovimientoAlmacen,cantidad,idUsuario,condicionItem,observacion,fechaMovimiento,claveOperacion)
        VALUES(v_item,v_habitacion,v_asignacion,1,v_usuarios(101),'OPTIMO','Asignación inicial a la habitación '||h.numero,
          v_ahora-INTERVAL '140' DAY(3),c_lote||'-MUEBLE-'||v_item);
        v_total_muebles:=v_total_muebles+1;
      END LOOP;
    END IF;
  END LOOP;

  -- 50 productos y sus movimientos iniciales; dos referencias agotadas mantienen entradas y salidas trazables.
  FOR p IN (SELECT * FROM JSON_TABLE(v_datos,'$.productos[*]' COLUMNS (
    idLocal NUMBER PATH '$.id', categoria VARCHAR2(30) PATH '$.categoria', nombre VARCHAR2(100) PATH '$.nombre',
    descripcion VARCHAR2(500) PATH '$.descripcion', precio NUMBER PATH '$.precio', stock NUMBER PATH '$.stock', minimo NUMBER PATH '$.minimo'
  )) ORDER BY idLocal) LOOP
    INSERT INTO categoria(nombreCategoria) SELECT p.categoria FROM dual WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE nombreCategoria=p.categoria);
    SELECT idCategoria INTO v_categoria FROM categoria WHERE nombreCategoria=p.categoria;
    INSERT INTO producto(nombreProducto,precioActual,stock,stockMinimo,idCategoria,estadoProducto,descripcion)
    VALUES(p.nombre,p.precio,p.stock,p.minimo,v_categoria,'ACTIVO',p.descripcion) RETURNING idProducto INTO v_id;
    v_cantidad:=GREATEST(1,p.stock);
    INSERT INTO movimiento_inventario(idProducto,idTipoMovimientoAlmacen,cantidad,stockResultante,idUsuario,observacion,fechaMovimiento,claveOperacion)
    VALUES(v_id,v_entrada,v_cantidad,v_cantidad,v_usuarios(101),'Recepción de mercadería para la tienda',v_ahora-INTERVAL '2' DAY,
      c_lote||'-PROD-'||TO_CHAR(p.idLocal,'FM000'));
    IF p.stock=0 THEN
      INSERT INTO movimiento_inventario(idProducto,idTipoMovimientoAlmacen,cantidad,stockResultante,idUsuario,observacion,fechaMovimiento,claveOperacion)
      VALUES(v_id,v_retiro,1,0,v_usuarios(101),'Retiro de la última unidad de exhibición',v_ahora-INTERVAL '1' DAY,
        c_lote||'-RET-'||TO_CHAR(p.idLocal,'FM000'));
    END IF;
  END LOOP;

  -- Quince habitaciones físicamente libres para las salidas recientes, estancias y check-in de hoy.
  v_n:=0;
  FOR h IN (
    SELECT h.idHabitacion FROM habitacion h JOIN tipo_habitacion t ON t.idTipoHabitacion=h.idTipo
    WHERE h.estadoHabitacion='DISPONIBLE' AND COALESCE(h.precioPorNoche,t.precioBase)>0
      AND NOT EXISTS(SELECT 1 FROM detalle_reserva d WHERE d.idHabitacion=h.idHabitacion AND (
        d.estadoDetalle='EN_CURSO' OR (d.estadoDetalle='CONFIRMADA' AND d.fechaSalida>v_ahora-INTERVAL '2' DAY AND d.fechaIngreso<v_ahora+INTERVAL '15' DAY)))
      AND NOT EXISTS(SELECT 1 FROM informe_revision i WHERE i.idHabitacion=h.idHabitacion AND i.estadoInforme IN ('BORRADOR','POR_APROBAR'))
    ORDER BY h.numeroCuarto FOR UPDATE OF h.estadoHabitacion
  ) LOOP
    v_n:=v_n+1; v_cuartos(v_n):=h.idHabitacion; EXIT WHEN v_n=15;
  END LOOP;
  IF v_n<15 THEN RAISE_APPLICATION_ERROR(-20105,'Se necesitan 15 habitaciones disponibles sin reservas activas coincidentes. La carga se revierte.'); END IF;

  FOR caso IN (SELECT * FROM JSON_TABLE(v_datos,'$.reservas[*]' COLUMNS (
    idLocal NUMBER PATH '$.id', cliente NUMBER PATH '$.cliente', huesped NUMBER PATH '$.huesped', forma VARCHAR2(30) PATH '$.forma'
  )) ORDER BY idLocal) LOOP
    v_ingreso_real:=NULL; v_salida_real:=NULL;
    IF caso.idLocal<=10 THEN
      v_habitacion:=v_cuartos(caso.idLocal);
      v_ingreso:=CAST(v_hoy-(30+4*caso.idLocal)+14/24 AS TIMESTAMP);
      v_salida:=CAST(v_hoy-(30+4*caso.idLocal)+1+MOD(caso.idLocal,3)+12/24 AS TIMESTAMP);
      v_ingreso_real:=v_ingreso+INTERVAL '20' MINUTE; v_salida_real:=v_salida-INTERVAL '15' MINUTE;
      v_estado:='FINALIZADA';
    ELSIF caso.idLocal<=15 THEN
      v_habitacion:=v_cuartos(caso.idLocal-10);
      v_ingreso:=CAST(v_hoy-(1+MOD(caso.idLocal,3))+14/24 AS TIMESTAMP);
      v_salida:=CAST(v_hoy+12/24 AS TIMESTAMP);
      v_ingreso_real:=v_ingreso+INTERVAL '20' MINUTE;
      v_salida_real:=v_ahora-NUMTODSINTERVAL(10+20*(15-caso.idLocal),'MINUTE');
      v_estado:='FINALIZADA';
    ELSIF caso.idLocal<=20 THEN
      v_habitacion:=v_cuartos(caso.idLocal-10);
      v_ingreso:=CAST(v_hoy-1+14/24 AS TIMESTAMP);
      v_salida:=CAST(v_hoy+1+MOD(caso.idLocal,2)+12/24 AS TIMESTAMP);
      v_ingreso_real:=v_ingreso+INTERVAL '20' MINUTE; v_estado:='EN_CURSO';
    ELSIF caso.idLocal<=25 THEN
      v_habitacion:=v_cuartos(caso.idLocal-10);
      v_ingreso:=v_ahora-INTERVAL '10' MINUTE;
      v_salida:=CAST(v_hoy+1+MOD(caso.idLocal,3)+12/24 AS TIMESTAMP); v_estado:='CONFIRMADA';
    ELSE
      v_habitacion:=v_cuartos(caso.idLocal-15);
      v_ingreso:=CAST(v_hoy+7+14/24 AS TIMESTAMP);
      v_salida:=CAST(v_hoy+7+1+MOD(caso.idLocal,3)+12/24 AS TIMESTAMP); v_estado:='CONFIRMADA';
    END IF;
    -- Evita solapar incluso reservas históricas ya existentes.
    v_intentos:=0;
    LOOP
      SELECT COUNT(*) INTO v_n FROM detalle_reserva WHERE idHabitacion=v_habitacion AND estadoDetalle<>'CANCELADA'
        AND fechaIngreso<v_salida AND fechaSalida>v_ingreso;
      EXIT WHEN v_n=0;
      IF caso.idLocal>10 OR v_intentos>=120 THEN RAISE_APPLICATION_ERROR(-20106,'Existe una reserva coincidente; no se sobrescriben estancias.'); END IF;
      v_ingreso:=v_ingreso-INTERVAL '3' DAY; v_salida:=v_salida-INTERVAL '3' DAY;
      v_ingreso_real:=v_ingreso_real-INTERVAL '3' DAY; v_salida_real:=v_salida_real-INTERVAL '3' DAY;
      v_intentos:=v_intentos+1;
    END LOOP;
    IF caso.idLocal<=20 THEN v_registro:=CAST(TRUNC(CAST(v_ingreso AS DATE))-1+10/24 AS TIMESTAMP);
    ELSE v_registro:=CAST(v_hoy-1+10/24 AS TIMESTAMP); END IF;
    v_registro:=v_registro+NUMTODSINTERVAL(MOD(caso.idLocal,20),'MINUTE');
    v_fecha_pago:=v_registro+INTERVAL '5' MINUTE;
    v_noches:=CEIL(CAST(v_salida AS DATE)-CAST(v_ingreso AS DATE));
    SELECT COALESCE(h.precioPorNoche,t.precioBase),h.capacidadPersonas,h.plazasCama,h.maxMenoresSinCama
    INTO v_precio,v_capacidad,v_camas,v_menores FROM habitacion h JOIN tipo_habitacion t ON t.idTipoHabitacion=h.idTipo WHERE h.idHabitacion=v_habitacion;
    v_costo:=v_noches*v_precio; v_clave:=c_lote||'-R-'||TO_CHAR(caso.idLocal,'FM000');
    v_operador:=v_usuarios(102+MOD(caso.idLocal,3));
    v_cliente:=v_perfiles(caso.cliente); v_adulto:=v_huespedes(caso.huesped);
    INSERT INTO reserva(idPerfil,idUsuario,fechaRegistro,claveRegistro,firmaSolicitud)
    VALUES(v_cliente,v_operador,v_registro,v_clave,LOWER(RAWTOHEX(STANDARD_HASH(v_clave,'SHA256')))) RETURNING idReserva INTO v_reserva;
    INSERT INTO detalle_reserva(idReserva,idHabitacion,fechaIngreso,fechaSalida,fechaIngresoReal,fechaSalidaReal,costoEstadia,estadoDetalle)
    VALUES(v_reserva,v_habitacion,v_ingreso,v_salida,v_ingreso_real,v_salida_real,v_costo,v_estado) RETURNING idDetalleReserva INTO v_detalle;
    INSERT INTO reserva_huesped(idDetalleReserva,idHuesped,ocupaPlazaCama)
    VALUES(v_detalle,v_adulto,1) RETURNING idReservaHuesped INTO v_responsable;
    IF caso.idLocal=21 AND v_capacidad>=2 AND v_menores>=1 THEN
      SELECT TRUNC(MONTHS_BETWEEN(CAST(v_ingreso AS DATE),fechaNacimiento)/12) INTO v_edad
      FROM persona_natural WHERE idPerfil=v_perfiles(33);
      IF v_edad<7 OR (v_edad<18 AND v_camas>=2) THEN
        INSERT INTO reserva_huesped(idDetalleReserva,idHuesped,idReservaHuespedResponsable,parentescoResponsable,ocupaPlazaCama)
        VALUES(v_detalle,v_huespedes(33),v_responsable,'MADRE',CASE WHEN v_edad<7 THEN 0 ELSE 1 END);
      END IF;
    ELSIF caso.idLocal=16 AND v_capacidad>=2 AND v_camas>=2 THEN
      SELECT TRUNC(MONTHS_BETWEEN(CAST(v_ingreso AS DATE),fechaNacimiento)/12) INTO v_edad
      FROM persona_natural WHERE idPerfil=v_perfiles(34);
      IF v_edad BETWEEN 7 AND 17 THEN
        INSERT INTO reserva_huesped(idDetalleReserva,idHuesped,idReservaHuespedResponsable,parentescoResponsable,ocupaPlazaCama)
        VALUES(v_detalle,v_huespedes(34),v_responsable,'PADRE',1);
      END IF;
    END IF;

    -- Turnos históricos de 08:00 a 18:00, agrupados por fecha y recepcionista.
    v_dia:=TO_CHAR(v_fecha_pago,'YYYYMMDD')||'-'||v_operador;
    IF NOT v_turnos.EXISTS(v_dia) THEN
      INSERT INTO turno_caja(idUsuario,fechaApertura,fechaCierre,montoBase,estadoSesion)
      VALUES(v_operador,CAST(TRUNC(CAST(v_fecha_pago AS DATE))+8/24 AS TIMESTAMP),
        CAST(TRUNC(CAST(v_fecha_pago AS DATE))+18/24 AS TIMESTAMP),100,0) RETURNING idTurno INTO v_turno;
      v_turnos(v_dia):=v_turno;
    ELSE v_turno:=v_turnos(v_dia); END IF;
    INSERT INTO comprobante(idReserva,idPerfilReceptor,idTipoComprobante,idTurno,serie,subtotal,igv,totalPagar,estadoComprobante,claveIdempotencia)
    VALUES(v_reserva,v_cliente,v_tipo_cuenta,v_turno,'CTA',v_costo,0,v_costo,'BORRADOR','CUENTA-R-'||v_reserva) RETURNING idComprobante INTO v_cuenta;
    INSERT INTO comprobante_detalle(idComprobante,idDetalleReserva,numeroLinea,descripcion,cantidad,valorUnitario,igv)
    SELECT v_cuenta,v_detalle,1,'Alojamiento habitación '||numeroCuarto,1,v_costo,0 FROM habitacion WHERE idHabitacion=v_habitacion;
    SELECT MIN(idFormaPago) INTO v_forma FROM forma_pago WHERE UPPER(TRIM(nombrePago))=caso.forma;
    INSERT INTO pago(idComprobante,idFormaPago,idUsuario,idTurno,monto,numeroOperacion,fechaPago,verificado,claveIdempotencia)
    VALUES(v_cuenta,v_forma,v_operador,v_turno,v_costo,
      CASE WHEN caso.forma='EFECTIVO' THEN NULL ELSE TO_CHAR(v_fecha_pago,'YYYYMMDD')||TO_CHAR(caso.idLocal,'FM000000') END,
      v_fecha_pago,1,'COBRO-INMEDIATO-'||v_cuenta) RETURNING idPago INTO v_pago;
    INSERT INTO flujo_caja(idTurno,idTipoMovimiento,idPago,concepto,monto,fechaHora,claveRegistro)
    VALUES(v_turno,v_tipo_cobro,v_pago,'Pago de alojamiento - reserva R'||v_reserva,v_costo,v_fecha_pago,c_lote||'-CAJA-'||TO_CHAR(caso.idLocal,'FM000'));
    IF caso.idLocal BETWEEN 11 AND 15 THEN
      UPDATE habitacion SET estadoHabitacion='POR_LIMPIAR' WHERE idHabitacion=v_habitacion AND estadoHabitacion='DISPONIBLE';
      IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20107,'La habitación cambió de estado durante la carga.'); END IF;
    ELSIF caso.idLocal BETWEEN 16 AND 20 THEN
      UPDATE habitacion SET estadoHabitacion='OCUPADO' WHERE idHabitacion=v_habitacion AND estadoHabitacion='DISPONIBLE';
      IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20107,'La habitación cambió de estado durante la carga.'); END IF;
    END IF;
  END LOOP;

  -- Arqueos de los turnos cerrados: cobros por medio más el fondo inicial en efectivo.
  v_dia:=v_turnos.FIRST;
  WHILE v_dia IS NOT NULL LOOP
    v_turno:=v_turnos(v_dia);
    FOR f IN (SELECT MIN(idFormaPago) idFormaPago,UPPER(TRIM(nombrePago)) nombrePago FROM forma_pago
      WHERE UPPER(TRIM(nombrePago)) IN ('EFECTIVO','YAPE','PLIN','TARJETA') GROUP BY UPPER(TRIM(nombrePago))) LOOP
      SELECT COALESCE(SUM(monto),0)+CASE WHEN UPPER(TRIM(f.nombrePago))='EFECTIVO' THEN 100 ELSE 0 END
      INTO v_costo FROM pago WHERE idTurno=v_turno AND idFormaPago=f.idFormaPago AND verificado=1;
      INSERT INTO arqueo_caja(idTurno,idFormaPago,montoCalculado,montoDeclarado) VALUES(v_turno,f.idFormaPago,v_costo,v_costo);
    END LOOP;
    v_dia:=v_turnos.NEXT(v_dia);
  END LOOP;

  SELECT COUNT(*) INTO v_n FROM comprobante c JOIN reserva r ON r.idReserva=c.idReserva
  WHERE r.claveRegistro LIKE c_lote||'-R-%' AND c.totalPagar<>(SELECT COALESCE(SUM(p.monto),0) FROM pago p WHERE p.idComprobante=c.idComprobante AND p.verificado=1);
  IF v_n<>0 THEN RAISE_APPLICATION_ERROR(-20108,'Una reserva no quedó completamente pagada.'); END IF;
  SELECT COUNT(*) INTO v_n FROM producto p JOIN movimiento_inventario inicial ON inicial.idProducto=p.idProducto
  WHERE inicial.claveOperacion LIKE c_lote||'-PROD-%' AND p.stock<>(SELECT SUM(m.cantidad*t.signo) FROM movimiento_inventario m
    JOIN tipo_movimiento_almacen t ON t.idTipoMovimientoAlmacen=m.idTipoMovimientoAlmacen WHERE m.idProducto=p.idProducto);
  IF v_n<>0 THEN RAISE_APPLICATION_ERROR(-20109,'El stock no coincide con sus movimientos.'); END IF;
  SELECT COUNT(*) INTO v_n FROM detalle_reserva d JOIN reserva r ON r.idReserva=d.idReserva WHERE r.claveRegistro LIKE c_lote||'-R-%';
  IF v_n<>30 THEN RAISE_APPLICATION_ERROR(-20110,'La cantidad de estancias no coincide con el lote.'); END IF;
  DBMS_OUTPUT.PUT_LINE('Carga completa: 30 reservas pagadas, 50 clientes, 50 productos y 8 empleados con usuario.');
  DBMS_OUTPUT.PUT_LINE('Reservas: 10 finalizadas antiguas, 5 checkout recientes, 5 en curso, 5 check-in pendiente hoy y 5 futuras.');
  DBMS_OUTPUT.PUT_LINE('Mobiliario básico agregado a habitaciones nuevas: '||v_total_muebles);
  DBMS_OUTPUT.PUT_LINE('Las contraseñas iniciales deben cambiarse al entrar; vencen a las 24 horas de esta carga.');
  COMMIT;
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO inicio_semilla;
  RAISE;
END;
/

-- El filtro separa este lote de las reservas que ya existían.
SELECT d.estadoDetalle,COUNT(*) cantidad,SUM(d.costoEstadia) alojamiento
FROM reserva r JOIN detalle_reserva d ON d.idReserva=r.idReserva
WHERE r.claveRegistro LIKE 'SEMILLA-RB-OPERACION-01-R-%'
GROUP BY d.estadoDetalle ORDER BY d.estadoDetalle;
