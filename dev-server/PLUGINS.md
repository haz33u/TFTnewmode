# Плагины для dev-сервера AstralClash

Все jar-файлы положи в папку **`dev-server/server/plugins/`**.

---

## Обязательные

| Плагин | Где скачать | Файл |
|--------|-------------|------|
| **PlugManX** | https://github.com/TheBlackEntity/PlugManX/releases | `PlugManX-3.x.x.jar` |

> **PacketEvents** — теперь встроен в AstralClash, отдельно ставить не нужно.

PlugManX нужен для hot-reload без перезапуска сервера (`reload-plugin.sh`).

---

## Опциональные (3D-модели чемпионов)

| Плагин | Где скачать | Файл |
|--------|-------------|------|
| **ModelEngine R4** | https://www.spigotmc.org/resources/modelengine.79477/ (платный) | `ModelEngine-R4.x.x.jar` |

Без ModelEngine плагин работает, но чемпионы показываются обычными предметами вместо 3D-моделей.

---

## После добавления плагинов

1. Запусти сервер: `bash dev-server/start.sh` или `.\dev-server\start.ps1`
2. Для resource-pack: в отдельном терминале `python -m http.server 8765 --directory dev-server/server`
3. Сборка пакa: `bash resource-pack/pack.sh` (из корня проекта)
