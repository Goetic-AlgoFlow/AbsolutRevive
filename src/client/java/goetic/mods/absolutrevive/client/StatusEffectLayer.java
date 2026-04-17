package goetic.mods.absolutrevive.client;

import goetic.mods.absolutrevive.AbsolutRevive;
import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;

public class StatusEffectLayer implements HudRenderCallback {
   public static final StatusEffectLayer INSTANCE = new StatusEffectLayer();
   private static final int GIVE_UP_BAR_WIDTH = 144;
   private static final int GIVE_UP_BAR_HEIGHT = 8;
   private static final int RESCUE_BAR_WIDTH = 144;
   private static final int RESCUE_BAR_HEIGHT = 8;
   private float painStrength;
   private float lastPainStrength;
   private float suppressionStrength;
   private float lastSuppressionStrength;

   public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && !minecraft.options.hideGui) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
         if (damageModel != null && AbsolutRevive.isSynced) {
            if (minecraft.player.isAlive() || damageModel.getUnconsciousTicks() > 0) {
               PlayerDamageModel playerDamageModel = damageModel instanceof PlayerDamageModel model ? model : null;
               int width = minecraft.getWindow().getGuiScaledWidth();
               int height = minecraft.getWindow().getGuiScaledHeight();
               float deathDanger = playerDamageModel == null ? 0.0F : playerDamageModel.getDeathCountdownDangerProgress();

               float targetPain = 0.0F;

               SuppressionFeedbackController suppressionFeedbackController = ClientEventHandler.getSuppressionFeedbackController();
               float targetSuppression = suppressionFeedbackController.getVisualStrength();

               this.tickStrengths(targetPain, targetSuppression);

               float smoothSuppression = Mth.lerp(deltaTracker.getGameTimeDeltaTicks(), this.lastSuppressionStrength, this.suppressionStrength);
               float pulseTime = minecraft.player.tickCount + deltaTracker.getGameTimeDeltaTicks();

               if (smoothSuppression > 0.0F && minecraft.player.isAlive() && damageModel.getUnconsciousTicks() <= 0) {
                  float pulse = 0.9F + 0.1F * Mth.sin(pulseTime * 0.46F + 0.8F);
                  float intensity = Math.min(2.0F, (0.45F + smoothSuppression * 0.75F) * 2.0F * pulse);
                  renderVignette(guiGraphics, width, height, 18, 24, 34, intensity, 30);
                  renderVignette(guiGraphics, width, height, 88, 102, 128, Math.min(2.0F, intensity * 0.72F), 18);
                  guiGraphics.fill(0, 0, width, height, color(Math.round(12.0F + 48.0F * smoothSuppression * 2.0F), 16, 18, 22));
               }

               if (damageModel.getUnconsciousTicks() > 0) {
                  guiGraphics.fill(0, 0, width, height, color(178, 0, 0, 0));
                  renderVignette(guiGraphics, width, height, 0, 0, 0, 0.8F, 24);
                  if (deathDanger > 0.0F) {
                     renderDeathDangerOverlay(guiGraphics, width, height, deathDanger, pulseTime);
                  }

                  float partialTick = deltaTracker.getGameTimeDeltaTicks();
                  Component title = Component.translatable(
                          playerDamageModel != null
                                  ? playerDamageModel.getUnconsciousReasonKey()
                                  : (damageModel.isCriticalConditionActive() ? "absolutrevive.gui.critical_condition" : "absolutrevive.gui.unconscious")
                  );
                  Component timer = playerDamageModel != null && playerDamageModel.canGiveUp()
                          ? Component.translatable(
                          "absolutrevive.gui.death_countdown_seconds", new Object[]{formatPreciseSeconds(damageModel.getUnconsciousTicks(), partialTick)}
                  )
                          : Component.translatable(
                          "absolutrevive.gui.unconscious_left", new Object[]{StringUtil.formatTickDuration(damageModel.getUnconsciousTicks(), 20.0F)}
                  );
                  int centerX = width / 2;
                  int centerY = height / 2;
                  guiGraphics.drawCenteredString(minecraft.font, title, centerX, centerY - 26, opaque(16773617));
                  guiGraphics.drawCenteredString(minecraft.font, timer, centerX, centerY - 10, opaque(13619151));
                  if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                     guiGraphics.drawCenteredString(
                             minecraft.font, Component.translatable("absolutrevive.gui.waiting_for_rescue"), centerX, centerY + 2, opaque(15260121)
                     );
                     guiGraphics.drawCenteredString(minecraft.font, Component.translatable("absolutrevive.gui.rescue_help"), centerX, centerY + 14, opaque(14207690));
                     guiGraphics.drawCenteredString(
                             minecraft.font,
                             Component.translatable("absolutrevive.gui.give_up_hint", new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}),
                             centerX,
                             centerY + 28,
                             opaque(16757683)
                     );
                     renderGiveUpProgress(guiGraphics, minecraft, centerX, centerY + 44, partialTick);
                  }
               } else if (ClientEventHandler.hasInteractionPrompt()) {
                  renderRescuePrompt(guiGraphics, minecraft, width / 2, height / 2 + 24, deltaTracker.getGameTimeDeltaTicks());
               }
            }
         }
      }
   }

   private void tickStrengths(float targetPain, float targetSuppression) {
      this.lastPainStrength = this.painStrength;
      this.lastSuppressionStrength = this.suppressionStrength;
      this.painStrength = approachStrength(this.painStrength, targetPain, 0.045F, 0.015F);
      this.suppressionStrength = approachStrength(this.suppressionStrength, targetSuppression, 0.18F, 0.012F);
   }

   private static float approachStrength(float current, float target, float gain, float decay) {
      return target > current ? Math.min(target, current + gain) : Math.max(target, current - decay);
   }

   private static void renderVignette(GuiGraphics guiGraphics, int width, int height, int red, int green, int blue, float intensity, int baseThickness) {
      if (!(intensity <= 0.0F)) {
         int layers = 7;

         for (int layer = 0; layer < layers; layer++) {
            float progress = (float)(layer + 1) / layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(4, Math.round(baseThickness * (0.35F + progress * (1.15F + intensity * 0.95F))));
            int alpha = Math.round((8.0F + 76.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
               fillEdge(guiGraphics, width, height, color(alpha, red, green, blue), thickness);
            }
         }

         guiGraphics.fill(0, 0, width, height, color(Math.round(6.0F + 18.0F * intensity), red, green, blue));
      }
   }

   private static void fillEdge(GuiGraphics guiGraphics, int width, int height, int color, int thickness) {
      guiGraphics.fill(0, 0, width, thickness, color);
      guiGraphics.fill(0, height - thickness, width, height, color);
      guiGraphics.fill(0, thickness, thickness, height - thickness, color);
      guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
   }

   private static void renderDeathDangerOverlay(GuiGraphics guiGraphics, int width, int height, float deathDanger, float pulseTime) {
      float pulse = 0.72F + (0.2F + deathDanger * 0.24F) * Mth.sin(pulseTime * (0.07F + deathDanger * 0.03F));
      float intensity = Mth.clamp(deathDanger * pulse, 0.0F, 1.0F);
      int redCoverAlpha = Math.round(16.0F + 132.0F * deathDanger);
      guiGraphics.fill(0, 0, width, height, color(redCoverAlpha, 90, 0, 0));
      renderVignette(guiGraphics, width, height, 160, 10, 10, 0.18F + intensity * 0.82F, 28);
   }

   private static void renderGiveUpProgress(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int top, float partialTick) {
      int left = centerX - 72;
      int right = left + 144;
      int bottom = top + 8;
      float progress = ClientEventHandler.getGiveUpHoldProgress(partialTick);
      int fillWidth = Math.round(142.0F * progress);
      guiGraphics.fill(left, top, right, bottom, color(180, 24, 6, 6));
      guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, color(180, 50, 12, 12));
      if (fillWidth > 0) {
         guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, color(220, 186, 32, 32));
      }

      guiGraphics.drawCenteredString(
              minecraft.font,
              Component.translatable(
                      "absolutrevive.gui.give_up_progress",
                      new Object[]{
                              formatSingleDecimal(ClientEventHandler.getGiveUpHoldSeconds(partialTick)),
                              formatSingleDecimal(ClientEventHandler.getGiveUpHoldDurationSeconds())
                      }
              ),
              centerX,
              top + 12,
              opaque(16757683)
      );
   }

   private static void renderRescuePrompt(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int centerY, float partialTick) {
      boolean executionPrompt = ClientEventHandler.isExecutionInteractionPrompt();
      guiGraphics.drawCenteredString(
              minecraft.font, ClientEventHandler.getInteractionPromptTitle(), centerX, centerY - 26, executionPrompt ? opaque(16767436) : opaque(15333346)
      );
      guiGraphics.drawCenteredString(
              minecraft.font, ClientEventHandler.getInteractionPromptDetail(), centerX, centerY - 12, executionPrompt ? opaque(15717458) : opaque(13624517)
      );
      if (ClientEventHandler.getInteractionHoldDurationSeconds() <= 0.0F) {
         return;
      }

      int left = centerX - 72;
      int right = left + 144;
      int top = centerY + 2;
      int bottom = top + 8;
      float progress = ClientEventHandler.getInteractionHoldProgress(partialTick);
      int fillWidth = Math.round(142.0F * progress);
      guiGraphics.fill(left, top, right, bottom, executionPrompt ? color(180, 48, 8, 8) : color(180, 10, 38, 14));
      guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, executionPrompt ? color(180, 82, 18, 18) : color(180, 24, 74, 28));
      if (fillWidth > 0) {
         guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, executionPrompt ? color(220, 232, 70, 70) : color(220, 126, 214, 110));
      }

      guiGraphics.drawCenteredString(
              minecraft.font,
              ClientEventHandler.getInteractionPromptProgressText(partialTick),
              centerX,
              top + 12,
              executionPrompt ? opaque(16760992) : opaque(14217424)
      );
   }

   private static String formatPreciseSeconds(int remainingTicks, float partialTick) {
      float seconds = Math.max(0.1F, (Math.max(0, remainingTicks) - Math.max(0.0F, partialTick)) / 20.0F);
      return formatSingleDecimal(seconds);
   }

   private static String formatSingleDecimal(float value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private static int color(int alpha, int red, int green, int blue) {
      return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
   }

   private static int opaque(int rgb) {
      return 0xFF000000 | rgb;
   }
}