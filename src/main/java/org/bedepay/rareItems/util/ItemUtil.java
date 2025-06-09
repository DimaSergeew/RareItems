package org.bedepay.rareItems.util;

import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class ItemUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    /**
     * Creates a NamespacedKey for the plugin
     */
    public static NamespacedKey getKey(Plugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }
    
    /**
     * Проверяет, является ли материал оружием используя Paper API
     */
    private static boolean isWeapon(Material material, RareItems plugin) {
        MaterialTypeChecker checker = new MaterialTypeChecker(plugin);
        return checker.isWeapon(material);
    }
    
    /**
     * Проверяет, является ли материал броней используя Paper API
     */
    private static boolean isArmor(Material material, RareItems plugin) {
        MaterialTypeChecker checker = new MaterialTypeChecker(plugin);
        return checker.isArmor(material);
    }
    
    /**
     * Применяет редкость к предмету с улучшенным UI и правильными атрибутами
     */
    public static ItemStack applyRarity(RareItems plugin, ItemStack item, Rarity rarity) {
        ItemStack clonedItem = item.clone();
        ItemMeta meta = clonedItem.getItemMeta();
        
        if (meta == null) {
            return clonedItem;
        }
        
        // Получаем базовые атрибуты предмета из Paper API
        Map<Attribute, Double> baseAttributes = getBaseAttributes(clonedItem.getType());
        
        // Создаем красивое название с градиентом
        Component displayName = createDisplayName(clonedItem.getType(), rarity);
        meta.displayName(displayName);
        
        // Создаем красивое описание
        List<Component> lore = createLore(rarity, baseAttributes, clonedItem.getType(), plugin);
        meta.lore(lore);
        
        // Сохраняем редкость в NBT
        meta.getPersistentDataContainer().set(
                        getKey(plugin, "rarity"),
        PersistentDataType.STRING,
        rarity.id()
        );
        
        // Применяем правильные атрибуты (добавляем к базовым, а не заменяем)
        applyCorrectAttributes(plugin, meta, clonedItem.getType(), rarity, baseAttributes);
        
        clonedItem.setItemMeta(meta);
        return clonedItem;
    }
    
    /**
     * Получает редкость предмета из NBT
     */
    public static Rarity getRarity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        String rarityId = meta.getPersistentDataContainer().get(
                new NamespacedKey("rareitems", "rarity"),
                PersistentDataType.STRING
        );
        
        if (rarityId == null) {
            return null;
        }
        
        // Получаем плагин через Bukkit для доступа к ConfigManager
        RareItems plugin = (RareItems) org.bukkit.Bukkit.getPluginManager().getPlugin("RareItems");
        if (plugin == null) {
            return null;
        }
        
        return plugin.getConfigManager().getRarityById(rarityId);
    }
    
    private static Component createDisplayName(Material material, Rarity rarity) {
        String materialName = material.name().toLowerCase().replace('_', ' ');
        String capitalizedName = capitalizeWords(materialName);
        
        // Создаем простое название с цветом редкости
        Component rarityComponent = Component.text(rarity.name() + " ")
                .color(rarity.color())
                .decoration(TextDecoration.ITALIC, false);
        
        Component nameComponent = Component.text(capitalizedName)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        
        return rarityComponent.append(nameComponent);
    }
    
    private static List<Component> createLore(Rarity rarity, Map<Attribute, Double> baseAttributes, Material material, RareItems plugin) {
        List<Component> lore = new ArrayList<>();
        
        // Заголовок редкости с звездами
        String stars = getRarityStars(rarity.id());
        Component rarityHeader = Component.text(stars + " " + rarity.name() + " " + stars)
                .color(rarity.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        lore.add(rarityHeader);
        
        // Компактная строка с основными бонусами
        List<String> bonuses = new ArrayList<>();
        
        if (rarity.damageBonus() > 0 && isWeapon(material, plugin)) {
            // Для оружия показываем итоговый урон
            double baseDamage = baseAttributes.getOrDefault(Attribute.GENERIC_ATTACK_DAMAGE, 0.0);
            double totalDamage = baseDamage + rarity.damageBonus();
            bonuses.add(formatBonus(totalDamage) + "⚔ (+" + formatBonus(rarity.damageBonus()) + ")");
        }
        if (rarity.armorBonus() > 0 && isArmor(material, plugin)) {
            // Для брони показываем итоговую защиту
            double baseArmor = baseAttributes.getOrDefault(Attribute.GENERIC_ARMOR, 0.0);
            double totalArmor = baseArmor + rarity.armorBonus();
            bonuses.add(formatBonus(totalArmor) + "🛡 (+" + formatBonus(rarity.armorBonus()) + ")");
        }
        if (rarity.healthBonus() > 0) {
            bonuses.add("+" + formatBonus(rarity.healthBonus()) + "❤");
        }
        if (rarity.luckBonus() > 0) {
            bonuses.add("+" + formatBonus(rarity.luckBonus()) + "🍀");
        }
        
        if (!bonuses.isEmpty()) {
            Component bonusLine = Component.text("Бонусы: " + String.join(" ", bonuses))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(bonusLine);
        }
        
        // Уникальные способности для типа предмета
        String itemTypeAbility = getItemTypeAbility(material, rarity);
        if (itemTypeAbility != null) {
            Component abilityLine = Component.text("✨ " + itemTypeAbility)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(abilityLine);
        }
        
        // Общие эффекты при ударе (только если есть)
        if (!rarity.onHitEffects().isEmpty()) {
            StringBuilder effects = new StringBuilder();
            rarity.onHitEffects().forEach((effect, amplifier) -> {
                if (effects.length() > 0) effects.append(", ");
                effects.append(getShortEffectName(effect.getName()));
            });
            
            Component effectLine = Component.text("⚡ " + effects.toString() + " (" + (int)rarity.effectChance() + "%)")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(effectLine);
        }
        
        return lore;
    }
    
    private static String getRarityStars(String rarityId) {
        return switch (rarityId) {
            case "celestial" -> "✦✦✦✦✦✦✦✦";
            case "divine" -> "✦✦✦✦✦✦✦";
            case "mythic" -> "✦✦✦✦✦✦";
            case "legendary" -> "✦✦✦✦✦";
            case "epic" -> "✦✦✦✦";
            case "rare" -> "✦✦✦";
            case "uncommon" -> "✦✦";
            default -> "✦";
        };
    }
    
    private static String getEffectName(String effectKey) {
        return switch (effectKey.toLowerCase()) {
            case "poison" -> "Отравление";
            case "slowness" -> "Замедление";
            case "weakness" -> "Слабость";
            case "wither" -> "Иссушение";
            case "blindness" -> "Слепота";
            case "nausea" -> "Тошнота";
            case "levitation" -> "Левитация";
            case "unluck" -> "Неудача";
            case "slow_falling" -> "Медленное падение";
            default -> effectKey;
        };
    }
    
    private static String getShortEffectName(String effectKey) {
        return switch (effectKey.toLowerCase()) {
            case "poison" -> "Яд";
            case "slowness" -> "Замедление";
            case "weakness" -> "Слабость";
            case "wither" -> "Иссушение";
            case "blindness" -> "Слепота";
            case "nausea" -> "Тошнота";
            case "levitation" -> "Левитация";
            case "unluck" -> "Неудача";
            default -> effectKey;
        };
    }
    
    /**
     * Получает уникальную способность для типа предмета
     */
    private static String getItemTypeAbility(Material material, Rarity rarity) {
        String materialName = material.name();
        String rarityId = rarity.id();
        
        // Мечи - урон и критические удары
        if (materialName.endsWith("_SWORD")) {
            return switch (rarityId) {
                case "uncommon" -> "Острое лезвие (+5% крит)";
                case "rare" -> "Точный удар (+10% крит)";
                case "epic" -> "Мастерский удар (+15% крит)";
                case "legendary" -> "Смертоносное лезвие (+20% крит)";
                case "mythic" -> "Легендарная заточка (+25% крит)";
                case "divine" -> "Божественная острота (+30% крит)";
                case "celestial" -> "Небесное лезвие (+35% крит)";
                default -> null;
            };
        }
        
        // Топоры - замедление и урон по щитам
        if (materialName.endsWith("_AXE")) {
            return switch (rarityId) {
                case "uncommon" -> "Тяжелый удар";
                case "rare" -> "Сокрушение щитов";
                case "epic" -> "Оглушающий удар";
                case "legendary" -> "Сокрушение брони";
                case "mythic" -> "Разрушитель защиты";
                case "divine" -> "Божественная мощь";
                case "celestial" -> "Небесный молот";
                default -> null;
            };
        }
        
        // Трезубцы - водные эффекты
        if (materialName.equals("TRIDENT")) {
            return switch (rarityId) {
                case "uncommon" -> "Водная стихия";
                case "rare" -> "Притягивание врагов";
                case "epic" -> "Молния при броске";
                case "legendary" -> "Водный вихрь";
                case "mythic" -> "Буря и молнии";
                case "divine" -> "Потоп";
                case "celestial" -> "Владыка морей";
                default -> null;
            };
        }
        
        // Луки - скорость и пробивание
        if (materialName.equals("BOW") || materialName.equals("CROSSBOW")) {
            return switch (rarityId) {
                case "uncommon" -> "Быстрая стрельба";
                case "rare" -> "Точный выстрел";
                case "epic" -> "Пробивание брони";
                case "legendary" -> "Огненные стрелы";
                case "mythic" -> "Взрывные стрелы";
                case "divine" -> "Божественный выстрел";
                case "celestial" -> "Небесные стрелы";
                default -> null;
            };
        }
        
        // Шлемы - защита головы и видение
        if (materialName.endsWith("_HELMET")) {
            return switch (rarityId) {
                case "uncommon" -> "Ясное зрение";
                case "rare" -> "Ночное видение";
                case "epic" -> "Защита от слепоты";
                case "legendary" -> "Водное дыхание";
                case "mythic" -> "Огненный иммунитет";
                case "divine" -> "Телепатия";
                case "celestial" -> "Всевидящее око";
                default -> null;
            };
        }
        
        // Нагрудники - защита тела и регенерация
        if (materialName.endsWith("_CHESTPLATE")) {
            return switch (rarityId) {
                case "uncommon" -> "Прочная защита";
                case "rare" -> "Быстрое восстановление";
                case "epic" -> "Отражение урона";
                case "legendary" -> "Регенерация";
                case "mythic" -> "Барьер силы";
                case "divine" -> "Божественная защита";
                case "celestial" -> "Небесная броня";
                default -> null;
            };
        }
        
        // Поножи - защита ног и мобильность
        if (materialName.endsWith("_LEGGINGS")) {
            return switch (rarityId) {
                case "uncommon" -> "Устойчивость";
                case "rare" -> "Быстрый бег";
                case "epic" -> "Высокий прыжок";
                case "legendary" -> "Неуязвимость к отбросу";
                case "mythic" -> "Фазовый шаг";
                case "divine" -> "Телепортация";
                case "celestial" -> "Измерение пространства";
                default -> null;
            };
        }
        
        // Ботинки - передвижение и спецэффекты
        if (materialName.endsWith("_BOOTS")) {
            return switch (rarityId) {
                case "uncommon" -> "Легкая походка";
                case "rare" -> "Хождение по воде";
                case "epic" -> "Ледяная дорожка";
                case "legendary" -> "Огненные следы";
                case "mythic" -> "Полет";
                case "divine" -> "Хождение по лаве";
                case "celestial" -> "Телепорт при приседании";
                default -> null;
            };
        }
        
        return null;
    }
    
    private static String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
    
    private static String capitalizeWords(String str) {
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Форматирует бонус для отображения (показывает десятичные числа если меньше 1)
     */
    private static String formatBonus(double bonus) {
        if (bonus == (int) bonus) {
            // Если число целое, показываем без десятичных
            return String.valueOf((int) bonus);
        } else {
            // Показываем с одним знаком после запятой
            return String.format("%.1f", bonus);
        }
    }
    
    /**
     * Получает базовые атрибуты материала используя Paper API
     */
    private static Map<Attribute, Double> getBaseAttributes(Material material) {
        Map<Attribute, Double> attributes = new HashMap<>();
        
        if (!material.isItem()) {
            return attributes;
        }
        
        try {
            // Используем Paper API для получения базовых атрибутов для основной руки
            var defaultModifiers = material.getDefaultAttributeModifiers(EquipmentSlot.HAND);
            
            defaultModifiers.forEach((attribute, modifier) -> {
                // Берем только модификаторы типа ADD_NUMBER (базовые значения)
                if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    attributes.put(attribute, modifier.getAmount());
                }
            });
            
            // Для брони проверяем все слоты  
            String name = material.name();
            if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || 
                name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("SHIELD")) {
                EquipmentSlot armorSlot = getEquipmentSlot(material);
                var armorModifiers = material.getDefaultAttributeModifiers(armorSlot);
                
                armorModifiers.forEach((attribute, modifier) -> {
                    if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                        attributes.put(attribute, modifier.getAmount());
                    }
                });
            }
            
        } catch (Exception e) {
            // Fallback на пустую карту если что-то пошло не так
            // Это может произойти для не-предметов или в тестах
        }
        
        return attributes;
    }
    
    private static void applyCorrectAttributes(RareItems plugin, ItemMeta meta, Material material, Rarity rarity, 
                                              Map<Attribute, Double> baseAttributes) {
        EquipmentSlot slot = getEquipmentSlot(material);
        
        // Атрибуты для оружия
        if (isWeapon(material, plugin) && rarity.damageBonus() > 0) {
            // Сначала удаляем существующие атрибуты урона (если есть)
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
            
            // Получаем базовый урон из Paper API
            double baseDamage = baseAttributes.getOrDefault(Attribute.GENERIC_ATTACK_DAMAGE, 0.0);
            
            // Вычисляем итоговый урон: базовый урон + бонус редкости
            double totalDamage = baseDamage + rarity.damageBonus();
            
            // Добавляем атрибут с полным уроном для основной руки
            meta.addAttributeModifier(
                    Attribute.GENERIC_ATTACK_DAMAGE,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "generic.attack_damage",
                            totalDamage,
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlot.HAND
                    )
            );
            
            // Отладочная информация
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info(String.format("[RareItems Debug] Оружие: %s | Базовый урон: %.1f | Бонус: %.1f | Итоговый урон: %.1f", 
                                        material.name(), baseDamage, rarity.damageBonus(), totalDamage));
            }
        }
        
        // Применяем скорость атаки для оружия (важно для корректного урона)
        if (isWeapon(material, plugin)) {
            // Удаляем существующий атрибут скорости атаки
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
            
            // Получаем базовую скорость атаки из Paper API
            double baseAttackSpeed = baseAttributes.getOrDefault(Attribute.GENERIC_ATTACK_SPEED, 4.0);
            
            // Применяем базовую скорость атаки (без бонусов для баланса PvP)
            meta.addAttributeModifier(
                    Attribute.GENERIC_ATTACK_SPEED,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "generic.attack_speed",
                            baseAttackSpeed,
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlot.HAND
                    )
            );
        }
        
        // Атрибуты для брони
        if (isArmor(material, plugin)) {
            if (rarity.armorBonus() > 0) {
                // Удаляем существующие атрибуты брони
                meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
                
                // Получаем базовую защиту из Paper API
                double baseArmor = baseAttributes.getOrDefault(Attribute.GENERIC_ARMOR, 0.0);
                
                // Вычисляем итоговую защиту: базовая + бонус
                double totalArmor = baseArmor + rarity.armorBonus();
                
                meta.addAttributeModifier(
                        Attribute.GENERIC_ARMOR,
                        new AttributeModifier(
                                UUID.randomUUID(),
                                "generic.armor",
                                totalArmor,
                                AttributeModifier.Operation.ADD_NUMBER,
                                slot
                        )
                );
            }
            
            if (rarity.toughnessBonus() > 0) {
                // Удаляем существующие атрибуты прочности
                meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
                
                // Получаем базовую прочность из Paper API
                double baseToughness = baseAttributes.getOrDefault(Attribute.GENERIC_ARMOR_TOUGHNESS, 0.0);
                
                meta.addAttributeModifier(
                        Attribute.GENERIC_ARMOR_TOUGHNESS,
                        new AttributeModifier(
                                UUID.randomUUID(),
                                "generic.armor_toughness",
                                baseToughness + rarity.toughnessBonus(),
                                AttributeModifier.Operation.ADD_NUMBER,
                                slot
                        )
                );
            }
            
            // Отладочная информация для брони
            if (plugin.getConfigManager().isDebugMode()) {
                double baseArmor = baseAttributes.getOrDefault(Attribute.GENERIC_ARMOR, 0.0);
                double totalArmor = baseArmor + rarity.armorBonus();
                plugin.getLogger().info(String.format("[RareItems Debug] Броня: %s | Базовая защита: %.1f | Бонус защиты: %.1f | Итоговая защита: %.1f | Прочность: %.1f", 
                                        material.name(), baseArmor, rarity.armorBonus(), totalArmor, rarity.toughnessBonus()));
            }
        }
        
        // Универсальные атрибуты (применяются ко всем типам предметов)
        if (rarity.healthBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_MAX_HEALTH,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.health.bonus",
                            rarity.healthBonus(),
                            AttributeModifier.Operation.ADD_NUMBER,
                            slot
                    )
            );
        }
        
        if (rarity.speedBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_MOVEMENT_SPEED,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.speed.bonus",
                            rarity.speedBonus(),
                            AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                            slot
                    )
            );
        }
        
        if (rarity.luckBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.luck.bonus",
                            rarity.luckBonus(),
                            AttributeModifier.Operation.ADD_NUMBER,
                            slot
                    )
            );
        }
        
        // Отладочная информация для универсальных атрибутов
        if (plugin.getConfigManager().isDebugMode() && 
            (rarity.healthBonus() > 0 || rarity.speedBonus() > 0 || rarity.luckBonus() > 0)) {
            plugin.getLogger().info(String.format("[RareItems Debug] Универсальные атрибуты %s | Здоровье: +%.1f | Скорость: +%.1f%% | Удача: +%.1f", 
                                    material.name(), rarity.healthBonus(), rarity.speedBonus() * 100, rarity.luckBonus()));
        }
    }
    
    private static EquipmentSlot getEquipmentSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        } else if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        } else if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        } else if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        } else if (name.equals("SHIELD")) {
            return EquipmentSlot.OFF_HAND;
        }
        return EquipmentSlot.HAND;
    }
} 