package goetic.mods.absolutrevive.mixin.client;

import goetic.mods.absolutrevive.client.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void absolutrevive$blockInventoryScreenWhenKO(Screen screen, CallbackInfo ci) {
        if (screen instanceof InventoryScreen) {
            Minecraft mc = (Minecraft)(Object)this;
            if (mc.player != null && ClientEventHandler.isUnconscious(mc.player)) {
                ci.cancel();
            }
        }
    }
}