package com.ironsspellbullet.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentic radial Spell Wheel overlay styled after Iron's Spells 'n Spellbooks.
 */
public class SpellBulletWheelOverlay implements LayeredDraw.Layer {

    public static final SpellBulletWheelOverlay INSTANCE = new SpellBulletWheelOverlay();

    private final Vector4f lineColor = new Vector4f(1.0f, 0.85f, 0.65f, 1.0f);
    private final Vector4f radialButtonColor = new Vector4f(0.04f, 0.03f, 0.02f, 0.70f);
    private final Vector4f highlightColor = new Vector4f(0.85f, 0.72f, 0.45f, 0.75f);

    private final float ringInnerEdge = 24.0f;
    private final float ringOuterEdge = 82.0f;

    private static int displayTicks = 0;
    private static List<SpellWheelOption> cachedOptions = new ArrayList<>();
    private static int selectedIndex = 0;

    public static void show(int ticks, List<SpellWheelOption> options, int index) {
        displayTicks = ticks;
        cachedOptions = options;
        selectedIndex = index;
    }

    public static void tick() {
        if (displayTicks > 0) {
            displayTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (displayTicks <= 0 || cachedOptions.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        int total = cachedOptions.size();
        float sliceAngle = (float) (2 * Math.PI / total);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = poseStack.last().pose();

        // 1. Draw Radial Slices (Iron's Spells radial mesh)
        for (int i = 0; i < total; i++) {
            float startAngle = (float) (-Math.PI / 2 + (i - 0.5f) * sliceAngle);
            float endAngle = startAngle + sliceAngle;
            boolean isSelected = (i == selectedIndex);

            Vector4f color = isSelected ? highlightColor : radialButtonColor;
            drawArcSlice(consumer, pose, centerX, centerY, ringInnerEdge, ringOuterEdge, startAngle, endAngle, color);

            // Dividing lines
            drawRadialLine(consumer, pose, centerX, centerY, ringInnerEdge, ringOuterEdge, startAngle, lineColor);
        }

        // Inner and Outer circular boundary rings
        drawCircleRing(consumer, pose, centerX, centerY, ringInnerEdge, 0.04f, 0.03f, 0.02f, 0.85f);
        drawCircleOutline(consumer, pose, centerX, centerY, ringInnerEdge, lineColor);
        drawCircleOutline(consumer, pose, centerX, centerY, ringOuterEdge, lineColor);

        graphics.bufferSource().endBatch();

        // 2. Render Bullet Icons on the wheel ring
        float iconDist = (ringInnerEdge + ringOuterEdge) / 2.0f;
        for (int i = 0; i < total; i++) {
            SpellWheelOption option = cachedOptions.get(i);
            float midAngle = (float) (-Math.PI / 2 + i * sliceAngle);
            int iconX = (int) (centerX + Mth.cos(midAngle) * iconDist - 8);
            int iconY = (int) (centerY + Mth.sin(midAngle) * iconDist - 8);

            graphics.renderItem(option.iconStack(), iconX, iconY);

            // Count badge
            String countText = "x" + option.count();
            int countWidth = font.width(countText);
            graphics.drawString(font, countText, iconX + 8 - countWidth / 2, iconY + 12, 0xFFFFFF, true);
        }

        // 3. Center Info Panel (Selected Bullet Details)
        if (selectedIndex >= 0 && selectedIndex < cachedOptions.size()) {
            SpellWheelOption active = cachedOptions.get(selectedIndex);

            // Name
            MutableComponent title = active.displayName().copy();
            if (active.isNormalBullet()) {
                title.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            } else if (active.spell() != null && active.spell().getSchoolType() != null) {
                title.withStyle(active.spell().getSchoolType().getDisplayName().getStyle()).withStyle(ChatFormatting.BOLD);
            }
            graphics.drawCenteredString(font, title, (int) centerX, (int) centerY - 14, 0xFFFFFF);

            // Available Count
            Component countInfo = Component.translatable("gui.irons_spell_bullet.available_count", active.count())
                    .withStyle(ChatFormatting.GRAY);
            graphics.drawCenteredString(font, countInfo, (int) centerX, (int) centerY - 3, 0xAAAAAA);

            // Action hint
            Component hint = Component.translatable("gui.irons_spell_bullet.reload_hint")
                    .withStyle(ChatFormatting.GOLD);
            graphics.drawCenteredString(font, hint, (int) centerX, (int) centerY + 8, 0xFFAA00);
        }

        poseStack.popPose();
    }

    private void drawArcSlice(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float innerR, float outerR, float startAngle, float endAngle, Vector4f col) {
        int steps = 10;
        float da = (endAngle - startAngle) / steps;
        for (int s = 0; s < steps; s++) {
            float a1 = startAngle + s * da;
            float a2 = a1 + da;

            float x1Inner = cx + Mth.cos(a1) * innerR;
            float y1Inner = cy + Mth.sin(a1) * innerR;
            float x1Outer = cx + Mth.cos(a1) * outerR;
            float y1Outer = cy + Mth.sin(a1) * outerR;

            float x2Inner = cx + Mth.cos(a2) * innerR;
            float y2Inner = cy + Mth.sin(a2) * innerR;
            float x2Outer = cx + Mth.cos(a2) * outerR;
            float y2Outer = cy + Mth.sin(a2) * outerR;

            consumer.addVertex(pose, x1Inner, y1Inner, 0).setColor(col.x, col.y, col.z, col.w);
            consumer.addVertex(pose, x1Outer, y1Outer, 0).setColor(col.x, col.y, col.z, col.w);
            consumer.addVertex(pose, x2Outer, y2Outer, 0).setColor(col.x, col.y, col.z, col.w);

            consumer.addVertex(pose, x1Inner, y1Inner, 0).setColor(col.x, col.y, col.z, col.w);
            consumer.addVertex(pose, x2Outer, y2Outer, 0).setColor(col.x, col.y, col.z, col.w);
            consumer.addVertex(pose, x2Inner, y2Inner, 0).setColor(col.x, col.y, col.z, col.w);
        }
    }

    private void drawRadialLine(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float innerR, float outerR, float angle, Vector4f col) {
        float x1 = cx + Mth.cos(angle) * innerR;
        float y1 = cy + Mth.sin(angle) * innerR;
        float x2 = cx + Mth.cos(angle) * outerR;
        float y2 = cy + Mth.sin(angle) * outerR;

        float nx = -Mth.sin(angle) * 0.75f;
        float ny = Mth.cos(angle) * 0.75f;

        consumer.addVertex(pose, x1 - nx, y1 - ny, 0).setColor(col.x, col.y, col.z, col.w);
        consumer.addVertex(pose, x1 + nx, y1 + ny, 0).setColor(col.x, col.y, col.z, col.w);
        consumer.addVertex(pose, x2 + nx, y2 + ny, 0).setColor(col.x, col.y, col.z, col.w);

        consumer.addVertex(pose, x1 - nx, y1 - ny, 0).setColor(col.x, col.y, col.z, col.w);
        consumer.addVertex(pose, x2 + nx, y2 + ny, 0).setColor(col.x, col.y, col.z, col.w);
        consumer.addVertex(pose, x2 - nx, y2 - ny, 0).setColor(col.x, col.y, col.z, col.w);
    }

    private void drawCircleOutline(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float r, Vector4f col) {
        int steps = 48;
        float da = (float) (2 * Math.PI / steps);
        for (int i = 0; i < steps; i++) {
            float a1 = i * da;
            float a2 = a1 + da;
            drawRadialLine(consumer, pose, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, 0, 1.0f, a1, col);
        }
    }

    private void drawCircleRing(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float r, float red, float green, float blue, float alpha) {
        int steps = 36;
        float da = (float) (2 * Math.PI / steps);
        for (int i = 0; i < steps; i++) {
            float a1 = i * da;
            float a2 = a1 + da;

            consumer.addVertex(pose, cx, cy, 0).setColor(red, green, blue, alpha);
            consumer.addVertex(pose, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, 0).setColor(red, green, blue, alpha);
            consumer.addVertex(pose, cx + Mth.cos(a2) * r, cy + Mth.sin(a2) * r, 0).setColor(red, green, blue, alpha);
        }
    }
}
