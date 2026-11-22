package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a passive bonus changes. Each kind is applied in one place, see {@link ActiveTraits}. */
public enum BonusType implements StringRepresentable {
    /** Flat vanilla armor. */
    ARMOR("armor"),
    /** Flat vanilla knockback resistance (0 to 1). */
    KNOCKBACK_RESISTANCE("knockback_resistance"),
    /** Extra damage dealt, as a fraction (0.1 = +10 %), optionally only with a tagged weapon or at night. */
    DAMAGE("damage"),
    /** Flat extra maximum mana. */
    MAX_MANA("max_mana"),
    /** Extra mana regeneration, as a fraction of the normal rate (0.25 = +25 %). */
    MANA_REGEN("mana_regen");

    public static final Codec<BonusType> CODEC = StringRepresentable.fromEnum(BonusType::values);

    private final String serializedName;

    BonusType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
