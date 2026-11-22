package com.orijua.mitofus.feature.npc;

/**
 * Whether a deal of Smith's shop can be closed, pure. The costs and what the player carries are counted by the
 * caller; here only the order of the checks lives, so the client's button and the server's verdict agree.
 */
public final class SmithShopRules {
    private SmithShopRules() {
    }

    /** The verdict on one deal. */
    public enum Check {
        OK,
        /** The offer has no uses left. */
        SOLD_OUT,
        /** The player does not carry the costs. */
        NOT_ENOUGH
    }

    /**
     * @param outOfStock the offer has no uses left
     * @param haveA      how much of the first cost the player carries
     * @param costA      the first cost
     * @param haveB      how much of the second cost the player carries
     * @param costB      the second cost (0 when there is none)
     * @return SOLD_OUT first (even with no money), then NOT_ENOUGH, else OK
     */
    public static Check check(boolean outOfStock, long haveA, int costA, long haveB, int costB) {
        if (outOfStock) {
            return Check.SOLD_OUT;
        }
        if (haveA < costA || (costB > 0 && haveB < costB)) {
            return Check.NOT_ENOUGH;
        }
        return Check.OK;
    }
}
