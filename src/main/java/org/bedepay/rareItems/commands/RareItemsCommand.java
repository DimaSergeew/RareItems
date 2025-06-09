package org.bedepay.rareItems.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bedepay.rareItems.RareItems;
import org.bedepay.rareItems.rarity.Rarity;
import org.bedepay.rareItems.util.ItemUtil;
import org.bedepay.rareItems.util.MaterialTypeChecker;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Современная реализация команд RareItems с улучшенной архитектурой и UX
 * 
 * @author BedePay
 * @version 3.0
 */
public class RareItemsCommand implements CommandExecutor, TabCompleter {
    
    private final RareItems plugin;
    private final MaterialTypeChecker materialTypeChecker;
    
    // Кэш для Tab Completion
    private final Map<String, List<String>> tabCompletionCache = new HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 30000; // 30 секунд
    
    // Подкоманды с их разрешениями
    private final Map<String, String> subCommandPermissions = Map.of(
        "reload", "rareitems.admin.reload",
        "info", "rareitems.admin.info", 
        "give", "rareitems.admin.give",
        "inspect", "rareitems.inspect",
        "upgrade", "rareitems.admin.upgrade",
        "debug", "rareitems.admin.debug",
        "validate", "rareitems.admin.validate",
        "help", ""
    );
    
    public RareItemsCommand(RareItems plugin) {
        this.plugin = plugin;
        this.materialTypeChecker = new MaterialTypeChecker(plugin);
        updateTabCompletionCache();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        // Обновляем кэш при необходимости
        updateCacheIfNeeded();
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender);
            case "upgrade" -> handleUpgrade(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "validate" -> handleValidate(sender);
            case "help" -> { showHelp(sender); yield true; }
            default -> handleUnknownCommand(sender, subCommand);
        };
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String[] args) {
        
        updateCacheIfNeeded();
        
        return switch (args.length) {
            case 1 -> getFilteredSubCommands(sender, args[0]);
            case 2 -> getSecondArgumentCompletions(sender, args);
            case 3 -> getThirdArgumentCompletions(sender, args);
            case 4 -> getFourthArgumentCompletions(sender, args);
            case 5 -> getFifthArgumentCompletions(sender, args);
            default -> List.of();
        };
    }
    
    // ================================
    // Обработчики команд
    // ================================
    
    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "rareitems.admin.reload")) {
            return true;
        }
        
        try {
            // Используем новый метод полной перезагрузки
            plugin.reloadPlugin();
            
            // Обновляем кэш tab completion после перезагрузки
            updateTabCompletionCache();
            
            sendSuccessMessage(sender, "✅ Плагин RareItems полностью перезагружен!");
            sendSuccessMessage(sender, "  📋 Конфигурация обновлена");
            sendSuccessMessage(sender, "  🗂️ Кэши очищены");
            sendSuccessMessage(sender, "  🎯 MaterialTypeChecker обновлен");
            
            if (plugin.getConfigManager().isDebugMode()) {
                logDebug("Плагин полностью перезагружен игроком: %s", sender.getName());
            }
            
        } catch (Exception e) {
            sendErrorMessage(sender, "❌ Ошибка при перезагрузке: " + e.getMessage());
            plugin.getLogger().severe("Ошибка при перезагрузке плагина: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "rareitems.admin.info")) {
            return true;
        }
        
        if (args.length == 1) {
            showGeneralInfo(sender);
        } else {
            String rarityId = args[1].toLowerCase();
            showRarityInfo(sender, rarityId);
        }
        
        return true;
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "rareitems.admin.give")) {
            return true;
        }
        
        if (args.length < 4) {
            sendUsageMessage(sender, "/rareitems give <игрок> <материал> <редкость> [количество]");
            return true;
        }
        
        // Парсинг аргументов
        String playerName = args[1];
        String materialName = args[2];
        String rarityId = args[3];
        int amount = args.length >= 5 ? parseAmount(args[4]) : 1;
        
        if (amount <= 0) {
            sendErrorMessage(sender, "❌ Количество должно быть положительным числом");
            return true;
        }
        
        // Найти игрока
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sendErrorMessage(sender, "❌ Игрок '" + playerName + "' не найден или не онлайн");
            return true;
        }
        
        // Парсить материал
        Material material = parseMaterial(materialName);
        if (material == null) {
            sendErrorMessage(sender, "❌ Неверный материал: " + materialName);
            showMaterialSuggestions(sender);
            return true;
        }
        
        if (!isWeaponOrArmor(material)) {
            sendErrorMessage(sender, "❌ Материал должен быть оружием или броней");
            return true;
        }
        
        // Найти редкость
        Rarity rarity = plugin.getConfigManager().getRarityById(rarityId);
        if (rarity == null) {
            sendErrorMessage(sender, "❌ Редкость '" + rarityId + "' не найдена");
            showAvailableRarities(sender);
            return true;
        }
        
        // Создать и выдать предметы
        int itemsGiven = 0;
        for (int i = 0; i < amount; i++) {
            ItemStack item = new ItemStack(material);
            ItemStack rareItem = ItemUtil.applyRarity(plugin, item, rarity);
            
            Map<Integer, ItemStack> notFitted = target.getInventory().addItem(rareItem);
            if (notFitted.isEmpty()) {
                itemsGiven++;
            } else {
                // Если инвентарь полон, уведомляем об этом
                break;
            }
        }
        
        String materialDisplayName = formatMaterialName(material);
        String amountText = itemsGiven > 1 ? " x" + itemsGiven : "";
        
        if (itemsGiven > 0) {
            target.sendMessage(Component.text("🎁 Вы получили " + rarity.getDisplayName() + " " + materialDisplayName + amountText + "!")
                    .color(NamedTextColor.GREEN));
            
            sendSuccessMessage(sender, "✅ Выдано " + rarity.getDisplayName() + " " + materialDisplayName + amountText + " игроку " + target.getName());
            
            if (itemsGiven < amount) {
                sendWarningMessage(sender, "⚠️ Выдано только " + itemsGiven + " из " + amount + " предметов (инвентарь полон)");
            }
            
            logDebug("Игрок %s выдал %s %s x%d игроку %s", sender.getName(), rarity.id(), material.name(), itemsGiven, target.getName());
        } else {
            sendErrorMessage(sender, "❌ Не удалось выдать предметы - инвентарь игрока полон");
        }
        
        return true;
    }
    
    private boolean handleInspect(CommandSender sender) {
        if (!hasPermission(sender, "rareitems.inspect")) {
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sendErrorMessage(sender, "❌ Эта команда только для игроков");
            return true;
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sendErrorMessage(player, "❌ Возьмите предмет в руку для проверки");
            return true;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sendErrorMessage(player, "❌ Этот предмет не имеет метаданных");
            return true;
        }
        
        if (!meta.getPersistentDataContainer().has(ItemUtil.getKey(plugin, "rarity"), PersistentDataType.STRING)) {
            sendErrorMessage(player, "❌ Этот предмет не имеет редкости");
            return true;
        }
        
        String rarityId = meta.getPersistentDataContainer().get(ItemUtil.getKey(plugin, "rarity"), PersistentDataType.STRING);
        Rarity rarity = plugin.getConfigManager().getRarityById(rarityId);
        
        if (rarity == null) {
            sendErrorMessage(player, "❌ Неизвестная редкость: " + rarityId);
            return true;
        }
        
        showItemInfo(player, item, rarity);
        return true;
    }
    
    private boolean handleUpgrade(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "rareitems.admin.upgrade")) {
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sendErrorMessage(sender, "❌ Эта команда только для игроков");
            return true;
        }
        
        if (args.length < 2) {
            sendUsageMessage(player, "/rareitems upgrade <редкость>");
            return true;
        }
        
        String rarityId = args[1].toLowerCase();
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sendErrorMessage(player, "❌ Возьмите предмет в руку для улучшения");
            return true;
        }
        
        if (!isWeaponOrArmor(item.getType())) {
            sendErrorMessage(player, "❌ Можно улучшать только оружие и броню");
            return true;
        }
        
        Rarity rarity = plugin.getConfigManager().getRarityById(rarityId);
        if (rarity == null) {
            sendErrorMessage(player, "❌ Редкость '" + rarityId + "' не найдена");
            showAvailableRarities(player);
            return true;
        }
        
        ItemStack upgradedItem = ItemUtil.applyRarity(plugin, item, rarity);
        player.getInventory().setItemInMainHand(upgradedItem);
        
        sendSuccessMessage(player, "✨ Предмет улучшен до редкости " + rarity.getDisplayName() + "!");
        
        logDebug("Игрок %s улучшил предмет %s до редкости %s", player.getName(), item.getType().name(), rarity.id());
        
        return true;
    }
    
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "rareitems.admin.debug")) {
            return true;
        }
        
        if (args.length == 1) {
            showDebugInfo(sender);
        } else {
            String action = args[1].toLowerCase();
            handleDebugAction(sender, action);
        }
        
        return true;
    }
    
    private boolean handleValidate(CommandSender sender) {
        if (!hasPermission(sender, "rareitems.admin.validate")) {
            return true;
        }
        
        try {
            sendSuccessMessage(sender, "🔍 Проверка конфигурации RareItems...");
            
            List<String> issues = validateConfig();
            
            if (issues.isEmpty()) {
                sendSuccessMessage(sender, "✅ Конфигурация полностью валидна!");
                sendSuccessMessage(sender, "  📋 Все редкости корректны");
                sendSuccessMessage(sender, "  🎯 Все шансы в правильном диапазоне");
                sendSuccessMessage(sender, "  🎨 Все цвета валидны");
                sendSuccessMessage(sender, "  🔊 Все звуки и частицы существуют");
            } else {
                sendWarningMessage(sender, "⚠️ Найдены проблемы в конфигурации:");
                for (String issue : issues) {
                    sender.sendMessage(Component.text("  • " + issue).color(NamedTextColor.RED));
                }
                sendWarningMessage(sender, "Рекомендуется исправить эти проблемы и перезагрузить конфиг.");
            }
            
        } catch (Exception e) {
            sendErrorMessage(sender, "❌ Ошибка при проверке конфигурации: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    private boolean handleUnknownCommand(CommandSender sender, String subCommand) {
        sendErrorMessage(sender, "❌ Неизвестная подкоманда: " + subCommand);
        sender.sendMessage(Component.text("💡 Используйте /rareitems help для списка команд").color(NamedTextColor.GRAY));
        return true;
    }
    
    // ================================
    // Tab Completion логика
    // ================================
    
    private List<String> getFilteredSubCommands(CommandSender sender, String partial) {
        return subCommandPermissions.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(partial.toLowerCase()))
                .filter(entry -> entry.getValue().isEmpty() || sender.hasPermission(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }
    
    private List<String> getSecondArgumentCompletions(CommandSender sender, String[] args) {
        String subCommand = args[0].toLowerCase();
        String partial = args[1].toLowerCase();
        
        return switch (subCommand) {
            case "give" -> getOnlinePlayerNames(partial);
            case "upgrade", "info" -> getRarityIds(partial);
            case "debug" -> getDebugOptions(partial);
            default -> List.of();
        };
    }
    
    private List<String> getThirdArgumentCompletions(CommandSender sender, String[] args) {
        if (!"give".equals(args[0].toLowerCase())) {
            return List.of();
        }
        
        String partial = args[2].toLowerCase();
        return getWeaponArmorMaterials(partial);
    }
    
    private List<String> getFourthArgumentCompletions(CommandSender sender, String[] args) {
        if (!"give".equals(args[0].toLowerCase())) {
            return List.of();
        }
        
        String partial = args[3].toLowerCase();
        return getRarityIds(partial);
    }
    
    private List<String> getFifthArgumentCompletions(CommandSender sender, String[] args) {
        if (!"give".equals(args[0].toLowerCase())) {
            return List.of();
        }
        
        // Предлагаем числа от 1 до 64
        return Stream.of("1", "8", "16", "32", "64")
                .filter(num -> num.startsWith(args[4]))
                .collect(Collectors.toList());
    }
    
    // ================================
    // Кэширование для Tab Completion
    // ================================
    
    private void updateCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_DURATION) {
            updateTabCompletionCache();
        }
    }
    
    private void updateTabCompletionCache() {
        tabCompletionCache.clear();
        
        // Кэшируем список редкостей
        List<String> rarityIds = plugin.getConfigManager().getAllRarities().stream()
                .map(Rarity::id)
                .sorted()
                .collect(Collectors.toList());
        tabCompletionCache.put("rarities", rarityIds);
        
        // Кэшируем материалы оружия и брони
        List<String> weaponArmorMaterials = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(this::isWeaponOrArmor)
                .map(material -> material.name().toLowerCase())
                .sorted()
                .collect(Collectors.toList());
        tabCompletionCache.put("weapons_armor", weaponArmorMaterials);
        
        // Кэшируем опции отладки
        tabCompletionCache.put("debug_options", List.of("true", "false", "on", "off", "enable", "disable"));
        
        lastCacheUpdate = System.currentTimeMillis();
    }
    
    private List<String> getOnlinePlayerNames(String partial) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .sorted()
                .collect(Collectors.toList());
    }
    
    private List<String> getRarityIds(String partial) {
        return tabCompletionCache.getOrDefault("rarities", List.of()).stream()
                .filter(id -> id.startsWith(partial))
                .collect(Collectors.toList());
    }
    
    private List<String> getWeaponArmorMaterials(String partial) {
        return tabCompletionCache.getOrDefault("weapons_armor", List.of()).stream()
                .filter(material -> material.startsWith(partial))
                .collect(Collectors.toList());
    }
    
    private List<String> getDebugOptions(String partial) {
        return tabCompletionCache.getOrDefault("debug_options", List.of()).stream()
                .filter(option -> option.startsWith(partial))
                .collect(Collectors.toList());
    }
    
    // ================================
    // Информационные методы
    // ================================
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text()
            .append(Component.text("════════════════════════════════════").color(NamedTextColor.DARK_PURPLE))
            .append(Component.newline())
            .append(Component.text("  🎁 ").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("RareItems").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text(" v3.0").color(NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("════════════════════════════════════").color(NamedTextColor.DARK_PURPLE))
            .build()
        );
        
        showCommandIfHasPermission(sender, "rareitems.admin.reload", 
            "/rareitems reload", "Перезагрузить конфигурацию");
        showCommandIfHasPermission(sender, "rareitems.admin.info", 
            "/rareitems info [редкость]", "Показать информацию о редкостях");
        showCommandIfHasPermission(sender, "rareitems.admin.give", 
            "/rareitems give <игрок> <материал> <редкость> [количество]", "Выдать редкий предмет");
        showCommandIfHasPermission(sender, "rareitems.inspect", 
            "/rareitems inspect", "Проверить предмет в руке");
        showCommandIfHasPermission(sender, "rareitems.admin.upgrade", 
            "/rareitems upgrade <редкость>", "Улучшить предмет до редкости");
        showCommandIfHasPermission(sender, "rareitems.admin.debug", 
            "/rareitems debug [опция]", "Управление режимом отладки");
        showCommandIfHasPermission(sender, "rareitems.admin.validate", 
            "/rareitems validate", "Проверить валидность конфигурации");
        
        sender.sendMessage(Component.text("  🔹 /rareitems help").color(NamedTextColor.WHITE)
                .append(Component.text(" - Показать эту справку").color(NamedTextColor.GRAY)));
        
        sender.sendMessage(Component.text("════════════════════════════════════").color(NamedTextColor.DARK_PURPLE));
    }
    
    private void showGeneralInfo(CommandSender sender) {
        var rarities = plugin.getConfigManager().getAllRarities();
        
        sender.sendMessage(Component.text()
                .append(Component.text("🎭 Доступные редкости: ").color(NamedTextColor.GOLD))
                .append(Component.text("(" + rarities.size() + ")").color(NamedTextColor.GRAY))
                .build());
        
        for (Rarity rarity : rarities) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  • ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(rarity.getDisplayName()).color(rarity.color()))
                    .append(Component.text(" (" + rarity.id() + ")").color(NamedTextColor.GRAY))
                    .append(Component.text(" - Шанс: ").color(NamedTextColor.GRAY))
                    .append(Component.text(formatChance(plugin.getConfigManager().getCraftChance(rarity.id()))).color(NamedTextColor.GREEN))
                    .build());
        }
        
        sender.sendMessage(Component.text("💡 Используйте /rareitems info <редкость> для подробной информации")
                .color(NamedTextColor.YELLOW));
    }
    
    private void showRarityInfo(CommandSender sender, String rarityId) {
        Rarity rarity = plugin.getConfigManager().getRarityById(rarityId);
        if (rarity == null) {
            sendErrorMessage(sender, "❌ Редкость '" + rarityId + "' не найдена");
            showAvailableRarities(sender);
            return;
        }
        
        showRarityAttributes(sender, rarity);
    }
    
    private void showItemInfo(Player player, ItemStack item, Rarity rarity) {
        player.sendMessage(Component.text("🔍 Информация о предмете:").color(NamedTextColor.GOLD));
        
        String materialName = formatMaterialName(item.getType());
        player.sendMessage(Component.text()
                .append(Component.text("  📦 Материал: ").color(NamedTextColor.GRAY))
                .append(Component.text(materialName).color(NamedTextColor.WHITE))
                .build());
        
        player.sendMessage(Component.text()
                .append(Component.text("  ✨ Редкость: ").color(NamedTextColor.GRAY))
                .append(Component.text(rarity.getDisplayName()).color(rarity.color()))
                .build());
        
        // Показать атрибуты
        showAttributeIfPresent(player, "⚔️", "Урон", rarity.damageBonus(), NamedTextColor.RED);
        showAttributeIfPresent(player, "🛡️", "Броня", rarity.armorBonus(), NamedTextColor.BLUE);
        showAttributeIfPresent(player, "⚡", "Скорость атаки", rarity.attackSpeedBonus(), NamedTextColor.YELLOW);
        showAttributeIfPresent(player, "🔄", "Сопротивление отбрасыванию", rarity.knockbackResistance(), NamedTextColor.DARK_PURPLE, "%");
        showAttributeIfPresent(player, "💎", "Множитель прочности", rarity.durabilityMultiplier(), NamedTextColor.AQUA, "x");
    }
    
    private void showRarityAttributes(CommandSender sender, Rarity rarity) {
        sender.sendMessage(Component.text()
                .append(Component.text("📊 Информация о редкости: ").color(NamedTextColor.GOLD))
                .append(Component.text(rarity.getDisplayName()).color(rarity.color()))
                .build());
        
        sender.sendMessage(Component.text()
                .append(Component.text("  🎲 Шанс выпадения: ").color(NamedTextColor.GRAY))
                .append(Component.text(formatChance(rarity.chance())).color(NamedTextColor.GREEN))
                .build());
        
        // Показать бонусы
        if (rarity.damageBonus() > 0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  ⚔️ Бонус урона: +").color(NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(rarity.damageBonus())).color(NamedTextColor.RED))
                    .build());
        }
        
        if (rarity.armorBonus() > 0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  🛡️ Бонус брони: +").color(NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(rarity.armorBonus())).color(NamedTextColor.BLUE))
                    .build());
        }
        
        if (rarity.attackSpeedBonus() > 0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  ⚡ Бонус скорости атаки: +").color(NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(rarity.attackSpeedBonus())).color(NamedTextColor.YELLOW))
                    .build());
        }
        
        if (rarity.knockbackResistance() > 0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  🔄 Сопротивление отбрасыванию: +").color(NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(rarity.knockbackResistance() * 100) + "%").color(NamedTextColor.DARK_PURPLE))
                    .build());
        }
        
        if (rarity.durabilityMultiplier() != 1.0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  💎 Множитель прочности: ").color(NamedTextColor.GRAY))
                    .append(Component.text("x" + formatNumber(rarity.durabilityMultiplier())).color(NamedTextColor.AQUA))
                    .build());
        }
        
        // Показать специальные способности
        if (!rarity.specialAbilities().isEmpty()) {
            sender.sendMessage(Component.text("  🌟 Специальные способности:").color(NamedTextColor.GOLD));
            for (String abilityKey : rarity.specialAbilities().keySet()) {
                Object abilityValue = rarity.specialAbilities().get(abilityKey);
                sender.sendMessage(Component.text()
                        .append(Component.text("    • ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(abilityKey + ": " + abilityValue).color(NamedTextColor.LIGHT_PURPLE))
                        .build());
            }
        }
        
        // Показать возможности крафта
        if (rarity.canCraft()) {
            sender.sendMessage(Component.text("  🔨 Доступен в крафте").color(NamedTextColor.GREEN));
        }
        
        if (rarity.canFindInDungeons()) {
            sender.sendMessage(Component.text("  🏰 Можно найти в подземельях").color(NamedTextColor.DARK_PURPLE));
        }
    }
    
    private void showAvailableRarities(CommandSender sender) {
        sender.sendMessage(Component.text("📋 Доступные редкости:").color(NamedTextColor.YELLOW));
        for (Rarity rarity : plugin.getConfigManager().getAllRarities()) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  • ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(rarity.id()).color(NamedTextColor.GRAY))
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(rarity.getDisplayName()).color(rarity.color()))
                    .build());
        }
    }
    
    private void showMaterialSuggestions(CommandSender sender) {
        sender.sendMessage(Component.text("💡 Примеры материалов: diamond_sword, iron_helmet, golden_chestplate, netherite_pickaxe")
                .color(NamedTextColor.GRAY));
    }
    
    private void showDebugInfo(CommandSender sender) {
        boolean debugMode = plugin.getConfigManager().isDebugMode();
        
        sender.sendMessage(Component.text("🔧 Режим отладки: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(debugMode ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН")
                        .color(debugMode ? NamedTextColor.GREEN : NamedTextColor.RED)));
        
        if (debugMode) {
            sender.sendMessage(Component.text("💡 Используйте /rareitems debug false для выключения")
                    .color(NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("💡 Используйте /rareitems debug true для включения")
                    .color(NamedTextColor.GRAY));
        }
    }
    
    private void handleDebugAction(CommandSender sender, String action) {
        boolean newValue = switch (action) {
            case "true", "on", "enable", "1" -> true;
            case "false", "off", "disable", "0" -> false;
            default -> {
                sendErrorMessage(sender, "❌ Неверная опция. Используйте: true/false, on/off, enable/disable");
                yield plugin.getConfigManager().isDebugMode(); // Возвращаем текущее значение
            }
        };
        
        if (newValue == plugin.getConfigManager().isDebugMode() && 
            !List.of("true", "false", "on", "off", "enable", "disable", "1", "0").contains(action)) {
            return; // Если была ошибка парсинга, не меняем состояние
        }
        
        plugin.getConfigManager().setDebugMode(newValue);
        
        sendSuccessMessage(sender, "🔧 Режим отладки " + (newValue ? "включен" : "выключен"));
        
        if (newValue) {
            logDebug("Режим отладки включен игроком: %s", sender.getName());
        }
    }
    
    // ================================
    // Вспомогательные методы
    // ================================
    
    private boolean hasPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sendErrorMessage(sender, "❌ У вас нет прав для этой команды");
            return false;
        }
        return true;
    }
    
    private void showCommandIfHasPermission(CommandSender sender, String permission, String command, String description) {
        if (permission.isEmpty() || sender.hasPermission(permission)) {
            sender.sendMessage(Component.text()
                    .append(Component.text("  🔹 ").color(NamedTextColor.AQUA))
                    .append(Component.text(command).color(NamedTextColor.WHITE))
                    .append(Component.text(" - ").color(NamedTextColor.GRAY))
                    .append(Component.text(description).color(NamedTextColor.GRAY))
                    .build());
        }
    }
    
    private void showAttributeIfPresent(Player player, String icon, String name, double value, NamedTextColor color) {
        showAttributeIfPresent(player, icon, name, value, color, "");
    }
    
    private void showAttributeIfPresent(Player player, String icon, String name, double value, NamedTextColor color, String suffix) {
        if (value > 0) {
            player.sendMessage(Component.text()
                    .append(Component.text("  " + icon + " " + name + ": +").color(NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(value) + suffix).color(color))
                    .build());
        }
    }
    
    private void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
    }
    
    private void sendSuccessMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(NamedTextColor.GREEN));
    }
    
    private void sendWarningMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(NamedTextColor.YELLOW));
    }
    
    private void sendUsageMessage(CommandSender sender, String usage) {
        sender.sendMessage(Component.text("📝 Использование: " + usage).color(NamedTextColor.YELLOW));
    }
    
    private void logDebug(String format, Object... args) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info(String.format("[DEBUG] " + format, args));
        }
    }
    
    // ================================
    // Парсинг и форматирование
    // ================================
    
    private int parseAmount(String amountStr) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(amountStr)));
        } catch (NumberFormatException e) {
            return -1; // Индикатор ошибки
        }
    }
    
    private Material parseMaterial(String materialName) {
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private String formatMaterialName(Material material) {
        return capitalizeWords(material.name().toLowerCase().replace("_", " "));
    }
    
    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        
        return result.toString();
    }
    
    private String formatNumber(double number) {
        if (number == (long) number) {
            return String.format("%d", (long) number);
        } else {
            return String.format("%.1f", number);
        }
    }
    
    private String formatChance(double chance) {
        return formatNumber(chance * 100) + "%";
    }
    
    private boolean isWeaponOrArmor(Material material) {
        return materialTypeChecker.isWeaponOrArmor(material);
    }
    
    /**
     * Проверяет валидность конфигурации
     * @return список обнаруженных проблем
     */
    private List<String> validateConfig() {
        List<String> issues = new ArrayList<>();
        
        // Проверяем редкости
        List<Rarity> rarities = plugin.getConfigManager().getAllRarities();
        if (rarities.isEmpty()) {
            issues.add("Нет определенных редкостей в конфигурации");
            return issues;
        }
        
        // Проверяем шансы крафта
        double totalChance = 0;
        for (Rarity rarity : rarities) {
            double chance = plugin.getConfigManager().getCraftChance(rarity.id());
            
            if (chance < 0) {
                issues.add("Отрицательный шанс для редкости '" + rarity.id() + "': " + chance);
            }
            if (chance > 100) {
                issues.add("Шанс больше 100% для редкости '" + rarity.id() + "': " + chance);
            }
            
            totalChance += chance;
        }
        
        if (totalChance > 100) {
            issues.add("Общий шанс всех редкостей превышает 100%: " + formatChance(totalChance / 100));
        }
        
        // Проверяем атрибуты
        for (Rarity rarity : rarities) {
            if (rarity.damageBonus() < 0) {
                issues.add("Отрицательный бонус урона для '" + rarity.id() + "': " + rarity.damageBonus());
            }
            if (rarity.armorBonus() < 0) {
                issues.add("Отрицательный бонус брони для '" + rarity.id() + "': " + rarity.armorBonus());
            }
            if (rarity.healthBonus() < 0) {
                issues.add("Отрицательный бонус здоровья для '" + rarity.id() + "': " + rarity.healthBonus());
            }
            
            // Проверяем разумные пределы
            if (rarity.damageBonus() > 10) {
                issues.add("Очень высокий бонус урона для '" + rarity.id() + "': " + rarity.damageBonus() + " (может нарушить баланс)");
            }
            if (rarity.armorBonus() > 10) {
                issues.add("Очень высокий бонус брони для '" + rarity.id() + "': " + rarity.armorBonus() + " (может нарушить баланс)");
            }
            if (rarity.healthBonus() > 20) {
                issues.add("Очень высокий бонус здоровья для '" + rarity.id() + "': " + rarity.healthBonus() + " (может нарушить баланс)");
            }
        }
        
        // Проверяем звуки (базовая проверка)
        try {
            String craftSound = plugin.getConfigManager().getCraftSound();
            if (craftSound != null && !craftSound.equals("NONE") && !craftSound.isEmpty()) {
                org.bukkit.Sound.valueOf(craftSound);
            }
        } catch (IllegalArgumentException e) {
            issues.add("Неверный звук создания: " + plugin.getConfigManager().getCraftSound());
        }
        
        // Проверяем существование настроек включения мотыг
        boolean includeHoes = plugin.getConfig().getBoolean("settings.includeHoes", false);
        if (includeHoes) {
            issues.add("Информация: Включена поддержка мотыг как оружия (может быть неожиданно для игроков)");
        }
        
        return issues;
    }
} 