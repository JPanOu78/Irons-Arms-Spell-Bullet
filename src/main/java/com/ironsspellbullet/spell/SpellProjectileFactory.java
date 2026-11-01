package com.ironsspellbullet.spell;

import com.ironsspellbullet.compat.GTBCSpellHelper;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.ArrowVolleyEntity;
import io.redspace.ironsspellbooks.entity.spells.acid_orb.AcidOrb;
import io.redspace.ironsspellbooks.entity.spells.ball_lightning.BallLightning;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.creeper_head.CreeperHeadProjectile;
import io.redspace.ironsspellbooks.entity.spells.ender_chain.ArcaneShackleProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball;
import io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile;
import io.redspace.ironsspellbooks.entity.spells.guiding_bolt.GuidingBoltProjectile;
import io.redspace.ironsspellbooks.entity.spells.icicle.IcicleProjectile;
import io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireBomb;
import io.redspace.ironsspellbooks.entity.spells.poison_arrow.PoisonArrow;
import io.redspace.ironsspellbooks.entity.spells.snowball.Snowball;
import io.redspace.ironsspellbooks.entity.spells.thunderstep.ThunderstepProjectile;
import io.redspace.ironsspellbooks.entity.spells.WitherSkullProjectile;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SpellProjectileFactory {

    public static final double BULLET_SPEED = 3.8;

    /**
     * Checks if the given spell is a projectile-type spell (Iron's Spells + GTBC Spells).
     */
    public static boolean isProjectileSpell(AbstractSpell spell) {
        if (spell == null) return false;
        String path = spell.getSpellResource().getPath().toLowerCase();
        return path.contains("fireball") ||
                path.contains("magma") ||
                path.contains("firebolt") ||
                path.contains("fire_arrow") ||
                path.contains("blaze_storm") ||
                path.contains("icicle") ||
                path.contains("snowball") ||
                path.contains("lightning_lance") ||
                path.contains("ball_lightning") ||
                path.contains("thunderstep") ||
                path.contains("thunder_step") ||
                path.contains("magic_missile") ||
                path.contains("magic_arrow") ||
                path.contains("comet") ||
                path.contains("starfall") ||
                path.contains("arcane_shackle") ||
                path.contains("blood_needles") ||
                path.contains("acupuncture") ||
                path.contains("blood_slash") ||
                path.contains("shadow_slash") ||
                path.contains("wither_skull") ||
                path.contains("acid_orb") ||
                path.contains("poison_arrow") ||
                path.contains("guiding_bolt") ||
                path.contains("creeper") ||
                path.contains("arrow_volley") ||
                // GTBC spells
                path.contains("psychic_bolt") ||
                path.contains("dripstone_bolt") ||
                path.contains("eroding_boulder") ||
                path.contains("chunker");
    }

    /**
     * Launches the actual spell projectile(s) directly from the gun's muzzle.
     * Fully supports shotgun firing: when projectileCount > 1, fires multiple scattered projectiles!
     */
    public static boolean tryLaunchProjectile(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            int projectileCount,
            float spread
    ) {
        if (spell == null) return false;

        String path = spell.getSpellResource().getPath().toLowerCase();
        float power = spell.getSpellPower(spellLevel, shooter);
        float baseDamage = Math.max(8.0f, power * 2.8f);

        int count = Math.max(1, projectileCount);
        float damagePerPellet = (count > 1)
                ? Math.max(3.0f, baseDamage / (float) Math.sqrt(count))
                : baseDamage;

        boolean anyLaunched = false;

        for (int i = 0; i < count; i++) {
            Vec3 pelletDir = direction.normalize();
            // Apply shotgun spread inaccuracy
            if (count > 1 && spread > 0.0f) {
                pelletDir = pelletDir.add(
                        level.random.triangle(0.0, 0.0172275 * (double) spread),
                        level.random.triangle(0.0, 0.0172275 * (double) spread),
                        level.random.triangle(0.0, 0.0172275 * (double) spread)
                ).normalize();
            }

            boolean launched = launchSingle(level, shooter, origin, pelletDir, spell, path, spellLevel, damagePerPellet, count);
            if (launched) {
                anyLaunched = true;
            }
        }

        return anyLaunched;
    }

    private static boolean launchSingle(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            String path,
            int spellLevel,
            float damage,
            int totalPellets
    ) {
        // 1. Check GTBC Projectiles first
        if (GTBCSpellHelper.isGTBCProjectile(path)) {
            boolean gtbcLaunched = GTBCSpellHelper.tryLaunchGTBCProjectile(level, shooter, origin, direction, path, damage, BULLET_SPEED);
            if (gtbcLaunched) return true;
        }

        // 2. Iron's Spells Magic Projectiles
        AbstractMagicProjectile projectile = null;

        if (path.contains("fireball")) {
            MagicFireball fireball = new MagicFireball(level, shooter);
            fireball.setExplosionRadius(totalPellets > 1 ? 1.5f + spellLevel * 0.2f : 2.0f + spellLevel * 0.4f);
            projectile = fireball;
        } else if (path.contains("magma")) {
            FireBomb bomb = new FireBomb(level, shooter);
            bomb.setExplosionRadius(totalPellets > 1 ? 1.8f + spellLevel * 0.25f : 2.5f + spellLevel * 0.4f);
            bomb.setAoeDamage(damage * 0.75f);
            projectile = bomb;
        } else if (path.contains("firebolt")) {
            projectile = new FireboltProjectile(level, shooter);
        } else if (path.contains("fire_arrow")) {
            projectile = new FireArrowProjectile(level, shooter);
        } else if (path.contains("blaze_storm")) {
            projectile = new SmallMagicFireball(level, shooter);
        } else if (path.contains("icicle")) {
            projectile = new IcicleProjectile(level, shooter);
        } else if (path.contains("snowball")) {
            projectile = new Snowball(level, shooter);
        } else if (path.contains("lightning_lance")) {
            projectile = new LightningLanceProjectile(level, shooter);
        } else if (path.contains("ball_lightning")) {
            projectile = new BallLightning(level, shooter);
        } else if (path.contains("thunderstep") || path.contains("thunder_step")) {
            projectile = new ThunderstepProjectile(level, shooter);
        } else if (path.contains("magic_missile")) {
            projectile = new MagicMissileProjectile(level, shooter);
        } else if (path.contains("magic_arrow")) {
            projectile = new MagicArrowProjectile(level, shooter);
        } else if (path.contains("comet") || path.contains("starfall")) {
            projectile = new Comet(level, shooter);
        } else if (path.contains("arcane_shackle")) {
            projectile = new ArcaneShackleProjectile(level, shooter);
        } else if (path.contains("blood_needles") || path.contains("acupuncture")) {
            projectile = new BloodNeedle(level, shooter);
        } else if (path.contains("blood_slash") || path.contains("shadow_slash")) {
            projectile = new BloodSlashProjectile(level, shooter);
        } else if (path.contains("wither_skull")) {
            projectile = new WitherSkullProjectile(level, shooter);
        } else if (path.contains("acid_orb")) {
            projectile = new AcidOrb(level, shooter);
        } else if (path.contains("poison_arrow")) {
            projectile = new PoisonArrow(level, shooter);
        } else if (path.contains("guiding_bolt")) {
            projectile = new GuidingBoltProjectile(level, shooter);
        } else if (path.contains("creeper")) {
            projectile = new CreeperHeadProjectile(level, shooter);
        } else if (path.contains("arrow_volley")) {
            ArrowVolleyEntity volley = new ArrowVolleyEntity(EntityRegistry.ARROW_VOLLEY_ENTITY.get(), level);
            volley.setOwner(shooter);
            volley.setRows(Math.max(1, 2 + spellLevel / 2));
            volley.setArrowsPerRow(Math.max(2, 4 + spellLevel));
            projectile = volley;
        }

        if (projectile != null) {
            projectile.setPos(origin.x, origin.y, origin.z);
            projectile.setDamage(damage);
            projectile.shoot(direction);
            projectile.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(projectile);
            return true;
        }

        return false;
    }
}
