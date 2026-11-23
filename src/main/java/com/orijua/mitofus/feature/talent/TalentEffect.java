package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.Codec;

/**
 * What owning a talent node gives. The JSON "type" field picks the implementation (see {@link TalentEffectType}); add a
 * new kind by writing a record and listing it in the enum.
 */
public interface TalentEffect {
    Codec<TalentEffect> CODEC = TalentEffectType.CODEC.dispatch("type", TalentEffect::type, TalentEffectType::codec);

    TalentEffectType type();
}
