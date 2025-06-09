package org.bedepay.rareItems.api;

import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.manager.RarityManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Публичный API для интеграции с другими плагинами
 * Обеспечивает безопасный доступ к функциональности RareItems
 */
public class RareItemsAPI {
    
    private static RareItems plugin;
    private static RarityManager rarityManager;
    
    /**
     * Инициализирует API (вызывается автоматически при загрузке плагина)
     */
    public static void initialize(RareItems pluginInstance, RarityManager manager) {
        plugin = pluginInstance;
        rarityManager = manager;
    }
    
    /**
     * Получает экземпляр плагина RareItems
     * @return экземпляр плагина или null если плагин не загружен
     */
    public static RareItems getPlugin() {
        return plugin;
    }
    
    /**
     * Проверяет, загружен ли плагин RareItems
     * @return true если плагин активен
     */
    public static boolean isEnabled() {
        return plugin != null && plugin.isEnabled();
    }
    
    /**
     * Получает редкость предмета
     * @param item предмет для проверки
     * @return редкость предмета или null если предмет не имеет редкости
     */
    public static Rarity getRarity(ItemStack item) {
        if (!isEnabled()) return null;
        return rarityManager.getRarity(item);
    }
    
    /**
     * Проверяет, имеет ли предмет редкость от RareItems
     * @param item предмет для проверки
     * @return true если предмет имеет редкость от RareItems
     */
    public static boolean hasRarity(ItemStack item) {
        if (!isEnabled()) return false;
        return rarityManager.hasRarity(item);
    }
    
    /**
     * Получает редкость по ID
     * @param rarityId ID редкости
     * @return объект редкости или null если не найдена
     */
    public static Rarity getRarityById(String rarityId) {
        if (!isEnabled()) return null;
        return rarityManager.getRarityById(rarityId);
    }
    
    /**
     * Применяет редкость к предмету
     * @param item предмет для модификации
     * @param rarity редкость для применения
     * @return модифицированный предмет
     */
    public static ItemStack applyRarity(ItemStack item, Rarity rarity) {
        if (!isEnabled()) return item;
        return org.bedepay.rareItems.util.ItemUtil.applyRarity(plugin, item, rarity);
    }
    
    /**
     * Применяет редкость к предмету по ID
     * @param item предмет для модификации
     * @param rarityId ID редкости
     * @return модифицированный предмет или оригинальный если редкость не найдена
     */
    public static ItemStack applyRarity(ItemStack item, String rarityId) {
        if (!isEnabled()) return item;
        Rarity rarity = getRarityById(rarityId);
        return rarity != null ? applyRarity(item, rarity) : item;
    }
    
    /**
     * Получает все доступные редкости
     * @return список всех редкостей
     */
    public static List<Rarity> getAllRarities() {
        if (!isEnabled()) return List.of();
        return plugin.getConfigManager().getRarities();
    }
    
    /**
     * Проверяет, является ли материал оружием или броней
     * @param material материал для проверки
     * @return true если материал поддерживается плагином
     */
    public static boolean isWeaponOrArmor(Material material) {
        if (!isEnabled()) return false;
        return rarityManager.isWeaponOrArmor(material);
    }
    
    /**
     * Проверяет, является ли материал оружием
     * @param material материал для проверки
     * @return true если материал - оружие
     */
    public static boolean isWeapon(Material material) {
        if (!isEnabled()) return false;
        return rarityManager.isWeapon(material);
    }
    
    /**
     * Проверяет, является ли материал броней
     * @param material материал для проверки
     * @return true если материал - броня
     */
    public static boolean isArmor(Material material) {
        if (!isEnabled()) return false;
        return rarityManager.isArmor(material);
    }
    
    /**
     * Получает шанс получения редкости при крафте
     * @param rarityId ID редкости
     * @return шанс в процентах (0.0 - 100.0)
     */
    public static double getCraftChance(String rarityId) {
        if (!isEnabled()) return 0.0;
        return rarityManager.getCraftChance(rarityId);
    }
    
    /**
     * Проверяет, является ли редкость очень редкой (legendary+)
     * @param rarityId ID редкости
     * @return true если редкость очень редкая
     */
    public static boolean isVeryRare(String rarityId) {
        if (!isEnabled()) return false;
        return rarityManager.isVeryRare(rarityId);
    }
    
    /**
     * Проверяет, является ли редкость крайне редкой (divine+)
     * @param rarityId ID редкости
     * @return true если редкость крайне редкая
     */
    public static boolean isExtremelyRare(String rarityId) {
        if (!isEnabled()) return false;
        return rarityManager.isExtremelyRare(rarityId);
    }
    
    /**
     * Получает отформатированное название материала
     * @param material материал
     * @return отформатированное название
     */
    public static String getFormattedMaterialName(Material material) {
        if (!isEnabled()) return material.name();
        return rarityManager.getFormattedMaterialName(material);
    }
    
    /**
     * События API для прослушивания другими плагинами
     */
    public static class Events {
        
        /**
         * Вызывается при получении игроком редкого предмета
         * @param player игрок
         * @param item предмет
         * @param rarity редкость
         */
        public static void onRareItemObtained(Player player, ItemStack item, Rarity rarity) {
            // Можно расширить для отправки кастомных событий
        }
        
        /**
         * Вызывается при улучшении редкости предмета
         * @param player игрок
         * @param originalItem оригинальный предмет
         * @param upgradedItem улучшенный предмет
         * @param fromRarity исходная редкость
         * @param toRarity новая редкость
         */
        public static void onRarityUpgrade(Player player, ItemStack originalItem, ItemStack upgradedItem, 
                                          Rarity fromRarity, Rarity toRarity) {
            // Можно расширить для отправки кастомных событий
        }
    }
    
    /**
     * Утилиты для работы с плагином
     */
    public static class Utils {
        
        /**
         * Проверяет совместимость версии API
         * @param requiredVersion требуемая версия
         * @return true если версия совместима
         */
        public static boolean isVersionCompatible(String requiredVersion) {
            if (!isEnabled()) return false;
            // Простая проверка версии
            return plugin.getDescription().getVersion().startsWith(requiredVersion);
        }
        
        /**
         * Получает версию API
         * @return версия плагина
         */
        public static String getVersion() {
            if (!isEnabled()) return "Unknown";
            return plugin.getDescription().getVersion();
        }
        
        /**
         * Регистрирует плагин как использующий RareItems API
         * @param plugin плагин, использующий API
         */
        public static void registerAPIUser(JavaPlugin plugin) {
            if (isEnabled()) {
                RareItemsAPI.plugin.getLogger().info("Плагин " + plugin.getName() + " использует RareItems API v" + getVersion());
            }
        }
    }
} 