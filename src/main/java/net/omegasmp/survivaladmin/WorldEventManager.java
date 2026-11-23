package net.omegasmp.survivaladmin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla-friendly world events: blood moon (buffed mobs, double drops) and
 * world boss (scheduled buffed mob with boss bar and rewards). No resource pack.
 */
public class WorldEventManager implements Listener {

    private final AdminMC plugin;
    private final Map<UUID, Double> bossDamage = new ConcurrentHashMap<>();

    private boolean bloodMoon = false;
    private BossBar bloodMoonBar;
    private long bloodMoonStartedDay = -1;
    private LivingEntity worldBoss;
    private BossBar bossBar;
    private long nextBossAt = 0L;
    private long bossSpawnedAt = 0L;

    public WorldEventManager(AdminMC plugin) {
        this.plugin = plugin;
        scheduleTick();
        scheduleBossTimer();
    }

    private void scheduleTick() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    private void scheduleBossTimer() {
        int interval = plugin.getConfigManager().getInt("events.world-boss.interval-minutes", 120);
        nextBossAt = System.currentTimeMillis() + interval * 60_000L;
    }

    // ----------------------------------------------------------------
    // Tick
    // ----------------------------------------------------------------
    private void tick() {
        if (!plugin.getConfigManager().getBool("events.blood-moon.enabled", true)) {
            if (bloodMoon) stopBloodMoon();
        } else {
            tickBloodMoon();
        }
        tickWorldBoss();
    }

    private void tickBloodMoon() {
        World world = Bukkit.getWorld("world");
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return;
        long time = world.getTime();
        boolean night = time >= 13000 && time < 23000;

        if (!bloodMoon && night && time >= 13000 && time <= 13200) {
            // decide
            if (Bukkit.getOnlinePlayers().size() < plugin.getConfigManager().getInt("events.blood-moon.min-players", 1))
                return;
            if (plugin.getConfigManager().getBool("events.blood-moon.only-full-moon", false)) {
                try {
                    Object phase = world.getClass().getMethod("getMoonPhase").invoke(world);
                    if (phase != null && !phase.toString().equalsIgnoreCase("FULL")) return;
                } catch (Throwable ignored) { }
            }
            double chance = plugin.getConfigManager().getDouble("events.blood-moon.chance-per-night", 0.25);
            if (Math.random() > chance) return;
            if (bloodMoonStartedDay == world.getFullTime() / 24000) return;
            startBloodMoon(world);
        }

        if (bloodMoon) {
            if (bloodMoonBar != null) {
                double prog = Math.max(0.0, Math.min(1.0, (23000.0 - time) / 10000.0));
                bloodMoonBar.setProgress(prog);
                bloodMoonBar.setTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "КРИВАВИЙ МІСЯЦЬ");
            }
            if (!night) stopBloodMoon();
        }
    }

    private void startBloodMoon(World world) {
        bloodMoon = true;
        bloodMoonStartedDay = world.getFullTime() / 24000;
        String msg = plugin.getConfigManager().msgRaw("bloodmoon-start");
        if (msg == null || msg.isEmpty()) msg = "&c&lКривавий місяць зійшов! Моби посилені, дроп подвоєно.";
        plugin.getConfigManager().broadcastStaffRaw(msg.replace("&", "§"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§4§lКРИВАВИЙ МІСЯЦЬ", "§7Моби посилені, дроп подвоєно", 10, 60, 10);
        }
        bloodMoonBar = Bukkit.createBossBar(ChatColor.DARK_RED + "" + ChatColor.BOLD + "КРИВАВИЙ МІСЯЦЬ",
                BarColor.RED, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) bloodMoonBar.addPlayer(p);
    }

    public void stopBloodMoon() {
        if (!bloodMoon) return;
        bloodMoon = false;
        String msg = plugin.getConfigManager().msgRaw("bloodmoon-end");
        if (msg == null || msg.isEmpty()) msg = "&aКривавий місяць завершився.";
        plugin.getConfigManager().broadcastStaffRaw(msg.replace("&", "§"));
        if (bloodMoonBar != null) {
            bloodMoonBar.removeAll();
            bloodMoonBar = null;
        }
    }

    public boolean isBloodMoon() { return bloodMoon; }

    // ----------------------------------------------------------------
    // World boss
    // ----------------------------------------------------------------
    private void tickWorldBoss() {
        if (!plugin.getConfigManager().getBool("events.world-boss.enabled", true)) return;
        if (worldBoss != null && worldBoss.isValid()) {
            // timeout
            long timeout = plugin.getConfigManager().getInt("events.world-boss.timeout-minutes", 10) * 60_000L;
            if (System.currentTimeMillis() - bossSpawnedAt > timeout) {
                removeBoss(false);
            }
            if (bossBar != null) {
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, worldBoss.getHealth() / worldBoss.getMaxHealth())));
            }
            return;
        }
        if (worldBoss != null) removeBoss(false);
        if (System.currentTimeMillis() < nextBossAt) return;
        if (Bukkit.getOnlinePlayers().size() < plugin.getConfigManager().getInt("events.world-boss.min-players", 1))
            return;
        spawnBoss();
        int interval = plugin.getConfigManager().getInt("events.world-boss.interval-minutes", 120);
        nextBossAt = System.currentTimeMillis() + interval * 60_000L;
    }

    public void spawnBoss() {
        if (worldBoss != null && worldBoss.isValid()) return;
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        // pick a random online player
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;
        Player anchor = online.get(new Random().nextInt(online.size()));
        Location loc = anchor.getLocation().clone();
        // offset
        double angle = Math.random() * Math.PI * 2;
        int radius = plugin.getConfigManager().getInt("events.world-boss.spawn-radius", 20);
        int dx = (int) (Math.cos(angle) * (8 + Math.random() * radius));
        int dz = (int) (Math.sin(angle) * (8 + Math.random() * radius));
        loc.add(dx, 0, dz);
        loc.setY(world.getHighestBlockYAt(loc) + 1);
        if (loc.getY() < world.getMinHeight() + 1) loc.setY(anchor.getLocation().getY());

        String typeName = plugin.getConfigManager().get("events.world-boss.type", "WITHER_SKELETON");
        EntityType type;
        try { type = EntityType.valueOf(typeName.toUpperCase()); }
        catch (Exception e) { type = EntityType.WITHER_SKELETON; }
        if (!type.isSpawnable() || !type.isAlive()) type = EntityType.WITHER_SKELETON;

        Entity e = world.spawnEntity(loc, type);
        if (!(e instanceof LivingEntity le)) { e.remove(); return; }
        worldBoss = le;
        bossSpawnedAt = System.currentTimeMillis();
        bossDamage.clear();

        String name = plugin.getConfigManager().get("events.world-boss.name", "&cСвітовий бос");
        le.setCustomName(name.replace("&", "§"));
        le.setCustomNameVisible(true);
        double health = plugin.getConfigManager().getDouble("events.world-boss.health", 300.0);
        try { le.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health); } catch (Throwable ignored) { }
        le.setHealth(Math.min(health, le.getMaxHealth()));
        try {
            double dmg = plugin.getConfigManager().getDouble("events.world-boss.damage", 12.0);
            if (le.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                le.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(dmg);
            double spd = plugin.getConfigManager().getDouble("events.world-boss.speed", 0.28);
            if (le.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                le.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(spd);
        } catch (Throwable ignored) { }
        le.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
        try { le.setGlowing(true); } catch (Throwable ignored) { }

        String msg = plugin.getConfigManager().msgRaw("boss-spawn");
        if (msg == null || msg.isEmpty())
            msg = "&c&lСвітовий бос з'явився! &7Координати: {x}, {y}, {z}";
        msg = msg.replace("{x}", String.valueOf(loc.getBlockX()))
                 .replace("{y}", String.valueOf(loc.getBlockY()))
                 .replace("{z}", String.valueOf(loc.getBlockZ()));
        plugin.getConfigManager().broadcastStaffRaw(msg.replace("&", "§"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§c§lСВІТОВИЙ БОС", "§7Координати: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(), 10, 60, 10);
        }

        bossBar = Bukkit.createBossBar(le.getCustomName() == null ? "World Boss" : le.getCustomName(),
                BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
    }

    private void removeBoss(boolean killed) {
        if (worldBoss != null) {
            try { worldBoss.remove(); } catch (Throwable ignored) { }
            worldBoss = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        bossDamage.clear();
    }

    // ----------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!bloodMoon) return;
        if (!(e.getEntity() instanceof Monster)) return;
        if (e.getEntity().hasMetadata("worldboss")) return;
        LivingEntity le = e.getEntity();
        int str = plugin.getConfigManager().getInt("events.blood-moon.strength-amplifier", 1);
        int spd = plugin.getConfigManager().getInt("events.blood-moon.speed-amplifier", 1);
        if (str > 0) le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, str - 1, true, false));
        if (spd > 0) le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, spd - 1, true, false));
        le.setMetadata("bloodmoon", new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (worldBoss == null || !worldBoss.isValid()) return;
        if (!e.getEntity().equals(worldBoss)) return;
        Player damager = null;
        if (e.getDamager() instanceof Player p) damager = p;
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) damager = p;
        if (damager == null) return;
        bossDamage.merge(damager.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        // World boss death
        if (e.getEntity().hasMetadata("worldboss")) {
            handleBossDeath(e);
            return;
        }
        // Blood moon drop multiplier
        if (!bloodMoon) return;
        if (!e.getEntity().hasMetadata("bloodmoon")) return;
        int mult = plugin.getConfigManager().getInt("events.blood-moon.drop-multiplier", 2);
        if (mult <= 1) return;
        List<ItemStack> drops = e.getDrops();
        for (ItemStack it : new ArrayList<>(drops)) {
            if (it == null) continue;
            it.setAmount(Math.min(it.getMaxStackSize(), it.getAmount() * mult));
        }
    }

    private void handleBossDeath(EntityDeathEvent e) {
        LivingEntity boss = e.getEntity();
        // rewards to top damagers
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(bossDamage.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder top = new StringBuilder();
        int rank = 0;
        for (Map.Entry<UUID, Double> en : sorted) {
            if (rank >= 3) break;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) { rank++; continue; }
            // bonus items
            for (String r : plugin.getConfigManager().getList("events.world-boss.top-rewards")) {
                ItemStack it = parseItem(r);
                if (it != null) p.getInventory().addItem(it);
            }
            top.append(p.getName()).append(" ");
            rank++;
        }
        // base drops at location
        for (String r : plugin.getConfigManager().getList("events.world-boss.rewards")) {
            ItemStack it = parseItem(r);
            if (it != null) boss.getWorld().dropItemNaturally(boss.getLocation(), it);
        }
        String msg = plugin.getConfigManager().msgRaw("boss-dead");
        if (msg == null || msg.isEmpty()) msg = "&a&lСвітовий бос переможений! Топ: {top}";
        msg = msg.replace("{top}", top.toString().trim());
        plugin.getConfigManager().broadcastStaffRaw(msg.replace("&", "§"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§a§lБОС ПЕРЕМОЖЕНИЙ", "§7Дякуємо за участь", 10, 60, 10);
        }
        removeBoss(true);
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

    // ----------------------------------------------------------------
    // Public controls
    // ----------------------------------------------------------------
    public void forceBloodMoon(boolean on) {
        World world = Bukkit.getWorld("world");
        if (on) {
            if (!bloodMoon) startBloodMoon(world == null ? null : world);
        } else {
            stopBloodMoon();
        }
    }

    public void forceBoss() {
        spawnBoss();
    }

    public String status() {
        return "BloodMoon=" + bloodMoon + ", Boss=" + (worldBoss != null && worldBoss.isValid());
    }

    public void onJoin(Player p) {
        if (bloodMoonBar != null) bloodMoonBar.addPlayer(p);
        if (bossBar != null) bossBar.addPlayer(p);
    }

    public void onQuit(Player p) {
        if (bloodMoonBar != null) bloodMoonBar.removePlayer(p);
        if (bossBar != null) bossBar.removePlayer(p);
    }
}
