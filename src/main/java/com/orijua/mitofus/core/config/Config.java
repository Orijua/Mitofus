package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.equipment.ItemRating;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs, in config/mitofus-server.toml (a world can override them in
 * saves/<world>/serverconfig/mitofus-server.toml).
 * A server config is synced to clients, so screens show the same numbers the server uses.
 * Code reads them through {@link Balance#current()}, never directly.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Balance D = Balance.DEFAULT;

    static {
        BUILDER.push("progression");
    }

    private static final ModConfigSpec.IntValue STAT_POINTS_PER_LEVEL = BUILDER
            .comment("Free stat points granted on each level up.")
            .defineInRange("statPointsPerLevel", D.statPointsPerLevel(), 0, 100);
    private static final ModConfigSpec.DoubleValue KILL_XP_MULTIPLIER = BUILDER
            .comment("Kill XP = (max health + attack damage * killXpPerAttackDamage + armor * killXpPerArmor) * killXpMultiplier.",
                    "With the defaults a zombie (20 HP, 3 attack, 2 armor) gives 28.")
            .defineInRange("killXpMultiplier", D.killXpMultiplier(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue KILL_XP_PER_ATTACK_DAMAGE = BUILDER
            .comment("How much each point of the mob's attack damage adds to its kill XP (see killXpMultiplier for the formula).")
            .defineInRange("killXpPerAttackDamage", D.killXpPerAttackDamage(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue KILL_XP_PER_ARMOR = BUILDER
            .comment("How much each point of the mob's armor adds to its kill XP (see killXpMultiplier for the formula).")
            .defineInRange("killXpPerArmor", D.killXpPerArmor(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue SPAWNER_XP_MULTIPLIER = BUILDER
            .comment("Kill XP multiplier for mobs that came from a spawner or trial spawner (anti-farming).")
            .defineInRange("spawnerXpMultiplier", D.spawnerXpMultiplier(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue XP_BASE = BUILDER
            .comment("XP to the next level = xpBase + xpPerLevel * current level.")
            .defineInRange("xpBase", D.xpBase(), 1, 100_000);
    private static final ModConfigSpec.IntValue XP_PER_LEVEL = BUILDER
            .comment("See xpBase: XP to the next level grows by this much per level you already have.")
            .defineInRange("xpPerLevel", D.xpPerLevel(), 0, 100_000);
    private static final ModConfigSpec.IntValue PASSIVE_FULL_XP_UNTIL_LEVEL = BUILDER
            .comment("Peaceful mobs (animals and the like) give their normal XP up to this player level; from there it falls in a straight line",
                    "to 1 XP at passiveOneXpFromLevel. It does not affect monsters, neutral mobs or bosses. Villagers give no XP.")
            .defineInRange("passiveFullXpUntilLevel", PassiveXpBalance.DEFAULT.fullXpUntilLevel(), 0, PassiveXpBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue PASSIVE_ONE_XP_FROM_LEVEL = BUILDER
            .comment("From this player level on, peaceful mobs give 1 XP. If it is not above passiveFullXpUntilLevel the drop is a single step.")
            .defineInRange("passiveOneXpFromLevel", PassiveXpBalance.DEFAULT.oneXpFromLevel(), 1, PassiveXpBalance.MAX_LEVEL);

    static {
        BUILDER.pop().push("mana");
    }

    private static final ModConfigSpec.DoubleValue BASE_MANA = BUILDER
            .comment("Max mana = baseMana + manaPerIntelligence * INT.")
            .defineInRange("baseMana", (double) D.baseMana(), 0.0, 10_000.0);
    private static final ModConfigSpec.DoubleValue MANA_PER_INTELLIGENCE = BUILDER
            .comment("See baseMana: maximum mana added by each point of intelligence.")
            .defineInRange("manaPerIntelligence", (double) D.manaPerIntelligence(), 0.0, 1_000.0);
    private static final ModConfigSpec.DoubleValue BASE_MANA_REGEN = BUILDER
            .comment("Mana per second = baseManaRegen + manaRegenPerIntelligence * INT.")
            .defineInRange("baseManaRegen", (double) D.baseManaRegen(), 0.0, 1_000.0);
    private static final ModConfigSpec.DoubleValue MANA_REGEN_PER_INTELLIGENCE = BUILDER
            .comment("See baseManaRegen: mana per second added by each point of intelligence.")
            .defineInRange("manaRegenPerIntelligence", (double) D.manaRegenPerIntelligence(), 0.0, 100.0);

    static {
        BUILDER.pop().push("enhancement");
    }

    private static final ModConfigSpec.DoubleValue ENHANCE_BASE_CHANCE = BUILDER
            .comment("Success chance = baseChance - chanceLossPerLevel * current level + chancePerDexterity * DEX,",
                    "clamped between minChance and baseChance.")
            .defineInRange("baseChance", D.enhanceBaseChance(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_LOSS_PER_LEVEL = BUILDER
            .comment("Success chance lost for each enhancement level the piece already has (see baseChance for the formula).")
            .defineInRange("chanceLossPerLevel", D.enhanceChanceLossPerLevel(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_PER_DEXTERITY = BUILDER
            .comment("Success chance gained for each point of dexterity (see baseChance for the formula).")
            .defineInRange("chancePerDexterity", D.enhanceChancePerDexterity(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_MIN_CHANCE = BUILDER
            .comment("The success chance of an enhancement never goes below this, however high the level (see baseChance).")
            .defineInRange("minChance", D.enhanceMinChance(), 0.0, 1.0);

    private static final EnhanceBalance ENH = EnhanceBalance.DEFAULT;
    private static final ModConfigSpec.IntValue ENHANCE_NORMAL_STONE_FAIL_LOSS = BUILDER
            .comment("Levels a piece loses when an enhancement with a normal stone fails (never below +0). 3.")
            .defineInRange("normalStoneFailLoss", ENH.normalStoneFailLoss(), 0, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.IntValue ENHANCE_BLESSED_STONE_FAIL_LOSS = BUILDER
            .comment("Levels lost when an enhancement with a blessed stone fails. 1.")
            .defineInRange("blessedStoneFailLoss", ENH.blessedStoneFailLoss(), 0, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.IntValue ENHANCE_BLESSED_SCROLL_FAIL_LOSS = BUILDER
            .comment("Levels lost when an enhancement with the blessed scroll fails. 0.")
            .defineInRange("blessedScrollFailLoss", ENH.blessedScrollFailLoss(), 0, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.DoubleValue ENHANCE_BONUS_PER_LEVEL = BUILDER
            .comment("Each enhancement level below bonusHighFromLevel raises the item's base attack (weapons) or base armor (armor) by this fraction,",
                    "compounding (0.05 = 5%).")
            .defineInRange("bonusPerLevel", ENH.bonusPerLevel(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_BONUS_PER_LEVEL_HIGH = BUILDER
            .comment("The same for each level from bonusHighFromLevel on (0.07 = 7%).")
            .defineInRange("bonusPerLevelHigh", ENH.bonusPerLevelHigh(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue ENHANCE_BONUS_HIGH_FROM_LEVEL = BUILDER
            .comment("First enhancement level that uses bonusPerLevelHigh.")
            .defineInRange("bonusHighFromLevel", ENH.bonusHighFromLevel(), 1, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_NORMAL = BUILDER
            .comment("Success chance of stones and the blessed scroll is multiplied by the factor of the piece's rank (the mod's own design).",
                    "0 in a rank = only the ancient scroll works on it.")
            .defineInRange("chanceFactorNormal", ENH.chanceFactor(ItemRating.NORMAL), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_RARE = BUILDER
            .comment("Success chance factor for a rare piece (see chanceFactorNormal).")
            .defineInRange("chanceFactorRare", ENH.chanceFactor(ItemRating.RARE), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_EPIC = BUILDER
            .comment("Success chance factor for an epic piece (see chanceFactorNormal).")
            .defineInRange("chanceFactorEpic", ENH.chanceFactor(ItemRating.EPIC), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_UNIQUE = BUILDER
            .comment("Success chance factor for a unique piece (see chanceFactorNormal).")
            .defineInRange("chanceFactorUnique", ENH.chanceFactor(ItemRating.UNIQUE), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_LEGENDARY = BUILDER
            .comment("Success chance factor for a legendary piece (see chanceFactorNormal).")
            .defineInRange("chanceFactorLegendary", ENH.chanceFactor(ItemRating.LEGENDARY), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ENHANCE_CHANCE_FACTOR_ANCESTRAL = BUILDER
            .comment("Success chance factor for a ancestral piece (see chanceFactorNormal).")
            .defineInRange("chanceFactorAncestral", ENH.chanceFactor(ItemRating.ANCESTRAL), 0.0, 1.0);
    private static final ModConfigSpec.IntValue ENHANCE_ANCIENT_MIN_GAIN = BUILDER
            .comment("Fewest levels an ancient scroll adds (it never fails; the mod's own design). If min is above max, the two swap.")
            .defineInRange("ancientScrollMinGain", ENH.ancientMinGain(), 1, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.IntValue ENHANCE_ANCIENT_MAX_GAIN = BUILDER
            .comment("Most levels an ancient scroll adds.")
            .defineInRange("ancientScrollMaxGain", ENH.ancientMaxGain(), 1, EnhanceBalance.MAX_LEVELS);
    private static final ModConfigSpec.DoubleValue ENHANCE_BONE_LORD_SCROLL_CHANCE = BUILDER
            .comment("Chance the Bone Lord drops an ancient scroll when a player kills it (the mod's own design).")
            .defineInRange("boneLordAncientScrollChance", ENH.boneLordAncientScrollChance(), 0.0, 1.0);

    static {
        BUILDER.pop().push("death");
    }

    private static final ModConfigSpec.DoubleValue DEATH_XP_LOSS = BUILDER
            .comment("Fraction of the current level's XP lost on death (0.1 = 10%). Never drops a level, and players under level 10 lose nothing. 0.3 by default. Set 0 to turn it off.")
            .defineInRange("xpLoss", D.deathXpLoss(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue DEATH_PVP_DROP_CHANCE = BUILDER
            .comment("Chance (0.05 = 5 %) that dying in Mitofus to another player, with PvP on, makes equipped pieces fall where you died, for whoever picks them up. 0 = never. Own design.")
            .defineInRange("pvpDropChance", DeathBalance.DEFAULT.pvpDropChance(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue DEATH_PVP_DROP_COUNT = BUILDER
            .comment("How many equipped pieces (main hand and the four armor pieces) fall when that happens.")
            .defineInRange("pvpDropCount", DeathBalance.DEFAULT.pvpDropCount(), 0, DeathBalance.MAX_PVP_DROP_COUNT);
    private static final ModConfigSpec.IntValue DEATH_DOUBLE_WINDOW_MINUTES = BUILDER
            .comment("A second death in Mitofus within this many in-game minutes (game time, stops while the server is closed) of the previous one also disconnects you to your VR helmet. 0 = never. Own design (24 h).")
            .defineInRange("doubleDeathWindowMinutes", DeathBalance.DEFAULT.doubleDeathWindowMinutes(), 0, DeathBalance.MAX_WINDOW_MINUTES);
    private static final ModConfigSpec.IntValue DEATH_RECONNECT_LOCK_MINUTES = BUILDER
            .comment("After that double-death disconnection you cannot connect to Mitofus again for this many in-game minutes (game time, stops while the server is closed). 0 = no lock. Fatigue and normal logouts never lock. Own design.")
            .defineInRange("reconnectLockMinutes", DeathBalance.DEFAULT.reconnectLockMinutes(), 0, DeathBalance.MAX_WINDOW_MINUTES);

    static {
        BUILDER.pop().push("rarity");
    }

    private static final RarityBalance RARITY = RarityBalance.DEFAULT;
    private static final ModConfigSpec.DoubleValue RARITY_NORMAL = BUILDER
            .comment("Base weight of Normal when forging or finding equipment (the six weights are relative: 86 means 86 % when they add up to 100).")
            .defineInRange("weightNormal", RARITY.normal(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_RARE = BUILDER
            .comment("Base weight of Rare.")
            .defineInRange("weightRare", RARITY.rare(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_EPIC = BUILDER
            .comment("Base weight of Epic.")
            .defineInRange("weightEpic", RARITY.epic(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_UNIQUE = BUILDER
            .comment("Base weight of Unique.")
            .defineInRange("weightUnique", RARITY.unique(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_LEGENDARY = BUILDER
            .comment("Base weight of Legendary.")
            .defineInRange("weightLegendary", RARITY.legendary(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_ANCESTRAL = BUILDER
            .comment("Base weight of Ancestral.")
            .defineInRange("weightAncestral", RARITY.ancestral(), 0.0, RarityBalance.MAX_WEIGHT);
    private static final ModConfigSpec.DoubleValue RARITY_SKILL_SCALE = BUILDER
            .comment("Skill points (dexterity + 3 x smithing level) that double the odds of each step up in rating. Higher = skill matters less.")
            .defineInRange("skillScale", RARITY.skillScale(), 1.0, 1000.0);
    private static final ModConfigSpec.BooleanValue RARITY_PITY_ENABLED = BUILDER
            .comment("Anti-bad-luck: after pityStart forges (or finds) in a row without an Epic or better, each next one gets pitySkillPerRoll extra virtual skill, up to pityCap. Counted per player and per way (forging or loot); an Epic or better resets it. It only lifts the odds up to Unique: Legendary and Ancestral never come out of it. It is meant to be a quiet nudge, not something the player notices.")
            .define("pityEnabled", RARITY.pityEnabled());
    private static final ModConfigSpec.IntValue RARITY_PITY_START = BUILDER
            .comment("How many forges (or finds) in a row without an Epic or better before the anti-bad-luck bonus starts.")
            .defineInRange("pityStart", RARITY.pityStart(), 0, 10_000);
    private static final ModConfigSpec.DoubleValue RARITY_PITY_PER_ROLL = BUILDER
            .comment("Virtual skill added for each forge (or find) once the bonus has started.")
            .defineInRange("pitySkillPerRoll", RARITY.pitySkillPerRoll(), 0.0, 1000.0);
    private static final ModConfigSpec.DoubleValue RARITY_PITY_CAP = BUILDER
            .comment("The most virtual skill the anti-bad-luck bonus can add.")
            .defineInRange("pityCap", RARITY.pityCap(), 0.0, 10_000.0);

    static {
        BUILDER.pop().push("stun");
    }

    private static final ModConfigSpec.DoubleValue STUN_LIFE_FRACTION = BUILDER
            .comment("A blow from a creature that takes this fraction of the victim's maximum life at once stuns it, player or mob (0.4 = 40%.")
            .defineInRange("lifeFraction", StunBalance.DEFAULT.lifeFraction(), 0.01, 1.0);
    private static final ModConfigSpec.IntValue STUN_DURATION_SECONDS = BUILDER
            .comment("Seconds a stunned creature cannot move or attack. 0 = stun is off. Duration is the mod's own design.")
            .defineInRange("durationSeconds", StunBalance.DEFAULT.durationSeconds(), 0, StunBalance.MAX_SECONDS);
    private static final ModConfigSpec.IntValue STUN_IMMUNITY_SECONDS = BUILDER
            .comment("Seconds after a stun ends in which the victim cannot be stunned again, so a stun cannot be chained. 0 = no immunity. Mod's own design.")
            .defineInRange("immunitySeconds", StunBalance.DEFAULT_IMMUNITY_SECONDS, 0, StunBalance.MAX_IMMUNITY_SECONDS);

    static {
        BUILDER.pop().push("mobs");
    }

    private static final ModConfigSpec.IntValue MOB_LEVEL_SPREAD = BUILDER
            .comment("Hostile mobs spawn at the nearest player's level, plus or minus up to this many levels.")
            .defineInRange("levelSpread", D.mobLevelSpread(), 0, 100);
    private static final ModConfigSpec.DoubleValue MOB_HEALTH_PER_LEVEL = BUILDER
            .comment("Extra max health per level above 1, as a fraction of the base (0.08 = +8%).")
            .defineInRange("healthPerLevel", D.mobHealthPerLevel(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue MOB_DAMAGE_PER_LEVEL = BUILDER
            .comment("Extra attack damage per level above 1, as a fraction of the base.")
            .defineInRange("damagePerLevel", D.mobDamagePerLevel(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue ELITE_CHANCE = BUILDER
            .comment("Chance for a hostile mob to be an elite: glowing, +eliteStatBonus health and damage, eliteXpMultiplier XP.")
            .defineInRange("eliteChance", D.eliteChance(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ELITE_STAT_BONUS = BUILDER
            .comment("Extra health and damage of an elite, as a fraction (1.0 = +100%). See eliteChance.")
            .defineInRange("eliteStatBonus", D.eliteStatBonus(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue ELITE_XP_MULTIPLIER = BUILDER
            .comment("Kill XP of an elite is multiplied by this. See eliteChance.")
            .defineInRange("eliteXpMultiplier", D.eliteXpMultiplier(), 1.0, 100.0);
    private static final ModConfigSpec.DoubleValue ELITE_EQUIPMENT_CHANCE = BUILDER
            .comment("Chance for an elite killed by a player to drop a rated piece from the #mitofus:elite_drops tag. Its material and quality depend on the elite's level (higher level: better material and a higher minimum rating).")
            .defineInRange("eliteEquipmentChance", D.eliteEquipmentChance(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ELITE_DROP_MIN_CHANCE = BUILDER
            .comment("The chance of an elite's equipment drop falls with its level (there is no level cap) and tends to this floor at very high levels. eliteEquipmentChance is the chance at level 1.")
            .defineInRange("eliteDropMinChance", RuleBalance.DEFAULT.eliteDropMinChance(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue ELITE_DROP_DECAY = BUILDER
            .comment("Each level above 1, what is left of the elite drop chance above its floor is multiplied by this (0.92 = 8% less per level; 1.0 = the chance never falls).")
            .defineInRange("eliteDropDecay", RuleBalance.DEFAULT.eliteDropDecay(), 0.5, 1.0);
    private static final ModConfigSpec.IntValue SMITH_LEVEL = BUILDER
            .comment("Level shown for Smith, the village blacksmith (Jade and name tag). The mod's own design.")
            .defineInRange("smithLevel", RuleBalance.DEFAULT.smithLevel(), 1, RuleBalance.MAX_NPC_LEVEL);
    private static final ModConfigSpec.IntValue CHIEF_LEVEL = BUILDER
            .comment("Level shown for the village chief. The mod's own design.")
            .defineInRange("chiefLevel", RuleBalance.DEFAULT.chiefLevel(), 1, RuleBalance.MAX_NPC_LEVEL);
    private static final ModConfigSpec.IntValue VELASCO_LEVEL = BUILDER
            .comment("Level shown for Velasco. The mod's own design.")
            .defineInRange("velascoLevel", RuleBalance.DEFAULT.velascoLevel(), 1, RuleBalance.MAX_NPC_LEVEL);
    private static final ModConfigSpec.IntValue MENCIA_LEVEL = BUILDER
            .comment("Level shown for Mencia. The mod's own design.")
            .defineInRange("menciaLevel", RuleBalance.DEFAULT.menciaLevel(), 1, RuleBalance.MAX_NPC_LEVEL);

    private static final ModConfigSpec.IntValue BONE_LORD_MINIONS = BUILDER
            .comment("Skeletons the Bone Lord summons once. 0 = none.")
            .defineInRange("boneLordMinions", RuleBalance.DEFAULT.boneLordMinions(), 0, RuleBalance.MAX_MINIONS);
    private static final ModConfigSpec.DoubleValue BONE_LORD_REGEN = BUILDER
            .comment("Percent of its max health the Bone Lord heals each second when left alone (see boneLordOutOfCombatSeconds).")
            .defineInRange("boneLordRegenPercent", RuleBalance.DEFAULT.boneLordRegenPercent(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue BONE_LORD_OUT_OF_COMBAT = BUILDER
            .comment("Seconds without being hurt before the Bone Lord starts healing.")
            .defineInRange("boneLordOutOfCombatSeconds", RuleBalance.DEFAULT.boneLordOutOfCombatSeconds(), 0.0, RuleBalance.MAX_SECONDS);

    static {
        BUILDER.pop().push("story");
    }

    private static final ModConfigSpec.EnumValue<Balance.ContestLoss> CONTEST_LOSS = BUILDER
            .comment("Losing the production contest against Mencia in Eibar: FAIL closes the quest for good, REMATCH allows",
                    "another try the next game day, AUTO fails it in a single-player world and gives a rematch in a shared one.")
            .defineEnum("contestLoss", D.contestLoss());

    private static final ContestBalance CONTEST = ContestBalance.DEFAULT;
    private static final ModConfigSpec.IntValue CONTEST_TIME_LIMIT_MINUTES = BUILDER
            .comment("Minutes of game time to forge the one piece of the contest against Mencia (the mod's own design).")
            .defineInRange("contestMinutes", CONTEST.timeLimitMinutes(), 1, ContestBalance.MAX_MINUTES);
    private static final ModConfigSpec.IntValue CONTEST_RIVAL_MIN = BUILDER
            .comment("Mencia's score is rolled between these two. A piece scores 100 per rating above Normal plus the precision",
                    "in percent (an Epic at 87% is 287). The defaults go from a fair Rare to a perfect Epic. If min is above max, they swap.")
            .defineInRange("rivalMinScore", CONTEST.rivalMinScore(), 0, ContestBalance.MAX_SCORE);
    private static final ModConfigSpec.IntValue CONTEST_RIVAL_MAX = BUILDER
            .comment("Most points Mencia can score.")
            .defineInRange("rivalMaxScore", CONTEST.rivalMaxScore(), 0, ContestBalance.MAX_SCORE);

    static {
        BUILDER.pop().push("fatigue");
    }

    private static final FatigueBalance F = FatigueBalance.DEFAULT;
    private static final ModConfigSpec.BooleanValue FATIGUE_ENABLED = BUILDER
            .comment("Fatigue: a bar that drains only in Mitofus and refills in the real world. When it runs out the VR helmet disconnects you.",
                    "These numbers are.")
            .define("enabled", F.enabled());
    private static final ModConfigSpec.DoubleValue FATIGUE_SESSION_MINUTES = BUILDER
            .comment("Real minutes of normal play from full energy to empty.")
            .defineInRange("sessionMinutes", F.sessionMinutes(), 1.0, 1440.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_REST_MULTIPLIER = BUILDER
            .comment("Share of the normal drain paid while standing still.")
            .defineInRange("restMultiplier", F.restMultiplier(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_COMBAT_MULTIPLIER = BUILDER
            .comment("Multiplier of the normal drain while fighting (damage given or taken in the last 10 seconds). Working at an anvil or a smelter costs the same.")
            .defineInRange("combatMultiplier", F.combatMultiplier(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_FORGE_COST_SECONDS = BUILDER
            .comment("Seconds of normal play that each forged item costs.")
            .defineInRange("forgeCostSeconds", F.forgeCostSeconds(), 0.0, 3600.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_RECOVERY_MINUTES = BUILDER
            .comment("Real minutes to go from empty to full while resting in the real world.")
            .defineInRange("recoveryMinutes", F.recoveryMinutes(), 0.1, 1440.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_MIN_ENERGY_TO_CONNECT = BUILDER
            .comment("The VR helmet refuses to connect below this energy (0 to 1).")
            .defineInRange("minEnergyToConnect", F.minEnergyToConnect(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue FATIGUE_GRACE_SECONDS = BUILDER
            .comment("Seconds between running out of energy and the forced disconnection.")
            .defineInRange("graceSeconds", F.graceSeconds(), 0, 600);
    private static final ModConfigSpec.DoubleValue FATIGUE_WARN_1 = BUILDER
            .comment("Energies (0 to 1) at which a warning shows once when crossed downwards.")
            .defineInRange("warnFirst", F.warnAt().get(0), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_WARN_2 = BUILDER
            .comment("Second warning energy (see warnFirst).")
            .defineInRange("warnSecond", F.warnAt().get(1), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_WARN_3 = BUILDER
            .comment("Third warning energy (see warnFirst).")
            .defineInRange("warnThird", F.warnAt().get(2), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue FATIGUE_SLEEP_RECOVERY = BUILDER
            .comment("Energy (0 to 1) that sleeping through the night in the real world gives back.")
            .defineInRange("sleepRecovery", F.sleepRecovery(), 0.0, 1.0);
    private static final RuleBalance RULE = RuleBalance.DEFAULT;
    private static final ModConfigSpec.DoubleValue FATIGUE_COMBAT_WINDOW = BUILDER
            .comment("Seconds after damage given or taken that still count as fighting for the fatigue (see combatMultiplier). 0 = never fighting.")
            .defineInRange("combatWindowSeconds", RULE.combatWindowSeconds(), 0.0, RuleBalance.MAX_SECONDS);
    private static final ModConfigSpec.DoubleValue FATIGUE_PROTECTION = BUILDER
            .comment("Seconds of protection after a forced disconnection (nothing can hurt the player while they reconnect). 0 = none.")
            .defineInRange("protectionSeconds", RULE.protectionSeconds(), 0.0, RuleBalance.MAX_SECONDS);
    private static final ModConfigSpec.DoubleValue FATIGUE_REST_MAX_MOVE = BUILDER
            .comment("Blocks the player may move (measured over one tick window) and still count as standing still for restMultiplier.")
            .defineInRange("restMaxMove", RULE.restMaxMove(), 0.0, RuleBalance.MAX_BLOCKS);
    private static final ModConfigSpec.DoubleValue FATIGUE_WORK_WINDOW = BUILDER
            .comment("Seconds after breaking a block that still count as working for the fatigue. 0 = mining never counts.")
            .defineInRange("workWindowSeconds", RULE.workWindowSeconds(), 0.0, RuleBalance.MAX_SECONDS);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.push("weight");
    }

    private static final ModConfigSpec.BooleanValue WEIGHT_ENABLED = BUILDER
            .comment("Weight: only smith's objects weigh (weapons, armor, tools, ingots and ores). Going over the limit",
                    "slows you by half the percentage over it and may weaken you. Off by default until it is tried in the game; the mod's own design.")
            .define("weightEnabled", WeightBalance.DEFAULT.enabled());
    private static final ModConfigSpec.DoubleValue WEIGHT_BASE_LIMIT = BUILDER
            .comment("Kilograms of smith's objects anyone can carry before slowing down.")
            .defineInRange("weightBaseLimitKg", WeightBalance.DEFAULT.baseLimitKg(), 1.0, WeightBalance.MAX_KG);
    private static final ModConfigSpec.DoubleValue WEIGHT_PER_STAMINA = BUILDER
            .comment("Kilograms each point of Stamina adds to the limit.")
            .defineInRange("weightKgPerStamina", WeightBalance.DEFAULT.kgPerStamina(), 0.0, WeightBalance.MAX_KG);

    static {
        BUILDER.pop().push("coach");
    }

    private static final ModConfigSpec.IntValue COACH_BASE = BUILDER
            .comment("The coachman's base fare in iron (100 iron = 1 gold). gold on a safe road, so 500.")
            .defineInRange("coachBaseIron", CoachBalance.DEFAULT.baseIron(), 1, CoachBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue COACH_SURCHARGE = BUILDER
            .comment("Iron the surcharge for risk adds for every 100 blocks of road. The mod's own design.")
            .defineInRange("coachSurchargeIron", CoachBalance.DEFAULT.surchargePer100Blocks(), 0, CoachBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue COACH_MAX_SURCHARGE = BUILDER
            .comment("The most the surcharge can add to the fare, in iron.")
            .defineInRange("coachMaxSurchargeIron", CoachBalance.DEFAULT.maxSurchargeIron(), 0, CoachBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue COACH_TRAVEL_SECONDS = BUILDER
            .comment("Seconds the trip lasts: you go blind and cannot move, then you arrive. 0 = you arrive at once.")
            .defineInRange("coachTravelSeconds", CoachBalance.DEFAULT.travelSeconds(), 0, CoachBalance.MAX_SECONDS);

    static {
        BUILDER.pop().push("storage");
    }

    private static final ModConfigSpec.IntValue STORAGE_OPEN = BUILDER
            .comment("What opening a storage costs in iron. It comes with 2 rows (18 slots).")
            .defineInRange("storageOpenIron", StorageBalance.DEFAULT.openIron(), 0, StorageBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue STORAGE_ROW3 = BUILDER
            .comment("What the third row costs, in iron. The rows are the mod's own design.")
            .defineInRange("storageRow3Iron", StorageBalance.DEFAULT.row3Iron(), 0, StorageBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue STORAGE_ROW4 = BUILDER
            .comment("What the fourth row costs, in iron.")
            .defineInRange("storageRow4Iron", StorageBalance.DEFAULT.row4Iron(), 0, StorageBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue STORAGE_ROW5 = BUILDER
            .comment("What the fifth row costs, in iron.")
            .defineInRange("storageRow5Iron", StorageBalance.DEFAULT.row5Iron(), 0, StorageBalance.MAX_IRON);
    private static final ModConfigSpec.IntValue STORAGE_ROW6 = BUILDER
            .comment("What the sixth row costs, in iron (54 slots in all).")
            .defineInRange("storageRow6Iron", StorageBalance.DEFAULT.row6Iron(), 0, StorageBalance.MAX_IRON);

    static {
        BUILDER.pop().push("residence");
    }

    private static final ModConfigSpec.IntValue RESIDENCE_COOLDOWN = BUILDER
            .comment("Real minutes a player must wait before changing their residence to another village (20 = one game day). The mod's own design.")
            .defineInRange("residenceCooldownMinutes", ResidenceBalance.DEFAULT.cooldownMinutes(), 0, ResidenceBalance.MAX_MINUTES);

    static {
        BUILDER.pop().push("trade");
    }

    private static final TradeBalance T = TradeBalance.DEFAULT;
    private static final ModConfigSpec.IntValue TRADE_BASE_NORMAL = BUILDER
            .comment("Trade: selling equipment for gold. These numbers are.",
                    "Reference price in gold of a piece of each rating; the price is this x material x level x options x enhancement x durability (this one: normal).")
            .defineInRange("basePriceGoldNormal", T.basePrice().get(ItemRating.NORMAL), 1, 1_000_000);
    private static final ModConfigSpec.IntValue TRADE_BASE_RARE = BUILDER
            .comment("Reference price of a rare piece.")
            .defineInRange("basePriceGoldRare", T.basePrice().get(ItemRating.RARE), 1, 1_000_000);
    private static final ModConfigSpec.IntValue TRADE_BASE_EPIC = BUILDER
            .comment("Reference price of a epic piece.")
            .defineInRange("basePriceGoldEpic", T.basePrice().get(ItemRating.EPIC), 1, 1_000_000);
    private static final ModConfigSpec.IntValue TRADE_BASE_UNIQUE = BUILDER
            .comment("Reference price of a unique piece.")
            .defineInRange("basePriceGoldUnique", T.basePrice().get(ItemRating.UNIQUE), 1, 1_000_000);
    private static final ModConfigSpec.IntValue TRADE_BASE_LEGENDARY = BUILDER
            .comment("Reference price of a legendary piece.")
            .defineInRange("basePriceGoldLegendary", T.basePrice().get(ItemRating.LEGENDARY), 1, 1_000_000);
    private static final ModConfigSpec.IntValue TRADE_BASE_ANCESTRAL = BUILDER
            .comment("Reference price of a ancestral piece.")
            .defineInRange("basePriceGoldAncestral", T.basePrice().get(ItemRating.ANCESTRAL), 1, 1_000_000);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_WOOD = BUILDER
            .comment("Price multiplier of wood equipment.")
            .defineInRange("materialWood", T.materialMultiplier().get("wood"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_STONE = BUILDER
            .comment("Price multiplier of stone equipment.")
            .defineInRange("materialStone", T.materialMultiplier().get("stone"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_IRON = BUILDER
            .comment("Price multiplier of iron equipment.")
            .defineInRange("materialIron", T.materialMultiplier().get("iron"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_GOLD = BUILDER
            .comment("Price multiplier of gold equipment.")
            .defineInRange("materialGold", T.materialMultiplier().get("gold"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_DIAMOND = BUILDER
            .comment("Price multiplier of diamond equipment.")
            .defineInRange("materialDiamond", T.materialMultiplier().get("diamond"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_NETHERITE = BUILDER
            .comment("Price multiplier of netherite equipment.")
            .defineInRange("materialNetherite", T.materialMultiplier().get("netherite"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_LEATHER = BUILDER
            .comment("Price multiplier of leather equipment.")
            .defineInRange("materialLeather", T.materialMultiplier().get("leather"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_MATERIAL_CHAINMAIL = BUILDER
            .comment("Price multiplier of chainmail equipment.")
            .defineInRange("materialChainmail", T.materialMultiplier().get("chainmail"), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_OTHER_MATERIAL = BUILDER
            .comment("Price multiplier of any other material.")
            .defineInRange("otherMaterial", T.otherMaterial(), 0.0, 100.0);
    private static final ModConfigSpec.DoubleValue TRADE_ENHANCEMENT_BONUS = BUILDER
            .comment("Share of the reference price added per enhancement level (every piece).")
            .defineInRange("enhancementBonus", T.enhancementBonus(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue TRADE_LEVEL_BONUS = BUILDER
            .comment("Share of the reference price added per level of the piece's required level above 1.")
            .defineInRange("levelBonus", T.levelBonus(), 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue TRADE_OPTION_BONUS = BUILDER
            .comment("Share of the reference price added per option the piece carries.")
            .defineInRange("optionBonus", T.optionBonus(), 0.0, 10.0);
    private static final ModConfigSpec.IntValue TRADE_IMPOSSIBLE_MARGIN = BUILDER
            .comment("A piece whose recipe asks for a level more than this many levels above the one its material and rating justify is \"impossible\".")
            .defineInRange("impossibleMargin", T.impossibleMargin(), 0, 1000);
    private static final ModConfigSpec.DoubleValue TRADE_IMPOSSIBLE_FACTOR = BUILDER
            .comment("Price factor of an impossible piece (0 to 1).")
            .defineInRange("impossibleFactor", T.impossibleFactor(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue TRADE_SELL_SHARE = BUILDER
            .comment("Share of the reference price paid in gold when selling (0 to 1: selling never pays more than the reference).")
            .defineInRange("sellShare", T.sellShare(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue TRADE_SATURATION_STEP = BUILDER
            .comment("Price factor for each point of pressure (each piece sold of the same category and rating).")
            .defineInRange("saturationStep", T.saturationStep(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue TRADE_SATURATION_FLOOR = BUILDER
            .comment("The saturation factor never goes below this.")
            .defineInRange("saturationFloor", T.saturationFloor(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue TRADE_RECOVERY_PER_DAY = BUILDER
            .comment("Points of pressure that go away each whole game day.")
            .defineInRange("recoveryPerDay", T.recoveryPerDay(), 0, 10_000);
    private static final ModConfigSpec.IntValue TRADE_PRESSURE_CAP = BUILDER
            .comment("Pressure never grows beyond this.")
            .defineInRange("pressureCap", T.pressureCap(), 1, 10_000);
    private static final ModConfigSpec.LongValue TRADE_MAX_SELL_GOLD = BUILDER
            .comment("A sale that would pay more gold than this is refused without touching the piece.")
            .defineInRange("maxSellGold", T.maxSellGold(), 21L, 33_333L);
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TRADE_ITEM_MULTIPLIERS = BUILDER
            .comment("Price multiplier of single objects, \"ns:path=N\" (N from 0 to 100). It replaces the one of the material, so a unique object",
                    "(Guadalete, the Wolfram sword, the smithing hammers) does not weigh as iron or netherite just for its tier. A wrong line is",
                    "dropped when NeoForge corrects the file (and if no line is left, the default list comes back); if an item repeats, the first line wins.")
            .defineListAllowEmpty("itemMultipliers",
                    T.itemMultiplier().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                            .map(entry -> new com.orijua.mitofus.feature.economy.TradeRules.ItemMultiplier(entry.getKey(), entry.getValue()).asString())
                            .toList(),
                    () -> "minecraft:iron_sword=1.0",
                    value -> value instanceof String text && com.orijua.mitofus.feature.economy.TradeRules.parseItemMultiplier(text).isPresent());
    private static final ModConfigSpec.DoubleValue TRADE_MIN_DURABILITY_FACTOR = BUILDER
            .comment("Price factor of a piece with no durability left (0 to 1). A new piece pays the full price; in between it is linear.")
            .defineInRange("minDurabilityFactor", T.minDurabilityFactor(), 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.push("equipLevel");
    }

    private static final EquipLevelBalance EL = EquipLevelBalance.DEFAULT;
    private static final ModConfigSpec.IntValue EL_WOOD = BUILDER
            .comment("The level a rated piece needs to be used, unless its recipe asks for more: the level of its material plus the extra of its rating.",
                    "These numbers are. Level of wood equipment.")
            .defineInRange("levelWood", EL.materialLevel().get("wood"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_STONE = BUILDER
            .comment("Level of stone equipment.")
            .defineInRange("levelStone", EL.materialLevel().get("stone"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_IRON = BUILDER
            .comment("Level of iron equipment.")
            .defineInRange("levelIron", EL.materialLevel().get("iron"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_GOLD = BUILDER
            .comment("Level of gold equipment.")
            .defineInRange("levelGold", EL.materialLevel().get("gold"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_DIAMOND = BUILDER
            .comment("Level of diamond equipment.")
            .defineInRange("levelDiamond", EL.materialLevel().get("diamond"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_NETHERITE = BUILDER
            .comment("Level of netherite equipment.")
            .defineInRange("levelNetherite", EL.materialLevel().get("netherite"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_LEATHER = BUILDER
            .comment("Level of leather equipment.")
            .defineInRange("levelLeather", EL.materialLevel().get("leather"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_CHAINMAIL = BUILDER
            .comment("Level of chainmail equipment.")
            .defineInRange("levelChainmail", EL.materialLevel().get("chainmail"), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_OTHER = BUILDER
            .comment("Level of any other material.")
            .defineInRange("levelOtherMaterial", EL.otherMaterialLevel(), 1, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_NORMAL = BUILDER
            .comment("Levels a rating adds on top of the material's (this one: normal).")
            .defineInRange("extraNormal", EL.ratingExtra().get(ItemRating.NORMAL), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_RARE = BUILDER
            .comment("Levels a rare piece adds.")
            .defineInRange("extraRare", EL.ratingExtra().get(ItemRating.RARE), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_EPIC = BUILDER
            .comment("Levels an epic piece adds.")
            .defineInRange("extraEpic", EL.ratingExtra().get(ItemRating.EPIC), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_UNIQUE = BUILDER
            .comment("Levels a unique piece adds.")
            .defineInRange("extraUnique", EL.ratingExtra().get(ItemRating.UNIQUE), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_LEGENDARY = BUILDER
            .comment("Levels a legendary piece adds.")
            .defineInRange("extraLegendary", EL.ratingExtra().get(ItemRating.LEGENDARY), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue EL_EXTRA_ANCESTRAL = BUILDER
            .comment("Levels a ancestral piece adds.")
            .defineInRange("extraAncestral", EL.ratingExtra().get(ItemRating.ANCESTRAL), 0, EquipLevelBalance.MAX_LEVEL);
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> EL_ITEM_LEVELS = BUILDER
            .comment("Level of single objects, \"ns:path=N\" (N from 1 to " + EquipLevelBalance.MAX_LEVEL + "). It replaces the one of the material (the rating still adds its extra).",
                    "A wrong line is dropped when NeoForge corrects the file (and if no line is left, the default list comes back); if an item repeats, the first line wins.")
            .defineListAllowEmpty("itemLevels",
                    EL.itemLevel().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                            .map(entry -> new com.orijua.mitofus.feature.equipment.EquipLevelRules.ItemLevel(entry.getKey(), entry.getValue()).asString())
                            .toList(),
                    () -> "minecraft:iron_sword=5",
                    value -> value instanceof String text && com.orijua.mitofus.feature.equipment.EquipLevelRules.parseItemLevel(text).isPresent());

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.push("terminal");
    }

    private static final TerminalBalance TERMINAL = TerminalBalance.DEFAULT;
    private static final ModConfigSpec.DoubleValue TERMINAL_BUY_MULTIPLIER = BUILDER
            .comment("Account terminal: the daily offer, paid with the coins of the purse. These numbers are the mod's own design.",
                    "How many times its reference value a piece of the daily offer costs (price = reference x this, in gold, at least 1 gold of reference).")
            .defineInRange("buyMultiplier", TERMINAL.buyMultiplier(), 1.0, 1000.0);
    private static final ModConfigSpec.IntValue TERMINAL_OFFER_PER_RANK = BUILDER
            .comment("Pieces of the daily offer for each rating, from normal up to offerMaxRating. 0 turns the offer off.")
            .defineInRange("offerPerRank", TERMINAL.offerPerRank(), 0, TerminalBalance.MAX_OFFER_PER_RANK);
    private static final ModConfigSpec.EnumValue<ItemRating> TERMINAL_OFFER_MAX_RATING = BUILDER
            .comment("The highest rating the daily offer reaches.")
            .defineEnum("offerMaxRating", TERMINAL.offerMaxRating());
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TERMINAL_OFFER_ITEMS = BUILDER
            .comment("The item types the daily offer draws from (equipment only: unknown ids and non-equipment are skipped with a warning).",
                    "The list cannot be empty (the config rejects it); to turn the offer off use offerPerRank = 0.")
            .defineList("offerItems", TERMINAL.offerItems().stream().map(net.minecraft.resources.ResourceLocation::toString).toList(),
                    () -> "minecraft:iron_sword",
                    value -> value instanceof String text && net.minecraft.resources.ResourceLocation.tryParse(text) != null);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.push("appraisal");
    }

    private static final AppraisalBalance APPRAISAL = AppraisalBalance.DEFAULT;
    private static final ModConfigSpec.IntValue APPRAISAL_MIN_DATA = BUILDER
            .comment("Appraisal of equipment with the Appraiser Eye. Loot, the terminal's offer and apprentices' pieces come unappraised; each use of the Eye reveals some of their data.",
                    "Revealing 6 to 10 data per use; the cooldown is the mod's own design.",
                    "Fewest data one use of the Eye reveals (if min is above max, the two swap).")
            .defineInRange("minData", APPRAISAL.minData(), 1, AppraisalBalance.MAX_DATA);
    private static final ModConfigSpec.IntValue APPRAISAL_MAX_DATA = BUILDER
            .comment("Most data one use of the Eye reveals.")
            .defineInRange("maxData", APPRAISAL.maxData(), 1, AppraisalBalance.MAX_DATA);
    private static final ModConfigSpec.IntValue APPRAISAL_EYE_COOLDOWN_TICKS = BUILDER
            .comment("Ticks before the Appraiser Eye can be used again (20 ticks = 1 second).")
            .defineInRange("eyeCooldownTicks", APPRAISAL.eyeCooldownTicks(), 0, AppraisalBalance.MAX_COOLDOWN_TICKS);

    private static final ModConfigSpec.DoubleValue TERMINAL_RANGE = BUILDER
            .comment("Blocks from the terminal beyond which its screen closes.")
            .defineInRange("terminalRange", RuleBalance.DEFAULT.terminalRange(), 1.0, RuleBalance.MAX_BLOCKS);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.push("vrhelmet");
    }

    private static final VrHelmetBalance VR_HELMET = VrHelmetBalance.DEFAULT;
    private static final ModConfigSpec.DoubleValue VR_HELMET_ADVANCED_SESSION_MINUTES = BUILDER
            .comment("The VR helmet tiers. The basic VR helmet uses the [fatigue] numbers as they are;",
                    "if fatigue.sessionMinutes is higher than these, the advanced VR helmet lasts less than the basic one (not checked).",
                    "Real minutes of normal play from full energy to empty with the advanced VR helmet.")
            .defineInRange("advancedSessionMinutes", VR_HELMET.advancedSessionMinutes(), 1.0, VrHelmetBalance.MAX_SESSION_MINUTES);
    private static final ModConfigSpec.DoubleValue VR_HELMET_ADVANCED_RECOVERY_MINUTES = BUILDER
            .comment("Real minutes to refill from empty in the real world after an advanced session.")
            .defineInRange("advancedRecoveryMinutes", VR_HELMET.advancedRecoveryMinutes(), VrHelmetBalance.MIN_RECOVERY_MINUTES, VrHelmetBalance.MAX_SESSION_MINUTES);
    private static final ModConfigSpec.DoubleValue VR_HELMET_DIAMOND_SESSION_MINUTES = BUILDER
            .comment("Real minutes of normal play from full energy to empty with the diamond VR helmet.")
            .defineInRange("diamondSessionMinutes", VR_HELMET.diamondSessionMinutes(), 1.0, VrHelmetBalance.MAX_SESSION_MINUTES);
    private static final ModConfigSpec.DoubleValue VR_HELMET_DIAMOND_RECOVERY_MINUTES = BUILDER
            .comment("Real minutes to refill from empty in the real world after a diamond session.")
            .defineInRange("diamondRecoveryMinutes", VR_HELMET.diamondRecoveryMinutes(), VrHelmetBalance.MIN_RECOVERY_MINUTES, VrHelmetBalance.MAX_SESSION_MINUTES);
    private static final ModConfigSpec.LongValue VR_HELMET_ADVANCED_PRICE = BUILDER
            .comment("Price of the advanced helmet in iron (1 gold = 100 iron): what unbinding it is priced from.")
            .defineInRange("advancedHelmetPrice", VR_HELMET.advancedHelmetPrice(), 1L, VrHelmetBalance.MAX_PRICE);
    private static final ModConfigSpec.LongValue VR_HELMET_DIAMOND_PRICE = BUILDER
            .comment("Price of the diamond helmet in iron: what unbinding it is priced from.")
            .defineInRange("diamondHelmetPrice", VR_HELMET.diamondHelmetPrice(), 1L, VrHelmetBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue VR_HELMET_UNBIND_COST_PERCENT = BUILDER
            .comment("Unbinding a bound helmet at the terminal costs this percent of the price of its tier (200 = double, on purpose). 0 = free. Paid with the coins of the purse.")
            .defineInRange("unbindCostPercent", VR_HELMET.unbindCostPercent(), 0, VrHelmetBalance.MAX_UNBIND_COST_PERCENT);
    private static final ModConfigSpec.LongValue VR_HELMET_BASIC_REFERENCE_PRICE = BUILDER
            .comment("Price of the basic helmet in iron, when pricing its unbinding.")
            .defineInRange("basicReferencePrice", VR_HELMET.basicReferencePrice(), 0L, VrHelmetBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue VR_HELMET_RESTED_MINUTES = BUILDER
            .comment("Minutes of the \"rested\" bonus a diamond connection grants (only with enough energy). 0 turns it off.")
            .defineInRange("restedMinutes", VR_HELMET.restedMinutes(), 0, VrHelmetBalance.MAX_RESTED_MINUTES);
    private static final ModConfigSpec.IntValue VR_HELMET_RESTED_XP_PERCENT = BUILDER
            .comment("Extra level XP, in percent, while rested (smithing XP is not affected).")
            .defineInRange("restedXpPercent", VR_HELMET.restedXpPercent(), 0, VrHelmetBalance.MAX_RESTED_XP_PERCENT);
    private static final ModConfigSpec.DoubleValue VR_HELMET_RESTED_MIN_ENERGY = BUILDER
            .comment("The energy (0 to 1) needed when connecting with the diamond VR helmet to get the rested bonus.")
            .defineInRange("restedMinEnergy", VR_HELMET.restedMinEnergy(), 0.0, 1.0);
    private static final ModConfigSpec.IntValue VR_HELMET_RESTED_COOLDOWN_MINUTES = BUILDER
            .comment("Minutes spent inside Mitofus after a rested bonus is granted before connecting grants another. 0 = no wait.")
            .defineInRange("restedCooldownMinutes", VR_HELMET.restedCooldownMinutes(), 0, VrHelmetBalance.MAX_RESTED_COOLDOWN_MINUTES);

    private static final ModConfigSpec.DoubleValue VR_HELMET_CONNECT_SECONDS = BUILDER
            .comment("Seconds lying in a VR helmet before connecting to Mitofus (moving or being hurt cancels it).")
            .defineInRange("connectSeconds", RuleBalance.DEFAULT.connectSeconds(), RuleBalance.MIN_WAIT_SECONDS, RuleBalance.MAX_SECONDS);
    private static final ModConfigSpec.DoubleValue VR_HELMET_LOGOUT_SECONDS = BUILDER
            .comment("Seconds of the /mitofus logout countdown.")
            .defineInRange("logoutSeconds", RuleBalance.DEFAULT.logoutSeconds(), RuleBalance.MIN_WAIT_SECONDS, RuleBalance.MAX_SECONDS);
    private static final ModConfigSpec.DoubleValue VR_HELMET_DANGER_SECONDS = BUILDER
            .comment("Seconds after any combat during which /mitofus logout is refused. 0 = never refused.")
            .defineInRange("dangerSeconds", RuleBalance.DEFAULT.dangerSeconds(), 0.0, RuleBalance.MAX_SECONDS);
    private static final ModConfigSpec.DoubleValue VR_HELMET_MAX_DISTANCE = BUILDER
            .comment("Blocks the player may get from the VR helmet while connecting before it is cancelled.")
            .defineInRange("maxDistance", RuleBalance.DEFAULT.maxDistance(), 0.5, RuleBalance.MAX_BLOCKS);
    private static final ModConfigSpec.DoubleValue VR_HELMET_STILL_TOLERANCE = BUILDER
            .comment("Blocks the player may move during the connection or the logout countdown and still count as standing still.")
            .defineInRange("stillTolerance", RuleBalance.DEFAULT.stillTolerance(), 0.0, RuleBalance.MAX_BLOCKS);

    static {
        BUILDER.pop().push("apprentices");
    }

    private static final ApprenticeBalance APPRENTICE = ApprenticeBalance.DEFAULT;
    private static final ModConfigSpec.IntValue APPRENTICE_FIRST_LEVEL = BUILDER
            .comment("Apprentice blacksmiths (the mod's own design).",
                    "Effective smithing level that allows the first apprentice.")
            .defineInRange("firstApprenticeLevel", APPRENTICE.firstLevel(), 0, ApprenticeBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue APPRENTICE_LEVELS_PER = BUILDER
            .comment("Smithing levels for each apprentice after the first (with the defaults: 1 at 50, 2 at 60... 5 at 90).")
            .defineInRange("levelsPerApprentice", APPRENTICE.levelsPerApprentice(), 1, ApprenticeBalance.MAX_LEVEL);
    private static final ModConfigSpec.IntValue APPRENTICE_MAX = BUILDER
            .comment("Most apprentices a smith may have. 0 turns them off (no new hires; the ones already hired stay).")
            .defineInRange("maxApprentices", APPRENTICE.maxApprentices(), 0, ApprenticeBalance.MAX_APPRENTICES);
    private static final ModConfigSpec.IntValue APPRENTICE_DAILY_WAGE = BUILDER
            .comment("Gold each apprentice costs per Minecraft day.")
            .defineInRange("dailyWage", APPRENTICE.dailyWage(), 1, ApprenticeBalance.MAX_GOLD);
    private static final ModConfigSpec.IntValue APPRENTICE_MAX_PAID_DAYS = BUILDER
            .comment("Days of wage that can be paid ahead.")
            .defineInRange("maxPaidDays", APPRENTICE.maxPaidDays(), 1, ApprenticeBalance.MAX_DAYS);
    private static final ModConfigSpec.IntValue APPRENTICE_CONTRACT_PRICE = BUILDER
            .comment("Gold Smith asks for each apprentice contract after the first, which he gives.")
            .defineInRange("contractPrice", APPRENTICE.contractPrice(), 1, ApprenticeBalance.MAX_GOLD);
    private static final ModConfigSpec.IntValue APPRENTICE_SLOWEST_SECONDS = BUILDER
            .comment("Seconds a beginner apprentice takes per piece; each smithing level takes half a second off, down to the fastest.",
                    "If slowest is below fastest, they swap.")
            .defineInRange("slowestSecondsPerPiece", APPRENTICE.slowestSecondsPerPiece(), 1, ApprenticeBalance.MAX_SECONDS);
    private static final ModConfigSpec.IntValue APPRENTICE_FASTEST_SECONDS = BUILDER
            .comment("Seconds the most skilled apprentice takes per piece.")
            .defineInRange("fastestSecondsPerPiece", APPRENTICE.fastestSecondsPerPiece(), 1, ApprenticeBalance.MAX_SECONDS);

    private static final ModConfigSpec.DoubleValue APPRENTICE_NAMED_CHANCE = BUILDER
            .comment("Chance (0 to 1) that a hired apprentice is a named NPC (grows without limit). With semiNamedChance it never passes 1.")
            .defineInRange("namedChance", RuleBalance.DEFAULT.namedChance(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue APPRENTICE_SEMI_NAMED_CHANCE = BUILDER
            .comment("Chance (0 to 1) that a hired apprentice is semi-named (grows further than a normal one). If namedChance + this passes 1, this is cut.")
            .defineInRange("semiNamedChance", RuleBalance.DEFAULT.semiNamedChance(), 0.0, 1.0);

    static {
        BUILDER.pop().push("smithShop");
    }

    private static final SmithShopBalance SHOP = SmithShopBalance.DEFAULT;
    private static final ModConfigSpec.IntValue SHOP_STONE_PRICE = BUILDER
            .comment("Smith's shop, prices in gold. A smith already in the world takes the new numbers the next time his shop opens,",
                    "keeping what was already bought. Weapon or armor enhancement stone.")
            .defineInRange("stonePrice", SHOP.stonePrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_BLESSED_STONE_PRICE = BUILDER
            .comment("Blessed weapon or armor enhancement stone.")
            .defineInRange("blessedStonePrice", SHOP.blessedStonePrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_BLESSED_SCROLL_PRICE = BUILDER
            .comment("Blessed enhancement scroll (the mod's own design).")
            .defineInRange("blessedScrollPrice", SHOP.blessedScrollPrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_STONE_STOCK = BUILDER
            .comment("Normal stones of each kind he has until he restocks.")
            .defineInRange("stoneStock", SHOP.stoneStock(), 1, SmithShopBalance.MAX_STOCK);
    private static final ModConfigSpec.IntValue SHOP_BLESSED_STOCK = BUILDER
            .comment("Blessed stones of each kind, and blessed scrolls, he has until he restocks.")
            .defineInRange("blessedStock", SHOP.blessedStock(), 1, SmithShopBalance.MAX_STOCK);
    private static final ModConfigSpec.IntValue SHOP_RESPEC_SCROLL_PRICE = BUILDER
            .comment("Stat reset scroll.")
            .defineInRange("respecScrollPrice", SHOP.respecScrollPrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_MITHRIL_SALE_GOLD = BUILDER
            .comment("Gold he pays for 4 mithril ingots.")
            .defineInRange("mithrilSaleGold", SHOP.mithrilSaleGold(), 1, SmithShopBalance.MAX_MITHRIL_SALE);
    private static final ModConfigSpec.IntValue SHOP_SKILL_BOOK_PRICE = BUILDER
            .comment("Each skill book.")
            .defineInRange("skillBookPrice", SHOP.skillBookPrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_EYE_PRICE = BUILDER
            .comment("Appraiser Eye (it also asks for 2 diamonds).")
            .defineInRange("eyePrice", SHOP.eyePrice(), 1, SmithShopBalance.MAX_PRICE);
    private static final ModConfigSpec.IntValue SHOP_EYE_DAILY_STOCK = BUILDER
            .comment("Appraiser Eyes on sale each Minecraft day.")
            .defineInRange("eyeDailyStock", SHOP.eyeDailyStock(), 1, SmithShopBalance.MAX_STOCK);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /**
     * Cached snapshot of one balance. The spec is read once per load or reload, not per call: the HUD layers ask for
     * the balance every frame (BUG-104). {@link #invalidate()} runs from the config events, and the cache is built
     * lazily on the next read.
     */
    static final class Cached<T> {
        private final java.util.function.Supplier<T> builder;
        private volatile T value;

        Cached(java.util.function.Supplier<T> builder) {
            this.builder = builder;
        }

        T get() {
            T v = value;
            if (v == null) {
                v = builder.get();
                value = v;
            }
            return v;
        }

        void invalidate() {
            value = null;
        }
    }

    private static final java.util.List<Cached<?>> CACHES = new java.util.ArrayList<>();

    private static <T> Cached<T> cache(java.util.function.Supplier<T> builder) {
        Cached<T> c = new Cached<>(builder);
        CACHES.add(c);
        return c;
    }

    /** Hooks the cache invalidation to this spec's load, reload and unload events (mod event bus). */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading e) -> invalidateFor(e));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> invalidateFor(e));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Unloading e) -> invalidateFor(e));
    }

    private static void invalidateFor(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            invalidateCaches();
        }
    }

    /** Forgets every cached balance; called when the config is loaded, reloaded or unloaded. */
    static void invalidateCaches() {
        for (Cached<?> c : CACHES) {
            c.invalidate();
        }
    }

    private static final Cached<FatigueBalance> FATIGUE_BALANCE_CACHE = cache(Config::buildFatigueBalance);
    private static final Cached<TradeBalance> TRADE_BALANCE_CACHE = cache(Config::buildTradeBalance);
    private static final Cached<EquipLevelBalance> EQUIP_LEVEL_BALANCE_CACHE = cache(Config::buildEquipLevelBalance);
    private static final Cached<TerminalBalance> TERMINAL_BALANCE_CACHE = cache(Config::buildTerminalBalance);
    private static final Cached<AppraisalBalance> APPRAISAL_BALANCE_CACHE = cache(Config::buildAppraisalBalance);
    private static final Cached<EnhanceBalance> ENHANCE_BALANCE_CACHE = cache(Config::buildEnhanceBalance);
    private static final Cached<RuleBalance> RULE_BALANCE_CACHE = cache(Config::buildRuleBalance);
    private static final Cached<VrHelmetBalance> VR_HELMET_BALANCE_CACHE = cache(Config::buildVrHelmetBalance);
    private static final Cached<PassiveXpBalance> PASSIVE_XP_BALANCE_CACHE = cache(Config::buildPassiveXpBalance);
    private static final Cached<RarityBalance> RARITY_BALANCE_CACHE = cache(() -> new RarityBalance(RARITY_NORMAL.get(), RARITY_RARE.get(),
            RARITY_EPIC.get(), RARITY_UNIQUE.get(), RARITY_LEGENDARY.get(), RARITY_ANCESTRAL.get(), RARITY_SKILL_SCALE.get(),
            RARITY_PITY_ENABLED.get(), RARITY_PITY_START.get(), RARITY_PITY_PER_ROLL.get(), RARITY_PITY_CAP.get()));
    private static final Cached<DeathBalance> DEATH_BALANCE_CACHE = cache(Config::buildDeathBalance);
    private static final Cached<CoachBalance> COACH_BALANCE_CACHE = cache(() -> new CoachBalance(COACH_BASE.get(), COACH_SURCHARGE.get(), COACH_MAX_SURCHARGE.get(), COACH_TRAVEL_SECONDS.get()));
    private static final Cached<StorageBalance> STORAGE_BALANCE_CACHE = cache(() -> new StorageBalance(STORAGE_OPEN.get(), STORAGE_ROW3.get(),
            STORAGE_ROW4.get(), STORAGE_ROW5.get(), STORAGE_ROW6.get()));
    private static final Cached<ResidenceBalance> RESIDENCE_BALANCE_CACHE = cache(() -> new ResidenceBalance(RESIDENCE_COOLDOWN.get()));
    private static final Cached<WeightBalance> WEIGHT_BALANCE_CACHE = cache(() -> new WeightBalance(WEIGHT_ENABLED.get(), WEIGHT_BASE_LIMIT.get(), WEIGHT_PER_STAMINA.get()));
    private static final Cached<StunBalance> STUN_BALANCE_CACHE = cache(Config::buildStunBalance);
    private static final Cached<ApprenticeBalance> APPRENTICE_BALANCE_CACHE = cache(Config::buildApprenticeBalance);
    private static final Cached<SmithShopBalance> SMITH_SHOP_BALANCE_CACHE = cache(Config::buildSmithShopBalance);
    private static final Cached<ContestBalance> CONTEST_BALANCE_CACHE = cache(Config::buildContestBalance);
    private static final Cached<Balance> BALANCE_CACHE = cache(Config::buildBalance);

    static FatigueBalance fatigueBalance() {
        return FATIGUE_BALANCE_CACHE.get();
    }

    static TradeBalance tradeBalance() {
        return TRADE_BALANCE_CACHE.get();
    }

    static EquipLevelBalance equipLevelBalance() {
        return EQUIP_LEVEL_BALANCE_CACHE.get();
    }

    static TerminalBalance terminalBalance() {
        return TERMINAL_BALANCE_CACHE.get();
    }

    static AppraisalBalance appraisalBalance() {
        return APPRAISAL_BALANCE_CACHE.get();
    }

    static EnhanceBalance enhanceBalance() {
        return ENHANCE_BALANCE_CACHE.get();
    }

    static RuleBalance ruleBalance() {
        return RULE_BALANCE_CACHE.get();
    }

    static VrHelmetBalance vrHelmetBalance() {
        return VR_HELMET_BALANCE_CACHE.get();
    }

    static PassiveXpBalance passiveXpBalance() {
        return PASSIVE_XP_BALANCE_CACHE.get();
    }

    static RarityBalance rarityBalance() {
        return RARITY_BALANCE_CACHE.get();
    }

    static DeathBalance deathBalance() {
        return DEATH_BALANCE_CACHE.get();
    }

    static CoachBalance coachBalance() {
        return COACH_BALANCE_CACHE.get();
    }

    static StorageBalance storageBalance() {
        return STORAGE_BALANCE_CACHE.get();
    }

    static ResidenceBalance residenceBalance() {
        return RESIDENCE_BALANCE_CACHE.get();
    }

    static WeightBalance weightBalance() {
        return WEIGHT_BALANCE_CACHE.get();
    }

    static StunBalance stunBalance() {
        return STUN_BALANCE_CACHE.get();
    }

    static ApprenticeBalance apprenticeBalance() {
        return APPRENTICE_BALANCE_CACHE.get();
    }

    static SmithShopBalance smithShopBalance() {
        return SMITH_SHOP_BALANCE_CACHE.get();
    }

    static ContestBalance contestBalance() {
        return CONTEST_BALANCE_CACHE.get();
    }

    static Balance balance() {
        return BALANCE_CACHE.get();
    }

    static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    private static FatigueBalance buildFatigueBalance() {
        return new FatigueBalance(
                FATIGUE_ENABLED.get(), FATIGUE_SESSION_MINUTES.get(), FATIGUE_REST_MULTIPLIER.get(), FATIGUE_COMBAT_MULTIPLIER.get(),
                FATIGUE_FORGE_COST_SECONDS.get(), FATIGUE_RECOVERY_MINUTES.get(), FATIGUE_MIN_ENERGY_TO_CONNECT.get(),
                FATIGUE_GRACE_SECONDS.get(),
                java.util.stream.Stream.of(FATIGUE_WARN_1.get(), FATIGUE_WARN_2.get(), FATIGUE_WARN_3.get())
                        .sorted(java.util.Comparator.reverseOrder()).toList(),
                FATIGUE_SLEEP_RECOVERY.get());
    }

    private static TradeBalance buildTradeBalance() {
        java.util.Map<ItemRating, Integer> prices = new java.util.EnumMap<>(ItemRating.class);
        prices.put(ItemRating.NORMAL, TRADE_BASE_NORMAL.get());
        prices.put(ItemRating.RARE, TRADE_BASE_RARE.get());
        prices.put(ItemRating.EPIC, TRADE_BASE_EPIC.get());
        prices.put(ItemRating.UNIQUE, TRADE_BASE_UNIQUE.get());
        prices.put(ItemRating.LEGENDARY, TRADE_BASE_LEGENDARY.get());
        prices.put(ItemRating.ANCESTRAL, TRADE_BASE_ANCESTRAL.get());
        java.util.Map<String, Double> materials = new java.util.HashMap<>();
        materials.put("wood", TRADE_MATERIAL_WOOD.get());
        materials.put("stone", TRADE_MATERIAL_STONE.get());
        materials.put("iron", TRADE_MATERIAL_IRON.get());
        materials.put("gold", TRADE_MATERIAL_GOLD.get());
        materials.put("diamond", TRADE_MATERIAL_DIAMOND.get());
        materials.put("netherite", TRADE_MATERIAL_NETHERITE.get());
        materials.put("leather", TRADE_MATERIAL_LEATHER.get());
        materials.put("chainmail", TRADE_MATERIAL_CHAINMAIL.get());
        return new TradeBalance(prices, materials, TRADE_OTHER_MATERIAL.get(), TRADE_ENHANCEMENT_BONUS.get(),
                TRADE_LEVEL_BONUS.get(), TRADE_OPTION_BONUS.get(), TRADE_IMPOSSIBLE_MARGIN.get(), TRADE_IMPOSSIBLE_FACTOR.get(),
                TRADE_SELL_SHARE.get(),
                TRADE_SATURATION_STEP.get(), TRADE_SATURATION_FLOOR.get(),
                TRADE_RECOVERY_PER_DAY.get(), TRADE_PRESSURE_CAP.get(), TRADE_MAX_SELL_GOLD.get(), TRADE_MIN_DURABILITY_FACTOR.get(),
                com.orijua.mitofus.feature.economy.TradeRules.itemMultipliers(TRADE_ITEM_MULTIPLIERS.get()));
    }

    private static EquipLevelBalance buildEquipLevelBalance() {
        java.util.Map<String, Integer> materials = new java.util.HashMap<>();
        materials.put("wood", EL_WOOD.get());
        materials.put("stone", EL_STONE.get());
        materials.put("iron", EL_IRON.get());
        materials.put("gold", EL_GOLD.get());
        materials.put("diamond", EL_DIAMOND.get());
        materials.put("netherite", EL_NETHERITE.get());
        materials.put("leather", EL_LEATHER.get());
        materials.put("chainmail", EL_CHAINMAIL.get());
        java.util.Map<ItemRating, Integer> extras = new java.util.EnumMap<>(ItemRating.class);
        extras.put(ItemRating.NORMAL, EL_EXTRA_NORMAL.get());
        extras.put(ItemRating.RARE, EL_EXTRA_RARE.get());
        extras.put(ItemRating.EPIC, EL_EXTRA_EPIC.get());
        extras.put(ItemRating.UNIQUE, EL_EXTRA_UNIQUE.get());
        extras.put(ItemRating.LEGENDARY, EL_EXTRA_LEGENDARY.get());
        extras.put(ItemRating.ANCESTRAL, EL_EXTRA_ANCESTRAL.get());
        return new EquipLevelBalance(materials, EL_OTHER.get(), extras,
                com.orijua.mitofus.feature.equipment.EquipLevelRules.itemLevels(EL_ITEM_LEVELS.get()));
    }

    private static TerminalBalance buildTerminalBalance() {
        java.util.List<net.minecraft.resources.ResourceLocation> offerItems = TERMINAL_OFFER_ITEMS.get().stream()
                .map(net.minecraft.resources.ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new TerminalBalance(TERMINAL_BUY_MULTIPLIER.get(), TERMINAL_OFFER_PER_RANK.get(), TERMINAL_OFFER_MAX_RATING.get(), offerItems);
    }

    private static AppraisalBalance buildAppraisalBalance() {
        int a = APPRAISAL_MIN_DATA.get();
        int b = APPRAISAL_MAX_DATA.get();
        return new AppraisalBalance(Math.min(a, b), Math.max(a, b), APPRAISAL_EYE_COOLDOWN_TICKS.get());
    }

    private static EnhanceBalance buildEnhanceBalance() {
        int a = ENHANCE_ANCIENT_MIN_GAIN.get();
        int b = ENHANCE_ANCIENT_MAX_GAIN.get();
        java.util.Map<ItemRating, Double> factors = new java.util.EnumMap<>(ItemRating.class);
        factors.put(ItemRating.NORMAL, ENHANCE_CHANCE_FACTOR_NORMAL.get());
        factors.put(ItemRating.RARE, ENHANCE_CHANCE_FACTOR_RARE.get());
        factors.put(ItemRating.EPIC, ENHANCE_CHANCE_FACTOR_EPIC.get());
        factors.put(ItemRating.UNIQUE, ENHANCE_CHANCE_FACTOR_UNIQUE.get());
        factors.put(ItemRating.LEGENDARY, ENHANCE_CHANCE_FACTOR_LEGENDARY.get());
        factors.put(ItemRating.ANCESTRAL, ENHANCE_CHANCE_FACTOR_ANCESTRAL.get());
        return new EnhanceBalance(ENHANCE_NORMAL_STONE_FAIL_LOSS.get(), ENHANCE_BLESSED_STONE_FAIL_LOSS.get(),
                ENHANCE_BLESSED_SCROLL_FAIL_LOSS.get(), ENHANCE_BONUS_PER_LEVEL.get(), ENHANCE_BONUS_PER_LEVEL_HIGH.get(),
                ENHANCE_BONUS_HIGH_FROM_LEVEL.get(), factors,
                Math.min(a, b), Math.max(a, b), ENHANCE_BONE_LORD_SCROLL_CHANCE.get());
    }

    private static RuleBalance buildRuleBalance() {
        double named = APPRENTICE_NAMED_CHANCE.get();
        double semi = Math.min(APPRENTICE_SEMI_NAMED_CHANCE.get(), 1.0 - named);
        return new RuleBalance(VR_HELMET_CONNECT_SECONDS.get(), VR_HELMET_LOGOUT_SECONDS.get(), VR_HELMET_DANGER_SECONDS.get(),
                VR_HELMET_MAX_DISTANCE.get(), VR_HELMET_STILL_TOLERANCE.get(), named, semi,
                BONE_LORD_MINIONS.get(), BONE_LORD_REGEN.get(), BONE_LORD_OUT_OF_COMBAT.get(),
                FATIGUE_COMBAT_WINDOW.get(), FATIGUE_PROTECTION.get(), FATIGUE_REST_MAX_MOVE.get(), FATIGUE_WORK_WINDOW.get(),
                TERMINAL_RANGE.get(), ELITE_DROP_MIN_CHANCE.get(), ELITE_DROP_DECAY.get(),
                SMITH_LEVEL.get(), CHIEF_LEVEL.get(), VELASCO_LEVEL.get(), MENCIA_LEVEL.get());
    }

    private static VrHelmetBalance buildVrHelmetBalance() {
        return new VrHelmetBalance(
                VR_HELMET_ADVANCED_SESSION_MINUTES.get(), VR_HELMET_ADVANCED_RECOVERY_MINUTES.get(),
                VR_HELMET_DIAMOND_SESSION_MINUTES.get(), VR_HELMET_DIAMOND_RECOVERY_MINUTES.get(),
                VR_HELMET_ADVANCED_PRICE.get(), VR_HELMET_DIAMOND_PRICE.get(),
                VR_HELMET_RESTED_MINUTES.get(), VR_HELMET_RESTED_XP_PERCENT.get(), VR_HELMET_RESTED_MIN_ENERGY.get(),
                VR_HELMET_RESTED_COOLDOWN_MINUTES.get(), VR_HELMET_UNBIND_COST_PERCENT.get(), VR_HELMET_BASIC_REFERENCE_PRICE.get());
    }

    private static PassiveXpBalance buildPassiveXpBalance() {
        return new PassiveXpBalance(PASSIVE_FULL_XP_UNTIL_LEVEL.get(), PASSIVE_ONE_XP_FROM_LEVEL.get());
    }

    private static DeathBalance buildDeathBalance() {
        return new DeathBalance(DEATH_DOUBLE_WINDOW_MINUTES.get(), DEATH_RECONNECT_LOCK_MINUTES.get(), DEATH_PVP_DROP_CHANCE.get(), DEATH_PVP_DROP_COUNT.get());
    }

    private static StunBalance buildStunBalance() {
        return new StunBalance(STUN_LIFE_FRACTION.get(), STUN_DURATION_SECONDS.get(), STUN_IMMUNITY_SECONDS.get());
    }

    private static ApprenticeBalance buildApprenticeBalance() {
        return new ApprenticeBalance(APPRENTICE_FIRST_LEVEL.get(), APPRENTICE_LEVELS_PER.get(), APPRENTICE_MAX.get(),
                APPRENTICE_DAILY_WAGE.get(), APPRENTICE_MAX_PAID_DAYS.get(), APPRENTICE_CONTRACT_PRICE.get(),
                APPRENTICE_SLOWEST_SECONDS.get(), APPRENTICE_FASTEST_SECONDS.get());
    }

    private static SmithShopBalance buildSmithShopBalance() {
        return new SmithShopBalance(SHOP_STONE_PRICE.get(), SHOP_BLESSED_STONE_PRICE.get(), SHOP_BLESSED_SCROLL_PRICE.get(),
                SHOP_STONE_STOCK.get(), SHOP_BLESSED_STOCK.get(), SHOP_RESPEC_SCROLL_PRICE.get(), SHOP_MITHRIL_SALE_GOLD.get(),
                SHOP_SKILL_BOOK_PRICE.get(), SHOP_EYE_PRICE.get(), SHOP_EYE_DAILY_STOCK.get());
    }

    private static ContestBalance buildContestBalance() {
        return new ContestBalance(CONTEST_TIME_LIMIT_MINUTES.get(), CONTEST_RIVAL_MIN.get(), CONTEST_RIVAL_MAX.get());
    }

    private static Balance buildBalance() {
        return new Balance(
                STAT_POINTS_PER_LEVEL.get(), KILL_XP_MULTIPLIER.get(), KILL_XP_PER_ATTACK_DAMAGE.get(), KILL_XP_PER_ARMOR.get(),
                SPAWNER_XP_MULTIPLIER.get(), XP_BASE.get(), XP_PER_LEVEL.get(),
                BASE_MANA.get().floatValue(), MANA_PER_INTELLIGENCE.get().floatValue(),
                BASE_MANA_REGEN.get().floatValue(), MANA_REGEN_PER_INTELLIGENCE.get().floatValue(),
                ENHANCE_BASE_CHANCE.get(), ENHANCE_LOSS_PER_LEVEL.get(), ENHANCE_PER_DEXTERITY.get(), ENHANCE_MIN_CHANCE.get(),
                DEATH_XP_LOSS.get(),
                MOB_LEVEL_SPREAD.get(), MOB_HEALTH_PER_LEVEL.get(), MOB_DAMAGE_PER_LEVEL.get(),
                ELITE_CHANCE.get(), ELITE_STAT_BONUS.get(), ELITE_XP_MULTIPLIER.get(), ELITE_EQUIPMENT_CHANCE.get(),
                CONTEST_LOSS.get());
    }
}
