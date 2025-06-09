package org.bedepay.rareItems.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class WeaponEffectListener implements Listener {
    private final RareItems plugin;
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    // Кулдауны для эффектов (UUID игрока -> время последнего использования)
    private final Map<UUID, Long> effectCooldowns = new HashMap<>();

    public WeaponEffectListener(RareItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) {
            return;
        }
        
        // Проверяем, есть ли у предмета редкость
        Rarity rarity = getRarity(weapon);
        if (rarity == null) {
            return;
        }
        
        // Проверяем, есть ли у редкости эффекты при ударе
        if (rarity.getOnHitEffects().isEmpty()) {
            return;
        }
        
        // Проверяем кулдаун
        if (isOnCooldown(player, rarity)) {
            return;
        }
        
        // Проверяем шанс срабатывания
        double roll = random.nextDouble() * 100.0;
        if (roll > rarity.getEffectChance()) {
            return;
        }
        
        // Применяем эффекты
        applyEffects(player, target, rarity);
        
        // Устанавливаем кулдаун
        setCooldown(player, rarity);
        
        // Играем эффекты
        playHitEffects(player, target, rarity);
        
        // Уведомляем игрока
        notifyPlayer(player, rarity, target);
    }
    
    private Rarity getRarity(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        String rarityId = meta.getPersistentDataContainer().get(
                ItemUtil.getKey(plugin, "rarity"), 
                PersistentDataType.STRING
        );
        
        if (rarityId == null) {
            return null;
        }
        
        return plugin.getConfigManager().getRarityById(rarityId);
    }
    
    private boolean isOnCooldown(Player player, Rarity rarity) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastUse = effectCooldowns.getOrDefault(playerId, 0L);
        
        return (currentTime - lastUse) < rarity.getEffectCooldown();
    }
    
    private void setCooldown(Player player, Rarity rarity) {
        effectCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    private void applyEffects(Player player, LivingEntity target, Rarity rarity) {
        for (Map.Entry<PotionEffectType, Integer> entry : rarity.getOnHitEffects().entrySet()) {
            PotionEffectType effectType = entry.getKey();
            int amplifier = entry.getValue();
            
            // Длительность зависит от редкости
            int duration = getDuration(rarity.getId()) * 20; // В тиках
            
            PotionEffect effect = new PotionEffect(effectType, duration, amplifier, false, true, true);
            target.addPotionEffect(effect);
            
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info(String.format("Игрок %s наложил эффект %s на %s", 
                        player.getName(), effectType.getName(), target.getName()));
            }
        }
    }
    
    private int getDuration(String rarityId) {
        return switch (rarityId) {
            case "celestial" -> 20; // 20 секунд
            case "divine" -> 15;    // 15 секунд
            case "mythic" -> 12;    // 12 секунд
            case "legendary" -> 10; // 10 секунд
            case "epic" -> 8;       // 8 секунд
            case "rare" -> 6;       // 6 секунд
            case "uncommon" -> 4;   // 4 секунды
            default -> 3;           // 3 секунды
        };
    }
    
    private void playHitEffects(Player player, LivingEntity target, Rarity rarity) {
        Location targetLocation = target.getLocation().add(0, 1, 0);
        
        // Звук
        Sound sound = getHitSound(rarity);
        if (sound != null) {
            player.playSound(targetLocation, sound, 0.7f, getHitPitch(rarity));
            target.getWorld().playSound(targetLocation, sound, 0.7f, getHitPitch(rarity));
        }
        
        // Частицы
        Particle particle = getHitParticle(rarity);
        if (particle != null) {
            target.getWorld().spawnParticle(particle, targetLocation, 15, 0.5, 0.5, 0.5, 0.1);
        }
    }
    
    private Sound getHitSound(Rarity rarity) {
        return switch (rarity.getId()) {
            case "celestial" -> Sound.ENTITY_WITHER_SPAWN;
            case "divine" -> Sound.ENTITY_ENDER_DRAGON_HURT;
            case "mythic" -> Sound.ENTITY_ENDERMAN_SCREAM;
            case "legendary" -> Sound.ENTITY_BLAZE_HURT;
            case "epic" -> Sound.ENTITY_WITCH_CELEBRATE;
            case "rare" -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            default -> Sound.ENTITY_ITEM_BREAK;
        };
    }
    
    private float getHitPitch(Rarity rarity) {
        return switch (rarity.getId()) {
            case "celestial" -> 0.5f;
            case "divine" -> 0.7f;
            case "mythic" -> 0.8f;
            case "legendary" -> 1.0f;
            case "epic" -> 1.2f;
            case "rare" -> 1.4f;
            default -> 1.0f;
        };
    }
    
    private Particle getHitParticle(Rarity rarity) {
        return switch (rarity.getId()) {
            case "celestial" -> Particle.END_ROD;
            case "divine" -> Particle.ENCHANTMENT_TABLE;
            case "mythic" -> Particle.DRAGON_BREATH;
            case "legendary" -> Particle.FLAME;
            case "epic" -> Particle.SPELL_WITCH;
            case "rare" -> Particle.CRIT_MAGIC;
            default -> Particle.CRIT;
        };
    }
    
    private void notifyPlayer(Player player, Rarity rarity, LivingEntity target) {
        // Создаем сообщение о срабатывании способности
        String message = switch (rarity.getId()) {
            case "celestial" -> "<gradient:#ff6b6b:#4ecdc4>✦ Небесная сила активирована! ✦</gradient>";
            case "divine" -> "<gradient:#a8edea:#fed6e3>✧ Божественная мощь! ✧</gradient>";
            case "mythic" -> "<gradient:#d299c2:#fef9d7>⚡ Мифическая энергия! ⚡</gradient>";
            case "legendary" -> "<gradient:#ffeaa7:#fab1a0>🔥 Легендарная сила! 🔥</gradient>";
            case "epic" -> "<gradient:#6c5ce7:#a29bfe>⭐ Эпическая способность! ⭐</gradient>";
            case "rare" -> "<gradient:#0984e3:#74b9ff>✨ Редкий эффект! ✨</gradient>";
            default -> "<color:" + rarity.getColor().asHexString() + ">• Особая способность! •</color>";
        };
        
        Component component = miniMessage.deserialize(message);
        
        // Отправляем сообщение только игроку (не спамим чат)
        player.sendActionBar(component);
    }
    
    // Очистка старых кулдаунов для экономии памяти
    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        long maxCooldown = 300000; // 5 минут
        
        effectCooldowns.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > maxCooldown
        );
    }
} 