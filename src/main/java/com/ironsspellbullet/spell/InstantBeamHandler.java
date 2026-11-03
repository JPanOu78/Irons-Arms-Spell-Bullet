package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Handles all Beam / Ray / Laser / Breath spells when fired from guns.
 * Completely cuts out charge-up / warmup delays and fires instantly from the gun's muzzle.
 */
public class InstantBeamHandler {

    public static boolean isBeamSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("solar_beam") ||
                p.contains("ray_of_siphoning") ||
                p.contains("siphon") ||
                p.contains("ray_of_frost") ||
                p.contains("soulfire_ray") ||
                p.contains("dragon_breath") ||
                p.contains("fire_breath") ||
                p.contains("poison_breath") ||
                p.contains("cone_of_cold") ||
                p.contains("void_beam") ||
                p.contains("atomic_laser") ||
                p.contains("laser") ||
                p.contains("beacon_ray") ||
                p.contains("ashen_breath") ||
                p.contains("frost_breath");
    }

    public static boolean fireBeam(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage,
            float spread
    ) {
        if (spell == null) return false;
        String path = spell.getSpellResource().getPath().toLowerCase();

        boolean isBreath = path.contains("breath") || path.contains("cone");
        double range = isBreath ? 20.0 : 48.0;

        // Apply spread if any
        Vec3 dir = direction.normalize();
        if (spread > 0.0f) {
            dir = dir.add(
                    level.random.triangle(0.0, 0.0172275 * (double) spread),
                    level.random.triangle(0.0, 0.0172275 * (double) spread),
                    level.random.triangle(0.0, 0.0172275 * (double) spread)
            ).normalize();
        }

        Vec3 targetPos = origin.add(dir.scale(range));

        // Clip against solid blocks to find beam end point
        BlockHitResult blockHit = level.clip(new ClipContext(
                origin,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shooter
        ));

        Vec3 endPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : targetPos;
        double actualDistance = origin.distanceTo(endPos);

        // 1. Render Instant Dense Particle Beam along line from muzzle to hit position
        renderBeamParticles(level, origin, dir, actualDistance, path, endPos);

        // 2. Play Instant Firing & Muzzle Sounds
        playBeamSounds(level, origin, endPos, path);

        // 3. Piercing Beam Hit Detection along line segment
        AABB beamBox = new AABB(origin, endPos).inflate(isBreath ? 2.0 : 1.2);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                beamBox,
                e -> e != shooter && e.isAlive() && (shooter == null || !e.isAlliedTo(shooter))
        );

        double hitRadius = isBreath ? 1.8 : 0.85;

        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            double distToBeam = distancePointToSegment(origin, endPos, targetCenter);

            if (distToBeam <= (target.getBbWidth() * 0.5 + hitRadius)) {
                applyBeamDamageAndEffects(level, shooter, target, spell, path, spellLevel, damage, origin);
            }
        }

        return true;
    }

    private static void renderBeamParticles(
            ServerLevel level,
            Vec3 origin,
            Vec3 direction,
            double maxDist,
            String path,
            Vec3 endPos
    ) {
        double step = 0.45;

        if (path.contains("solar_beam")) {
            // Blinding Solar Beam
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
                level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 2, 0.12, 0.12, 0.12, 0.02);
                level.sendParticles(ParticleHelper.SUNBEAM, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                if (level.random.nextFloat() < 0.4f) {
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            // Solar Flash at Impact
            level.sendParticles(ParticleTypes.FLASH, endPos.x, endPos.y, endPos.z, 2, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, endPos.x, endPos.y, endPos.z, 3, 0.3, 0.3, 0.3, 0.05);

        } else if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            // Blood Siphon Ray
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.SIPHON, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.01);
                level.sendParticles(ParticleHelper.BLOOD, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
            }
            level.sendParticles(ParticleHelper.BLOOD_GROUND, endPos.x, endPos.y, endPos.z, 5, 0.2, 0.2, 0.2, 0.05);

        } else if (path.contains("ray_of_frost") || path.contains("frost_breath")) {
            // Freezing Frost Ray
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.ICY_FOG, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
            }
            level.sendParticles(ParticleTypes.POOF, endPos.x, endPos.y, endPos.z, 8, 0.3, 0.3, 0.3, 0.05);

        } else if (path.contains("soulfire_ray")) {
            // Cyan Soulfire Ray
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.SOUL_FIRE, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
                level.sendParticles(ParticleHelper.SOULFIRE_SPARKS, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.03);
            }
            level.sendParticles(ParticleTypes.SOUL, endPos.x, endPos.y, endPos.z, 5, 0.2, 0.2, 0.2, 0.02);

        } else if (path.contains("void_beam") || path.contains("beacon_ray")) {
            // Abyssal / Void Beam
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 3, 0.12, 0.12, 0.12, 0.05);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, endPos.x, endPos.y, endPos.z, 10, 0.4, 0.4, 0.4, 0.05);

        } else if (path.contains("atomic_laser") || path.contains("laser")) {
            // Technological / Lightning Laser
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.08, 0.08, 0.08, 0.05);
                level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
            }
            level.sendParticles(ParticleTypes.FLASH, endPos.x, endPos.y, endPos.z, 1, 0, 0, 0, 0);

        } else if (path.contains("poison_breath")) {
            // Poison Cone
            for (double d = 0.5; d <= maxDist; d += step) {
                double spreadFactor = d * 0.12;
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.POISON_CLOUD, p.x, p.y, p.z, 3, spreadFactor, spreadFactor, spreadFactor, 0.02);
            }
        } else {
            // Generic Flame / Breath Beam
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.01);
            }
        }
    }

    private static void playBeamSounds(ServerLevel level, Vec3 origin, Vec3 endPos, String path) {
        if (path.contains("solar_beam")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.8f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.9f);
        } else if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.3f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 1.7f);
        } else if (path.contains("ray_of_frost") || path.contains("frost_breath")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.6f, 1.1f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 1.5f);
        } else if (path.contains("soulfire_ray")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 1.3f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 1.2f);
        } else if (path.contains("void_beam") || path.contains("beacon_ray")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.8f);
        } else {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 1.5f);
        }
    }

    private static void applyBeamDamageAndEffects(
            ServerLevel level,
            LivingEntity shooter,
            LivingEntity target,
            AbstractSpell spell,
            String path,
            int spellLevel,
            float damage,
            Vec3 origin
    ) {
        if (path.contains("solar_beam")) {
            // Instant Solar Beam Pierce
            target.hurt(SpellDamageSource.source(null, shooter, spell), damage * 1.5f);
            target.igniteForSeconds(5 + spellLevel);

        } else if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            // Instant Blood Siphon with Guaranteed Lifesteal
            target.hurt(SpellDamageSource.source(null, shooter, spell).setLifestealPercent(1.0f), damage);
            float healAmount = Math.max(3.0f, damage * 0.4f);
            if (shooter != null) {
                shooter.heal(healAmount);
                try {
                    Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    Vec3 shooterCenter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(targetCenter, shooterCenter));
                } catch (Throwable ignored) {}
            }

        } else if (path.contains("ray_of_frost") || path.contains("frost_breath") || path.contains("cone_of_cold")) {
            // Instant Freezing Frost
            int freezeTicks = 160 + spellLevel * 40;
            target.hurt(SpellDamageSource.source(null, shooter, spell).setFreezeTicks(freezeTicks), damage);
            Utils.addFreezeTicks(target, freezeTicks);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));

        } else if (path.contains("soulfire_ray")) {
            target.hurt(SpellDamageSource.source(null, shooter, spell), damage * 1.2f);
            target.igniteForSeconds(6 + spellLevel);

        } else if (path.contains("poison_breath")) {
            target.hurt(SpellDamageSource.source(null, shooter, spell), damage);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 140 + spellLevel * 20, 1));

        } else {
            target.hurt(SpellDamageSource.source(null, shooter, spell), damage);
        }
    }

    private static double distancePointToSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double abLenSq = ab.lengthSqr();
        if (abLenSq < 1e-6) {
            return p.distanceTo(a);
        }
        double t = p.subtract(a).dot(ab) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec3 projection = a.add(ab.scale(t));
        return p.distanceTo(projection);
    }
}

