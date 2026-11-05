package com.ironsspellbullet.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class SpellBulletClientConfig {

    public enum HudAnchor {
        HOTBAR_CENTER("Hotbar Center"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right"),
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        CENTER("Screen Center");

        private final String displayName;

        HudAnchor(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum HudOrientation {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String displayName;

        HudOrientation(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum TitlePosition {
        ABOVE("Above Slots"),
        BELOW("Below Slots"),
        LEFT("Left of Slots"),
        RIGHT("Right of Slots");

        private final String displayName;

        TitlePosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final SpellBulletClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<SpellBulletClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(SpellBulletClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.EnumValue<HudAnchor> anchor;
    public final ModConfigSpec.IntValue offsetX;
    public final ModConfigSpec.IntValue offsetY;
    public final ModConfigSpec.EnumValue<HudOrientation> orientation;
    public final ModConfigSpec.IntValue maxColumns;
    public final ModConfigSpec.DoubleValue scale;

    public final ModConfigSpec.BooleanValue showBulletName;
    public final ModConfigSpec.EnumValue<TitlePosition> namePosition;
    public final ModConfigSpec.DoubleValue nameScale;
    public final ModConfigSpec.IntValue nameOffsetX;
    public final ModConfigSpec.IntValue nameOffsetY;

    public SpellBulletClientConfig(ModConfigSpec.Builder builder) {
        builder.comment("Spell Bullet Action Bar HUD Client Configuration")
                .push("hud");

        anchor = builder
                .comment("Screen Anchor position for the Action Bar")
                .defineEnum("anchor", HudAnchor.HOTBAR_CENTER);

        offsetX = builder
                .comment("Horizontal offset in pixels from the anchor position")
                .defineInRange("offsetX", 0, -1000, 1000);

        offsetY = builder
                .comment("Vertical offset in pixels from the anchor position")
                .defineInRange("offsetY", 0, -1000, 1000);

        orientation = builder
                .comment("Layout orientation of bullet slots (Horizontal or Vertical)")
                .defineEnum("orientation", HudOrientation.HORIZONTAL);

        maxColumns = builder
                .comment("Maximum number of slots per row (or per column in vertical orientation)")
                .defineInRange("maxColumns", 9, 1, 20);

        scale = builder
                .comment("HUD Scale multiplier (0.5 = 50%, 1.0 = 100%, 1.5 = 150%)")
                .defineInRange("scale", 1.0, 0.5, 2.0);

        showBulletName = builder
                .comment("Whether to display the active spell bullet's name and info")
                .define("showBulletName", true);

        namePosition = builder
                .comment("Position of the spell bullet name relative to the slots (Above, Below, Left, Right)")
                .defineEnum("namePosition", TitlePosition.ABOVE);

        nameScale = builder
                .comment("Scale multiplier for the spell bullet name (0.5 = 50%, 1.0 = 100%, 1.5 = 150%)")
                .defineInRange("nameScale", 1.0, 0.5, 2.0);

        nameOffsetX = builder
                .comment("Additional horizontal offset in pixels for the spell bullet name")
                .defineInRange("nameOffsetX", 0, -300, 300);

        nameOffsetY = builder
                .comment("Additional vertical offset in pixels for the spell bullet name")
                .defineInRange("nameOffsetY", 0, -300, 300);

        builder.pop();
    }
}
