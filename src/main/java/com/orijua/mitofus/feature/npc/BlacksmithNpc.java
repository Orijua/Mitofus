package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.SmithShopBalance;
import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.quest.client.NpcQuestScreen;
import com.orijua.mitofus.feature.rumor.Rumors;
import com.orijua.mitofus.feature.skill.SkillBookItem;
import com.orijua.mitofus.feature.eibar.EibarStory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A blacksmith NPC: right-click opens his quest window (with a Trade button for the shop).
 * Reuses vanilla's merchant logic (AbstractVillager) and the villager model. It stays where it is
 * placed (no wandering), never despawns and only takes damage from creative players or /kill.
 */
public class BlacksmithNpc extends AbstractVillager implements PeacefulNpc {
    /** Skill books on sale; the content validator checks these skills exist. */
    public static final List<ResourceLocation> SOLD_SKILLS = List.of(Mitofus.id("rending_slash"), Mitofus.id("iron_guard"));

    public BlacksmithNpc(EntityType<? extends BlacksmithNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // Only for spotting shops stocked before the economy; the currency now is the gold coin.
    private static final Item LEGACY_CURRENCY = Items.EMERALD;

    /** Version of the shop's catalogue: 1 = priced in Mitofus emeralds (and no stamp saved), 2 = priced in gold, 3 = enhancement that can fail, 4 = the Appraiser Eye restocks to 4 every day. */
    public static final int SHOP_VERSION = 4;
    private static final String SHOP_VERSION_TAG = "mitofus_shop_version";
    private int shopVersion;
    /** The Appraiser Eye's uses are refilled when a new Minecraft day starts. */
    private static final String EYE_DAY_TAG = "mitofus_eye_day";
    private long eyeDay = Long.MIN_VALUE;
    // Prices in gold and stocks are in the server config (SmithShopBalance); the stones are 90 and 1,250.
    private static final int RESPEC_SCROLL_STOCK = 4;
    private static final int MITHRIL_INGOTS_BOUGHT = 4;
    private static final int MITHRIL_STOCK = 16;
    private static final int SKILL_BOOK_STOCK = 3;
    private static final int CONTRACT_STOCK = 5;

    /** The shop. Edit {@link #catalogue} to change what the blacksmith sells or buys. */
    @Override
    protected void updateTrades() {
        getOffers().addAll(catalogue(SmithShopBalance.current(), ApprenticeBalance.current()));
        shopVersion = SHOP_VERSION;
    }

    /** Everything Smith sells or buys, with the prices and stocks of the config. */
    static List<MerchantOffer> catalogue(SmithShopBalance shop, ApprenticeBalance apprentices) {
        List<MerchantOffer> offers = new ArrayList<>();
        offers.add(goldOffer(shop.stonePrice(), ModItems.ENHANCEMENT_STONE.get(), shop.stoneStock()));
        offers.add(goldOffer(shop.stonePrice(), ModItems.ARMOR_ENHANCEMENT_STONE.get(), shop.stoneStock()));
        offers.add(goldOffer(shop.blessedStonePrice(), ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get(), shop.blessedStock()));
        offers.add(goldOffer(shop.blessedStonePrice(), ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE.get(), shop.blessedStock()));
        offers.add(goldOffer(shop.blessedScrollPrice(), ModItems.BLESSED_ENHANCEMENT_SCROLL.get(), shop.blessedStock()));
        offers.add(goldOffer(shop.respecScrollPrice(), ModItems.RESPEC_SCROLL.get(), RESPEC_SCROLL_STOCK));
        offers.add(new MerchantOffer(new ItemCost(ModItems.MITHRIL_INGOT.get(), MITHRIL_INGOTS_BOUGHT),
                new ItemStack(ModItems.GOLD_COIN.get(), shop.mithrilSaleGold()), MITHRIL_STOCK, 0, 0.05f));
        for (ResourceLocation skill : SOLD_SKILLS) {
            offers.add(new MerchantOffer(new ItemCost(ModItems.GOLD_COIN.get(), shop.skillBookPrice()),
                    SkillBookItem.forSkill(ModItems.SKILL_BOOK.get(), skill), SKILL_BOOK_STOCK, 0, 0.05f));
        }
        offers.add(eyeOfAppraiser(shop));
        offers.add(apprenticeContract(apprentices));
        return offers;
    }

    private static MerchantOffer goldOffer(int price, Item item, int stock) {
        return new MerchantOffer(new ItemCost(ModItems.GOLD_COIN.get(), price), new ItemStack(item), stock, 0, 0.05f);
    }

    /** More apprentice blacksmiths, once the smith's level allows them (the first contract he gives for free). */
    private static MerchantOffer apprenticeContract(ApprenticeBalance apprentices) {
        return new MerchantOffer(new ItemCost(ModItems.GOLD_COIN.get(), apprentices.contractPrice()), new ItemStack(ModItems.APPRENTICE_CONTRACT.get()),
                CONTRACT_STOCK, 0, 0.05f);
    }

    /** The most expensive appraisal item, to know what Sancho's old book is. */
    private static MerchantOffer eyeOfAppraiser(SmithShopBalance shop) {
        return new MerchantOffer(new ItemCost(ModItems.GOLD_COIN.get(), shop.eyePrice()), Optional.of(new ItemCost(Items.DIAMOND, 2)),
                new ItemStack(ModItems.APPRAISER_EYE.get()), shop.eyeDailyStock(), 0, 0.05f);
    }

    /** The Eye's uses come back once per Minecraft day, whenever someone opens the shop on a new day. */
    private void refillEyeEachDay(MerchantOffers offers) {
        long today = level().getDayTime() / 24000L;
        if (today == eyeDay) {
            return;
        }
        eyeDay = today;
        offers.stream().filter(offer -> offer.getResult().is(ModItems.APPRAISER_EYE.get())).forEach(MerchantOffer::resetUses);
    }

    /**
     * Smiths whose shop was stocked before these offers existed get them added. One stocked before the economy (it
     * trades in vanilla emeralds) or before the gold coin (older catalogue version) is restocked from scratch, which
     * also refills its spent uses.
     */
    private void stockNewOffers() {
        MerchantOffers offers = getOffers();
        if (shopVersion < SHOP_VERSION || offers.stream().anyMatch(BlacksmithNpc::usesLegacyCurrency)) {
            offers.clear();
            updateTrades();
            return;
        }
        if (offers.stream().noneMatch(offer -> offer.getResult().is(ModItems.APPRAISER_EYE.get()))) {
            offers.add(eyeOfAppraiser(SmithShopBalance.current()));
        }
        if (offers.stream().noneMatch(offer -> offer.getResult().is(ModItems.APPRENTICE_CONTRACT.get()))) {
            offers.add(apprenticeContract(ApprenticeBalance.current()));
        }
        followConfig(offers, catalogue(SmithShopBalance.current(), ApprenticeBalance.current()));
        refillEyeEachDay(offers);
    }

    /**
     * Saved offers keep the price and stock they were made with, so a change in the config would never reach a smith
     * already in the world. Each offer that differs from its catalogue twin takes the new price, payment and stock,
     * keeping the uses already spent (capped at the new stock).
     */
    static void followConfig(MerchantOffers offers, List<MerchantOffer> catalogue) {
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer saved = offers.get(i);
            for (MerchantOffer wanted : catalogue) {
                if (sameDeal(saved, wanted) && !sameTerms(saved, wanted)) {
                    offers.set(i, new MerchantOffer(wanted.getItemCostA(), wanted.getItemCostB(), wanted.getResult().copy(),
                            Math.min(saved.getUses(), wanted.getMaxUses()), wanted.getMaxUses(), wanted.getXp(),
                            wanted.getPriceMultiplier(), saved.getDemand()));
                    break;
                }
            }
        }
    }

    /** The same thing paid with the same item (the skill books differ by the skill they teach). */
    private static boolean sameDeal(MerchantOffer a, MerchantOffer b) {
        return a.getItemCostA().item().equals(b.getItemCostA().item())
                && ItemStack.isSameItemSameComponents(a.getResult(), b.getResult());
    }

    private static boolean sameTerms(MerchantOffer a, MerchantOffer b) {
        return a.getItemCostA().count() == b.getItemCostA().count()
                && a.getItemCostB().equals(b.getItemCostB())
                && a.getResult().getCount() == b.getResult().getCount()
                && a.getMaxUses() == b.getMaxUses();
    }

    private static boolean usesLegacyCurrency(MerchantOffer offer) {
        return offer.getBaseCostA().is(LEGACY_CURRENCY) || offer.getCostB().is(LEGACY_CURRENCY)
                || offer.getResult().is(LEGACY_CURRENCY);
    }

    /** Right-click: his quest window, whose Trade button opens the shop ({@link #startTrading}). */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading()) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide) {
            // Only runs on the client, so the client-only class is never loaded on a server.
            NpcQuestScreen.open(this, true);
        } else if (player instanceof ServerPlayer serverPlayer) {
            ApprenticeContractItem.introduce(serverPlayer);
            EibarStory.talkToSmith(serverPlayer);
            Rumors.hear(serverPlayer, getType());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(SHOP_VERSION_TAG, shopVersion);
        tag.putLong(EYE_DAY_TAG, eyeDay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shopVersion = tag.getInt(SHOP_VERSION_TAG);
        eyeDay = tag.contains(EYE_DAY_TAG) ? tag.getLong(EYE_DAY_TAG) : Long.MIN_VALUE;
    }

    /** Opens the shop for {@code player}, unless someone is already trading with him. */
    public void startTrading(Player player) {
        if (isAlive() && !isTrading()) {
            stockNewOffers();
            if (player instanceof ServerPlayer serverPlayer) {
                // Closed first: the old window frees "its" trader when it closes, which must not undo the new trade.
                serverPlayer.closeContainer();
            }
            setTradingPlayer(player);
            if (player instanceof ServerPlayer serverPlayer) {
                SmithShop.open(serverPlayer, this);
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return NpcRules.isInvulnerableTo(source);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }
}
