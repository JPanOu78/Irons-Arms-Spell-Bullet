package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.acid_orb.AcidOrb;
import io.redspace.ironsspellbooks.entity.spells.ball_lightning.BallLightning;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile;
import io.redspace.ironsspellbooks.entity.spells.guiding_bolt.GuidingBoltProjectile;
import io.redspace.ironsspellbooks.entity.spells.icicle.IcicleProjectile;
import io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.poison_arrow.PoisonArrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SpellProjectileFactory {

    public static final double BULLET_SPEED = 3.8;

    /**
     * Checks if the spell is a projectile-type spell.
     * If so, launches the actual projectile entity directly from the gun's muzzle at bullet speed.
     * Returns true if launched as projectile entity.
     */
    public static boolean tryLaunchProjectile(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, AbstractSpell spell, int spellLevel) {
        if (spell == null) return false;

        String path = spell.getSpellResource().getPath();
        float power = spell.getSpellPower(spellLevel, shooter);
        float damage = Math.max(8.0f, power * 2.8f);

        AbstractMagicProjectile projectile = null;

        if (path.contains("fireball")) {
            MagicFireball fireball = new MagicFireball(level, shooter);
            fireball.setExplosionRadius(2.0f + spellLevel * 0.4f);
            projectile = fireball;
        } else if (path.contains("icicle")) {
            projectile = new IcicleProjectile(level, shooter);
        } else if (path.contains("magic_missile")) {
            projectile = new MagicMissileProjectile(level, shooter);
        } else if (path.contains("lightning_lance")) {
            projectile = new LightningLanceProjectile(level, shooter);
        } else if (path.contains("blood_needles")) {
            projectile = new BloodNeedle(level, shooter);
        } else if (path.contains("acid_orb")) {
            projectile = new AcidOrb(level, shooter);
        } else if (path.contains("firebolt")) {
            projectile = new FireboltProjectile(level, shooter);
        } else if (path.contains("guiding_bolt")) {
            projectile = new GuidingBoltProjectile(level, shooter);
        } else if (path.contains("poison_arrow")) {
            projectile = new PoisonArrow(level, shooter);
        } else if (path.contains("magic_arrow")) {
            projectile = new MagicArrowProjectile(level, shooter);
        } else if (path.contains("comet")) {
            projectile = new Comet(level, shooter);
        } else if (path.contains("ball_lightning")) {
            projectile = new BallLightning(level, shooter);
        }

        if (projectile != null) {
            projectile.setPos(origin.x, origin.y, origin.z);
            projectile.setDamage(damage);
            projectile.shoot(direction);
            // Accelerate velocity to match ballistic bullet speed
            projectile.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(projectile);
            return true;
        }

        return false;
    }
}

