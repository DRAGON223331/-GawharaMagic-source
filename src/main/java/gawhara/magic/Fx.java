package gawhara.magic;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Signature visual effects: every element has its own shape AND its own particle texture.
 *
 * FIRE      rising twin flame helix + ember ring
 * WATER     two orbiting water ribbons + bubbles
 * EARTH     ground crack line with erupting rock shards + stomp ring
 * WIND      widening tornado cone + expanding gust rings
 * LIGHTNING jagged sky-to-player bolts + crackling arcs
 * SHADOW    collapsing dark ring + orbiting shadow orbs + soul smoke
 * ICE       rotating six-armed snowflake on the ground + falling snow
 * METAL     counter-rotating steel rings that clamp in, then spark outward
 * NATURE    growing vines that bloom into flowers
 * LIGHT     sun-star rays + rotating halo
 * SOUND     concentric sound-wave rings travelling forward
 * CRYSTAL   rotating glowing octahedron
 */
final class Fx {
    private Fx() {}

    // ------------------------------------------------------------ primitives
    private static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    private static Particle.DustTransition fade(int from, int to, float size) {
        return new Particle.DustTransition(Color.fromRGB(from), Color.fromRGB(to), size);
    }

    private static void dot(World w, Location l, Particle p, int n, double spread, double speed, Object data) {
        if (data == null) w.spawnParticle(p, l, n, spread, spread, spread, speed);
        else w.spawnParticle(p, l, n, spread, spread, spread, speed, data);
    }

    private static void run(Plugin plugin, Player p, int ticks, IntConsumer body) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t >= ticks) { cancel(); return; }
                body.accept(t++);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void circle(Location c, double r, int pts, double phase, Consumer<Location> f) {
        for (int i = 0; i < pts; i++) {
            double a = phase + i * (Math.PI * 2 / pts);
            f.accept(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
    }

    private static void line(Location a, Location b, double step, Consumer<Location> f) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) { f.accept(a.clone()); return; }
        d.multiply(1.0 / len);
        for (double s = 0; s <= len; s += step) f.accept(a.clone().add(d.clone().multiply(s)));
    }

    private static double rnd() { return ThreadLocalRandom.current().nextDouble(); }

    /** The element's particle "paint": a small puff with that element's signature look. */
    static void tex(World w, Location l, Element el, int n, double s) {
        switch (el) {
            case FIRE -> {
                dot(w, l, Particle.FLAME, n, s, 0.012, null);
                if (rnd() < 0.25) dot(w, l, Particle.LAVA, 1, s, 0, null);
            }
            case WATER -> {
                dot(w, l, Particle.SPLASH, n, s, 0.02, null);
                dot(w, l, Particle.DUST, 1, s, 0, dust(0x2E86FF, 1.1f));
            }
            case EARTH -> {
                dot(w, l, Particle.BLOCK, n, s, 0.05, Material.DIRT.createBlockData());
                if (rnd() < 0.4) dot(w, l, Particle.BLOCK, 1, s, 0.05, Material.STONE.createBlockData());
            }
            case WIND -> {
                dot(w, l, Particle.CLOUD, n, s, 0.02, null);
                if (rnd() < 0.2) dot(w, l, Particle.POOF, 1, s, 0.02, null);
            }
            case LIGHTNING -> {
                dot(w, l, Particle.ELECTRIC_SPARK, n, s, 0.1, null);
                dot(w, l, Particle.DUST, 1, s, 0, dust(0xFFE600, 1.0f));
            }
            case SHADOW -> {
                dot(w, l, Particle.DUST, n, s, 0, dust(0x2B0A3D, 1.5f));
                if (rnd() < 0.3) dot(w, l, Particle.SQUID_INK, 1, s, 0.01, null);
            }
            case ICE -> {
                dot(w, l, Particle.SNOWFLAKE, n, s, 0.01, null);
                dot(w, l, Particle.DUST, 1, s, 0, dust(0xA8E8FF, 1.0f));
            }
            case METAL -> {
                dot(w, l, Particle.CRIT, n, s, 0.05, null);
                dot(w, l, Particle.DUST, 1, s, 0, dust(0xC9CED6, 1.2f));
            }
            case NATURE -> {
                dot(w, l, Particle.DUST, n, s, 0, dust(0x2FBF3A, 1.1f));
                if (rnd() < 0.35) dot(w, l, Particle.HAPPY_VILLAGER, 1, s, 0, null);
            }
            case LIGHT -> {
                dot(w, l, Particle.END_ROD, n, s, 0.01, null);
                dot(w, l, Particle.DUST_COLOR_TRANSITION, 1, s, 0, fade(0xFFD84A, 0xFFFFFF, 1.2f));
            }
            case SOUND -> {
                dot(w, l, Particle.NOTE, 1, s, 1, null);
                dot(w, l, Particle.DUST, n, s, 0, dust(0xE05CFF, 1.1f));
            }
            case CRYSTAL -> {
                dot(w, l, Particle.DUST_COLOR_TRANSITION, n, s, 0, fade(0xB06CFF, 0x4FF3FF, 1.2f));
                if (rnd() < 0.3) dot(w, l, Particle.END_ROD, 1, s, 0.01, null);
            }
            case SHAPESHIFT -> {
                dot(w, l, Particle.PORTAL, n, s, 0.15, null);
                if (rnd() < 0.35) dot(w, l, Particle.TOTEM_OF_UNDYING, 1, s, 0.03, null);
            }
        }
    }

    // ------------------------------------------------------------ main cast effect
    /** Big signature effect for a base move / weapon skill. Runs ~1 second. */
    static void cast(Plugin plugin, Player p, Element el, int tier) {
        World w = p.getWorld();
        Location o = p.getLocation().clone();
        Vector dir = p.getLocation().getDirection().clone().setY(0);
        if (dir.lengthSquared() < 0.001) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Vector flat = dir;
        final double sc = 1.0 + (Math.max(1, tier) - 1) * 0.12;

        switch (el) {
            case FIRE -> run(plugin, p, 16, t -> {
                Location c = p.getLocation();
                double h = t * 0.14;
                double r = Math.max(0.35, 1.15 - t * 0.04) * sc;
                for (int k = 0; k < 2; k++) {
                    double a = t * 0.6 + k * Math.PI;
                    tex(w, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), el, 2, 0.04);
                }
                if (t == 0) {
                    circle(o.clone().add(0, 0.1, 0), 2.2 * sc, 28, 0, l -> dot(w, l, Particle.FLAME, 2, 0.05, 0.03, null));
                    dot(w, o.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 8, 0.4, 0.02, null);
                }
                if (t == 12 && tier >= 4) dot(w, c.clone().add(0, 2.2, 0), Particle.SOUL_FIRE_FLAME, 14, 0.3, 0.04, null);
            });
            case WATER -> run(plugin, p, 20, t -> {
                Location c = p.getLocation();
                for (int k = 0; k < 2; k++) {
                    for (int j = 0; j < 6; j++) {
                        double a = t * 0.5 + k * Math.PI - j * 0.2;
                        double y = 1.0 + Math.sin(t * 0.35 - j * 0.2 + k * Math.PI) * 0.55;
                        Location l = c.clone().add(Math.cos(a) * 1.3 * sc, y, Math.sin(a) * 1.3 * sc);
                        tex(w, l, el, Math.max(1, 3 - j / 2), 0.03);
                    }
                }
                dot(w, c.clone().add(0, 0.2, 0), Particle.BUBBLE_POP, 2, 0.5, 0.02, null);
                if (t == 0) circle(o.clone().add(0, 0.1, 0), 2.0 * sc, 24, 0, l -> dot(w, l, Particle.SPLASH, 3, 0.05, 0.05, null));
            });
            case EARTH -> {
                circle(o.clone().add(0, 0.1, 0), 2.4 * sc, 26, 0, l -> tex(w, l, el, 3, 0.1));
                run(plugin, p, 12, t -> {
                    for (int i = Math.max(1, t - 1); i <= t + 1; i++) {
                        Location l = o.clone().add(flat.clone().multiply(i * 0.9)).add(0, 0.15, 0);
                        tex(w, l, el, 6, 0.18);
                        dot(w, l.clone().add(0, 0.3 + rnd() * 0.5, 0), Particle.BLOCK, 3, 0.12, 0.12,
                                Material.COBBLESTONE.createBlockData());
                    }
                });
            }
            case WIND -> run(plugin, p, 18, t -> {
                Location c = p.getLocation();
                double h = t / 18.0 * 3.2;
                double r = (0.35 + h * 0.38) * sc;
                for (int k = 0; k < 3; k++) {
                    double a = t * 0.85 + k * (Math.PI * 2 / 3);
                    tex(w, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), el, 1, 0.02);
                }
                if (t % 5 == 0) circle(c.clone().add(0, 0.2, 0), (1.0 + t * 0.16) * sc, 18, 0,
                        l -> dot(w, l, Particle.CLOUD, 1, 0.02, 0.03, null));
            });
            case LIGHTNING -> run(plugin, p, 10, t -> {
                if (t % 3 != 0) return;
                Location c = p.getLocation();
                bolt(w, c.clone().add((rnd() - 0.5) * 1.5, 10 + rnd() * 3, (rnd() - 0.5) * 1.5), c.clone().add(0, 0.1, 0));
                for (int k = 0; k < 3; k++) {
                    Location tip = c.clone().add((rnd() - 0.5) * 5 * sc, 0.2 + rnd() * 1.5, (rnd() - 0.5) * 5 * sc);
                    line(c.clone().add(0, 1, 0), tip, 0.4, l -> dot(w, l, Particle.ELECTRIC_SPARK, 1, 0.03, 0.02, null));
                }
            });
            case SHADOW -> run(plugin, p, 14, t -> {
                Location c = p.getLocation();
                double r = Math.max(0.2, (3.4 - t * 0.25)) * sc;
                circle(c.clone().add(0, 0.3, 0), r, 22, t * 0.3, l -> tex(w, l, el, 1, 0.03));
                for (int k = 0; k < 3; k++) {
                    double a = -t * 0.4 + k * (Math.PI * 2 / 3);
                    dot(w, c.clone().add(Math.cos(a) * 0.9, 1.5 + Math.sin(t * 0.3 + k) * 0.3, Math.sin(a) * 0.9),
                            Particle.DUST, 2, 0.05, 0, dust(0x7A1FC4, 1.6f));
                }
                if (t == 13) {
                    dot(w, c.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 14, 0.5, 0.03, null);
                    dot(w, c.clone().add(0, 1, 0), Particle.SCULK_SOUL, 8, 0.4, 0.03, null);
                }
            });
            case ICE -> run(plugin, p, 20, t -> {
                if (t % 2 != 0) return;
                Location c = o.clone().add(0, 0.12, 0);
                double rot = t * 0.06;
                for (int arm = 0; arm < 6; arm++) {
                    double a = rot + arm * (Math.PI / 3);
                    Vector ad = new Vector(Math.cos(a), 0, Math.sin(a));
                    Vector perp = new Vector(-ad.getZ(), 0, ad.getX());
                    double len = 3.0 * sc;
                    for (double d = 0.3; d <= len; d += 0.45) {
                        tex(w, c.clone().add(ad.clone().multiply(d)), el, 1, 0.02);
                        if (d > len * 0.5 && d < len * 0.85) {
                            for (int sgn = -1; sgn <= 1; sgn += 2) {
                                double bl = (d - len * 0.45) * 0.7;
                                tex(w, c.clone().add(ad.clone().multiply(d)).add(perp.clone().multiply(sgn * bl)), el, 1, 0.02);
                            }
                        }
                    }
                }
                dot(w, p.getLocation().add(0, 3, 0), Particle.SNOWFLAKE, 10, 1.2, 0.02, null);
            });
            case METAL -> run(plugin, p, 16, t -> {
                Location c = p.getLocation();
                double r = (t < 10 ? 2.4 - t * 0.14 : 1.0) * sc;
                for (int k = 0; k < 3; k++) {
                    double dirSign = (k % 2 == 0) ? 1 : -1;
                    circle(c.clone().add(0, 0.35 + k * 0.7, 0), r, 12, dirSign * t * 0.5, l -> tex(w, l, el, 1, 0.02));
                }
                if (t == 10) {
                    dot(w, c.clone().add(0, 1, 0), Particle.FIREWORK, 24, 0.9, 0.12, null);
                    dot(w, c.clone().add(0, 1, 0), Particle.CRIT, 20, 1.0, 0.3, null);
                }
            });
            case NATURE -> run(plugin, p, 18, t -> {
                double h = Math.min(2.6, t * 0.15);
                for (int v = 0; v < 4; v++) {
                    double base = v * (Math.PI / 2) + 0.4;
                    Location anchor = o.clone().add(Math.cos(base) * 1.5 * sc, 0, Math.sin(base) * 1.5 * sc);
                    double a = t * 0.5 + v;
                    Location l = anchor.clone().add(Math.cos(a) * 0.3, h, Math.sin(a) * 0.3);
                    tex(w, l, el, 2, 0.03);
                    if (t == 17) {
                        int rgb = (v % 2 == 0) ? 0xFF7AC8 : 0xFFE066;
                        dot(w, l.clone().add(0, 0.2, 0), Particle.DUST, 8, 0.2, 0, dust(rgb, 1.5f));
                        dot(w, l, Particle.CHERRY_LEAVES, 4, 0.3, 0.02, null);
                    }
                }
            });
            case LIGHT -> run(plugin, p, 14, t -> {
                Location c = p.getLocation().add(0, 1, 0);
                double len = Math.min(4.0, t * 0.35) * sc;
                for (int i = 0; i < 12; i++) {
                    double a = i * (Math.PI / 4);
                    double up = (i >= 8) ? 0.7 : 0.0;
                    Vector rayDir = (i < 8)
                            ? new Vector(Math.cos(a), 0, Math.sin(a))
                            : new Vector(Math.cos(a), up, Math.sin(a)).normalize();
                    tex(w, c.clone().add(rayDir.clone().multiply(len)), el, 1, 0.03);
                    if (t % 2 == 0) dot(w, c.clone().add(rayDir.clone().multiply(len * 0.5)),
                            Particle.DUST, 1, 0.02, 0, dust(0xFFD84A, 1.0f));
                }
                circle(p.getLocation().add(0, 2.4, 0), 0.6, 10, t * 0.5, l -> dot(w, l, Particle.END_ROD, 1, 0, 0, null));
            });
            case SOUND -> {
                final Vector look = p.getLocation().getDirection().clone().normalize();
                Vector rt = look.clone().crossProduct(new Vector(0, 1, 0));
                if (rt.lengthSquared() < 1e-4) rt = new Vector(1, 0, 0);
                final Vector right = rt.normalize();
                final Vector up = right.clone().crossProduct(look).normalize();
                run(plugin, p, 16, t -> {
                    Location eye = p.getEyeLocation();
                    for (int k = 0; k < 3; k++) {
                        int age = t - k * 3;
                        if (age < 0) continue;
                        double dist = 1.0 + age * 0.5;
                        double rad = (0.4 + age * 0.14) * sc;
                        Location center = eye.clone().add(look.clone().multiply(dist));
                        for (int i = 0; i < 16; i++) {
                            double a = i * (Math.PI * 2 / 16);
                            Location l = center.clone()
                                    .add(right.clone().multiply(Math.cos(a) * rad))
                                    .add(up.clone().multiply(Math.sin(a) * rad));
                            if (i % 4 == 0) tex(w, l, el, 1, 0.01);
                            else dot(w, l, Particle.DUST, 1, 0.01, 0, dust(0xE05CFF, 1.0f));
                        }
                    }
                    if (t == 0 && tier >= 3) dot(w, eye.clone().add(look.clone().multiply(2)), Particle.SONIC_BOOM, 1, 0, 0, null);
                });
            }
            case CRYSTAL -> run(plugin, p, 20, t -> {
                if (t % 2 != 0) return;
                Location c = p.getLocation().add(0, 1.1, 0);
                double r = (1.0 + Math.min(t, 8) * 0.09) * sc;
                double rot = t * 0.15;
                Vector[] v = new Vector[6];
                double[][] base = {{1, 0, 0}, {-1, 0, 0}, {0, 1.3, 0}, {0, -1.3, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int i = 0; i < 6; i++) {
                    double x = base[i][0] * r, y = base[i][1] * r, z = base[i][2] * r;
                    double rx = x * Math.cos(rot) - z * Math.sin(rot);
                    double rz = x * Math.sin(rot) + z * Math.cos(rot);
                    v[i] = new Vector(rx, y, rz);
                }
                for (int i = 0; i < 6; i++) {
                    for (int j = i + 1; j < 6; j++) {
                        if ((i ^ 1) == j) continue; // skip opposite vertices
                        line(c.clone().add(v[i]), c.clone().add(v[j]), 0.35, l -> tex(w, l, el, 1, 0.0));
                    }
                }
                for (int i = 0; i < 6; i++) dot(w, c.clone().add(v[i]), Particle.END_ROD, 1, 0.0, 0, null);
            });
            case SHAPESHIFT -> run(plugin, p, 18, t -> {
                Location center = p.getLocation().add(0, 1, 0);
                double radius = (0.5 + t * 0.08) * sc;
                for (int i = 0; i < 3; i++) {
                    double angle = t * 0.48 + i * Math.PI * 2.0 / 3.0;
                    Location at = center.clone().add(Math.cos(angle) * radius, Math.sin(angle * 1.4) * 0.45, Math.sin(angle) * radius);
                    tex(w, at, el, 3, 0.04);
                }
                if (t == 0) circle(o.clone().add(0, 0.1, 0), 2.4 * sc, 28, 0,
                        l -> dot(w, l, Particle.TOTEM_OF_UNDYING, 2, 0.04, 0.02, null));
            });
        }
    }

    // ------------------------------------------------------------ jagged bolt
    private static void bolt(World w, Location top, Location bottom) {
        int seg = 14;
        Vector d = bottom.toVector().subtract(top.toVector());
        Location prev = top.clone();
        for (int i = 1; i <= seg; i++) {
            Location next = top.clone().add(d.clone().multiply(i / (double) seg));
            if (i < seg) next.add((rnd() - 0.5) * 1.6, 0, (rnd() - 0.5) * 1.6);
            line(prev, next, 0.45, l -> {
                dot(w, l, Particle.ELECTRIC_SPARK, 1, 0.02, 0.01, null);
                if (rnd() < 0.3) dot(w, l, Particle.DUST, 1, 0.02, 0, dust(0xFFF176, 1.3f));
            });
            prev = next;
        }
        dot(w, bottom, Particle.ELECTRIC_SPARK, 18, 0.5, 0.2, null);
    }

    // ------------------------------------------------------------ weapon slash
    /** Distinct weapon-skill sweep in front of the player (crescent in the element's own texture). */
    static void weapon(Plugin plugin, Player p, Element el, int tier) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().clone().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        final Vector rt = right;
        final Vector up = rt.clone().crossProduct(look).normalize();
        double reach = 2.4 + tier * 0.25;
        // each element tilts its crescent differently so slashes look different
        double tilt = switch (el) {
            case FIRE, LIGHT -> 0.0;
            case WATER, SOUND -> 0.5;
            case EARTH, METAL -> -0.9;
            case WIND, ICE -> 0.9;
            case LIGHTNING, CRYSTAL -> 1.3;
            case SHADOW, NATURE -> -0.4;
            case SHAPESHIFT -> 0.2;
        };
        run(plugin, p, 6, t -> {
            double a0 = -1.2 + t * 0.4;
            for (double a = a0; a < a0 + 0.4; a += 0.1) {
                double x = Math.sin(a) * reach;
                double y = Math.cos(a) * 0.2;
                Vector off = rt.clone().multiply(x * Math.cos(tilt)).add(up.clone().multiply(x * Math.sin(tilt) + y));
                Location l = eye.clone().add(look.clone().multiply(Math.cos(a) * reach * 0.7)).add(off);
                tex(w, l, el, 2, 0.04);
            }
        });
        tex(w, eye.clone().add(look.clone().multiply(reach * 0.8)), el, 10, 0.35);
    }

    // ------------------------------------------------------------ burst / ultimate / awakening
    /** Shockwave + pillar for spin, ultimate, awakening and transformation. */
    static void burst(Plugin plugin, Player p, Element el, double radius, boolean big) {
        World w = p.getWorld();
        Location o = p.getLocation().clone();
        run(plugin, p, 14, t -> {
            double r = radius * (t + 1) / 14.0;
            circle(o.clone().add(0, 0.15, 0), r, Math.max(16, (int) (r * 7)), t * 0.2, l -> tex(w, l, el, 1, 0.04));
            Location c = p.getLocation();
            double h = t * 0.35;
            for (int k = 0; k < 3; k++) {
                double a = t * 0.7 + k * (Math.PI * 2 / 3);
                tex(w, c.clone().add(Math.cos(a) * 0.8, h, Math.sin(a) * 0.8), el, 2, 0.05);
            }
        });
        if (big) cast(plugin, p, el, 5);
    }

    // ------------------------------------------------------------ guard shell
    static void guard(Plugin plugin, Player p, Element el) {
        World w = p.getWorld();
        run(plugin, p, 12, t -> {
            Location c = p.getLocation();
            for (int ring = 0; ring < 3; ring++) {
                double y = 0.3 + ring * 0.8;
                double r = 1.1 - Math.abs(ring - 1) * 0.25;
                circle(c.clone().add(0, y, 0), r, 10, t * 0.35 + ring, l -> tex(w, l, el, 1, 0.02));
            }
        });
    }

    // ------------------------------------------------------------ aura (called repeatedly)
    /** Light signature aura used while Awakening / Transformation are active. */
    static void aura(Player p, Element el, long tick) {
        World w = p.getWorld();
        Location c = p.getLocation();
        for (int i = 0; i < 8; i++) {
            double h = i * 0.28;
            double ang = tick * 0.9 + i * 0.7;
            for (int k = 0; k < 2; k++) {
                double a = ang + k * Math.PI;
                tex(w, c.clone().add(Math.cos(a) * 0.75, 0.1 + h, Math.sin(a) * 0.75), el, 1, 0.03);
            }
        }
    }
}
