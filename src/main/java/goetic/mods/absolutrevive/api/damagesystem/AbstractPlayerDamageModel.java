package goetic.mods.absolutrevive.api.damagesystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class AbstractPlayerDamageModel {
    public boolean hasTutorial = false;

    public abstract CompoundTag serializeNBT();
    public abstract void deserializeNBT(CompoundTag nbt);
    public abstract void tick(Level world, Player player);

    public abstract boolean isDead(Player player);
    public abstract void sleepHeal(Player player);
    public abstract int getCurrentMaxHealth();
    public abstract void revivePlayer(Player player);
    public abstract void runScaleLogic(Player player);
    public abstract void scheduleResync();
    public abstract boolean hasNoCritical();

    public abstract int getUnconsciousTicks();
    public abstract boolean isCriticalConditionActive();
}