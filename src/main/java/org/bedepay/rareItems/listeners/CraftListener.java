package org.bedepay.rareItems.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class CraftListener implements Listener {
    private final RareItems plugin;
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    // Кэш для улучшения производительности
    private final Map<Material, Boolean> weaponArmorCache = new ConcurrentHashMap<>();

    public CraftListener(RareItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!plugin.getConfigManager().isEnabled()) {
            return;
        }
        
        ItemStack result = event.getRecipe().getResult();
        
        // Проверка на null для безопасности
        if (result == null) {
            return;
        }
        
        // ВАЖНО: Проверяем, не является ли это результатом улучшения редкости
        // Если да - не применяем случайные шансы, улучшение уже обработано в RarityUpgradeListener
        ItemStack currentResult = event.getCurrentItem();
        if (currentResult != null && currentResult.getItemMeta() != null) {
            if (currentResult.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "rarity_upgrade"), 
                    PersistentDataType.BYTE)) {
                
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[RareItems Debug] Пропускаем обработку крафта - это улучшение редкости");
                }
                return; // Это улучшение редкости, не обрабатываем
            }
        }
        
        // Проверка, если предмет оружие или броня
        if (!isWeaponOrArmor(result.getType())) {
            return;
        }
        
        // Проверяем, не имеет ли предмет уже редкость от нашего плагина
        if (hasOurRarity(result)) {
            return;
        }
        
        // Проверяем настройки совместимости
        if (shouldIgnoreItem(result)) {
            return;
        }
        
        // Определяем, должен ли предмет иметь редкость (ИСПРАВЛЕНА ЛОГИКА ШАНСОВ)
        Rarity selectedRarity = selectRarity();
        if (selectedRarity == null) {
            return;
        }
        
        // Применяем редкость к предмету
        ItemStack rareItem = ItemUtil.applyRarity(plugin, result.clone(), selectedRarity);
        
        // Заменяем результат редким предметом
        event.setCurrentItem(rareItem);
        
        // Уведомляем игрока и создаем эффекты
        if (event.getWhoClicked() instanceof Player player) {
            notifyPlayer(player, selectedRarity, rareItem.getType());
            playEffects(player, selectedRarity);
            
                    // Логирование для отладки
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info(String.format("[RareItems Debug] Обычный крафт: Игрок %s получил %s предмет: %s", 
                    player.getName(), selectedRarity.getName(), rareItem.getType().name()));
        }
        }
    }
    
    private boolean isWeaponOrArmor(Material material) {
        // Используем кэш для повышения производительности
        return weaponArmorCache.computeIfAbsent(material, mat -> {
            String name = mat.name();
            return name.endsWith("_SWORD") || 
                   name.endsWith("_AXE") || 
                   name.endsWith("_HELMET") || 
                   name.endsWith("_CHESTPLATE") || 
                   name.endsWith("_LEGGINGS") || 
                   name.endsWith("_BOOTS") ||
                   name.equals("BOW") ||
                   name.equals("CROSSBOW") ||
                   name.equals("TRIDENT") ||
                   name.equals("SHIELD") ||
                   (name.endsWith("_HOE") && plugin.getConfig().getBoolean("settings.includeHoes", false));
        });
    }
    
    private boolean hasOurRarity(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        // Проверяем наличие нашего ключа в PersistentDataContainer
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(ItemUtil.getKey(plugin, "rarity"), PersistentDataType.STRING);
    }
    
    private boolean shouldIgnoreItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        
        // Проверяем настройки совместимости
        boolean ignoreWithAttributes = plugin.getConfig().getBoolean("compatibility.ignoreItemsWithAttributes", true);
        boolean ignoreWithCustomNames = plugin.getConfig().getBoolean("compatibility.ignoreItemsWithCustomNames", false);
        
        if (ignoreWithAttributes && meta != null && meta.hasAttributeModifiers()) {
            return true;
        }
        
        if (ignoreWithCustomNames && meta != null && meta.hasDisplayName()) {
            return true;
        }
        
        // Проверяем список игнорируемых материалов
        List<String> ignoredMaterials = plugin.getConfig().getStringList("compatibility.ignoredMaterials");
        if (ignoredMaterials.contains(item.getType().name())) {
            return true;
        }
        
        // Проверяем список игнорируемых имен
        if (meta != null && meta.hasDisplayName()) {
            String displayName = LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName());
            List<String> ignoredNames = plugin.getConfig().getStringList("compatibility.ignoredItemNames");
            
            for (String ignoredName : ignoredNames) {
                if (displayName.toLowerCase().contains(ignoredName.toLowerCase())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private Rarity selectRarity() {
        List<Rarity> sortedRarities = plugin.getConfigManager().getSortedRarities();
        if (sortedRarities.isEmpty()) {
            return null;
        }
        
        double roll = random.nextDouble() * 100.0;
        
        // ИСПРАВЛЕНА ЛОГИКА: идем от самой редкой к менее редкой
        for (Rarity rarity : sortedRarities) {
            double chance = plugin.getConfigManager().getCraftChance(rarity.getId());
            
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info(String.format("Проверяем редкость %s (шанс: %.2f%%, ролл: %.2f%%)", 
                        rarity.getName(), chance, roll));
            }
            
            if (roll <= chance) {
                return rarity;
            }
        }
        
        return null; // Обычный предмет
    }
    
    private void notifyPlayer(Player player, Rarity rarity, Material material) {
        // Получаем сообщение из конфига
        String messageTemplate = plugin.getConfigManager().getCraftMessage();
        
        // Заменяем переменные
        String materialName = material.name().toLowerCase().replace('_', ' ');
        String capitalizedMaterial = capitalizeWords(materialName);
        
        messageTemplate = messageTemplate
                .replace("%player%", player.getName())
                .replace("%rarity%", rarity.getName())
                .replace("%item%", capitalizedMaterial);
        
        // Создаем красивое сообщение с градиентом в зависимости от редкости
        String message = createRarityMessage(rarity, messageTemplate);
        
        // Отправляем сообщение
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
        
        // Отправляем title для очень редких предметов
        if (isVeryRare(rarity.getId())) {
            sendTitle(player, rarity, capitalizedMaterial);
        }
        
        // Уведомляем других игроков о получении очень редкого предмета
        if (isExtremelyRare(rarity.getId())) {
            announceToServer(player, rarity, capitalizedMaterial);
        }
    }
    
    private String createRarityMessage(Rarity rarity, String message) {
        return switch (rarity.getId()) {
            case "celestial" -> "<gradient:#ff6b6b:#4ecdc4>✦✦✦ " + message + " ✦✦✦</gradient>";
            case "divine" -> "<gradient:#a8edea:#fed6e3>✦✦ " + message + " ✦✦</gradient>";
            case "mythic" -> "<gradient:#d299c2:#fef9d7>✦ " + message + " ✦</gradient>";
            case "legendary" -> "<gradient:#ffeaa7:#fab1a0>" + message + "</gradient>";
            case "epic" -> "<gradient:#6c5ce7:#a29bfe>" + message + "</gradient>";
            case "rare" -> "<gradient:#0984e3:#74b9ff>" + message + "</gradient>";
            case "uncommon" -> "<gradient:#00b894:#55a3ff>" + message + "</gradient>";
            default -> "<color:" + rarity.getColor().asHexString() + ">" + message + "</color>";
        };
    }
    
    private void sendTitle(Player player, Rarity rarity, String materialName) {
        String title = switch (rarity.getId()) {
            case "celestial" -> "<gradient:#ff6b6b:#4ecdc4>НЕБЕСНЫЙ ПРЕДМЕТ!</gradient>";
            case "divine" -> "<gradient:#a8edea:#fed6e3>БОЖЕСТВЕННЫЙ ПРЕДМЕТ!</gradient>";
            case "mythic" -> "<gradient:#d299c2:#fef9d7>МИФИЧЕСКИЙ ПРЕДМЕТ!</gradient>";
            case "legendary" -> "<gradient:#ffeaa7:#fab1a0>ЛЕГЕНДАРНЫЙ ПРЕДМЕТ!</gradient>";
            default -> "<color:" + rarity.getColor().asHexString() + ">" + rarity.getName().toUpperCase() + " ПРЕДМЕТ!</color>";
        };
        
        String subtitle = "<color:" + rarity.getColor().asHexString() + ">" + materialName + "</color>";
        
        player.showTitle(net.kyori.adventure.title.Title.title(
                miniMessage.deserialize(title),
                miniMessage.deserialize(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(3000),
                        java.time.Duration.ofMillis(1000)
                )
        ));
    }
    
    private void announceToServer(Player player, Rarity rarity, String materialName) {
        String announcement = String.format("<bold>🎉 Игрок %s получил %s %s! 🎉</bold>", 
                player.getName(), rarity.getName(), materialName);
        
        String message = switch (rarity.getId()) {
            case "celestial" -> "<gradient:#ff6b6b:#4ecdc4>" + announcement + "</gradient>";
            case "divine" -> "<gradient:#a8edea:#fed6e3>" + announcement + "</gradient>";
            default -> "<color:" + rarity.getColor().asHexString() + ">" + announcement + "</color>";
        };
        
        Component component = miniMessage.deserialize(message);
        
        // Отправляем всем игрокам на сервере
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(component);
        }
    }
    
    private void playEffects(Player player, Rarity rarity) {
        Location location = player.getLocation();
        
        // Звуки
        Sound sound = getSound(rarity);
        if (sound != null) {
            player.playSound(location, sound, 1.0f, getPitch(rarity));
        }
        
        // Особые эффекты для очень редких предметов
        if (isExtremelyRare(rarity.getId())) {
            spawnEpicEffects(player, rarity);
        }
        
        // Частицы
        Particle particle = getParticle(rarity);
        if (particle != null) {
            // Создаем красивый эффект частиц вокруг игрока
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnParticleEffect(player, particle, rarity);
            }, 10L); // Задержка в 10 тиков
        }
    }
    
    private void spawnEpicEffects(Player player, Rarity rarity) {
        Location loc = player.getLocation();
        
        // Мощные звуковые эффекты
        player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.3f, 1.8f);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        
        // Эпичные частицы в зависимости от редкости
        switch (rarity.getId()) {
            case "celestial" -> {
                // Небесные эффекты - кольцо частиц
                for (int i = 0; i < 360; i += 15) {
                    double angle = Math.toRadians(i);
                    double x = Math.cos(angle) * 3;
                    double z = Math.sin(angle) * 3;
                    Location particleLoc = loc.clone().add(x, 2, z);
                    player.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, particleLoc, 2, 0.1, 0.1, 0.1, 0);
                }
                // Центральный взрыв
                player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, loc.add(0, 1, 0), 100, 2, 2, 2, 0.1);
            }
            case "divine" -> {
                // Божественные эффекты - спираль
                for (int i = 0; i < 30; i++) {
                    double angle = i * 0.4;
                    double radius = i * 0.08;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location spiralLoc = loc.clone().add(x, i * 0.08, z);
                    player.getWorld().spawnParticle(Particle.TOTEM, spiralLoc, 2, 0.1, 0.1, 0.1, 0);
                }
            }
            case "mythic" -> {
                // Мифические эффекты - портал
                player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc.add(0, 1, 0), 40, 1.5, 1.5, 1.5, 0.1);
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 80, 2, 2, 2, 0.1);
            }
        }
    }
    
    private Sound getSound(Rarity rarity) {
        String soundName = rarity.getSound();
        if (soundName == null || soundName.equals("NONE")) {
            soundName = plugin.getConfigManager().getCraftSound();
        }
        
        if (soundName.equals("NONE")) {
            return null;
        }
        
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Неверный звук: " + soundName);
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }
    
    private float getPitch(Rarity rarity) {
        return switch (rarity.getId()) {
            case "celestial" -> 2.0f;
            case "divine" -> 1.8f;
            case "mythic" -> 1.6f;
            case "legendary" -> 1.4f;
            case "epic" -> 1.2f;
            case "rare" -> 1.1f;
            default -> 1.0f;
        };
    }
    
    private Particle getParticle(Rarity rarity) {
        String particleName = rarity.getParticle();
        if (particleName == null || particleName.equals("NONE")) {
            particleName = switch (rarity.getId()) {
                case "celestial" -> "END_ROD";
                case "divine" -> "ENCHANTMENT_TABLE";
                case "mythic" -> "DRAGON_BREATH";
                case "legendary" -> "TOTEM";
                case "epic" -> "WITCH";
                case "rare" -> "VILLAGER_HAPPY";
                default -> "NONE";
            };
        }
        
        if (particleName.equals("NONE")) {
            return null;
        }
        
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Неверная частица: " + particleName);
            return null;
        }
    }
    
    private void spawnParticleEffect(Player player, Particle particle, Rarity rarity) {
        Location location = player.getLocation().add(0, 1, 0);
        
        int count = switch (rarity.getId()) {
            case "celestial" -> 50;
            case "divine" -> 40;
            case "mythic" -> 30;
            case "legendary" -> 25;
            case "epic" -> 20;
            case "rare" -> 15;
            default -> 10;
        };
        
        // Спиральный эффект
        for (int i = 0; i < count; i++) {
            double angle = (i * 2 * Math.PI) / count;
            double radius = 1.5;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            double y = Math.sin(i * 0.5) * 0.5;
            
            Location particleLocation = location.clone().add(x, y, z);
            player.getWorld().spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }
    
    private boolean isVeryRare(String rarityId) {
        return rarityId.equals("legendary") || rarityId.equals("mythic") || 
               rarityId.equals("divine") || rarityId.equals("celestial");
    }
    
    private boolean isExtremelyRare(String rarityId) {
        return rarityId.equals("divine") || rarityId.equals("celestial");
    }
    
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
} 