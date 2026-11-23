package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

import java.util.Optional;

/**
 * What the server remembers of a player's VR helmet: where their body is while they are in Mitofus (the VR helmet and the
 * spot they step out to), and where they last disconnected in Mitofus. Saved with the player, never synced.
 */
public record VrHelmetState(Optional<GlobalPos> vrhelmet, Optional<GlobalPos> exit, Optional<BlockPos> lastSave, float bodyYaw) {
    public static final VrHelmetState EMPTY = new VrHelmetState(Optional.empty(), Optional.empty(), Optional.empty());

    public VrHelmetState(Optional<GlobalPos> vrhelmet, Optional<GlobalPos> exit, Optional<BlockPos> lastSave) {
        this(vrhelmet, exit, lastSave, 0.0f);
    }

    public static final Codec<VrHelmetState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("vrhelmet").forGetter(VrHelmetState::vrhelmet),
            GlobalPos.CODEC.optionalFieldOf("exit").forGetter(VrHelmetState::exit),
            BlockPos.CODEC.optionalFieldOf("last_save").forGetter(VrHelmetState::lastSave),
            Codec.FLOAT.optionalFieldOf("body_yaw", 0.0f).forGetter(VrHelmetState::bodyYaw)
    ).apply(instance, VrHelmetState::new));

    /** Connected: the body stays in {@code vrhelmet}, and the player will step out at {@code exit}. */
    public VrHelmetState connected(GlobalPos vrhelmet, GlobalPos exit) {
        return new VrHelmetState(Optional.of(vrhelmet), Optional.of(exit), lastSave, bodyYaw);
    }

    /** The same state, remembering which way the body lies (it is put back the same way when the player logs in). */
    public VrHelmetState withBodyYaw(float yaw) {
        return new VrHelmetState(vrhelmet, exit, lastSave, yaw);
    }

    /** Disconnected in Mitofus at {@code at}: that is where the next connection starts. The body is back in the real world. */
    public VrHelmetState disconnected(BlockPos at) {
        return new VrHelmetState(Optional.empty(), Optional.empty(), Optional.of(at), 0.0f);
    }

    /** The body is gone from the VR helmet (the player died in Mitofus): no VR helmet, no exit; the last save is kept. */
    public VrHelmetState withoutBody() {
        return new VrHelmetState(Optional.empty(), Optional.empty(), lastSave, 0.0f);
    }

    /** Whether the player is connected, i.e. their body lies in a VR helmet. The single definition of "connected". */
    public boolean isConnected() {
        return vrhelmet.isPresent();
    }
}
