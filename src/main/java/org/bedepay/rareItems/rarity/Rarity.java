package org.bedepay.rareItems.rarity;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

public class Rarity {
    private final String id;
    private final String name;
    private final TextColor color;
    private final double damageBonus;
    private final double armorBonus;
    private final double speedBonus;
    private final double toughnessBonus;
    private final double attackSpeedBonus;
    private final double healthBonus;
    private final double luckBonus;
    private final List<String> enchantments;
    private final Map<PotionEffectType, Integer> onHitEffects;
    private final int effectCooldown;
    private final double effectChance;
    private final String particle;
    private final String sound;
    
    // Уникальные способности для разных типов предметов
    private final Map<String, Object> specialAbilities;

    public Rarity(String id, String name, TextColor color, double damageBonus, double armorBonus, 
                  double speedBonus, double toughnessBonus, double attackSpeedBonus, double healthBonus,
                  double luckBonus, List<String> enchantments, Map<PotionEffectType, Integer> onHitEffects,
                  int effectCooldown, double effectChance, String particle, String sound, 
                  Map<String, Object> specialAbilities) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.damageBonus = damageBonus;
        this.armorBonus = armorBonus;
        this.speedBonus = speedBonus;
        this.toughnessBonus = toughnessBonus;
        this.attackSpeedBonus = attackSpeedBonus;
        this.healthBonus = healthBonus;
        this.luckBonus = luckBonus;
        this.enchantments = enchantments;
        this.onHitEffects = onHitEffects;
        this.effectCooldown = effectCooldown;
        this.effectChance = effectChance;
        this.particle = particle;
        this.sound = sound;
        this.specialAbilities = specialAbilities;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TextColor getColor() {
        return color;
    }

    public double getDamageBonus() {
        return damageBonus;
    }

    public double getArmorBonus() {
        return armorBonus;
    }

    public double getSpeedBonus() {
        return speedBonus;
    }

    public double getToughnessBonus() {
        return toughnessBonus;
    }

    public double getAttackSpeedBonus() {
        return attackSpeedBonus;
    }

    public double getHealthBonus() {
        return healthBonus;
    }

    public double getLuckBonus() {
        return luckBonus;
    }

    public List<String> getEnchantments() {
        return enchantments;
    }

    public Map<PotionEffectType, Integer> getOnHitEffects() {
        return onHitEffects;
    }

    public int getEffectCooldown() {
        return effectCooldown;
    }

    public double getEffectChance() {
        return effectChance;
    }

    public String getParticle() {
        return particle;
    }

    public String getSound() {
        return sound;
    }
    
    public String getDisplayName() {
        return name;
    }
    
    public Map<String, Object> getSpecialAbilities() {
        return specialAbilities;
    }
} 