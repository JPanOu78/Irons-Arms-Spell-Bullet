package com.ironsspellbullet.client;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.client.gui.SpellBulletActionbarOverlay;
import com.ironsspellbullet.client.gui.SpellWheelOption;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
            "key.category.irons_spell_bullet"
    );

    public static final KeyMapping KEY_CONFIG_HUD = new KeyMapping(
            "key.irons_spell_bullet.config_hud",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.irons_spell_bullet"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_SELECT_SPELL_BULLET);
        event.register(KEY_CONFIG_HUD);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.AIR_LEVEL,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "spell_bullet_actionbar"),
                SpellBulletActionbarOverlay.INSTANCE
        );
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        // Tint Iron's Arms native Bullet item with the spell school's color when imbued with a spell!
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

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            SpellBulletActionbarOverlay.tick();

            Minecraft mc = Minecraft.getInstance();
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
                        SpellBulletActionbarOverlay.show(SpellBulletActionbarOverlay.MAX_DISPLAY_TICKS, available, currentSelectionIndex);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;

            // Trigger when holding Alt or the dedicated keybind
            boolean isAltActive = KEY_SELECT_SPELL_BULLET.isDown() || Screen.hasAltDown();
            if (!isAltActive) return;

            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            boolean holdingGun = (main.getItem() instanceof GunItem) || (off.getItem() instanceof GunItem);
            if (!holdingGun) return;

            List<SpellWheelOption> available = getAvailableOptions(mc.player);
            if (available.isEmpty()) return;

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
            SpellBulletActionbarOverlay.show(SpellBulletActionbarOverlay.MAX_DISPLAY_TICKS, available, currentSelectionIndex);
        }

        public static boolean isHoldingShotgun(Player player) {
            if (player == null) return false;
            return isShotgunItem(player.getMainHandItem()) || isShotgunItem(player.getOffhandItem());
        }

        public static boolean isShotgunItem(ItemStack stack) {
            if (stack != null && stack.getItem() instanceof GunItem gunItem) {
                if (gunItem.getGun() != null && gunItem.getGun().baseProfile() != null) {
                    Value countVal = gunItem.getGun().baseProfile().getOrDefault(io.redspace.irons_artifice.data.ShotComponents.PROJECTILE_COUNT);
                    if (countVal != null) {
                        return countVal.compute() > 1.5;
                    }
                }
            }
            return false;
        }

        public static List<SpellWheelOption> getAvailableOptions(Player player) {
            List<SpellWheelOption> options = new ArrayList<>();
            if (player == null) return options;

            boolean isCreative = player.hasInfiniteMaterials();
            boolean holdingShotgun = isHoldingShotgun(player);

            // Count standard bullets (including Ammo Box / containers)
            int normalBullets = isCreative ? 999 : ReloadHelper.countSpecificBullets(player, ActiveSpellBulletManager.NORMAL_BULLET);

            // Find all distinct spell-imbued bullets in player inventory
            Map<String, SpellData> distinctSpells = new LinkedHashMap<>();
            Map<String, Integer> spellCounts = new LinkedHashMap<>();

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (ReloadHelper.isSpellBullet(stack)) {
                    SpellData data = ReloadHelper.getSpellData(stack);
                    if (data != null && data.getSpell() != null && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(data.getSpell())) {
                        AbstractSpell spell = data.getSpell();
                        // For shotguns: only allow projectile and ray/beam spells! Exclude area zones, summons, foot trailing, etc.
                        if (holdingShotgun && !SpellProjectileFactory.isProjectileSpell(spell)) {
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
                if (data != null && data.getSpell() != null && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(data.getSpell())) {
                    AbstractSpell spell = data.getSpell();
                    if (!holdingShotgun || SpellProjectileFactory.isProjectileSpell(spell)) {
                        String id = spell.getSpellResource().toString();
                        distinctSpells.putIfAbsent(id, data);
                        spellCounts.put(id, spellCounts.getOrDefault(id, 0) + (isCreative ? 999 : offhand.getCount()));
                    }
                }
            }

            // Always add Normal Bullet as option 0 if player has normal bullets, any spell bullets, or is in Creative mode
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
    }
}
