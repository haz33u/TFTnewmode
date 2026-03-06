# AstralClash — Honkai: Star Rail × TFT для Minecraft

TFT-style авто-шахматы в Minecraft 1.21.4 с персонажами из Honkai: Star Rail.

---

## Что делает плагин

| Фича | Статус |
|---|---|
| Lobby / матчмейкинг (2–8 игроков) | ✅ |
| Гексагональная 4×7 доска каждого игрока | ✅ |
| 16 чемпионов (Kafka, Blade, Seele…) | ✅ |
| Система черт (Nihility, Hunt, Preservation…) | ✅ |
| Боевая симуляция (урон, баффы, дот) | ✅ |
| Магазин чемпионов + 3-star система | ✅ |
| SQLite / MySQL | ✅ |
| 3D-модели через ModelEngine R4 | ✅ |
| PacketEvents для визуальных эффектов | ✅ |

---

## ЧТО НУЖНО ТЕБЕ (шаги вручную)

### 1. Скачать и установить зависимости

#### A. Java 21
```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk
java -version  # должно быть 21+
```

#### B. Maven (для сборки)
```bash
sudo apt install maven
# или скачай с https://maven.apache.org/download.cgi
```

#### C. Paper 1.21.4 (сервер)
- Сайт: **https://papermc.io/downloads/paper**
- Выбери версию **1.21.4** → скачай последний build
- Сохрани как `dev-server/server/paper-1.21.4.jar`

> Либо запусти `bash dev-server/setup.sh` — он скачает сам через API.

#### D. ModelEngine R4 (ПЛАТНЫЙ, нужен для 3D-моделей)
- Купить: https://www.spigotmc.org/resources/modelengine.79477/
- Положи `ModelEngine-R4.x.x.jar` в `dev-server/server/plugins/`
- Зарегистрируй в Maven локально:
```bash
mvn install:install-file \
  -Dfile=ModelEngine-R4.x.x.jar \
  -DgroupId=com.ticxo.modelengine \
  -DartifactId=ModelEngine \
  -Dversion=R4.0 \
  -Dpackaging=jar
```

#### E. PacketEvents (бесплатный)
- Сайт: https://www.spigotmc.org/resources/packetevents-api.80279/
- Либо автоматически подтянется через Maven (scope=provided)
- Положи `packetevents-spigot-2.x.x.jar` в `dev-server/server/plugins/`

---

### 2. Сборка проекта

```bash
# Из корня проекта:
mvn clean package -DskipTests

# Shaded jar (включает SQLite + HikariCP):
ls target/AstralClash-*-shaded.jar
```

### 3. Первый запуск

```bash
# Один раз — первичная настройка + скачивание Paper:
bash dev-server/setup.sh

# Каждый раз для разработки:
bash dev-server/start.sh
```

**Windows (PowerShell):**
```powershell
# Один раз:
.\dev-server\setup.ps1

# Положи плагины в dev-server\server\plugins\ (см. dev-server\PLUGINS.md)

# Каждый раз:
.\dev-server\start.ps1
```

Resource-pack (иконки чемпионов):
```powershell
.\resource-pack\pack.ps1
# В другом терминале: cd dev-server\server; python -m http.server 8765
```

---

## КАК ДЕБАЖИТЬ

### Вариант 1: Remote Debugging через IntelliJ IDEA (рекомендуется)

1. `start.sh` уже запускает JVM с флагом:
   ```
   -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
   ```

2. В IntelliJ: **Run → Edit Configurations → Add → Remote JVM Debug**
   - Host: `localhost`
   - Port: `5005`

3. Ставь breakpoints прямо в коде — они сработают во время игры.

### Вариант 2: Логи в консоль

В любом классе:
```java
plugin.getLogger().info("DEBUG: phase=" + phase + " players=" + players.size());
```

Для Bukkit-событий используй `Bukkit.getLogger()`.

### Вариант 3: Hot-reload без перезапуска

1. Установи **PlugManX**: https://www.spigotmc.org/resources/plugmanx.88135/
2. Установи **rcon-cli**: https://github.com/gorcon/rcon-cli/releases
3. В `server.properties` добавь:
   ```properties
   enable-rcon=true
   rcon.port=25575
   rcon.password=devpass
   ```
4. Затем для быстрого обновления:
   ```bash
   bash dev-server/reload-plugin.sh
   ```
   Это перекомпилирует и перезагрузит плагин **без перезапуска сервера**.

---

## КАК ТЕСТИРОВАТЬ ИГРУ

### Тестирование одному (без второго игрока)

1. Установи **ViaFabricPlus** или используй **Fake Players** через плагин.
2. Либо добавь команду `/astraladmin forcestart` (уже есть в AdminCommand).

#### Рекомендуемый способ — плагин фейковых игроков:
- **ServerSidePlayer** или **FakeOnline**: создаёт ботов в лобби
- `/astraladmin addbot <count>` — добавить N ботов в очередь

### Тестирование вдвоём с другом
- В `server.properties`: `online-mode=false`
- Можно подключаться с пиратского клиента или разных аккаунтов
- Клиент: **Minecraft Java Edition 1.21.4**
  - Лаунчер: официальный или **Prism Launcher** (бесплатный, удобный)

### Последовательность проверки игры

```
1. /astral join         — войти в очередь
2. (ждём 2+ игроков или /astraladmin forcestart)
3. В фазе Planning — drag&drop чемпионов на доску (будущее UI)
4. /astral shop         — открыть магазин
5. Фаза Combat — автоматически начнётся через таймер
6. Смотри в консоль — там вся боевая симуляция пишется в лог
```

---

## Структура проекта

```
src/main/java/dev/astralclash/
├── AstralClash.java          ← точка входа
├── board/                    ← 4×7 доска игрока
├── champion/
│   ├── impl/                 ← 16 персонажей (Kafka, Blade, Seele…)
│   ├── trait/                ← черты и их бонусы
│   └── ability/              ← интерфейс способностей
├── combat/                   ← боевая симуляция
├── game/                     ← GameManager, RoundManager, GamePhase
├── shop/                     ← магазин + пул чемпионов
├── economy/                  ← золото
├── player/                   ← ArenaPlayer, PlayerManager
├── ui/                       ← GUI (инвентарь-меню)
├── database/                 ← SQLite/MySQL через HikariCP
├── commands/                 ← /astral и /astraladmin
├── listeners/                ← Bukkit event listeners
└── config/                   ← ConfigManager

dev-server/
├── setup.sh                  ← первичная настройка + скачивание Paper
├── start.sh                  ← запуск с remote debug
└── reload-plugin.sh          ← hot-reload через RCON + PlugManX
```

---

## Зависимости — краткая таблица

| Зависимость | Где взять | Скоуп |
|---|---|---|
| Paper 1.21.4 | papermc.io | сервер |
| ModelEngine R4 | spigotmc.org (платно) | `provided` |
| PacketEvents 2.x | spigotmc.org | `provided` |
| SQLite JDBC | Maven Central | `compile` (shade) |
| HikariCP | Maven Central | `compile` (shade) |

---

## Быстрый старт (минимум шагов)

```bash
# 1. Зарегистрируй ModelEngine в локальном Maven (после покупки)
mvn install:install-file -Dfile=ModelEngine-R4.x.x.jar \
  -DgroupId=com.ticxo.modelengine -DartifactId=ModelEngine -Dversion=R4.0 -Dpackaging=jar

# 2. Первичная настройка
bash dev-server/setup.sh

# 3. Скопируй ModelEngine.jar и PacketEvents.jar в dev-server/server/plugins/

# 4. Запуск сервера с дебагом
bash dev-server/start.sh

# 5. В IntelliJ подключись на localhost:5005 (Remote JVM Debug)
```
