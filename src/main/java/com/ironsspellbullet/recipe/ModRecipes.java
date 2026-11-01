package com.ironsspellbullet.recipe;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, IronsArmsSpellBulletMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SpellBulletCraftingRecipe>> SPELL_BULLET_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_spell_bullet",
                    () -> new SimpleCraftingRecipeSerializer<>(SpellBulletCraftingRecipe::new));

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}

