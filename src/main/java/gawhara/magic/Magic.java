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
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};

    enum TrialKind {
        FIRE_GLOW, FIRE_LIGHTS,
        WATER_DIVE, WATER_BOAT,
        EARTH_STONE_MINED, EARTH_ORE_MINED, EARTH_CAVE,
        WIND_AIR, WIND_SPRINT, WIND_GLIDE,
        LIGHTNING_ROD, LIGHTNING_ROD_NEAR, LIGHTNING_REDSTONE,
        SHADOW_DARK, SHADOW_SNEAK_DARK,
        ELEMENT_MASTERY
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
    AdvancedSystems advanced;
    PowerMode power;
    ShapeshiftSystem shapeshift;

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
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        advanced = new AdvancedSystems(this);
        advanced.enable();
        power = new PowerMode(this);
        power.enable();
        shapeshift = new ShapeshiftSystem(this);
        shapeshift.enable();
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
    public void onDisable() {
        if (power != null) power.shutdown();
        if (shapeshift != null) shapeshift.shutdown();
        if (advanced != null) advanced.shutdown();
        save();
    }

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

    // Every element now receives its own five-tier weapon set; empty-hand moves remain available too.
    boolean isUnarmedOnly(Element el) { return true; }

    int pts(Player p) { return data.getInt(playerPath(p) + ".points", 0); }

    int completedTrials(Player p) {
        // OP is a full-access tester/admin: secret trials never gate their content.
        if (p.isOp()) return TOTAL_TRIALS;
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
        if (p.isOp()) return MAX_LEVEL;
        return Math.min(MAX_LEVEL, (completedTrials(p) + 1) * 5);
    }

    int levelOf(Player p) {
        // OP receives virtual max level without writing artificial progress to data.yml.
        if (p.isOp()) return MAX_LEVEL;
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
            case ICE -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "اجعل أنفاسك كالصقيع، وأطلق قبضة الجليد دون سلاح عدة مرات.";
                        case 1 -> "لا تكسر الجليد... دع حركتك نفسها تترك أثر الشتاء.";
                        case 2 -> "حين يهدأ العالم، أطلق موجة البرد من جديد.";
                        default -> "أثبت أن إرادتك أبرد من أقسى عاصفة.";
                    });
            case METAL -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "اجعل جسدك درعًا، وكرر حركة الفولاذ بيدين فارغتين.";
                        case 1 -> "صلابة المعدن لا تكفي؛ أتقن ضربة الفولاذ مرة بعد مرة.";
                        case 2 -> "دع صدى الدرع يعلن أن المعدن اختارك.";
                        default -> "اختبر ثباتك، وأطلق قوة المعدن من جديد.";
                    });
            case NATURE -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "أيقظ الجذور من حولك، وكرر نداء الطبيعة دون أدوات.";
                        case 1 -> "الحياة تنمو بالتوازن؛ أطلق قوة الجذور أكثر من مرة.";
                        case 2 -> "دع نبض الطبيعة يمر عبرك مرة أخرى.";
                        default -> "أثبت أن الطبيعة تثق بمن يحميها.";
                    });
            case LIGHT -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "أطلق ومضة النور حتى تتبدد الظلال من حولك.";
                        case 1 -> "النور لا يتردد؛ كرر اندفاعتك المضيئة.";
                        case 2 -> "اجعل وهجك دليلًا لمن يسير في العتمة.";
                        default -> "أثبت أنك تستطيع حمل النور دون أن تنطفئ.";
                    });
            case SOUND -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "أطلق صدى قوتك، ودع الموجة تتحدث بدلًا منك.";
                        case 1 -> "كل نبضة تترك أثرًا؛ كرر الموجة الصوتية.";
                        case 2 -> "اجعل الصمت يسمع قوة صوتك.";
                        default -> "وحّد إيقاعك مع الصدى وأطلقه مرة أخرى.";
                    });
            case CRYSTAL -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "أطلق شظايا البلور، ودع بريقها يكشف عزيمتك.";
                        case 1 -> "النقاء يحتاج إلى تركيز؛ كرر انفجار البلور.";
                        case 2 -> "دع الكريستال يلمع بقوتك مرة أخرى.";
                        default -> "أثبت أن إرادتك أصلب من البلور.";
                    });
            case SHAPESHIFT -> new Trial(TrialKind.ELEMENT_MASTERY, 2 + difficulty,
                    switch (phase) {
                        case 0 -> "تعلّم شكلًا جديدًا من الكائنات العشرة، ثم أطلق قدرته.";
                        case 1 -> "بدّل بين أشكالك المكتسبة واستخدم قدرة كل شكل.";
                        case 2 -> "أثبت أنك أتقنت التحول باختيار شكل جديد واستخدامه.";
                        default -> "اجمع الأشكال وبدّل بينها لتثبت سيطرتك على التحول.";
                    });
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
                case ELEMENT_MASTERY -> false;
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
        h.inv = Bukkit.createInventory(h, 54, c("§8✦ اختار قوتك السحرية ✦"));
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(c(" "));
        glass.setItemMeta(gm);
        for (int i = 0; i < 54; i++) h.inv.setItem(i, glass);

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
            if (el != Element.SHAPESHIFT) {
                int affinityPercent = getConfig().getBoolean("environmental-bonus.enabled", true)
                        ? (int) Math.round((getConfig().getDouble(
                                "environmental-bonus.multipliers." + el.name(),
                                getConfig().getDouble("environmental-bonus.default-multiplier", 1.25)) - 1.0) * 100.0)
                        : 0;
                lore.add(c("§6✦ تعزيز البيئة: +" + Math.max(0, affinityPercent) + "%"));
                lore.add(c("§7" + ElementAffinity.requirement(el)));
                lore.add(c("§8يقوى الضرر والشفاء والتحكم والحركة في هذه البيئة"));
                lore.add(c("§eكل لفل من 1 إلى 50 يفتح تقنية جديدة: 10 أساسية + 40 تطور"));
                lore.add(c("§7من لفل 51 إلى 100 تزيد قوة تقنياتك كل 10 مستويات فقط"));
            }
            if (el == Element.SHAPESHIFT) {
                lore.add(c("§dكل لفل يفتح خانة شكل جديدة حتى 10 أشكال"));
                lore.add(c("§7اقتُل سبايدر أو كريبر أو أحد الكائنات المحددة لتتعلم شكله"));
                lore.add(c("§7كل شكل له قدرة قتالية مستقلة"));
            } else {
                lore.add(c("§7اختبارات سرية كل 5 مستويات حتى لفل 100"));
                lore.add(c("§7اجمع النقاط من اللعب وافتح المستويات بالتدريج"));
            }
            lore.add(c(" "));
            lore.add(c(p.isOp() ? "§d▶ OP: اضغط لاختيار/تغيير العنصر في أي وقت" : "§a▶ اضغط عشان تختارها (مرة واحدة بس)"));
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
        if (getEl(p) != null && !p.isOp()) { p.closeInventory(); return; }
        for (int i = 0; i < SLOTS.length; i++) {
            if (e.getSlot() == SLOTS[i]) { choose(p, Element.values()[i]); return; }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MagicGui) e.setCancelled(true);
    }

    void choose(Player p, Element el) {
        boolean firstChoice = getEl(p) == null;
        if (!firstChoice && !p.isOp()) return;
        if (shapeshift != null && !firstChoice) shapeshift.stopActiveForm(p);
        data.set("players." + p.getUniqueId() + ".element", el.name());
        // OP can switch schools for testing without wiping saved player progress.
        if (firstChoice) {
            data.set("players." + p.getUniqueId() + ".points", 0);
            data.set("players." + p.getUniqueId() + ".trialsCompleted", 0);
            data.set("players." + p.getUniqueId() + ".trialProgress", 0);
        }
        if (power != null) power.resetSelection(p);
        save();
        p.closeInventory();
        if (!isUnarmedOnly(el)) giveWeapon(p, el, 1);
        p.sendMessage(c(p.isOp()
                ? "§d✦ تم اختيار " + el.color + el.ar + " §7— كل قدرات العنصر مفتوحة لك كـ OP."
                : "§6✦ §fاتعلمت قوة " + el.color + el.ar + "§f! اجمع النقاط من اللعب، واكتشف الاختبارات السرية."));
        p.sendMessage(c("§eوضع القوة: اضغط §fF §eللتفعيل | سكرول للتبديل | كليك يمين للاستخدام | كليك شمال للضرب بقوتك."));
        p.sendMessage(c("§7حركتك الحالية: " + el.color + unarmedMoveName(el) + " §8| §7تتطور مع المستوى."));
        if (el != Element.SHAPESHIFT) {
            p.sendMessage(c("§6✦ بيئتك الطبيعية تقوّي العنصر: §f" + ElementAffinity.requirement(el)));
            p.sendMessage(c("§7كل لفل من 1 إلى 50 يفتح تقنية جديدة؛ من 51 إلى 100 تزيد القوة فقط كل 10 مستويات."));
        }
        if (el == Element.SHAPESHIFT) {
            p.sendMessage(c("§dاقتل الكائنات العشرة لتتعلم أشكالها. استخدم §e/gmagic forms §dلعرض سجل التحول."));
            p.sendMessage(c("§7كل مستوى يفتح خانة إضافية؛ الحد الأقصى 10 أشكال."));
        }
        p.sendMessage(c("§7اكتب §e/gmagic powers §7لرؤية وصف القوى الثلاث عشرة."));
        if (p.isOp()) p.sendMessage(c("§dOP: /gmagic choose §7لتغيير العنصر من غير تصفير التقدم، وكل الحركات والأشكال مفتوحة لك."));
        p.showTitle(net.kyori.adventure.title.Title.title(
                c(el.color + "✦ قوة " + el.ar + " ✦"), c("§7قوتك وحركتك بدون سلاح جاهزين")));
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
        meta.displayName(c(el.color + "✦ " + weaponDisplayName(el, lvl)));
        List<Component> lore = new ArrayList<>();
        lore.add(c("§7لفل " + lvl + " • قوة " + el.color + el.ar));
        lore.add(c(" "));
        lore.add(c("§eمهارة السلاح: " + el.color + weaponSkillName(el, lvl)));
        lore.add(c(weaponSkillDescription(el, lvl)));
        lore.add(c(lvl == 3 ? "§7شيفت + كليك يمين لإطلاق السهم السحري" : "§7كليك يمين لتفعيل المهارة"));
        lore.add(c("§7التأثير العنصري: " + el.ar + " • مستوى السلاح " + lvl));
        lore.add(c("§bMana: §fتُخصم من AuraSkills عند الاستخدام"));
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
        // الأسلحة اتلغت في 2.1.0 — أي سلاح قديم مبقاش صالح وبيتشال من الشنطة.
        if (true) return false;
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
            if (!isUnarmedOnly(el)) {
                for (int l = oldLevel + 1; l <= Math.min(newLevel, 5); l++) {
                    giveWeapon(p, el, l);
                    p.sendMessage(c("§6✦ §fلفل جديد! §e" + l + " §f— استلمت: " + el.color + weaponDisplayName(el, l)));
                }
            }
            p.sendMessage(c("§6✦ §fارتفع مستواك إلى §e" + newLevel + "§f!"));
            if (shapeshift != null) shapeshift.onLevelUp(p, oldLevel, newLevel);
            if (power != null) power.onLevelUp(p, oldLevel, newLevel);
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
        if (atk != null) e.setDamage(e.getDamage() * environmentMultiplier(atk, w.el()));
        hitFx(atk, v, w);
    }

    void hitFx(Player atk, LivingEntity victim, W weapon) {
        int tier = weapon.lvl();
        Element element = weapon.el();
        Location hit = victim.getLocation().add(0, 1, 0);
        victim.getWorld().spawnParticle(element.particle, hit, 14 + tier * 3, 0.25, 0.4, 0.25, 0.04);
        applyWeaponElementEffect(atk, victim, element, tier);

        // Lightning is a real chain effect now, not just a low-chance extra damage flash.
        if (element == Element.LIGHTNING) {
            double chance = Math.min(0.72, 0.18 + tier * 0.11);
            if (Math.random() < chance) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                chainLightning(atk, victim, tier, Math.min(1 + tier, 4), 4.5 + tier * 0.7);
                addEnvironmentEffect(atk, weapon.el(), atk, PotionEffectType.SPEED, 20 + tier * 8, 0);
            }
        }

        // Higher-tier weapons carry a stronger impact signature in addition to their active skill.
        if (tier == 2) {
            push(atk, weapon.el(), victim, atk.getLocation(), 0.12 + tier * 0.04, 0.08);
        } else if (tier >= 4 && element != Element.WIND && element != Element.LIGHTNING) {
            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 1);
        }
    }

    void applyWeaponElementEffect(Player attacker, LivingEntity target, Element element, int tier) {
        World world = target.getWorld();
        Location at = target.getLocation().add(0, 1, 0);
        int shortTicks = 24 + tier * 12;
        switch (element) {
            case FIRE -> {
                target.setFireTicks(Math.max(target.getFireTicks(), environmentDuration(attacker, element, 30 + tier * 18)));
                world.spawnParticle(Particle.FLAME, at, 5 + tier * 2, 0.18, 0.25, 0.18, 0.015);
            }
            case WATER -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.SLOWNESS, shortTicks, tier >= 4 ? 1 : 0);
                world.spawnParticle(Particle.SPLASH, at, 8 + tier * 2, 0.2, 0.3, 0.2, 0.04);
            }
            case EARTH -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.SLOWNESS, shortTicks, 1);
                if (tier >= 3) addEnvironmentEffect(attacker, element, target, PotionEffectType.WEAKNESS, shortTicks, 0);
                addEnvironmentEffect(attacker, element, attacker, PotionEffectType.ABSORPTION, 30 + tier * 8, 0);
                world.spawnParticle(Particle.BLOCK, at, 8 + tier * 2, 0.2, 0.25, 0.2, 0.06,
                        Material.STONE.createBlockData());
            }
            case WIND -> {
                Vector away = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
                if (away.lengthSquared() > 0.001) away.normalize(); else away = attacker.getLocation().getDirection().normalize();
                elementalVelocity(attacker, element, target, target.getVelocity().add(away.multiply(0.12 + tier * 0.035).setY(0.08)));
                world.spawnParticle(Particle.CLOUD, at, 7 + tier, 0.2, 0.2, 0.2, 0.025);
            }
            case LIGHTNING -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, at, 12 + tier * 4, 0.22, 0.35, 0.22, 0.12);
                addEnvironmentEffect(attacker, element, target, PotionEffectType.SLOWNESS, 10 + tier * 3, 0);
            }
            case SHADOW -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.WITHER, 35 + tier * 16, 0);
                if (tier >= 3) addEnvironmentEffect(attacker, element, target, PotionEffectType.BLINDNESS, 12 + tier * 4, 0);
                world.spawnParticle(Particle.PORTAL, at, 8 + tier * 3, 0.2, 0.35, 0.2, 0.08);
            }
            case ICE -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.SLOWNESS, 45 + tier * 18, 1 + (tier >= 4 ? 1 : 0));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + environmentDuration(attacker, element, 18 + tier * 12)));
                world.spawnParticle(Particle.SNOWFLAKE, at, 10 + tier * 3, 0.22, 0.3, 0.22, 0.02);
            }
            case METAL -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.WEAKNESS, 35 + tier * 14, tier >= 4 ? 1 : 0);
                if (tier >= 3) push(attacker, element, target, attacker.getLocation(), 0.18 + tier * 0.06, 0.12);
                world.spawnParticle(Particle.CRIT, at, 10 + tier * 3, 0.2, 0.3, 0.2, 0.15);
            }
            case NATURE -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.POISON, 35 + tier * 15, 0);
                if (tier >= 3) addEnvironmentEffect(attacker, element, attacker, PotionEffectType.REGENERATION, 25 + tier * 8, 0);
                world.spawnParticle(Particle.COMPOSTER, at, 8 + tier * 3, 0.2, 0.3, 0.2, 0.03);
            }
            case LIGHT -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.GLOWING, 55 + tier * 12, 0);
                if (tier >= 3) addEnvironmentEffect(attacker, element, target, PotionEffectType.BLINDNESS, 12 + tier * 4, 0);
                world.spawnParticle(Particle.END_ROD, at, 8 + tier * 3, 0.2, 0.3, 0.2, 0.03);
            }
            case SOUND -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.NAUSEA, 30 + tier * 12, 0);
                push(attacker, element, target, attacker.getLocation(), 0.16 + tier * 0.045, 0.1);
                world.spawnParticle(Particle.NOTE, at, 7 + tier * 2, 0.25, 0.3, 0.25, 0.04);
            }
            case CRYSTAL -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.SLOWNESS, 35 + tier * 15, tier >= 4 ? 1 : 0);
                world.spawnParticle(Particle.BLOCK, at, 10 + tier * 3, 0.2, 0.3, 0.2, 0.08,
                        Material.AMETHYST_BLOCK.createBlockData());
                world.spawnParticle(Particle.END_ROD, at, 3 + tier, 0.12, 0.2, 0.12, 0.02);
            }
            case SHAPESHIFT -> {
                addEnvironmentEffect(attacker, element, target, PotionEffectType.WEAKNESS, 20 + tier * 8, 0);
                world.spawnParticle(Particle.PORTAL, at, 8 + tier * 2, 0.2, 0.3, 0.2, 0.04);
            }
        }
    }

    void chainLightning(Player attacker, LivingEntity source, int tier, int maxTargets, double radius) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : source.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && living != attacker && living != source
                    && !(living instanceof ArmorStand)
                    && !(living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)) {
                candidates.add(living);
            }
        }
        candidates.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(source.getLocation())));
        boolean previousAbilityState = inAbility;
        inAbility = true;
        try {
            int chains = Math.min(maxTargets, candidates.size());
            for (int i = 0; i < chains; i++) {
                LivingEntity next = candidates.get(i);
                next.getWorld().strikeLightningEffect(next.getLocation());
                next.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, next.getLocation().add(0, 1, 0),
                        18 + tier * 3, 0.25, 0.45, 0.25, 0.12);
                elementalDamage(attacker, next, 1.0 + tier * 0.45, Element.LIGHTNING);
                addEnvironmentEffect(attacker, Element.LIGHTNING, next, PotionEffectType.SLOWNESS, 12 + tier * 3, 0);
            }
        } finally {
            inAbility = previousAbilityState;
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
        if (act != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && act != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (power != null && power.isActive(p)) return;
        ItemStack held = p.getInventory().getItemInMainHand();

        // القدرات باليد الفارغة: Shift + Right Click في الهواء أو على بلوك عادي.
        // تظل الصناديق والأبواب والأزرار القابلة للتفاعل متاحة للاستخدام الطبيعي.
        if (held.getType() == Material.AIR) {
            boolean rightClick = act == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                    || act == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
            // دعم الضغط على الهواء أو على بلوك عادي؛ لا نعطّل فتح الصناديق والأبواب والأزرار.
            boolean safeBlockClick = act != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    || e.getClickedBlock() == null || !e.getClickedBlock().getType().isInteractable();
            if (rightClick && safeBlockClick && p.isSneaking()) {
                Element el = getEl(p);
                e.setCancelled(true);
                if (el == null) {
                    p.sendActionBar(c("§cاختار قوة أولًا: /gmagic powers"));
                    openGui(p);
                    return;
                }
                unarmedAbility(p, el);
            }
            return;
        }

        W w = info(held);
        if (w == null || !isValidWeapon(p, held)) return;
        if (act == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        if (held.getType() == Material.BOW && !p.isSneaking()) return;
        e.setCancelled(true);
        ability(p, w);
    }

    // ماينكرافت لا يرسل حدث Right Click في الهواء واليد فاضية، لذلك نضيف طرقًا بديلة تعمل فعلًا:
    // 1) Shift + Right Click على كائن (موب/لاعب)  2) الضغط على Shift مرتين بسرعة  3) الأمر /gmagic m
    final Map<UUID, Long> lastSneakTap = new HashMap<>();

    @EventHandler
    public void onUnarmedEntityClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (power != null && power.isActive(p)) return;
        if (!p.isSneaking() || p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        if (e.getRightClicked() instanceof org.bukkit.inventory.Merchant) return;
        Element el = getEl(p);
        e.setCancelled(true);
        if (el == null) {
            p.sendActionBar(c("§cاختار قوة أولًا: /gmagic powers"));
            openGui(p);
            return;
        }
        unarmedAbility(p, el);
    }

    @EventHandler
    public void onUnarmedDoubleSneak(org.bukkit.event.player.PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (power != null && power.isActive(p)) return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        long now = System.currentTimeMillis();
        Long last = lastSneakTap.put(p.getUniqueId(), now);
        if (last == null || now - last > 350L) return;
        lastSneakTap.remove(p.getUniqueId());
        Element el = getEl(p);
        if (el == null) return;
        unarmedAbility(p, el);
    }

    @EventHandler
    public void onQuitClearSneak(org.bukkit.event.player.PlayerQuitEvent e) {
        lastSneakTap.remove(e.getPlayer().getUniqueId());
    }

    List<LivingEntity> near(Player p, double r) {
        List<LivingEntity> l = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof LivingEntity le && !(en instanceof ArmorStand) && le != p
                    && !(le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)) l.add(le);
        }
        return l;
    }

    double environmentMultiplier(Player caster) {
        return environmentMultiplier(caster, getEl(caster));
    }

    double environmentMultiplier(Player caster, Element element) {
        return power == null ? 1.0 : power.environmentMultiplier(caster, element);
    }

    int environmentDuration(Player caster, Element element, int ticks) {
        return power == null ? ticks : power.environmentDuration(caster, element, ticks);
    }

    void addEnvironmentEffect(Player caster, Element element, LivingEntity target,
                              PotionEffectType type, int duration, int amplifier) {
        if (target == null || target.isDead()) return;
        target.addPotionEffect(new PotionEffect(type,
                environmentDuration(caster, element, duration), amplifier));
    }

    void elementalDamage(Player caster, LivingEntity target, double damage) {
        elementalDamage(caster, target, damage, getEl(caster));
    }

    void elementalDamage(Player caster, LivingEntity target, double damage, Element element) {
        if (target == null || target.isDead() || damage <= 0) return;
        target.damage(damage * environmentMultiplier(caster, element), caster);
    }

    void elementalVelocity(Player caster, org.bukkit.entity.Entity target, Vector velocity) {
        elementalVelocity(caster, getEl(caster), target, velocity);
    }

    void elementalVelocity(Player caster, Element element, org.bukkit.entity.Entity target, Vector velocity) {
        target.setVelocity(velocity.clone().multiply(environmentMultiplier(caster, element)));
    }

    void push(Player caster, LivingEntity e, Location from, double power, double up) {
        push(caster, getEl(caster), e, from, power, up);
    }

    void push(Player caster, Element element, LivingEntity e, Location from, double power, double up) {
        Vector v = e.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 0); else v.normalize();
        elementalVelocity(caster, element, e, v.multiply(power).setY(up));
    }

    int unarmedTier(Player p) {
        // من لفل 1 إلى 50 تُفتح التقنيات؛ من 51 إلى 100 تزيد القوة كل 10 مستويات.
        int level = levelOf(p);
        int start = Math.max(51, getConfig().getInt("progression.power-scaling-start-level", 51));
        int step = Math.max(1, getConfig().getInt("progression.power-scaling-step-levels", 10));
        if (level < start) return 1;
        return Math.max(2, Math.min(6, 2 + (level - start) / step));
    }

    String unarmedMoveName(Element el) {
        return switch (el) {
            case FIRE -> "لكمة اللهب";
            case WATER -> "خطوة المد";
            case EARTH -> "زلزال الأرض";
            case WIND -> "اندفاعة الريح";
            case LIGHTNING -> "خطوة البرق";
            case SHADOW -> "اختفاء الظل";
            case ICE -> "قبضة الصقيع";
            case METAL -> "درع الفولاذ";
            case NATURE -> "جذور الحياة";
            case LIGHT -> "وميض النور";
            case SOUND -> "الموجة الصوتية";
            case CRYSTAL -> "شظايا البلور";
            case SHAPESHIFT -> "قدرة الشكل المختار";
        };
    }

    List<LivingEntity> inFront(Player p, double range, double minDot) {
        List<LivingEntity> result = new ArrayList<>();
        Vector direction = p.getLocation().getDirection().normalize();
        for (LivingEntity target : near(p, range)) {
            Vector offset = target.getLocation().add(0, 1, 0).toVector()
                    .subtract(p.getEyeLocation().toVector());
            double distance = offset.length();
            if (distance > 0.001 && distance <= range
                    && direction.dot(offset.multiply(1.0 / distance)) >= minDot) {
                result.add(target);
            }
        }
        return result;
    }

    void unarmedAbility(Player p, Element el) {
        if (el == Element.SHAPESHIFT && power != null) {
            power.useSelected(p);
            return;
        }
        int tier = unarmedTier(p);
        if (advanced == null) {
            p.sendMessage(c("§cنظام القدرات لم يبدأ بشكل صحيح. أعد تشغيل البلجن وراجع سجل السيرفر."));
            return;
        }
        if (!advanced.consumeMoveMana(p, tier)) return;

        updateTrial(p, TrialKind.ELEMENT_MASTERY, 1);
        if (advanced != null) advanced.noteCast(p);

        Location start = p.getLocation().clone();
        World world = p.getWorld();
        Vector direction = p.getLocation().getDirection().normalize();
        if (direction.lengthSquared() < 0.001) direction = new Vector(0, 0, 1);
        p.sendActionBar(c(el.color + "✦ " + unarmedMoveName(el) + " §7(بدون سلاح)"));
        world.playSound(start, el.sound, 1.0f, 1.15f);
        Fx.cast(this, p, el, tier);

        inAbility = true;
        try {
            switch (el) {
                case FIRE -> {
                    // لكمة نارية أمام اللاعب تصيب من يقع في اتجاه نظره فقط.
                    for (int i = 1; i <= 5 + tier; i++) {
                        Location point = p.getEyeLocation().add(direction.clone().multiply(i));
                        world.spawnParticle(Particle.FLAME, point, 7 + tier, 0.12, 0.12, 0.12, 0.015);
                        world.spawnParticle(Particle.SMOKE, point, 2, 0.08, 0.08, 0.08, 0.01);
                    }
                    for (LivingEntity target : inFront(p, 5 + tier, 0.55)) {
                        elementalDamage(p, target, 2.0 + tier * 1.2);
                        target.setFireTicks(environmentDuration(p, el, 40 + tier * 20));
                        push(p, target, start, 0.35 + tier * 0.10, 0.18);
                    }
                }
                case WATER -> {
                    // انزلاق مائي للأمام مع استعادة جزء من الصحة وإبعاد الخصوم.
                    elementalVelocity(p, p, direction.clone().multiply(1.0 + tier * 0.16).setY(0.24));
                    addEnvironmentEffect(p, el, p, PotionEffectType.REGENERATION, 60 + tier * 20, 0);
                    addEnvironmentEffect(p, el, p, PotionEffectType.SLOW_FALLING, 30, 0);
                    ring(start, Particle.SPLASH, null, 2.2 + tier * 0.45, 9);
                    for (LivingEntity target : near(p, 3.0 + tier * 0.35)) {
                        addEnvironmentEffect(p, el, target, PotionEffectType.SLOWNESS, 45 + tier * 10, 1);
                        push(p, target, start, 0.45 + tier * 0.08, 0.18);
                    }
                }
                case EARTH -> {
                    // ضربة قدم أرضية تمنح درعًا وتُبعد الكائنات القريبة.
                    world.spawnParticle(Particle.BLOCK, start, 55 + tier * 10,
                            1.4 + tier * 0.25, 0.15, 1.4 + tier * 0.25, 0.1,
                            Material.STONE.createBlockData());
                    ring(start, Particle.BLOCK, Material.DIRT.createBlockData(), 3.0 + tier * 0.5, 10);
                    addEnvironmentEffect(p, el, p, PotionEffectType.RESISTANCE, 60 + tier * 20, 0);
                    addEnvironmentEffect(p, el, p, PotionEffectType.ABSORPTION, 100 + tier * 20, 0);
                    for (LivingEntity target : near(p, 3.0 + tier * 0.5)) {
                        elementalDamage(p, target, 1.5 + tier);
                        push(p, target, start, 0.55 + tier * 0.10, 0.65);
                    }
                }
                case WIND -> {
                    // اندفاعة هوائية بعيدة مع سقوط آمن ودفعة هواء أمام اللاعب.
                    elementalVelocity(p, p, direction.clone().multiply(1.45 + tier * 0.22).setY(0.32 + tier * 0.035));
                    addEnvironmentEffect(p, el, p, PotionEffectType.SLOW_FALLING, 70 + tier * 10, 0);
                    for (LivingEntity target : inFront(p, 4.0 + tier, 0.35)) {
                        push(p, target, start, 0.75 + tier * 0.12, 0.35);
                    }
                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override public void run() {
                            if (!p.isOnline() || ticks++ >= 12) { cancel(); return; }
                            Location trail = p.getLocation().add(0, 0.7, 0);
                            p.getWorld().spawnParticle(Particle.CLOUD, trail, 10, 0.25, 0.25, 0.25, 0.02);
                        }
                    }.runTaskTimer(this, 0, 1);
                }
                case LIGHTNING -> {
                    // اندفاعة برق، ثم شرارة تصيب أقرب خصوم أمام اللاعب دون صاعقة طبيعية.
                    elementalVelocity(p, p, direction.clone().multiply(1.25 + tier * 0.20).setY(0.22));
                    addEnvironmentEffect(p, el, p, PotionEffectType.SPEED, 50 + tier * 15, 1);
                    List<LivingEntity> targets = inFront(p, 6.0 + tier, 0.4);
                    targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(start)));
                    int hits = Math.min(targets.size(), 1 + tier / 2);
                    for (int i = 0; i < hits; i++) {
                        LivingEntity target = targets.get(i);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0),
                                28 + tier * 5, 0.3, 0.55, 0.3, 0.12);
                        world.strikeLightningEffect(target.getLocation());
                        elementalDamage(p, target, 2.0 + tier * 1.3);
                    }
                    world.spawnParticle(Particle.ELECTRIC_SPARK, start, 22, 0.5, 0.6, 0.5, 0.15);
                }
                case SHADOW -> {
                    // اختفاء خاطف وانتقال آمن للأمام، دون الحاجة إلى أداة أو سلاح.
                    Location destination = start.clone();
                    int distance = 3 + tier;
                    for (int i = 1; i <= distance; i++) {
                        Location candidate = start.clone().add(direction.clone().multiply(i));
                        if (candidate.getBlock().isPassable()
                                && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                                && candidate.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                            destination = candidate;
                        } else {
                            break;
                        }
                    }
                    world.spawnParticle(Particle.PORTAL, start.clone().add(0, 1, 0), 55, 0.35, 0.7, 0.35, 0.35);
                    p.teleport(destination);
                    addEnvironmentEffect(p, el, p, PotionEffectType.INVISIBILITY, 35 + tier * 12, 0);
                    addEnvironmentEffect(p, el, p, PotionEffectType.SPEED, 35 + tier * 10, 1);
                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                            destination.clone().add(0, 1, 0), 55, 0.35, 0.7, 0.35, 0.2);
                    p.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.2f);
                }
                case ICE -> {
                    // لكمة صقيع أمامية تبطئ الخصوم وتراكم عليهم التجمد.
                    for (int i = 1; i <= 5 + tier; i++) {
                        Location point = p.getEyeLocation().add(direction.clone().multiply(i));
                        world.spawnParticle(Particle.SNOWFLAKE, point, 8 + tier * 2, 0.16, 0.16, 0.16, 0.01);
                    }
                    for (LivingEntity target : inFront(p, 5 + tier, 0.45)) {
                        elementalDamage(p, target, 1.5 + tier * 1.1);
                        addEnvironmentEffect(p, el, target, PotionEffectType.SLOWNESS, 70 + tier * 15, 1 + tier / 3);
                        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + environmentDuration(p, el, 35 + tier * 15)));
                    }
                    addEnvironmentEffect(p, el, p, PotionEffectType.SPEED, 35 + tier * 8, 0);
                }
                case METAL -> {
                    // وضعية فولاذية تمنح حماية ثم تطلق دفعة معدنية حول اللاعب.
                    world.spawnParticle(Particle.BLOCK, start, 65 + tier * 12,
                            1.5 + tier * 0.2, 0.35, 1.5 + tier * 0.2, 0.1,
                            Material.IRON_BLOCK.createBlockData());
                    ring(start, Particle.CRIT, null, 2.5 + tier * 0.5, 9);
                    addEnvironmentEffect(p, el, p, PotionEffectType.RESISTANCE, 70 + tier * 18, 1);
                    addEnvironmentEffect(p, el, p, PotionEffectType.ABSORPTION, 100 + tier * 20, 1);
                    for (LivingEntity target : near(p, 3.0 + tier * 0.45)) {
                        elementalDamage(p, target, 1.5 + tier);
                        push(p, target, start, 0.65 + tier * 0.12, 0.3);
                    }
                }
                case NATURE -> {
                    // جذور خضراء تحمي صاحبها وتحبس الخصوم القريبين.
                    ring(start, Particle.HAPPY_VILLAGER, null, 2.6 + tier * 0.5, 10);
                    world.spawnParticle(Particle.COMPOSTER, start.clone().add(0, 0.4, 0), 35 + tier * 8, 1, 0.2, 1, 0.02);
                    addEnvironmentEffect(p, el, p, PotionEffectType.REGENERATION, 90 + tier * 20, 0);
                    addEnvironmentEffect(p, el, p, PotionEffectType.ABSORPTION, 80 + tier * 15, 0);
                    for (LivingEntity target : near(p, 3.2 + tier * 0.4)) {
                        elementalDamage(p, target, 1.0 + tier * 0.7);
                        addEnvironmentEffect(p, el, target, PotionEffectType.SLOWNESS, 75 + tier * 12, 2);
                        push(p, target, start, 0.25, 0.12);
                    }
                }
                case LIGHT -> {
                    // ومضة قوية تعمي الخصوم وتمنح اللاعب سرعة اندفاع قصيرة.
                    world.spawnParticle(Particle.END_ROD, start.clone().add(0, 1, 0), 55 + tier * 12, 1.1, 0.9, 1.1, 0.08);
                    world.spawnParticle(Particle.FLASH, start.clone().add(0, 1, 0), 1);
                    addEnvironmentEffect(p, el, p, PotionEffectType.SPEED, 65 + tier * 12, 1 + tier / 4);
                    addEnvironmentEffect(p, el, p, PotionEffectType.GLOWING, 50 + tier * 10, 0);
                    for (LivingEntity target : near(p, 3.5 + tier * 0.45)) {
                        elementalDamage(p, target, 1.0 + tier);
                        addEnvironmentEffect(p, el, target, PotionEffectType.BLINDNESS, 35 + tier * 12, 0);
                    }
                }
                case SOUND -> {
                    // موجة صوتية دائرية تزيح الخصوم بعيدًا عن اللاعب.
                    world.spawnParticle(Particle.SONIC_BOOM, start.clone().add(0, 1, 0), 1);
                    ring(start, Particle.NOTE, null, 3.0 + tier * 0.65, 10);
                    world.playSound(start, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.3f, 0.55f + tier * 0.12f);
                    for (LivingEntity target : near(p, 4.0 + tier * 0.5)) {
                        elementalDamage(p, target, 2.0 + tier);
                        push(p, target, start, 1.0 + tier * 0.15, 0.5 + tier * 0.08);
                    }
                }
                case CRYSTAL -> {
                    // شظايا بلورية تصيب الأهداف الواقعة أمام اللاعب.
                    for (int i = 1; i <= 6 + tier; i++) {
                        Location point = p.getEyeLocation().add(direction.clone().multiply(i));
                        world.spawnParticle(Particle.CRIT, point, 7 + tier, 0.12, 0.12, 0.12, 0.08);
                        world.spawnParticle(Particle.END_ROD, point, 2 + tier / 2, 0.04, 0.04, 0.04, 0.01);
                    }
                    List<LivingEntity> targets = inFront(p, 6 + tier, 0.45);
                    targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(start)));
                    int hits = Math.min(targets.size(), 1 + tier / 2);
                    for (int i = 0; i < hits; i++) {
                        LivingEntity target = targets.get(i);
                        elementalDamage(p, target, 2.0 + tier * 1.15);
                        addEnvironmentEffect(p, el, target, PotionEffectType.SLOWNESS, 45 + tier * 10, 1);
                        world.spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 22,
                                0.25, 0.4, 0.25, 0.08, Material.AMETHYST_BLOCK.createBlockData());
                    }
                }
                case SHAPESHIFT -> { /* Routed through PowerMode above. */ }
            }
        } finally {
            inAbility = false;
        }
    }

    String weaponDisplayName(Element element, int tier) {
        String[][] names = {
            {"شفرة الجمر", "فأس اللهب", "قوس الشهاب", "سيف التنين", "نصل الانفجار"},
            {"رمح المد", "فأس الأعماق", "قوس الزبد", "نصل الطوفان", "سيف المحيط"},
            {"نصل الصخر", "كسارة الجبال", "قوس الحصى", "مطرقة الزلزال", "قاطع القارات"},
            {"شفرة النسيم", "فأس العاصفة", "قوس الإعصار", "نصل السماء", "سيف العاصفة الكبرى"},
            {"شرارة الرعد", "فأس السلسلة", "قوس الصاعقة", "نصل العاصفة", "حكم الرعد"},
            {"نصل الكسوف", "فأس الهاوية", "قوس الشفق", "سيف العتمة", "قاطع الفراغ"},
            {"نصل الصقيع", "فأس الشتاء", "قوس البلور الجليدي", "سيف العاصفة الثلجية", "قلب الشتاء"},
            {"شفرة الفولاذ", "كسارة الحديد", "قوس الشظايا", "سيف المغناطيس", "حصن المعدن"},
            {"نصل الأشواك", "فأس الجذور", "قوس البذور", "سيف الإزهار", "حارس الغابة"},
            {"شفرة الفجر", "فأس الشروق", "قوس الشعاع", "نصل الوميض", "تاج الشمس"},
            {"نصل النغمة", "فأس الصدى", "قوس الرنين", "سيف الاهتزاز", "مطرقة الصوت"},
            {"نصل الأميثيست", "كسارة البلور", "قوس الشظايا", "سيف المنشور", "قلب الكريستال"}
        };
        return names[element.ordinal()][Math.max(1, Math.min(5, tier)) - 1];
    }

    String weaponSkillName(Element element, int tier) {
        String[][] names = {
            {"قوس اللهب", "موجة الجمر", "كرة اللهب", "دوامة الحريق", "انفجار الجحيم"},
            {"رمح الضغط", "موجة المد", "قذيفة الماء", "دوامة المحيط", "الطوفان العظيم"},
            {"سن الصخر", "هزة أرضية", "قذيفة الجرانيت", "درع الجبل", "زلزال القارات"},
            {"قطع النسيم", "إعصار الريح", "سهم العاصفة", "خطوة الرياح", "التورنادو الأعظم"},
            {"شرارة الصاعقة", "سلسلة الرعد", "سهم البرق", "عاصفة البرق", "حكم الرعد"},
            {"طعنة الظل", "سقوط الليل", "سهم الفراغ", "خطوة الشبح", "الكسوف الأبدي"},
            {"إبرة الصقيع", "موجة التجمد", "رمح الجليد", "عاصفة الثلج", "الصفر المطلق"},
            {"اختراق الفولاذ", "موجة الحديد", "شظية معدنية", "جذب المغناطيس", "حصن الفولاذ"},
            {"شوكة سامة", "أسر الجذور", "قذيفة البذور", "ازدهار الحياة", "الغابة القديمة"},
            {"شعاع الفجر", "نبضة التوهج", "رمح الضوء", "خطوة الوميض", "الشمس الساطعة"},
            {"لسعة صوتية", "موجة الصدمة", "قذيفة الرنين", "نبضة الصدى", "الانفجار الصوتي"},
            {"شظية بلورية", "وابل البلور", "رمح الكريستال", "سجن المنشور", "انفجار المنشور"}
        };
        return names[element.ordinal()][Math.max(1, Math.min(5, tier)) - 1];
    }

    String weaponSkillDescription(Element element, int tier) {
        String[][] descriptions = {
            {"اندفاعة لهب دقيقة تحرق الهدف أمامك", "موجة جمر تضرب عدة أهداف في خط مستقيم", "كرة نارية بعيدة المدى تتبع أثر اللهب", "دوامة حريق تحيط بك وتحرق القريبين", "انفجار جحيم واسع يمنحك قوة إضافية"},
            {"رمح ماء يبطئ الهدف ويعيد لك بعض الصحة", "موجة مد تدفع الخصوم بعيدًا عنك", "قذيفة مائية تصيب هدفًا من مسافة", "دوامة تسحب الخصوم نحو مركزها", "طوفان واسع يضر الخصوم ويجدد صحتك"},
            {"سن صخري يرفع الهدف ويكسر اندفاعه", "هزة أرضية تصيب من حولك", "قذيفة حجرية بعيدة المدى", "درع جبلي يحميك ويضرب الأرض حولك", "زلزال واسع يمنحك امتصاصًا ومقاومة"},
            {"قطع هوائي يدفع الهدف بعيدًا", "إعصار يرفع الخصوم ويبعثرهم", "سهم سريع يحمل أثر العاصفة", "اندفاعة جوية سريعة عبر ساحة القتال", "تورنادو يرفع الخصوم ويمنحك سقوطًا آمنًا"},
            {"شرارة دقيقة تتصل بهدف قريب", "سلسلة برق تقفز بين عدة أهداف", "سهم برق بعيد المدى", "عاصفة تضرب عدة أهداف وتسرّعك", "حكم الرعد يضرب مساحة واسعة"},
            {"طعنة ظل تترك أثرًا مظلمًا", "ظلام مفاجئ يضعف رؤية القريبين", "سهم فراغ يصيب من بعيد", "خطوة شبح تمنحك اختفاءً واندفاعًا", "كسوف يضعف الخصوم ويخفيك مؤقتًا"},
            {"إبرة جليدية تجمد الهدف تدريجيًا", "موجة صقيع تبطئ عدة خصوم", "رمح جليدي بعيد المدى", "عاصفة ثلجية تجمد المنطقة حولك", "الصفر المطلق يجمّد مساحة واسعة"},
            {"ضربة فولاذية تضعف الهدف", "موجة حديد تدفع الخصوم للخلف", "شظية معدنية بعيدة المدى", "جذب مغناطيسي يسحب الخصوم نحوك", "حصن فولاذي يمنحك مقاومة ويصد القريبين"},
            {"شوكة سامة تصيب هدفًا أمامك", "جذور تقيد حركة عدة خصوم", "قذيفة بذور تحمل أثر السم", "ازدهار يعالجك ويؤذي القريبين", "غابة قديمة تقيد الخصوم وتجدد صحتك"},
            {"شعاع يكشف الهدف ويشوشه", "نبضة نور تكشف القريبين وتدفعهم", "رمح ضوئي بعيد المدى", "خطوة وميض سريعة عبر خط الهجوم", "شمس ساطعة تكشف الخصوم وتمنحك سرعة"},
            {"لسعة صوتية تصدم هدفًا أمامك", "موجة صدمة تدفع القريبين", "قذيفة رنين بعيدة المدى", "نبضة صدى تهز المنطقة عدة مرات بصريًا", "انفجار صوتي واسع يبعثر الخصوم"},
            {"شظية حادة تبطئ الهدف", "وابل بلوري يصيب عدة خصوم", "رمح كريستالي بعيد المدى", "سجن منشوري يقيّد هدفًا أمامك", "انفجار منشوري يملأ المنطقة بالشظايا"}
        };
        return "§7" + descriptions[element.ordinal()][Math.max(1, Math.min(5, tier)) - 1];
    }

    void weaponDamage(Player player, LivingEntity target, W weapon, double damage, Location origin,
                      double knockback, double knockup) {
        elementalDamage(player, target, damage, weapon.el());
        applyWeaponElementEffect(player, target, weapon.el(), weapon.lvl());
        if (knockback > 0) push(player, weapon.el(), target, origin, knockback, knockup);
    }

    void launchWeaponArrow(Player player, W weapon, double damage) {
        Arrow arrow = player.launchProjectile(Arrow.class);
        elementalVelocity(player, weapon.el(), arrow, player.getLocation().getDirection().normalize().multiply(2.8));
        arrow.setDamage(damage);
        arrow.setCritical(true);
        arrow.setMetadata("gm", new FixedMetadataValue(this,
                weapon.el().name() + ":" + weapon.lvl() + ":" + player.getUniqueId() + ":" + magicGeneration(player)));
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || ticks++ > 100) { cancel(); return; }
                arrow.getWorld().spawnParticle(weapon.el().particle, arrow.getLocation(),
                        3 + weapon.lvl(), 0.04, 0.04, 0.04, 0.01);
                if (weapon.el() == Element.LIGHTNING) {
                    arrow.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, arrow.getLocation(), 4, 0.04, 0.04, 0.04, 0.08);
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    void lightningWeaponAbility(Player player, W weapon, Location origin, int tier, double damage) {
        World world = player.getWorld();
        if (tier == 1) {
            List<LivingEntity> targets = inFront(player, 8.0, 0.55);
            targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
            LivingEntity target = targets.isEmpty() ? null : targets.get(0);
            if (target == null) {
                player.sendActionBar(c("§eوجّه نظرك نحو خصم لتصيب بشرارة الصاعقة."));
                return;
            }
            world.strikeLightningEffect(target.getLocation());
            elementalDamage(player, target, damage + 1.5, Element.LIGHTNING);
            addEnvironmentEffect(player, Element.LIGHTNING, target, PotionEffectType.SLOWNESS, 16, 1);
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 35, 0.3, 0.5, 0.3, 0.15);
            chainLightning(player, target, tier, 1, 4.0);
        } else if (tier == 2) {
            List<LivingEntity> targets = near(player, 6.5);
            targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
            int limit = Math.min(targets.size(), 3);
            ring(origin, Particle.ELECTRIC_SPARK, null, 4.5, 8);
            for (int i = 0; i < limit; i++) {
                LivingEntity target = targets.get(i);
                world.strikeLightningEffect(target.getLocation());
                elementalDamage(player, target, damage * 1.05, Element.LIGHTNING);
                addEnvironmentEffect(player, Element.LIGHTNING, target, PotionEffectType.SLOWNESS, 22, 1);
                world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 26, 0.35, 0.6, 0.35, 0.14);
            }
        } else if (tier == 3) {
            launchWeaponArrow(player, weapon, damage + 2.0);
        } else if (tier == 4) {
            List<LivingEntity> targets = near(player, 8.0);
            targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
            int limit = Math.min(targets.size(), 5);
            ring(origin, Particle.ELECTRIC_SPARK, null, 6.0, 10);
            for (int i = 0; i < limit; i++) {
                LivingEntity target = targets.get(i);
                world.strikeLightningEffect(target.getLocation());
                elementalDamage(player, target, damage * 1.15, Element.LIGHTNING);
                addEnvironmentEffect(player, Element.LIGHTNING, target, PotionEffectType.SLOWNESS, 28, 1);
            }
            addEnvironmentEffect(player, Element.LIGHTNING, player, PotionEffectType.SPEED, 90, 1);
            world.spawnParticle(Particle.ELECTRIC_SPARK, origin.clone().add(0, 1, 0), 65, 2.0, 1.0, 2.0, 0.2);
        } else {
            List<LivingEntity> targets = near(player, 11.0);
            targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
            int limit = Math.min(targets.size(), 8);
            ring(origin, Particle.ELECTRIC_SPARK, null, 9.0, 14);
            world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f, 0.65f);
            world.spawnParticle(Particle.FLASH, origin.clone().add(0, 1, 0), 2);
            for (int i = 0; i < limit; i++) {
                LivingEntity target = targets.get(i);
                world.strikeLightningEffect(target.getLocation());
                elementalDamage(player, target, damage * 1.35, Element.LIGHTNING);
                addEnvironmentEffect(player, Element.LIGHTNING, target, PotionEffectType.SLOWNESS, 35, 1);
                world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 50, 0.45, 0.8, 0.45, 0.18);
            }
            addEnvironmentEffect(player, Element.LIGHTNING, player, PotionEffectType.SPEED, 130, 1);
            addEnvironmentEffect(player, Element.LIGHTNING, player, PotionEffectType.HASTE, 100, 1);
        }
    }

    List<LivingEntity> sortedFront(Player player, double range, double dot) {
        List<LivingEntity> targets = inFront(player, range, dot);
        targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(player.getLocation())));
        return targets;
    }

    int hitFront(Player player, W weapon, Location origin, double damage, double range, double dot,
                 int maxTargets, double knockback, double knockup) {
        List<LivingEntity> targets = sortedFront(player, range, dot);
        int count = Math.min(maxTargets, targets.size());
        for (int i = 0; i < count; i++) {
            weaponDamage(player, targets.get(i), weapon, damage, origin, knockback, knockup);
        }
        return count;
    }

    int hitArea(Player player, W weapon, Location origin, double damage, double radius,
                int maxTargets, double knockback, double knockup) {
        List<LivingEntity> targets = near(player, radius);
        targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
        int count = Math.min(maxTargets, targets.size());
        for (int i = 0; i < count; i++) {
            weaponDamage(player, targets.get(i), weapon, damage, origin, knockback, knockup);
        }
        return count;
    }

    void pullTarget(Player caster, LivingEntity target, Location center, double strength, double lift) {
        pullTarget(caster, getEl(caster), target, center, strength, lift);
    }

    void pullTarget(Player caster, Element element, LivingEntity target, Location center, double strength, double lift) {
        Vector pull = center.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (pull.lengthSquared() > 0.001) pull.normalize();
        elementalVelocity(caster, element, target, target.getVelocity().add(pull.multiply(strength)).setY(lift));
    }

    void lineParticles(Player player, Particle particle, int points, double spacing, int amount) {
        Vector direction = player.getLocation().getDirection().normalize();
        for (int i = 1; i <= points; i++) {
            Location at = player.getEyeLocation().clone().add(direction.clone().multiply(i * spacing));
            player.getWorld().spawnParticle(particle, at, amount, 0.12, 0.12, 0.12, 0.02);
        }
    }

    void elementalWeaponAbility(Player player, W weapon, Location origin, double damage) {
        int tier = weapon.lvl();
        World world = player.getWorld();
        switch (weapon.el()) {
            case FIRE -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.FLAME, 5, 0.75, 5); hitFront(player, weapon, origin, damage + 1.4, 7.0, 0.45, 1, 0.28, 0.1); }
                    case 2 -> { lineParticles(player, Particle.FLAME, 8, 0.75, 9); hitFront(player, weapon, origin, damage + 2.0, 8.0, 0.2, 4, 0.42, 0.18); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.STRENGTH, 45, 0); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.4);
                    case 4 -> { ring(origin, Particle.FLAME, null, 6.5, 9); hitArea(player, weapon, origin, damage + 2.4, 6.5, 6, 0.48, 0.18); world.spawnParticle(Particle.SOUL_FIRE_FLAME, origin.clone().add(0, 1, 0), 45, 1.5, 0.8, 1.5, 0.04); }
                    default -> { ring(origin, Particle.FLAME, null, 10.0, 12); hitArea(player, weapon, origin, damage + 4.2, 10.0, 9, 0.85, 0.34); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.STRENGTH, 120, 1); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 80, 0); }
                }
            }
            case WATER -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.SPLASH, 5, 0.7, 5); if (hitFront(player, weapon, origin, damage + 1.1, 7.5, 0.5, 1, 0.1, 0.0) > 0) player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + environmentMultiplier(player, weapon.el()))); }
                    case 2 -> { lineParticles(player, Particle.SPLASH, 8, 0.7, 12); hitFront(player, weapon, origin, damage + 1.8, 8.5, 0.1, 5, 0.78, 0.24); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.REGENERATION, 50, 0); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.0);
                    case 4 -> { ring(origin, Particle.SPLASH, null, 6.5, 10); for (LivingEntity target : near(player, 6.5)) { weaponDamage(player, target, weapon, damage + 1.2, origin, 0, 0); pullTarget(player, weapon.el(), target, origin, 0.55, 0.08); } }
                    default -> { ring(origin, Particle.SPLASH, null, 10.0, 14); hitArea(player, weapon, origin, damage + 3.6, 10.0, 9, 0.9, 0.3); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.REGENERATION, 140, 1); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.WATER_BREATHING, 160, 0); }
                }
            }
            case EARTH -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.CRIT, 5, 0.65, 4); hitFront(player, weapon, origin, damage + 1.7, 6.5, 0.4, 1, 0.2, 0.55); }
                    case 2 -> { world.spawnParticle(Particle.BLOCK, origin, 85, 3.2, 0.15, 3.2, 0.1, Material.STONE.createBlockData()); ring(origin, Particle.BLOCK, Material.STONE.createBlockData(), 5.0, 8); hitArea(player, weapon, origin, damage + 2.0, 5.0, 6, 0.45, 0.72); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.5);
                    case 4 -> { ring(origin, Particle.BLOCK, Material.DEEPSLATE.createBlockData(), 6.5, 9); hitArea(player, weapon, origin, damage + 2.2, 6.5, 7, 0.62, 0.36); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 100, 0); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.ABSORPTION, 100, 1); }
                    default -> { ring(origin, Particle.BLOCK, Material.STONE.createBlockData(), 10.0, 12); hitArea(player, weapon, origin, damage + 4.3, 10.0, 10, 0.95, 0.78); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 160, 1); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.ABSORPTION, 180, 2); }
                }
            }
            case WIND -> {
                switch (tier) {
                    case 1 -> { elementalVelocity(player, weapon.el(), player, player.getLocation().getDirection().normalize().multiply(0.65).setY(0.12)); lineParticles(player, Particle.CLOUD, 5, 0.55, 5); hitFront(player, weapon, origin, damage + 1.0, 6.5, 0.42, 1, 0.6, 0.14); }
                    case 2 -> { ring(origin, Particle.CLOUD, null, 5.2, 8); hitArea(player, weapon, origin, damage + 1.9, 5.2, 7, 0.82, 0.42); world.spawnParticle(Particle.CLOUD, origin.clone().add(0, 1, 0), 65, 1.8, 0.8, 1.8, 0.06); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.1);
                    case 4 -> { Vector dash = player.getLocation().getDirection().normalize().multiply(1.65).setY(0.28); elementalVelocity(player, weapon.el(), player, dash); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.SPEED, 80, 1); lineParticles(player, Particle.CLOUD, 10, 0.45, 5); hitFront(player, weapon, origin, damage + 2.3, 7.5, 0.15, 4, 0.65, 0.24); }
                    default -> { ring(origin, Particle.CLOUD, null, 10.0, 12); hitArea(player, weapon, origin, damage + 3.8, 10.0, 9, 0.95, 0.9); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.SLOW_FALLING, 180, 0); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.SPEED, 160, 1); }
                }
            }
            case LIGHTNING -> lightningWeaponAbility(player, weapon, origin, tier, damage);
            case SHADOW -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.PORTAL, 5, 0.7, 6); hitFront(player, weapon, origin, damage + 1.5, 7.0, 0.45, 1, 0.2, 0.08); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.INVISIBILITY, 25, 0); }
                    case 2 -> { ring(origin, Particle.PORTAL, null, 5.5, 9); hitArea(player, weapon, origin, damage + 1.7, 5.5, 6, 0.3, 0.12); for (LivingEntity target : near(player, 5.5)) addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.BLINDNESS, 35, 0); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.3);
                    case 4 -> { elementalVelocity(player, weapon.el(), player, player.getLocation().getDirection().normalize().multiply(1.45).setY(0.18)); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.INVISIBILITY, 65, 0); lineParticles(player, Particle.PORTAL, 9, 0.5, 6); hitFront(player, weapon, origin, damage + 2.0, 8.0, 0.1, 4, 0.35, 0.14); }
                    default -> { ring(origin, Particle.PORTAL, null, 9.5, 13); hitArea(player, weapon, origin, damage + 3.7, 9.5, 9, 0.75, 0.28); for (LivingEntity target : near(player, 9.5)) addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.BLINDNESS, 50, 0); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.INVISIBILITY, 100, 0); }
                }
            }
            case ICE -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.SNOWFLAKE, 5, 0.7, 6); hitFront(player, weapon, origin, damage + 1.3, 7.0, 0.5, 1, 0.1, 0.0); }
                    case 2 -> { lineParticles(player, Particle.SNOWFLAKE, 8, 0.7, 12); hitFront(player, weapon, origin, damage + 1.8, 8.5, 0.1, 5, 0.3, 0.12); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.2);
                    case 4 -> { ring(origin, Particle.SNOWFLAKE, null, 6.5, 11); hitArea(player, weapon, origin, damage + 2.0, 6.5, 7, 0.25, 0.08); for (LivingEntity target : near(player, 6.5)) target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + environmentDuration(player, weapon.el(), 90))); }
                    default -> { ring(origin, Particle.SNOWFLAKE, null, 10.0, 15); hitArea(player, weapon, origin, damage + 3.9, 10.0, 10, 0.32, 0.1); for (LivingEntity target : near(player, 10.0)) { target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + environmentDuration(player, weapon.el(), 150))); addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.SLOWNESS, 100, 3); } addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 100, 0); }
                }
            }
            case METAL -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.CRIT, 5, 0.65, 6); hitFront(player, weapon, origin, damage + 1.8, 6.5, 0.5, 1, 0.18, 0.08); }
                    case 2 -> { ring(origin, Particle.CRIT, null, 4.8, 8); hitArea(player, weapon, origin, damage + 1.9, 4.8, 7, 0.95, 0.18); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.6);
                    case 4 -> { ring(origin, Particle.CRIT, null, 6.0, 8); for (LivingEntity target : near(player, 6.0)) { pullTarget(player, weapon.el(), target, origin, 0.72, 0.12); weaponDamage(player, target, weapon, damage + 1.6, origin, 0, 0); } addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 65, 0); }
                    default -> { ring(origin, Particle.CRIT, null, 9.5, 12); hitArea(player, weapon, origin, damage + 3.8, 9.5, 10, 1.1, 0.28); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 160, 1); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.ABSORPTION, 160, 2); }
                }
            }
            case NATURE -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.COMPOSTER, 5, 0.7, 6); hitFront(player, weapon, origin, damage + 1.3, 7.0, 0.48, 1, 0.1, 0.0); }
                    case 2 -> { ring(origin, Particle.COMPOSTER, null, 5.2, 9); hitArea(player, weapon, origin, damage + 1.7, 5.2, 7, 0.18, 0.0); for (LivingEntity target : near(player, 5.2)) addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.SLOWNESS, 100, 2); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.1);
                    case 4 -> { ring(origin, Particle.HAPPY_VILLAGER, null, 6.2, 10); hitArea(player, weapon, origin, damage + 1.8, 6.2, 7, 0.2, 0.0); player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0 * environmentMultiplier(player, weapon.el()))); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.REGENERATION, 100, 1); }
                    default -> { ring(origin, Particle.HAPPY_VILLAGER, null, 10.0, 13); hitArea(player, weapon, origin, damage + 3.6, 10.0, 9, 0.25, 0.0); for (LivingEntity target : near(player, 10.0)) addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.SLOWNESS, 100, 3); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.REGENERATION, 180, 1); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.RESISTANCE, 100, 0); }
                }
            }
            case LIGHT -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.END_ROD, 6, 0.7, 5); hitFront(player, weapon, origin, damage + 1.3, 7.5, 0.52, 1, 0.1, 0.0); }
                    case 2 -> { ring(origin, Particle.END_ROD, null, 5.0, 8); hitArea(player, weapon, origin, damage + 1.6, 5.0, 7, 0.62, 0.12); for (LivingEntity target : near(player, 5.0)) addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.GLOWING, 100, 0); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.2);
                    case 4 -> { elementalVelocity(player, weapon.el(), player, player.getLocation().getDirection().normalize().multiply(1.5).setY(0.22)); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.SPEED, 100, 1); lineParticles(player, Particle.END_ROD, 10, 0.45, 7); hitFront(player, weapon, origin, damage + 2.2, 8.0, 0.12, 4, 0.4, 0.12); }
                    default -> { ring(origin, Particle.END_ROD, null, 10.0, 13); hitArea(player, weapon, origin, damage + 3.8, 10.0, 10, 0.55, 0.16); for (LivingEntity target : near(player, 10.0)) { addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.GLOWING, 140, 0); addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.BLINDNESS, 35, 0); } addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.SPEED, 180, 1); }
                }
            }
            case SOUND -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.NOTE, 5, 0.65, 5); hitFront(player, weapon, origin, damage + 1.4, 7.0, 0.48, 1, 0.9, 0.22); }
                    case 2 -> { ring(origin, Particle.NOTE, null, 5.5, 8); hitArea(player, weapon, origin, damage + 1.8, 5.5, 8, 1.05, 0.28); world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.45f); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.1);
                    case 4 -> { for (int pulse = 1; pulse <= 3; pulse++) ring(origin, Particle.NOTE, null, 2.0 + pulse * 1.5, 5 + pulse * 2); hitArea(player, weapon, origin, damage + 2.5, 7.0, 8, 0.85, 0.24); world.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.6f, 0.7f); }
                    default -> { ring(origin, Particle.NOTE, null, 10.0, 12); hitArea(player, weapon, origin, damage + 4.0, 10.0, 10, 1.3, 0.5); world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.8f); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.ABSORPTION, 180, 2); }
                }
            }
            case CRYSTAL -> {
                switch (tier) {
                    case 1 -> { lineParticles(player, Particle.END_ROD, 5, 0.7, 7); hitFront(player, weapon, origin, damage + 1.4, 7.0, 0.5, 1, 0.12, 0.04); }
                    case 2 -> { ring(origin, Particle.BLOCK, Material.AMETHYST_BLOCK.createBlockData(), 5.0, 8); hitArea(player, weapon, origin, damage + 1.8, 5.0, 7, 0.36, 0.16); }
                    case 3 -> launchWeaponArrow(player, weapon, damage + 2.4);
                    case 4 -> { List<LivingEntity> targets = sortedFront(player, 8.0, 0.12); if (!targets.isEmpty()) { LivingEntity target = targets.get(0); weaponDamage(player, target, weapon, damage + 2.8, origin, 0, 0); addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.SLOWNESS, 100, 5); addEnvironmentEffect(player, weapon.el(), target, PotionEffectType.WEAKNESS, 80, 1); target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 55, 0.6, 0.9, 0.6, 0.08, Material.AMETHYST_BLOCK.createBlockData()); } }
                    default -> { ring(origin, Particle.BLOCK, Material.AMETHYST_BLOCK.createBlockData(), 10.0, 13); hitArea(player, weapon, origin, damage + 3.9, 10.0, 10, 0.48, 0.2); world.spawnParticle(Particle.END_ROD, origin.clone().add(0, 1, 0), 100, 2.0, 1.3, 2.0, 0.06); addEnvironmentEffect(player, weapon.el(), player, PotionEffectType.ABSORPTION, 180, 1); }
                }
            }
            case SHAPESHIFT -> { /* Shapeshift is an unarmed school; no weapon skills are issued. */ }
        }
    }

    void ability(Player p, W weapon) {
        if (advanced == null) {
            p.sendMessage(c("§cنظام AuraSkills Mana غير جاهز؛ لا يمكن استخدام السلاح السحري."));
            return;
        }
        if (weapon.lvl() == 1) {
            double range = weapon.el() == Element.LIGHTNING ? 8.0 : 7.5;
            double dot = weapon.el() == Element.LIGHTNING ? 0.55 : 0.45;
            if (inFront(p, range, dot).isEmpty()) {
                p.sendActionBar(c("§eوجّه السلاح نحو خصم قريب لتفعيل " + weaponSkillName(weapon.el(), 1) + "."));
                return;
            }
        }
        // Every weapon skill now spends the real AuraSkills mana pool.
        if (!advanced.consumeMana(p, "weapon", weapon.lvl())) return;
        Fx.weapon(this, p, weapon.el(), weapon.lvl());

        int tier = weapon.lvl();
        updateTrial(p, TrialKind.ELEMENT_MASTERY, 1);
        advanced.noteCast(p);

        Location origin = p.getLocation().clone();
        World world = p.getWorld();
        double damage = 3.2 + tier * 1.15;
        p.sendActionBar(c(weapon.el().color + "✦ " + weaponSkillName(weapon.el(), tier) + " §7| Mana AuraSkills"));
        world.playSound(origin, weapon.el().sound, 1.15f, 0.85f + tier * 0.08f);

        inAbility = true;
        try {
            elementalWeaponAbility(p, weapon, origin, damage);
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
        if (stash.removeIf(it -> info(it) != null || (advanced != null && advanced.isRelicItem(it)))) {
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

    // ------------------------------------------------------------ admin editing
    private void adminHelp(CommandSender sender) {
        sender.sendMessage(c("§6✦ GawharaMagic Admin — أوامر تعديل اللاعبين"));
        sender.sendMessage(c("§e/gmagic admin element <player> <ELEMENT> §7تغيير عنصر اللاعب"));
        sender.sendMessage(c("§e/gmagic admin level <player> <1-100> §7تحديد المستوى"));
        sender.sendMessage(c("§e/gmagic admin points <player> <amount> §7تحديد نقاط التقدم"));
        sender.sendMessage(c("§e/gmagic admin trials <player> <0-20> §7تحديد الاختبارات المكتملة"));
        sender.sendMessage(c("§e/gmagic admin mana <player> <amount> §7تحديد مانا AuraSkills"));
        sender.sendMessage(c("§e/gmagic admin reputation <player> <amount> §7تحديد السمعة"));
        sender.sendMessage(c("§e/gmagic admin forms <player> <all|spider|creeper|...|clear> §7إدارة أشكال التحول"));
        sender.sendMessage(c("§e/gmagic admin reset <player> §7تصفير بيانات القوة"));
        sender.sendMessage(c("§e/gmagic admin info <player> §7عرض بيانات اللاعب"));
        sender.sendMessage(c("§8كل الأوامر دي تحتاج gawhara.magic.admin (متاحة للـOP افتراضيًا)."));
    }

    private Element parseElement(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        for (Element element : Element.values()) {
            if (element.name().equalsIgnoreCase(normalized) || element.ar.equalsIgnoreCase(normalized)) return element;
        }
        return switch (normalized.toLowerCase(java.util.Locale.ROOT)) {
            case "wind", "air", "رياح", "الهواء" -> Element.WIND;
            case "fire", "نار" -> Element.FIRE;
            case "water", "ماء", "ماية" -> Element.WATER;
            case "earth", "أرض", "ارض" -> Element.EARTH;
            case "lightning", "thunder", "برق" -> Element.LIGHTNING;
            case "shadow", "dark", "ظلام" -> Element.SHADOW;
            case "ice", "جليد", "تلج" -> Element.ICE;
            case "metal", "معدن" -> Element.METAL;
            case "nature", "طبيعة" -> Element.NATURE;
            case "light", "نور" -> Element.LIGHT;
            case "sound", "صوت" -> Element.SOUND;
            case "crystal", "كريستال" -> Element.CRYSTAL;
            case "shapeshift", "transform", "التحول", "تحول" -> Element.SHAPESHIFT;
            default -> null;
        };
    }

    private Player adminTarget(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) sender.sendMessage(c("§cاللاعب لازم يكون أونلاين حاليًا لتنفيذ التعديل: " + name));
        return target;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("admin")) return false;
        if (!sender.hasPermission("gawhara.magic.admin")) {
            sender.sendMessage(c("§cمعندكش صلاحية إدارة GawharaMagic."));
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("help")) { adminHelp(sender); return true; }
        if (args[1].equalsIgnoreCase("info")) {
            if (args.length < 3) { adminHelp(sender); return true; }
            Player target = adminTarget(sender, args[2]);
            if (target == null) return true;
            Element element = getEl(target);
            sender.sendMessage(c("§6✦ " + target.getName() + " §7| العنصر: §f" + (element == null ? "غير محدد" : element.ar)
                    + " §7| المستوى: §e" + levelOf(target) + " §7| النقاط: §f" + pts(target)
                    + " §7| الاختبارات: §f" + completedTrials(target) + "/" + TOTAL_TRIALS));
            if (advanced != null) sender.sendMessage(c("§7المانا: §b" + String.format(java.util.Locale.US, "%.1f", advanced.mana(target))
                    + " §7| السمعة: §d" + advanced.reputationValue(target)));
            return true;
        }
        if (args.length < 3) { adminHelp(sender); return true; }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        Player target = adminTarget(sender, args[2]);
        if (target == null) return true;
        try {
            switch (action) {
                case "element" -> {
                    if (args.length < 4) { adminHelp(sender); return true; }
                    Element element = parseElement(args[3]);
                    if (element == null) { sender.sendMessage(c("§cعنصر غير معروف. استخدم اسم العنصر بالإنجليزي أو العربي.")); return true; }
                    if (shapeshift != null) shapeshift.stopActiveForm(target);
                    data.set(playerPath(target) + ".element", element.name());
                    dirty = true; save();
                    if (power != null) power.resetSelection(target);
                    sender.sendMessage(c("§aتم تغيير عنصر " + target.getName() + " إلى " + element.ar));
                    target.sendMessage(c("§d✦ تم تغيير قوتك إلى " + element.ar + " بواسطة الإدارة."));
                }
                case "level" -> {
                    if (args.length < 4) { adminHelp(sender); return true; }
                    int level = Math.max(1, Math.min(MAX_LEVEL, Integer.parseInt(args[3])));
                    data.set(playerPath(target) + ".points", pointsForLevel(level));
                    data.set(playerPath(target) + ".trialsCompleted", Math.min(TOTAL_TRIALS, level / 5));
                    data.set(playerPath(target) + ".trialProgress", 0);
                    dirty = true; save();
                    sender.sendMessage(c("§aتم ضبط مستوى " + target.getName() + " إلى " + level));
                    target.sendMessage(c("§6✦ الإدارة ضبطت مستواك إلى " + level));
                }
                case "points" -> {
                    if (args.length < 4) { adminHelp(sender); return true; }
                    int points = Math.max(0, Integer.parseInt(args[3]));
                    data.set(playerPath(target) + ".points", points);
                    dirty = true; save();
                    sender.sendMessage(c("§aتم ضبط نقاط " + target.getName() + " إلى " + points + " (المستوى الحالي " + levelOf(target) + ")"));
                    target.sendMessage(c("§6✦ الإدارة عدّلت نقاط تقدمك إلى " + points));
                }
                case "trials" -> {
                    if (args.length < 4) { adminHelp(sender); return true; }
                    int trials = Math.max(0, Math.min(TOTAL_TRIALS, Integer.parseInt(args[3])));
                    data.set(playerPath(target) + ".trialsCompleted", trials);
                    data.set(playerPath(target) + ".trialProgress", 0);
                    dirty = true; save();
                    sender.sendMessage(c("§aتم ضبط الاختبارات المكتملة لـ " + target.getName() + " إلى " + trials + "/" + TOTAL_TRIALS));
                    target.sendMessage(c("§6✦ الإدارة عدّلت اختباراتك المكتملة إلى " + trials + "/" + TOTAL_TRIALS));
                }
                case "mana" -> {
                    if (args.length < 4 || advanced == null) { adminHelp(sender); return true; }
                    double amount = Math.max(0.0, Double.parseDouble(args[3]));
                    if (!advanced.setMana(target, amount)) { sender.sendMessage(c("§cتعذر تعديل المانا. تأكد إن AuraSkills شغال وبيانات اللاعب محمّلة.")); return true; }
                    sender.sendMessage(c("§aتم ضبط مانا " + target.getName() + " إلى " + String.format(java.util.Locale.US, "%.1f", advanced.mana(target))));
                    target.sendMessage(c("§b✦ الإدارة عدّلت المانا الخاصة بك."));
                }
                case "reputation", "rep" -> {
                    if (args.length < 4 || advanced == null) { adminHelp(sender); return true; }
                    int amount = Math.max(0, Integer.parseInt(args[3]));
                    advanced.setReputation(target, amount);
                    sender.sendMessage(c("§aتم ضبط سمعة " + target.getName() + " إلى " + amount));
                    target.sendMessage(c("§d✦ الإدارة عدّلت سمعتك إلى " + amount));
                }
                case "forms" -> {
                    if (args.length < 4 || shapeshift == null) { adminHelp(sender); return true; }
                    String form = args[3];
                    if (form.equalsIgnoreCase("all")) {
                        shapeshift.unlockAllForms(target);
                        sender.sendMessage(c("§aتم فتح أشكال التحول العشرة لـ " + target.getName()));
                        target.sendMessage(c("§d✦ الإدارة فتحت لك أشكال التحول العشرة."));
                    } else if (form.equalsIgnoreCase("clear")) {
                        shapeshift.clearForms(target);
                        sender.sendMessage(c("§aتم إغلاق كل أشكال التحول لـ " + target.getName()));
                        target.sendMessage(c("§cالإدارة أعادت قفل أشكال التحول الخاصة بك."));
                    } else {
                        String unlocked = shapeshift.unlockForm(target, form);
                        if (unlocked == null) sender.sendMessage(c("§cاسم شكل غير معروف. استخدم all أو clear أو اسم الكائن بالإنجليزي."));
                        else {
                            sender.sendMessage(c("§aتم فتح شكل " + unlocked + " لـ " + target.getName()));
                            target.sendMessage(c("§d✦ الإدارة فتحت لك شكل " + unlocked));
                        }
                    }
                }
                case "reset" -> {
                    UUID uuid = target.getUniqueId();
                    data.set("magicGeneration." + uuid, magicGeneration(target) + 1);
                    int removed = removeMagicItems(target);
                    int relicsRemoved = advanced != null ? advanced.resetPlayer(target) : 0;
                    if (shapeshift != null) shapeshift.clearForms(target);
                    data.set(playerPath(target), null);
                    dirty = true; save();
                    sender.sendMessage(c("§aاتعمل ريسيت كامل لـ " + target.getName() + " (أسلحة: " + removed + "، آثار: " + relicsRemoved + ")"));
                    target.sendMessage(c("§cالإدارة صفّرت نظام قوتك السحرية."));
                    openGui(target);
                }
                default -> adminHelp(sender);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(c("§cقيمة رقمية غير صحيحة."));
        }
        return true;
    }

    // ------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("grestore")) {
            if (!(s instanceof Player p)) { s.sendMessage("للاعبين بس."); return true; }
            restore(p);
            return true;
        }
        if (handleAdminCommand(s, a)) return true;
        if (advanced != null && advanced.handleCommand(s, a)) return true;
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
                int relicsRemoved = advanced != null ? advanced.resetPlayer(t) : 0;
                data.set(playerPath(t), null);
                save();
                s.sendMessage(c("§aاتعمل ريسيت لـ " + t.getName() + " وتم إبطال أسلحته السحرية القديمة."));
                t.sendMessage(c("§cتم تصفير قوتك السحرية! اتشالت أسلحتك القديمة وتأثيراتها، واختار قوة جديدة للبدء من الأول."));
                t.sendMessage(c("§7الأسلحة السحرية التي أُزيلت: §e" + removed + " §7| الآثار السحرية التي أُزيلت: §e" + relicsRemoved));
                openGui(t);
            } else {
                Element el = getEl(t);
                if (el == null || a.length < 3) { s.sendMessage(c("§cاللاعب لازم يكون مختار قوة، والمطلوب /gmagic level <لاعب> <1-100>")); return true; }
                int lv;
                try { lv = Math.max(1, Math.min(MAX_LEVEL, Integer.parseInt(a[2]))); }
                catch (NumberFormatException ex) { s.sendMessage(c("§cرقم غلط. استخدم رقمًا من 1 إلى 100.")); return true; }
                data.set(playerPath(t) + ".points", pointsForLevel(lv));
                data.set(playerPath(t) + ".trialsCompleted", Math.min(TOTAL_TRIALS, lv / 5));
                data.set(playerPath(t) + ".trialProgress", 0);
                save();
                s.sendMessage(c("§aتم ضبط مستوى " + t.getName() + " إلى " + lv));
                if (trialReady(t)) showTrialHint(t);
            }
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("للاعبين بس."); return true; }
        if (a.length >= 1 && a[0].equalsIgnoreCase("choose") && p.isOp()) {
            openGui(p);
            p.sendMessage(c("§dاختار أي عنصر للتجربة؛ تقدمك المحفوظ مش هيتصفر."));
            return true;
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("element") && p.isOp()) {
            Element requested = parseElement(a[1]);
            if (requested == null) p.sendMessage(c("§cعنصر غير معروف. اكتب /gmagic powers لعرض العناصر."));
            else choose(p, requested);
            return true;
        }
        Element el = getEl(p);
        if (a.length >= 1 && List.of("m", "mv", "move", "power", "skill").contains(a[0].toLowerCase(java.util.Locale.ROOT))) {
            if (el == null) {
                p.sendMessage(c("§cلازم تختار قوة الأول."));
                openGui(p);
            } else {
                // أمر مباشر للحركة الأساسية؛ لا يحتاج إمساك أي سلاح.
                unarmedAbility(p, el);
            }
            return true;
        }
        if (el == null) { openGui(p); return true; }
        if (shapeshift != null && shapeshift.handleCommand(p, a)) return true;
        if (power != null && power.handleCommand(p, a)) return true;
        if (a.length >= 1 && a[0].equalsIgnoreCase("powers")) {
            p.sendMessage(c("§6✦ §eمدارس القوى الثلاث عشرة في GawharaMagic"));
            for (Element power : Element.values()) {
                p.sendMessage(c(power.color + "✦ " + power.ar + " §7— " + power.ability + "§8: " + power.desc));
            }
            return true;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("weapons")) {
            p.sendMessage(c("§7الأسلحة اتلغت. استخدم وضع القوة: §eF §7للتفعيل، وسكرول لتبديل القدرة، وكليك يمين للاستخدام."));
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
        p.sendMessage(c("§b⚡ وضع القوة: §fF §7تفعيل/إيقاف | §fسكرول §7تبديل | §fكليك يمين §7استخدام | §fكليك شمال §7ضربة بقوتك"));
        p.sendMessage(c("§7/gwm pw ← تفعيل/إيقاف | /gwm next|prev|use | /gwm ab ← قدراتك"));
        p.sendMessage(c("§7/gmagic powers ← عرض القوى الثلاث عشرة"));
        if (el == Element.SHAPESHIFT) p.sendMessage(c("§d/gmagic forms ← سجل الأشكال التي تعلمتها"));
        p.sendMessage(c("§7/gmagic task ← تلميح الاختبار السري"));
        p.sendMessage(c("§b/gwm m §7← حركة العنصر بدون سلاح | أو Shift + كليك يمين في الهواء/بلوك عادي"));
        p.sendMessage(c("§b/gwm s §7← الدوران (Shift + كليك شمال) | §b/gwm g §7← الدرع (Shift + F)"));
        p.sendMessage(c("§b/gwm u §7← القوة النهائية | §b/gwm a §7← الاستيقاظ عند لفل 100"));
        p.sendMessage(c("§d/gmagic fusion <player> §7← دعوة اندماج | fusion accept للموافقة"));
        p.sendMessage(c("§d/gmagic forbidden | relics | relic claim <id> | spirit | bounty | reputation | transform"));
        p.sendMessage(c("§d/gwm f §7القوة المحرمة | §d/gwm tr §7التحول | §d/gwm mn §7المانا"));
        p.sendMessage(c("§d/gwm fu <player> §7الاندماج | §d/gwm rep §7السمعة | §d/gwm b §7العقود"));
        p.sendMessage(c("§eاختصارات: m=move, s=spin, g=guard, u=ultimate, a=awaken."));
        p.sendMessage(c("§7حركتك: " + el.color + unarmedMoveName(el) + " §8| §7درجة القوة " + unarmedTier(p) + "/6"));
        p.sendMessage(c("§7المانا من AuraSkills هي وقود الحركات؛ استخدم /gmagic relics و /gmagic bounty للمزيد."));
        return true;
    }
}
