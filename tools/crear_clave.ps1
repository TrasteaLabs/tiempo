# Crea la clave de firma del APK de release en ~/.weatherapp/ (una sola vez).
# Uso (PowerShell, desde la carpeta del proyecto):  .\tools\crear_clave.ps1
# Genera una contraseña aleatoria, la guarda solo en keystore.properties y NO la imprime.
# No sobrescribe nada si ya existe una clave.

$ErrorActionPreference = "Stop"
$dir = Join-Path $HOME ".weatherapp"
$jks = Join-Path $dir "weatherapp.jks"
$props = Join-Path $dir "keystore.properties"

if ((Test-Path $jks) -or (Test-Path $props)) {
    Write-Host "Ya existe una clave en $dir. No toco nada."
    exit 1
}

$keytool = @(
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\keytool.exe" }),
    $(Get-Command keytool -ErrorAction SilentlyContinue | ForEach-Object Source)
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $keytool) { Write-Host "No encuentro keytool (está en la carpeta jbr\bin de Android Studio)."; exit 1 }

New-Item -ItemType Directory -Force $dir | Out-Null
$chars = [char[]]([char]'a'..[char]'z' + [char]'A'..[char]'Z' + [char]'0'..[char]'9')
$password = -join (1..24 | ForEach-Object { $chars | Get-Random })

& $keytool -genkeypair -keystore $jks -alias weatherapp -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $password -keypass $password -dname "CN=WeatherApp, O=Personal, C=ES"
if ($LASTEXITCODE -ne 0) { Write-Host "keytool ha fallado."; exit 1 }

@(
    "storeFile=weatherapp.jks",
    "storePassword=$password",
    "keyAlias=weatherapp",
    "keyPassword=$password"
) | Set-Content -Path $props -Encoding ascii

Write-Host "Hecho. Clave creada en $dir"
Write-Host "HAZ COPIA de toda esa carpeta (weatherapp.jks y keystore.properties) en un sitio seguro."
Write-Host "Sin ella no podrás actualizar la app instalada sin desinstalarla antes."
