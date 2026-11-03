package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile;
import io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.electrocute.ElectrocuteProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.ray_of_frost.RayOfFrostVisualEntity;
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
 * Handles continuous / channeling and instant beam spells from core Iron's Spells.
 * Fires directly and immediately from the gun muzzle with authentic 3D entities and effects!
 */
public class InstantBeamHandler {

    /**
     * Checks if a spell is a beam or ray spell in core Iron's Spells.
     */
    public static boolean isBeamSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("ray_of_frost") ||
                p.contains("ray_of_siphoning") ||
                p.contains("siphon") ||
                p.contains("electrocute") ||
                p.contains("cone_of_cold") ||
                p.contains("fire_breath") ||
                p.contains("dragon_breath") ||
                p.contains("poison_breath");
    }

    /**
     * Fires the beam spell directly from the gun muzzle.
     */
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

        // 1. Ray of Frost (Iron's Spells: RayOfFrostVisualEntity)
        if (path.contains("ray_of_frost")) {
            return fireRayOfFrost(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 2. Ray of Siphoning (Iron's Spells: BloodSiphon stream + instant max lifesteal)
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            return fireRayOfSiphoning(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 4. Electrocute (Iron's Spells: ElectrocuteProjectile)
        if (path.contains("electrocute")) {
            ElectrocuteProjectile projectile = new ElectrocuteProjectile(level, shooter);
            projectile.setPos(origin);
            projectile.setDamage(baseDamage);
            level.addFreshEntity(projectile);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, 1.8f);
            return true;
        }

        // 5. Cone of Cold (Iron's Spells: ConeOfColdProjectile)
        if (path.contains("cone_of_cold")) {
            ConeOfColdProjectile cone = new ConeOfColdProjectile(level, shooter);
            cone.setPos(origin);
            cone.setDamage(baseDamage);
            level.addFreshEntity(cone);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.6f, 1.2f);
            return true;
        }

        // 6. Fire Breath (Iron's Spells: FireBreathProjectile)
        if (path.contains("fire_breath")) {
            FireBreathProjectile breath = new FireBreathProjectile(level, shooter);
            breath.setPos(origin);
            breath.setDamage(baseDamage);
            level.addFreshEntity(breath);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f);
            return true;
        }

        // 7. Dragon Breath (Iron's Spells: DragonBreathProjectile)
        if (path.contains("dragon_breath")) {
            DragonBreathProjectile breath = new DragonBreathProjectile(level, shooter);
            breath.setPos(origin);
            breath.setDamage(baseDamage);
            level.addFreshEntity(breath);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.4f, 1.5f);
            return true;
        }

        // 8. Poison Breath
        if (path.contains("poison_breath")) {
            return firePoisonBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        return false;
    }

    private static boolean fireRayOfFrost(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        Vec3 targetPos = origin.add(direction.scale(64.0));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 hitPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : targetPos;

        AABB beamBox = new AABB(origin, hitPos).inflate(1.2);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, beamBox, e -> e != shooter && e.isAlive());
        LivingEntity hitTarget = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity e : targets) {
            if (e.getBoundingBox().inflate(0.3).clip(origin, hitPos).isPresent()) {
                double d = origin.distanceToSqr(e.position());
                if (d < closest) {
                    closest = d;
                    hitTarget = e;
                }
            }
        }
        if (hitTarget != null) {
            hitPos = hitTarget.position().add(0, hitTarget.getBbHeight() * 0.5, 0);
        }

        // 1. Spawn authentic 100% Iron's Spells RayOfFrostVisualEntity
        RayOfFrostVisualEntity frostBeam = new RayOfFrostVisualEntity(level, origin, hitPos, shooter);
        level.addFreshEntity(frostBeam);

        // 2. Play sound and impact particles
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 1.4f);
        level.sendParticles(ParticleHelper.ICY_FOG, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05);

        // 3. Apply freeze & damage
        if (hitTarget != null) {
            int freezeTicks = 160 + spellLevel * 40;
            SpellDamageSource src = SpellDamageSource.source(null, shooter, spell).indirect().setFreezeTicks(freezeTicks);
            hitTarget.hurt(src, damage);
            Utils.addFreezeTicks(hitTarget, freezeTicks);
            hitTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100 + spellLevel * 20, 2));
        }

        return true;
    }

    private static boolean fireRayOfSiphoning(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        Vec3 targetPos = origin.add(direction.scale(36.0));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 hitPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : targetPos;

        AABB beamBox = new AABB(origin, hitPos).inflate(1.2);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, beamBox, e -> e != shooter && e.isAlive());
        LivingEntity hitTarget = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity e : targets) {
            if (e.getBoundingBox().inflate(0.3).clip(origin, hitPos).isPresent()) {
                double d = origin.distanceToSqr(e.position());
                if (d < closest) {
                    closest = d;
                    hitTarget = e;
                }
            }
        }
        if (hitTarget != null) {
            hitPos = hitTarget.position().add(0, hitTarget.getBbHeight() * 0.5, 0);
        }

        // 1. Guaranteed Blood Siphon stream sent to client
        Vec3 shooterCenter = (shooter != null) ? shooter.position().add(0, shooter.getBbHeight() * 0.5, 0) : origin;
        if (shooter != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, origin));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, shooterCenter));
        }

        // 2. Dense SIPHON and BLOOD particles along the beam path
        double dist = origin.distanceTo(hitPos);
        for (double d = 0.5; d <= dist; d += 0.4) {
            Vec3 p = origin.add(direction.scale(d));
            level.sendParticles(ParticleHelper.SIPHON, p.x, p.y, p.z, 3, 0.08, 0.08, 0.08, 0.01);
            level.sendParticles(ParticleHelper.BLOOD, p.x, p.y, p.z, 3, 0.08, 0.08, 0.08, 0.02);
        }
        level.sendParticles(ParticleHelper.BLOOD_GROUND, hitPos.x, hitPos.y, hitPos.z, 15, 0.3, 0.3, 0.3, 0.05);

        // 3. Audio
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.4f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.3f, 1.6f);

        // 4. Instant burst damage + instant max lifesteal
        float power = spell.getSpellPower(spellLevel, shooter);
        float totalDamage = Math.max(16.0f, power * 4.8f);
        if (hitTarget != null) {
            hitTarget.hurt(SpellDamageSource.source(null, shooter, spell).setLifestealPercent(1.0f), totalDamage);
            if (shooter != null) {
                shooter.heal(Math.min(20.0f, totalDamage * 0.85f));
            }
        }

        return true;
    }

    private static boolean firePoisonBreath(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        Vec3 targetPos = origin.add(direction.scale(20.0));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 endPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : targetPos;

        double dist = origin.distanceTo(endPos);
        for (double d = 0.5; d <= dist; d += 0.5) {
            Vec3 p = origin.add(direction.scale(d));
            level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.5f, 1.0f);

        AABB box = new AABB(origin, endPos).inflate(1.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != shooter && e.isAlive());
        for (LivingEntity target : targets) {
            target.hurt(SpellDamageSource.source(null, shooter, spell), damage);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 140 + spellLevel * 20, 1));
        }

        return true;
    }
}
