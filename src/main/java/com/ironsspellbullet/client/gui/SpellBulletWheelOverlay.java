package com.ironsspellbullet.client.gui;

import com.ironsspellbullet.item.ModItems;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SpellBulletWheelOverlay implements LayeredDraw.Layer {

    public static final SpellBulletWheelOverlay INSTANCE = new SpellBulletWheelOverlay();

    private static int displayTicks = 0;
    private static List<SpellData> cachedSpells = new ArrayList<>();
    private static int selectedIndex = 0;

    public static void show(int ticks, List<SpellData> spells, int index) {
        displayTicks = ticks;
        cachedSpells = spells;
        selectedIndex = index;
    }

    public static void tick() {
        if (displayTicks > 0) {
            displayTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (displayTicks <= 0 || cachedSpells.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int centerX = screenWidth / 2;
        int centerY = screenHeight - 80;

        int totalItems = cachedSpells.size();
        int itemSpacing = 26;
        int totalWidth = Math.max(160, totalItems * itemSpacing + 20);
        int halfWidth = totalWidth / 2;

        // Background panel
        graphics.fill(centerX - halfWidth, centerY - 28, centerX + halfWidth, centerY + 28, 0xCC111115);
        graphics.fill(centerX - halfWidth, centerY - 29, centerX + halfWidth, centerY - 28, 0xFF444455);
        graphics.fill(centerX - halfWidth, centerY + 28, centerX + halfWidth, centerY + 29, 0xFF444455);

        // Header Title
        Component title = Component.translatable("gui.irons_spell_bullet.select_title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        graphics.drawCenteredString(font, title, centerX, centerY - 24, 0xFFAA00);

        int startX = centerX - (totalItems * itemSpacing) / 2 + (itemSpacing - 16) / 2;
        int itemY = centerY - 10;

        for (int i = 0; i < totalItems; i++) {
            SpellData spellData = cachedSpells.get(i);
            int slotX = startX + i * itemSpacing;

            ItemStack displayStack = new ItemStack(ModItems.SPELL_BULLET.get());
            ISpellContainer.createScrollContainer(spellData.getSpell(), spellData.getLevel(), displayStack);

            if (i == selectedIndex) {
                // Highlight box
                graphics.fill(slotX - 3, itemY - 3, slotX + 19, itemY + 19, 0x55FFAA00);
                graphics.fill(slotX - 4, itemY - 4, slotX + 20, itemY - 3, 0xFFFFAA00);
                graphics.fill(slotX - 4, itemY + 19, slotX + 20, itemY + 20, 0xFFFFAA00);
                graphics.fill(slotX - 4, itemY - 4, slotX - 3, itemY + 20, 0xFFFFAA00);
                graphics.fill(slotX + 19, itemY - 4, slotX + 20, itemY + 20, 0xFFFFAA00);
            }

            graphics.renderItem(displayStack, slotX, itemY);
        }

        // Active Spell Info label
        if (selectedIndex >= 0 && selectedIndex < cachedSpells.size()) {
            SpellData activeData = cachedSpells.get(selectedIndex);
            AbstractSpell activeSpell = activeData.getSpell();
            if (activeSpell != null) {
                Component spellName = Component.literal(activeSpell.getDisplayName(mc.player).getString() + " " + activeData.getLevel())
                        .withStyle(activeSpell.getSchoolType().getDisplayName().getStyle());
                graphics.drawCenteredString(font, spellName, centerX, centerY + 13, 0xFFFFFF);
            }
        }
    }
}
