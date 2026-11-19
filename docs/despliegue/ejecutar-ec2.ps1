# Ejecutar en la EC2 con Windows Server, PowerShell 5.1, Git, Java 21 y Node 24.
# Ejemplo: .\ejecutar-ec2.ps1 -IpPublica 203.0.113.10 -DbUrl 'jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1'
# Para un unico repositorio, agregar -RepositorioUnificado URL -RamaUnificada main.
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$IpPublica,
  [Parameter(Mandatory = $true)]
  [string]$DbUrl,
  [string]$DbUsuario = 'REBOLSYSDBV2',
  [string]$Carpeta = 'C:\Rebolsys',
  [string]$RamaBackend = 'iteracion3',
  [string]$RamaFrontend = 'iteracion3',
  [string]$RepositorioUnificado = '',
  [string]$RamaUnificada = 'main',
  [ValidateRange(30, 3600)]
  [int]$IntervaloSegundos = 60
)

$ErrorActionPreference = 'Stop'
$backend = $null
$frontend = $null
$bloqueo = [System.Threading.Mutex]::new($false, 'Global\RebolsysActualizacionEC2')
if (-not $bloqueo.WaitOne(0)) {
  $bloqueo.Dispose()
  throw 'Ya hay otra ejecucion de REBOLSYS activa con este script.'
}

function Invocar-Git {
  param([string]$Directorio, [string[]]$Argumentos)
  # PowerShell 5.1 convierte stderr de programas nativos en objetos de error.
  $ErrorActionPreference = 'Continue'
  $salida = & git.exe -C $Directorio @Argumentos 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw ($salida -join [Environment]::NewLine)
  }
  return ($salida -join [Environment]::NewLine).Trim()
}

function Preparar-Copia {
  param([string]$Nombre, [string]$Repositorio, [string]$Rama)
  $destino = Join-Path $Carpeta $Nombre
  if (-not (Test-Path -LiteralPath $destino)) {
    Write-Host "Clonando $Nombre, rama $Rama..."
    $null = Invocar-Git $Carpeta @('clone', '--single-branch', '--branch', $Rama, $Repositorio, $Nombre)
  }
  $raizGit = Invocar-Git $destino @('rev-parse', '--show-toplevel')
  if ([IO.Path]::GetFullPath($raizGit).TrimEnd('\') -ne [IO.Path]::GetFullPath($destino).TrimEnd('\')) {
    throw "La carpeta $destino debe contener su propio clon de Git."
  }
  if ((Invocar-Git $destino @('remote', 'get-url', 'origin')) -ne $Repositorio) {
    throw "El repositorio de $destino no coincide con $Repositorio."
  }
  if ((Invocar-Git $destino @('branch', '--show-current')) -ne $Rama) {
    throw "La carpeta $destino debe usar la rama $Rama."
  }
  return $destino
}

function Sincronizar-Copia {
  param([string]$Directorio, [string]$Rama)
  if (Invocar-Git $Directorio @('status', '--porcelain', '--untracked-files=no')) {
    throw "Hay cambios locales pendientes en $Directorio. Revisalos antes de actualizar."
  }
  $null = Invocar-Git $Directorio @('fetch', 'origin')
  $revision = Invocar-Git $Directorio @('rev-parse', "origin/$Rama")
  $adelantados = Invocar-Git $Directorio @('rev-list', '--count', "origin/$Rama..HEAD")
  if ($adelantados -ne '0') {
    throw "Hay commits locales en $Directorio que requieren revision."
  }
  $null = Invocar-Git $Directorio @('merge', '--ff-only', "origin/$Rama")
  return $revision
}

function Detener-Proceso {
  param([System.Diagnostics.Process]$Proceso)
  if ($null -ne $Proceso -and -not $Proceso.HasExited) {
    # El objeto Process conserva la identidad del proceso iniciado por este script.
    & taskkill.exe /PID $Proceso.Id /T /F | Out-Null
    $Proceso.WaitForExit()
  }
}

function Compilar-Backend {
  $ErrorActionPreference = 'Continue'
  Push-Location $copiaBackend
  try {
    & .\mvnw.cmd package '-DskipTests' > (Join-Path $runtime 'compilacion-backend.log') 2>&1
    if ($LASTEXITCODE -ne 0) {
      throw "Fallo la compilacion. Revisa $runtime\compilacion-backend.log."
    }
  } finally {
    Pop-Location
  }
}

function Instalar-Frontend {
  $ErrorActionPreference = 'Continue'
  Push-Location $copiaFrontend
  try {
    & npm.cmd ci > (Join-Path $runtime 'dependencias-frontend.log') 2>&1
    if ($LASTEXITCODE -ne 0) {
      throw "Fallo npm ci. Revisa $runtime\dependencias-frontend.log."
    }
  } finally {
    Pop-Location
  }
}

try {
  foreach ($comando in @('git.exe', 'java.exe', 'javac.exe', 'node.exe', 'npm.cmd')) {
    $null = Get-Command $comando -ErrorAction Stop
  }
  $versionJava = (& java.exe --version | Out-String)
  if ($versionJava -notmatch '(?m)^(openjdk|java) 21(?:\.|\s)') {
    throw 'Configura Java 21 en PATH antes de iniciar.'
  }
  $versionNode = [version](& node.exe -p 'process.versions.node')
  if ($versionNode.Major -ne 24 -or $versionNode -lt [version]'24.15.0') {
    throw 'Configura Node 24.15 o una version posterior de la rama 24.'
  }
  foreach ($puerto in @(8080, 4200)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
      throw "El puerto $puerto ya esta ocupado. Deten la ejecucion anterior antes de iniciar este script."
    }
  }

  $Carpeta = [IO.Path]::GetFullPath($Carpeta)
  $runtime = Join-Path $Carpeta 'runtime'
  $null = New-Item -ItemType Directory -Path $Carpeta, $runtime -Force
  if ($RepositorioUnificado) {
    $copiaUnificada = Preparar-Copia 'repositorio' $RepositorioUnificado $RamaUnificada
    $copiaBackend = Join-Path $copiaUnificada 'rebolsysBackend'
    $copiaFrontend = Join-Path $copiaUnificada 'rebolsysFrontend'
    foreach ($archivo in @((Join-Path $copiaBackend 'pom.xml'), (Join-Path $copiaFrontend 'angular.json'))) {
      if (-not (Test-Path -LiteralPath $archivo)) {
        throw "El repositorio unificado debe incluir rebolsysBackend y rebolsysFrontend. Falta $archivo."
      }
    }
  } else {
    $copiaBackend = Preparar-Copia 'rebolsysBackend' 'https://github.com/branpachman-lang/rebolsysBackend.git' $RamaBackend
    $copiaFrontend = Preparar-Copia 'rebolsysFrontend' 'https://github.com/branpachman-lang/rebolsysFrontend.git' $RamaFrontend
  }

  $env:DB_URL = $DbUrl
  $env:DB_USERNAME = $DbUsuario
  if (-not $env:DB_PASSWORD) {
    $clave = Read-Host 'Contrasena del esquema Oracle' -AsSecureString
    $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $clave).Password
    $clave.Dispose()
  }
  $env:SPRING_PROFILES_ACTIVE = 'prod'
  if (-not $env:JWT_SECRET) {
    $jwtBytes = New-Object byte[] 32
    $jwtAleatorio = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
      $jwtAleatorio.GetBytes($jwtBytes)
      $env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
    } finally {
      $jwtAleatorio.Dispose()
      [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
    }
  }
  $env:CORS_ALLOWED_ORIGINS = "http://${IpPublica}:4200,http://localhost:4200,http://127.0.0.1:4200"
  $env:FACTURACION_HABILITADA = 'false'
  # El proxy queda fuera del clon para que las actualizaciones lo conserven.
  '{"/api/**":{"target":"http://127.0.0.1:8080","secure":false}}' |
    Set-Content -LiteralPath (Join-Path $runtime 'proxy.ec2.json') -Encoding ASCII

  $revisionBackend = ''
  $revisionFrontend = ''
  Write-Host "Acceso de pruebas: http://${IpPublica}:4200/login"
  Write-Host "Actualizacion cada $IntervaloSegundos segundos. Ctrl+C detiene esta ejecucion."

  while ($true) {
    if ($RepositorioUnificado) {
      try {
        $revisionUnificada = Sincronizar-Copia $copiaUnificada $RamaUnificada
      } catch {
        Write-Warning "Actualizacion del repositorio: $($_.Exception.Message)"
        Start-Sleep -Seconds $IntervaloSegundos
        continue
      }
    }
    try {
      $revision = if ($RepositorioUnificado) { $revisionUnificada } else { Sincronizar-Copia $copiaBackend $RamaBackend }
      if ($revision -ne $revisionBackend) {
        Write-Host 'Compilando backend actualizado...'
        Compilar-Backend
        $jar = Join-Path $copiaBackend 'target\rebolsysBackend-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $jar)) {
          throw "No se encontro el JAR compilado en $jar."
        }
        Detener-Proceso $backend
        Copy-Item -LiteralPath $jar -Destination (Join-Path $runtime 'backend.jar') -Force
        $revisionBackend = $revision
      }
      if ($null -eq $backend -or $backend.HasExited) {
        $backend = Start-Process -FilePath (Get-Command java.exe).Source -WorkingDirectory $runtime -WindowStyle Hidden -PassThru `
          -ArgumentList '-jar backend.jar --spring.profiles.active=prod --server.address=127.0.0.1 --server.port=8080 --server.servlet.session.cookie.secure=false' `
          -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend-error.log')
        Write-Host "Backend iniciado con $($revisionBackend.Substring(0, 8))."
      }
    } catch {
      Write-Warning "Backend: $($_.Exception.Message)"
    }

    try {
      $revision = if ($RepositorioUnificado) { $revisionUnificada } else { Sincronizar-Copia $copiaFrontend $RamaFrontend }
      if ($revision -ne $revisionFrontend) {
        Detener-Proceso $frontend
        Write-Host 'Instalando dependencias del frontend actualizado...'
        Instalar-Frontend
        $revisionFrontend = $revision
      }
      if ($null -eq $frontend -or $frontend.HasExited) {
        $proxy = Join-Path $runtime 'proxy.ec2.json'
        $argumentos = '/d /s /c "npm.cmd start -- --host 0.0.0.0 --port 4200 --proxy-config "{0}""' -f $proxy
        $frontend = Start-Process -FilePath $env:ComSpec -ArgumentList $argumentos -WorkingDirectory $copiaFrontend -WindowStyle Hidden -PassThru `
          -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend-error.log')
        Write-Host "Frontend iniciado con $($revisionFrontend.Substring(0, 8))."
      }
    } catch {
      Write-Warning "Frontend: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds $IntervaloSegundos
  }
} finally {
  Detener-Proceso $frontend
  Detener-Proceso $backend
  $bloqueo.ReleaseMutex()
  $bloqueo.Dispose()
}
