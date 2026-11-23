package net.omegasmp.survivaladmin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EventCommand implements CommandExecutor {
    private final AdminMC plugin;

    public EventCommand(AdminMC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        if (!plugin.getRoleManager().isStaff(p)) {
            p.sendMessage(plugin.getConfigManager().msg("no-permission"));
            return true;
        }
        WorldEventManager em = plugin.getWorldEventManager();
        if (args.length == 0) {
            p.sendMessage(plugin.getConfigManager().prefix() + " §7Статус: " + em.status());
            p.sendMessage("§7/event bloodmoon [start|stop]");
            p.sendMessage("§7/event boss");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("bloodmoon") || sub.equals("blood") || sub.equals("bm")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                em.forceBloodMoon(false);
                p.sendMessage(plugin.getConfigManager().prefix() + " §aКривавий місяць зупинено.");
            } else if (args.length > 1 && args[1].equalsIgnoreCase("start")) {
                em.forceBloodMoon(true);
                p.sendMessage(plugin.getConfigManager().prefix() + " §aКривавий місяць запущено.");
            } else {
                // toggle
                if (em.isBloodMoon()) em.forceBloodMoon(false);
                else em.forceBloodMoon(true);
                p.sendMessage(plugin.getConfigManager().prefix() + " §aПеремкнено. Статус: " + em.status());
            }
            return true;
        }
        if (sub.equals("boss")) {
            em.forceBoss();
            p.sendMessage(plugin.getConfigManager().prefix() + " §aСвітовий бос викликаний.");
            return true;
        }
        p.sendMessage("§cНевідома підкоманда. /event bloodmoon|boss");
        return true;
    }
}
