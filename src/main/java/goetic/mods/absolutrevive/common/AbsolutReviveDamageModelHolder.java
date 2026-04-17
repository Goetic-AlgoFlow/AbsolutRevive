package goetic.mods.absolutrevive.common;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;

import javax.annotation.Nullable;

public interface AbsolutReviveDamageModelHolder {
   PlayerDamageModel absolutrevive$getDamageModel();

   @Nullable
   PlayerDamageModel absolutrevive$getDamageModelNullable();

   void absolutrevive$setDamageModel(PlayerDamageModel var1);
}
