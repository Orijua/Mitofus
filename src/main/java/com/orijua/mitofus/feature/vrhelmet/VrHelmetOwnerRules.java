package com.orijua.mitofus.feature.vrhelmet;

import java.util.Optional;
import java.util.UUID;

/** Who may use a VR helmet: the first player to use it becomes its owner and nobody else can until it is unbound. */
public final class VrHelmetOwnerRules {
    private VrHelmetOwnerRules() {
    }

    public enum Access {
        /** Nobody owns it: whoever uses it now becomes the owner. */
        BIND,
        OWNER,
        STRANGER
    }

    public static Access access(Optional<UUID> owner, UUID player) {
        if (owner.isEmpty()) {
            return Access.BIND;
        }
        return owner.get().equals(player) ? Access.OWNER : Access.STRANGER;
    }
}
