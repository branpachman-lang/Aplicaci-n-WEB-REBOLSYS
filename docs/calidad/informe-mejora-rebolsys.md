# Informe técnico de mejora de REBOLSYS

- **Fecha de referencia** 23 de noviembre de 2026
- **Destinatario** Desarrollador responsable de la mejora
- **Línea base de SonarQube** `v1.0-demo`

REBOLSYS tiene dos módulos en el mismo repositorio. El backend usa Java 21, Spring Boot, Maven y Oracle. El frontend usa Angular, TypeScript, Node.js 24 y Vitest. Los dos análisis iniciales aprobaron su Quality Gate. La primera entrega debe resolver o justificar la incidencia de CSRF del backend y cubrir con pruebas las operaciones de caja que pueden afectar saldos, movimientos y cierres. La cobertura del frontend ofrece el mayor margen de mejora general.

## Línea base y alcance de los datos

| Métrica | Backend Java | Frontend Angular |
| --- | --- | --- |
| Clave del tablero | `rebolsys_backend` | `rebolsys-frontend` |
| Quality Gate observado | Passed | Passed |
| Cobertura general en SonarQube | 69,2 % | 55,8 % |
| Incidencias visibles en SonarQube | 1 de seguridad, impacto alto | 0 |
| Líneas duplicadas | Pendiente de registrar desde Measures | 0,0 % |
| Pruebas locales ejecutadas | 262 aprobadas, 115 omitidas | 144 aprobadas |
| Origen de la cobertura | JaCoCo | Vitest y LCOV |

La última ejecución local de JaCoCo midió **76,9 % de líneas** y **55,1 % de ramas**. Para comparar versiones usa el **69,2 %** del tablero, que corresponde a la métrica general de SonarQube. Las pruebas de Oracle requieren una ejecución explícita contra un esquema de desarrollo y deben registrarse aparte. El informe inicial se publicó desde un árbol con cambios locales. Usa la revisión que figure en **Activity** y registra los cambios incluidos en cada análisis antes de atribuirlo a un commit reproducible. [Backend en SonarQube](http://localhost:9000/project/overview?id=rebolsys_backend) · [Frontend en SonarQube](http://localhost:9000/project/overview?id=rebolsys-frontend)

## Prioridad 1. Revisar la incidencia de CSRF

SonarQube abrió `java:S4502` en `SecurityConfig.java`, línea 42, por la desactivación de CSRF. La configuración observada declara `STATELESS`. `JwtAuthenticationFilter` lee la cabecera `Authorization` y el interceptor Angular añade el JWT como `Bearer`. `AccesoHttpTest` ya comprueba que una cookie `JSESSIONID` por sí sola no autentica una consulta. Estos indicios respaldan revisar la aplicabilidad de la regla para esta configuración. [Configuración de Spring Security](../../rebolsysBackend/src/main/java/pe/com/rebolsysBackend/config/SecurityConfig.java) · [Filtro JWT](../../rebolsysBackend/src/main/java/pe/com/rebolsysBackend/security/JwtAuthenticationFilter.java) · [Pruebas de acceso HTTP](../../rebolsysBackend/src/test/java/pe/com/rebolsysBackend/seguridad/AccesoHttpTest.java) · [Incidencia en SonarQube](http://localhost:9000/project/issues?open=7a6ec920-d4be-43e7-9d2c-09ac22bc08e1&id=rebolsys_backend)

1. Revisa las cadenas de seguridad y las rutas que cambian estado, incluidas autenticación, sesión y operaciones de caja. Confirma qué credencial acepta cada una y verifica si el despliegue añade autenticación por cookies, sesión, Basic u otro mecanismo que el navegador adjunte automáticamente.
2. Comprueba en pruebas HTTP que una solicitud de escritura protegida sin Bearer recibe `401`, que un usuario autenticado sin el permiso recibe `403` y que la misma operación con el rol permitido funciona. Usa un entorno de prueba y datos desechables.
3. Si existe una vía de autenticación automática aplicable, implementa la protección correspondiente y verifica el flujo en backend y frontend. Si todas las rutas protegidas dependen del Bearer explícito y las pruebas respaldan el resultado, registra la justificación en la incidencia y selecciona el estado que permita su tipo en SonarQube. En esta instancia `S4502` aparece en **Issues**, por lo que corresponde documentar esa resolución por separado de los **Security Hotspots**.

## Prioridad 2. Cubrir el flujo de caja

| Área | Evidencia | Casos de aceptación |
| --- | --- | --- |
| Backend, registro de gastos | `GastoServiceImpl.java` tiene 83 líneas sin cobertura en JaCoCo | Un gasto válido crea un movimiento. Un turno cerrado o ajeno se rechaza sin guardar. Repetir la misma solicitud con la misma clave devuelve el registro existente. Usar la misma clave con datos distintos se rechaza. |
| Frontend, cierre y recuperación | `caja-panel.ts` tiene 85 líneas sin cobertura en SonarQube | El cierre correcto actualiza el turno. Ante `409` o una pérdida de conexión, el panel consulta el estado del cierre y guía al usuario según el resultado. Un reintento de movimiento conserva su clave y evita envíos simultáneos desde la pantalla. |
| Integración con Oracle | Existe `GastosOracleTest`, omitido en la ejecución local de referencia | Ejecutar la suite contra un esquema de desarrollo. Verificar persistencia y unicidad ante reintentos, además del control del turno si dos solicitudes compiten. |

Usa JUnit y las pruebas de servicio existentes para las reglas de Java. Usa Vitest y TestBed con la API simulada para verificar el estado y los mensajes del panel. Reserva las comprobaciones de bloqueo, restricciones y transacciones reales para Oracle. [Servicio de gastos](../../rebolsysBackend/src/main/java/pe/com/rebolsysBackend/service/impl/caja/GastoServiceImpl.java) · [Panel de caja](../../rebolsysFrontend/src/app/features/caja/caja-panel.ts) · [Pruebas Oracle de gastos](../../rebolsysBackend/src/test/java/pe/com/rebolsysBackend/propuesta/GastosOracleTest.java)

## Prioridad 3. Extender la revisión al resto del proyecto

1. **Acceso y sesión.** `acceso.ts` tiene 36 líneas sin cobertura. Añade casos de inicio correcto, credenciales rechazadas y cambio de contraseña requerido. Conserva las pruebas del interceptor y del cierre de sesión.
2. **Emisión electrónica.** `EmisionElectronicaServiceImpl.java` tiene 91 líneas sin cobertura en JaCoCo. Revisa éxito, rechazo del proveedor, falta de respuesta y reintento. Usa respuestas simuladas para comprobar el tratamiento de cada estado sin emitir comprobantes reales.
3. **Reservas, ventas y limpieza.** Levanta la cobertura por archivo en **Measures** y revisa los flujos que cambian estados o registran cobros. Amplía las pruebas existentes con los caminos de error o permisos que sigan descubiertos. Ordena los cambios por impacto operativo y luego por esfuerzo.
4. **Mantenibilidad y duplicación.** Revisa **Issues** y **Measures** de ambos módulos después de cada entrega. Simplifica código repetido cuando represente la misma regla de negocio y conserva pruebas que fijen el comportamiento de sus consumidores. Registra la duplicación inicial del backend antes de comparar.

[Acceso de Angular](../../rebolsysFrontend/src/app/features/seguridad/acceso.ts) · [Servicio de emisión](../../rebolsysBackend/src/main/java/pe/com/rebolsysBackend/service/impl/comun/comprobantes/EmisionElectronicaServiceImpl.java) · [Cobertura del frontend por archivo](http://localhost:9000/component_measures?id=rebolsys-frontend&metric=coverage&view=list)

## Verificación y entrega

1. Guarda la evidencia de `v1.0-demo` de los dos proyectos. Registra fecha, revisión, perfil de reglas, Quality Gate asignado, incidencias, cobertura, duplicación y estado de los hotspots. Completa los valores pendientes desde **Measures** y **Security Hotspots**.
2. Ejecuta `.\mvnw.cmd clean verify` con JDK 21 desde `rebolsysBackend`. Cuando haya un esquema Oracle de desarrollo disponible, ejecuta la integración con `RUN_ORACLE_TESTS=true` y registra cuántas pruebas se ejecutaron. En `rebolsysFrontend`, ejecuta `npm.cmd test -- --watch=false --coverage --coverage-reporters=lcov` y `npm.cmd run build`.
3. Publica `v1.1-final` en los mismos tableros. Para Java, agrega `'-Dsonar.projectKey=rebolsys_backend'` al comando de la guía, ya que el `pom.xml` usa `rebolsys-backend`. En Angular, la clave `rebolsys-frontend` ya está configurada. Carga el token de cada proyecto en la terminal mediante el prompt oculto. [Guía de ejecución](README.md)
4. Compara las dos versiones en **Activity** y los archivos intervenidos en **Measures**. La entrega debe incluir pruebas aprobadas, cobertura mayor en los archivos objetivo, resultado documentado para `S4502` y evidencia del Quality Gate de ambos módulos. Usa el mismo alcance de cobertura y perfil de análisis para que la comparación sea verificable.
5. Para el **E13**, verifica la asignación de `LP2-Curso` y sus condiciones sobre **Overall Code**. La plantilla exige calificaciones A, 100 % de hotspots revisados, duplicación de hasta 3 % y cobertura de al menos 20 %. Guarda las capturas sin tokens y registra regla, archivo, corrección y commit de cada incidencia real. La línea base muestra una sola incidencia. Comprueba con el docente cómo sustentar el requisito de diez si la evidencia disponible sigue por debajo de ese número.
