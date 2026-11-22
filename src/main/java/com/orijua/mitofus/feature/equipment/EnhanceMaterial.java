package com.orijua.mitofus.feature.equipment;

/** The materials that enhance equipment, with the kind of piece each one accepts. No Minecraft classes: pure rules. */
public enum EnhanceMaterial {
    WEAPON_STONE(Target.WEAPON, Kind.STONE),
    ARMOR_STONE(Target.ARMOR, Kind.STONE),
    BLESSED_WEAPON_STONE(Target.WEAPON, Kind.BLESSED_STONE),
    BLESSED_ARMOR_STONE(Target.ARMOR, Kind.BLESSED_STONE),
    BLESSED_SCROLL(Target.ANY, Kind.BLESSED_SCROLL),
    ANCIENT_WEAPON_SCROLL(Target.WEAPON, Kind.ANCIENT_SCROLL),
    ANCIENT_ARMOR_SCROLL(Target.ARMOR, Kind.ANCIENT_SCROLL);

    /** What kind of piece a material works on. */
    public enum Target { WEAPON, ARMOR, ANY }

    /** How a material behaves when it is used. */
    public enum Kind { STONE, BLESSED_STONE, BLESSED_SCROLL, ANCIENT_SCROLL }

    private final Target target;
    private final Kind kind;

    EnhanceMaterial(Target target, Kind kind) {
        this.target = target;
        this.kind = kind;
    }

    public Target target() {
        return target;
    }

    public Kind kind() {
        return kind;
    }

    /** The normal stone for a kind of piece. */
    public static EnhanceMaterial normalStoneFor(EnhanceRules.PieceType type) {
        return type == EnhanceRules.PieceType.ARMOR ? ARMOR_STONE : WEAPON_STONE;
    }
}
