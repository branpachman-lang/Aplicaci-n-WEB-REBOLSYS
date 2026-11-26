# REBOLSYS

Aplicación web para la operación del hospedaje Real Bolognesi. Centraliza la disponibilidad de habitaciones, las reservas y estadías, las ventas, los turnos de caja, el inventario y el seguimiento de limpieza y mantenimiento.

## Módulos

| Módulo | Función | Documentación |
|---|---|---|
| Backend | API REST con Java 21, Spring Boot y Oracle | [Backend](rebolsysBackend/README.md) |
| Frontend | Aplicación Angular para el personal del hospedaje | [Frontend](rebolsysFrontend/README.md) |

Para iniciar el proyecto en IntelliJ, prepara Oracle y arranca la API siguiendo la guía del backend. En una segunda terminal, instala las dependencias y arranca Angular siguiendo la guía del frontend. La aplicación local se abre en `http://localhost:4200` y accede a la API del puerto `8080` mediante el proxy de Angular.

## Evidencias y calidad

- [Contrato OpenAPI](rebolsysBackend/api/openapi.json) y [colecciones de Postman](rebolsysBackend/api/postman/)
- [Pruebas funcionales del backend](rebolsysBackend/PRUEBAS-BACKEND.md) y [datos de prueba](rebolsysBackend/tools/datos-prueba/README.md)
- [SonarQube en IntelliJ](docs/calidad/README.md), [línea base](docs/calidad/informe-mejora-rebolsys.md) y [resultados locales de mejora](docs/calidad/resultados-mejora-v1.1.md)

Proyecto académico de Lenguaje de Programación II · EP Ingeniería de Sistemas · Universidad Peruana Unión · 2026-II.
