package gawhara.magic;

import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * نظام "وضع القوة" (Power Mode) — بديل الأسلحة.
 *
 * ماينكرافت (سيرفر Paper) مبيقدرش يقرا أزرار كيبورد مخصصة زي G / C / X (دي بتحتاج مود على جهاز اللاعب).
 * فبنستخدم الأزرار اللي السيرفر بيشوفها فعلًا:
 *   F            : تشغيل / إيقاف وضع القوة        (بدل C)
 *   سكرول الماوس : تبديل القدرة الحالية           (بدل X)
 *   كليك يمين    : استخدام القدرة المختارة
 *   كليك شمال    : ضربة بقوتك (ضرر زيادة + تأثير العنصر) بمانا قليلة   (بدل G)
 *
 * عشان كليك يمين يشتغل واليد فاضية (ماينكرافت مبيبعتش الحدث في الهوا) بنحط "قلب القوة"
 * في اليد التانية (Offhand) وقت التفعيل، وبنرجّع اللي كان فيها أول ما توقف الوضع.
 */
final class PowerMode implements Listener {

    record Ab(String name, String desc, double cost, int cd) {}

    static final Map<Element, Ab[]> ABS = new EnumMap<>(Element.class);

    static {
        ABS.put(Element.FIRE, new Ab[]{
                new Ab("حلقة اللهب", "النار تنتشر حواليك وتحرق اللي يقرب", 6, 12),
                new Ab("كرة النار", "قذيفة نار تنفجر عند الهدف", 4, 2),
                new Ab("طيران النار", "تطير بالنار في اتجاه نظرك (Shift لإيقافه)", 8, 10)});
        ABS.put(Element.WATER, new Ab[]{
                new Ab("موجة المد", "موجة مية تدفع وتبطّئ قدامك وتشفيك", 4, 4),
                new Ab("سجن الفقاعة", "فقاعة تحبس الهدف وترفعه", 5, 7),
                new Ab("سباحة الموج", "سرعة مائية وتنفس تحت الماء", 5, 12)});
        ABS.put(Element.EARTH, new Ab[]{
                new Ab("رمح الصخر", "رماح صخر تطلع من الأرض قدامك", 4, 4),
                new Ab("درع الأرض", "درع صخري يقلل الضرر", 5, 14),
                new Ab("الزلزال", "موجات زلزال ترمي اللي حواليك", 7, 10)});
        ABS.put(Element.WIND, new Ab[]{
                new Ab("هبّة الرياح", "هبة قوية تزق اللي قدامك", 3, 3),
                new Ab("قفزة الريح", "قفزة عالية مع سقوط آمن", 3, 5),
                new Ab("الإعصار", "إعصار يشفط الأعداء ويرفعهم", 7, 12)});
        ABS.put(Element.LIGHTNING, new Ab[]{
                new Ab("الشرارة", "صاعقة على الهدف وتتفرع لجنبه", 4, 3),
                new Ab("اندفاعة البرق", "اندفاعة سريعة تصعق اللي في طريقك", 5, 6),
                new Ab("المجال الساكن", "مجال كهربي يصعق الأقرب ليك", 7, 14)});
        ABS.put(Element.SHADOW, new Ab[]{
                new Ab("خطوة الظل", "تنتقل لأقرب مكان مظلم/فيه ظل", 5, 6),
                new Ab("ضربة الظلام", "ضربة أقوى كل ما المكان أضلم", 4, 4),
                new Ab("عباءة الظلام", "اختفاء وقوة خارقة في الضلمة", 6, 16)});
        ABS.put(Element.ICE, new Ab[]{
                new Ab("نوفا الصقيع", "موجة برد تجمّد اللي حواليك", 5, 8),
                new Ab("وابل الجليد", "ثلاث شظايا جليد قدام", 4, 3),
                new Ab("عاصفة الثلج", "عاصفة ثلج تبطّئ وتجمّد منطقة", 7, 14)});
        ABS.put(Element.METAL, new Ab[]{
                new Ab("درع الفولاذ", "درع حديد يقلل الضرر ويزق الأعداء", 5, 12),
                new Ab("المغناطيس", "يسحب الأعداء ناحيتك", 5, 7),
                new Ab("قبضة الحديد", "اندفاعة وضربة ثقيلة", 6, 8)});
        ABS.put(Element.NATURE, new Ab[]{
                new Ab("قيد الجذور", "جذور تقيّد وتسمّم الأعداء", 5, 8),
                new Ab("زهرة الشفاء", "تشفيك أنت واللاعبين القريبين", 6, 12),
                new Ab("انفجار الأشواك", "أشواك تنفجر حواليك", 5, 7)});
        ABS.put(Element.LIGHT, new Ab[]{
                new Ab("وميضة", "وميض يعمي الأعداء ويديك سرعة", 4, 8),
                new Ab("شعاع النور", "شعاع طويل يخترق الأعداء", 5, 4),
                new Ab("نعمة النور", "تشفيك وتشيل التأثيرات السيئة", 6, 14)});
        ABS.put(Element.SOUND, new Ab[]{
                new Ab("موجة الصدمة", "صدمة صوتية تزق قدامك", 3, 3),
                new Ab("الانفجار الصوتي", "شعاع صوتي مدمّر", 5, 5),
                new Ab("الصدى", "ثلاث نبضات صدى حواليك", 6, 10)});
        ABS.put(Element.CRYSTAL, new Ab[]{
                new Ab("وابل الشظايا", "خمس شظايا بلور", 4, 3),
                new Ab("درع المنشور", "درع بلور يمتص الضرر", 5, 14),
                new Ab("قفص الكريستال", "يحبس الهدف داخل قفص بلور", 6, 10)});
        ABS.put(Element.SHAPESHIFT, new Ab[]{
                new Ab("شبكة الصياد", "قدرة سبايدر: شبكة تثبّت الأعداء وتمنعهم من القفز", 5, 12),
                new Ab("الانفجار المشحون", "قدرة كريبر: انفجار دائري بلا تكسير للبلوكات", 9, 24),
                new Ab("وابل العظام", "قدرة سكيلتون: ثلاث سهام دقيقة في مروحة", 5, 7),
                new Ab("القذيفة اللهبية", "قدرة بليز: كرة نارية ومقاومة للنار", 6, 9),
                new Ab("قذيفة الجاست", "قدرة جاست: قذيفة ثقيلة بلا تخريب للبيئة", 10, 26),
                new Ab("الزئير الصوتي", "قدرة واردن: موجة صوتية تخترق المسار", 12, 32),
                new Ab("ضربة الحارس", "قدرة آيرون جولم: صدمة أرضية ودرع حديدي", 8, 20),
                new Ab("أنياب الاستدعاء", "قدرة إيفوكر: صف من الأنياب السحرية", 9, 22),
                new Ab("زفير التنين", "قدرة تنين الإند: كرة تنين وسحابة تنفس", 14, 38),
                new Ab("خطوة الفراغ", "قدرة إندرمان: انتقال خلف الهدف وضربة ظل", 8, 18)});
        // القدرات 4..10 لكل عنصر معرّفة في Moves.java
        for (Element e : Element.values()) {
            Ab[] base = ABS.get(e);
            Moves.Mv[] ex = Moves.MV.getOrDefault(e, new Moves.Mv[0]);
            Ab[] all = new Ab[base.length + ex.length];
            System.arraycopy(base, 0, all, 0, base.length);
            for (int i = 0; i < ex.length; i++)
                all[base.length + i] = new Ab(ex[i].name(), ex[i].desc(), ex[i].cost(), ex[i].cd());
            ABS.put(e, all);
        }
    }

    private final Magic magic;
    private final Moves moves;
    private final NamespacedKey coreKey;
    private final File file;
    private final YamlConfiguration store;
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, Integer> selected = new HashMap<>();
    private final Map<String, Long> cds = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> strikeCd = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private long tick = 0;

    PowerMode(Magic magic) {
        this.magic = magic;
        this.moves = new Moves(this, magic);
        this.coreKey = new NamespacedKey(magic, "power_core");
        this.file = new File(magic.getDataFolder(), "powermode.yml");
        this.store = YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------ lifecycle
    void enable() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, magic);
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) cleanup(p);
        new BukkitRunnable() {
            @Override public void run() { tickAll(); }
        }.runTaskTimer(magic, 10L, 5L);
    }

    void shutdown() {
        for (UUID u : new ArrayList<>(active)) {
            Player p = org.bukkit.Bukkit.getPlayer(u);
            if (p != null) disable(p, true);
        }
        saveStore();
    }

    boolean isActive(Player p) { return active.contains(p.getUniqueId()); }

    private void saveStore() {
        try { store.save(file); }
        catch (IOException ex) { magic.getLogger().warning("مقدرتش أحفظ powermode.yml: " + ex.getMessage()); }
    }

    // ------------------------------------------------------------ core item
    private ItemStack makeCore(Element el) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Magic.c(el.color + "✦ قلب القوة — " + el.ar));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Magic.c("§7كليك يمين: استخدم القدرة | سكرول: بدّل القدرة"));
        lore.add(Magic.c("§7كليك شمال: ضربة بقوتك | F: إيقاف الوضع"));
        m.lore(lore);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isCore(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE);
    }

    private void stripCores(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (isCore(c[i])) inv.setItem(i, null);
        }
        if (isCore(inv.getItemInOffHand())) inv.setItemInOffHand(new ItemStack(Material.AIR));
    }

    /** الأسلحة اتلغت: أي سلاح سحري قديم بيتشال من الشنطة. */
    int stripWeapons(Player p) {
        int removed = 0;
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] != null && magic.info(c[i]) != null) { inv.setItem(i, null); removed++; }
        }
        return removed;
    }

    /** يرجّع اللي كان في اليد التانية لو السيرفر وقع وهو الوضع شغال. */
    private void cleanup(Player p) {
        String u = p.getUniqueId().toString();
        if (!active.contains(p.getUniqueId())) {
            if (store.getBoolean("active." + u, false) || hasCore(p)) restoreOffhand(p);
        }
        stripWeapons(p);
    }

    private boolean hasCore(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isCore(it)) return true;
        return false;
    }

    // ------------------------------------------------------------ toggle
    void toggle(Player p) {
        if (isActive(p)) disable(p, false); else enable(p);
    }

    private void enable(Player p) {
        Element el = magic.getEl(p);
        if (el == null) {
            p.sendActionBar(Magic.c("§cاختار قوة أولًا: /gmagic powers"));
            magic.openGui(p);
            return;
        }
        UUID id = p.getUniqueId();
        PlayerInventory inv = p.getInventory();
        ItemStack off = inv.getItemInOffHand();
        boolean has = off != null && off.getType() != Material.AIR && !isCore(off);
        store.set("saved." + id, has ? off.clone() : null);
        store.set("active." + id, true);
        saveStore();
        inv.setItemInOffHand(makeCore(el));
        active.add(id);
        selected.putIfAbsent(id, 0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
        p.getWorld().spawnParticle(el.particle, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
        p.sendMessage(Magic.c(el.color + "✦ وضع القوة شغّال §7— سكرول لتبديل القدرة | كليك يمين للاستخدام | F للإيقاف"));
        showSelected(p);
    }

    private void disable(Player p, boolean silent) {
        UUID id = p.getUniqueId();
        if (!active.remove(id)) return;
        restoreOffhand(p);
        if (!silent) {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.2f);
            p.sendActionBar(Magic.c("§7✦ وضع القوة اتقفل"));
        }
    }

    private void restoreOffhand(Player p) {
        UUID id = p.getUniqueId();
        PlayerInventory inv = p.getInventory();
        ItemStack saved = store.getItemStack("saved." + id);
        stripCores(p);
        if (saved != null && saved.getType() != Material.AIR) {
            if (inv.getItemInOffHand().getType() == Material.AIR) inv.setItemInOffHand(saved);
            else for (ItemStack left : inv.addItem(saved).values())
                p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        store.set("saved." + id, null);
        store.set("active." + id, null);
        saveStore();
    }

    // ------------------------------------------------------------ selection
    private Ab[] abs(Player p) {
        Element el = magic.getEl(p);
        return el == null ? null : ABS.get(el);
    }

    private double cost(Element el, int idx) {
        List<Double> l = magic.getConfig().getDoubleList("power.costs." + el.name());
        double base = l.size() > idx ? l.get(idx) : ABS.get(el)[idx].cost();
        return Math.max(0, base * magic.getConfig().getDouble("power.cost-multiplier", 1.0));
    }

    private int cooldown(Element el, int idx) {
        List<Integer> l = magic.getConfig().getIntegerList("power.cooldowns." + el.name());
        return l.size() > idx ? l.get(idx) : ABS.get(el)[idx].cd();
    }

    void useSelected(Player p) { cast(p); }

    void cycle(Player p, int dir) {
        Ab[] a = abs(p);
        if (a == null) return;
        int i = Math.floorMod(selected.getOrDefault(p.getUniqueId(), 0) + dir, a.length);
        selected.put(p.getUniqueId(), i);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + i * 0.3f);
        showSelected(p);
    }

    /** يظهر اسم القدرة المختارة في نص الشاشة نص ثانية ثم يختفي (الـ Action Bar متسيب لـ AuraSkills). */
    private void showSelected(Player p) {
        Element el = magic.getEl(p);
        if (el == null) return;
        UUID id = p.getUniqueId();
        int i = selected.getOrDefault(id, 0);
        Ab ab = ABS.get(el)[i];
        long left = cds.getOrDefault(id + ":" + el + i, 0L) - System.currentTimeMillis();
        String state = left > 0 ? "§c" + (left / 1000 + 1) + "ث" : "§aجاهزة";
        p.showTitle(Title.title(
                Magic.c(el.color + "✦ " + ab.name() + " ✦"),
                Magic.c("§7[" + (i + 1) + "/" + ABS.get(el).length + "] §8| §b" + trim(cost(el, i)) + " مانا §8| " + state),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)));
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(java.util.Locale.US, "%.1f", d);
    }

    // ------------------------------------------------------------ periodic
    private void tickAll() {
        tick++;
        for (UUID id : new ArrayList<>(active)) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { active.remove(id); continue; }
            Element el = magic.getEl(p);
            if (el == null) { disable(p, true); continue; }
            if (!isCore(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(makeCore(el));
            Fx.aura(p, el, tick);
            if (el == Element.SHADOW && lightAt(p.getLocation().getBlock()) <= 4) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false));
            }
        }
    }

    // ------------------------------------------------------------ events
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        // Shift + F بيدي درع الحماية (AdvancedSystems) واليد فاضية؛ ماندخلش فيه.
        if (p.isSneaking() && p.getInventory().getItemInMainHand().getType() == Material.AIR) return;
        if (magic.getEl(p) == null) return;
        // لو الوضع مقفول وإيدك الأساسية ماسكة حاجة: F بينقلها بين الأيدي عادي.
        if (!isActive(p) && p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        e.setCancelled(true);
        toggle(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!isActive(p)) return;
        int d = e.getNewSlot() - e.getPreviousSlot();
        if (d > 4) d -= 9;
        if (d < -4) d += 9;
        e.setCancelled(true);
        if (d != 0) cycle(p, d > 0 ? 1 : -1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isActive(p)) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return; // صناديق/أبواب عادية
        e.setCancelled(true);
        trigger(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!isActive(p)) return;
        e.setCancelled(true);
        trigger(p);
    }

    private void trigger(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 150L) return; // نفس الكليك جاي من اليدين
        lastUse.put(p.getUniqueId(), now);
        cast(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !isActive(p)) return;
        if (!(e.getEntity() instanceof LivingEntity target) || busy.contains(p.getUniqueId())) return;
        if (e.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK) return;
        Element el = magic.getEl(p);
        if (el == null) return;
        long now = System.currentTimeMillis();
        if (strikeCd.getOrDefault(p.getUniqueId(), 0L) > now) return;
        double cost = magic.getConfig().getDouble("power.strike-mana", 1.5);
        if (!magic.advanced.spendMana(p, cost)) return;
        strikeCd.put(p.getUniqueId(), now + magic.getConfig().getLong("power.strike-cooldown-ms", 700L));
        int t = magic.unarmedTier(p);
        double bonus = 1.0 + 0.3 * t;
        if (el == Element.SHADOW) bonus *= shadowMult(p);
        e.setDamage(e.getDamage() + bonus);
        strikeEffect(p, el, target, t);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isCore(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean core = isCore(e.getCurrentItem()) || isCore(e.getCursor());
        boolean offhandTouch = isActive(p) && (e.getClick() == ClickType.SWAP_OFFHAND
                || (e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == 40));
        if (core || offhandTouch) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isCore(e.getOldCursor()) || (isActive(p) && e.getInventorySlots().contains(40))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!isActive(p)) {
            e.getDrops().removeIf(this::isCore);
            return;
        }
        ItemStack saved = store.getItemStack("saved." + p.getUniqueId());
        e.getDrops().removeIf(this::isCore);
        active.remove(p.getUniqueId());
        if (e.getKeepInventory()) {
            stripCores(p);
            if (saved != null) p.getInventory().setItemInOffHand(saved);
        } else if (saved != null && saved.getType() != Material.AIR) {
            e.getDrops().add(saved);
        }
        store.set("saved." + p.getUniqueId(), null);
        store.set("active." + p.getUniqueId(), null);
        saveStore();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        disable(e.getPlayer(), true);
        lastUse.remove(e.getPlayer().getUniqueId());
        selected.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override public void run() { if (p.isOnline()) cleanup(p); }
        }.runTaskLater(magic, 20L);
    }

    // ------------------------------------------------------------ commands
    boolean handleCommand(Player p, String[] a) {
        if (a.length == 0) return false;
        String c = a[0].toLowerCase(java.util.Locale.ROOT);
        String needOn = "§cفعّل وضع القوة الأول (F أو /gwm pw)";
        if (c.equals("pw") || c.equals("on") || c.equals("off") || c.equals("toggle")) {
            toggle(p);
            return true;
        }
        if (c.equals("next") || c.equals("prev") || c.equals("use")) {
            if (!isActive(p)) { p.sendActionBar(Magic.c(needOn)); return true; }
            if (c.equals("use")) cast(p);
            else cycle(p, c.equals("next") ? 1 : -1);
            return true;
        }
        if (c.equals("abilities") || c.equals("ab")) {
            listAbilities(p);
            return true;
        }
        return false;
    }

    void listAbilities(Player p) {
        Element el = magic.getEl(p);
        if (el == null) { magic.openGui(p); return; }
        p.sendMessage(Magic.c("§6✦ قدرات " + el.color + el.ar));
        Ab[] a = ABS.get(el);
        for (int i = 0; i < a.length; i++) {
            String lock = el == Element.SHAPESHIFT && (magic.shapeshift == null || !magic.shapeshift.hasForm(p, i))
                    ? "§c[مقفولة] " : "§a[مفتوحة] ";
            p.sendMessage(Magic.c((el == Element.SHAPESHIFT ? lock : "") + el.color + (i + 1) + ". " + a[i].name() + " §8| §b" + trim(cost(el, i))
                    + " مانا §8| §7" + cooldown(el, i) + "ث §8— §7" + a[i].desc()));
        }
        if (el == Element.SHAPESHIFT && magic.shapeshift != null) {
            p.sendMessage(Magic.c("§7سجل الأشكال: §e/gmagic forms"));
        }
    }

    // ------------------------------------------------------------ casting
    private void cast(Player p) {
        Element el = magic.getEl(p);
        if (el == null) return;
        UUID id = p.getUniqueId();
        int idx = selected.getOrDefault(id, 0);
        String key = id + ":" + el + idx;
        long now = System.currentTimeMillis();
        long ready = cds.getOrDefault(key, 0L);
        Ab ab = ABS.get(el)[idx];
        if (ready > now) {
            p.sendActionBar(Magic.c("§c" + ab.name() + " جاهزة بعد " + ((ready - now) / 1000 + 1) + " ث"));
            return;
        }
        double cost = cost(el, idx);
        if (!magic.advanced.spendMana(p, cost)) return;
        cds.put(key, now + cooldown(el, idx) * 1000L);
        int t = magic.unarmedTier(p);
        boolean ok;
        try {
            ok = run(p, el, idx, t);
        } catch (RuntimeException ex) {
            magic.getLogger().warning("خطأ في قدرة " + ab.name() + ": " + ex);
            ok = false;
        }
        if (!ok) {
            magic.advanced.giveMana(p, cost);
            cds.remove(key);
            return;
        }
        magic.updateTrial(p, Magic.TrialKind.ELEMENT_MASTERY, 1);
        magic.advanced.noteCast(p);
        p.sendActionBar(Magic.c(el.color + "✦ " + ab.name()));
    }

    private boolean run(Player p, Element el, int idx, int t) {
        if (el == Element.SHAPESHIFT) return magic.shapeshift != null && magic.shapeshift.castForm(p, idx, t);
        if (idx >= 3) return moves.run(p, el, idx - 3, t);
        return switch (el) {
            case FIRE -> switch (idx) { case 0 -> fireAura(p, t); case 1 -> fireball(p, t); default -> fireFlight(p, t); };
            case WATER -> switch (idx) { case 0 -> tide(p, t); case 1 -> bubble(p, t); default -> swim(p, t); };
            case EARTH -> switch (idx) { case 0 -> spikes(p, t); case 1 -> earthArmor(p, t); default -> quake(p, t); };
            case WIND -> switch (idx) { case 0 -> gust(p, t); case 1 -> windLeap(p, t); default -> tornado(p, t); };
            case LIGHTNING -> switch (idx) { case 0 -> spark(p, t); case 1 -> thunderDash(p, t); default -> staticField(p, t); };
            case SHADOW -> switch (idx) { case 0 -> shadowStep(p, t); case 1 -> darkStrike(p, t); default -> nightCloak(p, t); };
            case ICE -> switch (idx) { case 0 -> frostNova(p, t); case 1 -> iceVolley(p, t); default -> blizzard(p, t); };
            case METAL -> switch (idx) { case 0 -> steelGuard(p, t); case 1 -> magnet(p, t); default -> ironFist(p, t); };
            case NATURE -> switch (idx) { case 0 -> rootBind(p, t); case 1 -> bloom(p, t); default -> thornBurst(p, t); };
            case LIGHT -> switch (idx) { case 0 -> flash(p, t); case 1 -> lightBeam(p, t); default -> blessing(p, t); };
            case SOUND -> switch (idx) { case 0 -> shockwave(p, t); case 1 -> sonicBoom(p, t); default -> echo(p, t); };
            case CRYSTAL -> switch (idx) { case 0 -> shardVolley(p, t); case 1 -> prismShield(p, t); default -> crystalCage(p, t); };
            case SHAPESHIFT -> false;
        };
    }

    // ------------------------------------------------------------ helpers
    Vector dir(Player p) {
        Vector d = p.getLocation().getDirection();
        if (d.lengthSquared() < 0.0001) return new Vector(0, 0, 1);
        return d.normalize();
    }

    boolean ok(Player p, LivingEntity le) {
        if (le == p || le.isDead() || le instanceof ArmorStand || le instanceof Allay) return false;
        if (le instanceof Player o && o.getGameMode() == GameMode.SPECTATOR) return false;
        if (le instanceof Tameable tm && tm.isTamed() && p.equals(tm.getOwner())) return false;
        return true;
    }

    List<LivingEntity> foes(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : magic.near(p, r)) if (ok(p, le)) out.add(le);
        return out;
    }

    List<LivingEntity> foesAt(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity en : c.getWorld().getNearbyEntities(c, r, r, r))
            if (en instanceof LivingEntity le && ok(p, le)) out.add(le);
        return out;
    }

    List<LivingEntity> cone(Player p, double range, double minDot) {
        List<LivingEntity> out = new ArrayList<>();
        Vector d = dir(p);
        Vector eye = p.getEyeLocation().toVector();
        for (LivingEntity le : foes(p, range)) {
            Vector off = le.getLocation().add(0, 1, 0).toVector().subtract(eye);
            double len = off.length();
            if (len < 0.001 || len > range) continue;
            if (d.dot(off.multiply(1.0 / len)) >= minDot) out.add(le);
        }
        return out;
    }

    LivingEntity firstTarget(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), dir(p), range, 0.8,
                en -> en instanceof LivingEntity le && ok(p, le));
        if (r != null && r.getHitEntity() instanceof LivingEntity le) return le;
        List<LivingEntity> c = cone(p, range, 0.85);
        c.sort(Comparator.comparingDouble(x -> x.getLocation().distanceSquared(p.getLocation())));
        return c.isEmpty() ? null : c.get(0);
    }

    Location aimPoint(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), dir(p), range, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitPosition() != null) return r.getHitPosition().toLocation(p.getWorld());
        return p.getEyeLocation().add(dir(p).multiply(range));
    }

    void hurt(Player p, LivingEntity t, double dmg) {
        if (t == p || t.isDead() || dmg <= 0) return;
        UUID u = p.getUniqueId();
        boolean was = !busy.add(u);
        try { t.damage(dmg, p); }
        finally { if (!was) busy.remove(u); }
    }

    void ring(Location c, double r, int pts, Consumer<Location> f) {
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            f.accept(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
    }

    void fx(Location l, Particle pt, int n, double spread, double speed) {
        l.getWorld().spawnParticle(pt, l, n, spread, spread, spread, speed);
    }

    void snd(Location l, Sound s, float pitch) {
        l.getWorld().playSound(l, s, 1.0f, pitch);
    }

    void effect(LivingEntity e, PotionEffectType type, int ticks, int amp) {
        e.addPotionEffect(new PotionEffect(type, ticks, amp, true, true));
    }

    double scale(int t) { return 1.0 + (t - 1) * 0.2; }

    /** إضاءة فعلية: بنقلل تأثير ضوء السما بالليل عشان الليل يتحسب ظلام. */
    private int lightAt(Block b) {
        int blockL = b.getLightFromBlocks();
        int sky = b.getLightFromSky();
        long time = b.getWorld().getTime();
        boolean day = time < 12300 || time > 23850;
        if (!day) sky = (int) (sky * 0.3);
        return Math.max(blockL, sky);
    }

    private double shadowMult(Player p) {
        int l = lightAt(p.getLocation().getBlock());
        if (l <= 4) return 1.8;
        if (l >= 12) return 0.65;
        return 1.0;
    }

    void projectile(Player p, double speed, int maxTicks, double radius,
                            Consumer<Location> trail, BiConsumer<Location, LivingEntity> end, Vector dirOverride) {
        Vector d = dirOverride != null ? dirOverride.clone().normalize() : dir(p);
        Location pos = p.getEyeLocation().add(d.clone().multiply(0.8));
        Vector v = d.clone().multiply(speed);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ > maxTicks) { cancel(); end.accept(pos.clone(), null); return; }
                pos.add(v);
                trail.accept(pos);
                if (!pos.getBlock().isPassable()) { cancel(); end.accept(pos.clone().subtract(v), null); return; }
                for (Entity en : pos.getWorld().getNearbyEntities(pos, radius, radius, radius)) {
                    if (en instanceof LivingEntity le && ok(p, le)) { cancel(); end.accept(pos.clone(), le); return; }
                }
            }
        }.runTaskTimer(magic, 0L, 1L);
    }

    // ============================================================ FIRE
    private boolean fireAura(Player p, int t) {
        effect(p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
        snd(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f);
        double r = 3.5 + 0.3 * t;
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 16) { cancel(); return; }
                World w = p.getWorld();
                Location c = p.getLocation();
                ring(c.clone().add(0, 0.2, 0), r, 30, l -> w.spawnParticle(Particle.FLAME, l, 2, 0.08, 0.1, 0.08, 0.01));
                ring(c.clone().add(0, 0.2, 0), r * 0.6, 18, l -> w.spawnParticle(Particle.FLAME, l, 1, 0.05, 0.1, 0.05, 0.01));
                w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 3, r / 2, 0.3, r / 2, 0);
                for (LivingEntity le : foes(p, r)) {
                    le.setFireTicks(60 + 20 * t);
                    if (n % 2 == 0) hurt(p, le, 1.0 + 0.4 * t);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean fireball(Player p, int t) {
        snd(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.9f);
        projectile(p, 1.2, 40, 0.9,
                l -> { l.getWorld().spawnParticle(Particle.FLAME, l, 10, 0.18, 0.18, 0.18, 0.02);
                       l.getWorld().spawnParticle(Particle.SMOKE, l, 3, 0.1, 0.1, 0.1, 0.01); },
                (l, hit) -> {
                    World w = l.getWorld();
                    w.spawnParticle(Particle.EXPLOSION, l, 1);
                    w.spawnParticle(Particle.FLAME, l, 60, 1.2, 0.8, 1.2, 0.1);
                    snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f);
                    for (LivingEntity le : foesAt(p, l, 3.5)) {
                        hurt(p, le, 5 + t * 1.2);
                        le.setFireTicks(80 + 20 * t);
                        magic.push(le, l, 0.6, 0.35);
                    }
                }, null);
        return true;
    }

    private boolean fireFlight(Player p, int t) {
        effect(p, PotionEffectType.FIRE_RESISTANCE, 160, 0);
        snd(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f);
        final int max = 40 + 6 * t; // حد أقصى ~3.5 ثانية عشان نظام منع الطيران
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= max || (n > 6 && p.isSneaking())) {
                    if (p.isOnline()) effect(p, PotionEffectType.SLOW_FALLING, 80, 0);
                    cancel();
                    return;
                }
                p.setVelocity(dir(p).multiply(0.95));
                p.setFallDistance(0f);
                World w = p.getWorld();
                Location l = p.getLocation();
                w.spawnParticle(Particle.FLAME, l, 14, 0.25, 0.2, 0.25, 0.04);
                w.spawnParticle(Particle.LAVA, l, 1, 0.2, 0.1, 0.2, 0);
                w.spawnParticle(Particle.SMOKE, l, 4, 0.2, 0.1, 0.2, 0.01);
                if (n % 4 == 0) for (LivingEntity le : foesAt(p, l, 2.2)) { le.setFireTicks(60); hurt(p, le, 1.5); }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    // ============================================================ WATER
    private boolean tide(Player p, int t) {
        World w = p.getWorld();
        Vector d = dir(p);
        for (int i = 1; i <= 7; i++) {
            Location l = p.getEyeLocation().add(d.clone().multiply(i));
            w.spawnParticle(Particle.SPLASH, l, 16, 0.5, 0.4, 0.5, 0.1);
            w.spawnParticle(Particle.DRIPPING_WATER, l, 6, 0.5, 0.4, 0.5, 0);
        }
        snd(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.7f);
        effect(p, PotionEffectType.REGENERATION, 60, 0);
        for (LivingEntity le : cone(p, 7, 0.5)) {
            hurt(p, le, 2 + 0.6 * t);
            effect(le, PotionEffectType.SLOWNESS, 60, 1);
            le.setVelocity(d.clone().multiply(1.0).setY(0.3));
        }
        return true;
    }

    private boolean bubble(Player p, int t) {
        LivingEntity target = firstTarget(p, 18);
        if (target == null) { p.sendActionBar(Magic.c("§cمفيش هدف قدامك")); return false; }
        effect(target, PotionEffectType.SLOWNESS, 50, 4);
        effect(target, PotionEffectType.LEVITATION, 40, 0);
        snd(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (target.isDead() || n++ >= 12) {
                    if (!target.isDead()) {
                        hurt(p, target, 2 + 0.5 * t);
                        target.getWorld().spawnParticle(Particle.SPLASH, target.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.2);
                        snd(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.3f);
                    }
                    cancel();
                    return;
                }
                Location c = target.getLocation().add(0, 1, 0);
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 + n * 0.3;
                    double h = Math.sin(i * 1.3) * 0.9;
                    c.getWorld().spawnParticle(Particle.DRIPPING_WATER, c.clone().add(Math.cos(a) * 1.1, h, Math.sin(a) * 1.1), 1, 0, 0, 0, 0);
                    c.getWorld().spawnParticle(Particle.SPLASH, c.clone().add(Math.cos(a) * 1.1, h, Math.sin(a) * 1.1), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean swim(Player p, int t) {
        effect(p, PotionEffectType.DOLPHINS_GRACE, 160, 1);
        effect(p, PotionEffectType.SPEED, 100, 1);
        effect(p, PotionEffectType.REGENERATION, 80, 0);
        effect(p, PotionEffectType.WATER_BREATHING, 240, 0);
        p.setVelocity(dir(p).multiply(1.1).setY(0.25));
        snd(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.1f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 40) { cancel(); return; }
                p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 0.5, 0), 12, 0.35, 0.4, 0.35, 0.1);
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    // ============================================================ EARTH
    private boolean spikes(Player p, int t) {
        Location base = p.getLocation();
        Vector d = dir(p).setY(0);
        if (d.lengthSquared() < 0.01) d = new Vector(0, 0, 1);
        final Vector flat = d.normalize();
        Set<LivingEntity> hit = new HashSet<>();
        snd(base, Sound.BLOCK_ANVIL_LAND, 0.6f);
        new BukkitRunnable() {
            int i = 1;
            @Override public void run() {
                if (i > 9 || !p.isOnline()) { cancel(); return; }
                Location l = base.clone().add(flat.clone().multiply(i));
                l.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 0.5, 0), 25, 0.3, 0.6, 0.3, 0.1, Material.STONE.createBlockData());
                l.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 1.4, 0), 10, 0.15, 0.4, 0.15, 0.05, Material.DEEPSLATE.createBlockData());
                for (LivingEntity le : foesAt(p, l, 1.4)) {
                    if (!hit.add(le)) continue;
                    hurt(p, le, 4 + t);
                    le.setVelocity(new Vector(0, 0.8, 0));
                }
                i++;
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean earthArmor(Player p, int t) {
        effect(p, PotionEffectType.RESISTANCE, 120, 1);
        effect(p, PotionEffectType.ABSORPTION, 200, 1);
        snd(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f);
        World w = p.getWorld();
        ring(p.getLocation().add(0, 0.3, 0), 1.2, 20, l -> w.spawnParticle(Particle.BLOCK, l, 8, 0.1, 0.5, 0.1, 0.05, Material.DIRT.createBlockData()));
        w.spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05, Material.STONE.createBlockData());
        return true;
    }

    private boolean quake(Player p, int t) {
        Location c = p.getLocation();
        snd(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f);
        double maxR = 5 + 0.4 * t;
        Set<LivingEntity> hit = new HashSet<>();
        new BukkitRunnable() {
            int n = 1;
            @Override public void run() {
                if (n > 3) { cancel(); return; }
                double r = maxR * n / 3.0;
                World w = c.getWorld();
                ring(c.clone().add(0, 0.2, 0), r, (int) (r * 8), l -> w.spawnParticle(Particle.BLOCK, l, 4, 0.15, 0.2, 0.15, 0.05, Material.COARSE_DIRT.createBlockData()));
                for (LivingEntity le : foesAt(p, c, r + 0.5)) {
                    if (!hit.add(le)) continue;
                    hurt(p, le, 3 + t);
                    magic.push(le, c, 0.6, 0.8);
                }
                n++;
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    // ============================================================ WIND
    private boolean gust(Player p, int t) {
        World w = p.getWorld();
        Vector d = dir(p);
        for (int i = 1; i <= 7; i++) {
            Location l = p.getEyeLocation().add(d.clone().multiply(i));
            w.spawnParticle(Particle.CLOUD, l, 8, 0.2 + i * 0.1, 0.2 + i * 0.07, 0.2 + i * 0.1, 0.08);
        }
        snd(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f);
        effect(p, PotionEffectType.SPEED, 60, 1);
        for (LivingEntity le : cone(p, 7, 0.4)) {
            hurt(p, le, 1 + 0.3 * t);
            le.setVelocity(d.clone().multiply(1.3).setY(0.5));
        }
        return true;
    }

    private boolean windLeap(Player p, int t) {
        Vector d = dir(p).setY(0);
        if (d.lengthSquared() > 0.01) d.normalize().multiply(0.9);
        p.setVelocity(d.setY(1.1 + 0.05 * t));
        effect(p, PotionEffectType.SLOW_FALLING, 100, 0);
        snd(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 40, 0.5, 0.1, 0.5, 0.1);
        return true;
    }

    private boolean tornado(Player p, int t) {
        Location c = aimPoint(p, 12);
        snd(c, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 40) { cancel(); return; }
                World w = c.getWorld();
                for (int i = 0; i < 14; i++) {
                    double h = i * 0.4;
                    double r = 0.4 + h * 0.35;
                    double a = n * 0.5 + i * 0.9;
                    w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
                for (LivingEntity le : foesAt(p, c, 4.5)) {
                    Vector pull = c.toVector().subtract(le.getLocation().toVector()).setY(0).multiply(0.15);
                    le.setVelocity(pull.setY(0.38));
                    if (n % 10 == 0) hurt(p, le, 1 + 0.3 * t);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    // ============================================================ LIGHTNING
    private boolean spark(Player p, int t) {
        LivingEntity target = firstTarget(p, 20);
        if (target == null) { p.sendActionBar(Magic.c("§cمفيش هدف قدامك")); return false; }
        World w = target.getWorld();
        w.strikeLightningEffect(target.getLocation());
        w.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.15);
        hurt(p, target, 5 + 1.2 * t);
        for (LivingEntity other : foesAt(p, target.getLocation(), 4.5)) {
            if (other == target) continue;
            w.spawnParticle(Particle.ELECTRIC_SPARK, other.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.1);
            hurt(p, other, 2.5 + 0.5 * t);
        }
        return true;
    }

    private boolean thunderDash(Player p, int t) {
        Vector d = dir(p);
        p.setVelocity(d.clone().multiply(1.5 + 0.15 * t).setY(Math.max(0.1, d.getY() * 0.6)));
        effect(p, PotionEffectType.SPEED, 60, 1);
        snd(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f);
        Set<LivingEntity> hit = new HashSet<>();
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                Location l = p.getLocation().add(0, 1, 0);
                l.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, l, 25, 0.4, 0.6, 0.4, 0.2);
                for (LivingEntity le : foesAt(p, l, 2.2)) {
                    if (!hit.add(le)) continue;
                    le.getWorld().strikeLightningEffect(le.getLocation());
                    hurt(p, le, 3 + t);
                }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean staticField(Player p, int t) {
        effect(p, PotionEffectType.SPEED, 100, 1);
        snd(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 10) { cancel(); return; }
                Location c = p.getLocation();
                World w = c.getWorld();
                ring(c.clone().add(0, 0.2, 0), 5.5, 24, l -> w.spawnParticle(Particle.ELECTRIC_SPARK, l, 2, 0.1, 0.3, 0.1, 0.05));
                List<LivingEntity> f = foes(p, 6);
                f.sort(Comparator.comparingDouble(x -> x.getLocation().distanceSquared(c)));
                for (int i = 0; i < Math.min(2, f.size()); i++) {
                    LivingEntity le = f.get(i);
                    Vector a = c.clone().add(0, 1, 0).toVector();
                    Vector b = le.getLocation().add(0, 1, 0).toVector();
                    double len = a.distance(b);
                    Vector step = b.clone().subtract(a).normalize().multiply(0.5);
                    Location cur = c.clone().add(0, 1, 0);
                    for (double s = 0; s < len; s += 0.5) { w.spawnParticle(Particle.ELECTRIC_SPARK, cur, 1, 0.05, 0.05, 0.05, 0); cur.add(step); }
                    hurt(p, le, 2 + 0.5 * t);
                }
            }
        }.runTaskTimer(magic, 0L, 10L);
        return true;
    }

    // ============================================================ SHADOW
    private Location findShadow(Player p) {
        Location base = p.getLocation();
        World w = base.getWorld();
        Vector d = dir(p).setY(0);
        if (d.lengthSquared() < 0.01) d = new Vector(0, 0, 1);
        d.normalize();
        Location best = null;
        double bestScore = -1e9;
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 3 || dist > 16) continue;
                double dot = (dx * d.getX() + dz * d.getZ()) / dist;
                if (dot < 0.25) continue;
                int x = bx + dx, z = bz + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int dy = 3; dy >= -4; dy--) {
                    Block feet = w.getBlockAt(x, by + dy, z);
                    if (!feet.isPassable() || feet.isLiquid()) continue;
                    Block head = feet.getRelative(0, 1, 0);
                    Block ground = feet.getRelative(0, -1, 0);
                    if (!head.isPassable() || head.isLiquid() || !ground.getType().isSolid()) continue;
                    if (lightAt(feet) <= 7) {
                        double score = dot * 2 + dist / 16.0 - Math.abs(dy) * 0.05;
                        if (score > bestScore) { bestScore = score; best = feet.getLocation().add(0.5, 0, 0.5); }
                    }
                    break;
                }
            }
        }
        if (best != null) { best.setYaw(base.getYaw()); best.setPitch(base.getPitch()); }
        return best;
    }

    private boolean shadowStep(Player p, int t) {
        Location dest = findShadow(p);
        if (dest == null) { p.sendActionBar(Magic.c("§cمفيش ظل قريب قدامك")); return false; }
        World w = p.getWorld();
        w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        snd(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f);
        p.teleport(dest);
        w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.2);
        snd(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f);
        effect(p, PotionEffectType.INVISIBILITY, 40, 0);
        if (shadowMult(p) > 1.0) effect(p, PotionEffectType.SPEED, 60, 1);
        return true;
    }

    private boolean darkStrike(Player p, int t) {
        double mult = shadowMult(p);
        World w = p.getWorld();
        Vector d = dir(p);
        for (int i = 1; i <= 6; i++) {
            Location l = p.getEyeLocation().add(d.clone().multiply(i));
            w.spawnParticle(Particle.SQUID_INK, l, 6, 0.3, 0.3, 0.3, 0.02);
            w.spawnParticle(Particle.PORTAL, l, 14, 0.4, 0.4, 0.4, 0.3);
        }
        snd(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f + (float) (mult * 0.2));
        for (LivingEntity le : cone(p, 6, 0.5)) {
            hurt(p, le, (3 + t * 1.3) * mult);
            effect(le, PotionEffectType.BLINDNESS, (int) (60 * mult), 0);
            if (mult > 1.0) effect(le, PotionEffectType.WITHER, 60, 0);
        }
        p.sendActionBar(Magic.c(mult > 1.0 ? "§5الظلام بيقوّي ضربتك!" : mult < 1.0 ? "§7النور بيضعّف ضربتك" : "§7ضربة الظلام"));
        return true;
    }

    private boolean nightCloak(Player p, int t) {
        World w = p.getWorld();
        w.spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 50, 0.5, 0.9, 0.5, 0.05);
        if (shadowMult(p) > 1.0) {
            effect(p, PotionEffectType.INVISIBILITY, 200, 0);
            effect(p, PotionEffectType.SPEED, 200, 1);
            effect(p, PotionEffectType.STRENGTH, 200, 1);
            effect(p, PotionEffectType.NIGHT_VISION, 260, 0);
            snd(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f);
            p.sendActionBar(Magic.c("§5العباءة كاملة — الضلمة معاك"));
        } else {
            effect(p, PotionEffectType.INVISIBILITY, 60, 0);
            p.sendActionBar(Magic.c("§7مفيش ضلمة كفاية — العباءة أضعف"));
        }
        return true;
    }

    // ============================================================ ICE
    private boolean frostNova(Player p, int t) {
        Location c = p.getLocation();
        double r = 4.5 + 0.3 * t;
        snd(c, Sound.BLOCK_GLASS_BREAK, 0.8f);
        World w = c.getWorld();
        new BukkitRunnable() {
            int n = 1;
            @Override public void run() {
                if (n > 4) { cancel(); return; }
                double rr = r * n / 4.0;
                ring(c.clone().add(0, 0.3, 0), rr, (int) (rr * 8), l -> w.spawnParticle(Particle.SNOWFLAKE, l, 3, 0.1, 0.2, 0.1, 0.02));
                n++;
            }
        }.runTaskTimer(magic, 0L, 2L);
        for (LivingEntity le : foesAt(p, c, r)) {
            le.setFreezeTicks(le.getMaxFreezeTicks() + 60);
            effect(le, PotionEffectType.SLOWNESS, 80, 2);
            hurt(p, le, 2 + 0.5 * t);
        }
        return true;
    }

    private boolean iceVolley(Player p, int t) {
        snd(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.4f);
        for (int a = -12; a <= 12; a += 12) {
            Vector d = dir(p).rotateAroundY(Math.toRadians(a));
            projectile(p, 1.3, 30, 0.8,
                    l -> l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 6, 0.08, 0.08, 0.08, 0.01),
                    (l, hit) -> {
                        l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 30, 0.4, 0.4, 0.4, 0.1);
                        if (hit != null) {
                            hurt(p, hit, 3 + 0.8 * t);
                            effect(hit, PotionEffectType.SLOWNESS, 60, 1);
                            hit.setFreezeTicks(hit.getMaxFreezeTicks() + 40);
                        }
                    }, d);
        }
        return true;
    }

    private boolean blizzard(Player p, int t) {
        Location c = aimPoint(p, 8);
        snd(c, Sound.ENTITY_PLAYER_SPLASH, 0.5f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 20) { cancel(); return; }
                World w = c.getWorld();
                w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 2, 0), 40, 3.5, 1.8, 3.5, 0.05);
                w.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.5, 0), 6, 3, 0.3, 3, 0.02);
                for (LivingEntity le : foesAt(p, c, 4.5)) {
                    effect(le, PotionEffectType.SLOWNESS, 30, 2);
                    le.setFreezeTicks(le.getMaxFreezeTicks() + 20);
                    if (n % 4 == 0) hurt(p, le, 1 + 0.3 * t);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    // ============================================================ METAL
    private boolean steelGuard(Player p, int t) {
        effect(p, PotionEffectType.RESISTANCE, 100, 2);
        snd(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f);
        World w = p.getWorld();
        Location c = p.getLocation();
        ring(c.clone().add(0, 1, 0), 1.3, 24, l -> w.spawnParticle(Particle.CRIT, l, 3, 0.05, 0.4, 0.05, 0.02));
        for (LivingEntity le : foesAt(p, c, 3.0)) magic.push(le, c, 0.9, 0.25);
        return true;
    }

    private boolean magnet(Player p, int t) {
        List<LivingEntity> list = cone(p, 8, 0.3);
        if (list.isEmpty()) { p.sendActionBar(Magic.c("§cمفيش حد قدامك")); return false; }
        snd(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f);
        for (LivingEntity le : list) {
            Vector pull = p.getLocation().toVector().subtract(le.getLocation().toVector()).multiply(0.25);
            if (pull.length() > 1.6) pull.normalize().multiply(1.6);
            le.setVelocity(pull.setY(0.2));
            hurt(p, le, 2 + 0.5 * t);
            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.2);
        }
        return true;
    }

    private boolean ironFist(Player p, int t) {
        Vector d = dir(p);
        p.setVelocity(d.clone().multiply(1.2).setY(0.15));
        snd(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                Location l = p.getLocation();
                l.getWorld().spawnParticle(Particle.CRIT, l.clone().add(0, 1, 0).add(dir(p).multiply(1.2)), 40, 0.5, 0.5, 0.5, 0.3);
                snd(l, Sound.BLOCK_ANVIL_LAND, 1.0f);
                for (LivingEntity le : cone(p, 3.5, 0.2)) {
                    hurt(p, le, 6 + 1.4 * t);
                    magic.push(le, l, 1.2, 0.4);
                }
            }
        }.runTaskLater(magic, 6L);
        return true;
    }

    // ============================================================ NATURE
    private boolean rootBind(Player p, int t) {
        List<LivingEntity> list = foes(p, 6);
        if (list.isEmpty()) { p.sendActionBar(Magic.c("§cمفيش حد قريب")); return false; }
        snd(p.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.6f);
        for (LivingEntity le : list) {
            effect(le, PotionEffectType.SLOWNESS, 100, 5);
            effect(le, PotionEffectType.POISON, 60, 0);
            hurt(p, le, 1);
            Location l = le.getLocation();
            l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, 0.4, 0), 25, 0.4, 0.4, 0.4, 0.05);
            l.getWorld().spawnParticle(Particle.COMPOSTER, l.clone().add(0, 0.2, 0), 20, 0.4, 0.2, 0.4, 0.1);
        }
        return true;
    }

    private boolean bloom(Player p, int t) {
        Location c = p.getLocation();
        snd(c, Sound.ITEM_BONE_MEAL_USE, 1.2f);
        World w = c.getWorld();
        ring(c.clone().add(0, 0.2, 0), 5, 36, l -> w.spawnParticle(Particle.HAPPY_VILLAGER, l, 2, 0.1, 0.3, 0.1, 0.02));
        w.spawnParticle(Particle.CHERRY_LEAVES, c.clone().add(0, 1.5, 0), 40, 2.5, 1, 2.5, 0.02);
        effect(p, PotionEffectType.REGENERATION, 100, 1);
        effect(p, PotionEffectType.INSTANT_HEALTH, 1, 0);
        for (Entity en : w.getNearbyEntities(c, 5, 5, 5)) {
            if (en instanceof Player o && o != p && o.getGameMode() != GameMode.SPECTATOR) {
                effect(o, PotionEffectType.REGENERATION, 100, 1);
                effect(o, PotionEffectType.INSTANT_HEALTH, 1, 0);
            }
        }
        return true;
    }

    private boolean thornBurst(Player p, int t) {
        Location c = p.getLocation();
        snd(c, Sound.ENTITY_GENERIC_EXPLODE, 1.6f);
        World w = c.getWorld();
        w.spawnParticle(Particle.CRIT, c.clone().add(0, 1, 0), 80, 2.2, 0.8, 2.2, 0.4);
        w.spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 1, 0), 40, 2.2, 0.8, 2.2, 0.1);
        for (LivingEntity le : foesAt(p, c, 5)) {
            hurt(p, le, 3 + 0.8 * t);
            effect(le, PotionEffectType.POISON, 80, 0);
            magic.push(le, c, 0.7, 0.3);
        }
        return true;
    }

    // ============================================================ LIGHT
    private boolean flash(Player p, int t) {
        Location c = p.getLocation();
        World w = c.getWorld();
        w.spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 1);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.2);
        snd(c, Sound.BLOCK_BEACON_ACTIVATE, 1.8f);
        effect(p, PotionEffectType.SPEED, 80, 1);
        for (LivingEntity le : foesAt(p, c, 8)) {
            effect(le, PotionEffectType.BLINDNESS, 60 + 10 * t, 0);
            effect(le, PotionEffectType.GLOWING, 100, 0);
        }
        return true;
    }

    private boolean lightBeam(Player p, int t) {
        World w = p.getWorld();
        Vector d = dir(p);
        Location eye = p.getEyeLocation();
        Set<LivingEntity> hit = new HashSet<>();
        snd(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f);
        for (double i = 1; i <= 15; i += 0.5) {
            Location l = eye.clone().add(d.clone().multiply(i));
            if (!l.getBlock().isPassable()) break;
            w.spawnParticle(Particle.END_ROD, l, 2, 0.03, 0.03, 0.03, 0);
            for (LivingEntity le : foesAt(p, l, 1.0)) if (hit.add(le)) hurt(p, le, 5 + 1.2 * t);
        }
        return true;
    }

    private boolean blessing(Player p, int t) {
        for (PotionEffectType bad : new PotionEffectType[]{PotionEffectType.POISON, PotionEffectType.WITHER,
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA})
            p.removePotionEffect(bad);
        p.setFireTicks(0);
        effect(p, PotionEffectType.INSTANT_HEALTH, 1, 0);
        effect(p, PotionEffectType.REGENERATION, 100, 1);
        effect(p, PotionEffectType.ABSORPTION, 100, 0);
        Location c = p.getLocation();
        snd(c, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
        World w = c.getWorld();
        ring(c.clone().add(0, 0.1, 0), 1.4, 24, l -> w.spawnParticle(Particle.END_ROD, l, 2, 0.05, 1.2, 0.05, 0.02));
        w.spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0);
        return true;
    }

    // ============================================================ SOUND
    private boolean shockwave(Player p, int t) {
        World w = p.getWorld();
        Vector d = dir(p);
        for (int i = 1; i <= 8; i++) {
            Location l = p.getEyeLocation().add(d.clone().multiply(i));
            w.spawnParticle(Particle.NOTE, l, 5, 0.2 + i * 0.08, 0.2 + i * 0.05, 0.2 + i * 0.08, 1);
        }
        snd(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f);
        for (LivingEntity le : cone(p, 8, 0.3)) {
            hurt(p, le, 2 + 0.5 * t);
            le.setVelocity(d.clone().multiply(1.6).setY(0.4));
        }
        return true;
    }

    private boolean sonicBoom(Player p, int t) {
        World w = p.getWorld();
        Vector d = dir(p);
        Location eye = p.getEyeLocation();
        Set<LivingEntity> hit = new HashSet<>();
        snd(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f);
        for (double i = 1; i <= 14; i += 1.5) {
            Location l = eye.clone().add(d.clone().multiply(i));
            if (!l.getBlock().isPassable()) break;
            w.spawnParticle(Particle.SONIC_BOOM, l, 1);
            for (LivingEntity le : foesAt(p, l, 1.5)) {
                if (!hit.add(le)) continue;
                hurt(p, le, 5 + 1.2 * t);
                effect(le, PotionEffectType.NAUSEA, 100, 0);
            }
        }
        return true;
    }

    private boolean echo(Player p, int t) {
        snd(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 3) { cancel(); return; }
                Location c = p.getLocation();
                World w = c.getWorld();
                ring(c.clone().add(0, 1, 0), 2 + n, 30, l -> w.spawnParticle(Particle.NOTE, l, 1, 0, 0, 0, 1));
                snd(c, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f + n * 0.3f);
                for (LivingEntity le : foesAt(p, c, 5)) {
                    hurt(p, le, 1.5 + 0.4 * t);
                    magic.push(le, c, 0.35, 0.15);
                }
            }
        }.runTaskTimer(magic, 0L, 12L);
        return true;
    }

    // ============================================================ CRYSTAL
    private boolean shardVolley(Player p, int t) {
        snd(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.2f);
        for (int a = -20; a <= 20; a += 10) {
            Vector d = dir(p).rotateAroundY(Math.toRadians(a));
            projectile(p, 1.4, 25, 0.8,
                    l -> { l.getWorld().spawnParticle(Particle.END_ROD, l, 2, 0.03, 0.03, 0.03, 0);
                           l.getWorld().spawnParticle(Particle.DUST, l, 3, 0.05, 0.05, 0.05, 0, dust); },
                    (l, hit) -> {
                        l.getWorld().spawnParticle(Particle.END_ROD, l, 15, 0.3, 0.3, 0.3, 0.1);
                        if (hit != null) {
                            hurt(p, hit, 2.5 + 0.7 * t);
                            effect(hit, PotionEffectType.SLOWNESS, 40, 0);
                        }
                    }, d);
        }
        return true;
    }

    private boolean prismShield(Player p, int t) {
        effect(p, PotionEffectType.ABSORPTION, 200, 2);
        effect(p, PotionEffectType.RESISTANCE, 160, 0);
        snd(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 120, 255), 1.1f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 30) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                World w = c.getWorld();
                double a = n * 0.45;
                for (int i = 0; i < 6; i++) {
                    double ang = a + i * Math.PI / 3;
                    double yy = (i % 2 == 0) ? 0.9 : -0.9;
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * 1.1, yy * 0.6, Math.sin(ang) * 1.1), 2, 0.02, 0.02, 0.02, 0, dust);
                    w.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(ang) * 1.1, 0, Math.sin(ang) * 1.1), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean crystalCage(Player p, int t) {
        LivingEntity target = firstTarget(p, 14);
        if (target == null) { p.sendActionBar(Magic.c("§cمفيش هدف قدامك")); return false; }
        effect(target, PotionEffectType.SLOWNESS, 70, 6);
        effect(target, PotionEffectType.GLOWING, 70, 0);
        snd(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 240, 255), 1.3f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (target.isDead() || n++ >= 12) {
                    if (!target.isDead()) {
                        hurt(p, target, 2 + 0.5 * t);
                        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.15);
                        snd(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f);
                    }
                    cancel();
                    return;
                }
                Location c = target.getLocation();
                World w = c.getWorld();
                for (int y = 0; y < 3; y++)
                    ring(c.clone().add(0, 0.2 + y * 0.8, 0), 1.0, 8, l -> w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dust));
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    // ============================================================ left-click strike effects
    void strikeEffect(Player p, Element el, LivingEntity t, int tier) {
        Location l = t.getLocation().add(0, 1, 0);
        World w = l.getWorld();
        w.spawnParticle(el.particle, l, 14, 0.3, 0.4, 0.3, 0.05);
        switch (el) {
            case FIRE -> t.setFireTicks(60 + 10 * tier);
            case WATER -> { effect(t, PotionEffectType.SLOWNESS, 40, 0); effect(p, PotionEffectType.REGENERATION, 40, 0); }
            case EARTH -> t.setVelocity(new Vector(0, 0.45, 0));
            case WIND -> magic.push(t, p.getLocation(), 0.9, 0.3);
            case LIGHTNING -> {
                w.strikeLightningEffect(t.getLocation());
                for (LivingEntity o : foesAt(p, t.getLocation(), 4)) if (o != t) { hurt(p, o, 2); break; }
            }
            case SHADOW -> effect(t, PotionEffectType.BLINDNESS, (int) (40 * shadowMult(p)), 0);
            case ICE -> { t.setFreezeTicks(t.getMaxFreezeTicks() + 40); effect(t, PotionEffectType.SLOWNESS, 50, 1); }
            case METAL -> magic.push(t, p.getLocation(), 0.5, 0.2);
            case NATURE -> effect(t, PotionEffectType.POISON, 60, 0);
            case LIGHT -> { effect(t, PotionEffectType.GLOWING, 100, 0); effect(t, PotionEffectType.BLINDNESS, 30, 0); }
            case SOUND -> { effect(t, PotionEffectType.NAUSEA, 60, 0); magic.push(t, p.getLocation(), 0.6, 0.2); }
            case CRYSTAL -> effect(t, PotionEffectType.SLOWNESS, 60, 1);
            case SHAPESHIFT -> { if (magic.shapeshift != null) magic.shapeshift.onMelee(p, t, tier); }
        }
    }
}
