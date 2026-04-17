package goetic.mods.absolutrevive.compat.jei;

import goetic.mods.absolutrevive.AbsolutRevive;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class AbsolutReviveJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(AbsolutRevive.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
                new ItemStack(AbsolutRevive.DEFIBRILLATOR),
                VanillaTypes.ITEM_STACK,
                Component.translatable("absolutrevive.jei.defibrillator.desc")
        );
    }
}