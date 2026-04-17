package goetic.mods.absolutrevive.client.network;

import goetic.mods.absolutrevive.AbsolutRevive;
import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.network.MessageSyncDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class MessageSyncDamageModelHandler {
   public static void handle(MessageSyncDamageModel message, Context context) {
      context.client().execute(() -> {
         Minecraft mc = context.client();
         if (mc.level != null && mc.player != null) {
            Player targetPlayer = mc.player.getId() == message.entityId() ? mc.player : (Player) mc.level.getEntity(message.entityId());
            if (targetPlayer != null) {
               AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(targetPlayer);

               if (damageModel instanceof PlayerDamageModel pdm) {
                  boolean wasUnconscious = pdm.isUnconscious();
                  pdm.deserializeNBT(message.playerDamageModel());
                  boolean isUnconscious = pdm.isUnconscious();

                  if (!wasUnconscious && isUnconscious) {
                     if (pdm.getUnconsciousTicks() > 1750) {
                        pdm.triggerCollapseAnimation();
                     }
                  }

                  if (targetPlayer == mc.player) {
                     AbsolutRevive.isSynced = true;
                  }

                  if (wasUnconscious != isUnconscious) {
                     targetPlayer.refreshDimensions();
                  }
               }
            }
         }
      });
   }
}