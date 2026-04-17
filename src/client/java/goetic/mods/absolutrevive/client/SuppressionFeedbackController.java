package goetic.mods.absolutrevive.client;

import javax.annotation.Nullable;

import goetic.mods.absolutrevive.AbsolutRevive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;

public final class SuppressionFeedbackController {
   @Nullable
   private Level trackedLevel;
   private HeartbeatSound currentHeartbeat = null;
   private boolean wasKO = false;

   public boolean isActive() { return false; }

   public void tick(Minecraft client) {
      Player player = client.player;
      Level level = client.level;

      if (player != null && level != null && AbsolutRevive.isSynced) {
         if (this.trackedLevel != level || player.tickCount < 20) {
            this.trackedLevel = level;
            this.clear();
         }

         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         boolean isKO = damageModel != null && damageModel.getUnconsciousTicks() > 0;
         boolean isDead = !player.isAlive() || player.isSpectator();

         if (isDead) {
            this.clear();
            return;
         }

         if (isKO) {
            if (this.currentHeartbeat == null || this.currentHeartbeat.isStopped()) {
               this.currentHeartbeat = new HeartbeatSound();
               client.getSoundManager().play(this.currentHeartbeat);
            }
         } else if (this.wasKO) {
            this.clear();
         }
         this.wasKO = isKO;

      } else {
         this.trackedLevel = null;
         this.clear();
      }
   }

   public void clear() {
      if (this.currentHeartbeat != null) {
         this.currentHeartbeat.stopSound();
      }
      this.currentHeartbeat = null;
      this.wasKO = false;
   }

   public float getSuppressionIntensity() { return 0.0F; }
   public int getHoldTicks() { return 0; }
   public float getVisualStrength() { return 0.0F; }
   public float getAudioMuffleStrength() { return 0.0F; }
   public float getTinnitusStrength() { return 0.0F; }
   @Nullable public SoundInstance maybeMuffle(@Nullable SoundInstance original) { return original; }
   public float getSustainedFovCompression() { return 0.0F; }
   public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) { }
   public SuppressionFeedbackController.CameraAngles applyCameraAngles(@Nullable Entity entity, float partialTick, float yaw, float pitch) {
      return new SuppressionFeedbackController.CameraAngles(yaw, pitch, 0.0F);
   }
   public float applyFov(float baseFov) { return baseFov; }
   public record CameraAngles(float yaw, float pitch, float roll) {}

   private class HeartbeatSound extends AbstractTickableSoundInstance {
      public HeartbeatSound() {
         super(AbsolutRevive.HEARTBEAT_SOUND, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.looping = true;
         this.delay = 0;
         this.volume = 1.0F;
         this.pitch = 1.0F;
         this.relative = true;
      }

      @Override
      public void tick() {
         if (!SuppressionFeedbackController.this.wasKO) {
            this.stopSound();
         }
      }

      public void stopSound() {
         this.stop();
      }
   }
}