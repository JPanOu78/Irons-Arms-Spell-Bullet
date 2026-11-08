package com.ironsspellbullet.mixin;

import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.event.GunplayEvents;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import io.redspace.irons_artifice.gun.ShotProfile;
import io.redspace.irons_artifice.entity.Bullet;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bullet.class)
public abstract class BulletMixin extends Projectile {

    protected BulletMixin() {
        super(null, null);
    }

    @Shadow
    public abstract ShotProfile getProfile();

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void irons_spell_bullet$allowHittingSummonsAndAllies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || !entity.isAlive() || entity.isInvulnerable()) {
            return;
        }
        if (entity == this.getOwner()) {
            return;
        }

        Bullet bullet = (Bullet) (Object) this;
        ShotProfile profile = bullet.getProfile();
        if (profile == null) return;

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(GunplayEvents.SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            AbstractSpell spell = entry.getSpell();
            if (spell == null) return;
            String path = spell.getSpellResource().getPath().toLowerCase();

            LivingEntity shooter = (this.getOwner() instanceof LivingEntity livingOwner) ? livingOwner : null;

            // 1. Allow bullets to hit summons when using Sacrifice
            if (path.contains("sacrifice")) {
                if (entity instanceof LivingEntity living && SpellBulletEffectHandler.isSacrificeableSummon(living, shooter)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // 2. Allow bullets to hit allies and players when using healing, cleansing, or support spells
            if (path.contains("heal") || path.contains("cleanse") || path.contains("fortify") || path.contains("bless")) {
                if (entity instanceof LivingEntity) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}

