package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.Champion;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.shop.Shop;
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
 * Builds and displays the champion shop as a Bukkit chest GUI.
 *
 * <p>Layout (54-slot inventory = 6 rows × 9 cols):
 * <pre>
 * Row 0: [SHOP LABEL] [SLOT 0] [SLOT 1] [SLOT 2] [SLOT 3] [SLOT 4] [       ] [REROLL] [LOCK]
 * Row 1-5: Bench (45 slots, not shown in this simple MVP — bench is a separate GUI or in-world)
 * </pre>
 * For the MVP we use a single-row 9-slot inventory showing: 5 champions + lock + reroll + buy XP + close.
 */
public class ShopUI {

    static final String    SHOP_TITLE           = "✦ AstralClash Shop ✦";
    static final Component SHOP_TITLE_COMPONENT  =
            Component.text(SHOP_TITLE, NamedTextColor.LIGHT_PURPLE);

    private static final int SLOT_CHAMPION_START = 0; // slots 0-4 = champions
    private static final int SLOT_REROLL        = 5;
    private static final int SLOT_LOCK          = 6;
    private static final int SLOT_BUY_XP        = 7;
    private static final int SLOT_CLOSE         = 8;

    private final AstralClash plugin;

    public ShopUI(AstralClash plugin) { this.plugin = plugin; }

    // ── Open ─────────────────────────────────────────────────────────────────

    public void openShop(ArenaPlayer ap) {
        Inventory inv = Bukkit.createInventory(null, 9, SHOP_TITLE_COMPONENT);
        populateShop(inv, ap);
        ap.getPlayer().openInventory(inv);
    }

    public void populateShop(Inventory inv, ArenaPlayer ap) {
        Shop shop = ap.getShop();
        if (shop == null) return;

        // Champion slots
        for (int i = 0; i < shop.size() && i < 5; i++) {
            Champion c = shop.getSlot(i);
            inv.setItem(SLOT_CHAMPION_START + i, c != null
                    ? buildChampionItem(c, shop.isLocked(i))
                    : buildEmptySlot());
        }

        // Reroll button
        inv.setItem(SLOT_REROLL, buildActionItem(Material.EMERALD,
                "§a§lReroll Shop",
                List.of("§7Cost: §6" + plugin.getConfigManager().getRerollCost() + " gold",
                        "§7Your gold: §6" + ap.getGold())));

        // Lock button
        inv.setItem(SLOT_LOCK, buildActionItem(
                ap.isShopLocked() ? Material.LIME_DYE : Material.GRAY_DYE,
                ap.isShopLocked() ? "§a§lUnlock Shop" : "§e§lLock Shop",
                List.of("§7Lock to keep offerings after round")));

        // Buy XP
        inv.setItem(SLOT_BUY_XP, buildActionItem(Material.EXPERIENCE_BOTTLE,
                "§b§lBuy XP",
                List.of("§7Cost: §6" + plugin.getConfigManager().getXpBuyCost() + " gold",
                        "§7+" + plugin.getConfigManager().getXpBuyAmount() + " XP",
                        "§7Level: §e" + ap.getLevel() + " §7XP: §e" + ap.getXp())));

        // Close
        inv.setItem(SLOT_CLOSE, buildActionItem(Material.BARRIER, "§c§lClose", List.of()));
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildChampionItem(Champion c, boolean locked) {
        Material mat = tierMaterial(c.getTier().getCost());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(c.getDisplayName(),
                c.getTraits().isEmpty() ? NamedTextColor.WHITE
                        : c.getTraits().get(0).getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Cost: " + c.getTier().getCost() + " gold", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Traits:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        for (var t : c.getTraits()) {
            lore.add(Component.text("  • " + t.getDisplayName(), t.getColor())
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (c.getAbility() != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Ability: " + c.getAbility().getName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(wrap(c.getAbility().getDescription(), 40), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (locked) {
            lore.add(Component.empty());
            lore.add(Component.text("[LOCKED]", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to purchase!", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildEmptySlot() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Empty", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildActionItem(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material tierMaterial(int tier) {
        return switch (tier) {
            case 1  -> Material.IRON_INGOT;
            case 2  -> Material.GOLD_INGOT;
            case 3  -> Material.DIAMOND;
            case 4  -> Material.EMERALD;
            case 5  -> Material.NETHERITE_INGOT;
            default -> Material.STONE;
        };
    }

    private String wrap(String text, int lineLength) {
        // Simple naive wrap — not ideal but sufficient for lore
        if (text.length() <= lineLength) return text;
        int idx = text.lastIndexOf(' ', lineLength);
        if (idx < 0) idx = lineLength;
        return text.substring(0, idx) + "\n" + wrap(text.substring(idx + 1), lineLength);
    }

    // ── Slot helpers (used by UIManager click handler) ────────────────────────

    public static int getChampionSlotStart()  { return SLOT_CHAMPION_START; }
    public static int getRerollSlot()         { return SLOT_REROLL; }
    public static int getLockSlot()           { return SLOT_LOCK; }
    public static int getBuyXpSlot()          { return SLOT_BUY_XP; }
    public static int getCloseSlot()          { return SLOT_CLOSE; }
}
