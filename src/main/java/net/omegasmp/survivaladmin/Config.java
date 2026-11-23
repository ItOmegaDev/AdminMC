package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Config {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private FileConfiguration messages;

    public Config(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadMessages();
    }

    private void loadMessages() {
        File mf = new File(plugin.getDataFolder(), "messages.yml");
        if (!mf.exists()) {
            try { plugin.saveResource("messages.yml", false); } catch (Exception ignored) { }
        }
        if (mf.exists()) {
            messages = YamlConfiguration.loadConfiguration(mf);
        } else {
            messages = config;
        }
    }

    public String get(String path, String def) {
        return config.getString(path, def);
    }

    public List<String> getList(String path) {
        return config.getStringList(path);
    }

    public List<String> getPermissions(String role) {
        return config.getStringList("permissions." + role);
    }

    public boolean hasPermission(String role, String perm) {
        if ("owner".equals(role)) return true;
        List<String> perms = getPermissions(role);
        if (perms.contains("*") || perms.contains("survivaladmin.*")) return true;
        return perms.contains(perm);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public boolean getBool(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public String prefix() {
        String p = config.getString("prefix", "&5OmegaMC");
        return p.replace("&", "§");
    }

    public String msg(String key) {
        String m = "";
        if (messages != null) {
            m = messages.getString(key, "");
        }
        if (m == null || m.isEmpty()) {
            m = config.getString("messages." + key, "");
        }
        if (m == null) m = "";
        String colored = m.replace("&", "§");
        if (colored.isEmpty()) return colored;
        return prefix() + " §7- " + colored;
    }

    public String msgRaw(String key) {
        String m = "";
        if (messages != null) {
            m = messages.getString(key, "");
        }
        if (m == null || m.isEmpty()) {
            m = config.getString("messages." + key, "");
        }
        if (m == null) m = "";
        return m.replace("&", "§");
    }

    /** Send a message to a player only if they are staff+owner. No-op for regular users. */
    public void msgStaff(Player p, String key) {
        if (p == null) return;
        if (!isStaff(p)) return;
        p.sendMessage(msg(key));
    }

    /** Broadcast a plugin message to staff+owner players only. Supports {placeholders}. */
    public void broadcastStaff(String key, Object... placeholders) {
        String message = msg(key);
        if (message.isEmpty()) return;
        if (placeholders.length % 2 != 0) return;
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            if (placeholder.startsWith("{") && placeholder.endsWith("}") && placeholder.length() > 2) {
                placeholder = placeholder.substring(1, placeholder.length() - 1);
            }
            message = message.replace("{" + placeholder + "}", String.valueOf(placeholders[i + 1]));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isStaff(p)) {
                p.sendMessage(message);
            }
        }
    }

    /** Send a raw (no prefix) message to a player only if they are staff+owner. */
    public void msgStaffRaw(Player p, String key) {
        if (p == null) return;
        if (!isStaff(p)) return;
        p.sendMessage(msgRaw(key));
    }

    /** Broadcast a raw (already-colored) plugin message to staff+owner players only. */
    public void broadcastStaffRaw(String message) {
        if (message == null || message.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isStaff(p)) {
                p.sendMessage(message);
            }
        }
    }

    private boolean isStaff(Player p) {
        return ((AdminMC) plugin).getRoleManager().isStaff(p);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save config: " + e.getMessage());
        }
    }

    public FileConfiguration raw() {
        return config;
    }
}