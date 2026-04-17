package goetic.mods.absolutrevive.common.network;

import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class AbsolutReviveNetworking {
   private static boolean typesRegistered = false;
   private static boolean serverHandlersRegistered = false;

   private AbsolutReviveNetworking() {
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("absolutrevive", path);
   }

   public static void registerCommon() {
      if (!typesRegistered) {
         typesRegistered = true;
         PayloadTypeRegistry.playC2S().register(MessageClientRequest.TYPE, MessageClientRequest.STREAM_CODEC);
         PayloadTypeRegistry.playS2C().register(MessageSyncDamageModel.TYPE, MessageSyncDamageModel.STREAM_CODEC);
      }

      if (!serverHandlersRegistered) {
         serverHandlersRegistered = true;
         ServerPlayNetworking.registerGlobalReceiver(MessageClientRequest.TYPE, MessageClientRequest::handle);
      }
   }

   public static void sendDamageModelSync(ServerPlayer player, AbstractPlayerDamageModel model, boolean scaleMaxHealth) {
      MessageSyncDamageModel payload = new MessageSyncDamageModel(player.getId(), model, scaleMaxHealth);
      ServerPlayNetworking.send(player, payload);

      for (ServerPlayer trackingPlayer : PlayerLookup.tracking(player)) {
         if (trackingPlayer != player) {
            ServerPlayNetworking.send(trackingPlayer, payload);
         }
      }
   }
}