package com.orijua.mitofus.feature.form;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Pure rules of how elemental types hit each other. Not used in play yet: it waits for mobs with a type (design:
 * mob types). Until then a form only adds its own type bonus.
 */
public final class ElementRules {
    private ElementRules() {
    }

    /**
     * The damage multiplier of an attack of {@code attacker} against a target of {@code defender}. A missing type (the
     * base form of a class, a mob with no type) is neutral: 1.0.
     */
    public static double multiplier(Optional<ResourceLocation> attackerId, Optional<ElementTypeDefinition> attacker,
                                    Optional<ResourceLocation> defenderId, Optional<ElementTypeDefinition> defender) {
        if (attackerId.isEmpty() || attacker.isEmpty() || defenderId.isEmpty() || defender.isEmpty()) {
            return 1.0;
        }
        boolean strong = attacker.get().strongAgainst().contains(defenderId.get());
        boolean weak = defender.get().strongAgainst().contains(attackerId.get());
        if (strong) {
            return 1.0 + attacker.get().advantage();
        }
        if (weak) {
            return 1.0 - attacker.get().disadvantage();
        }
        return 1.0;
    }
}
