package gawhara.magic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Element-specific ability book.
 *
 * There is deliberately no shared ability-kind/template system here. Every
 * entry points to its own implementation; helpers below only provide low-level
 * operations such as target checks, particles, safe locations and damage.
 */
final class Moves {

    @FunctionalInterface
    interface Cast {
        boolean run(Moves engine, Player player, int tier);
    }

    record Mv(String name, String desc, double cost, int cd, Cast cast) {}

    static final Map<Element, Mv[]> MV = new EnumMap<>(Element.class);

    static {
        MV.put(Element.FIRE, new Mv[]{
                new Mv("لغم الجمر", "تزرع فخًا ناريًا ينفجر عند اقتراب الخصم", 5, 9, Moves::fireEmberMine),
                new Mv("درب الرماد", "تترك خلفك طريقًا مشتعلًا يضر من يطارده", 5, 11, Moves::fireCinderTrail),
                new Mv("كماشة الحمم", "أعمدة حمم تحاصر الهدف ثم تنفجر للداخل", 6, 13, Moves::fireMagmaSnare),
                new Mv("قوس اللهب", "قوس ناري واسع يمسح المنطقة أمامك", 5, 8, Moves::fireCrescent),
                new Mv("بعث العنقاء", "تستعيد جزءًا من حياتك وتطرد آثار السم والذبول", 7, 32, Moves::firePhoenixPulse),
                new Mv("مطر الشرر", "شرارات متساقطة تطارد مساحة التصويب لعدة لحظات", 8, 21, Moves::fireCinderRain),
                new Mv("زفير التنين", "زفير ناري متحرك يمسح مخروطًا أمامك", 10, 42, Moves::fireDragonBreath)
        });
        MV.put(Element.WATER, new Mv[]{
                new Mv("السحب العكسي", "تيار يسحب هدفًا محددًا نحوك على دفعات", 5, 9, Moves::waterUndertow),
                new Mv("مرآة الماء", "تذوب في رذاذ مائي وتترك انعكاسات مضللة", 6, 18, Moves::waterMirror),
                new Mv("مطر الينبوع", "أمطار علاجية متتابعة في المكان المحدد", 6, 15, Moves::waterSpringfall),
                new Mv("نفاث الضغط", "تيار مركز يضغط الهدف ويدفعه بعيدًا", 5, 8, Moves::waterPressureJet),
                new Mv("دوامة المد", "تجبر الأعداء على الدوران حول مركز مائي", 7, 17, Moves::waterWhirlpool),
                new Mv("ستار الضباب", "ضباب يخفي الحلفاء ويشوّش رؤية الخصوم", 6, 22, Moves::waterMistVeil),
                new Mv("زحف الليفياثان", "جدار موجة عريض يعبر الساحة ويجرف الخصوم", 10, 45, Moves::waterLeviathanWave)
        });
        MV.put(Element.EARTH, new Mv[]{
                new Mv("الصخرة الدوارة", "تدفع صخرة متدحرجة تكبر خطورتها مع تقدمها", 5, 10, Moves::earthBoulderRoll),
                new Mv("الصدع الزاحف", "شق أرضي يتقدم أمامك ويقذف الصخور من تحته", 6, 13, Moves::earthCreepingFault),
                new Mv("مرساة الجبل", "تثقل هدفًا وتمنعه من القفز أو الاندفاع", 5, 12, Moves::earthMountainAnchor),
                new Mv("نبض الصفائح", "صفائح أرضية ترتفع بالتتابع تحت الأعداء", 6, 14, Moves::earthPlatePulse),
                new Mv("خطوة الهضبة", "ترفعك صخرة عالية ثم تهبط بضربة مركزة", 6, 18, Moves::earthPlateauStep),
                new Mv("انهيار الوادي", "تساقط صخري متفرق يغلق مساحة التصويب", 8, 23, Moves::earthAvalanche),
                new Mv("زحزحة القارات", "تجذب الخصوم إلى مركز ثم تقذفهم على أطرافه", 10, 48, Moves::earthContinentalShift)
        });
        MV.put(Element.WIND, new Mv[]{
                new Mv("شفرة الضغط", "شفرتان هوائيتان متقاطعتان تقطعان أمامك", 4, 7, Moves::windPressureShear),
                new Mv("انزلاق الغيم", "تنزلق بمحاذاة الأرض وتترك تيارًا يبطئ المطاردين", 5, 11, Moves::windCloudSkate),
                new Mv("هبوط الريشة", "تحكم في سقوطك وسقوط الحلفاء القريبين", 4, 14, Moves::windFeatherFall),
                new Mv("رفع الإعصار", "يرفع هدفًا ويديره في الهواء قبل إسقاطه", 6, 15, Moves::windCycloneLift),
                new Mv("المجرى الهوائي", "تصنع ممرًا يمنح الحلفاء سرعة عند عبوره", 5, 20, Moves::windSlipstream),
                new Mv("جيب الفراغ", "تسحب الهواء من حول خصومك ثم تطلقه دفعة واحدة", 7, 21, Moves::windAirPocket),
                new Mv("كاسر السماء", "ترفع الأعداء أمامك ثم تسحقهم بهبوط هوائي", 10, 44, Moves::windSkybreaker)
        });
        MV.put(Element.LIGHTNING, new Mv[]{
                new Mv("المكثف", "تجمع شحنة أثناء الحركة ثم تفرغها على القريبين", 5, 12, Moves::lightningCapacitor),
                new Mv("سحب الشحنة", "تسرق حركة الهدف وتحولها إلى سرعة لك", 5, 10, Moves::lightningStaticSiphon),
                new Mv("قضبان التأريض", "تضع نقاط جذب تستدعي ضربات برق متتابعة", 7, 19, Moves::lightningGroundingRods),
                new Mv("الانتقال الكهرومغناطيسي", "تنجذب إلى هدف مشحون وتظهر بجواره", 6, 14, Moves::lightningRailJump),
                new Mv("فرقعة الرعد", "تطلق فرقعة مؤجلة تربك من يسمعها", 6, 16, Moves::lightningThunderclap),
                new Mv("الحمل الزائد", "تدخل حالة شحن تضرب أقرب خصم على فترات", 8, 27, Moves::lightningOvercharge),
                new Mv("قفص العاصفة", "أعمدة برق تحاصر مساحة وتضرب من بداخلها", 10, 48, Moves::lightningStormCage)
        });
        MV.put(Element.SHADOW, new Mv[]{
                new Mv("النسخ الشبحية", "تظهر ظلالًا وهمية حولك ثم تندفع نحو خصومك", 5, 12, Moves::shadowAfterimages),
                new Mv("تبادل الظلال", "تبدل مكانك مع هدف تختاره", 6, 14, Moves::shadowPositionSwap),
                new Mv("نظرة الرعب", "تجبر هدفًا على التراجع تحت تأثير الخوف", 5, 12, Moves::shadowDreadGaze),
                new Mv("مصاص الليل", "تستنزف حياة هدف على دفعات وتستعيدها لنفسك", 7, 18, Moves::shadowNightLeech),
                new Mv("الطعنة الغائبة", "تختفي ثم تظهر خلف أقرب خصم وتصيبه", 6, 15, Moves::shadowVoidStab),
                new Mv("دين الظلام", "تضع لعنة تنفجر بعد مهلة وتزداد في الأماكن المعتمة", 8, 24, Moves::shadowUmbralDebt),
                new Mv("مطاردة الكسوف", "تختفي وتلاحق هدفًا بعينه بضربة ختامية", 10, 46, Moves::shadowEclipseHunt)
        });
        MV.put(Element.ICE, new Mv[]{
                new Mv("انزلاق الصقيع", "تندفع على الأرض وتترك مسارًا يجمد المطاردين", 5, 10, Moves::iceSkate),
                new Mv("وسم الصقيع", "تراكم طبقات برد على هدف حتى يتجمد", 5, 11, Moves::iceFrostbiteBrand),
                new Mv("سجن البلور الجليدي", "تغلق على هدف دائرة جليدية تضيق تدريجيًا", 6, 16, Moves::iceMirrorPrison),
                new Mv("تقاطع النوازل", "رماح جليد تهبط من جهات متقاطعة حول الهدف", 6, 15, Moves::iceIcicleCross),
                new Mv("العمى الأبيض", "عاصفة ثلجية تحجب الرؤية وتثقل الحركة", 6, 19, Moves::iceWhiteout),
                new Mv("مزلجة النهر", "تشق طريقًا جليديًا سريعًا وتجر من يعترضك", 7, 22, Moves::iceGlacierSlide),
                new Mv("الصفر المطلق", "تجمع الصقيع ثم تجمد مساحة كاملة لحظة إطلاقه", 10, 48, Moves::iceAbsoluteZero)
        });
        MV.put(Element.METAL, new Mv[]{
                new Mv("حقل الجذب", "تجذب الخصوم والأغراض المتحركة نحو قطبك", 5, 12, Moves::metalMagneticField),
                new Mv("مدار الشفرات", "شفرات حديدية تدور حولك ثم تنطلق نحو الأهداف", 6, 17, Moves::metalBladeOrbit),
                new Mv("الارتداد المعدني", "مقذوف يرتد بين عدة خصوم قبل أن يتوقف", 6, 14, Moves::metalRicochet),
                new Mv("خطاف السلاسل", "تثبت خطافًا وتسحب نفسك أو الهدف حسب نقطة الإصابة", 5, 11, Moves::metalChainGrapple),
                new Mv("النبضة القطبية", "تعكس اتجاه حركة الخصوم حولك بقوة مغناطيسية", 6, 15, Moves::metalPolarRepulsion),
                new Mv("الحدادة الحية", "تغلفك صفائح متحركة تمتص الضربات القليلة القادمة", 7, 24, Moves::metalLivingForge),
                new Mv("كبش الحصار", "اندفاعة ثقيلة مستقيمة تصدم كل من يقف في مسارك", 10, 42, Moves::metalSiegeRam)
        });
        MV.put(Element.NATURE, new Mv[]{
                new Mv("وسادة الوثب", "تنمو ورقة نابضة تقذف أول كائن يطأها إلى الأعلى", 5, 15, Moves::natureSpringPad),
                new Mv("غبار اللقاح", "سحابة لقاح تربك الخصوم وتبطئهم", 5, 13, Moves::naturePollenCloud),
                new Mv("رابطة الحياة", "تربطك بحليف وتوزع عليه الشفاء معك", 6, 17, Moves::natureLifeBond),
                new Mv("سوط الكروم", "كرمة تمتد وتلتف حول هدف ثم تشده", 6, 13, Moves::natureVineLash),
                new Mv("الزهرة المفترسة", "تطلق فم زهرة يعض الأعداء عند اقترابهم", 7, 18, Moves::naturePredatorBloom),
                new Mv("النمو البري", "كل خطوة لك تترك أثرًا يعالج الحلفاء ويؤذي الخصوم", 7, 25, Moves::natureWildGrowth),
                new Mv("نداء القطيع", "تستدعي ذئابًا حليفة لفترة قصيرة", 10, 50, Moves::natureWildHunt)
        });
        MV.put(Element.LIGHT, new Mv[]{
                new Mv("انكسار الطيف", "تقسم الضوء عند نقطة التصويب إلى مسارات متعددة", 5, 11, Moves::lightPrismSplit),
                new Mv("وسم الشمس", "تضع علامة مضيئة تنفجر إذا بقي الهدف مكشوفًا", 5, 13, Moves::lightSunBrand),
                new Mv("ملاذ الفجر", "تطهر الحلفاء القريبين من التأثيرات السلبية", 6, 18, Moves::lightDawnSanctuary),
                new Mv("خطوة الطيف", "تنتقل إلى موضع مضيء آمن ضمن مجال نظرك", 6, 15, Moves::lightSpectrumStep),
                new Mv("التركيز الشمسي", "تجمع ضوء المكان وتزيد قوتك بحسب الإضاءة", 7, 24, Moves::lightSolarFocus),
                new Mv("هالة الكشف", "تكشف الأعداء وتضعف من يحاول الاختباء", 6, 20, Moves::lightRevelationHalo),
                new Mv("حكم السماء", "عمود نور يتعقب أهدافك المحددة من أعلى", 10, 46, Moves::lightCelestialJudgment)
        });
        MV.put(Element.SOUND, new Mv[]{
                new Mv("رنين الحركة", "يربط هدفًا باهتزازات تنفجر عندما يتحرك", 5, 12, Moves::soundMotionResonance),
                new Mv("حقل الصمت", "يخمد المنطقة ويبطئ من بداخلها", 5, 15, Moves::soundSilenceField),
                new Mv("رادار الصدى", "يكشف الكائنات القريبة حتى خلف الجدران", 4, 18, Moves::soundEchoRadar),
                new Mv("خطوة الصدى", "تترك نسخة صوتية من موقعك وتعود إليها بعد لحظة", 6, 17, Moves::soundReverbStep),
                new Mv("الرفع التوافقي", "يرفع الخصوم بنبضات صوتية متصاعدة", 6, 16, Moves::soundHarmonicLift),
                new Mv("التردد المعاكس", "يقلب اتجاه اندفاع الخصم ويكسر توازنه", 7, 20, Moves::soundFeedbackLoop),
                new Mv("النغمة الأخيرة", "تصاعد نغمي بثلاث موجات أقوى من السابقة", 10, 45, Moves::soundFinalCrescendo)
        });
        MV.put(Element.CRYSTAL, new Mv[]{
                new Mv("منجم الجيود", "تزرع شظايا بلور تنفجر عند اقتراب الخصم", 5, 13, Moves::crystalGeodeMine),
                new Mv("قلب الأوجه", "تكوّن أوجهًا بلورية تمتص الضرر ثم تتشقق", 6, 20, Moves::crystalFacetHeart),
                new Mv("انكسار المنشور", "تطلق أشعة منعكسة بزوايا مختلفة", 6, 14, Moves::crystalPrismRefraction),
                new Mv("نمو البلور", "أعمدة بلورية تنمو حول الهدف وتحبسه", 6, 16, Moves::crystalGrowth),
                new Mv("كسر الرنين", "تزرع شقوقًا بلورية تتفجر بعد اكتمال الرنين", 7, 19, Moves::crystalResonantFracture),
                new Mv("خطوة التشظي", "تتشظى إلى نقطة أمامك وتترك شظايا خلفك", 6, 15, Moves::crystalShatterstep),
                new Mv("إزهار الماس", "زهرة بلورية تتفتح طبقة بعد طبقة وتطلق شظايا", 10, 48, Moves::crystalDiamondBloom)
        });
    }

    private final PowerMode pm;
    private final Magic magic;

    Moves(PowerMode pm, Magic magic) {
        this.pm = pm;
        this.magic = magic;
    }

    /** i = 0..6 means ability number 4..10. */
    boolean run(Player p, Element el, int i, int tier) {
        Mv[] list = MV.get(el);
        if (list == null || i < 0 || i >= list.length) return false;
        return list[i].cast().run(this, p, tier);
    }

    // ---------------------------------------------------------------------
    // Low-level utilities only. None of these chooses an ability archetype.

    private double power(int tier, double base) {
        return base * pm.scale(tier);
    }

    private List<LivingEntity> foes(Player p, Location center, double radius) {
        return pm.foesAt(p, center, radius);
    }

    private void hurt(Player p, LivingEntity target, double amount) {
        if (target != null && !target.isDead()) pm.hurt(p, target, amount);
    }

    private void fx(Location at, Particle particle, int count, double x, double y, double z, double speed) {
        if (at.getWorld() != null) at.getWorld().spawnParticle(particle, at, count, x, y, z, speed);
    }

    private void blockFx(Location at, Material material, int count, double x, double y, double z, double speed) {
        if (at.getWorld() != null) at.getWorld().spawnParticle(Particle.BLOCK, at, count, x, y, z, speed, material.createBlockData());
    }

    private void blockRing(Location center, double radius, Material material, int count) {
        World world = center.getWorld();
        if (world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            world.spawnParticle(Particle.BLOCK, point, 1, 0.02, 0.02, 0.02, 0.0, material.createBlockData());
        }
    }

    private void sound(Location at, Sound sound, float pitch) {
        if (at.getWorld() != null) at.getWorld().playSound(at, sound, 1.0f, pitch);
    }

    private void ring(Location center, double radius, Particle particle, int count) {
        World world = center.getWorld();
        if (world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private Location aim(Player p, double range) {
        return pm.aimPoint(p, range);
    }

    private LivingEntity target(Player p, double range) {
        return pm.firstTarget(p, range);
    }

    private void slow(LivingEntity e, int ticks, int amplifier) {
        pm.effect(e, PotionEffectType.SLOWNESS, ticks, amplifier);
    }

    private void freeze(LivingEntity e, int ticks) {
        e.setFreezeTicks(Math.min(e.getMaxFreezeTicks(), Math.max(e.getFreezeTicks(), ticks)));
    }

    private Location safeGround(Location point) {
        Location l = point.clone();
        World w = l.getWorld();
        if (w == null) return null;
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int y = Math.min(w.getMaxHeight() - 2, Math.max(w.getMinHeight() + 1, l.getBlockY()));
        for (int i = 0; i < 8; i++) {
            Block feet = w.getBlockAt(x, y, z);
            Block head = w.getBlockAt(x, y + 1, z);
            Block floor = w.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !floor.isPassable())
                return new Location(w, x + 0.5, y, z + 0.5, l.getYaw(), l.getPitch());
            y += feet.isPassable() ? -1 : 1;
            if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 2) break;
        }
        return null;
    }

    private void heal(Player player, double amount) {
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
    }

    private boolean sameSchool(Player a, Player b) {
        Element e = magic.getEl(a);
        return e != null && e == magic.getEl(b);
    }

    private List<Player> allies(Player player, Location center, double radius) {
        List<Player> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof Player other && (other.equals(player) || sameSchool(player, other))) out.add(other);
        }
        if (!out.contains(player)) out.add(player);
        return out;
    }

    private int localLight(Location at) {
        Block block = at.getBlock();
        int blockLight = block.getLightFromBlocks();
        int sky = block.getLightFromSky();
        long time = at.getWorld().getTime();
        if (time >= 12300 && time <= 23850) sky = (int) (sky * 0.3);
        return Math.max(blockLight, sky);
    }

    private void status(Player p, String text) {
        p.sendActionBar(Magic.c(text));
    }

    // ================================================================ FIRE

    private boolean fireEmberMine(Player p, int tier) {
        Location mine = safeGround(aim(p, 17));
        if (mine == null) { status(p, "§cمفيش أرض صالحة لزرع لغم الجمر"); return false; }
        World w = mine.getWorld();
        sound(mine, Sound.ITEM_FIRECHARGE_USE, 0.7f);
        new BukkitRunnable() {
            int age = 0;
            @Override public void run() {
                if (!p.isOnline() || age++ >= 60) { cancel(); return; }
                fx(mine.clone().add(0, 0.12, 0), Particle.SOUL_FIRE_FLAME, 2, 0.18, 0.02, 0.18, 0.005);
                ring(mine, 0.45 + age * 0.006, Particle.FLAME, 10);
                boolean triggered = foes(p, mine, 1.55).stream().anyMatch(e -> e.getLocation().distanceSquared(mine) <= 2.4);
                if (triggered || age >= 60) {
                    fx(mine, Particle.EXPLOSION, 1, 0, 0, 0, 0);
                    fx(mine, Particle.LAVA, 35, 1.1, 0.35, 1.1, 0.05);
                    for (LivingEntity e : foes(p, mine, 3.2)) {
                        hurt(p, e, power(tier, 5.0)); e.setFireTicks(80 + tier * 8);
                    }
                    sound(mine, Sound.ENTITY_GENERIC_EXPLODE, 0.8f);
                    cancel();
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean fireCinderTrail(Player p, int tier) {
        Set<UUID> burned = new HashSet<>();
        sound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.8f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || ticks++ >= 36) { cancel(); return; }
                Location step = p.getLocation().clone().add(0, 0.15, 0);
                fx(step, Particle.FLAME, 12, 0.45, 0.08, 0.45, 0.02);
                fx(step, Particle.SMOKE, 3, 0.25, 0.05, 0.25, 0.01);
                for (LivingEntity e : foes(p, step, 1.35)) {
                    if (burned.add(e.getUniqueId())) {
                        e.setFireTicks(80 + tier * 8);
                        hurt(p, e, power(tier, 4.0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean fireMagmaSnare(Player p, int tier) {
        LivingEntity victim = target(p, 15);
        Location center = victim == null ? aim(p, 13) : victim.getLocation();
        World w = center.getWorld();
        sound(center, Sound.BLOCK_LAVA_POP, 0.65f);
        new BukkitRunnable() {
            int stage = 0;
            @Override public void run() {
                if (!p.isOnline() || stage >= 5) { cancel(); return; }
                stage++;
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6;
                    Location pillar = center.clone().add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0);
                    double rise = stage * 0.65;
                    fx(pillar.add(0, rise, 0), Particle.LAVA, 5, 0.12, 0.35, 0.12, 0.02);
                    fx(pillar, Particle.FLAME, 4, 0.12, 0.35, 0.12, 0.01);
                }
                for (LivingEntity e : foes(p, center, 3.1)) {
                    slow(e, 14, 5);
                    if (stage == 5) { hurt(p, e, power(tier, 7.0)); e.setFireTicks(80); }
                }
                if (stage == 5) sound(center, Sound.BLOCK_LAVA_EXTINGUISH, 0.7f);
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean fireCrescent(Player p, int tier) {
        Location eye = p.getEyeLocation();
        Vector forward = pm.dir(p);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Set<UUID> hit = new HashSet<>();
        World w = p.getWorld();
        for (int step = 0; step <= 18; step++) {
            double angle = Math.toRadians(-78 + step * 8.7);
            Vector ray = forward.clone().multiply(Math.cos(angle)).add(right.clone().multiply(Math.sin(angle)));
            Location point = eye.clone().add(ray.multiply(5.5));
            fx(point, Particle.FLAME, 5, 0.18, 0.14, 0.18, 0.02);
            fx(point, Particle.SOUL_FIRE_FLAME, 2, 0.12, 0.1, 0.12, 0.01);
            for (LivingEntity e : foes(p, point, 1.25)) {
                if (hit.add(e.getUniqueId())) { hurt(p, e, power(tier, 4.5)); e.setFireTicks(70); }
            }
        }
        sound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f);
        return true;
    }

    private boolean firePhoenixPulse(Player p, int tier) {
        double restored = Math.min(7.0 + tier, p.getMaxHealth() - p.getHealth());
        heal(p, restored);
        p.removePotionEffect(PotionEffectType.POISON);
        p.removePotionEffect(PotionEffectType.WITHER);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.FIRE_RESISTANCE, 140, 0, true, true));
        Location c = p.getLocation().add(0, 0.4, 0);
        for (int i = 0; i < 3; i++) {
            ring(c.clone().add(0, i * 0.45, 0), 1.1 + i * 0.75, Particle.FLAME, 36);
            fx(c.clone().add(0, i * 0.5, 0), Particle.TOTEM_OF_UNDYING, 12, 0.35, 0.2, 0.35, 0.04);
        }
        sound(c, Sound.ITEM_TOTEM_USE, 0.9f);
        return true;
    }

    private boolean fireCinderRain(Player p, int tier) {
        Location center = aim(p, 22);
        World w = center.getWorld();
        sound(center, Sound.ENTITY_BLAZE_SHOOT, 0.65f);
        new BukkitRunnable() {
            int pulse = 0;
            @Override public void run() {
                if (!p.isOnline() || pulse++ >= 7) { cancel(); return; }
                for (int i = 0; i < 7; i++) {
                    double x = ThreadLocalRandom.current().nextDouble(-3.2, 3.2);
                    double z = ThreadLocalRandom.current().nextDouble(-3.2, 3.2);
                    Location ember = center.clone().add(x, 8 + ThreadLocalRandom.current().nextDouble(2), z);
                    Location landing = center.clone().add(x, 0.2, z);
                    fx(ember, Particle.FLAME, 4, 0.1, 0.12, 0.1, 0.02);
                    fx(landing, Particle.LAVA, 3, 0.25, 0.1, 0.25, 0.01);
                    for (LivingEntity e : foes(p, landing, 1.15)) {
                        hurt(p, e, power(tier, 1.5)); e.setFireTicks(45);
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean fireDragonBreath(Player p, int tier) {
        Vector direction = pm.dir(p);
        Set<UUID> hit = new HashSet<>();
        sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f);
        new BukkitRunnable() {
            int pulse = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || pulse++ >= 12) { cancel(); return; }
                Vector side = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
                for (int d = 2; d <= 10; d += 2) {
                    double spread = d * 0.22;
                    for (int s = -2; s <= 2; s++) {
                        Location point = p.getEyeLocation().add(direction.clone().multiply(d)).add(side.clone().multiply(s * spread));
                        fx(point, Particle.SOUL_FIRE_FLAME, 3, 0.2, 0.18, 0.2, 0.03);
                        fx(point, Particle.FLAME, 4, 0.3, 0.2, 0.3, 0.04);
                        for (LivingEntity e : foes(p, point, 1.0)) {
                            if (hit.add(e.getUniqueId())) { hurt(p, e, power(tier, 8.0)); e.setFireTicks(120); }
                        }
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    // ================================================================ WATER

    private boolean waterUndertow(Player p, int tier) {
        LivingEntity victim = target(p, 18);
        if (victim == null) { status(p, "§cلازم تحدد هدف للسحب العكسي"); return false; }
        sound(victim.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 10 || !victim.getWorld().equals(p.getWorld())) { cancel(); return; }
                Vector pull = p.getLocation().toVector().subtract(victim.getLocation().toVector());
                if (pull.lengthSquared() < 1.5) { cancel(); return; }
                pull.normalize().multiply(0.62).setY(0.12);
                victim.setVelocity(pull);
                fx(victim.getLocation(), Particle.BUBBLE, 9, 0.3, 0.45, 0.3, 0.04);
                if (n % 3 == 0) hurt(p, victim, power(tier, 1.2));
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean waterMirror(Player p, int tier) {
        Location origin = p.getLocation();
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 100, 1, true, false));
        sound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 10) { cancel(); return; }
                Location c = p.getLocation();
                for (int side : new int[]{-1, 1}) {
                    Vector right = new Vector(-c.getDirection().getZ(), 0, c.getDirection().getX()).normalize().multiply(side * 1.1);
                    Location reflection = c.clone().add(right).add(0, 1, 0);
                    fx(reflection, Particle.SPLASH, 8, 0.25, 0.45, 0.25, 0.03);
                    fx(reflection, Particle.BUBBLE, 5, 0.2, 0.3, 0.2, 0.03);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean waterSpringfall(Player p, int tier) {
        Location center = safeGround(aim(p, 18));
        if (center == null) center = p.getLocation();
        Location fixed = center.clone();
        sound(fixed, Sound.WEATHER_RAIN, 0.9f);
        new BukkitRunnable() {
            int pulse = 0;
            @Override public void run() {
                if (!p.isOnline() || pulse++ >= 8) { cancel(); return; }
                fx(fixed.clone().add(0, 4, 0), Particle.FALLING_WATER, 18, 2.1, 0.4, 2.1, 0.04);
                ring(fixed, 2.4, Particle.SPLASH, 24);
                for (Player ally : allies(p, fixed, 4.2)) {
                    heal(ally, 0.8 + tier * 0.12);
                    ally.setFireTicks(0);
                    ally.removePotionEffect(PotionEffectType.POISON);
                    ally.removePotionEffect(PotionEffectType.WITHER);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean waterPressureJet(Player p, int tier) {
        Vector dir = pm.dir(p);
        Set<UUID> struck = new HashSet<>();
        sound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.5f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 12) { cancel(); return; }
                for (double d = 1; d <= 13; d += 1.0) {
                    Location point = p.getEyeLocation().add(dir.clone().multiply(d));
                    fx(point, Particle.SPLASH, 5, 0.13, 0.13, 0.13, 0.02);
                    fx(point, Particle.BUBBLE, 3, 0.09, 0.09, 0.09, 0.01);
                    if (!point.getBlock().isPassable()) break;
                    for (LivingEntity e : foes(p, point, 0.85)) {
                        Vector shove = dir.clone().multiply(0.95).setY(0.16);
                        e.setVelocity(shove);
                        if (struck.add(e.getUniqueId())) hurt(p, e, power(tier, 5.0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean waterWhirlpool(Player p, int tier) {
        Location center = aim(p, 18);
        sound(center, Sound.ENTITY_DOLPHIN_SPLASH, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 24) { cancel(); return; }
                double spin = n * 0.48;
                ring(center.clone().add(0, 0.2 + Math.sin(spin) * 0.25, 0), 2.8 - n * 0.025, Particle.SPLASH, 28);
                fx(center.clone().add(0, 1.0, 0), Particle.BUBBLE_COLUMN_UP, 6, 0.45, 0.55, 0.45, 0.01);
                for (LivingEntity e : foes(p, center, 4.5)) {
                    Vector offset = center.toVector().subtract(e.getLocation().toVector());
                    Vector tangent = new Vector(-offset.getZ(), 0, offset.getX()).normalize().multiply(0.38);
                    Vector inward = offset.normalize().multiply(0.18);
                    e.setVelocity(tangent.add(inward).setY(0.08));
                    if (n % 4 == 0) hurt(p, e, power(tier, 1.25));
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean waterMistVeil(Player p, int tier) {
        Location center = p.getLocation();
        sound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 28) { cancel(); return; }
                Location c = p.getLocation();
                fx(c.clone().add(0, 1, 0), Particle.CLOUD, 22, 3.3, 1.0, 3.3, 0.015);
                fx(c.clone().add(0, 1.2, 0), Particle.SPLASH, 10, 2.8, 0.8, 2.8, 0.02);
                for (LivingEntity e : foes(p, c, 3.3)) {
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false));
                    slow(e, 30, 0);
                }
                for (Player ally : allies(p, c, 3.0))
                    ally.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, true, false));
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean waterLeviathanWave(Player p, int tier) {
        Vector forward = pm.dir(p);
        Vector side = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Location start = p.getLocation().clone();
        sound(start, Sound.ENTITY_GUARDIAN_ATTACK, 0.55f);
        Set<UUID> swept = new HashSet<>();
        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                if (!p.isOnline() || step++ >= 18) { cancel(); return; }
                Location front = start.clone().add(forward.clone().multiply(step * 1.15)).add(0, 1, 0);
                for (int s = -5; s <= 5; s++) {
                    Location crest = front.clone().add(side.clone().multiply(s * 0.72));
                    fx(crest, Particle.SPLASH, 8, 0.2, 0.65, 0.2, 0.05);
                    fx(crest.clone().add(0, 1, 0), Particle.BUBBLE, 4, 0.15, 0.3, 0.15, 0.02);
                    for (LivingEntity e : foes(p, crest, 0.95)) {
                        if (swept.add(e.getUniqueId())) {
                            e.setVelocity(forward.clone().multiply(1.2).setY(0.48));
                            hurt(p, e, power(tier, 7.0));
                        }
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    // ================================================================ EARTH

    private boolean earthBoulderRoll(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0.03).normalize();
        Location spawn = p.getLocation().add(dir.clone().multiply(1.5)).add(0, 1.2, 0);
        FallingBlock rock = p.getWorld().spawnFallingBlock(spawn, Material.COBBLESTONE.createBlockData());
        rock.setDropItem(false);
        rock.setHurtEntities(false);
        rock.setGravity(false);
        Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            int n = 0;
            double radius = 0.55;
            @Override public void run() {
                if (!p.isOnline() || !rock.isValid() || n++ >= 24) { rock.remove(); cancel(); return; }
                radius = Math.min(1.35, radius + 0.035);
                Location at = rock.getLocation();
                blockFx(at, Material.COBBLESTONE, 8, 0.25, 0.25, 0.25, 0.02);
                fx(at, Particle.POOF, 3, radius, 0.2, radius, 0.02);
                rock.setVelocity(dir.clone().multiply(0.85 + n * 0.012));
                for (LivingEntity e : foes(p, at, radius + 0.7)) {
                    if (struck.add(e.getUniqueId())) {
                        hurt(p, e, power(tier, 7.0));
                        e.setVelocity(dir.clone().multiply(0.95).setY(0.32));
                    }
                }
                if (!at.clone().add(dir).getBlock().isPassable()) { rock.remove(); cancel(); }
            }
        }.runTaskTimer(magic, 0L, 1L);
        sound(spawn, Sound.BLOCK_STONE_BREAK, 0.6f);
        return true;
    }

    private boolean earthCreepingFault(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        Location origin = p.getLocation().clone();
        Set<UUID> hit = new HashSet<>();
        sound(origin, Sound.BLOCK_DEEPSLATE_BREAK, 0.7f);
        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                if (!p.isOnline() || step++ >= 15) { cancel(); return; }
                Location crack = origin.clone().add(dir.clone().multiply(step * 0.9));
                for (int side = -1; side <= 1; side++) {
                    Location shard = crack.clone().add(-dir.getZ() * side * 0.45, 0.1, dir.getX() * side * 0.45);
                    blockFx(shard, Material.DIRT, 5, 0.12, 0.1, 0.12, 0.02);
                    fx(shard.clone().add(0, 0.5, 0), Particle.POOF, 2, 0.1, 0.2, 0.1, 0.01);
                }
                for (LivingEntity e : foes(p, crack, 1.25)) {
                    if (hit.add(e.getUniqueId())) {
                        hurt(p, e, power(tier, 6.0));
                        e.setVelocity(new Vector(0, 0.62, 0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean earthMountainAnchor(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف لمرساة الجبل"); return false; }
        sound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.65f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 16) { cancel(); return; }
                blockFx(victim.getLocation().add(0, 0.1, 0), Material.STONE, 10, 0.4, 0.08, 0.4, 0.02);
                victim.setVelocity(new Vector(victim.getVelocity().getX() * 0.15, Math.min(0, victim.getVelocity().getY()), victim.getVelocity().getZ() * 0.15));
                slow(victim, 8, 7);
                if (n % 4 == 0) hurt(p, victim, power(tier, 1.5));
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean earthPlatePulse(Player p, int tier) {
        Location center = safeGround(aim(p, 17));
        if (center == null) center = p.getLocation();
        Location fixed = center.clone();
        sound(fixed, Sound.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON, 0.5f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 5) { cancel(); return; }
                double radius = 1.0 + n * 1.25;
                blockRing(fixed, radius, Material.STONE, 30);
                for (LivingEntity e : foes(p, fixed, radius + 0.85)) {
                    double distance = e.getLocation().distance(fixed);
                    if (Math.abs(distance - radius) < 1.0) {
                        hurt(p, e, power(tier, 2.2));
                        e.setVelocity(new Vector(0, 0.48, 0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean earthPlateauStep(Player p, int tier) {
        Location base = safeGround(p.getLocation());
        if (base == null) return false;
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, false));
        p.setVelocity(new Vector(0, 1.35 + tier * 0.03, 0));
        sound(base, Sound.BLOCK_STONE_BREAK, 0.55f);
        new BukkitRunnable() {
            int n = 0;
            boolean landed = false;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 35) { cancel(); return; }
                Location under = p.getLocation().subtract(0, 0.8, 0);
                blockFx(under, Material.STONE, 12, 0.55, 0.18, 0.55, 0.03);
                if (!landed && p.isOnGround() && n > 5) {
                    landed = true;
                    blockRing(p.getLocation(), 3.4, Material.STONE, 38);
                    for (LivingEntity e : foes(p, p.getLocation(), 4.0)) {
                        hurt(p, e, power(tier, 6.0));
                        e.setVelocity(new Vector(0, 0.8, 0));
                    }
                    sound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.65f);
                    cancel();
                }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean earthAvalanche(Player p, int tier) {
        Location center = aim(p, 20);
        World world = center.getWorld();
        sound(center, Sound.BLOCK_GRAVEL_BREAK, 0.65f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                double x = ThreadLocalRandom.current().nextDouble(-3.2, 3.2);
                double z = ThreadLocalRandom.current().nextDouble(-3.2, 3.2);
                Location drop = center.clone().add(x, 9, z);
                FallingBlock debris = world.spawnFallingBlock(drop, Material.COBBLESTONE.createBlockData());
                debris.setDropItem(false);
                debris.setHurtEntities(false);
                debris.setVelocity(new Vector(0, -0.8, 0));
                new BukkitRunnable() {
                    int wait = 0;
                    @Override public void run() {
                        if (!debris.isValid() || wait++ >= 18) {
                            Location impact = debris.getLocation(); debris.remove();
                            blockFx(impact, Material.COBBLESTONE, 18, 0.6, 0.4, 0.6, 0.04);
                            for (LivingEntity e : foes(p, impact, 2.1)) hurt(p, e, power(tier, 3.0));
                            cancel();
                        }
                    }
                }.runTaskTimer(magic, 1L, 1L);
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean earthContinentalShift(Player p, int tier) {
        Location center = aim(p, 16);
        sound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 0.45f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                for (LivingEntity e : foes(p, center, 7.0)) {
                    Vector toCenter = center.toVector().subtract(e.getLocation().toVector());
                    if (n <= 5) e.setVelocity(toCenter.normalize().multiply(0.72).setY(0.12));
                    else {
                        Vector away = e.getLocation().toVector().subtract(center.toVector()).normalize();
                        e.setVelocity(away.multiply(1.25).setY(0.5));
                        hurt(p, e, power(tier, 2.4));
                    }
                }
                blockRing(center, 1.2 + n * 0.7, Material.STONE, 36);
                fx(center, Particle.POOF, 12, 2.3, 0.4, 2.3, 0.02);
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    // ================================================================ WIND

    private boolean windPressureShear(Player p, int tier) {
        Vector f = pm.dir(p);
        Vector right = new Vector(-f.getZ(), 0, f.getX()).normalize();
        Set<UUID> hit = new HashSet<>();
        for (int step = 1; step <= 14; step++) {
            for (int lane : new int[]{-1, 1}) {
                Location point = p.getEyeLocation().add(f.clone().multiply(step)).add(right.clone().multiply(lane * step * 0.28));
                fx(point, Particle.SWEEP_ATTACK, 1, 0.12, 0.12, 0.12, 0);
                fx(point, Particle.CLOUD, 3, 0.1, 0.1, 0.1, 0.01);
                for (LivingEntity e : foes(p, point, 0.8)) {
                    if (hit.add(e.getUniqueId())) { hurt(p, e, power(tier, 5.0)); e.setVelocity(f.clone().multiply(0.8).setY(0.18)); }
                }
            }
        }
        sound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.7f);
        return true;
    }

    private boolean windCloudSkate(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        final Vector travelDirection = dir.clone();
        Vector launch = travelDirection.clone().multiply(1.45 + tier * 0.025).setY(0.12);
        p.setVelocity(launch);
        Set<UUID> struck = new HashSet<>();
        sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.45f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 14) { cancel(); return; }
                Location feet = p.getLocation().clone().add(0, 0.15, 0);
                fx(feet, Particle.CLOUD, 14, 0.55, 0.12, 0.55, 0.02);
                fx(feet, Particle.SWEEP_ATTACK, 2, 0.35, 0.05, 0.35, 0);
                for (LivingEntity e : foes(p, feet, 1.3)) {
                    slow(e, 18, 1);
                    if (struck.add(e.getUniqueId())) hurt(p, e, power(tier, 3.5));
                }
                p.setVelocity(travelDirection.clone().multiply(1.15).setY(p.isOnGround() ? 0.1 : p.getVelocity().getY()));
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean windFeatherFall(Player p, int tier) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOW_FALLING, 220, 0, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1, true, false));
        for (Player ally : allies(p, p.getLocation(), 6.0)) {
            ally.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOW_FALLING, 220, 0, true, false));
            fx(ally.getLocation().add(0, 1, 0), Particle.CLOUD, 18, 0.5, 0.6, 0.5, 0.02);
        }
        sound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.9f);
        return true;
    }

    private boolean windCycloneLift(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف لرفع الإعصار"); return false; }
        Location center = victim.getLocation().clone();
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 14) { cancel(); return; }
                double a = n * 0.8;
                Vector orbit = new Vector(Math.cos(a) * 0.55, 0.38, Math.sin(a) * 0.55);
                victim.setVelocity(orbit);
                fx(victim.getLocation().add(0, 0.7, 0), Particle.SMALL_GUST, 3, 0.25, 0.35, 0.25, 0.02);
                fx(victim.getLocation(), Particle.CLOUD, 8, 0.4, 0.5, 0.4, 0.02);
                if (n % 4 == 0) hurt(p, victim, power(tier, 1.2));
                if (n == 14) { victim.setVelocity(new Vector(0, -0.9, 0)); hurt(p, victim, power(tier, 3.0)); }
            }
        }.runTaskTimer(magic, 0L, 2L);
        sound(center, Sound.ENTITY_PHANTOM_FLAP, 0.75f);
        return true;
    }

    private boolean windSlipstream(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        Location start = p.getLocation().clone();
        sound(start, Sound.ENTITY_BREEZE_CHARGE, 0.8f);
        for (int i = 1; i <= 12; i++) {
            Location marker = start.clone().add(dir.clone().multiply(i * 1.2));
            ring(marker, 0.85, Particle.CLOUD, 16);
        }
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 30) { cancel(); return; }
                for (Player ally : allies(p, start, 15.0)) {
                    Vector offset = ally.getLocation().toVector().subtract(start.toVector());
                    double along = offset.dot(dir);
                    double lateral = offset.clone().subtract(dir.clone().multiply(along)).length();
                    if (along >= 0 && along <= 15 && lateral <= 1.15) {
                        ally.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 12, 2, true, false));
                        fx(ally.getLocation(), Particle.CLOUD, 3, 0.25, 0.1, 0.25, 0.01);
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean windAirPocket(Player p, int tier) {
        Location c = p.getLocation();
        sound(c, Sound.ENTITY_BREEZE_DEATH, 0.75f);
        for (LivingEntity e : foes(p, c, 6.5)) {
            Vector away = e.getLocation().toVector().subtract(c.toVector()).normalize();
            e.setVelocity(away.multiply(1.4).setY(0.45));
            hurt(p, e, power(tier, 4.0));
        }
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (n++ >= 8) { cancel(); return; }
                ring(p.getLocation(), 0.8 + n * 0.65, Particle.SMALL_GUST, 28);
                fx(p.getLocation().add(0, 1, 0), Particle.CLOUD, 24, n * 0.2, 0.4, n * 0.2, 0.03);
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean windSkybreaker(Player p, int tier) {
        Location center = aim(p, 15);
        List<LivingEntity> locked = foes(p, center, 5.5);
        sound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 9) { cancel(); return; }
                for (LivingEntity e : locked) {
                    if (e.isDead() || !e.isValid()) continue;
                    e.setVelocity(new Vector(e.getVelocity().getX() * 0.3, n < 7 ? 0.8 : -1.05, e.getVelocity().getZ() * 0.3));
                    fx(e.getLocation(), Particle.CLOUD, 12, 0.35, 0.5, 0.35, 0.03);
                    if (n == 9) hurt(p, e, power(tier, 8.0));
                }
                ring(center, 1.0 + n * 0.45, Particle.SMALL_GUST, 24);
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    // ============================================================= LIGHTNING

    private boolean lightningCapacitor(Player p, int tier) {
        Location origin = p.getLocation().clone();
        sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.7f);
        new BukkitRunnable() {
            int n = 0;
            double distance = 0;
            Location previous = p.getLocation().clone();
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 50) { discharge(); cancel(); return; }
                Location now = p.getLocation();
                distance += now.distance(previous);
                previous = now.clone();
                fx(now.add(0, 1, 0), Particle.ELECTRIC_SPARK, 5 + n / 8, 0.35, 0.45, 0.35, 0.02);
                if (n >= 20 && distance >= 8.0) { discharge(); cancel(); }
            }
            private void discharge() {
                Location at = p.getLocation();
                double radius = 3.0 + Math.min(4.0, distance * 0.25);
                fx(at, Particle.FLASH, 1, 0, 0, 0, 0);
                for (LivingEntity e : foes(p, at, radius)) {
                    e.getWorld().strikeLightningEffect(e.getLocation());
                    hurt(p, e, power(tier, 3.0 + distance * 0.22));
                    slow(e, 24, 1);
                }
                sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f);
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean lightningStaticSiphon(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف لسرقة الشحنة"); return false; }
        sound(victim.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 12) { cancel(); return; }
                Vector velocity = victim.getVelocity();
                victim.setVelocity(velocity.multiply(0.2));
                fx(victim.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 10, 0.35, 0.45, 0.35, 0.06);
                fx(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 4, 0.2, 0.25, 0.2, 0.03);
                if (n % 3 == 0) hurt(p, victim, power(tier, 1.1));
            }
        }.runTaskTimer(magic, 0L, 2L);
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 100, 1, true, false));
        return true;
    }

    private boolean lightningGroundingRods(Player p, int tier) {
        Location center = safeGround(aim(p, 18));
        if (center == null) center = p.getLocation();
        Location fixed = center.clone();
        sound(fixed, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 6) { cancel(); return; }
                double a = n * Math.PI / 3;
                Location rod = fixed.clone().add(Math.cos(a) * 3.0, 0.1, Math.sin(a) * 3.0);
                fx(rod, Particle.ELECTRIC_SPARK, 16, 0.18, 0.6, 0.18, 0.08);
                ring(rod, 0.45, Particle.ELECTRIC_SPARK, 14);
                LivingEntity nearest = foes(p, rod, 4.5).stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(rod))).orElse(null);
                if (nearest != null) {
                    nearest.getWorld().strikeLightningEffect(nearest.getLocation());
                    hurt(p, nearest, power(tier, 4.0));
                }
            }
        }.runTaskTimer(magic, 0L, 7L);
        return true;
    }

    private boolean lightningRailJump(Player p, int tier) {
        LivingEntity victim = target(p, 18);
        if (victim == null) { status(p, "§cلا يوجد هدف للانتقال الكهرومغناطيسي"); return false; }
        Location destination = victim.getLocation().clone().subtract(pm.dir(p).multiply(1.6));
        Location safe = safeGround(destination);
        if (safe == null) { status(p, "§cالمكان خلف الهدف غير آمن"); return false; }
        Location old = p.getLocation();
        fx(old, Particle.ELECTRIC_SPARK, 35, 0.5, 0.8, 0.5, 0.08);
        p.teleport(safe);
        fx(safe, Particle.FLASH, 1, 0, 0, 0, 0);
        fx(safe, Particle.ELECTRIC_SPARK, 30, 0.5, 0.8, 0.5, 0.08);
        hurt(p, victim, power(tier, 5.0));
        slow(victim, 35, 2);
        sound(safe, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.25f);
        return true;
    }

    private boolean lightningThunderclap(Player p, int tier) {
        Location center = p.getLocation().clone();
        sound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f);
        ring(center, 0.8, Particle.ELECTRIC_SPARK, 24);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                ring(p.getLocation(), 1.0 + n * 0.75, Particle.ELECTRIC_SPARK, 32);
                if (n == 8) {
                    for (LivingEntity e : foes(p, p.getLocation(), 7.0)) {
                        e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.NAUSEA, 65, 0, true, false));
                        slow(e, 45, 1);
                        hurt(p, e, power(tier, 6.0));
                        e.setVelocity(new Vector(0, 0.45, 0));
                    }
                    sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean lightningOvercharge(Player p, int tier) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.HASTE, 160, 1, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 160, 1, true, false));
        sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 8) { cancel(); return; }
                Location c = p.getLocation();
                fx(c.add(0, 1, 0), Particle.ELECTRIC_SPARK, 14, 0.55, 0.55, 0.55, 0.07);
                LivingEntity nearest = foes(p, p.getLocation(), 6.5).stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
                if (nearest != null) {
                    nearest.getWorld().strikeLightningEffect(nearest.getLocation());
                    hurt(p, nearest, power(tier, 2.2));
                }
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean lightningStormCage(Player p, int tier) {
        Location center = aim(p, 19);
        sound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    Location pillar = center.clone().add(Math.cos(a) * 3.3, 0.2, Math.sin(a) * 3.3);
                    fx(pillar, Particle.ELECTRIC_SPARK, 10, 0.08, 1.3, 0.08, 0.08);
                }
                for (LivingEntity e : foes(p, center, 4.0)) {
                    slow(e, 12, 4);
                    if (n % 2 == 0) {
                        e.getWorld().strikeLightningEffect(e.getLocation());
                        hurt(p, e, power(tier, 2.0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    // ================================================================ SHADOW

    private boolean shadowAfterimages(Player p, int tier) {
        sound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4;
            Location shade = origin.clone().add(Math.cos(a) * 2, 0.2, Math.sin(a) * 2);
            fx(shade, Particle.PORTAL, 28, 0.35, 0.8, 0.35, 0.08);
        }
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 6) { cancel(); return; }
                for (LivingEntity e : foes(p, p.getLocation(), 5.0)) {
                    if (n % 2 == 0) {
                        fx(e.getLocation().add(0, 1, 0), Particle.SQUID_INK, 14, 0.4, 0.7, 0.4, 0.05);
                        hurt(p, e, power(tier, 1.7));
                    }
                }
                fx(p.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 14, 0.35, 0.55, 0.35, 0.02);
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean shadowPositionSwap(Player p, int tier) {
        LivingEntity victim = target(p, 18);
        if (victim == null) { status(p, "§cلا يوجد هدف لتبادل الظلال"); return false; }
        Location playerSpot = safeGround(victim.getLocation());
        Location targetSpot = safeGround(p.getLocation());
        if (playerSpot == null || targetSpot == null) { status(p, "§cأحد موقعي التبادل غير آمن"); return false; }
        fx(p.getLocation(), Particle.PORTAL, 35, 0.4, 0.7, 0.4, 0.08);
        fx(victim.getLocation(), Particle.SQUID_INK, 35, 0.4, 0.7, 0.4, 0.08);
        victim.teleport(targetSpot);
        p.teleport(playerSpot);
        hurt(p, victim, power(tier, 4.0));
        slow(victim, 35, 1);
        sound(playerSpot, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f);
        return true;
    }

    private boolean shadowDreadGaze(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف لنظرة الرعب"); return false; }
        Vector away = victim.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
        sound(victim.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.65f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 12) { cancel(); return; }
                victim.setVelocity(away.clone().multiply(0.42).setY(0.08));
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, 14, 0, true, false));
                fx(victim.getEyeLocation(), Particle.SOUL, 3, 0.1, 0.1, 0.1, 0.01);
                if (n % 4 == 0) hurt(p, victim, power(tier, 1.25));
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean shadowNightLeech(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف لامتصاص الحياة"); return false; }
        sound(victim.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 8) { cancel(); return; }
                Location from = victim.getLocation().add(0, 1, 0);
                Location to = p.getLocation().add(0, 1, 0);
                Vector d = to.toVector().subtract(from.toVector());
                for (double step = 0; step < 1; step += 0.2) fx(from.clone().add(d.clone().multiply(step)), Particle.SOUL, 2, 0.03, 0.03, 0.03, 0);
                hurt(p, victim, power(tier, 1.8));
                heal(p, 0.9 + tier * 0.12);
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean shadowVoidStab(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف للطعن من الظل"); return false; }
        Vector facing = victim.getLocation().getDirection().setY(0).normalize();
        if (facing.lengthSquared() < 0.01) facing = new Vector(0, 0, 1);
        Location behind = safeGround(victim.getLocation().subtract(facing.multiply(1.6)));
        if (behind == null) behind = safeGround(victim.getLocation());
        if (behind == null) return false;
        fx(p.getLocation(), Particle.PORTAL, 24, 0.3, 0.6, 0.3, 0.08);
        p.teleport(behind);
        fx(behind, Particle.SQUID_INK, 25, 0.35, 0.7, 0.35, 0.05);
        hurt(p, victim, power(tier, 7.0));
        slow(victim, 24, 1);
        sound(behind, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f);
        return true;
    }

    private boolean shadowUmbralDebt(Player p, int tier) {
        LivingEntity victim = target(p, 18);
        if (victim == null) { status(p, "§cلا يوجد هدف للّعنة"); return false; }
        int darkness = Math.max(0, 15 - localLight(victim.getLocation()));
        int delay = 45;
        sound(victim.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= delay) { cancel(); return; }
                fx(victim.getLocation().add(0, 1, 0), Particle.SOUL, 5, 0.22, 0.3, 0.22, 0.015);
                if (n % 10 == 0) fx(victim.getLocation(), Particle.REVERSE_PORTAL, 12, 0.4, 0.3, 0.4, 0.02);
                if (n == delay) {
                    double amount = 6.0 + darkness * 0.45;
                    hurt(p, victim, power(tier, amount));
                    fx(victim.getLocation(), Particle.SQUID_INK, 40, 1.1, 0.9, 1.1, 0.05);
                    sound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f);
                }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean shadowEclipseHunt(Player p, int tier) {
        LivingEntity victim = target(p, 22);
        if (victim == null) { status(p, "§cلا يوجد هدف لمطاردة الكسوف"); return false; }
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.INVISIBILITY, 65, 0, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 65, 2, true, false));
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 12) { cancel(); return; }
                fx(victim.getLocation().add(0, 1, 0), Particle.PORTAL, 12, 0.4, 0.6, 0.4, 0.06);
                if (n == 12) {
                    Vector back = victim.getLocation().getDirection().setY(0).normalize();
                    Location landing = safeGround(victim.getLocation().subtract(back.multiply(1.4)));
                    if (landing != null) p.teleport(landing);
                    hurt(p, victim, power(tier, 10.0));
                    fx(victim.getLocation(), Particle.SQUID_INK, 45, 0.7, 0.8, 0.7, 0.05);
                    sound(victim.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.6f);
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    // ================================================================= ICE

    private boolean iceSkate(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        p.setVelocity(dir.multiply(1.3 + tier * 0.02).setY(0.1));
        Set<UUID> hit = new HashSet<>();
        sound(p.getLocation(), Sound.BLOCK_GLASS_STEP, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 16) { cancel(); return; }
                Location feet = p.getLocation().clone();
                fx(feet, Particle.SNOWFLAKE, 18, 0.65, 0.15, 0.65, 0.02);
                fx(feet, Particle.CLOUD, 5, 0.3, 0.05, 0.3, 0.01);
                for (LivingEntity e : foes(p, feet, 1.25)) {
                    slow(e, 30, 2);
                    if (hit.add(e.getUniqueId())) hurt(p, e, power(tier, 4.0));
                }
                p.setVelocity(dir.clone().multiply(1.0).setY(p.isOnGround() ? 0.06 : p.getVelocity().getY()));
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean iceFrostbiteBrand(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف لوسم الصقيع"); return false; }
        sound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.65f);
        new BukkitRunnable() {
            int stacks = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || stacks++ >= 5) { cancel(); return; }
                freeze(victim, 35 + stacks * 10);
                slow(victim, 35, stacks);
                fx(victim.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 18 + stacks * 3, 0.35, 0.5, 0.35, 0.02);
                if (stacks == 5) {
                    hurt(p, victim, power(tier, 7.0));
                    fx(victim.getLocation(), Particle.CLOUD, 35, 0.65, 0.5, 0.65, 0.02);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean iceMirrorPrison(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف لسجن البلور الجليدي"); return false; }
        Location center = victim.getLocation().clone();
        sound(center, Sound.BLOCK_GLASS_PLACE, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 18) { cancel(); return; }
                double radius = Math.max(0.7, 2.2 - n * 0.08);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    Location edge = center.clone().add(Math.cos(a) * radius, 0.3 + (i % 3) * 0.75, Math.sin(a) * radius);
                    fx(edge, Particle.SNOWFLAKE, 5, 0.08, 0.18, 0.08, 0.01);
                    fx(edge, Particle.END_ROD, 1, 0.02, 0.03, 0.02, 0);
                }
                victim.setVelocity(new Vector(0, Math.min(0, victim.getVelocity().getY()), 0));
                slow(victim, 10, 6);
                freeze(victim, 80 + n * 3);
                if (n == 18) hurt(p, victim, power(tier, 6.5));
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean iceIcicleCross(Player p, int tier) {
        Location center = aim(p, 18);
        sound(center, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 0.7f);
        new BukkitRunnable() {
            int wave = 0;
            @Override public void run() {
                if (!p.isOnline() || wave++ >= 4) { cancel(); return; }
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI * 0.5 * i + wave * 0.22;
                    Location strike = center.clone().add(Math.cos(a) * (1.2 + wave * 0.45), 0, Math.sin(a) * (1.2 + wave * 0.45));
                    fx(strike.clone().add(0, 5, 0), Particle.SNOWFLAKE, 12, 0.2, 0.1, 0.2, 0.02);
                    blockFx(strike, Material.PACKED_ICE, 18, 0.24, 0.55, 0.24, 0.04);
                    for (LivingEntity e : foes(p, strike, 1.2)) {
                        hurt(p, e, power(tier, 2.2)); freeze(e, 40);
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean iceWhiteout(Player p, int tier) {
        Location center = aim(p, 15);
        sound(center, Sound.WEATHER_RAIN_ABOVE, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 24) { cancel(); return; }
                fx(center.clone().add(0, 2, 0), Particle.WHITE_ASH, 35, 4.0, 1.6, 4.0, 0.015);
                fx(center, Particle.SNOWFLAKE, 35, 4.0, 1.2, 4.0, 0.02);
                for (LivingEntity e : foes(p, center, 4.2)) {
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, 18, 0, true, false));
                    slow(e, 22, 1);
                    if (n % 6 == 0) hurt(p, e, power(tier, 1.3));
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean iceGlacierSlide(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        p.setVelocity(dir.clone().multiply(1.0).setY(0.35));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOW_FALLING, 70, 0, true, false));
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 18) { cancel(); return; }
                Location at = p.getLocation();
                ring(at.clone().add(0, 0.1, 0), 1.1, Particle.SNOWFLAKE, 24);
                blockFx(at, Material.ICE, 12, 0.5, 0.12, 0.5, 0.03);
                for (LivingEntity e : foes(p, at, 1.8)) {
                    if (hit.add(e.getUniqueId())) {
                        hurt(p, e, power(tier, 5.0));
                        e.setVelocity(dir.clone().multiply(0.6).setY(0.45));
                    }
                }
                p.setVelocity(dir.clone().multiply(0.88).setY(p.getVelocity().getY() * 0.65));
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean iceAbsoluteZero(Player p, int tier) {
        Location center = aim(p, 18);
        sound(center, Sound.BLOCK_BEACON_AMBIENT, 0.55f);
        new BukkitRunnable() {
            int charge = 0;
            @Override public void run() {
                if (!p.isOnline() || charge++ >= 12) { cancel(); return; }
                double r = 1.2 + charge * 0.32;
                ring(center, r, Particle.SNOWFLAKE, 42);
                fx(center.clone().add(0, 1.5, 0), Particle.END_ROD, 8 + charge, 0.3, 0.4, 0.3, 0.01);
                if (charge == 12) {
                    for (LivingEntity e : foes(p, center, 5.5)) {
                        freeze(e, 180 + tier * 6);
                        slow(e, 100, 4);
                        hurt(p, e, power(tier, 8.0));
                    }
                    fx(center, Particle.SNOWFLAKE, 120, 3.5, 1.5, 3.5, 0.05);
                    sound(center, Sound.BLOCK_GLASS_BREAK, 0.45f);
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    // ================================================================ METAL

    private boolean metalMagneticField(Player p, int tier) {
        sound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.3f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 10) { cancel(); return; }
                ring(p.getLocation(), 2.0 + n * 0.28, Particle.CRIT, 36);
                for (LivingEntity e : foes(p, p.getLocation(), 6.0)) {
                    Vector pull = p.getLocation().toVector().subtract(e.getLocation().toVector()).normalize();
                    e.setVelocity(pull.multiply(0.56).setY(0.12));
                    if (n % 3 == 0) hurt(p, e, power(tier, 1.0));
                }
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 5, 5, 5)) {
                    if (!(e instanceof LivingEntity) && !(e instanceof Player)) {
                        Vector pull = p.getLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(0.35);
                        e.setVelocity(pull);
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean metalBladeOrbit(Player p, int tier) {
        sound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 36) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 4; i++) {
                    double a = n * 0.34 + i * Math.PI / 2;
                    Location blade = c.clone().add(Math.cos(a) * 1.8, Math.sin(a * 1.5) * 0.35, Math.sin(a) * 1.8);
                    fx(blade, Particle.CRIT, 6, 0.08, 0.08, 0.08, 0.02);
                    fx(blade, Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0);
                    for (LivingEntity e : foes(p, blade, 0.9)) {
                        if (hit.add(e.getUniqueId())) hurt(p, e, power(tier, 5.0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    private boolean metalRicochet(Player p, int tier) {
        LivingEntity first = target(p, 18);
        if (first == null) { status(p, "§cلا يوجد هدف للارتداد المعدني"); return false; }
        Set<UUID> hit = new HashSet<>();
        LivingEntity[] current = new LivingEntity[]{first};
        new BukkitRunnable() {
            int bounce = 0;
            @Override public void run() {
                if (!p.isOnline() || bounce >= 5 || current[0] == null || current[0].isDead()) { cancel(); return; }
                LivingEntity victim = current[0];
                hit.add(victim.getUniqueId());
                Location at = victim.getLocation().add(0, 1, 0);
                fx(at, Particle.CRIT, 35, 0.35, 0.45, 0.35, 0.08);
                fx(at, Particle.SWEEP_ATTACK, 2, 0, 0, 0, 0);
                hurt(p, victim, power(tier, 3.2));
                sound(at, Sound.BLOCK_ANVIL_LAND, 1.4f);
                current[0] = foes(p, at, 7.0).stream().filter(e -> !hit.contains(e.getUniqueId()))
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(at))).orElse(null);
                if (current[0] != null) {
                    Vector d = current[0].getLocation().toVector().subtract(at.toVector());
                    for (double i = 0; i < 1; i += 0.1) fx(at.clone().add(d.clone().multiply(i)), Particle.CRIT, 2, 0.04, 0.04, 0.04, 0.01);
                }
                bounce++;
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean metalChainGrapple(Player p, int tier) {
        LivingEntity victim = target(p, 20);
        if (victim != null) {
            Vector pull = p.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize().multiply(1.15).setY(0.22);
            victim.setVelocity(pull);
            hurt(p, victim, power(tier, 4.0));
            fx(victim.getLocation(), Particle.CRIT, 24, 0.35, 0.4, 0.35, 0.08);
        } else {
            Location anchor = aim(p, 16);
            Vector pull = anchor.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.45).setY(0.28);
            p.setVelocity(pull);
            fx(anchor, Particle.CRIT, 28, 0.25, 0.35, 0.25, 0.08);
        }
        sound(p.getLocation(), Sound.BLOCK_CHAIN_HIT, 0.8f);
        return true;
    }

    private boolean metalPolarRepulsion(Player p, int tier) {
        Location c = p.getLocation();
        sound(c, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
        for (LivingEntity e : foes(p, c, 6.5)) {
            Vector away = e.getLocation().toVector().subtract(c.toVector()).normalize();
            e.setVelocity(away.multiply(1.55).setY(0.35));
            hurt(p, e, power(tier, 4.5));
        }
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                ring(p.getLocation(), 1.0 + n * 0.75, Particle.CRIT, 32);
                fx(p.getLocation().add(0, 1, 0), Particle.SCRAPE, 10, 0.8, 0.6, 0.8, 0.02);
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean metalLivingForge(Player p, int tier) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 140, 1, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.ABSORPTION, 140, 1, true, false));
        sound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 28) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + n * 0.22;
                    Location plate = c.clone().add(Math.cos(a) * 1.15, Math.sin(n * 0.4) * 0.4, Math.sin(a) * 1.15);
                    fx(plate, Particle.CRIT, 5, 0.12, 0.1, 0.12, 0.02);
                    fx(plate, Particle.SCRAPE, 2, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean metalSiegeRam(Player p, int tier) {
        Vector dir = pm.dir(p).setY(0).normalize();
        Set<UUID> struck = new HashSet<>();
        sound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f);
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 35, 2, true, false));
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 12) { cancel(); return; }
                p.setVelocity(dir.clone().multiply(1.3).setY(0.08));
                Location c = p.getLocation().add(0, 1, 0);
                fx(c, Particle.CRIT, 20, 0.55, 0.6, 0.55, 0.09);
                for (LivingEntity e : foes(p, c, 1.8)) {
                    if (struck.add(e.getUniqueId())) {
                        hurt(p, e, power(tier, 8.0));
                        e.setVelocity(dir.clone().multiply(1.4).setY(0.48));
                    }
                }
                if (!p.getLocation().add(dir).getBlock().isPassable()) { p.setVelocity(new Vector(0, 0.2, 0)); cancel(); }
            }
        }.runTaskTimer(magic, 0L, 1L);
        return true;
    }

    // ================================================================ NATURE

    private boolean natureSpringPad(Player p, int tier) {
        Location pad = safeGround(aim(p, 16));
        if (pad == null) pad = safeGround(p.getLocation());
        if (pad == null) { status(p, "§cلا توجد أرض صالحة لنمو وسادة الوثب"); return false; }
        Location fixed = pad.clone();
        sound(fixed, Sound.BLOCK_CHORUS_FLOWER_GROW, 0.8f);
        new BukkitRunnable() {
            int age = 0;
            boolean ready = false;
            @Override public void run() {
                if (!p.isOnline() || age++ >= 70) { cancel(); return; }
                double growth = Math.min(1.0, age / 10.0);
                ring(fixed.clone().add(0, 0.08, 0), 0.45 + growth * 1.15, Particle.CHERRY_LEAVES, 22);
                blockFx(fixed.clone().add(0, 0.1 + growth * 0.12, 0), Material.MOSS_BLOCK, 12, 0.45 * growth, 0.08, 0.45 * growth, 0.02);
                fx(fixed.clone().add(0, 0.25 + growth * 0.35, 0), Particle.HAPPY_VILLAGER, 5, 0.2, 0.18, 0.2, 0.01);
                if (age >= 10) ready = true;
                if (!ready) return;
                for (Entity entity : fixed.getWorld().getNearbyEntities(fixed.clone().add(0, 0.7, 0), 1.35, 1.6, 1.35)) {
                    if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isOnGround()) continue;
                    if (Math.abs(living.getLocation().getY() - fixed.getY()) > 1.25) continue;
                    Vector launch = living.getVelocity().clone().setY(1.05 + tier * 0.035);
                    living.setVelocity(launch);
                    if (living instanceof Player player && sameSchool(p, player)) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, true, false));
                    }
                    fx(living.getLocation(), Particle.CHERRY_LEAVES, 35, 0.7, 0.25, 0.7, 0.04);
                    sound(living.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 1.25f);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean naturePollenCloud(Player p, int tier) {
        Location center = aim(p, 16);
        sound(center, Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 22) { cancel(); return; }
                fx(center.clone().add(0, 1.2, 0), Particle.COMPOSTER, 32, 2.8, 1.0, 2.8, 0.025);
                fx(center.clone().add(0, 0.3, 0), Particle.CHERRY_LEAVES, 14, 2.5, 0.5, 2.5, 0.02);
                for (LivingEntity e : foes(p, center, 3.5)) {
                    slow(e, 25, 1);
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.NAUSEA, 30, 0, true, false));
                    if (n % 5 == 0) hurt(p, e, power(tier, 1.4));
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean natureLifeBond(Player p, int tier) {
        Player partner = allies(p, p.getLocation(), 12).stream().filter(x -> !x.equals(p))
                .min(Comparator.comparingDouble(x -> x.getLocation().distanceSquared(p.getLocation()))).orElse(null);
        if (partner == null) { heal(p, 4 + tier); p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false)); return true; }
        sound(p.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || !partner.isOnline() || n++ >= 10) { cancel(); return; }
                Location a = p.getLocation().add(0, 1, 0);
                Location b = partner.getLocation().add(0, 1, 0);
                Vector d = b.toVector().subtract(a.toVector());
                for (double s = 0; s < 1; s += 0.12) fx(a.clone().add(d.clone().multiply(s)), Particle.HAPPY_VILLAGER, 2, 0.03, 0.03, 0.03, 0.01);
                heal(p, 0.7 + tier * 0.1);
                heal(partner, 0.7 + tier * 0.1);
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean natureVineLash(Player p, int tier) {
        LivingEntity victim = target(p, 15);
        if (victim == null) { status(p, "§cلا يوجد هدف لسوط الكروم"); return false; }
        Location start = p.getEyeLocation();
        Location end = victim.getLocation().add(0, 1, 0);
        Vector d = end.toVector().subtract(start.toVector());
        for (double s = 0; s < 1; s += 0.055) {
            Location vine = start.clone().add(d.clone().multiply(s)).add(0, Math.sin(s * Math.PI) * 0.8, 0);
            fx(vine, Particle.CHERRY_LEAVES, 3, 0.06, 0.06, 0.06, 0.01);
            fx(vine, Particle.HAPPY_VILLAGER, 1, 0.02, 0.02, 0.02, 0);
        }
        Vector yank = p.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize().multiply(0.9).setY(0.22);
        victim.setVelocity(yank);
        slow(victim, 25, 1);
        hurt(p, victim, power(tier, 5.0));
        sound(end, Sound.BLOCK_VINE_PLACE, 0.75f);
        return true;
    }

    private boolean naturePredatorBloom(Player p, int tier) {
        Location flower = safeGround(aim(p, 14));
        if (flower == null) flower = safeGround(p.getLocation());
        if (flower == null) return false;
        Location fixed = flower.clone();
        sound(fixed, Sound.BLOCK_CHORUS_FLOWER_GROW, 0.75f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 14) { cancel(); return; }
                double open = Math.min(1.0, n / 4.0);
                ring(fixed, 0.5 + open, Particle.CHERRY_LEAVES, 20);
                fx(fixed.clone().add(0, open * 1.4, 0), Particle.HAPPY_VILLAGER, 7, open * 0.3, open * 0.2, open * 0.3, 0.02);
                LivingEntity prey = foes(p, fixed, 4.5).stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(fixed))).orElse(null);
                if (prey != null && n % 2 == 0) {
                    fx(prey.getLocation().add(0, 0.8, 0), Particle.COMPOSTER, 16, 0.25, 0.4, 0.25, 0.02);
                    hurt(p, prey, power(tier, 2.8));
                    slow(prey, 24, 1);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean natureWildGrowth(Player p, int tier) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 180, 1, true, false));
        sound(p.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.3f);
        new BukkitRunnable() {
            int n = 0;
            Location previous = p.getLocation().clone();
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || n++ >= 36) { cancel(); return; }
                Location now = p.getLocation();
                if (now.distanceSquared(previous) > 0.3) {
                    fx(previous, Particle.CHERRY_LEAVES, 12, 0.45, 0.15, 0.45, 0.02);
                    for (Player ally : allies(p, previous, 2.2)) heal(ally, 0.35 + tier * 0.04);
                    for (LivingEntity e : foes(p, previous, 1.4)) {
                        e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.POISON, 28, 0, true, false));
                    }
                    previous = now.clone();
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean natureWildHunt(Player p, int tier) {
        sound(p.getLocation(), Sound.ENTITY_WOLF_HOWL, 0.7f);
        int count = Math.min(3, 1 + tier / 3);
        for (int i = 0; i < count; i++) {
            Location spawn = p.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
            Wolf wolf = p.getWorld().spawn(spawn, Wolf.class);
            wolf.setTamed(true);
            wolf.setOwner(p);
            wolf.setCustomName("§aروح الغابة");
            wolf.setCustomNameVisible(false);
            new BukkitRunnable() {
                int life = 0;
                @Override public void run() {
                    if (!p.isOnline() || !wolf.isValid() || life++ >= 160) { wolf.remove(); cancel(); return; }
                    LivingEntity prey = foes(p, wolf.getLocation(), 12).stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(wolf.getLocation()))).orElse(null);
                    if (prey != null && wolf.getTarget() == null) wolf.setTarget(prey);
                    if (life % 12 == 0) fx(wolf.getLocation().add(0, 0.7, 0), Particle.HAPPY_VILLAGER, 4, 0.15, 0.2, 0.15, 0.01);
                }
            }.runTaskTimer(magic, 0L, 2L);
        }
        return true;
    }

    // ================================================================= LIGHT

    private boolean lightPrismSplit(Player p, int tier) {
        Location split = aim(p, 14);
        Vector forward = split.toVector().subtract(p.getEyeLocation().toVector()).normalize();
        Vector side = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        sound(split, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f);
        Set<UUID> hit = new HashSet<>();
        for (int branch = -1; branch <= 1; branch++) {
            Vector ray = forward.clone().add(side.clone().multiply(branch * 0.5)).normalize();
            for (int step = 1; step <= 10; step++) {
                Location point = split.clone().add(ray.clone().multiply(step * 0.85));
                fx(point, Particle.END_ROD, 4, 0.08, 0.08, 0.08, 0.01);
                for (LivingEntity e : foes(p, point, 0.9)) if (hit.add(e.getUniqueId())) hurt(p, e, power(tier, 5.5));
            }
        }
        fx(split, Particle.FLASH, 1, 0, 0, 0, 0);
        return true;
    }

    private boolean lightSunBrand(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف لوسم الشمس"); return false; }
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, 90, 0, true, false));
        sound(victim.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.8f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 30) { cancel(); return; }
                Location eye = victim.getEyeLocation();
                fx(eye, Particle.END_ROD, 6, 0.2, 0.3, 0.2, 0.015);
                if (n == 30) {
                    int light = localLight(victim.getLocation());
                    hurt(p, victim, power(tier, 5.0 + light * 0.22));
                    fx(victim.getLocation(), Particle.FLASH, 1, 0, 0, 0, 0);
                    fx(victim.getLocation(), Particle.FIREWORK, 35, 0.7, 0.7, 0.7, 0.04);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean lightDawnSanctuary(Player p, int tier) {
        Location c = p.getLocation();
        sound(c, Sound.BLOCK_BEACON_ACTIVATE, 0.8f);
        for (Player ally : allies(p, c, 8.0)) {
            ally.removePotionEffect(PotionEffectType.POISON);
            ally.removePotionEffect(PotionEffectType.WITHER);
            ally.removePotionEffect(PotionEffectType.BLINDNESS);
            ally.removePotionEffect(PotionEffectType.SLOWNESS);
            ally.removePotionEffect(PotionEffectType.WEAKNESS);
            ally.removePotionEffect(PotionEffectType.NAUSEA);
            heal(ally, 2.0 + tier * 0.4);
            ally.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
            fx(ally.getLocation().add(0, 1, 0), Particle.END_ROD, 18, 0.45, 0.7, 0.45, 0.03);
        }
        ring(c, 2.0, Particle.END_ROD, 40);
        return true;
    }

    private boolean lightSpectrumStep(Player p, int tier) {
        Vector dir = pm.dir(p);
        Location chosen = null;
        for (double d = 2; d <= 16; d += 0.5) {
            Location check = p.getLocation().clone().add(dir.clone().multiply(d));
            Location ground = safeGround(check);
            if (ground != null && localLight(ground) >= 10) chosen = ground;
        }
        if (chosen == null) { status(p, "§cمفيش نقطة مضيئة وآمنة في اتجاهك"); return false; }
        Location old = p.getLocation();
        fx(old, Particle.END_ROD, 28, 0.35, 0.65, 0.35, 0.04);
        p.teleport(chosen);
        fx(chosen, Particle.FLASH, 1, 0, 0, 0, 0);
        fx(chosen, Particle.END_ROD, 32, 0.35, 0.65, 0.35, 0.04);
        sound(chosen, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f);
        return true;
    }

    private boolean lightSolarFocus(Player p, int tier) {
        int light = localLight(p.getLocation());
        int amplifier = light >= 13 ? 2 : light >= 8 ? 1 : 0;
        int duration = 100 + light * 4;
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.STRENGTH, duration, amplifier, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, duration, 0, true, false));
        sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 20) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 6; i++) {
                    double a = n * 0.2 + i * Math.PI / 3;
                    fx(c.clone().add(Math.cos(a) * 1.1, Math.sin(a) * 0.5, Math.sin(a) * 1.1), Particle.END_ROD, 3, 0.04, 0.04, 0.04, 0.01);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean lightRevelationHalo(Player p, int tier) {
        Location c = p.getLocation();
        sound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.1f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 10) { cancel(); return; }
                ring(p.getLocation(), 1.4 + n * 0.5, Particle.END_ROD, 48);
                for (LivingEntity e : foes(p, p.getLocation(), 7.0)) {
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, 35, 0, true, false));
                    if (localLight(e.getLocation()) <= 4) {
                        slow(e, 20, 1);
                        if (n % 3 == 0) hurt(p, e, power(tier, 1.6));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    private boolean lightCelestialJudgment(Player p, int tier) {
        Location center = aim(p, 22);
        List<LivingEntity> marked = foes(p, center, 5.0);
        sound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.55f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 9) { cancel(); return; }
                if (marked.isEmpty()) {
                    Location beam = center.clone().add(0, 8, 0);
                    fx(beam, Particle.END_ROD, 18, 0.25, 3.5, 0.25, 0.01);
                } else {
                    LivingEntity victim = marked.get((n - 1) % marked.size());
                    if (victim.isDead()) return;
                    Location top = victim.getLocation().add(0, 8, 0);
                    for (double y = 0; y <= 8; y += 0.45) fx(top.clone().subtract(0, y, 0), Particle.END_ROD, 2, 0.08, 0.08, 0.08, 0.01);
                    fx(victim.getLocation(), Particle.FLASH, 1, 0, 0, 0, 0);
                    hurt(p, victim, power(tier, 3.0));
                }
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }

    // ================================================================ SOUND

    private boolean soundMotionResonance(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف للرنين"); return false; }
        Location last = victim.getLocation().clone();
        sound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 45) { cancel(); return; }
                Location now = victim.getLocation();
                if (now.distanceSquared(last) > 0.16) {
                    ring(now, 1.0, Particle.NOTE, 20);
                    hurt(p, victim, power(tier, 2.2));
                    victim.setVelocity(victim.getVelocity().multiply(0.35));
                    last = now.clone();
                }
                fx(victim.getLocation().add(0, 1, 0), Particle.NOTE, 2, 0.1, 0.15, 0.1, 0.01);
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean soundSilenceField(Player p, int tier) {
        Location center = aim(p, 16);
        sound(center, Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 20) { cancel(); return; }
                ring(center, 3.4, Particle.SONIC_BOOM, 12);
                fx(center, Particle.CLOUD, 18, 3.0, 1.1, 3.0, 0.01);
                for (LivingEntity e : foes(p, center, 3.8)) {
                    slow(e, 24, 2);
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.WEAKNESS, 28, 1, true, false));
                    if (n % 5 == 0) hurt(p, e, power(tier, 1.4));
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean soundEchoRadar(Player p, int tier) {
        Location c = p.getLocation();
        sound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65f);
        for (LivingEntity e : foes(p, c, 24)) {
            e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false));
            Vector d = e.getLocation().toVector().subtract(c.toVector());
            double dist = Math.sqrt(d.lengthSquared());
            Location marker = c.clone().add(d.normalize().multiply(Math.min(dist, 20)));
            fx(marker, Particle.NOTE, 5, 0.16, 0.16, 0.16, 0.01);
        }
        for (int r = 3; r <= 24; r += 3) ring(c.clone().add(0, 0.1, 0), r, Particle.NOTE, 48);
        return true;
    }

    private boolean soundReverbStep(Player p, int tier) {
        Location echo = p.getLocation().clone();
        sound(echo, Sound.BLOCK_NOTE_BLOCK_HARP, 1.7f);
        ring(echo, 1.0, Particle.NOTE, 28);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 16) { cancel(); return; }
                fx(echo.clone().add(0, 1, 0), Particle.NOTE, 7, 0.35, 0.5, 0.35, 0.03);
                fx(p.getLocation().add(0, 1, 0), Particle.NOTE, 5, 0.3, 0.4, 0.3, 0.02);
                if (n == 16) {
                    Location safe = safeGround(echo);
                    if (safe != null) {
                        Location old = p.getLocation();
                        p.teleport(safe);
                        fx(old, Particle.SONIC_BOOM, 1, 0, 0, 0, 0);
                        for (LivingEntity e : foes(p, old, 2.5)) hurt(p, e, power(tier, 3.0));
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean soundHarmonicLift(Player p, int tier) {
        Location center = aim(p, 16);
        sound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 8) { cancel(); return; }
                float pitch = 0.6f + n * 0.16f;
                sound(center, Sound.BLOCK_NOTE_BLOCK_BELL, pitch);
                ring(center, 1.0 + n * 0.35, Particle.NOTE, 28);
                for (LivingEntity e : foes(p, center, 4.0)) {
                    e.setVelocity(new Vector(e.getVelocity().getX() * 0.4, 0.46 + n * 0.035, e.getVelocity().getZ() * 0.4));
                    if (n == 8) hurt(p, e, power(tier, 5.5));
                }
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean soundFeedbackLoop(Player p, int tier) {
        LivingEntity victim = target(p, 16);
        if (victim == null) { status(p, "§cلا يوجد هدف للتردد المعاكس"); return false; }
        sound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.55f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 10) { cancel(); return; }
                Vector v = victim.getVelocity();
                victim.setVelocity(v.clone().multiply(-0.9).setY(Math.max(0.18, Math.abs(v.getY()) + 0.1)));
                fx(victim.getLocation().add(0, 1, 0), Particle.SONIC_BOOM, 1, 0, 0, 0, 0);
                if (n % 2 == 0) hurt(p, victim, power(tier, 1.1));
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean soundFinalCrescendo(Player p, int tier) {
        Location center = aim(p, 17);
        sound(center, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
        new BukkitRunnable() {
            int wave = 0;
            @Override public void run() {
                if (!p.isOnline() || wave++ >= 3) { cancel(); return; }
                int index = wave;
                sound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f + index * 0.42f);
                new BukkitRunnable() {
                    int radius = 0;
                    @Override public void run() {
                        if (!p.isOnline() || radius++ >= 5 + index * 2) { cancel(); return; }
                        double r = radius * (0.65 + index * 0.18);
                        ring(center, r, Particle.SONIC_BOOM, 24 + index * 8);
                        for (LivingEntity e : foes(p, center, r + 0.75)) {
                            if (Math.abs(e.getLocation().distance(center) - r) <= 0.85) {
                                hurt(p, e, power(tier, 1.5 + index * 1.4));
                                e.setVelocity(e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.6 + index * 0.2).setY(0.2));
                            }
                        }
                    }
                }.runTaskTimer(magic, 0L, 2L);
            }
        }.runTaskTimer(magic, 0L, 12L);
        return true;
    }

    // =============================================================== CRYSTAL

    private boolean crystalGeodeMine(Player p, int tier) {
        Location center = safeGround(aim(p, 17));
        if (center == null) center = safeGround(p.getLocation());
        if (center == null) return false;
        Location fixed = center.clone();
        sound(fixed, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f);
        new BukkitRunnable() {
            int age = 0;
            @Override public void run() {
                if (!p.isOnline() || age++ >= 65) { cancel(); return; }
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * 2 * i / 5;
                    Location shard = fixed.clone().add(Math.cos(a) * 1.1, 0.1 + (i % 2) * 0.25, Math.sin(a) * 1.1);
                    fx(shard, Particle.END_ROD, 3, 0.06, 0.14, 0.06, 0.01);
                }
                LivingEntity intruder = foes(p, fixed, 1.9).stream().filter(e -> e.getLocation().distanceSquared(fixed) < 3.2).findFirst().orElse(null);
                if (intruder != null || age >= 65) {
                    for (int i = 0; i < 5; i++) {
                        double a = Math.PI * 2 * i / 5;
                        Location shard = fixed.clone().add(Math.cos(a) * 1.1, 0.2, Math.sin(a) * 1.1);
                        fx(shard, Particle.CRIT, 20, 0.3, 0.5, 0.3, 0.06);
                    }
                    for (LivingEntity e : foes(p, fixed, 3.3)) hurt(p, e, power(tier, 5.5));
                    sound(fixed, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f);
                    cancel();
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean crystalFacetHeart(Player p, int tier) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.ABSORPTION, 180, 2, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 90, 0, true, false));
        sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.75f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || n++ >= 30) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Location facet = c.clone().add(Math.cos(a) * 1.0, Math.sin(n * 0.35) * 0.35, Math.sin(a) * 1.0);
                    fx(facet, Particle.END_ROD, 4, 0.06, 0.08, 0.06, 0.01);
                    fx(facet, Particle.CRIT, 2, 0.04, 0.04, 0.04, 0.01);
                }
            }
        }.runTaskTimer(magic, 0L, 2L);
        return true;
    }

    private boolean crystalPrismRefraction(Player p, int tier) {
        Location impact = aim(p, 13);
        sound(impact, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.7f);
        Vector forward = impact.toVector().subtract(p.getEyeLocation().toVector()).normalize();
        Vector side = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Set<UUID> struck = new HashSet<>();
        for (int i = -2; i <= 2; i++) {
            Vector ray = forward.clone().add(side.clone().multiply(i * 0.32)).normalize();
            for (double d = 1; d <= 11; d += 0.6) {
                Location point = impact.clone().add(ray.clone().multiply(d));
                fx(point, Particle.END_ROD, 2, 0.05, 0.05, 0.05, 0.01);
                fx(point, Particle.CRIT, 1, 0.04, 0.04, 0.04, 0.02);
                for (LivingEntity e : foes(p, point, 0.75)) if (struck.add(e.getUniqueId())) hurt(p, e, power(tier, 5.5));
            }
        }
        fx(impact, Particle.FLASH, 1, 0, 0, 0, 0);
        return true;
    }

    private boolean crystalGrowth(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف لنمو البلور"); return false; }
        Location center = victim.getLocation().clone();
        sound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.65f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || n++ >= 14) { cancel(); return; }
                double height = Math.min(3.0, n * 0.24);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6;
                    Location shard = center.clone().add(Math.cos(a) * 1.5, height, Math.sin(a) * 1.5);
                    fx(shard, Particle.END_ROD, 5, 0.06, 0.2, 0.06, 0.01);
                }
                slow(victim, 10, 5);
                victim.setVelocity(new Vector(0, Math.min(0, victim.getVelocity().getY()), 0));
                if (n == 14) hurt(p, victim, power(tier, 7.0));
            }
        }.runTaskTimer(magic, 0L, 3L);
        return true;
    }

    private boolean crystalResonantFracture(Player p, int tier) {
        LivingEntity victim = target(p, 17);
        if (victim == null) { status(p, "§cلا يوجد هدف لكسر الرنين"); return false; }
        sound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.55f);
        new BukkitRunnable() {
            int fractures = 0;
            @Override public void run() {
                if (!p.isOnline() || victim.isDead() || fractures++ >= 6) { cancel(); return; }
                Location c = victim.getLocation().add(0, 1, 0);
                double a = fractures * Math.PI / 3;
                fx(c.clone().add(Math.cos(a) * 0.7, Math.sin(a) * 0.7, Math.sin(a) * 0.7), Particle.CRIT, 14, 0.12, 0.12, 0.12, 0.03);
                fx(c, Particle.END_ROD, 6, 0.25, 0.3, 0.25, 0.03);
                if (fractures == 6) {
                    fx(c, Particle.FLASH, 1, 0, 0, 0, 0);
                    fx(c, Particle.CRIT, 55, 0.8, 0.8, 0.8, 0.1);
                    hurt(p, victim, power(tier, 9.0));
                    slow(victim, 50, 2);
                }
            }
        }.runTaskTimer(magic, 0L, 5L);
        return true;
    }

    private boolean crystalShatterstep(Player p, int tier) {
        Vector direction = pm.dir(p);
        Location destination = null;
        for (double d = 2; d <= 12; d += 0.5) {
            Location candidate = safeGround(p.getLocation().clone().add(direction.clone().multiply(d)));
            if (candidate != null) destination = candidate;
        }
        if (destination == null) { status(p, "§cلا توجد نقطة آمنة للتشظي"); return false; }
        Location old = p.getLocation();
        fx(old, Particle.CRIT, 28, 0.4, 0.8, 0.4, 0.08);
        p.teleport(destination);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Location shard = old.clone().add(Math.cos(a) * 1.2, 0.5, Math.sin(a) * 1.2);
            fx(shard, Particle.END_ROD, 8, 0.08, 0.3, 0.08, 0.03);
        }
        for (LivingEntity e : foes(p, old, 2.3)) {
            hurt(p, e, power(tier, 4.0));
            slow(e, 25, 1);
        }
        sound(destination, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f);
        return true;
    }

    private boolean crystalDiamondBloom(Player p, int tier) {
        Location center = aim(p, 15);
        sound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f);
        new BukkitRunnable() {
            int stage = 0;
            @Override public void run() {
                if (!p.isOnline() || stage++ >= 6) { cancel(); return; }
                double radius = 0.8 + stage * 0.9;
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + stage * 0.12;
                    Location petal = center.clone().add(Math.cos(a) * radius, Math.sin(a * 2) * 0.4 + 0.3, Math.sin(a) * radius);
                    fx(petal, Particle.END_ROD, 5, 0.08, 0.14, 0.08, 0.015);
                    fx(petal, Particle.CRIT, 2, 0.04, 0.04, 0.04, 0.02);
                }
                for (LivingEntity e : foes(p, center, radius + 0.9)) {
                    if (Math.abs(e.getLocation().distance(center) - radius) < 1.1) {
                        hurt(p, e, power(tier, 2.0));
                        slow(e, 25, 1);
                    }
                }
            }
        }.runTaskTimer(magic, 0L, 4L);
        return true;
    }
}
