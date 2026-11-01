package com.ironsspellbullet.mixin;

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

    @Inject(
            method = "attemptFinishReload",
            at = @At(value = "INVOKE", target = "Lio/redspace/irons_artifice/item/GunplayManager;consumeBullets(Lnet/minecraft/world/entity/player/Player;I)V"),
            cancellable = true,
            remap = false
    )
    private static void irons_spell_bullet$onFinishReload(LivingEntity entity, ItemStack gunStack, int roundsToLoad, CallbackInfoReturnable<ReloadResult> cir) {
        if (entity instanceof Player player && gunStack.getItem() instanceof GunItem gunItem) {
            int capacity = gunItem.magazineCapacity();
            MagazineContents magazine = GunItem.getMagazine(gunStack);
            int missing = magazine.missing(capacity);
            int bulletsToLoad = roundsToLoad > 0 ? Math.min(missing, roundsToLoad) : missing;

            ReloadHelper.smartConsumeAndRecord(player, gunStack, bulletsToLoad);

            GunItem.setMagazine(gunStack, magazine.with(magazine.count() + bulletsToLoad));
            cir.setReturnValue(ReloadResult.FINISHED_RELOAD);
        }
    }
}
