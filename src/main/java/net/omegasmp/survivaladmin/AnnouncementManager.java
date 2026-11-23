package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;

public class AnnouncementManager {

    private final AdminMC plugin;
    private int index = 0;
    private int taskId = -1;

    public AnnouncementManager(AdminMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().getBool("announcements.enabled", false)) return;
        int intervalMin = plugin.getConfigManager().getInt("announcements.interval-minutes", 5);
        if (intervalMin < 1) intervalMin = 5;
        long ticks = intervalMin * 60L * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::broadcastNext, ticks, ticks);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void broadcastNext() {
        java.util.List<String> msgs = plugin.getConfigManager().getList("announcements.messages");
        if (msgs == null || msgs.isEmpty()) return;
        String msg = msgs.get(index % msgs.size());
        index++;
        String prefix = plugin.getConfigManager().get("announcements.prefix", "&6[Announcement] ");
        String message = prefix.replace("&", "§") + msg.replace("&", "§");
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getRoleManager().isStaff(p)) {
                p.sendMessage(message);
            }
        }
    }

    public void broadcastNow() {
        broadcastNext();
    }
}