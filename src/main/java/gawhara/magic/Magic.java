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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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

    static final int MAX_LEVEL = 100;
    static final int TOTAL_TRIALS = 20;
    static final int[] FIRST_LEVELS = {0, 20, 60, 130, 250};
    static final int[] SLOTS = {10, 11, 12, 14, 15, 16};
    static final String[] WNAMES = {"نصل", "فأس", "قوس", "سيف الغضب", "السيف الأسطوري"};

    enum TrialKind {
        FIRE_GLOW, FIRE_LIGHTS,
        WATER_DIVE, WATER_BOAT,
        EARTH_STONE_MINED, EARTH_ORE_MINED, EARTH_CAVE,
        WIND_AIR, WIND_SPRINT, WIND_GLIDE,
        LIGHTNING_ROD, LIGHTNING_ROD_NEAR, LIGHTNING_REDSTONE,
        SHADOW_DARK, SHADOW_SNEAK_DARK
    }

    record Trial(TrialKind kind, int goal, String clue) {}
    record W(Element el, int lvl) {}

    static class MagicGui implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    NamespacedKey KEY;
    NamespacedKey OWNER_KEY;
    NamespacedKey GENERATION_KEY;
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
        OWNER_KEY = new NamespacedKey(this, "weapon_owner");
        GENERATION_KEY = new NamespacedKey(this, "weapon_generation");
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
                    ItemStack held = p.getInventory().getItemInMainHand();
                    W w = info(held);
                    if (w == null || !isValidWeapon(p, held)) continue;
                    for (int i = 0; i < 2; i++) {
                        double ang = a * 3 + i * Math.PI;
                        Location l = p.getLocation().add(Math.cos(ang) * 0.9, 0.3 + (Math.sin(a * 2) + 1) * 0.5, Math.sin(ang) * 0.9);
                        p.getWorld().spawnParticle(w.el().particle, l, 2, 0.05, 0.05, 0.05, 0.01, null);
                    }
                }
            }
        }.runTaskTimer(this, 10, 4);

        // متابعة الاختبارات السرية عدة مرات في الثانية لالتقاط القفزات والحركة القصيرة.
        new BukkitRunnable() {
            @Override public void run() { tickTrials(); }
        }.runTaskTimer(this, 10, 10);

        // حفظ دوري
        new BukkitRunnable() {
            @Override public void run() { if (dirty) save(); }
        }.runTaskTimer(this, 600, 600);

        // أول ما البلاجن يتضاف: افتح الاختيار للي لسه مختارش، وذكّر الباقي باختبارهم.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getEl(p) == null) openGui(p);
            else if (trialReady(p)) showTrialHint(p);
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

    String playerPath(Player p) { return "players." + p.getUniqueId(); }

    int magicGeneration(Player p) {
        return data.getInt("magicGeneration." + p.getUniqueId(), 0);
    }

    int pts(Player p) { return data.getInt(playerPath(p) + ".points", 0); }

    int completedTrials(Player p) {
        return Math.max(0, Math.min(TOTAL_TRIALS, data.getInt(playerPath(p) + ".trialsCompleted", 0)));
    }

    static int pointsForLevel(int level) {
        int l = Math.max(1, Math.min(MAX_LEVEL, level));
        if (l <= FIRST_LEVELS.length) return FIRST_LEVELS[l - 1];
        int d = l - 5;
        // نحافظ على نقاط أول خمسة مستويات، وبعدها يزيد المطلوب تدريجيًا حتى لفل 100.
        return 250 + 25 * d + (d * (d + 1)) / 2;
    }

    static int rawLevelOf(int points) {
        int level = 1;
        for (int i = 2; i <= MAX_LEVEL; i++) {
            if (points >= pointsForLevel(i)) level = i;
            else break;
        }
        return level;
    }

    int unlockedCap(Player p) {
        return Math.min(MAX_LEVEL, (completedTrials(p) + 1) * 5);
    }

    int levelOf(Player p) {
        return Math.min(rawLevelOf(pts(p)), unlockedCap(p));
    }

    Trial currentTrial(Player p) {
        int index = completedTrials(p);
        if (index >= TOTAL_TRIALS) return null;
        int difficulty = index / 4;
        int phase = index % 4;
        Element el = getEl(p);
        if (el == null) return null;
        return switch (el) {
            case FIRE -> switch (phase) {
                case 0 -> new Trial(TrialKind.FIRE_GLOW, 4 + difficulty * 2,
                        "لا تقترب من اللهب المؤذي؛ اقترب من وهجه الهادئ وابقَ بجواره.");
                case 1 -> new Trial(TrialKind.FIRE_LIGHTS, 3 + difficulty,
                        "أشعل نقاطًا صغيرة من النور، واجعل الظلام يتراجع خطوةً خطوة.");
                case 2 -> new Trial(TrialKind.FIRE_GLOW, 8 + difficulty * 2,
                        "قوة النار ليست في اندفاعها فقط؛ تعلّم الثبات قرب دفئها.");
                default -> new Trial(TrialKind.FIRE_LIGHTS, 5 + difficulty * 2,
                        "ازرع مزيدًا من الوهج في طريقك، وسيعرف العنصر أنك فهمت رسالته.");
            };
            case WATER -> switch (phase) {
                case 0 -> new Trial(TrialKind.WATER_DIVE, 5 + difficulty * 2,
                        "أسرار الماء لا تظهر فوق السطح... غص حتى يهدأ العالم من حولك.");
                case 1 -> new Trial(TrialKind.WATER_BOAT, 5 + difficulty * 2,
                        "هذه المرة لا تقاوم التيار؛ دع الماء يحملُك إلى الأمام.");
                case 2 -> new Trial(TrialKind.WATER_DIVE, 8 + difficulty * 2,
                        "عد إلى الأعماق، فهناك ما زال للماء سر لم تكشفه.");
                default -> new Trial(TrialKind.WATER_BOAT, 10 + difficulty * 2,
                        "اترك للموج مهمة حملك، واستمر في رحلتك فوقه.");
            };
            case EARTH -> switch (phase) {
                case 0 -> new Trial(TrialKind.EARTH_STONE_MINED, 12 + difficulty * 3,
                        "الأرض لا تفتح أسرارها إلا لمن ينقّب في صمتها.");
                case 1 -> new Trial(TrialKind.EARTH_ORE_MINED, 2 + difficulty,
                        "في جوف الصخر تختبئ عروق ثمينة. اعثر عليها.");
                case 2 -> new Trial(TrialKind.EARTH_CAVE, 5 + difficulty * 2,
                        "ابتعد عن السماء قليلًا، واستمع إلى نبض العالم من تحته.");
                default -> new Trial(TrialKind.EARTH_STONE_MINED, 20 + difficulty * 4,
                        "الصخر لا يملّ من الصبر، فهل تملك أنت ما يكفي منه؟");
            };
            case WIND -> switch (phase) {
                case 0 -> new Trial(TrialKind.WIND_AIR, 4 + difficulty * 2,
                        "الأرض ليست موطنك الوحيد... دع الهواء يحمل جسدك لبعض الوقت.");
                case 1 -> new Trial(TrialKind.WIND_SPRINT, 10 + difficulty * 5,
                        "لا تمنح الريح قوتها للمتردد؛ دع خطواتك تتسابق معها.");
                case 2 -> new Trial(TrialKind.WIND_GLIDE, 5 + difficulty * 2,
                        "افتح جناحيك واترك الريح تختار لك الطريق.");
                default -> new Trial(TrialKind.WIND_AIR, 8 + difficulty * 2,
                        "حلّق قليلًا فوق الأرض، فالريح لا تسكن عند قدميك.");
            };
            case LIGHTNING -> switch (phase) {
                case 0 -> new Trial(TrialKind.LIGHTNING_ROD, 1 + difficulty,
                        "اصنع طريقًا بين الأرض والسماء، واتركه ينتظر الشرارة.");
                case 1 -> new Trial(TrialKind.LIGHTNING_ROD_NEAR, 5 + difficulty * 2,
                        "حين تصنع موصلًا للسماء، اقترب منه وانتظر أن تشعر بالشرارة.");
                case 2 -> new Trial(TrialKind.LIGHTNING_REDSTONE, 4 + difficulty,
                        "الشرارة الصغيرة قد تحرّك أشياء أكبر منها؛ اصنع لها مسارًا.");
                default -> new Trial(TrialKind.LIGHTNING_ROD, 2 + difficulty,
                        "أنشئ مزيدًا من الجسور الرفيعة بين الأرض والسماء.");
            };
            case SHADOW -> switch (phase) {
                case 0 -> new Trial(TrialKind.SHADOW_DARK, 5 + difficulty * 2,
                        "لا تبحث عن النور هذه المرة... ابقَ حيث لا يصل إليك.");
                case 1 -> new Trial(TrialKind.SHADOW_SNEAK_DARK, 5 + difficulty * 2,
                        "اخفض خطاك، واختفِ في مكان لا تستطيع أشعة الشمس الوصول إليه.");
                case 2 -> new Trial(TrialKind.SHADOW_DARK, 8 + difficulty * 2,
                        "تعلّم أن تصادق العتمة بدلًا من أن تحاول طردها.");
                default -> new Trial(TrialKind.SHADOW_SNEAK_DARK, 8 + difficulty * 2,
                        "اجعل خطواتك همسًا، ولا تترك للضوء أثرًا يصل إليك.");
            };
        };
    }

    boolean trialReady(Player p) {
        int index = completedTrials(p);
        return index < TOTAL_TRIALS && rawLevelOf(pts(p)) >= (index + 1) * 5;
    }

    void showTrialHint(Player p) {
        Trial trial = currentTrial(p);
        if (trial == null || !trialReady(p)) return;
        int milestone = (completedTrials(p) + 1) * 5;
        p.sendMessage(c("§6✦ §eاختبار سري جديد — لفل " + milestone + " §6✦"));
        p.sendMessage(c("§7" + trial.clue()));
        p.sendMessage(c("§8لن تظهر لك التعليمات مباشرة. استنتج ما يطلبه العنصر."));
        p.sendMessage(c("§7تابع تقدم السر من خلال §e/gmagic task"));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f);
    }

    void showTrialStatus(Player p) {
        Trial trial = currentTrial(p);
        if (trial == null) {
            p.sendMessage(c("§a✦ أتقنت الأسرار العشرين ووصلت إلى قمة القوة!"));
            return;
        }
        int milestone = (completedTrials(p) + 1) * 5;
        if (!trialReady(p)) {
            p.sendMessage(c("§6✦ §fالاختبار السري القادم عند لفل §e" + milestone));
            p.sendMessage(c("§7مستواك الحالي: §e" + levelOf(p) + "§7. واصل التقدم."));
            return;
        }
        p.sendMessage(c("§6✦ §eالاختبار السري — " + getEl(p).ar + " §7(" + milestone + "/100)"));
        p.sendMessage(c("§7" + trial.clue()));
        p.sendMessage(c("§8التقدم السري: §f" + trialProgress(p) + "§7/§f" + trial.goal()));
        p.sendMessage(c("§7لن تُكشف المهمة؛ حاول فهم التلميح بنفسك."));
    }

    int trialProgress(Player p) { return data.getInt(playerPath(p) + ".trialProgress", 0); }

    void updateTrial(Player p, TrialKind kind, int amount) {
        if (amount <= 0 || !trialReady(p)) return;
        Trial trial = currentTrial(p);
        if (trial == null || trial.kind() != kind) return;
        String path = playerPath(p) + ".trialProgress";
        int now = Math.min(trial.goal(), data.getInt(path, 0) + amount);
        data.set(path, now);
        dirty = true;
        if (now >= trial.goal()) {
            completeTrial(p);
        } else {
            p.sendActionBar(c("§6✦ §8سر العنصر §7[§e" + now + "§7/§e" + trial.goal() + "§7]"));
        }
    }

    void completeTrial(Player p) {
        int index = completedTrials(p);
        if (index >= TOTAL_TRIALS) return;
        Element el = getEl(p);
        if (el == null) return;
        int milestone = (index + 1) * 5;
        data.set(playerPath(p) + ".trialsCompleted", index + 1);
        data.set(playerPath(p) + ".trialProgress", 0);
        dirty = true;
        save();
        p.sendMessage(c("§6✦ §aنجحت في الاختبار السري! §fانكشف لك مستوى جديد من قوة " + el.color + el.ar));
        if (milestone < MAX_LEVEL) {
            p.sendMessage(c("§7أصبح بإمكانك التقدم حتى لفل §e" + (milestone + 5) + "§7. الطريق أصبح مفتوحًا."));
        } else {
            p.sendMessage(c("§d✦ أكملت الاختبارات السرية العشرين ووصلت إلى المستوى 100! ✦"));
        }
        p.showTitle(net.kyori.adventure.title.Title.title(
                c("§6✦ اكتمل الاختبار ✦"), c(el.color + "قوة " + el.ar + " تزداد")));
        celebrate(p, el);
    }

    boolean nearLightningRod(Player p) {
        Location loc = p.getLocation();
        for (int x = -4; x <= 4; x++) for (int y = -2; y <= 2; y++) for (int z = -4; z <= 4; z++) {
            if (loc.clone().add(x, y, z).getBlock().getType() == Material.LIGHTNING_ROD) return true;
        }
        return false;
    }

    boolean nearSafeFlame(Player p) {
        Location loc = p.getLocation();
        for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) {
            Material type = loc.clone().add(x, y, z).getBlock().getType();
            if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                    || type == Material.TORCH || type == Material.SOUL_TORCH
                    || type == Material.LANTERN || type == Material.SOUL_LANTERN) return true;
        }
        return false;
    }

    boolean inDarkness(Player p) {
        return p.getLocation().getBlock().getLightLevel() <= 2;
    }

    void tickTrials() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!trialReady(p)) continue;
            Trial trial = currentTrial(p);
            if (trial == null) continue;
            boolean valid = switch (trial.kind()) {
                case FIRE_GLOW -> nearSafeFlame(p);
                case WATER_DIVE -> p.isUnderWater();
                case WATER_BOAT -> p.getVehicle() instanceof Boat boat && boat.getVelocity().lengthSquared() > 0.02;
                case EARTH_CAVE -> p.getLocation().getBlockY() < p.getWorld().getSeaLevel() - 5
                        && p.getLocation().getBlock().getLightLevel() <= 7;
                case WIND_AIR -> !p.isOnGround() && !p.isInWater() && !p.isInLava();
                case WIND_SPRINT -> p.isSprinting();
                case WIND_GLIDE -> p.isGliding();
                case LIGHTNING_ROD_NEAR -> nearLightningRod(p);
                case SHADOW_DARK -> inDarkness(p);
                case SHADOW_SNEAK_DARK -> p.isSneaking() && inDarkness(p);
                default -> false;
            };
            if (valid) updateTrial(p, trial.kind(), 1);
        }
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
            lore.add(c("§7اختبارات سرية كل 5 مستويات حتى لفل 100"));
            lore.add(c("§7اجمع النقاط من اللعب وافتح المستويات بالتدريج"));
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
        data.set("players." + p.getUniqueId() + ".trialsCompleted", 0);
        data.set("players." + p.getUniqueId() + ".trialProgress", 0);
        save();
        p.closeInventory();
        giveWeapon(p, el, 1);
        p.sendMessage(c("§6✦ §fاتعلمت قوة " + el.color + el.ar + "§f! اجمع النقاط من اللعب، واكتشف الاختبارات السرية."));
        p.showTitle(net.kyori.adventure.title.Title.title(
                c(el.color + "✦ قوة " + el.ar + " ✦"), c("§7سلاحك الأول في إيدك")));
        celebrate(p, el);
        Bukkit.broadcast(c("§6✦ §e" + p.getName() + " §7اختار قوة " + el.color + el.ar));
    }

    // ------------------------------------------------------------ weapons
    ItemStack makeWeapon(Player p, Element el, int lvl) {
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
        // كل سلاح مربوط بصاحبه وبنسخة القوة الحالية؛ أي سلاح أقدم من الريسيت يصبح غير صالح.
        meta.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, p.getUniqueId().toString());
        meta.getPersistentDataContainer().set(GENERATION_KEY, PersistentDataType.INTEGER, magicGeneration(p));
        it.setItemMeta(meta);
        return it;
    }

    void giveWeapon(Player p, Element el, int lvl) {
        give(p, makeWeapon(p, el, lvl));
        if (lvl == 3) give(p, new ItemStack(Material.ARROW, 1));
    }

    boolean isValidWeapon(Player p, ItemStack it) {
        W w = info(it);
        if (w == null || it == null || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        String owner = pdc.get(OWNER_KEY, PersistentDataType.STRING);
        Integer generation = pdc.get(GENERATION_KEY, PersistentDataType.INTEGER);

        // توافق مع أسلحة النسخة السابقة: تظل صالحة قبل أول Reset فقط.
        // بعد الريسيت تصبح كل النسخ القديمة غير صالحة حتى لو كانت محفوظة داخل صندوق.
        if (owner == null && generation == null) {
            return magicGeneration(p) == 0 && getEl(p) == w.el();
        }

        return p.getUniqueId().toString().equals(owner)
                && generation != null && generation == magicGeneration(p);
    }

    boolean hasWeapon(Player p, Element el, int lvl) {
        for (ItemStack it : p.getInventory().getContents()) {
            W w = info(it);
            if (w != null && isValidWeapon(p, it) && w.el() == el && w.lvl() == lvl) return true;
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
        ItemStack item = e.getItemDrop().getItemStack();
        if (info(item) != null && isValidWeapon(e.getPlayer(), item)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(c("§cالسلاح السحري مينفعش يتحدف"));
        }
    }

    // ------------------------------------------------------------ levels & secret trials
    // الاحتفاظ بمصدر النقاط الأصلي، مع السماح بالتعدين أيضًا للتقدم.
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        Element el = getEl(k);
        if (el == null) return;
        int add = e.getEntity() instanceof Player ? 8 : (e.getEntity() instanceof Monster ? 2 : 1);
        addPoints(k, el, add);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrialBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Element el = getEl(p);
        if (el == null) return;
        Material type = e.getBlock().getType();
        String name = type.name();
        boolean stone = isStoneForTrial(type);
        boolean ore = name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;

        Trial trial = trialReady(p) ? currentTrial(p) : null;
        if (trial != null) {
            if (trial.kind() == TrialKind.EARTH_STONE_MINED && stone) updateTrial(p, trial.kind(), 1);
            else if (trial.kind() == TrialKind.EARTH_ORE_MINED && ore) updateTrial(p, trial.kind(), 1);
        }

        // التعدين نشاط تقدم أساسي، وتمنح الخامات نقاطًا أكثر من الأحجار العادية.
        if (stone || ore) addPoints(p, el, ore ? 3 : 1);
    }

    boolean isStoneForTrial(Material type) {
        return switch (type) {
            case STONE, COBBLESTONE, DEEPSLATE, COBBLED_DEEPSLATE, TUFF,
                    GRANITE, DIORITE, ANDESITE, BLACKSTONE, BASALT, NETHERRACK -> true;
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrialBlockPlace(BlockPlaceEvent e) {
        Material type = e.getBlockPlaced().getType();
        if (type == Material.LIGHTNING_ROD) updateTrial(e.getPlayer(), TrialKind.LIGHTNING_ROD, 1);
        if (type == Material.REDSTONE_WIRE || type == Material.REPEATER || type == Material.COMPARATOR)
            updateTrial(e.getPlayer(), TrialKind.LIGHTNING_REDSTONE, 1);
        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE || type == Material.TORCH
                || type == Material.SOUL_TORCH || type == Material.LANTERN || type == Material.SOUL_LANTERN)
            updateTrial(e.getPlayer(), TrialKind.FIRE_LIGHTS, 1);
    }

    void addPoints(Player p, Element el, int amt) {
        int old = pts(p);
        int oldLevel = levelOf(p);
        int cap = unlockedCap(p);
        if (completedTrials(p) < TOTAL_TRIALS && rawLevelOf(old) >= cap) {
            p.sendActionBar(c("§6✦ §7اختبار العنصر يمنع تقدمك مؤقتًا — §e/gmagic task"));
            return;
        }
        int nw = Math.min(pointsForLevel(cap), old + amt);
        data.set(playerPath(p) + ".points", nw);
        dirty = true;
        int newLevel = levelOf(p);
        if (newLevel > oldLevel) {
            for (int l = oldLevel + 1; l <= Math.min(newLevel, 5); l++) {
                giveWeapon(p, el, l);
                p.sendMessage(c("§6✦ §fلفل جديد! §e" + l + " §f— استلمت: " + el.color + WNAMES[l - 1] + " " + el.ar));
            }
            p.sendMessage(c("§6✦ §fارتفع مستواك إلى §e" + newLevel + "§f!"));
            p.showTitle(net.kyori.adventure.title.Title.title(
                    c("§6✦ لفل " + newLevel + " ✦"), c(el.color + "قوة " + el.ar)));
            if (newLevel == cap && completedTrials(p) < TOTAL_TRIALS) showTrialHint(p);
            else celebrate(p, el);
        } else if (completedTrials(p) < TOTAL_TRIALS && rawLevelOf(nw) >= cap) {
            showTrialHint(p);
        } else {
            int next = newLevel >= MAX_LEVEL ? nw : pointsForLevel(newLevel + 1);
            p.sendActionBar(c(el.color + "✦ " + el.ar + " §7| لفل " + newLevel + " | " + nw + "/" + next));
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
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!isValidWeapon(p, held)) return;
            w = info(held);
        } else if (e.getDamager() instanceof Arrow a && a.hasMetadata("gm") && a.getShooter() instanceof Player p) {
            atk = p;
            String[] s = a.getMetadata("gm").get(0).asString().split(":");
            try {
                if (s.length != 4 || !p.getUniqueId().toString().equals(s[2])
                        || magicGeneration(p) != Integer.parseInt(s[3])) return;
                w = new W(Element.valueOf(s[0]), Integer.parseInt(s[1]));
            } catch (RuntimeException ignored) {
                return;
            }
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
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof Arrow a)) return;
        W w = info(e.getBow());
        if (w == null || !isValidWeapon(p, e.getBow())) return;
        a.setMetadata("gm", new FixedMetadataValue(this,
                w.el().name() + ":" + w.lvl() + ":" + p.getUniqueId() + ":" + magicGeneration(p)));
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
        ItemStack held = p.getInventory().getItemInMainHand();
        W w = info(held);
        if (w == null || !isValidWeapon(p, held)) return;
        if (held.getType() == Material.BOW && !p.isSneaking()) return;
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

    // ------------------------------------------------------------ reset cleanup
    int removeMagicItemsFrom(Inventory inventory) {
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (info(inventory.getItem(slot)) != null) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    int removeMagicItems(Player p) {
        int removed = removeMagicItemsFrom(p.getInventory());
        removed += removeMagicItemsFrom(p.getEnderChest());

        PlayerInventory inv = p.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (info(armor[i]) != null) {
                armor[i] = null;
                removed++;
                armorChanged = true;
            }
        }
        if (armorChanged) inv.setArmorContents(armor);

        ItemStack[] extra = inv.getExtraContents();
        boolean extraChanged = false;
        for (int i = 0; i < extra.length; i++) {
            if (info(extra[i]) != null) {
                extra[i] = null;
                removed++;
                extraChanged = true;
            }
        }
        if (extraChanged) inv.setExtraContents(extra);

        // نظّف أي نسخة سحرية كانت محفوظة في صندوق الاسترجاع من موت سابق.
        UUID uuid = p.getUniqueId();
        List<ItemStack> stash = loadStash(uuid);
        if (stash.removeIf(it -> info(it) != null)) {
            data.set("stash." + uuid + ".items", stash);
            dirty = true;
        }

        // امسح الـ buffs التي تمنحها قدرات العناصر فقط، ولا تلمس باقي تأثيرات اللاعب العادية.
        for (PotionEffectType type : List.of(
                PotionEffectType.REGENERATION,
                PotionEffectType.ABSORPTION,
                PotionEffectType.RESISTANCE,
                PotionEffectType.SLOW_FALLING,
                PotionEffectType.SPEED)) {
            p.removePotionEffect(type);
        }
        p.setFireTicks(0);
        cd.remove(uuid);
        return removed;
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
            if (isValidWeapon(p, it)) keep.add(it);
            else if (info(it) == null) stash.add(it); // النسخ القديمة من الأسلحة السحرية لا تُسترجع
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
                    if (trialReady(p)) showTrialHint(p);
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
            if (a.length < 2) { s.sendMessage(c("§c/gmagic " + a[0] + " <لاعب>" + (a[0].equalsIgnoreCase("level") ? " <1-100>" : ""))); return true; }
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(c("§cاللاعب مش أونلاين.")); return true; }
            if (a[0].equalsIgnoreCase("reset")) {
                UUID uuid = t.getUniqueId();
                // تغيير رقم الجيل يبطل أي نسخة قديمة من السلاح حتى لو كانت مخزنة في صندوق.
                data.set("magicGeneration." + uuid, magicGeneration(t) + 1);
                int removed = removeMagicItems(t);
                data.set(playerPath(t), null);
                save();
                s.sendMessage(c("§aاتعمل ريسيت لـ " + t.getName() + " وتم إبطال أسلحته السحرية القديمة."));
                t.sendMessage(c("§cتم تصفير قوتك السحرية! اتشالت أسلحتك القديمة وتأثيراتها، واختار قوة جديدة للبدء من الأول."));
                t.sendMessage(c("§7عدد الأسلحة السحرية التي أُزيلت من أغراضك: §e" + removed));
                openGui(t);
            } else {
                Element el = getEl(t);
                if (el == null || a.length < 3) { s.sendMessage(c("§cاللاعب لازم يكون مختار قوة، والمطلوب /gmagic level <لاعب> <1-100>")); return true; }
                int lv;
                try { lv = Math.max(1, Math.min(MAX_LEVEL, Integer.parseInt(a[2]))); }
                catch (NumberFormatException ex) { s.sendMessage(c("§cرقم غلط. استخدم رقمًا من 1 إلى 100.")); return true; }
                data.set(playerPath(t) + ".points", pointsForLevel(lv));
                data.set(playerPath(t) + ".trialsCompleted", Math.min(TOTAL_TRIALS, (lv - 1) / 5));
                data.set(playerPath(t) + ".trialProgress", 0);
                save();
                s.sendMessage(c("§aتم ضبط مستوى " + t.getName() + " إلى " + lv));
                if (trialReady(t)) showTrialHint(t);
            }
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("للاعبين بس."); return true; }
        Element el = getEl(p);
        if (el == null) { openGui(p); return true; }
        if (a.length >= 1 && a[0].equalsIgnoreCase("weapons")) {
            int lv = levelOf(p);
            int given = 0;
            if (!hasWeapon(p, el, 1)) { giveWeapon(p, el, 1); given++; }
            for (int l = 2; l <= Math.min(lv, 5); l++) {
                if (!hasWeapon(p, el, l)) { giveWeapon(p, el, l); given++; }
            }
            p.sendMessage(c(given == 0 ? "§7معاك كل أسلحتك المفتوحة." : "§aرجعتلك " + given + " سلاح."));
            return true;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("task")) {
            showTrialStatus(p);
            return true;
        }
        int pt = pts(p);
        int lv = levelOf(p);
        String next = lv >= MAX_LEVEL ? "أقصى لفل" : pt + "/" + pointsForLevel(lv + 1);
        p.sendMessage(c("§6✦ قوتك: " + el.color + el.ar + " §7| لفل §e" + lv + "§7/100 | نقاط: §f" + next));
        if (trialReady(p)) p.sendMessage(c("§e✦ لديك اختبار سري يمنع تقدمك — /gmagic task"));
        else if (completedTrials(p) < TOTAL_TRIALS)
            p.sendMessage(c("§7الاختبار السري القادم عند لفل §e" + ((completedTrials(p) + 1) * 5)));
        p.sendMessage(c("§7/gmagic weapons ← استرجاع الأسلحة المفتوحة"));
        p.sendMessage(c("§7/gmagic task ← تلميح الاختبار السري"));
        return true;
    }
}
