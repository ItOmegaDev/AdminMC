package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla-friendly quest system. Quests are defined in config.yml and tracked
 * per player. Rewards are vanilla items and XP levels - no custom currency.
 * Progress is driven by EventListener (kills, mining, collecting).
 */
public class QuestManager {

    public static class Quest {
        public final String id;
        public final String name;
        public final String type;   // kill | mine | collect | craft
        public final String target; // entity/material name
        public final int amount;
        public final boolean repeatable;
        public final List<String> rewardItems; // "MATERIAL:amount"
        public final int rewardXpLevels;

        public Quest(String id, String name, String type, String target, int amount,
                     boolean repeatable, List<String> rewardItems, int rewardXpLevels) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.repeatable = repeatable;
            this.rewardItems = rewardItems;
            this.rewardXpLevels = rewardXpLevels;
        }
    }

    private final SurvivalAdmin plugin;
    private final List<Quest> quests = new ArrayList<>();
    private final File file;
    private FileConfiguration data;

    // player -> (questId -> progress)
    private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    // player -> set of claimed quest ids
    private final Map<UUID, Set<String>> claimed = new HashMap<>();

    public QuestManager(SurvivalAdmin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        loadQuests();
        loadData();
        // periodic save
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    private void loadQuests() {
        quests.clear();
        if (!plugin.getConfigManager().getBool("quests.enabled", true)) return;
        List<Map<?, ?>> list = plugin.getConfigManager().raw().getMapList("quests.list");
        for (Map<?, ?> m : list) {
            String id = str(m, "id");
            if (id == null || id.isBlank()) continue;
            String name = str(m, "name", id);
            String type = str(m, "type", "kill").toLowerCase();
            String target = str(m, "target", "");
            int amount = intOf(m, "amount", 1);
            boolean repeatable = boolOf(m, "repeatable", false);
            List<String> items = new ArrayList<>();
            Object reward = m.get("reward");
            int xp = 0;
            if (reward instanceof Map<?, ?> rm) {
                Object it = rm.get("items");
                if (it instanceof List<?> l) {
                    for (Object o : l) if (o != null) items.add(String.valueOf(o));
                }
                Object x = rm.get("xp-levels");
                if (x instanceof Number n) xp = n.intValue();
                else if (x != null) {
                    try { xp = Integer.parseInt(String.valueOf(x)); } catch (Exception ignored) { }
                }
            }
            quests.add(new Quest(id, name, type, target.toUpperCase(), amount, repeatable, items, xp));
        }
    }

    private void loadData() {
        if (!file.exists()) {
            data = new YamlConfiguration();
            return;
        }
        data = YamlConfiguration.loadConfiguration(file);
        for (String uuidStr : data.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { continue; }
            ConfigurationSection sec = data.getConfigurationSection(uuidStr);
            if (sec == null) continue;
            Map<String, Integer> p = new HashMap<>();
            ConfigurationSection prog = sec.getConfigurationSection("progress");
            if (prog != null) {
                for (String k : prog.getKeys(false)) p.put(k, prog.getInt(k, 0));
            }
            progress.put(uuid, p);
            Set<String> c = new HashSet<>();
            List<String> cl = sec.getStringList("claimed");
            if (cl != null) c.addAll(cl);
            claimed.put(uuid, c);
        }
    }

    public void save() {
        try {
            if (data == null) data = new YamlConfiguration();
            // clear old
            for (String k : new ArrayList<>(data.getKeys(false))) data.set(k, null);
            for (Map.Entry<UUID, Map<String, Integer>> e : progress.entrySet()) {
                String base = e.getKey().toString();
                for (Map.Entry<String, Integer> pe : e.getValue().entrySet()) {
                    data.set(base + ".progress." + pe.getKey(), pe.getValue());
                }
            }
            for (Map.Entry<UUID, Set<String>> e : claimed.entrySet()) {
                data.set(e.getKey().toString() + ".claimed", new ArrayList<>(e.getValue()));
            }
            data.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save quests: " + ex.getMessage());
        }
    }

    public List<Quest> quests() { return quests; }

    public Quest get(String id) {
        for (Quest q : quests) if (q.id.equalsIgnoreCase(id)) return q;
        return null;
    }

    public int progressOf(Player p, String id) {
        Map<String, Integer> m = progress.get(p.getUniqueId());
        return (m == null) ? 0 : m.getOrDefault(id, 0);
    }

    public boolean isClaimed(Player p, String id) {
        Set<String> c = claimed.get(p.getUniqueId());
        return c != null && c.contains(id);
    }

    /** Called by EventListener when a relevant action happens. */
    public void addProgress(Player p, String type, String target, int amount) {
        if (!plugin.getConfigManager().getBool("quests.enabled", true)) return;
        if (p == null) return;
        target = target.toUpperCase();
        for (Quest q : quests) {
            if (!q.type.equalsIgnoreCase(type)) continue;
            if (!q.target.equalsIgnoreCase(target)) continue;
            if (isClaimed(p, q.id) && !q.repeatable) continue;
            // reset if repeatable and claimed? keep simple: if claimed and repeatable, reset progress on claim already.
            int cur = progressOf(p, q.id);
            if (cur >= q.amount) continue; // already done, waiting claim
            Map<String, Integer> m = progress.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            int neu = Math.min(q.amount, cur + amount);
            m.put(q.id, neu);
            if (neu >= q.amount) {
                p.sendMessage(plugin.getConfigManager().prefix() + " §aКвест виконано: §f" + q.name.replace("&", "§")
                        + " §7(/quests claim " + q.id + ")");
            }
        }
    }

    public void claim(Player p, String id) {
        Quest q = get(id);
        if (q == null) { p.sendMessage(plugin.getConfigManager().msg("player-not-found").replace("{player}", id)); return; }
        if (isClaimed(p, q.id) && !q.repeatable) {
            p.sendMessage(plugin.getConfigManager().prefix() + " §cНагорода вже отримана.");
            return;
        }
        int cur = progressOf(p, q.id);
        if (cur < q.amount) {
            p.sendMessage(plugin.getConfigManager().prefix() + " §cПрогрес: " + cur + "/" + q.amount);
            return;
        }
        // give rewards
        for (String spec : q.rewardItems) {
            ItemStack it = parseItem(spec);
            if (it != null) {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
                for (ItemStack l : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
            }
        }
        if (q.rewardXpLevels > 0) p.giveExpLevels(q.rewardXpLevels);
        // mark claimed
        claimed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(q.id);
        if (q.repeatable) {
            // reset progress
            Map<String, Integer> m = progress.get(p.getUniqueId());
            if (m != null) m.put(q.id, 0);
            claimed.get(p.getUniqueId()).remove(q.id);
        }
        save();
        p.sendMessage(plugin.getConfigManager().prefix() + " §aНагорода отримана: " + q.name.replace("&", "§"));
    }

    public void list(Player p) {
        p.sendMessage(plugin.getConfigManager().prefix() + " §6Квести:");
        if (quests.isEmpty()) { p.sendMessage("§7Немає квестів."); return; }
        for (Quest q : quests) {
            int cur = progressOf(p, q.id);
            String status;
            if (isClaimed(p, q.id) && !q.repeatable) status = "§a[Отримано]";
            else if (cur >= q.amount) status = "§e[Готово до отримання]";
            else status = "§7[" + cur + "/" + q.amount + "]";
            p.sendMessage("§f" + q.id + " §7- " + q.name.replace("&", "§") + " " + status);
        }
        p.sendMessage("§7/quests claim <id>  §7- отримати нагороду");
    }

    private ItemStack parseItem(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String[] parts = spec.split(":");
        Material m;
        try { m = Material.valueOf(parts[0].trim().toUpperCase()); }
        catch (Exception e) { return null; }
        int amt = 1;
        if (parts.length > 1) {
            try { amt = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) { }
        }
        return new ItemStack(m, Math.max(1, amt));
    }

    private static String str(Map<?, ?> m, String key) { return str(m, key, null); }
    private static String str(Map<?, ?> m, String key, String def) {
        Object o = m.get(key);
        return o == null ? def : String.valueOf(o);
    }
    private static int intOf(Map<?, ?> m, String key, int def) {
        Object o = m.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { }
        }
        return def;
    }
    private static boolean boolOf(Map<?, ?> m, String key, boolean def) {
        Object o = m.get(key);
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(String.valueOf(o));
        return def;
    }
}
