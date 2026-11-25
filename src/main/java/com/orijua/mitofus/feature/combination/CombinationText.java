package com.orijua.mitofus.feature.combination;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The name of the player's class: the written name of their combination, or the names of its branches joined with a dash. Works on both sides. */
public final class CombinationText {
    private CombinationText() {
    }

    public static Component branchName(Level level, ResourceLocation branch) {
        return Definitions.get(level, ModDatapackRegistries.PLAYER_CLASS, branch).map(PlayerClassDefinition::name).orElse(Component.literal(branch.toString()));
    }

    /** @return empty text when the player holds no branch yet */
    public static Component name(Level level, List<ResourceLocation> held) {
        if (held.isEmpty()) {
            return Component.empty();
        }
        Map<List<ResourceLocation>, Component> written = new HashMap<>();
        Definitions.all(level, ModDatapackRegistries.COMBINATION).forEach(entry ->
                written.put(CombinationRules.key(entry.value().branches()), entry.value().name()));
        return CombinationRules.find(written, held).orElseGet(() -> generated(level, held));
    }

    private static Component generated(Level level, List<ResourceLocation> held) {
        MutableComponent text = Component.empty();
        List<ResourceLocation> ordered = CombinationRules.key(held);
        for (int i = 0; i < ordered.size(); i++) {
            text.append(i == 0 ? Component.empty() : Component.literal("-")).append(branchName(level, ordered.get(i)));
        }
        return text;
    }
}
