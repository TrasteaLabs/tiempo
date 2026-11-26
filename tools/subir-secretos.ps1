<#
.SYNOPSIS
  Sube a GitHub los cuatro secretos con los que GitHub Actions firma la release (021).

.DESCRIPTION
  Lo ejecuta quien mantiene la app, en su PC, nunca un asistente. Lee keystore.properties de la carpeta de la
  clave, crea el repositorio (público y vacío) si no existe y sube con `gh secret set`:
    RELEASE_KEYSTORE_BASE64   el fichero de la clave, en base64
    RELEASE_KEYSTORE_PASSWORD
    RELEASE_KEY_ALIAS
    RELEASE_KEY_PASSWORD
  Los valores van a `gh` por la entrada estándar: no salen en pantalla, en el historial ni en la lista de procesos.
  Necesita GitHub CLI con la sesión iniciada (`gh auth login`) en una cuenta que pueda crear repos en la organización.

.PARAMETER KeyDir
  Carpeta con keystore.properties y la clave. Por defecto, ~/.weatherapp.

.PARAMETER Repo
  Repositorio dueño/nombre. Por defecto, trastealabs/tiempo.

.PARAMETER Description
  Descripción del repositorio si hay que crearlo.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\subir-secretos.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\subir-secretos.ps1 -KeyDir $HOME\.cuaderno -Repo trastealabs/cuaderno
#>
param(
    [string]$KeyDir = (Join-Path $HOME ".weatherapp"),
    [string]$Repo = "trastealabs/tiempo",
    [string]$Description = "Tiempo: el tiempo de donde estás, sin cuentas ni rastreo. App de Android de Trastea Labs."
)

$ErrorActionPreference = "Stop"
# Lo que se pasa a gh por la tubería, en UTF-8 (PowerShell 5.1 usa ASCII y estropearía una contraseña con «ñ»).
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

function Fail([string]$message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

# Ejecuta una orden de gh sin enseñar su salida. En PowerShell 5.1, con "Stop", cualquier línea que gh escriba en
# la salida de errores y se redirija corta el script: aquí dentro va en "Continue" y se mira $LASTEXITCODE después.
function Invoke-Quiet([scriptblock]$block) {
    $ErrorActionPreference = "Continue"
    & $block *> $null
}

# gh puede no estar en el PATH.
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) {
    $candidate = "C:\Program Files\GitHub CLI\gh.exe"
    if (Test-Path $candidate) { $gh = $candidate } else { Fail "no encuentro GitHub CLI (gh). Instálalo desde https://cli.github.com/" }
}

Invoke-Quiet { & $gh auth status }
if ($LASTEXITCODE -ne 0) { Fail "GitHub CLI no tiene la sesión iniciada: ejecuta 'gh auth login' y vuelve a probar." }

# Leer keystore.properties sin enseñar nada.
$propsFile = Join-Path $KeyDir "keystore.properties"
if (-not (Test-Path $propsFile)) { Fail "no existe $propsFile" }
$props = @{}
foreach ($line in Get-Content -LiteralPath $propsFile -Encoding UTF8) {
    $trimmed = $line.Trim()
    if ($trimmed -eq "" -or $trimmed.StartsWith("#") -or $trimmed.StartsWith("!")) { continue }
    $idx = $trimmed.IndexOfAny([char[]]"=:")
    if ($idx -lt 1) { continue }
    # Escapes de .properties más comunes (rutas de Windows escritas como C\:\\Users\\...).
    $value = $trimmed.Substring($idx + 1).Trim() -replace '\\([\\:=!# ])', '$1'
    $props[$trimmed.Substring(0, $idx).Trim()] = $value
}
$missing = @("storeFile", "storePassword", "keyAlias", "keyPassword") | Where-Object { -not $props[$_] }
if ($missing) { Fail "a keystore.properties le falta: $($missing -join ', ')" }

# storeFile puede ser absoluto o relativo a la carpeta de la clave (como en buildSrc/ReleaseSigning.kt).
$storeFile = $props["storeFile"]
if (-not [IO.Path]::IsPathRooted($storeFile)) { $storeFile = Join-Path $KeyDir $storeFile }
if (-not (Test-Path -LiteralPath $storeFile)) { Fail "no existe el fichero de la clave que dice keystore.properties" }

# Crear el repositorio si no existe (público y vacío: sin README ni licencia, el código llega después).
Invoke-Quiet { & $gh repo view $Repo }
if ($LASTEXITCODE -ne 0) {
    Write-Host "El repositorio $Repo no existe: lo creo (público, vacío)."
    & $gh repo create $Repo --public --description $Description
    if ($LASTEXITCODE -ne 0) { Fail "no he podido crear $Repo. ¿Existe la organización y tu cuenta puede crear repos en ella?" }
} else {
    Write-Host "El repositorio $Repo ya existe."
}

$secrets = [ordered]@{
    "RELEASE_KEYSTORE_BASE64"   = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $storeFile)))
    "RELEASE_KEYSTORE_PASSWORD" = $props["storePassword"]
    "RELEASE_KEY_ALIAS"         = $props["keyAlias"]
    "RELEASE_KEY_PASSWORD"      = $props["keyPassword"]
}

foreach ($name in $secrets.Keys) {
    Invoke-Quiet { $secrets[$name] | & $gh secret set $name --repo $Repo }
    if ($LASTEXITCODE -ne 0) { Fail "no he podido subir $name" }
    Write-Host "Subido $name"
}

# Comprobar que están los cuatro (gh solo lista nombres y fechas, nunca valores).
$listed = & $gh secret list --repo $Repo --json name --jq ".[].name"
$notThere = $secrets.Keys | Where-Object { $listed -notcontains $_ }
if ($notThere) { Fail "GitHub no lista: $($notThere -join ', ')" }

Write-Host ""
Write-Host "Listo: los cuatro secretos están en $Repo." -ForegroundColor Green
