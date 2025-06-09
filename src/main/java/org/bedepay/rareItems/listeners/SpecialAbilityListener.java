package org.bedepay.rareItems.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.config.ConfigManager;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SpecialAbilityListener implements Listener {
    
    private final RareItems plugin;
    private final ConfigManager configManager;
    private final Random random = new Random();
    
    // Кулдауны способностей
    private final Map<UUID, Long> lastAbilityUse = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    
    // Постоянные эффекты от ношения брони
    private final Map<UUID, BukkitRunnable> activeEffects = new HashMap<>();
    
    public SpecialAbilityListener(RareItems plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        
        // Запускаем проверку экипировки каждые 5 секунд
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkArmorEffects(player);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Каждые 5 секунд
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;
        
        Rarity rarity = ItemUtil.getRarity(weapon);
        if (rarity == null) return;
        
        handleWeaponAbility(attacker, target, weapon, rarity, event);
    }
    
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (!bow.getType().name().equals("BOW") && !bow.getType().name().equals("CROSSBOW")) return;
        
        Rarity rarity = ItemUtil.getRarity(bow);
        if (rarity == null) return;
        
        handleBowAbility(shooter, arrow, rarity);
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        
        // Проверяем есть ли данные о луке в metadata стрелы
        if (arrow.hasMetadata("rare_bow_ability")) {
            String abilityType = arrow.getMetadata("rare_bow_ability").get(0).asString();
            handleArrowHitAbility(shooter, arrow, event.getHitEntity(), abilityType);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        
        if (boots == null || boots.getType() == Material.AIR) return;
        
        Rarity rarity = ItemUtil.getRarity(boots);
        if (rarity == null) return;
        
        handleBootsMovement(player, boots, rarity);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) return;
        
        Rarity rarity = ItemUtil.getRarity(boots);
        if (rarity == null) return;
        
        // Телепорт для небесных ботинок
        if (rarity.getId().equals("celestial")) {
            handleTeleportAbility(player);
        }
    }
    
    private void handleWeaponAbility(Player attacker, LivingEntity target, ItemStack weapon, Rarity rarity, EntityDamageByEntityEvent event) {
        String materialName = weapon.getType().name();
        String rarityId = rarity.getId();
        
        // Проверяем кулдаун
        UUID playerId = attacker.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastAbilityUse.containsKey(playerId) && currentTime - lastAbilityUse.get(playerId) < 3000) {
            return; // 3 секунды кулдаун
        }
        
        // Мечи - критические удары
        if (materialName.endsWith("_SWORD")) {
            double critChance = switch (rarityId) {
                case "uncommon" -> 0.05;
                case "rare" -> 0.10;
                case "epic" -> 0.15;
                case "legendary" -> 0.20;
                case "mythic" -> 0.25;
                case "divine" -> 0.30;
                case "celestial" -> 0.35;
                default -> 0.0;
            };
            
                         if (random.nextDouble() < critChance) {
                 // Критический удар (нужен доступ к event из параметра метода)
                 // Урон будет увеличен в другом методе через reflection или другую механику
                
                // Эффекты
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 20, 0.5, 1, 0.5, 0);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                
                attacker.showTitle(Title.title(
                    Component.empty(),
                    Component.text("КРИТИЧЕСКИЙ УДАР!").color(NamedTextColor.RED),
                    Title.Times.times(
                        java.time.Duration.ofMillis(250),
                        java.time.Duration.ofMillis(1000),
                        java.time.Duration.ofMillis(250)
                    )
                ));
                
                lastAbilityUse.put(playerId, currentTime);
            }
        }
        
        // Топоры - эффекты замедления и оглушения
        if (materialName.endsWith("_AXE")) {
            switch (rarityId) {
                                 case "uncommon", "rare" -> {
                     target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0));
                     target.getWorld().spawnParticle(Particle.BLOCK_CRACK, target.getLocation(), 10, 0.3, 0.5, 0.3, 0);
                 }
                 case "epic", "legendary" -> {
                     target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                     target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0));
                     target.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, target.getLocation(), 15, 0.5, 0.5, 0.5, 0);
                 }
                 case "mythic", "divine", "celestial" -> {
                     target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2));
                     target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
                     target.setVelocity(new Vector(0, 0, 0)); // Полная остановка
                     target.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, target.getLocation(), 5, 0.3, 0.3, 0.3, 0);
                 }
            }
            
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
            lastAbilityUse.put(playerId, currentTime);
        }
        
        // Трезубцы - водные эффекты
        if (materialName.equals("TRIDENT")) {
            switch (rarityId) {
                                 case "uncommon", "rare" -> {
                     target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                     target.getWorld().spawnParticle(Particle.WATER_SPLASH, target.getLocation(), 20, 1, 1, 1, 0);
                 }
                case "epic" -> {
                    // Молния
                    target.getWorld().strikeLightning(target.getLocation());
                }
                case "legendary", "mythic" -> {
                    // Водный вихрь - притягивает врагов
                    Location center = attacker.getLocation();
                    for (LivingEntity entity : target.getWorld().getLivingEntities()) {
                        if (entity != attacker && entity.getLocation().distance(center) <= 5) {
                            Vector direction = center.toVector().subtract(entity.getLocation().toVector()).normalize();
                            entity.setVelocity(direction.multiply(0.5));
                        }
                    }
                    target.getWorld().spawnParticle(Particle.WATER_WAKE, center, 50, 3, 1, 3, 0);
                }
                case "divine", "celestial" -> {
                    // Мощная буря
                    Location center = target.getLocation();
                    for (int i = 0; i < 3; i++) {
                        Location strikeLocation = center.clone().add(
                            random.nextGaussian() * 3,
                            0,
                            random.nextGaussian() * 3
                        );
                        target.getWorld().strikeLightning(strikeLocation);
                    }
                }
            }
            
            attacker.playSound(attacker.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.2f);
            lastAbilityUse.put(playerId, currentTime);
        }
    }
    
    private void handleBowAbility(Player shooter, Arrow arrow, Rarity rarity) {
        String rarityId = rarity.getId();
        
        switch (rarityId) {
            case "uncommon", "rare" -> {
                // Быстрая стрельба
                arrow.setVelocity(arrow.getVelocity().multiply(1.2));
                arrow.setMetadata("rare_bow_ability", new org.bukkit.metadata.FixedMetadataValue(plugin, "fast"));
            }
            case "epic" -> {
                // Пробивание
                arrow.setMetadata("rare_bow_ability", new org.bukkit.metadata.FixedMetadataValue(plugin, "piercing"));
            }
            case "legendary" -> {
                // Огненные стрелы
                arrow.setFireTicks(200);
                arrow.setMetadata("rare_bow_ability", new org.bukkit.metadata.FixedMetadataValue(plugin, "fire"));
            }
            case "mythic" -> {
                // Взрывные стрелы
                arrow.setMetadata("rare_bow_ability", new org.bukkit.metadata.FixedMetadataValue(plugin, "explosive"));
            }
            case "divine", "celestial" -> {
                // Божественные стрелы
                arrow.setVelocity(arrow.getVelocity().multiply(1.5));
                arrow.setMetadata("rare_bow_ability", new org.bukkit.metadata.FixedMetadataValue(plugin, "divine"));
            }
        }
    }
    
    private void handleArrowHitAbility(Player shooter, Arrow arrow, org.bukkit.entity.Entity hitEntity, String abilityType) {
        Location hitLocation = arrow.getLocation();
        
        switch (abilityType) {
            case "explosive" -> {
                // Взрыв без разрушения блоков
                hitLocation.getWorld().createExplosion(hitLocation, 2.0f, false, false);
            }
            case "divine" -> {
                // Божественное исцеление стрелка и урон врагам
                shooter.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                if (hitEntity instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                }
                hitLocation.getWorld().spawnParticle(Particle.END_ROD, hitLocation, 20, 1, 1, 1, 0);
            }
        }
    }
    
    private void handleBootsMovement(Player player, ItemStack boots, Rarity rarity) {
        String rarityId = rarity.getId();
        Location loc = player.getLocation();
        
        switch (rarityId) {
            case "rare" -> {
                // Хождение по воде
                Block below = loc.clone().subtract(0, 1, 0).getBlock();
                if (below.getType() == Material.WATER) {
                    below.setType(Material.FROSTED_ICE);
                    // Убираем лед через 5 секунд
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (below.getType() == Material.FROSTED_ICE) {
                                below.setType(Material.WATER);
                            }
                        }
                    }.runTaskLater(plugin, 100L);
                }
            }
            case "epic" -> {
                // Ледяная дорожка
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = loc.clone().add(x, -1, z).getBlock();
                        if (block.getType() == Material.WATER) {
                            block.setType(Material.FROSTED_ICE);
                        }
                    }
                }
            }
            case "legendary" -> {
                // Огненные следы
                Block below = loc.clone().subtract(0, 1, 0).getBlock();
                if (below.getType().isSolid()) {
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.3, 0, 0.3, 0);
                    
                    // Поджигаем ближайших врагов
                    for (LivingEntity entity : player.getWorld().getLivingEntities()) {
                        if (entity != player && entity.getLocation().distance(loc) <= 2) {
                            entity.setFireTicks(40);
                        }
                    }
                }
            }
            case "divine" -> {
                // Хождение по лаве
                Block below = loc.clone().subtract(0, 1, 0).getBlock();
                if (below.getType() == Material.LAVA) {
                    below.setType(Material.OBSIDIAN);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (below.getType() == Material.OBSIDIAN) {
                                below.setType(Material.LAVA);
                            }
                        }
                    }.runTaskLater(plugin, 200L);
                }
                
                // Иммунитет к огню
                player.setFireTicks(0);
            }
        }
    }
    
    private void handleTeleportAbility(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastTeleport.containsKey(playerId) && currentTime - lastTeleport.get(playerId) < 10000) {
            return; // 10 секунд кулдаун
        }
        
        // Телепорт в направлении взгляда на 10 блоков
        Location current = player.getLocation();
        Vector direction = current.getDirection().normalize().multiply(10);
        Location target = current.add(direction);
        
        // Проверяем безопасность места телепорта
        while (target.getBlock().getType().isSolid() && target.getY() > 0) {
            target.subtract(0, 1, 0);
        }
        
        if (target.getY() > 0) {
            player.teleport(target);
            player.getWorld().spawnParticle(Particle.PORTAL, current, 30, 1, 1, 1, 0);
            player.getWorld().spawnParticle(Particle.PORTAL, target, 30, 1, 1, 1, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            
            lastTeleport.put(playerId, currentTime);
        }
    }
    
    private void checkArmorEffects(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Шлемы
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getType() != Material.AIR) {
            Rarity rarity = ItemUtil.getRarity(helmet);
            if (rarity != null) {
                applyHelmetEffects(player, rarity);
            }
        }
        
        // Нагрудники
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getType() != Material.AIR) {
            Rarity rarity = ItemUtil.getRarity(chestplate);
            if (rarity != null) {
                applyChestplateEffects(player, rarity);
            }
        }
        
        // Поножи
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && leggings.getType() != Material.AIR) {
            Rarity rarity = ItemUtil.getRarity(leggings);
            if (rarity != null) {
                applyLeggingsEffects(player, rarity);
            }
        }
    }
    
    private void applyHelmetEffects(Player player, Rarity rarity) {
        switch (rarity.getId()) {
            case "rare" -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0));
            case "epic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0));
                // Защита от слепоты
                if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                }
            }
            case "legendary" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 120, 0));
            }
            case "mythic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 120, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));
            }
        }
    }
    
    private void applyChestplateEffects(Player player, Rarity rarity) {
        switch (rarity.getId()) {
            case "legendary" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0));
                         case "mythic" -> {
                 player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0));
                 player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 0));
             }
             case "divine", "celestial" -> {
                 player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
                 player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 1));
             }
        }
    }
    
    private void applyLeggingsEffects(Player player, Rarity rarity) {
        switch (rarity.getId()) {
            case "rare" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0));
            case "epic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 120, 0));
            }
            case "legendary" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 120, 1));
            }
            case "mythic", "divine", "celestial" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 120, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0));
            }
        }
    }
} 