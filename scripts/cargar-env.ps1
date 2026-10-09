# Carga infra/.env como variables de entorno de la sesión actual de PowerShell.
# Uso: .\scripts\cargar-env.ps1 [-Archivo ruta\al\.env]
# Las variables de entorno son del proceso, así que quedan disponibles para spring-boot:run en esta misma terminal.
# No imprime valores: solo los nombres cargados.
param(
    [string]$Archivo = (Join-Path $PSScriptRoot '..\infra\.env')
)

if (-not (Test-Path -LiteralPath $Archivo -PathType Leaf)) {
    Write-Error "No existe $Archivo. Copiá infra\.env.example a infra\.env y completalo."
    exit 1
}

$cargadas = 0
foreach ($linea in Get-Content -LiteralPath $Archivo) {
    $linea = $linea.Trim()
    if ($linea -eq '' -or $linea.StartsWith('#')) { continue }
    if ($linea.StartsWith('export ')) { $linea = $linea.Substring(7).TrimStart() }

    $separador = $linea.IndexOf('=')
    if ($separador -lt 1) { continue }

    $nombre = $linea.Substring(0, $separador).Trim()
    $valor = $linea.Substring($separador + 1).Trim()
    if ($nombre -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        Write-Warning 'Línea ignorada: nombre de variable inválido.'
        continue
    }
    if ($valor -match '^"(.*)"$' -or $valor -match "^'(.*)'$") { $valor = $Matches[1] }

    Set-Item -LiteralPath "Env:$nombre" -Value $valor
    Write-Host "  $nombre"
    $cargadas++
}

Write-Host "Cargadas $cargadas variables desde $Archivo"
