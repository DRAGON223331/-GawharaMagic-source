package gawhara.magic;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Optional RPG systems for GawharaMagic. All skill costs are paid from AuraSkills mana.
 * Abilities are activated without adding any new weapon items.
 */
public final class AdvancedSystems implements Listener {
    private record FusionInvite(UUID inviter, long expiresAt) {}

    private final Magic magic;
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final org.bukkit.NamespacedKey relicKey;
    private final org.bukkit.NamespacedKey spiritOwnerKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> guardUntil = new HashMap<>();
    private final Map<UUID, Long> awakeningUntil = new HashMap<>();
    private final Map<UUID, Long> forbiddenUntil = new HashMap<>();
    private final Map<UUID, Long> transformationUntil = new HashMap<>();
    private final Map<UUID, UUID> spiritEntities = new HashMap<>();
    private final Map<UUID, Location> lastTravelLocation = new HashMap<>();
    private final Map<UUID, Long> spiritGiftAt = new HashMap<>();
    private final Map<UUID, FusionInvite> fusionInvites = new HashMap<>();
    private boolean dirty;

    AdvancedSystems(Magic magic) {
        this.magic = magic;
        this.plugin = magic;
        this.file = new File(plugin.getDataFolder(), "systems.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.relicKey = new org.bukkit.NamespacedKey(plugin, "gmagic_relic");
        this.spiritOwnerKey = new org.bukkit.NamespacedKey(plugin, "gmagic_spirit_owner");
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        new BukkitRunnable() {
            @Override public void run() {
                tickCompanions();
                tickTransformEffects();
                if (dirty) save();
            }
        }.runTaskTimer(plugin, 20L, 10L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            lastTravelLocation.put(player.getUniqueId(), player.getLocation().clone());
            if (data.getBoolean(path(player) + ".spirit", false)) {
                new BukkitRunnable() {
                    @Override public void run() {
                        if (player.isOnline() && data.getBoolean(path(player) + ".spirit", false)) spawnSpirit(player, false);
                    }
                }.runTaskLater(plugin, 30L);
            }
        }
    }

    void shutdown() {
        save();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isSpirit(entity)) entity.remove();
            }
        }
    }

    void save() {
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save systems.yml: " + ex.getMessage());
        }
    }

    private String path(Player player) {
        return "players." + player.getUniqueId();
    }

    private void changed() {
        dirty = true;
    }

    private SkillsUser auraUser(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) return null;
        try {
            return AuraSkillsApi.get().getUser(player.getUniqueId());
        } catch (IllegalStateException | LinkageError ex) {
            return null;
        }
    }

    double mana(Player player) {
        SkillsUser user = auraUser(player);
        return user == null ? 0.0 : user.getMana();
    }

    double maxMana(Player player) {
        SkillsUser user = auraUser(player);
        return user == null ? 0.0 : user.getMaxMana();
    }

    boolean consumeMoveMana(Player player, int tier) {
        return consumeMana(player, "base-move", tier);
    }

    boolean consumeMana(Player player, String ability, int tier) {
        SkillsUser user = auraUser(player);
        if (user == null) {
            player.sendActionBar(Magic.c("§cAuraSkills غير جاهز؛ لا يمكن استخدام قدرات المانا الآن."));
            return false;
        }
        double base = plugin.getConfig().getDouble("mana." + ability, 18.0);
        double scale = plugin.getConfig().getDouble("mana.level-scale", 1.25);
        double cost = Math.max(0.0, base + Math.max(0, tier - 1) * scale);
        if (awakeningUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()
                && !ability.equals("awakening")) {
            cost *= plugin.getConfig().getDouble("awakening.mana-cost-multiplier", 0.85);
        }
        double currentMana = user.getMana();
        if (currentMana + 0.0001 < cost) {
            player.sendActionBar(Magic.c("§cمانا غير كافية: تحتاج " + String.format(Locale.US, "%.0f", cost)
                    + " ولديك " + String.format(Locale.US, "%.0f", currentMana)));
            player.sendMessage(Magic.c("§7زود المانا من AuraSkills أو انتظر تجددها، ثم جرّب الحركة مرة ثانية."));
            return false;
        }
        if (!user.consumeMana(cost)) {
            player.sendActionBar(Magic.c("§cAuraSkills لم يسمح بخصم المانا؛ حاول بعد لحظة."));
            return false;
        }
        player.sendActionBar(Magic.c("§b✦ §f" + String.format(Locale.US, "%.0f", cost) + " Mana §7| §bAuraSkills"));
        return true;
    }

    private boolean spendBoth(Player first, Player second, double cost) {
        SkillsUser a = auraUser(first);
        SkillsUser b = auraUser(second);
        if (a == null || b == null) {
            first.sendMessage(Magic.c("§cبيانات AuraSkills غير جاهزة لأحد اللاعبين."));
            return false;
        }
        double oldA = a.getMana();
        double oldB = b.getMana();
        if (oldA < cost || oldB < cost) {
            first.sendMessage(Magic.c("§cلازم أنت وشريكك يكون معاكم " + (int) cost + " مانا على الأقل."));
            return false;
        }
        if (!a.consumeMana(cost)) return false;
        if (!b.consumeMana(cost)) {
            a.setMana(oldA);
            return false;
        }
        return true;
    }

    private boolean cooldownReady(Player player, String action) {
        String key = player.getUniqueId() + ":" + action;
        long now = System.currentTimeMillis();
        long next = cooldowns.getOrDefault(key, 0L);
        if (next > now) {
            player.sendActionBar(Magic.c("§c" + actionName(action) + " جاهزة بعد " + ((next - now) / 1000 + 1) + " ث"));
            return false;
        }
        return true;
    }

    private void startCooldown(Player player, String action, int seconds) {
        cooldowns.put(player.getUniqueId() + ":" + action,
                System.currentTimeMillis() + Math.max(1, seconds) * 1000L);
    }

    private String actionName(String action) {
        return switch (action) {
            case "spin" -> "الدوران العنصري";
            case "ultimate" -> "القوة النهائية";
            case "guard" -> "درع الحماية";
            case "awakening" -> "الاستيقاظ";
            case "forbidden" -> "القوة المحرمة";
            case "transform" -> "التحول النادر";
            case "relic" -> "الأثر السحري";
            default -> action;
        };
    }

    void noteCast(Player player) {
        String base = path(player) + ".bounty";
        if (!data.contains(base + ".type") || data.getBoolean(base + ".claimed", false)) return;
        if (!"cast".equals(data.getString(base + ".type"))) return;
        progressBounty(player, 1);
    }

    private void progressBounty(Player player, double amount) {
        String base = path(player) + ".bounty";
        if (!data.contains(base + ".type") || data.getBoolean(base + ".claimed", false)) return;
        double target = data.getDouble(base + ".target", 1.0);
        double progress = Math.min(target, data.getDouble(base + ".progress", 0.0) + amount);
        data.set(base + ".progress", progress);
        changed();
        if (progress >= target && !data.getBoolean(base + ".announced", false)) {
            data.set(base + ".announced", true);
            player.sendMessage(Magic.c("§6✦ §aاكتمل العقد! استخدم §e/gmagic bounty claim §aلتحصيل المكافأة."));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    private void showMana(Player player) {
        SkillsUser user = auraUser(player);
        if (user == null) {
            player.sendMessage(Magic.c("§cAuraSkills غير جاهز أو بياناتك لم تُحمّل بعد."));
            return;
        }
        player.sendMessage(Magic.c("§b✦ المانا: §f" + String.format(Locale.US, "%.1f", user.getMana())
                + "§7 / §f" + String.format(Locale.US, "%.1f", user.getMaxMana()) + " §b(AuraSkills)"));
    }

    private int reputation(Player player) {
        return data.getInt(path(player) + ".reputation", 0);
    }

    private void addReputation(Player player, int amount, String reason) {
        if (amount <= 0) return;
        String key = path(player) + ".reputation";
        data.set(key, Math.max(0, data.getInt(key, 0)) + amount);
        changed();
        player.sendMessage(Magic.c("§d✦ السمعة +" + amount + " §7(" + reason + ") §8| §dالإجمالي: " + reputation(player)));
    }

    private String reputationTitle(int value) {
        if (value >= 1000) return "§6أسطورة العناصر";
        if (value >= 500) return "§dسيد العناصر";
        if (value >= 250) return "§bحارس العناصر";
        if (value >= 100) return "§aمتقن";
        return "§7رحّالة";
    }

    private boolean hasElement(Player player) {
        if (magic.getEl(player) != null) return true;
        player.sendMessage(Magic.c("§cاختار قوتك الأول باستخدام §e/gmagic"));
        return false;
    }

    private int tier(Player player) {
        return Math.max(1, Math.min(5, ((magic.levelOf(player) - 1) / 20) + 1));
    }

    private List<Player> nearbyPlayers(Player center, double radius) {
        List<Player> result = new ArrayList<>();
        for (Entity entity : center.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player other && other != center && other.getGameMode() != GameMode.SPECTATOR) {
                result.add(other);
            }
        }
        return result;
    }

    private void showBurst(Player player, Element element, double radius) {
        Location location = player.getLocation().clone();
        magic.ring(location, element.particle, null, radius, 12);
        magic.spiral(player, element.particle, 24);
        player.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 35,
                radius / 4, 0.7, radius / 4, 0.04);
        player.getWorld().playSound(location, element.sound, 1.0f, 0.9f);
    }

    // ------------------------------------------------------------ combat skill systems
    void activateSpin(Player player) {
        if (!hasElement(player)) return;
        int tier = tier(player);
        if (!cooldownReady(player, "spin")) return;
        if (!consumeMana(player, "spin", tier)) return;
        startCooldown(player, "spin", plugin.getConfig().getInt("cooldowns.spin", 18));
        Element element = magic.getEl(player);
        double radius = 3.5 + tier * 0.65;
        showBurst(player, element, radius);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50 + tier * 12, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 + tier * 10, 0));
        for (LivingEntity target : magic.near(player, radius)) {
            if (isSpirit(target)) continue;
            if (target instanceof Player other) {
                if (other.getGameMode() == GameMode.SPECTATOR || magic.getEl(other) == element) continue;
            }
            Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.001) away.normalize();
            target.setVelocity(away.multiply(0.45 + tier * 0.10).setY(0.15));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25 + tier * 7, 0));
        }
        player.sendMessage(Magic.c(element.color + "✦ الدوران العنصري اتفعل! §7(درجة " + tier + "/5)"));
        noteCast(player);
    }

    void activateUltimate(Player player) {
        if (!hasElement(player)) return;
        int required = plugin.getConfig().getInt("requirements.ultimate-level", 60);
        if (magic.levelOf(player) < required) {
            player.sendMessage(Magic.c("§cالقوة النهائية بتتفتح عند لفل " + required + "."));
            return;
        }
        int tier = tier(player);
        if (!cooldownReady(player, "ultimate")) return;
        if (!consumeMana(player, "ultimate", tier)) return;
        startCooldown(player, "ultimate", plugin.getConfig().getInt("cooldowns.ultimate", 180));
        Element element = magic.getEl(player);
        double radius = 8.0 + tier;
        showBurst(player, element, radius);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1));
        for (Player ally : nearbyPlayers(player, radius)) {
            if (magic.getEl(ally) == element) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                ally.getWorld().spawnParticle(element.particle, ally.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
            }
        }
        player.sendTitle("§6✦ القوة النهائية ✦", element.color + "هالة " + element.ar, 5, 35, 10);
        addReputation(player, 5, "إتقان القوة النهائية");
        noteCast(player);
    }

    void activateGuard(Player player) {
        if (!hasElement(player)) return;
        int tier = tier(player);
        if (!cooldownReady(player, "guard")) return;
        if (!consumeMana(player, "guard", tier)) return;
        startCooldown(player, "guard", plugin.getConfig().getInt("cooldowns.guard", 28));
        long duration = 4500L + tier * 500L;
        guardUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration);
        Element element = magic.getEl(player);
        magic.ring(player.getLocation(), element.particle, null, 2.1 + tier * 0.35, 9);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 22, 0.6, 0.8, 0.6, 0.02);
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.3f);
        player.sendActionBar(Magic.c("§bدرع " + element.ar + " §7نشط لمدة " + (duration / 1000.0) + " ثانية"));
        noteCast(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuardDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long now = System.currentTimeMillis();
        long until = guardUntil.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) {
            event.setDamage(event.getDamage() * plugin.getConfig().getDouble("guard.damage-multiplier", 0.35));
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 8, 0.25, 0.35, 0.25, 0.01);
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.35f, 1.5f);
        } else if (until != 0L) {
            guardUntil.remove(player.getUniqueId());
        }
        if (awakeningUntil.getOrDefault(player.getUniqueId(), 0L) > now) {
            event.setDamage(event.getDamage() * plugin.getConfig().getDouble("awakening.damage-multiplier", 0.75));
        }
    }

    void activateAwakening(Player player) {
        if (!hasElement(player)) return;
        int required = plugin.getConfig().getInt("requirements.awakening-level", 100);
        if (magic.levelOf(player) < required) {
            player.sendMessage(Magic.c("§cالاستيقاظ الكامل بيتفتح عند لفل " + required + "."));
            return;
        }
        int tier = tier(player);
        if (!cooldownReady(player, "awakening")) return;
        if (!consumeMana(player, "awakening", tier)) return;
        startCooldown(player, "awakening", plugin.getConfig().getInt("cooldowns.awakening", 300));
        Element element = magic.getEl(player);
        long durationSeconds = plugin.getConfig().getLong("awakening.duration-seconds", 30L);
        long duration = durationSeconds * 1000L;
        awakeningUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration);
        applyTransformationBuffs(player, element, 2, (int) (durationSeconds * 20L));
        showBurst(player, element, 5.0 + tier);
        player.sendTitle("§6✦ الاستيقاظ ✦", element.color + "وصلت لمرحلة الإتقان", 5, 45, 10);
        addReputation(player, 25, "استيقاظ العنصر");
        noteCast(player);
    }

    private void applyTransformationBuffs(Player player, Element element, int power) {
        applyTransformationBuffs(player, element, power, 220);
    }

    private void applyTransformationBuffs(Player player, Element element, int power, int durationTicks) {
        int duration = Math.max(40, durationTicks);
        int amplifier = Math.min(2, Math.max(0, power - 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Math.min(duration, 240), 0));
        switch (element) {
            case FIRE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, amplifier));
            }
            case WATER -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, duration, amplifier));
            }
            case EARTH -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier));
            case WIND -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, amplifier));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0));
            }
            case LIGHTNING -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier));
            case SHADOW -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Math.min(duration, 300), 0));
            }
            case ICE -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0));
            case METAL -> player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier));
            case NATURE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 1, 0));
            }
            case LIGHT -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
            case SOUND -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier));
            case CRYSTAL -> player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier));
        }
    }

    // ------------------------------------------------------------ idea set 4: fusion, forbidden power, relics, spirit, bounty, rep, forms
    void inviteFusion(Player player, String targetName) {
        if (!hasElement(player)) return;
        Player partner = Bukkit.getPlayerExact(targetName);
        if (partner == null || partner == player || !partner.isOnline()) {
            player.sendMessage(Magic.c("§cاكتب اسم لاعب أونلاين قريب منك."));
            return;
        }
        Element firstElement = magic.getEl(player);
        Element secondElement = magic.getEl(partner);
        if (secondElement == null || firstElement == secondElement) {
            player.sendMessage(Magic.c("§cالاندماج محتاج لاعبين بقوتين مختلفتين."));
            return;
        }
        if (!player.getWorld().equals(partner.getWorld()) || player.getLocation().distanceSquared(partner.getLocation()) > 100) {
            player.sendMessage(Magic.c("§cلازم تكونوا في نفس العالم وعلى مسافة 10 بلوكات."));
            return;
        }
        if (magic.levelOf(player) < 20 || magic.levelOf(partner) < 20) {
            player.sendMessage(Magic.c("§cاندماج العناصر بيتفتح من لفل 20 للطرفين."));
            return;
        }
        long now = System.currentTimeMillis();
        long ready = Math.max(cooldowns.getOrDefault("fusion:" + player.getUniqueId(), 0L),
                cooldowns.getOrDefault("fusion:" + partner.getUniqueId(), 0L));
        if (ready > now) {
            player.sendMessage(Magic.c("§cالاندماج جاهز بعد " + ((ready - now) / 1000 + 1) + " ثانية."));
            return;
        }
        fusionInvites.put(partner.getUniqueId(), new FusionInvite(player.getUniqueId(), now + 30_000L));
        player.sendMessage(Magic.c("§d✦ بعتّ دعوة اندماج إلى " + partner.getName() + ". مستني موافقته."));
        partner.sendMessage(Magic.c("§d✦ " + player.getName() + " دعاك لاندماج " + firstElement.ar + " + " + secondElement.ar + "."));
        partner.sendMessage(Magic.c("§eاكتب /gmagic fusion accept للموافقة أو /gmagic fusion decline للرفض. الدعوة صالحة 30 ثانية."));
    }

    private void acceptFusion(Player partner) {
        FusionInvite invite = fusionInvites.get(partner.getUniqueId());
        if (invite == null) {
            partner.sendMessage(Magic.c("§cمفيش دعوة اندماج معلقة عندك."));
            return;
        }
        if (System.currentTimeMillis() > invite.expiresAt()) {
            fusionInvites.remove(partner.getUniqueId());
            partner.sendMessage(Magic.c("§cدعوة الاندماج انتهت."));
            return;
        }
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter == null || !inviter.isOnline() || !hasElement(partner)) {
            fusionInvites.remove(partner.getUniqueId());
            partner.sendMessage(Magic.c("§cصاحب الدعوة لم يعد متاحًا."));
            return;
        }
        Element firstElement = magic.getEl(inviter);
        Element secondElement = magic.getEl(partner);
        if (firstElement == null || secondElement == null || firstElement == secondElement
                || !inviter.getWorld().equals(partner.getWorld())
                || inviter.getLocation().distanceSquared(partner.getLocation()) > 100
                || magic.levelOf(inviter) < 20 || magic.levelOf(partner) < 20) {
            fusionInvites.remove(partner.getUniqueId());
            partner.sendMessage(Magic.c("§cشروط الاندماج لم تعد متحققة؛ لازم تكونوا قريبين وبقوتين مختلفتين ومن لفل 20."));
            inviter.sendMessage(Magic.c("§cتعذّر الاندماج لأن شروطه لم تعد متحققة."));
            return;
        }
        long now = System.currentTimeMillis();
        String firstCooldown = "fusion:" + inviter.getUniqueId();
        String secondCooldown = "fusion:" + partner.getUniqueId();
        long ready = Math.max(cooldowns.getOrDefault(firstCooldown, 0L), cooldowns.getOrDefault(secondCooldown, 0L));
        if (ready > now) {
            partner.sendMessage(Magic.c("§cالاندماج جاهز بعد " + ((ready - now) / 1000 + 1) + " ثانية."));
            return;
        }
        double cost = plugin.getConfig().getDouble("mana.fusion", 38.0);
        if (!spendBoth(inviter, partner, cost)) return;
        long nextReady = now + plugin.getConfig().getInt("cooldowns.fusion", 90) * 1000L;
        cooldowns.put(firstCooldown, nextReady);
        cooldowns.put(secondCooldown, nextReady);
        fusionInvites.remove(partner.getUniqueId());
        fusionInvites.entrySet().removeIf(entry -> entry.getValue().inviter().equals(inviter.getUniqueId()));
        applyFusion(inviter, partner, firstElement, secondElement);
        addReputation(inviter, 15, "تناغم العناصر");
        addReputation(partner, 15, "تناغم العناصر");
        inviter.sendMessage(Magic.c("§d✦ اندماج " + firstElement.ar + " + " + secondElement.ar + " اكتمل!"));
        partner.sendMessage(Magic.c("§d✦ اندماج " + firstElement.ar + " + " + secondElement.ar + " اكتمل!"));
        noteCast(inviter);
        noteCast(partner);
    }

    private void declineFusion(Player player) {
        FusionInvite invite = fusionInvites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Magic.c("§7مفيش دعوة اندماج معلقة."));
            return;
        }
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) inviter.sendMessage(Magic.c("§c" + player.getName() + " رفض دعوة الاندماج."));
        player.sendMessage(Magic.c("§7رفضت دعوة الاندماج."));
    }

    private void applyFusion(Player first, Player second, Element a, Element b) {
        boolean bloom = (a == Element.WATER && b == Element.NATURE) || (b == Element.WATER && a == Element.NATURE);
        boolean steel = (a == Element.EARTH && b == Element.METAL) || (b == Element.EARTH && a == Element.METAL);
        boolean gale = (a == Element.FIRE && b == Element.WIND) || (b == Element.FIRE && a == Element.WIND);
        boolean eclipse = (a == Element.LIGHT && b == Element.SHADOW) || (b == Element.LIGHT && a == Element.SHADOW);
        boolean resonance = (a == Element.LIGHTNING && b == Element.SOUND) || (b == Element.LIGHTNING && a == Element.SOUND);
        for (Player player : List.of(first, second)) {
            player.getWorld().spawnParticle(a.particle, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.03);
            player.getWorld().spawnParticle(b.particle, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.03);
            magic.ring(player.getLocation(), a.particle, null, 3.0, 12);
            magic.ring(player.getLocation(), b.particle, null, 2.0, 12);
            if (bloom) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 1));
            } else if (steel) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300, 1));
            } else if (gale || resonance) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 1));
            } else if (eclipse) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0));
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.15f);
        }
    }

    void activateForbidden(Player player) {
        if (!hasElement(player)) return;
        int required = plugin.getConfig().getInt("requirements.forbidden-level", 75);
        if (magic.levelOf(player) < required || reputation(player) < plugin.getConfig().getInt("requirements.forbidden-reputation", 100)) {
            player.sendMessage(Magic.c("§cالقوة المحرمة تحتاج لفل " + required + " وسمعة 100 على الأقل."));
            return;
        }
        int tier = tier(player);
        if (!cooldownReady(player, "forbidden")) return;
        if (!consumeMana(player, "forbidden", tier)) return;
        startCooldown(player, "forbidden", plugin.getConfig().getInt("cooldowns.forbidden", 240));
        Element element = magic.getEl(player);
        long forbiddenTicks = plugin.getConfig().getLong("forbidden.duration-seconds", 14L) * 20L;
        long finishAt = System.currentTimeMillis() + forbiddenTicks * 50L;
        forbiddenUntil.put(player.getUniqueId(), finishAt);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) forbiddenTicks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) forbiddenTicks, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (int) (forbiddenTicks * 0.65), 1));
        showBurst(player, element, 6.0);
        player.sendTitle("§5✦ القوة المحرمة ✦", "§dقوة كبيرة بثمن من المانا", 5, 35, 10);
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                Long activeUntil = forbiddenUntil.get(player.getUniqueId());
                if (activeUntil == null || activeUntil != finishAt) return; // Reset or a newer cast invalidated this task.
                forbiddenUntil.remove(player.getUniqueId());
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
                player.sendMessage(Magic.c("§7انتهت القوة المحرمة؛ تحتاج طاقتك إلى الهدوء."));
            }
        }.runTaskLater(plugin, forbiddenTicks);
        noteCast(player);
    }

    private ItemStack createRelic(String id) {
        Material material = switch (id) {
            case "tide" -> Material.HEART_OF_THE_SEA;
            case "focus" -> Material.AMETHYST_SHARD;
            case "guardian" -> Material.ECHO_SHARD;
            default -> null;
        };
        if (material == null) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = switch (id) {
            case "tide" -> "§bأثر المد";
            case "focus" -> "§dأثر التركيز";
            default -> "§6أثر الحارس";
        };
        meta.displayName(Magic.c(name));
        meta.lore(List.of(Magic.c("§7أثر سحري قابل لإعادة الاستخدام"),
                Magic.c("§7كليك يمين لتفعيل تأثيره باستخدام المانا"),
                Magic.c("§8GawharaMagic Relic")));
        meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private void useRelic(Player player, String id) {
        if (!cooldownReady(player, "relic-" + id)) return;
        if (!consumeMana(player, "relic", 1)) return;
        startCooldown(player, "relic-" + id, plugin.getConfig().getInt("cooldowns.relic", 40));
        switch (id) {
            case "tide" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 180, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 0));
                magic.ring(player.getLocation(), Particle.SPLASH, null, 3.0, 10);
            }
            case "focus" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 240, 1));
                magic.spiral(player, Particle.END_ROD, 24);
            }
            case "guardian" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300, 1));
                magic.ring(player.getLocation(), Particle.TOTEM_OF_UNDYING, null, 3.0, 10);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        player.sendActionBar(Magic.c("§d✦ تم تفعيل الأثر السحري"));
        noteCast(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRelicUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(relicKey, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        useRelic(event.getPlayer(), id);
    }

    private void claimRelic(Player player, String id) {
        int requiredRep = switch (id) {
            case "tide" -> plugin.getConfig().getInt("relics.tide-reputation", 100);
            case "focus" -> plugin.getConfig().getInt("relics.focus-reputation", 200);
            case "guardian" -> plugin.getConfig().getInt("relics.guardian-reputation", 350);
            default -> -1;
        };
        if (requiredRep < 0) {
            player.sendMessage(Magic.c("§cاختار tide أو focus أو guardian."));
            return;
        }
        String claimPath = path(player) + ".claimedRelics." + id;
        if (data.getBoolean(claimPath, false)) {
            player.sendMessage(Magic.c("§cأنت حصلت على الأثر ده قبل كده."));
            return;
        }
        if (reputation(player) < requiredRep) {
            player.sendMessage(Magic.c("§cالأثر محتاج سمعة " + requiredRep + " على الأقل."));
            return;
        }
        ItemStack relic = createRelic(id);
        if (relic == null) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(relic);
        if (!overflow.isEmpty()) {
            player.sendMessage(Magic.c("§cفضّي مكان في الشنطة وبعدها حاول تاني."));
            return;
        }
        data.set(claimPath, true);
        changed();
        player.sendMessage(Magic.c("§d✦ حصلت على أثر نادر: " + id + "!"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
    }

    void showRelics(Player player) {
        player.sendMessage(Magic.c("§d✦ الآثار السحرية النادرة"));
        player.sendMessage(Magic.c("§bأثر المد §7- تجدد وتنفس تحت الماء | سمعة " + plugin.getConfig().getInt("relics.tide-reputation", 100)));
        player.sendMessage(Magic.c("§dأثر التركيز §7- سرعة وتسريع التعدين | سمعة " + plugin.getConfig().getInt("relics.focus-reputation", 200)));
        player.sendMessage(Magic.c("§6أثر الحارس §7- مقاومة وامتصاص | سمعة " + plugin.getConfig().getInt("relics.guardian-reputation", 350)));
        player.sendMessage(Magic.c("§eاكتسب أثرًا مرة واحدة: /gmagic relic claim <tide|focus|guardian>"));
    }

    // ------------------------------------------------------------ spirit companion
    private boolean isSpirit(Entity entity) {
        if (!(entity instanceof Allay) || !entity.getPersistentDataContainer().has(spiritOwnerKey, PersistentDataType.STRING)) return false;
        return true;
    }

    private void removeSpirit(UUID owner) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isSpirit(entity) && owner.toString().equals(
                        entity.getPersistentDataContainer().get(spiritOwnerKey, PersistentDataType.STRING))) {
                    entity.remove();
                }
            }
        }
        spiritEntities.remove(owner);
    }

    private void spawnSpirit(Player player, boolean announce) {
        removeSpirit(player.getUniqueId());
        Location location = player.getLocation().clone().add(0, 0.3, 1.2);
        Allay allay = (Allay) player.getWorld().spawnEntity(location, EntityType.ALLAY);
        allay.setCustomName("§d✦ روح " + (magic.getEl(player) == null ? "العنصر" : magic.getEl(player).ar));
        allay.setCustomNameVisible(true);
        allay.setInvulnerable(true);
        allay.setSilent(true);
        allay.setAI(false);
        allay.setGravity(false);
        allay.getPersistentDataContainer().set(spiritOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        spiritEntities.put(player.getUniqueId(), allay.getUniqueId());
        data.set(path(player) + ".spirit", true);
        changed();
        if (announce) player.sendMessage(Magic.c("§d✦ روح العنصر بقت رفيقتك. /gmagic spirit dismiss لإخفائها."));
    }

    private void tickCompanions() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        fusionInvites.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        guardUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        awakeningUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        int giftInterval = Math.max(15, plugin.getConfig().getInt("spirit.mana-interval-seconds", 60));
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = spiritEntities.get(player.getUniqueId());
            if (id == null && data.getBoolean(path(player) + ".spirit", false)) {
                spawnSpirit(player, false);
                continue;
            }
            if (id == null) continue;
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Allay allay) || allay.isDead()) {
                spawnSpirit(player, false);
                continue;
            }
            Location target = player.getLocation().clone().add(0.8, 0.8, 0.8);
            if (!allay.getWorld().equals(player.getWorld()) || allay.getLocation().distanceSquared(target) > 16) {
                allay.teleport(target);
            } else {
                Element element = magic.getEl(player);
                Particle particle = element == null ? Particle.END_ROD : element.particle;
                allay.getWorld().spawnParticle(particle, allay.getLocation(), 2, 0.12, 0.12, 0.12, 0.005);
            }

            Long nextGift = spiritGiftAt.get(player.getUniqueId());
            if (nextGift == null) {
                spiritGiftAt.put(player.getUniqueId(), now + giftInterval * 1000L);
            } else if (now >= nextGift) {
                SkillsUser user = auraUser(player);
                if (user != null && user.getMaxMana() > 0.0) {
                    double percent = plugin.getConfig().getDouble("spirit.mana-restore-percent", 2.5);
                    double gift = Math.max(1.0, Math.min(6.0, user.getMaxMana() * percent / 100.0));
                    double before = user.getMana();
                    user.setMana(Math.min(user.getMaxMana(), before + gift));
                    if (user.getMana() > before) {
                        player.sendActionBar(Magic.c("§d✦ روح العنصر أعادت " + Math.round(user.getMana() - before) + " مانا"));
                    }
                }
                spiritGiftAt.put(player.getUniqueId(), now + giftInterval * 1000L);
            }
        }
    }

    @EventHandler
    public void onSpiritDamage(EntityDamageEvent event) {
        if (isSpirit(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onSpiritJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastTravelLocation.put(player.getUniqueId(), player.getLocation().clone());
        if (data.getBoolean(path(player) + ".spirit", false)) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (player.isOnline() && data.getBoolean(path(player) + ".spirit", false)) spawnSpirit(player, false);
                }
            }.runTaskLater(plugin, 30L);
        }
    }

    @EventHandler
    public void onSpiritQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeSpirit(player.getUniqueId());
        spiritGiftAt.remove(player.getUniqueId());
        lastTravelLocation.remove(player.getUniqueId());
        save();
    }

    // ------------------------------------------------------------ bounty contracts and reputation
    @EventHandler(ignoreCancelled = true)
    public void onBountyMine(BlockBreakEvent event) {
        String type = data.getString(path(event.getPlayer()) + ".bounty.type", "");
        if ("mine".equals(type)) progressBounty(event.getPlayer(), 1.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBountyTravel(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player player = event.getPlayer();
        String base = path(player) + ".bounty";
        if (!"travel".equals(data.getString(base + ".type", ""))) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Location last = lastTravelLocation.put(player.getUniqueId(), event.getTo().clone());
        if (last == null || !last.getWorld().equals(event.getTo().getWorld())) return;
        double distance = Math.min(2.0, last.distance(event.getTo()));
        if (distance > 0.0) progressBounty(player, distance);
    }

    private void acceptBounty(Player player, String type) {
        String task = type.toLowerCase(Locale.ROOT);
        double target;
        String description;
        switch (task) {
            case "mine" -> { target = 25; description = "اكسر 25 بلوك"; }
            case "cast" -> { target = 8; description = "استخدم 8 قدرات سحرية"; }
            case "travel" -> { target = 250; description = "تحرك لمسافة 250 بلوك"; }
            default -> {
                player.sendMessage(Magic.c("§cاختار نوع العقد: mine أو cast أو travel."));
                return;
            }
        }
        String base = path(player) + ".bounty";
        if (data.contains(base + ".type") && !data.getBoolean(base + ".claimed", false)) {
            player.sendMessage(Magic.c("§cعندك عقد نشط بالفعل. استخدم /gmagic bounty لمتابعته."));
            return;
        }
        data.set(base + ".type", task);
        data.set(base + ".target", target);
        data.set(base + ".progress", 0.0);
        data.set(base + ".claimed", false);
        data.set(base + ".announced", false);
        changed();
        if ("travel".equals(task)) lastTravelLocation.put(player.getUniqueId(), player.getLocation().clone());
        player.sendMessage(Magic.c("§6✦ عقد جديد: §e" + description));
    }

    private void showBounty(Player player) {
        String base = path(player) + ".bounty";
        String type = data.getString(base + ".type");
        if (type == null || data.getBoolean(base + ".claimed", false)) {
            player.sendMessage(Magic.c("§6✦ مفيش عقد نشط. اختار واحد:"));
            player.sendMessage(Magic.c("§e/gmagic bounty accept mine §7- تكسير 25 بلوك"));
            player.sendMessage(Magic.c("§e/gmagic bounty accept cast §7- استخدام 8 قدرات"));
            player.sendMessage(Magic.c("§e/gmagic bounty accept travel §7- التحرك 250 بلوك"));
            return;
        }
        double progress = data.getDouble(base + ".progress", 0.0);
        double target = data.getDouble(base + ".target", 1.0);
        player.sendMessage(Magic.c("§6✦ عقدك الحالي: §e" + type + " §7| §f" + (int) progress + "/" + (int) target));
        if (progress >= target) player.sendMessage(Magic.c("§aاكتمل! استخدم /gmagic bounty claim."));
    }

    private void claimBounty(Player player) {
        String base = path(player) + ".bounty";
        if (!data.contains(base + ".type") || data.getBoolean(base + ".claimed", false)) {
            player.sendMessage(Magic.c("§cمفيش عقد جاهز للتحصيل."));
            return;
        }
        if (data.getDouble(base + ".progress", 0.0) < data.getDouble(base + ".target", 1.0)) {
            player.sendMessage(Magic.c("§cلسه ماكملتش العقد."));
            return;
        }
        data.set(base + ".claimed", true);
        changed();
        addReputation(player, plugin.getConfig().getInt("bounty.reputation-reward", 40), "إكمال عقد" );
        SkillsUser user = auraUser(player);
        if (user != null) {
            double reward = Math.min(20.0, Math.max(5.0, user.getMaxMana() * 0.10));
            user.setMana(Math.min(user.getMaxMana(), user.getMana() + reward));
            player.sendMessage(Magic.c("§bتمت استعادة " + (int) reward + " مانا من AuraSkills."));
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
    }

    private void showReputation(Player player) {
        int value = reputation(player);
        player.sendMessage(Magic.c("§d✦ السمعة العنصرية: " + value + " §7| " + reputationTitle(value)));
        player.sendMessage(Magic.c("§7ارفع سمعتك بإكمال العقود، واندماج العناصر، والاستيقاظ."));
        player.sendMessage(Magic.c("§7التحول النادر يحتاج لفل 80 وسمعة 150."));
    }

    // ------------------------------------------------------------ rare transformations
    private String transformationName(Element element) {
        return switch (element) {
            case FIRE -> "العنقاء المتوهجة";
            case WATER -> "روح الأعماق";
            case EARTH -> "العملاق الحجري";
            case WIND -> "طيف العاصفة";
            case LIGHTNING -> "قلب الرعد";
            case SHADOW -> "كسوف الظلال";
            case ICE -> "تاج الصقيع";
            case METAL -> "الحارس الفولاذي";
            case NATURE -> "روح الغابة";
            case LIGHT -> "شعاع الفجر";
            case SOUND -> "سيد الصدى";
            case CRYSTAL -> "التاج البلوري";
        };
    }

    private void activateTransformation(Player player) {
        if (!hasElement(player)) return;
        int requiredLevel = plugin.getConfig().getInt("requirements.transformation-level", 80);
        int requiredRep = plugin.getConfig().getInt("requirements.transformation-reputation", 150);
        String unlockPath = path(player) + ".rare-transform-unlocked";
        if (magic.levelOf(player) < requiredLevel || reputation(player) < requiredRep) {
            player.sendMessage(Magic.c("§cالتحول محتاج لفل " + requiredLevel + " وسمعة " + requiredRep + "."));
            return;
        }
        if (!data.getBoolean(unlockPath, false)) {
            data.set(unlockPath, true);
            changed();
            addReputation(player, 10, "اكتشاف التحول النادر");
            player.sendMessage(Magic.c("§d✦ اكتشفت تحول: §f" + transformationName(magic.getEl(player))));
        }
        int tier = tier(player);
        if (!cooldownReady(player, "transform")) return;
        if (!consumeMana(player, "transformation", tier)) return;
        startCooldown(player, "transform", plugin.getConfig().getInt("cooldowns.transformation", 180));
        Element element = magic.getEl(player);
        long durationTicks = plugin.getConfig().getLong("transformation.duration-seconds", 35L) * 20L;
        transformationUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationTicks * 50L);
        applyTransformationBuffs(player, element, 3, (int) durationTicks);
        showBurst(player, element, 5.5 + tier);
        player.sendTitle("§d✦ " + transformationName(element) + " ✦", element.color + "التحول النادر", 5, 45, 10);
        noteCast(player);
    }

    private void tickTransformEffects() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long end = transformationUntil.get(player.getUniqueId());
            if (end == null || end <= now) {
                if (end != null) {
                    transformationUntil.remove(player.getUniqueId());
                    player.sendMessage(Magic.c("§7انتهى التحول النادر."));
                }
                continue;
            }
            Element element = magic.getEl(player);
            if (element != null && Math.floorMod(now / 250L, 2) == 0) {
                player.getWorld().spawnParticle(element.particle, player.getLocation().add(0, 1, 0), 3,
                        0.25, 0.5, 0.25, 0.01);
            }
        }
    }

    // ------------------------------------------------------------ gestures
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpinGesture(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || player.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        event.setCancelled(true);
        activateSpin(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardGesture(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || player.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        event.setCancelled(true);
        activateGuard(player);
    }

    // ------------------------------------------------------------ reset support
    boolean isRelicItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(relicKey, PersistentDataType.STRING);
    }

    private int removeRelicsFrom(org.bukkit.inventory.Inventory inventory) {
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(relicKey, PersistentDataType.STRING)) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    int resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int removed = removeRelicsFrom(player.getInventory()) + removeRelicsFrom(player.getEnderChest());
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(relicKey, PersistentDataType.STRING)) {
                armor[i] = null;
                removed++;
                armorChanged = true;
            }
        }
        if (armorChanged) player.getInventory().setArmorContents(armor);
        ItemStack[] extra = player.getInventory().getExtraContents();
        boolean extraChanged = false;
        for (int i = 0; i < extra.length; i++) {
            ItemStack item = extra[i];
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(relicKey, PersistentDataType.STRING)) {
                extra[i] = null;
                removed++;
                extraChanged = true;
            }
        }
        if (extraChanged) player.getInventory().setExtraContents(extra);

        removeSpirit(uuid);
        spiritGiftAt.remove(uuid);
        guardUntil.remove(uuid);
        awakeningUntil.remove(uuid);
        forbiddenUntil.remove(uuid);
        transformationUntil.remove(uuid);
        lastTravelLocation.remove(uuid);
        cooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":") || key.startsWith("fusion:" + uuid));
        fusionInvites.remove(uuid);
        fusionInvites.entrySet().removeIf(entry -> entry.getValue().inviter().equals(uuid));
        data.set(path(player), null);
        changed();

        for (PotionEffectType type : List.of(
                PotionEffectType.JUMP_BOOST, PotionEffectType.NIGHT_VISION,
                PotionEffectType.INVISIBILITY, PotionEffectType.HASTE,
                PotionEffectType.WATER_BREATHING, PotionEffectType.SLOWNESS,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.STRENGTH,
                PotionEffectType.DOLPHINS_GRACE, PotionEffectType.SATURATION,
                PotionEffectType.CONDUIT_POWER)) {
            player.removePotionEffect(type);
        }
        save();
        return removed;
    }

    // ------------------------------------------------------------ command router
    boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        if (!(sender instanceof Player player)) {
            if (args[0].equalsIgnoreCase("relic") && args.length >= 4 && args[1].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("gawhara.magic.admin")) return true;
                Player target = Bukkit.getPlayerExact(args[2]);
                ItemStack relic = createRelic(args[3].toLowerCase(Locale.ROOT));
                if (target == null || relic == null) {
                    sender.sendMessage("Player or relic type not found. Use tide, focus, or guardian.");
                } else {
                    target.getInventory().addItem(relic);
                    sender.sendMessage("Relic given to " + target.getName());
                }
                return true;
            }
            return false;
        }

        String sub = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "s", "sp" -> "spin";
            case "u", "ult" -> "ultimate";
            case "g", "gd" -> "guard";
            case "a", "aw" -> "awaken";
            case "f", "fr" -> "forbidden";
            case "mn", "mna" -> "mana";
            case "rel", "rl" -> "relics";
            case "pet" -> "spirit";
            case "b", "bt" -> "bounty";
            case "rep" -> "reputation";
            case "tr" -> "transform";
            case "fu" -> "fusion";
            default -> args[0].toLowerCase(Locale.ROOT);
        };
        switch (sub) {
            case "spin" -> { activateSpin(player); return true; }
            case "ultimate" -> { activateUltimate(player); return true; }
            case "guard" -> { activateGuard(player); return true; }
            case "awaken", "awakening" -> { activateAwakening(player); return true; }
            case "fusion" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("accept")) acceptFusion(player);
                else if (args.length >= 2 && args[1].equalsIgnoreCase("decline")) declineFusion(player);
                else if (args.length >= 2) inviteFusion(player, args[1]);
                else player.sendMessage(Magic.c("§c/gmagic fusion <player> | /gmagic fusion accept | decline"));
                return true;
            }
            case "forbidden" -> { activateForbidden(player); return true; }
            case "mana" -> { showMana(player); return true; }
            case "relics" -> { showRelics(player); return true; }
            case "relic" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("claim")) {
                    claimRelic(player, args[2].toLowerCase(Locale.ROOT));
                } else if (args.length >= 4 && args[1].equalsIgnoreCase("give")) {
                    if (!player.hasPermission("gawhara.magic.admin")) {
                        player.sendMessage(Magic.c("§cمعندكش صلاحية لمنح الآثار."));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    ItemStack relic = createRelic(args[3].toLowerCase(Locale.ROOT));
                    if (target == null || relic == null) player.sendMessage(Magic.c("§c/gmagic relic give <player> <tide|focus|guardian>"));
                    else {
                        target.getInventory().addItem(relic);
                        player.sendMessage(Magic.c("§aتم منح الأثر إلى " + target.getName()));
                    }
                } else player.sendMessage(Magic.c("§c/gmagic relic claim <tide|focus|guardian> أو /gmagic relic give <player> <id>"));
                return true;
            }
            case "spirit" -> {
                String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "summon";
                if (action.equals("dismiss")) {
                    data.set(path(player) + ".spirit", false);
                    changed();
                    spiritGiftAt.remove(player.getUniqueId());
                    removeSpirit(player.getUniqueId());
                    player.sendMessage(Magic.c("§7روح العنصر رجعت إلى عالمها."));
                } else if (action.equals("summon")) {
                    if (!hasElement(player)) return true;
                    spawnSpirit(player, true);
                } else player.sendMessage(Magic.c("§c/gmagic spirit [summon|dismiss]"));
                return true;
            }
            case "bounty" -> {
                if (args.length == 1) { showBounty(player); return true; }
                if (args[1].equalsIgnoreCase("accept") && args.length >= 3) acceptBounty(player, args[2]);
                else if (args[1].equalsIgnoreCase("claim")) claimBounty(player);
                else showBounty(player);
                return true;
            }
            case "reputation", "rep" -> { showReputation(player); return true; }
            case "transform", "transformation" -> { activateTransformation(player); return true; }
            default -> { return false; }
        }
    }
}
