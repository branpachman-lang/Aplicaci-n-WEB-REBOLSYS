# REBOLSYS Frontend

Aplicación web Angular para la operación del hospedaje Real Bolognesi. Consume la API de REBOLSYS para gestionar reservas, estadías, ventas, caja, inventario, limpieza y reportes.

| Dato | Valor |
|---|---|
| Versión del paquete | `0.0.0` (sin release etiquetado) |
| Curso | Lenguaje de Programación II · EP Ingeniería de Sistemas · UPeU · 2026-II |
| Docente | Benjamin David Reyna Barreto |
| Integrantes | Yeremin Brian Ccapa Chacca, Esther Mariana Landa Torres, Brandon Erly Pacho Mamani, James Tequen Oncoy y Sebastian Gamarra Tarazona |
| Backend local | `http://localhost:8080/api/v1` |
| Demo pública | No definida; revisar la regla de API de Vercel antes de desplegar |

## Contenido

- [Funciones](#funciones)
- [Capturas](#capturas)
- [Tecnologías y estructura](#tecnologías-y-estructura)
- [Rutas y seguridad](#rutas-y-seguridad)
- [Requisitos y ejecución local](#requisitos-y-ejecución-local)
- [Compilación y despliegue](#compilación-y-despliegue)
- [Pruebas, rendimiento y calidad](#pruebas-rendimiento-y-calidad)
- [Versión y responsables](#versión-y-responsables)

## Funciones

- Panel de habitaciones y calendario de disponibilidad.
- Reservas con titular natural o empresa, varias habitaciones, ocupantes, pagos y cuenta de estadía.
- Ventas de productos, cobro y comprobantes internos.
- Configuración de habitaciones, mobiliario, productos y categorías.
- Caja por turnos, gastos, reportes e informes de limpieza con incidencias y tickets de mantenimiento.
- Comprobantes imprimibles en A4; los reportes admiten impresión A4 horizontal o ticket de 80 mm mediante el diálogo del navegador.
- Interfaz adaptable a distintos anchos de pantalla. Las acciones visibles dependen de la sesión y los permisos del usuario.

## Capturas

El repositorio no incluye capturas de las pantallas funcionales. Los recursos de `public/images/` son el logo y el fondo de acceso; se pueden agregar capturas de la aplicación en `docs/capturas/` cuando estén disponibles.

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

## Rutas y seguridad

| Ruta | Módulo | Acceso |
|---|---|---|
| `/login` | Inicio de sesión | Pública |
| `/habitaciones` | Estado y calendario | Sesión y permiso `HABITACIONES:VER` |
| `/reservas` | Reservas y estadías | Sesión; acciones según permisos |
| `/ventas` | Ventas | Sesión; acciones según permisos |
| `/configuraciones` | Habitaciones, mobiliario y productos | Sesión; acciones según permisos |
| `/reportes` | Reportes | Sesión de gerencia |
| `/limpieza` | Informes e incidencias | Sesión y permisos de limpieza |
| `/caja` | Turnos, movimientos y gastos | Sesión y permisos de caja |
| `/seguridad` | Usuarios, roles y accesos | Sesión activa y permisos de seguridad |

Las rutas privadas usan el guard de sesión. El token se envía a la API para las solicitudes autenticadas; el backend vuelve a validar roles y permisos. No guardes credenciales Oracle, tokens de proveedores ni secretos JWT en el código del frontend.

## Requisitos y ejecución local

Requisitos: Node.js 24.x, npm 11.17 o compatible, y el backend conectado a Oracle.

```powershell
npm ci
npm start
```

Abre `http://localhost:4200`. El proxy envía `/api` al backend local en el puerto `8080`. Para cambiarlo, edita `proxy.conf.json` y reinicia el servidor Angular.

## Compilación y despliegue

Genera el build de producción con:

```powershell
npm run build
```

La salida queda en `dist/rebolsys-frontend/browser`. El servidor debe entregar `index.html` para las rutas Angular y reenviar `/api/*` a una instancia accesible del backend. El backend debe permitir el origen del sitio en `CORS_ALLOWED_ORIGINS`.

`vercel.json` configura instalación con `npm ci`, compilación con `npm run build` y la carpeta de salida. Su destino actual para `/api/*` es una URL temporal de Cloudflare Tunnel. Antes de desplegar, reemplázala por la URL estable del backend y confirma que CORS permite el dominio final; el túnel temporal no constituye un endpoint permanente.

## Pruebas, rendimiento y calidad

Ejecuta las pruebas unitarias y la compilación con:

```powershell
npm test -- --watch=false
npm run build
```

`package.json` no define un comando E2E. El repositorio tampoco incluye un informe o configuración de SonarQube ni una medición comparativa de rendimiento; por eso no se publican porcentajes ni resultados de calidad estática. El build de Angular establece límites de tamaño inicial de 500 kB para advertencia y 1 MB para error, y límites por hoja de estilos de componente de 4 kB y 8 kB respectivamente.

Para revisar errores durante el desarrollo, consulta la consola del navegador y la pestaña Network. Los errores de solicitudes API también se registran en la salida del backend.

## Versión e historial

Versión declarada en `package.json`: `0.0.0`. No se han definido releases etiquetados. El historial está en Git; entre los cambios recientes figura `feat: mejorar reportes y comprobantes A4`.
