package com.ironsspellbullet.compat.addon;

import com.ironsspellbullet.event.ActiveSpellEntityManager;
import com.ironsspellbullet.spell.InstantBeamHandler;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import com.ironsspellbullet.spell.SpellProjectileFactory;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.CameraShakeData;
import io.redspace.ironsspellbooks.api.util.CameraShakeManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import org.joml.Vector3f;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Universal Addon Compatibility Handler for GTBC's Geomancy Plus and GTBC's
 * Spellbooks.
 * STRICT CONDITION: No assets, models, or sounds are copied into this mod.
 * All resources, entity types, sound events, and particles are referenced
 * directly through paths.
 */
public class AddonSpellBulletHandler {

    public static final String MOD_GEOMANCY = "gtbcs_geomancy_plus";
    public static final String MOD_SPELLBOOKS = "gametechbcs_spellbooks";

    /**
     * Authentic Solar Beam duration in ticks (matches original
     * SolarBeamSpell.onCast).
     */
    public static final int c_SolarBeamDuration = 55;

    /**
     * Solar Beam Variant toggle:
     * Mode A (false): Channels from player muzzle, tracks crosshair in real-time,
     * locks hotbar until finished.
     * Mode B (true): Creates a stationary magic beam at firing location. Charges up
     * for 20 ticks (1s) with authentic
     * sun orb particles, then fires forward along initial aim. Does NOT follow
     * player movement,
     * allowing free player movement, weapon swapping, and reloading.
     */
    public static final boolean USE_SOLAR_BEAM_VARIANT_B = true;

    /**
     * Spawns Solar Beam with support for both Variant A and Variant B:
     * - Variant A: Attached to player (setHasPlayer=true, tickCount=20 for instant,
     * locks aim to crosshair).
     * - Variant B: Placed in world (setHasPlayer=false, authentic 20-tick
     * charge-up, fixed direction, free movement).
     */
    public static Entity spawnAuthenticSolarBeam(ServerLevel level, LivingEntity caster, Vec3 origin, Vec3 direction,
            float damage) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_GEOMANCY, "extended_solar_beam");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        try {
            Class<?> beamClass = Class
                    .forName("com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedSolarBeamEntity");
            Constructor<?> ctor = beamClass.getConstructor(EntityType.class, Level.class, LivingEntity.class,
                    double.class, double.class, double.class, float.class, float.class, int.class);

            // Compute exact forward direction angles in radians matching Mowzie's
            // coordinate convention:
            // yaw (rad) = (yHeadRot + 90) * PI / 180
            // pitch (rad) = -xRot * PI / 180
            float yawRad = (float) ((caster.yHeadRot + 90.0f) * Math.PI / 180.0);
            float pitchRad = (float) (-caster.getXRot() * Math.PI / 180.0);

            // Spawn at exact muzzle origin if provided, otherwise eye offset
            Vec3 spawnPos;
            if (origin != null) {
                spawnPos = origin;
            } else {
                Vec3 look = caster.getLookAngle().normalize().scale(1.0);
                spawnPos = new Vec3(caster.getX() + look.x, caster.getY() + 1.2 + look.y, caster.getZ() + look.z);
            }

            Entity beam = (Entity) ctor.newInstance(type, level, caster,
                    spawnPos.x, spawnPos.y, spawnPos.z, yawRad, pitchRad, c_SolarBeamDuration);

            // Per-tick damage scaling:
            // ExtendedSolarBeamEntity hurts targets EVERY tick (20 ticks/sec) via
            // SpellDamageSource(iFrames=0).
            float perTickDamage = Math.max(4.5f, damage);
            invokeMethod(beam, "setDamageMob", new Class<?>[] { float.class }, perTickDamage);
            invokeMethod(beam, "setDamageFire", new Class<?>[] { float.class }, 2.0f);

            // Ensure yaw and pitch synched entity data are explicitly set
            invokeMethod(beam, "setYaw", new Class<?>[] { float.class }, yawRad);
            invokeMethod(beam, "setPitch", new Class<?>[] { float.class }, pitchRad);

            if (USE_SOLAR_BEAM_VARIANT_B) {
                // =============================================================
                // VARIANT B: Stationary Placed Magic Beam (with Charge-up)
                // =============================================================
                // DO NOT sync caster ID to client (pass -1) so client never binds this.caster
                // to player!
                // This completely prevents the client from tracking the player crosshair/head
                // rotation.
                invokeMethod(beam, "setCasterID", new Class<?>[] { int.class }, -1);
                invokeMethod(beam, "setHasPlayer", new Class<?>[] { boolean.class }, false);

                // Authentic 20-tick (1.0s) charge-up windup with gathering sun energy
                // particles!
                beam.tickCount = 0;
            } else {
                // =============================================================
                // VARIANT A: Instant Channeling Crosshair-Locked Beam
                // =============================================================
                invokeMethod(beam, "setCasterID", new Class<?>[] { int.class }, caster.getId());
                invokeMethod(beam, "setHasPlayer", new Class<?>[] { boolean.class }, true);
                beam.tickCount = 20; // Instant firing without delay
            }

            // Immediately invoke calculateEndPos() so endPosX/Y/Z are calculated along the
            // forward direction
            try {
                Method calcMethod = beam.getClass().getDeclaredMethod("calculateEndPos");
                calcMethod.setAccessible(true);
                calcMethod.invoke(beam);
            } catch (Throwable ignored) {
            }

            level.addFreshEntity(beam);
            return beam;
        } catch (Exception e) {
            com.ironsspellbullet.IronsArmsSpellBulletMod.LOGGER.error("Failed to spawn authentic Solar Beam", e);
            return null;
        }
    }

    public static Entity spawnAuthenticSolarBeam(ServerLevel level, LivingEntity caster, float damage) {
        return spawnAuthenticSolarBeam(level, caster, null, null, damage);
    }

    /**
     * Spawns an authentic ExtendedSunstrikeEntity directly matching
     * SolarStormEffect.spawnSunstrike:
     * new ExtendedSunstrikeEntity(type, level, caster, x, y, z)
     * + setDamageFire(2.0f) + setDamageMob(damage).
     */
    public static Entity spawnAuthenticSunstrike(ServerLevel level, LivingEntity caster, int x, int y, int z,
            float damage) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_GEOMANCY, "extended_sunstrike");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        try {
            Class<?> strikeClass = Class
                    .forName("com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedSunstrikeEntity");
            Constructor<?> ctor = strikeClass.getConstructor(EntityType.class, Level.class, LivingEntity.class,
                    int.class, int.class, int.class);
            Entity strike = (Entity) ctor.newInstance(type, level, caster, x, y, z);
            invokeMethod(strike, "setDamageFire", new Class<?>[] { float.class }, 2.0f);
            invokeMethod(strike, "setDamageMob", new Class<?>[] { float.class }, damage);
            level.addFreshEntity(strike);
            return strike;
        } catch (Exception e) {
            com.ironsspellbullet.IronsArmsSpellBulletMod.LOGGER.error("Failed to spawn authentic Sunstrike", e);
            return null;
        }
    }

    // =========================================================================
    // SPELL CLASSIFICATION
    // =========================================================================

    public static boolean isAddonProjectileSpell(String path) {
        if (path == null)
            return false;
        String p = path.toLowerCase();
        return p.contains("dripstone_bolt") ||
                p.contains("eroding_boulder") ||
                p.contains("chunker") ||
                p.contains("solar_beam") ||
                p.contains("psychic_bolt") ||
                p.contains("ashen_breath");
    }

    public static boolean isAddonSlugSpell(String path) {
        if (path == null)
            return false;
        String p = path.toLowerCase();
        return p.contains("meteor_storm") ||
                p.contains("chunker") ||
                p.contains("fissure") ||
                p.contains("petrivise") ||
                p.contains("solar_storm") ||
                p.contains("pillar_of_the_resounding_earth");
    }

    public static boolean isAddonBeamSpell(String path) {
        if (path == null)
            return false;
        String p = path.toLowerCase();
        return p.contains("solar_beam") || p.contains("ashen_breath");
    }

    // =========================================================================
    // REFLECTION & REGISTRY RESOLUTION HELPERS (Zero Asset Copying)
    // =========================================================================

    public static Entity createEntity(ServerLevel level, String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null)
            return null;
        return type.create(level);
    }

    public static void invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        if (target == null)
            return;
        Class<?> curr = target.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Method m = curr.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                m.invoke(target, args);
                return;
            } catch (NoSuchMethodException ignored) {
                curr = curr.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    public static Object invokeMethodWithReturn(Object target, String methodName, Class<?>[] paramTypes,
            Object... args) {
        if (target == null)
            return null;
        Class<?> curr = target.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Method m = curr.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                curr = curr.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }

    public static void playSound(ServerLevel level, Vec3 pos, String namespace, String soundPath, float volume,
            float pitch) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, soundPath);
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
            if (event != null) {
                level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume, pitch);
                return;
            }
        }
        level.playSound(null, pos.x, pos.y, pos.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, volume, pitch);
    }

    public static void applyMobEffect(LivingEntity target, String namespace, String effectPath, int duration,
            int amplifier) {
        if (target == null)
            return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, effectPath);
        if (BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
            if (holder != null) {
                target.addEffect(new MobEffectInstance(holder, duration, amplifier));
            }
        }
    }

    // =========================================================================
    // PROJECTILE LAUNCHER (Direct Muzzle Casting)
    // =========================================================================

    public static boolean tryLaunchAddonProjectile(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            String path,
            int spellLevel,
            float damage,
            int totalPellets,
            boolean isFirstPellet) {
        if (spell == null)
            return false;

        // 1. Dripstone Bolt (gtbcs_geomancy_plus:dripstone_bolt)
        if (path.contains("dripstone_bolt")) {
            Entity bolt = createEntity(level, MOD_GEOMANCY, "dripstone_bolt");
            if (bolt != null) {
                bolt.setPos(origin.x, origin.y, origin.z);
                if (bolt instanceof AbstractMagicProjectile magicProj) {
                    magicProj.setOwner(shooter);
                    magicProj.setDamage(damage);
                    magicProj.setNoGravity(true);
                    magicProj.shoot(direction);
                    magicProj.setDeltaMovement(direction.normalize().scale(SpellProjectileFactory.BULLET_SPEED));
                } else {
                    invokeMethod(bolt, "setOwner", new Class<?>[] { Entity.class }, shooter);
                    invokeMethod(bolt, "setDamage", new Class<?>[] { float.class }, damage);
                    invokeMethod(bolt, "setNoGravity", new Class<?>[] { boolean.class }, true);
                    invokeMethod(bolt, "shoot", new Class<?>[] { Vec3.class }, direction);
                    bolt.setDeltaMovement(direction.normalize().scale(SpellProjectileFactory.BULLET_SPEED));
                }
                level.addFreshEntity(bolt);
                playSound(level, origin, "minecraft", "entity.arrow.shoot", 1.0f, 1.2f);
                return true;
            }
        }

        // 2. Eroding Boulder (gtbcs_geomancy_plus:eroding_boulder) - Spread pellets for
        // shotguns!
        if (path.contains("eroding_boulder")) {
            Entity boulder = createEntity(level, MOD_GEOMANCY, "eroding_boulder");
            if (boulder != null) {
                Vec3 spawnPos = origin.add(direction.normalize().scale(totalPellets > 1 ? 0.8 : 1.2));
                boulder.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                if (boulder instanceof AbstractMagicProjectile magicProj) {
                    magicProj.setOwner(shooter);
                    magicProj.setDamage(damage);
                    magicProj.setNoGravity(true);
                    magicProj.shoot(direction);
                    magicProj.setDeltaMovement(direction.normalize()
                            .scale(SpellProjectileFactory.BULLET_SPEED * (totalPellets > 1 ? 1.0 : 0.9)));
                } else {
                    invokeMethod(boulder, "setOwner", new Class<?>[] { Entity.class }, shooter);
                    invokeMethod(boulder, "setDamage", new Class<?>[] { float.class }, damage);
                    invokeMethod(boulder, "setNoGravity", new Class<?>[] { boolean.class }, true);
                    invokeMethod(boulder, "shoot", new Class<?>[] { Vec3.class }, direction);
                    boulder.setDeltaMovement(direction.normalize()
                            .scale(SpellProjectileFactory.BULLET_SPEED * (totalPellets > 1 ? 1.0 : 0.9)));
                }
                float blastRadius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter,
                        (totalPellets > 1) ? 2.0f : (3.0f + spellLevel * 0.5f));
                int erodeAmp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter,
                        1 + spellLevel / 2);
                invokeMethod(boulder, "setBlastRadius", new Class<?>[] { float.class },
                        (totalPellets > 1) ? blastRadius * 0.6f : blastRadius);
                invokeMethod(boulder, "setEffectAmplifier", new Class<?>[] { int.class }, erodeAmp);
                level.addFreshEntity(boulder);
                if (isFirstPellet) {
                    playSound(level, origin, "minecraft", "entity.iron_golem.attack", 1.2f, 0.7f);
                }
                return true;
            }
        }

        // 3. Chunker (gtbcs_geomancy_plus:chunker) - Rolls forward accurately towards
        // aim
        if (path.contains("chunker")) {
            Entity chunker = createEntity(level, MOD_GEOMANCY, "chunker");
            if (chunker != null) {
                Vec3 forwardDir = direction.normalize();
                Vec3 spawnPos = origin.add(forwardDir.scale(3.5));

                float yRot = (float) (Mth.atan2(forwardDir.z, forwardDir.x) * (180.0 / Math.PI)) - 90.0f;
                float xRot = (float) -(Mth.atan2(forwardDir.y,
                        Math.sqrt(forwardDir.x * forwardDir.x + forwardDir.z * forwardDir.z)) * (180.0 / Math.PI));

                chunker.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                chunker.setYRot(yRot);
                chunker.setXRot(xRot);
                chunker.yRotO = yRot;
                chunker.xRotO = xRot;
                invokeMethod(chunker, "setYHeadRot", new Class<?>[] { float.class }, yRot);
                invokeMethod(chunker, "setSummoner", new Class<?>[] { LivingEntity.class }, shooter);
                invokeMethod(chunker, "setDamage", new Class<?>[] { float.class }, damage);
                invokeMethod(chunker, "setMaxAge", new Class<?>[] { int.class }, 180);
                chunker.setDeltaMovement(forwardDir.scale(SpellProjectileFactory.BULLET_SPEED));
                level.addFreshEntity(chunker);
                playSound(level, origin, "minecraft", "block.stone.place", 1.2f, 0.8f);
                return true;
            }
        }

        // 4. Solar Beam (gtbcs_geomancy_plus:solar_beam) - Beam raycast from muzzle
        if (path.contains("solar_beam")) {
            // Only one beam per trigger pull (shotgun volleys fire a single beam)
            if (!isFirstPellet || shooter == null)
                return true;

            // In Variant A, check if a beam is already channeling
            if (!USE_SOLAR_BEAM_VARIANT_B) {
                if (shooter instanceof net.minecraft.world.entity.player.Player player
                        && com.ironsspellbullet.event.SolarBeamLockManager.hasActiveBeam(player)) {
                    return true;
                }
            }

            Entity solarBeam = spawnAuthenticSolarBeam(level, shooter, origin, direction, damage);
            if (solarBeam != null) {
                if (!USE_SOLAR_BEAM_VARIANT_B && shooter instanceof net.minecraft.world.entity.player.Player player) {
                    com.ironsspellbullet.event.SolarBeamLockManager.lock(player, c_SolarBeamDuration + 5);
                }
                return true;
            }
        }

        // 5. Psychic Bolt (gametechbcs_spellbooks:psychic_bolt) - Fires initial bolt +
        // rapid-fire barrage like Flaming Barrage
        if (path.contains("psychic_bolt")) {
            boolean fired = fireSinglePsychicBolt(level, shooter, origin, direction, damage, spellLevel);
            if (fired && isFirstPellet && shooter instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                int totalBarrage = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 5);
                com.ironsspellbullet.event.ActiveSpellEntityManager.queuePsychicBoltBurst(
                        level, serverPlayer, damage, spellLevel, Math.max(1, totalBarrage - 1));
            }
            return fired;
        }

        // 7. Ashen Breath (gametechbcs_spellbooks:ashen_breath)
        // Fires as an authentic cone breath like Fire Breath with dense billowing smoke & fire stream
        if (path.contains("ashen_breath")) {
            Entity breath = createEntity(level, MOD_SPELLBOOKS, "ashen_breath");
            if (breath instanceof io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile cone) {
                // Position projectile at eye height so its native client-side tracking and parts align with shooter's aim
                Vec3 eyePos = (shooter != null) ? shooter.getEyePosition().subtract(0, 0.8, 0) : origin;
                InstantBeamHandler.configureAndSpawnCone(level, shooter, eyePos, direction, spell, damage, cone,
                        ParticleHelper.FIRE, ParticleTypes.SMOKE);

                // Replicate authentic AshenBreath projectile particle calculation directly on server
                // Forward velocity vector with 97% SMOKE and 3% FIRE billowing outwards from gun muzzle
                Vec3 normDir = direction.lengthSqr() > 1e-4 ? direction.normalize() : new Vec3(0, 0, 1);
                Vec3 emitterPos = origin.add(normDir.scale(1.6));
                for (int i = 0; i < 64; i++) {
                    double spreadX = (level.random.nextDouble() * 2.0 - 1.0) * 0.25;
                    double spreadY = (level.random.nextDouble() * 2.0 - 1.0) * 0.25;
                    double spreadZ = (level.random.nextDouble() * 2.0 - 1.0) * 0.25;

                    Vec3 randCone = new Vec3(
                            (level.random.nextDouble() * 2.0 - 1.0) * 0.8,
                            (level.random.nextDouble() * 2.0 - 1.0) * 0.8,
                            (level.random.nextDouble() * 2.0 - 1.0) * 0.8
                    ).normalize();

                    double speed = (level.random.nextDouble() * 0.4 + 0.45);
                    Vec3 vel = normDir.scale(3.0).add(randCone).normalize().scale(speed);

                    ParticleOptions pType = level.random.nextFloat() < 0.97f ? ParticleTypes.SMOKE : ParticleHelper.FIRE;
                    level.sendParticles(pType,
                            emitterPos.x + spreadX,
                            emitterPos.y + spreadY,
                            emitterPos.z + spreadZ,
                            0, vel.x, vel.y, vel.z, 1.0);
                }

                playSound(level, origin, "gametechbcs_spellbooks", "refined_revenant_breath", 1.5f, 1.0f);
                return true;
            }
        }

        return false;
    }

    public static boolean fireSinglePsychicBolt(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction,
            float damage, int spellLevel) {
        Entity bolt = createEntity(level, MOD_SPELLBOOKS, "psychic_bolt_projectile");
        if (bolt == null)
            return false;

        // Position slightly in front of gun muzzle/eye so it never self-clips the shooter
        Vec3 normDir = direction.lengthSqr() > 1e-4 ? direction.normalize() : new Vec3(0, 0, 1);
        Vec3 spawnPos = origin.add(normDir.scale(0.5));
        bolt.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        // Calculate authentic effect duration scaling with spellLevel and caster's spell power
        int effectDuration = 200 + (spellLevel * 20);
        invokeMethod(bolt, "setEffectDuration", new Class<?>[] { int.class }, effectDuration);

        // Search for target in front of gun crosshair to assign homing
        LivingEntity homingTarget = SpellProjectileFactory.findHomingTarget(level, shooter, origin, normDir, 48.0);
        if (homingTarget != null) {
            invokeMethod(bolt, "setHomingTarget", new Class<?>[] { LivingEntity.class }, homingTarget);
        }

        // Fire projectile with authentic speed and slight spread
        if (bolt instanceof AbstractMagicProjectile magicProj) {
            magicProj.setOwner(shooter);
            magicProj.setDamage(damage);
            magicProj.setNoGravity(true);
            // Call PsychicBoltProjectile's specific shoot(Vec3, float) if available, otherwise fallback
            try {
                Method shootWithSpread = bolt.getClass().getMethod("shoot", Vec3.class, float.class);
                shootWithSpread.invoke(bolt, normDir.scale(0.75), 0.05f);
            } catch (Throwable t) {
                magicProj.shoot(normDir);
            }
            magicProj.setDeltaMovement(normDir.scale(SpellProjectileFactory.BULLET_SPEED));
        } else {
            invokeMethod(bolt, "setOwner", new Class<?>[] { Entity.class }, shooter);
            invokeMethod(bolt, "setDamage", new Class<?>[] { float.class }, damage);
            invokeMethod(bolt, "setNoGravity", new Class<?>[] { boolean.class }, true);
            invokeMethod(bolt, "shoot", new Class<?>[] { Vec3.class, float.class }, normDir.scale(0.75), 0.05f);
            bolt.setDeltaMovement(normDir.scale(SpellProjectileFactory.BULLET_SPEED));
        }

        level.addFreshEntity(bolt);
        playSound(level, origin, "minecraft", "entity.illusioner.cast_spell", 1.0f, 1.3f);
        return true;
    }

    // =========================================================================
    // IMPACT EFFECT HANDLER (When Bullet Lands On Block Or Entity)
    // =========================================================================

    public static boolean handleImpact(
            ServerLevel level,
            Vec3 hitPos,
            Entity hitEntity,
            Entity bullet,
            LivingEntity shooter,
            AbstractSpell spell,
            int spellLevel,
            float damage,
            HitResult hitResult) {
        if (spell == null)
            return false;
        String path = spell.getSpellResource().getPath().toLowerCase();

        // ---------------------------------------------------------------------
        // A. GTBC's Geomancy Plus Impacts
        // ---------------------------------------------------------------------

        // 1. Fissure: Immediate authentic earth spike eruption directly at hit spot!
        if (path.contains("fissure")) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(hitPos));
            Vec3 burstPos = new Vec3(hitPos.x, ground.getY(), hitPos.z);

            // Ground block state for authentic texture & particle
            BlockPos blockBelow = ground.below();
            net.minecraft.world.level.block.state.BlockState groundState = level.getBlockState(blockBelow);
            if (groundState.isAir())
                groundState = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();

            // Spawn exactly ONE authentic Earth Spike at the hit spot (identical to
            // original ExtendedEntityFissure)
            Entity spike = createEntity(level, MOD_GEOMANCY, "extended_earth_spike");
            if (spike != null) {
                spike.setPos(burstPos.x, burstPos.y, burstPos.z);
                if (shooter != null) {
                    float yRot = (float) (Mth.atan2(shooter.getLookAngle().z, shooter.getLookAngle().x)
                            * (180.0 / Math.PI)) - 90.0f;
                    spike.setYRot(yRot);
                }
                invokeMethod(spike, "setCaster", new Class<?>[] { LivingEntity.class }, shooter);
                invokeMethod(spike, "setOwner", new Class<?>[] { Entity.class }, shooter);
                invokeMethod(spike, "setBlock",
                        new Class<?>[] { net.minecraft.world.level.block.state.BlockState.class }, groundState);
                invokeMethod(spike, "setDamage", new Class<?>[] { double.class }, (double) damage);
                level.addFreshEntity(spike);
            }

            // Authentic ground block particles bursting into the air
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, groundState),
                    burstPos.x, burstPos.y + 0.5, burstPos.z, 28, 0.7, 0.5, 0.7, 0.12);

            // Screen shake at burst location (matching original FissureSpell: 6.0f radius,
            // 0.008f, 5 ticks)
            try {
                Class<?> shakeClass = Class.forName("com.gametechbc.spelllib.entity.GSLScreenShakeEntity");
                Method shakeMethod = shakeClass.getDeclaredMethod("createScreenShake", Level.class, Vec3.class,
                        float.class, float.class, int.class, int.class, int.class, boolean.class);
                shakeMethod.invoke(null, level, burstPos, 6.0f, 0.008f, 5, 0, 5, false);
            } catch (Throwable ignored) {
            }

            playSound(level, burstPos, "minecraft", "block.deepslate.break", 1.8f, 0.8f);
            return true;
        }

        // 2. Tremor Spike: Erupts stone spikes in a cluster / concentric rings scaling
        // with spellLevel
        if (path.contains("tremor_spike")) {
            int ringCount = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter,
                    2 + spellLevel / 2);
            spawnTremorSpike(level, hitPos.x, hitPos.z, 0.0f, 0, shooter, damage);
            for (int i = 0; i < ringCount; i++) {
                double r = 2.5d + i * 1.5d;
                int count = 6 + i * 6;
                spawnTremorSpikeRing(level, hitPos, r, count, i * 2, shooter, damage);
            }
            playSound(level, hitPos, "minecraft", "block.dripstone_block.break", 1.6f, 0.85f);
            return true;
        }

        // 3. Petrivise: Traps target by spawning authentic stone walls surrounding the
        // target upon impact
        if (path.contains("petrivise")) {
            Vec3 targetCenter = (hitEntity != null) ? hitEntity.position() : hitPos;
            Vec3 dir = (bullet != null && bullet.getDeltaMovement().lengthSqr() > 1e-4)
                    ? bullet.getDeltaMovement()
                    : (shooter != null ? shooter.getLookAngle() : new Vec3(0, 0, 1));
            spawnPetriviseWallsAroundTarget(level, targetCenter, dir, shooter, damage);
            return true;
        }

        // 4. Pillar of the Resounding Earth: Creates a single resonating stone pillar
        // (clears old ones)
        if (path.contains("pillar_of_the_resounding_earth")) {
            removeExistingPillars(level, shooter);

            Entity pillar = createEntity(level, MOD_GEOMANCY, "resonate_pillar");
            if (pillar != null) {
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(hitPos));
                pillar.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter,
                        300 + spellLevel * 60);
                float shockwaveRadius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter,
                        6.0f + spellLevel * 1.5f);
                int erodeAmp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter,
                        1 + spellLevel / 2);

                invokeMethod(pillar, "setSummoner", new Class<?>[] { LivingEntity.class }, shooter);
                invokeMethod(pillar, "setDamage", new Class<?>[] { float.class }, damage);
                invokeMethod(pillar, "setShockwaveRadius", new Class<?>[] { float.class }, shockwaveRadius);
                invokeMethod(pillar, "setMaxAge", new Class<?>[] { int.class }, duration);
                invokeMethod(pillar, "setErodeAmplifier", new Class<?>[] { int.class }, erodeAmp);
                level.addFreshEntity(pillar);
                playSound(level, hitPos, "minecraft", "block.deepslate.place", 2.0f, 0.7f);
                return true;
            }
        }

        // 5. Geo Conductor: Resonator pillar
        if (path.contains("geo_conductor")) {
            Entity resonator = createEntity(level, MOD_GEOMANCY, "resonator");
            if (resonator != null) {
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(hitPos));
                resonator.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter,
                        300 + spellLevel * 60);
                float effectRadius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter,
                        8.0f + spellLevel * 2.0f);
                int maxAegis = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, spellLevel);

                invokeMethod(resonator, "setSummoner", new Class<?>[] { LivingEntity.class }, shooter);
                invokeMethod(resonator, "setEffectRadius", new Class<?>[] { float.class }, effectRadius);
                invokeMethod(resonator, "setMaxAge", new Class<?>[] { int.class }, duration);
                invokeMethod(resonator, "setMaxAegisAmplifier", new Class<?>[] { int.class }, maxAegis);
                level.addFreshEntity(resonator);
                playSound(level, hitPos, "minecraft", "block.amethyst_block.resonate", 1.5f, 1.0f);
                return true;
            }
        }

        // 6. Solar Storm: Spawns authentic continuous bombardment zone (matching
        // SolarStormSpell & SolarStormEffect)
        if (path.contains("solar_storm")) {
            Vec3 targetCenter = (hitEntity != null) ? hitEntity.position() : hitPos;
            LivingEntity pinnedTarget = (hitEntity instanceof LivingEntity living) ? living : null;
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter,
                    160 + spellLevel * 60);
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 20.0f);
            ActiveSpellEntityManager.queueSolarStormZone(level, shooter, pinnedTarget, targetCenter, damage, spellLevel,
                    duration, (double) radius);
            playSound(level, targetCenter, "minecraft", "entity.lightning_bolt.thunder", 1.8f, 1.2f);
            level.sendParticles(ParticleTypes.FLAME, targetCenter.x, targetCenter.y + 0.5, targetCenter.z, 30, 0.8, 0.6,
                    0.8, 0.1);
            return true;
        }

        // 7. Tremor Step / Seismic Surf Impact: Earth burst shockwave
        if (path.contains("tremor_step") || path.contains("seismic_surf")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 4.0f);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, hitPos.x, hitPos.y + 0.1, hitPos.z, 20, radius * 0.4,
                    0.1, radius * 0.4, 0.05);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(hitPos.subtract(radius, 2, radius), hitPos.add(radius, 2, radius)),
                    e -> e != shooter && e.isAlive());
            for (LivingEntity target : targets) {
                SpellProjectileFactory.applyAuthenticSpellDamage(target, damage, spell, bullet, shooter);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.4, 0));
                target.hurtMarked = true;
            }
            playSound(level, hitPos, "minecraft", "entity.generic.explode", 1.0f, 1.3f);
            return true;
        }

        // ---------------------------------------------------------------------
        // B. GTBC's Spellbooks Impacts
        // ---------------------------------------------------------------------

        // 1. Ensnare: Spawns authentic plant root trap
        if (path.contains("ensnare")) {
            Entity ensnare = createEntity(level, MOD_SPELLBOOKS, "ensnare");
            if (ensnare instanceof AoeEntity aoe) {
                aoe.setPos(hitPos.x, hitPos.y, hitPos.z);
                aoe.setOwner(shooter);
                aoe.setRadius(SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 3.5f));
                aoe.setDuration(160);
                invokeMethod(ensnare, "setRootDuration", new Class<?>[] { int.class }, 100);
                level.addFreshEntity(ensnare);
                playSound(level, hitPos, "minecraft", "block.crop.break", 1.2f, 0.8f);
                return true;
            }
        }

        // 2. Crimson Downpour: Blood rain AOE cloud
        if (path.contains("crimson_downpour")) {
            Entity bloodRain = createEntity(level, MOD_SPELLBOOKS, "blood_rain_aoe");
            if (bloodRain instanceof AoeEntity aoe) {
                aoe.setPos(hitPos.x, hitPos.y, hitPos.z);
                aoe.setOwner(shooter);
                float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 5.0f);
                aoe.setRadius(radius);
                aoe.setDuration(240);
                aoe.setDamage(damage);
                invokeMethod(aoe, "setCircular", new Class<?>[] {});
                invokeMethod(aoe, "setHemorrhageEffectAmplifier", new Class<?>[] { int.class }, Math.max(0, spellLevel - 1));
                invokeMethod(aoe, "setLifestealAmount", new Class<?>[] { float.class }, 0.12f);
                level.addFreshEntity(bloodRain);
                playSound(level, hitPos, "minecraft", "weather.rain", 1.5f, 0.9f);
                return true;
            }
        }

        // 3. Acid Rain: Acidic downpour AOE
        if (path.contains("acid_rain")) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hitPos));
            Vec3 aoePos = new Vec3(hitPos.x, ground.getY() + 0.1, hitPos.z);
            Entity acidRain = createEntity(level, MOD_SPELLBOOKS, "acid_rain_aoe");
            float rad = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 5.0f);
            float acidDamage = Math.max(1.5f, damage);

            if (acidRain instanceof AoeEntity aoe) {
                aoe.setPos(aoePos.x, aoePos.y, aoePos.z);
                aoe.setOwner(shooter);
                aoe.setRadius(rad);
                aoe.setDuration(240);
                aoe.setDamage(acidDamage);
                invokeMethod(acidRain, "setCircular", new Class<?>[] {});
                invokeMethod(acidRain, "setCorrodedEffectAmplifier", new Class<?>[] { int.class },
                        Math.max(0, spellLevel - 1));
                level.addFreshEntity(acidRain);
            }

            // Sustained damage ticker ensuring enemies trapped in the downpour take continuous acid damage
            ActiveSpellEntityManager.queueAcidRainZone(level, shooter, acidRain, aoePos, acidDamage, rad, 240, spell);
            playSound(level, aoePos, "gametechbcs_spellbooks", "acid_rain_active", 1.5f, 1.0f);
            return true;
        }

        // 4. Blackout: Anti-magic nullification field
        if (path.contains("blackout")) {
            Entity blackout = createEntity(level, MOD_SPELLBOOKS, "blackout_anti_magic_field");
            if (blackout instanceof AoeEntity aoe) {
                aoe.setPos(hitPos.x, hitPos.y, hitPos.z);
                aoe.setOwner(shooter);
                aoe.setRadius(SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f));
                aoe.setDuration(200);
                aoe.setDamage(damage);
                level.addFreshEntity(blackout);
                playSound(level, hitPos, "minecraft", "block.beacon.deactivate", 1.5f, 0.7f);
                return true;
            }
        }

        // 5. Call Forth The Dead King: Summons Dead King boss (Limited to 1 active Dead King per world/area)
        if (path.contains("call_forth_the_dead_king") || path.contains("dead_king")) {
            // Check if a Dead King boss is already alive nearby (within 128 blocks)
            List<LivingEntity> existingBosses = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(hitPos.subtract(128, 128, 128), hitPos.add(128, 128, 128)),
                    e -> e.isAlive() && (e.getClass().getSimpleName().contains("DeadKing") || EntityType.getKey(e.getType()).getPath().contains("dead_king")));
            if (!existingBosses.isEmpty()) {
                if (shooter instanceof net.minecraft.world.entity.player.Player player) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("ui.irons_spell_bullet.dead_king_already_summoned")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                            true);
                }
                return false;
            }

            Entity deadKing = createEntity(level, MOD_SPELLBOOKS, "hard_dead_king");
            if (deadKing != null) {
                deadKing.setPos(hitPos.x, hitPos.y, hitPos.z);
                level.addFreshEntity(deadKing);
                playSound(level, hitPos, "minecraft", "entity.wither.spawn", 1.5f, 0.8f);
                return true;
            }
        }

        // 7. Meteor Storm: Sustained celestial meteor bombardment zone (similar to Solar Storm)
        if (path.contains("meteor_storm")) {
            Vec3 targetCenter = (hitEntity != null) ? hitEntity.position() : hitPos;
            LivingEntity pinnedTarget = (hitEntity instanceof LivingEntity living) ? living : null;
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter,
                    160 + spellLevel * 60);
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 24.0f);

            ActiveSpellEntityManager.queueMeteorStormZone(level, shooter, pinnedTarget, targetCenter, damage, spellLevel,
                    duration, (double) radius, spell);

            playSound(level, targetCenter, "minecraft", "entity.generic.explode", 1.8f, 0.7f);
            level.sendParticles(ParticleTypes.LAVA, targetCenter.x, targetCenter.y + 0.5, targetCenter.z, 30, 0.8, 0.6,
                    0.8, 0.1);
            return true;
        }

        // 8. Nullflare: Only applies status effects (Nullflare Fire & Nullflare Ice)
        if (path.contains("nullflare")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 4.0f);
            boolean isSneaking = shooter != null && shooter.isCrouching();

            // School targeting colors for blastwave particle: Fire if sneaking, Ice if normal
            Vector3f blastColor = isSneaking
                    ? SchoolRegistry.FIRE.get().getTargetingColor()
                    : SchoolRegistry.ICE.get().getTargetingColor();
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(blastColor, radius),
                    hitPos.x, hitPos.y + 0.165, hitPos.z, 1, 0, 0, 0, 0, true);

            // Ambient dual elemental particles at hit location
            MagicManager.spawnParticles(level, ParticleTypes.SOUL_FIRE_FLAME, hitPos.x, hitPos.y + 0.5, hitPos.z, 20, radius * 0.4, 0.4, radius * 0.4, 0.05, false);
            MagicManager.spawnParticles(level, ParticleTypes.SNOWFLAKE, hitPos.x, hitPos.y + 0.5, hitPos.z, 20, radius * 0.4, 0.4, radius * 0.4, 0.05, false);

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(hitPos.subtract(radius, 4, radius), hitPos.add(radius, 4, radius)),
                    e -> e != shooter && e.isAlive() && !e.isAlliedTo(shooter));

            for (LivingEntity target : targets) {
                if (isSneaking) {
                    // Fire mode: Applies Nullflare Fire effect (stacks amplifier if already present)
                    applyMobEffect(target, MOD_SPELLBOOKS, "nullflare_fire", 200, spellLevel - 1);
                } else {
                    // Ice mode: Applies Nullflare Ice effect (stacks amplifier if already present)
                    applyMobEffect(target, MOD_SPELLBOOKS, "nullflare_ice", 200, spellLevel - 1);
                }
            }

            playSound(level, hitPos, "gametechbcs_spellbooks", "nullflare_two", 1.8f, 1.0f);
            return true;
        }

        // 9. Shatterpoint: Frost freeze and shatter explosion (also handled as beam on launch)
        if (path.contains("shatterpoint")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 3.5f);
            MagicManager.spawnParticles(level, ParticleHelper.SNOW_DUST, hitPos.x, hitPos.y + 0.5, hitPos.z, 50, radius * 0.3, 0.3, radius * 0.3, 0.2, false);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(hitPos.subtract(radius, 2, radius), hitPos.add(radius, 2, radius)),
                    e -> e != shooter && e.isAlive());
            for (LivingEntity target : targets) {
                SpellProjectileFactory.applyAuthenticSpellDamage(target, damage, spell, bullet, shooter);
                target.setTicksFrozen(200);
            }
            playSound(level, hitPos, "gametechbcs_spellbooks", "shatterpoint_cast", 1.5f, 1.0f);
            return true;
        }

        // 10. Aerial Collapse: Crushing gravity pull downward + Authentic Aerial Collapse effect
        if (path.contains("aerial_collapse")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 5.0f);
            Vector3f natureColor = SchoolRegistry.NATURE.get().getTargetingColor();
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(natureColor, radius),
                    hitPos.x, hitPos.y + 0.165, hitPos.z, 1, 0, 0, 0, 0, true);

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(hitPos.subtract(radius, 4, radius), hitPos.add(radius, 4, radius)),
                    e -> e != shooter && e.isAlive() && !e.isAlliedTo(shooter));

            for (LivingEntity target : targets) {
                // Apply authentic Aerial Collapse status effect (which triggers the true crushing and damage mechanics)
                applyMobEffect(target, MOD_SPELLBOOKS, "aerial_collapse", 100, Math.max(0, (int) damage - 1));
                // Add immediate downward momentum
                target.setDeltaMovement(target.getDeltaMovement().add(0, -1.8, 0));
                target.hurtMarked = true;
            }

            CameraShakeManager.addCameraShake(new CameraShakeData(level, 20, hitPos, 8.0f));
            playSound(level, hitPos, "gametechbcs_spellbooks", "aerial_collapse", 1.8f, 1.0f);
            return true;
        }

        // 11. Status Effect Impacts (Displacement, Lingering Strain, Flames Reborn)
        if (path.contains("displacement")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
            AABB area = new AABB(hitPos.subtract(radius, radius, radius), hitPos.add(radius, radius, radius));
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != shooter && e.isAlive() && !e.isAlliedTo(shooter));

            for (LivingEntity target : nearby) {
                // Emit authentic unstable ender body particles on the target before displacement
                for (int i = 0; i < 50; i++) {
                    double ox = (level.random.nextDouble() - 0.5) * target.getBbWidth();
                    double oy = level.random.nextDouble() * target.getBbHeight();
                    double oz = (level.random.nextDouble() - 0.5) * target.getBbWidth();
                    level.sendParticles(ParticleHelper.UNSTABLE_ENDER, target.getX() + ox, target.getY() + oy, target.getZ() + oz, 1, 0, 0, 0, 0);
                }

                // Scatter / warp teleport target away from the impact point and behind their facing angle
                Vec3 awayFromImpact = target.position().subtract(hitPos);
                Vec3 pushDir;
                if (awayFromImpact.lengthSqr() > 1e-4) {
                    pushDir = awayFromImpact.normalize();
                } else {
                    pushDir = target.getLookAngle().scale(-1.0).normalize();
                }

                // Base teleport pos shifted 5 blocks away + random horizontal scatter (matching DisplacementSpell)
                Vec3 base = target.position().add(pushDir.scale(5.0));
                double rx = (level.random.nextDouble() - 0.5) * 7.0;
                double rz = (level.random.nextDouble() - 0.5) * 7.0;
                double destX = base.x + rx;
                double destZ = base.z + rz;

                // Find valid motion-blocking ground level around target's height
                BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(destX, target.getY(), destZ));
                double destY = Math.abs(groundPos.getY() - target.getY()) <= 6 ? groundPos.getY() : target.getY();

                target.teleportTo(destX, destY, destZ);

                // Emit authentic unstable ender body particles at destination
                for (int i = 0; i < 50; i++) {
                    double ox = (level.random.nextDouble() - 0.5) * target.getBbWidth();
                    double oy = level.random.nextDouble() * target.getBbHeight();
                    double oz = (level.random.nextDouble() - 0.5) * target.getBbWidth();
                    level.sendParticles(ParticleHelper.UNSTABLE_ENDER, target.getX() + ox, target.getY() + oy, target.getZ() + oz, 1, 0, 0, 0, 0);
                }

                // Apply authentic spell damage without applying displacement mob effect to enemies
                SpellProjectileFactory.applyAuthenticSpellDamage(target, damage, spell, bullet, shooter);
            }

            // Visual shockwave & sound at impact point
            playSound(level, hitPos, "gametechbcs_spellbooks", "displacement", 1.5f, 1.0f);
            return true;
        }
        if (path.contains("lingering_strain")) {
            if (hitEntity instanceof LivingEntity living) {
                applyMobEffect(living, MOD_SPELLBOOKS, "lingering_strain", 200, spellLevel - 1);
                SpellProjectileFactory.applyAuthenticSpellDamage(living, damage, spell, bullet, shooter);
                return true;
            }
        }
        if (path.contains("flames_reborn")) {
            applyMobEffect(shooter, MOD_SPELLBOOKS, "flames_reborn", 300, spellLevel - 1);
            if (hitEntity instanceof LivingEntity living) {
                SpellProjectileFactory.applyAuthenticSpellDamage(living, damage, spell, bullet, shooter);
            }
            return true;
        }

        return false;
    }

    // =========================================================================
    // SUB-HELPERS FOR ADVANCED ENTITY ARRANGEMENTS
    // =========================================================================

    public static void removeExistingPillars(ServerLevel level, LivingEntity shooter) {
        if (shooter == null)
            return;
        List<Entity> pillars = level.getEntitiesOfClass(Entity.class, shooter.getBoundingBox().inflate(96.0),
                e -> {
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                    if (id != null && id.getNamespace().equals(MOD_GEOMANCY)
                            && id.getPath().equals("resonate_pillar")) {
                        try {
                            Method m = e.getClass().getMethod("getSummoner");
                            Object sum = m.invoke(e);
                            if (sum == shooter)
                                return true;
                            if (sum instanceof LivingEntity living && living.getUUID().equals(shooter.getUUID()))
                                return true;
                        } catch (Throwable ignored) {
                        }
                    }
                    return false;
                });
        for (Entity p : pillars) {
            p.discard();
        }
    }

    public static void spawnPetriviseWallsAroundTarget(ServerLevel level, Vec3 center, Vec3 dir, LivingEntity shooter,
            float damage) {
        Vec3 forward = new Vec3(dir.x, 0, dir.z).normalize();
        if (forward.lengthSqr() < 1e-4)
            forward = new Vec3(0, 0, 1);

        Vec3 leftNormal = new Vec3(-forward.z, 0, forward.x).normalize();
        Vec3 rightNormal = leftNormal.scale(-1.0);

        float baseYaw = (float) (Mth.atan2(forward.z, forward.x) * (180.0 / Math.PI)) - 90.0f;
        float leftYaw = baseYaw - 90.0f;
        float rightYaw = baseYaw + 90.0f;

        float wallOffset = 5.0f;
        int totalPillars = 14;
        double spacing = 1.25;
        float slamSpeed = 0.65f;
        int baseDelay = 5;

        try {
            Class<?> pillarClass = Class.forName(
                    "com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.petrivise_pillar.PetrivisePillarEntity");
            Constructor<?> ctor = pillarClass.getConstructor(
                    Level.class, double.class, double.class, double.class,
                    float.class, int.class, int.class, float.class, float.class,
                    int.class, LivingEntity.class);

            for (int i = 0; i < totalPillars; i++) {
                double longitudinalOffset = (i - (totalPillars - 1) / 2.0) * spacing;
                Vec3 centerLinePos = center.add(forward.scale(longitudinalOffset));

                Vec3 leftPos = centerLinePos.add(leftNormal.scale(wallOffset));
                Vec3 rightPos = centerLinePos.add(rightNormal.scale(wallOffset));

                Vec3 leftGround = findGroundLevel(level, leftPos);
                Vec3 rightGround = findGroundLevel(level, rightPos);

                int warmupDelay = i;
                int slamDelay = baseDelay + (totalPillars - 1 - i);
                int pillarIndex = i + 1;

                Entity leftPillar = (Entity) ctor.newInstance(
                        level, leftGround.x, leftGround.y, leftGround.z,
                        leftYaw, warmupDelay, slamDelay, slamSpeed, damage, pillarIndex, shooter);
                if (i == totalPillars / 2) {
                    invokeMethod(leftPillar, "setEnableScreenShake", new Class<?>[] { boolean.class }, true);
                }
                level.addFreshEntity(leftPillar);

                Entity rightPillar = (Entity) ctor.newInstance(
                        level, rightGround.x, rightGround.y, rightGround.z,
                        rightYaw, warmupDelay, slamDelay, slamSpeed, damage, pillarIndex, shooter);
                if (i == totalPillars / 2) {
                    invokeMethod(rightPillar, "setEnableScreenShake", new Class<?>[] { boolean.class }, true);
                }
                level.addFreshEntity(rightPillar);
            }

            // Screen shake at target center
            try {
                Class<?> shakeClass = Class.forName("com.gametechbc.spelllib.entity.GSLScreenShakeEntity");
                Method shakeMethod = shakeClass.getDeclaredMethod("createScreenShake", Level.class, Vec3.class,
                        float.class, float.class, int.class, int.class, int.class, boolean.class);
                shakeMethod.invoke(null, level, center, 24.0f, 0.015f, 26, 0, 12, true);
            } catch (Throwable ignored) {
            }

            playSound(level, center, "minecraft", "block.dripstone_block.break", 2.0f, 0.8f);
        } catch (Throwable ignored) {
        }
    }

    private static Vec3 findGroundLevel(Level level, Vec3 pos) {
        double startY = pos.y + 1.0;
        int minY = Mth.floor(pos.y) - 3;
        BlockPos currentPos = new BlockPos(Mth.floor(pos.x), Mth.floor(startY), Mth.floor(pos.z));
        boolean found = false;
        double groundYOffset = 0.0;
        while (currentPos.getY() >= minY) {
            BlockPos below = currentPos.below();
            net.minecraft.world.level.block.state.BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(currentPos)) {
                    net.minecraft.world.level.block.state.BlockState currentState = level.getBlockState(currentPos);
                    VoxelShape shape = currentState.getCollisionShape(level, currentPos);
                    if (!shape.isEmpty()) {
                        groundYOffset = shape.max(Direction.Axis.Y);
                    }
                }
                found = true;
                break;
            }
            currentPos = currentPos.below();
        }
        if (found) {
            return new Vec3(pos.x, (double) currentPos.getY() + groundYOffset, pos.z);
        }
        return pos;
    }

    private static void spawnTremorSpikeRing(ServerLevel level, Vec3 center, double radius, int count, int warmup,
            LivingEntity caster, float damage) {
        for (int i = 0; i < count; i++) {
            double theta = (2.0 * Math.PI * i) / count;
            double x = center.x + Math.cos(theta) * radius;
            double z = center.z + Math.sin(theta) * radius;
            float yaw = (float) Math.toDegrees(theta) + 270.0f;
            spawnTremorSpike(level, x, z, yaw, warmup, caster, damage);
        }
    }

    private static void spawnTremorSpike(ServerLevel level, double x, double z, float yaw, int warmup,
            LivingEntity caster, float damage) {
        Entity spike = createEntity(level, MOD_GEOMANCY, "tremor_spike");
        if (spike != null) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, 0, z));
            spike.setPos(x, ground.getY(), z);
            spike.setYRot(yaw);
            spike.setXRot(0.0f);
            invokeMethod(spike, "setDamage", new Class<?>[] { float.class }, damage);
            invokeMethod(spike, "setWarmupTicks", new Class<?>[] { int.class }, warmup);
            invokeMethod(spike, "setCaster", new Class<?>[] { LivingEntity.class }, caster);
            invokeMethod(spike, "detectBlockBeneath", new Class<?>[] {});
            level.addFreshEntity(spike);
        }
    }
}
