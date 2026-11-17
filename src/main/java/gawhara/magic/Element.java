package gawhara.magic;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum Element {
    FIRE("النار", "§c", Material.BLAZE_POWDER, Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT,
            "موجة النار", "بتحرق أعداءك وتدفعهم بعيد بموجة نار"),
    WATER("الماء", "§b", Material.HEART_OF_THE_SEA, Particle.SPLASH, Sound.ENTITY_PLAYER_SPLASH,
            "موجة الشفاء", "بتشفيك وبتبطّئ أعداءك بموجة مية"),
    EARTH("الأرض", "§2", Material.GRASS_BLOCK, Particle.HAPPY_VILLAGER, Sound.BLOCK_ANVIL_LAND,
            "ضربة الأرض", "بتدرّعك وبترمي أعداءك في الهوا"),
    WIND("الريح", "§f", Material.FEATHER, Particle.CLOUD, Sound.ENTITY_ENDER_DRAGON_FLAP,
            "اندفاعة الريح", "بتندفع لقدّام بسرعة وبتزق اللي في طريقك"),
    LIGHTNING("البرق", "§e", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
            "صاعقة", "بتنزّل صواعق على أقرب أعدائك"),
    SHADOW("الظلام", "§5", Material.ECHO_SHARD, Particle.PORTAL, Sound.ENTITY_WITHER_SHOOT,
            "قفزة الظل", "بتتنقل في الضلمة وبتعمي وتسمّم اللي حواليك");

    public final String ar;
    public final String color;
    public final Material icon;
    public final Particle particle;
    public final Sound sound;
    public final String ability;
    public final String desc;

    Element(String ar, String color, Material icon, Particle particle, Sound sound, String ability, String desc) {
        this.ar = ar;
        this.color = color;
        this.icon = icon;
        this.particle = particle;
        this.sound = sound;
        this.ability = ability;
        this.desc = desc;
    }
}
