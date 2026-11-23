package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.mob.BoneLord;
import com.orijua.mitofus.feature.vrhelmet.SleepingBody;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.coach.CoachmanNpc;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.storage.StorekeeperNpc;
import com.orijua.mitofus.feature.sancho.Mesnadero;
import com.orijua.mitofus.feature.sancho.CondeNuno;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import com.orijua.mitofus.feature.eibar.LevanteThug;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Custom entities. Each living one also needs its attributes (below) and a renderer (MitofusClient). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Mitofus.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BlacksmithNpc>> BLACKSMITH = ENTITIES.register("blacksmith",
            () -> EntityType.Builder.of(BlacksmithNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("blacksmith"));

    public static final DeferredHolder<EntityType<?>, EntityType<VillageChiefNpc>> VILLAGE_CHIEF = ENTITIES.register("village_chief",
            () -> EntityType.Builder.of(VillageChiefNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("village_chief"));

    public static final DeferredHolder<EntityType<?>, EntityType<CoachmanNpc>> COACHMAN = ENTITIES.register("coachman",
            () -> EntityType.Builder.of(CoachmanNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("coachman"));

    public static final DeferredHolder<EntityType<?>, EntityType<StorekeeperNpc>> STOREKEEPER = ENTITIES.register("storekeeper",
            () -> EntityType.Builder.of(StorekeeperNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("storekeeper"));

    public static final DeferredHolder<EntityType<?>, EntityType<BoneLord>> BONE_LORD = ENTITIES.register("bone_lord",
            () -> EntityType.Builder.of(BoneLord::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.4f) // the wither skeleton's hitbox; the scale attribute grows it to boss size
                    .clientTrackingRange(10)
                    .build("bone_lord"));

    // An apprentice blacksmith hired with a contract from Smith: forges alone at a smithing anvil (chest in, barrel out).
    public static final DeferredHolder<EntityType<?>, EntityType<ApprenticeSmith>> APPRENTICE_SMITH = ENTITIES.register("apprentice_smith",
            () -> EntityType.Builder.of(ApprenticeSmith::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("apprentice_smith"));

    // Conde Nuno and his knights: peaceful in his manor near Covadonga, hunters once Sancho's book is appraised.
    public static final DeferredHolder<EntityType<?>, EntityType<CondeNuno>> CONDE_NUNO = ENTITIES.register("conde_nuno",
            () -> EntityType.Builder.of(CondeNuno::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("conde_nuno"));

    public static final DeferredHolder<EntityType<?>, EntityType<Mesnadero>> MESNADERO = ENTITIES.register("mesnadero",
            () -> EntityType.Builder.of(Mesnadero::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("mesnadero"));

    // Velasco, Eibar's old blacksmith: drinks in his ruined smithy until Sancho's successor comes to help.
    public static final DeferredHolder<EntityType<?>, EntityType<VelascoNpc>> VELASCO = ENTITIES.register("velasco",
            () -> EntityType.Builder.of(VelascoNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("velasco"));

    // The Compañía de Levante's thugs: sent into Velasco's smithy for the player who takes "The Blacksmith's Rage".
    public static final DeferredHolder<EntityType<?>, EntityType<LevanteThug>> LEVANTE_THUG = ENTITIES.register("levante_thug",
            () -> EntityType.Builder.of(LevanteThug::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("levante_thug"));

    // Mencia: the smith the Compañía de Levante hires for the production contest in Eibar's square.
    public static final DeferredHolder<EntityType<?>, EntityType<MenciaNpc>> MENCIA = ENTITIES.register("mencia",
            () -> EntityType.Builder.of(MenciaNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("mencia"));

    // The body of a player connected to Mitofus: it lies in the real world while they are away.
    public static final DeferredHolder<EntityType<?>, EntityType<SleepingBody>> SLEEPING_BODY = ENTITIES.register("sleeping_body",
            () -> EntityType.Builder.<SleepingBody>of(SleepingBody::new, MobCategory.MISC)
                    .sized(1.8f, 0.4f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("sleeping_body"));

    private ModEntities() {
    }

    /** The NPCs with a quest window: a quest's "giver" must be one of them. */
    public static Set<EntityType<?>> questGivers() {
        return Set.of(BLACKSMITH.get(), VILLAGE_CHIEF.get(), CONDE_NUNO.get(), VELASCO.get());
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BLACKSMITH.get(), BlacksmithNpc.createAttributes().build());
        event.put(VILLAGE_CHIEF.get(), VillageChiefNpc.createAttributes().build());
        event.put(COACHMAN.get(), CoachmanNpc.createAttributes().build());
        event.put(STOREKEEPER.get(), StorekeeperNpc.createAttributes().build());
        event.put(BONE_LORD.get(), BoneLord.createAttributes().build());
        event.put(APPRENTICE_SMITH.get(), ApprenticeSmith.createAttributes().build());
        event.put(CONDE_NUNO.get(), CondeNuno.createAttributes().build());
        event.put(MESNADERO.get(), Mesnadero.createAttributes().build());
        event.put(VELASCO.get(), VelascoNpc.createAttributes().build());
        event.put(LEVANTE_THUG.get(), LevanteThug.createAttributes().build());
        event.put(MENCIA.get(), MenciaNpc.createAttributes().build());
    }
}
