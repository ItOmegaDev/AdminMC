package net.omegasmp.survivaladmin;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandRegistrar {

    private final SurvivalAdmin plugin;

    public CommandRegistrar(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        AdminCommands adminCmds = new AdminCommands(plugin);

        // Role management (owner only)
        register("staff", new RoleCommand(plugin, "staff"));
        register("unstaff", new RoleCommand(plugin, "unstaff"));
        register("owner", new RoleCommand(plugin, "owner"));
        register("unowner", new RoleCommand(plugin, "unowner"));

        // Admin panel
        register("admin", new AdminCommand(plugin));
        register("help", new AdminCommand(plugin));

        // Anti-cheat check command
        register("check", new CheckCommand(plugin));

        // Vanilla /list replacement that hides vanished players
        register("list", new ListCommand(plugin));

        // Sudo command (owner-level)
        register("sudo", new SudoCommand(plugin));

        // Admin commands (staff+)
        String[] adminCommands = {"tp", "gm", "gamemode", "time", "weather", "invsee", "inv", "endersee",
                "getoff", "invis", "vanish", "kick", "mute", "tempmute", "unmute", "freeze", "unfreeze",
                "clearchat", "cc", "broadcast", "bc", "warn", "clearentities", "tps", "announce"};
        for (String cmd : adminCommands) {
            org.bukkit.command.PluginCommand pc = plugin.getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(adminCmds);
                pc.setTabCompleter(adminCmds);
            }
        }
    }

    private void register(String name, CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                cmd.setTabCompleter((TabCompleter) executor);
            }
        }
    }
}