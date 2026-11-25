package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.effect.BonusLimit;
import com.orijua.mitofus.feature.effect.status.StatusDefinition;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.form.FormDefinition;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.rumor.RumorDefinition;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.combination.CombinationDefinition;
import com.orijua.mitofus.feature.synergy.SynergyDefinition;
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
    public static final ResourceKey<Registry<CombinationDefinition>> COMBINATION = key("combination");
    public static final ResourceKey<Registry<TitleDefinition>> TITLE = key("title");
    public static final ResourceKey<Registry<QuestDefinition>> QUEST = key("quest");
    public static final ResourceKey<Registry<RumorDefinition>> RUMOR = key("rumor");
    public static final ResourceKey<Registry<TalentDefinition>> TALENT = key("talent");
    public static final ResourceKey<Registry<SynergyDefinition>> SYNERGY = key("synergy");
    public static final ResourceKey<Registry<ElementTypeDefinition>> ELEMENT_TYPE = key("element_type");
    public static final ResourceKey<Registry<FormDefinition>> FORM = key("form");
    public static final ResourceKey<Registry<StatusDefinition>> STATUS = key("status");
    public static final ResourceKey<Registry<BonusLimit>> BONUS_LIMIT = key("bonus_limit");

    private ModDatapackRegistries() {
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SKILL, SkillDefinition.CODEC, SkillDefinition.CODEC);
        event.dataPackRegistry(PASSIVE, PassiveDefinition.CODEC, PassiveDefinition.CODEC);
        event.dataPackRegistry(ITEM_OPTION, ItemOptionDefinition.CODEC, ItemOptionDefinition.CODEC);
        event.dataPackRegistry(PLAYER_CLASS, PlayerClassDefinition.CODEC, PlayerClassDefinition.CODEC);
        event.dataPackRegistry(JOB, JobDefinition.CODEC, JobDefinition.CODEC);
        event.dataPackRegistry(COMBINATION, CombinationDefinition.CODEC, CombinationDefinition.CODEC);
        event.dataPackRegistry(TITLE, TitleDefinition.CODEC, TitleDefinition.CODEC);
        event.dataPackRegistry(QUEST, QuestDefinition.CODEC, QuestDefinition.CODEC);
        event.dataPackRegistry(RUMOR, RumorDefinition.CODEC, RumorDefinition.CODEC);
        event.dataPackRegistry(TALENT, TalentDefinition.CODEC, TalentDefinition.CODEC);
        event.dataPackRegistry(SYNERGY, SynergyDefinition.CODEC, SynergyDefinition.CODEC);
        event.dataPackRegistry(ELEMENT_TYPE, ElementTypeDefinition.CODEC, ElementTypeDefinition.CODEC);
        event.dataPackRegistry(FORM, FormDefinition.CODEC, FormDefinition.CODEC);
        event.dataPackRegistry(STATUS, StatusDefinition.CODEC, StatusDefinition.CODEC);
        event.dataPackRegistry(BONUS_LIMIT, BonusLimit.CODEC, BonusLimit.CODEC);
    }

    private static <T> ResourceKey<Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(Mitofus.id(name));
    }
}
