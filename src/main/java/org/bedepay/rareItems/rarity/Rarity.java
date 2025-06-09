package org.bedepay.rareItems.rarity;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Java Record для представления редкости предмета.
 * Автоматически генерирует конструктор, геттеры, equals(), hashCode(), toString()
 * и делает класс immutable (неизменяемым) по дизайну.
 */
public record Rarity(
        String id,
        String name,
        TextColor color,
        double damageBonus,
        double armorBonus,
        double speedBonus,
        double toughnessBonus,
        double attackSpeedBonus,
        double healthBonus,
        double luckBonus,
        List<String> enchantments,
        Map<PotionEffectType, Integer> onHitEffects,
        int effectCooldown,
        double effectChance,
        String particle,
        String sound,
        Map<String, Object> specialAbilities
) {
    
    /**
     * Компактный конструктор для валидации данных.
     * Код здесь выполняется перед инициализацией полей.
     */
    public Rarity {
        // Валидация обязательных полей
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID редкости не может быть null или пустым");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя редкости не может быть null или пустым");
        }
        if (color == null) {
            throw new IllegalArgumentException("Цвет редкости не может быть null");
        }
        
        // Валидация числовых значений
        if (effectCooldown < 0) {
            throw new IllegalArgumentException("Время восстановления эффекта не может быть отрицательным");
        }
        if (effectChance < 0 || effectChance > 100) {
            throw new IllegalArgumentException("Шанс эффекта должен быть от 0 до 100");
        }
        
        // Обеспечиваем immutability для коллекций
        enchantments = enchantments != null ? List.copyOf(enchantments) : List.of();
        onHitEffects = onHitEffects != null ? Map.copyOf(onHitEffects) : Map.of();
        specialAbilities = specialAbilities != null ? Map.copyOf(specialAbilities) : Map.of();
    }
    
    /**
     * Альтернативный конструктор с минимальными параметрами.
     * Остальные значения устанавливаются по умолчанию.
     */
    public Rarity(String id, String name, TextColor color) {
        this(id, name, color, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 
             List.of(), Map.of(), 5000, 100.0, "NONE", "NONE", Map.of());
    }
    
    /**
     * Метод для получения отображаемого имени (синоним для name()).
     * Оставлен для обратной совместимости с существующим кодом.
     */
    public String getDisplayName() {
        return name();
    }
    
    /**
     * Дополнительные методы для совместимости с новым Command API
     */
    public double chance() {
        // Этот метод будет использоваться для получения шанса из ConfigManager
        // Возвращаем 0 как значение по умолчанию, реальное значение получается из ConfigManager
        return 0.0;
    }
    
    public double knockbackResistance() {
        // Сопротивление отбрасыванию - новый атрибут
        return (Double) specialAbilities.getOrDefault("knockbackResistance", 0.0);
    }
    
    public double durabilityMultiplier() {
        // Множитель прочности - новый атрибут
        return (Double) specialAbilities.getOrDefault("durabilityMultiplier", 1.0);
    }
    
    public boolean canCraft() {
        // Возможность крафта
        return (Boolean) specialAbilities.getOrDefault("canCraft", true);
    }
    
    public boolean canFindInDungeons() {
        // Возможность найти в подземельях
        return (Boolean) specialAbilities.getOrDefault("canFindInDungeons", false);
    }
} 