package org.bedepay.rareItems;

import org.bedepay.rareItems.commands.RareItemsCommand;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.listeners.CraftListener;
import org.bedepay.rareItems.listeners.WeaponEffectListener;
import org.bedepay.rareItems.listeners.SpecialAbilityListener;
import org.bedepay.rareItems.listeners.DungeonLootListener;
import org.bedepay.rareItems.listeners.RarityUpgradeListener;
import org.bedepay.rareItems.manager.RarityManager;
import org.bedepay.rareItems.util.MaterialTypeChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RareItems extends JavaPlugin {
    private ConfigManager configManager;
    private RarityManager rarityManager;
    private WeaponEffectListener weaponEffectListener;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize config manager
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        // Initialize rarity manager
        rarityManager = new RarityManager(this, configManager);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        
        // Register weapon effect listener
        weaponEffectListener = new WeaponEffectListener(this);
        getServer().getPluginManager().registerEvents(weaponEffectListener, this);
        
        // Register special ability listener
        getServer().getPluginManager().registerEvents(new SpecialAbilityListener(this, configManager), this);
        
        // Register dungeon loot listener
        getServer().getPluginManager().registerEvents(new DungeonLootListener(this, configManager), this);
        
        // Register rarity upgrade listener
        getServer().getPluginManager().registerEvents(new RarityUpgradeListener(this, configManager), this);
        
        // Register commands
        RareItemsCommand commandHandler = new RareItemsCommand(this);
        getCommand("rareitems").setExecutor(commandHandler);
        getCommand("rareitems").setTabCompleter(commandHandler);
        
        // Start cleanup task for effect cooldowns
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (weaponEffectListener != null) {
                weaponEffectListener.cleanupCooldowns();
            }
        }, 6000L, 6000L); // Every 5 minutes
        
        getLogger().info("RareItems v2.0 - Современный RPG плагин загружен!");
        getLogger().info("Автор: BedePay | Поддержка Adventure API и MiniMessage");
    }

    @Override
    public void onDisable() {
        getLogger().info("RareItems v2.0 выгружен. До свидания!");
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public RarityManager getRarityManager() {
        return rarityManager;
    }
    
    /**
     * Перезагружает весь плагин (конфиг + кэши)
     */
    public void reloadPlugin() {
        // Перезагружаем конфигурацию
        configManager.reloadConfig();
        
        // Перезагружаем кэши
        if (rarityManager != null) {
            rarityManager.reloadCache();
        }
        
        // Очищаем кэш MaterialTypeChecker
        MaterialTypeChecker.clearCache();
        
        getLogger().info("Плагин RareItems полностью перезагружен!");
    }
}
