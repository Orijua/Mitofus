package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A class placed in one of the player's class slots: it has its own level and XP (it starts at level 1 and gets the
 * same level XP the player earns while it sits in a slot), its own grade and the talent nodes bought in its tree (the free
 * root is not stored: it is always owned).
 */
public record ClassSlot(int slot, ResourceLocation classId, int classLevel, int classXp, ClassGrade grade,
                        List<ResourceLocation> talents, List<ResourceLocation> origin, int paidPoints) {
    public static final Codec<ClassSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(ClassSlot::slot),
            ResourceLocation.CODEC.fieldOf("class").forGetter(ClassSlot::classId),
            Codec.INT.optionalFieldOf("class_level", 1).forGetter(ClassSlot::classLevel),
            Codec.INT.optionalFieldOf("class_xp", 0).forGetter(ClassSlot::classXp),
            ClassGrade.CODEC.optionalFieldOf("grade", ClassGrade.APPRENTICE).forGetter(ClassSlot::grade),
            ResourceLocation.CODEC.listOf().optionalFieldOf("talents", List.of()).forGetter(ClassSlot::talents),
            ResourceLocation.CODEC.listOf().optionalFieldOf("origin", List.of()).forGetter(ClassSlot::origin),
            Codec.INT.optionalFieldOf("paid_points", 0).forGetter(ClassSlot::paidPoints)
    ).apply(instance, ClassSlot::new));

    public ClassSlot(int slot, ResourceLocation classId, int classLevel, int classXp, ClassGrade grade) {
        this(slot, classId, classLevel, classXp, grade, List.of());
    }

    public ClassSlot(int slot, ResourceLocation classId, int classLevel, int classXp, ClassGrade grade, List<ResourceLocation> talents) {
        this(slot, classId, classLevel, classXp, grade, talents, List.of(), 0);
    }

    /** Whether this class is a hybrid: it came from a fusion and keeps the two parent classes it was made of. */
    public boolean isHybrid() {
        return !origin.isEmpty();
    }

    /** The classes whose talent nodes make up this slot's tree: the class itself and, for a hybrid, its two parents. */
    public List<ResourceLocation> treeClasses() {
        if (origin.isEmpty()) {
            return List.of(classId);
        }
        List<ResourceLocation> all = new java.util.ArrayList<>(origin);
        all.add(classId);
        return List.copyOf(all);
    }

    /** A class just placed in {@code slot}: level 1, no XP, Apprentice. */
    public static ClassSlot fresh(int slot, ResourceLocation classId) {
        return new ClassSlot(slot, classId, 1, 0, ClassGrade.APPRENTICE);
    }

    public ClassSlot withProgress(int level, int xp) {
        return new ClassSlot(slot, classId, level, xp, grade, talents, origin, paidPoints);
    }

    public ClassSlot withGrade(ClassGrade newGrade) {
        return new ClassSlot(slot, classId, classLevel, classXp, newGrade, talents, origin, paidPoints);
    }

    public ClassSlot withTalents(List<ResourceLocation> bought) {
        return new ClassSlot(slot, classId, classLevel, classXp, grade, List.copyOf(bought), origin, paidPoints);
    }
}
