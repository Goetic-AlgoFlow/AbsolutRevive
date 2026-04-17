package goetic.mods.absolutrevive.common.damagesystem;

import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.network.AbsolutReviveNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public class PlayerDamageModel extends AbstractPlayerDamageModel {
    private static final EntityDimensions UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.6F);
    private static final EntityDimensions CRAMPED_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.6F);
    private static final Identifier ATTR_UNCONSCIOUS = Identifier.fromNamespaceAndPath("absolutrevive", "unconscious");

    private int unconsciousTicks = 0;
    private boolean criticalConditionActive = false;
    private boolean unconsciousAllowsGiveUp = false;
    private boolean unconsciousCausesDeath = false;
    private String unconsciousReasonKey = "";
    private int collapseAnimationTicks = 0;
    private boolean externalRevivePending = false;

    public PlayerDamageModel() {}

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("unconsciousTicks", this.unconsciousTicks);
        tag.putBoolean("criticalConditionActive", this.criticalConditionActive);
        tag.putBoolean("unconsciousAllowsGiveUp", this.unconsciousAllowsGiveUp);
        tag.putBoolean("unconsciousCausesDeath", this.unconsciousCausesDeath);
        tag.putBoolean("externalRevivePending", this.externalRevivePending);
        tag.putInt("collapseAnimationTicks", this.collapseAnimationTicks);
        if (!this.unconsciousReasonKey.isEmpty()) tag.putString("unconsciousReasonKey", this.unconsciousReasonKey);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.unconsciousTicks = nbt.getIntOr("unconsciousTicks", 0);
        this.criticalConditionActive = nbt.getBooleanOr("criticalConditionActive", false);
        this.unconsciousAllowsGiveUp = nbt.getBooleanOr("unconsciousAllowsGiveUp", this.criticalConditionActive);
        this.unconsciousCausesDeath = nbt.getBooleanOr("unconsciousCausesDeath", this.criticalConditionActive);
        this.externalRevivePending = nbt.getBooleanOr("externalRevivePending", false);
        this.unconsciousReasonKey = nbt.getStringOr("unconsciousReasonKey", this.criticalConditionActive ? "absolutrevive.gui.critical_condition" : "");
        this.collapseAnimationTicks = nbt.getIntOr("collapseAnimationTicks", 0);
    }

    @Override
    public void tick(Level world, Player player) {
        if (this.unconsciousTicks > 0) {
            this.unconsciousTicks--;
            this.applyUnconsciousPenalties(player);

            if (!world.isClientSide()) {
                if (this.unconsciousTicks <= 0 && this.unconsciousCausesDeath) {
                    this.resetRecoveredPlayerState(player);
                    player.setHealth(0.0F);
                    player.die(player.damageSources().fellOutOfWorld());
                    return;
                }

                if (this.unconsciousTicks <= 0) {
                    this.clearUnconsciousState();
                    this.resetRecoveredPlayerState(player);
                }
            }
        } else {
            this.clearUnconsciousPenalties(player);
        }

        if (this.collapseAnimationTicks > 0) {
            this.collapseAnimationTicks--;
        }
    }

    public void setUnconsciousState(int ticks, boolean allowsGiveUp, boolean causesDeath, String reasonKey) {
        this.unconsciousTicks = ticks;
        this.unconsciousAllowsGiveUp = allowsGiveUp;
        this.unconsciousCausesDeath = causesDeath;
        this.unconsciousReasonKey = reasonKey;
        this.criticalConditionActive = true;
        this.collapseAnimationTicks = 12;
    }

    public void triggerCollapseAnimation() {
        this.collapseAnimationTicks = 12;
    }

    public void clearUnconsciousState() {
        this.unconsciousTicks = 0;
        this.unconsciousAllowsGiveUp = false;
        this.unconsciousCausesDeath = false;
        this.unconsciousReasonKey = "";
        this.criticalConditionActive = false;
        this.collapseAnimationTicks = 0;
    }

    public boolean isUnconscious() { return this.unconsciousTicks > 0; }
    public boolean canGiveUp() { return this.isUnconscious() && this.unconsciousAllowsGiveUp; }

    public void giveUp(Player player) {
        if (this.canGiveUp()) {
            this.clearUnconsciousState();
            this.resetRecoveredPlayerState(player);
            player.setHealth(0.0F);
            player.die(player.damageSources().generic());
        }
    }

    public boolean canBeRescued() { return this.isUnconscious(); }

    public boolean rescueFromCriticalState(Player player) {
        if (!this.canBeRescued()) return false;
        this.clearUnconsciousState();
        this.resetRecoveredPlayerState(player);

        player.setHealth(8.0F);

        player.getFoodData().setFoodLevel(4);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            AbsolutReviveNetworking.sendDamageModelSync(serverPlayer, this, false);
        }
        return true;
    }

    public float getCollapseAnimationProgress(float partialTick) {
        return !this.isUnconscious() ? 1.0F : Mth.clamp(1.0F - (Math.max(0.0F, (float)this.collapseAnimationTicks) - Math.max(0.0F, partialTick)) / 12.0F, 0.0F, 1.0F);
    }

    public String getUnconsciousReasonKey() {
        return this.unconsciousReasonKey.isEmpty() ? "absolutrevive.gui.unconscious" : this.unconsciousReasonKey;
    }

    public float getDeathCountdownDangerProgress() {
        if (!this.canGiveUp()) return 0.0F;
        float remaining = Math.max(0.0F, (float)this.unconsciousTicks);
        float progress = 1.0F - remaining / 1800.0F;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    public void markExternalRevivePending(Player player) {
        this.externalRevivePending = true;
        this.clearUnconsciousState();
        this.resetRecoveredPlayerState(player);
        if (player instanceof ServerPlayer serverPlayer) AbsolutReviveNetworking.sendDamageModelSync(serverPlayer, this, false);
    }

    private void applyUnconsciousPenalties(Player player) {
        player.setSprinting(false);
        player.stopUsingItem();
        this.updateUnconsciousAttributes(player, true);

        player.xxa = 0.0F;
        player.yya = 0.0F;
        player.zza = 0.0F;
        player.setJumping(false);

        player.setPose(this.shouldUseCrampedUnconsciousDimensions(player) ? Pose.CROUCHING : Pose.STANDING);
    }

    private void clearUnconsciousPenalties(Player player) {
        this.updateUnconsciousAttributes(player, false);
    }

    private void resetRecoveredPlayerState(Player player) {
        this.clearUnconsciousPenalties(player);
        player.setShiftKeyDown(false);
        player.refreshDimensions();
    }

    private void updateUnconsciousAttributes(Player player, boolean unconscious) {
        AttributeMap map = player.getAttributes();
        updateUnconsciousModifier(map, Attributes.MOVEMENT_SPEED, unconscious);
        updateUnconsciousModifier(map, Attributes.JUMP_STRENGTH, unconscious);
        updateUnconsciousModifier(map, Attributes.ATTACK_SPEED, unconscious);
    }

    private void updateUnconsciousModifier(AttributeMap map, net.minecraft.core.Holder<Attribute> attribute, boolean unconscious) {
        AttributeInstance instance = map.getInstance(attribute);
        if (instance != null) {
            if (unconscious) {
                if (!instance.hasModifier(ATTR_UNCONSCIOUS)) instance.addTransientModifier(new AttributeModifier(ATTR_UNCONSCIOUS, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
            } else if (instance.hasModifier(ATTR_UNCONSCIOUS)) {
                instance.removeModifier(ATTR_UNCONSCIOUS);
            }
        }
    }

    public static EntityDimensions getUnconsciousDimensions(boolean cramped) {
        return cramped ? CRAMPED_UNCONSCIOUS_DIMENSIONS : UNCONSCIOUS_DIMENSIONS;
    }

    public boolean shouldUseCrampedUnconsciousDimensions(Player player) {
        AABB boundingBox = UNCONSCIOUS_DIMENSIONS.makeBoundingBox(player.getX(), player.getY(), player.getZ());
        return !player.level().noCollision(player, boundingBox);
    }

    @Override public void sleepHeal(Player player) {}
    @Override public boolean isDead(Player player) { return !player.isAlive(); }
    @Override public int getCurrentMaxHealth() { return 20; }
    @Override public void runScaleLogic(Player player) {}
    @Override public void scheduleResync() {}
    @Override public boolean hasNoCritical() { return true; }
    @Override public void revivePlayer(Player player) {}
    @Override public int getUnconsciousTicks() { return this.unconsciousTicks; }
    @Override public boolean isCriticalConditionActive() { return this.criticalConditionActive; }
}