package goetic.mods.absolutrevive.mixin;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerDimensionsMixin {
   @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
   private void absolutrevive$getDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
      if ((Object)this instanceof Player player) {
         if (!player.isPassenger()) {
            if (CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious()) {
               cir.setReturnValue(PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
            }
         }
      }
   }
}
