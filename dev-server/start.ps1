# AstralClash — Запуск dev-сервера (Windows)
# Перед первым запуском: .\dev-server\setup.ps1

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$serverDir = Join-Path $scriptDir "server"
$pluginsDir = Join-Path $serverDir "plugins"

$paperJar = Get-ChildItem -Path $serverDir -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $paperJar) {
    Write-Host "Paper jar не найден. Сначала запусти: .\dev-server\setup.ps1" -ForegroundColor Red
    exit 1
}

# Remove old PacketEvents jar (now bundled in AstralClash)
Get-ChildItem (Join-Path $pluginsDir "packetevents-spigot-*.jar") -ErrorAction SilentlyContinue | Remove-Item -Force

# Rebuild plugin
Write-Host "==> Rebuilding plugin..." -ForegroundColor Cyan
Push-Location $projectRoot
mvn package -q -DskipTests 2>&1 | Select-Object -Last 5
$pluginJar = Get-ChildItem -Path "target" -Filter "AstralClash-1.0.0-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pluginJar) {
    $pluginJar = Get-ChildItem -Path "target" -Filter "AstralClash-*.jar" -Exclude "*-sources.jar","original-*" -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($pluginJar) {
    Copy-Item $pluginJar.FullName (Join-Path $pluginsDir "AstralClash-1.0.0-SNAPSHOT-shaded.jar") -Force
    Write-Host "==> Плагин обновлён." -ForegroundColor Green
}
Pop-Location

Write-Host "==> Запуск Paper (JDWP debug port 5005)..." -ForegroundColor Cyan
Push-Location $serverDir
& java -Xms512M -Xmx2G `
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 `
  -jar $paperJar.Name `
  --nogui
Pop-Location
