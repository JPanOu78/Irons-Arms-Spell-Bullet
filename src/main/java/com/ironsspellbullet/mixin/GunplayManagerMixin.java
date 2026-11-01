package com.ironsspellbullet.mixin;

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
            // If the gun chamber doesn't match what the player currently has selected in the wheel,
            // and the player has bullets of the selected type in their inventory, unload the current chamber
            // back to the player's inventory so the new bullet type can be loaded!
            if (!ReloadHelper.isChamberMatchingSelection(gunStack, active)) {
                if (ReloadHelper.countSpecificBullets(player, active) > 0) {
                    ReloadHelper.unloadGunChamber(player, gunStack);
                }
            }
        }
    }

    @Inject(
            method = "attemptFinishReload",
            at = @At(value = "INVOKE", target = "Lio/redspace/irons_artifice/item/GunplayManager;consumeBullets(Lnet/minecraft/world/entity/player/Player;I)V"),
            cancellable = true,
            remap = false
    )
    private static void irons_spell_bullet$onFinishReload(LivingEntity entity, ItemStack gunStack, int roundsToLoad, CallbackInfoReturnable<ReloadResult> cir) {
        if (entity instanceof Player player && gunStack.getItem() instanceof GunItem gunItem) {
            String active = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());
            int capacity = gunItem.magazineCapacity();
            MagazineContents magazine = GunItem.getMagazine(gunStack);
            int missing = magazine.missing(capacity);
            int available = ReloadHelper.countSpecificBullets(player, active);

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
