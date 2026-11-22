package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.rumor.RumorDefinition;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.talent.TalentDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Content defined in JSON. Each registry reads data/<namespace>/mitofus/<name>/<id>.json,
 * e.g. data/mitofus/mitofus/skill/sancho_link.json, and is synced to clients for the UI.
 */
public final class ModDatapackRegistries {
    public static final ResourceKey<Registry<SkillDefinition>> SKILL = key("skill");
    public static final ResourceKey<Registry<PassiveDefinition>> PASSIVE = key("passive");
    public static final ResourceKey<Registry<ItemOptionDefinition>> ITEM_OPTION = key("item_option");
    public static final ResourceKey<Registry<PlayerClassDefinition>> PLAYER_CLASS = key("player_class");
    public static final ResourceKey<Registry<JobDefinition>> JOB = key("job");
    public static final ResourceKey<Registry<TitleDefinition>> TITLE = key("title");
    public static final ResourceKey<Registry<QuestDefinition>> QUEST = key("quest");
    public static final ResourceKey<Registry<RumorDefinition>> RUMOR = key("rumor");
    public static final ResourceKey<Registry<TalentDefinition>> TALENT = key("talent");

    private ModDatapackRegistries() {
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SKILL, SkillDefinition.CODEC, SkillDefinition.CODEC);
        event.dataPackRegistry(PASSIVE, PassiveDefinition.CODEC, PassiveDefinition.CODEC);
        event.dataPackRegistry(ITEM_OPTION, ItemOptionDefinition.CODEC, ItemOptionDefinition.CODEC);
        event.dataPackRegistry(PLAYER_CLASS, PlayerClassDefinition.CODEC, PlayerClassDefinition.CODEC);
        event.dataPackRegistry(JOB, JobDefinition.CODEC, JobDefinition.CODEC);
        event.dataPackRegistry(TITLE, TitleDefinition.CODEC, TitleDefinition.CODEC);
        event.dataPackRegistry(QUEST, QuestDefinition.CODEC, QuestDefinition.CODEC);
        event.dataPackRegistry(RUMOR, RumorDefinition.CODEC, RumorDefinition.CODEC);
        event.dataPackRegistry(TALENT, TalentDefinition.CODEC, TalentDefinition.CODEC);
    }

    private static <T> ResourceKey<Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(Mitofus.id(name));
    }
}
