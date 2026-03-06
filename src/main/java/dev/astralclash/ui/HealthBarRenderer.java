package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Renders floating health bars above champion models during combat.
 * Uses TextDisplay entities for clean, customizable HP bars.
 */
public class HealthBarRenderer {

    // Health bar appearance
    private static final int BAR_WIDTH = 20;  // Characters wide
    private static final char BAR_FILLED = '█';
    private static final char BAR_EMPTY = '░';
    private static final char BAR_SHIELD = '▓';
    
    // Colors for health percentage
    private static final TextColor HP_HIGH = TextColor.color(0x55FF55);    // Green (>60%)
    private static final TextColor HP_MED = TextColor.color(0xFFFF55);     // Yellow (30-60%)
    private static final TextColor HP_LOW = TextColor.color(0xFF5555);     // Red (<30%)
    private static final TextColor SHIELD_COLOR = TextColor.color(0x55FFFF); // Cyan for shields
    private static final TextColor MANA_COLOR = TextColor.color(0x5555FF);  // Blue for mana
    
    private final AstralClash plugin;
    private final Map<UUID, TextDisplay> healthBars = new HashMap<>();
    private final Map<UUID, TextDisplay> nameTags = new HashMap<>();
    private BukkitTask updateTask;
    
    public HealthBarRenderer(AstralClash plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Creates a health bar above a champion's model entity.
     */
    public void createHealthBar(ChampionInstance ci) {
        if (ci.getModelEntityId() == null) return;
        
        Entity modelEntity = plugin.getServer().getEntity(ci.getModelEntityId());
        if (modelEntity == null) return;
        
        Location loc = modelEntity.getLocation().clone().add(0, 2.5, 0);
        
        // Create name tag
        TextDisplay nameTag = (TextDisplay) loc.getWorld().spawnEntity(
            loc.clone().add(0, 0.3, 0), EntityType.TEXT_DISPLAY);
        configureTextDisplay(nameTag, buildNameTag(ci));
        nameTags.put(ci.getModelEntityId(), nameTag);
        
        // Create health bar
        TextDisplay healthBar = (TextDisplay) loc.getWorld().spawnEntity(
            loc, EntityType.TEXT_DISPLAY);
        configureTextDisplay(healthBar, buildHealthBar(ci));
        healthBars.put(ci.getModelEntityId(), healthBar);
    }
    
    /**
     * Updates all health bars to reflect current HP.
     */
    public void updateHealthBar(ChampionInstance ci) {
        if (ci.getModelEntityId() == null) return;
        
        TextDisplay healthBar = healthBars.get(ci.getModelEntityId());
        if (healthBar != null && !healthBar.isDead()) {
            healthBar.text(buildHealthBar(ci));
            
            // Update position to follow the model
            Entity modelEntity = plugin.getServer().getEntity(ci.getModelEntityId());
            if (modelEntity != null) {
                healthBar.teleport(modelEntity.getLocation().clone().add(0, 2.5, 0));
            }
        }
        
        TextDisplay nameTag = nameTags.get(ci.getModelEntityId());
        if (nameTag != null && !nameTag.isDead()) {
            Entity modelEntity = plugin.getServer().getEntity(ci.getModelEntityId());
            if (modelEntity != null) {
                nameTag.teleport(modelEntity.getLocation().clone().add(0, 2.8, 0));
            }
        }
    }
    
    /**
     * Removes a health bar.
     */
    public void removeHealthBar(ChampionInstance ci) {
        if (ci.getModelEntityId() == null) return;
        
        TextDisplay healthBar = healthBars.remove(ci.getModelEntityId());
        if (healthBar != null && !healthBar.isDead()) {
            healthBar.remove();
        }
        
        TextDisplay nameTag = nameTags.remove(ci.getModelEntityId());
        if (nameTag != null && !nameTag.isDead()) {
            nameTag.remove();
        }
    }
    
    /**
     * Removes all health bars.
     */
    public void removeAll() {
        for (TextDisplay td : healthBars.values()) {
            if (td != null && !td.isDead()) td.remove();
        }
        for (TextDisplay td : nameTags.values()) {
            if (td != null && !td.isDead()) td.remove();
        }
        healthBars.clear();
        nameTags.clear();
    }
    
    /**
     * Starts a task to continuously update health bars during combat.
     */
    public void startUpdateLoop(List<ChampionInstance> champions) {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (ChampionInstance ci : champions) {
                if (ci.isAlive() && ci.getModelEntityId() != null) {
                    updateHealthBar(ci);
                } else {
                    removeHealthBar(ci);
                }
            }
        }, 0L, 5L); // Update every 5 ticks (0.25 seconds)
    }
    
    /**
     * Stops the update loop.
     */
    public void stopUpdateLoop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }
    
    /**
     * Shows a floating damage number.
     */
    public void showDamageNumber(Location loc, double damage, boolean isCrit, boolean isHeal) {
        if (loc == null || loc.getWorld() == null) return;
        
        Location displayLoc = loc.clone().add(
            (Math.random() - 0.5) * 0.5,
            2.0 + Math.random() * 0.5,
            (Math.random() - 0.5) * 0.5
        );
        
        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(displayLoc, EntityType.TEXT_DISPLAY);
        
        Component text;
        if (isHeal) {
            text = Component.text("+" + (int) damage, NamedTextColor.GREEN, TextDecoration.BOLD);
        } else if (isCrit) {
            text = Component.text("✦ " + (int) damage + " ✦", NamedTextColor.YELLOW, TextDecoration.BOLD);
        } else {
            text = Component.text((int) damage, NamedTextColor.WHITE);
        }
        
        configureTextDisplay(td, text);
        td.setViewRange(32f);
        
        // Animate: float upward and fade
        final int[] ticks = {0};
        final Location startLoc = displayLoc.clone();
        
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticks[0]++;
            if (ticks[0] > 20 || td.isDead()) {
                td.remove();
                task.cancel();
                return;
            }
            td.teleport(startLoc.clone().add(0, ticks[0] * 0.05, 0));
        }, 1L, 1L);
    }
    
    /**
     * Shows a status effect indicator.
     */
    public void showStatusIndicator(ChampionInstance ci, String status, TextColor color) {
        if (ci.getModelEntityId() == null) return;
        
        Entity modelEntity = plugin.getServer().getEntity(ci.getModelEntityId());
        if (modelEntity == null) return;
        
        Location loc = modelEntity.getLocation().clone().add(0, 3.2, 0);
        
        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.text(Component.text(status, color, TextDecoration.BOLD));
        configureTextDisplay(td, Component.text(status, color, TextDecoration.BOLD));
        
        // Remove after 1 second
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!td.isDead()) td.remove();
        }, 20L);
    }
    
    // ── Private helper methods ──
    
    private void configureTextDisplay(TextDisplay td, Component text) {
        td.text(text);
        td.setBillboard(Display.Billboard.CENTER);
        td.setDefaultBackground(false);
        td.setShadowed(true);
        td.setViewRange(48f);
        td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
    }
    
    private Component buildNameTag(ChampionInstance ci) {
        String name = ci.getChampion().getDisplayName();
        int stars = ci.getStarLevel().getStars();
        String starStr = "★".repeat(stars);
        
        TextColor starColor = switch (stars) {
            case 1 -> TextColor.color(0xAAAAAA);
            case 2 -> TextColor.color(0xFFAA00);
            case 3 -> TextColor.color(0xFFFF55);
            default -> NamedTextColor.WHITE;
        };
        
        return Component.text(name + " ", NamedTextColor.WHITE)
            .append(Component.text(starStr, starColor));
    }
    
    private Component buildHealthBar(ChampionInstance ci) {
        double hpPercent = ci.getHpPercent();
        double shieldPercent = ci.getShield() / ci.getMaxHp();
        double manaPercent = ci.getChampion().getMaxMana() > 0 
            ? ci.getCurrentMana() / ci.getChampion().getMaxMana() 
            : 0;
        
        // Determine HP color
        TextColor hpColor;
        if (hpPercent > 0.6) {
            hpColor = HP_HIGH;
        } else if (hpPercent > 0.3) {
            hpColor = HP_MED;
        } else {
            hpColor = HP_LOW;
        }
        
        // Build HP bar
        int hpFilled = (int) Math.ceil(hpPercent * BAR_WIDTH);
        int shieldFilled = (int) Math.ceil(Math.min(shieldPercent, 1.0 - hpPercent) * BAR_WIDTH);
        int empty = BAR_WIDTH - hpFilled - shieldFilled;
        
        StringBuilder hpBar = new StringBuilder();
        hpBar.append(String.valueOf(BAR_FILLED).repeat(Math.max(0, hpFilled)));
        
        Component result = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(hpBar.toString(), hpColor));
        
        if (shieldFilled > 0) {
            result = result.append(Component.text(
                String.valueOf(BAR_SHIELD).repeat(shieldFilled), SHIELD_COLOR));
        }
        
        if (empty > 0) {
            result = result.append(Component.text(
                String.valueOf(BAR_EMPTY).repeat(empty), NamedTextColor.DARK_GRAY));
        }
        
        result = result.append(Component.text("]", NamedTextColor.DARK_GRAY));
        
        // Add HP text
        result = result.append(Component.text(" " + (int) ci.getCurrentHp() + "/" + (int) ci.getMaxHp(), 
            NamedTextColor.WHITE));
        
        // Add mana bar and numeric mana if applicable
        if (ci.getChampion().getMaxMana() > 0) {
            int manaFilled = (int) Math.ceil(manaPercent * 10);
            int manaEmpty = 10 - manaFilled;
            int maxMana = ci.getChampion().getMaxMana();
            
            result = result.append(Component.text("\n", NamedTextColor.WHITE));
            result = result.append(Component.text("[", NamedTextColor.DARK_GRAY));
            result = result.append(Component.text(
                String.valueOf(BAR_FILLED).repeat(manaFilled), MANA_COLOR));
            result = result.append(Component.text(
                String.valueOf(BAR_EMPTY).repeat(manaEmpty), NamedTextColor.DARK_GRAY));
            result = result.append(Component.text("]", NamedTextColor.DARK_GRAY));
            result = result.append(Component.text(" " + (int) ci.getCurrentMana() + "/" + maxMana, NamedTextColor.WHITE));
        }
        
        return result;
    }
}
