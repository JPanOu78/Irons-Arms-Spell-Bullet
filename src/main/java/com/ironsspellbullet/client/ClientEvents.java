package com.ironsspellbullet.client;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.client.gui.SpellBulletWheelOverlay;
import com.ironsspellbullet.client.gui.SpellWheelOption;
import com.ironsspellbullet.event.ActiveSpellBulletManager;
import com.ironsspellbullet.event.ReloadHelper;
import com.ironsspellbullet.item.ModItems;
import com.ironsspellbullet.item.SpellBulletItem;
import com.ironsspellbullet.network.ServerboundSelectSpellBulletPacket;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
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

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_SELECT_SPELL_BULLET);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "spell_bullet_wheel"),
                SpellBulletWheelOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                SpellData data = SpellBulletItem.getSpellData(stack);
                if (data != null && !data.equals(SpellData.EMPTY) && data.getSpell() != null) {
                    SchoolType school = data.getSpell().getSchoolType();
                    if (school != null && school.getTargetingColor() != null) {
                        // Ensure alpha is 0xFF (255) so item is fully opaque and vibrantly colored
                        return 0xFF000000 | Utils.packRGB(school.getTargetingColor());
                    }
                }
            }
            return 0xFFFFFFFF;
        }, ModItems.SPELL_BULLET.get());
    }

    /**
     * Client gameplay event bus subscriber for inputs and ticking.
     */
    @EventBusSubscriber(modid = IronsArmsSpellBulletMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientGameEvents {

        private static int currentSelectionIndex = 0;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            SpellBulletWheelOverlay.tick();
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;

            // Trigger when holding Alt or the dedicated keybind
            boolean isAltActive = KEY_SELECT_SPELL_BULLET.isDown() || Screen.hasAltDown();
            if (!isAltActive) return;

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

            // Play light feedback click sound
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.8f);

            // Update client state and notify server
            ActiveSpellBulletManager.setSelectedSpell(mc.player.getUUID(), selected.id());
            PacketDistributor.sendToServer(new ServerboundSelectSpellBulletPacket(selected.id()));

            // Activate and display HUD overlay
            SpellBulletWheelOverlay.show(70, available, currentSelectionIndex);
        }

        public static List<SpellWheelOption> getAvailableOptions(Player player) {
            List<SpellWheelOption> options = new ArrayList<>();
            if (player == null) return options;

            // Count standard bullets (including Ammo Box / containers)
            int normalBullets = ReloadHelper.countSpecificBullets(player, ActiveSpellBulletManager.NORMAL_BULLET);

            // Find all distinct spell bullets in player inventory
            Map<String, SpellData> distinctSpells = new LinkedHashMap<>();
            Map<String, Integer> spellCounts = new LinkedHashMap<>();

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof SpellBulletItem && SpellBulletItem.hasSpell(stack)) {
                    SpellData data = SpellBulletItem.getSpellData(stack);
                    if (data != null && data.getSpell() != null) {
                        String id = data.getSpell().getSpellResource().toString();
                        distinctSpells.putIfAbsent(id, data);
                        spellCounts.put(id, spellCounts.getOrDefault(id, 0) + stack.getCount());
                    }
                }
            }

            // Always add Normal Bullet as option 0 if player has normal bullets or any spell bullets
            if (normalBullets > 0 || !distinctSpells.isEmpty()) {
                options.add(SpellWheelOption.ofNormal(normalBullets));
            }

            // Add each distinct spell bullet
            for (Map.Entry<String, SpellData> entry : distinctSpells.entrySet()) {
                String id = entry.getKey();
                int count = spellCounts.getOrDefault(id, 0);
                options.add(SpellWheelOption.ofSpell(entry.getValue(), count, player));
            }

            return options;
        }
    }
}
