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
import org.bukkit.inventory.EquipmentSlot;
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
 * عشان كليك يمين يشتغل في الهوا على Java وBedrock/Geyser، بنحط "قلب القوة"
 * مؤقتًا في اليد الأساسية (Main Hand) وقت التفعيل، ونحفظ العنصر الأصلي ونرجّعه
 * لنفس خانة الهوتبار عند إيقاف الوضع. وجود عنصر في اليد الأساسية يضمن إرسال تفاعل
 * RIGHT_CLICK_AIR بدل الاعتماد على اليد الفاضية أو دعم Offhand في عميل Bedrock.
 */
final class PowerMode implements Listener {

    record Ab(String name, String desc, double cost, int baseIndex, int evolutionRank) {
        Ab(String name, String desc, double cost) {
            this(name, desc, cost, -1, 0);
        }
    }

    static final Map<Element, Ab[]> ABS = new EnumMap<>(Element.class);

    static {
        ABS.put(Element.FIRE, new Ab[]{
                new Ab("حلقة اللهب", "النار تنتشر حواليك وتحرق اللي يقرب", 6),
                new Ab("كرة النار", "قذيفة نار تنفجر عند الهدف", 4),
                new Ab("طيران النار", "تطير بالنار في اتجاه نظرك (Shift لإيقافه)", 8)});
        ABS.put(Element.WATER, new Ab[]{
                new Ab("موجة المد", "موجة مية تدفع وتبطّئ قدامك وتشفيك", 4),
                new Ab("سجن الفقاعة", "فقاعة تحبس الهدف وترفعه", 5),
                new Ab("سباحة الموج", "سرعة مائية وتنفس تحت الماء", 5)});
        ABS.put(Element.EARTH, new Ab[]{
                new Ab("رمح الصخر", "رماح صخر تطلع من الأرض قدامك", 4),
                new Ab("درع الأرض", "درع صخري يقلل الضرر", 5),
                new Ab("الزلزال", "موجات زلزال ترمي اللي حواليك", 7)});
        ABS.put(Element.WIND, new Ab[]{
                new Ab("هبّة الرياح", "هبة قوية تزق اللي قدامك", 3),
                new Ab("قفزة الريح", "قفزة عالية مع سقوط آمن", 3),
                new Ab("الإعصار", "إعصار يشفط الأعداء ويرفعهم", 7)});
        ABS.put(Element.LIGHTNING, new Ab[]{
                new Ab("الشرارة", "صاعقة على الهدف وتتفرع لجنبه", 4),
                new Ab("اندفاعة البرق", "اندفاعة سريعة تصعق اللي في طريقك", 5),
                new Ab("المجال الساكن", "مجال كهربي يصعق الأقرب ليك", 7)});
        ABS.put(Element.SHADOW, new Ab[]{
                new Ab("خطوة الظل", "تنتقل لأقرب مكان مظلم/فيه ظل", 5),
                new Ab("ضربة الظلام", "ضربة أقوى كل ما المكان أضلم", 4),
                new Ab("عباءة الظلام", "اختفاء وقوة خارقة في الضلمة", 6)});
        ABS.put(Element.ICE, new Ab[]{
                new Ab("نوفا الصقيع", "موجة برد تجمّد اللي حواليك", 5),
                new Ab("وابل الجليد", "ثلاث شظايا جليد قدام", 4),
                new Ab("عاصفة الثلج", "عاصفة ثلج تبطّئ وتجمّد منطقة", 7)});
        ABS.put(Element.METAL, new Ab[]{
                new Ab("درع الفولاذ", "درع حديد يقلل الضرر ويزق الأعداء", 5),
                new Ab("المغناطيس", "يسحب الأعداء ناحيتك", 5),
                new Ab("قبضة الحديد", "اندفاعة وضربة ثقيلة", 6)});
        ABS.put(Element.NATURE, new Ab[]{
                new Ab("قيد الجذور", "جذور تقيّد وتسمّم الأعداء", 5),
                new Ab("زهرة الشفاء", "تشفيك أنت واللاعبين القريبين", 6),
                new Ab("انفجار الأشواك", "أشواك تنفجر حواليك", 5)});
        ABS.put(Element.LIGHT, new Ab[]{
                new Ab("وميضة", "وميض يعمي الأعداء ويديك سرعة", 4),
                new Ab("شعاع النور", "شعاع طويل يخترق الأعداء", 5),
                new Ab("نعمة النور", "تشفيك وتشيل التأثيرات السيئة", 6)});
        ABS.put(Element.SOUND, new Ab[]{
                new Ab("موجة الصدمة", "صدمة صوتية تزق قدامك", 3),
                new Ab("الانفجار الصوتي", "شعاع صوتي مدمّر", 5),
                new Ab("الصدى", "ثلاث نبضات صدى حواليك", 6)});
        ABS.put(Element.CRYSTAL, new Ab[]{
                new Ab("وابل الشظايا", "خمس شظايا بلور", 4),
                new Ab("درع المنشور", "درع بلور يمتص الضرر", 5),
                new Ab("قفص الكريستال", "يحبس الهدف داخل قفص بلور", 6)});
        ABS.put(Element.SHAPESHIFT, new Ab[]{
                new Ab("شبكة الصياد", "قدرة سبايدر: شبكة تثبّت الأعداء وتمنعهم من القفز", 5),
                new Ab("الانفجار المشحون", "قدرة كريبر: انفجار دائري بلا تكسير للبلوكات", 9),
                new Ab("وابل العظام", "قدرة سكيلتون: ثلاث سهام دقيقة في مروحة", 5),
                new Ab("القذيفة اللهبية", "قدرة بليز: كرة نارية ومقاومة للنار", 6),
                new Ab("قذيفة الجاست", "قدرة جاست: قذيفة ثقيلة بلا تخريب للبيئة", 10),
                new Ab("الزئير الصوتي", "قدرة واردن: موجة صوتية تخترق المسار", 12),
                new Ab("ضربة الحارس", "قدرة آيرون جولم: صدمة أرضية ودرع حديدي", 8),
                new Ab("أنياب الاستدعاء", "قدرة إيفوكر: صف من الأنياب السحرية", 9),
                new Ab("زفير التنين", "قدرة تنين الإند: كرة تنين وسحابة تنفس", 14),
                new Ab("خطوة الفراغ", "قدرة إندرمان: انتقال خلف الهدف وضربة ظل", 8)});
        // أول 10 حركات أصلية، ثم 40 نسخة متطورة بميكانيكية إضافية خاصة بكل عنصر.
        for (Element e : Element.values()) {
            Ab[] base = ABS.get(e);
            Moves.Mv[] ex = Moves.MV.getOrDefault(e, new Moves.Mv[0]);
            Ab[] core = new Ab[base.length + ex.length];
            for (int i = 0; i < base.length; i++) {
                Ab a = base[i];
                core[i] = new Ab(a.name(), a.desc(), a.cost(), i, 0);
            }
            for (int i = 0; i < ex.length; i++) {
                int index = base.length + i;
                core[index] = new Ab(ex[i].name(), ex[i].desc(), ex[i].cost(), index, 0);
            }
            if (core.length != 10) {
                throw new IllegalStateException("Each element must define exactly 10 core moves: " + e + " has " + core.length);
            }
            Ab[] progression = new Ab[50];
            for (int i = 0; i < 50; i++) {
                int baseIndex = i < 10 ? i : (i - 10) % 10;
                int rank = i < 10 ? 0 : 1 + (i - 10) / 10;
                Ab original = core[baseIndex];
                if (rank == 0) {
                    progression[i] = original;
                } else {
                    String prefix = switch (rank) {
                        case 1 -> "النسخة المتطورة";
                        case 2 -> "النسخة المعززة";
                        case 3 -> "النسخة المتقدمة";
                        default -> "النسخة القصوى";
                    };
                    String description = original.desc() + " — تطور " + rank
                            + ": تأثير إضافي مميز لعنصر " + e.ar;
                    progression[i] = new Ab(prefix + " · " + original.name(), description,
                            original.cost() * (1.0 + rank * 0.15), baseIndex, rank);
                }
            }
            ABS.put(e, progression);
        }
    }

    private final Magic magic;
    private final Moves moves;
    private final NamespacedKey coreKey;
    private final File file;
    private final YamlConfiguration store;
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, Integer> selected = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, BukkitRunnable> flights = new HashMap<>();
    // حماية صغيرة من تكرار نفس حزمة التفاعل خلال 5ms فقط؛ ليست كولداون للقدرات.
    private final Map<UUID, Long> lastInteractNanos = new HashMap<>();
    private record AffinityCache(long checkedAt, Element element, boolean empowered) {}
    private final Map<String, AffinityCache> affinityCache = new HashMap<>();
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
        for (UUID u : new ArrayList<>(flights.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(u);
            stopFlight(player, false, null);
        }
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
        lore.add(Magic.c("§7كليك يمين في الهوا أو على بلوك عادي: استخدم القدرة"));
        lore.add(Magic.c("§7سكرول: بدّل القدرة | كليك شمال: ضربة بقوتك"));
        lore.add(Magic.c("§7F: إيقاف الوضع | /gwm use: استخدام بديل"));
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

    /** يعيد العنصر الأصلي إلى خانة الهوتبار بعد إعادة تشغيل السيرفر أو الخروج. */
    private void cleanup(Player p) {
        String u = p.getUniqueId().toString();
        if (!active.contains(p.getUniqueId())) {
            if (store.getBoolean("active." + u, false) || hasCore(p)) restoreMainHand(p);
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
        int heldSlot = inv.getHeldItemSlot();
        ItemStack main = inv.getItemInMainHand();
        boolean has = main != null && main.getType() != Material.AIR && !isCore(main);
        store.set("saved-main." + id, has ? main.clone() : null);
        store.set("saved-slot." + id, heldSlot);
        store.set("active." + id, true);
        saveStore();
        // وجود قلب القوة في اليد الأساسية يجعل RIGHT_CLICK_AIR موثوقًا
        // حتى على عملاء Bedrock عبر Geyser الذين لا يعتمدون على Offhand.
        inv.setItemInMainHand(makeCore(el));
        active.add(id);
        selected.putIfAbsent(id, 0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
        p.getWorld().spawnParticle(el.particle, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
        p.sendMessage(Magic.c(el.color + "✦ وضع القوة شغّال §7— كليك يمين في الهوا لاستخدام القدرة | سكرول للتبديل | F للإيقاف"));
        showSelected(p);
    }

    private void disable(Player p, boolean silent) {
        UUID id = p.getUniqueId();
        stopFlight(p, false, null);
        if (!active.remove(id)) return;
        restoreMainHand(p);
        if (!silent) {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.2f);
            p.sendActionBar(Magic.c("§7✦ وضع القوة اتقفل"));
        }
    }

    private void restoreMainHand(Player p) {
        UUID id = p.getUniqueId();
        PlayerInventory inv = p.getInventory();
        ItemStack saved = store.getItemStack("saved-main." + id);
        int slot = store.getInt("saved-slot." + id, inv.getHeldItemSlot());
        stripCores(p);

        // توافق مع ملفات 2.3.7/2.3.8 القديمة التي كانت تحفظ العنصر الأصلي في Offhand.
        ItemStack legacyOffhand = store.getItemStack("saved." + id);
        if (saved != null && saved.getType() != Material.AIR) {
            int safeSlot = Math.max(0, Math.min(8, slot));
            ItemStack current = inv.getItem(safeSlot);
            // لو السيرفر حفظ اللاعب قبل وضع قلب القوة، العنصر الأصلي ما زال في مكانه؛
            // لا نضيف نسخة ثانية منه أثناء استعادة حالة التحديث/إعادة التشغيل.
            boolean alreadyRestored = current != null && current.getType() != Material.AIR
                    && current.isSimilar(saved) && current.getAmount() == saved.getAmount();
            if (!alreadyRestored) {
                if (current == null || current.getType() == Material.AIR) {
                    inv.setItem(safeSlot, saved);
                } else {
                    for (ItemStack left : inv.addItem(saved).values())
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
            }
        }
        if (legacyOffhand != null && legacyOffhand.getType() != Material.AIR) {
            if (inv.getItemInOffHand().getType() == Material.AIR) inv.setItemInOffHand(legacyOffhand);
            else for (ItemStack left : inv.addItem(legacyOffhand).values())
                p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        store.set("saved-main." + id, null);
        store.set("saved-slot." + id, null);
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
        Ab ability = ABS.get(el)[idx];
        int baseIndex = ability.baseIndex();
        List<Double> costs = magic.getConfig().getDoubleList("power.costs." + el.name());
        double base = costs.size() > baseIndex ? costs.get(baseIndex) : ability.cost();
        if (ability.evolutionRank() > 0) base *= (1.0 + ability.evolutionRank() * 0.15);
        if (el == Element.FIRE && baseIndex == 2)
            base = magic.getConfig().getDouble("flight.fire.activation-cost", 4.0);
        if (el == Element.WIND && baseIndex == 3)
            base = magic.getConfig().getDouble("flight.wind.activation-cost", 0.5);
        return Math.max(0, base * magic.getConfig().getDouble("power.cost-multiplier", 1.0));
    }

    void useSelected(Player p) { cast(p); }

    int selectedIndex(Player p) {
        Element element = magic.getEl(p);
        if (element == null) return 0;
        int maxIndex = ABS.get(element).length - 1;
        return Math.max(0, Math.min(maxIndex, selected.getOrDefault(p.getUniqueId(), 0)));
    }

    Ab selectedAbility(Player p) {
        Element element = magic.getEl(p);
        if (element == null) return null;
        Ab[] abilities = ABS.get(element);
        int index = selectedIndex(p);
        return index >= 0 && index < abilities.length ? abilities[index] : null;
    }

    int requiredAbilityLevel(Element element, int index) {
        return Math.min(moveUnlockMaxLevel(), index + 1);
    }

    boolean selectAbility(Player player, int index) {
        Element element = magic.getEl(player);
        if (element == null || index < 0 || index >= ABS.get(element).length) return false;
        if (!abilityUnlocked(player, element, index)) {
            int required = requiredAbilityLevel(element, index);
            if (element == Element.SHAPESHIFT && magic.shapeshift != null
                    && magic.levelOf(player) >= required
                    && !magic.shapeshift.hasForm(player, ABS.get(element)[index].baseIndex())) {
                player.sendMessage(Magic.c("§cلازم تتعلم الشكل المرتبط بالحركة بقتل الكائن الأول."));
            } else {
                player.sendMessage(Magic.c("§cالحركة دي لسه مقفولة. هتفتح عند لفل §e" + required + "§c."));
            }
            return false;
        }
        selected.put(player.getUniqueId(), index);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + index * 0.03f);
        showSelected(player);
        return true;
    }

    void resetSelection(Player p) {
        selected.put(p.getUniqueId(), 0);
        if (isActive(p)) showSelected(p);
    }

    private int moveUnlockMaxLevel() {
        return Math.max(1, Math.min(50, magic.getConfig().getInt("progression.move-unlocks-max-level", 50)));
    }

    // تقنية جديدة كل مستوى حتى الحد المحدد.

    boolean abilityUnlocked(Player player, Element element, int index) {
        if (index < 0 || index >= ABS.get(element).length) return false;
        // OP can test every move immediately, without level progression.
        if (player.isOp()) return true;
        if (magic.levelOf(player) < requiredAbilityLevel(element, index)) return false;
        if (element == Element.SHAPESHIFT) {
            int formIndex = ABS.get(element)[index].baseIndex();
            return magic.shapeshift != null && magic.shapeshift.hasForm(player, formIndex);
        }
        return true;
    }

    void onLevelUp(Player player, int oldLevel, int newLevel) {
        Element element = magic.getEl(player);
        if (element == null || newLevel <= oldLevel) return;
        Ab[] abilities = ABS.get(element);
        int unlockMax = moveUnlockMaxLevel();
        for (int level = Math.max(1, oldLevel + 1); level <= Math.min(unlockMax, newLevel); level++) {
            Ab unlocked = abilities[level - 1];
            if (element == Element.SHAPESHIFT && magic.shapeshift != null
                    && !magic.shapeshift.hasForm(player, unlocked.baseIndex())) {
                player.sendMessage(Magic.c("§d✦ لفل " + level + " فتح تقنية جديدة: §f" + unlocked.name()
                        + " §7— اقتل الكائن المرتبط بالشكل لاستخدامها."));
            } else {
                player.sendMessage(Magic.c("§6✦ حركة جديدة اتفتحت عند لفل " + level + "! §f" + unlocked.name()));
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75f, 1.0f + level * 0.01f);
        }
        if (oldLevel < unlockMax && newLevel >= unlockMax) {
            player.sendMessage(Magic.c("§d✦ وصلت لمرحلة إتقان الحركات! كل التقنيات المتاحة أصبحت مفتوحة؛ من بعد لفل " + unlockMax + " قوتك هي اللي هتزيد."));
        }
        int scaleStart = Math.max(unlockMax + 1, magic.getConfig().getInt("progression.power-scaling-start-level", 51));
        int scaleStep = Math.max(1, magic.getConfig().getInt("progression.power-scaling-step-levels", 10));
        if (newLevel >= scaleStart && (newLevel - scaleStart) % scaleStep == 0) {
            player.sendMessage(Magic.c("§e✦ قوة العنصر زادت — §fلفل " + newLevel + " §7| مفيش حركات جديدة بعد لفل " + unlockMax + "."));
        }
    }

    void cycle(Player p, int dir) {
        Ab[] a = abs(p);
        Element element = magic.getEl(p);
        if (a == null || element == null) return;
        int current = selected.getOrDefault(p.getUniqueId(), 0);
        for (int step = 1; step <= a.length; step++) {
            int candidate = Math.floorMod(current + dir * step, a.length);
            if (abilityUnlocked(p, element, candidate)) {
                selected.put(p.getUniqueId(), candidate);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + candidate * 0.3f);
                showSelected(p);
                return;
            }
        }
        p.sendActionBar(Magic.c("§cارفع مستوى العنصر لفتح حركات جديدة."));
    }

    /** يظهر اسم القدرة المختارة في نص الشاشة نص ثانية ثم يختفي (الـ Action Bar متسيب لـ AuraSkills). */
    private void showSelected(Player p) {
        Element el = magic.getEl(p);
        if (el == null) return;
        UUID id = p.getUniqueId();
        int i = selected.getOrDefault(id, 0);
        Ab ab = ABS.get(el)[i];
        String manaLabel = p.isOp() ? "§dمجاني (OP)" : "§b" + trim(cost(el, i)) + " مانا";
        String evolution = ab.evolutionRank() == 0 ? "" : " §8| §dتطور " + ab.evolutionRank() + "/4";
        p.showTitle(Title.title(
                Magic.c(el.color + "✦ " + ab.name() + " ✦"),
                Magic.c("§7[" + (i + 1) + "/50] §8| " + manaLabel + " §8| §aبدون كولداون" + evolution + environmentBonusText(p)),
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
            if (!isCore(p.getInventory().getItemInMainHand())) p.getInventory().setItemInMainHand(makeCore(el));
            Fx.aura(p, el, tick);
            if (el == Element.SHADOW && lightAt(p.getLocation().getBlock()) <= 4) {
                effect(p, p, PotionEffectType.STRENGTH, 30, 0);
                effect(p, p, PotionEffectType.SPEED, 30, 0);
            }
        }
    }

    // ------------------------------------------------------------ events
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        // Shift + F مخصص للدرع: اسمح للـAdvancedSystems بالتعامل معه سواء
        // كانت اليد فاضية (قبل تفعيل القوة) أو تحمل قلب القوة (أثناء التفعيل).
        if (p.isSneaking() && (main.getType() == Material.AIR || (isActive(p) && isCore(main)))) return;
        if (magic.getEl(p) == null) return;
        // لم نعد نشترط إفراغ اليد: نحفظ العنصر الحالي ونضع قلب القوة مكانه مؤقتًا.
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isActive(p)) return;
        Action action = e.getAction();
        // كليك يمين يشتغل سواء في الهوا تمامًا أو على بلوك عادي.
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        // قلب القوة موجود في Main Hand؛ تجاهل حدث Offhand كي لا تتكرر الضغطة
        // أو تتعارض مع درع/عنصر اللاعب الموجود في اليد الثانية.
        if (e.getHand() != EquipmentSlot.HAND) return;

        // لا نمنع فتح الصناديق والأبواب والأزرار. استخدم Shift أو /gwm use
        // لو محتاج تطلق القدرة وأنت مستهدف بلوكًا تفاعليًا.
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) {
            return;
        }

        if (duplicateInteract(p)) return;
        e.setCancelled(true);
        trigger(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!isActive(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (duplicateInteract(p)) return;
        e.setCancelled(true);
        trigger(p);
    }

    private boolean duplicateInteract(Player player) {
        long now = System.nanoTime();
        UUID id = player.getUniqueId();
        Long previous = lastInteractNanos.put(id, now);
        return previous != null && now - previous >= 0L && now - previous < 5_000_000L;
    }

    private void trigger(Player p) {
        cast(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !isActive(p)) return;
        if (!(e.getEntity() instanceof LivingEntity target) || busy.contains(p.getUniqueId())) return;
        if (e.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK) return;
        Element el = magic.getEl(p);
        if (el == null) return;
        double cost = magic.getConfig().getDouble("power.strike-mana", 1.5);
        if (!magic.advanced.spendMana(p, cost)) return;
        int t = magic.unarmedTier(p);
        double bonus = (1.0 + 0.3 * t) * environmentMultiplier(p);
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
        boolean hotbarCoreSwap = isActive(p) && e.getClick() == ClickType.NUMBER_KEY
                && e.getHotbarButton() >= 0 && e.getHotbarButton() <= 8
                && isCore(p.getInventory().getItem(e.getHotbarButton()));
        if (core || offhandTouch || hotbarCoreSwap) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isCore(e.getOldCursor()) || (isActive(p) && e.getInventorySlots().contains(40))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        stopFlight(p, false, null);
        if (!isActive(p)) {
            e.getDrops().removeIf(this::isCore);
            return;
        }
        UUID id = p.getUniqueId();
        ItemStack saved = store.getItemStack("saved-main." + id);
        int savedSlot = store.getInt("saved-slot." + id, p.getInventory().getHeldItemSlot());
        ItemStack legacyOffhand = store.getItemStack("saved." + id);
        e.getDrops().removeIf(this::isCore);
        active.remove(id);
        if (e.getKeepInventory()) {
            stripCores(p);
            if (saved != null && saved.getType() != Material.AIR)
                p.getInventory().setItem(Math.max(0, Math.min(8, savedSlot)), saved);
            if (legacyOffhand != null && legacyOffhand.getType() != Material.AIR)
                p.getInventory().setItemInOffHand(legacyOffhand);
        } else {
            if (saved != null && saved.getType() != Material.AIR) e.getDrops().add(saved);
            if (legacyOffhand != null && legacyOffhand.getType() != Material.AIR) e.getDrops().add(legacyOffhand);
        }
        store.set("saved-main." + id, null);
        store.set("saved-slot." + id, null);
        store.set("saved." + id, null);
        store.set("active." + id, null);
        saveStore();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        disable(e.getPlayer(), true);
        selected.remove(e.getPlayer().getUniqueId());
        lastInteractNanos.remove(e.getPlayer().getUniqueId());
        String cachePrefix = e.getPlayer().getUniqueId().toString() + ":";
        affinityCache.keySet().removeIf(key -> key.startsWith(cachePrefix));
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
            boolean unlocked = abilityUnlocked(p, el, i);
            String lock;
            if (unlocked) {
                lock = "§a[مفتوحة] ";
            } else if (el == Element.SHAPESHIFT && magic.levelOf(p) >= requiredAbilityLevel(el, i)
                    && magic.shapeshift != null && !magic.shapeshift.hasForm(p, a[i].baseIndex())) {
                lock = "§6[اقتل الكائن لفتح الشكل] ";
            } else {
                lock = "§c[مقفولة حتى لفل " + (i + 1) + "] ";
            }
            String manaLabel = p.isOp() ? "§dمجاني (OP)" : "§b" + trim(cost(el, i)) + " مانا";
            String evolution = a[i].evolutionRank() == 0 ? "" : " §d(تطور " + a[i].evolutionRank() + "/4)";
            p.sendMessage(Magic.c(lock + el.color + (i + 1) + ". " + a[i].name() + evolution + " §8| " + manaLabel
                    + " §8| §aبدون كولداون §8— §7" + a[i].desc()));
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
        if (!abilityUnlocked(p, el, idx)) {
            int required = requiredAbilityLevel(el, idx);
            if (el == Element.SHAPESHIFT && magic.shapeshift != null
                    && magic.levelOf(p) >= required && !magic.shapeshift.hasForm(p, ABS.get(el)[idx].baseIndex())) {
                p.sendMessage(Magic.c("§cلازم تتعلم الشكل المرتبط بالحركة بقتل الكائن الأول."));
            } else {
                p.sendMessage(Magic.c("§cالحركة دي لسه مقفولة. محتاج توصل لفل §e" + required + " §cفي عنصر " + el.ar));
            }
            return;
        }
        Ab ab = ABS.get(el)[idx];
        double cost = cost(el, idx);
        if (!magic.advanced.spendMana(p, cost)) return;
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
            return;
        }
        magic.updateTrial(p, Magic.TrialKind.ELEMENT_MASTERY, 1);
        magic.advanced.noteCast(p);
        p.sendActionBar(Magic.c(el.color + "✦ " + ab.name() + environmentBonusText(p)));
    }

    private boolean run(Player p, Element el, int idx, int t) {
        Ab ability = ABS.get(el)[idx];
        int baseIndex = ability.baseIndex();
        boolean success;
        if (el == Element.SHAPESHIFT) {
            success = magic.shapeshift != null && magic.shapeshift.castForm(p, baseIndex, t);
        } else if (baseIndex >= 3) {
            success = moves.run(p, el, baseIndex - 3, t);
        } else {
            success = switch (el) {
                case FIRE -> switch (baseIndex) { case 0 -> fireAura(p, t); case 1 -> fireball(p, t); default -> fireFlight(p, t); };
                case WATER -> switch (baseIndex) { case 0 -> tide(p, t); case 1 -> bubble(p, t); default -> swim(p, t); };
                case EARTH -> switch (baseIndex) { case 0 -> spikes(p, t); case 1 -> earthArmor(p, t); default -> quake(p, t); };
                case WIND -> switch (baseIndex) { case 0 -> gust(p, t); case 1 -> windLeap(p, t); default -> tornado(p, t); };
                case LIGHTNING -> switch (baseIndex) { case 0 -> spark(p, t); case 1 -> thunderDash(p, t); default -> staticField(p, t); };
                case SHADOW -> switch (baseIndex) { case 0 -> shadowStep(p, t); case 1 -> darkStrike(p, t); default -> nightCloak(p, t); };
                case ICE -> switch (baseIndex) { case 0 -> frostNova(p, t); case 1 -> iceVolley(p, t); default -> blizzard(p, t); };
                case METAL -> switch (baseIndex) { case 0 -> steelGuard(p, t); case 1 -> magnet(p, t); default -> ironFist(p, t); };
                case NATURE -> switch (baseIndex) { case 0 -> rootBind(p, t); case 1 -> bloom(p, t); default -> thornBurst(p, t); };
                case LIGHT -> switch (baseIndex) { case 0 -> flash(p, t); case 1 -> lightBeam(p, t); default -> blessing(p, t); };
                case SOUND -> switch (baseIndex) { case 0 -> shockwave(p, t); case 1 -> sonicBoom(p, t); default -> echo(p, t); };
                case CRYSTAL -> switch (baseIndex) { case 0 -> shardVolley(p, t); case 1 -> prismShield(p, t); default -> crystalCage(p, t); };
                case SHAPESHIFT -> false;
            };
        }
        if (success && ability.evolutionRank() > 0)
            applyEvolutionTechnique(p, el, baseIndex, ability.evolutionRank(), t);
        return success;
    }

    /** Each evolution rank adds a distinct elemental technique on top of its original move. */
    private void applyEvolutionTechnique(Player p, Element el, int baseIndex, int rank, int tier) {
        Location center = p.getLocation().clone().add(0, 0.25, 0);
        World world = p.getWorld();
        double r = 2.4 + rank * 0.9;
        switch (el) {
            case FIRE -> {
                switch (rank) {
                    case 1 -> {
                        ring(center, r, 28, l -> world.spawnParticle(Particle.FLAME, l, 2, 0.08, 0.12, 0.08, 0.01));
                        for (LivingEntity target : foesAt(p, center, r)) { target.setFireTicks(environmentDuration(p, el, 60)); hurt(p, target, 1.8 + tier); }
                    }
                    case 2 -> {
                        Vector dash = dir(p).multiply(0.48);
                        applyVelocity(p, p, dash.setY(Math.max(0.12, dash.getY() + 0.12)));
                        for (LivingEntity target : cone(p, 6.5, 0.35)) { target.setFireTicks(environmentDuration(p, el, 80)); hurt(p, target, 2.5 + tier); }
                    }
                    case 3 -> {
                        effect(p, p, PotionEffectType.FIRE_RESISTANCE, 120, 0);
                        effect(p, p, PotionEffectType.STRENGTH, 80, 0);
                        fx(p.getLocation().add(0, 1, 0), Particle.FLAME, 55, 0.8, 0.7);
                    }
                    default -> {
                        ring(center, r + 2, 56, l -> world.spawnParticle(Particle.LAVA, l, 1, 0.08, 0.08, 0.08, 0));
                        for (LivingEntity target : foesAt(p, center, r + 2)) { target.setFireTicks(environmentDuration(p, el, 120)); hurt(p, target, 5.0 + tier); magic.push(p, target, center, 0.9, 0.45); }
                    }
                }
            }
            case WATER -> {
                switch (rank) {
                    case 1 -> { heal(p, 2.0 + rank); fx(center, Particle.SPLASH, 45, 1.2, 0.25); }
                    case 2 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { effect(p, target, PotionEffectType.SLOWNESS, 65, 2); hurt(p, target, 2.2 + tier); } }
                    case 3 -> { effect(p, p, PotionEffectType.RESISTANCE, 100, 0); effect(p, p, PotionEffectType.REGENERATION, 100, 1); effect(p, p, PotionEffectType.WATER_BREATHING, 180, 0); }
                    default -> { fx(center, Particle.SPLASH, 90, 2.8, 0.45); for (LivingEntity target : foesAt(p, center, r + 2)) { hurt(p, target, 4.5 + tier); magic.push(p, target, center, 0.8, 0.5); } heal(p, 4.0); }
                }
            }
            case EARTH -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.RESISTANCE, 100, 0); ring(center, r, 28, l -> world.spawnParticle(Particle.BLOCK, l, 3, 0.1, 0.3, 0.1, 0.04, Material.STONE.createBlockData())); }
                    case 2 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { hurt(p, target, 3 + tier); magic.push(p, target, center, 0.5, 0.7); } }
                    case 3 -> { for (LivingEntity target : cone(p, 9, 0.2)) { effect(p, target, PotionEffectType.SLOWNESS, 80, 2); hurt(p, target, 4 + tier); } }
                    default -> { ring(center, r + 2, 48, l -> world.spawnParticle(Particle.BLOCK, l, 4, 0.1, 0.2, 0.1, 0.05, Material.DEEPSLATE.createBlockData())); for (LivingEntity target : foesAt(p, center, r + 2)) { hurt(p, target, 5 + tier); magic.push(p, target, center, 1.0, 0.8); } }
                }
            }
            case WIND -> {
                switch (rank) {
                    case 1 -> { fx(center, Particle.CLOUD, 65, 1.2, 0.25); for (LivingEntity target : foesAt(p, center, r + 1)) magic.push(p, target, center, 0.8, 0.5); }
                    case 2 -> { for (LivingEntity target : cone(p, 10, 0.15)) { effect(p, target, PotionEffectType.LEVITATION, 18, 0); hurt(p, target, 2 + tier); } }
                    case 3 -> { Vector dash = dir(p).multiply(0.72); applyVelocity(p, p, dash.setY(Math.max(0.2, dash.getY() + 0.15))); effect(p, p, PotionEffectType.SLOW_FALLING, 80, 0); }
                    default -> { for (LivingEntity target : foesAt(p, center, r + 2)) { Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.65).setY(0.42); applyVelocity(p, target, pull); hurt(p, target, 4 + tier); } fx(center, Particle.CLOUD, 110, 2.4, 0.35); }
                }
            }
            case LIGHTNING -> {
                switch (rank) {
                    case 1 -> { LivingEntity target = firstTarget(p, 18); if (target != null) { world.strikeLightningEffect(target.getLocation()); hurt(p, target, 3.5 + tier); } }
                    case 2 -> { effect(p, p, PotionEffectType.SPEED, 100, 2); effect(p, p, PotionEffectType.HASTE, 100, 1); applyVelocity(p, p, dir(p).multiply(0.7)); }
                    case 3 -> { LivingEntity target = firstTarget(p, 18); if (target != null) for (LivingEntity other : foesAt(p, target.getLocation(), 5)) { world.spawnParticle(Particle.ELECTRIC_SPARK, other.getLocation().add(0, 1, 0), 20, 0.25, 0.5, 0.25, 0.1); hurt(p, other, 3 + tier); } }
                    default -> { ring(center, r + 1, 44, l -> world.spawnParticle(Particle.ELECTRIC_SPARK, l, 3, 0.1, 0.2, 0.1, 0.1)); for (LivingEntity target : foesAt(p, center, r + 1)) { world.strikeLightningEffect(target.getLocation()); hurt(p, target, 5 + tier); } }
                }
            }
            case SHADOW -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.INVISIBILITY, 70, 0); effect(p, p, PotionEffectType.SPEED, 70, 1); fx(center, Particle.PORTAL, 55, 0.8, 0.3); }
                    case 2 -> { LivingEntity target = firstTarget(p, 14); if (target != null) { effect(p, target, PotionEffectType.BLINDNESS, 70, 0); effect(p, target, PotionEffectType.WITHER, 70, 0); hurt(p, target, 3 + tier); } }
                    case 3 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { effect(p, target, PotionEffectType.DARKNESS, 80, 0); effect(p, target, PotionEffectType.SLOWNESS, 60, 1); } }
                    default -> { ring(center, r + 2, 48, l -> world.spawnParticle(Particle.PORTAL, l, 4, 0.1, 0.35, 0.1, 0.04)); for (LivingEntity target : foesAt(p, center, r + 2)) { effect(p, target, PotionEffectType.WITHER, 100, 1); hurt(p, target, 5 + tier); } }
                }
            }
            case ICE -> {
                switch (rank) {
                    case 1 -> { for (LivingEntity target : foesAt(p, center, r)) { target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 70)); effect(p, target, PotionEffectType.SLOWNESS, 65, 2); } fx(center, Particle.SNOWFLAKE, 45, 1.0, 0.15); }
                    case 2 -> { LivingEntity target = firstTarget(p, 18); if (target != null) { hurt(p, target, 3 + tier); target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 120)); } }
                    case 3 -> { effect(p, p, PotionEffectType.RESISTANCE, 100, 0); effect(p, p, PotionEffectType.SLOW_FALLING, 120, 0); fx(center, Particle.SNOWFLAKE, 75, 1.1, 0.2); }
                    default -> { for (LivingEntity target : foesAt(p, center, r + 2)) { target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 180)); effect(p, target, PotionEffectType.SLOWNESS, 100, 3); hurt(p, target, 5 + tier); } fx(center, Particle.SNOWFLAKE, 130, 2.2, 0.25); }
                }
            }
            case METAL -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.RESISTANCE, 100, 0); effect(p, p, PotionEffectType.ABSORPTION, 100, 1); fx(center, Particle.CRIT, 55, 0.7, 0.2); }
                    case 2 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.6).setY(0.15); applyVelocity(p, target, pull); } }
                    case 3 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { hurt(p, target, 4 + tier); magic.push(p, target, center, 0.9, 0.55); } }
                    default -> { effect(p, p, PotionEffectType.RESISTANCE, 120, 1); ring(center, r + 2, 50, l -> world.spawnParticle(Particle.CRIT, l, 4, 0.1, 0.2, 0.1, 0.02)); for (LivingEntity target : foesAt(p, center, r + 2)) hurt(p, target, 5 + tier); }
                }
            }
            case NATURE -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.REGENERATION, 120, 1); heal(p, 2.5); fx(center, Particle.HAPPY_VILLAGER, 55, 0.9, 0.2); }
                    case 2 -> { for (LivingEntity target : foesAt(p, center, r + 1)) { effect(p, target, PotionEffectType.POISON, 80, 0); effect(p, target, PotionEffectType.SLOWNESS, 80, 2); } }
                    case 3 -> { effect(p, p, PotionEffectType.SPEED, 120, 1); for (LivingEntity target : foesAt(p, center, r + 1)) { effect(p, target, PotionEffectType.SLOWNESS, 70, 3); hurt(p, target, 3 + tier); } }
                    default -> { effect(p, p, PotionEffectType.REGENERATION, 180, 1); heal(p, 5); for (LivingEntity target : foesAt(p, center, r + 2)) { effect(p, target, PotionEffectType.POISON, 120, 1); hurt(p, target, 5 + tier); } fx(center, Particle.HAPPY_VILLAGER, 100, 2.0, 0.25); }
                }
            }
            case LIGHT -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.SPEED, 100, 1); for (LivingEntity target : foesAt(p, center, r + 1)) effect(p, target, PotionEffectType.GLOWING, 120, 0); fx(center, Particle.END_ROD, 50, 0.8, 0.3); }
                    case 2 -> { for (LivingEntity target : cone(p, 10, 0.2)) effect(p, target, PotionEffectType.BLINDNESS, 50, 0); fx(p.getEyeLocation(), Particle.FLASH, 1, 0, 0); }
                    case 3 -> { heal(p, 4); effect(p, p, PotionEffectType.REGENERATION, 120, 1); effect(p, p, PotionEffectType.RESISTANCE, 80, 0); }
                    default -> { LivingEntity target = firstTarget(p, 24); if (target != null) { hurt(p, target, 7 + tier); fx(target.getLocation().add(0, 1, 0), Particle.END_ROD, 80, 0.4, 0.3); } }
                }
            }
            case SOUND -> {
                switch (rank) {
                    case 1 -> { for (LivingEntity target : foesAt(p, center, r + 1)) magic.push(p, target, center, 0.8, 0.35); fx(center, Particle.NOTE, 70, 1.0, 0.4); }
                    case 2 -> { for (LivingEntity target : cone(p, 12, 0.1)) { effect(p, target, PotionEffectType.NAUSEA, 90, 0); effect(p, target, PotionEffectType.SLOWNESS, 70, 1); } }
                    case 3 -> { for (LivingEntity target : foesAt(p, center, r + 2)) effect(p, target, PotionEffectType.GLOWING, 120, 0); fx(center, Particle.NOTE, 100, 1.8, 0.5); }
                    default -> { snd(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f); for (LivingEntity target : cone(p, 18, 0.0)) { hurt(p, target, 6 + tier); magic.push(p, target, center, 1.1, 0.35); } }
                }
            }
            case CRYSTAL -> {
                switch (rank) {
                    case 1 -> { effect(p, p, PotionEffectType.ABSORPTION, 140, 1); fx(center, Particle.END_ROD, 55, 0.8, 0.3); }
                    case 2 -> { LivingEntity target = firstTarget(p, 20); if (target != null) { hurt(p, target, 4 + tier); fx(target.getLocation(), Particle.CRIT, 65, 0.5, 0.3); } }
                    case 3 -> { effect(p, p, PotionEffectType.RESISTANCE, 100, 0); effect(p, p, PotionEffectType.ABSORPTION, 160, 2); ring(center, r, 28, l -> world.spawnParticle(Particle.END_ROD, l, 2, 0.1, 0.4, 0.1, 0.02)); }
                    default -> { ring(center, r + 2, 52, l -> world.spawnParticle(Particle.END_ROD, l, 3, 0.08, 0.2, 0.08, 0.02)); for (LivingEntity target : foesAt(p, center, r + 2)) { hurt(p, target, 6 + tier); effect(p, target, PotionEffectType.SLOWNESS, 90, 2); } }
                }
            }
            case SHAPESHIFT -> evolveForm(p, baseIndex, rank, tier, center, r);
        }
    }

    private void evolveForm(Player p, int form, int rank, int tier, Location center, double radius) {
        switch (form) {
            case 0 -> { // Spider: خيوط وشبكات إضافية.
                for (LivingEntity target : foesAt(p, center, radius + rank)) { effect(p, target, PotionEffectType.SLOWNESS, 50 + rank * 20, rank); }
                fx(center, Particle.CLOUD, 30 + rank * 15, 0.9 + rank * 0.2, 0.1);
            }
            case 1 -> { // Creeper: تفريغ طاقة بلا تكسير بلوكات.
                ring(center, radius + rank, 28 + rank * 8, l -> p.getWorld().spawnParticle(Particle.EXPLOSION, l, 1, 0, 0, 0));
                for (LivingEntity target : foesAt(p, center, radius + rank)) { hurt(p, target, 2 + rank + tier); magic.push(p, target, center, 0.8 + rank * 0.12, 0.35); }
            }
            case 2 -> { // Skeleton: وابل سهام إضافي.
                Vector d = dir(p);
                for (int i = 0; i < rank + 1; i++) {
                    org.bukkit.entity.Arrow arrow = p.launchProjectile(org.bukkit.entity.Arrow.class, d.clone().rotateAroundY(Math.toRadians((i - rank / 2.0) * 8)).multiply(1.1));
                    arrow.setDamage(2.0 + rank + tier * 0.3);
                }
            }
            case 3 -> { // Blaze: جمر يدور حول اللاعب.
                for (LivingEntity target : foesAt(p, center, radius + rank)) { target.setFireTicks(environmentDuration(p, Element.FIRE, 60 + rank * 20)); hurt(p, target, 2 + rank); }
                fx(center, Particle.FLAME, 55 + rank * 20, 1.0 + rank * 0.3, 0.12);
            }
            case 4 -> { // Ghast: قذيفة انفجارية متزايدة.
                LivingEntity target = firstTarget(p, 20);
                if (target != null) { hurt(p, target, 4 + rank * 2 + tier); magic.push(p, target, center, 1.0, 0.6); fx(target.getLocation(), Particle.EXPLOSION, 1, 0, 0); }
            }
            case 5 -> { // Warden: موجة صوتية ثقيلة.
                snd(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f);
                for (LivingEntity target : cone(p, 12 + rank * 2, 0.05)) { hurt(p, target, 3 + rank * 1.5 + tier); effect(p, target, PotionEffectType.GLOWING, 100, 0); }
            }
            case 6 -> { // Iron Golem: دفاع وضربة ارتدادية.
                effect(p, p, PotionEffectType.RESISTANCE, 60 + rank * 30, 0);
                for (LivingEntity target : foesAt(p, center, radius + rank)) { hurt(p, target, 2 + rank + tier); magic.push(p, target, center, 0.9 + rank * 0.1, 0.5); }
            }
            case 7 -> { // Evoker: أنياب على شكل صفوف.
                for (int row = 1; row <= rank + 1; row++) {
                    Location at = p.getLocation().add(dir(p).multiply(row * 1.5));
                    p.getWorld().spawn(at, org.bukkit.entity.EvokerFangs.class, f -> { f.setOwner(p); f.setSilent(true); });
                }
            }
            case 8 -> { // Ender Dragon: زفير تنين متوسع.
                fx(p.getEyeLocation().add(dir(p).multiply(3)), Particle.DRAGON_BREATH, 80 + rank * 20, 0.9 + rank * 0.2, 0.08);
                for (LivingEntity target : cone(p, 10 + rank * 2, 0.15)) { hurt(p, target, 4 + rank * 1.5 + tier); effect(p, target, PotionEffectType.WITHER, 50 + rank * 20, 0); }
            }
            default -> { // Enderman: انتقال وظل.
                LivingEntity target = firstTarget(p, 16);
                if (target != null) { Vector back = target.getLocation().getDirection().setY(0).normalize().multiply(-1.4); Location dest = target.getLocation().add(back); dest.setYaw(p.getLocation().getYaw()); dest.setPitch(p.getLocation().getPitch()); p.teleport(dest); hurt(p, target, 3 + rank * 2 + tier); }
                fx(center, Particle.PORTAL, 45 + rank * 15, 0.9, 0.2);
            }
        }
    }

    // ------------------------------------------------------------ environmental affinity
    boolean isEmpoweredByEnvironment(Player p) {
        return isEmpoweredByEnvironment(p, magic.getEl(p));
    }

    boolean isEmpoweredByEnvironment(Player p, Element el) {
        if (p == null || el == null || !p.isOnline()
                || !magic.getConfig().getBoolean("environmental-bonus.enabled", true)) return false;
        String cacheKey = p.getUniqueId() + ":" + el.name();
        long now = System.currentTimeMillis();
        AffinityCache cached = affinityCache.get(cacheKey);
        long cacheMs = Math.max(0L, magic.getConfig().getLong("environmental-bonus.cache-ms", 500L));
        if (cached != null && now - cached.checkedAt() <= cacheMs) return cached.empowered();
        boolean empowered = ElementAffinity.isEmpowered(p, el);
        affinityCache.put(cacheKey, new AffinityCache(now, el, empowered));
        return empowered;
    }

    double environmentMultiplier(Player p) {
        return environmentMultiplier(p, magic.getEl(p));
    }

    double environmentMultiplier(Player p, Element el) {
        if (el == null || !isEmpoweredByEnvironment(p, el)) return 1.0;
        double fallback = magic.getConfig().getDouble("environmental-bonus.default-multiplier", 1.25);
        return Math.max(1.0, magic.getConfig().getDouble("environmental-bonus.multipliers." + el.name(), fallback));
    }

    int environmentDuration(Player p, int ticks) {
        return environmentDuration(p, magic.getEl(p), ticks);
    }

    int environmentDuration(Player p, Element el, int ticks) {
        return Math.max(1, (int) Math.round(ticks * environmentMultiplier(p, el)));
    }

    Vector environmentVector(Player p, Vector velocity) {
        return environmentVector(p, magic.getEl(p), velocity);
    }

    Vector environmentVector(Player p, Element el, Vector velocity) {
        return velocity.clone().multiply(environmentMultiplier(p, el));
    }

    String environmentBonusText(Player p) {
        if (!isEmpoweredByEnvironment(p)) return "";
        int percent = (int) Math.round((environmentMultiplier(p) - 1.0) * 100.0);
        return " §6✦ تعزيز البيئة +" + percent + "%";
    }

    private void applyVelocity(Player caster, org.bukkit.entity.Entity target, Vector velocity) {
        target.setVelocity(environmentVector(caster, velocity));
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

    private void heal(Player player, double amount) {
        if (player == null || player.isDead() || amount <= 0) return;
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount * environmentMultiplier(player)));
    }

    void hurt(Player p, LivingEntity t, double dmg) {
        if (t == p || t.isDead() || dmg <= 0) return;
        UUID u = p.getUniqueId();
        boolean was = !busy.add(u);
        try { t.damage(dmg * environmentMultiplier(p), p); }
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

    void effect(Player caster, LivingEntity target, PotionEffectType type, int ticks, int amp) {
        effect(target, type, environmentDuration(caster, ticks), amp);
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
        // Darkness now grants the same configurable environmental boost as the other schools;
        // bright conditions do not impose an extra penalty.
        return magic.getEl(p) == Element.SHADOW ? environmentMultiplier(p) : 1.0;
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
        effect(p, p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
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
                    le.setFireTicks(environmentDuration(p, Element.FIRE, 60 + 20 * t));
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
                        le.setFireTicks(environmentDuration(p, Element.FIRE, 80 + 20 * t));
                        magic.push(p, le, l, 0.6, 0.35);
                    }
                }, null);
        return true;
    }

    private boolean fireFlight(Player p, int t) {
        return toggleFlight(p, Element.FIRE);
    }

    /** Toggle a persistent, mana-fed flight style. Wind is the economical form; fire is faster and hotter. */
    boolean toggleFlight(Player p, Element element) {
        UUID id = p.getUniqueId();
        if (flights.containsKey(id)) {
            stopFlight(p, true, "§7أوقفت الطيران. اضغط القدرة مرة ثانية للعودة.");
            return false; // cast() يرجّع تكلفة التفعيل عند الإيقاف.
        }
        if (!isActive(p) || magic.getEl(p) != element) return false;
        int minimum = element == Element.FIRE ? 3 : 4;
        if (magic.levelOf(p) < minimum) {
            p.sendActionBar(Magic.c("§cالطيران يفتح من لفل " + minimum));
            return false;
        }
        effect(p, p, PotionEffectType.SLOW_FALLING, 80, 0);
        snd(p.getLocation(), element == Element.FIRE ? Sound.ENTITY_FIREWORK_ROCKET_LAUNCH : Sound.ENTITY_ENDER_DRAGON_FLAP,
                element == Element.FIRE ? 0.9f : 1.4f);
        p.sendMessage(Magic.c(element == Element.WIND
                ? "§f✦ بدأت طيران الرياح — حرّك نظرك للتحكم، Shift للإيقاف. المانا المستهلكة قليلة جدًا."
                : "§c✦ بدأت طيران النار — اللهب يندفع من يديك لأسفل. حرّك نظرك للتحكم، Shift للإيقاف."));
        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;
            int previousStage = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || !isActive(p) || magic.getEl(p) != element) {
                    stopFlight(p, false, null);
                    return;
                }
                if (p.isSneaking()) {
                    stopFlight(p, true, "§7تم إيقاف الطيران.");
                    return;
                }
                if (element == Element.FIRE && p.isInWater()) {
                    stopFlight(p, true, "§cانطفأ طيران النار بسبب الماء.");
                    return;
                }
                int level = magic.levelOf(p);
                int secondStage = Math.max(2, magic.getConfig().getInt("flight.second-stage-level", 15));
                int thirdStage = Math.max(secondStage + 1, magic.getConfig().getInt("flight.third-stage-level", 30));
                int stage = level >= thirdStage ? 3 : level >= secondStage ? 2 : 1;
                if (stage != previousStage) {
                    previousStage = stage;
                    String stageText = element == Element.WIND
                            ? (stage == 1 ? "الرياح بتدور حول جسمك" : stage == 2 ? "اندفاع الرياح بدأ يخرج من قدميك" : "وصلت لهيئة الرياح الكاملة")
                            : (stage == 1 ? "نفث اللهب الأساسي من يديك" : stage == 2 ? "نفث ناري أقوى وأسرع" : "اندفاع اللهب المتقدم");
                    p.sendMessage(Magic.c((element == Element.WIND ? "§f✦ " : "§c✦ ") + stageText + " §7(مرحلة " + stage + "/3)"));
                }
                int drainInterval = Math.max(5, magic.getConfig().getInt("flight." + element.name().toLowerCase(java.util.Locale.ROOT) + ".drain-interval-ticks",
                        element == Element.WIND ? 40 : 20));
                double drain = magic.getConfig().getDouble("flight." + element.name().toLowerCase(java.util.Locale.ROOT) + ".mana-per-drain",
                        element == Element.WIND ? 0.15 : 0.8);
                if (elapsed > 0 && elapsed % drainInterval == 0 && !magic.advanced.spendMana(p, drain)) {
                    stopFlight(p, true, "§cماناك خلصت — الطيران اتوقف.");
                    return;
                }
                double speed;
                double lift;
                if (element == Element.WIND) {
                    speed = stage == 1 ? 0.46 : stage == 2 ? 0.64 : 0.82;
                    lift = stage == 1 ? 0.11 : stage == 2 ? 0.15 : 0.18;
                } else {
                    speed = stage == 1 ? 0.72 : stage == 2 ? 0.94 : 1.18;
                    lift = stage == 1 ? 0.16 : stage == 2 ? 0.21 : 0.26;
                    effect(p, p, PotionEffectType.FIRE_RESISTANCE, 30, 0);
                }
                if (p.isSprinting()) speed *= 1.12;
                Vector direction = p.getEyeLocation().getDirection().normalize();
                Vector velocity = direction.multiply(speed);
                velocity.setY(Math.max(-0.36, Math.min(0.58, velocity.getY() + lift)));
                p.setVelocity(environmentVector(p, element, velocity));
                p.setFallDistance(0f);
                if (element == Element.WIND) renderWindFlight(p, stage, elapsed);
                else renderFireFlight(p, stage, elapsed);
                elapsed++;
            }
        };
        flights.put(id, task);
        task.runTaskTimer(magic, 1L, 1L);
        return true;
    }

    private void renderWindFlight(Player p, int stage, int ticks) {
        Location body = p.getLocation().clone().add(0, 0.9, 0);
        World world = p.getWorld();
        int points = stage == 1 ? 8 : stage == 2 ? 12 : 22;
        double radius = stage == 1 ? 0.48 : stage == 2 ? 0.62 : 0.8;
        for (int i = 0; i < points; i++) {
            double angle = (ticks * (stage == 3 ? 0.32 : 0.22)) + Math.PI * 2 * i / points;
            double y = stage == 3 ? -0.55 + (i % 8) * 0.19 : -0.25 + (i % 4) * 0.22;
            Location point = body.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.CLOUD, point, stage == 3 ? 2 : 1, 0.04, 0.08, 0.04, 0.01);
            if (stage == 3 && i % 3 == 0) world.spawnParticle(Particle.WHITE_ASH, point, 1, 0.05, 0.08, 0.05, 0.005);
        }
        if (stage >= 2) {
            Vector forward = p.getLocation().getDirection().setY(0).normalize();
            Vector back = forward.multiply(-0.3);
            Location leftFoot = p.getLocation().clone().add(-0.18, 0.05, 0).add(back);
            Location rightFoot = p.getLocation().clone().add(0.18, 0.05, 0).add(back);
            world.spawnParticle(Particle.CLOUD, leftFoot, stage == 2 ? 5 : 9, 0.08, 0.2, 0.08, 0.04);
            world.spawnParticle(Particle.CLOUD, rightFoot, stage == 2 ? 5 : 9, 0.08, 0.2, 0.08, 0.04);
        }
    }

    private void renderFireFlight(Player p, int stage, int ticks) {
        World world = p.getWorld();
        Vector facing = p.getLocation().getDirection().setY(0).normalize();
        if (facing.lengthSquared() < 0.001) facing = new Vector(0, 0, 1);
        Vector right = new Vector(-facing.getZ(), 0, facing.getX()).normalize();
        Location torso = p.getLocation().clone().add(0, 0.95, 0).add(facing.clone().multiply(0.18));
        int jets = stage == 1 ? 2 : stage == 2 ? 3 : 4;
        for (int side : new int[]{-1, 1}) {
            Location hand = torso.clone().add(right.clone().multiply(side * 0.34));
            for (int i = 1; i <= jets; i++) {
                Location exhaust = hand.clone().add(0, -i * 0.34, 0).subtract(facing.clone().multiply(i * 0.08));
                world.spawnParticle(Particle.FLAME, exhaust, stage == 3 ? 5 : 3, 0.07, 0.07, 0.07, 0.025);
                world.spawnParticle(Particle.SMOKE, exhaust, stage == 1 ? 1 : 2, 0.04, 0.06, 0.04, 0.01);
            }
        }
        if (stage >= 2) world.spawnParticle(Particle.LAVA, p.getLocation().clone().add(0, -0.2, 0), 2 + stage, 0.22, 0.12, 0.22, 0.01);
        if (stage == 3 && ticks % 3 == 0) world.spawnParticle(Particle.FLAME, p.getLocation(), 18, 0.42, 0.45, 0.42, 0.035);
    }

    private void stopFlight(Player p, boolean notify, String message) {
        if (p == null) return;
        BukkitRunnable task = flights.remove(p.getUniqueId());
        if (task != null) task.cancel();
        if (p.isOnline() && !p.isDead()) {
            effect(p, p, PotionEffectType.SLOW_FALLING, 60, 0);
            p.setFallDistance(0f);
            if (notify && message != null) p.sendMessage(Magic.c(message));
        }
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
        effect(p, p, PotionEffectType.REGENERATION, 60, 0);
        for (LivingEntity le : cone(p, 7, 0.5)) {
            hurt(p, le, 2 + 0.6 * t);
            effect(p, le, PotionEffectType.SLOWNESS, 60, 1);
            applyVelocity(p, le, d.clone().multiply(1.0).setY(0.3));
        }
        return true;
    }

    private boolean bubble(Player p, int t) {
        LivingEntity target = firstTarget(p, 18);
        if (target == null) { p.sendActionBar(Magic.c("§cمفيش هدف قدامك")); return false; }
        effect(p, target, PotionEffectType.SLOWNESS, 50, 4);
        effect(p, target, PotionEffectType.LEVITATION, 40, 0);
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
        effect(p, p, PotionEffectType.DOLPHINS_GRACE, 160, 1);
        effect(p, p, PotionEffectType.SPEED, 100, 1);
        effect(p, p, PotionEffectType.REGENERATION, 80, 0);
        effect(p, p, PotionEffectType.WATER_BREATHING, 240, 0);
        applyVelocity(p, p, dir(p).multiply(1.1).setY(0.25));
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
                    applyVelocity(p, le, new Vector(0, 0.8, 0));
                }
                i++;
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean earthArmor(Player p, int t) {
        effect(p, p, PotionEffectType.RESISTANCE, 120, 1);
        effect(p, p, PotionEffectType.ABSORPTION, 200, 1);
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
                    magic.push(p, le, c, 0.6, 0.8);
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
        effect(p, p, PotionEffectType.SPEED, 60, 1);
        for (LivingEntity le : cone(p, 7, 0.4)) {
            hurt(p, le, 1 + 0.3 * t);
            applyVelocity(p, le, d.clone().multiply(1.3).setY(0.5));
        }
        return true;
    }

    private boolean windLeap(Player p, int t) {
        Vector d = dir(p).setY(0);
        if (d.lengthSquared() > 0.01) d.normalize().multiply(0.9);
        applyVelocity(p, p, d.setY(1.1 + 0.05 * t));
        effect(p, p, PotionEffectType.SLOW_FALLING, 100, 0);
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
                    applyVelocity(p, le, pull.setY(0.38));
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
        applyVelocity(p, p, d.clone().multiply(1.5 + 0.15 * t).setY(Math.max(0.1, d.getY() * 0.6)));
        effect(p, p, PotionEffectType.SPEED, 60, 1);
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
        effect(p, p, PotionEffectType.SPEED, 100, 1);
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
        effect(p, p, PotionEffectType.INVISIBILITY, 40, 0);
        if (shadowMult(p) > 1.0) effect(p, p, PotionEffectType.SPEED, 60, 1);
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
            hurt(p, le, 3 + t * 1.3);
            effect(p, le, PotionEffectType.BLINDNESS, 60, 0);
            if (mult > 1.0) effect(p, le, PotionEffectType.WITHER, 60, 0);
        }
        p.sendActionBar(Magic.c(mult > 1.0 ? "§5الظلام بيقوّي ضربتك!" : mult < 1.0 ? "§7النور بيضعّف ضربتك" : "§7ضربة الظلام"));
        return true;
    }

    private boolean nightCloak(Player p, int t) {
        World w = p.getWorld();
        w.spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 50, 0.5, 0.9, 0.5, 0.05);
        if (shadowMult(p) > 1.0) {
            effect(p, p, PotionEffectType.INVISIBILITY, 200, 0);
            effect(p, p, PotionEffectType.SPEED, 200, 1);
            effect(p, p, PotionEffectType.STRENGTH, 200, 1);
            effect(p, p, PotionEffectType.NIGHT_VISION, 260, 0);
            snd(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f);
            p.sendActionBar(Magic.c("§5العباءة كاملة — الضلمة معاك"));
        } else {
            effect(p, p, PotionEffectType.INVISIBILITY, 60, 0);
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
            le.setFreezeTicks(Math.min(le.getMaxFreezeTicks(), le.getFreezeTicks() + environmentDuration(p, 60)));
            effect(p, le, PotionEffectType.SLOWNESS, 80, 2);
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
                            effect(p, hit, PotionEffectType.SLOWNESS, 60, 1);
                            hit.setFreezeTicks(Math.min(hit.getMaxFreezeTicks(), hit.getFreezeTicks() + environmentDuration(p, 40)));
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
                    effect(p, le, PotionEffectType.SLOWNESS, 30, 2);
                    le.setFreezeTicks(Math.min(le.getMaxFreezeTicks(), le.getFreezeTicks() + environmentDuration(p, 20)));
                    if (n % 4 == 0) hurt(p, le, 1 + 0.3 * t);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    // ============================================================ METAL
    private boolean steelGuard(Player p, int t) {
        effect(p, p, PotionEffectType.RESISTANCE, 100, 2);
        snd(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f);
        World w = p.getWorld();
        Location c = p.getLocation();
        ring(c.clone().add(0, 1, 0), 1.3, 24, l -> w.spawnParticle(Particle.CRIT, l, 3, 0.05, 0.4, 0.05, 0.02));
        for (LivingEntity le : foesAt(p, c, 3.0)) magic.push(p, le, c, 0.9, 0.25);
        return true;
    }

    private boolean magnet(Player p, int t) {
        List<LivingEntity> list = cone(p, 8, 0.3);
        if (list.isEmpty()) { p.sendActionBar(Magic.c("§cمفيش حد قدامك")); return false; }
        snd(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f);
        for (LivingEntity le : list) {
            Vector pull = p.getLocation().toVector().subtract(le.getLocation().toVector()).multiply(0.25);
            if (pull.length() > 1.6) pull.normalize().multiply(1.6);
            applyVelocity(p, le, pull.setY(0.2));
            hurt(p, le, 2 + 0.5 * t);
            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.2);
        }
        return true;
    }

    private boolean ironFist(Player p, int t) {
        Vector d = dir(p);
        applyVelocity(p, p, d.clone().multiply(1.2).setY(0.15));
        snd(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                Location l = p.getLocation();
                l.getWorld().spawnParticle(Particle.CRIT, l.clone().add(0, 1, 0).add(dir(p).multiply(1.2)), 40, 0.5, 0.5, 0.5, 0.3);
                snd(l, Sound.BLOCK_ANVIL_LAND, 1.0f);
                for (LivingEntity le : cone(p, 3.5, 0.2)) {
                    hurt(p, le, 6 + 1.4 * t);
                    magic.push(p, le, l, 1.2, 0.4);
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
            effect(p, le, PotionEffectType.SLOWNESS, 100, 5);
            effect(p, le, PotionEffectType.POISON, 60, 0);
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
        effect(p, p, PotionEffectType.REGENERATION, 100, 1);
        effect(p, p, PotionEffectType.INSTANT_HEALTH, 1, 0);
        for (Entity en : w.getNearbyEntities(c, 5, 5, 5)) {
            if (en instanceof Player o && o != p && o.getGameMode() != GameMode.SPECTATOR) {
                effect(p, o, PotionEffectType.REGENERATION, 100, 1);
                effect(p, o, PotionEffectType.INSTANT_HEALTH, 1, 0);
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
            effect(p, le, PotionEffectType.POISON, 80, 0);
            magic.push(p, le, c, 0.7, 0.3);
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
        effect(p, p, PotionEffectType.SPEED, 80, 1);
        for (LivingEntity le : foesAt(p, c, 8)) {
            effect(p, le, PotionEffectType.BLINDNESS, 60 + 10 * t, 0);
            effect(p, le, PotionEffectType.GLOWING, 100, 0);
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
        effect(p, p, PotionEffectType.INSTANT_HEALTH, 1, 0);
        effect(p, p, PotionEffectType.REGENERATION, 100, 1);
        effect(p, p, PotionEffectType.ABSORPTION, 100, 0);
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
            applyVelocity(p, le, d.clone().multiply(1.6).setY(0.4));
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
                effect(p, le, PotionEffectType.NAUSEA, 100, 0);
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
                    magic.push(p, le, c, 0.35, 0.15);
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
                            effect(p, hit, PotionEffectType.SLOWNESS, 40, 0);
                        }
                    }, d);
        }
        return true;
    }

    private boolean prismShield(Player p, int t) {
        effect(p, p, PotionEffectType.ABSORPTION, 200, 2);
        effect(p, p, PotionEffectType.RESISTANCE, 160, 0);
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
        effect(p, target, PotionEffectType.SLOWNESS, 70, 6);
        effect(p, target, PotionEffectType.GLOWING, 70, 0);
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
            case FIRE -> t.setFireTicks(environmentDuration(p, Element.FIRE, 60 + 10 * tier));
            case WATER -> { effect(p, t, PotionEffectType.SLOWNESS, 40, 0); effect(p, p, PotionEffectType.REGENERATION, 40, 0); }
            case EARTH -> applyVelocity(p, t, new Vector(0, 0.45, 0));
            case WIND -> magic.push(p, t, p.getLocation(), 0.9, 0.3);
            case LIGHTNING -> {
                w.strikeLightningEffect(t.getLocation());
                for (LivingEntity o : foesAt(p, t.getLocation(), 4)) if (o != t) { hurt(p, o, 2); break; }
            }
            case SHADOW -> effect(p, t, PotionEffectType.BLINDNESS, 40, 0);
            case ICE -> { t.setFreezeTicks(Math.min(t.getMaxFreezeTicks(), t.getFreezeTicks() + environmentDuration(p, Element.ICE, 40))); effect(p, t, PotionEffectType.SLOWNESS, 50, 1); }
            case METAL -> magic.push(p, t, p.getLocation(), 0.5, 0.2);
            case NATURE -> effect(p, t, PotionEffectType.POISON, 60, 0);
            case LIGHT -> { effect(p, t, PotionEffectType.GLOWING, 100, 0); effect(p, t, PotionEffectType.BLINDNESS, 30, 0); }
            case SOUND -> { effect(p, t, PotionEffectType.NAUSEA, 60, 0); magic.push(p, t, p.getLocation(), 0.6, 0.2); }
            case CRYSTAL -> effect(p, t, PotionEffectType.SLOWNESS, 60, 1);
            case SHAPESHIFT -> { if (magic.shapeshift != null) magic.shapeshift.onMelee(p, t, tier); }
        }
    }
}
