package com.orijua.mitofus.feature.effect;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;

import java.util.Optional;

/** Reads the global caps of the bonuses from data (see {@link BonusLimit}); a kind with no file has no cap. */
public final class BonusLimits {
    private BonusLimits() {
    }

    public static Optional<Double> cap(BonusType type) {
        return Definitions.get(ModDatapackRegistries.BONUS_LIMIT, Mitofus.id(type.getSerializedName())).map(BonusLimit::cap);
    }

    /** {@code value} under the cap of {@code type}. */
    public static double capped(BonusType type, double value) {
        return TraitRules.capped(value, cap(type));
    }
}
