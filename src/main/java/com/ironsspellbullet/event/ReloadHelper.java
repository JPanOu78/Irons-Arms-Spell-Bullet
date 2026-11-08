package com.ironsspellbullet.event;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import io.redspace.irons_artifice.item.BulletContainerItem;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.irons_artifice.item.MagazineContents;
import io.redspace.irons_artifice.registry.ItemRegistry;
import io.redspace.irons_artifice.utils.IronsArtificeTags;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReloadHelper {

    /**
     * Creates an Iron's Arms Bullet ItemStack imbued with the specified spell and level.
     */
    public static ItemStack createSpellBullet(AbstractSpell spell, int level, int count) {
        ItemStack result = new ItemStack(ItemRegistry.BULLET.get(), count);
        if (spell != null) {
            ISpellContainer.createScrollContainer(spell, level, result);
            result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            Component spellName = Component.translatable(spell.getComponentId())
                    .append(" ")
                    .append(Component.translatable("enchantment.level." + level));
            result.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.irons_spell_bullet.spell_bullet.named", spellName));
        }
        return result;
    }

    /**
     * Returns true if the stack is an Iron's Arms bullet (or ammo) imbued with a valid spell.
     */
    public static boolean isSpellBullet(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.is(ItemRegistry.BULLET.get()) && !stack.is(IronsArtificeTags.AMMO)) return false;
        return hasSpell(stack);
    }

    public static boolean hasSpell(ItemStack stack) {
        SpellData data = getSpellData(stack);
        return data != null && !data.equals(SpellData.EMPTY) && data.getSpell() != null;
    }

    public static SpellData getSpellData(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack)) {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container != null && !container.isEmpty()) {
                return container.getSpellAtIndex(0);
            }
        }
        return SpellData.EMPTY;
    }

    /**
     * Counts reserve bullets available for the active selected bullet type only.
     */
    public static int countSpecificBullets(Player player, String activeBulletId) {
        if (player == null) return 0;
        Inventory inv = player.getInventory();
        int count = 0;

        if (ActiveSpellBulletManager.NORMAL_BULLET.equals(activeBulletId)) {
            // Count standard (non-spell) bullets only
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

                if (isSpellBullet(stack) && matchesSpell(stack, activeBulletId)) {
                    count += stack.getCount();
                }
            }
        }

        return count;
    }

    /**
     * Checks whether an item stack is a shotgun (fires > 1.5 projectiles per shot).
     */
    public static boolean isShotgun(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof GunItem gunItem) {
            if (gunItem.getGun() != null && gunItem.getGun().baseProfile() != null) {
                io.redspace.irons_artifice.data.Value countVal = gunItem.getGun().baseProfile().getOrDefault(io.redspace.irons_artifice.data.ShotComponents.PROJECTILE_COUNT);
                if (countVal != null) {
                    return countVal.compute() > 1.5;
                }
            }
        }
        return false;
    }

    /**
     * Consumes ONLY the chosen bullet type and records it into the gun's chamber.
     */
    public static void smartConsumeAndRecord(Player player, ItemStack gunStack, int amount) {
        if (player == null || gunStack == null || amount <= 0) return;

        List<SpellChamberData.SpellSlotEntry> consumedEntries = new ArrayList<>();
        int remaining = amount;
        String activeSelected = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());

        // For shotguns: verify that the chosen spell is strictly a Projectile or Slug spell
        if (!ActiveSpellBulletManager.NORMAL_BULLET.equals(activeSelected) && isShotgun(gunStack)) {
            AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(net.minecraft.resources.ResourceLocation.parse(activeSelected));
            if (!com.ironsspellbullet.spell.SpellProjectileFactory.isAllowedInShotgun(spell)) {
                activeSelected = ActiveSpellBulletManager.NORMAL_BULLET;
            }
        }

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
            if (remaining > 0 && isSpellBullet(offhand) && matchesSpell(offhand, activeSelected)) {
                int take = Math.min(remaining, offhand.getCount());
                SpellData spellData = getSpellData(offhand);
                SpellChamberData.SpellSlotEntry entry = SpellChamberData.SpellSlotEntry.fromSpellData(spellData);
                for (int i = 0; i < take; i++) consumedEntries.add(entry);
                offhand.shrink(take);
                remaining -= take;
            }

            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining <= 0) break;
                ItemStack stack = inv.getItem(i);
                if (isSpellBullet(stack) && matchesSpell(stack, activeSelected)) {
                    int take = Math.min(remaining, stack.getCount());
                    SpellData spellData = getSpellData(stack);
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
     * Safely unloads all rounds from the gun back into the player's inventory as Iron's Arms bullets.
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
            if (entry != null && entry.hasSpell() && entry.getSpell() != null) {
                bulletStack = createSpellBullet(entry.getSpell(), entry.spellLevel(), 1);
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
        if (stack == null || stack.isEmpty()) return false;
        if (hasSpell(stack)) return false;
        return stack.is(ItemRegistry.BULLET.get()) || stack.is(IronsArtificeTags.AMMO);
    }

    public static boolean matchesSpell(ItemStack stack, String spellId) {
        SpellData data = getSpellData(stack);
        if (data != null && data.getSpell() != null) {
            if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(data.getSpell())) {
                return false;
            }
            return data.getSpell().getSpellResource().toString().equals(spellId);
        }
        return false;
    }

    /**
     * Instantly sets the firearm's chamber and magazine in Creative mode to the chosen bullet type.
     */
    public static void setCreativeChamber(Player player, ItemStack gunStack, String selectedBulletId) {
        if (player == null || gunStack == null || !(gunStack.getItem() instanceof GunItem gunItem)) return;

        int capacity = gunItem.magazineCapacity();
        if (ActiveSpellBulletManager.NORMAL_BULLET.equals(selectedBulletId) || selectedBulletId == null || selectedBulletId.isEmpty()) {
            gunStack.remove(ModDataComponents.LOADED_SPELLS.get());
            GunItem.setMagazine(gunStack, new MagazineContents(capacity));
        } else {
            AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(net.minecraft.resources.ResourceLocation.parse(selectedBulletId));
            if (spell != null && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                if (isShotgun(gunStack) && !com.ironsspellbullet.spell.SpellProjectileFactory.isAllowedInShotgun(spell)) {
                    // Incompatible with shotguns: fallback to normal bullets
                    gunStack.remove(ModDataComponents.LOADED_SPELLS.get());
                    GunItem.setMagazine(gunStack, new MagazineContents(capacity));
                    return;
                }
                int level = spell.getMaxLevel();
                ItemStack offhand = player.getOffhandItem();
                if (isSpellBullet(offhand) && matchesSpell(offhand, selectedBulletId)) {
                    SpellData sd = getSpellData(offhand);
                    if (sd != null) level = sd.getLevel();
                } else {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (isSpellBullet(invStack) && matchesSpell(invStack, selectedBulletId)) {
                            SpellData sd = getSpellData(invStack);
                            if (sd != null) {
                                level = sd.getLevel();
                                break;
                            }
                        }
                    }
                }
                SpellChamberData.SpellSlotEntry entry = new SpellChamberData.SpellSlotEntry(selectedBulletId, level);
                List<SpellChamberData.SpellSlotEntry> entries = new ArrayList<>();
                for (int i = 0; i < capacity; i++) {
                    entries.add(entry);
                }
                gunStack.set(ModDataComponents.LOADED_SPELLS.get(), new SpellChamberData(entries));
                GunItem.setMagazine(gunStack, new MagazineContents(capacity));
            }
        }
    }
}
