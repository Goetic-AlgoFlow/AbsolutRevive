package goetic.mods.absolutrevive.fabric;

import goetic.mods.absolutrevive.AbsolutReviveClient;
import net.fabricmc.api.ClientModInitializer;

public class AbsolutReviveFabricClientEntrypoint implements ClientModInitializer {
   public void onInitializeClient() {
      AbsolutReviveClient.initClient();
   }
}
