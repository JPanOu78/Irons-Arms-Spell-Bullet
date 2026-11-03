package com.ironsspellbullet.item;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Inscribed Spell Bullet item.
 * Encapsulates spell energy into a ballistic round, bypassing cast time and cooldown.
 */
public class SpellBulletItem extends Item {

    public SpellBulletItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellData spellData = getSpellData(stack);
        if (spellData != null && !spellData.equals(SpellData.EMPTY) && spellData.getSpell() != null) {
            MutableComponent spellName = (MutableComponent) spellData.getDisplayName();
            return Component.translatable("item.irons_spell_bullet.spell_bullet.named", spellName);
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasSpell(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        SpellData spellData = getSpellData(stack);
        if (spellData != null && !spellData.equals(SpellData.EMPTY) && spellData.getSpell() != null) {
            AbstractSpell spell = spellData.getSpell();

            if (com.ironsspellbullet.spell.SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.incompatible_mobility")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            SpellRarity rarity = spellData.getRarity();

            // School & Rarity header
            MutableComponent schoolComponent = Component.translatable(spell.getSchoolType().getDisplayName().getString())
                    .withStyle(spell.getSchoolType().getDisplayName().getStyle());

            tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.school_info", schoolComponent)
                    .withStyle(ChatFormatting.GRAY));

            // Spell Details
            tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.instant_fire")
                    .withStyle(ChatFormatting.GOLD));
            tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.zero_mana")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean hasSpell(ItemStack stack) {
        SpellData data = getSpellData(stack);
        return data != null && !data.equals(SpellData.EMPTY) && data.getSpell() != null;
    }

    public static SpellData getSpellData(ItemStack stack) {
        if (!stack.isEmpty() && ISpellContainer.isSpellContainer(stack)) {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container != null && !container.isEmpty()) {
                return container.getSpellAtIndex(0);
            }
        }
        return SpellData.EMPTY;
    }
}

