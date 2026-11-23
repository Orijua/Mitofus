package com.orijua.mitofus.feature.vrhelmet;

/**
 * The client's side of connecting: when the "Connecting…" screen started and how long it lasts. No client classes, so
 * the network handler can use it on a dedicated server; the screen that draws it is in {@code vrhelmet.client}.
 */
public final class ClientVrHelmet {
    private static final long MILLIS_PER_TICK = 50;

    private static volatile long startMillis;
    private static volatile long durationMillis;
    /** When the screen was cut short or finished, and how dark it was then: it fades out from there instead of vanishing in one frame. */
    private static volatile long fadeStartMillis = -1;
    private static volatile float fadeFromDark;
    public static final long FADE_OUT_MILLIS = 500;
    public static final float MAX_DARK = 0.92f;

    private ClientVrHelmet() {
    }

    /** {@code ticks} = 0 cancels. */
    public static void connecting(int ticks, long nowMillis) {
        if (ticks <= 0) {
            float fraction = fraction(nowMillis);
            if (fraction >= 0.0f) {
                fadeFromDark = dark(fraction);
                fadeStartMillis = nowMillis;
            }
        } else {
            fadeStartMillis = -1;
        }
        startMillis = nowMillis;
        durationMillis = Math.max(0, ticks) * MILLIS_PER_TICK;
    }

    /** How far the connection is, 0 to 1, or -1 if nothing is connecting. */
    public static float fraction(long nowMillis) {
        long duration = durationMillis;
        if (duration <= 0) {
            return -1.0f;
        }
        float fraction = (nowMillis - startMillis) / (float) duration;
        if (fraction >= 1.0f) {
            // The server moves the player at 1; a late packet must not leave the screen up.
            return nowMillis - startMillis > duration + 1_000 ? -1.0f : 1.0f;
        }
        return Math.max(0.0f, fraction);
    }

    /** How dark the view is while connecting: it comes in over the first half and stays. */
    public static float dark(float fraction) {
        return Math.min(1.0f, Math.max(0.0f, fraction * 2.0f)) * MAX_DARK;
    }

    /** The darkness left while fading out after the screen ended (0 when there is nothing to fade). */
    public static float fadeOutDark(long nowMillis) {
        long start = fadeStartMillis;
        if (start < 0 || nowMillis - start >= FADE_OUT_MILLIS) {
            return 0.0f;
        }
        return fadeFromDark * (1.0f - (nowMillis - start) / (float) FADE_OUT_MILLIS);
    }
}
