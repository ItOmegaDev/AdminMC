package net.omegasmp.survivaladmin;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Hides command/tab completions from non-OP players when they type "/" + TAB.
 * OPs keep full completion list as before.
 *
 * Async-safe: only touches the event's completion lists and reads Player#isOp().
 */
public class TabCompleteBlocker implements Listener {

    private final SurvivalAdmin plugin;

    public TabCompleteBlocker(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        org.bukkit.command.CommandSender sender = event.getSender();
        if (!(sender instanceof Player player)) return;
        if (player.isOp()) return;

        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;

        // Clear all completions (both legacy string list and modern Completion list)
        try {
            event.setCompletions(new java.util.ArrayList<>());
        } catch (Throwable ignored) { }
        try {
            event.completions(new java.util.ArrayList<>());
        } catch (Throwable ignored) { }
        try {
            event.getCompletions().clear();
        } catch (Throwable ignored) { }
        try {
            event.completions().clear();
        } catch (Throwable ignored) { }
    }
}