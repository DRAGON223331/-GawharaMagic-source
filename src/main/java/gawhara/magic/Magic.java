package gawhara.magic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Magic extends JavaPlugin implements Listener, CommandExecutor {

    static final String[] WNAMES = {"نصل", "فأس", "قوس", "سيف الغضب", "السيف الأسطوري"};
    static final int[] REQ = {0, 20, 60, 130, 250};          // نقاط كل لفل
    static final int[] SLOTS = {10, 11, 12, 14, 15, 16};      // أماكن العناصر في الـ GUI

    record W(Element el, int lvl) {}

    static class MagicGui implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    NamespacedKey KEY;
    File dataFile;
    YamlConfiguration data;
    boolean dirty = false;
    boolean inAbility = false;
    final Map<UUID, Long> cd = new HashMap<>();

    static Component c(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------ lifecycle
    @Override
    public void onEnable() {
        KEY = new NamespacedKey(this, "weapon");
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("gmagic").setExecutor(this);
        getCommand("grestore").setExecutor(this);

        // هالة الجسيمات حوالين اللي ماسك سلاح سحري
        new BukkitRunnable() {
            @Override public void run() {
                double a = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    W w = info(p.getInventory().getItemInMainHand());
                    if (w == null) continue;
                    for (int i = 0; i < 2; i++) {
                        double ang = a * 3 + i * Math.PI;
                        Location l = p.getLocation().add(Math.cos(ang) * 0.9, 0.3 + (Math.sin(a * 2) + 1) * 0.5, Math.sin(ang) * 0.9);
                        p.getWorld().spawnParticle(w.el().particle, l, 2, 0.05, 0.05, 0.05, 0.01, null);
                    }
                }
            }
        }.runTaskTimer(this, 10, 4);

        // حفظ دوري
        new BukkitRunnable() {
            @Override public void run() { if (dirty) save(); }
        }.runTaskTimer(this, 600, 600);

        // أول ما البلاجن يتضاف: كل اللي أونلاين ومختارش قوة يظهرله الـ GUI
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getEl(p) == null) openGui(p);
        }
    }

    @Override
    public void onDisable() { save(); }

    void save() {
        try { data.save(dataFile); dirty = false; }
        catch (IOException ex) { getLogger().warning("مقدرتش أحفظ data.yml: " + ex.getMessage()); }
    }

    // ------------------------------------------------------------ data
    Element getEl(Player p) {
        String s = data.getString("players." + p.getUniqueId() + ".element");
        if (s == null) return null;
        try { return Element.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    int pts(Player p) { return data.getInt("players." + p.getUniqueId() + ".points", 0); }

    static int levelOf(int pts) {
        int l = 1;
        for (int i = 0; i < REQ.length; i++) if (pts >= REQ[i]) l = i + 1;
        return l;
    }

    W info(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            String[] a = s.split(":");
            return new W(Element.valueOf(a[0]), Integer.parseInt(a[1]));
        } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------ GUI
    void openGui(Player p) {
        MagicGui h = new MagicGui();
        h.inv = Bukkit.createInventory(h, 27, c("§8✦ اختار قوتك السحرية ✦"));
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(c(" "));
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) h.inv.setItem(i, glass);

        Element[] els = Element.values();
        for (int i = 0; i < els.length; i++) {
            Element el = els[i];
            ItemStack it = new ItemStack(el.icon);
            ItemMeta m = it.getItemMeta();
            m.displayName(c(el.color + "✦ قوة " + el.ar));
            List<Component> lore = new ArrayList<>();
            lore.add(c("§7" + el.desc));
            lore.add(c(" "));
            lore.add(c("§eالقدرة: " + el.color + el.ability));
            lore.add(c("§7سلاح خاص جديد في كل لفل (5 لفلات)"));
            lore.add(c("§7أسلحتك مبتضيعش لما تموت"));
            lore.add(c(" "));
            lore.add(c("§a▶ اضغط عشان تختارها (مرة واحدة بس)"));
            m.lore(lore);
            m.addEnchant(Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
            h.inv.setItem(SLOTS[i], it);
        }
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MagicGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (getEl(p) != null) { p.closeInventory(); return; }
        for (int i = 0; i < SLOTS.length; i++) {
            if (e.getSlot() == SLOTS[i]) { choose(p, Element.values()[i]); return; }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MagicGui) e.setCancelled(true);
    }

    void choose(Player p, Element el) {
        data.set("players." + p.getUniqueId() + ".element", el.name());
        data.set("players." + p.getUniqueId() + ".points", 0);
        save();
        p.closeInventory();
        giveWeapon(p, el, 1);
        p.sendMessage(c("§6✦ §fاتعلمت قوة " + el.color + el.ar + "§f! جرّب سلاحك الجديد واضغط كليك يمين للقدرة."));
        p.showTitle(net.kyori.adventure.title.Title.title(
                c(el.color + "✦ قوة " + el.ar + " ✦"), c("§7سلاحك الأول في إيدك")));
        celebrate(p, el);
        Bukkit.broadcast(c("§6✦ §e" + p.getName() + " §7اختار قوة " + el.color + el.ar));
    }

    // ------------------------------------------------------------ weapons
    ItemStack makeWeapon(Element el, int lvl) {
        Material m = switch (lvl) {
            case 1 -> Material.IRON_SWORD;
            case 2 -> Material.DIAMOND_AXE;
            case 3 -> Material.BOW;
            case 4 -> Material.DIAMOND_SWORD;
            default -> Material.NETHERITE_SWORD;
        };
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(el.color + "✦ " + WNAMES[lvl - 1] + " " + el.ar));
        List<Component> lore = new ArrayList<>();
        lore.add(c("§7لفل " + lvl + " • قوة " + el.color + el.ar));
        lore.add(c(" "));
        lore.add(c("§eالقدرة: " + el.color + el.ability));
        lore.add(c(lvl == 3 ? "§7شيفت + كليك يمين للقدرة" : "§7كليك يمين للقدرة"));
        lore.add(c("§7كل ضربة بتطلّع تأثير " + el.ar));
        lore.add(c(" "));
        lore.add(c("§8مبيضيعش لما تموت"));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (m == Material.BOW) {
            meta.addEnchant(Enchantment.POWER, 2 + lvl, true);
            meta.addEnchant(Enchantment.INFINITY, 1, true);
        } else {
            meta.addEnchant(Enchantment.SHARPNESS, 1 + lvl * 2, true);
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, el.name() + ":" + lvl);
        it.setItemMeta(meta);
        return it;
    }

    void giveWeapon(Player p, Element el, int lvl) {
        give(p, makeWeapon(el, lvl));
        if (lvl == 3) give(p, new ItemStack(Material.ARROW, 1));
    }

    boolean hasWeapon(Player p, Element el, int lvl) {
        for (ItemStack it : p.getInventory().getContents()) {
            W w = info(it);
            if (w != null && w.el() == el && w.lvl() == lvl) return true;
        }
        return false;
    }

    void give(Player p, ItemStack it) {
        String n = it.getType().name();
        PlayerInventory inv = p.getInventory();
        if (n.endsWith("_HELMET") && inv.getHelmet() == null) { inv.setHelmet(it); return; }
        if ((n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) && inv.getChestplate() == null) { inv.setChestplate(it); return; }
        if (n.endsWith("_LEGGINGS") && inv.getLeggings() == null) { inv.setLeggings(it); return; }
        if (n.endsWith("_BOOTS") && inv.getBoots() == null) { inv.setBoots(it); return; }
        if (n.equals("SHIELD") && inv.getItemInOffHand().getType() == Material.AIR) { inv.setItemInOffHand(it); return; }
        for (ItemStack left : inv.addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (info(e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(c("§cالسلاح السحري مينفعش يتحدف"));
        }
    }

    // ------------------------------------------------------------ levels
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player && e.getEntity() == k) return;
        Element el = getEl(k);
        if (el == null) return;
        int add = e.getEntity() instanceof Player ? 8 : (e.getEntity() instanceof Monster ? 2 : 1);
        addPoints(k, el, add);
    }

    void addPoints(Player p, Element el, int amt) {
        int old = pts(p);
        int nw = old + amt;
        data.set("players." + p.getUniqueId() + ".points", nw);
        dirty = true;
        int ol = levelOf(old), nl = levelOf(nw);
        if (nl > ol) {
            for (int l = ol + 1; l <= nl; l++) {
                giveWeapon(p, el, l);
                p.sendMessage(c("§6✦ §fلفل جديد! §e" + l + " §f— استلمت: " + el.color + WNAMES[l - 1] + " " + el.ar));
            }
            p.showTitle(net.kyori.adventure.title.Title.title(
                    c("§6✦ لفل " + nl + " ✦"), c(el.color + "سلاح جديد: " + WNAMES[nl - 1] + " " + el.ar)));
            celebrate(p, el);
        } else {
            String next = nl >= REQ.length ? "§aأقصى لفل" : "§7" + nw + "/" + REQ[nl];
            p.sendActionBar(c(el.color + "✦ " + el.ar + " §7| لفل " + nl + " | " + next));
        }
    }

    // ------------------------------------------------------------ combat fx
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (inAbility || !(e.getEntity() instanceof LivingEntity v)) return;
        W w = null;
        Player atk = null;
        if (e.getDamager() instanceof Player p) {
            atk = p;
            w = info(p.getInventory().getItemInMainHand());
        } else if (e.getDamager() instanceof Arrow a && a.hasMetadata("gm") && a.getShooter() instanceof Player p) {
            atk = p;
            String[] s = a.getMetadata("gm").get(0).asString().split(":");
            w = new W(Element.valueOf(s[0]), Integer.parseInt(s[1]));
        }
        if (w == null) return;
        hitFx(atk, v, w);
    }

    void hitFx(Player atk, LivingEntity v, W w) {
        int l = w.lvl();
        Location vl = v.getLocation().add(0, 1, 0);
        v.getWorld().spawnParticle(w.el().particle, vl, 25, 0.3, 0.5, 0.3, 0.05, null);
        switch (w.el()) {
            case FIRE -> v.setFireTicks(40 + l * 20);
            case WATER -> v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + l * 10, 1));
            case EARTH -> {
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 + l * 10, 2));
                atk.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 0));
            }
            case WIND -> {
                Vector d = atk.getLocation().getDirection().setY(0);
                if (d.lengthSquared() > 0.0001) d.normalize();
                v.setVelocity(v.getVelocity().add(d.multiply(0.5 + l * 0.12).setY(0.4)));
            }
            case LIGHTNING -> {
                if (Math.random() < 0.3) {
                    v.getWorld().strikeLightningEffect(v.getLocation());
                    inAbility = true;
                    try { v.damage(2 + l); } finally { inAbility = false; }
                }
            }
            case SHADOW -> {
                v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60 + l * 20, 0));
                v.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getProjectile() instanceof Arrow a)) return;
        W w = info(e.getBow());
        if (w == null) return;
        a.setMetadata("gm", new FixedMetadataValue(this, w.el().name() + ":" + w.lvl()));
        a.setCritical(true);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (a.isDead() || a.isOnGround() || t++ > 120) { cancel(); return; }
                a.getWorld().spawnParticle(w.el().particle, a.getLocation(), 4, 0.05, 0.05, 0.05, 0.01, null);
            }
        }.runTaskTimer(this, 0, 1);
    }

    // ------------------------------------------------------------ abilities
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        org.bukkit.event.block.Action act = e.getAction();
        if (act != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && act != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        W w = info(p.getInventory().getItemInMainHand());
        if (w == null) return;
        if (w.lvl() == 3 && !p.isSneaking()) return; // القوس: شيفت + كليك يمين
        ability(p, w);
    }

    List<LivingEntity> near(Player p, double r) {
        List<LivingEntity> l = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof LivingEntity le && !(en instanceof ArmorStand) && le != p
                    && !(le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)) l.add(le);
        }
        return l;
    }

    void push(LivingEntity e, Location from, double power, double up) {
        Vector v = e.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 0); else v.normalize();
        e.setVelocity(v.multiply(power).setY(up));
    }

    void ability(Player p, W w) {
        long now = System.currentTimeMillis();
        Long ready = cd.get(p.getUniqueId());
        if (ready != null && ready > now) {
            p.sendActionBar(c("§cالقدرة جاهزة بعد " + ((ready - now) / 1000 + 1) + " ث"));
            return;
        }
        cd.put(p.getUniqueId(), now + (long) ((12 - w.lvl() * 1.5) * 1000));

        World wd = p.getWorld();
        Location loc = p.getLocation();
        int lvl = w.lvl();
        double rad = 4 + lvl;
        double dmg = 3 + lvl * 1.5;
        wd.playSound(loc, w.el().sound, 1.2f, 1f);
        p.sendActionBar(c(w.el().color + "✦ " + w.el().ability + " ✦"));

        inAbility = true;
        try {
            switch (w.el()) {
                case FIRE -> {
                    ring(loc, Particle.FLAME, null, rad, 12);
                    wd.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 1);
                    for (LivingEntity t : near(p, rad)) {
                        t.damage(dmg, p);
                        t.setFireTicks(80 + lvl * 20);
                        push(t, loc, 0.9, 0.35);
                    }
                }
                case WATER -> {
                    ring(loc, Particle.SPLASH, null, rad, 12);
                    ring(loc, Particle.BUBBLE_POP, null, rad * 0.7, 12);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
                    for (LivingEntity t : near(p, rad)) {
                        t.damage(dmg * 0.6, p);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                        push(t, loc, 0.6, 0.2);
                    }
                }
                case EARTH -> {
                    ring(loc, Particle.BLOCK, Material.DIRT.createBlockData(), rad, 10);
                    wd.spawnParticle(Particle.BLOCK, loc, 60, rad / 2, 0.2, rad / 2, 0.1, Material.STONE.createBlockData());
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
                    for (LivingEntity t : near(p, rad)) {
                        t.damage(dmg, p);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        t.setVelocity(new Vector(0, 0.9, 0));
                    }
                }
                case WIND -> {
                    Vector d = loc.getDirection().setY(0);
                    if (d.lengthSquared() < 0.0001) d = new Vector(0, 0, 1);
                    d.normalize();
                    p.setVelocity(d.clone().multiply(1.8).setY(0.25));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0));
                    for (LivingEntity t : near(p, 3.5)) {
                        t.damage(dmg * 0.5, p);
                        push(t, loc, 1.1, 0.4);
                    }
                    new BukkitRunnable() {
                        int t = 0;
                        @Override public void run() {
                            if (!p.isOnline() || t++ > 10) { cancel(); return; }
                            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 12, 0.3, 0.4, 0.3, 0.02, null);
                            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
                        }
                    }.runTaskTimer(this, 0, 1);
                }
                case LIGHTNING -> {
                    List<LivingEntity> ts = near(p, 10 + lvl);
                    ts.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(loc)));
                    int hits = Math.min(ts.size(), 2 + lvl / 2);
                    if (hits == 0) {
                        wd.strikeLightningEffect(p.getTargetBlock(null, 15).getLocation());
                    }
                    for (int i = 0; i < hits; i++) {
                        LivingEntity t = ts.get(i);
                        wd.strikeLightningEffect(t.getLocation());
                        t.damage(dmg * 1.3, p);
                        wd.spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2, null);
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
                }
                case SHADOW -> {
                    Vector d = loc.getDirection().setY(0);
                    if (d.lengthSquared() < 0.0001) d = new Vector(0, 0, 1);
                    d.normalize();
                    Location dest = loc.clone();
                    for (int i = 1; i <= 8; i++) {
                        Location t = loc.clone().add(d.clone().multiply(i));
                        if (t.getBlock().isPassable() && t.clone().add(0, 1, 0).getBlock().isPassable()) dest = t;
                        else break;
                    }
                    wd.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.5, null);
                    p.teleport(dest);
                    wd.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.2, null);
                    wd.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                    for (LivingEntity t : near(p, rad)) {
                        t.damage(dmg * 0.8, p);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                    }
                }
            }
        } finally {
            inAbility = false;
        }
    }

    // ------------------------------------------------------------ effects helpers
    void ring(Location c, Particle pt, Object data, double max, int ticks) {
        Location center = c.clone();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= ticks) { cancel(); return; }
                double r = max * (t + 1) / ticks;
                for (int i = 0; i < 28; i++) {
                    double a = i * Math.PI * 2 / 28;
                    center.getWorld().spawnParticle(pt, center.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, data);
                }
                t++;
            }
        }.runTaskTimer(this, 0, 1);
    }

    void spiral(Player p, Particle pt, int ticks) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t > ticks) { cancel(); return; }
                double y = t * (2.3 / ticks);
                for (int i = 0; i < 2; i++) {
                    double a = t * 0.5 + i * Math.PI;
                    Location l = p.getLocation().add(Math.cos(a) * 1.1, y, Math.sin(a) * 1.1);
                    p.getWorld().spawnParticle(pt, l, 2, 0, 0, 0, 0, null);
                }
                t++;
            }
        }.runTaskTimer(this, 0, 1);
    }

    void celebrate(Player p, Element el) {
        spiral(p, el.particle, 30);
        spiral(p, Particle.END_ROD, 30);
        ring(p.getLocation(), el.particle, null, 4, 14);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3, null);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
    }

    // ------------------------------------------------------------ death & /grestore
    @SuppressWarnings("unchecked")
    List<ItemStack> loadStash(UUID u) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = data.getList("stash." + u + ".items");
        if (raw != null) for (Object o : raw) if (o instanceof ItemStack is) out.add(is);
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory()) return;

        List<ItemStack> keep = new ArrayList<>();
        List<ItemStack> stash = new ArrayList<>();
        for (ItemStack it : new ArrayList<>(e.getDrops())) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (info(it) != null) keep.add(it); else stash.add(it);
        }
        int exp = p.getTotalExperience();
        e.getDrops().clear();
        e.getItemsToKeep().addAll(keep);
        e.setDroppedExp(0);

        if (!stash.isEmpty() || exp > 0) {
            UUID u = p.getUniqueId();
            List<ItemStack> all = loadStash(u);
            all.addAll(stash);
            data.set("stash." + u + ".items", all);
            data.set("stash." + u + ".exp", data.getInt("stash." + u + ".exp", 0) + exp);
            save();
        }

        Location l = p.getLocation().add(0, 1, 0);
        Element el = getEl(p);
        Particle pt = el != null ? el.particle : Particle.SOUL;
        p.getWorld().spawnParticle(Particle.SOUL, l, 40, 0.4, 0.8, 0.4, 0.05, null);
        p.getWorld().spawnParticle(pt, l, 60, 0.5, 0.8, 0.5, 0.1, null);
        p.getWorld().playSound(l, Sound.ENTITY_WITHER_DEATH, 0.4f, 1.6f);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                UUID u = p.getUniqueId();
                if (!loadStash(u).isEmpty() || data.getInt("stash." + u + ".exp", 0) > 0) {
                    p.sendMessage(c("§6✦ §fحاجتك محفوظة! اكتب §e/grestore §fعشان ترجعلك كلها."));
                }
            }
        }.runTaskLater(this, 30);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                if (getEl(p) == null) {
                    p.sendMessage(c("§6✦ §fاختار قوتك السحرية! لو قفلت الشاشة اكتب §e/gmagic"));
                    openGui(p);
                } else {
                    UUID u = p.getUniqueId();
                    if (!loadStash(u).isEmpty() || data.getInt("stash." + u + ".exp", 0) > 0)
                        p.sendMessage(c("§6✦ §fعندك حاجة محفوظة من آخر موت — اكتب §e/grestore"));
                }
            }
        }.runTaskLater(this, 60);
    }

    void restore(Player p) {
        UUID u = p.getUniqueId();
        List<ItemStack> items = loadStash(u);
        int exp = data.getInt("stash." + u + ".exp", 0);
        if (items.isEmpty() && exp <= 0) {
            p.sendMessage(c("§cمفيش حاجة محفوظة ترجعها."));
            return;
        }
        data.set("stash." + u, null);
        save();
        for (ItemStack it : items) give(p, it);
        if (exp > 0) p.giveExp(exp);
        p.sendMessage(c("§a✦ رجعتلك كل حاجتك (" + items.size() + " عنصر)!"));
        Element el = getEl(p);
        spiral(p, Particle.TOTEM_OF_UNDYING, 30);
        spiral(p, el != null ? el.particle : Particle.END_ROD, 30);
        ring(p.getLocation(), Particle.END_ROD, null, 5, 14);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.3f);
    }

    // ------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("grestore")) {
            if (!(s instanceof Player p)) { s.sendMessage("للاعبين بس."); return true; }
            restore(p);
            return true;
        }
        // /gmagic
        if (a.length >= 1 && (a[0].equalsIgnoreCase("reset") || a[0].equalsIgnoreCase("level"))) {
            if (!s.hasPermission("gawhara.magic.admin")) { s.sendMessage(c("§cمعندكش صلاحية.")); return true; }
            if (a.length < 2) { s.sendMessage(c("§c/gmagic " + a[0] + " <لاعب>" + (a[0].equalsIgnoreCase("level") ? " <1-5>" : ""))); return true; }
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(c("§cاللاعب مش أونلاين.")); return true; }
            if (a[0].equalsIgnoreCase("reset")) {
                data.set("players." + t.getUniqueId(), null);
                save();
                s.sendMessage(c("§aاتعمل ريسيت لـ " + t.getName()));
                openGui(t);
            } else {
                Element el = getEl(t);
                if (el == null || a.length < 3) { s.sendMessage(c("§cاللاعب لازم يكون مختار قوة، والمطلوب /gmagic level <لاعب> <1-5>")); return true; }
                int lv;
                try { lv = Math.max(1, Math.min(5, Integer.parseInt(a[2]))); }
                catch (NumberFormatException ex) { s.sendMessage(c("§cرقم غلط.")); return true; }
                int cur = pts(t);
                int target = REQ[lv - 1];
                if (target > cur) addPoints(t, el, target - cur);
                else { data.set("players." + t.getUniqueId() + ".points", target); dirty = true; }
                s.sendMessage(c("§aخلاص."));
            }
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("للاعبين بس."); return true; }
        Element el = getEl(p);
        if (el == null) { openGui(p); return true; }
        if (a.length >= 1 && a[0].equalsIgnoreCase("weapons")) {
            int lv = levelOf(pts(p));
            int given = 0;
            for (int l = 1; l <= lv; l++) {
                if (!hasWeapon(p, el, l)) { giveWeapon(p, el, l); given++; }
            }
            p.sendMessage(c(given == 0 ? "§7معاك كل أسلحتك." : "§aرجعتلك " + given + " سلاح."));
            return true;
        }
        int pt = pts(p);
        int lv = levelOf(pt);
        String next = lv >= REQ.length ? "أقصى لفل" : pt + "/" + REQ[lv];
        p.sendMessage(c("§6✦ قوتك: " + el.color + el.ar + " §7| لفل §e" + lv + " §7| نقاط: §f" + next));
        p.sendMessage(c("§7/gmagic weapons ← ترجّع أسلحتك لو ضاعت"));
        return true;
    }
}
