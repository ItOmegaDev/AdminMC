package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * SurvivalAdmin - Professional administration plugin for Minecraft Survival servers.
 *
 * Features:
 * - Role-based access control (user, staff, owner) with permission inheritance
 * - UUID-based role storage (immune to name changes)
 * - Atomic file writes (crash-safe)
 * - Granular permissions (survivaladmin.kick, survivaladmin.ban, etc.)
 * - Namespace/alias bypass prevention
 * - Command blocking for regular users
 * - Custom messages (join, quit, death, kill)
 * - Admin commands: tp, gamemode, time, weather, invsee, getoff, vanish
 * - Moderation: kick, mute, freeze, clearchat, broadcast, warn
 * - Mute + warn persistence (mutes.json, warns.json)
 * - YAML config
 *
 * Made by Omega (@ItOmega)
 * Version: 2.1.0
 */
public final class SurvivalAdmin extends JavaPlugin {

    private static SurvivalAdmin instance;
    private RoleManager roleManager;
    private CommandBlocker commandBlocker;
    private Config config;
    private MuteManager muteManager;
    private WarnManager warnManager;
    private AntiCheatManager antiCheatManager;
    private AltDetectionManager altDetectionManager;
    private ChatManager chatManager;
    private AnnouncementManager announcementManager;
    private AdminGUI adminGUI;

    // Frozen players (in-memory, cleared on restart)
    private final Set<java.util.UUID> frozen = new java.util.HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.config = new Config(this);
        this.roleManager = new RoleManager(this);
        this.commandBlocker = new CommandBlocker(this);
        this.muteManager = new MuteManager(this);
        this.warnManager = new WarnManager(this);
        this.antiCheatManager = new AntiCheatManager(this);
        this.altDetectionManager = new AltDetectionManager(this);
        this.chatManager = new ChatManager(this);
        this.announcementManager = new AnnouncementManager(this);
        this.adminGUI = new AdminGUI(this);

        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.commandBlocker, this);
        Bukkit.getPluginManager().registerEvents(new TabCompleteBlocker(this), this);
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onSignChange(org.bukkit.event.block.SignChangeEvent e) {
                if (antiCheatManager != null) antiCheatManager.handleSignChange(e);
            }
            @org.bukkit.event.EventHandler
            public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
                if (!(e.getWhoClicked() instanceof Player player)) return;
                if (!adminGUI.isOpen(player) || !adminGUI.isCurrent(player, e.getView().getTopInventory())) {
                    return;
                }
                e.setCancelled(true);
                if (!roleManager.isStaff(player)) {
                    adminGUI.close(player);
                    player.closeInventory();
                    player.sendMessage(config.msg("no-permission"));
                    return;
                }
                int slot = e.getRawSlot();
                if (slot < 0 || slot >= e.getInventory().getSize()) return;
                Map<Integer, String> acts = adminGUI.currentActions(player);
                String action = acts.get(slot);
                if (action == null) return;
                handleGuiAction(player, action);
            }
            @org.bukkit.event.EventHandler
            public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
                if (e.getPlayer() instanceof Player player) {
                    // Only drop the session when the page itself is closed, not when a
                    // new page replaced it (the close event of the old page can arrive late).
                    if (adminGUI.isCurrent(player, e.getInventory())) adminGUI.close(player);
                }
            }
        }, this);

        new CommandRegistrar(this).registerAll();
        announcementManager.start();

        getLogger().info("SurvivalAdmin v2.4.0 enabled - Made by Omega (@ItOmega)");
    }

    @Override
    public void onDisable() {
        if (this.roleManager != null) this.roleManager.save();
        if (this.muteManager != null) this.muteManager.save();
        if (this.warnManager != null) this.warnManager.save();
        try {
            org.bukkit.Bukkit.broadcastMessage(this.config.msg("stop"));
        } catch (Exception ignored) { }
        getLogger().info("SurvivalAdmin disabled.");
    }

    public static SurvivalAdmin getInstance() { return instance; }
    public RoleManager getRoleManager() { return roleManager; }
    public CommandBlocker getCommandBlocker() { return commandBlocker; }
    public Config getConfigManager() { return config; }
    public MuteManager getMuteManager() { return muteManager; }
    public WarnManager getWarnManager() { return warnManager; }
    public AntiCheatManager getAntiCheatManager() { return antiCheatManager; }
    public AltDetectionManager getAltDetectionManager() { return altDetectionManager; }
    public ChatManager getChatManager() { return chatManager; }
    public AnnouncementManager getAnnouncementManager() { return announcementManager; }
    public AdminGUI getAdminGUI() { return adminGUI; }

    private void handleGuiAction(Player player, String action) {
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("open:")) {
            String t = action.substring(5);
            switch (t) {
                case "main" -> adminGUI.openMain(player);
                case "moderation" -> adminGUI.openModeration(player);
                case "teleport" -> adminGUI.openTeleport(player);
                case "world" -> adminGUI.openWorld(player);
                case "players" -> adminGUI.openPlayers(player);
                case "anticheat" -> adminGUI.openAnticheat(player);
                case "chat" -> adminGUI.openChat(player);
                case "server" -> adminGUI.openServer(player);
                case "roles" -> adminGUI.openRoles(player);
            }
            return;
        }
        if (action.startsWith("select:")) {
            adminGUI.handleSelect(player, action);
            return;
        }
        if (action.startsWith("cmd:")) {
            String cmd = action.substring(4);
            player.closeInventory();
            // map friendly names
            if (cmd.equals("usage-kick")) { player.sendMessage("§cUsage: /kick <player> [reason]"); return; }
            if (cmd.equals("usage-getoff")) { player.sendMessage("§cUsage: /getoff <player> <time> [reason]"); return; }
            if (cmd.equals("usage-mute")) { player.sendMessage("§cUsage: /mute <player> [time] [reason]"); return; }
            if (cmd.equals("usage-freeze")) { player.sendMessage("§cUsage: /freeze <player>"); return; }
            if (cmd.equals("usage-unfreeze")) { player.sendMessage("§cUsage: /unfreeze <player>"); return; }
            if (cmd.equals("usage-tempmute")) { player.sendMessage("§cUsage: /tempmute <player> <time> [reason]"); return; }
            if (cmd.equals("usage-broadcast")) { player.sendMessage("§cUsage: /broadcast <text>"); return; }
            if (cmd.equals("usage-sudo")) { player.sendMessage("§cUsage: /sudo <player> <command>"); return; }
            if (cmd.equals("usage-staff")) { player.sendMessage("§cUsage: /staff <player>"); return; }
            if (cmd.equals("usage-unstaff")) { player.sendMessage("§cUsage: /unstaff <player>"); return; }
            if (cmd.equals("usage-owner")) { player.sendMessage("§cUsage: /owner <player>"); return; }
            if (cmd.equals("usage-unowner")) { player.sendMessage("§cUsage: /unowner <player>"); return; }
            if (cmd.equals("usage-warn")) { player.sendMessage("§cUsage: /warn <player> <reason>"); return; }
            if (cmd.equals("unmute")) { player.sendMessage("§cUsage: /unmute <player>"); return; }
            if (cmd.equals("tphere")) { player.sendMessage("§cUsage: /tphere <player>"); return; }
            if (cmd.equals("spawn")) { Bukkit.dispatchCommand(player, "spawn"); return; }
            if (cmd.equals("clearentities")) { Bukkit.dispatchCommand(player, "clearentities"); return; }
            if (cmd.equals("tps")) { Bukkit.dispatchCommand(player, "tps"); return; }
            if (cmd.equals("announce")) { announcementManager.broadcastNow(); player.sendMessage("§aAnnounced."); return; }
            Bukkit.dispatchCommand(player, cmd);
            return;
        }
        if (action.startsWith("pcmd:")) {
            String cmd = action.substring(5);
            player.closeInventory();
            Bukkit.dispatchCommand(player, cmd);
            return;
        }
        if (action.startsWith("info:")) {
            player.sendMessage("§e" + action.substring(5));
            return;
        }
    }

    public boolean isFrozen(java.util.UUID uuid) { return frozen.contains(uuid); }
    public void setFrozen(java.util.UUID uuid, boolean state) {
        if (state) frozen.add(uuid); else frozen.remove(uuid);
    }
}