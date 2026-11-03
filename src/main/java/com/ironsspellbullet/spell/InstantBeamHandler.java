package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
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
 * Handles continuous / channeling spells from Iron's Spells when fired from guns.
 * Converts multi-second channeling spells into an instant single-shot burst:
 * - Uses authentic original textures and particles for a short, crisp pulse.
 * - Instantly applies the FULL MAXIMUM damage of the entire channeling duration.
 * - Instantly grants the MAXIMUM total lifesteal / healing to the shooter without needing to channel.
 */
public class InstantBeamHandler {

    /**
     * Checks if a spell is a continuous / channeling stream spell in core Iron's Spells.
     */
    public static boolean isBeamSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("ray_of_siphoning") ||
                p.contains("siphon") ||
                p.contains("electrocute") ||
                p.contains("cone_of_cold") ||
                p.contains("fire_breath") ||
                p.contains("dragon_breath") ||
                p.contains("poison_breath");
    }

    public static boolean fireBeam(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float baseDamage,
            float spread
    ) {
        if (spell == null) return false;
        String path = spell.getSpellResource().getPath().toLowerCase();

        boolean isBreath = path.contains("breath") || path.contains("cone");
        double range = isBreath ? 18.0 : 36.0;

        // Apply spread if shotgun / inaccurate
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

        // 1. Render authentic visual particle pulse along the beam line
        renderPulseParticles(level, origin, dir, actualDistance, path, endPos);

        // 2. Play authentic firing sound
        playPulseSounds(level, origin, endPos, path);

        // 3. Piercing Hit Detection along line segment
        AABB beamBox = new AABB(origin, endPos).inflate(isBreath ? 2.2 : 1.2);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                beamBox,
                e -> e != shooter && e.isAlive() && (shooter == null || !e.isAlliedTo(shooter))
        );

        double hitRadius = isBreath ? 2.0 : 0.9;
        float power = spell.getSpellPower(spellLevel, shooter);

        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            double distToBeam = distancePointToSegment(origin, endPos, targetCenter);

            if (distToBeam <= (target.getBbWidth() * 0.5 + hitRadius)) {
                applyBurstDamageAndEffects(level, shooter, target, spell, path, spellLevel, power, baseDamage, origin);
            }
        }

        return true;
    }

    private static void renderPulseParticles(
            ServerLevel level,
            Vec3 origin,
            Vec3 direction,
            double maxDist,
            String path,
            Vec3 endPos
    ) {
        double step = 0.5;

        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            // Authentic Blood Siphon Stream
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.SIPHON, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.01);
                level.sendParticles(ParticleHelper.BLOOD, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
            }
            level.sendParticles(ParticleHelper.BLOOD_GROUND, endPos.x, endPos.y, endPos.z, 6, 0.2, 0.2, 0.2, 0.05);

        } else if (path.contains("electrocute")) {
            // Authentic High-Voltage Lightning Arc Stream
            for (double d = 0.5; d <= maxDist; d += step) {
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.ELECTRICITY, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.03);
                level.sendParticles(ParticleHelper.ELECTRIC_SPARKS, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.05);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, endPos.x, endPos.y, endPos.z, 12, 0.3, 0.3, 0.3, 0.1);

        } else if (path.contains("cone_of_cold")) {
            // Authentic Freezing Frost Spray
            for (double d = 0.5; d <= maxDist; d += step) {
                double spread = d * 0.12;
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.ICY_FOG, p.x, p.y, p.z, 2, spread, spread, spread, 0.02);
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 2, spread, spread, spread, 0.03);
            }
            level.sendParticles(ParticleTypes.POOF, endPos.x, endPos.y, endPos.z, 8, 0.4, 0.4, 0.4, 0.05);

        } else if (path.contains("fire_breath")) {
            // Authentic Flamethrower Stream
            for (double d = 0.5; d <= maxDist; d += step) {
                double spread = d * 0.14;
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.FIRE, p.x, p.y, p.z, 2, spread, spread, spread, 0.02);
                level.sendParticles(ParticleHelper.EMBERS, p.x, p.y, p.z, 2, spread, spread, spread, 0.04);
            }

        } else if (path.contains("dragon_breath")) {
            // Authentic Dragon Breath Stream
            for (double d = 0.5; d <= maxDist; d += step) {
                double spread = d * 0.14;
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 3, spread, spread, spread, 0.02);
                level.sendParticles(ParticleHelper.UNSTABLE_ENDER, p.x, p.y, p.z, 1, spread, spread, spread, 0.02);
            }

        } else if (path.contains("poison_breath")) {
            // Authentic Poison Cloud Mist
            for (double d = 0.5; d <= maxDist; d += step) {
                double spread = d * 0.14;
                Vec3 p = origin.add(direction.scale(d));
                level.sendParticles(ParticleHelper.POISON_CLOUD, p.x, p.y, p.z, 3, spread, spread, spread, 0.02);
            }
        }
    }

    private static void playPulseSounds(ServerLevel level, Vec3 origin, Vec3 endPos, String path) {
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.4f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.3f, 1.6f);
        } else if (path.contains("electrocute")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, 1.8f);
        } else if (path.contains("cone_of_cold")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.6f, 1.2f);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f);
        } else if (path.contains("fire_breath")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f);
        } else if (path.contains("dragon_breath")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 1.5f);
        } else if (path.contains("poison_breath")) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.5f, 1.0f);
        }
    }

    private static void applyBurstDamageAndEffects(
            ServerLevel level,
            LivingEntity shooter,
            LivingEntity target,
            AbstractSpell spell,
            String path,
            int spellLevel,
            float power,
            float baseDamage,
            Vec3 origin
    ) {
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            // Full 5-second channeling damage delivered in 1 instant burst!
            float totalDamage = Math.max(16.0f, power * 4.8f);
            target.hurt(SpellDamageSource.source(null, shooter, spell).setLifestealPercent(1.0f), totalDamage);

            // Maximum total lifesteal heal granted to shooter instantly in 1 shot!
            if (shooter != null) {
                float totalHeal = Math.min(20.0f, totalDamage * 0.85f);
                shooter.heal(totalHeal);
                try {
                    Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    Vec3 shooterCenter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(targetCenter, shooterCenter));
                } catch (Throwable ignored) {}
            }

        } else if (path.contains("electrocute")) {
            // Full shock channel damage + stun slowdown
            float shockDamage = Math.max(14.0f, power * 4.2f);
            target.hurt(SpellDamageSource.source(null, shooter, spell), shockDamage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80 + spellLevel * 20, 3));

        } else if (path.contains("cone_of_cold")) {
            // Full freeze channel damage + instant max freeze ticks
            float coldDamage = Math.max(14.0f, power * 4.0f);
            int freezeTicks = 160 + spellLevel * 40;
            target.hurt(SpellDamageSource.source(null, shooter, spell).setFreezeTicks(freezeTicks), coldDamage);
            Utils.addFreezeTicks(target, freezeTicks);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));

        } else if (path.contains("fire_breath")) {
            // Full flamethrower damage + max fire ticks
            float fireDamage = Math.max(14.0f, power * 4.0f);
            target.hurt(SpellDamageSource.source(null, shooter, spell), fireDamage);
            target.igniteForSeconds(8 + spellLevel * 2);

        } else if (path.contains("dragon_breath")) {
            // Full dragon breath damage
            float dragonDamage = Math.max(15.0f, power * 4.2f);
            target.hurt(SpellDamageSource.source(null, shooter, spell), dragonDamage);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80 + spellLevel * 20, 1));

        } else if (path.contains("poison_breath")) {
            // Full toxic damage + Poison II
            float poisonDamage = Math.max(12.0f, power * 3.6f);
            target.hurt(SpellDamageSource.source(null, shooter, spell), poisonDamage);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 140 + spellLevel * 20, 1));
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
