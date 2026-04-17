package goetic.mods.absolutrevive.common;

import goetic.mods.absolutrevive.AbsolutRevive;
import goetic.mods.absolutrevive.api.damagesystem.AbstractPlayerDamageModel;
import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.network.AbsolutReviveNetworking;
import goetic.mods.absolutrevive.common.network.MessageSyncDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;

public final class EventHandler {
   private static final Map<UUID, EventHandler.RescueProgress> rescueProgress = new HashMap<>();
   private static final Map<UUID, Long> lastSearchSoundTimes = new HashMap<>();
   private static java.lang.reflect.Field cachedTicksField = null;

   private EventHandler() {}

   // MÉTHODE UTILITAIRE : Vérifie si le joueur tient un défibrillateur dans une de ses mains
   private static boolean holdsDefibrillator(Player player) {
      if (player == null) return false;
      String main = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
      String off = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).getPath();
      return main.equals("defibrillator") || off.equals("defibrillator");
   }

   public static void registerServerEvents() {
      ServerTickEvents.END_WORLD_TICK.register(EventHandler::tickPlayers);
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onLogin(handler.getPlayer()));
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.getPlayer()));
      ServerPlayerEvents.COPY_FROM.register(EventHandler::onCopyFrom);
      ServerPlayerEvents.AFTER_RESPAWN.register(EventHandler::onAfterRespawn);
      ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> onDimensionChange(player));

      EntityTrackingEvents.START_TRACKING.register((trackedEntity, trackingPlayer) -> {
         if (trackedEntity instanceof ServerPlayer trackedServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(trackedServerPlayer);
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
               MessageSyncDamageModel payload = new MessageSyncDamageModel(trackedServerPlayer.getId(), playerDamageModel, false);
               ServerPlayNetworking.send(trackingPlayer, payload);
            }
         }
      });

      UseEntityCallback.EVENT.register(EventHandler::onEntityInteract);
      UseItemCallback.EVENT.register(EventHandler::onItemUse);
      UseBlockCallback.EVENT.register(EventHandler::onBlockInteract);
      AttackEntityCallback.EVENT.register(EventHandler::onAttackEntity);
      AttackBlockCallback.EVENT.register(EventHandler::onBlockAttack);

      ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
         if (entity instanceof Player player) {
            if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            if (handleFatalPlayerDamage(player)) return false;
         }
         return true;
      });

      ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, damageAmount) -> {
         if (entity instanceof Player player && isUnconscious(player)) {
            if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            if (damageSource.getEntity() != null) return true;
            return false;
         }
         return true;
      });
   }

   public static boolean handleFatalPlayerDamage(Player player) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel instanceof PlayerDamageModel playerDamageModel) {
         if (playerDamageModel.isUnconscious()) {
            return false;
         } else {
            // --- VÉRIFICATION DU TOTEM D'IMMORTALITÉ ---
            ItemStack totemStack = null;
            for (InteractionHand hand : InteractionHand.values()) {
               ItemStack itemInHand = player.getItemInHand(hand);
               if (itemInHand.is(Items.TOTEM_OF_UNDYING)) {
                  totemStack = itemInHand.copy();
                  itemInHand.shrink(1);
                  break;
               }
            }

            if (totemStack != null) {
               // Le joueur a un Totem en main ! On le consomme et on le sauve de la mort/du KO.
               if (player instanceof ServerPlayer serverPlayer) {
                  serverPlayer.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                  net.minecraft.advancements.CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, totemStack);
               }

               player.setHealth(1.0F);
               player.removeAllEffects();
               player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 900, 1));
               player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 100, 1));
               player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 800, 0));
               player.level().broadcastEntityEvent(player, (byte)35); // Joue l'animation dorée du Totem à l'écran

               return true; // Retourner true annule la mort et empêche de déclencher le KO
            }
            // -------------------------------------------

            playerDamageModel.setUnconsciousState(1800, true, true, "absolutrevive.gui.critical_condition");
            player.clearFire();

            ItemStack mainHand = player.getMainHandItem().copy();
            if (!mainHand.isEmpty()) {
               player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
               player.drop(mainHand, true, false);
            }

            ItemStack offHand = player.getOffhandItem().copy();
            if (!offHand.isEmpty()) {
               player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
               player.drop(offHand, true, false);
            }

            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(player.getMaxHealth() / 2.0F));

            if (player instanceof ServerPlayer serverPlayer) {
               AbsolutReviveNetworking.sendDamageModelSync(serverPlayer, playerDamageModel, false);
            }
            return true;
         }
      }
      return false;
   }

   private static void tickPlayers(ServerLevel world) {
      for (ServerPlayer player : world.players()) {
         if (player.isAlive()) {
            // On compte le temps de réanimation pour TOUS les joueurs, y compris ceux en mode Créatif !
            tickRescueProgress(player);

            // La logique de KO et de santé s'applique uniquement si le joueur peut subir des dégâts (Survie/Aventure)
            if (!player.getAbilities().invulnerable) {
               AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
               if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                  if (playerDamageModel.isUnconscious()) {
                     clearAttackTargetsAround(player, 24.0);

                     if (player.containerMenu != player.inventoryMenu) {
                        player.closeContainer();
                     }

                     ItemStack mainHand = player.getMainHandItem().copy();
                     if (!mainHand.isEmpty()) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.drop(mainHand, true, false);
                     }

                     ItemStack offHand = player.getOffhandItem().copy();
                     if (!offHand.isEmpty()) {
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        player.drop(offHand, true, false);
                     }

                     // Suppression radicale de l'effet de Régénération
                     if (player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
                        player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
                     }

                     // MISE EN PAUSE PENDANT LA RÉANIMATION
                     boolean isBeingRescued = false;
                     for (EventHandler.RescueProgress progress : rescueProgress.values()) {
                        if (progress.targetId().equals(player.getUUID())) {
                           isBeingRescued = true;
                           break;
                        }
                     }

                     if (isBeingRescued) {
                        if (cachedTicksField == null) {
                           try {
                              cachedTicksField = playerDamageModel.getClass().getDeclaredField("unconsciousTicks");
                              cachedTicksField.setAccessible(true);
                           } catch (Exception e) {}
                        }
                        if (cachedTicksField != null) {
                           try {
                              int currentTicks = cachedTicksField.getInt(playerDamageModel);
                              cachedTicksField.setInt(playerDamageModel, Math.min(1800, currentTicks + 1));

                              // Force le client à se mettre à jour en continu pour que le chronomètre se fige à l'écran !
                              if (player.tickCount % 2 == 0) {
                                 AbsolutReviveNetworking.sendDamageModelSync((ServerPlayer) player, playerDamageModel, false);
                              }
                           } catch (Exception e) {}
                        }
                     }

                     // SYNC ABSOLUE : Santé (HP) <=> Chronomètre (Temps)
                     float maxKoHp = player.getMaxHealth() / 2.0F; // 10 HP = 5 coeurs pleins
                     float currentHp = player.getHealth();
                     int currentTicks = playerDamageModel.getUnconsciousTicks();
                     float expectedHp = maxKoHp * ((float)currentTicks / 1800.0F);

                     if (currentHp < expectedHp - 0.05F) {
                        int newTicks = (int)(1800.0F * (currentHp / maxKoHp));
                        try {
                           java.lang.reflect.Field ticksField = playerDamageModel.getClass().getDeclaredField("unconsciousTicks");
                           ticksField.setAccessible(true);
                           ticksField.setInt(playerDamageModel, Math.max(0, newTicks));

                           AbsolutReviveNetworking.sendDamageModelSync(player, playerDamageModel, false);
                        } catch (Exception e) {}
                     }
                     else if (currentHp - expectedHp >= 1.0F) {
                        float newHp = Math.max(0.01F, expectedHp);
                        CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(newHp));
                     }
                  }
                  damageModel.tick(player.level(), player);
               }
            }
         }
      }
   }

   private static InteractionResult onEntityInteract(Player rescuer, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
      if (isUnconscious(rescuer)) return InteractionResult.FAIL;

      if (hand == InteractionHand.MAIN_HAND && entity instanceof Player target && isUnconscious(target)) {
         if (!rescuer.isCrouching()) {
            if (!level.isClientSide()) {
               openLootMenu((ServerPlayer) rescuer, target);
            }
            return InteractionResult.SUCCESS;
         }
      }
      return InteractionResult.PASS;
   }

   private static void openLootMenu(ServerPlayer rescuer, Player target) {
      long currentTime = rescuer.level().getGameTime();
      Long lastTime = lastSearchSoundTimes.get(target.getUUID());

      if (lastTime == null || currentTime - lastTime > 40) {
         rescuer.level().playSound(null, target.blockPosition(), AbsolutRevive.SEARCH_SOUND, SoundSource.PLAYERS, 1.0F, 1.0F);
         lastSearchSoundTimes.put(target.getUUID(), currentTime);
      }

      rescuer.openMenu(new SimpleMenuProvider(
              (containerId, playerInventory, player) -> new ChestMenu(MenuType.GENERIC_9x5, containerId, playerInventory, new PlayerLootContainer(target), 5) {

                 @Override
                 public void clicked(int slotId, int button, ClickType clickType, Player p) {
                    if (slotId >= 0 && slotId < this.slots.size()) {
                       Slot slot = this.slots.get(slotId);
                       if (slot.container instanceof PlayerLootContainer lootContainer) {
                          int cSlot = slot.getContainerSlot();

                          if (lootContainer.isBlockedSlot(cSlot)) {
                             return;
                          }

                          if (cSlot >= 0 && cSlot <= 3) {
                             ItemStack actionStack = ItemStack.EMPTY;
                             if (clickType == ClickType.PICKUP || clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_CRAFT) {
                                actionStack = this.getCarried();
                             } else if (clickType == ClickType.SWAP) {
                                actionStack = p.getInventory().getItem(button);
                             }

                             if (!actionStack.isEmpty() && !lootContainer.canPlaceItem(cSlot, actionStack)) {
                                return;
                             }
                          }
                       }
                    }
                    super.clicked(slotId, button, clickType, p);
                 }

                 @Override
                 public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
                    if (slot.container instanceof PlayerLootContainer lootContainer) {
                       if (lootContainer.isBlockedSlot(slot.getContainerSlot())) {
                          return false;
                       }
                    }
                    return super.canTakeItemForPickAll(stack, slot);
                 }

                 @Override
                 public ItemStack quickMoveStack(Player p, int slotId) {
                    ItemStack itemStack = ItemStack.EMPTY;
                    Slot slot = this.slots.get(slotId);

                    if (slot != null && slot.hasItem()) {
                       if (slot.container instanceof PlayerLootContainer lootContainer) {
                          if (lootContainer.isBlockedSlot(slot.getContainerSlot())) {
                             return ItemStack.EMPTY;
                          }
                       }

                       ItemStack itemStack2 = slot.getItem();
                       itemStack = itemStack2.copy();

                       if (slotId < 45) {
                          if (!this.moveItemStackTo(itemStack2, 45, this.slots.size(), true)) {
                             return ItemStack.EMPTY;
                          }
                       }
                       else {
                          boolean isArmor = false;
                          String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack2.getItem()).getPath().toLowerCase(java.util.Locale.ROOT);

                          if (name.contains("helmet") || name.contains("skull") || name.contains("head") || name.contains("carved")) {
                             this.moveItemStackTo(itemStack2, 0, 1, false);
                             isArmor = true;
                          } else if (name.contains("chestplate") || name.contains("elytra") || name.contains("cloak")) {
                             this.moveItemStackTo(itemStack2, 1, 2, false);
                             isArmor = true;
                          } else if (name.contains("leggings")) {
                             this.moveItemStackTo(itemStack2, 2, 3, false);
                             isArmor = true;
                          } else if (name.contains("boots")) {
                             this.moveItemStackTo(itemStack2, 3, 4, false);
                             isArmor = true;
                          }

                          if (!itemStack2.isEmpty()) {
                             if (!this.moveItemStackTo(itemStack2, 9, 45, false)) {
                                if (!isArmor || itemStack2.getCount() == itemStack.getCount()) {
                                   return ItemStack.EMPTY;
                                }
                             }
                          }
                       }

                       if (itemStack2.isEmpty()) {
                          slot.setByPlayer(ItemStack.EMPTY);
                       } else {
                          slot.setChanged();
                       }

                       if (itemStack2.getCount() == itemStack.getCount()) {
                          return ItemStack.EMPTY;
                       }

                       slot.onTake(p, itemStack2);
                    }

                    return itemStack;
                 }
              },
              Component.translatable("absolutrevive.gui.loot", target.getDisplayName())
      ));
   }

   private static InteractionResult onItemUse(Player player, Level level, InteractionHand hand) { return cancelIfUnconscious(player); }
   private static InteractionResult onBlockInteract(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) { return cancelIfUnconscious(player); }
   private static InteractionResult onBlockAttack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) { return cancelIfUnconscious(player); }
   private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) { return cancelIfUnconscious(player); }

   private static void onLogin(ServerPlayer player) {
      if (!player.level().isClientSide()) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel != null) AbsolutReviveNetworking.sendDamageModelSync(player, damageModel, false);
      }
   }

   private static void onLogout(ServerPlayer player) {
      EventHandler.RescueProgress progress = rescueProgress.get(player.getUUID());
      if (progress != null && progress.durationTicks() == 60) {
         stopDefibrillatorSound(player);
      }
      rescueProgress.remove(player.getUUID());
      lastSearchSoundTimes.remove(player.getUUID());
   }

   private static void onDimensionChange(ServerPlayer player) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel != null) AbsolutReviveNetworking.sendDamageModelSync(player, damageModel, false);
   }

   private static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
      if (CommonUtils.getExistingDamageModel(oldPlayer) instanceof PlayerDamageModel oldDamageModel && newPlayer instanceof goetic.mods.absolutrevive.common.AbsolutReviveDamageModelHolder holder) {
         PlayerDamageModel cloned = new PlayerDamageModel();
         cloned.deserializeNBT(oldDamageModel.serializeNBT());

         // CORRECTION DU BUG DE RESPAWN :
         // Si le joueur est mort (pas un simple passage de portail), on efface absolument tout son KO !
         if (!alive) {
            cloned.clearUnconsciousState();
         }

         holder.absolutrevive$setDamageModel(cloned);
      }
   }

   private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
      if (!alive) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(newPlayer);
         if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.clearUnconsciousState();
            playerDamageModel.scheduleResync();

            // CORRECTION DU BUG DE RESPAWN :
            // 1. On force la mise à jour immédiate auprès du client du joueur et des autres joueurs
            AbsolutReviveNetworking.sendDamageModelSync(newPlayer, playerDamageModel, false);

            // 2. On annule toutes les tentatives de réanimation qui ciblaient ce joueur avant sa mort
            rescueProgress.entrySet().removeIf(entry -> entry.getValue().targetId().equals(newPlayer.getUUID()));
            lastSearchSoundTimes.remove(newPlayer.getUUID());
         }
      }
   }

   private static InteractionResult cancelIfUnconscious(Player player) {
      return isUnconscious(player) ? InteractionResult.FAIL : InteractionResult.PASS;
   }

   public static boolean isUnconscious(Player player) {
      return CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel pdm && pdm.isUnconscious();
   }

   private static void clearAttackTargetsAround(LivingEntity victim, double range) {
      for (Mob mob : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range))) {
         if (mob.getTarget() == victim) {
            mob.setTarget(null);
            Brain<?> brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
         }
      }
   }

   private static void stopDefibrillatorSound(ServerPlayer rescuer) {
      ClientboundStopSoundPacket stopPacket = new ClientboundStopSoundPacket(AbsolutRevive.DEFIBRILLATOR_SOUND_ID, SoundSource.PLAYERS);
      rescuer.connection.send(stopPacket);

      for (ServerPlayer trackingPlayer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(rescuer)) {
         trackingPlayer.connection.send(stopPacket);
      }
   }

   private static void tickRescueProgress(ServerPlayer rescuer) {
      Player target = findClosestRescueTarget(rescuer);
      EventHandler.RescueProgress previousProgress = rescueProgress.get(rescuer.getUUID());

      // On mémorise les objets exacts tenus en main ce tick-ci
      net.minecraft.world.item.Item currentMain = rescuer.getMainHandItem().getItem();
      net.minecraft.world.item.Item currentOff = rescuer.getOffhandItem().getItem();

      if (target == null || !rescuer.isCrouching()) {
         if (previousProgress != null) {
            if (previousProgress.durationTicks() == 60) {
               stopDefibrillatorSound(rescuer);
            }
            rescueProgress.remove(rescuer.getUUID());
         }
      } else {
         int rescueDurationTicks = holdsDefibrillator(rescuer) ? 60 : 160;
         EventHandler.RescueProgress progress = previousProgress;

         // SÉCURITÉ ABSOLUE : Si l'un des deux objets en main change (même entre deux blocs de terre), on reset !
         if (progress == null || !progress.targetId().equals(target.getUUID())
                 || progress.mainHandItem() != currentMain || progress.offhandItem() != currentOff) {

            if (progress != null && progress.durationTicks() == 60) {
               stopDefibrillatorSound(rescuer);
            }

            // ON REMET LE COMPTEUR A ZERO avec mémorisation des nouveaux objets !
            progress = new EventHandler.RescueProgress(target.getUUID(), 0, rescueDurationTicks, currentMain, currentOff);

            if (holdsDefibrillator(rescuer)) {
               // Volume monté à 2.0F et centré sur le secouriste pour être sûr de l'entendre de loin
               rescuer.level().playSound(null, rescuer.blockPosition(), AbsolutRevive.DEFIBRILLATOR_SOUND, SoundSource.PLAYERS, 2.0F, 1.0F);
            }
         }

         int nextTicks = Math.min(rescueDurationTicks, progress.ticks() + 1);
         if (nextTicks < rescueDurationTicks) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
         } else {
            completeRescue(rescuer, target);
         }
      }
   }

   public static void attemptImmediateRescue(ServerPlayer rescuer) {
      Player target = findClosestRescueTarget(rescuer);
      if (target != null) {
         EventHandler.RescueProgress progress = rescueProgress.get(rescuer.getUUID());
         if (progress != null && progress.ticks() >= progress.durationTicks() - 10) {
            completeRescue(rescuer, target);
         }
      }
   }

   private static void completeRescue(ServerPlayer rescuer, Player target) {
      if (CommonUtils.getDamageModel(target) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.canBeRescued()) {
         if (playerDamageModel.rescueFromCriticalState(target)) {
            rescuer.displayClientMessage(Component.translatable("absolutrevive.gui.rescue_other", target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            target.displayClientMessage(Component.translatable("absolutrevive.gui.rescue_received", rescuer.getDisplayName()).withStyle(ChatFormatting.GREEN), true);

            if (holdsDefibrillator(rescuer)) {
               target.setHealth(8.0F); // 4 coeurs avec le défibrillateur
               ItemStack mainHand = rescuer.getMainHandItem();
               ItemStack offHand = rescuer.getOffhandItem();
               boolean isMainHand = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainHand.getItem()).getPath().equals("defibrillator");
               ItemStack defib = isMainHand ? mainHand : offHand;

               defib.hurtAndBreak(1, (ServerLevel) rescuer.level(), rescuer, item -> {
                  rescuer.level().playSound(null, rescuer.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.8F + rescuer.level().random.nextFloat() * 0.4F);
               });
            } else {
               target.setHealth(4.0F); // 2 coeurs à mains nues
            }
         }
         rescueProgress.remove(rescuer.getUUID());
      }
   }

   private static Player findClosestRescueTarget(Player actor) {
      if (actor == null || actor.level().isClientSide() || isUnconscious(actor)) return null;

      double maxDistanceSqr = 3.0 * 3.0;
      Player closestTarget = null;
      double closestDistanceSqr = maxDistanceSqr;

      for (Player candidate : actor.level().players()) {
         if (candidate != actor && candidate.isAlive() && CommonUtils.getDamageModel(candidate) instanceof PlayerDamageModel pdm && pdm.canBeRescued()) {
            double distanceSqr = actor.distanceToSqr(candidate);
            if (distanceSqr < closestDistanceSqr) {
               closestDistanceSqr = distanceSqr;
               closestTarget = candidate;
            }
         }
      }
      return closestTarget;
   }

   // MISE À JOUR DE LA CLASSE POUR MÉMORISER LES OBJETS
   private record RescueProgress(UUID targetId, int ticks, int durationTicks, net.minecraft.world.item.Item mainHandItem, net.minecraft.world.item.Item offhandItem) {
      private EventHandler.RescueProgress withTicks(int updatedTicks) {
         return new EventHandler.RescueProgress(this.targetId, updatedTicks, this.durationTicks, this.mainHandItem, this.offhandItem);
      }
   }

   // =======================================================================
   // CONTENEUR VIRTUEL "MAPPEUR"
   // =======================================================================
   public static class PlayerLootContainer implements Container {
      private final Player target;
      private final net.minecraft.world.entity.player.Inventory inv;

      private final ItemStack placeholderHead;
      private final ItemStack placeholderArmorLabel;
      private final ItemStack placeholderInvLabel;
      private final ItemStack placeholderHotbarLabel;
      private final ItemStack placeholderMainHand;
      private final ItemStack placeholderOffHand;

      private final int lockedMainHandSlot;

      public PlayerLootContainer(Player target) {
         this.target = target;
         this.inv = target.getInventory();

         this.placeholderHead = new ItemStack(Items.PLAYER_HEAD);
         this.placeholderHead.set(DataComponents.CUSTOM_NAME, Component.literal("Inventaire de " + target.getScoreboardName()).withStyle(ChatFormatting.GOLD));

         this.placeholderArmorLabel = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
         this.placeholderArmorLabel.set(DataComponents.CUSTOM_NAME, Component.translatable("absolutrevive.gui.loot.armor").withStyle(ChatFormatting.YELLOW));

         this.placeholderInvLabel = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
         this.placeholderInvLabel.set(DataComponents.CUSTOM_NAME, Component.translatable("absolutrevive.gui.loot.main_inventory").withStyle(ChatFormatting.GRAY));

         this.placeholderHotbarLabel = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
         this.placeholderHotbarLabel.set(DataComponents.CUSTOM_NAME, Component.translatable("absolutrevive.gui.loot.hotbar").withStyle(ChatFormatting.GRAY));

         this.placeholderMainHand = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
         this.placeholderMainHand.set(DataComponents.CUSTOM_NAME, Component.translatable("absolutrevive.gui.loot.main_hand").withStyle(ChatFormatting.GRAY));

         this.placeholderOffHand = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
         this.placeholderOffHand.set(DataComponents.CUSTOM_NAME, Component.translatable("absolutrevive.gui.loot.off_hand").withStyle(ChatFormatting.GRAY));

         int selectedIndex = 0;
         try {
            for (java.lang.reflect.Field field : this.inv.getClass().getDeclaredFields()) {
               String name = field.getName();
               if (name.equals("selected") || name.equals("selectedSlot")) {
                  field.setAccessible(true);
                  selectedIndex = field.getInt(this.inv);
                  break;
               }
            }
         } catch (Exception e) {}
         this.lockedMainHandSlot = Math.max(0, Math.min(8, selectedIndex));
      }

      public boolean isBlockedSlot(int slot) {
         if (slot >= 5 && slot <= 8) return true;
         if (slot == 4) return true;
         if (slot == 36 + this.lockedMainHandSlot) return true;
         return false;
      }

      private int mapSlot(int chestSlot) {
         if (chestSlot >= 36 && chestSlot <= 44) return chestSlot - 36;
         if (chestSlot >= 9 && chestSlot <= 35) return chestSlot;
         if (chestSlot == 0) return 39;
         if (chestSlot == 1) return 38;
         if (chestSlot == 2) return 37;
         if (chestSlot == 3) return 36;
         if (chestSlot == 4) return 40;
         return -1;
      }

      @Override
      public int getContainerSize() { return 45; }

      @Override
      public boolean isEmpty() { return inv.isEmpty(); }

      @Override
      public ItemStack getItem(int slot) {
         if (slot == 5) return placeholderHead;
         if (slot == 6) return placeholderArmorLabel;
         if (slot == 7) return placeholderInvLabel;
         if (slot == 8) return placeholderHotbarLabel;

         if (slot == 4) return placeholderOffHand;
         if (slot == 36 + this.lockedMainHandSlot) return placeholderMainHand;

         int mapped = mapSlot(slot);
         return mapped == -1 ? ItemStack.EMPTY : inv.getItem(mapped);
      }

      @Override
      public ItemStack removeItem(int slot, int amount) {
         if (isBlockedSlot(slot)) return ItemStack.EMPTY;

         int mapped = mapSlot(slot);
         if (mapped != -1) {
            ItemStack stack = inv.removeItem(mapped, amount);
            this.setChanged();
            return stack;
         }
         return ItemStack.EMPTY;
      }

      @Override
      public ItemStack removeItemNoUpdate(int slot) {
         if (isBlockedSlot(slot)) return ItemStack.EMPTY;
         int mapped = mapSlot(slot);
         if (mapped != -1) {
            ItemStack stack = inv.removeItemNoUpdate(mapped);
            this.setChanged();
            return stack;
         }
         return ItemStack.EMPTY;
      }

      @Override
      public void setItem(int slot, ItemStack stack) {
         if (isBlockedSlot(slot)) return;
         int mapped = mapSlot(slot);
         if (mapped != -1) inv.setItem(mapped, stack);
      }

      @Override
      public boolean canPlaceItem(int slot, ItemStack stack) {
         if (isBlockedSlot(slot)) return false;
         if (slot >= 0 && slot <= 3) {
            if (stack.isEmpty()) return true;
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(java.util.Locale.ROOT);
            if (slot == 0 && (name.contains("helmet") || name.contains("skull") || name.contains("head") || name.contains("carved"))) return true;
            if (slot == 1 && (name.contains("chestplate") || name.contains("elytra") || name.contains("cloak"))) return true;
            if (slot == 2 && name.contains("leggings")) return true;
            if (slot == 3 && name.contains("boots")) return true;
            return false;
         }
         return true;
      }

      @Override
      public void setChanged() { inv.setChanged(); }

      @Override
      public boolean stillValid(Player player) {
         return target.isAlive() && isUnconscious(target) && player.distanceToSqr(target) < 64.0;
      }

      @Override
      public void clearContent() { inv.clearContent(); }
   }
}