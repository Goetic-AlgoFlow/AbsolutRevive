package goetic.mods.absolutrevive.common.util;

import goetic.mods.absolutrevive.AbsolutRevive;
import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.AbsolutReviveDamageModelHolder;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class CommonUtils {
   private static final ThreadLocal<Integer> SET_HEALTH_INTERCEPTION_SUPPRESSION = ThreadLocal.withInitial(() -> 0);

   public static void killPlayer(@Nonnull AbstractPlayerDamageModel damageModel, @Nonnull Player player, @Nullable DamageSource source) {
      killPlayerDirectly(player, source);
   }

   public static void killPlayerDirectly(@Nonnull Player player, @Nullable DamageSource source) {
      DamageSource resolvedSource = source != null ? source : player.damageSources().generic();
      player.setHealth(0.0F);
      player.die(resolvedSource);
   }

   @Nullable
   public static AbstractPlayerDamageModel getDamageModel(@Nullable Player player) {
      if (player == null) {
         return null;
      } else if (player instanceof AbsolutReviveDamageModelHolder holder) {
         return holder.absolutrevive$getDamageModel();
      } else {
         return null;
      }
   }

   @Nonnull
   public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(@Nullable Player player) {
      return Optional.ofNullable(getExistingDamageModel(player));
   }

   @Nullable
   public static AbstractPlayerDamageModel getExistingDamageModel(@Nullable Player player) {
      return player instanceof AbsolutReviveDamageModelHolder holder ? holder.absolutrevive$getDamageModelNullable() : null;
   }

   public static boolean hasDamageModel(Entity entity) {
      return entity instanceof Player;
   }

   public static boolean isSetHealthInterceptionSuppressed() {
      return SET_HEALTH_INTERCEPTION_SUPPRESSION.get() > 0;
   }

   public static void runWithoutSetHealthInterception(Runnable action) {
      callWithoutSetHealthInterception(() -> {
         action.run();
         return null;
      });
   }

   public static <T> T callWithoutSetHealthInterception(Supplier<T> action) {
      int depth = SET_HEALTH_INTERCEPTION_SUPPRESSION.get();
      SET_HEALTH_INTERCEPTION_SUPPRESSION.set(depth + 1);

      try {
         return action.get();
      } finally {
         if (depth == 0) {
            SET_HEALTH_INTERCEPTION_SUPPRESSION.remove();
         } else {
            SET_HEALTH_INTERCEPTION_SUPPRESSION.set(depth);
         }
      }
   }

   public static void debugLogStacktrace(String name) {
      AbsolutRevive.LOGGER.info("DEBUG: " + name);
   }
}