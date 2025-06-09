package org.bedepay.rareItems.util;

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

public class ItemUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    /**
     * Creates a NamespacedKey for the plugin
     */
    public static NamespacedKey getKey(Plugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }
    
    /**
     * Проверяет, является ли материал оружием
     */
    private static boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || 
               name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT");
    }
    
    /**
     * Проверяет, является ли материал броней
     */
    private static boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || 
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
               name.equals("SHIELD"); // Щиты тоже броня
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
        
        // Получаем базовые атрибуты предмета
        double baseDamage = getBaseDamage(clonedItem.getType());
        double baseArmor = getBaseArmor(clonedItem.getType());
        double baseAttackSpeed = getBaseAttackSpeed(clonedItem.getType());
        
        // Создаем красивое название с градиентом
        Component displayName = createDisplayName(clonedItem.getType(), rarity);
        meta.displayName(displayName);
        
        // Создаем красивое описание
        List<Component> lore = createLore(rarity, baseDamage, baseArmor, baseAttackSpeed, clonedItem.getType());
        meta.lore(lore);
        
        // Сохраняем редкость в NBT
        meta.getPersistentDataContainer().set(
                getKey(plugin, "rarity"),
                PersistentDataType.STRING,
                rarity.getId()
        );
        
        // Применяем правильные атрибуты (добавляем к базовым, а не заменяем)
        applyCorrectAttributes(plugin, meta, clonedItem.getType(), rarity, baseDamage, baseArmor, baseAttackSpeed);
        
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
        Component rarityComponent = Component.text(rarity.getName() + " ")
                .color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false);
        
        Component nameComponent = Component.text(capitalizedName)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        
        return rarityComponent.append(nameComponent);
    }
    
    private static List<Component> createLore(Rarity rarity, double baseDamage, double baseArmor, double baseAttackSpeed, Material material) {
        List<Component> lore = new ArrayList<>();
        
        // Заголовок редкости с звездами
        String stars = getRarityStars(rarity.getId());
        Component rarityHeader = Component.text(stars + " " + rarity.getName() + " " + stars)
                .color(rarity.getColor())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        lore.add(rarityHeader);
        
        // Компактная строка с основными бонусами
        List<String> bonuses = new ArrayList<>();
        
        if (rarity.getDamageBonus() > 0 && isWeapon(material)) {
            // Для оружия показываем итоговый урон
            double totalDamage = baseDamage + rarity.getDamageBonus();
            bonuses.add(formatBonus(totalDamage) + "⚔ (+" + formatBonus(rarity.getDamageBonus()) + ")");
        }
        if (rarity.getArmorBonus() > 0 && isArmor(material)) {
            // Для брони показываем итоговую защиту
            double totalArmor = baseArmor + rarity.getArmorBonus();
            bonuses.add(formatBonus(totalArmor) + "🛡 (+" + formatBonus(rarity.getArmorBonus()) + ")");
        }
        if (rarity.getHealthBonus() > 0) {
            bonuses.add("+" + formatBonus(rarity.getHealthBonus()) + "❤");
        }
        if (rarity.getLuckBonus() > 0) {
            bonuses.add("+" + formatBonus(rarity.getLuckBonus()) + "🍀");
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
        if (!rarity.getOnHitEffects().isEmpty()) {
            StringBuilder effects = new StringBuilder();
            rarity.getOnHitEffects().forEach((effect, amplifier) -> {
                if (effects.length() > 0) effects.append(", ");
                effects.append(getShortEffectName(effect.getName()));
            });
            
            Component effectLine = Component.text("⚡ " + effects.toString() + " (" + (int)rarity.getEffectChance() + "%)")
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
        String rarityId = rarity.getId();
        
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
    

    
    private static void applyCorrectAttributes(RareItems plugin, ItemMeta meta, Material material, Rarity rarity, 
                                              double baseDamage, double baseArmor, double baseAttackSpeed) {
        EquipmentSlot slot = getEquipmentSlot(material);
        
        // Атрибуты для оружия
        if (isWeapon(material) && rarity.getDamageBonus() > 0) {
            // Сначала удаляем существующие атрибуты урона (если есть)
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
            
            // Вычисляем итоговый урон: базовый урон + бонус редкости
            double totalDamage = baseDamage + rarity.getDamageBonus();
            
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
                                        material.name(), baseDamage, rarity.getDamageBonus(), totalDamage));
            }
        }
        
        // Применяем скорость атаки для оружия (важно для корректного урона)
        if (isWeapon(material)) {
            // Удаляем существующий атрибут скорости атаки
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
            
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
        if (isArmor(material)) {
            if (rarity.getArmorBonus() > 0) {
                // Удаляем существующие атрибуты брони
                meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
                
                // Вычисляем итоговую защиту: базовая + бонус
                double totalArmor = baseArmor + rarity.getArmorBonus();
                
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
            
            if (rarity.getToughnessBonus() > 0) {
                // Удаляем существующие атрибуты прочности
                meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
                
                meta.addAttributeModifier(
                        Attribute.GENERIC_ARMOR_TOUGHNESS,
                        new AttributeModifier(
                                UUID.randomUUID(),
                                "generic.armor_toughness",
                                rarity.getToughnessBonus(),
                                AttributeModifier.Operation.ADD_NUMBER,
                                slot
                        )
                );
            }
            
            // Отладочная информация для брони
            if (plugin.getConfigManager().isDebugMode()) {
                double totalArmor = baseArmor + rarity.getArmorBonus();
                plugin.getLogger().info(String.format("[RareItems Debug] Броня: %s | Базовая защита: %.1f | Бонус защиты: %.1f | Итоговая защита: %.1f | Прочность: %.1f", 
                                        material.name(), baseArmor, rarity.getArmorBonus(), totalArmor, rarity.getToughnessBonus()));
            }
        }
        
        // Универсальные атрибуты (применяются ко всем типам предметов)
        if (rarity.getHealthBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_MAX_HEALTH,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.health.bonus",
                            rarity.getHealthBonus(),
                            AttributeModifier.Operation.ADD_NUMBER,
                            slot
                    )
            );
        }
        
        if (rarity.getSpeedBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_MOVEMENT_SPEED,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.speed.bonus",
                            rarity.getSpeedBonus(),
                            AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                            slot
                    )
            );
        }
        
        if (rarity.getLuckBonus() > 0) {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            UUID.randomUUID(),
                            "rarity.luck.bonus",
                            rarity.getLuckBonus(),
                            AttributeModifier.Operation.ADD_NUMBER,
                            slot
                    )
            );
        }
        
        // Отладочная информация для универсальных атрибутов
        if (plugin.getConfigManager().isDebugMode() && 
            (rarity.getHealthBonus() > 0 || rarity.getSpeedBonus() > 0 || rarity.getLuckBonus() > 0)) {
            plugin.getLogger().info(String.format("[RareItems Debug] Универсальные атрибуты %s | Здоровье: +%.1f | Скорость: +%.1f%% | Удача: +%.1f", 
                                    material.name(), rarity.getHealthBonus(), rarity.getSpeedBonus() * 100, rarity.getLuckBonus()));
        }
    }
    

    
    private static double getBaseDamage(Material material) {
        return switch (material) {
            case WOODEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case GOLDEN_SWORD -> 4.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            case WOODEN_AXE -> 7.0;
            case STONE_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case GOLDEN_AXE -> 7.0;
            case DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;
            case BOW -> 0.0;
            case CROSSBOW -> 0.0;
            case TRIDENT -> 9.0;
            default -> 0.0;
        };
    }
    
    private static double getBaseArmor(Material material) {
        return switch (material) {
            case LEATHER_HELMET, GOLDEN_HELMET, CHAINMAIL_HELMET -> 1.0;
            case IRON_HELMET -> 2.0;
            case DIAMOND_HELMET, NETHERITE_HELMET -> 3.0;
            
            case LEATHER_CHESTPLATE, GOLDEN_CHESTPLATE -> 3.0;
            case CHAINMAIL_CHESTPLATE -> 5.0;
            case IRON_CHESTPLATE -> 6.0;
            case DIAMOND_CHESTPLATE -> 8.0;
            case NETHERITE_CHESTPLATE -> 8.0;
            
            case LEATHER_LEGGINGS, GOLDEN_LEGGINGS -> 2.0;
            case CHAINMAIL_LEGGINGS -> 4.0;
            case IRON_LEGGINGS -> 5.0;
            case DIAMOND_LEGGINGS -> 6.0;
            case NETHERITE_LEGGINGS -> 6.0;
            
            case LEATHER_BOOTS, GOLDEN_BOOTS, CHAINMAIL_BOOTS -> 1.0;
            case IRON_BOOTS -> 2.0;
            case DIAMOND_BOOTS, NETHERITE_BOOTS -> 3.0;
            
            case SHIELD -> 0.0; // Щиты не дают базовой защиты в ванили
            
            default -> 0.0;
        };
    }
    
    private static double getBaseAttackSpeed(Material material) {
        return switch (material) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> 1.6;
            case WOODEN_AXE, GOLDEN_AXE -> 0.8;
            case STONE_AXE -> 0.8;
            case IRON_AXE -> 0.9;
            case DIAMOND_AXE -> 1.0;
            case NETHERITE_AXE -> 1.0;
            case TRIDENT -> 1.1;
            default -> 0.0;
        };
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
        } else {
            return EquipmentSlot.HAND;
        }
    }
} 