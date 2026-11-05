package com.ironsspellbullet.spell;

import com.ironsspellbullet.event.ActiveSpellEntityManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile;
import io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.eldritch_blast.EldritchBlastVisualEntity;
import io.redspace.ironsspellbooks.entity.spells.electrocute.ElectrocuteProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.gust.GustCollider;
import io.redspace.ironsspellbooks.entity.spells.poison_breath.PoisonBreathProjectile;
import io.redspace.ironsspellbooks.entity.spells.ray_of_frost.RayOfFrostVisualEntity;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.particle.SoulfireRayParticleOptions;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handles continuous / channeling, instant beam, and spray/cone spells from core Iron's Spells.
 * Uses Iron's Spells' native damage calculation, range, freeze time, SpellDamageSource, and DamageSources.applyDamage directly!
 */
public class InstantBeamHandler {

    /**
     * Checks if a spell is a beam, ray, breath, or sonic stream in core Iron's Spells.
     */
    public static boolean isBeamSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("ray_of_frost") ||
                p.contains("soulfire_ray") ||
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
     * Fires the beam or spray spell directly from the gun muzzle using Iron's Spells' authentic parameters.
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

        // 1. Ray of Frost (Iron's Spells: RayOfFrostVisualEntity + getRange + getDamage + getFreezeTime)
        if (path.contains("ray_of_frost")) {
            return fireRayOfFrost(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 2. Soulfire Ray (Iron's Spells: SoulfireRaySpell + SoulfireRayParticleOptions + getRange + getDamage + getFreezeTime)
        if (path.contains("soulfire_ray")) {
            return fireSoulfireRay(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 3. Ray of Siphoning (Iron's Spells: RayOfSiphoningSpell + getTickDamage + getRange + BloodSiphonParticlesPacket)
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            return fireRayOfSiphoning(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 4. Eldritch Blast (Iron's Spells: EldritchBlastVisualEntity + getDamage + getRange)
        if (path.contains("eldritch_blast")) {
            return fireEldritchBlast(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 5. Sonic Boom (Iron's Spells: SonicBoomSpell + getDamage + getRange + knockback)
        if (path.contains("sonic_boom")) {
            return fireSonicBoom(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 6. Gust (Iron's Spells: GustCollider + getRange + getStrength + getAmplifier)
        if (path.contains("gust")) {
            return fireGust(level, shooter, origin, direction, spell, spellLevel);
        }

        // 7. Electrocute (Iron's Spells: ElectrocuteProjectile + getDamage)
        if (path.contains("electrocute")) {
            return fireElectrocute(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 8. Cone of Cold (Iron's Spells: ConeOfColdProjectile + getDamage)
        if (path.contains("cone_of_cold")) {
            return fireConeOfCold(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 9. Fire Breath (Iron's Spells: FireBreathProjectile + getDamage)
        if (path.contains("fire_breath")) {
            return fireFireBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 10. Dragon Breath (Iron's Spells: DragonBreathProjectile + getDamage)
        if (path.contains("dragon_breath")) {
            return fireDragonBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 11. Poison Breath (Iron's Spells: PoisonBreathProjectile + getDamage)
        if (path.contains("poison_breath")) {
            return firePoisonBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        return false;
    }

    // ==========================================
    // RAY & BEAM SPELLS (Authentic Iron's Spells)
    // ==========================================

    private static boolean fireRayOfFrost(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        Vec3 eyePos = (shooter != null) ? shooter.getEyePosition() : origin;
        float range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 30.0f);
        Vec3 targetPos = origin.add(direction.scale(range));

        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.3f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = (hitResult.getType() == HitResult.Type.ENTITY) ? ((EntityHitResult) hitResult).getEntity() : null;

        RayOfFrostVisualEntity frostBeam = new RayOfFrostVisualEntity(level, eyePos, hitPos, shooter);
        level.addFreshEntity(frostBeam);
        ActiveSpellEntityManager.registerEntity(shooter, frostBeam, 15);

        MagicManager.spawnParticles(level, ParticleHelper.ICY_FOG, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.1, 0.1, 0.05, true);
        MagicManager.spawnParticles(level, ParticleHelper.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 50, 0.25, 0.25, 0.25, 0.08, false);

        if (hitEntity != null) {
            Integer freezeTime = SpellProjectileFactory.invokeSpellInt(spell, "getFreezeTime", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            int freezeTicks = (freezeTime != null) ? freezeTime : 100;

            DamageSource src = getAuthenticDamageSource(level, frostBeam, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.setFreezeTicks(freezeTicks);
            }
            DamageSources.applyDamage(hitEntity, damage, src);
        }

        return true;
    }

    private static boolean fireSoulfireRay(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        float range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 25.0f);
        Vec3 targetPos = origin.add(direction.scale(range));

        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.35f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = (hitResult.getType() == HitResult.Type.ENTITY) ? ((EntityHitResult) hitResult).getEntity() : null;

        double distance = origin.distanceTo(hitPos);
        for (double d = 0.5; d < distance; d += 0.5) {
            Vec3 pt = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, new SoulfireRayParticleOptions(hitPos), pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0, true);
            MagicManager.spawnParticles(level, ParticleHelper.FIRE_EMITTER, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.02, true);
        }

        MagicManager.spawnParticles(level, ParticleHelper.FIERY_SPARKS, hitPos.x, hitPos.y, hitPos.z, 30, 0.25, 0.25, 0.25, 0.08, true);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f);

        if (hitEntity != null) {
            Integer freezeTime = SpellProjectileFactory.invokeSpellInt(spell, "getFreezeTime", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            int freezeTicks = (freezeTime != null) ? freezeTime : 60;

            DamageSource src = getAuthenticDamageSource(level, shooter, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.setFreezeTicks(freezeTicks);
            }
            DamageSources.applyDamage(hitEntity, damage, src);
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
        float range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 15.0f);
        Vec3 targetPos = origin.add(direction.scale(range));
        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.35f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = (hitResult.getType() == HitResult.Type.ENTITY) ? ((EntityHitResult) hitResult).getEntity() : null;

        if (hitEntity != null) {
            hitPos = hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0);
        }

        Vec3 shooterCenter = (shooter != null) ? shooter.position().add(0, shooter.getBbHeight() * 0.5, 0) : origin;
        if (shooter != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, origin));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, shooterCenter));
        }

        double distance = origin.distanceTo(hitPos);
        for (double d = 0.25; d < distance; d += 0.25) {
            Vec3 pt = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, ParticleHelper.SIPHON, pt.x, pt.y, pt.z, 2, 0.02, 0.02, 0.02, 0.01, true);
            MagicManager.spawnParticles(level, ParticleHelper.BLOOD, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.01, true);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.RAY_OF_SIPHONING.get(), SoundSource.PLAYERS, 1.6f, 1.0f);

        if (hitEntity != null) {
            DamageSource src = getAuthenticDamageSource(level, shooter, shooter, spell);
            // RayOfSiphoningSpell has LifestealPercent = 1.0f configured on its SpellDamageSource!
            DamageSources.applyDamage(hitEntity, damage, src);
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
        Vec3 eyePos = (shooter != null) ? shooter.getEyePosition() : origin;
        float range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 30.0f);
        Vec3 targetPos = origin.add(direction.scale(range));
        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.3f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = (hitResult.getType() == HitResult.Type.ENTITY) ? ((EntityHitResult) hitResult).getEntity() : null;

        EldritchBlastVisualEntity blast = new EldritchBlastVisualEntity(level, eyePos, hitPos, shooter);
        level.addFreshEntity(blast);
        ActiveSpellEntityManager.registerEntity(shooter, blast, 15);

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.ELDRITCH_BLAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        MagicManager.spawnParticles(level, ParticleHelper.UNSTABLE_ENDER, hitPos.x, hitPos.y, hitPos.z, 50, 0.25, 0.25, 0.25, 0.15, false);

        if (hitEntity != null) {
            DamageSource src = getAuthenticDamageSource(level, blast, shooter, spell);
            DamageSources.applyDamage(hitEntity, damage, src);
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
        float maxDist = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 25.0f);
        Vec3 endPos = origin.add(direction.scale(maxDist));

        for (double d = 1.0; d <= maxDist; d += 1.0) {
            Vec3 p = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0, false);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.SONIC_BOOM.get(), SoundSource.PLAYERS, 3.0f, 1.0f);

        HitResult hitResult = Utils.raycastForEntity(level, shooter, origin, endPos, false, 1.0f, e -> e != shooter && e instanceof LivingEntity);
        if (hitResult.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hitResult).getEntity() instanceof LivingEntity target) {
            DamageSource src = getAuthenticDamageSource(level, shooter, shooter, spell);
            DamageSources.applyDamage(target, damage, src);
            Vec3 knockback = direction.normalize().scale(0.75).add(0, 0.25, 0);
            target.setDeltaMovement(target.getDeltaMovement().add(knockback));
            target.hurtMarked = true;
        }

        return true;
    }

    // ==========================================
    // SPRAY & CONE SPELLS (Authentic Iron's Spells)
    // ==========================================

    private static boolean fireConeOfCold(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        ConeOfColdProjectile cone = new ConeOfColdProjectile(level, shooter);
        configureAndSpawnCone(level, shooter, origin, direction, spell, damage, cone, ParticleHelper.SNOW_DUST, ParticleHelper.SNOWFLAKE);
        return true;
    }

    private static boolean fireFireBreath(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        FireBreathProjectile breath = new FireBreathProjectile(level, shooter);
        configureAndSpawnCone(level, shooter, origin, direction, spell, damage, breath, ParticleHelper.FIRE, ParticleHelper.FIERY_SPARKS);
        return true;
    }

    private static boolean fireDragonBreath(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        DragonBreathProjectile breath = new DragonBreathProjectile(level, shooter);
        configureAndSpawnCone(level, shooter, origin, direction, spell, damage, breath, ParticleTypes.DRAGON_BREATH, ParticleHelper.UNSTABLE_ENDER);
        return true;
    }

    private static boolean fireElectrocute(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel,
            float damage
    ) {
        ElectrocuteProjectile projectile = new ElectrocuteProjectile(level, shooter);
        configureAndSpawnCone(level, shooter, origin, direction, spell, damage, projectile, ParticleHelper.ELECTRICITY, ParticleHelper.ELECTRIC_SPARKS);
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
        PoisonBreathProjectile breath = new PoisonBreathProjectile(level, shooter);
        configureAndSpawnCone(level, shooter, origin, direction, spell, damage, breath, ParticleHelper.ACID, ParticleHelper.POISON_CLOUD);
        return true;
    }

    private static void configureAndSpawnCone(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            float damage,
            AbstractConeProjectile cone,
            ParticleOptions primaryParticle,
            ParticleOptions secondaryParticle
    ) {
        cone.setPos(origin.x, origin.y, origin.z);
        cone.setDamage(damage);
        cone.setDealDamageActive();
        level.addFreshEntity(cone);
        ActiveSpellEntityManager.registerEntity(shooter, cone, 10);

        // Trigger the cone entity's native dealDamage() + spawnConeParticles()!
        cone.tick();
        spawnConeParticles(level, origin, direction, 12.0, primaryParticle, secondaryParticle);

        // Also sweep cone area with authentic SpellDamageSource so fast single-click shots reliably apply the cone's damage and effects
        sweepConeArea(level, shooter, origin, direction, 12.0, 22.0, target -> {
            DamageSource src = getAuthenticDamageSource(level, cone, shooter, spell);
            DamageSources.applyDamage(target, damage, src);
            if (cone instanceof PoisonBreathProjectile) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
        });
    }

    private static boolean fireGust(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            AbstractSpell spell,
            int spellLevel
    ) {
        float range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 12.0f);
        Float strengthVal = SpellProjectileFactory.invokeSpellFloat(spell, "getStrength", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
        float strength = (strengthVal != null) ? strengthVal : 1.5f;
        int amplifier = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, spellLevel - 1);

        GustCollider gust = new GustCollider(level, shooter);
        gust.setPos(origin.x, origin.y, origin.z);
        gust.range = range;
        gust.strength = strength;
        gust.amplifier = amplifier;
        level.addFreshEntity(gust);
        gust.setDealDamageActive();
        gust.tick();
        ActiveSpellEntityManager.registerEntity(shooter, gust, 10);

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.GUST_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
        return true;
    }

    // ==========================================
    // HELPER & UTILITY METHODS
    // ==========================================

    private static DamageSource getAuthenticDamageSource(ServerLevel level, Entity directEntity, LivingEntity shooter, AbstractSpell spell) {
        Entity sourceEntity = (directEntity != null) ? directEntity : shooter;
        if (sourceEntity != null) {
            return spell.getDamageSource(sourceEntity, shooter);
        }
        return level.damageSources().magic();
    }

    private static void sweepConeArea(
            ServerLevel level,
            LivingEntity shooter,
            Vec3 origin,
            Vec3 direction,
            double maxRange,
            double halfAngleDegrees,
            Consumer<LivingEntity> onHit
    ) {
        double cosThreshold = Math.cos(Math.toRadians(halfAngleDegrees));
        AABB searchBox = new AABB(origin, origin).inflate(maxRange);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != shooter && e.isAlive());

        Vec3 normDir = direction.normalize();

        for (LivingEntity entity : candidates) {
            Vec3 targetCenter = entity.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(origin);
            double dist = toTarget.length();
            if (dist > maxRange || dist < 0.05) continue;

            double dot = toTarget.normalize().dot(normDir);
            if (dot >= cosThreshold || dist <= 2.0) {
                if (Utils.hasLineOfSight(level, origin, targetCenter, true)) {
                    onHit.accept(entity);
                }
            }
        }
    }

    private static void spawnConeParticles(
            ServerLevel level,
            Vec3 origin,
            Vec3 direction,
            double maxRange,
            ParticleOptions primaryParticle,
            ParticleOptions secondaryParticle
    ) {
        Vec3 normDir = direction.normalize();
        for (double d = 1.0; d <= maxRange; d += 1.5) {
            Vec3 center = origin.add(normDir.scale(d));
            double spread = d * 0.25;
            MagicManager.spawnParticles(level, primaryParticle, center.x, center.y, center.z, 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.03, true);
            if (secondaryParticle != null) {
                MagicManager.spawnParticles(level, secondaryParticle, center.x, center.y, center.z, 2, spread * 0.35, spread * 0.35, spread * 0.35, 0.05, true);
            }
        }
    }
}
