package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin GUI panel: /admin opens a menu with every capability of the plugin.
 *
 * Layout: 6 rows, framed border, black filler, centered header, category grid.
 * Each open inventory is tracked with its own action map, and the map is bound
 * to the inventory instance so a late close event of the previous page cannot
 * wipe the state of the page that was just opened.
 */
public class AdminGUI {

    private static final int SIZE = 54;

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material FRAME = Material.GRAY_STAINED_GLASS_PANE;

    private final SurvivalAdmin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        private final String type;
        private final Inventory inventory;
        private final Map<Integer, String> actions;

        private Session(String type, Inventory inventory, Map<Integer, String> actions) {
            this.type = type;
            this.inventory = inventory;
            this.actions = actions;
        }
    }

    public AdminGUI(SurvivalAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpen(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public String currentType(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s == null ? null : s.type;
    }

    /** True when the clicked inventory is the one this session created. */
    public boolean isCurrent(Player p, Inventory inv) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && inv != null && s.inventory.equals(inv);
    }

    public Map<Integer, String> currentActions(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s == null ? new HashMap<>() : s.actions;
    }

    public void close(Player p) {
        sessions.remove(p.getUniqueId());
    }

    // ================================================================
    // MAIN
    // ================================================================
    public void openMain(Player p) {
        Inventory inv = base("SurvivalAdmin");
        Map<Integer, String> act = new HashMap<>();

        item(inv, act, 4, Material.NETHER_STAR, "&d&lSurvivalAdmin",
                "info:SurvivalAdmin v2.4.0 by Omega",
                "&7Панель керування сервером",
                "&8Онлайн: &f" + Bukkit.getOnlinePlayers().size(),
                "&8Ваша роль: &f" + plugin.getRoleManager().getRole(p));

        item(inv, act, 19, Material.IRON_SWORD, "&c&lМодерація",
                "open:moderation",
                "&7Кік, бан, мут, заморозка",
                "&7попередження, ваниш, чат");

        item(inv, act, 21, Material.ENDER_PEARL, "&a&lТелепортація",
                "open:teleport",
                "&7Телепорт до гравця,",
                "&7до себе та на спавн");

        item(inv, act, 23, Material.GRASS_BLOCK, "&2&lСвіт",
                "open:world",
                "&7Час, погода, режим гри",
                "&7та очищення сутностей");

        item(inv, act, 25, Material.PLAYER_HEAD, "&e&lГравці",
                "open:players",
                "&7Список онлайн-гравців",
                "&7та дії з ними");

        item(inv, act, 28, Material.SHIELD, "&b&lАнти-чіт",
                "open:anticheat",
                "&7Перевірка на чити,",
                "&7статус модулів захисту");

        item(inv, act, 30, Material.BOOK, "&6&lЧат",
                "open:chat",
                "&7Очищення чату, оголошення",
                "&7та фільтри");

        item(inv, act, 32, Material.REDSTONE, "&d&lСервер",
                "open:server",
                "&7TPS, пам'ять, сутності",
                "&7та авто-оголошення");

        if (plugin.getRoleManager().isOwner(p)) {
            item(inv, act, 34, Material.NAME_TAG, "&b&lРолі",
                    "open:roles",
                    "&7Видача та зняття ролей",
                    "&7staff, owner");
        } else {
            item(inv, act, 34, Material.NAME_TAG, "&8&lРолі",
                    "roles",
                    "&7Доступно лише власнику");
        }

        item(inv, act, 49, Material.BARRIER, "&c&lЗакрити", "close",
                "&7Закрити меню");

        show(p, "main", inv, act);
    }

    // ================================================================
    // MODERATION
    // ================================================================
    public void openModeration(Player p) {
        Inventory inv = base("Moderation");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.IRON_SWORD, "&c&lМодерація", "&7Оберіть дію");

        item(inv, act, 19, Material.IRON_AXE, "&c&lКік", "cmd:usage-kick",
                "&7Вигнати гравця з сервера", "&8/kick <гравець> [причина]");
        item(inv, act, 20, Material.IRON_BARS, "&c&lБан", "cmd:usage-getoff",
                "&7Забанити на час", "&8/getoff <гравець> <час> [причина]");
        item(inv, act, 21, Material.PAPER, "&e&lМут", "cmd:usage-mute",
                "&7Замутити гравця", "&8/mute <гравець> [час] [причина]");
        item(inv, act, 22, Material.CLOCK, "&e&lТимчасовий мут", "cmd:usage-tempmute",
                "&7Мут на заданий час", "&8/tempmute <гравець> <час> [причина]");
        item(inv, act, 23, Material.SPONGE, "&a&lЗняти мут", "cmd:usage-unmute",
                "&7Зняти мут з гравця", "&8/unmute <гравець>");
        item(inv, act, 24, Material.ICE, "&b&lЗаморозити", "cmd:usage-freeze",
                "&7Заблокувати рух гравця", "&8/freeze <гравець>");
        item(inv, act, 25, Material.PACKED_ICE, "&b&lРозморозити", "cmd:usage-unfreeze",
                "&7Зняти заморозку", "&8/unfreeze <гравець>");

        item(inv, act, 28, Material.BELL, "&e&lПопередження", "cmd:usage-warn",
                "&7Видати попередження", "&8/warn <гравець> <причина>");
        item(inv, act, 29, Material.LAVA_BUCKET, "&c&lОчистити чат", "cmd:clearchat",
                "&7Прибрати повідомлення з чату");
        item(inv, act, 30, Material.OAK_SIGN, "&6&lОголошення", "cmd:usage-broadcast",
                "&7Написати від імені сервера", "&8/broadcast <текст>");
        item(inv, act, 31, Material.SHIELD, "&b&lВаниш", "cmd:vanish",
                "&7Увімкнути або вимкнути невидимість",
                "&7та приховування зі списків",
                "&8Статус: " + onOff(plugin.getVanishManager().isVanished(p)));
        item(inv, act, 32, Material.COMMAND_BLOCK, "&4&lSudo", "cmd:usage-sudo",
                "&7Виконати команду від імені гравця", "&8/sudo <гравець> <команда>");
        item(inv, act, 33, Material.PLAYER_HEAD, "&e&lГравці", "open:players",
                "&7Відкрити список гравців");

        footer(p, inv, act, "main");
        show(p, "moderation", inv, act);
    }

    // ================================================================
    // TELEPORT
    // ================================================================
    public void openTeleport(Player p) {
        Inventory inv = base("Teleport");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.ENDER_PEARL, "&a&lТелепортація", "&7Оберіть дію");

        item(inv, act, 20, Material.ENDER_PEARL, "&a&lДо гравця", "cmd:usage-tp",
                "&7Телепортуватися до гравця", "&8/tp <гравець>");
        item(inv, act, 22, Material.SPECTRAL_ARROW, "&e&lГравець до мене", "cmd:usage-tphere",
                "&7Телепортувати гравця до вас", "&8/tphere <гравець>");
        item(inv, act, 24, Material.COMPASS, "&b&lСпавн", "cmd:spawn",
                "&7Телепортуватися на спавн");

        item(inv, act, 30, Material.PLAYER_HEAD, "&e&lСписок гравців", "open:players",
                "&7Обрати гравця зі списку");

        footer(p, inv, act, "main");
        show(p, "teleport", inv, act);
    }

    // ================================================================
    // WORLD
    // ================================================================
    public void openWorld(Player p) {
        Inventory inv = base("World");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.GRASS_BLOCK, "&2&lСвіт", "&7Час, погода, режим гри");

        item(inv, act, 19, Material.SUNFLOWER, "&e&lДень", "cmd:time day",
                "&7Встановити день");
        item(inv, act, 20, Material.GLOWSTONE, "&e&lПолудень", "cmd:time noon",
                "&7Встановити полудень");
        item(inv, act, 21, Material.CLOCK, "&8&lНіч", "cmd:time night",
                "&7Встановити ніч");
        item(inv, act, 22, Material.COAL, "&8&lОпівночі", "cmd:time midnight",
                "&7Встановити опівночі");
        item(inv, act, 24, Material.REDSTONE, "&c&lОчистити сутності", "cmd:clearentities",
                "&7Видалити дроп та мобів",
                "&7поблизу вас");

        item(inv, act, 28, Material.SUNFLOWER, "&e&lЯсно", "cmd:weather clear",
                "&7Ясна погода");
        item(inv, act, 29, Material.WATER_BUCKET, "&b&lДощ", "cmd:weather rain",
                "&7Дощ");
        item(inv, act, 30, Material.LIGHTNING_ROD, "&d&lГроза", "cmd:weather thunder",
                "&7Гроза");

        item(inv, act, 37, Material.GRASS_BLOCK, "&a&lВиживання", "cmd:gm 0",
                "&7Режим виживання");
        item(inv, act, 39, Material.DIAMOND_PICKAXE, "&c&lТворчий", "cmd:gm 1",
                "&7Творчий режим");
        item(inv, act, 41, Material.MAP, "&6&lПригоди", "cmd:gm 2",
                "&7Режим пригод");
        item(inv, act, 43, Material.ENDER_EYE, "&5&lСпостерігач", "cmd:gm 3",
                "&7Режим спостерігача");

        footer(p, inv, act, "main");
        show(p, "world", inv, act);
    }

    // ================================================================
    // PLAYERS
    // ================================================================
    public void openPlayers(Player p) {
        Inventory inv = base("Players");
        Map<Integer, String> act = new HashMap<>();

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        header(inv, act, Material.PLAYER_HEAD, "&e&lГравці",
                "&7Онлайн: &f" + online.size(),
                "&7Оберіть гравця для дій");

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        int index = 0;
        for (Player target : online) {
            if (index >= slots.length) break;
            addHead(inv, act, slots[index], target, "select:" + target.getName());
            index++;
        }
        if (online.isEmpty()) {
            item(inv, act, 22, Material.COBWEB, "&7Порожньо", "info:Немає гравців онлайн");
        }

        footer(p, inv, act, "main");
        show(p, "players", inv, act);
    }

    // ================================================================
    // PLAYER ACTIONS
    // ================================================================
    public void openPlayerActions(Player p, String targetName) {
        String safeName = targetName == null ? "?" : targetName.replaceAll("[^A-Za-z0-9_]", "");
        if (safeName.isEmpty()) safeName = "?";

        Inventory inv = base("Actions: " + safeName);
        Map<Integer, String> act = new HashMap<>();

        Player target = Bukkit.getPlayerExact(safeName);
        if (target != null) {
            addHead(inv, act, 4, target, "info:Дії з " + safeName);
        } else {
            item(inv, act, 4, Material.PLAYER_HEAD, "&e&l" + safeName, "info:Гравця не знайдено");
        }

        item(inv, act, 19, Material.ENDER_PEARL, "&a&lДо гравця", "pcmd:tp " + safeName,
                "&7Телепортуватися до гравця");
        item(inv, act, 20, Material.CHEST, "&e&lІнвентар", "pcmd:invsee " + safeName,
                "&7Переглянути інвентар");
        item(inv, act, 21, Material.ENDER_CHEST, "&5&lЕндер-скриня", "pcmd:endersee " + safeName,
                "&7Переглянути ендер-скриню");
        item(inv, act, 22, Material.IRON_AXE, "&c&lКік", "pcmd:kick " + safeName,
                "&7Вигнати гравця");
        item(inv, act, 23, Material.IRON_BARS, "&c&lБан на 1 день", "pcmd:getoff " + safeName + " 1d",
                "&7Забанити на 1 день");
        item(inv, act, 24, Material.PAPER, "&e&lМут 10 хв", "pcmd:tempmute " + safeName + " 10m",
                "&7Замутити на 10 хвилин");
        item(inv, act, 25, Material.ICE, "&b&lЗаморозити", "pcmd:freeze " + safeName,
                "&7Заблокувати рух гравця");

        item(inv, act, 28, Material.PACKED_ICE, "&b&lРозморозити", "pcmd:unfreeze " + safeName,
                "&7Зняти заморозку");
        item(inv, act, 29, Material.SPONGE, "&a&lЗняти мут", "pcmd:unmute " + safeName,
                "&7Зняти мут");
        item(inv, act, 30, Material.BELL, "&e&lПопередження", "pcmd:warn " + safeName,
                "&8/warn " + safeName + " <причина>");
        item(inv, act, 31, Material.SHIELD, "&b&lПеревірити на чити", "pcmd:check " + safeName,
                "&7Перевірка модів та поведінки");

        item(inv, act, 45, Material.ARROW, "&7&lНазад", "open:players",
                "&7До списку гравців");
        item(inv, act, 49, Material.BARRIER, "&c&lЗакрити", "close",
                "&7Закрити меню");

        show(p, "playeractions", inv, act);
    }

    // ================================================================
    // ANTI-CHEAT
    // ================================================================
    public void openAnticheat(Player p) {
        Inventory inv = base("Anti-Cheat");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.SHIELD, "&b&lАнти-чіт", "&7Перевірка та модулі захисту");

        item(inv, act, 20, Material.SHIELD, "&b&lПеревірити гравця", "info:/check <гравець>",
                "&7Перевірка модів та поведінки",
                "&8Результат бачать лише staff");
        item(inv, act, 22, Material.OAK_SIGN, "&e&lПеревірка табличкою",
                "info:Sign-check: " + status(isOn("anticheat.sign-check.enabled", true)),
                "&7Виявляє моди через",
                "&7редактор таблички",
                "&8Статус: " + onOff(isOn("anticheat.sign-check.enabled", true)));
        item(inv, act, 24, Material.CLOCK, "&e&lАвтоперевірка при вході",
                "info:Auto-check-on-join: " + status(isOn("anticheat.auto-check-on-join.enabled", true)),
                "&7Перевірка поведінки",
                "&7після входу гравця",
                "&8Статус: " + onOff(isOn("anticheat.auto-check-on-join.enabled", true)));

        item(inv, act, 30, Material.SPYGLASS, "&d&lДетекція альтів",
                "info:Alt-detection: " + status(isOn("anticheat.alt-detection.enabled", true)),
                "&7Повідомляє staff про",
                "&7кілька акаунтів з однієї IP",
                "&8Статус: " + onOff(isOn("anticheat.alt-detection.enabled", true)));
        item(inv, act, 32, Material.PLAYER_HEAD, "&e&lОбрати гравця", "open:players",
                "&7Відкрити список гравців");

        footer(p, inv, act, "main");
        show(p, "anticheat", inv, act);
    }

    // ================================================================
    // CHAT
    // ================================================================
    public void openChat(Player p) {
        Inventory inv = base("Chat");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.BOOK, "&6&lЧат", "&7Керування чатом та фільтрами");

        item(inv, act, 20, Material.LAVA_BUCKET, "&c&lОчистити чат", "cmd:clearchat",
                "&7Прибрати повідомлення з чату");
        item(inv, act, 22, Material.OAK_SIGN, "&6&lОголошення", "cmd:usage-broadcast",
                "&7Написати від імені сервера", "&8/broadcast <текст>");
        item(inv, act, 24, Material.ANVIL, "&e&lАвто-оголошення", "cmd:announce",
                "&7Надіслати наступне",
                "&7авто-оголошення зараз",
                "&8Статус: " + onOff(isOn("announcements.enabled", true)));

        item(inv, act, 30, Material.HOPPER, "&e&lАнтиспам",
                "info:Antispam: " + status(isOn("chat.antispam.enabled", true)),
                "&7Затримка між повідомленнями",
                "&8Статус: " + onOff(isOn("chat.antispam.enabled", true)));
        item(inv, act, 32, Material.NAME_TAG, "&e&lФільтр слів",
                "info:Word-filter: " + status(isOn("chat.word-filter.enabled", true)),
                "&7Заміна заборонених слів",
                "&8Статус: " + onOff(isOn("chat.word-filter.enabled", true)));

        footer(p, inv, act, "main");
        show(p, "chat", inv, act);
    }

    // ================================================================
    // SERVER
    // ================================================================
    public void openServer(Player p) {
        Inventory inv = base("Server");
        Map<Integer, String> act = new HashMap<>();

        double[] tps = Bukkit.getServer().getTPS();
        double tps1 = Math.min(20.0, tps[0]);
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long maxMb = rt.maxMemory() / 1048576L;

        header(inv, act, Material.REDSTONE, "&d&lСервер", "&7Стан сервера в реальному часі");

        item(inv, act, 19, Material.CLOCK, "&e&lTPS",
                "info:TPS: " + String.format("%.1f", tps1),
                "&7Поточний TPS: &f" + String.format("%.1f", tps1),
                "&8Норма: 20.0");
        item(inv, act, 21, Material.REDSTONE_BLOCK, "&c&lПам'ять",
                "info:Memory: " + usedMb + " / " + maxMb + " MB",
                "&7Використано: &f" + usedMb + " MB",
                "&7Максимум: &f" + maxMb + " MB");
        item(inv, act, 23, Material.GRASS_BLOCK, "&a&lСутності",
                "info:Entities: " + p.getWorld().getEntities().size(),
                "&7У світі &f" + p.getWorld().getName() + "&7: &f"
                        + p.getWorld().getEntities().size(),
                "&7Гравців онлайн: &f" + Bukkit.getOnlinePlayers().size());
        item(inv, act, 25, Material.ANVIL, "&6&lАвто-оголошення", "cmd:announce",
                "&7Надіслати наступне",
                "&7авто-оголошення зараз");

        item(inv, act, 30, Material.REDSTONE, "&c&lОчистити сутності", "cmd:clearentities",
                "&7Видалити дроп та мобів поблизу");
        item(inv, act, 32, Material.PLAYER_HEAD, "&e&lГравці", "open:players",
                "&7Список онлайн-гравців");

        footer(p, inv, act, "main");
        show(p, "server", inv, act);
    }

    // ================================================================
    // ROLES (owner only)
    // ================================================================
    public void openRoles(Player p) {
        Inventory inv = base("Roles");
        Map<Integer, String> act = new HashMap<>();
        header(inv, act, Material.NAME_TAG, "&b&lРолі", "&7Лише для власника");

        if (!plugin.getRoleManager().isOwner(p)) {
            item(inv, act, 22, Material.BARRIER, "&c&lНемає доступу", "roles",
                    "&7Керування ролями",
                    "&7доступне лише власнику");
            footer(p, inv, act, "main");
            show(p, "roles", inv, act);
            return;
        }

        item(inv, act, 20, Material.NAME_TAG, "&a&lВидати Staff", "cmd:usage-staff",
                "&7Надати роль staff", "&8/staff <гравець>");
        item(inv, act, 22, Material.NAME_TAG, "&c&lЗняти Staff", "cmd:usage-unstaff",
                "&7Забрати роль staff", "&8/unstaff <гравець>");
        item(inv, act, 24, Material.NETHER_STAR, "&5&lВидати Owner", "cmd:usage-owner",
                "&7Надати роль owner", "&8/owner <гравець>");
        item(inv, act, 26, Material.BARRIER, "&4&lЗняти Owner", "cmd:usage-unowner",
                "&7Забрати роль owner", "&8/unowner <гравець>");

        footer(p, inv, act, "main");
        show(p, "roles", inv, act);
    }

    public void handleSelect(Player p, String action) {
        if (action.startsWith("select:")) {
            String name = action.substring("select:".length());
            openPlayerActions(p, name);
        }
    }

    // ================================================================
    // helpers
    // ================================================================
    private Inventory base(String title) {
        Inventory inv = Bukkit.createInventory(null, SIZE,
                ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + title);
        fill(inv);
        return inv;
    }

    private void header(Inventory inv, Map<Integer, String> act, Material mat, String name, String... lore) {
        item(inv, act, 4, mat, name, "info:" + ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', name)), lore);
    }

    private void footer(Player p, Inventory inv, Map<Integer, String> act, String backTarget) {
        item(inv, act, 45, Material.ARROW, "&7&lНазад", "open:" + backTarget,
                "&7Повернутися назад");
        item(inv, act, 49, Material.BARRIER, "&c&lЗакрити", "close",
                "&7Закрити меню");
    }

    private boolean isOn(String path, boolean def) {
        return plugin.getConfigManager().getBool(path, def);
    }

    private String onOff(boolean value) {
        return value ? "&aON" : "&cOFF";
    }

    private String status(boolean value) {
        return value ? "ON" : "OFF";
    }

    private void show(Player p, String type, Inventory inv, Map<Integer, String> act) {
        sessions.put(p.getUniqueId(), new Session(type, inv, act));
        p.openInventory(inv);
    }

    /** Fill the whole inventory with the dark filler and a light border. */
    private void fill(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane(FILLER));
        }
        for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44,
                45, 46, 47, 48, 50, 51, 52, 53}) {
            inv.setItem(slot, pane(FRAME));
        }
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addHead(Inventory inv, Map<Integer, String> act, int slot, Player target, String action) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            skull.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Клікніть, щоб відкрити");
            lore.add(ChatColor.GRAY + "дії з цим гравцем");
            skull.setLore(lore);
            head.setItemMeta(skull);
        }
        inv.setItem(slot, head);
        act.put(slot, action);
    }

    private void item(Inventory inv, Map<Integer, String> act, int slot, Material mat, String name,
                      String action, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
        act.put(slot, action);
    }
}
