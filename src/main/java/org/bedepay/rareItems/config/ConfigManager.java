package org.bedepay.rareItems.config;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final RareItems plugin;
    private final List<Rarity> rarities = new ArrayList<>();
    private final Map<String, Double> craftChances = new HashMap<>();

    public ConfigManager(RareItems plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        
        // Load rarities
        ConfigurationSection raritiesSection = config.getConfigurationSection("rarities");
        if (raritiesSection != null) {
            rarities.clear();
            for (String key : raritiesSection.getKeys(false)) {
                ConfigurationSection raritySection = raritiesSection.getConfigurationSection(key);
                if (raritySection != null) {
                    String name = raritySection.getString("name", key);
                    TextColor color = parseColor(raritySection.getString("color", "white"));
                    
                    double damageBonus = raritySection.getDouble("attributes.damage", 0.0);
                    double armorBonus = raritySection.getDouble("attributes.armor", 0.0);
                    double speedBonus = raritySection.getDouble("attributes.speed", 0.0);
                    double toughnessBonus = raritySection.getDouble("attributes.toughness", 0.0);
                    double attackSpeedBonus = raritySection.getDouble("attributes.attackSpeed", 0.0);
                    double healthBonus = raritySection.getDouble("attributes.health", 0.0);
                    double luckBonus = raritySection.getDouble("attributes.luck", 0.0);
                    
                    List<String> enchantments = raritySection.getStringList("enchantments");
                    if (enchantments == null) {
                        enchantments = new ArrayList<>();
                    }
                    
                    Map<PotionEffectType, Integer> onHitEffects = new HashMap<>();
                    ConfigurationSection effectsSection = raritySection.getConfigurationSection("onHitEffects");
                    if (effectsSection != null) {
                        for (String effectName : effectsSection.getKeys(false)) {
                            try {
                                PotionEffectType effectType = PotionEffectType.getByName(effectName.toUpperCase());
                                if (effectType != null) {
                                    int amplifier = effectsSection.getInt(effectName, 0);
                                    onHitEffects.put(effectType, amplifier);
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("Неверный эффект: " + effectName);
                            }
                        }
                    }
                    
                    int effectCooldown = raritySection.getInt("effectCooldown", 5000);
                    double effectChance = raritySection.getDouble("effectChance", 100.0);
                    String particle = raritySection.getString("particle", "NONE");
                    String sound = raritySection.getString("sound", "NONE");
                    
                    // Загружаем специальные способности
                    Map<String, Object> specialAbilities = new HashMap<>();
                    ConfigurationSection abilitiesSection = raritySection.getConfigurationSection("specialAbilities");
                    if (abilitiesSection != null) {
                        for (String abilityKey : abilitiesSection.getKeys(false)) {
                            Object value = abilitiesSection.get(abilityKey);
                            specialAbilities.put(abilityKey, value);
                        }
                    }
                    
                    Rarity rarity = new Rarity(key, name, color, damageBonus, armorBonus, speedBonus, 
                            toughnessBonus, attackSpeedBonus, healthBonus, luckBonus, enchantments, 
                            onHitEffects, effectCooldown, effectChance, particle, sound, specialAbilities);
                    rarities.add(rarity);
                }
            }
        }
        
        // Load craft chances and sort them from lowest to highest
        ConfigurationSection chancesSection = config.getConfigurationSection("craftChances");
        if (chancesSection != null) {
            craftChances.clear();
            
            // Collect chances in a list and sort
            List<Map.Entry<String, Double>> sortedChances = new ArrayList<>();
            for (String key : chancesSection.getKeys(false)) {
                double chance = chancesSection.getDouble(key, 0.0);
                sortedChances.add(Map.entry(key, chance));
            }
            
            // Sort by chance value (ascending order - rarest first)
            sortedChances.sort(Map.Entry.comparingByValue());
            
            // Add to map in sorted order
            for (Map.Entry<String, Double> entry : sortedChances) {
                craftChances.put(entry.getKey(), entry.getValue());
            }
        }
    }
    
    private TextColor parseColor(String colorString) {
        if (colorString == null) {
            return NamedTextColor.WHITE;
        }
        
        // Try hex color first
        if (colorString.startsWith("#") && colorString.length() == 7) {
            try {
                return TextColor.fromHexString(colorString);
            } catch (Exception e) {
                plugin.getLogger().warning("Неверный hex цвет: " + colorString);
            }
        }
        
        // Try named colors
        return switch (colorString.toUpperCase()) {
            case "BLACK" -> NamedTextColor.BLACK;
            case "DARK_BLUE" -> NamedTextColor.DARK_BLUE;
            case "DARK_GREEN" -> NamedTextColor.DARK_GREEN;
            case "DARK_AQUA" -> NamedTextColor.DARK_AQUA;
            case "DARK_RED" -> NamedTextColor.DARK_RED;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "GOLD" -> NamedTextColor.GOLD;
            case "GRAY" -> NamedTextColor.GRAY;
            case "DARK_GRAY" -> NamedTextColor.DARK_GRAY;
            case "BLUE" -> NamedTextColor.BLUE;
            case "GREEN" -> NamedTextColor.GREEN;
            case "AQUA" -> NamedTextColor.AQUA;
            case "RED" -> NamedTextColor.RED;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "WHITE" -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }
    
    public List<Rarity> getRarities() {
        return rarities;
    }
    
    public List<Rarity> getSortedRarities() {
        List<Rarity> sorted = new ArrayList<>(rarities);
        sorted.sort((r1, r2) -> Double.compare(getCraftChance(r1.getId()), getCraftChance(r2.getId())));
        return sorted;
    }
    
    public Rarity getRarityById(String id) {
        return rarities.stream()
                .filter(rarity -> rarity.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    public double getCraftChance(String rarityId) {
        return craftChances.getOrDefault(rarityId, 0.0);
    }
    
    public String getCraftMessage() {
        return plugin.getConfig().getString("settings.craftMessage", "&aВы создали %rarity% предмет!");
    }
    
    public String getCraftSound() {
        return plugin.getConfig().getString("settings.craftSound", "ENTITY_PLAYER_LEVELUP");
    }
    
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("settings.enabled", true);
    }
    
    public boolean isDebugMode() {
        return plugin.getConfig().getBoolean("settings.debug", false);
    }
} 