# REBOLSYS · Frontend SPA

Aplicación web Angular para la operación del hospedaje Real Bolognesi. Consume la API de REBOLSYS para gestionar reservas, estadías, ventas, caja, inventario, limpieza y reportes.

| Dato | Valor |
|---|---|
| Versión del paquete | `0.0.0` |
| Curso | Lenguaje de Programación II · EP Ingeniería de Sistemas · UPeU · 2026-II |
| Docente | Benjamin David Reyna Barreto |
| Integrantes | Yeremin Brian Ccapa Chacca, Esther Mariana Landa Torres, Brandon Erly Pacho Mamani, James Tequen Oncoy y Sebastian Gamarra Tarazona |
| Backend local | `http://localhost:8080/api/v1` |
| API y configuración local | [Backend de REBOLSYS](../rebolsysBackend/README.md) |

## Contenido

- [Funciones](#funciones)
- [Capturas](#capturas)
- [Tecnologías y estructura](#tecnologías-y-estructura)
- [Requisitos y ejecución local](#requisitos-y-ejecución-local)
- [Rutas y seguridad](#rutas-y-seguridad)
- [Compilación y despliegue](#compilación-y-despliegue)
- [Pruebas y calidad](#pruebas-y-calidad)
- [Versión e historial](#versión-e-historial)

## Funciones

- Panel de habitaciones y calendario de disponibilidad.
- Reservas con titular natural o empresa, varias habitaciones, ocupantes, pagos y cuenta de estadía.
- Ventas de productos, cobro y comprobantes internos.
- Configuración de habitaciones, mobiliario, productos y categorías.
- Caja por turnos, gastos, reportes e informes de limpieza con incidencias y tickets de mantenimiento.
- Comprobantes imprimibles en A4; los reportes admiten impresión A4 horizontal o ticket de 80 mm mediante el diálogo del navegador.
- Interfaz adaptable a distintos anchos de pantalla. Las acciones visibles dependen de la sesión y los permisos del usuario.

## Capturas

Capturas pendientes de incorporar: inicio de sesión, panel de habitaciones, registro de reserva y registro de venta.

## Tecnologías y estructura

| Tecnología | Uso |
|---|---|
| Angular 22.2 | Aplicación de una sola página y enrutamiento |
| TypeScript 6.0.2 | Lógica de componentes y servicios |
| RxJS 7.8 | Flujos de datos y solicitudes HTTP |
| Node.js 24 · npm 11.17 | Instalación, desarrollo y build |
| Vitest 5 | Pruebas unitarias mediante Angular CLI |

- `src/app/features/`: funcionalidades, páginas, modelos y servicios por módulo.
- `src/app/core/`: sesión, HTTP, permisos, utilidades y servicios compartidos.
- `src/app/shared/`: componentes, directivas y pantallas comunes.
- `src/app/layout/`: estructura principal, encabezado y menú lateral.
- `public/`: imágenes y otros recursos estáticos.
- `proxy.conf.json`: proxy local de `/api` hacia `http://localhost:8080`.

## Requisitos y ejecución local

Requisitos: Node.js 24.x, npm 11.17 o compatible, y el backend conectado a Oracle.

```powershell
npm ci
npm start
```

Abre `http://localhost:4200`. El proxy envía `/api` al backend local en el puerto `8080`. Para cambiarlo, edita `proxy.conf.json` y reinicia el servidor Angular.

## Rutas y seguridad

La sesión usa signals y conserva el JWT vigente en `sessionStorage`. El interceptor añade `Authorization: Bearer` a las solicitudes dirigidas a la API. Cuando la API devuelve `401` para el token vigente, la aplicación limpia la sesión y redirige a `/login`. El guard valida los permisos declarados por cada pantalla y redirige al cambio de contraseña cuando la cuenta lo requiere. El backend aplica los permisos de cada operación.

| Ruta | Módulo | Acceso |
|---|---|---|
| `/login` | Inicio de sesión | Público |
| `/habitaciones` | Estado y calendario | `HABITACIONES:VER` |
| `/reservas` | Consulta de reservas y estadías | `RESERVAS:VER` |
| `/reservas/nueva` | Registro de reserva | `RESERVAS:CREAR` |
| `/ventas` | Registro y confirmación de venta | `VENTAS:CREAR_Y_CONFIRMAR` |
| `/ventas/historial` | Historial de ventas | `VENTAS:VER` |
| `/configuraciones` | Habitaciones, mobiliario y productos | Permisos según la pantalla |
| `/reportes` | Reportes | Gerencia |
| `/limpieza` | Informes e incidencias | Al menos un permiso de consulta de limpieza |
| `/caja` | Turnos, movimientos y gastos | Al menos un permiso de caja o `GASTOS:CREAR` |
| `/seguridad` | Usuarios, roles y accesos | Sesión activa y al menos un permiso habilitado para empleados, usuarios o roles |

Consulta [las rutas de la aplicación](src/app/app.routes.ts) y la [guía de seguridad](../rebolsysBackend/API-SEGURIDAD.md) para el detalle de los accesos por rol.

## Compilación y despliegue

Genera el build de producción con:

```powershell
npm run build
```

La salida queda en `dist/rebolsys-frontend/browser`. El servidor debe entregar `index.html` para las rutas Angular y reenviar `/api/*` a una instancia accesible del backend. El backend debe permitir el origen del sitio en `CORS_ALLOWED_ORIGINS`.

`vercel.json` configura instalación con `npm ci`, compilación con `npm run build`, cabeceras de seguridad y la carpeta de salida. La regla `/api/*` reenvía las solicitudes a la URL del backend. Para el túnel temporal actual, esa URL está en `vercel.json`; al reiniciar el túnel, reemplázala por la nueva URL HTTPS `*.trycloudflare.com` y vuelve a desplegar. Para un backend estable, configura `REBOLSYS_API_URL` con su URL HTTPS completa, incluido `/api/v1`, y actualiza el destino de `/api/*`. Para despliegue propio en servidor web, [`deploy/nginx.conf`](deploy/nginx.conf) incluye proxy `/api/`, caché inmutable para assets con hash y `no-cache` para `index.html`.

Entrega este servidor Nginx detrás de la terminación HTTPS del entorno. Para publicar una versión, copia primero los assets con hash y reemplaza `index.html` al final. Conserva los assets de la versión previa durante la transición para que las pestañas abiertas puedan seguir cargando sus módulos y para facilitar una reversión compatible con el backend.

## Pruebas y calidad

Ejecuta las pruebas unitarias y la compilación con:

```powershell
npm test -- --watch=false
npm run build
```

Para generar el reporte LCOV que importa SonarQube:

```powershell
npm.cmd test -- --watch=false --coverage --coverage-reporters=lcov
```

El informe SonarQube de la línea base `v1.0-demo` registra **Quality Gate aprobado**, **55,8 % de cobertura** y **0,0 % de líneas duplicadas**. La verificación local del 23 de noviembre documentó **169 pruebas aprobadas** y la compilación correcta. La comparación oficial con `v1.1-final` figura como pendiente en el informe de mejora. [Configuración del escáner](sonar-project.properties) · [Guía de SonarQube](../docs/calidad/README.md) · [Línea base](../docs/calidad/informe-mejora-rebolsys.md) · [Resultados locales](../docs/calidad/resultados-mejora-v1.1.md)

Para comprobar flujos completos con el backend y Oracle en marcha, usa [los casos funcionales de REBOLSYS](../rebolsysBackend/PRUEBAS-BACKEND.md). La automatización E2E con Playwright está pendiente de incorporar.

El build advierte cuando el paquete inicial supera 500 kB y falla al superar 1 MB. Para los estilos de cada componente, los límites son 6,5 kB y 8 kB, respectivamente.

Para revisar errores durante el desarrollo, consulta la consola del navegador y la pestaña Network. Los errores de solicitudes API también se registran en la salida del backend.

## Versión e historial

El paquete declara la versión `0.0.0`. `v1.0-demo` identifica la línea base de SonarQube y `v1.1-final`, el análisis de seguimiento indicado en la guía de calidad. El historial de cambios del código está en Git.
