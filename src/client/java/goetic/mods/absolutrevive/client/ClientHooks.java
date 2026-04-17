package goetic.mods.absolutrevive.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import goetic.mods.absolutrevive.AbsolutRevive;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public final class ClientHooks {
   private static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("absolutrevive", "absolutrevive"));

   public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", Type.KEYSYM, 71, CATEGORY);

   private ClientHooks() {
   }

   public static void setup() {
      AbsolutRevive.LOGGER.debug("Loading ClientHooks (KO Mod Only)");
      KeyBindingHelper.registerKeyBinding(GIVE_UP);

      HudRenderCallback.EVENT.register(StatusEffectLayer.INSTANCE);

      ClientEventHandler.register();
   }
}