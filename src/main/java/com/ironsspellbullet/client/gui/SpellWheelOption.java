package com.ironsspellbullet.client.gui;

import com.ironsspellbullet.event.ReloadHelper;
import io.redspace.irons_artifice.registry.ItemRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * An option in the Arcane Bullet Wheel (either a normal bullet or a specific spell-imbued bullet).
 */
public record SpellWheelOption(
        String id,
        Component displayName,
        ItemStack iconStack,
        int count,
        boolean isNormalBullet,
        AbstractSpell spell,
        int spellLevel
) {
    public static SpellWheelOption ofNormal(int count) {
        return new SpellWheelOption(
                "normal",
                Component.translatable("gui.irons_spell_bullet.normal_bullet"),
                new ItemStack(ItemRegistry.BULLET.get()),
                count,
                true,
                null,
                0
        );
    }

    public static SpellWheelOption ofSpell(SpellData spellData, int count, Player player) {
        ItemStack stack = ReloadHelper.createSpellBullet(spellData.getSpell(), spellData.getLevel(), 1);

        Component name = (player != null && spellData.getSpell() != null)
                ? spellData.getSpell().getDisplayName(player)
                : Component.translatable(spellData.getSpell().getComponentId());

        return new SpellWheelOption(
                spellData.getSpell().getSpellResource().toString(),
                name,
                stack,
                count,
                false,
                spellData.getSpell(),
                spellData.getLevel()
        );
    }

    public static SpellWheelOption ofSpell(SpellData spellData, int count) {
        return ofSpell(spellData, count, null);
    }
}
