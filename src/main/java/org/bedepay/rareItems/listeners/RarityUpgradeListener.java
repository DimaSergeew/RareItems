package org.bedepay.rareItems.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bedepay.rareItems.util.MaterialTypeChecker;
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
import org.bukkit.enchantments.Enchantment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RarityUpgradeListener implements Listener {
    
    private final RareItems plugin;
    private final ConfigManager configManager;
    private final MaterialTypeChecker materialTypeChecker;
    private final Random random = new Random();
    
    // Карта улучшений редкости
    private final Map<String, String> rarityUpgrades = Map.of(
        "common", "uncommon",
        "uncommon", "rare", 
        "rare", "epic",
        "epic", "legendary",
        "legendary", "mythic",
        "mythic", "divine",
        "divine", "celestial"
        // celestial - максимальная редкость
    );
    
    public RarityUpgradeListener(RareItems plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.materialTypeChecker = new MaterialTypeChecker(plugin);
        
        // Регистрируем кастомные рецепты
        registerUpgradeRecipes();
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
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftUpgrade(CraftItemEvent event) {
        if (!configManager.isEnabled()) {
            return;
        }
        
        // Проверяем, включена ли система улучшений
        if (!plugin.getConfig().getBoolean("upgradeSystem.combination.enabled", true)) {
            return;
        }
        
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (matrix.length < 2) {
            return;
        }
        
        // Ищем два одинаковых предмета с редкостью
        ItemStack item1 = null;
        ItemStack item2 = null;
        List<ItemStack> otherItems = new ArrayList<>();
        
        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                Rarity rarity = ItemUtil.getRarity(item);
                if (rarity != null) {
                    if (item1 == null) {
                        item1 = item;
                    } else if (item2 == null && canCombineItems(item1, item)) {
                        item2 = item;
                    } else {
                        otherItems.add(item);
                    }
                } else {
                    otherItems.add(item);
                }
            }
        }
        
        // Если не нашли два подходящих предмета - выходим
        if (item1 == null || item2 == null) {
            return;
        }
        
        Rarity rarity1 = ItemUtil.getRarity(item1);
        Rarity rarity2 = ItemUtil.getRarity(item2);
        
        if (configManager.isDebugMode()) {
            plugin.getLogger().info(String.format("[RareItems Debug] Попытка улучшения: %s | Редкость 1: %s | Редкость 2: %s", 
                                    item1.getType().name(), 
                                    (rarity1 != null ? rarity1.id() : "null"),
                                    (rarity2 != null ? rarity2.id() : "null")));
        }
        
        // Проверяем, что оба предмета имеют одинаковую редкость
        if (rarity1 == null || rarity2 == null || !rarity1.id().equals(rarity2.id())) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().info("[RareItems Debug] Улучшение невозможно: предметы не имеют одинаковой редкости");
            }
            return;
        }
        
        // Получаем следующую редкость
        String nextRarityId = rarityUpgrades.get(rarity1.id());
        if (nextRarityId == null) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().info(String.format("[RareItems Debug] Улучшение: %s уже максимальная редкость", rarity1.id()));
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
        
        // НОВЫЕ ПРОВЕРКИ ТРЕБОВАНИЙ
        if (!checkUpgradeRequirements(player, item1, item2, rarity1, nextRarityId, otherItems)) {
            event.setCancelled(true);
            return;
        }
        
        // НОВАЯ СИСТЕМА ШАНСОВ ПРОВАЛА
        if (!rollSuccessChance(player, rarity1.id(), item1, item2)) {
            event.setCancelled(true);
            return;
        }
        
        // Создаем улучшенный предмет
        ItemStack upgradedItem = new ItemStack(item1.getType());
        
        // Сохраняем прочность с наилучшего из двух предметов
        if (item1.getType().getMaxDurability() > 0) {
            short durability1 = item1.getDurability();
            short durability2 = item2.getDurability();
            upgradedItem.setDurability((short) Math.min(durability1, durability2)); // Меньшее значение = больше прочности
        }
        
        // Объединяем зачарования (берем максимальные уровни)
        Map<Enchantment, Integer> combinedEnchantments = new HashMap<>();
        item1.getEnchantments().forEach((ench, level) -> 
            combinedEnchantments.put(ench, Math.max(combinedEnchantments.getOrDefault(ench, 0), level)));
        item2.getEnchantments().forEach((ench, level) -> 
            combinedEnchantments.put(ench, Math.max(combinedEnchantments.getOrDefault(ench, 0), level)));
        
        upgradedItem.addUnsafeEnchantments(combinedEnchantments);
        
        // Применяем редкость
        ItemUtil.applyRarity(plugin, upgradedItem, nextRarity);
        
        // Помечаем как результат улучшения
        ItemMeta meta = upgradedItem.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "rarity_upgrade"), 
                PersistentDataType.BOOLEAN, 
                true
            );
            upgradedItem.setItemMeta(meta);
        }
        
        event.getInventory().setResult(upgradedItem);
        
        // Уведомление игрока
        sendUpgradeSuccess(player, nextRarity);
        
        if (configManager.isDebugMode()) {
            plugin.getLogger().info(String.format("[RareItems Debug] Успешное улучшение: %s -> %s", 
                                    rarity1.id(), nextRarity.id()));
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItemComplete(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        
        // Проверяем, является ли это результатом улучшения редкости
        ItemMeta meta = result.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "rarity_upgrade"), PersistentDataType.BOOLEAN)) {
            // Убираем метку улучшения после завершения крафта
            meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "rarity_upgrade"));
            result.setItemMeta(meta);
            
            // Играем эффекты улучшения
            if (event.getWhoClicked() instanceof Player player) {
                playUpgradeEffects(player, result);
                
                // Уведомляем о успешном улучшении
                Rarity rarity = ItemUtil.getRarity(result);
                if (rarity != null) {
                    player.sendMessage(Component.text("✨ Предмет улучшен до " + rarity.name() + " редкости!").color(rarity.color()));
                    
                    if (configManager.isDebugMode()) {
                        plugin.getLogger().info(String.format("[RareItems Debug] Игрок %s успешно улучшил предмет до %s", 
                                                player.getName(), rarity.id()));
                    }
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
        if (rarity != null && (rarity.id().equals("mythic") || rarity.id().equals("divine") || rarity.id().equals("celestial"))) {
            player.showTitle(Title.title(
                    Component.text("УЛУЧШЕНИЕ!").color(rarity.color()),
                    Component.text(rarity.getDisplayName() + " предмет создан!").color(rarity.color()),
                    Title.Times.times(
                            java.time.Duration.ofMillis(500),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(500)
                    )
            ));
        }
    }
    
    private boolean isWeaponOrArmor(Material material) {
        return materialTypeChecker.isWeaponOrArmor(material);
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
    
    /**
     * Проверяет все требования для улучшения
     */
    private boolean checkUpgradeRequirements(Player player, ItemStack item1, ItemStack item2, 
                                           Rarity currentRarity, String nextRarityId, 
                                           List<ItemStack> otherItems) {
        
        // 1. Проверка прочности предметов
        if (!checkDurabilityRequirement(player, item1, item2)) {
            return false;
        }
        
        // 2. Проверка требований к зачарованиям
        if (!checkEnchantmentRequirement(player, item1, item2, nextRarityId)) {
            return false;
        }
        
        // 3. Проверка требований к материалу
        if (!checkMaterialRequirement(player, item1, nextRarityId)) {
            return false;
        }
        
        // 4. Проверка специальных ингредиентов
        if (!checkSpecialIngredients(player, nextRarityId, otherItems)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Проверяет требование к прочности предметов
     */
    private boolean checkDurabilityRequirement(Player player, ItemStack item1, ItemStack item2) {
        int minDurabilityPercent = plugin.getConfig().getInt("upgradeSystem.combination.requirements.minDurabilityPercent", 80);
        
        if (item1.getType().getMaxDurability() > 0) {
            double durability1 = ((double)(item1.getType().getMaxDurability() - item1.getDurability()) / item1.getType().getMaxDurability()) * 100;
            if (durability1 < minDurabilityPercent) {
                player.sendMessage(Component.text("❌ Первый предмет слишком изношен! Нужно минимум " + minDurabilityPercent + "% прочности")
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        
        if (item2.getType().getMaxDurability() > 0) {
            double durability2 = ((double)(item2.getType().getMaxDurability() - item2.getDurability()) / item2.getType().getMaxDurability()) * 100;
            if (durability2 < minDurabilityPercent) {
                player.sendMessage(Component.text("❌ Второй предмет слишком изношен! Нужно минимум " + minDurabilityPercent + "% прочности")
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Проверяет требование к зачарованиям
     */
    private boolean checkEnchantmentRequirement(Player player, ItemStack item1, ItemStack item2, String targetRarity) {
        int requiredEnchants = plugin.getConfig().getInt("upgradeSystem.combination.requirements.enchantmentRequirements." + targetRarity, 0);
        
        if (requiredEnchants > 0) {
            int enchants1 = item1.getEnchantments().size();
            int enchants2 = item2.getEnchantments().size();
            
            if (enchants1 < requiredEnchants || enchants2 < requiredEnchants) {
                player.sendMessage(Component.text("❌ Для создания " + targetRarity + " предмета нужно минимум " + requiredEnchants + " зачарований на каждом предмете!")
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Проверяет требование к материалу
     */
    private boolean checkMaterialRequirement(Player player, ItemStack item, String targetRarity) {
        List<String> allowedMaterials = plugin.getConfig().getStringList("upgradeSystem.combination.requirements.materialRequirements." + targetRarity);
        
        if (!allowedMaterials.isEmpty()) {
            String itemMaterial = item.getType().name();
            boolean materialAllowed = false;
            
            for (String allowed : allowedMaterials) {
                if (itemMaterial.contains(allowed)) {
                    materialAllowed = true;
                    break;
                }
            }
            
            if (!materialAllowed) {
                player.sendMessage(Component.text("❌ Для создания " + targetRarity + " можно использовать только: " + String.join(", ", allowedMaterials))
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Проверяет требование к специальным ингредиентам
     */
    private boolean checkSpecialIngredients(Player player, String targetRarity, List<ItemStack> otherItems) {
        if (!plugin.getConfig().getBoolean("upgradeSystem.specialIngredients.enabled", true)) {
            return true;
        }
        
        List<String> requiredMaterials = plugin.getConfig().getStringList("upgradeSystem.specialIngredients.requirements." + targetRarity + ".materials");
        List<Integer> requiredCounts = plugin.getConfig().getIntegerList("upgradeSystem.specialIngredients.requirements." + targetRarity + ".count");
        
        if (requiredMaterials.isEmpty()) {
            return true; // Нет требований к ингредиентам
        }
        
        // Если count не указан, считаем что нужно по 1 штуке каждого
        if (requiredCounts.isEmpty()) {
            requiredCounts = Collections.nCopies(requiredMaterials.size(), 1);
        }
        
        Map<Material, Integer> availableItems = new HashMap<>();
        for (ItemStack item : otherItems) {
            availableItems.put(item.getType(), availableItems.getOrDefault(item.getType(), 0) + item.getAmount());
        }
        
        for (int i = 0; i < requiredMaterials.size(); i++) {
            Material requiredMaterial = Material.valueOf(requiredMaterials.get(i));
            int requiredCount = requiredCounts.get(i);
            int availableCount = availableItems.getOrDefault(requiredMaterial, 0);
            
            if (availableCount < requiredCount) {
                player.sendMessage(Component.text("❌ Для создания " + targetRarity + " нужно: " + requiredCount + " x " + requiredMaterial.name())
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Проверяет шанс успеха комбинирования
     */
    private boolean rollSuccessChance(Player player, String currentRarity, ItemStack item1, ItemStack item2) {
        double successChance = plugin.getConfig().getDouble("upgradeSystem.combination.successChances." + currentRarity, 100.0);
        
        if (successChance >= 100.0) {
            return true; // Гарантированный успех
        }
        
        if (random.nextDouble() * 100 > successChance) {
            // Провал комбинирования
            handleCombinationFailure(player, currentRarity, item1, item2);
            return false;
        }
        
        return true;
    }
    
    /**
     * Обрабатывает провал комбинирования
     */
    private void handleCombinationFailure(Player player, String currentRarity, ItemStack item1, ItemStack item2) {
        boolean destroyOneItem = plugin.getConfig().getBoolean("upgradeSystem.combination.failureConsequences.destroyOneItem", true);
        double criticalFailureChance = plugin.getConfig().getDouble("upgradeSystem.combination.failureConsequences.criticalFailureChance", 10.0);
        double downgradeChance = plugin.getConfig().getDouble("upgradeSystem.combination.failureConsequences.downgradeChance", 30.0);
        
        // Проверяем критический провал
        if (random.nextDouble() * 100 < criticalFailureChance) {
            // Критический провал - потеря обоих предметов
            player.sendMessage(Component.text("💥 КРИТИЧЕСКИЙ ПРОВАЛ! Оба предмета уничтожены!")
                .color(NamedTextColor.DARK_RED));
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            
            // Результат будет воздух (предметы исчезнут)
            return;
        }
        
        // Обычный провал
        if (destroyOneItem) {
            player.sendMessage(Component.text("❌ Комбинирование провалилось! Один предмет утерян.")
                .color(NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("❌ Комбинирование провалилось! Предметы остались без изменений.")
                .color(NamedTextColor.YELLOW));
        }
        
        // Проверяем шанс понижения редкости
        if (random.nextDouble() * 100 < downgradeChance) {
            // Возвращаем предмет с пониженной редкостью
            String downgradedRarity = getDowngradedRarity(currentRarity);
            if (downgradedRarity != null) {
                Rarity newRarity = configManager.getRarityById(downgradedRarity);
                if (newRarity != null) {
                    ItemStack downgradedItem = new ItemStack(item1.getType());
                    ItemUtil.applyRarity(plugin, downgradedItem, newRarity);
                    // Результат будет пониженный предмет
                    
                    player.sendMessage(Component.text("⬇️ Редкость понижена до " + newRarity.name())
                        .color(NamedTextColor.GOLD));
                }
            }
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
    }
    
    /**
     * Получает пониженную редкость
     */
    private String getDowngradedRarity(String currentRarity) {
        return switch (currentRarity) {
            case "uncommon" -> "common";
            case "rare" -> "uncommon";
            case "epic" -> "rare";
            case "legendary" -> "epic";
            case "mythic" -> "legendary";
            case "divine" -> "mythic";
            default -> null;
        };
    }
    
    private boolean canCombineItems(ItemStack item1, ItemStack item2) {
        // Проверяем, что материалы одинаковые
        if (item1.getType() != item2.getType()) {
            return false;
        }
        
        // Проверяем, что редкости одинаковые
        Rarity rarity1 = ItemUtil.getRarity(item1);
        Rarity rarity2 = ItemUtil.getRarity(item2);
        
        if (rarity1 == null || rarity2 == null) {
            return false;
        }
        
        return rarity1.id().equals(rarity2.id());
    }
    
    private void sendUpgradeSuccess(Player player, Rarity newRarity) {
        // Звук успеха
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        
        // Сообщение в чат
        Component message = Component.text("✨ Улучшение успешно! Получена редкость: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(newRarity.name()).color(newRarity.color()));
        
        player.sendMessage(message);
        
        // Title с новой редкостью
        Component titleMain = Component.text("УЛУЧШЕНИЕ!").color(NamedTextColor.GOLD);
        Component titleSub = Component.text(newRarity.name()).color(newRarity.color());
        
        Title title = Title.title(titleMain, titleSub, 
            Title.Times.of(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500)));
        
        player.showTitle(title);
    }
} 