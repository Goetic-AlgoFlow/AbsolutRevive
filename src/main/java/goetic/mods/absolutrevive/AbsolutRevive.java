package goetic.mods.absolutrevive;

import goetic.mods.absolutrevive.common.EventHandler;
import goetic.mods.absolutrevive.common.network.AbsolutReviveNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AbsolutRevive {
   public static final String MODID = "absolutrevive";
   public static final Logger LOGGER = LogManager.getLogger(MODID);
   public static boolean isSynced = false;
   public static boolean rescueWakeUpEnabled = true;

   public static final Identifier HEARTBEAT_ID = Identifier.fromNamespaceAndPath(MODID, "debuff.heartbeat");
   public static final SoundEvent HEARTBEAT_SOUND = SoundEvent.createVariableRangeEvent(HEARTBEAT_ID);

   public static final Identifier SEARCH_ID = Identifier.fromNamespaceAndPath(MODID, "debuff.search");
   public static final SoundEvent SEARCH_SOUND = SoundEvent.createVariableRangeEvent(SEARCH_ID);

   public static final Identifier DEFIBRILLATOR_SOUND_ID = Identifier.fromNamespaceAndPath(MODID, "defibrillator_use");
   public static final SoundEvent DEFIBRILLATOR_SOUND = SoundEvent.createVariableRangeEvent(DEFIBRILLATOR_SOUND_ID);

   public static final Identifier DEFIBRILLATOR_ID = Identifier.fromNamespaceAndPath(MODID, "defibrillator");
   public static final ResourceKey<Item> DEFIBRILLATOR_KEY = ResourceKey.create(Registries.ITEM, DEFIBRILLATOR_ID);
   public static final Item DEFIBRILLATOR = new Item(new Item.Properties().setId(DEFIBRILLATOR_KEY).durability(3));

   private AbsolutRevive() {}

   public static void initCommon() {
      LOGGER.info("{} starting (Vanilla Health KO Version)...", MODID);

      Registry.register(BuiltInRegistries.SOUND_EVENT, HEARTBEAT_ID, HEARTBEAT_SOUND);
      Registry.register(BuiltInRegistries.SOUND_EVENT, SEARCH_ID, SEARCH_SOUND);
      Registry.register(BuiltInRegistries.SOUND_EVENT, DEFIBRILLATOR_SOUND_ID, DEFIBRILLATOR_SOUND);

      Registry.register(BuiltInRegistries.ITEM, DEFIBRILLATOR_ID, DEFIBRILLATOR);

      ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(content -> {
         content.accept(DEFIBRILLATOR);
      });

      EventHandler.registerServerEvents();
      AbsolutReviveNetworking.registerCommon();
   }
}