package com.ironsspellbullet.client;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.client.gui.SpellBulletActionbarOverlay;
import com.ironsspellbullet.client.gui.SpellWheelOption;
import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.event.ActiveSpellBulletManager;
import com.ironsspellbullet.event.ReloadHelper;
import com.ironsspellbullet.network.ServerboundSelectSpellBulletPacket;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import io.redspace.irons_artifice.data.Value;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.irons_artifice.registry.ItemRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import com.ironsspellbullet.spell.SpellProjectileFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = IronsArmsSpellBulletMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    public static final KeyMapping KEY_SELECT_SPELL_BULLET = new KeyMapping(
            "key.irons_spell_bullet.select_bullet",
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.category.irons_spell_bullet");

    public static final KeyMapping KEY_CONFIG_HUD = new KeyMapping(
            "key.irons_spell_bullet.config_hud",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.irons_spell_bullet");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_SELECT_SPELL_BULLET);
        event.register(KEY_CONFIG_HUD);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.AIR_LEVEL,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID,
                        "spell_bullet_actionbar"),
                SpellBulletActionbarOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        // Tint Iron's Arms native Bullet item with the spell school's color when imbued
        // with a spell!
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0 && ReloadHelper.isSpellBullet(stack)) {
                SpellData data = ReloadHelper.getSpellData(stack);
                if (data != null && !data.equals(SpellData.EMPTY) && data.getSpell() != null) {
                    SchoolType school = data.getSpell().getSchoolType();
                    if (school != null && school.getTargetingColor() != null) {
                        return 0xFF000000 | Utils.packRGB(school.getTargetingColor());
                    }
                }
            }
            return 0xFFFFFFFF;
        }, ItemRegistry.BULLET.get());
    }

    /**
     * Client gameplay event bus subscriber for inputs and ticking.
     */
    @EventBusSubscriber(modid = IronsArmsSpellBulletMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientGameEvents {

        private static int currentSelectionIndex = 0;
        private static int s_LockedHotbarSlot = -1;
        private static final java.util.Map<Integer, float[]> s_SolarBeamLockedAngles = new java.util.concurrent.ConcurrentHashMap<>();

        @SubscribeEvent
        public static void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                net.minecraft.world.entity.Entity entity = event.getEntity();
                if (entity != null && entity.getType().toString().contains("solar_beam")) {
                    if (com.ironsspellbullet.compat.addon.AddonSpellBulletHandler.USE_SOLAR_BEAM_VARIANT_B) {
                        try {
                            // Ensure caster is strictly null on client so entity.tick() never tracks player head/mouse
                            java.lang.reflect.Field casterField = entity.getClass().getField("caster");
                            casterField.set(entity, null);

                            java.lang.reflect.Method getYawMethod = entity.getClass().getMethod("getYaw");
                            java.lang.reflect.Method getPitchMethod = entity.getClass().getMethod("getPitch");
                            float lockedYaw = (Float) getYawMethod.invoke(entity);
                            float lockedPitch = (Float) getPitchMethod.invoke(entity);

                            if (lockedYaw != 0.0f || lockedPitch != 0.0f) {
                                s_SolarBeamLockedAngles.put(entity.getId(), new float[]{lockedYaw, lockedPitch});
                            }

                            java.lang.reflect.Field ry = entity.getClass().getField("renderYaw");
                            java.lang.reflect.Field rp = entity.getClass().getField("renderPitch");
                            java.lang.reflect.Field py = entity.getClass().getField("prevYaw");
                            java.lang.reflect.Field pp = entity.getClass().getField("prevPitch");

                            ry.setFloat(entity, lockedYaw);
                            rp.setFloat(entity, lockedPitch);
                            py.setFloat(entity, lockedYaw);
                            pp.setFloat(entity, lockedPitch);

                            java.lang.reflect.Method calc = entity.getClass().getDeclaredMethod("calculateEndPos");
                            calc.setAccessible(true);
                            calc.invoke(entity);
                        } catch (Throwable ignored) {}
                    } else {
                        // Variant A: Skip charge-up to fire instantly
                        entity.tickCount = 20;
                        try {
                            java.lang.reflect.Field f = entity.getClass().getField("appear");
                            Object anim = f.get(entity);
                            if (anim != null) {
                                java.lang.reflect.Method m = anim.getClass().getMethod("setTimer", int.class);
                                m.invoke(anim, 3);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onClientEntityTickPre(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
            handleSolarBeamClientTick(event.getEntity());
        }

        @SubscribeEvent
        public static void onClientEntityTickPost(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
            handleSolarBeamClientTick(event.getEntity());
        }

        private static void handleSolarBeamClientTick(net.minecraft.world.entity.Entity entity) {
            if (entity != null && entity.level().isClientSide() && entity.getType().toString().contains("solar_beam")) {
                if (!entity.isAlive()) {
                    s_SolarBeamLockedAngles.remove(entity.getId());
                    return;
                }
                try {
                    boolean isVariantB = com.ironsspellbullet.compat.addon.AddonSpellBulletHandler.USE_SOLAR_BEAM_VARIANT_B;

                    if (isVariantB) {
                        // =============================================================
                        // VARIANT B: Stationary Placed Magic Beam
                        // =============================================================
                        // Force caster to null on client so entity.tick() NEVER overwrites angles with mouse/head rotation
                        java.lang.reflect.Field casterField = entity.getClass().getField("caster");
                        casterField.set(entity, null);

                        // Read locked direction from synched entity data (setYaw / setPitch)
                        java.lang.reflect.Method getYawMethod = entity.getClass().getMethod("getYaw");
                        java.lang.reflect.Method getPitchMethod = entity.getClass().getMethod("getPitch");
                        float lockedYaw = (Float) getYawMethod.invoke(entity);
                        float lockedPitch = (Float) getPitchMethod.invoke(entity);

                        float[] cached = s_SolarBeamLockedAngles.get(entity.getId());
                        if (lockedYaw != 0.0f || lockedPitch != 0.0f) {
                            cached = new float[]{lockedYaw, lockedPitch};
                            s_SolarBeamLockedAngles.put(entity.getId(), cached);
                        } else if (cached != null) {
                            lockedYaw = cached[0];
                            lockedPitch = cached[1];
                        }

                        java.lang.reflect.Field ry = entity.getClass().getField("renderYaw");
                        java.lang.reflect.Field rp = entity.getClass().getField("renderPitch");
                        java.lang.reflect.Field py = entity.getClass().getField("prevYaw");
                        java.lang.reflect.Field pp = entity.getClass().getField("prevPitch");

                        ry.setFloat(entity, lockedYaw);
                        rp.setFloat(entity, lockedPitch);
                        py.setFloat(entity, lockedYaw);
                        pp.setFloat(entity, lockedPitch);

                        // Position remains stationary at its world placement point
                        java.lang.reflect.Method calc = entity.getClass().getDeclaredMethod("calculateEndPos");
                        calc.setAccessible(true);
                        calc.invoke(entity);
                    } else {
                        // =============================================================
                        // VARIANT A: Instant Channeling Crosshair-Locked Beam
                        // =============================================================
                        java.lang.reflect.Field casterField = entity.getClass().getField("caster");
                        net.minecraft.world.entity.LivingEntity caster = (net.minecraft.world.entity.LivingEntity) casterField.get(entity);
                        if (caster == null) {
                            java.lang.reflect.Method getCasterId = entity.getClass().getMethod("getCasterID");
                            int id = (Integer) getCasterId.invoke(entity);
                            if (id > 0) {
                                net.minecraft.world.entity.Entity found = entity.level().getEntity(id);
                                if (found instanceof net.minecraft.world.entity.LivingEntity living) {
                                    casterField.set(entity, living);
                                    caster = living;
                                }
                            }
                        }

                        if (caster != null) {
                            float yaw = (float) ((caster.yHeadRot + 90.0f) * Math.PI / 180.0);
                            float pitch = (float) (-caster.getXRot() * Math.PI / 180.0);

                            java.lang.reflect.Field ry = entity.getClass().getField("renderYaw");
                            java.lang.reflect.Field rp = entity.getClass().getField("renderPitch");
                            ry.setFloat(entity, yaw);
                            rp.setFloat(entity, pitch);

                            java.lang.reflect.Field py = entity.getClass().getField("prevYaw");
                            java.lang.reflect.Field pp = entity.getClass().getField("prevPitch");
                            if (Math.abs(py.getFloat(entity)) < 1e-4) py.setFloat(entity, yaw);
                            if (Math.abs(pp.getFloat(entity)) < 1e-4) pp.setFloat(entity, pitch);

                            net.minecraft.world.phys.Vec3 look = caster.getLookAngle().normalize().scale(1.0);
                            entity.setPos(caster.getX() + look.x, caster.getY() + 1.2 + look.y, caster.getZ() + look.z);

                            java.lang.reflect.Method calc = entity.getClass().getDeclaredMethod("calculateEndPos");
                            calc.setAccessible(true);
                            calc.invoke(entity);
                        }

                        if (entity.tickCount < 20) {
                            entity.tickCount = 20;
                        }
                        java.lang.reflect.Field appearField = entity.getClass().getField("appear");
                        Object anim = appearField.get(entity);
                        if (anim != null) {
                            java.lang.reflect.Method setTimer = anim.getClass().getMethod("setTimer", int.class);
                            setTimer.invoke(anim, 3);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            SpellBulletActionbarOverlay.tick();

            Minecraft mc = Minecraft.getInstance();

            // Solar Beam channel lock: keep the firing weapon selected until the beam ends
            if (mc.player != null && com.ironsspellbullet.event.SolarBeamLockManager.isLocked(mc.player)) {
                if (s_LockedHotbarSlot < 0) {
                    s_LockedHotbarSlot = mc.player.getInventory().selected;
                } else if (mc.player.getInventory().selected != s_LockedHotbarSlot) {
                    mc.player.getInventory().selected = s_LockedHotbarSlot;
                }
                return;
            }
            s_LockedHotbarSlot = -1;
            if (mc.player != null && mc.screen == null) {
                // Check in-game keybind to open HUD configuration screen
                while (KEY_CONFIG_HUD.consumeClick()) {
                    mc.setScreen(new com.ironsspellbullet.client.gui.SpellBulletHudConfigScreen(null));
                    return;
                }

                boolean isAltActive = KEY_SELECT_SPELL_BULLET.isDown() || Screen.hasAltDown();
                ItemStack main = mc.player.getMainHandItem();
                ItemStack off = mc.player.getOffhandItem();
                boolean holdingGun = (main.getItem() instanceof GunItem) || (off.getItem() instanceof GunItem);

                // Auto-fallback on weapon switch:
                // If holding a shotgun while having an incompatible spell selected (e.g.
                // switched from Rifle to Shotgun),
                // immediately revert client selection to Normal Bullet and notify the server!
                if (holdingGun && isHoldingShotgun(mc.player)) {
                    String currentId = ActiveSpellBulletManager.getSelectedSpell(mc.player.getUUID());
                    if (currentId != null && !currentId.isEmpty()
                            && !ActiveSpellBulletManager.NORMAL_BULLET.equals(currentId)) {
                        AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry
                                .getSpell(net.minecraft.resources.ResourceLocation.parse(currentId));
                        if (!SpellProjectileFactory.isAllowedInShotgun(spell)) {
                            ActiveSpellBulletManager.setSelectedSpell(mc.player.getUUID(),
                                    ActiveSpellBulletManager.NORMAL_BULLET);
                            PacketDistributor.sendToServer(
                                    new ServerboundSelectSpellBulletPacket(ActiveSpellBulletManager.NORMAL_BULLET));
                        }
                    }
                }

                // If holding Alt while wielding a gun, show the Action Bar continuously
                if (isAltActive && holdingGun) {
                    List<SpellWheelOption> available = getAvailableOptions(mc.player);
                    if (!available.isEmpty()) {
                        String currentId = ActiveSpellBulletManager.getSelectedSpell(mc.player.getUUID());
                        int foundIdx = 0;
                        for (int i = 0; i < available.size(); i++) {
                            if (available.get(i).id().equals(currentId)) {
                                foundIdx = i;
                                break;
                            }
                        }
                        currentSelectionIndex = foundIdx;
                        SpellBulletActionbarOverlay.show(SpellBulletActionbarOverlay.MAX_DISPLAY_TICKS, available,
                                currentSelectionIndex);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null)
                return;

            // Block both hotbar scrolling and ammo scrolling while a Solar Beam is
            // channeling
            if (com.ironsspellbullet.event.SolarBeamLockManager.isLocked(mc.player)) {
                event.setCanceled(true);
                return;
            }

            // Trigger when holding Alt or the dedicated keybind
            boolean isAltActive = KEY_SELECT_SPELL_BULLET.isDown() || Screen.hasAltDown();
            if (!isAltActive)
                return;

            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            boolean holdingGun = (main.getItem() instanceof GunItem) || (off.getItem() instanceof GunItem);
            if (!holdingGun)
                return;

            List<SpellWheelOption> available = getAvailableOptions(mc.player);
            if (available.isEmpty())
                return;

            // Cancel hotbar scroll so player doesn't change active hotbar slot
            event.setCanceled(true);

            // Synchronize selection index with active selected spell
            String currentId = ActiveSpellBulletManager.getSelectedSpell(mc.player.getUUID());
            int foundIdx = -1;
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).id().equals(currentId)) {
                    foundIdx = i;
                    break;
                }
            }
            if (foundIdx != -1) {
                currentSelectionIndex = foundIdx;
            }

            double scrollDelta = event.getScrollDeltaY();
            if (scrollDelta > 0) {
                currentSelectionIndex--;
            } else if (scrollDelta < 0) {
                currentSelectionIndex++;
            }

            currentSelectionIndex = Math.floorMod(currentSelectionIndex, available.size());
            SpellWheelOption selected = available.get(currentSelectionIndex);

            // Play crisp feedback click sound
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.6f);

            // Update client state and notify server
            ActiveSpellBulletManager.setSelectedSpell(mc.player.getUUID(), selected.id());
            PacketDistributor.sendToServer(new ServerboundSelectSpellBulletPacket(selected.id()));

            // Activate and display Action Bar HUD overlay
            SpellBulletActionbarOverlay.show(SpellBulletActionbarOverlay.MAX_DISPLAY_TICKS, available,
                    currentSelectionIndex);
        }

        public static boolean isHoldingShotgun(Player player) {
            if (player == null)
                return false;
            return ReloadHelper.isShotgun(player.getMainHandItem()) || ReloadHelper.isShotgun(player.getOffhandItem());
        }

        public static boolean isShotgunItem(ItemStack stack) {
            return ReloadHelper.isShotgun(stack);
        }

        public static List<SpellWheelOption> getAvailableOptions(Player player) {
            List<SpellWheelOption> options = new ArrayList<>();
            if (player == null)
                return options;

            boolean isCreative = player.hasInfiniteMaterials();
            boolean holdingShotgun = isHoldingShotgun(player);

            // Count standard bullets (including Ammo Box / containers)
            int normalBullets = isCreative ? 999
                    : ReloadHelper.countSpecificBullets(player, ActiveSpellBulletManager.NORMAL_BULLET);

            // Find all distinct spell-imbued bullets in player inventory
            Map<String, SpellData> distinctSpells = new LinkedHashMap<>();
            Map<String, Integer> spellCounts = new LinkedHashMap<>();

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (ReloadHelper.isSpellBullet(stack)) {
                    SpellData data = ReloadHelper.getSpellData(stack);
                    if (data != null && data.getSpell() != null
                            && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(data.getSpell())) {
                        AbstractSpell spell = data.getSpell();
                        // For shotguns: strictly only allow Projectile spells and Slug spells!
                        if (holdingShotgun && !SpellProjectileFactory.isAllowedInShotgun(spell)) {
                            continue;
                        }
                        String id = spell.getSpellResource().toString();
                        distinctSpells.putIfAbsent(id, data);
                        spellCounts.put(id, spellCounts.getOrDefault(id, 0) + (isCreative ? 999 : stack.getCount()));
                    }
                }
            }

            // Also check offhand item
            ItemStack offhand = player.getOffhandItem();
            if (ReloadHelper.isSpellBullet(offhand)) {
                SpellData data = ReloadHelper.getSpellData(offhand);
                if (data != null && data.getSpell() != null
                        && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(data.getSpell())) {
                    AbstractSpell spell = data.getSpell();
                    if (!holdingShotgun || SpellProjectileFactory.isAllowedInShotgun(spell)) {
                        String id = spell.getSpellResource().toString();
                        distinctSpells.putIfAbsent(id, data);
                        spellCounts.put(id, spellCounts.getOrDefault(id, 0) + (isCreative ? 999 : offhand.getCount()));
                    }
                }
            }

            // Always add Normal Bullet as option 0 if player has normal bullets, any spell
            // bullets, or is in Creative mode
            if (isCreative || normalBullets > 0 || !distinctSpells.isEmpty()) {
                options.add(SpellWheelOption.ofNormal(normalBullets));
            }

            // Add each distinct spell bullet
            for (Map.Entry<String, SpellData> entry : distinctSpells.entrySet()) {
                String id = entry.getKey();
                int count = spellCounts.getOrDefault(id, isCreative ? 999 : 0);
                options.add(SpellWheelOption.ofSpell(entry.getValue(), count, player));
            }

            return options;
        }

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) return;

            // 1. Imbued Iron's Arms Bullet Tooltip
            if (ReloadHelper.isSpellBullet(stack)) {
                SpellData spellData = ReloadHelper.getSpellData(stack);
                if (spellData != null && spellData.getSpell() != null) {
                    AbstractSpell spell = spellData.getSpell();
                    List<Component> tooltipComponents = event.getToolTip();

                    if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                        tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.incompatible_mobility")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }

                    MutableComponent schoolComponent = Component.translatable(spell.getSchoolType().getDisplayName().getString())
                            .withStyle(spell.getSchoolType().getDisplayName().getStyle());

                    tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.school_info", schoolComponent)
                            .withStyle(ChatFormatting.GRAY));
                    tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.instant_fire")
                            .withStyle(ChatFormatting.GOLD));
                    tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.zero_mana")
                            .withStyle(ChatFormatting.AQUA));

                    float spellDamage = SpellProjectileFactory.computeAuthenticSpellDamage(spell, spellData.getLevel(), event.getEntity());
                    if (spellDamage > 0.0f) {
                        tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.spell_damage", String.format("%.1f", spellDamage))
                                .withStyle(ChatFormatting.GREEN));
                    }

                    // Shotgun compatibility indicator
                    if (SpellProjectileFactory.isSlugSpell(spell)) {
                        tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.shotgun_slug")
                                .withStyle(ChatFormatting.GOLD));
                    } else if (SpellProjectileFactory.isProjectileSpell(spell)) {
                        tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.shotgun_pellet")
                                .withStyle(ChatFormatting.AQUA));
                    } else {
                        tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.shotgun_incompatible")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                return;
            }

            // 2. Firearm Loaded Chamber Tooltip
            if (!(stack.getItem() instanceof GunItem)) return;

            SpellChamberData chamber = stack.get(ModDataComponents.LOADED_SPELLS.get());
            if (chamber == null || chamber.isEmpty()) return;

            List<Component> tooltips = event.getToolTip();
            tooltips.add(Component.translatable("tooltip.irons_spell_bullet.chamber_header")
                    .withStyle(ChatFormatting.GOLD));

            // Group consecutive/identical spells for clean display
            Map<String, Integer> spellCounts = new LinkedHashMap<>();
            for (SpellChamberData.SpellSlotEntry entry : chamber.loadedSpells()) {
                if (entry.hasSpell()) {
                    AbstractSpell spell = entry.getSpell();
                    String displayName = spell != null ? spell.getDisplayName(event.getEntity()).getString() + " " + entry.spellLevel() : entry.spellId();
                    spellCounts.put(displayName, spellCounts.getOrDefault(displayName, 0) + 1);
                } else {
                    spellCounts.put("Normal Bullet", spellCounts.getOrDefault("Normal Bullet", 0) + 1);
                }
            }

            for (Map.Entry<String, Integer> entry : spellCounts.entrySet()) {
                MutableComponent line = Component.literal(" • " + entry.getKey());
                if (entry.getValue() > 1) {
                    line.append(Component.literal(" (x" + entry.getValue() + ")").withStyle(ChatFormatting.DARK_GRAY));
                }
                line.withStyle(ChatFormatting.AQUA);
                tooltips.add(line);
            }
        }
    }
}
