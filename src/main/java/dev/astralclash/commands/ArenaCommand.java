package dev.astralclash.commands;

import dev.astralclash.AstralClash;
import dev.astralclash.bot.BotPlayer;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /astral <join|leave|shop|board|sell|stats|help>
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final AstralClash plugin;

    public ArenaCommand(AstralClash plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "join"       -> cmdJoin(player);
            case "leave"      -> cmdLeave(player);
            case "shop"       -> cmdShop(player);
            case "bench"      -> cmdBench(player);
            case "menu"       -> cmdMenu(player);
            case "stats"      -> cmdStats(player);
            case "sell"       -> cmdSell(player, args);
            case "forcestart" -> cmdForceStart(player);
            case "addbot"     -> cmdAddBot(player, args);
            case "removebots" -> cmdRemoveBots(player);
            case "help"       -> { sendHelp(player); yield true; }
            default          -> { sendHelp(player); yield true; }
        };
    }

    // ── Subcommands ──────────────────────────────────────────────────────────

    private boolean cmdJoin(Player player) {
        boolean joined = plugin.getGameManager().joinLobby(player);
        if (joined) {
            player.sendMessage(Component.text(
                    "You joined the AstralClash lobby!", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean cmdForceStart(Player player) {
        if (!player.hasPermission("astralclash.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        var lobby = plugin.getGameManager().getLobby();
        if (lobby.getSize() < 1) {
            player.sendMessage(Component.text("No players in lobby!", NamedTextColor.RED));
            return true;
        }
        // Combine players and bots
        List<java.util.UUID> allParticipants = new ArrayList<>(lobby.getWaitingPlayers());
        allParticipants.addAll(lobby.getWaitingBots());
        plugin.getGameManager().startGame(allParticipants);
        player.sendMessage(Component.text("Force-started the game with " + 
            lobby.getPlayerCount() + " players and " + lobby.getBotCount() + " bots!", NamedTextColor.GREEN));
        return true;
    }

    private boolean cmdLeave(Player player) {
        var ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null && !plugin.getGameManager().getLobby().hasPlayer(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in a game or lobby.", NamedTextColor.RED));
            return true;
        }
        plugin.getGameManager().leaveLobby(player);
        plugin.getPlayerManager().removeArenaPlayer(player.getUniqueId());
        player.sendMessage(Component.text("You left AstralClash.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean cmdShop(Player player) {
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }
        plugin.getUIManager().openShop(ap);
        return true;
    }

    private boolean cmdBench(Player player) {
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }
        plugin.getUIManager().openBench(ap);
        return true;
    }

    private boolean cmdMenu(Player player) {
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }
        plugin.getUIManager().openMainMenu(ap);
        return true;
    }

    private boolean cmdStats(Player player) {
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) {
            // Load from DB just to display
            var stats = plugin.getDatabaseManager()
                    .loadStats(player.getUniqueId(), player.getName());
            player.sendMessage(Component.text("═══ Your AstralClash Stats ═══", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("Games Played: " + stats.getGamesPlayed(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Games Won:    " + stats.getGamesWon(), NamedTextColor.GREEN));
            player.sendMessage(Component.text("Win Rate:     " + String.format("%.1f", stats.getWinRate()) + "%", NamedTextColor.AQUA));
            player.sendMessage(Component.text("Best Place:   #" + stats.getHighestPlace(), NamedTextColor.GOLD));
        } else {
            var stats = ap.getStats();
            player.sendMessage(Component.text("═══ In-Game Stats ═══", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("Gold: " + ap.getGold(), NamedTextColor.GOLD));
            player.sendMessage(Component.text("HP:   " + ap.getHealth(), NamedTextColor.RED));
            player.sendMessage(Component.text("Lv:   " + ap.getLevel() + " | XP: " + ap.getXp(), NamedTextColor.AQUA));
            player.sendMessage(Component.text("Units: " + ap.getDeployedCount() + "/" + ap.getBoardSizeLimit(), NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean cmdSell(Player player, String[] args) {
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /astral sell <bench-slot 1-9>", NamedTextColor.RED));
            return true;
        }
        try {
            int slot = Integer.parseInt(args[1]) - 1;
            var bench = ap.getBench();
            if (slot < 0 || slot >= bench.size()) {
                player.sendMessage(Component.text("Invalid bench slot.", NamedTextColor.RED));
                return true;
            }
            ChampionInstance ci = bench.get(slot);
            int gold = plugin.getShopManager().sell(ap, ci);
            player.sendMessage(Component.text(
                    "Sold " + ci.getChampion().getDisplayName() + " for " + gold + " gold.",
                    NamedTextColor.YELLOW));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Please provide a valid slot number.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean cmdAddBot(Player player, String[] args) {
        if (!player.hasPermission("astralclash.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        
        int count = 1;
        double difficulty = 0.5;
        
        if (args.length >= 2) {
            try {
                count = Math.max(1, Math.min(7, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid count. Usage: /astral addbot [count] [difficulty]", NamedTextColor.RED));
                return true;
            }
        }
        
        if (args.length >= 3) {
            try {
                difficulty = Math.max(0, Math.min(1, Double.parseDouble(args[2])));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid difficulty (0.0-1.0). Usage: /astral addbot [count] [difficulty]", NamedTextColor.RED));
                return true;
            }
        }
        
        var botManager = plugin.getBotManager();
        List<BotPlayer> bots = botManager.createBots(count, difficulty);
        
        // Add bots to lobby
        var lobby = plugin.getGameManager().getLobby();
        for (BotPlayer bot : bots) {
            lobby.addBot(bot);
        }
        
        player.sendMessage(Component.text("Added " + count + " bot(s) with difficulty " + 
            String.format("%.1f", difficulty) + "!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Total in lobby: " + lobby.getSize() + " (bots: " + 
            botManager.getBotCount() + ")", NamedTextColor.YELLOW));
        
        return true;
    }

    private boolean cmdRemoveBots(Player player) {
        if (!player.hasPermission("astralclash.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        
        int count = plugin.getBotManager().getBotCount();
        plugin.getBotManager().removeAllBots();
        plugin.getGameManager().getLobby().removeAllBots();
        
        player.sendMessage(Component.text("Removed " + count + " bot(s).", NamedTextColor.YELLOW));
        return true;
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("═══ AstralClash Commands ═══", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("/astral join        — join the lobby", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/astral leave       — leave the game", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/astral menu        — open main menu (GUI)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/astral shop        — open the shop", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/astral bench       — open your bench", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/astral sell <slot> — sell a bench champion", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/astral stats       — view your stats", NamedTextColor.YELLOW));
        if (player.hasPermission("astralclash.admin")) {
            player.sendMessage(Component.text("§6═══ Admin Commands ═══", NamedTextColor.GOLD));
            player.sendMessage(Component.text("/astral forcestart      — start game now", NamedTextColor.GOLD));
            player.sendMessage(Component.text("/astral addbot [n] [d]  — add bots (n=count, d=difficulty 0-1)", NamedTextColor.GOLD));
            player.sendMessage(Component.text("/astral removebots      — remove all bots", NamedTextColor.GOLD));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            var list = new ArrayList<>(List.of("join", "leave", "menu", "shop", "bench", "sell", "stats", "help"));
            if (sender.hasPermission("astralclash.admin")) {
                list.add("forcestart");
                list.add("addbot");
                list.add("removebots");
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addbot")) {
            return List.of("1", "2", "3", "4", "5", "6", "7");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addbot")) {
            return List.of("0.0", "0.3", "0.5", "0.7", "1.0");
        }
        return List.of();
    }
}
