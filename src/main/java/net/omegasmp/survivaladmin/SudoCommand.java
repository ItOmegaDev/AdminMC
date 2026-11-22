package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SudoCommand implements CommandExecutor, TabCompleter {

    private final SurvivalAdmin plugin;

    public SudoCommand(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("only-players"));
            return true;
        }
        if (!plugin.getRoleManager().hasPermission(player, "survivaladmin.sudo")) {
            player.sendMessage(plugin.getConfigManager().msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().msg("usage-sudo"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String cmd = sb.toString();
        Bukkit.dispatchCommand(target, cmd);
        player.sendMessage(plugin.getConfigManager().msg("sudo-success")
                .replace("{player}", target.getName()).replace("{command}", cmd));
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