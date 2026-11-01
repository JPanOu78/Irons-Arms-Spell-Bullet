package com.ironsspellbullet.client;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.client.gui.SpellBulletWheelOverlay;
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

            List<SpellData> available = getAvailableSpellBullets(mc.player);
            if (available.isEmpty()) return;

            // Cancel hotbar scroll so player doesn't change active hotbar slot
            event.setCanceled(true);

            double scrollDelta = event.getScrollDeltaY();
            if (scrollDelta > 0) {
                currentSelectionIndex--;
            } else if (scrollDelta < 0) {
                currentSelectionIndex++;
            }

            currentSelectionIndex = Math.floorMod(currentSelectionIndex, available.size());
            SpellData selected = available.get(currentSelectionIndex);

            // Play light feedback click sound
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.8f);

            // Notify server of selected spell bullet
            PacketDistributor.sendToServer(new ServerboundSelectSpellBulletPacket(selected.getSpell().getSpellResource().toString()));

            // Activate and display HUD overlay
            SpellBulletWheelOverlay.show(70, available, currentSelectionIndex);
        }

        public static List<SpellData> getAvailableSpellBullets(Player player) {
            Map<String, SpellData> distinct = new LinkedHashMap<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof SpellBulletItem && SpellBulletItem.hasSpell(stack)) {
                    SpellData data = SpellBulletItem.getSpellData(stack);
                    String id = data.getSpell().getSpellResource().toString() + "_" + data.getLevel();
                    distinct.putIfAbsent(id, data);
                }
            }
            return new ArrayList<>(distinct.values());
        }
    }
}
