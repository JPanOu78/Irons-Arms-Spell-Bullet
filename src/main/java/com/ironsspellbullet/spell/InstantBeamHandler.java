package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Fires directly and immediately from the gun muzzle with authentic 3D entities, particles, and sounds.
 * Delivers authentic downrange damage and effects without relying on short-range caster-locked tickers.
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
     * Fires the beam or spray spell directly from the gun muzzle.
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

        // 2. Ray of Siphoning (Iron's Spells: BloodSiphon stream + instant max lifesteal burst)
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

        // 5. Gust (Iron's Spells: GustCollider + cone shockwave push)
        if (path.contains("gust")) {
            return fireGust(level, shooter, origin, direction);
        }

        // 6. Electrocute (Iron's Spells: ElectrocuteProjectile + downrange lightning cone)
        if (path.contains("electrocute")) {
            return fireElectrocute(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 7. Cone of Cold (Iron's Spells: ConeOfColdProjectile + downrange blizzard cone)
        if (path.contains("cone_of_cold")) {
            return fireConeOfCold(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 8. Fire Breath (Iron's Spells: FireBreathProjectile + downrange flamethrower cone)
        if (path.contains("fire_breath")) {
            return fireFireBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 9. Dragon Breath (Iron's Spells: DragonBreathProjectile + downrange dragon breath cone)
        if (path.contains("dragon_breath")) {
            return fireDragonBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        // 10. Poison Breath (Iron's Spells: downrange toxic gas cone)
        if (path.contains("poison_breath")) {
            return firePoisonBreath(level, shooter, origin, direction, spell, spellLevel, baseDamage);
        }

        return false;
    }

    // ==========================================
    // RAY & BEAM SPELLS (Long-range precision)
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
        Vec3 targetPos = origin.add(direction.scale(64.0));

        // Use Iron's Spells native RaycastBuilder with bbInflation for forgiving entity hits
        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.45f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        LivingEntity hitTarget = null;
        if (hitResult.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hitResult).getEntity() instanceof LivingEntity living) {
            hitTarget = living;
        } else {
            hitTarget = findClosestLivingOnSegment(level, shooter, origin, hitPos, 0.75);
        }

        if (hitTarget != null) {
            hitPos = hitTarget.position().add(0, hitTarget.getBbHeight() * 0.5, 0);
        }

        // Spawn authentic 3D visual entity at eye position (as RayOfFrostSpell expects)
        RayOfFrostVisualEntity frostBeam = new RayOfFrostVisualEntity(level, eyePos, hitPos, shooter);
        level.addFreshEntity(frostBeam);

        // High-density crystalline frost laser beam connecting muzzle to target
        double distance = origin.distanceTo(hitPos);
        for (double d = 0.3; d < distance; d += 0.22) {
            Vec3 pt = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, ParticleHelper.ICY_FOG, pt.x, pt.y, pt.z, 2, 0.02, 0.02, 0.02, 0.0, true);
            MagicManager.spawnParticles(level, ParticleHelper.SNOWFLAKE, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.0, true);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 1.4f);
        MagicManager.spawnParticles(level, ParticleHelper.ICY_FOG, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05, true);
        MagicManager.spawnParticles(level, ParticleHelper.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 50, 0.4, 0.4, 0.4, 0.15, true);

        if (hitTarget != null) {
            float power = spell.getSpellPower(spellLevel, shooter);
            float damageToApply = Math.max(damage, 5.0f + power * 1.8f);
            int freezeTicks = hitTarget.getTicksRequiredToFreeze() + (int) (power * 15.0f) + 80;

            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect().setFreezeTicks(freezeTicks);
            }
            DamageSources.applyDamage(hitTarget, damageToApply, src);
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
        Vec3 targetPos = origin.add(direction.scale(40.0));
        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.45f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        LivingEntity hitTarget = null;
        if (hitResult.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hitResult).getEntity() instanceof LivingEntity living) {
            hitTarget = living;
        } else {
            hitTarget = findClosestLivingOnSegment(level, shooter, origin, hitPos, 0.75);
        }

        if (hitTarget != null) {
            hitPos = hitTarget.position().add(0, hitTarget.getBbHeight() * 0.5, 0);
        }

        Vec3 shooterCenter = (shooter != null) ? shooter.position().add(0, shooter.getBbHeight() * 0.5, 0) : origin;
        if (shooter != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, origin));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(hitPos, shooterCenter));
        }

        // Render continuous "เส้น เลเซอร์เลือด" (Solid Blood Laser Beam) using authentic Iron's Spells blood & siphon particles
        double distance = origin.distanceTo(hitPos);
        for (double d = 0.25; d < distance; d += 0.18) {
            Vec3 pt = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, ParticleHelper.SIPHON, pt.x, pt.y, pt.z, 2, 0.02, 0.02, 0.02, 0.01, true);
            MagicManager.spawnParticles(level, ParticleHelper.BLOOD, pt.x, pt.y, pt.z, 2, 0.02, 0.02, 0.02, 0.01, true);
        }

        // Reverse siphon stream flowing back into the gun muzzle
        Vec3 returnDir = origin.subtract(hitPos).normalize();
        for (double d = 0.5; d < distance; d += 0.5) {
            Vec3 pt = hitPos.add(returnDir.scale(d));
            MagicManager.spawnParticles(level, ParticleHelper.BLOOD, pt.x, pt.y, pt.z, 3, returnDir.x * 0.25, returnDir.y * 0.25, returnDir.z * 0.25, 0.12, true);
        }

        // Impact & Muzzle bursts
        MagicManager.spawnParticles(level, ParticleHelper.BLOOD_GROUND, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.05, true);
        MagicManager.spawnParticles(level, ParticleHelper.SIPHON, hitPos.x, hitPos.y, hitPos.z, 20, 0.2, 0.2, 0.2, 0.02, true);
        MagicManager.spawnParticles(level, ParticleHelper.BLOOD, origin.x, origin.y, origin.z, 12, 0.1, 0.1, 0.1, 0.05, true);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 1.4f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.3f, 1.6f);

        // Instant single shot burst: deal maximum siphon damage and apply maximum instant heal!
        float power = spell.getSpellPower(spellLevel, shooter);
        float totalDamage = Math.max(damage * 1.5f, Math.max(18.0f, power * 5.2f));

        if (hitTarget != null) {
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.setLifestealPercent(1.0f).indirect();
            }
            DamageSources.applyDamage(hitTarget, totalDamage, src);
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
        Vec3 eyePos = (shooter != null) ? shooter.getEyePosition() : origin;
        Vec3 targetPos = origin.add(direction.scale(54.0));
        HitResult hitResult = RaycastBuilder.begin(level, shooter)
                .start(origin)
                .end(targetPos)
                .checkForBlocks(true)
                .bbInflation(0.45f)
                .build();

        Vec3 hitPos = hitResult.getLocation();
        LivingEntity hitTarget = null;
        if (hitResult.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hitResult).getEntity() instanceof LivingEntity living) {
            hitTarget = living;
        } else {
            hitTarget = findClosestLivingOnSegment(level, shooter, origin, hitPos, 0.75);
        }

        if (hitTarget != null) {
            hitPos = hitTarget.position().add(0, hitTarget.getBbHeight() * 0.5, 0);
        }

        // 3D EldritchBlastVisualEntity from Iron's Spells
        EldritchBlastVisualEntity blast = new EldritchBlastVisualEntity(level, eyePos, hitPos, shooter);
        level.addFreshEntity(blast);

        // Dense void tendrils laser line
        double distance = origin.distanceTo(hitPos);
        for (double d = 0.4; d < distance; d += 0.25) {
            Vec3 pt = origin.add(direction.scale(d));
            MagicManager.spawnParticles(level, ParticleHelper.VOID_TENTACLE_FOG, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0, true);
            MagicManager.spawnParticles(level, ParticleTypes.SCULK_SOUL, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.02, true);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundRegistry.ELDRITCH_BLAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        MagicManager.spawnParticles(level, ParticleTypes.SCULK_SOUL, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05, true);

        if (hitTarget != null) {
            float power = spell.getSpellPower(spellLevel, shooter);
            float blastDamage = Math.max(damage * 1.3f, 12.0f + power * 2.6f);

            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect();
            }
            DamageSources.applyDamage(hitTarget, blastDamage, src);
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
            MagicManager.spawnParticles(level, ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0, true);
        }

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5f, 1.0f);

        float power = spell.getSpellPower(spellLevel, shooter);
        float sonicDamage = Math.max(damage * 1.5f, 14.0f + power * 2.2f);
        DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
        if (src instanceof SpellDamageSource sds) {
            sds.indirect();
        }

        AABB lineBox = new AABB(origin, endPos).inflate(1.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, lineBox, e -> e != shooter && e.isAlive());
        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (distancePointToSegment(origin, endPos, targetCenter) <= (target.getBbWidth() * 0.5 + 1.2)) {
                DamageSources.applyDamage(target, sonicDamage, src);
                target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(1.2)));
                target.hurtMarked = true;
            }
        }

        return true;
    }

    // ==========================================
    // SPRAY & CONE SPELLS (Downrange area sweeping)
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
        float power = spell.getSpellPower(spellLevel, shooter);
        float coldDamage = Math.max(damage, 8.0f + power * 1.8f);

        // Spawn authentic 3D entity at origin for authentic muzzle effect
        ConeOfColdProjectile cone = new ConeOfColdProjectile(level, shooter);
        cone.setPos(origin);
        cone.setDamage(coldDamage);
        level.addFreshEntity(cone);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.8f, 1.2f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 1.5f);

        // Downrange expanding cone particles
        spawnConeParticles(level, origin, direction, 22.0, ParticleHelper.SNOW_DUST, ParticleHelper.SNOWFLAKE);

        // Sweep targets downrange up to 22 blocks
        sweepConeArea(level, shooter, origin, direction, 22.0, 26.0, target -> {
            int freeze = target.getTicksRequiredToFreeze() + 100 + spellLevel * 40;
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect().setFreezeTicks(freeze);
            }
            DamageSources.applyDamage(target, coldDamage, src);
            Utils.addFreezeTicks(target, freeze);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100 + spellLevel * 20, 2));
        });

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
        float power = spell.getSpellPower(spellLevel, shooter);
        float fireDamage = Math.max(damage, 9.0f + power * 2.0f);

        FireBreathProjectile breath = new FireBreathProjectile(level, shooter);
        breath.setPos(origin);
        breath.setDamage(fireDamage);
        level.addFreshEntity(breath);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 1.1f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 1.3f);

        spawnConeParticles(level, origin, direction, 22.0, ParticleHelper.FIRE, ParticleHelper.FIERY_SPARKS);

        sweepConeArea(level, shooter, origin, direction, 22.0, 26.0, target -> {
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect().setFireTicks(80 + spellLevel * 30);
            }
            DamageSources.applyDamage(target, fireDamage, src);
            target.igniteForSeconds(4 + spellLevel * 2);
        });

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
        float power = spell.getSpellPower(spellLevel, shooter);
        float dragonDamage = Math.max(damage, 10.0f + power * 2.2f);

        DragonBreathProjectile breath = new DragonBreathProjectile(level, shooter);
        breath.setPos(origin);
        breath.setDamage(dragonDamage);
        level.addFreshEntity(breath);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.4f);

        spawnConeParticles(level, origin, direction, 22.0, ParticleTypes.DRAGON_BREATH, ParticleTypes.WITCH);

        sweepConeArea(level, shooter, origin, direction, 22.0, 26.0, target -> {
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect();
            }
            DamageSources.applyDamage(target, dragonDamage, src);
            target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
        });

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
        float power = spell.getSpellPower(spellLevel, shooter);
        float shockDamage = Math.max(damage, 9.0f + power * 2.2f);

        ElectrocuteProjectile projectile = new ElectrocuteProjectile(level, shooter);
        projectile.setPos(origin);
        projectile.setDamage(shockDamage);
        level.addFreshEntity(projectile);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.6f, 1.8f);

        spawnConeParticles(level, origin, direction, 22.0, ParticleHelper.ELECTRICITY, ParticleHelper.ELECTRIC_SPARKS);

        sweepConeArea(level, shooter, origin, direction, 22.0, 26.0, target -> {
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect();
            }
            DamageSources.applyDamage(target, shockDamage, src);
            target.hurtMarked = true;
        });

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
        float power = spell.getSpellPower(spellLevel, shooter);
        float poisonDamage = Math.max(damage, 8.0f + power * 1.8f);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.6f, 1.0f);

        spawnConeParticles(level, origin, direction, 22.0, ParticleHelper.ACID, ParticleTypes.ITEM_SLIME);

        sweepConeArea(level, shooter, origin, direction, 22.0, 26.0, target -> {
            DamageSource src = getSafeSpellDamageSource(level, shooter, spell);
            if (src instanceof SpellDamageSource sds) {
                sds.indirect();
            }
            DamageSources.applyDamage(target, poisonDamage, src);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 140 + spellLevel * 20, 1));
        });

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

        sweepConeArea(level, shooter, origin, direction, 16.0, 30.0, target -> {
            target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(1.4).add(0, 0.25, 0)));
            target.hurtMarked = true;
        });

        return true;
    }

    // ==========================================
    // HELPER & UTILITY METHODS
    // ==========================================

    /**
     * Safely creates a SpellDamageSource without throwing NullPointerException when directEntity/shooter is null.
     */
    private static DamageSource getSafeSpellDamageSource(ServerLevel level, LivingEntity shooter, AbstractSpell spell) {
        if (shooter != null) {
            return spell.getDamageSource(shooter);
        }
        return level.damageSources().magic();
    }

    /**
     * Sweeps all living entities inside a forward-facing cone downrange up to maxRange blocks.
     */
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
            // Hit if inside cone angle OR point-blank within 2.5 blocks
            if (dot >= cosThreshold || dist <= 2.5) {
                // Check line of sight so cone doesn't hit through solid walls
                if (Utils.hasLineOfSight(level, origin, targetCenter, true)) {
                    onHit.accept(entity);
                }
            }
        }
    }

    /**
     * Spawns an expanding authentic 3D cone particle plume along the shooting direction.
     */
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
            double spread = d * 0.28;
            MagicManager.spawnParticles(level, primaryParticle, center.x, center.y, center.z, 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.03, true);
            if (secondaryParticle != null) {
                MagicManager.spawnParticles(level, secondaryParticle, center.x, center.y, center.z, 2, spread * 0.35, spread * 0.35, spread * 0.35, 0.05, true);
            }
        }
    }

    /**
     * Forgiving raycast fallback: finds the closest living entity within a cylinder segment.
     */
    private static LivingEntity findClosestLivingOnSegment(ServerLevel level, LivingEntity shooter, Vec3 start, Vec3 end, double radius) {
        AABB box = new AABB(start, end).inflate(radius + 0.5);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != shooter && e.isAlive());
        LivingEntity closestTarget = null;
        double minDistance = Double.MAX_VALUE;

        for (LivingEntity e : list) {
            Vec3 center = e.getBoundingBox().getCenter();
            double distToSegment = distancePointToSegment(start, end, center);
            double hitRadius = Math.max(e.getBbWidth() * 0.5, 0.4) + radius;
            if (distToSegment <= hitRadius) {
                double distFromStart = start.distanceToSqr(center);
                if (distFromStart < minDistance) {
                    minDistance = distFromStart;
                    closestTarget = e;
                }
            }
        }
        return closestTarget;
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
