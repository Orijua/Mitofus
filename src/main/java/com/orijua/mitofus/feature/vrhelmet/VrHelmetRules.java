package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.feature.fatigue.FatigueRules.SessionModifiers;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * The pure rules of the VR helmet (own design: the helmet is how Mitofus is entered and left; the
 * waiting time and the spawn rules below are the mod's adaptation to Minecraft).
 */
public final class VrHelmetRules {
    /** Lying down and connecting takes 3 seconds (the default of {@code RuleBalance.connectSeconds}). */
    public static final int CONNECT_TICKS = 60;
    /** How far the player may get from the VR helmet while connecting before it is cancelled. */
    public static final double MAX_DISTANCE = 3.0;
    /**
     * How far the player may move (3D, measured from where the wait or countdown began, not tick to tick) and still
     * count as standing still.
     */
    public static final double STILL_TOLERANCE = 0.5;

    private VrHelmetRules() {
    }

    /** Where a connection lands. */
    public enum Source { RESIDENCE, LAST_SAVE, COVADONGA }

    public record Spawn(BlockPos pos, Source source) {
    }

    /**
     * Where the player appears in Mitofus: where they last disconnected, or, the first time (or if that is not known),
     * Covadonga's plaza. Empty if the world has neither (an old world without the Mitofus sites).
     */
    public static Optional<Spawn> spawn(Optional<BlockPos> lastSave, Optional<BlockPos> covadonga) {
        if (lastSave.isPresent()) {
            return Optional.of(new Spawn(lastSave.get(), Source.LAST_SAVE));
        }
        return covadonga.map(pos -> new Spawn(pos, Source.COVADONGA));
    }

    /**
     * Where the player reappears after dying in Mitofus: at their residence if they have one, otherwise as a connection does. Nobody has a residence
     * yet: the point is the seam where a chosen return point will plug in.
     */
    public static Optional<Spawn> deathSpawn(Optional<BlockPos> residence, Optional<BlockPos> lastSave, Optional<BlockPos> covadonga) {
        if (residence.isPresent()) {
            return Optional.of(new Spawn(residence.get(), Source.RESIDENCE));
        }
        return spawn(lastSave, covadonga);
    }

    /** Whether the saved state agrees with where the player is. */
    public enum Coherence { OK, CONNECTED_OUTSIDE, DISCONNECTED_INSIDE }

    /** OK: connected and in Mitofus, or disconnected and outside it. */
    public static Coherence coherence(VrHelmetState state, boolean inMitofus) {
        if (state.isConnected()) {
            return inMitofus ? Coherence.OK : Coherence.CONNECTED_OUTSIDE;
        }
        return inMitofus ? Coherence.DISCONNECTED_INSIDE : Coherence.OK;
    }

    /**
     * Whether a trip between dimensions is cancelled: the real world and Mitofus are separate worlds, and the only ways
     * across are the VR helmet (connect/disconnect) and the debug goto, which are {@code allowed}.
     */
    public static boolean crossingBlocked(boolean fromMitofus, boolean toMitofus, boolean allowed) {
        return fromMitofus != toMitofus && !allowed;
    }

    /** Whether the player is still close enough to the VR helmet to keep connecting. */
    public static boolean inReach(double distanceSquared) {
        double max = RuleBalance.current().maxDistance();
        return distanceSquared <= max * max;
    }

    /**
     * Whether the player stayed still: the squared 3D distance from where they started is within {@link #STILL_TOLERANCE}.
     * Turning the camera does not count as moving; NaN counts as moved.
     */
    public static boolean stayedStill(double distanceSquared) {
        double tolerance = RuleBalance.current().stillTolerance();
        return distanceSquared <= tolerance * tolerance;
    }

    /**
     * How the VR helmet of this tier changes the session. The basic VR helmet is exactly {@link SessionModifiers#NEUTRAL}; the
     * others stretch the session and speed up the recovery relative to the fatigue's numbers, and only the diamond one
     * carries rested seconds.
     */
    public static SessionModifiers modifiers(VrHelmetTier tier, FatigueBalance fatigue, VrHelmetBalance vrhelmet) {
        if (tier == VrHelmetTier.BASIC) {
            return SessionModifiers.NEUTRAL;
        }
        double durationMul = vrhelmet.sessionMinutes(tier, fatigue) / fatigue.sessionMinutes();
        double recoveryMul = fatigue.recoveryMinutes() / vrhelmet.recoveryMinutes(tier, fatigue);
        int restedSeconds = tier == VrHelmetTier.DIAMOND ? vrhelmet.restedMinutes() * 60 : 0;
        return new SessionModifiers(durationMul, recoveryMul, restedSeconds);
    }

    /**
     * The rested seconds a connection starts with: those of the modifiers if the energy reaches the minimum (compared as
     * floats, like the saved energy and {@code FatigueRules.canConnect}), otherwise none. It never adds to what the
     * player already had.
     */
    public static int restedOnConnect(SessionModifiers modifiers, double energy, VrHelmetBalance vrhelmet) {
        return (float) energy >= (float) vrhelmet.restedMinEnergy() ? modifiers.restedSeconds() : 0;
    }

    /** What a connection does with the rested bonus: the seconds it starts with and the seconds of wait that follow. */
    public record RestedGrant(int restedSeconds, int cooldownSeconds) {
    }

    /**
     * What connecting gives. While a wait is running ({@code cooldownLeft} > 0) there is no bonus and the wait goes on as
     * it is. Otherwise the bonus is the one of {@link #restedOnConnect} and, only if it was granted (more than 0 seconds),
     * the wait starts; a connection that grants nothing (low energy, another tier) never opens one.
     */
    public static RestedGrant onConnect(SessionModifiers modifiers, double energy, int cooldownLeft, VrHelmetBalance vrhelmet) {
        if (cooldownLeft > 0) {
            return new RestedGrant(0, cooldownLeft);
        }
        int granted = restedOnConnect(modifiers, energy, vrhelmet);
        return new RestedGrant(granted, granted > 0 ? vrhelmet.restedCooldownMinutes() * 60 : 0);
    }

    /**
     * The wait that really remains: what is saved, within 0 and the configured wait (lowering the config shortens a wait
     * already running, and 0 frees it).
     */
    public static int cooldownLeft(int saved, VrHelmetBalance vrhelmet) {
        return Math.max(0, Math.min(saved, vrhelmet.restedCooldownMinutes() * 60));
    }

    /** One second of the wait goes by; it never drops below 0 (nor wraps around for the smallest int). */
    public static int tickDown(int seconds) {
        return seconds <= 0 ? 0 : seconds - 1;
    }
}
