# Analizar REBOLSYS con SonarQube desde IntelliJ

El repositorio contiene dos módulos. SonarQube genera un tablero para `rebolsys-backend` (Java 21, Maven y JaCoCo) y otro para `rebolsys-frontend` (Angular, Node.js 24 y Vitest). Si el curso asigna claves con el número de grupo, cámbialas en `rebolsysBackend/pom.xml` y `rebolsysFrontend/sonar-project.properties` antes de crear los proyectos.

## Iniciar el servidor local

1. Abre Docker Desktop y espera a que el motor esté disponible. En IntelliJ, abre la carpeta raíz del repositorio y usa la pestaña **Terminal** con PowerShell.
2. Ejecuta estos comandos desde la raíz. Espera a que el estado sea `UP`. La primera ejecución descarga la imagen.

   ```powershell
   docker compose -f docs/calidad/docker-compose.sonarqube.yml up -d
   (Invoke-RestMethod http://localhost:9000/api/system/status).status
   ```

3. Abre [SonarQube local](http://localhost:9000). En el primer acceso usa `admin` como usuario y contraseña, y establece una contraseña propia cuando te la pida.
4. En **Projects > Create project > Local project** crea `rebolsys-backend` y `rebolsys-frontend`, con `main` como rama principal. Puedes elegir la configuración predeterminada de **New Code**.
5. En **My Account > Security** genera un token de tipo **Project Analysis** para cada proyecto. Usa cada token en la terminal al analizar su módulo. Pégalo solo en el prompt oculto para mantenerlo fuera del historial, los archivos del proyecto y las capturas.

La configuración usa SonarQube Community Build 26.11 con H2 para la práctica local. Los volúmenes conservan proyectos, reglas y resultados al detener o recrear el contenedor. Para pausarlo, ejecuta `docker compose -f docs/calidad/docker-compose.sonarqube.yml stop`. Evita `down -v` si quieres conservar la evidencia. Si el estado tarda, consulta `docker compose -f docs/calidad/docker-compose.sonarqube.yml logs --tail 80 sonarqube`.

## Publicar el análisis y la cobertura

Para el backend, abre una terminal en `rebolsysBackend`. Verifica con `.\mvnw.cmd -v` que Maven use JDK 21. Si hace falta, apunta `JAVA_HOME` al JDK 21 antes de ejecutar el wrapper.

```powershell
$env:SONAR_HOST_URL = 'http://localhost:9000'
$env:SONAR_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Token del backend' -AsSecureString)).Password
.\mvnw.cmd clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar '-Dsonar.projectVersion=v1.0-demo'
Remove-Item Env:SONAR_TOKEN
```

`verify` prueba el código y escribe `target/site/jacoco/jacoco.xml`. El escáner envía ese reporte al servidor. La ejecución habitual omite las pruebas de integración que requieren Oracle cuando `RUN_ORACLE_TESTS` está desactivada.

Para el frontend, abre una terminal en `rebolsysFrontend`. Ejecuta `npm.cmd ci` al preparar la carpeta por primera vez. En Windows, `npm.cmd` usa el ejecutable de npm directamente.

```powershell
npm.cmd test -- --watch=false --coverage --coverage-reporters=lcov
```

Cuando terminen las pruebas correctamente, carga el token del frontend y publica el análisis.

```powershell
$env:SONAR_HOST_URL = 'http://localhost:9000'
$env:SONAR_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Token del frontend' -AsSecureString)).Password
npx.cmd --yes @sonar/scan@5.1.0 '-Dsonar.projectVersion=v1.0-demo'
Remove-Item Env:SONAR_TOKEN
```

Angular escribe `coverage/rebolsys-frontend/lcov.info`. SonarQube importa ese archivo y excluye de la métrica de cobertura el punto de entrada, los entornos y las rutas. En el backend se mide la cobertura de todo el código de producción. Ambos escáneres imprimen la URL del tablero al publicar.

## Revisar incidencias mientras programas

En IntelliJ, ve a **Settings > Plugins > Marketplace**, busca **SonarQube for IDE**, instálalo y reinicia si IntelliJ lo pide. Para conectar el IDE, genera un **User Token** en SonarQube. En **Settings > Tools > SonarQube for IDE**, añade una conexión de tipo **SonarQube Server** a `http://localhost:9000`. En **Project Settings**, vincula el proyecto con `rebolsys-backend`. Si abriste los dos módulos juntos y Angular aparece como módulo de IntelliJ, usa **Override binding per module** para asignarle `rebolsys-frontend`.

La ventana **SonarQube for IDE** muestra incidencias junto al código. Abre una incidencia para ver la regla, la línea y su explicación. Después de corregirla, vuelve a ejecutar el escáner de ese módulo para actualizar las métricas del servidor.

## Configurar el Quality Gate del curso

En el servidor, abre **Quality Gates > Create**, crea `LP2-Curso` y añade las siguientes condiciones sobre **Overall Code**. Asigna el gate a los dos proyectos desde **Projects** en la página del gate o desde **Project Settings > Quality Gate** en cada proyecto.

| Condición | Falla cuando |
| --- | --- |
| Reliability Rating | Es peor que A |
| Security Rating | Es peor que A |
| Maintainability Rating | Es peor que A |
| Security Hotspots Reviewed | Baja del 100 % |
| Duplicated Lines (%) | Supera el 3 % |
| Coverage | Baja del 20 % |

En **Overview** verás el estado del gate y qué condiciones fallaron. Usa **Issues** para abrir y filtrar incidencias, **Measures** para consultar cobertura, deuda técnica y duplicación, y **Security Hotspots** para revisar cada punto y documentar si queda **Fixed** o **Safe**.

Para el E13, guarda el primer tablero de cada módulo antes de aplicar correcciones. Registra el hash del código analizado y las capturas del gate y de los tableros. Tras corregir y registrar las incidencias, repite los comandos con `v1.1-final`, usando las mismas claves. **Activity** permite localizar ambas versiones y seguir la evolución de las métricas. La plantilla pide al menos diez incidencias corregidas entre los dos módulos, con sus commits.

## Documentación oficial

- [SonarQube Community Build local](https://docs.sonarsource.com/sonarqube-community-build/try-out-sonarqube)
- [Escáner de Maven](https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/sonarscanner-for-maven)
- [Escáner de npm](https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/npm/using)
- [Conectar SonarQube con IntelliJ IDEA](https://docs.sonarsource.com/sonarqube-for-intellij/connect-your-ide/setup)
- [Configurar Quality Gates](https://docs.sonarsource.com/sonarqube-community-build/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates)
