#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AstralClash — DEV SERVER QUICK SETUP
# Запускать из папки проекта: bash dev-server/setup.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PAPER_VERSION="1.21.4"
PAPER_BUILD="latest"   # или конкретный номер билда, напр. 196
SERVER_DIR="$(dirname "$0")/server"
JAR_NAME="paper-${PAPER_VERSION}.jar"

echo "==> Создаём папку dev-сервера: $SERVER_DIR"
mkdir -p "$SERVER_DIR/plugins"

# ── Скачиваем Paper ──────────────────────────────────────────────────────────
if [ ! -f "$SERVER_DIR/$JAR_NAME" ]; then
  echo "==> Скачиваем Paper $PAPER_VERSION ..."
  BUILD=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds" \
    | python3 -c "import sys,json; b=json.load(sys.stdin)['builds']; print(b[-1]['build'])")
  DOWNLOAD_URL="https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${BUILD}/downloads/paper-${PAPER_VERSION}-${BUILD}.jar"
  echo "==> Build: $BUILD — $DOWNLOAD_URL"
  curl -L -o "$SERVER_DIR/$JAR_NAME" "$DOWNLOAD_URL"
  echo "==> Paper скачан."
else
  echo "==> Paper уже есть, пропускаем."
fi

# ── EULA ─────────────────────────────────────────────────────────────────────
echo "eula=true" > "$SERVER_DIR/eula.txt"

# ── server.properties (dev-настройки) ───────────────────────────────────────
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
gamemode=adventure
max-players=8
motd=AstralClash DEV
level-name=world
spawn-protection=0
view-distance=10
EOF

# ── bukkit.yml — отключаем спавн мобов для чистоты ───────────────────────────
cat > "$SERVER_DIR/bukkit.yml" <<'EOF'
spawn-limits:
  monsters: 0
  animals: 0
  water-animals: 0
  ambient: 0
EOF

# ── Собираем плагин и копируем jar ───────────────────────────────────────────
echo "==> Сборка AstralClash (mvn package) ..."
(cd "$(dirname "$0")/.." && mvn package -q -DskipTests)

PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*-shaded.jar" | head -1)
if [ -z "$PLUGIN_JAR" ]; then
  PLUGIN_JAR=$(find "$(dirname "$0")/../target" -name "AstralClash-*.jar" ! -name "*-sources.jar" | head -1)
fi
echo "==> Копируем $PLUGIN_JAR -> plugins/"
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/"

echo ""
echo "┌─────────────────────────────────────────────────────┐"
echo "│  Готово! Положи в $SERVER_DIR/plugins/ вручную:     │"
echo "│    • ModelEngine-R4.x.jar  (купить на SpigotMC)     │"
echo "│    • packetevents-spigot-2.x.jar                    │"
echo "│  Затем запускай: bash dev-server/start.sh           │"
echo "└─────────────────────────────────────────────────────┘"
