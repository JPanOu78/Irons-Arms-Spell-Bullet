package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile;
import io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.eldritch_blast.EldritchBlastVisualEntity;
import io.redspace.ironsspellbooks.entity.spells.electrocute.ElectrocuteProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.gust.GustCollider;
import io.redspace.ironsspellbooks.entity.spells.ray_of_frost.RayOfFrostVisualEntity;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
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
     * Checks if a spell is a beam, ray, breath, or sonic stream in core Iron's Spells.
     */
    public static boolean isBeamSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("ray_of_frost") ||
                p.contains("ray_of_siphoning") ||
                p.contains("siphon") ||
                p.contains("eldritch_blast") ||
                p.contains("sonic_boom") ||
                p.contains("gust") ||
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

        // 1. Ray of Frost (Iron's Spells: RayOfFrostVisualEntity 3D ice beam)
        if (path.contains("ray_of_frost")) {
            return fireRayOfFrost(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 2. Ray of Siphoning (Iron's Spells: BloodSiphon stream + instant max lifesteal)
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            return fireRayOfSiphoning(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 3. Eldritch Blast (Iron's Spells: EldritchBlastVisualEntity 3D void beam)
        if (path.contains("eldritch_blast")) {
            return fireEldritchBlast(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 4. Sonic Boom (Warden Piercing Sonic Wave penetrating blocks)
        if (path.contains("sonic_boom")) {
            return fireSonicBoom(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 5. Gust (Iron's Spells: GustCollider)
        if (path.contains("gust")) {
            return fireGust(level, shooter, origin, direction);
        }

        // 6. Electrocute (Iron's Spells: ElectrocuteProjectile 3D lightning branch entity)
        if (path.contains("electrocute")) {
            ElectrocuteProjectile projectile = new ElectrocuteProjectile(level, shooter);
            projectile.setPos(origin);
            projectile.setDamage(baseDamage);
            level.addFreshEntity(projectile);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, 1.8f);
            return true;
        }

        // 7. Cone of Cold (Iron's Spells: ConeOfColdProjectile 3D frost cone entity)
        if (path.contains("cone_of_cold")) {
            ConeOfColdProjectile cone = new ConeOfColdProjectile(level, shooter);
            cone.setPos(origin);
            cone.setDamage(baseDamage);
            level.addFreshEntity(cone);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.6f, 1.2f);
            return true;
        }

        // 8. Fire Breath (Iron's Spells: FireBreathProjectile 3D flame jet entity)
        if (path.contains("fire_breath")) {
            FireBreathProjectile breath = new FireBreathProjectile(level, shooter);
            breath.setPos(origin);
            breath.setDamage(baseDamage);
            level.addFreshEntity(breath);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f);
            return true;
        }

        // 9. Dragon Breath (Iron's Spells: DragonBreathProjectile 3D dragon breath entity)
        if (path.contains("dragon_breath")) {
            DragonBreathProjectile breath = new DragonBreathProjectile(level, shooter);
            breath.setPos(origin);
            breath.setDamage(baseDamage);
            level.addFreshEntity(breath);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.4f, 1.5f);
            return true;
        }

        // 10. Poison Breath
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

        RayOfFrostVisualEntity frostBeam = new RayOfFrostVisualEntity(level, origin, hitPos, shooter);
        level.addFreshEntity(frostBeam);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 1.4f);
        level.sendParticles(ParticleHelper.ICY_FOG, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05);

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

        Vec3 shooterCenter = (shooter != null) ? shooter.position().add(0, shooter.getBbHeight() * 0.5, 0) : origin;
        if (shooter != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, origin));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, shooterCenter));
        }

        double dist = origin.distanceTo(hitPos);
        for (double d = 0.5; d <= dist; d += 0.35) {
            Vec3 p = origin.add(direction.scale(d));
            level.sendParticles(ParticleHelper.SIPHON, p.x, p.y, p.z, 3, 0.08, 0.08, 0.08, 0.01);
            level.sendParticles(ParticleHelper.BLOOD, p.x, p.y, p.z, 3, 0.08, 0.08, 0.08, 0.02);
        }
        level.sendParticles(ParticleHelper.BLOOD_GROUND, hitPos.x, hitPos.y, hitPos.z, 15, 0.3, 0.3, 0.3, 0.05);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.4f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.3f, 1.6f);

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

    private static boolean fireEldritchBlast(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        Vec3 targetPos = origin.add(direction.scale(54.0));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 hitPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : targetPos;

        AABB beamBox = new AABB(origin, hitPos).inflate(1.4);
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

        // 3D EldritchBlastVisualEntity from Iron's Spells
        EldritchBlastVisualEntity blast = new EldritchBlastVisualEntity(level, origin.subtract(0, 0.75, 0), hitPos, shooter);
        level.addFreshEntity(blast);

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.ELDRITCH_BLAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.SCULK_SOUL, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05);

        if (hitTarget != null) {
            hitTarget.hurt(SpellDamageSource.source(null, shooter, spell), damage * 1.3f);
            hitTarget.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100 + spellLevel * 20, 0));
            hitTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80 + spellLevel * 20, 2));
        }

        return true;
    }

    private static boolean fireSonicBoom(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        // Sonic Boom pierces through walls and shields up to 36 blocks!
        double maxDist = 36.0;
        Vec3 endPos = origin.add(direction.scale(maxDist));

        for (double d = 1.0; d <= maxDist; d += 1.0) {
            Vec3 p = origin.add(direction.scale(d));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5f, 1.0f);

        AABB lineBox = new AABB(origin, endPos).inflate(1.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, lineBox, e -> e != shooter && e.isAlive());
        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (distancePointToSegment(origin, endPos, targetCenter) <= (target.getBbWidth() * 0.5 + 1.2)) {
                target.hurt(SpellDamageSource.source(null, shooter, spell), damage * 1.5f);
                // Knockback
                target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(1.2)));
                target.hurtMarked = true;
            }
        }

        return true;
    }

    private static boolean fireGust(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction
    ) {
        GustCollider gust = new GustCollider(level, shooter);
        gust.setPos(origin);
        level.addFreshEntity(gust);
        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.GUST_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.2f);
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

    private static double distancePointToSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        Vec3 ap = p.subtract(a);
        double abLenSq = ab.lengthSqr();
        if (abLenSq == 0.0) return p.distanceTo(a);
        double t = Math.max(0.0, Math.min(1.0, ap.dot(ab) / abLenSq));
        Vec3 projection = a.add(ab.scale(t));
        return p.distanceTo(projection);
    }
}
