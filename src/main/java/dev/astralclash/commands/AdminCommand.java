package dev.astralclash.commands;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /astraladmin <reload|setgold|addxp|forcestart|champions>
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AstralClash plugin;

    public AdminCommand(AstralClash plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("astralclash.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendAdminHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "reload"      -> cmdReload(sender);
            case "setgold"     -> cmdSetGold(sender, args);
            case "addxp"       -> cmdAddXp(sender, args);
            case "forcestart"  -> cmdForceStart(sender);
            case "champions"   -> cmdListChampions(sender);
            case "help"        -> { sendAdminHelp(sender); yield true; }
            default            -> { sendAdminHelp(sender); yield true; }
        };
    }

    // ── Subcommands ──────────────────────────────────────────────────────────

    private boolean cmdReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        sender.sendMessage(Component.text("AstralClash config reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private boolean cmdSetGold(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /astraladmin setgold <player> <amount>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(target);
        if (ap == null) { sender.sendMessage(Component.text("Player is not in a game.", NamedTextColor.RED)); return true; }
        try {
            int gold = Integer.parseInt(args[2]);
            ap.setGold(gold);
            sender.sendMessage(Component.text("Set " + target.getName() + "'s gold to " + gold, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean cmdAddXp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /astraladmin addxp <player> <amount>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(target);
        if (ap == null) { sender.sendMessage(Component.text("Player is not in a game.", NamedTextColor.RED)); return true; }
        try {
            int xp = Integer.parseInt(args[2]);
            ap.addXp(xp);
            sender.sendMessage(Component.text("Added " + xp + " XP to " + target.getName() +
                    " (now Lv" + ap.getLevel() + ")", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean cmdForceStart(CommandSender sender) {
        var lobby = plugin.getGameManager().getLobby();
        if (lobby.getSize() < 1) {
            sender.sendMessage(Component.text("No players in lobby!", NamedTextColor.RED));
            return true;
        }
        plugin.getGameManager().startGame(List.copyOf(
                lobby.getWaitingPlayers().stream().toList()));
        sender.sendMessage(Component.text("Force-started the game!", NamedTextColor.GREEN));
        return true;
    }

    private boolean cmdListChampions(CommandSender sender) {
        sender.sendMessage(Component.text("══ Loaded Champions ══", NamedTextColor.LIGHT_PURPLE));
        plugin.getChampionManager().getAllChampions().forEach(c ->
                sender.sendMessage(Component.text(
                        String.format("  §f%-12s §7Tier §6%d §7Traits: %s",
                                c.getId(), c.getTier().getCost(),
                                c.getTraits().stream().map(t -> t.name()).reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b)),
                        NamedTextColor.WHITE)));
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══ AstralClash Admin Commands ═══", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(Component.text("/aca reload           — reload config", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca setgold <p> <n>  — set player gold", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca addxp <p> <n>    — add xp to player", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca forcestart       — force start game", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca champions        — list loaded champions", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("reload", "setgold", "addxp", "forcestart", "champions");
        return List.of();
    }
}
