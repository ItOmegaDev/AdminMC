package net.omegasmp.survivaladmin;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filters command/tab completions for non-OP players.
 *
 * OPs keep the full completion list. For everyone else, only commands that are
 * either listed in config (user-commands + tab-complete.allowed) or, for
 * staff/owners, commands they actually have permission for are shown.
 *
 * Argument completions are kept for allowed commands and cleared for everything
 * else, so typing "/disallowed <TAB>" produces no suggestions.
 *
 * Async-safe: only reads config/role state and mutates the event's completion
 * lists.
 */
public class TabCompleteBlocker implements Listener {

    private final SurvivalAdmin plugin;

    public TabCompleteBlocker(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        CommandSender sender = event.getSender();
        if (!(sender instanceof Player player)) return;
        if (player.isOp()) return;
        if (!plugin.getConfigManager().getBool("tab-complete.filter", true)) return;

        String buffer = event.getBuffer();
        if (buffer == null) return;
        String trimmed = buffer.trim();
        if (!trimmed.startsWith("/")) return;

        String[] parts = trimmed.substring(1).split("\\s+", -1);
        String commandToken = parts.length > 0 ? parts[0] : "";
        String commandBase = normalize(commandToken);
        boolean topLevel = parts.length <= 1;

        Set<String> allowed = allowedCommands(player);

        try {
            if (topLevel) {
                String query = commandToken;
                List<String> originalStrings = event.getCompletions();
                List<String> filteredStrings = new ArrayList<>();
                if (originalStrings != null) {
                    for (String suggestion : originalStrings) {
                        if (suggestion == null) continue;
                        String base = normalize(suggestion);
                        if (!allowed.contains(base)) {
                            // Staff may still use commands they have permission for,
                            // even when those commands are not listed in config.
                            if (!isPermissionAllowed(player, suggestion)) continue;
                        }
                        if (!suggestion.toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) continue;
                        filteredStrings.add(suggestion);
                    }
                }
                event.setCompletions(filteredStrings);

                List<AsyncTabCompleteEvent.Completion> originalCompletions = event.completions();
                List<AsyncTabCompleteEvent.Completion> filteredCompletions = new ArrayList<>();
                if (originalCompletions != null) {
                    for (AsyncTabCompleteEvent.Completion completion : originalCompletions) {
                        if (completion == null) continue;
                        String suggestion = completion.suggestion();
                        if (suggestion == null) continue;
                        String base = normalize(suggestion);
                        if (!allowed.contains(base) && !isPermissionAllowed(player, suggestion)) continue;
                        if (!suggestion.toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) continue;
                        filteredCompletions.add(AsyncTabCompleteEvent.Completion.completion(suggestion, completion.tooltip()));
                    }
                }
                event.completions(filteredCompletions);
            } else if (!allowed.contains(commandBase) && !isPermissionAllowed(player, commandToken)) {
                // Clear argument suggestions for commands the player may not use.
                event.setCompletions(new ArrayList<>());
                event.completions(new ArrayList<>());
            }
        } catch (Throwable ignored) {
            // Never let tab completion filtering break the event pipeline.
        }
    }

    private Set<String> allowedCommands(Player player) {
        Set<String> allowed = new LinkedHashSet<>();

        for (String command : plugin.getConfigManager().getList("user-commands")) {
            String normalized = normalize(command);
            if (!normalized.isEmpty()) allowed.add(normalized);
        }
        for (String command : plugin.getConfigManager().getList("tab-complete.allowed")) {
            String normalized = normalize(command);
            if (!normalized.isEmpty()) allowed.add(normalized);
        }

        // Staff/owners also see SurvivalAdmin commands they are allowed to run.
        if (plugin.getRoleManager().isStaff(player)) {
            for (String commandName : plugin.getDescription().getCommands().keySet()) {
                org.bukkit.command.PluginCommand pluginCommand = plugin.getCommand(commandName);
                String permission = pluginCommand == null ? null : pluginCommand.getPermission();
                if (permission == null || plugin.getRoleManager().hasPermission(player, permission)) {
                    String normalized = normalize(commandName);
                    if (!normalized.isEmpty()) allowed.add(normalized);
                }
            }
        }

        return allowed;
    }

    /**
     * For staff, also honor the permission declared by any command in Bukkit's
     * command map. This keeps tab suggestions consistent with the commands the
     * player can actually execute.
     */
    private boolean isPermissionAllowed(Player player, String suggestion) {
        if (!plugin.getRoleManager().isStaff(player)) return false;
        String base = normalize(suggestion);
        if (base.isEmpty()) return false;
        Command command = Bukkit.getCommandMap().getCommand(base);
        if (command == null) return false;
        return command.testPermissionSilent(player);
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) value = value.substring(1);
        if (value.contains(":")) value = value.substring(value.lastIndexOf(':') + 1);
        return value;
    }
}
