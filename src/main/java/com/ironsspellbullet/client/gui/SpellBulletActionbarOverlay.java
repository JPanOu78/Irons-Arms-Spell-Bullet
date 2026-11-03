package com.ironsspellbullet.client.gui;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Action Bar / Scroll Bar HUD styled after the "spell_actionbar" mod.
 * Displays a horizontal action bar of bullet options above the hotbar when holding Alt or scrolling.
 */
public class SpellBulletActionbarOverlay implements LayeredDraw.Layer {

    public static final SpellBulletActionbarOverlay INSTANCE = new SpellBulletActionbarOverlay();

    public static final ResourceLocation IRONS_ICONS = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/gui/icons.png");

    public static final int SLOT_SIZE = 22;
    public static final int SLOT_GAP = 2;
    public static final int ICON_SIZE = 16;
    public static final int MAX_DISPLAY_TICKS = 70;

    private static int displayTicks = 0;
    private static List<SpellWheelOption> cachedOptions = new ArrayList<>();
    private static int selectedIndex = 0;

    public static void show(int ticks, List<SpellWheelOption> options, int index) {
        displayTicks = ticks;
        cachedOptions = options;
        selectedIndex = index;
    }

    public static void keepAlive() {
        if (displayTicks < 40) {
            displayTicks = 40;
        }
    }

    public static void tick() {
        if (displayTicks > 0) {
            displayTicks--;
        }
    }

    public static boolean isVisible() {
        return displayTicks > 0 && !cachedOptions.isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (displayTicks <= 0 || cachedOptions.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // Only display if the player is holding a gun
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingGun = (mainHand.getItem() instanceof GunItem) || (offHand.getItem() instanceof GunItem);
        if (!holdingGun) return;

        Font font = mc.font;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();

        int totalSlots = cachedOptions.size();
        int totalWidth = totalSlots * SLOT_SIZE + (totalSlots - 1) * SLOT_GAP;
        int startX = (guiWidth - totalWidth) / 2;
        int startY = guiHeight - 56; // Positioned cleanly directly above the hotbar

        // Alpha fade out in last 15 ticks
        float alpha = 1.0f;
        if (displayTicks < 15) {
            alpha = displayTicks / 15.0f;
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, alpha);

        // 1. Render Slots & Icons
        for (int i = 0; i < totalSlots; i++) {
            SpellWheelOption option = cachedOptions.get(i);
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            int slotY = startY;

            boolean isSelected = (i == selectedIndex);

            // Render slot background
            if (isSelected) {
                // Highlighted golden border
                graphics.blit(IRONS_ICONS, slotX, slotY, 66, 84, SLOT_SIZE, SLOT_SIZE);
            } else {
                // Normal empty slot frame
                graphics.blit(IRONS_ICONS, slotX, slotY, 0, 84, SLOT_SIZE, SLOT_SIZE);
            }

            // Render Slot Icon
            int iconX = slotX + 3;
            int iconY = slotY + 3;

            if (option.isNormalBullet()) {
                // Render standard bullet icon
                graphics.renderItem(option.iconStack(), iconX, iconY);
            } else if (option.spell() != null) {
                // Render high-res spell icon from Iron's Spells
                ResourceLocation icon = option.spell().getSpellIconResource();
                if (icon != null) {
                    graphics.blit(icon, iconX, iconY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                } else {
                    graphics.renderItem(option.iconStack(), iconX, iconY);
                }
            }

            // Render count badge in bottom-right corner of slot
            if (option.count() > 0) {
                String countStr = option.count() >= 999 ? "∞" : String.valueOf(option.count());
                int countWidth = font.width(countStr);
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.drawString(font, countStr, slotX + SLOT_SIZE - countWidth - 1, slotY + 13, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        // 2. Render Active Bullet Info Header above Action Bar
        if (selectedIndex >= 0 && selectedIndex < cachedOptions.size()) {
            SpellWheelOption active = cachedOptions.get(selectedIndex);

            MutableComponent title = Component.empty();
            if (active.isNormalBullet()) {
                title.append(Component.literal("Normal Bullet").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            } else {
                AbstractSpell spell = active.spell();
                SchoolType school = (spell != null) ? spell.getSchoolType() : null;

                MutableComponent spellName = active.displayName().copy();
                if (school != null && school.getDisplayName() != null) {
                    spellName.withStyle(school.getDisplayName().getStyle()).withStyle(ChatFormatting.BOLD);
                    title.append(spellName);
                    title.append(Component.literal(" (" + school.getDisplayName().getString() + ")").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    title.append(spellName.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
            }

            String countDisplay = active.count() >= 999 ? "∞" : String.valueOf(active.count());
            title.append(Component.literal(" • " + countDisplay + " Available").withStyle(ChatFormatting.GRAY));

            int textWidth = font.width(title);
            int textX = (guiWidth - textWidth) / 2;
            int textY = startY - 13;

            // Subtle dark background backing behind text for high readability
            graphics.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + font.lineHeight + 1, (int) (alpha * 0x90) << 24);
            graphics.drawString(font, title, textX, textY, 0xFFFFFF, true);
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

