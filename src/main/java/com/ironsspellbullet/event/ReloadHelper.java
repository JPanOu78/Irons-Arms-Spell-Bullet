package com.ironsspellbullet.event;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.item.ModItems;
import com.ironsspellbullet.item.SpellBulletItem;
import io.redspace.irons_artifice.item.BulletContainerItem;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.irons_artifice.item.MagazineContents;
import io.redspace.irons_artifice.registry.ItemRegistry;
import io.redspace.irons_artifice.utils.IronsArtificeTags;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReloadHelper {

    /**
     * Counts reserve bullets available for the active selected bullet type only.
     */
    public static int countSpecificBullets(Player player, String activeBulletId) {
        if (player == null) return 0;
        Inventory inv = player.getInventory();
        int count = 0;

        if (ActiveSpellBulletManager.NORMAL_BULLET.equals(activeBulletId)) {
            // Count standard bullets only
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;

                if (isNormalBullet(stack)) {
                    count += stack.getCount();
                } else if (stack.getItem() instanceof BulletContainerItem) {
                    count += BulletContainerItem.count(stack);
                }
            }
        } else {
            // Count specific spell bullets only
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;

                if (stack.getItem() instanceof SpellBulletItem && matchesSpell(stack, activeBulletId)) {
                    count += stack.getCount();
                }
            }
        }

        return count;
    }

    /**
     * Consumes ONLY the chosen bullet type and records it into the gun's chamber.
     */
    public static void smartConsumeAndRecord(Player player, ItemStack gunStack, int amount) {
        if (player == null || gunStack == null || amount <= 0) return;

        List<SpellChamberData.SpellSlotEntry> consumedEntries = new ArrayList<>();
        int remaining = amount;
        String activeSelected = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());

        if (ActiveSpellBulletManager.NORMAL_BULLET.equals(activeSelected)) {
            // Consume ONLY normal bullets
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining <= 0) break;
                ItemStack stack = inv.getItem(i);
                if (isNormalBullet(stack)) {
                    int take = Math.min(remaining, stack.getCount());
                    for (int j = 0; j < take; j++) consumedEntries.add(SpellChamberData.SpellSlotEntry.EMPTY);
                    stack.shrink(take);
                    remaining -= take;
                }
            }

            // Fallback to bullet container
            if (remaining > 0) {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (remaining <= 0) break;
                    ItemStack stack = inv.getItem(i);
                    if (stack.getItem() instanceof BulletContainerItem) {
                        int drained = BulletContainerItem.drain(stack, remaining);
                        for (int j = 0; j < drained; j++) consumedEntries.add(SpellChamberData.SpellSlotEntry.EMPTY);
                        remaining -= drained;
                    }
                }
            }
        } else {
            // Consume ONLY the active chosen spell bullet
            ItemStack offhand = player.getOffhandItem();
            if (remaining > 0 && offhand.getItem() instanceof SpellBulletItem && matchesSpell(offhand, activeSelected)) {
                int take = Math.min(remaining, offhand.getCount());
                SpellData spellData = SpellBulletItem.getSpellData(offhand);
                SpellChamberData.SpellSlotEntry entry = SpellChamberData.SpellSlotEntry.fromSpellData(spellData);
                for (int i = 0; i < take; i++) consumedEntries.add(entry);
                offhand.shrink(take);
                remaining -= take;
            }

            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining <= 0) break;
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof SpellBulletItem && matchesSpell(stack, activeSelected)) {
                    int take = Math.min(remaining, stack.getCount());
                    SpellData spellData = SpellBulletItem.getSpellData(stack);
                    SpellChamberData.SpellSlotEntry entry = SpellChamberData.SpellSlotEntry.fromSpellData(spellData);
                    for (int j = 0; j < take; j++) consumedEntries.add(entry);
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }

        // Update gun chamber data
        SpellChamberData existingChamber = gunStack.getOrDefault(ModDataComponents.LOADED_SPELLS.get(), SpellChamberData.EMPTY);
        gunStack.set(ModDataComponents.LOADED_SPELLS.get(), existingChamber.withAdded(consumedEntries));
    }

    /**
     * Checks if the currently loaded rounds in the gun match the active selection.
     */
    public static boolean isChamberMatchingSelection(ItemStack gunStack, String activeSelection) {
        if (gunStack == null || gunStack.isEmpty()) return true;
        MagazineContents magazine = GunItem.getMagazine(gunStack);
        if (magazine.count() <= 0) return true;

        SpellChamberData chamber = gunStack.getOrDefault(ModDataComponents.LOADED_SPELLS.get(), SpellChamberData.EMPTY);
        List<SpellChamberData.SpellSlotEntry> entries = chamber.loadedSpells();

        if (ActiveSpellBulletManager.NORMAL_BULLET.equals(activeSelection)) {
            for (SpellChamberData.SpellSlotEntry entry : entries) {
                if (entry != null && entry.hasSpell()) {
                    return false;
                }
            }
            return true;
        } else {
            if (entries.size() < magazine.count()) {
                return false;
            }
            for (SpellChamberData.SpellSlotEntry entry : entries) {
                if (entry == null || !entry.hasSpell()) {
                    return false;
                }
                if (!entry.spellId().equals(activeSelection)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Safely unloads all rounds from the gun back into the player's inventory.
     */
    public static void unloadGunChamber(Player player, ItemStack gunStack) {
        if (player == null || gunStack == null) return;

        MagazineContents magazine = GunItem.getMagazine(gunStack);
        int totalCount = magazine.count();
        if (totalCount <= 0) return;

        SpellChamberData chamber = gunStack.getOrDefault(ModDataComponents.LOADED_SPELLS.get(), SpellChamberData.EMPTY);
        List<SpellChamberData.SpellSlotEntry> loaded = new ArrayList<>(chamber.loadedSpells());

        while (loaded.size() < totalCount) {
            loaded.add(SpellChamberData.SpellSlotEntry.EMPTY);
        }

        for (int i = 0; i < totalCount; i++) {
            SpellChamberData.SpellSlotEntry entry = loaded.get(i);
            ItemStack bulletStack;
            if (entry != null && entry.hasSpell()) {
                bulletStack = new ItemStack(ModItems.SPELL_BULLET.get());
                AbstractSpell spell = entry.getSpell();
                if (spell != null) {
                    ISpellContainer.createScrollContainer(spell, entry.spellLevel(), bulletStack);
                }
            } else {
                bulletStack = new ItemStack(ItemRegistry.BULLET.get());
            }

            if (!player.getInventory().add(bulletStack)) {
                player.drop(bulletStack, false);
            }
        }

        gunStack.remove(ModDataComponents.LOADED_SPELLS.get());
        GunItem.setMagazine(gunStack, MagazineContents.EMPTY);
    }

    public static boolean isNormalBullet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof SpellBulletItem) return false;
        return stack.is(ItemRegistry.BULLET.get()) || stack.is(IronsArtificeTags.AMMO);
    }

    public static boolean matchesSpell(ItemStack stack, String spellId) {
        SpellData data = SpellBulletItem.getSpellData(stack);
        if (data != null && data.getSpell() != null) {
            return data.getSpell().getSpellResource().toString().equals(spellId);
        }
        return false;
    }
}
