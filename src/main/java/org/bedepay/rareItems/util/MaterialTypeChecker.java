package org.bedepay.rareItems.util;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Современная утилита для определения типов материалов через Paper API
 * Использует Material.getDefaultAttributeModifiers() для точного определения типов
 */
public class MaterialTypeChecker {
    
    // Кэширование для производительности
    private static final Map<Material, Boolean> WEAPON_CACHE = new ConcurrentHashMap<>();
    private static final Map<Material, Boolean> ARMOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Material, Boolean> WEAPON_OR_ARMOR_CACHE = new ConcurrentHashMap<>();
    
    // Слоты брони для проверки
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    
    private final Plugin plugin;
    
    public MaterialTypeChecker(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Проверяет, является ли материал оружием, используя Paper API
     * @param material Материал для проверки
     * @return true если материал является оружием
     */
    public boolean isWeapon(Material material) {
        return WEAPON_CACHE.computeIfAbsent(material, this::calculateIsWeapon);
    }
    
    /**
     * Проверяет, является ли материал броней, используя Paper API
     * @param material Материал для проверки
     * @return true если материал является броней
     */
    public boolean isArmor(Material material) {
        return ARMOR_CACHE.computeIfAbsent(material, this::calculateIsArmor);
    }
    
    /**
     * Проверяет, является ли материал оружием или броней
     * @param material Материал для проверки
     * @return true если материал является оружием или броней
     */
    public boolean isWeaponOrArmor(Material material) {
        return WEAPON_OR_ARMOR_CACHE.computeIfAbsent(material, mat -> 
            isWeapon(mat) || isArmor(mat)
        );
    }
    
    /**
     * Внутренний метод для определения оружия через Paper API
     */
    private boolean calculateIsWeapon(Material material) {
        if (!material.isItem()) {
            return false;
        }
        
        // Получаем стандартные атрибуты материала из Paper API для основной руки
        Multimap<Attribute, AttributeModifier> attributes = material.getDefaultAttributeModifiers(EquipmentSlot.HAND);
        
        // Если у предмета есть атрибут урона - это оружие
        if (!attributes.get(Attribute.GENERIC_ATTACK_DAMAGE).isEmpty()) {
            return true;
        }
        
        // Дополнительная проверка для особых случаев оружия
        return isSpecialWeapon(material);
    }
    
    /**
     * Внутренний метод для определения брони через Paper API
     */
    private boolean calculateIsArmor(Material material) {
        if (!material.isItem()) {
            return false;
        }
        
        // Проверяем каждый слот брони
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Multimap<Attribute, AttributeModifier> attributes = material.getDefaultAttributeModifiers(slot);
            
            // Проверяем наличие защиты или прочности брони
            if (!attributes.get(Attribute.GENERIC_ARMOR).isEmpty() || 
                !attributes.get(Attribute.GENERIC_ARMOR_TOUGHNESS).isEmpty()) {
                return true;
            }
        }
        
        // Щит - особый случай брони
        return material == Material.SHIELD;
    }
    
    /**
     * Проверяет особые случаи оружия, которые могут не иметь стандартного урона
     */
    private boolean isSpecialWeapon(Material material) {
        // Дистанционное оружие
        if (material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT) {
            return true;
        }
        
        // Мотыги, если включены в настройках
        if (material.name().endsWith("_HOE") && 
            plugin.getConfig().getBoolean("settings.includeHoes", false)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Очищает кэш (полезно для перезагрузки конфига)
     */
    public static void clearCache() {
        WEAPON_CACHE.clear();
        ARMOR_CACHE.clear();
        WEAPON_OR_ARMOR_CACHE.clear();
    }
    
    /**
     * Получает информацию о материале для отладки
     */
    public String getMaterialInfo(Material material) {
        if (!material.isItem()) {
            return material.name() + " - НЕ ПРЕДМЕТ";
        }
        
        StringBuilder info = new StringBuilder();
        info.append(material.name()).append(" - ");
        
        // Проверяем урон для основной руки
        Multimap<Attribute, AttributeModifier> handAttributes = material.getDefaultAttributeModifiers(EquipmentSlot.HAND);
        var damageModifiers = handAttributes.get(Attribute.GENERIC_ATTACK_DAMAGE);
        if (!damageModifiers.isEmpty()) {
            info.append("ОРУЖИЕ (урон: ");
            for (AttributeModifier mod : damageModifiers) {
                info.append(String.format("%.1f", mod.getAmount()));
                break;
            }
            info.append(") ");
        }
        
        // Проверяем защиту для всех слотов брони
        boolean hasArmor = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Multimap<Attribute, AttributeModifier> armorAttributes = material.getDefaultAttributeModifiers(slot);
            var armorModifiers = armorAttributes.get(Attribute.GENERIC_ARMOR);
            if (!armorModifiers.isEmpty()) {
                if (!hasArmor) {
                    info.append("БРОНЯ (защита: ");
                    for (AttributeModifier mod : armorModifiers) {
                        info.append(String.format("%.1f", mod.getAmount()));
                        break;
                    }
                    info.append(") ");
                    hasArmor = true;
                }
                break;
            }
        }
        
        if (damageModifiers.isEmpty() && !hasArmor) {
            // Проверяем особые случаи
            if (isSpecialWeapon(material)) {
                info.append("ОСОБОЕ ОРУЖИЕ");
            } else if (material == Material.SHIELD) {
                info.append("ЩИТ (БРОНЯ)");
            } else {
                info.append("ОБЫЧНЫЙ ПРЕДМЕТ");
            }
        }
        
        return info.toString();
    }
} 