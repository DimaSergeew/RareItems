package org.bedepay.rareItems.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Централизованный менеджер для управления редкостями
 * Решает проблемы с архитектурой и повышает производительность
 */
public class RarityManager {
    
    // Константы для избежания магических чисел
    public static final class Constants {
        public static final int DEFAULT_ABILITY_COOLDOWN = 3000; // 3 секунды
        public static final int TELEPORT_COOLDOWN = 10000; // 10 секунд
        public static final int CLEANUP_INTERVAL = 300000; // 5 минут
        public static final int ARMOR_CHECK_INTERVAL = 100; // 5 секунд в тиках
    }
    
    // Кэши для производительности
    private final Map<Material, Boolean> weaponArmorCache = new ConcurrentHashMap<>();
    private final Map<String, Rarity> rarityCache = new ConcurrentHashMap<>();
    private final Map<String, Double> rarityProbabilities = new HashMap<>();
    
    private final RareItems plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    public RarityManager(RareItems plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.initializeCache();
    }
    
    /**
     * Инициализирует кэши при загрузке
     */
    private void initializeCache() {
        // Кэшируем все редкости
        rarityCache.clear();
        for (Rarity rarity : configManager.getRarities()) {
            rarityCache.put(rarity.getId(), rarity);
            rarityProbabilities.put(rarity.getId(), configManager.getCraftChance(rarity.getId()));
        }
        
        // Кэшируем материалы оружия/брони
        weaponArmorCache.clear();
        for (Material material : Material.values()) {
            if (material.isItem()) {
                weaponArmorCache.put(material, calculateIsWeaponOrArmor(material));
            }
        }
    }
    
    /**
     * Проверяет, является ли материал оружием или броней
     */
    public boolean isWeaponOrArmor(Material material) {
        return weaponArmorCache.getOrDefault(material, false);
    }
    
    /**
     * Внутренний метод для расчета типа материала
     */
    private boolean calculateIsWeaponOrArmor(Material material) {
        String name = material.name();
        
                 // Оружие
         boolean isWeapon = name.endsWith("_SWORD") || 
                           name.endsWith("_AXE") || 
                           name.equals("BOW") ||
                           name.equals("CROSSBOW") ||
                           name.equals("TRIDENT") ||
                           (name.endsWith("_HOE") && plugin.getConfig().getBoolean("settings.includeHoes", false));
        
        // Броня
        boolean isArmor = name.endsWith("_HELMET") || 
                         name.endsWith("_CHESTPLATE") || 
                         name.endsWith("_LEGGINGS") || 
                         name.endsWith("_BOOTS") ||
                         name.equals("SHIELD");
        
        return isWeapon || isArmor;
    }
    
    /**
     * Проверяет, является ли материал оружием
     */
    public boolean isWeapon(Material material) {
        String name = material.name();
                 return name.endsWith("_SWORD") || name.endsWith("_AXE") || 
                name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT") ||
                (name.endsWith("_HOE") && plugin.getConfig().getBoolean("settings.includeHoes", false));
    }
    
    /**
     * Проверяет, является ли материал броней
     */
    public boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || 
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("SHIELD");
    }
    
    /**
     * Получает редкость по ID с кэшированием
     */
    public Rarity getRarityById(String id) {
        return rarityCache.get(id);
    }
    
    /**
     * Получает редкость предмета (улучшенная версия без статических вызовов)
     */
    public Rarity getRarity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        String rarityId = meta.getPersistentDataContainer().get(
                ItemUtil.getKey(plugin, "rarity"),
                PersistentDataType.STRING
        );
        
        return rarityId != null ? getRarityById(rarityId) : null;
    }
    
    /**
     * Проверяет, имеет ли предмет редкость от нашего плагина
     */
    public boolean hasRarity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        return meta.getPersistentDataContainer().has(
                ItemUtil.getKey(plugin, "rarity"), 
                PersistentDataType.STRING
        );
    }
    
    /**
     * Получает шанс крафта редкости
     */
    public double getCraftChance(String rarityId) {
        return rarityProbabilities.getOrDefault(rarityId, 0.0);
    }
    
    /**
     * Проверяет, является ли редкость очень редкой
     */
    public boolean isVeryRare(String rarityId) {
        return Set.of("legendary", "mythic", "divine", "celestial").contains(rarityId);
    }
    
    /**
     * Проверяет, является ли редкость крайне редкой
     */
    public boolean isExtremelyRare(String rarityId) {
        return Set.of("divine", "celestial").contains(rarityId);
    }
    
    /**
     * Создает красивое сообщение с градиентом для редкости
     */
    public Component createRarityMessage(Rarity rarity, String message) {
        String styledMessage = switch (rarity.getId()) {
            case "celestial" -> "<gradient:#ff6b6b:#4ecdc4>✦✦✦ " + message + " ✦✦✦</gradient>";
            case "divine" -> "<gradient:#a8edea:#fed6e3>✦✦ " + message + " ✦✦</gradient>";
            case "mythic" -> "<gradient:#d299c2:#fef9d7>✦ " + message + " ✦</gradient>";
            case "legendary" -> "<gradient:#ffeaa7:#fab1a0>" + message + "</gradient>";
            case "epic" -> "<gradient:#6c5ce7:#a29bfe>" + message + "</gradient>";
            case "rare" -> "<gradient:#0984e3:#74b9ff>" + message + "</gradient>";
            case "uncommon" -> "<gradient:#00b894:#55a3ff>" + message + "</gradient>";
            default -> "<color:" + rarity.getColor().asHexString() + ">" + message + "</color>";
        };
        
        return miniMessage.deserialize(styledMessage);
    }
    
    /**
     * Перезагружает все кэши (вызывается при перезагрузке конфига)
     */
    public void reloadCache() {
        initializeCache();
    }
    
    /**
     * Получает отформатированное имя материала
     */
    public String getFormattedMaterialName(Material material) {
        String materialName = material.name().toLowerCase().replace('_', ' ');
        return capitalizeWords(materialName);
    }
    
    /**
     * Делает первые буквы слов заглавными
     */
    private String capitalizeWords(String str) {
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
     * Получает звезды для редкости
     */
    public String getRarityStars(String rarityId) {
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
    
    /**
     * Форматирует числовое значение бонуса
     */
    public String formatBonus(double bonus) {
        if (bonus == (int) bonus) {
            return String.valueOf((int) bonus);
        } else {
            return String.format("%.1f", bonus);
        }
    }
    
    // Геттеры
    public RareItems getPlugin() {
        return plugin;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
} 