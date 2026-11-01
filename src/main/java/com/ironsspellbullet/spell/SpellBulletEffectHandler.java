package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import io.redspace.ironsspellbooks.entity.spells.ice_spike.IceSpikeEntity;
import io.redspace.ironsspellbooks.entity.spells.poison_cloud.PoisonCloud;
import io.redspace.ironsspellbooks.entity.spells.void_tentacle.VoidTentacle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import io.redspace.ironsspellbooks.entity.spells.ExtendedEvokerFang;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Handles the impact of a Spell Bullet, executing the spell's effect instantly at the target location.
 */
public class SpellBulletEffectHandler {

    public static void onSpellBulletHit(Level level, HitResult hitResult, Entity bullet, LivingEntity shooter, SpellData spellData) {
        if (!(level instanceof ServerLevel serverLevel) || spellData == null || spellData.equals(SpellData.EMPTY)) {
            return;
        }

        AbstractSpell spell = spellData.getSpell();
        if (spell == null || spell.equals(SpellRegistry.none())) {
            return;
        }

        int spellLevel = spellData.getLevel();
        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        SchoolType school = spell.getSchoolType();

        // Calculate power & base damage
        float spellPower = spell.getSpellPower(spellLevel, shooter);
        float damage = Math.max(8.0f, spellPower * 2.8f);

        // Execute school-specific behavior
        if (school.equals(SchoolRegistry.FIRE.get())) {
            handleFireImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.ICE.get())) {
            handleIceImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.LIGHTNING.get())) {
            handleLightningImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.HOLY.get())) {
            handleHolyImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.ENDER.get())) {
            handleEnderImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.BLOOD.get())) {
            handleBloodImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.EVOCATION.get())) {
            handleEvocationImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.NATURE.get())) {
            handleNatureImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.ELDRITCH.get())) {
            handleEldritchImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, spellLevel, damage);
        } else {
            // Default generic spell damage fallback
            if (hitEntity instanceof LivingEntity livingTarget) {
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                livingTarget.hurt(source, damage);
            }
        }
    }

    private static void handleFireImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        float radius = 1.5f + spellLevel * 0.4f;
        level.explode(bullet, hitPos.x, hitPos.y, hitPos.z, radius, Level.ExplosionInteraction.NONE);

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setFireTicks(40 + spellLevel * 20);
            target.hurt(source, damage);
            target.igniteForSeconds(3 + spellLevel * 2);
        }

        level.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.LAVA, hitPos.x, hitPos.y, hitPos.z, 8, 0.2, 0.2, 0.2, 0.1);
    }

    private static void handleIceImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        int freezeTicks = 100 + spellLevel * 50;

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setFreezeTicks(freezeTicks);
            target.hurt(source, damage);
            Utils.addFreezeTicks(target, freezeTicks);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + spellLevel * 20, 1));
        }

        // Frost AOE shards
        AABB aabb = new AABB(hitPos.x - 3, hitPos.y - 2, hitPos.z - 3, hitPos.x + 3, hitPos.y + 2, hitPos.z + 3);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        for (LivingEntity entity : nearby) {
            entity.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.5f);
            Utils.addFreezeTicks(entity, freezeTicks / 2);
        }

        level.sendParticles(ParticleTypes.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f);
    }

    private static void handleLightningImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // Chain lightning to nearby enemies
        AABB aabb = new AABB(hitPos.x - 5, hitPos.y - 3, hitPos.z - 5, hitPos.x + 5, hitPos.y + 3, hitPos.z + 5);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        int chainsLeft = 2 + spellLevel;
        for (LivingEntity chainTarget : nearby) {
            if (chainsLeft-- <= 0) break;
            chainTarget.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.7f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chainTarget.getX(), chainTarget.getY() + 1, chainTarget.getZ(), 15, 0.2, 0.2, 0.2, 0.1);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitPos.x, hitPos.y, hitPos.z, 30, 0.4, 0.4, 0.4, 0.15);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.8f);
    }

    private static void handleHolyImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        float healAmount = 5.0f + spellLevel * 2.5f;

        if (hitEntity instanceof LivingEntity target) {
            if (target.isInvertedHealAndHarm()) {
                // Undead smite
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                target.hurt(source, damage * 1.5f);
                target.igniteForSeconds(4 + spellLevel);
            } else if (target == shooter || target.isAlliedTo(shooter)) {
                // Heal ally
                target.heal(healAmount);
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80 + spellLevel * 20, 0));
            } else {
                // Neutral/Enemy living entity
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                target.hurt(source, damage);
            }
        } else if (shooter != null && hitPos.distanceTo(shooter.position()) < 4.0) {
            // Shot at feet / close proximity heals shooter
            shooter.heal(healAmount);
            shooter.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80 + spellLevel * 20, 0));
        }

        level.sendParticles(ParticleTypes.WAX_ON, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 2.0f);
    }

    private static void handleEnderImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // If Black Hole spell, deploy Black Hole entity
        if (spell.getSpellResource().getPath().contains("black_hole")) {
            BlackHole blackHole = new BlackHole(level, shooter);
            blackHole.setPos(hitPos);
            blackHole.setDamage(damage * 0.4f);
            level.addFreshEntity(blackHole);
        }

        level.sendParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static void handleBloodImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setLifestealPercent(0.35f);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60 + spellLevel * 20, 0));

            // Life leech to shooter
            if (shooter != null) {
                shooter.heal(damage * 0.25f);
            }
        }

        level.sendParticles(ParticleTypes.CRIMSON_SPORE, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static void handleEvocationImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // Summon Evoker Fangs in a small ring around hit point
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0;
            double fx = hitPos.x + Math.cos(angle) * 1.5;
            double fz = hitPos.z + Math.sin(angle) * 1.5;
            ExtendedEvokerFang fangs = new ExtendedEvokerFang(level, fx, hitPos.y, fz, (float) angle, 1, shooter, damage * 0.75f);
            level.addFreshEntity(fangs);
        }

        level.sendParticles(ParticleTypes.ENCHANT, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static void handleNatureImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + spellLevel * 40, Math.min(2, spellLevel - 1)));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + spellLevel * 20, 1));
        }

        // Spawn Poison Cloud
        PoisonCloud cloud = new PoisonCloud(level);
        cloud.setOwner(shooter);
        cloud.setDuration(100 + spellLevel * 40);
        cloud.setRadius(2.0f + spellLevel * 0.5f);
        cloud.setPos(hitPos);
        level.addFreshEntity(cloud);

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, hitPos.x, hitPos.y, hitPos.z, 25, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    private static void handleEldritchImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, int spellLevel, float damage) {
        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80 + spellLevel * 20, 0));
        }

        level.sendParticles(ParticleTypes.SCULK_SOUL, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    public static ParticleOptions getTrailParticleForSchool(SchoolType school) {
        if (school == null) return null;
        if (school.equals(SchoolRegistry.FIRE.get())) return ParticleTypes.FLAME;
        if (school.equals(SchoolRegistry.ICE.get())) return ParticleTypes.SNOWFLAKE;
        if (school.equals(SchoolRegistry.LIGHTNING.get())) return ParticleTypes.ELECTRIC_SPARK;
        if (school.equals(SchoolRegistry.HOLY.get())) return ParticleTypes.WAX_ON;
        if (school.equals(SchoolRegistry.ENDER.get())) return ParticleTypes.PORTAL;
        if (school.equals(SchoolRegistry.BLOOD.get())) return ParticleTypes.CRIMSON_SPORE;
        if (school.equals(SchoolRegistry.EVOCATION.get())) return ParticleTypes.ENCHANT;
        if (school.equals(SchoolRegistry.NATURE.get())) return ParticleTypes.SPORE_BLOSSOM_AIR;
        if (school.equals(SchoolRegistry.ELDRITCH.get())) return ParticleTypes.SCULK_SOUL;
        return null;
    }
}
