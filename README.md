# RareItems v3.0 - Современный RPG плагин для Minecraft

![Version](https://img.shields.io/badge/version-3.0-blue.svg)
![Paper](https://img.shields.io/badge/Paper-1.20+-green.svg)
![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)

> Продвинутый плагин для создания редких предметов с уникальными способностями, современной архитектурой и Adventure API

## ✨ Что нового в версии 3.0

### 🚀 Современная архитектура команд
- **Полностью переписанная система команд** с улучшенным UX
- **Кэширование Tab Completion** для лучшей производительности  
- **Умное автодополнение** для всех аргументов команд
- **Современные сообщения** с эмодзи и цветовым кодированием

### 🎨 Улучшенный пользовательский интерфейс
- **Adventure API** для современных текстовых компонентов
- **Красивые информационные панели** с границами и эмодзи
- **Цветовая индикация** для различных типов сообщений
- **Детальная информация** о предметах и редкостях

### 🔧 Расширенная функциональность
- **Валидация входных данных** с понятными сообщениями об ошибках
- **Гибкие опции отладки** (true/false, on/off, enable/disable)
- **Автоматическое управление инвентарем** при выдаче предметов
- **Поддержка количества** при выдаче предметов (1-64)

## 📋 Команды

### Основные команды

| Команда | Описание | Разрешение |
|---------|----------|------------|
| `/rareitems help` | Показать справку по командам | Нет |
| `/rareitems reload` | Перезагрузить конфигурацию | `rareitems.admin.reload` |
| `/rareitems info [редкость]` | Информация о редкостях | `rareitems.admin.info` |
| `/rareitems give <игрок> <материал> <редкость> [количество]` | Выдать редкий предмет | `rareitems.admin.give` |
| `/rareitems inspect` | Проверить предмет в руке | `rareitems.inspect` |
| `/rareitems upgrade <редкость>` | Улучшить предмет до редкости | `rareitems.admin.upgrade` |
| `/rareitems debug [опция]` | Управление режимом отладки | `rareitems.admin.debug` |

### Примеры использования

```bash
# Выдать алмазный меч эпической редкости игроку Steve
/rareitems give Steve diamond_sword epic

# Выдать 5 железных шлемов редкой редкости
/rareitems give Steve iron_helmet rare 5

# Посмотреть информацию о легендарной редкости
/rareitems info legendary

# Включить режим отладки
/rareitems debug on

# Улучшить предмет в руке до мифической редкости
/rareitems upgrade mythic
```

## 🎯 Особенности Tab Completion

Система автодополнения поддерживает:

- **Подкоманды** с фильтрацией по разрешениям
- **Имена онлайн игроков** для команды give
- **Материалы оружия и брони** с автофильтрацией
- **ID редкостей** из конфигурации
- **Количество предметов** (предустановленные значения: 1, 8, 16, 32, 64)
- **Опции отладки** (true, false, on, off, enable, disable)

## ⚙️ Конфигурация

### Базовые настройки

```yaml
settings:
  enabled: true
  debug: false
  craftMessage: "&aВы создали %rarity% предмет!"
  craftSound: "ENTITY_PLAYER_LEVELUP"

rarities:
  common:
    name: "Обычный"
    color: "WHITE"
    attributes:
      damage: 0
      armor: 0
      speed: 0
      toughness: 0
      attackSpeed: 0
      health: 0
      luck: 0
    enchantments: []
    onHitEffects: {}
    effectCooldown: 5000
    effectChance: 100.0
    particle: "NONE"
    sound: "NONE"
    specialAbilities:
      knockbackResistance: 0.0
      durabilityMultiplier: 1.0
      canCraft: true
      canFindInDungeons: false

craftChances:
  common: 70.0
  rare: 20.0
  epic: 8.0
  legendary: 2.0
```

### Новые атрибуты в v3.0

- **knockbackResistance** - сопротивление отбрасыванию (0.0-1.0)
- **durabilityMultiplier** - множитель прочности (>0.0)
- **canCraft** - доступность в крафте (true/false)
- **canFindInDungeons** - возможность найти в подземельях (true/false)

## 🔑 Разрешения

### Административные

| Разрешение | Описание |
|------------|----------|
| `rareitems.admin` | Полный доступ (наследует все админские права) |
| `rareitems.admin.reload` | Перезагрузка конфигурации |
| `rareitems.admin.info` | Просмотр информации о редкостях |
| `rareitems.admin.give` | Выдача предметов |
| `rareitems.admin.upgrade` | Улучшение предметов |
| `rareitems.admin.debug` | Управление отладкой |

### Пользовательские

| Разрешение | Описание |
|------------|----------|
| `rareitems.inspect` | Проверка предметов в руке |

## 🛠️ Разработка

### Требования

- **Java 17+**
- **Paper 1.20+**
- **Gradle 8.0+**

### Сборка

```bash
./gradlew clean build
```

### Запуск тестового сервера

```bash
./gradlew runServer
```

## 📈 Производительность

### Оптимизации v3.0

- **Кэширование Tab Completion** (обновление каждые 30 секунд)
- **Ленивая инициализация** конфигурации
- **Эффективная валидация** входных данных
- **Минимальные аллокации** в горячих путях

## 🔄 Миграция с v2.0

Версия 3.0 полностью совместима с конфигурацией v2.0. Новые функции добавлены через опциональные поля в `specialAbilities`.

### Что изменилось:
- ✅ **Все старые команды работают**
- ✅ **Конфигурация совместима**
- ✅ **Разрешения не изменились**
- ✅ **API остается обратно совместимым**

### Что улучшилось:
- 🚀 **Более быстрое Tab Completion**
- 🎨 **Лучший пользовательский интерфейс**
- 🔧 **Расширенная отладка**
- 📱 **Современные сообщения**

## 🤝 Вклад в проект

Мы приветствуем вклад в развитие проекта! Пожалуйста:

1. Форкните репозиторий
2. Создайте feature branch (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Запушьте в branch (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📝 Лицензия

Этот проект лицензирован под MIT License - см. файл [LICENSE](LICENSE) для деталей.

## 👨‍💻 Автор

**BedePay**
- GitHub: [@BedePay](https://github.com/BedePay)

## 📞 Поддержка

- 🐛 [Сообщить о баге](https://github.com/BedePay/RareItems/issues)
- 💡 [Предложить функцию](https://github.com/BedePay/RareItems/issues)
- 📖 [Wiki документация](https://github.com/BedePay/RareItems/wiki)

---

<div align="center">

**Сделано с ❤️ для Minecraft сообщества**

![Minecraft](https://img.shields.io/badge/Minecraft-green.svg?logo=minecraft)
![Paper](https://img.shields.io/badge/Paper-blue.svg)
![Java](https://img.shields.io/badge/Java-orange.svg?logo=java)

</div> 