package gawhara.magic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Environmental attunement rules for the elemental schools.
 * Conditions are intentionally based on the caster's current surroundings.
 */
final class ElementAffinity {
    private ElementAffinity() {}

    static boolean isEmpowered(Player player, Element element) {
        if (player == null || element == null || !player.isOnline()) return false;
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return false;

        Block block = location.getBlock();
        String biome = block.getBiome().name();
        int y = location.getBlockY();
        int sea = world.getSeaLevel();

        return switch (element) {
            case FIRE -> world.getEnvironment() == World.Environment.NETHER
                    || isHotBiome(biome)
                    || nearby(player, 4, 2, ElementAffinity::isHeatSource);
            case WATER -> player.isInWater() || player.isUnderWater()
                    || (world.hasStorm() && !isColdBiome(biome) && isExposedToSky(location));
            case EARTH -> y <= sea - 5 && !player.isInWater() && !player.isUnderWater()
                    && world.getHighestBlockYAt(location) > y + 3;
            case WIND -> world.getEnvironment() == World.Environment.NORMAL
                    && location.getY() >= sea + 30;
            case LIGHTNING -> world.isThundering();
            case SHADOW -> block.getLightLevel() <= 4;
            case ICE -> isColdBiome(biome)
                    || nearby(player, 3, 2, ElementAffinity::isColdBlock);
            case METAL -> nearby(player, 4, 2, ElementAffinity::isMetalSource);
            case NATURE -> isNatureBiome(biome);
            case LIGHT -> isBrightEnvironment(location);
            case SOUND -> biome.equals("DEEP_DARK")
                    || nearby(player, 4, 3, ElementAffinity::isResonanceSource)
                    || (y < sea - 8 && world.getHighestBlockYAt(location) > y + 8
                        && block.getLightLevel() <= 8);
            case CRYSTAL -> nearby(player, 5, 3, ElementAffinity::isCrystalSource);
            // Shapeshift is driven by the collected forms, not a single natural element.
            case SHAPESHIFT -> false;
            case MIND_CONTROL -> block.getLightLevel() <= 7 || biome.contains("DARK") || biome.contains("DEEP");
            case TELEKINESIS -> nearby(player, 5, 3, m -> m.isSolid() && m.isBlock());
        };
    }

    static String requirement(Element element) {
        return switch (element) {
            case FIRE -> "النذر أو البيئات الحارة أو قرب النار والحمم";
            case WATER -> "تحت الماء أو أثناء المطر تحت السماء";
            case EARTH -> "تحت سطح الأرض وفي الكهوف";
            case WIND -> "على ارتفاع 30 بلوك أو أكثر فوق مستوى البحر";
            case LIGHTNING -> "أثناء العاصفة الرعدية";
            case SHADOW -> "في الأماكن شديدة الظلام (إضاءة 4 أو أقل)";
            case ICE -> "المناطق الثلجية والباردة أو قرب الجليد والثلج";
            case METAL -> "بالقرب من خامات وكتل المعادن أو أدوات الحدادة";
            case NATURE -> "الغابات والأدغال والتايغا والمستنقعات والكهوف الخضراء";
            case LIGHT -> "أي مكان شديد الإضاءة (مستوى إضاءة 12 أو أكثر)";
            case SOUND -> "الكهوف العميقة أو قرب السكالك وبلوكات النوت";
            case CRYSTAL -> "بالقرب من الجيود وبلورات الأميثيست";
            case SHAPESHIFT -> "القوة تعتمد على الشكل الذي تتحول إليه";
            case MIND_CONTROL -> "الأماكن الهادئة والمظلمة والكهوف العميقة";
            case TELEKINESIS -> "بالقرب من الكتل الصلبة والصخور والمعادن";
        };
    }

    private static boolean isExposedToSky(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        int highest = world.getHighestBlockYAt(location);
        return location.getBlockY() >= highest - 1
                && location.getBlock().getLightFromSky() > 0;
    }

    private static boolean isBrightEnvironment(Location location) {
        return location.getBlock().getLightLevel() >= 12;
    }

    private static boolean isHotBiome(String biome) {
        return biome.contains("DESERT") || biome.contains("SAVANNA")
                || biome.contains("BADLANDS") || biome.contains("NETHER")
                || biome.equals("BASALT_DELTAS") || biome.equals("SOUL_SAND_VALLEY")
                || biome.equals("CRIMSON_FOREST") || biome.equals("WARPED_FOREST");
    }

    private static boolean isColdBiome(String biome) {
        return biome.contains("SNOW") || biome.contains("FROZEN")
                || biome.equals("ICE_SPIKES") || biome.equals("GROVE")
                || biome.contains("PEAKS");
    }

    private static boolean isNatureBiome(String biome) {
        return biome.contains("FOREST") || biome.contains("JUNGLE")
                || biome.contains("TAIGA") || biome.contains("SWAMP")
                || biome.contains("GROVE") || biome.contains("MEADOW")
                || biome.contains("MUSHROOM") || biome.contains("LUSH_CAVES")
                || biome.contains("CHERRY");
    }

    private static boolean isHeatSource(Material material) {
        String name = material.name();
        return name.equals("LAVA") || name.equals("FIRE") || name.equals("SOUL_FIRE")
                || name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")
                || name.equals("MAGMA_BLOCK");
    }

    private static boolean isColdBlock(Material material) {
        String name = material.name();
        return name.equals("ICE") || name.equals("PACKED_ICE") || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE") || name.equals("SNOW")
                || name.equals("SNOW_BLOCK") || name.equals("POWDER_SNOW");
    }

    private static boolean isMetalSource(Material material) {
        String name = material.name();
        boolean ore = name.endsWith("_ORE") && (name.contains("IRON")
                || name.contains("COPPER") || name.contains("GOLD"));
        boolean block = (name.contains("IRON") || name.contains("COPPER") || name.contains("GOLD"))
                && (name.endsWith("_BLOCK") || name.contains("EXPOSED_COPPER")
                    || name.contains("WEATHERED_COPPER") || name.contains("OXIDIZED_COPPER"));
        return ore || block || name.equals("ANVIL") || name.equals("CHIPPED_ANVIL")
                || name.equals("DAMAGED_ANVIL") || name.equals("BLAST_FURNACE")
                || name.equals("SMITHING_TABLE");
    }

    private static boolean isResonanceSource(Material material) {
        String name = material.name();
        return name.equals("NOTE_BLOCK") || name.contains("SCULK")
                || name.equals("AMETHYST_BLOCK") || name.equals("CALIBRATED_SCULK_SENSOR");
    }

    private static boolean isCrystalSource(Material material) {
        String name = material.name();
        return name.contains("AMETHYST") || name.equals("BUDDING_AMETHYST");
    }

    private static boolean nearby(Player player, int horizontal, int vertical,
                                  java.util.function.Predicate<Material> predicate) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int minY = Math.max(world.getMinHeight(), y - vertical);
        int maxY = Math.min(world.getMaxHeight() - 1, y + vertical);
        for (int bx = x - horizontal; bx <= x + horizontal; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = z - horizontal; bz <= z + horizontal; bz++) {
                    if (predicate.test(world.getBlockAt(bx, by, bz).getType())) return true;
                }
            }
        }
        return false;
    }
}
