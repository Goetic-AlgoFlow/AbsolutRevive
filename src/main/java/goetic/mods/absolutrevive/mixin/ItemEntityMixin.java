package goetic.mods.absolutrevive.mixin;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void absolutrevive$preventItemPickup(Player player, CallbackInfo ci) {
        if (CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel pdm && pdm.isUnconscious()) {
            ci.cancel();
        }
    }
}