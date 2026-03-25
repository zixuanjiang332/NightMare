package jdd.nightMare.Shop;

import org.bukkit.Material;

public enum ShopCategory {
    WEAPONS("武器", Material.GOLDEN_SWORD),
    BLOCKS("方块", Material.TERRACOTTA),
    MISC("杂项", Material.BOOK),
    HEALTH("生命提升", Material.APPLE);

    public final String name;
    public final Material icon;

    ShopCategory(String name, Material icon) { this.name = name; this.icon = icon; }
    public Material getIcon () { return icon; }
}