package goetic.mods.absolutrevive.common.network;

import goetic.mods.absolutrevive.common.EventHandler;
import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class MessageClientRequest implements CustomPacketPayload {
   public static final Type<MessageClientRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("absolutrevive", "client_request"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.composite(
           ByteBufCodecs.BYTE, message -> (byte)message.type.ordinal(), ordinal -> new MessageClientRequest(RequestType.values()[ordinal])
   );
   private final RequestType type;

   public MessageClientRequest(RequestType type) {
      this.type = type;
   }

   public Type<MessageClientRequest> type() {
      return TYPE;
   }

   public static void handle(MessageClientRequest message, Context context) {
      ServerPlayer player = context.player();
      context.server().execute(() -> {
         if (message.type == RequestType.GIVE_UP) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel) {
               playerDamageModel.giveUp(player);
            }
         } else if (message.type == RequestType.ATTEMPT_RESCUE) {
            EventHandler.attemptImmediateRescue(player);
         }
      });
   }

   public enum RequestType {
      GIVE_UP,
      ATTEMPT_RESCUE;
   }
}