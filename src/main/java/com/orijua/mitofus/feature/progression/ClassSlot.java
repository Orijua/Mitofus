package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * A class placed in one of the player's class slots: it has its own level and XP (it starts at level 1 and gets the
 * same level XP the player earns while it sits in a slot) and its own grade.
 */
public record ClassSlot(int slot, ResourceLocation classId, int classLevel, int classXp, ClassGrade grade) {
    public static final Codec<ClassSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(ClassSlot::slot),
            ResourceLocation.CODEC.fieldOf("class").forGetter(ClassSlot::classId),
            Codec.INT.optionalFieldOf("class_level", 1).forGetter(ClassSlot::classLevel),
            Codec.INT.optionalFieldOf("class_xp", 0).forGetter(ClassSlot::classXp),
            ClassGrade.CODEC.optionalFieldOf("grade", ClassGrade.APPRENTICE).forGetter(ClassSlot::grade)
    ).apply(instance, ClassSlot::new));

    /** A class just placed in {@code slot}: level 1, no XP, Apprentice. */
    public static ClassSlot fresh(int slot, ResourceLocation classId) {
        return new ClassSlot(slot, classId, 1, 0, ClassGrade.APPRENTICE);
    }

    public ClassSlot withProgress(int level, int xp) {
        return new ClassSlot(slot, classId, level, xp, grade);
    }

    public ClassSlot withGrade(ClassGrade newGrade) {
        return new ClassSlot(slot, classId, classLevel, classXp, newGrade);
    }
}
