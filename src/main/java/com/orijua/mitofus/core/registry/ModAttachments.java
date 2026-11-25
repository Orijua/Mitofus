package com.orijua.mitofus.core.registry;

import com.mojang.serialization.Codec;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.save.SaveMigrations;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.vrhelmet.Residence;
import com.orijua.mitofus.feature.storage.Storage;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeaths;
import com.orijua.mitofus.feature.equipment.RarityPity;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.nbt.CompoundTag;
import com.orijua.mitofus.feature.economy.OfferBought;
import com.orijua.mitofus.feature.economy.Purse;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.view.LearnedToday;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.npc.WorkshopEntry;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.PlayerIdentity;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.XpRate;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.goal.PlayerCounters;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SkillMastery;
import com.orijua.mitofus.feature.eibar.ContestState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.orijua.mitofus.feature.character.CharacterVault;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import com.orijua.mitofus.feature.effect.PassiveOverrides;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-player state, one attachment per system. Saved with the player and synced to its owner
 * on every {@code setData}, so client screens just read them.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Mitofus.MODID);

    public static final Supplier<AttachmentType<PlayerStats>> PLAYER_STATS =
            playerData("player_stats", () -> PlayerStats.DEFAULT, PlayerStats.CODEC, PlayerStats.STREAM_CODEC, true);

    public static final Supplier<AttachmentType<PlayerSkills>> PLAYER_SKILLS =
            playerData("player_skills", () -> PlayerSkills.EMPTY, PlayerSkills.CODEC, PlayerSkills.STREAM_CODEC, true);

    /** Successful uses of each skill, for its mastery level (see skill.MasteryRules). Synced to its owner, kept on death. */
    public static final Supplier<AttachmentType<SkillMastery>> SKILL_MASTERY =
            playerData("skill_mastery", () -> SkillMastery.EMPTY, SkillMastery.CODEC, SkillMastery.STREAM_CODEC, true);

    /** Class passives given or taken away by {@code /mitofus give passive}. Empty in normal play; synced, kept on death, cleared by {@link #resetAll}. */
    public static final Supplier<AttachmentType<PassiveOverrides>> PASSIVE_OVERRIDES =
            playerData("passive_overrides", () -> PassiveOverrides.EMPTY, PassiveOverrides.CODEC, PassiveOverrides.STREAM_CODEC, true);

    public static final Supplier<AttachmentType<Float>> MANA =
            playerData("mana", () -> 20.0f, Codec.FLOAT, ByteBufCodecs.FLOAT, false);

    /**
     * The energy of the player's body, 1 (rested) to 0 (spent): drains in Mitofus, refills in the real world (see
     * fatigue.FatigueService). One value for the player, not swapped with the characters; kept on death.
     */
    public static final Supplier<AttachmentType<Float>> FATIGUE =
            playerData("fatigue", () -> 1.0f, Codec.FLOAT, ByteBufCodecs.FLOAT, true);

    /**
     * How saturated each category and rating of equipment is for the player's sales (see economy.TradeService). One
     * value for the player, not swapped with the characters and outside their vault; synced to its owner (the Trade
     * tab shows the prices), kept on death and cleared by {@link #resetAll}.
     */
    public static final Supplier<AttachmentType<TradeSaturation>> TRADE_SATURATION =
            playerData("trade_saturation", () -> TradeSaturation.EMPTY, TradeSaturation.CODEC, TradeSaturation.STREAM_CODEC, true);

    /** Seconds left of the grace period after running out of energy, or -1 when it is not running. Saved (quitting does not stretch it) and synced to its owner, for the countdown on screen. */
    public static final Supplier<AttachmentType<Integer>> FATIGUE_GRACE =
            playerData("fatigue_grace", () -> -1, Codec.INT, ByteBufCodecs.VAR_INT, true);

    /** The last disconnection was forced by the fatigue: the next connection starts with a few seconds of protection. */
    public static final Supplier<AttachmentType<Boolean>> FATIGUE_FORCED_OUT =
            serverData("fatigue_forced_out", () -> false, Codec.BOOL);

    /** Wall-clock milliseconds when the player last left the server, or 0: lets the real world refill the energy while they are away. */
    public static final Supplier<AttachmentType<Long>> FATIGUE_AWAY_SINCE =
            serverData("fatigue_away_since", () -> 0L, Codec.LONG);

    public static final Supplier<AttachmentType<PlayerIdentity>> PLAYER_IDENTITY =
            playerData("player_identity", () -> PlayerIdentity.DEFAULT, PlayerIdentity.CODEC, PlayerIdentity.STREAM_CODEC, true);

    /** The classes in the player's slots (each with its level and grade) and their job. Synced to its owner, kept on death, cleared by {@link #resetAll}. */
    public static final Supplier<AttachmentType<PlayerClasses>> PLAYER_CLASSES =
            playerData("player_classes", () -> PlayerClasses.DEFAULT, PlayerClasses.CODEC, PlayerClasses.STREAM_CODEC, true);

    /** The uses of each form, the grade-quest forms granted and the one equipped form (see form.FormService). Synced to its owner, kept on death, cleared by {@link #resetAll}. */
    public static final Supplier<AttachmentType<PlayerForms>> PLAYER_FORMS =
            playerData("player_forms", () -> PlayerForms.EMPTY, PlayerForms.CODEC, PlayerForms.STREAM_CODEC, true);

    public static final Supplier<AttachmentType<PlayerCounters>> PLAYER_COUNTERS =
            playerData("player_counters", () -> PlayerCounters.EMPTY, PlayerCounters.CODEC, PlayerCounters.STREAM_CODEC, true);

    public static final Supplier<AttachmentType<PlayerQuests>> PLAYER_QUESTS =
            playerData("player_quests", () -> PlayerQuests.EMPTY, PlayerQuests.CODEC, PlayerQuests.STREAM_CODEC, true);

    /** The apprentice blacksmiths a player has hired (their UUIDs), to know how many more they may hire. */
    public static final Supplier<AttachmentType<List<UUID>>> APPRENTICES =
            playerData("apprentices", List::of, UUIDUtil.CODEC.listOf(), ByteBufCodecs.fromCodec(UUIDUtil.CODEC.listOf()), true);

    /** What the Workshop tab shows of each apprentice (see npc.Workshop). */
    public static final Supplier<AttachmentType<List<WorkshopEntry>>> WORKSHOP =
            playerData("workshop", List::of, WorkshopEntry.CODEC.listOf(), ByteBufCodecs.fromCodec(WorkshopEntry.CODEC.listOf()), true);

    /** View (Perspicacia): not allocated, it grows by observing (see view.Observation). */
    public static final Supplier<AttachmentType<Integer>> VIEW =
            playerData("view", () -> 0, Codec.INT, ByteBufCodecs.VAR_INT, true);

    /** Targets the player already learned from today (see view.Observation). Server-only: the client never reads it. */
    public static final Supplier<AttachmentType<LearnedToday>> VIEW_LEARNED =
            serverData("view_learned", () -> LearnedToday.EMPTY, LearnedToday.CODEC);

    /** The villagers whose talent the player has found with Talent Search, for life (see view.TalentSearch). Server-only. */
    public static final Supplier<AttachmentType<List<UUID>>> TALENTS_FOUND =
            serverData("talents_found", List::of, UUIDUtil.CODEC.listOf());

    /** The rumors the player has heard from NPCs, oldest first (the Journal's Rumors section; see rumor.Rumors). */
    public static final Supplier<AttachmentType<List<ResourceLocation>>> HEARD_RUMORS =
            playerData("heard_rumors", List::of, ResourceLocation.CODEC.listOf(), ByteBufCodecs.fromCodec(ResourceLocation.CODEC.listOf()), true);

    /** The production contest against Mencia in Eibar (see eibar.VelascoContest). */
    public static final Supplier<AttachmentType<ContestState>> VELASCO_CONTEST =
            playerData("velasco_contest", () -> ContestState.NONE, ContestState.CODEC, ContestState.STREAM_CODEC, true);

    /** The VR helmet: where the player's body is while they are in Mitofus and where they last disconnected there. Server-only. */
    public static final Supplier<AttachmentType<VrHelmetState>> VR_HELMET =
            serverData("vrhelmet", () -> VrHelmetState.EMPTY, VrHelmetState.CODEC);

    /** Where the player is resident in Mitofus (assigned by a village chief): where they come back to life. Server-only. */
    public static final Supplier<AttachmentType<Residence>> RESIDENCE =
            serverData("residence", () -> Residence.NONE, Residence.CODEC);

    /** The player's gold, iron and copper in Mitofus as one balance (no coin items needed). Kept at death. */
    public static final Supplier<AttachmentType<Purse>> PURSE =
            playerData("purse", () -> Purse.EMPTY, Purse.CODEC, Purse.STREAM_CODEC, true);

    /** The character's storage (opened with the storekeeper): rows, registration number and items. Server-only. */
    public static final Supplier<AttachmentType<Storage>> STORAGE =
            serverData("storage", () -> Storage.NONE, Storage.CODEC);

    /** How many forges and finds in a row gave less than Epic (the anti-bad-luck bonus, see equipment.RarityPity). Saved and kept on death, never synced. */
    public static final Supplier<AttachmentType<RarityPity>> RARITY_PITY =
            serverData("rarity_pity", () -> RarityPity.EMPTY, RarityPity.CODEC);

    /**
     * When the player last died in Mitofus and whether a disconnection is pending (see VR helmet.MitofusDeathService).
     * Server-only, kept on death, cleared by {@link #resetAll}.
     */
    public static final Supplier<AttachmentType<MitofusDeaths>> MITOFUS_DEATHS =
            serverData("mitofus_deaths", () -> MitofusDeaths.EMPTY, MitofusDeaths.CODEC);

    /**
     * What the player carried when they died in Mitofus ("inventory") and the death counter before that death
     * ("deaths_before"), put aside so vanilla cannot drop it (see VR helmet.MitofusDeathService). Saved with the player but
     * NOT copied on death (the new player never inherits it; the original is read at respawn) and NOT cleared by
     * {@link #resetAll}: they are the player's belongings, like the vault.
     */
    public static final Supplier<AttachmentType<CompoundTag>> MITOFUS_DEATH_KEPT = ATTACHMENT_TYPES.register("mitofus_death_kept",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());

    /**
     * The tier of the VR helmet the player last connected with: it sets their session and recovery times (the recovery
     * in the real world keeps using the last one). Synced for the status tab, kept on death.
     */
    public static final Supplier<AttachmentType<VrHelmetTier>> VR_HELMET_TIER =
            playerData("vrhelmet_tier", () -> VrHelmetTier.BASIC, VrHelmetTier.CODEC, VrHelmetTier.STREAM_CODEC, true);

    /**
     * Seconds of the "rested" bonus left (diamond VR helmet): +XP of level while they last, counted down in Mitofus.
     * Synced for the HUD; lost on death, on disconnecting and when the player turns up in the real world still connected.
     */
    public static final Supplier<AttachmentType<Integer>> RESTED_SECONDS =
            playerData("rested_seconds", () -> 0, Codec.INT, ByteBufCodecs.VAR_INT, false);

    /**
     * Seconds left before connecting with the diamond VR helmet can grant the "rested" bonus again (the config's
     * {@code restedCooldownMinutes} starts it when a connection grants the bonus). It only counts down inside Mitofus, like the
     * bonus. Synced for the status tab. Unlike the bonus it is kept on death, on disconnecting and on login: otherwise
     * dying or reconnecting would be a way around the wait. Only {@link #resetAll} clears it.
     */
    public static final Supplier<AttachmentType<Integer>> RESTED_COOLDOWN =
            playerData("rested_cooldown", () -> 0, Codec.INT, ByteBufCodecs.VAR_INT, true);

    /**
     * The level XP the player earned in their last minutes played in Mitofus, for the XP per hour (see
     * progression.XpRateService). Saved, synced to its owner for the screens, kept on death and cleared by {@link #resetAll}.
     */
    public static final Supplier<AttachmentType<XpRate>> XP_RATE =
            playerData("xp_rate", () -> XpRate.EMPTY, XpRate.CODEC, XpRate.STREAM_CODEC, true);

    /** Whether the player already got their starter VR helmet (see VR helmet.StarterVrHelmet). Server-only, kept on death. */
    public static final Supplier<AttachmentType<Boolean>> STARTER_VR_HELMET_GIVEN =
            serverData("starter_vrhelmet_given", () -> false, Codec.BOOL);

    /**
     * The pieces of the terminal's daily offer the player already bought today (see economy.TerminalShop): one unit per
     * piece, player and day. Server-only (the terminal's state carries it to the screen), outside the character vault,
     * kept on death and cleared by {@link #resetAll}.
     */
    public static final Supplier<AttachmentType<OfferBought>> OFFER_BOUGHT =
            serverData("offer_bought", () -> OfferBought.EMPTY, OfferBought.CODEC);

    /**
     * Whether the player already got their phone (see economy.TerminalService#giveIfMissing). Server-only, kept on death
     * and cleared by {@link #resetAll}.
     */
    public static final Supplier<AttachmentType<Boolean>> PHONE_GIVEN =
            serverData("phone_given", () -> false, Codec.BOOL);

    /**
     * The two characters: which world's things the player carries and the snapshot of the other's (see
     * character.CharacterVault). Server-only, kept on death, and NOT cleared by {@link #resetAll}: they are the
     * player's belongings, not progress.
     */
    public static final Supplier<AttachmentType<CharacterVault>> CHARACTER_VAULT =
            serverData("character_vault", () -> CharacterVault.EMPTY, CharacterVault.CODEC);

    /**
     * The version of the mod's saved data this player was last saved with (see save.SaveMigrations). Server-only, kept
     * on death and NOT cleared by {@link #resetAll}: after a reset the data is the current version's defaults.
     */
    public static final Supplier<AttachmentType<Integer>> SAVE_VERSION =
            serverData("save_version", () -> SaveMigrations.BASELINE, Codec.INT);

    /** On mobs (not players): true if it came from a spawner. Saved with the mob, never synced. */
    public static final Supplier<AttachmentType<Boolean>> FROM_SPAWNER = ATTACHMENT_TYPES.register("from_spawner",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    /** On hostile mobs: level and elite flag. Saved with the mob and synced to every client that sees it (for the name tag). */
    public static final Supplier<AttachmentType<MobLevel>> MOB_LEVEL = ATTACHMENT_TYPES.register("mob_level",
            () -> AttachmentType.builder(() -> MobLevel.NONE).serialize(MobLevel.CODEC).sync(MobLevel.STREAM_CODEC).build());

    private ModAttachments() {
    }

    /** Back to defaults for every system (used by /mitofus reset all). */
    public static void resetAll(Player player) {
        ATTACHMENT_TYPES.getEntries().stream()
                .filter(type -> type.get() != CHARACTER_VAULT.get() && type.get() != SAVE_VERSION.get()
                        && type.get() != MITOFUS_DEATH_KEPT.get())
                .forEach(type -> player.removeData(type.get()));
    }

    /** Saved with the player and kept on death, but never synced: state only the server needs. */
    private static <T> Supplier<AttachmentType<T>> serverData(String name, Supplier<T> defaultValue, Codec<T> codec) {
        return ATTACHMENT_TYPES.register(name, () -> AttachmentType.builder(defaultValue)
                .serialize(codec)
                .copyOnDeath()
                .build());
    }

    private static <T> Supplier<AttachmentType<T>> playerData(String name, Supplier<T> defaultValue, Codec<T> codec,
                                                             StreamCodec<ByteBuf, T> streamCodec, boolean keepOnDeath) {
        return ATTACHMENT_TYPES.register(name, () -> {
            AttachmentType.Builder<T> builder = AttachmentType.builder(defaultValue)
                    .serialize(codec)
                    .sync((holder, to) -> holder == to, streamCodec);
            if (keepOnDeath) {
                builder.copyOnDeath();
            }
            return builder.build();
        });
    }
}
