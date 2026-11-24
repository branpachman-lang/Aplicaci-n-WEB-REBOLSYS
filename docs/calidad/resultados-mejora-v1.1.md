# Resultados de la mejora sobre `v1.0-demo`

- **Rama** `fix-gamarra`
- **Fecha** 23 de noviembre de 2026
- **Informe de referencia** [informe-mejora-rebolsys.md](informe-mejora-rebolsys.md)

Las cifras de este documento salen de ejecuciones locales. Las cifras oficiales de `v1.1-final` deben tomarse de SonarQube después de publicar el análisis.

## Prioridad 1. `java:S4502` en `SecurityConfig.java`

**Resultado: la regla no aplica a esta configuración. No se cambia código de producción.**

Revisión realizada:

| Mecanismo que el navegador adjunta solo | Estado en REBOLSYS |
| --- | --- |
| Cookie de sesión (`JSESSIONID`) | `SessionCreationPolicy.STATELESS` y `NullSecurityContextRepository`. Ninguna respuesta crea sesión ni envía `Set-Cookie`. |
| HTTP Basic | `httpBasic` desactivado. Una cabecera `Basic` recibe `401`. |
| Formulario de login o remember-me | `formLogin` desactivado; no hay remember-me. |
| Credenciales CORS | `allowCredentials=false`. Los orígenes no autorizados reciben `403` sin cabeceras `Access-Control-Allow-*`. |

Solo existe una `SecurityFilterChain`. `JwtAuthenticationFilter` autentica exclusivamente con `Authorization: Bearer`, que el interceptor de Angular añade de forma explícita y que un sitio ajeno no puede adjuntar.

Evidencia automatizada en [AccesoHttpTest.java](../../rebolsysBackend/src/test/java/pe/com/rebolsysBackend/seguridad/AccesoHttpTest.java):

- `escriturasSinBearerExplicitoSeRechazanAunqueElNavegadorAdjunteCredenciales`. Las escrituras de gastos, cierre y movimientos de caja, reservas, sesión, accesos y habitaciones reciben `401` con una cookie `JSESSIONID`. Una cabecera Basic recibe `401`. Un origen ajeno recibe `403` de CORS. No se crea sesión.
- `autenticaConTemporalLimitaFuncionesYRevocaAccesos`, ya existente. Un usuario autenticado sin permiso recibe `403` en el cierre de caja y en reservas. Con el rol permitido, el registro de movimientos y el cierre funcionan.

Texto propuesto para la incidencia en SonarQube. Estado sugerido: **Accept** o **False Positive**, según lo que ofrezca la instancia.

> CSRF desactivado intencionalmente. La API es stateless (STATELESS, NullSecurityContextRepository) y solo autentica con el encabezado Authorization: Bearer, que el navegador no adjunta automáticamente. formLogin, httpBasic y logout están desactivados, CORS usa allowCredentials=false y no se emiten cookies. AccesoHttpTest verifica 401 en escrituras con cookie o Basic, 403 sin permiso y éxito con el rol permitido.

Si en el futuro se añade autenticación por cookie, como un refresh token en una cookie `HttpOnly`, debe reactivarse CSRF para esas rutas.

## Prioridad 2. Flujo de caja

| Área | Pruebas añadidas | Casos de aceptación cubiertos |
| --- | --- | --- |
| `GastoServiceImpl.java` | [GastoServiceImplTest.java](../../rebolsysBackend/src/test/java/pe/com/rebolsysBackend/service/impl/caja/GastoServiceImplTest.java), 14 pruebas | Un gasto válido crea el movimiento. Un turno cerrado, ajeno o inexistente se rechaza sin guardar. La misma clave con los mismos datos devuelve el registro existente. La misma clave con otros datos, o perteneciente a un cobro, se rechaza. También se validan el tipo de salida, la evidencia, el listado, el historial y el catálogo. |
| `caja-panel.ts` | [caja-panel.spec.ts](../../rebolsysFrontend/src/app/features/caja/caja-panel.spec.ts), 13 pruebas | El cierre correcto actualiza el turno y la sesión. Ante `409` se consulta el turno y se muestran los importes actualizados. Tras perder la conexión se confirma si el turno ya se cerró. Si no se puede confirmar, el siguiente intento consulta en vez de reenviar. El reintento de un movimiento conserva su clave y se bloquean los envíos simultáneos. |
| Integración con Oracle | Sin cambios | `GastosOracleTest` requiere un esquema de desarrollo y `RUN_ORACLE_TESTS=true`. Queda pendiente de ejecutar. |

## Prioridad 3. Resto del proyecto

| Área | Pruebas añadidas | Casos cubiertos |
| --- | --- | --- |
| `acceso.ts` | [acceso.spec.ts](../../rebolsysFrontend/src/app/features/seguridad/acceso.spec.ts), 12 pruebas | Inicio correcto, credenciales rechazadas, errores de conexión, cambio de contraseña requerido y sus validaciones, usuario recordado y cierre de sesión. |
| `EmisionElectronicaServiceImpl.java` | [EmisionElectronicaServiceImplTest.java](../../rebolsysBackend/src/test/java/pe/com/rebolsysBackend/service/impl/comun/comprobantes/EmisionElectronicaServiceImplTest.java), 11 pruebas con un proveedor simulado | Aceptación, rechazo, respuesta sin veredicto, falta de respuesta, error con reintento, consulta del CDR, configuración incompleta y validación de los archivos XML, CDR y PDF. No se emiten comprobantes reales. |

Reservas, ventas, limpieza y duplicación siguen pendientes. Deben ordenarse a partir de **Measures** de `v1.1-final`.

## Verificación local

| Comprobación | `v1.0-demo` | Rama `fix-gamarra` |
| --- | --- | --- |
| `.\mvnw.cmd clean verify` con JDK 21 | 262 aprobadas, 115 omitidas | **288 aprobadas**, 115 omitidas (Oracle), BUILD SUCCESS |
| JaCoCo, líneas y ramas del backend | 76,9 % y 55,1 % | **80,8 % y 60,0 %** |
| `GastoServiceImpl.java`, líneas sin cubrir | 83 | **0** (83/83 líneas, 52/62 ramas) |
| `EmisionElectronicaServiceImpl.java`, líneas sin cubrir | 91 | **29** (114/143 líneas) |
| `npm.cmd test -- --watch=false --coverage` | 144 aprobadas | **169 aprobadas** |
| `caja-panel.ts`, líneas cubiertas en LCOV | 85 sin cubrir | **116/116** |
| `acceso.ts`, líneas cubiertas en LCOV | 36 sin cubrir | **57/58** |
| `npm.cmd run build` | Correcto | Correcto, con los dos avisos de presupuesto CSS previos |

El total de LCOV, 60,5 %, solo incluye los archivos que cargan las pruebas. Por eso no es comparable con el 55,8 % de SonarQube. La comparación oficial debe hacerse en **Activity** tras publicar `v1.1-final`.

## Pendiente para el responsable

1. Publicar `v1.1-final` en ambos tableros. Para Java, agregar `'-Dsonar.projectKey=rebolsys_backend'`.
2. Registrar la resolución de `S4502` en SonarQube con el texto anterior.
3. Ejecutar `GastosOracleTest` contra el esquema de desarrollo y registrar cuántas pruebas se ejecutaron.
4. Completar la duplicación inicial del backend desde **Measures** de `v1.0-demo`.
