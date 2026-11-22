package net.omegasmp.survivaladmin;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat filtering: anti-spam (cooldown, duplicate, caps) + word filter.
 */
public class ChatManager {

    private final SurvivalAdmin plugin;

    private static class PlayerChatState {
        long lastTime;
        String lastMessage;
    }

    private final Map<UUID, PlayerChatState> states = new ConcurrentHashMap<>();

    public ChatManager(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public static class FilterResult {
        public boolean blocked;
        public String message;
        public String reason;
    }

    /**
     * Filter a chat message. Returns result with blocked flag and possibly modified message.
     */
    public FilterResult filter(Player player, String message) {
        FilterResult r = new FilterResult();
        r.message = message;
        r.blocked = false;

        if (!plugin.getConfigManager().getBool("chat.antispam.enabled", false)
                && !plugin.getConfigManager().getBool("chat.word-filter.enabled", false)) {
            return r;
        }

        // Anti-spam
        if (plugin.getConfigManager().getBool("chat.antispam.enabled", false)) {
            PlayerChatState st = states.computeIfAbsent(player.getUniqueId(), k -> new PlayerChatState());
            long now = System.currentTimeMillis();
            int cooldown = plugin.getConfigManager().getInt("chat.antispam.cooldown-seconds", 2);
            if (cooldown > 0 && st.lastTime > 0 && (now - st.lastTime) < cooldown * 1000L) {
                r.blocked = true;
                r.reason = "spam-cooldown";
                return r;
            }
            if (plugin.getConfigManager().getBool("chat.antispam.block-duplicates", true)) {
                if (st.lastMessage != null && st.lastMessage.equalsIgnoreCase(message)) {
                    r.blocked = true;
                    r.reason = "spam-duplicate";
                    return r;
                }
            }
            int maxCaps = plugin.getConfigManager().getInt("chat.antispam.max-caps-percent", 70);
            if (maxCaps > 0 && message.length() >= 4) {
                int caps = 0;
                for (char c : message.toCharArray()) if (Character.isUpperCase(c)) caps++;
                int letters = 0;
                for (char c : message.toCharArray()) if (Character.isLetter(c)) letters++;
                if (letters > 0 && (caps * 100 / letters) > maxCaps) {
                    r.blocked = true;
                    r.reason = "spam-caps";
                    return r;
                }
            }
            st.lastTime = now;
            st.lastMessage = message;
        }

        // Word filter
        if (plugin.getConfigManager().getBool("chat.word-filter.enabled", false)) {
            String action = plugin.getConfigManager().get("chat.word-filter.action", "replace");
            String replacement = plugin.getConfigManager().get("chat.word-filter.replacement", "***");
            List<String> words = plugin.getConfigManager().getList("chat.word-filter.words");
            if (words != null && !words.isEmpty()) {
                String lower = message.toLowerCase();
                for (String w : words) {
                    if (w == null || w.trim().isEmpty()) continue;
                    if (lower.contains(w.toLowerCase())) {
                        if ("block".equalsIgnoreCase(action)) {
                            r.blocked = true;
                            r.reason = "word-filter";
                            return r;
                        } else {
                            // replace (case-insensitive simple)
                            message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(w), replacement);
                            r.message = message;
                        }
                    }
                }
            }
        }

        return r;
    }
}