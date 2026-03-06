package dev.astralclash.items;

import dev.astralclash.AstralClash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides special game items that players can use for quick access to game functions.
 */
public class GameItems {

    public static final int SLOT_MENU = 4; // Hotbar slot 5 (0-indexed)
    public static final int SLOT_EXIT_ARENA_VIEW = 8; // Hotbar slot 9 for exit arena view

    /**
     * Gives the player their game items (menu opener, etc.)
     */
    public static void giveGameItems(Player player) {
        // Clear hotbar slot first
        player.getInventory().setItem(SLOT_MENU, null);

        // Main Menu Opener
        ItemStack menuItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = menuItem.getItemMeta();
        meta.displayName(Component.text("§d§l✦ AstralClash Menu ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Right-click to open", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7the main game menu", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        menuItem.setItemMeta(meta);

        player.getInventory().setItem(SLOT_MENU, menuItem);
    }

    /**
     * Checks if an item is the menu opener.
     */
    public static boolean isMenuOpener(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.displayName().toString().contains("AstralClash Menu");
    }

    /** Creates the "Exit Arena View" item (e.g. given when entering arena spectator). */
    public static ItemStack createExitArenaViewItem() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c§lExit Arena View", NamedTextColor.RED, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Right-click to return to your board", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Checks if an item is the Exit Arena View item. */
    public static boolean isExitArenaViewItem(ItemStack item) {
        if (item == null || item.getType() != Material.REDSTONE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.displayName().toString().contains("Exit Arena View");
    }
}
