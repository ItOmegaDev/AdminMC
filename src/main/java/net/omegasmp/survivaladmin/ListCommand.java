package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla /list output so vanished players do not appear in it
 * for anyone who cannot see them.
 */
public class ListCommand implements CommandExecutor {

    private final AdminMC plugin;

    public ListCommand(AdminMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean seeVanished;
        if (sender instanceof Player player) {
            seeVanished = plugin.getVanishManager().canSeeVanished(player)
                    || plugin.getVanishManager().isVanished(player);
        } else {
            seeVanished = true;
        }

        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!seeVanished && plugin.getVanishManager().isVanished(online)) continue;
            names.add(online.getName());
        }

        StringBuilder out = new StringBuilder("§6There are §e" + names.size()
                + "§6 of a max of §e" + Bukkit.getMaxPlayers() + "§6 players online");
        if (!names.isEmpty()) {
            out.append("§6: §f").append(String.join("§7, §f", names));
        }
        sender.sendMessage(out.toString());
        return true;
    }
}
