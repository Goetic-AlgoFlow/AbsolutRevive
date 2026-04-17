package goetic.mods.absolutrevive.mixin;

import goetic.mods.absolutrevive.common.EventHandler;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthMixin {

   // On se branche sur le RETOUR (RETURN) de la méthode de Minecraft, et non plus au début (HEAD)
   @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
   private void absolutrevive$interceptDeathAfterTotem(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof Player player) {

         // Si le jeu a déjà retourné 'true', ça veut dire que le joueur AVAIT un Totem.
         // Minecraft a déjà consommé le Totem et donné les effets (Régénération, Absorption).
         // On s'arrête donc ici pour ne pas le mettre KO !
         if (cir.getReturnValueZ()) {
            return;
         }

         // Si on arrive ici, le joueur n'avait PAS de Totem en main et s'apprête à mourir.

         // Si le joueur tombe dans le vide intersidéral ou fait /kill, on le laisse mourir
         if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
         }

         // On met le joueur KO. Si on y parvient, on force le retour de la méthode à 'true'
         // pour faire croire à Minecraft qu'il a été sauvé de la mort (par notre mod).
         if (EventHandler.handleFatalPlayerDamage(player)) {
            cir.setReturnValue(true);
         }
      }
   }
}