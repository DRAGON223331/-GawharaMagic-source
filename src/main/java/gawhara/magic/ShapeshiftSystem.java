package gawhara.magic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.kyori.adventure.title.Title;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Shapeshift elemental school. The ten mob forms are learned by killing
 * their matching mobs, and each form owns a different active ability.
 * Real player-model disguises are supported through optional LibsDisguises.
 */
final class ShapeshiftSystem implements Listener {

    enum Form {
        SPIDER(EntityType.SPIDER, "سبايدر", "شبكة الصياد", "تطلق شبكة تثبّت الأعداء أمامك وتمنعهم من القفز", Material.SPIDER_EYE),
        CREEPER(EntityType.CREEPER, "كريبر", "الانفجار المشحون", "تفجّر طاقة حولك وتدفع الأعداء من غير تكسير أي بلوك", Material.GUNPOWDER),
        SKELETON(EntityType.SKELETON, "سكيلتون", "وابل العظام", "تطلق ثلاث سهام دقيقة في مروحة أمامك", Material.BONE),
        BLAZE(EntityType.BLAZE, "بليز", "القذيفة اللهبية", "تطلق كرة نارية وتحصل على مقاومة النار", Material.BLAZE_ROD),
        GHAST(EntityType.GHAST, "جاست", "قذيفة الجاست", "تطلق قذيفة انفجارية قوية لا تدمّر البيئة", Material.GHAST_TEAR),
        WARDEN(EntityType.WARDEN, "واردن", "الزئير الصوتي", "موجة صوتية تخترق المسار وتكشف وتؤذي أول هدف", Material.SCULK_CATALYST),
        IRON_GOLEM(EntityType.IRON_GOLEM, "آيرون جولم", "ضربة الحارس", "تضرب الأرض وتطلق موجة صدمة مع درع حديدي مؤقت", Material.IRON_BLOCK),
        EVOKER(EntityType.EVOKER, "إيفوكر", "أنياب الاستدعاء", "تستدعي صفًا من أنياب الإيفوكر أمامك", Material.TOTEM_OF_UNDYING),
        ENDER_DRAGON(EntityType.ENDER_DRAGON, "تنين الإند", "زفير التنين", "تطلق كرة تنين تنشئ سحابة تنفّس مؤذية عند الاصطدام", Material.DRAGON_HEAD),
        ENDERMAN(EntityType.ENDERMAN, "إندرمان", "خطوة الفراغ", "تنتقل خلف أقرب هدف أمامك وتصيبه بضربة ظل", Material.ENDER_PEARL);

        final EntityType type;
        final String ar;
        final String ability;
        final String description;
        final Material icon;

        Form(EntityType type, String ar, String ability, String description, Material icon) {
            this.type = type;
            this.ar = ar;
            this.ability = ability;
            this.description = description;
            this.icon = icon;
        }

        static Form from(EntityType type) {
            for (Form form : values()) if (form.type == type) return form;
            return null;
        }
    }

    private record ActiveForm(Form form, long expiresAt) {}

    private final Magic magic;
    private final Map<UUID, ActiveForm> activeForms = new HashMap<>();
    private final Set<UUID> disguiseWarningSent = new HashSet<>();
    private BukkitTask ticker;

    ShapeshiftSystem(Magic magic) {
        this.magic = magic;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, magic);
        ticker = Bukkit.getScheduler().runTaskTimer(magic, this::tick, 2L, 2L);
    }

    void shutdown() {
        if (ticker != null) ticker.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) endForm(player, false);
        activeForms.clear();
    }

    private String path(Player player) {
        return magic.playerPath(player) + ".shapeshift.forms";
    }

    List<String> learnedNames(Player player) {
        List<String> names = new ArrayList<>();
        for (String raw : magic.data.getStringList(path(player))) {
            try {
                Form.valueOf(raw);
                if (!names.contains(raw)) names.add(raw);
            } catch (IllegalArgumentException ignored) {
                // Ignore stale or manually edited entries.
            }
        }
        return names;
    }

    int capacity(Player player) {
        // Level 1 opens the first slot; each level opens one more, up to ten.
        return Math.max(1, Math.min(Form.values().length, magic.levelOf(player)));
    }

    boolean hasForm(Player player, int index) {
        if (index < 0 || index >= Form.values().length) return false;
        return learnedNames(player).contains(Form.values()[index].name());
    }

    Form activeForm(Player player) {
        ActiveForm state = activeForms.get(player.getUniqueId());
        return state == null ? null : state.form();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKilled(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || magic.getEl(killer) != Element.SHAPESHIFT) return;
        Form form = Form.from(event.getEntity().getType());
        if (form == null) return;

        List<String> learned = learnedNames(killer);
        if (learned.contains(form.name())) {
            killer.sendActionBar(Magic.c("§d✦ أنت تعرف شكل " + form.ar + " بالفعل"));
            return;
        }

        int max = capacity(killer);
        if (learned.size() >= max) {
            killer.sendMessage(Magic.c("§cخانات التحول ممتلئة: §e" + learned.size() + "/10§c. ارفع مستواك لفتح خانة جديدة ثم اقتل كائنًا جديدًا."));
            return;
        }

        learned.add(form.name());
        magic.data.set(path(killer), learned);
        magic.dirty = true;
        magic.save();
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.25f);
        killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                killer.getLocation().add(0, 1, 0), 35, 0.7, 0.9, 0.7, 0.08);
        killer.showTitle(Title.title(Magic.c("§d✦ شكل جديد: " + form.ar + " ✦"),
                Magic.c("§7اتعلمت " + form.ability + " — " + learned.size() + "/" + max + " خانة"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
        killer.sendMessage(Magic.c("§aاتفتح لك شكل " + form.ar + "! §7فعّل وضع القوة بـ F، واختار "
                + (indexOf(form) + 1) + " بالسكرول لاستخدام قدرته."));
    }

    void onLevelUp(Player player, int oldLevel, int newLevel) {
        if (magic.getEl(player) != Element.SHAPESHIFT || newLevel <= oldLevel) return;
        int oldCapacity = Math.max(1, Math.min(10, oldLevel));
        int newCapacity = capacity(player);
        if (newCapacity > oldCapacity) {
            player.sendMessage(Magic.c("§d✦ قوة التحول تطورت! §fفتحت خانة جديدة §e(" + newCapacity + "/10)"));
            player.sendMessage(Magic.c("§7اقتُل كائنًا جديدًا من القائمة لتتعلم شكله وقدرته الخاصة."));
        }
    }

    void showForms(Player player) {
        List<String> learned = learnedNames(player);
        int max = capacity(player);
        player.sendMessage(Magic.c("§d✦ سجل التحول §7— الخانات المفتوحة: §e" + learned.size() + "/" + max + " §8(الحد الأقصى 10)"));
        boolean hasFreeSlot = learned.size() < max;
        for (int i = 0; i < Form.values().length; i++) {
            Form form = Form.values()[i];
            boolean known = learned.contains(form.name());
            String state = known ? " §8— §aمفتوح"
                    : hasFreeSlot ? " §8— §cاقتل " + form.ar + " لفتحه"
                    : " §8— §6ارفع لفل العنصر لفتح خانة";
            player.sendMessage(Magic.c((known ? "§a✔ " : "§7✖ ") + "§f" + (i + 1) + ". " + form.ar
                    + state + " §7| §e" + form.ability + " §8— §7" + form.description));
        }
        player.sendMessage(Magic.c("§7F لتفعيل وضع القوة، السكرول لاختيار شكل، وكليك يمين لاستخدام قدرته."));
    }

    boolean handleCommand(Player player, String[] args) {
        if (args.length == 0) return false;
        if (args[0].equalsIgnoreCase("forms") || args[0].equalsIgnoreCase("shapes")) {
            if (magic.getEl(player) != Element.SHAPESHIFT) {
                player.sendMessage(Magic.c("§cالأمر ده خاص بعنصر التحول."));
                return true;
            }
            showForms(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("unmorph") || args[0].equalsIgnoreCase("untransform")) {
            endForm(player, true);
            return true;
        }
        return false;
    }

    boolean castForm(Player player, int index, int tier) {
        if (index < 0 || index >= Form.values().length) return false;
        Form form = Form.values()[index];
        if (!hasForm(player, index)) {
            player.sendMessage(Magic.c("§cلسه ما اتعلمتش شكل " + form.ar + ". اقتل " + form.ar + " عشان تفتح قدرته."));
            return false;
        }

        endForm(player, false);
        int safeTier = Math.max(1, Math.min(6, tier));
        long duration = (15L + safeTier * 3L) * 1000L;
        activeForms.put(player.getUniqueId(), new ActiveForm(form, System.currentTimeMillis() + duration));
        applyDisguise(player, form);
        applyFormPerks(player, form, safeTier);
        useAbility(player, form, safeTier);

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 55,
                0.7, 1.0, 0.7, 0.14);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f + safeTier * 0.12f);
        player.showTitle(Title.title(Magic.c("§d✦ " + form.ar + " ✦"),
                Magic.c("§7" + form.ability + " §8| §f" + (duration / 1000) + " ثانية"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(400))));
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")
                && disguiseWarningSent.add(player.getUniqueId())) {
            player.sendMessage(Magic.c("§eملاحظة: القدرات شغالة، ولتغيير شكل موديل اللاعب فعلًا ثبّت إضافة LibsDisguises (اختيارية)."));
        }
        return true;
    }

    private void applyFormPerks(Player player, Form form, int tier) {
        int duration = 15 * 20 + tier * 3 * 20;
        int amp = Math.min(2, (tier - 1) / 2);
        switch (form) {
            case SPIDER -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 1 + amp, true, true));
            case CREEPER -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, true, true));
            case SKELETON -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, true, true));
            case BLAZE -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, true, true));
            case GHAST -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0, true, true));
            case WARDEN -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1, true, true));
            case IRON_GOLEM -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, true, true));
            }
            case EVOKER -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amp, true, true));
            case ENDER_DRAGON -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, true, true));
            }
            case ENDERMAN -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1 + amp, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, true, true));
            }
        }
    }

    private void useAbility(Player player, Form form, int tier) {
        World world = player.getWorld();
        Location origin = player.getLocation().clone();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double scale = 1.0 + (tier - 1) * 0.22;
        switch (form) {
            case SPIDER -> {
                world.playSound(origin, Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.75f);
                for (LivingEntity target : targetsInFront(player, 9.0, 0.55)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70 + tier * 18, 3, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 70 + tier * 18, 5, true, true));
                    world.spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 28,
                            0.35, 0.6, 0.35, 0.05, Material.COBWEB.createBlockData());
                }
                world.spawnParticle(Particle.SWEEP_ATTACK, player.getEyeLocation().add(direction.multiply(3)), 12, 0.5, 0.4, 0.5, 0.1);
            }
            case CREEPER -> {
                world.playSound(origin, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.7f);
                world.spawnParticle(Particle.EXPLOSION, origin.clone().add(0, 1, 0), 2 + tier, 0.6, 0.5, 0.6, 0.1);
                world.spawnParticle(Particle.SMOKE, origin.clone().add(0, 1, 0), 45 + tier * 8, 1.5, 0.8, 1.5, 0.08);
                for (LivingEntity target : targetsNear(player, 4.5 + tier * 0.55)) {
                    target.damage((4.0 + tier * 1.6) * scale, player);
                    Vector away = target.getLocation().toVector().subtract(origin.toVector()).setY(0);
                    if (away.lengthSquared() > 0.001) away.normalize();
                    target.setVelocity(away.multiply(0.9 + tier * 0.18).setY(0.45 + tier * 0.08));
                }
                world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.75f);
            }
            case SKELETON -> {
                world.playSound(origin, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.85f);
                Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
                if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
                right.normalize();
                for (int i = -1; i <= 1; i++) {
                    Vector shotDirection = direction.clone().add(right.clone().multiply(i * 0.12)).normalize();
                    Arrow arrow = world.spawnArrow(player.getEyeLocation(), shotDirection.multiply(2.8 + tier * 0.25), 2.8f, 0.0f);
                    arrow.setShooter(player);
                    arrow.setDamage(3.0 + tier * 1.15);
                    arrow.setCritical(tier >= 3);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setGlowing(true);
                }
                world.spawnParticle(Particle.CRIT, player.getEyeLocation().add(direction.multiply(1.2)), 22, 0.25, 0.25, 0.25, 0.15);
            }
            case BLAZE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 15 * 20 + tier * 60, 0, true, true));
                SmallFireball shot = player.launchProjectile(SmallFireball.class);
                shot.setVelocity(direction.multiply(1.25 + tier * 0.12));
                shot.setIsIncendiary(false);
                shot.setYield(0.0f);
                world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
                world.spawnParticle(Particle.FLAME, player.getEyeLocation(), 24, 0.25, 0.25, 0.25, 0.08);
            }
            case GHAST -> {
                LargeFireball shot = player.launchProjectile(LargeFireball.class);
                shot.setVelocity(direction.multiply(0.9 + tier * 0.1));
                shot.setIsIncendiary(false);
                shot.setYield(0.0f);
                world.playSound(origin, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.8f);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getEyeLocation(), 35, 0.3, 0.3, 0.3, 0.06);
            }
            case WARDEN -> {
                world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
                world.spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation().add(direction.clone().multiply(1.2)), 1);
                RayTraceResult trace = world.rayTraceEntities(player.getEyeLocation(), direction, 22.0 + tier * 2.0,
                        1.0, entity -> entity instanceof LivingEntity && entity != player);
                if (trace != null && trace.getHitEntity() instanceof LivingEntity target) {
                    target.damage((7.0 + tier * 2.0) * scale, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70 + tier * 18, 0, true, true));
                    world.spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
                }
            }
            case IRON_GOLEM -> {
                world.playSound(origin, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.65f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100 + tier * 25, 1, true, true));
                world.spawnParticle(Particle.BLOCK, origin.clone().add(0, 0.1, 0), 100 + tier * 15,
                        2.6 + tier * 0.3, 0.15, 2.6 + tier * 0.3, 0.1, Material.IRON_BLOCK.createBlockData());
                for (LivingEntity target : targetsNear(player, 4.5 + tier * 0.5)) {
                    target.damage((4.0 + tier * 1.25) * scale, player);
                    Vector away = target.getLocation().toVector().subtract(origin.toVector()).setY(0);
                    if (away.lengthSquared() > 0.001) away.normalize();
                    target.setVelocity(away.multiply(0.65 + tier * 0.15).setY(0.45 + tier * 0.06));
                }
            }
            case EVOKER -> {
                world.playSound(origin, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.9f);
                Vector flat = direction.clone().setY(0);
                if (flat.lengthSquared() < 0.001) flat = new Vector(0, 0, 1);
                flat.normalize();
                int count = 3 + tier;
                for (int i = 1; i <= count; i++) {
                    Location at = origin.clone().add(flat.clone().multiply(1.7 + i * 1.25));
                    at.setY(world.getHighestBlockYAt(at) + 0.05);
                    if (at.getBlock().isPassable()) at.subtract(0, 1, 0);
                    EvokerFangs fang = (EvokerFangs) world.spawnEntity(at, EntityType.EVOKER_FANGS);
                    fang.setOwner(player);
                    fang.setAttackDelay(Math.max(0, (i - 1) * 2));
                }
                world.spawnParticle(Particle.ENCHANT, origin.clone().add(0, 1, 0), 45, 0.8, 0.8, 0.8, 0.6);
            }
            case ENDER_DRAGON -> {
                DragonFireball shot = player.launchProjectile(DragonFireball.class);
                shot.setVelocity(direction.multiply(0.82 + tier * 0.09));
                shot.setIsIncendiary(false);
                shot.setYield(0.0f);
                world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 0.8f);
                world.spawnParticle(Particle.DRAGON_BREATH, player.getEyeLocation(), 45, 0.35, 0.35, 0.35, 0.06);
            }
            case ENDERMAN -> {
                LivingEntity target = firstTarget(player, 16.0 + tier * 2.0);
                if (target == null) {
                    player.sendMessage(Magic.c("§cبص على هدف قريب عشان تستخدم خطوة الفراغ."));
                    return;
                }
                Vector behind = target.getLocation().getDirection().setY(0).normalize().multiply(-1.25);
                Location destination = target.getLocation().clone().add(behind);
                destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
                if (!safeStandingSpace(destination)) destination = target.getLocation().clone().add(1.4, 0, 0);
                if (safeStandingSpace(destination)) {
                    player.teleport(destination);
                    target.damage((6.0 + tier * 1.4) * scale, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 35 + tier * 10, 0, true, true));
                    world.spawnParticle(Particle.PORTAL, origin, 35, 0.5, 0.8, 0.5, 0.12);
                    world.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 45, 0.5, 0.8, 0.5, 0.12);
                } else {
                    player.sendMessage(Magic.c("§cمفيش مكان آمن كفاية ورا الهدف."));
                }
            }
        }
    }

    void onMelee(Player player, LivingEntity target, int tier) {
        Form form = activeForm(player);
        if (form == null) return;
        switch (form) {
            case SPIDER -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 35 + tier * 10, 0, true, true));
            case CREEPER -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 35 + tier * 10, 0, true, true));
            case SKELETON -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 45 + tier * 10, 0, true, true));
            case BLAZE -> target.setFireTicks(Math.max(target.getFireTicks(), 50 + tier * 15));
            case GHAST -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35 + tier * 10, 1, true, true));
            case WARDEN -> target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 45 + tier * 10, 0, true, true));
            case IRON_GOLEM -> target.setVelocity(target.getVelocity().setY(Math.max(0.25, target.getVelocity().getY())));
            case EVOKER -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45 + tier * 10, 0, true, true));
            case ENDER_DRAGON -> target.setFireTicks(Math.max(target.getFireTicks(), 70 + tier * 15));
            case ENDERMAN -> target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25 + tier * 8, 0, true, true));
        }
    }

    private int indexOf(Form form) {
        for (int i = 0; i < Form.values().length; i++) if (Form.values()[i] == form) return i;
        return 0;
    }

    private List<LivingEntity> targetsNear(Player player, double radius) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && validTarget(player, living)) targets.add(living);
        }
        return targets;
    }

    private List<LivingEntity> targetsInFront(Player player, double range, double minDot) {
        List<LivingEntity> targets = new ArrayList<>();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        for (LivingEntity target : targetsNear(player, range)) {
            Vector offset = target.getLocation().add(0, 1, 0).toVector().subtract(player.getEyeLocation().toVector());
            double distance = offset.length();
            if (distance > 0.001 && distance <= range && direction.dot(offset.multiply(1.0 / distance)) >= minDot) {
                targets.add(target);
            }
        }
        return targets;
    }

    private LivingEntity firstTarget(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection().normalize(), range, 0.8,
                entity -> entity instanceof LivingEntity living && validTarget(player, living));
        return result != null && result.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    private boolean validTarget(Player player, LivingEntity target) {
        if (target == player || target.isDead() || target instanceof org.bukkit.entity.ArmorStand) return false;
        if (target instanceof Player other && other.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        return true;
    }

    private boolean safeStandingSpace(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid();
    }

    private void applyDisguise(Player player, Form form) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return;
        try {
            Class<?> disguiseTypeClass = Class.forName("me.libraryaddict.disguise.disguisetypes.DisguiseType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object disguiseType = Enum.valueOf((Class<? extends Enum>) disguiseTypeClass.asSubclass(Enum.class), form.type.name());
            Class<?> mobDisguiseClass = Class.forName("me.libraryaddict.disguise.disguisetypes.MobDisguise");
            Object disguise = createMobDisguise(mobDisguiseClass, disguiseTypeClass, disguiseType);
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            for (Method method : api.getMethods()) {
                if (!method.getName().equals("disguiseToAll") || method.getParameterCount() != 2) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Player.class)
                        || !method.getParameterTypes()[1].isInstance(disguise)) continue;
                method.invoke(null, player, disguise);
                return;
            }
            throw new NoSuchMethodException("DisguiseAPI.disguiseToAll");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            magic.getLogger().warning("تعذر تفعيل مظهر التحول عبر LibsDisguises: " + ex.getMessage());
            if (disguiseWarningSent.add(player.getUniqueId())) {
                player.sendMessage(Magic.c("§eإضافة LibsDisguises موجودة لكن إصدارها غير متوافق مع واجهة التحول الحالية."));
            }
        }
    }

    private Object createMobDisguise(Class<?> mobClass, Class<?> typeClass, Object type) throws ReflectiveOperationException {
        for (Constructor<?> constructor : mobClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[0].isAssignableFrom(typeClass) && params[1] == boolean.class) {
                return constructor.newInstance(type, true);
            }
            if (params.length == 1 && params[0].isAssignableFrom(typeClass)) return constructor.newInstance(type);
        }
        throw new NoSuchMethodException("MobDisguise constructor");
    }

    private void removeDisguise(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return;
        try {
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            for (Method method : api.getMethods()) {
                if (!method.getName().equals("undisguiseToAll") || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Player.class)) continue;
                method.invoke(null, player);
                return;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            magic.getLogger().fine("تعذر إزالة مظهر التحول: " + ex.getMessage());
        }
    }

    private void endForm(Player player, boolean announce) {
        ActiveForm state = activeForms.remove(player.getUniqueId());
        if (state == null) {
            if (announce && player.isOnline()) player.sendMessage(Magic.c("§7أنت بالفعل في شكلك الطبيعي."));
            return;
        }
        removeDisguise(player);
        if (announce && player.isOnline()) player.sendMessage(Magic.c("§7رجعت لشكلك الطبيعي."));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ActiveForm> entry : new ArrayList<>(activeForms.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                activeForms.remove(entry.getKey());
                continue;
            }
            if (entry.getValue().expiresAt() <= now) {
                endForm(player, true);
                continue;
            }
            if (entry.getValue().form() == Form.SPIDER && !player.isOnGround()) {
                Vector look = player.getLocation().getDirection().setY(0).normalize();
                if (look.lengthSquared() > 0.001) {
                    Block wall = player.getLocation().clone().add(look.multiply(0.65)).getBlock();
                    if (wall.getType().isSolid()) {
                        Vector velocity = player.getVelocity();
                        player.setVelocity(new Vector(velocity.getX(), Math.max(velocity.getY(), 0.18), velocity.getZ()));
                        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 0.5, 0), 1, 0.15, 0.2, 0.15, 0.02);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        endForm(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        endForm(event.getPlayer(), false);
    }
}
