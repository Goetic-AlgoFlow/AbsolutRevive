package goetic.mods.absolutrevive.mixin;

import goetic.mods.absolutrevive.common.EventHandler;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void absolutrevive$blockContainerClickWhenKO(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (this.player != null && EventHandler.isUnconscious(this.player)) {
            ci.cancel();
            this.player.inventoryMenu.sendAllDataToRemote();
        }
    }
}