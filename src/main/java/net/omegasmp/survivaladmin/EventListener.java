package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;

public class EventListener implements Listener {

    private final SurvivalAdmin plugin;

    public EventListener(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getRoleManager().migrateOnJoin(player);
        // Hide already vanished players from the newcomer.
        plugin.getVanishManager().applyTo(player);
        plugin.getWorldEventManager().onJoin(player);
        String role = plugin.getRoleManager().getRole(player);
        if (plugin.getVanishManager().isVanished(player)) {
            event.setJoinMessage(null);
        } else {
            event.setJoinMessage(plugin.getConfigManager().msgRaw("join").replace("{player}", rolePrefix(role) + player.getName()));
        }

        // Alt detection
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.getAltDetectionManager().onJoin(player);
        }, 40L);

        // Auto anti-cheat check on join (behavior only by default)
        int delay = plugin.getConfigManager().getInt("anticheat.auto-check-on-join.delay-ticks", 100);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.getAntiCheatManager().autoCheck(player);
        }, delay);
    }

    private String rolePrefix(String role) {
        return switch (role) {
            case "owner" -> "§6[Owner] ";
            case "staff" -> "§e[Staff] ";
            default -> "";
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getWorldEventManager().onQuit(player);
        if (plugin.getVanishManager().isVanished(player)) {
            event.setQuitMessage(null);
            plugin.getVanishManager().forget(player);
            return;
        }
        event.setQuitMessage(plugin.getConfigManager().msgRaw("quit").replace("{player}", player.getName()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getVanishManager().isVanished(player)) {
            event.setDeathMessage(null);
            return;
        }
        Player killer = player.getKiller();
        if (killer != null) {
            plugin.getQuestManager().addProgress(killer, "kill", event.getEntity().getType().name(), 1);
            event.setDeathMessage(plugin.getConfigManager().msg("kill")
                    .replace("{player}", player.getName())
                    .replace("{killer}", killer.getName()));
        } else {
            event.setDeathMessage(plugin.getConfigManager().msg("death").replace("{player}", player.getName()));
        }
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player)) {
            event.setLeaveMessage(null);
            plugin.getVanishManager().forget(player);
            return;
        }
        event.setLeaveMessage(plugin.getConfigManager().msg("kick").replace("{player}", player.getName()));
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player)) return;
        String name = event.getAdvancement().getDisplay() == null ? event.getAdvancement().getKey().getKey()
                : event.getAdvancement().getDisplay().title().toString();
        Bukkit.broadcastMessage(plugin.getConfigManager().msg("advancement")
                .replace("{player}", player.getName())
                .replace("{advancement}", name));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        plugin.getQuestManager().addProgress(event.getPlayer(), "mine", event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int amount = event.getItem().getItemStack().getAmount();
        plugin.getQuestManager().addProgress(player, "collect", event.getItem().getItemStack().getType().name(), amount);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage().toLowerCase().trim();
        if (!raw.startsWith("/")) return;
        String cmd = raw.substring(1).split(" ")[0];
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.lastIndexOf(":") + 1);
        }

        if (player.isOp() || plugin.getRoleManager().isStaff(player)) return;

        List<String> allowed = plugin.getConfig().getStringList("user-commands");
        if (allowed.contains(cmd)) return;

        event.setCancelled(true);
        player.sendMessage(plugin.getConfigManager().msg("command-blocked"));
    }

    // --- Mute: block chat ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MuteManager.MuteEntry me = plugin.getMuteManager().getMute(player);
        if (me != null) {
            event.setCancelled(true);
            long rem = plugin.getMuteManager().remainingMillis(player);
            String timeStr = (rem < 0) ? "permanent" : formatMillis(rem);
            player.sendMessage(plugin.getConfigManager().msg("muted-chat")
                    .replace("{time}", timeStr).replace("{reason}", me.reason == null ? "" : me.reason));
            return;
        }

        ChatManager.FilterResult fr = plugin.getChatManager().filter(player, event.getMessage());
        if (fr.blocked) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().msg("chat-blocked")
                    .replace("{reason}", fr.reason == null ? "" : fr.reason));
            return;
        }
        if (fr.message != null && !fr.message.equals(event.getMessage())) {
            event.setMessage(fr.message);
        }
    }

    // --- Freeze: block movement ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isFrozen(player.getUniqueId())) return;
        // allow head rotation but block position change
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        event.setTo(event.getFrom());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (plugin.getVanishManager().isVanished(player)) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("removal")
    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (plugin.getVanishManager().count() == 0) return;
        try {
            java.util.Iterator<Player> it = event.iterator();
            while (it.hasNext()) {
                Player sample = it.next();
                if (plugin.getVanishManager().isVanished(sample)) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {
            // Sample list is not mutable on this server version; nothing to do.
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.isFrozen(player.getUniqueId())) return;
        // frozen players take no damage (optional safety)
    }

    private String formatMillis(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}