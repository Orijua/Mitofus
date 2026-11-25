package com.orijua.mitofus.feature.form;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a form is obtained: just by reaching its class level, or by finishing the grade quest of that level. */
public enum FormSource implements StringRepresentable {
    LEVEL("level"),
    GRADE_QUEST("grade_quest");

    public static final Codec<FormSource> CODEC = StringRepresentable.fromEnum(FormSource::values);

    private final String serialized;

    FormSource(String serialized) {
        this.serialized = serialized;
    }

    @Override
    public String getSerializedName() {
        return serialized;
    }
}
