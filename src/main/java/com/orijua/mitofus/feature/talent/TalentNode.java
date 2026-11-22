package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** What the rules need to know about a talent node; {@link TalentDefinition} is the real one. Lets the rules be tested on their own. */
public interface TalentNode {
    ResourceLocation playerClass();

    int cost();

    List<ResourceLocation> requires();

    ClassGrade minGrade();

    default boolean isRoot() {
        return requires().isEmpty();
    }
}
