package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Beautiful main menu GUI with all game functions.
 */
public class MainMenuUI {

    static final String MENU_TITLE = "✦ AstralClash Menu ✦";
    public static final Component MENU_TITLE_COMPONENT =
            Component.text(MENU_TITLE, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);

    private static final int SLOT_SHOP = 10;
    private static final int SLOT_BENCH = 12;
    private static final int SLOT_BOARD = 14;
    private static final int SLOT_STATS = 16;
    private static final int SLOT_ARENA_VIEW = 28;
    private static final int SLOT_LEAVE = 31;
    private static final int SLOT_CLOSE = 35;

    private final AstralClash plugin;

    public MainMenuUI(AstralClash plugin) {
        this.plugin = plugin;
    }

    public void openMenu(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return;
        Inventory inv = Bukkit.createInventory(null, 45, MENU_TITLE_COMPONENT);
        populateMenu(inv, ap);
        ap.getPlayer().openInventory(inv);
    }

    public void populateMenu(Inventory inv, ArenaPlayer ap) {
        // Fill background
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createGlassPane());
            }
        }

        // ── Row 2: Main Actions ──
        inv.setItem(SLOT_SHOP, createActionButton(
            Material.EMERALD,
            "§a§l⬛ Open Shop ⬛",
            List.of("§7Buy champions", "§7Reroll offerings", "§7Buy XP"),
            NamedTextColor.GREEN));

        inv.setItem(SLOT_BENCH, createActionButton(
            Material.CHEST,
            "§e§l⬛ Open Bench ⬛",
            List.of("§7View your champions", "§7Deploy to board", "§7Sell units"),
            NamedTextColor.YELLOW));

        inv.setItem(SLOT_BOARD, createActionButton(
            Material.ARMOR_STAND,
            "§b§l⬛ View Board ⬛",
            List.of("§7See deployed units", "§7" + ap.getDeployedCount() + "/" + ap.getBoardSizeLimit() + " deployed"),
            NamedTextColor.AQUA));

        inv.setItem(SLOT_STATS, createActionButton(
            Material.BOOK,
            "§d§l⬛ Your Stats ⬛",
            List.of("§7View statistics", "§7Win rate", "§7Best placement"),
            NamedTextColor.LIGHT_PURPLE));

        // ── Row 4: Special Actions ──
        inv.setItem(SLOT_ARENA_VIEW, createActionButton(
            Material.ENDER_EYE,
            "§6§l⬛ Arena View ⬛",
            List.of("§7Spectate from above", "§7View all boards", "§7Press to toggle"),
            NamedTextColor.GOLD));

        inv.setItem(SLOT_LEAVE, createActionButton(
            Material.BARRIER,
            "§c§l⬛ Leave Game ⬛",
            List.of("§7Exit to lobby", "§7Your progress will be saved"),
            NamedTextColor.RED));

        inv.setItem(SLOT_CLOSE, createActionButton(
            Material.OAK_DOOR,
            "§7§lClose Menu",
            List.of(),
            NamedTextColor.GRAY));
    }

    private ItemStack createActionButton(Material mat, String name, List<String> lore, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane() {
        ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    // ── Static accessors for click handler ──

    public static int getShopSlot() { return SLOT_SHOP; }
    public static int getBenchSlot() { return SLOT_BENCH; }
    public static int getBoardSlot() { return SLOT_BOARD; }
    public static int getStatsSlot() { return SLOT_STATS; }
    public static int getArenaViewSlot() { return SLOT_ARENA_VIEW; }
    public static int getLeaveSlot() { return SLOT_LEAVE; }
    public static int getCloseSlot() { return SLOT_CLOSE; }
    public static String getMenuTitle() { return MENU_TITLE; }
}
