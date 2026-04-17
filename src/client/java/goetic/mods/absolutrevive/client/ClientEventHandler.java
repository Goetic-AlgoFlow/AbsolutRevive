package goetic.mods.absolutrevive.client;

import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.client.network.AbsolutReviveClientNetworking;
import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.network.MessageClientRequest;
import goetic.mods.absolutrevive.common.network.MessageClientRequest.RequestType;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ClientEventHandler {
   private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
   private static int giveUpHoldTicks;
   private static boolean giveUpTriggered;
   private static int interactionHoldTicks;
   private static boolean interactionTriggered;
   private static InteractionPrompt interactionPrompt;

   // Mémorise les objets que vous aviez en main pour réagir instantanément au changement
   private static net.minecraft.world.item.Item lastMainHand = null;
   private static net.minecraft.world.item.Item lastOffHand = null;

   private static final Map<UUID, Float> lockedRotations = new HashMap<>();

   public static void register() {
      ClientTickEvents.START_CLIENT_TICK.register(ClientEventHandler::clientTick);
      ClientTickEvents.END_CLIENT_TICK.register(ClientEventHandler::endClientTick);
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
      ClientPreAttackCallback.EVENT.register(ClientEventHandler::onPreAttack);
   }

   private static boolean holdsDefibrillator(Player player) {
      if (player == null) return false;
      String main = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
      String off = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).getPath();
      return main.equals("defibrillator") || off.equals("defibrillator");
   }

   private static void clientTick(Minecraft mc) {
      if (mc.level == null || mc.player == null) {
         resetGiveUpHoldState();
      } else if (!mc.isPaused()) {

         if (isUnconscious(mc.player)) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyShift.setDown(false);

            mc.options.keyInventory.setDown(false);
            mc.options.keySwapOffhand.setDown(false);
            mc.options.keyDrop.setDown(false);

            if (mc.screen instanceof InventoryScreen) {
               mc.setScreen(null);
            }
         }

         SUPPRESSION_FEEDBACK_CONTROLLER.tick(mc);

         for (Player player : mc.level.players()) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel instanceof PlayerDamageModel pdm) {
               pdm.tick(mc.level, player);
            }
         }

         AbstractPlayerDamageModel localModel = CommonUtils.getDamageModel(mc.player);
         if (localModel instanceof PlayerDamageModel playerDamageModel) {
            updateGiveUpHoldState(mc, playerDamageModel);
            updateInteractionPromptState(mc);
         } else {
            resetGiveUpHoldState();
            resetInteractionPromptState();
         }
      }
   }

   private static void endClientTick(Minecraft mc) {
      if (mc.level != null && !mc.isPaused()) {

         for (Player player : mc.level.players()) {
            if (isUnconscious(player)) {

               if (player == mc.player) {
                  player.xxa = 0.0F;
                  player.yya = 0.0F;
                  player.zza = 0.0F;
                  player.setJumping(false);
                  player.setSprinting(false);
                  player.setShiftKeyDown(false);

                  if (player.isInWater()) {
                     player.setDeltaMovement(0.0D, -0.05D, 0.0D);
                  } else {
                     player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
                  }
               }

               UUID playerId = player.getUUID();
               if (!lockedRotations.containsKey(playerId)) {
                  lockedRotations.put(playerId, player.yBodyRot);
               }

               float lockedRot = lockedRotations.get(playerId);
               player.yBodyRot = lockedRot;
               player.yBodyRotO = lockedRot;

            } else {
               lockedRotations.remove(player.getUUID());
            }
         }
      } else {
         lockedRotations.clear();
      }
   }

   private static boolean onPreAttack(Minecraft minecraft, LocalPlayer player, int clickCount) {
      return isUnconscious(player);
   }

   private static void onDisconnect() {
      resetGiveUpHoldState();
      resetInteractionPromptState();
      SUPPRESSION_FEEDBACK_CONTROLLER.clear();
      lockedRotations.clear();
      lastMainHand = null;
      lastOffHand = null;
   }

   public static boolean isUnconscious(Player player) {
      return CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel pdm && pdm.isUnconscious();
   }

   public static SuppressionFeedbackController getSuppressionFeedbackController() {
      return SUPPRESSION_FEEDBACK_CONTROLLER;
   }

   private static void updateGiveUpHoldState(Minecraft mc, PlayerDamageModel playerDamageModel) {
      if (!playerDamageModel.canGiveUp() || mc.screen != null) {
         resetGiveUpHoldState();
      } else if (!isGiveUpKeyHeld()) {
         resetGiveUpHoldState();
      } else if (!giveUpTriggered) {
         giveUpHoldTicks = Math.min(60, giveUpHoldTicks + 1);
         if (giveUpHoldTicks >= 60) {
            giveUpTriggered = true;
            AbsolutReviveClientNetworking.sendToServer(new MessageClientRequest(RequestType.GIVE_UP));
         }
      }
   }

   private static boolean isGiveUpKeyHeld() {
      Minecraft mc = Minecraft.getInstance();
      return mc.screen == null && ClientHooks.GIVE_UP.isDown();
   }

   private static void resetGiveUpHoldState() {
      giveUpHoldTicks = 0;
      giveUpTriggered = false;
   }

   public static float getGiveUpHoldProgress(float partialTick) {
      return Math.min(1.0F, getDisplayedGiveUpHoldTicks(partialTick) / 60.0F);
   }

   public static float getGiveUpHoldSeconds(float partialTick) {
      return getDisplayedGiveUpHoldTicks(partialTick) / 20.0F;
   }

   public static float getGiveUpHoldDurationSeconds() {
      return 3.0F;
   }

   private static float getDisplayedGiveUpHoldTicks(float partialTick) {
      if (giveUpHoldTicks <= 0) return 0.0F;
      float extraTicks = isGiveUpKeyHeld() && !giveUpTriggered ? Math.max(0.0F, partialTick) : 0.0F;
      return Math.min(60.0F, giveUpHoldTicks + extraTicks);
   }

   private static void updateInteractionPromptState(Minecraft mc) {
      InteractionPrompt nextPrompt = findInteractionPrompt(mc);

      // On lit les objets actuellement tenus
      net.minecraft.world.item.Item currentMain = mc.player.getMainHandItem().getItem();
      net.minecraft.world.item.Item currentOff = mc.player.getOffhandItem().getItem();

      // Si le joueur lâche la touche, change de cible, OU CHANGE N'IMPORTE QUEL ITEM EN MAIN : on reset l'interface !
      if (interactionPrompt == null || nextPrompt == null || interactionPrompt.targetId() != nextPrompt.targetId()
              || lastMainHand != currentMain || lastOffHand != currentOff) {
         interactionHoldTicks = 0;
         interactionTriggered = false;
      }

      interactionPrompt = nextPrompt;
      lastMainHand = currentMain;
      lastOffHand = currentOff;

      if (interactionPrompt != null && mc.screen == null && interactionPrompt.isSneaking()) {
         int holdDurationTicks = holdsDefibrillator(mc.player) ? 60 : 160;
         interactionHoldTicks = Math.min(holdDurationTicks, interactionHoldTicks + 1);

         if (interactionHoldTicks >= holdDurationTicks && !interactionTriggered) {
            interactionTriggered = true;
            AbsolutReviveClientNetworking.sendToServer(new MessageClientRequest(RequestType.ATTEMPT_RESCUE));
         }
      } else {
         interactionHoldTicks = 0;
         interactionTriggered = false;
      }
   }

   private static InteractionPrompt findInteractionPrompt(Minecraft mc) {
      if (mc.player != null && mc.level != null && mc.player.isAlive() && !isUnconscious(mc.player)) {
         Player closestTarget = null;
         double closestDistanceSqr = 9.0;
         for (Player candidate : mc.level.players()) {
            if (candidate != mc.player && candidate.isAlive() && CommonUtils.getDamageModel(candidate) instanceof PlayerDamageModel pdm && pdm.canBeRescued()) {
               double distanceSqr = mc.player.distanceToSqr(candidate);
               if (distanceSqr < closestDistanceSqr) {
                  closestDistanceSqr = distanceSqr;
                  closestTarget = candidate;
               }
            }
         }
         if (closestTarget == null) return null;
         return new InteractionPrompt(closestTarget.getId(), closestTarget.getDisplayName().copy(), mc.player.isCrouching());
      }
      return null;
   }

   public static boolean hasInteractionPrompt() { return interactionPrompt != null; }
   public static boolean isExecutionInteractionPrompt() { return false; }

   public static Component getInteractionPromptTitle() {
      return interactionPrompt == null ? Component.empty() : Component.translatable("absolutrevive.gui.rescue_prompt_title", interactionPrompt.targetName()).withStyle(ChatFormatting.GREEN);
   }

   public static Component getInteractionPromptDetail() {
      return interactionPrompt == null ? Component.empty() : Component.translatable("absolutrevive.gui.rescue_prompt_crouch", formatSingleDecimal(getInteractionHoldDurationSeconds())).withStyle(ChatFormatting.GREEN);
   }

   public static float getInteractionHoldDurationSeconds() {
      Minecraft mc = Minecraft.getInstance();
      return holdsDefibrillator(mc.player) ? 3.0F : 8.0F;
   }

   public static float getInteractionHoldProgress(float partialTick) {
      float maxTicks = holdsDefibrillator(Minecraft.getInstance().player) ? 60.0F : 160.0F;
      return Math.min(1.0F, getDisplayedInteractionHoldTicks(partialTick) / maxTicks);
   }

   public static Component getInteractionPromptProgressText(float partialTick) {
      return interactionPrompt == null ? Component.empty() : Component.translatable("absolutrevive.gui.rescue_progress", formatSingleDecimal(getDisplayedInteractionHoldTicks(partialTick) / 20.0F), formatSingleDecimal(getInteractionHoldDurationSeconds())).withStyle(ChatFormatting.GREEN);
   }

   private static float getDisplayedInteractionHoldTicks(float partialTick) {
      if (interactionHoldTicks <= 0) return 0.0F;
      float extraTicks = interactionPrompt != null && interactionPrompt.isSneaking() && Minecraft.getInstance().screen == null ? Math.max(0.0F, partialTick) : 0.0F;
      float maxTicks = holdsDefibrillator(Minecraft.getInstance().player) ? 60.0F : 160.0F;
      return Math.min(maxTicks, interactionHoldTicks + extraTicks);
   }

   private static void resetInteractionPromptState() {
      interactionHoldTicks = 0;
      interactionTriggered = false;
      interactionPrompt = null;
   }

   private static String formatSingleDecimal(float value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private record InteractionPrompt(int targetId, Component targetName, boolean isSneaking) {}
}