package com.ironsspellbullet.recipe;

import com.ironsspellbullet.item.ModItems;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import io.redspace.irons_artifice.registry.ItemRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 3x3 Inscribing recipe:
 * 8x Bullet (surrounding) + 1x Scroll (center) -> 8x Spell Bullet
 */
public class SpellBulletCraftingRecipe extends CustomRecipe {

    public SpellBulletCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        // Center slot (row 1, col 1 -> index 4) must be a scroll with a valid spell
        ItemStack centerStack = input.getItem(1, 1);
        if (centerStack.isEmpty() || !ISpellContainer.isSpellContainer(centerStack)) {
            return false;
        }

        ISpellContainer container = ISpellContainer.get(centerStack);
        if (container == null || container.isEmpty()) {
            return false;
        }

        SpellData spellData = container.getSpellAtIndex(0);
        if (spellData == null || spellData.equals(SpellData.EMPTY) || spellData.getSpell() == null) {
            return false;
        }

        // Strictly forbidden: Teleportation and mobility spells cannot be imbued onto spell bullets!
        if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(spellData.getSpell())) {
            return false;
        }

        // All 8 surrounding slots must be standard bullets
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1) continue; // skip center

                ItemStack slotStack = input.getItem(col, row);
                if (slotStack.isEmpty() || !slotStack.is(ItemRegistry.BULLET.get())) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack centerStack = input.getItem(1, 1);
        if (centerStack.isEmpty() || !ISpellContainer.isSpellContainer(centerStack)) {
            return ItemStack.EMPTY;
        }

        ISpellContainer container = ISpellContainer.get(centerStack);
        if (container == null || container.isEmpty()) {
            return ItemStack.EMPTY;
        }

        SpellData spellData = container.getSpellAtIndex(0);
        if (spellData == null || spellData.equals(SpellData.EMPTY) || spellData.getSpell() == null) {
            return ItemStack.EMPTY;
        }

        // Strictly forbidden: Teleportation and mobility spells cannot be imbued onto spell bullets!
        if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(spellData.getSpell())) {
            return ItemStack.EMPTY;
        }

        // Create 8x Spell Bullets imbued with the scroll's spell and level
        ItemStack result = new ItemStack(ModItems.SPELL_BULLET.get(), 8);
        ISpellContainer.createScrollContainer(spellData.getSpell(), spellData.getLevel(), result);

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SPELL_BULLET_RECIPE_SERIALIZER.get();
    }
}

