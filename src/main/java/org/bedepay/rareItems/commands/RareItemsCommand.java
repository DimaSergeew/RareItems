package org.bedepay.rareItems.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RareItemsCommand implements CommandExecutor, TabCompleter {
    private final RareItems plugin;
    private final List<String> subCommands = Arrays.asList("reload", "info", "give", "inspect", "upgrade", "help", "debug");
    
    public RareItemsCommand(RareItems plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload" -> {
                if (!sender.hasPermission("rareitems.admin.reload")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                plugin.getConfigManager().reloadConfig();
                sender.sendMessage(Component.text("Конфигурация RareItems перезагружена!").color(NamedTextColor.GREEN));
            }
            case "info" -> {
                if (!sender.hasPermission("rareitems.admin.info")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                showInfo(sender);
            }
            case "give" -> {
                if (!sender.hasPermission("rareitems.admin.give")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Использование: /rareitems give <игрок> <материал> <редкость>").color(NamedTextColor.RED));
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок не найден.").color(NamedTextColor.RED));
                    return true;
                }
                
                Material material;
                try {
                    material = Material.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Неверный материал.").color(NamedTextColor.RED));
                    return true;
                }
                
                Rarity rarity = plugin.getConfigManager().getRarityById(args[3].toLowerCase());
                if (rarity == null) {
                    sender.sendMessage(Component.text("Редкость не найдена.").color(NamedTextColor.RED));
                    return true;
                }
                
                ItemStack item = new ItemStack(material);
                ItemStack rareItem = ItemUtil.applyRarity(plugin, item, rarity);
                
                target.getInventory().addItem(rareItem);
                target.sendMessage(Component.text("Вы получили " + rarity.getDisplayName() + " " + material.name().toLowerCase().replace('_', ' ')).color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Вы выдали " + rarity.getDisplayName() + " предмет игроку " + target.getName()).color(NamedTextColor.GREEN));
            }
            case "inspect" -> {
                if (!sender.hasPermission("rareitems.inspect")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Эта команда только для игроков.").color(NamedTextColor.RED));
                    return true;
                }
                
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) {
                    player.sendMessage(Component.text("Возьмите предмет в руку для проверки.").color(NamedTextColor.RED));
                    return true;
                }
                
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    player.sendMessage(Component.text("Этот предмет не имеет метаданных.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!meta.getPersistentDataContainer().has(ItemUtil.getKey(plugin, "rarity"), PersistentDataType.STRING)) {
                    player.sendMessage(Component.text("Этот предмет не имеет редкости.").color(NamedTextColor.RED));
                    return true;
                }
                
                String rarityId = meta.getPersistentDataContainer().get(ItemUtil.getKey(plugin, "rarity"), PersistentDataType.STRING);
                Rarity rarity = plugin.getConfigManager().getRarityById(rarityId);
                
                if (rarity == null) {
                    player.sendMessage(Component.text("Редкость предмета не найдена в конфигурации.").color(NamedTextColor.RED));
                    return true;
                }
                
                player.sendMessage(Component.text("Информация о предмете:").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("Редкость: " + rarity.getDisplayName()).color(rarity.getColor()));
                
                if (rarity.getDamageBonus() > 0) {
                    player.sendMessage(Component.text("Бонус урона: +" + formatBonus(rarity.getDamageBonus())).color(NamedTextColor.RED));
                }
                
                if (rarity.getArmorBonus() > 0) {
                    player.sendMessage(Component.text("Бонус брони: +" + formatBonus(rarity.getArmorBonus())).color(NamedTextColor.BLUE));
                }
                
                if (rarity.getToughnessBonus() > 0) {
                    player.sendMessage(Component.text("Бонус прочности: +" + formatBonus(rarity.getToughnessBonus())).color(NamedTextColor.BLUE));
                }
                
                if (rarity.getSpeedBonus() > 0) {
                    player.sendMessage(Component.text("Бонус скорости: +" + formatBonus(rarity.getSpeedBonus() * 100) + "%").color(NamedTextColor.GREEN));
                }
            }
            case "upgrade" -> {
                if (!sender.hasPermission("rareitems.admin.upgrade")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Эта команда только для игроков.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage(Component.text("Использование: /rareitems upgrade <редкость>").color(NamedTextColor.RED));
                    return true;
                }
                
                Rarity rarity = plugin.getConfigManager().getRarityById(args[1].toLowerCase());
                if (rarity == null) {
                    player.sendMessage(Component.text("Редкость не найдена.").color(NamedTextColor.RED));
                    return true;
                }
                
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) {
                    player.sendMessage(Component.text("Возьмите предмет в руку для улучшения.").color(NamedTextColor.RED));
                    return true;
                }
                
                ItemStack upgradedItem = ItemUtil.applyRarity(plugin, item, rarity);
                player.getInventory().setItemInMainHand(upgradedItem);
                player.sendMessage(Component.text("Предмет улучшен до " + rarity.getDisplayName() + " редкости!").color(NamedTextColor.GREEN));
            }
            case "debug" -> {
                if (!sender.hasPermission("rareitems.admin.debug")) {
                    sender.sendMessage(Component.text("У вас нет прав для этой команды.").color(NamedTextColor.RED));
                    return true;
                }
                
                boolean debugMode = plugin.getConfig().getBoolean("settings.debug", false);
                plugin.getConfig().set("settings.debug", !debugMode);
                plugin.saveConfig();
                
                if (!debugMode) {
                    sender.sendMessage(Component.text("Режим отладки включен.").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Режим отладки выключен.").color(NamedTextColor.RED));
                }
            }
            default -> {
                showHelp(sender);
            }
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== RareItems Команды ===").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        
        if (sender.hasPermission("rareitems.admin.reload")) {
            sender.sendMessage(Component.text("/rareitems reload").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Перезагрузить конфигурацию плагина").color(NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("rareitems.admin.info")) {
            sender.sendMessage(Component.text("/rareitems info").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Показать информацию о настроенных редкостях").color(NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("rareitems.admin.give")) {
            sender.sendMessage(Component.text("/rareitems give <игрок> <материал> <редкость>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Выдать предмет определенной редкости").color(NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("rareitems.inspect")) {
            sender.sendMessage(Component.text("/rareitems inspect").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Проверить редкость предмета в руке").color(NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("rareitems.admin.upgrade")) {
            sender.sendMessage(Component.text("/rareitems upgrade <редкость>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Обновить предмет в руке до указанной редкости").color(NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("rareitems.admin.debug")) {
            sender.sendMessage(Component.text("/rareitems debug").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Переключить режим отладки").color(NamedTextColor.WHITE)));
        }
    }
    
    private void showInfo(CommandSender sender) {
        List<Rarity> rarities = plugin.getConfigManager().getRarities();
        
        sender.sendMessage(Component.text("=== RareItems Информация ===").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Настроенные редкости: " + rarities.size()).color(NamedTextColor.YELLOW));
        
        for (Rarity rarity : rarities) {
            double chance = plugin.getConfigManager().getCraftChance(rarity.getId());
            
            sender.sendMessage(Component.text("• " + rarity.getDisplayName())
                    .append(Component.text(" (Шанс: " + chance + "%)").color(NamedTextColor.GRAY)));
            
            Component attributes = Component.text("  Атрибуты: ").color(NamedTextColor.GRAY);
            boolean hasAttributes = false;
            
            if (rarity.getDamageBonus() > 0) {
                attributes = attributes.append(Component.text("Урон +" + formatBonus(rarity.getDamageBonus())).color(NamedTextColor.RED));
                hasAttributes = true;
            }
            
            if (rarity.getArmorBonus() > 0) {
                if (hasAttributes) {
                    attributes = attributes.append(Component.text(", ").color(NamedTextColor.GRAY));
                }
                attributes = attributes.append(Component.text("Броня +" + formatBonus(rarity.getArmorBonus())).color(NamedTextColor.BLUE));
                hasAttributes = true;
            }
            
            if (rarity.getToughnessBonus() > 0) {
                if (hasAttributes) {
                    attributes = attributes.append(Component.text(", ").color(NamedTextColor.GRAY));
                }
                attributes = attributes.append(Component.text("Прочность +" + formatBonus(rarity.getToughnessBonus())).color(NamedTextColor.BLUE));
                hasAttributes = true;
            }
            
            if (rarity.getSpeedBonus() > 0) {
                if (hasAttributes) {
                    attributes = attributes.append(Component.text(", ").color(NamedTextColor.GRAY));
                }
                attributes = attributes.append(Component.text("Скорость +" + formatBonus(rarity.getSpeedBonus() * 100) + "%").color(NamedTextColor.GREEN));
                hasAttributes = true;
            }
            
            if (hasAttributes) {
                sender.sendMessage(attributes);
            }
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    if (subCommand.equals("reload") && !sender.hasPermission("rareitems.admin.reload")) continue;
                    if (subCommand.equals("info") && !sender.hasPermission("rareitems.admin.info")) continue;
                    if (subCommand.equals("give") && !sender.hasPermission("rareitems.admin.give")) continue;
                    if (subCommand.equals("inspect") && !sender.hasPermission("rareitems.inspect")) continue;
                    if (subCommand.equals("upgrade") && !sender.hasPermission("rareitems.admin.upgrade")) continue;
                    if (subCommand.equals("debug") && !sender.hasPermission("rareitems.admin.debug")) continue;
                    
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("rareitems.admin.give")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("rareitems.admin.upgrade")) {
                for (Rarity rarity : plugin.getConfigManager().getRarities()) {
                    if (rarity.getId().startsWith(args[1].toLowerCase())) {
                        completions.add(rarity.getId());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("rareitems.admin.give")) {
                for (Material material : Material.values()) {
                    if (material.isItem() && isWeaponOrArmor(material) && 
                        material.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(material.name());
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("rareitems.admin.give")) {
                for (Rarity rarity : plugin.getConfigManager().getRarities()) {
                    if (rarity.getId().startsWith(args[3].toLowerCase())) {
                        completions.add(rarity.getId());
                    }
                }
            }
        }
        
        return completions;
    }
    
    /**
     * Форматирует бонус для отображения (показывает десятичные числа если меньше 1)
     */
    private String formatBonus(double bonus) {
        if (bonus == (int) bonus) {
            // Если число целое, показываем без десятичных
            return String.valueOf((int) bonus);
        } else {
            // Показываем с одним знаком после запятой
            return String.format("%.1f", bonus);
        }
    }
    
    /**
     * Проверяет, является ли материал оружием или броней
     */
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
               name.equals("SHIELD") ||
               (name.endsWith("_HOE") && plugin.getConfig().getBoolean("settings.includeHoes", false));
    }
} 