package org.bedepay.rareItems.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RarityUpgradeListener implements Listener {
    
    private final RareItems plugin;
    private final ConfigManager configManager;
    
    // Карта улучшений редкости
    private final Map<String, String> rarityUpgrades = new HashMap<>();
    
    public RarityUpgradeListener(RareItems plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        
        // Инициализируем карту улучшений
        initializeUpgradeMap();
        
        // Регистрируем кастомные рецепты
        registerUpgradeRecipes();
    }
    
    private void initializeUpgradeMap() {
        rarityUpgrades.put("common", "uncommon");
        rarityUpgrades.put("uncommon", "rare");
        rarityUpgrades.put("rare", "epic");
        rarityUpgrades.put("epic", "legendary");
        rarityUpgrades.put("legendary", "mythic");
        rarityUpgrades.put("mythic", "divine");
        rarityUpgrades.put("divine", "celestial");
        // celestial - максимальная редкость
    }
    
    private void registerUpgradeRecipes() {
        // Регистрируем общий рецепт для любых двух одинаковых предметов
        for (Material material : Material.values()) {
            if (isWeaponOrArmor(material)) {
                registerRecipeForMaterial(material);
            }
        }
    }
    
    private void registerRecipeForMaterial(Material material) {
        NamespacedKey key = new NamespacedKey(plugin, "upgrade_" + material.name().toLowerCase());
        
        ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(material));
        recipe.addIngredient(2, material); // Два одинаковых предмета
        
        try {
            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            // Рецепт уже существует, игнорируем
        }
    }
    
    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Recipe recipe = event.getRecipe();
        
        // Проверяем, что это крафт двух предметов
        ItemStack[] matrix = inventory.getMatrix();
        List<ItemStack> items = new ArrayList<>();
        
        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }
        
        // Должно быть ровно 2 предмета
        if (items.size() != 2) {
            return;
        }
        
        ItemStack item1 = items.get(0);
        ItemStack item2 = items.get(1);
        
        // Проверяем, что предметы одинаковые
        if (item1.getType() != item2.getType()) {
            return;
        }
        
        // Проверяем, что это оружие или броня
        if (!isWeaponOrArmor(item1.getType())) {
            return;
        }
        
        // Получаем редкости предметов
        Rarity rarity1 = ItemUtil.getRarity(item1);
        Rarity rarity2 = ItemUtil.getRarity(item2);
        
        // Отладочная информация
        if (configManager.isDebugMode()) {
            plugin.getLogger().info(String.format("[RareItems Debug] Попытка улучшения: %s | Редкость 1: %s | Редкость 2: %s", 
                                    item1.getType().name(), 
                                    (rarity1 != null ? rarity1.getId() : "null"),
                                    (rarity2 != null ? rarity2.getId() : "null")));
        }
        
        // Проверяем, что оба предмета имеют одинаковую редкость
        if (rarity1 == null || rarity2 == null || !rarity1.getId().equals(rarity2.getId())) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().info(String.format("[RareItems Debug] Улучшение невозможно: предметы не имеют одинаковой редкости или не имеют редкости вообще"));
            }
            return;
        }
        
        // Получаем следующую редкость
        String nextRarityId = rarityUpgrades.get(rarity1.getId());
        if (nextRarityId == null) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().info(String.format("[RareItems Debug] Улучшение: %s уже максимальная редкость", rarity1.getId()));
            }
            return; // Максимальная редкость
        }
        
        Rarity nextRarity = configManager.getRarityById(nextRarityId);
        if (nextRarity == null) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().info(String.format("[RareItems Debug] Улучшение: редкость %s не найдена в конфиге", nextRarityId));
            }
            return;
        }
        
        // Создаем улучшенный предмет
        ItemStack upgradedItem = new ItemStack(item1.getType());
        upgradedItem = ItemUtil.applyRarity(plugin, upgradedItem, nextRarity);
        
        // ВАЖНО: Помечаем предмет как результат улучшения, чтобы CraftListener его не обрабатывал
        ItemMeta meta = upgradedItem.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "rarity_upgrade"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            upgradedItem.setItemMeta(meta);
        }
        
        // Устанавливаем результат крафта
        inventory.setResult(upgradedItem);
        
        // Отладочная информация
        if (configManager.isDebugMode()) {
            plugin.getLogger().info(String.format("[RareItems Debug] Успешное улучшение: %s %s -> %s", 
                                    item1.getType().name(), rarity1.getId(), nextRarity.getId()));
        }
        
        // Показываем предварительный эффект (только визуально)
        if (event.getView().getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        
        // Проверяем, является ли это результатом улучшения редкости
        ItemMeta meta = result.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "rarity_upgrade"), PersistentDataType.BYTE)) {
            // Убираем метку улучшения после завершения крафта
            meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "rarity_upgrade"));
            result.setItemMeta(meta);
            
            // Играем эффекты улучшения
            if (event.getWhoClicked() instanceof Player player) {
                playUpgradeEffects(player, result);
                
                // Уведомляем о успешном улучшении
                Rarity rarity = ItemUtil.getRarity(result);
                if (rarity != null) {
                    player.sendMessage(Component.text("✨ Предмет улучшен до " + rarity.getDisplayName() + " редкости!").color(rarity.getColor()));
                }
                
                if (configManager.isDebugMode()) {
                    plugin.getLogger().info(String.format("[RareItems Debug] Игрок %s успешно улучшил предмет до %s", 
                                            player.getName(), rarity != null ? rarity.getId() : "unknown"));
                }
            }
        }
    }
    
    private void playUpgradeEffects(Player player, ItemStack result) {
        // Звук улучшения
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
        
        // Частицы
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        
        // Title для особых редкостей
        Rarity rarity = ItemUtil.getRarity(result);
        if (rarity != null && (rarity.getId().equals("mythic") || rarity.getId().equals("divine") || rarity.getId().equals("celestial"))) {
            player.showTitle(Title.title(
                    Component.text("УЛУЧШЕНИЕ!").color(rarity.getColor()),
                    Component.text(rarity.getDisplayName() + " предмет создан!").color(rarity.getColor()),
                    Title.Times.times(
                            java.time.Duration.ofMillis(500),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(500)
                    )
            ));
        }
    }
    
    private boolean isWeaponOrArmor(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || 
               name.endsWith("_AXE") || 
               name.endsWith("_HELMET") || 
               name.endsWith("_CHESTPLATE") || 
               name.endsWith("_LEGGINGS") || 
               name.endsWith("_BOOTS") ||
               name.equals("BOW") || 
               name.equals("CROSSBOW") || 
               name.equals("TRIDENT") ||
               name.equals("SHIELD");
    }
    
    /**
     * Метод для перерегистрации рецептов при перезагрузке
     */
    public void reregisterRecipes() {
        // Удаляем старые рецепты
        for (Material material : Material.values()) {
            if (isWeaponOrArmor(material)) {
                NamespacedKey key = new NamespacedKey(plugin, "upgrade_" + material.name().toLowerCase());
                plugin.getServer().removeRecipe(key);
            }
        }
        
        // Регистрируем заново
        registerUpgradeRecipes();
    }
} 