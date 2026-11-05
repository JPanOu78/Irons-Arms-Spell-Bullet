package com.ironsspellbullet.spell;

import com.ironsspellbullet.event.ActiveSpellEntityManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.WitherSkullProjectile;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public class SpellProjectileFactory {

    public static final double BULLET_SPEED = 2.0;

    /**
     * Checks if the given spell is a direct projectile or beam spell in Iron's Spells.
     */
    public static boolean isProjectileSpell(AbstractSpell spell) {
        if (spell == null || SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) return false;
        String path = spell.getSpellResource().getPath().toLowerCase();
        if (InstantBeamHandler.isBeamSpell(path)) {
            return true;
        }
        return path.contains("fireball") ||
                path.contains("magma") ||
                path.contains("firebolt") ||
                path.contains("fire_arrow") ||
                path.contains("blaze_storm") ||
                path.contains("flaming_barrage") ||
                path.contains("icicle") ||
                path.contains("snowball") ||
                path.contains("lightning_lance") ||
                path.contains("ball_lightning") ||
                path.contains("thunderstep") ||
                path.contains("thunder_step") ||
                path.contains("magic_missile") ||
                path.contains("magic_arrow") ||
                path.contains("comet") ||
                path.contains("arcane_shackle") ||
                path.contains("blood_needles") ||
                path.contains("blood_slash") ||
                path.contains("wither_skull") ||
                path.contains("acid_orb") ||
                path.contains("poison_arrow") ||
                path.contains("guiding_bolt") ||
                path.contains("lob_creeper");
    }

    /**
     * Checks if a spell is a heavy/explosive/ultimate spell that should fire
     * as a single concentrated Slug Core in shotguns rather than spawning 7-8 copies.
     */
    public static boolean isHeavySpell(String path) {
        return path.contains("magma") ||
                path.contains("black_hole") ||
                path.contains("comet");
    }

    // =========================================================================
    // AUTHENTIC IRON'S SPELLS REFLECTION & DAMAGE PIPELINE HELPERS
    // =========================================================================

    public static Float invokeSpellFloat(AbstractSpell spell, String methodName, Class<?>[] paramTypes, Object... args) {
        if (spell == null) return null;
        Class<?> curr = spell.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Method m = curr.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                Object res = m.invoke(spell, args);
                if (res instanceof Number num) {
                    return num.floatValue();
                }
            } catch (NoSuchMethodException ignored) {
                curr = curr.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }

    public static Integer invokeSpellInt(AbstractSpell spell, String methodName, Class<?>[] paramTypes, Object... args) {
        if (spell == null) return null;
        Class<?> curr = spell.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Method m = curr.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                Object res = m.invoke(spell, args);
                if (res instanceof Number num) {
                    return num.intValue();
                }
            } catch (NoSuchMethodException ignored) {
                curr = curr.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }

    /**
     * Computes authentic spell damage directly from Iron's Spells' own methods:
     * 1. spell.getDamage(int, LivingEntity)
     * 2. spell.getArrowDamage(int, LivingEntity) (PoisonArrowSpell)
     * 3. spell.getTickDamage(int, LivingEntity) (RayOfSiphoningSpell)
     * 4. spell.getSpellPower(spellLevel, shooter) (BloodSlashSpell, LightningLanceSpell, LightningBoltSpell, MagicArrowSpell, WispSpell)
     */
    public static float computeAuthenticSpellDamage(AbstractSpell spell, int spellLevel, LivingEntity shooter) {
        if (spell == null) return 0.0f;
        Float val = invokeSpellFloat(spell, "getDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getArrowDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getTickDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        return spell.getSpellPower(spellLevel, shooter);
    }

    /**
     * Computes authentic AOE / secondary splash damage directly from Iron's Spells' own methods.
     */
    public static float computeAuthenticAoeDamage(AbstractSpell spell, int spellLevel, LivingEntity shooter, float fallback) {
        if (spell == null) return fallback;
        Float val = invokeSpellFloat(spell, "getAoeDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getAOEDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        return fallback;
    }

    /**
     * Computes authentic spell radius directly from Iron's Spells' own methods.
     */
    public static float computeAuthenticRadius(AbstractSpell spell, int spellLevel, LivingEntity shooter, float fallback) {
        if (spell == null) return fallback;
        Float val = invokeSpellFloat(spell, "getRadius", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getRadius", new Class<?>[]{LivingEntity.class}, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getSwirlRadius", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getLashRadius", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        return fallback;
    }

    /**
     * Computes authentic spell duration (in ticks) directly from Iron's Spells' own methods.
     */
    public static int computeAuthenticDuration(AbstractSpell spell, int spellLevel, LivingEntity shooter, int fallback) {
        if (spell == null) return fallback;
        String[] methodNames = {
                "getDuration",
                "getDurationTicks",
                "getEffectDuration",
                "getRendDuration",
                "getChainDuration",
                "getSwirlDurationTicks"
        };
        for (String name : methodNames) {
            Integer val = invokeSpellInt(spell, name, new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (val != null) return val;
        }
        return fallback;
    }

    /**
     * Computes authentic healing amount directly from Iron's Spells' own methods.
     */
    public static float computeAuthenticHealing(AbstractSpell spell, int spellLevel, LivingEntity shooter) {
        if (spell == null) return 0.0f;
        Float val = invokeSpellFloat(spell, "getHealing", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        return spell.getSpellPower(spellLevel, shooter);
    }

    /**
     * Computes authentic effect amplifier directly from Iron's Spells' own methods.
     */
    public static int computeAuthenticAmplifier(AbstractSpell spell, int spellLevel, LivingEntity shooter, int fallback) {
        if (spell == null) return fallback;
        String[] twoArgMethods = {
                "getAmplifier",
                "getAmplifierForLevel",
                "getRendAmplifier",
                "getSlownessAmplifier"
        };
        for (String name : twoArgMethods) {
            Integer val = invokeSpellInt(spell, name, new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (val != null) return val;
        }
        Integer val = invokeSpellInt(spell, "getAmplifierForLevel", new Class<?>[]{int.class}, spellLevel);
        if (val != null) return val;

        return fallback;
    }

    /**
     * Computes authentic projectile/summon/ring count directly from Iron's Spells' own methods.
     */
    public static int computeAuthenticCount(AbstractSpell spell, int spellLevel, LivingEntity shooter, int fallback) {
        if (spell == null) return fallback;
        String[] twoArgMethods = {
                "getCount",
                "getSummonCount",
                "getRings",
                "getRecastCount"
        };
        for (String name : twoArgMethods) {
            Integer val = invokeSpellInt(spell, name, new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (val != null) return val;
        }
        String[] oneArgMethods = {
                "getCount",
                "getSummonCount",
                "getRings"
        };
        for (String name : oneArgMethods) {
            Integer val = invokeSpellInt(spell, name, new Class<?>[]{int.class}, spellLevel);
            if (val != null) return val;
        }
        return fallback;
    }

    /**
     * Computes authentic beam/effect range directly from Iron's Spells' own methods.
     */
    public static float computeAuthenticRange(AbstractSpell spell, int spellLevel, LivingEntity shooter, float fallback) {
        if (spell == null) return fallback;
        Float val = invokeSpellFloat(spell, "getRange", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        if (val != null) return val;

        val = invokeSpellFloat(spell, "getRange", new Class<?>[]{int.class}, spellLevel);
        if (val != null) return val;

        return fallback;
    }

    /**
     * Applies damage through Iron's Spells' native DamageSources.applyDamage pipeline using spell.getDamageSource(directEntity, shooter).
     * Automatically triggers SpellDamageEvent, school resistance attributes, fireTicks, freezeTicks, lifestealPercent, and iFrames!
     */
    public static boolean applyAuthenticSpellDamage(Entity target, float damage, AbstractSpell spell, Entity directEntity, LivingEntity shooter) {
        if (target == null || spell == null) return false;
        Entity sourceEntity = (directEntity != null) ? directEntity : shooter;
        DamageSource ds = (sourceEntity != null)
                ? spell.getDamageSource(sourceEntity, shooter)
                : spell.getDamageSource(shooter);
        return DamageSources.applyDamage(target, damage, ds);
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
        if (spell == null || SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) return false;

        String path = spell.getSpellResource().getPath().toLowerCase();
        float baseDamage = computeAuthenticSpellDamage(spell, spellLevel, shooter);

        int count = Math.max(1, projectileCount);
        float damagePerPellet = (count > 1)
                ? baseDamage / (float) Math.sqrt(count)
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

            boolean isFirstPellet = (i == 0);
            boolean launched = launchSingle(level, shooter, origin, pelletDir, spell, path, spellLevel, damagePerPellet, count, isFirstPellet);
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
            int totalPellets,
            boolean isFirstPellet
    ) {
        // 1. Instant Beam / Ray / Cone Spells (Fired directly from muzzle using Iron's Spells formulas)
        if (InstantBeamHandler.isBeamSpell(path)) {
            return InstantBeamHandler.fireBeam(level, shooter, origin, direction, spell, spellLevel, damage, totalPellets > 1 ? 0.05f : 0.0f);
        }

        // 2. Iron's Spells Magic Projectiles configured with authentic spell parameters
        AbstractMagicProjectile projectile = null;

        if (path.contains("fireball")) {
            MagicFireball fireball = new MagicFireball(level, shooter);
            float radius = computeAuthenticRadius(spell, spellLevel, shooter, (int) (2.0f + spell.getSpellPower(spellLevel, shooter)));
            fireball.setExplosionRadius(radius);
            projectile = fireball;
        } else if (path.contains("magma")) {
            FireBomb bomb = new FireBomb(level, shooter);
            float radius = computeAuthenticRadius(spell, spellLevel, shooter, 3.0f);
            float aoeDamage = computeAuthenticAoeDamage(spell, spellLevel, shooter, damage * 0.2f);
            bomb.setExplosionRadius(radius);
            bomb.setAoeDamage(aoeDamage);
            projectile = bomb;
        } else if (path.contains("firebolt")) {
            projectile = new FireboltProjectile(level, shooter);
        } else if (path.contains("fire_arrow")) {
            FireArrowProjectile fireArrow = new FireArrowProjectile(level, shooter);
            float radius = computeAuthenticRadius(spell, spellLevel, shooter, 3.0f);
            fireArrow.setExplosionRadius(radius);
            projectile = fireArrow;
        } else if (path.contains("blaze_storm")) {
            projectile = new SmallMagicFireball(level, shooter);
            if (isFirstPellet && shooter instanceof net.minecraft.server.level.ServerPlayer) {
                ActiveSpellEntityManager.queueBlazeStormBurst(level, shooter, origin, direction, damage, spellLevel, 4);
            }
        } else if (path.contains("flaming_barrage")) {
            projectile = new SmallMagicFireball(level, shooter);
            if (isFirstPellet && shooter instanceof net.minecraft.server.level.ServerPlayer) {
                int totalBarrage = computeAuthenticCount(spell, spellLevel, shooter, 5);
                ActiveSpellEntityManager.queueFlamingBarrageBurst(level, shooter, origin, direction, damage, spellLevel, Math.max(1, totalBarrage - 1));
            }
        } else if (path.contains("icicle")) {
            projectile = new IcicleProjectile(level, shooter);
        } else if (path.contains("snowball")) {
            Snowball snowball = new Snowball(level, shooter);
            float radius = computeAuthenticRadius(spell, spellLevel, shooter, 3.0f);
            int frostbiteDuration = computeAuthenticDuration(spell, spellLevel, shooter, 200);
            snowball.setExplosionRadius(radius);
            // In SnowballSpell.onCast, snowball.setDamage(getDuration) stores the frostbite duration!
            snowball.setPos(origin.x, origin.y, origin.z);
            snowball.setDamage((float) frostbiteDuration);
            snowball.setNoGravity(true);
            snowball.shoot(direction);
            snowball.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(snowball);
            return true;
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
        } else if (path.contains("comet")) {
            Comet comet = new Comet(level, shooter);
            comet.setExplosionRadius(2.0f);
            projectile = comet;
        } else if (path.contains("arcane_shackle")) {
            ArcaneShackleProjectile shackle = new ArcaneShackleProjectile(level, shooter);
            Float chainHealth = invokeSpellFloat(spell, "getChainHealth", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            int chainLifetime = computeAuthenticDuration(spell, spellLevel, shooter, 200);
            float lashRadius = computeAuthenticRadius(spell, spellLevel, shooter, 4.0f);
            shackle.setChainHealth(chainHealth != null ? chainHealth : 20.0f);
            shackle.setChainLifetime(chainLifetime);
            shackle.setLashRadius(lashRadius);
            shackle.setRestraintStrength(0.015f);
            projectile = shackle;
        } else if (path.contains("blood_needles")) {
            int count = computeAuthenticCount(spell, spellLevel, shooter, 5);
            BloodNeedle needle = new BloodNeedle(level, shooter);
            needle.setZRot(0.0f);
            projectile = needle;
            if (isFirstPellet && shooter instanceof net.minecraft.server.level.ServerPlayer) {
                ActiveSpellEntityManager.queueBloodNeedlesBurst(level, shooter, origin, direction, damage, spellLevel, Math.max(1, count - 1));
            }
        } else if (path.contains("blood_slash")) {
            projectile = new BloodSlashProjectile(level, shooter);
        } else if (path.contains("wither_skull")) {
            float speed = (float) (0.8 + spellLevel * 0.15);
            WitherSkullProjectile skull = new WitherSkullProjectile(shooter, level, speed, damage);
            skull.setPos(origin.x, origin.y, origin.z);
            skull.setNoGravity(true);
            skull.shoot(direction);
            skull.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(skull);
            return true;
        } else if (path.contains("acid_orb")) {
            AcidOrb orb = new AcidOrb(level, shooter);
            float radius = computeAuthenticRadius(spell, spellLevel, shooter, 3.0f);
            int rendLevel = computeAuthenticAmplifier(spell, spellLevel, shooter, spellLevel);
            int rendDuration = computeAuthenticDuration(spell, spellLevel, shooter, 300);
            orb.setExplosionRadius(radius);
            orb.setRendLevel(rendLevel);
            orb.setRendDuration(rendDuration);
            projectile = orb;
        } else if (path.contains("poison_arrow")) {
            PoisonArrow arrow = new PoisonArrow(level, shooter);
            float aoeDamage = computeAuthenticAoeDamage(spell, spellLevel, shooter, damage * 0.25f);
            arrow.setAoeDamage(aoeDamage);
            projectile = arrow;
        } else if (path.contains("guiding_bolt")) {
            projectile = new GuidingBoltProjectile(level, shooter);
        } else if (path.contains("lob_creeper")) {
            float speed = (float) (0.85 + spellLevel * 0.05);
            CreeperHeadProjectile creeper = new CreeperHeadProjectile(shooter, level, speed, damage);
            creeper.setPos(origin.x, origin.y, origin.z);
            creeper.setNoGravity(true);
            creeper.shoot(direction);
            creeper.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(creeper);
            return true;
        }

        if (projectile != null) {
            projectile.setPos(origin.x, origin.y, origin.z);
            projectile.setDamage(damage);
            // Disable gravity so projectiles fired from firearms fly straight along barrel trajectory
            projectile.setNoGravity(true);
            projectile.shoot(direction);
            projectile.setDeltaMovement(direction.normalize().scale(BULLET_SPEED));
            level.addFreshEntity(projectile);
            return true;
        }

        return false;
    }
}
