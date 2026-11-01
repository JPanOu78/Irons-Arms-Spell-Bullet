package com.ironsspellbullet.event;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.item.SpellBulletItem;
import io.redspace.irons_artifice.item.BulletContainerItem;
import io.redspace.irons_artifice.utils.IronsArtificeTags;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReloadHelper {

    public static void smartConsumeAndRecord(Player player, ItemStack gunStack, int amount) {
        if (player == null || gunStack == null || amount <= 0) return;

        List<SpellChamberData.SpellSlotEntry> consumedEntries = new ArrayList<>();
        int remaining = amount;

        // 1. Prioritize Offhand if holding Spell Bullets
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() instanceof SpellBulletItem) {
            int take = Math.min(remaining, offhand.getCount());
            SpellData spellData = SpellBulletItem.getSpellData(offhand);
            SpellChamberData.SpellSlotEntry entry = SpellChamberData.SpellSlotEntry.fromSpellData(spellData);
            for (int i = 0; i < take; i++) {
                consumedEntries.add(entry);
            }
            offhand.shrink(take);
            remaining -= take;
        }

        // 2. Check player inventory
        Inventory inv = player.getInventory();
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining <= 0) break;
                ItemStack slotStack = inv.getItem(i);
                if (slotStack.isEmpty()) continue;

                if (slotStack.getItem() instanceof SpellBulletItem) {
                    int take = Math.min(remaining, slotStack.getCount());
                    SpellData spellData = SpellBulletItem.getSpellData(slotStack);
                    SpellChamberData.SpellSlotEntry entry = SpellChamberData.SpellSlotEntry.fromSpellData(spellData);
                    for (int j = 0; j < take; j++) {
                        consumedEntries.add(entry);
                    }
                    slotStack.shrink(take);
                    remaining -= take;
                } else if (slotStack.is(IronsArtificeTags.AMMO)) {
                    int take = Math.min(remaining, slotStack.getCount());
                    for (int j = 0; j < take; j++) {
                        consumedEntries.add(SpellChamberData.SpellSlotEntry.EMPTY);
                    }
                    slotStack.shrink(take);
                    remaining -= take;
                }
            }
        }

        // 3. Check Bullet Containers / Pouches
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining <= 0) break;
                ItemStack slotStack = inv.getItem(i);
                if (slotStack.getItem() instanceof BulletContainerItem) {
                    int drained = BulletContainerItem.drain(slotStack, remaining);
                    for (int j = 0; j < drained; j++) {
                        consumedEntries.add(SpellChamberData.SpellSlotEntry.EMPTY);
                    }
                    remaining -= drained;
                }
            }
        }

        // Update gun chamber data
        SpellChamberData existingChamber = gunStack.getOrDefault(ModDataComponents.LOADED_SPELLS.get(), SpellChamberData.EMPTY);
        gunStack.set(ModDataComponents.LOADED_SPELLS.get(), existingChamber.withAdded(consumedEntries));
    }
}
