package com.ironsspellbullet.client.gui;

import com.ironsspellbullet.config.SpellBulletClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * Modern Action Bar / Scroll Bar HUD styled after Iron's Spells' SpellBarOverlay.
 * Displays a configurable grid of bullet options above the hotbar or at configured screen anchors.
 * Supports in-game real-time preview, drag-and-drop repositioning, layout scaling, and columns.
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

    public static record HudBounds(double x, double y, double width, double height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

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

    public static HudBounds computeLayoutBounds(int guiWidth, int guiHeight, int totalSlots,
                                               SpellBulletClientConfig.HudAnchor anchor,
                                               int offsetX, int offsetY,
                                               SpellBulletClientConfig.HudOrientation orientation,
                                               int maxColsOrRows,
                                               float scale) {
        if (totalSlots <= 0) {
            return new HudBounds(0, 0, 0, 0);
        }

        int maxLimit = Math.max(1, maxColsOrRows);
        int cols, rows;
        if (orientation == SpellBulletClientConfig.HudOrientation.HORIZONTAL) {
            cols = Math.min(totalSlots, maxLimit);
            rows = (int) Math.ceil((double) totalSlots / cols);
        } else {
            rows = Math.min(totalSlots, maxLimit);
            cols = (int) Math.ceil((double) totalSlots / rows);
        }

        int unscaledWidth = cols * SLOT_SIZE + (cols - 1) * SLOT_GAP;
        int unscaledHeight = rows * SLOT_SIZE + (rows - 1) * SLOT_GAP;

        int baseX, baseY;
        Minecraft mc = Minecraft.getInstance();

        switch (anchor) {
            case HOTBAR_CENTER -> {
                int maxStatusHeight = 49;
                if (mc.gui != null) {
                    maxStatusHeight = Math.max(maxStatusHeight, Math.max(mc.gui.leftHeight, mc.gui.rightHeight));
                }
                baseX = (guiWidth - unscaledWidth) / 2;
                baseY = guiHeight - maxStatusHeight - unscaledHeight - 6;
            }
            case BOTTOM_LEFT -> {
                baseX = 14;
                baseY = guiHeight - unscaledHeight - 14;
            }
            case BOTTOM_RIGHT -> {
                baseX = guiWidth - unscaledWidth - 14;
                baseY = guiHeight - unscaledHeight - 14;
            }
            case TOP_LEFT -> {
                baseX = 14;
                baseY = 24;
            }
            case TOP_RIGHT -> {
                baseX = guiWidth - unscaledWidth - 14;
                baseY = 24;
            }
            case CENTER -> {
                baseX = (guiWidth - unscaledWidth) / 2;
                baseY = (guiHeight - unscaledHeight) / 2;
            }
            default -> {
                baseX = (guiWidth - unscaledWidth) / 2;
                baseY = guiHeight - 77;
            }
        }

        int rawX = baseX + offsetX;
        int rawY = baseY + offsetY;

        double finalW = unscaledWidth * scale;
        double finalH = unscaledHeight * scale;

        return new HudBounds(rawX, rawY, finalW, finalH);
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

        // Alpha fade out in last 15 ticks
        float alpha = 1.0f;
        if (displayTicks < 15) {
            alpha = displayTicks / 15.0f;
        }

        // Read active config
        SpellBulletClientConfig cfg = SpellBulletClientConfig.INSTANCE;
        SpellBulletClientConfig.HudAnchor anchor = cfg.anchor.get();
        int offsetX = cfg.offsetX.get();
        int offsetY = cfg.offsetY.get();
        SpellBulletClientConfig.HudOrientation orientation = cfg.orientation.get();
        int maxColumns = cfg.maxColumns.get();
        float scale = (float) (double) cfg.scale.get();
        boolean showBulletName = cfg.showBulletName.get();
        SpellBulletClientConfig.TitlePosition namePosition = cfg.namePosition.get();
        float nameScale = (float) (double) cfg.nameScale.get();
        int nameOffsetX = cfg.nameOffsetX.get();
        int nameOffsetY = cfg.nameOffsetY.get();

        renderCustom(
                graphics,
                mc.font,
                graphics.guiWidth(),
                graphics.guiHeight(),
                cachedOptions,
                selectedIndex,
                alpha,
                anchor,
                offsetX,
                offsetY,
                orientation,
                maxColumns,
                scale,
                showBulletName,
                namePosition,
                nameScale,
                nameOffsetX,
                nameOffsetY
        );
    }

    public static void renderCustom(GuiGraphics graphics, Font font, int guiWidth, int guiHeight,
                                    List<SpellWheelOption> options, int activeIndex, float alpha,
                                    SpellBulletClientConfig.HudAnchor anchor,
                                    int offsetX, int offsetY,
                                    SpellBulletClientConfig.HudOrientation orientation,
                                    int maxColsOrRows,
                                    float scale,
                                    boolean showBulletName,
                                    SpellBulletClientConfig.TitlePosition namePosition,
                                    float nameScale,
                                    int nameOffsetX,
                                    int nameOffsetY) {
        int totalSlots = options.size();
        if (totalSlots == 0) return;

        HudBounds bounds = computeLayoutBounds(guiWidth, guiHeight, totalSlots, anchor, offsetX, offsetY, orientation, maxColsOrRows, 1.0f);
        int startX = (int) bounds.x;
        int startY = (int) bounds.y;

        int maxLimit = Math.max(1, maxColsOrRows);
        int cols, rows;
        if (orientation == SpellBulletClientConfig.HudOrientation.HORIZONTAL) {
            cols = Math.min(totalSlots, maxLimit);
            rows = (int) Math.ceil((double) totalSlots / cols);
        } else {
            rows = Math.min(totalSlots, maxLimit);
            cols = (int) Math.ceil((double) totalSlots / rows);
        }

        graphics.pose().pushPose();
        if (scale != 1.0f) {
            graphics.pose().translate(startX, startY, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.pose().translate(-startX, -startY, 0);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0f, 1.0f, 1.0f, alpha);

        // Compute slot coordinates
        int[] slotXs = new int[totalSlots];
        int[] slotYs = new int[totalSlots];
        for (int i = 0; i < totalSlots; i++) {
            int c, r;
            if (orientation == SpellBulletClientConfig.HudOrientation.HORIZONTAL) {
                c = i % cols;
                r = i / cols;
            } else {
                r = i % rows;
                c = i / rows;
            }
            slotXs[i] = startX + c * (SLOT_SIZE + SLOT_GAP);
            slotYs[i] = startY + r * (SLOT_SIZE + SLOT_GAP);
        }

        // 1. Pass 1: Draw Base Slot Background (u=66, v=84) for ALL slots
        for (int i = 0; i < totalSlots; i++) {
            int slotX = slotXs[i];
            int slotY = slotYs[i];
            // Subtle translucent dark backing inside slot for depth
            graphics.fill(slotX + 1, slotY + 1, slotX + 21, slotY + 21, ((int) (alpha * 0x70)) << 24);
            graphics.blit(IRONS_ICONS, slotX, slotY, 66, 84, SLOT_SIZE, SLOT_SIZE);
        }

        // 2. Pass 2: Draw Slot Icons (Bullet / Spell Icon)
        for (int i = 0; i < totalSlots; i++) {
            SpellWheelOption option = options.get(i);
            int slotX = slotXs[i];
            int slotY = slotYs[i];
            int iconX = slotX + 3;
            int iconY = slotY + 3;
            boolean isSelected = (i == activeIndex);

            // Active slot has full 100% brightness; unselected slots are clearly visible with gentle 85% contrast
            float brightness = isSelected ? 1.0f : 0.85f;
            float iconAlpha = isSelected ? alpha : alpha * 0.90f;

            if (option.isNormalBullet()) {
                graphics.renderItem(option.iconStack(), iconX, iconY);
            } else if (option.spell() != null) {
                ResourceLocation icon = option.spell().getSpellIconResource();
                if (icon != null) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    graphics.setColor(brightness, brightness, brightness, iconAlpha);
                    graphics.blit(icon, iconX, iconY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                } else {
                    graphics.renderItem(option.iconStack(), iconX, iconY);
                }
            }
        }

        // 3. Pass 3: Draw Golden Selection Border ONLY on top of the active slot (u=0, v=84 has transparent center)
        if (activeIndex >= 0 && activeIndex < totalSlots) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 150);
            int selectedSlotX = slotXs[activeIndex];
            int selectedSlotY = slotYs[activeIndex];
            graphics.blit(IRONS_ICONS, selectedSlotX, selectedSlotY, 0, 84, SLOT_SIZE, SLOT_SIZE);
            graphics.pose().popPose();
        }

        // 4. Pass 4: Render Count Badges in bottom-right corner of each slot
        for (int i = 0; i < totalSlots; i++) {
            SpellWheelOption option = options.get(i);
            if (option.count() > 0) {
                int slotX = slotXs[i];
                int slotY = slotYs[i];
                String countStr = option.count() >= 999 ? "∞" : String.valueOf(option.count());
                int countWidth = font.width(countStr);
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.drawString(font, countStr, slotX + SLOT_SIZE - countWidth - 2, slotY + 12, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        // 5. Render Active Bullet Info Header (if enabled)
        if (showBulletName && activeIndex >= 0 && activeIndex < options.size()) {
            SpellWheelOption active = options.get(activeIndex);

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
            int totalGridW = cols * SLOT_SIZE + (cols - 1) * SLOT_GAP;
            int totalGridH = rows * SLOT_SIZE + (rows - 1) * SLOT_GAP;

            int scaledTextWidth = (int) Math.round(textWidth * nameScale);
            int scaledTextHeight = (int) Math.round(font.lineHeight * nameScale);

            int textBaseX;
            int textBaseY;

            switch (namePosition) {
                case BELOW -> {
                    textBaseX = startX + (totalGridW - scaledTextWidth) / 2;
                    textBaseY = startY + totalGridH + 6;
                }
                case LEFT -> {
                    textBaseX = startX - scaledTextWidth - 8;
                    textBaseY = startY + (totalGridH - scaledTextHeight) / 2;
                }
                case RIGHT -> {
                    textBaseX = startX + totalGridW + 8;
                    textBaseY = startY + (totalGridH - scaledTextHeight) / 2;
                }
                default -> { // ABOVE
                    textBaseX = startX + (totalGridW - scaledTextWidth) / 2;
                    textBaseY = startY - scaledTextHeight - 5;
                }
            }

            int textX = textBaseX + nameOffsetX;
            int textY = textBaseY + nameOffsetY;

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 220);
            graphics.fill(textX - 4, textY - 2, textX + scaledTextWidth + 4, textY + (int) Math.round(font.lineHeight * nameScale) + 2, ((int) (alpha * 0x90)) << 24);
            graphics.pose().translate(textX, textY, 0);
            if (nameScale != 1.0f) {
                graphics.pose().scale(nameScale, nameScale, 1.0f);
            }
            graphics.drawString(font, title, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().popPose();
    }
}
