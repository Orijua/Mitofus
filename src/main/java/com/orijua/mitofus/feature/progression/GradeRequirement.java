package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** To reach {@code grade} the player's {@code job} must be at least at level {@code jobLevel} (a job counts as a grade requirement). */
public record GradeRequirement(ClassGrade grade, ResourceLocation job, int jobLevel) {
    public static final Codec<GradeRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ClassGrade.CODEC.fieldOf("grade").forGetter(GradeRequirement::grade),
            ResourceLocation.CODEC.fieldOf("job").forGetter(GradeRequirement::job),
            Codec.INT.fieldOf("job_level").forGetter(GradeRequirement::jobLevel)
    ).apply(instance, GradeRequirement::new));
}
