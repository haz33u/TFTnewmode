#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Запуск dev-сервера с remote debugging на порту 5005
# Перед запуском: bash dev-server/setup.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SERVER_DIR="$(dirname "$0")/server"
JAR=$(find "$SERVER_DIR" -maxdepth 1 -name "paper-*.jar" | head -1)

if [ -z "$JAR" ]; then
  echo "Paper jar не найден. Сначала запусти setup.sh"
  exit 1
fi

# Пересобираем и обновляем плагин (hot-deploy)
echo "==> Пересборка плагина..."
(cd "$(dirname "$0")/.." && mvn package -q -DskipTests 2>&1 | tail -5)

PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*-shaded.jar" 2>/dev/null | head -1)
[ -z "$PLUGIN_JAR" ] && PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*.jar" ! -name "*-sources.jar" | head -1)
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/"
echo "==> Плагин обновлён."

echo "==> Запуск Paper (JDWP debug port 5005)..."
cd "$SERVER_DIR"
exec java \
  -Xms512M -Xmx2G \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar "$JAR" \
  --nogui
