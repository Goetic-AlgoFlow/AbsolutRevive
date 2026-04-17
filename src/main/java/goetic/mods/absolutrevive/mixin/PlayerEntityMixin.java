package goetic.mods.absolutrevive.mixin;

import goetic.mods.absolutrevive.common.AbsolutReviveDamageModelHolder;

import javax.annotation.Nullable;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerEntityMixin implements AbsolutReviveDamageModelHolder {
   private static final String FIRSTAID_NBT_KEY = "AbsolutReviveDamageModel";
   @Unique
   private PlayerDamageModel absolutrevive$damageModel;

   @Override
   public PlayerDamageModel absolutrevive$getDamageModel() {
      if (this.absolutrevive$damageModel == null) {
         this.absolutrevive$damageModel = new PlayerDamageModel();
      }

      return this.absolutrevive$damageModel;
   }

   @Nullable
   @Override
   public PlayerDamageModel absolutrevive$getDamageModelNullable() {
      return this.absolutrevive$damageModel;
   }

   @Override
   public void absolutrevive$setDamageModel(PlayerDamageModel model) {
      this.absolutrevive$damageModel = model;
   }

   @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
   private void absolutrevive$readAdditionalSaveData(ValueInput input, CallbackInfo ci) {
      input.read("AbsolutReviveDamageModel", CompoundTag.CODEC).ifPresent(tag -> this.absolutrevive$getDamageModel().deserializeNBT(tag));
   }

   @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
   private void absolutrevive$addAdditionalSaveData(ValueOutput output, CallbackInfo ci) {
      PlayerDamageModel model = this.absolutrevive$getDamageModelNullable();
      if (model != null) {
         output.store("AbsolutReviveDamageModel", CompoundTag.CODEC, model.serializeNBT());
      }
   }
}
