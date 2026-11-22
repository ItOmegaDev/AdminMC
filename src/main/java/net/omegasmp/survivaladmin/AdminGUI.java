package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Admin GUI panel: /admin opens a menu with all capabilities.
 */
public class AdminGUI {

    private final SurvivalAdmin plugin;

    // player UUID -> current gui type
    private final Map<UUID, String> currentGui = new HashMap<>();
    // player UUID -> slot->action map for current gui
    private final Map<UUID, Map<Integer, String>> actions = new HashMap<>();
    // player UUID -> selected target player name (for player action menus)
    private final Map<UUID, String> selectedTarget = new HashMap<>();

    public AdminGUI(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpen(Player p) {
        return currentGui.containsKey(p.getUniqueId());
    }

    public String currentType(Player p) {
        return currentGui.get(p.getUniqueId());
    }

    public Map<Integer, String> currentActions(Player p) {
        return actions.getOrDefault(p.getUniqueId(), new HashMap<>());
    }

    public void close(Player p) {
        currentGui.remove(p.getUniqueId());
        actions.remove(p.getUniqueId());
    }

    // ---- Main menu ----
    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "SurvivalAdmin");
        Map<Integer, String> act = new HashMap<>();

        addItem(inv, act, 10, Material.IRON_SWORD, "&cModeration", "open:moderation");
        addItem(inv, act, 12, Material.ENDER_PEARL, "&aTeleport", "open:teleport");
        addItem(inv, act, 14, Material.GRASS_BLOCK, "&2World", "open:world");
        addItem(inv, act, 16, Material.PLAYER_HEAD, "&ePlayers", "open:players");
        addItem(inv, act, 28, Material.SHIELD, "&bAnti-Cheat", "open:anticheat");
        addItem(inv, act, 30, Material.BOOK, "&6Chat", "open:chat");
        addItem(inv, act, 32, Material.REDSTONE, "&dServer", "open:server");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");

        register(p, "main", inv, act);
        p.openInventory(inv);
    }

    // ---- Moderation ----
    public void openModeration(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Moderation");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.IRON_AXE, "&cKick", "cmd:usage-kick");
        addItem(inv, act, 11, Material.BARRIER, "&cBan (getoff)", "cmd:usage-getoff");
        addItem(inv, act, 12, Material.PAPER, "&eMute", "cmd:usage-mute");
        addItem(inv, act, 13, Material.ICE, "&bFreeze", "cmd:usage-freeze");
        addItem(inv, act, 14, Material.YELLOW_WOOL, "&eWarn", "cmd:usage-warn");
        addItem(inv, act, 15, Material.SPONGE, "&aUnmute", "cmd:unmute");
        addItem(inv, act, 16, Material.LAVA_BUCKET, "&cClearchat", "cmd:clearchat");
        addItem(inv, act, 19, Material.BELL, "&6Broadcast", "cmd:broadcast");
        addItem(inv, act, 20, Material.SHIELD, "&bVanish", "cmd:vanish");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "moderation", inv, act);
        p.openInventory(inv);
    }

    // ---- Teleport ----
    public void openTeleport(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Teleport");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.ENDER_PEARL, "&aTP to player", "cmd:tp");
        addItem(inv, act, 11, Material.SPECTRAL_ARROW, "&eTP Here", "cmd:tphere");
        addItem(inv, act, 12, Material.COMPASS, "&bSpawn", "cmd:spawn");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "teleport", inv, act);
        p.openInventory(inv);
    }

    // ---- World ----
    public void openWorld(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "World");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.CLOCK, "&eTime: Day", "cmd:time day");
        addItem(inv, act, 11, Material.CLOCK, "&8Time: Night", "cmd:time night");
        addItem(inv, act, 12, Material.CLOCK, "&eTime: Noon", "cmd:time noon");
        addItem(inv, act, 13, Material.SUNFLOWER, "&eWeather: Clear", "cmd:weather clear");
        addItem(inv, act, 14, Material.WATER_BUCKET, "&bWeather: Rain", "cmd:weather rain");
        addItem(inv, act, 15, Material.LIGHTNING_ROD, "&dWeather: Thunder", "cmd:weather thunder");
        addItem(inv, act, 16, Material.GRASS_BLOCK, "&aGamemode: Survival", "cmd:gm 0");
        addItem(inv, act, 17, Material.GRASS_BLOCK, "&cGamemode: Creative", "cmd:gm 1");
        addItem(inv, act, 19, Material.BLAZE_POWDER, "&6Gamemode: Adventure", "cmd:gm 2");
        addItem(inv, act, 20, Material.ENDER_EYE, "&5Gamemode: Spectator", "cmd:gm 3");
        addItem(inv, act, 21, Material.REDSTONE, "&cClearentities", "cmd:clearentities");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "world", inv, act);
        p.openInventory(inv);
    }

    // ---- Players list ----
    public void openPlayers(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Players");
        Map<Integer, String> act = new HashMap<>();
        int slot = 0;
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            addItem(inv, act, slot, Material.PLAYER_HEAD, "&e" + pl.getName(), "select:" + pl.getName());
            slot++;
        }
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "players", inv, act);
        p.openInventory(inv);
    }

    // ---- Player action menu ----
    public void openPlayerActions(Player p, String targetName) {
        selectedTarget.put(p.getUniqueId(), targetName);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Actions: " + targetName);
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.ENDER_PEARL, "&aTP to", "pcmd:tp " + targetName);
        addItem(inv, act, 11, Material.CHEST, "&eInvsee", "pcmd:invsee " + targetName);
        addItem(inv, act, 12, Material.ENDER_CHEST, "&5Endersee", "pcmd:endersee " + targetName);
        addItem(inv, act, 13, Material.IRON_AXE, "&cKick", "pcmd:kick " + targetName);
        addItem(inv, act, 14, Material.BARRIER, "&cBan", "pcmd:getoff " + targetName + " 1d");
        addItem(inv, act, 15, Material.PAPER, "&eMute", "pcmd:mute " + targetName);
        addItem(inv, act, 16, Material.ICE, "&bFreeze", "pcmd:freeze " + targetName);
        addItem(inv, act, 17, Material.YELLOW_WOOL, "&eWarn", "pcmd:warn " + targetName);
        addItem(inv, act, 19, Material.SHIELD, "&aCheck hacks", "pcmd:check " + targetName);
        addItem(inv, act, 20, Material.FEATHER, "&aUnfreeze", "pcmd:unfreeze " + targetName);
        addItem(inv, act, 21, Material.PAPER, "&aUnmute", "pcmd:unmute " + targetName);
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:players");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "playeractions", inv, act);
        p.openInventory(inv);
    }

    // ---- Anti-cheat ----
    public void openAnticheat(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Anti-Cheat");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.SHIELD, "&bCheck (self?)", "info:use /check <player>");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "anticheat", inv, act);
        p.openInventory(inv);
    }

    // ---- Chat ----
    public void openChat(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Chat");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.LAVA_BUCKET, "&cClearchat", "cmd:clearchat");
        addItem(inv, act, 11, Material.BELL, "&6Broadcast", "cmd:broadcast");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "chat", inv, act);
        p.openInventory(inv);
    }

    // ---- Server ----
    public void openServer(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Server");
        Map<Integer, String> act = new HashMap<>();
        addItem(inv, act, 10, Material.CLOCK, "&eTPS", "cmd:tps");
        addItem(inv, act, 11, Material.REDSTONE, "&cClearentities", "cmd:clearentities");
        addItem(inv, act, 12, Material.ANVIL, "&6Announce now", "cmd:announce");
        addItem(inv, act, 45, Material.ARROW, "&7Back", "open:main");
        addItem(inv, act, 49, Material.BARRIER, "&cClose", "close");
        register(p, "server", inv, act);
        p.openInventory(inv);
    }

    public void handleSelect(Player p, String action) {
        if (action.startsWith("select:")) {
            String name = action.substring("select:".length());
            openPlayerActions(p, name);
        }
    }

    private void register(Player p, String type, Inventory inv, Map<Integer, String> act) {
        currentGui.put(p.getUniqueId(), type);
        actions.put(p.getUniqueId(), act);
    }

    private void addItem(Inventory inv, Map<Integer, String> act, int slot, Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
        act.put(slot, action);
    }
}