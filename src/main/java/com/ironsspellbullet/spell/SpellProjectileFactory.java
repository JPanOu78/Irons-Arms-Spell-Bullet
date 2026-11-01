package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.acid_orb.AcidOrb;
import io.redspace.ironsspellbooks.entity.spells.ball_lightning.BallLightning;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile;
import io.redspace.ironsspellbooks.entity.spells.guiding_bolt.GuidingBoltProjectile;
import io.redspace.ironsspellbooks.entity.spells.icicle.IcicleProjectile;
import io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireBomb;
import io.redspace.ironsspellbooks.entity.spells.poison_arrow.PoisonArrow;
import io.redspace.ironsspellbooks.entity.spells.snowball.Snowball;
import io.redspace.ironsspellbooks.entity.spells.WitherSkullProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SpellProjectileFactory {

    public static final double BULLET_SPEED = 3.8;

    /**
     * Checks if the given spell is a projectile-type spell.
     */
    public static boolean isProjectileSpell(AbstractSpell spell) {
        if (spell == null) return false;
        String path = spell.getSpellResource().getPath();
        return path.contains("fireball") ||
                path.contains("magma") ||
                path.contains("icicle") ||
                path.contains("magic_missile") ||
                path.contains("lightning_lance") ||
                path.contains("blood_needles") ||
                path.contains("acid_orb") ||
                path.contains("firebolt") ||
                path.contains("guiding_bolt") ||
                path.contains("poison_arrow") ||
                path.contains("magic_arrow") ||
                path.contains("comet") ||
                path.contains("ball_lightning") ||
                path.contains("blood_slash") ||
                path.contains("wither_skull") ||
                path.contains("fire_arrow") ||
                path.contains("snowball");
    }

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
        } else if (path.contains("magma")) {
            FireBomb bomb = new FireBomb(level, shooter);
            bomb.setExplosionRadius(2.5f + spellLevel * 0.4f);
            bomb.setAoeDamage(damage * 0.75f);
            projectile = bomb;
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
        } else if (path.contains("blood_slash")) {
            projectile = new BloodSlashProjectile(level, shooter);
        } else if (path.contains("wither_skull")) {
            projectile = new WitherSkullProjectile(level, shooter);
        } else if (path.contains("fire_arrow")) {
            projectile = new FireArrowProjectile(level, shooter);
        } else if (path.contains("snowball")) {
            projectile = new Snowball(level, shooter);
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
