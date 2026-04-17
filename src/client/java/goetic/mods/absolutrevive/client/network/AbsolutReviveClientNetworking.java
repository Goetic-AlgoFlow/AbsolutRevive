package goetic.mods.absolutrevive.client.network;

import goetic.mods.absolutrevive.common.network.MessageSyncDamageModel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class AbsolutReviveClientNetworking {
   private static boolean handlersRegistered = false;

   private AbsolutReviveClientNetworking() {}

   public static void registerClient() {
      if (!handlersRegistered) {
         handlersRegistered = true;
         ClientPlayNetworking.registerGlobalReceiver(MessageSyncDamageModel.TYPE, MessageSyncDamageModelHandler::handle);
      }
   }

   public static void sendToServer(CustomPacketPayload message) {
      ClientPlayNetworking.send(message);
   }
}