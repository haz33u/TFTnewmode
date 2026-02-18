package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.Champion;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the player's bench as an inventory GUI.
 *
 * <p>Layout (9-slot, 1 row):
 * <pre>
 * [Unit 0] [Unit 1] [Unit 2] [Unit 3] [Unit 4] [Unit 5] [Unit 6] [Unit 7] [Unit 8]
 * </pre>
 * Left-click on a unit = deploy to board (during PLANNING phase).
 * Right-click on a unit = sell.
 */
public class BenchUI {

    static final String    BENCH_TITLE          = "✦ AstralClash Bench ✦";
    static final Component BENCH_TITLE_COMPONENT =
            Component.text(BENCH_TITLE, NamedTextColor.GOLD);

    private final AstralClash plugin;

    public BenchUI(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Open ─────────────────────────────────────────────────────────────────

    public void openBench(ArenaPlayer ap) {
        Inventory inv = Bukkit.createInventory(null, 9, BENCH_TITLE_COMPONENT);
        populateBench(inv, ap);
        ap.getPlayer().openInventory(inv);
    }

    public void populateBench(Inventory inv, ArenaPlayer ap) {
        List<ChampionInstance> bench = ap.getBench();
        int maxBench = plugin.getConfigManager().getBenchSize();

        for (int i = 0; i < maxBench; i++) {
            if (i < bench.size()) {
                inv.setItem(i, buildBenchItem(bench.get(i)));
            } else {
                inv.setItem(i, buildEmptySlot());
            }
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildBenchItem(ChampionInstance ci) {
        Champion c   = ci.getChampion();
        Material mat = tierMaterial(c.getTier().getCost());

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        // Custom Model Data — links to resource pack portrait texture
        Integer cmd = ShopUI.CHAMPION_CMD.get(c.getId());
        if (cmd != null) meta.setCustomModelData(cmd);

        // Name: "★★ Seele" colored by first trait
        String stars = "★".repeat(ci.getStarLevel().getStars());
        meta.displayName(Component.text(stars + " " + c.getDisplayName(),
                c.getTraits().isEmpty() ? NamedTextColor.WHITE : c.getTraits().get(0).getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tier " + c.getTier().getCost() + " — " +
                        ci.getStarLevel().getStars() + "★", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Traits
        lore.add(Component.text("Traits:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        for (var t : c.getTraits()) {
            lore.add(Component.text("  • " + t.getDisplayName(), t.getColor())
                    .decoration(TextDecoration.ITALIC, false));
        }

        // Stats
        lore.add(Component.empty());
        lore.add(Component.text(String.format("HP: %.0f  ATK: %.0f  AS: %.2f",
                        ci.getMaxHp(), ci.getAttackDamage(), ci.getAttackSpeed()),
                NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        // Ability
        if (c.getAbility() != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Ability: " + c.getAbility().getName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Left-click → Deploy to board", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click → Sell for " +
                        sellValue(ci) + "g", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildEmptySlot() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Empty bench slot", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
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

    private int sellValue(ChampionInstance ci) {
        int baseCost = ci.getChampion().getTier().getCost();
        return switch (ci.getStarLevel().getStars()) {
            case 2  -> baseCost * 3;
            case 3  -> baseCost * 9;
            default -> baseCost;
        };
    }

    // ── Static accessors for PlayerListener ──────────────────────────────────

    public static String getBenchTitle() { return BENCH_TITLE; }
}
