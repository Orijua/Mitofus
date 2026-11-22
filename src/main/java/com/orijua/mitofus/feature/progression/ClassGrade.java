package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * The grade of a class: it goes up every 100 class levels (Apprentice, Adept, Expert, Master and Grand Master).
 * Going up is not automatic: the class must have reached the level ({@link #canRankUp}) and meet the grade's requirements.
 */
public enum ClassGrade implements StringRepresentable {
    APPRENTICE("apprentice"),
    ADEPT("adept"),
    EXPERT("expert"),
    MASTER("master"),
    GRAND_MASTER("grand_master");

    public static final Codec<ClassGrade> CODEC = StringRepresentable.fromEnum(ClassGrade::values);

    /** Class levels each grade lasts. */
    public static final int LEVELS_PER_GRADE = 100;

    private final String serializedName;

    ClassGrade(String serializedName) {
        this.serializedName = serializedName;
    }

    /** 1 for Apprentice up to 5 for Grand Master. */
    public int number() {
        return ordinal() + 1;
    }

    public Optional<ClassGrade> next() {
        ClassGrade[] all = values();
        return ordinal() + 1 < all.length ? Optional.of(all[ordinal() + 1]) : Optional.empty();
    }

    /** The class level from which this grade can go up to the next one (100 times its number). */
    public int levelToRankUp() {
        return LEVELS_PER_GRADE * number();
    }

    /** Whether a class of this grade with {@code classLevel} may go up: there is a next grade and the level is reached. */
    public boolean canRankUp(int classLevel) {
        return next().isPresent() && classLevel >= levelToRankUp();
    }

    public Component displayName() {
        return Component.translatable("class_grade.mitofus." + serializedName);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
