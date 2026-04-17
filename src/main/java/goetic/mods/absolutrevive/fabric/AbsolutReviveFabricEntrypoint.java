package goetic.mods.absolutrevive.fabric;

import goetic.mods.absolutrevive.AbsolutRevive;
import net.fabricmc.api.ModInitializer;

public class AbsolutReviveFabricEntrypoint implements ModInitializer {
   public void onInitialize() {
      AbsolutRevive.initCommon();
   }
}
