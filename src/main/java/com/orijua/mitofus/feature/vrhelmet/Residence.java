package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Where a player is resident in Mitofus and comes back to life when they die there. Own design: the village chief of Covadonga or Eibar assigns it. {@code site} is only for the message,
 * {@code changedAt} is the game time of the last change (it starts the cooldown). Saved with the player, never synced.
 */
public record Residence(Optional<BlockPos> pos, String site, long changedAt) {
    public static final Residence NONE = new Residence(Optional.empty(), "", 0L);

    public static final Codec<Residence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(Residence::pos),
            Codec.STRING.optionalFieldOf("site", "").forGetter(Residence::site),
            Codec.LONG.optionalFieldOf("changed_at", 0L).forGetter(Residence::changedAt)
    ).apply(instance, Residence::new));

    public boolean has() {
        return pos.isPresent();
    }
}
