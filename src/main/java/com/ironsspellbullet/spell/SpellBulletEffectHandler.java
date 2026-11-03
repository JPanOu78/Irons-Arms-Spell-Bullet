package com.ironsspellbullet.spell;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.SummonedHorse;
import io.redspace.ironsspellbooks.entity.mobs.SummonedPolarBear;
import io.redspace.ironsspellbooks.entity.mobs.SummonedVex;
import io.redspace.ironsspellbooks.entity.mobs.SummonedZombie;
import io.redspace.ironsspellbooks.entity.spells.BlizzardAoe;
import io.redspace.ironsspellbooks.entity.spells.EarthquakeAoe;
import io.redspace.ironsspellbooks.entity.spells.ExtendedEvokerFang;
import io.redspace.ironsspellbooks.entity.spells.HealingAoe;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import io.redspace.ironsspellbooks.entity.spells.devour_jaw.DevourJaw;
import io.redspace.ironsspellbooks.entity.spells.ice_spike.IceSpikeEntity;
import io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity;
import io.redspace.ironsspellbooks.entity.spells.poison_cloud.PoisonCloud;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.entity.spells.scapegoat.ScapegoatEntity;
import io.redspace.ironsspellbooks.entity.spells.spectral_hammer.SpectralHammer;
import io.redspace.ironsspellbooks.entity.spells.sunbeam.SunbeamEntity;
import io.redspace.ironsspellbooks.entity.spells.void_tentacle.VoidTentacle;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.entity.spells.wisp.WispEntity;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the impact of a Spell Bullet, executing the spell's effect instantly at the target location.
 * Fully supports all 9 schools, authentic entities, tactical warps, stimulus darts, and drop pods!
 */
public class SpellBulletEffectHandler {

    private static final Set<UUID> TRIGGERED_VOLLEYS = Collections.newSetFromMap(
            new java.util.LinkedHashMap<UUID, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > 500;
                }
            }
    );

    public static boolean checkAndMarkVolley(UUID shotId) {
        if (shotId == null) return true;
        return TRIGGERED_VOLLEYS.add(shotId);
    }

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
        String path = spell.getSpellResource().getPath().toLowerCase();

        // Calculate power & base damage
        float spellPower = spell.getSpellPower(spellLevel, shooter);
        float damage = Math.max(8.0f, spellPower * 2.8f);

        // Execute school-specific behavior
        if (school.equals(SchoolRegistry.FIRE.get())) {
            handleFireImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage, hitResult);
        } else if (school.equals(SchoolRegistry.ICE.get())) {
            handleIceImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage, hitResult);
        } else if (school.equals(SchoolRegistry.LIGHTNING.get())) {
            handleLightningImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.HOLY.get())) {
            handleHolyImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.ENDER.get())) {
            handleEnderImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.BLOOD.get())) {
            handleBloodImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.EVOCATION.get())) {
            handleEvocationImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage, hitResult);
        } else if (school.equals(SchoolRegistry.NATURE.get())) {
            handleNatureImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else if (school.equals(SchoolRegistry.ELDRITCH.get())) {
            handleEldritchImpact(serverLevel, hitPos, hitEntity, bullet, shooter, spell, path, spellLevel, damage);
        } else {
            // Default generic spell damage fallback
            if (hitEntity instanceof LivingEntity livingTarget) {
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                livingTarget.hurt(source, damage);
            }
        }
    }

    private static void handleFireImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        // 1. Tactical Barricade: Wall of Fire
        if (path.contains("wall_of_fire")) {
            Vec3 normal = (shooter != null) ? shooter.getLookAngle().cross(new Vec3(0, 1, 0)).normalize() : new Vec3(1, 0, 0);
            Vec3 p1 = hitPos.subtract(normal.scale(3.0));
            Vec3 p2 = hitPos.add(normal.scale(3.0));
            WallOfFireEntity wall = new WallOfFireEntity(level, shooter, List.of(p1, hitPos, p2), damage);
            wall.setPos(hitPos);
            level.addFreshEntity(wall);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.0f);
            return;
        }

        // 2. Tactical Mobility: Burning Dash
        if (path.contains("burning_dash") && shooter != null) {
            shooter.teleportTo(hitPos.x, hitPos.y + 0.2, hitPos.z);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f);
            level.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y + 1, hitPos.z, 35, 0.5, 0.5, 0.5, 0.1);
        }

        float radius = 1.8f + spellLevel * 0.4f;
        level.explode(bullet, hitPos.x, hitPos.y, hitPos.z, radius, Level.ExplosionInteraction.NONE);

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setFireTicks(40 + spellLevel * 20);
            target.hurt(source, damage);
            target.igniteForSeconds(3 + spellLevel * 2);
        }

        // Area ignite
        AABB aabb = new AABB(hitPos.x - radius, hitPos.y - 1, hitPos.z - radius, hitPos.x + radius, hitPos.y + 2, hitPos.z + radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        for (LivingEntity entity : nearby) {
            entity.hurt(SpellDamageSource.source(bullet, shooter, spell).setFireTicks(40), damage * 0.6f);
            entity.igniteForSeconds(3 + spellLevel);
        }

        level.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
        level.sendParticles(ParticleTypes.LAVA, hitPos.x, hitPos.y, hitPos.z, 10, 0.2, 0.2, 0.2, 0.1);
    }

    private static void handleIceImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        int freezeTicks = 120 + spellLevel * 60;

        // 1. Tactical Mobility: Frost Step (Blink to hit point with freezing shockwave)
        if (path.contains("frost_step") && shooter != null) {
            shooter.teleportTo(hitPos.x, hitPos.y + 0.2, hitPos.z);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.FROST_STEP.get(), SoundSource.PLAYERS, 1.5f, 1.2f);
            level.sendParticles(ParticleHelper.ICY_FOG, hitPos.x, hitPos.y + 1, hitPos.z, 35, 0.6, 0.5, 0.6, 0.05);
        }

        // 2. Minion Drop Pod: Summon Polar Bear
        if (path.contains("summon_polar_bear")) {
            SummonedPolarBear bear = new SummonedPolarBear(level, shooter);
            bear.moveTo(hitPos);
            level.addFreshEntity(bear);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.5f, 1.0f);
            level.sendParticles(ParticleHelper.SNOWFLAKE, hitPos.x, hitPos.y + 1, hitPos.z, 30, 0.5, 0.5, 0.5, 0.05);
            return;
        }

        // 3. Ice Tomb Entity
        if (path.contains("ice_tomb") && hitEntity instanceof LivingEntity target) {
            IceTombEntity tomb = new IceTombEntity(level, target);
            tomb.moveTo(target.position());
            tomb.setLifetime(80 + spellLevel * 20);
            tomb.setHealing(damage * 0.5f);
            level.addFreshEntity(tomb);
            target.startRiding(tomb, true);
            return;
        }

        // 4. Blizzard AoE
        if (path.contains("blizzard")) {
            BlizzardAoe blizzard = new BlizzardAoe(EntityRegistry.BLIZZARD_AOE.get(), level);
            blizzard.setOwner(shooter);
            blizzard.setRadius(3.5f + spellLevel * 0.5f);
            blizzard.setDuration(120 + spellLevel * 40);
            blizzard.setDamage(damage * 0.4f);
            blizzard.setPos(hitPos);
            level.addFreshEntity(blizzard);
        } else if (path.contains("ice_spikes")) {
            IceSpikeEntity spike = new IceSpikeEntity(level, shooter);
            spike.setPos(hitPos);
            spike.setDamage(damage);
            level.addFreshEntity(spike);
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setFreezeTicks(freezeTicks);
            target.hurt(source, damage);
            Utils.addFreezeTicks(target, freezeTicks);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80 + spellLevel * 20, 1));
        }

        // Frost AOE shards
        AABB aabb = new AABB(hitPos.x - 3.5, hitPos.y - 2, hitPos.z - 3.5, hitPos.x + 3.5, hitPos.y + 2, hitPos.z + 3.5);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        for (LivingEntity entity : nearby) {
            entity.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.5f);
            Utils.addFreezeTicks(entity, freezeTicks / 2);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
        }

        level.sendParticles(ParticleTypes.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 35, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f);
    }

    private static void handleLightningImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // If Lightning Bolt or Thunderstorm, spawn authentic Lightning Bolt entity
        if (path.contains("lightning_bolt") || path.contains("thunderstorm") || path.contains("volt_strike")) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(hitPos);
                if (shooter instanceof ServerPlayer sp) {
                    bolt.setCause(sp);
                }
                level.addFreshEntity(bolt);
            }
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // Chain lightning to nearby enemies
        AABB aabb = new AABB(hitPos.x - 6, hitPos.y - 3, hitPos.z - 6, hitPos.x + 6, hitPos.y + 3, hitPos.z + 6);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        int chainsLeft = 2 + spellLevel;
        for (LivingEntity chainTarget : nearby) {
            if (chainsLeft-- <= 0) break;
            chainTarget.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.7f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chainTarget.getX(), chainTarget.getY() + 1, chainTarget.getZ(), 15, 0.2, 0.2, 0.2, 0.1);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitPos.x, hitPos.y, hitPos.z, 35, 0.5, 0.5, 0.5, 0.15);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.8f);
    }

    private static void handleHolyImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        float healAmount = 6.0f + spellLevel * 3.0f;

        // 1. Stim / Support Dart (Ranged Ally Buffing)
        boolean isAlly = (hitEntity instanceof LivingEntity target && (target == shooter || target.isAlliedTo(shooter)));
        if (isAlly) {
            LivingEntity ally = (LivingEntity) hitEntity;
            if (path.contains("haste")) {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300 + spellLevel * 60, 1));
            } else if (path.contains("fortify")) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300 + spellLevel * 60, 1));
            } else if (path.contains("angel_wings")) {
                ally.addEffect(new MobEffectInstance(MobEffectRegistry.ANGEL_WINGS, 200 + spellLevel * 60, 0));
            } else if (path.contains("cleanse")) {
                ally.removeAllEffects();
                level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.CLEANSE_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
            } else {
                ally.heal(healAmount);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100 + spellLevel * 20, 1));
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1, ally.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            return;
        }

        if (path.contains("cloud_of_regeneration") || path.contains("healing_circle")) {
            HealingAoe aoe = new HealingAoe(level);
            aoe.setOwner(shooter);
            aoe.setRadius(2.5f + spellLevel * 0.5f);
            aoe.setDuration(120 + spellLevel * 40);
            aoe.setPos(hitPos);
            level.addFreshEntity(aoe);
        } else if (path.contains("sunbeam")) {
            Vec3 strikePos = (hitEntity != null) ? hitEntity.position() : Utils.moveToRelativeGroundLevel(level, hitPos, 3, 18);
            SunbeamEntity sunbeam = new SunbeamEntity(level);
            sunbeam.setOwner(shooter);
            sunbeam.moveTo(strikePos);
            sunbeam.setDamage(damage);
            level.addFreshEntity(sunbeam);
            level.playSound(null, sunbeam.blockPosition(), SoundRegistry.SUNBEAM_WINDUP.get(), SoundSource.NEUTRAL, 3.5f, 1.0f);
        } else if (path.contains("wisp")) {
            WispEntity wisp = new WispEntity(level, shooter, damage * 0.4f);
            wisp.setPos(hitPos.x, hitPos.y + 1.0, hitPos.z);
            level.addFreshEntity(wisp);
        }

        if (hitEntity instanceof LivingEntity target) {
            if (target.isInvertedHealAndHarm()) {
                // Undead smite
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                target.hurt(source, damage * 1.6f);
                target.igniteForSeconds(4 + spellLevel);
            } else {
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
                target.hurt(source, damage);
            }
        } else if (shooter != null && hitPos.distanceTo(shooter.position()) < 4.0) {
            shooter.heal(healAmount);
            shooter.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100 + spellLevel * 20, 1));
        }

        level.sendParticles(ParticleTypes.WAX_ON, hitPos.x, hitPos.y, hitPos.z, 30, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 2.0f);
    }

    private static void handleEnderImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Tactical Mobility: Teleport Bullet
        if (path.contains("teleport") && shooter != null) {
            shooter.teleportTo(hitPos.x, hitPos.y + 0.1, hitPos.z);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.ABYSSAL_TELEPORT.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
            level.sendParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y + 1, hitPos.z, 40, 0.5, 1.0, 0.5, 0.2);
            return;
        }

        // 2. Anti-Magic / Dispel Bullet: Counterspell
        if (path.contains("counterspell") && hitEntity instanceof LivingEntity target) {
            target.removeAllEffects();
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.5f, 1.4f);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y + 1, hitPos.z, 30, 0.4, 0.4, 0.4, 0.1);
        }

        // 3. Black Hole Entity
        if (path.contains("black_hole")) {
            BlackHole blackHole = new BlackHole(level, shooter);
            blackHole.setPos(hitPos);
            blackHole.setRadius(2.5f + spellLevel * 0.5f);
            blackHole.setDamage(damage * 0.5f);
            level.addFreshEntity(blackHole);
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // Gravity pull to hit pos for ender impact
        AABB aabb = new AABB(hitPos.x - 5, hitPos.y - 3, hitPos.z - 5, hitPos.x + 5, hitPos.y + 3, hitPos.z + 5);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter && e != hitEntity);
        for (LivingEntity entity : nearby) {
            Vec3 pull = hitPos.subtract(entity.position()).normalize().scale(0.45);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull));
            entity.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.4f);
        }

        level.sendParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y, hitPos.z, 35, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static void handleBloodImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Tactical Mobility: Blood Step (Blink + Blood Burst)
        if (path.contains("blood_step") && shooter != null) {
            shooter.teleportTo(hitPos.x, hitPos.y + 0.2, hitPos.z);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.BLOOD_STEP.get(), SoundSource.PLAYERS, 1.5f, 1.2f);
            level.sendParticles(ParticleHelper.BLOOD, hitPos.x, hitPos.y + 1, hitPos.z, 35, 0.5, 0.5, 0.5, 0.1);
        }

        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            if (hitEntity instanceof LivingEntity target) {
                SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setLifestealPercent(1.0f);
                target.hurt(source, damage);
                float healAmount = Math.max(4.0f, damage * 0.5f);
                if (shooter != null) {
                    shooter.heal(healAmount);
                    try {
                        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
                        Vec3 shooterCenter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0);
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(targetCenter, shooterCenter));
                    } catch (Throwable ignored) {}
                }
            }
            level.sendParticles(ParticleHelper.SIPHON, hitPos.x, hitPos.y + 1, hitPos.z, 30, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 1.3f);
            return;
        }

        if (path.contains("devour") && hitEntity instanceof LivingEntity target) {
            DevourJaw jaw = new DevourJaw(level, shooter, target);
            jaw.setDamage(damage * 1.2f);
            jaw.setPos(hitPos);
            level.addFreshEntity(jaw);
        } else if (path.contains("raise_dead")) {
            SummonedZombie zombie = new SummonedZombie(level, shooter, true);
            zombie.setPos(hitPos.x, hitPos.y, hitPos.z);
            level.addFreshEntity(zombie);
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell).setLifestealPercent(0.4f);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80 + spellLevel * 20, 0));

            // Life leech to shooter
            if (shooter != null) {
                shooter.heal(damage * 0.35f);
            }
        }

        level.sendParticles(ParticleTypes.CRIMSON_SPORE, hitPos.x, hitPos.y, hitPos.z, 30, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static void handleEvocationImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        // 1. Minion Drop Pod: Summon Vex
        if (path.contains("summon_vex")) {
            SummonedVex vex = new SummonedVex(level, shooter);
            vex.setPos(hitPos.x, hitPos.y + 1, hitPos.z);
            level.addFreshEntity(vex);
        }

        // 2. Minion Drop Pod: Summon Horse (Spectral Steed)
        if (path.contains("summon_horse")) {
            SummonedHorse horse = new SummonedHorse(level, shooter);
            horse.moveTo(hitPos);
            level.addFreshEntity(horse);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.HORSE_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.0f);
            level.sendParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y + 1, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
            return;
        }

        // 3. Decoy: Scapegoat Entity
        if (path.contains("scapegoat")) {
            ScapegoatEntity goat = new ScapegoatEntity(level);
            goat.moveTo(hitPos);
            goat.setOwner(shooter);
            goat.setDurationRemaining(300);
            level.addFreshEntity(goat);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.0f);
            Utils.performTaunt(goat, 14.0f, Utils.tauntPredicate(shooter));
            return;
        }

        // 4. Spectral Hammer Entity
        if (path.contains("spectral_hammer")) {
            BlockHitResult bhr = (hitResult instanceof BlockHitResult r) ? r : new BlockHitResult(hitPos, Direction.UP, BlockPos.containing(hitPos), false);
            SpectralHammer hammer = new SpectralHammer(level, shooter, bhr, spellLevel, (int) damage);
            hammer.moveTo(hitPos);
            level.addFreshEntity(hammer);
            return;
        }

        // 5. Wololo
        if (path.contains("wololo")) {
            if (hitEntity instanceof Sheep sheep) {
                sheep.setColor(DyeColor.byId(level.random.nextInt(16)));
            }
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 2.0f, 1.0f);
            level.sendParticles(ParticleTypes.ENCHANT, hitPos.x, hitPos.y + 1, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1);
        }

        // 6. Stim Dart: Invisibility on Ally
        if (path.contains("invisibility") && hitEntity instanceof LivingEntity ally) {
            ally.addEffect(new MobEffectInstance(MobEffectRegistry.TRUE_INVISIBILITY, 300 + spellLevel * 60, 0));
            level.sendParticles(ParticleTypes.SQUID_INK, ally.getX(), ally.getY() + 1, ally.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            return;
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
        }

        // Summon Evoker Fangs in a ring around hit point
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0;
            double fx = hitPos.x + Math.cos(angle) * 1.5;
            double fz = hitPos.z + Math.sin(angle) * 1.5;
            ExtendedEvokerFang fangs = new ExtendedEvokerFang(level, fx, hitPos.y, fz, (float) angle, 1, shooter, damage * 0.75f);
            level.addFreshEntity(fangs);
        }

        level.sendParticles(ParticleTypes.ENCHANT, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static void handleNatureImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Tactical Mining Bullet: Touch Dig
        if (path.contains("touch_dig")) {
            BlockPos pos = BlockPos.containing(hitPos);
            if (level.getBlockState(pos).isAir()) pos = pos.relative(Direction.DOWN);
            level.destroyBlock(pos, true, shooter);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.TOUCH_DIG_CAST.get(), SoundSource.PLAYERS, 1.4f, 1.0f);
            level.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.1);
            return;
        }

        // 2. Stim Darts: Oakskin and Spider Aspect on Ally
        if (hitEntity instanceof LivingEntity ally && (ally == shooter || ally.isAlliedTo(shooter))) {
            if (path.contains("oakskin")) {
                ally.addEffect(new MobEffectInstance(MobEffectRegistry.OAKSKIN, 300 + spellLevel * 60, 0));
                level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundRegistry.OAKSKIN_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1, ally.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
                return;
            } else if (path.contains("spider_aspect")) {
                ally.addEffect(new MobEffectInstance(MobEffectRegistry.SPIDER_ASPECT, 300 + spellLevel * 60, 0));
                level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundRegistry.SPIDER_ASPECT_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                return;
            }
        }

        if (path.contains("earthquake")) {
            EarthquakeAoe aoe = new EarthquakeAoe(level);
            aoe.setOwner(shooter);
            aoe.setRadius(3.0f + spellLevel * 0.6f);
            aoe.setDuration(100 + spellLevel * 30);
            aoe.setDamage(damage * 0.5f);
            aoe.setSlownessAmplifier(Math.min(2, spellLevel - 1));
            aoe.setPos(hitPos);
            level.addFreshEntity(aoe);
        } else if (path.contains("root")) {
            RootEntity root = new RootEntity(level, shooter);
            root.setPos(hitPos);
            if (hitEntity instanceof LivingEntity target) {
                root.setTarget(target);
            }
            level.addFreshEntity(root);
        } else {
            // Spawn Poison Cloud
            PoisonCloud cloud = new PoisonCloud(level);
            cloud.setOwner(shooter);
            cloud.setDuration(100 + spellLevel * 40);
            cloud.setRadius(2.2f + spellLevel * 0.5f);
            cloud.setPos(hitPos);
            level.addFreshEntity(cloud);
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 120 + spellLevel * 40, Math.min(2, spellLevel - 1)));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80 + spellLevel * 20, 1));
        }

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    private static void handleEldritchImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        if (path.contains("sculk_tentacles")) {
            VoidTentacle tentacle = new VoidTentacle(level, shooter, damage);
            tentacle.setPos(hitPos);
            level.addFreshEntity(tentacle);
        } else if (path.contains("sonic_boom")) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y + 0.5, hitPos.z, 1, 0, 0, 0, 0);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 1.0f);
        }

        if (hitEntity instanceof LivingEntity target) {
            SpellDamageSource source = SpellDamageSource.source(bullet, shooter, spell);
            target.hurt(source, damage);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100 + spellLevel * 20, 0));
        }

        level.sendParticles(ParticleTypes.SCULK_SOUL, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
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
