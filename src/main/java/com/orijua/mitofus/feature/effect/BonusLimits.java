package com.orijua.mitofus.feature.effect;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Reads the global caps of the bonuses from data (see {@link BonusLimit}); a kind with no file has no cap. */
public final class BonusLimits {
    /** The caps read from one registry; rebuilt when the registry changes (a reload), never on every hit. */
    private record Snapshot(Object source, Map<BonusType, Optional<Double>> caps) {
    }

    private static volatile Snapshot snapshot = new Snapshot(null, Map.of());

    private BonusLimits() {
    }

    public static Optional<Double> cap(BonusType type) {
        HolderLookup.RegistryLookup<BonusLimit> lookup = CommonHooks.resolveLookup(ModDatapackRegistries.BONUS_LIMIT);
        if (lookup == null) {
            return Optional.empty();
        }
        Snapshot current = snapshot;
        if (current.source() != lookup) {
            Map<BonusType, Optional<Double>> caps = new EnumMap<>(BonusType.class);
            for (BonusType each : BonusType.values()) {
                caps.put(each, lookup.get(ResourceKey.create(ModDatapackRegistries.BONUS_LIMIT, Mitofus.id(each.getSerializedName())))
                        .map(holder -> holder.value().cap()));
            }
            current = new Snapshot(lookup, caps);
            snapshot = current;
        }
        return current.caps().getOrDefault(type, Optional.empty());
    }

    /** {@code value} under the cap of {@code type}. */
    public static double capped(BonusType type, double value) {
        return TraitRules.capped(value, cap(type));
    }
}
