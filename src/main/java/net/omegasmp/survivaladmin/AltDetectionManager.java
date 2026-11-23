package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Tracks player IPs and alerts staff when multiple accounts share an IP.
 */
public class AltDetectionManager {

    private final AdminMC plugin;
    private final File file;
    // IP -> set of player names
    private final Map<String, Set<String>> ipToPlayers = new HashMap<>();

    public AltDetectionManager(AdminMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ips.json");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            String content = new String(Files.readAllBytes(file.toPath())).trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
                // parse "ip":["name1","name2"]
                // simple: split by top-level commas between ip entries
                int depth = 0;
                StringBuilder entry = new StringBuilder();
                List<String> entries = new ArrayList<>();
                for (char c : content.toCharArray()) {
                    if (c == '[') depth++;
                    if (c == ']') depth--;
                    if (c == ',' && depth == 0) {
                        entries.add(entry.toString());
                        entry.setLength(0);
                    } else {
                        entry.append(c);
                    }
                }
                if (entry.length() > 0) entries.add(entry.toString());
                for (String e : entries) {
                    int colon = e.indexOf(':');
                    if (colon < 0) continue;
                    String ip = e.substring(0, colon).replace("\"", "").trim();
                    String rest = e.substring(colon + 1).trim();
                    Set<String> names = new HashSet<>();
                    rest = rest.replace("[", "").replace("]", "");
                    for (String n : rest.split(",")) {
                        n = n.replace("\"", "").trim();
                        if (!n.isEmpty()) names.add(n);
                    }
                    if (!names.isEmpty()) ipToPlayers.put(ip, names);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load ips.json: " + e.getMessage());
        }
    }

    public void save() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Set<String>> e : ipToPlayers.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":[");
            boolean f2 = true;
            for (String n : e.getValue()) {
                if (!f2) sb.append(",");
                sb.append("\"").append(n.replace("\"", "\\\"")).append("\"");
                f2 = false;
            }
            sb.append("]");
            first = false;
        }
        sb.append("}");
        try {
            File tmp = new File(file.getParent(), "ips.json.tmp");
            Files.write(tmp.toPath(), sb.toString().getBytes());
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save ips.json: " + e.getMessage());
        }
    }

    public void onJoin(Player player) {
        if (!plugin.getConfigManager().getBool("anticheat.alt-detection.enabled", false)) return;
        if (player == null) return;
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        if (ip == null) return;

        Set<String> names = ipToPlayers.computeIfAbsent(ip, k -> new HashSet<>());
        boolean isNew = names.add(player.getName());
        save();

        int threshold = plugin.getConfigManager().getInt("anticheat.alt-detection.threshold", 2);
        if (names.size() >= threshold) {
            String msg = plugin.getConfigManager().msgRaw("alt-detected")
                    .replace("{player}", player.getName())
                    .replace("{ip}", maskIp(ip))
                    .replace("{accounts}", String.join(", ", names));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.getRoleManager().isStaff(p)) {
                    p.sendMessage(plugin.getConfigManager().prefix() + " " + msg);
                }
            }
            plugin.getLogger().warning("Alt detection: " + player.getName() + " from " + maskIp(ip) + " accounts: " + names);
        }
    }

    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        int last = ip.lastIndexOf('.');
        if (last > 0) return ip.substring(0, last) + ".xxx";
        return ip;
    }
}