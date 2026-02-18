#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Быстрый hot-reload плагина без перезапуска сервера
# Требует: PlugManX (https://www.spigotmc.org/resources/plugmanx.88135/)
#          и mc-send (или rcon-cli для отправки команд в консоль)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SERVER_DIR="$(dirname "$0")/server"
RCON_PORT=25575
RCON_PASS="devpass"   # задай в server.properties: rcon.password=devpass, enable-rcon=true

echo "==> Пересборка..."
(cd "$(dirname "$0")/.." && mvn package -q -DskipTests)

PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*-shaded.jar" 2>/dev/null | head -1)
[ -z "$PLUGIN_JAR" ] && PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*.jar" ! -name "*-sources.jar" | head -1)
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/"

# Отправляем команду reload через RCON (нужен rcon-cli: https://github.com/gorcon/rcon-cli)
if command -v rcon-cli &>/dev/null; then
  rcon-cli --host 127.0.0.1 --port "$RCON_PORT" --password "$RCON_PASS" \
    "plugman reload AstralClash"
  echo "==> Hot-reload выполнен через RCON."
else
  echo "==> rcon-cli не найден. Плагин обновлён в папке, перезапусти сервер вручную."
  echo "    Установить: https://github.com/gorcon/rcon-cli/releases"
fi
