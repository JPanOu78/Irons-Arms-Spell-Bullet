package com.ironsspellbullet.mixin;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.event.ActiveSpellBulletManager;
import com.ironsspellbullet.event.ReloadHelper;
import io.redspace.irons_artifice.data.ReloadResult;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.irons_artifice.item.GunplayManager;
import io.redspace.irons_artifice.item.MagazineContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunplayManager.class)
public abstract class GunplayManagerMixin {

    @Inject(method = "countBullets", at = @At("HEAD"), cancellable = true, remap = false)
    private static void irons_spell_bullet$countBullets(Player player, CallbackInfoReturnable<Integer> cir) {
        if (player != null) {
            if (player.hasInfiniteMaterials()) {
                cir.setReturnValue(999);
                return;
            }
            String active = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());
            if (active != null && !active.isEmpty()) {
                cir.setReturnValue(ReloadHelper.countSpecificBullets(player, active));
            }
        }
    }

    @Inject(method = "attemptStartReload", at = @At("HEAD"), remap = false)
    private static void irons_spell_bullet$onStartReload(LivingEntity entity, ItemStack gunStack, CallbackInfoReturnable<ReloadResult> cir) {
        if (entity instanceof Player player && gunStack.getItem() instanceof GunItem) {
            String active = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());
            if (player.hasInfiniteMaterials()) {
                // Creative Mode: If the gun chamber doesn't match selection, reset chamber and magazine so reload can begin
                if (!ReloadHelper.isChamberMatchingSelection(gunStack, active)) {
                    gunStack.remove(ModDataComponents.LOADED_SPELLS.get());
                    GunItem.setMagazine(gunStack, MagazineContents.EMPTY);
                }
            } else {
                // Survival Mode:
                if (!ReloadHelper.isChamberMatchingSelection(gunStack, active)) {
                    if (ReloadHelper.countSpecificBullets(player, active) > 0) {
                        ReloadHelper.unloadGunChamber(player, gunStack);
                    }
                }
            }
        }
    }

    @Inject(method = "attemptFinishReload", at = @At("HEAD"), cancellable = true, remap = false)
    private static void irons_spell_bullet$onFinishReload(LivingEntity entity, ItemStack gunStack, int roundsToLoad, CallbackInfoReturnable<ReloadResult> cir) {
        if (entity instanceof Player player && gunStack.getItem() instanceof GunItem gunItem) {
            int capacity = gunItem.magazineCapacity();
            MagazineContents magazine = GunItem.getMagazine(gunStack);
            int missing = magazine.missing(capacity);
            if (missing <= 0) {
                cir.setReturnValue(ReloadResult.ALREADY_FULL);
                return;
            }

            String active = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());

            if (player.hasInfiniteMaterials()) {
                // Creative mode: full reload with selected spell
                ReloadHelper.setCreativeChamber(player, gunStack, active);
                cir.setReturnValue(ReloadResult.FINISHED_RELOAD);
                return;
            }

            // Survival mode:
            int available = ReloadHelper.countSpecificBullets(player, active);
            if (available <= 0) {
                cir.setReturnValue(ReloadResult.NO_AMMO);
                return;
            }

            int bulletsToLoad = Math.min(missing, available);
            if (roundsToLoad > 0) {
                bulletsToLoad = Math.min(bulletsToLoad, roundsToLoad);
            }

            if (bulletsToLoad > 0) {
                ReloadHelper.smartConsumeAndRecord(player, gunStack, bulletsToLoad);
                GunItem.setMagazine(gunStack, magazine.with(magazine.count() + bulletsToLoad));
            }

            cir.setReturnValue(ReloadResult.FINISHED_RELOAD);
        }
    }
}
