package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CheckCommand implements CommandExecutor, TabCompleter {

    private final SurvivalAdmin plugin;

    public CheckCommand(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("only-players"));
            return true;
        }
        if (!plugin.getRoleManager().hasPermission(player, "survivaladmin.check")) {
            player.sendMessage(plugin.getConfigManager().msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getConfigManager().msg("usage-check"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.getConfigManager().msg("cannot-self"));
            return true;
        }
        plugin.getAntiCheatManager().startCheck(target, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}