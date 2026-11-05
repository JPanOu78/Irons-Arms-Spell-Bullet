package com.ironsspellbullet.client.gui;

import com.ironsspellbullet.config.SpellBulletClientConfig;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Real-time HUD Configuration Screen for Spell Bullet Action Bar.
 * Allows visual drag-and-drop repositioning, anchor selection, orientation toggling,
 * column adjustments, scaling, and spell bullet name customization (toggle, position, scale, offset).
 */
public class SpellBulletHudConfigScreen extends Screen {

    private final Screen parent;
    private static List<SpellWheelOption> previewDummyOptions = null;

    private boolean isDraggingHud = false;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartOffsetX;
    private int dragStartOffsetY;

    // Temporary working state for real-time preview before saving
    private SpellBulletClientConfig.HudAnchor currentAnchor;
    private int currentOffsetX;
    private int currentOffsetY;
    private SpellBulletClientConfig.HudOrientation currentOrientation;
    private int currentMaxColumns;
    private double currentScale;

    private boolean currentShowBulletName;
    private SpellBulletClientConfig.TitlePosition currentNamePosition;
    private double currentNameScale;
    private int currentNameOffsetX;
    private int currentNameOffsetY;

    private Button anchorBtn;
    private Button orientationBtn;
    private ColumnsSlider columnsSlider;
    private ScaleSlider scaleSlider;

    private Button showNameBtn;
    private Button namePosBtn;
    private NameScaleSlider nameScaleSlider;
    private NameOffsetSlider nameOffsetSlider;

    public SpellBulletHudConfigScreen(Screen parent) {
        super(Component.literal("Spell Bullet HUD Configuration"));
        this.parent = parent;

        // Load current config into working state
        SpellBulletClientConfig cfg = SpellBulletClientConfig.INSTANCE;
        this.currentAnchor = cfg.anchor.get();
        this.currentOffsetX = cfg.offsetX.get();
        this.currentOffsetY = cfg.offsetY.get();
        this.currentOrientation = cfg.orientation.get();
        this.currentMaxColumns = cfg.maxColumns.get();
        this.currentScale = cfg.scale.get();

        this.currentShowBulletName = cfg.showBulletName.get();
        this.currentNamePosition = cfg.namePosition.get();
        this.currentNameScale = cfg.nameScale.get();
        this.currentNameOffsetX = cfg.nameOffsetX.get();
        this.currentNameOffsetY = cfg.nameOffsetY.get();
    }

    @Override
    protected void init() {
        super.init();

        int btnWidth = 125;
        int btnHeight = 20;
        int spacing = 6;

        int totalControlWidth = (btnWidth * 4) + (spacing * 3);
        int startX = (this.width - totalControlWidth) / 2;

        // ==========================================
        // Row 1: Slots HUD Controls (Y = 10)
        // ==========================================
        int row1Y = 10;

        // 1. Anchor Button
        anchorBtn = Button.builder(
                Component.literal("Anchor: " + currentAnchor.getDisplayName()),
                btn -> {
                    SpellBulletClientConfig.HudAnchor[] values = SpellBulletClientConfig.HudAnchor.values();
                    int nextIdx = (currentAnchor.ordinal() + 1) % values.length;
                    currentAnchor = values[nextIdx];
                    currentOffsetX = 0;
                    currentOffsetY = 0;
                    btn.setMessage(Component.literal("Anchor: " + currentAnchor.getDisplayName()));
                }
        ).bounds(startX, row1Y, btnWidth, btnHeight).build();
        addRenderableWidget(anchorBtn);

        // 2. Orientation Button
        orientationBtn = Button.builder(
                Component.literal("Layout: " + currentOrientation.getDisplayName()),
                btn -> {
                    currentOrientation = (currentOrientation == SpellBulletClientConfig.HudOrientation.HORIZONTAL)
                            ? SpellBulletClientConfig.HudOrientation.VERTICAL
                            : SpellBulletClientConfig.HudOrientation.HORIZONTAL;
                    btn.setMessage(Component.literal("Layout: " + currentOrientation.getDisplayName()));
                }
        ).bounds(startX + (btnWidth + spacing), row1Y, btnWidth, btnHeight).build();
        addRenderableWidget(orientationBtn);

        // 3. Columns Slider
        columnsSlider = new ColumnsSlider(startX + (btnWidth + spacing) * 2, row1Y, btnWidth, btnHeight);
        addRenderableWidget(columnsSlider);

        // 4. Scale Slider
        scaleSlider = new ScaleSlider(startX + (btnWidth + spacing) * 3, row1Y, btnWidth, btnHeight);
        addRenderableWidget(scaleSlider);

        // ==========================================
        // Row 2: Spell Bullet Name Controls (Y = 34)
        // ==========================================
        int row2Y = 34;

        // 5. Show / Hide Name Button
        showNameBtn = Button.builder(
                getShowNameComponent(),
                btn -> {
                    currentShowBulletName = !currentShowBulletName;
                    btn.setMessage(getShowNameComponent());
                }
        ).bounds(startX, row2Y, btnWidth, btnHeight).build();
        addRenderableWidget(showNameBtn);

        // 6. Name Position Button (Above / Below / Left / Right)
        namePosBtn = Button.builder(
                Component.literal("Name Pos: " + currentNamePosition.getDisplayName()),
                btn -> {
                    SpellBulletClientConfig.TitlePosition[] values = SpellBulletClientConfig.TitlePosition.values();
                    int nextIdx = (currentNamePosition.ordinal() + 1) % values.length;
                    currentNamePosition = values[nextIdx];
                    currentNameOffsetX = 0;
                    currentNameOffsetY = 0;
                    btn.setMessage(Component.literal("Name Pos: " + currentNamePosition.getDisplayName()));
                    if (nameOffsetSlider != null) {
                        nameOffsetSlider.resetOffset();
                    }
                }
        ).bounds(startX + (btnWidth + spacing), row2Y, btnWidth, btnHeight).build();
        addRenderableWidget(namePosBtn);

        // 7. Name Scale Slider
        nameScaleSlider = new NameScaleSlider(startX + (btnWidth + spacing) * 2, row2Y, btnWidth, btnHeight);
        addRenderableWidget(nameScaleSlider);

        // 8. Dynamic Name Offset Slider (Offset X for Left/Right, Offset Y for Above/Below)
        nameOffsetSlider = new NameOffsetSlider(startX + (btnWidth + spacing) * 3, row2Y, btnWidth, btnHeight);
        addRenderableWidget(nameOffsetSlider);

        // ==========================================
        // Bottom Bar: Reset & Done Buttons
        // ==========================================
        int bottomY = this.height - 28;
        int bottomBtnWidth = 100;
        addRenderableWidget(Button.builder(
                Component.literal("Reset Defaults").withStyle(ChatFormatting.RED),
                btn -> resetDefaults()
        ).bounds((this.width / 2) - bottomBtnWidth - 10, bottomY, bottomBtnWidth, 20).build());

        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> saveAndClose()
        ).bounds((this.width / 2) + 10, bottomY, bottomBtnWidth, 20).build());

        initPreviewDummies();
    }

    private Component getShowNameComponent() {
        return currentShowBulletName
                ? Component.literal("Show Name: ").append(Component.literal("ON").withStyle(ChatFormatting.GREEN))
                : Component.literal("Show Name: ").append(Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private void resetDefaults() {
        this.currentAnchor = SpellBulletClientConfig.HudAnchor.HOTBAR_CENTER;
        this.currentOffsetX = 0;
        this.currentOffsetY = 0;
        this.currentOrientation = SpellBulletClientConfig.HudOrientation.HORIZONTAL;
        this.currentMaxColumns = 9;
        this.currentScale = 1.0;

        this.currentShowBulletName = true;
        this.currentNamePosition = SpellBulletClientConfig.TitlePosition.ABOVE;
        this.currentNameScale = 1.0;
        this.currentNameOffsetX = 0;
        this.currentNameOffsetY = 0;

        anchorBtn.setMessage(Component.literal("Anchor: " + currentAnchor.getDisplayName()));
        orientationBtn.setMessage(Component.literal("Layout: " + currentOrientation.getDisplayName()));
        columnsSlider.setValueFromColumns(9);
        scaleSlider.setValueFromScale(1.0);

        showNameBtn.setMessage(getShowNameComponent());
        namePosBtn.setMessage(Component.literal("Name Pos: " + currentNamePosition.getDisplayName()));
        nameScaleSlider.setValueFromScale(1.0);
        nameOffsetSlider.resetOffset();
    }

    private void saveAndClose() {
        SpellBulletClientConfig cfg = SpellBulletClientConfig.INSTANCE;
        cfg.anchor.set(currentAnchor);
        cfg.offsetX.set(currentOffsetX);
        cfg.offsetY.set(currentOffsetY);
        cfg.orientation.set(currentOrientation);
        cfg.maxColumns.set(currentMaxColumns);
        cfg.scale.set(currentScale);

        cfg.showBulletName.set(currentShowBulletName);
        cfg.namePosition.set(currentNamePosition);
        cfg.nameScale.set(currentNameScale);
        cfg.nameOffsetX.set(currentNameOffsetX);
        cfg.nameOffsetY.set(currentNameOffsetY);

        SpellBulletClientConfig.SPEC.save();

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            SpellBulletActionbarOverlay.HudBounds bounds = computeCurrentBounds();
            if (bounds.contains(mouseX, mouseY)) {
                isDraggingHud = true;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartOffsetX = currentOffsetX;
                dragStartOffsetY = currentOffsetY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingHud) {
            double deltaX = mouseX - dragStartMouseX;
            double deltaY = mouseY - dragStartMouseY;
            currentOffsetX = (int) Math.round(dragStartOffsetX + deltaX);
            currentOffsetY = (int) Math.round(dragStartOffsetY + deltaY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingHud) {
            isDraggingHud = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private SpellBulletActionbarOverlay.HudBounds computeCurrentBounds() {
        List<SpellWheelOption> dummyOptions = getPreviewOptions();
        return SpellBulletActionbarOverlay.computeLayoutBounds(
                this.width,
                this.height,
                dummyOptions.size(),
                currentAnchor,
                currentOffsetX,
                currentOffsetY,
                currentOrientation,
                currentMaxColumns,
                (float) currentScale
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Subtle dark translucent background
        graphics.fillGradient(0, 0, this.width, this.height, 0x90050810, 0xC00A0E18);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Header and live readout
        String coords = String.format("Anchor: %s | Offset: (%+d, %+d) | Scale: %.0f%% | Name: %s (%s, %.0f%%)",
                currentAnchor.getDisplayName(), currentOffsetX, currentOffsetY, currentScale * 100.0,
                currentShowBulletName ? "ON" : "OFF", currentNamePosition.getDisplayName(), currentNameScale * 100.0);
        graphics.drawCenteredString(this.font, Component.literal(coords).withStyle(ChatFormatting.AQUA), this.width / 2, 58, 0xFFFFFF);

        // Render Real-time Action Bar preview
        List<SpellWheelOption> dummyOptions = getPreviewOptions();
        SpellBulletActionbarOverlay.renderCustom(
                graphics,
                this.font,
                this.width,
                this.height,
                dummyOptions,
                1, // Selected index (Fireball)
                1.0f,
                currentAnchor,
                currentOffsetX,
                currentOffsetY,
                currentOrientation,
                currentMaxColumns,
                (float) currentScale,
                currentShowBulletName,
                currentNamePosition,
                (float) currentNameScale,
                currentNameOffsetX,
                currentNameOffsetY
        );

        // Draw selection / drag bounding box around the Action Bar
        SpellBulletActionbarOverlay.HudBounds bounds = computeCurrentBounds();
        boolean hovering = bounds.contains(mouseX, mouseY);
        int borderColor = hovering || isDraggingHud ? 0xFFFFD700 : 0x8055FFFF;
        graphics.renderOutline((int) bounds.x() - 2, (int) bounds.y() - 2, (int) bounds.width() + 4, (int) bounds.height() + 4, borderColor);

        if (hovering || isDraggingHud) {
            String dragLabel = isDraggingHud ? "Dragging..." : "Click & Drag";
            graphics.drawString(this.font, Component.literal(dragLabel).withStyle(ChatFormatting.YELLOW), (int) bounds.x(), (int) bounds.y() - 10, 0xFFFFFF, true);
        }
    }

    private synchronized List<SpellWheelOption> getPreviewOptions() {
        if (previewDummyOptions == null) {
            initPreviewDummies();
        }
        return previewDummyOptions;
    }

    private void initPreviewDummies() {
        previewDummyOptions = new ArrayList<>();
        // 1. Normal Bullet
        previewDummyOptions.add(SpellWheelOption.ofNormal(64));

        // 2. Fireball
        AbstractSpell fireball = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fireball"));
        if (fireball != null) {
            previewDummyOptions.add(SpellWheelOption.ofSpell(new SpellData(fireball, 1), 32));
        }

        // 3. Icicle
        AbstractSpell icicle = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "icicle"));
        if (icicle != null) {
            previewDummyOptions.add(SpellWheelOption.ofSpell(new SpellData(icicle, 1), 48));
        }

        // 4. Lightning Lance
        AbstractSpell lance = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning_lance"));
        if (lance != null) {
            previewDummyOptions.add(SpellWheelOption.ofSpell(new SpellData(lance, 1), 16));
        }

        // 5. Ray of Frost
        AbstractSpell ray = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ray_of_frost"));
        if (ray != null) {
            previewDummyOptions.add(SpellWheelOption.ofSpell(new SpellData(ray, 1), 24));
        }

        // 6. Poison Arrow
        AbstractSpell poison = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "poison_arrow"));
        if (poison != null) {
            previewDummyOptions.add(SpellWheelOption.ofSpell(new SpellData(poison, 1), 30));
        }
    }

    private class ColumnsSlider extends AbstractSliderButton {
        public ColumnsSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (currentMaxColumns - 1.0) / 19.0);
            updateMessage();
        }

        public void setValueFromColumns(int cols) {
            this.value = (Mth.clamp(cols, 1, 20) - 1.0) / 19.0;
            currentMaxColumns = cols;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int cols = 1 + (int) Math.round(this.value * 19.0);
            setMessage(Component.literal("Slots/Row: " + cols));
        }

        @Override
        protected void applyValue() {
            currentMaxColumns = 1 + (int) Math.round(this.value * 19.0);
        }
    }

    private class ScaleSlider extends AbstractSliderButton {
        public ScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (currentScale - 0.5) / 1.0);
            updateMessage();
        }

        public void setValueFromScale(double s) {
            this.value = (Mth.clamp(s, 0.5, 1.5) - 0.5) / 1.0;
            currentScale = s;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double s = 0.5 + this.value * 1.0;
            setMessage(Component.literal(String.format("Scale: %d%%", Math.round(s * 100))));
        }

        @Override
        protected void applyValue() {
            currentScale = 0.5 + this.value * 1.0;
        }
    }

    private class NameScaleSlider extends AbstractSliderButton {
        public NameScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (currentNameScale - 0.5) / 1.0);
            updateMessage();
        }

        public void setValueFromScale(double s) {
            this.value = (Mth.clamp(s, 0.5, 1.5) - 0.5) / 1.0;
            currentNameScale = s;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double s = 0.5 + this.value * 1.0;
            setMessage(Component.literal(String.format("Name Size: %d%%", Math.round(s * 100))));
        }

        @Override
        protected void applyValue() {
            currentNameScale = 0.5 + this.value * 1.0;
        }
    }

    private class NameOffsetSlider extends AbstractSliderButton {
        public NameOffsetSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.5);
            resetOffset();
        }

        public void resetOffset() {
            int off = isHorizontalAxis() ? currentNameOffsetX : currentNameOffsetY;
            this.value = (Mth.clamp(off, -100, 100) + 100.0) / 200.0;
            updateMessage();
        }

        private boolean isHorizontalAxis() {
            return currentNamePosition == SpellBulletClientConfig.TitlePosition.LEFT
                    || currentNamePosition == SpellBulletClientConfig.TitlePosition.RIGHT;
        }

        @Override
        protected void updateMessage() {
            int off = -100 + (int) Math.round(this.value * 200.0);
            String axis = isHorizontalAxis() ? "Offset X" : "Offset Y";
            setMessage(Component.literal(String.format("%s: %+dpx", axis, off)));
        }

        @Override
        protected void applyValue() {
            int off = -100 + (int) Math.round(this.value * 200.0);
            if (isHorizontalAxis()) {
                currentNameOffsetX = off;
            } else {
                currentNameOffsetY = off;
            }
        }
    }
}
