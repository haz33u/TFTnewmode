# AstralClash Resource Pack Builder (Windows)
# Run: .\resource-pack\pack.ps1

$ErrorActionPreference = "Stop"
$packDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$outZip = Join-Path (Split-Path -Parent $packDir) "dev-server\server\astralclash-pack.zip"

Write-Host "==> Generating placeholder textures..." -ForegroundColor Cyan
& python "$packDir\generate_textures.py"
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "==> Packing to ZIP..." -ForegroundColor Cyan
if (Test-Path $outZip) { Remove-Item $outZip }
Push-Location $packDir
Compress-Archive -Path "pack.mcmeta", "pack.png", "assets" -DestinationPath $outZip -Force
Pop-Location

$sha1 = (Get-FileHash -Path $outZip -Algorithm SHA1).Hash.ToLower()
Write-Host "==> Done: $outZip" -ForegroundColor Green
Write-Host "==> SHA1:   $sha1"

# Обновляем server.properties
$propsPath = Join-Path (Split-Path -Parent $packDir) "dev-server\server\server.properties"
if (Test-Path $propsPath) {
    $content = Get-Content $propsPath -Raw
    $content = $content -replace "(?m)^resource-pack=.*\r?\n?", ""
    $content = $content -replace "(?m)^resource-pack-sha1=.*\r?\n?", ""
    $content = $content -replace "(?m)^resource-pack-prompt=.*\r?\n?", ""
    $content = $content.TrimEnd()
    $content += "`nresource-pack=http://localhost:8765/astralclash-pack.zip`n"
    $content += "resource-pack-sha1=$sha1`n"
    $content += 'resource-pack-prompt={"text":"AstralClash requires resource pack","color":"light_purple"}' + "`n"
    $content | Out-File $propsPath -Encoding utf8
    Write-Host "==> server.properties updated (resource-pack URL set)" -ForegroundColor Green
}

Write-Host ""
Write-Host "  Next: run HTTP server in a separate terminal:" -ForegroundColor Green
Write-Host "    cd dev-server\server; python -m http.server 8765"
Write-Host "  Then: .\dev-server\start.ps1"
