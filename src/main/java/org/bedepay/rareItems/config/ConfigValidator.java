package org.bedepay.rareItems.config;

import org.bedepay.rareItems.RareItems;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Валидатор конфигурации для предотвращения ошибок
 * Проверяет корректность всех настроек перед загрузкой
 */
public class ConfigValidator {
    
    private final RareItems plugin;
    private final Logger logger;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    
    public ConfigValidator(RareItems plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * Валидирует весь конфиг
     * @param config конфигурация для проверки
     * @return true если конфиг валиден
     */
    public boolean validate(FileConfiguration config) {
        errors.clear();
        warnings.clear();
        
        validateSettings(config);
        validateRarities(config);
        validateCraftChances(config);
        validateCompatibility(config);
        validateSpecialAbilities(config);
        
        // Выводим результаты
        if (!warnings.isEmpty()) {
            logger.warning("Найдены предупреждения в конфигурации:");
            warnings.forEach(logger::warning);
        }
        
        if (!errors.isEmpty()) {
            logger.severe("Найдены ошибки в конфигурации:");
            errors.forEach(logger::severe);
            return false;
        }
        
        logger.info("Конфигурация прошла валидацию успешно!");
        return true;
    }
    
    /**
     * Валидирует основные настройки
     */
    private void validateSettings(FileConfiguration config) {
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings == null) {
            errors.add("Секция 'settings' отсутствует в конфигурации");
            return;
        }
        
        // Проверяем обязательные настройки
        if (!settings.contains("enabled")) {
            warnings.add("Отсутствует настройка 'settings.enabled', используется значение по умолчанию: true");
        }
        
        if (!settings.contains("debug")) {
            warnings.add("Отсутствует настройка 'settings.debug', используется значение по умолчанию: false");
        }
        
        // Проверяем сообщение крафта
        String craftMessage = settings.getString("craftMessage");
        if (craftMessage != null && !craftMessage.contains("%rarity%")) {
            warnings.add("Сообщение крафта не содержит переменную %rarity%");
        }
        
        // Проверяем звук крафта
        String craftSound = settings.getString("craftSound");
        if (craftSound != null && !craftSound.equals("NONE")) {
            try {
                org.bukkit.Sound.valueOf(craftSound.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("Неверный звук крафта: " + craftSound);
            }
        }
    }
    
    /**
     * Валидирует редкости
     */
    private void validateRarities(FileConfiguration config) {
        ConfigurationSection raritiesSection = config.getConfigurationSection("rarities");
        if (raritiesSection == null) {
            errors.add("Секция 'rarities' отсутствует в конфигурации");
            return;
        }
        
        if (raritiesSection.getKeys(false).isEmpty()) {
            errors.add("Не определена ни одна редкость");
            return;
        }
        
        for (String rarityId : raritiesSection.getKeys(false)) {
            validateSingleRarity(raritiesSection, rarityId);
        }
    }
    
    /**
     * Валидирует одну редкость
     */
    private void validateSingleRarity(ConfigurationSection raritiesSection, String rarityId) {
        ConfigurationSection rarity = raritiesSection.getConfigurationSection(rarityId);
        if (rarity == null) {
            errors.add("Редкость '" + rarityId + "' не является секцией конфигурации");
            return;
        }
        
        // Проверяем имя
        if (!rarity.contains("name")) {
            warnings.add("Редкость '" + rarityId + "' не имеет имени, будет использован ID");
        }
        
        // Проверяем цвет
        String color = rarity.getString("color");
        if (color == null) {
            warnings.add("Редкость '" + rarityId + "' не имеет цвета, будет использован белый");
        } else if (color.startsWith("#") && color.length() != 7) {
            errors.add("Неверный hex цвет для редкости '" + rarityId + "': " + color);
        }
        
        // Валидируем атрибуты
        validateAttributes(rarity, rarityId);
        
        // Валидируем эффекты
        validateEffects(rarity, rarityId);
        
        // Валидируем звуки и частицы
        validateSoundsAndParticles(rarity, rarityId);
    }
    
    /**
     * Валидирует атрибуты редкости
     */
    private void validateAttributes(ConfigurationSection rarity, String rarityId) {
        ConfigurationSection attributes = rarity.getConfigurationSection("attributes");
        if (attributes == null) {
            warnings.add("Редкость '" + rarityId + "' не имеет атрибутов");
            return;
        }
        
        // Проверяем числовые значения
        validateNumberAttribute(attributes, rarityId, "damage", 0, 100);
        validateNumberAttribute(attributes, rarityId, "armor", 0, 100);
        validateNumberAttribute(attributes, rarityId, "speed", 0, 1);
        validateNumberAttribute(attributes, rarityId, "toughness", 0, 100);
        validateNumberAttribute(attributes, rarityId, "health", 0, 100);
        validateNumberAttribute(attributes, rarityId, "luck", 0, 100);
    }
    
    /**
     * Валидирует числовой атрибут
     */
    private void validateNumberAttribute(ConfigurationSection attributes, String rarityId, 
                                       String attributeName, double min, double max) {
        if (attributes.contains(attributeName)) {
            double value = attributes.getDouble(attributeName);
            if (value < min || value > max) {
                warnings.add("Атрибут '" + attributeName + "' редкости '" + rarityId + 
                           "' имеет значение " + value + " (рекомендуется: " + min + "-" + max + ")");
            }
        }
    }
    
    /**
     * Валидирует эффекты при ударе
     */
    private void validateEffects(ConfigurationSection rarity, String rarityId) {
        ConfigurationSection effects = rarity.getConfigurationSection("onHitEffects");
        if (effects != null) {
            for (String effectName : effects.getKeys(false)) {
                try {
                    PotionEffectType effectType = PotionEffectType.getByName(effectName.toUpperCase());
                    if (effectType == null) {
                        errors.add("Неверный эффект '" + effectName + "' для редкости '" + rarityId + "'");
                    }
                } catch (Exception e) {
                    errors.add("Ошибка при проверке эффекта '" + effectName + "' для редкости '" + rarityId + "'");
                }
            }
        }
        
        // Проверяем шанс и кулдаун эффектов
        double effectChance = rarity.getDouble("effectChance", 0);
        if (effectChance < 0 || effectChance > 100) {
            warnings.add("Шанс эффекта для редкости '" + rarityId + "' должен быть от 0 до 100");
        }
        
        int effectCooldown = rarity.getInt("effectCooldown", 0);
        if (effectCooldown < 0) {
            warnings.add("Кулдаун эффекта для редкости '" + rarityId + "' не может быть отрицательным");
        }
    }
    
    /**
     * Валидирует звуки и частицы
     */
    private void validateSoundsAndParticles(ConfigurationSection rarity, String rarityId) {
        String sound = rarity.getString("sound");
        if (sound != null && !sound.equals("NONE")) {
            try {
                org.bukkit.Sound.valueOf(sound.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("Неверный звук '" + sound + "' для редкости '" + rarityId + "'");
            }
        }
        
        String particle = rarity.getString("particle");
        if (particle != null && !particle.equals("NONE")) {
            try {
                org.bukkit.Particle.valueOf(particle.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("Неверная частица '" + particle + "' для редкости '" + rarityId + "'");
            }
        }
    }
    
    /**
     * Валидирует шансы крафта
     */
    private void validateCraftChances(FileConfiguration config) {
        ConfigurationSection chances = config.getConfigurationSection("craftChances");
        if (chances == null) {
            warnings.add("Секция 'craftChances' отсутствует");
            return;
        }
        
        ConfigurationSection rarities = config.getConfigurationSection("rarities");
        if (rarities == null) return;
        
        double totalChance = 0;
        
        for (String rarityId : chances.getKeys(false)) {
            // Проверяем, что редкость существует
            if (!rarities.contains(rarityId)) {
                warnings.add("Шанс крафта определен для несуществующей редкости: " + rarityId);
                continue;
            }
            
            double chance = chances.getDouble(rarityId);
            if (chance < 0 || chance > 100) {
                errors.add("Шанс крафта для '" + rarityId + "' должен быть от 0 до 100, получен: " + chance);
            }
            
            totalChance += chance;
        }
        
        if (totalChance > 100) {
            warnings.add("Общий шанс получения редких предметов превышает 100%: " + totalChance + "%");
        }
        
        // Проверяем, что для всех редкостей определены шансы
        for (String rarityId : rarities.getKeys(false)) {
            if (!chances.contains(rarityId)) {
                warnings.add("Не определен шанс крафта для редкости: " + rarityId);
            }
        }
    }
    
    /**
     * Валидирует настройки совместимости
     */
    private void validateCompatibility(FileConfiguration config) {
        ConfigurationSection compatibility = config.getConfigurationSection("compatibility");
        if (compatibility == null) {
            warnings.add("Секция 'compatibility' отсутствует, используются настройки по умолчанию");
        }
    }
    
    /**
     * Валидирует специальные способности
     */
    private void validateSpecialAbilities(FileConfiguration config) {
        ConfigurationSection abilities = config.getConfigurationSection("specialAbilities");
        if (abilities == null) {
            warnings.add("Секция 'specialAbilities' отсутствует");
            return;
        }
        
        // Проверяем основные настройки способностей
        if (abilities.contains("swords.criticalHitMultiplier")) {
            double multiplier = abilities.getDouble("swords.criticalHitMultiplier");
            if (multiplier < 1.0 || multiplier > 5.0) {
                warnings.add("Множитель критического удара должен быть от 1.0 до 5.0, получен: " + multiplier);
            }
        }
        
        if (abilities.contains("boots.teleportCooldown")) {
            int cooldown = abilities.getInt("boots.teleportCooldown");
            if (cooldown < 1000) {
                warnings.add("Кулдаун телепорта слишком мал (< 1 секунды), может вызвать спам");
            }
        }
    }
    
    /**
     * Получает список ошибок
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    /**
     * Получает список предупреждений
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
} 