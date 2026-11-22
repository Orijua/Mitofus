package com.orijua.mitofus.feature.effect;

import java.util.List;

/** The single entry point for running an {@link Effect}. Everything works only in Mitofus. */
public final class EffectEngine {
    private EffectEngine() {
    }

    /** Whether the engine would run effects with these conditions: in Mitofus and every condition true. */
    public static boolean canApply(List<Condition> conditions, EffectContext context) {
        if (!InMitofusCondition.INSTANCE.test(context)) {
            return false;
        }
        for (Condition condition : conditions) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    /** What running a list of effects came to. */
    public enum Outcome {
        /** Not in Mitofus, or a condition failed: nothing ran. */
        REFUSED,
        /** It ran but no effect did anything (a cone that hit nobody). */
        IDLE,
        /** At least one effect did something. */
        ACTED
    }

    /** @return false when a condition failed and nothing was applied. */
    public static boolean apply(Effect effect, EffectContext context) {
        return run(List.of(effect), List.of(), context) != Outcome.REFUSED;
    }

    public static boolean apply(Effect effect, List<Condition> conditions, EffectContext context) {
        return run(List.of(effect), conditions, context) != Outcome.REFUSED;
    }

    public static boolean apply(List<Effect> effects, List<Condition> conditions, EffectContext context) {
        return run(effects, conditions, context) != Outcome.REFUSED;
    }

    /** Runs every effect in order, once, when the conditions hold, and says whether any of them did something. */
    public static Outcome run(List<Effect> effects, List<Condition> conditions, EffectContext context) {
        if (!canApply(conditions, context)) {
            return Outcome.REFUSED;
        }
        boolean acted = false;
        for (Effect effect : effects) {
            acted |= effect.apply(context);
        }
        return acted ? Outcome.ACTED : Outcome.IDLE;
    }
}
