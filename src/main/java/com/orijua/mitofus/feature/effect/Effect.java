package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;

/**
 * An action, server side only. The JSON "type" field picks the implementation (see {@link EffectType});
 * add a new kind by writing a record and listing it in the enum. Run it through {@link EffectEngine}, never directly.
 */
public interface Effect {
    Codec<Effect> CODEC = EffectType.CODEC.dispatch("type", Effect::type, EffectType::codec);

    EffectType type();

    /** @return whether it did something (a cone that hits nobody did not): only that counts as a use for the skill mastery. */
    boolean apply(EffectContext context);
}
