# AstralClash — DEV SERVER SETUP (Windows)
# Запуск: .\dev-server\setup.ps1
# Требует: Maven (mvn), Java 21

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$serverDir = Join-Path $scriptDir "server"
$pluginsDir = Join-Path $serverDir "plugins"

$paperVersion = "1.21.4"
$jarName = "paper-$paperVersion.jar"

Write-Host "==> Создаём папку dev-сервера: $serverDir" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null

# Скачиваем Paper
$paperPath = Join-Path $serverDir $jarName
if (-not (Test-Path $paperPath)) {
    Write-Host "==> Скачиваем Paper $paperVersion ..." -ForegroundColor Cyan
    $buildsJson = Invoke-RestMethod -Uri "https://api.papermc.io/v2/projects/paper/versions/$paperVersion/builds"
    $latestBuild = $buildsJson.builds[-1].build
    $downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/$paperVersion/builds/$latestBuild/downloads/paper-$paperVersion-$latestBuild.jar"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $paperPath -UseBasicParsing
    Write-Host "==> Paper скачан (build $latestBuild)." -ForegroundColor Green
} else {
    Write-Host "==> Paper уже есть, пропускаем." -ForegroundColor Yellow
}

# EULA (обязательно для запуска сервера)
@"
#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).
eula=true
"@ | Out-File (Join-Path $serverDir "eula.txt") -Encoding utf8

# server.properties
$props = @"
online-mode=false
gamemode=adventure
max-players=8
motd=AstralClash DEV
level-name=world
spawn-protection=0
view-distance=10
enable-rcon=true
rcon.port=25575
rcon.password=devpass
"@
$props | Out-File (Join-Path $serverDir "server.properties") -Encoding utf8

# bukkit.yml
$bukkit = @"
spawn-limits:
  monsters: 0
  animals: 0
  water-animals: 0
  ambient: 0
"@
$bukkit | Out-File (Join-Path $serverDir "bukkit.yml") -Encoding utf8

# Remove old PacketEvents jar if present (now bundled in AstralClash)
$oldPe = Join-Path $pluginsDir "packetevents-spigot-*.jar"
Get-ChildItem $oldPe -ErrorAction SilentlyContinue | Remove-Item -Force

# Build plugin
Write-Host "==> Сборка AstralClash (mvn package) ..." -ForegroundColor Cyan
Push-Location $projectRoot
mvn package -q -DskipTests
if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }

$pluginJar = Get-ChildItem -Path "target" -Filter "AstralClash-*-shaded.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pluginJar) {
    $pluginJar = Get-ChildItem -Path "target" -Filter "AstralClash-*.jar" -Exclude "*-sources.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
}
Pop-Location
if ($pluginJar) {
    Copy-Item $pluginJar.FullName $pluginsDir -Force
    Write-Host "==> Плагин скопирован в plugins/" -ForegroundColor Green
}

Write-Host ""
Write-Host '  Done! PacketEvents is auto-installed. Also in plugins/:' -ForegroundColor Green
Write-Host '    - PlugManX, ModelEngine (optional) - see dev-server\PLUGINS.md'
Write-Host '  Start server: .\dev-server\start.ps1'
