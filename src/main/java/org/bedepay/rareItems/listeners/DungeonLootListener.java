package org.bedepay.rareItems.listeners;

import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonLootListener implements Listener {
    
    private final RareItems plugin;
    private final ConfigManager configManager;
    private final Random random = new Random();
    
    public DungeonLootListener(RareItems plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent event) {
        LootContext context = event.getLootContext();
        
        // Проверяем, что это лут из структуры (данжа)
        if (!isDungeonLoot(context)) {
            return;
        }
        
        List<ItemStack> newLoot = new ArrayList<>();
        boolean anyChanges = false;
        
        for (ItemStack item : event.getLoot()) {
            if (item == null || item.getType() == Material.AIR) {
                newLoot.add(item);
                continue;
            }
            
            // Применяем редкость только к оружию и броне
            if (isWeaponOrArmor(item.getType())) {
                ItemStack rareItem = applyDungeonRarity(item);
                if (rareItem != null) {
                    newLoot.add(rareItem);
                    anyChanges = true;
                    continue;
                }
            }
            
            newLoot.add(item);
        }
        
        if (anyChanges) {
            event.setLoot(newLoot);
        }
    }
    
    private boolean isDungeonLoot(LootContext context) {
        // Проверяем по локации - если это под землей или в структуре
        if (context.getLocation() != null) {
            // Простая проверка - если Y координата низкая, вероятно данж
            int y = context.getLocation().getBlockY();
            return y < 50; // Большинство данжей под землей
        }
        
                 // Альтернативная проверка через другие параметры
         return true; // Пока применяем ко всему луту для тестирования
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
               name.equals("TRIDENT");
    }
    
    private ItemStack applyDungeonRarity(ItemStack item) {
        // Особые шансы для данжей - больше редких предметов!
        double roll = random.nextDouble() * 100.0;
        
        // Улучшенные шансы для данжей
        String rarityId = null;
        if (roll < 1.0) {           // 1% - очень редкие
            rarityId = random.nextDouble() < 0.3 ? "mythic" : 
                      random.nextDouble() < 0.6 ? "legendary" : "epic";
        } else if (roll < 5.0) {    // 4% - редкие
            rarityId = random.nextDouble() < 0.5 ? "epic" : "rare";
        } else if (roll < 15.0) {   // 10% - необычные
            rarityId = random.nextDouble() < 0.3 ? "rare" : "uncommon";
        } else if (roll < 40.0) {   // 25% - обычные с бонусом
            rarityId = "common";
        }
        
        if (rarityId != null) {
            Rarity rarity = configManager.getRarityById(rarityId);
            if (rarity != null) {
                return ItemUtil.applyRarity(plugin, item, rarity);
            }
        }
        
        return null; // Возвращаем null если не применили редкость
    }
} 