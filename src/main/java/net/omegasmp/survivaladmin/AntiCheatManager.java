package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-cheat helper for SurvivalAdmin.
 *
 * Provides:
 *  - Behavior-based checks (gamemode, OP, permissions, ping, vanish, fly, speed, suspicious items, client brand).
 *  - Sign-based mod detection (like CheckHacks): opens a temporary sign editor with detection keys,
 *    waits for the player to submit, and compares the submitted lines to the keys sent.
 *
 * Mod detection is best-effort and requires the player to interact with the sign editor.
 */
public class AntiCheatManager {

    private final SurvivalAdmin plugin;

    // UUID -> active check session
    private final Map<UUID, CheckSession> sessions = new ConcurrentHashMap<>();

    // Default detection keys (translation/keybind keys registered by known mods).
    // These can be overridden via config: anticheat.mods (list of "name:key").
    private static final List<String[]> DEFAULT_MODS = Arrays.asList(
            new String[]{"Meteor Client", "key.meteor-client.open-gui"},
            new String[]{"Freecam", "key.freecam.toggle"},
            new String[]{"XRay (Fabric)", "xray.config.toggle"},
            new String[]{"ChestESP", "key.chestesp.toggle"},
            new String[]{"KillAura (Fabric)", "key.killaura"},
            new String[]{"AutoFish", "key.autofish.open_gui"},
            new String[]{"BleachHack", "bleachhack.module.killaura"},
            new String[]{"World Downloader", "key.wdl.startStop"},
            new String[]{"AutoClicker", "autoclicker-fabric.hud.holding"},
            new String[]{"AntiAFK", "key.antiafk.toggle"},
            new String[]{"Litematica", "litematica.hotkey.name.openmainmenuscreen"},
            new String[]{"Item Scroller", "itemscroller.hotkey.name.openconfigscreen"},
            new String[]{"Xaero's Minimap", "gui.xaero_minimap"},
            new String[]{"Baritone", "baritone.setting.allowBreak"}
    );

    public AntiCheatManager(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean isChecking(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Start a full check for target, initiated by checker (may be null for auto/console).
     */
    public void startCheck(Player target, Player checker) {
        if (target == null || !target.isOnline()) return;
        if (sessions.containsKey(target.getUniqueId())) {
            if (checker != null) checker.sendMessage(plugin.getConfigManager().msg("check-already-running")
                    .replace("{player}", target.getName()));
            return;
        }

        List<String[]> mods = loadMods();
        CheckSession session = new CheckSession(target, checker, mods);
        sessions.put(target.getUniqueId(), session);

        if (checker != null) {
            checker.sendMessage(plugin.getConfigManager().msg("check-started").replace("{player}", target.getName()));
        }

        // Behavior checks first (sync, on main thread)
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.behaviorFindings.addAll(runBehaviorChecks(target));
            // Then sign-based mod detection
            if (plugin.getConfigManager().getBool("anticheat.sign-check.enabled", true)) {
                runSignBatches(session);
            } else {
                finish(session);
            }
        });
    }

    /**
     * Auto-check on join (behavior only by default to avoid sign popups).
     */
    public void autoCheck(Player target) {
        if (target == null || !target.isOnline()) return;
        if (!plugin.getConfigManager().getBool("anticheat.auto-check-on-join.enabled", false)) return;

        // Skip staff/owners
        if (plugin.getRoleManager().isStaff(target)) return;

        List<String> findings = runBehaviorChecks(target);
        if (!findings.isEmpty()) {
            String msg = "Auto-check [" + target.getName() + "]: " + String.join("; ", findings);
            plugin.getLogger().warning(msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.getRoleManager().isStaff(p)) {
                    p.sendMessage(plugin.getConfigManager().msgRaw("check-auto-alert")
                            .replace("{player}", target.getName())
                            .replace("{findings}", String.join(", ", findings)));
                }
            }
        }

        if (plugin.getConfigManager().getBool("anticheat.auto-check-on-join.sign-check", false)) {
            // Run sign mod check silently-ish (still pops signs)
            startCheck(target, null);
        }
    }

    private List<String[]> loadMods() {
        List<String> raw = plugin.getConfigManager().getList("anticheat.mods");
        List<String[]> mods = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            mods.addAll(DEFAULT_MODS);
            return mods;
        }
        for (String entry : raw) {
            // format: "Display Name:key"
            int idx = entry.indexOf(':');
            if (idx > 0 && idx < entry.length() - 1) {
                mods.add(new String[]{entry.substring(0, idx).trim(), entry.substring(idx + 1).trim()});
            }
        }
        if (mods.isEmpty()) mods.addAll(DEFAULT_MODS);
        return mods;
    }

    private List<String> runBehaviorChecks(Player target) {
        List<String> findings = new ArrayList<>();
        // Gamemode
        switch (target.getGameMode()) {
            case CREATIVE -> findings.add("Creative mode");
            case SPECTATOR -> findings.add("Spectator mode");
            default -> { }
        }
        // OP
        if (target.isOp()) findings.add("OP status");
        // Dangerous permissions (best-effort via effective permissions)
        try {
            if (target.hasPermission("bukkit.command.op") || target.hasPermission("*")
                    || target.hasPermission("minecraft.command.op")) {
                findings.add("Dangerous permission (op/*)");
            }
        } catch (Exception ignored) { }
        // Vanish
        if (target.hasMetadata("survivaladmin_vanished")) findings.add("Vanished");
        // Fly
        if (target.getAllowFlight() && target.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            findings.add("Flight enabled in survival");
        }
        // Walk speed
        if (target.getWalkSpeed() > 0.4f) findings.add("High walk speed (" + target.getWalkSpeed() + ")");
        // Ping
        int ping = target.getPing();
        if (ping > 500) findings.add("High ping (" + ping + "ms)");
        // Client brand
        String brand = target.getClientBrandName();
        if (brand != null) {
            String lb = brand.toLowerCase();
            if (lb.contains("fabric") || lb.contains("forge") || lb.contains("quilt")) {
                findings.add("Modded client brand: " + brand);
            }
        }
        // Suspicious items in survival inventory
        if (target.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            for (org.bukkit.inventory.ItemStack it : target.getInventory().getContents()) {
                if (it == null) continue;
                Material m = it.getType();
                if (m == Material.COMMAND_BLOCK || m == Material.CHAIN_COMMAND_BLOCK
                        || m == Material.REPEATING_COMMAND_BLOCK || m == Material.BARRIER
                        || m == Material.STRUCTURE_BLOCK || m == Material.JIGSAW
                        || m == Material.DEBUG_STICK) {
                    findings.add("Suspicious item: " + m.name());
                }
            }
        }
        return findings;
    }

    private void runSignBatches(CheckSession session) {
        // Group mods into batches of 4 (sign lines)
        List<List<String[]>> batches = new ArrayList<>();
        List<String[]> cur = new ArrayList<>();
        for (String[] m : session.mods) {
            cur.add(m);
            if (cur.size() >= 4) {
                batches.add(new ArrayList<>(cur));
                cur.clear();
            }
        }
        if (!cur.isEmpty()) batches.add(new ArrayList<>(cur));

        session.batches = batches;
        session.batchIndex = 0;
        openNextBatch(session);
    }

    private void openNextBatch(CheckSession session) {
        if (session.batchIndex >= session.batches.size()) {
            finish(session);
            return;
        }
        List<String[]> batch = session.batches.get(session.batchIndex);
        Player target = session.target;
        if (target == null || !target.isOnline()) {
            finish(session);
            return;
        }

        // Find a safe spot for the sign
        Location spot = findSignSpot(target);
        if (spot == null) {
            // Skip sign detection
            session.batchIndex = session.batches.size();
            finish(session);
            return;
        }

        // Save original block
        Block block = spot.getBlock();
        Material original = block.getType();
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();

        // Set sign block
        block.setType(Material.OAK_SIGN);
        if (!(block.getState() instanceof Sign sign)) {
            block.setType(original);
            block.setBlockData(originalData);
            session.batchIndex++;
            openNextBatch(session);
            return;
        }

        // Prepare lines = keys
        String[] keys = new String[4];
        Arrays.fill(keys, "");
        for (int i = 0; i < batch.size(); i++) keys[i] = batch.get(i)[1];
        sign.setLine(0, keys[0]);
        sign.setLine(1, keys[1]);
        sign.setLine(2, keys[2]);
        sign.setLine(3, keys[3]);
        sign.update();

        // Record sent keys for this batch
        session.sentKeys = keys;
        session.currentBatchMods = batch;
        session.spot = spot;
        session.originalMaterial = original;
        session.originalData = originalData;
        session.waiting = true;

        // Open sign editor
        try {
            target.openSign(sign);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not open sign for " + target.getName() + ": " + e.getMessage());
            restoreBlock(session);
            session.waiting = false;
            session.batchIndex++;
            openNextBatch(session);
            return;
        }

        // Timeout
        int timeoutTicks = plugin.getConfigManager().getInt("anticheat.sign-check.timeout-ticks", 200);
        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.waiting) {
                session.waiting = false;
                // Treat as no response (skip)
                restoreBlock(session);
                session.batchIndex++;
                openNextBatch(session);
            }
        }, timeoutTicks);
    }

    /**
     * Called by event listener when a SignChangeEvent occurs.
     * Returns true if handled.
     */
    public boolean handleSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        CheckSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.waiting) return false;
        if (session.spot == null || !event.getBlock().getLocation().equals(session.spot)) return false;

        session.waiting = false;
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
            session.timeoutTask = null;
        }

        String[] submitted = event.getLines();
        // Cancel the change so the sign doesn't actually update
        event.setCancelled(true);

        // Compare
        String[] sent = session.sentKeys;
        List<String[]> batch = session.currentBatchMods;
        for (int i = 0; i < batch.size(); i++) {
            String sentKey = (sent[i] == null) ? "" : sent[i].trim();
            String got = (submitted != null && i < submitted.length && submitted[i] != null) ? submitted[i].trim() : "";
            if (!got.isEmpty() && !got.equalsIgnoreCase(sentKey)) {
                // Mod likely replaced the key
                session.modFindings.add(batch.get(i)[0] + " -> " + got);
            }
        }

        restoreBlock(session);
        session.batchIndex++;
        // Small delay before next batch to avoid spam
        int delay = plugin.getConfigManager().getInt("anticheat.sign-check.between-batches-ticks", 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> openNextBatch(session), delay);
        return true;
    }

    private void restoreBlock(CheckSession session) {
        if (session.spot == null) return;
        try {
            Block b = session.spot.getBlock();
            if (session.originalData != null) {
                b.setBlockData(session.originalData, false);
            } else if (session.originalMaterial != null) {
                b.setType(session.originalMaterial, false);
            }
        } catch (Exception ignored) { }
        session.spot = null;
    }

    private Location findSignSpot(Player target) {
        Location base = target.getLocation().clone();
        org.bukkit.World w = base.getWorld();
        if (w == null) return null;
        // Search a small area around player for an air block with solid below
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Location l = base.clone().add(dx, dy, dz);
                    Block b = l.getBlock();
                    Block below = l.clone().subtract(0, 1, 0).getBlock();
                    if (b.getType() == Material.AIR && below.getType().isSolid()) {
                        return l;
                    }
                }
            }
        }
        return null;
    }

    private void finish(CheckSession session) {
        sessions.remove(session.target.getUniqueId());
        restoreBlock(session);
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
            session.timeoutTask = null;
        }

        Player target = session.target;
        Player checker = session.checker;

        List<String> all = new ArrayList<>();
        all.addAll(session.behaviorFindings);
        all.addAll(session.modFindings);

        StringBuilder report = new StringBuilder();
        report.append("Check result for ").append(target.getName()).append(": ");
        if (all.isEmpty()) {
            report.append("CLEAN");
        } else {
            report.append("FLAGS (").append(all.size()).append("): ").append(String.join("; ", all));
        }

        String msg = plugin.getConfigManager().msgRaw("check-result")
                .replace("{player}", target.getName())
                .replace("{result}", all.isEmpty() ? "CLEAN" : "FLAGGED")
                .replace("{count}", String.valueOf(all.size()))
                .replace("{findings}", all.isEmpty() ? "none" : String.join(", ", all));

        if (checker != null && checker.isOnline()) {
            checker.sendMessage(plugin.getConfigManager().prefix() + " " + msg);
        } else {
            plugin.getLogger().info(msg);
        }

        // Broadcast to staff if flagged
        if (!all.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(checker)) continue;
                if (plugin.getRoleManager().isStaff(p)) {
                    p.sendMessage(plugin.getConfigManager().msgRaw("check-alert")
                            .replace("{player}", target.getName())
                            .replace("{findings}", String.join(", ", all)));
                }
            }
            plugin.getLogger().warning("CheckHacks-like check flagged " + target.getName() + ": " + String.join("; ", all));
        }
    }

    // ---- inner session ----
    private static class CheckSession {
        final Player target;
        final Player checker;
        final List<String[]> mods;
        final List<String> behaviorFindings = new ArrayList<>();
        final List<String> modFindings = new ArrayList<>();
        List<List<String[]>> batches = new ArrayList<>();
        int batchIndex = 0;
        String[] sentKeys;
        List<String[]> currentBatchMods;
        Location spot;
        Material originalMaterial;
        org.bukkit.block.data.BlockData originalData;
        boolean waiting = false;
        org.bukkit.scheduler.BukkitTask timeoutTask;

        CheckSession(Player target, Player checker, List<String[]> mods) {
            this.target = target;
            this.checker = checker;
            this.mods = mods;
        }
    }
}