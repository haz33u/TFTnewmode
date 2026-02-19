package dev.astralclash.commands;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.Champion;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.StarLevel;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * /astraladmin <subcommand>
 *
 * Subcommands:
 *   reload                          — reload config
 *   setgold  <player> <amount>      — set player gold
 *   addxp    <player> <amount>      — add XP to player
 *   sethealth <player> <amount>     — set player HP
 *   forcestart                      — force start game
 *   champions                       — list loaded champions
 *   addchampion <player> <champion> — add champion to player's bench
 *   status                          — show game state for all players
 *   tp       <player>               — teleport sender to player
 *   help                            — show this help
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AstralClash plugin;
    private final TraitManager traitManager = new TraitManager();

    public AdminCommand(AstralClash plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("astralclash.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendAdminHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "reload"       -> cmdReload(sender);
            case "setgold"      -> cmdSetGold(sender, args);
            case "addxp"        -> cmdAddXp(sender, args);
            case "sethealth"    -> cmdSetHealth(sender, args);
            case "forcestart"   -> cmdForceStart(sender);
            case "champions"    -> cmdListChampions(sender);
            case "addchampion"  -> cmdAddChampion(sender, args);
            case "status"       -> cmdStatus(sender);
            case "tp"           -> cmdTp(sender, args);
            case "help"         -> { sendAdminHelp(sender); yield true; }
            default             -> { sendAdminHelp(sender); yield true; }
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

    private boolean cmdSetHealth(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /astraladmin sethealth <player> <amount>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(target);
        if (ap == null) { sender.sendMessage(Component.text("Player is not in a game.", NamedTextColor.RED)); return true; }
        try {
            int hp = Integer.parseInt(args[2]);
            ap.setHealth(Math.max(0, hp));
            sender.sendMessage(Component.text("Set " + target.getName() + "'s HP to " + ap.getHealth(), NamedTextColor.GREEN));
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
                                c.getTraits().stream().map(Enum::name).reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b)),
                        NamedTextColor.WHITE)));
        return true;
    }

    private boolean cmdAddChampion(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /astraladmin addchampion <player> <champion_id>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(target);
        if (ap == null) { sender.sendMessage(Component.text("Player is not in a game.", NamedTextColor.RED)); return true; }
        if (!ap.hasBenchSpace()) { sender.sendMessage(Component.text("Bench is full.", NamedTextColor.RED)); return true; }

        String champId = args[2].toLowerCase();
        Champion champ = plugin.getChampionManager().getChampion(champId);
        if (champ == null) {
            sender.sendMessage(Component.text("Unknown champion: " + champId +
                    ". Use /aca champions to list IDs.", NamedTextColor.RED));
            return true;
        }
        StarLevel star = args.length >= 4 ? parseStarLevel(args[3]) : StarLevel.ONE;
        ChampionInstance ci = new ChampionInstance(champ, star, target.getUniqueId());
        ap.addToBench(ci);
        sender.sendMessage(Component.text("Added " + champ.getDisplayName() + " " +
                star.getStars() + "★ to " + target.getName() + "'s bench.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Admin added " + champ.getDisplayName() +
                " " + star.getStars() + "★ to your bench.", NamedTextColor.AQUA));
        return true;
    }

    private boolean cmdStatus(CommandSender sender) {
        sender.sendMessage(Component.text("══ AstralClash Game Status ══", NamedTextColor.LIGHT_PURPLE));

        var gm = plugin.getGameManager();
        sender.sendMessage(Component.text(
                "Phase: " + gm.getPhase() +
                " | Round: " + gm.getRoundManager().getRoundNumber() +
                " | Players alive: " + gm.getActivePlayers().size(),
                NamedTextColor.YELLOW));

        for (ArenaPlayer ap : gm.getActivePlayers()) {
            int deployed = ap.getDeployedCount();
            int bench    = ap.getBench().size();

            // Active traits summary
            Map<Trait, Integer> traits = ap.getBoard() != null
                    ? traitManager.countTraits(ap.getBoard().getDeployedChampions())
                    : Map.of();
            String traitSummary = traits.entrySet().stream()
                    .filter(e -> e.getKey().getActiveTier(e.getValue()) > 0)
                    .map(e -> e.getKey().getDisplayName() + "(" + e.getValue() + ")")
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);

            sender.sendMessage(Component.text(
                    String.format("  §f%-16s §cHP:§f%3d  §6G:§f%2d  §aLv:§f%d  §e%d+%d units  §7%s",
                            ap.getPlayer().getName(),
                            ap.getHealth(),
                            ap.getGold(),
                            ap.getLevel(),
                            deployed, bench,
                            traitSummary.isEmpty() ? "no active traits" : traitSummary),
                    NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("Must be a player to use /aca tp.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /astraladmin tp <player>", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        admin.teleport(target.getLocation());
        sender.sendMessage(Component.text("Teleported to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private StarLevel parseStarLevel(String s) {
        return switch (s) {
            case "2", "2*" -> StarLevel.TWO;
            case "3", "3*" -> StarLevel.THREE;
            default        -> StarLevel.ONE;
        };
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══ AstralClash Admin Commands (/aca) ═══", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(Component.text("/aca reload                     — reload config",                  NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca setgold    <p> <n>         — set player gold",               NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca addxp      <p> <n>         — add xp to player",              NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca sethealth  <p> <n>         — set player HP",                 NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca forcestart                 — force start game",              NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca champions                  — list loaded champions",         NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca addchampion <p> <id> [star]— add champion to bench (1-3★)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca status                     — show game state / traits",      NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/aca tp         <p>             — teleport to player",            NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("reload", "setgold", "addxp", "sethealth",
                "forcestart", "champions", "addchampion", "status", "tp");
        if (args.length == 3 && args[0].equalsIgnoreCase("addchampion")) {
            return plugin.getChampionManager().getAllChampions().stream()
                    .map(Champion::getId).toList();
        }
        return List.of();
    }
}
