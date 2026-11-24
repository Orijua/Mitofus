package com.orijua.mitofus.feature.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Marks a quest as the one that takes the class in {@code required_class} from grade {@code from} to the next: completing it
 * raises the grade. {@code job} makes it the variant for players with that job; with no job it is the generic one, used
 * by players whose job has no variant of its own. A job never blocks a grade, it only picks the variant.
 */
public record GradeQuest(ClassGrade from, Optional<ResourceLocation> job) {
    public static final Codec<GradeQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ClassGrade.CODEC.fieldOf("from").forGetter(GradeQuest::from),
            ResourceLocation.CODEC.optionalFieldOf("job").forGetter(GradeQuest::job)
    ).apply(instance, GradeQuest::new));
}
