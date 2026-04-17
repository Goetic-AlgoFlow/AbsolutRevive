package goetic.mods.absolutrevive;

import goetic.mods.absolutrevive.client.ClientHooks;
import goetic.mods.absolutrevive.client.network.AbsolutReviveClientNetworking;

public final class AbsolutReviveClient {
   private AbsolutReviveClient() {}

   public static void initClient() {
      ClientHooks.setup();
      AbsolutReviveClientNetworking.registerClient();
   }
}