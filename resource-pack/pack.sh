#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AstralClash Resource Pack Builder
# Запускать из корня проекта: bash resource-pack/pack.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PACK_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_ZIP="$PACK_DIR/../dev-server/server/astralclash-pack.zip"

echo "==> Генерируем placeholder текстуры (если нет реальных)..."
python3 "$PACK_DIR/generate_textures.py"

echo "==> Упаковываем в ZIP..."
rm -f "$OUT_ZIP"
cd "$PACK_DIR"
zip -r "$OUT_ZIP" pack.mcmeta pack.png assets/ -x "*.py" -x "*.sh" -x "*.DS_Store" -x "__pycache__/*"

SHA1=$(sha1sum "$OUT_ZIP" | awk '{print $1}')
echo "==> Готово: $OUT_ZIP"
echo "==> SHA1:   $SHA1"

# ── Записываем настройки в server.properties ──────────────────────────────
PROPS="$PACK_DIR/../dev-server/server/server.properties"
if [ -f "$PROPS" ]; then
  # Убираем старые строки и дописываем новые
  sed -i '/^resource-pack=/d;/^resource-pack-sha1=/d;/^resource-pack-prompt=/d' "$PROPS"
  cat >> "$PROPS" <<EOF
resource-pack=http://localhost:8765/astralclash-pack.zip
resource-pack-sha1=${SHA1}
resource-pack-prompt=\u00a75AstralClash \u00a77requires the resource pack for champion icons
EOF
  echo "==> server.properties обновлён (resource-pack URL прописан)"
fi

echo ""
echo "┌──────────────────────────────────────────────────────────────┐"
echo "│  Следующий шаг: запусти локальный HTTP-сервер (в отдельном  │"
echo "│  терминале) чтобы Minecraft мог скачать пак:                 │"
echo "│                                                              │"
echo "│    python3 -m http.server 8765 --directory dev-server/server │"
echo "│                                                              │"
echo "│  Затем запусти сервер: bash dev-server/start.sh              │"
echo "│  При входе клиент скачает и применит ресурс-пак.            │"
echo "└──────────────────────────────────────────────────────────────┘"
