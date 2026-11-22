package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Vanish state: a vanished player is invisible, absent from the tab list,
 * absent from /list and from the server ping sample, does not collide with
 * entities, does not attract mob targeting and does not broadcast join, quit,
 * death or advancement messages.
 *
 * State is intentionally in-memory only: a restart or rejoin makes the player
 * visible again, so a forgotten vanish can never hide a player forever.
 */
public class VanishManager {

    private final SurvivalAdmin plugin;
    private final Set<UUID> vanished = Collections.synchronizedSet(new HashSet<>());

    public VanishManager(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return uuid != null && vanished.contains(uuid);
    }

    /** Number of currently vanished players. */
    public int count() {
        return vanished.size();
    }

    /**
     * Toggles vanish and applies or removes every side effect.
     *
     * @return the new vanish state
     */
    public boolean toggle(Player player) {
        boolean state = !isVanished(player);
        setVanished(player, state);
        return state;
    }

    public void setVanished(Player player, boolean state) {
        if (player == null) return;
        if (state) {
            vanished.add(player.getUniqueId());
            player.setInvisible(true);
            player.setCollidable(false);
            player.setAffectsSpawning(false);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player)) continue;
                if (!canSeeVanished(viewer)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
        } else {
            vanished.remove(player.getUniqueId());
            if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                player.setInvisible(false);
            }
            player.setCollidable(true);
            player.setAffectsSpawning(true);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    /**
     * Applies the current vanish state to a player who just joined, so vanished
     * staff stay hidden from them and visible staff keep seeing ghosts.
     */
    public void applyTo(Player joiner) {
        if (joiner == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(joiner)) continue;
            if (isVanished(online) && !canSeeVanished(joiner)) {
                joiner.hidePlayer(plugin, online);
            }
        }
    }

    /** Drops vanish state without restoring visibility (the player is leaving). */
    public void forget(Player player) {
        if (player != null) vanished.remove(player.getUniqueId());
    }

    /**
     * Whether a viewer is allowed to see vanished players. Staff only, and only
     * when explicitly enabled in config (off by default: vanish hides from all).
     */
    public boolean canSeeVanished(Player viewer) {
        if (viewer == null) return false;
        if (!plugin.getConfigManager().getBool("vanish.show-to-staff", false)) return false;
        return plugin.getRoleManager().isStaff(viewer);
    }
}
