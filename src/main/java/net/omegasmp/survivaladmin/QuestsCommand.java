package net.omegasmp.survivaladmin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuestsCommand implements CommandExecutor {
    private final SurvivalAdmin plugin;

    public QuestsCommand(SurvivalAdmin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        QuestManager qm = plugin.getQuestManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            qm.list(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("claim")) {
            if (args.length < 2) { p.sendMessage("§cUsage: /quests claim <id>"); return true; }
            qm.claim(p, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("progress")) {
            qm.list(p);
            return true;
        }
        p.sendMessage("§cUsage: /quests [list|claim <id>]");
        return true;
    }
}
