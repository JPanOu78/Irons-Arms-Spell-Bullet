package com.ironsspellbullet.spell;

import com.ironsspellbullet.event.ActiveSpellEntityManager;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.CameraShakeData;
import io.redspace.ironsspellbooks.api.util.CameraShakeManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.SummonedHorse;
import io.redspace.ironsspellbooks.entity.mobs.SummonedPolarBear;
import io.redspace.ironsspellbooks.entity.mobs.SummonedSkeleton;
import io.redspace.ironsspellbooks.entity.mobs.SummonedVex;
import io.redspace.ironsspellbooks.entity.mobs.SummonedZombie;
import io.redspace.ironsspellbooks.entity.spells.ArrowVolleyEntity;
import io.redspace.ironsspellbooks.entity.spells.BlizzardAoe;
import io.redspace.ironsspellbooks.entity.spells.ChainLightning;
import io.redspace.ironsspellbooks.entity.spells.EarthquakeAoe;
import io.redspace.ironsspellbooks.entity.spells.ExtendedEvokerFang;
import io.redspace.ironsspellbooks.entity.spells.ExtendedFireworkRocket;
import io.redspace.ironsspellbooks.entity.spells.FangSwirlEntity;
import io.redspace.ironsspellbooks.entity.spells.FireEruptionAoe;
import io.redspace.ironsspellbooks.entity.spells.HealingAoe;
import io.redspace.ironsspellbooks.entity.spells.StompAoe;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.devour_jaw.DevourJaw;
import io.redspace.ironsspellbooks.entity.spells.firefly_swarm.FireflySwarmProjectile;
import io.redspace.ironsspellbooks.entity.spells.ice_block.IceBlockProjectile;
import io.redspace.ironsspellbooks.entity.spells.ice_spike.IceSpikeEntity;
import io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField;
import io.redspace.ironsspellbooks.entity.spells.poison_cloud.PoisonSplash;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.entity.spells.scapegoat.ScapegoatEntity;
import io.redspace.ironsspellbooks.entity.spells.shield.ShieldEntity;
import io.redspace.ironsspellbooks.entity.spells.spectral_hammer.SpectralHammer;
import io.redspace.ironsspellbooks.entity.spells.summoned_weapons.SummonedClaymoreEntity;
import io.redspace.ironsspellbooks.entity.spells.summoned_weapons.SummonedRapierEntity;
import io.redspace.ironsspellbooks.entity.spells.summoned_weapons.SummonedSwordEntity;
import io.redspace.ironsspellbooks.entity.spells.sunbeam.SunbeamEntity;
import io.redspace.ironsspellbooks.entity.spells.target_area.TargetedAreaEntity;
import io.redspace.ironsspellbooks.entity.spells.void_tentacle.VoidTentacle;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.entity.spells.wisp.WispEntity;
import io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket;
import io.redspace.ironsspellbooks.network.particles.FortifyAreaParticlesPacket;
import io.redspace.ironsspellbooks.network.particles.HealParticlesPacket;
import io.redspace.ironsspellbooks.network.particles.RegenCloudParticlesPacket;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.FlameStrikeParticleOptions;
import io.redspace.ironsspellbooks.particle.ZapParticleOption;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.spells.blood.SacrificeSpell;
import io.redspace.ironsspellbooks.spells.evocation.ChainCreeperSpell;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the impact of a Spell Bullet by executing Iron's Spells 'n Spellbooks' own authentic
 * damage calculations, SpellDamageSource, DamageSources.applyDamage, entities, and status effects directly!
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

    /**
     * Strictly checks whether a spell is a mobility, dash, or teleportation spell.
     * These spells are completely forbidden from firearms and spell bullet crafting!
     */
    public static boolean isMobilityOrTeleportSpell(AbstractSpell spell) {
        if (spell == null || spell.equals(SpellRegistry.none())) return false;
        return isMobilityOrTeleportSpell(spell.getSpellResource().getPath());
    }

    public static boolean isMobilityOrTeleportSpell(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.contains("teleport") ||
                p.contains("frost_step") ||
                p.contains("blood_step") ||
                p.contains("burning_dash") ||
                p.contains("dash") ||
                p.contains("charge") ||
                p.contains("ascension") ||
                p.contains("recall") ||
                p.contains("portal") ||
                p.contains("shadow_slash") ||
                p.contains("pocket_dimension") ||
                p.contains("volt_strike");
    }

    /**
     * Checks if the shooter can summon for the given spell.
     * Enforces Iron's Spells' native summon limit: returns false if the shooter already has an active summon recast.
     */
    public static boolean checkCanSummon(LivingEntity shooter, AbstractSpell spell) {
        if (shooter == null || spell == null) return true;
        MagicData magicData = MagicData.getPlayerMagicData(shooter);
        PlayerRecasts playerRecasts = magicData.getPlayerRecasts();
        return !playerRecasts.hasRecastForSpell(spell);
    }

    /**
     * Registers active summoned entities to Iron's Spells' native SummonManager and PlayerRecasts system.
     * Synchronizes the active recast UI (spell icon, remaining recast count, countdown timer)
     * and ensures automatic despawn on death or expiration.
     */
    public static void registerSummonRecast(
            LivingEntity shooter,
            AbstractSpell spell,
            int spellLevel,
            List<? extends Entity> summonedEntities,
            int durationTicks
    ) {
        if (shooter == null || spell == null || summonedEntities == null || summonedEntities.isEmpty()) return;
        MagicData magicData = MagicData.getPlayerMagicData(shooter);
        PlayerRecasts playerRecasts = magicData.getPlayerRecasts();

        SummonedEntitiesCastData summonData = new SummonedEntitiesCastData();
        for (Entity e : summonedEntities) {
            SummonManager.initSummon(shooter, e, durationTicks, summonData);
        }

        int recastCount = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 1);
        RecastInstance recast = new RecastInstance(spell.getSpellId(), spellLevel, recastCount, durationTicks, CastSource.SCROLL, summonData);
        playerRecasts.addRecast(recast, magicData);
    }

    public static void onSpellBulletHit(Level level, HitResult hitResult, Entity bullet, LivingEntity shooter, SpellData spellData) {
        if (!(level instanceof ServerLevel serverLevel) || spellData == null || spellData.equals(SpellData.EMPTY)) {
            return;
        }

        AbstractSpell spell = spellData.getSpell();
        if (spell == null || spell.equals(SpellRegistry.none()) || isMobilityOrTeleportSpell(spell)) {
            return;
        }

        int spellLevel = spellData.getLevel();
        Vec3 hitPos = hitResult.getLocation();
        Entity hitEntity = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        SchoolType school = spell.getSchoolType();
        String path = spell.getSpellResource().getPath().toLowerCase();

        // Compute authentic spell damage directly from Iron's Spells' own methods
        float damage = SpellProjectileFactory.computeAuthenticSpellDamage(spell, spellLevel, shooter);

        // Execute school-specific authentic Iron's Spells behavior
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
            if (hitEntity != null) {
                SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
            }
        }
    }

    // =========================================================================
    // 1. FIRE SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleFireImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        // 1. Wall of Fire (WallOfFireSpell: getWallLength, getDamage, WallOfFireEntity)
        if (path.contains("wall_of_fire")) {
            Float wallLenVal = SpellProjectileFactory.invokeSpellFloat(spell, "getWallLength", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            float halfLen = (wallLenVal != null ? wallLenVal : 6.0f) * 0.5f;
            Vec3 normal = (shooter != null) ? shooter.getLookAngle().cross(new Vec3(0, 1, 0)).normalize() : new Vec3(1, 0, 0);
            Vec3 p1 = Utils.moveToRelativeGroundLevel(level, hitPos.subtract(normal.scale(halfLen)), 4);
            Vec3 pMid = Utils.moveToRelativeGroundLevel(level, hitPos, 4);
            Vec3 p2 = Utils.moveToRelativeGroundLevel(level, hitPos.add(normal.scale(halfLen)), 4);
            WallOfFireEntity wall = new WallOfFireEntity(level, shooter, List.of(p1, pMid, p2), damage);
            wall.setPos(pMid);
            level.addFreshEntity(wall);
            return;
        }

        // 2. Raise Hell (RaiseHellSpell: FireEruptionAoe + getRadius + getDamage + CameraShake + FIRE_ERUPTION_SLAM)
        if (path.contains("raise_hell")) {
            float eruptionRadius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 8.0f);
            Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, hitPos, 4);
            FireEruptionAoe eruption = new FireEruptionAoe(level, eruptionRadius);
            eruption.setOwner(shooter);
            eruption.setDamage(damage);
            eruption.moveTo(groundPos);
            level.addFreshEntity(eruption);

            CameraShakeManager.addCameraShake(
                    new CameraShakeData(
                            level,
                            20 + (int) eruptionRadius,
                            groundPos,
                            eruptionRadius * 2.0f + 5.0f
                    )
            );
            level.playSound(null, groundPos.x, groundPos.y, groundPos.z,
                    SoundRegistry.FIRE_ERUPTION_SLAM.get(), SoundSource.PLAYERS, 2.5f, 1.0f);
            return;
        }

        // 3. Scorch (ScorchSpell: getRadius + getDamage + DamageSources.applyDamage + FireField AOE)
        if (path.contains("scorch")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 2.5f);
            Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, hitPos, 4);

            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.FIRE.get().getTargetingColor(), radius),
                    groundPos.x, groundPos.y + 0.165, groundPos.z, 1, 0, 0, 0, 0, true);
            MagicManager.spawnParticles(level, ParticleTypes.SMOKE, groundPos.x, groundPos.y, groundPos.z, (int) (radius * 10), radius * 0.4, 0.2, radius * 0.4, 0.05, false);
            level.playSound(null, groundPos.x, groundPos.y, groundPos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 1.2f);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(groundPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                if (victim.distanceToSqr(groundPos) <= radius * radius) {
                    SpellProjectileFactory.applyAuthenticSpellDamage(victim, damage, spell, bullet, shooter);
                }
            }

            FireField fire = new FireField(level);
            fire.setOwner(shooter);
            fire.setDuration(100);
            fire.setDamage(damage * 0.1f);
            fire.setRadius(radius);
            fire.setCircular();
            fire.moveTo(groundPos);
            level.addFreshEntity(fire);
            return;
        }

        // 4. Heat Surge (HeatSurgeSpell: getRadius + getDuration + getRendAmplifier + REND effect + DamageSources.applyDamage)
        if (path.contains("heat_surge")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 160);
            int amplifier = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);

            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.FIRE.get().getTargetingColor(), radius),
                    hitPos.x, hitPos.y + 0.165, hitPos.z, 1, 0, 0, 0, 0, true);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.HEAT_SURGE_PREPARE.get(), SoundSource.PLAYERS, 1.8f, 1.0f);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                if (victim.distanceToSqr(hitPos) <= radius * radius) {
                    SpellProjectileFactory.applyAuthenticSpellDamage(victim, damage, spell, bullet, shooter);
                    victim.addEffect(new MobEffectInstance(MobEffectRegistry.REND, duration, amplifier));
                }
            }
            return;
        }

        // 5. Flaming Strike (FlamingStrikeSpell: FlameStrikeParticleOptions + getDamage + DamageSources.applyDamage)
        if (path.contains("flaming_strike")) {
            float radius = 3.25f;
            Vec3 look = (shooter != null) ? shooter.getLookAngle() : new Vec3(1, 0, 0);
            MagicManager.spawnParticles(level, new FlameStrikeParticleOptions((float) look.x, 0.0f, (float) look.z, false, false, 1.0f),
                    hitPos.x, hitPos.y + 0.5, hitPos.z, 1, 0, 0, 0, 0, true);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.FLAMING_STRIKE_SWING.get(), SoundSource.PLAYERS, 1.8f, 1.0f);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                SpellProjectileFactory.applyAuthenticSpellDamage(victim, damage, spell, bullet, shooter);
            }
            return;
        }

        // Default Fire School fallback using authentic SpellDamageSource & DamageSources.applyDamage
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleHelper.FIRE, hitPos.x, hitPos.y, hitPos.z, 20, 0.3, 0.3, 0.3, 0.08, false);
    }

    // =========================================================================
    // 2. ICE SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleIceImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        // 1. Summon Polar Bear (SummonPolarBearSpell: getBearDamage, getBearHealth, SummonManager, RecastInstance)
        if (path.contains("summon_polar_bear")) {
            if (!checkCanSummon(shooter, spell)) {
                return;
            }
            SummonedPolarBear bear = new SummonedPolarBear(level, shooter);
            Float bearHealth = SpellProjectileFactory.invokeSpellFloat(spell, "getBearHealth", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            Float bearDamage = SpellProjectileFactory.invokeSpellFloat(spell, "getBearDamage", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (bearHealth != null && bear.getAttribute(Attributes.MAX_HEALTH) != null) {
                bear.getAttribute(Attributes.MAX_HEALTH).setBaseValue(bearHealth);
                bear.setHealth(bear.getMaxHealth());
            }
            if (bearDamage != null && bear.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                bear.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(bearDamage);
            }
            bear.moveTo(hitPos);
            level.addFreshEntity(bear);
            registerSummonRecast(shooter, spell, spellLevel, List.of(bear), 12000);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.5f, 1.0f);
            return;
        }

        // 2. Ice Tomb (IceTombSpell: getHealing, getDuration, IceTombEntity)
        if (path.contains("ice_tomb")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 100);
                float healing = SpellProjectileFactory.computeAuthenticHealing(spell, spellLevel, shooter);
                IceTombEntity tomb = new IceTombEntity(level, shooter);
                tomb.moveTo(target.position());
                tomb.setLifetime(duration);
                tomb.setHealing(healing);
                level.addFreshEntity(tomb);
                target.startRiding(tomb, true);
            }
            return;
        }

        // 3. Blizzard (BlizzardSpell: getRadius, getDurationTicks, getDamage, BlizzardAoe)
        if (path.contains("blizzard")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 100);
            BlizzardAoe blizzard = new BlizzardAoe(EntityRegistry.BLIZZARD_AOE.get(), level);
            blizzard.setOwner(shooter);
            blizzard.setRadius(radius);
            blizzard.setDuration(duration);
            blizzard.setDamage(damage);
            blizzard.setPos(hitPos.x, hitPos.y + 6.0, hitPos.z);
            level.addFreshEntity(blizzard);
            return;
        }

        // 4. Ice Block (IceBlockSpell: IceBlockProjectile + getDamage)
        if (path.contains("ice_block")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : null;
            Vec3 spawnPos = hitPos.add(0, 5.0, 0);
            IceBlockProjectile iceBlock = new IceBlockProjectile(level, shooter, target);
            iceBlock.moveTo(spawnPos);
            iceBlock.setAirTime(target == null ? 15 : 30);
            iceBlock.setDamage(damage);
            level.addFreshEntity(iceBlock);
            level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundRegistry.ICE_BLOCK_CAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
            return;
        }

        // 5. Ice Spikes (IceSpikesSpell: Option B - authentic distance scaling with lerping size & 4.5f giant finisher spike)
        if (path.contains("ice_spikes") || path.contains("ice_spike")) {
            Vec3 forward = (shooter != null)
                    ? shooter.getForward().multiply(1.0, 0.0, 1.0).normalize()
                    : new Vec3(1, 0, 0);
            Vec3 startPos = (shooter != null)
                    ? Utils.moveToRelativeGroundLevel(level, shooter.getEyePosition().add(forward.scale(1.5)), 1, 3).add(0, 0.1, 0)
                    : hitPos.subtract(0, 0, 10);
            Vec3 diff = hitPos.subtract(startPos);
            Vec3 horizDir = (diff.horizontalDistance() > 0.001) ? new Vec3(diff.x, 0, diff.z).normalize() : forward;
            float shooterYRot = (shooter != null) ? shooter.getYRot() : 0.0f;
            double totalDist = diff.horizontalDistance();

            // Option B: dynamically scale number of spikes with distance (1 spike per ~1.3 blocks, min 6, max 35)
            int spikeCount = (int) Math.min(35, Math.max(6, Math.round(totalDist / 1.3)));
            double stepDist = totalDist / (double) spikeCount;

            for (int i = 0; i < spikeCount; i++) {
                double dist = (i + 1.0) * stepDist;
                Vec3 samplePos = startPos.add(horizDir.scale(dist));
                Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, samplePos, 2, 4);

                boolean isLastSpike = (i == spikeCount - 1);
                float progress = (float) i / (float) Math.max(1, spikeCount - 1);
                // Authentic Iron's Spells scaling: lerps from 1.0f to 3.0f, with the final finisher spike growing to 4.5f!
                float spikeScale = isLastSpike ? 4.5f : Mth.lerp(progress, 1.0f, 3.0f);
                float spikeDamage = isLastSpike ? damage * 1.5f : damage;

                IceSpikeEntity spike = new IceSpikeEntity(level, shooter);
                spike.moveTo(groundPos);
                spike.setWaitTime(i);
                spike.setSpikeSize(spikeScale);
                spike.setDamage(spikeDamage);
                spike.setYRot(shooterYRot - 45.0f + level.random.nextIntBetweenInclusive(-20, 20));
                spike.setXRot(level.random.nextIntBetweenInclusive(-15, 15));
                if (i % 2 != 0 && !isLastSpike) {
                    spike.setSilent(true);
                }
                level.addFreshEntity(spike);
            }
            return;
        }

        // 6. Frostwave (FrostwaveSpell: getRadius, getDuration, CHILLED effect)
        if (path.contains("frostwave")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 160);

            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.ICE.get().getTargetingColor(), radius),
                    hitPos.x, hitPos.y + 0.165, hitPos.z, 1, 0, 0, 0, 0, true);
            MagicManager.spawnParticles(level, ParticleHelper.SNOWFLAKE, hitPos.x, hitPos.y + 0.5, hitPos.z, 40, radius * 0.4, 0.3, radius * 0.4, 0.08, false);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.FROSTWAVE_PREPARE.get(), SoundSource.PLAYERS, 1.8f, 1.0f);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                if (victim.distanceToSqr(hitPos) <= radius * radius) {
                    victim.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, duration, 0));
                    Utils.addFreezeTicks(victim, victim.getTicksRequiredToFreeze());
                }
            }
            return;
        }

        // 7. Frostbite (FrostbiteSpell: FROSTBITTEN_STRIKES effect + getAmplifierForLevel)
        if (path.contains("frostbite")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = (int) (spell.getSpellPower(spellLevel, shooter) * 20.0f);
                int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
                target.addEffect(new MobEffectInstance(MobEffectRegistry.FROSTBITTEN_STRIKES, duration, amp));
            }
            return;
        }

        // Default Ice School fallback using authentic SpellDamageSource & DamageSources.applyDamage
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleHelper.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05, false);
    }

    // =========================================================================
    // 3. LIGHTNING SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleLightningImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Chain Lightning (ChainLightningSpell: ChainLightning entity + getDamage + getRange + getMaxConnections)
        if (path.contains("chain_lightning")) {
            Entity initialVictim = hitEntity;
            if (initialVictim == null) {
                List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, 8.0, 8.0, 8.0), e -> e != shooter && e.isAlive());
                if (!nearby.isEmpty()) {
                    initialVictim = nearby.get(0);
                }
            }
            if (initialVictim != null) {
                ChainLightning chain = new ChainLightning(level, shooter, initialVictim);
                chain.setDamage(damage);
                chain.range = SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 8.0f);
                Integer maxConn = SpellProjectileFactory.invokeSpellInt(spell, "getMaxConnections", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
                chain.maxConnections = (maxConn != null) ? maxConn : (3 + spellLevel);
                level.addFreshEntity(chain);
            }
            return;
        }

        // 2. Shockwave (ShockwaveSpell: getRadius + getDamage + BlastwaveParticleOptions + ZapParticleOption + DamageSources.applyDamage)
        if (path.contains("shockwave")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.LIGHTNING.get().getTargetingColor(), radius),
                    hitPos.x, hitPos.y + 0.165, hitPos.z, 1, 0, 0, 0, 0, true);
            MagicManager.spawnParticles(level, ParticleHelper.ELECTRIC_SPARKS, hitPos.x, hitPos.y + 0.25, hitPos.z, 35, radius * 0.3, 0.2, radius * 0.3, 0.1, false);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.SHOCKWAVE_CAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);

            LightningBolt dummyBolt = EntityType.LIGHTNING_BOLT.create(level);
            if (dummyBolt != null) {
                dummyBolt.setVisualOnly(true);
                dummyBolt.setDamage(0.0f);
            }

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                if (victim.distanceToSqr(hitPos) <= radius * radius) {
                    Vec3 start = hitPos.add(0, 0.5, 0);
                    Vec3 dest = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                    MagicManager.spawnParticles(level, new ZapParticleOption(dest), start.x, start.y, start.z, 1, 0, 0, 0, 0, true);
                    SpellProjectileFactory.applyAuthenticSpellDamage(victim, damage, spell, bullet, shooter);
                    if (victim instanceof Creeper creeper && dummyBolt != null) {
                        creeper.thunderHit(level, dummyBolt);
                    }
                }
            }
            return;
        }

        // 3. Thunderstorm (ThunderstormSpell: Authentic LightningStrike zone + storm cloud fog)
        if (path.contains("thunderstorm")) {
            if (checkAndMarkVolley(bullet != null ? bullet.getUUID() : null)) {
                int durationTicks = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 160);
                int strikes = Math.max(5, durationTicks / 20);
                ActiveSpellEntityManager.queueThunderstormZone(level, shooter, hitPos, damage, spellLevel, strikes, 8.0f);
                level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.THUNDERSTORM_PREPARE.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
            }
            return;
        }

        // 4. Lightning Bolt (LightningBoltSpell: getSpellPower + 4.0f radius + DamageSources.applyDamage + Creeper.thunderHit)
        if (path.contains("lightning_bolt")) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.moveTo(hitPos);
                if (shooter instanceof ServerPlayer sp) {
                    bolt.setCause(sp);
                }
                level.addFreshEntity(bolt);
            }

            float radius = 4.0f;
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                double dist = victim.distanceTo(bolt != null ? bolt : bullet);
                if (dist <= radius) {
                    float scaledDamage = (float) (damage * (1.0 - dist / (radius * 2.0)));
                    SpellProjectileFactory.applyAuthenticSpellDamage(victim, scaledDamage, spell, bolt, shooter);
                    if (victim instanceof Creeper creeper && bolt != null) {
                        creeper.thunderHit(level, bolt);
                    }
                }
            }
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.0f);
            return;
        }

        // Default Lightning School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleHelper.ELECTRIC_SPARKS, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1, false);
    }

    // =========================================================================
    // 4. HOLY SCHOOL (Authentic Iron's Spells Entities, Healing, Buffs & Damage)
    // =========================================================================
    private static void handleHolyImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        LivingEntity targetEntity = (hitEntity instanceof LivingEntity living) ? living : shooter;

        // 1. Heal & Blessing of Life (HealSpell / BlessingOfLifeSpell: getSpellPower + SpellHealEvent + HealParticlesPacket)
        if (path.equals("heal") || path.contains("blessing_of_life")) {
            if (targetEntity != null) {
                float healAmount = spell.getSpellPower(spellLevel, shooter);
                NeoForge.EVENT_BUS.post(new SpellHealEvent(shooter, targetEntity, healAmount, SchoolRegistry.HOLY.get()));
                targetEntity.heal(healAmount);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(targetEntity, new HealParticlesPacket(targetEntity.position()));
            }
            return;
        }

        // 2. Greater Heal (GreaterHealSpell: heal max health + SpellHealEvent + HealParticlesPacket)
        if (path.contains("greater_heal")) {
            if (targetEntity != null) {
                float healAmount = targetEntity.getMaxHealth();
                NeoForge.EVENT_BUS.post(new SpellHealEvent(shooter, targetEntity, healAmount, SchoolRegistry.HOLY.get()));
                targetEntity.heal(healAmount);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(targetEntity, new HealParticlesPacket(targetEntity.position()));
            }
            return;
        }

        // 3. Cloud of Regeneration (CloudOfRegenerationSpell: radius = 5.0f, getHealing, RegenCloudParticlesPacket)
        if (path.contains("cloud_of_regeneration")) {
            float radius = 5.0f;
            float healing = SpellProjectileFactory.computeAuthenticHealing(spell, spellLevel, shooter);
            List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0),
                    e -> e.isAlive() && (e == shooter || (shooter != null && Utils.shouldHealEntity(shooter, e))));
            for (LivingEntity ally : allies) {
                NeoForge.EVENT_BUS.post(new SpellHealEvent(shooter, ally, healing, SchoolRegistry.HOLY.get()));
                ally.heal(healing);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(ally, new HealParticlesPacket(ally.position()));
            }
            if (shooter != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new RegenCloudParticlesPacket(hitPos));
            }
            return;
        }

        // 4. Healing Circle (HealingCircleSpell: HealingAoe + getRadius + getDuration + getHealing + TargetedAreaEntity)
        if (path.contains("healing_circle")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 4.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 200);
            float healing = SpellProjectileFactory.computeAuthenticHealing(spell, spellLevel, shooter);
            Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, hitPos, 4);

            HealingAoe aoe = new HealingAoe(level);
            aoe.setOwner(shooter);
            aoe.setCircular();
            aoe.setRadius(radius);
            aoe.setDuration(duration);
            aoe.setDamage(healing);
            aoe.setPos(groundPos);
            level.addFreshEntity(aoe);

            TargetedAreaEntity visual = TargetedAreaEntity.createTargetAreaEntity(level, groundPos, radius, 0xC80046, aoe);
            visual.setDuration(duration);
            visual.setOwner(aoe);
            return;
        }

        // 5. Fortify (FortifySpell: radius = 16.0f, FORTIFY effect, getDuration, (int) getSpellPower - 1)
        if (path.contains("fortify")) {
            float radius = 16.0f;
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 400);
            int amp = Math.max(0, (int) spell.getSpellPower(spellLevel, shooter) - 1);
            List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0),
                    e -> e.isAlive() && (e == shooter || (shooter != null && Utils.shouldHealEntity(shooter, e))));
            for (LivingEntity ally : allies) {
                ally.addEffect(new MobEffectInstance(MobEffectRegistry.FORTIFY, duration, amp, false, false, true));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(ally, new FortifyAreaParticlesPacket(ally.position()));
            }
            return;
        }

        // 6. Haste (HasteSpell: radius = 6.0f, HASTENED effect, getDuration, getAmplifier)
        if (path.contains("haste")) {
            float radius = 6.0f;
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 600);
            int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
            List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0),
                    e -> e.isAlive() && (e == shooter || (shooter != null && Utils.shouldHealEntity(shooter, e))));
            for (LivingEntity ally : allies) {
                ally.addEffect(new MobEffectInstance(MobEffectRegistry.HASTENED, duration, amp, false, false, true));
            }
            return;
        }

        // 7. Cleanse (CleanseSpell: radius = 6.0f, remove HARMFUL effects)
        if (path.contains("cleanse")) {
            float radius = 6.0f;
            List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0),
                    e -> e.isAlive() && (e == shooter || (shooter != null && Utils.shouldHealEntity(shooter, e))));
            for (LivingEntity ally : allies) {
                List<Holder<MobEffect>> harmful = new ArrayList<>();
                for (MobEffectInstance inst : ally.getActiveEffects()) {
                    if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                        harmful.add(inst.getEffect());
                    }
                }
                harmful.forEach(ally::removeEffect);
            }
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.CLEANSE_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
            return;
        }

        // 8. Angel Wings (AngelWingsSpell: ANGEL_WINGS effect, getEffectDuration)
        if (path.contains("angel_wings")) {
            if (targetEntity != null) {
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 600);
                targetEntity.addEffect(new MobEffectInstance(MobEffectRegistry.ANGEL_WINGS, duration));
            }
            return;
        }

        // 9. Divine Smite (DivineSmiteSpell: getDamage + radius 2.25f + BlastwaveParticleOptions + CameraShake + DamageSources.applyDamage)
        if (path.contains("divine_smite")) {
            float radius = 2.25f;
            Vec3 smitePos = Utils.moveToRelativeGroundLevel(level, hitPos, 3);
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.HOLY.get().getTargetingColor(), radius * 2.0f),
                    smitePos.x, smitePos.y + 0.165, smitePos.z, 1, 0, 0, 0, 0, true);
            MagicManager.spawnParticles(level, ParticleTypes.ELECTRIC_SPARK, smitePos.x, smitePos.y + 0.2, smitePos.z, 50, radius * 0.4, 0.3, radius * 0.4, 0.15, false);
            CameraShakeManager.addCameraShake(new CameraShakeData(level, 10, smitePos, 10.0f));
            level.playSound(null, smitePos.x, smitePos.y, smitePos.z, SoundRegistry.DIVINE_SMITE_WINDUP.get(), SoundSource.PLAYERS, 2.0f, 1.0f);

            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(smitePos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                SpellProjectileFactory.applyAuthenticSpellDamage(victim, damage, spell, bullet, shooter);
            }
            return;
        }

        // 10. Sunbeam (SunbeamSpell: SunbeamEntity + getDamage)
        if (path.contains("sunbeam")) {
            Vec3 strikePos = (hitEntity != null) ? hitEntity.position() : Utils.moveToRelativeGroundLevel(level, hitPos, 3, 18);
            SunbeamEntity sunbeam = new SunbeamEntity(level);
            sunbeam.setOwner(shooter);
            sunbeam.moveTo(strikePos);
            sunbeam.setDamage(damage);
            level.addFreshEntity(sunbeam);
            level.playSound(null, sunbeam.blockPosition(), SoundRegistry.SUNBEAM_WINDUP.get(), SoundSource.NEUTRAL, 3.5f, 1.0f);
            return;
        }

        // 11. Wisp (WispSpell: WispEntity + getSpellPower + setTarget)
        if (path.contains("wisp")) {
            WispEntity wisp = new WispEntity(level, shooter, spell.getSpellPower(spellLevel, shooter));
            wisp.setPos(hitPos.x, hitPos.y + 0.5, hitPos.z);
            if (hitEntity instanceof LivingEntity livingTarget && livingTarget != shooter) {
                wisp.setTarget(livingTarget);
            }
            level.addFreshEntity(wisp);
            return;
        }

        // Default Holy School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
    }

    // =========================================================================
    // 5. ENDER SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleEnderImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Counterspell (CounterspellSpell: AntiMagicSusceptible, IMagicSummon, serverSideCancelCast, MagicMobEffect)
        if (path.contains("counterspell")) {
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(SchoolRegistry.ENDER.get().getTargetingColor(), 2.5f),
                    hitPos.x, hitPos.y + 0.5, hitPos.z, 1, 0, 0, 0, 0, true);
            List<Entity> targets = level.getEntities(bullet, AABB.ofSize(hitPos, 5.0, 5.0, 5.0), e -> e != shooter);
            for (Entity entity : targets) {
                if (entity instanceof AntiMagicSusceptible antiMagic) {
                    if (antiMagic instanceof IMagicSummon summon && summon.getSummoner() == shooter) {
                        continue;
                    }
                    antiMagic.onAntiMagic(MagicData.getPlayerMagicData(shooter));
                }
                if (entity instanceof LivingEntity living) {
                    if (living instanceof ServerPlayer sp) {
                        Utils.serverSideCancelCast(sp, true);
                    }
                    List<Holder<MobEffect>> magicEffects = new ArrayList<>();
                    for (MobEffectInstance inst : living.getActiveEffects()) {
                        if (inst.getEffect().value() instanceof MagicMobEffect) {
                            magicEffects.add(inst.getEffect());
                        }
                    }
                    magicEffects.forEach(living::removeEffect);
                }
            }
            return;
        }

        // 2. Starfall (StarfallSpell: getDamage, getRadius, Comet bombardment)
        if (path.contains("starfall")) {
            if (checkAndMarkVolley(bullet != null ? bullet.getUUID() : null)) {
                float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 6.0f);
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 160);
                int cometCount = Math.max(8, duration / 10);
                ActiveSpellEntityManager.queueStarfallBombardment(level, shooter, hitPos, damage, spellLevel, cometCount, radius);
                level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.5f, 0.6f);
            }
            return;
        }

        // 3. Black Hole (BlackHoleSpell: BlackHole + getRadius + getDamage)
        if (path.contains("black_hole")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 8.0f);
            BlackHole blackHole = new BlackHole(level, shooter);
            blackHole.setRadius(radius);
            blackHole.setDamage(damage);
            blackHole.moveTo(hitPos.add(0, 1.0, 0));
            level.addFreshEntity(blackHole);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.BLACK_HOLE_CAST.get(), SoundSource.PLAYERS, 2.5f, 1.0f);
            return;
        }

        // 4. Gravity Fissure (GravityFissureSpell: BlackHole + getRadius + getDamage)
        if (path.contains("gravity_fissure")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 4.0f);
            BlackHole fissure = new BlackHole(level, shooter);
            fissure.setRadius(radius);
            fissure.setDamage(damage);
            fissure.moveTo(hitPos.add(0, 1.0, 0));
            level.addFreshEntity(fissure);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.BLACK_HOLE_CAST.get(), SoundSource.PLAYERS, 2.0f, 1.2f);
            return;
        }

        // 5. Summon Swords (SummonSwordsSpell: SummonedSwordEntity, SummonedRapierEntity, SummonedClaymoreEntity, SummonManager, RecastInstance)
        if (path.contains("summon_swords")) {
            if (!checkCanSummon(shooter, spell)) {
                return;
            }
            SummonedSwordEntity sword = new SummonedSwordEntity(level, shooter);
            SummonedRapierEntity rapier = new SummonedRapierEntity(level, shooter);
            SummonedClaymoreEntity claymore = new SummonedClaymoreEntity(level, shooter);

            sword.setPos(hitPos.add(-1.2, 0.5, 0));
            rapier.setPos(hitPos.add(1.2, 0.5, 0));
            claymore.setPos(hitPos.add(0, 0.5, 1.2));

            level.addFreshEntity(sword);
            level.addFreshEntity(rapier);
            level.addFreshEntity(claymore);
            registerSummonRecast(shooter, spell, spellLevel, List.of(sword, rapier, claymore), 12000);
            return;
        }

        // 6. Echoing Strikes (EchoingStrikesSpell: ECHOING_STRIKES effect + getDuration + getAmplifier)
        if (path.contains("echoing_strikes")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 400);
                int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
                target.addEffect(new MobEffectInstance(MobEffectRegistry.ECHOING_STRIKES, duration, amp, false, false, true));
            }
            return;
        }

        // 7. Evasion (EvasionSpell: EVASION effect + getSpellPower * 20 duration + spellLevel - 1 amplifier)
        if (path.contains("evasion")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = (int) (spell.getSpellPower(spellLevel, shooter) * 20.0f);
                target.addEffect(new MobEffectInstance(MobEffectRegistry.EVASION, duration, Math.max(0, spellLevel - 1), false, false, true));
            }
            return;
        }

        // Default Ender School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleHelper.UNSTABLE_ENDER, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1, false);
    }

    // =========================================================================
    // 6. BLOOD SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleBloodImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Ray of Siphoning (RayOfSiphoningSpell: getTickDamage + LifestealPercent 1.0f + BloodSiphonParticlesPacket)
        if (path.contains("ray_of_siphoning") || path.contains("siphon")) {
            if (hitEntity != null) {
                SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
                if (shooter != null) {
                    Vec3 targetCenter = hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0);
                    Vec3 shooterCenter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, new BloodSiphonParticlesPacket(targetCenter, shooterCenter));
                }
            }
            return;
        }

        // 2. Acupuncture (AcupunctureSpell: sphere of getCount BloodNeedles aimed inward at target with getDamage & setScale(0.4f))
        if (path.contains("acupuncture")) {
            int count = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 10);
            Vec3 center = (hitEntity != null)
                    ? hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0)
                    : hitPos.add(0, 1.0, 0);

            for (int i = 0; i < count; i++) {
                double theta = level.random.nextDouble() * 2.0 * Math.PI;
                double phi = Math.acos(2.0 * level.random.nextDouble() - 1.0);
                double r = 2.5 + level.random.nextDouble() * 1.5;
                Vec3 offset = new Vec3(
                        r * Math.sin(phi) * Math.cos(theta),
                        Math.abs(r * Math.cos(phi)) + 0.5,
                        r * Math.sin(phi) * Math.sin(theta)
                );
                Vec3 spawnPos = center.add(offset);
                Vec3 aimDir = center.subtract(spawnPos).normalize();

                BloodNeedle needle = new BloodNeedle(level, shooter);
                needle.moveTo(spawnPos);
                needle.shoot(aimDir);
                needle.setDamage(damage);
                needle.setScale(0.4f);
                level.addFreshEntity(needle);
            }
            return;
        }

        // 3. Devour (DevourSpell: DevourJaw + getDamage + getHpBonus)
        if (path.contains("devour")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : null;
            DevourJaw jaw = new DevourJaw(level, shooter, target);
            jaw.setPos(hitPos);
            if (shooter != null) {
                jaw.setYRot(shooter.getYRot());
            }
            jaw.setDamage(damage);
            Integer hpBonus = SpellProjectileFactory.invokeSpellInt(spell, "getHpBonus", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (hpBonus != null) {
                jaw.vigorLevel = (hpBonus / 2) - 1;
            }
            level.addFreshEntity(jaw);
            return;
        }

        // 4. Sacrifice (SacrificeSpell: doSacrificeExplosion + getDamage + getRadius)
        if (path.contains("sacrifice")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 5.0f);
            SacrificeSpell.doSacrificeExplosion(level, spell.getDamageSource(bullet, shooter), damage, radius, hitPos);
            return;
        }

        // 5. Heartstop (HeartstopSpell: HEARTSTOP effect + getSpellPower * 20 duration)
        if (path.contains("heartstop")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = (int) (spell.getSpellPower(spellLevel, shooter) * 20.0f);
                target.addEffect(new MobEffectInstance(MobEffectRegistry.HEARTSTOP, duration, 0, false, false, true));
            }
            return;
        }

        // 6. Raise Dead (RaiseDeadSpell: SummonedZombie / SummonedSkeleton + getSummonCount, SummonManager, RecastInstance)
        if (path.contains("raise_dead")) {
            if (!checkCanSummon(shooter, spell)) {
                return;
            }
            int count = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 1 + spellLevel);
            List<LivingEntity> undeadList = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double angle = (2.0 * Math.PI * i) / Math.max(1, count);
                Vec3 pos = Utils.moveToRelativeGroundLevel(level, hitPos.add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5), 3);
                LivingEntity undead = (level.random.nextFloat() < 0.35f)
                        ? new SummonedSkeleton(level, shooter, true)
                        : new SummonedZombie(level, shooter, true);
                undead.moveTo(pos);
                level.addFreshEntity(undead);
                undeadList.add(undead);
            }
            registerSummonRecast(shooter, spell, spellLevel, undeadList, 12000);
            return;
        }

        // Default Blood School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleHelper.BLOOD, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.08, false);
    }

    // =========================================================================
    // 7. EVOCATION SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleEvocationImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage, HitResult hitResult) {
        // 1. Fang Strike (FangStrikeSpell: line of getCount ExtendedEvokerFangs trailing from shooter to hitPos)
        if (path.contains("fang_strike")) {
            int count = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 7 + spellLevel);
            Vec3 startPos = (shooter != null) ? shooter.position() : hitPos.subtract(0, 0, count);
            Vec3 diff = hitPos.subtract(startPos);
            Vec3 dir = (diff.horizontalDistance() > 0.001) ? new Vec3(diff.x, 0, diff.z).normalize() : new Vec3(1, 0, 0);
            float yRot = (float) Math.atan2(dir.z, dir.x);

            double totalDist = diff.horizontalDistance();
            double stepDist = Math.max(1.2, totalDist / (double) count);

            for (int i = 0; i < count; i++) {
                Vec3 sample = startPos.add(dir.scale((i + 1) * stepDist));
                Vec3 ground = Utils.moveToRelativeGroundLevel(level, sample, 4);
                ExtendedEvokerFang fang = new ExtendedEvokerFang(level, ground.x, ground.y, ground.z, yRot, i, shooter, damage);
                level.addFreshEntity(fang);
            }
            return;
        }

        // 2. Fang Ward (FangWardSpell: getRings concentric rings of ExtendedEvokerFangs + getDamage)
        if (path.contains("fang_ward")) {
            int rings = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 2);
            for (int r = 0; r < rings; r++) {
                float radius = 1.75f + r * 1.35f;
                int fangsInRing = 8 + r * 4;
                for (int i = 0; i < fangsInRing; i++) {
                    double angle = (2.0 * Math.PI * i) / fangsInRing;
                    Vec3 pos = Utils.moveToRelativeGroundLevel(level, hitPos.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 4);
                    ExtendedEvokerFang fang = new ExtendedEvokerFang(level, pos.x, pos.y, pos.z, (float) angle, r * 3, shooter, damage);
                    level.addFreshEntity(fang);
                }
            }
            return;
        }

        // 3. Fang Swirl (FangSwirlSpell: FangSwirlEntity + getSwirlRadius + getSwirlDurationTicks + getDamage)
        if (path.contains("fang_swirl")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 5.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 100);
            Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, hitPos, 4);

            FangSwirlEntity swirl = new FangSwirlEntity(EntityRegistry.FANG_SWIRL.get(), level);
            swirl.setOwner(shooter);
            swirl.setStartPos(groundPos);
            swirl.setRadius(radius);
            swirl.setDuration(duration);
            swirl.setDamage(damage);
            swirl.moveTo(groundPos);
            level.addFreshEntity(swirl);
            return;
        }

        // 4. Chain Creeper (ChainCreeperSpell: summonCreeperRing + getDamage + getCount)
        if (path.contains("chain_creeper")) {
            int count = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 3 + spellLevel);
            ChainCreeperSpell.summonCreeperRing(level, shooter, hitPos.add(0, 1.5, 0), damage, count);
            return;
        }

        // 5. Firecracker (FirecrackerSpell: ExtendedFireworkRocket + randomFireworkRocket + getDamage)
        if (path.contains("firecracker")) {
            ItemStack rocketStack = new ItemStack(Items.FIREWORK_ROCKET);
            try {
                Method m = spell.getClass().getDeclaredMethod("randomFireworkRocket");
                m.setAccessible(true);
                Object res = m.invoke(spell);
                if (res instanceof ItemStack stack) {
                    rocketStack = stack;
                }
            } catch (Throwable ignored) {}

            ExtendedFireworkRocket firework = new ExtendedFireworkRocket(level, rocketStack, shooter, hitPos.x, hitPos.y + 0.25, hitPos.z, true, damage);
            level.addFreshEntity(firework);
            firework.shoot(0, 0, 0, 0, 0);
            return;
        }

        // 6. Slow (SlowSpell: SLOWED effect + getDuration + getAmplifier + radius)
        if (path.contains("slow")) {
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 200);
            int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
            float radius = 5.0f;
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                victim.addEffect(new MobEffectInstance(MobEffectRegistry.SLOWED, duration, amp));
            }
            return;
        }

        // 7. Shield (ShieldSpell: ShieldEntity + getShieldHP)
        if (path.contains("shield")) {
            Float shieldHp = SpellProjectileFactory.invokeSpellFloat(spell, "getShieldHP", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            ShieldEntity shield = new ShieldEntity(level, shieldHp != null ? shieldHp : 20.0f);
            shield.setPos(hitPos.x, hitPos.y + 0.5, hitPos.z);
            if (shooter != null) {
                shield.setRotation(shooter.getXRot(), shooter.getYRot());
            }
            level.addFreshEntity(shield);
            return;
        }

        // 8. Summon Vex (SummonVexSpell: getSummonCount + SummonedVex, SummonManager, RecastInstance)
        if (path.contains("summon_vex")) {
            if (!checkCanSummon(shooter, spell)) {
                return;
            }
            int count = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 3);
            List<LivingEntity> vexList = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                SummonedVex vex = new SummonedVex(level, shooter);
                vex.moveTo(hitPos.add((level.random.nextDouble() - 0.5) * 1.5, 1.0, (level.random.nextDouble() - 0.5) * 1.5));
                level.addFreshEntity(vex);
                vexList.add(vex);
            }
            registerSummonRecast(shooter, spell, spellLevel, vexList, 12000);
            return;
        }

        // 9. Summon Horse (SummonHorseSpell: SummonedHorse, SummonManager, RecastInstance)
        if (path.contains("summon_horse")) {
            if (!checkCanSummon(shooter, spell)) {
                return;
            }
            SummonedHorse horse = new SummonedHorse(level, shooter);
            horse.moveTo(hitPos);
            level.addFreshEntity(horse);
            registerSummonRecast(shooter, spell, spellLevel, List.of(horse), 12000);
            return;
        }

        // 10. Scapegoat (ScapegoatSpell: ScapegoatEntity + getGoatHealth)
        if (path.contains("scapegoat")) {
            ScapegoatEntity goat = new ScapegoatEntity(level);
            Float goatHp = SpellProjectileFactory.invokeSpellFloat(spell, "getGoatHealth", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            if (goatHp != null && goat.getAttribute(Attributes.MAX_HEALTH) != null) {
                goat.getAttribute(Attributes.MAX_HEALTH).setBaseValue(goatHp);
                goat.setHealth(goat.getMaxHealth());
            }
            goat.moveTo(hitPos);
            goat.setOwner(shooter);
            goat.setDurationRemaining(300);
            level.addFreshEntity(goat);
            Utils.performTaunt(goat, 14.0f, Utils.tauntPredicate(shooter));
            return;
        }

        // 11. Spectral Hammer (SpectralHammerSpell: SpectralHammer + getRadius + getDepth)
        if (path.contains("spectral_hammer")) {
            Integer radius = SpellProjectileFactory.invokeSpellInt(spell, "getRadius", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            Integer depth = SpellProjectileFactory.invokeSpellInt(spell, "getDepth", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            BlockHitResult bhr = (hitResult instanceof BlockHitResult r) ? r : new BlockHitResult(hitPos, Direction.UP, BlockPos.containing(hitPos), false);
            SpectralHammer hammer = new SpectralHammer(level, shooter, bhr, radius != null ? radius : 1, depth != null ? depth : 1);
            hammer.moveTo(hitPos);
            level.addFreshEntity(hammer);
            return;
        }

        // 12. Wololo (WololoSpell)
        if (path.contains("wololo")) {
            if (hitEntity instanceof Sheep sheep) {
                sheep.setColor(DyeColor.byId(level.random.nextInt(16)));
            }
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 2.0f, 1.0f);
            return;
        }

        // 13. Invisibility (InvisibilitySpell: TRUE_INVISIBILITY + getDuration)
        if (path.contains("invisibility")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 400);
                target.addEffect(new MobEffectInstance(MobEffectRegistry.TRUE_INVISIBILITY, duration, 0, false, false, true));
            }
            return;
        }

        // 14. Arrow Volley (ArrowVolleySpell: ArrowVolleyEntity + getDamage + getArrowsPerRow + getRows)
        if (path.contains("arrow_volley")) {
            Vec3 targetPos = hitPos;
            Vec3 shooterPos = (shooter != null) ? shooter.position() : targetPos.subtract(0, 0, 10);
            Vec3 dir = new Vec3(targetPos.x - shooterPos.x, 0, targetPos.z - shooterPos.z).normalize();
            Vec3 backwardOffset = dir.scale(-4.0);
            Vec3 spawnPos = Utils.moveToRelativeGroundLevel(level, targetPos.add(0, 2, 0), 4).add(backwardOffset).add(0, 6, 0);

            BlockHitResult blockHit = Utils.raycastForBlock(level, targetPos, spawnPos, ClipContext.Fluid.NONE);
            Vec3 spawnPoint = blockHit.getLocation().subtract(targetPos).scale(0.9).add(targetPos);

            double dx = targetPos.x - spawnPoint.x;
            double dz = targetPos.z - spawnPoint.z;
            float yRot = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float xRot = (float) (-(Mth.atan2(spawnPoint.y - targetPos.y, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI))) + 25.0f;

            Integer arrowsPerRow = SpellProjectileFactory.invokeSpellInt(spell, "getArrowsPerRow", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);
            Integer rows = SpellProjectileFactory.invokeSpellInt(spell, "getRows", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);

            ArrowVolleyEntity volley = new ArrowVolleyEntity(EntityRegistry.ARROW_VOLLEY_ENTITY.get(), level);
            volley.moveTo(spawnPoint);
            volley.setYRot(yRot);
            volley.setXRot(xRot);
            volley.setDamage(damage);
            volley.setArrowsPerRow(arrowsPerRow != null ? arrowsPerRow : 6);
            volley.setRows(rows != null ? rows : (4 + spellLevel));
            volley.setOwner(shooter);
            level.addFreshEntity(volley);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.ARROW_VOLLEY_PREPARE.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
            return;
        }

        // Default Evocation School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
    }

    // =========================================================================
    // 8. NATURE SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleNatureImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Touch Dig (TouchDigSpell)
        if (path.contains("touch_dig")) {
            BlockPos pos = BlockPos.containing(hitPos);
            if (level.getBlockState(pos).isAir()) pos = pos.relative(Direction.DOWN);
            level.destroyBlock(pos, true, shooter);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.TOUCH_DIG_CAST.get(), SoundSource.PLAYERS, 1.4f, 1.0f);
            return;
        }

        // 2. Oakskin / Spider Aspect / Gluttony (Buff Spells: getSpellPower * 20 duration + getAmplifier)
        if (path.contains("oakskin") || path.contains("spider_aspect") || path.contains("gluttony")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = (int) (spell.getSpellPower(spellLevel, shooter) * 20.0f);
                int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
                if (path.contains("oakskin")) {
                    target.addEffect(new MobEffectInstance(MobEffectRegistry.OAKSKIN, duration, amp, false, false, true));
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundRegistry.OAKSKIN_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                } else if (path.contains("spider_aspect")) {
                    target.addEffect(new MobEffectInstance(MobEffectRegistry.SPIDER_ASPECT, duration, amp, false, false, true));
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundRegistry.SPIDER_ASPECT_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                } else {
                    target.addEffect(new MobEffectInstance(MobEffectRegistry.GLUTTONY, duration, amp, false, false, true));
                }
            }
            return;
        }

        // 3. Blight (BlightSpell: BLIGHT effect + getDuration + getAmplifier)
        if (path.contains("blight")) {
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 300);
            int amp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 0);
            float radius = 5.0f;
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, radius * 2.0, radius * 2.0, radius * 2.0), e -> e != shooter && e.isAlive());
            for (LivingEntity victim : victims) {
                victim.addEffect(new MobEffectInstance(MobEffectRegistry.BLIGHT, duration, amp));
            }
            return;
        }

        // 4. Earthquake (EarthquakeSpell: EarthquakeAoe + setCircular + getRadius + getDamage + getSlownessAmplifier)
        if (path.contains("earthquake")) {
            float radius = SpellProjectileFactory.computeAuthenticRadius(spell, spellLevel, shooter, 8.0f);
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 240);
            int slowAmp = SpellProjectileFactory.computeAuthenticAmplifier(spell, spellLevel, shooter, 1);
            Vec3 groundPos = Utils.moveToRelativeGroundLevel(level, hitPos, 4);

            EarthquakeAoe aoe = new EarthquakeAoe(level);
            aoe.setOwner(shooter);
            aoe.setCircular();
            aoe.setRadius(radius);
            aoe.setDuration(duration);
            aoe.setDamage(damage);
            aoe.setSlownessAmplifier(slowAmp);
            aoe.moveTo(groundPos);
            level.addFreshEntity(aoe);
            return;
        }

        // 5. Poison Splash (PoisonSplashSpell: PoisonSplash + getDamage + getDuration)
        if (path.contains("poison_splash")) {
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 200);
            PoisonSplash splash = new PoisonSplash(level);
            splash.setOwner(shooter);
            splash.setDamage(damage);
            splash.setEffectDuration(duration);
            splash.moveTo(hitPos);
            level.addFreshEntity(splash);
            return;
        }

        // 6. Root (RootSpell: RootEntity + getDuration + getRootHealth)
        if (path.contains("root")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : null;
            int duration = SpellProjectileFactory.computeAuthenticDuration(spell, spellLevel, shooter, 140);
            Float rootHp = SpellProjectileFactory.invokeSpellFloat(spell, "getRootHealth", new Class<?>[]{int.class, LivingEntity.class}, spellLevel, shooter);

            RootEntity root = new RootEntity(level, shooter);
            root.setDuration(duration);
            if (rootHp != null && root.getAttribute(Attributes.MAX_HEALTH) != null) {
                root.getAttribute(Attributes.MAX_HEALTH).setBaseValue(rootHp);
                root.setHealth(root.getMaxHealth());
            }
            if (target != null) {
                root.setTarget(target);
                root.moveTo(target.position());
                level.addFreshEntity(root);
                target.stopRiding();
                target.startRiding(root, true);
            } else {
                root.moveTo(hitPos);
                level.addFreshEntity(root);
            }
            return;
        }

        // 7. Firefly Swarm (FireflySwarmSpell: FireflySwarmProjectile + getDamage)
        if (path.contains("firefly_swarm")) {
            Entity target = (hitEntity instanceof LivingEntity) ? hitEntity : null;
            FireflySwarmProjectile swarm = new FireflySwarmProjectile(level, shooter, target, damage);
            swarm.moveTo(hitPos.add(0, 0.75, 0));
            level.addFreshEntity(swarm);
            return;
        }

        // 8. Stomp (StompSpell: StompAoe + getRange + getDamage)
        if (path.contains("stomp")) {
            int range = (int) SpellProjectileFactory.computeAuthenticRange(spell, spellLevel, shooter, 5.0f);
            float yRot = (shooter != null) ? shooter.getYRot() : 0.0f;
            StompAoe stomp = new StompAoe(level, range, yRot);
            stomp.setOwner(shooter);
            stomp.setDamage(damage);
            stomp.moveTo(Utils.moveToRelativeGroundLevel(level, hitPos, 3));
            level.addFreshEntity(stomp);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundRegistry.EARTHQUAKE_IMPACT.get(), SoundSource.PLAYERS, 1.8f, 1.1f);
            return;
        }

        // Default Nature School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
    }

    // =========================================================================
    // 9. ELDRITCH SCHOOL (Authentic Iron's Spells Entities, Damage & Effects)
    // =========================================================================
    private static void handleEldritchImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // 1. Sculk Tentacles (SculkTentaclesSpell: getRings concentric rings of VoidTentacle + getDamage)
        if (path.contains("sculk_tentacles") || path.contains("void_tentacles")) {
            int rings = SpellProjectileFactory.computeAuthenticCount(spell, spellLevel, shooter, 2);
            Vec3 center = Utils.moveToRelativeGroundLevel(level, hitPos, 4);
            for (int r = 0; r < rings; r++) {
                float radius = 1.3f * (r + 1);
                int count = (r + 1) * 4;
                for (int i = 0; i < count; i++) {
                    double angle = (2.0 * Math.PI * i) / count + (r * 0.4);
                    Vec3 spawn = Utils.moveToRelativeGroundLevel(level, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 4);
                    VoidTentacle tentacle = new VoidTentacle(level, shooter, damage);
                    tentacle.moveTo(spawn);
                    tentacle.setYRot(level.random.nextInt(360));
                    level.addFreshEntity(tentacle);
                }
            }
            level.playSound(null, center.x, center.y, center.z, SoundRegistry.VOID_TENTACLES_START.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
            return;
        }

        // 2. Abyssal Shroud / Planar Sight (Buff Spells: getSpellPower * 20 duration)
        if (path.contains("abyssal_shroud") || path.contains("planar_sight")) {
            LivingEntity target = (hitEntity instanceof LivingEntity living) ? living : shooter;
            if (target != null) {
                int duration = (int) (spell.getSpellPower(spellLevel, shooter) * 20.0f);
                if (path.contains("abyssal_shroud")) {
                    target.addEffect(new MobEffectInstance(MobEffectRegistry.ABYSSAL_SHROUD, duration, 0, false, false, true));
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundRegistry.ABYSSAL_SHROUD.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                } else {
                    target.addEffect(new MobEffectInstance(MobEffectRegistry.PLANAR_SIGHT, duration, 0, false, false, true));
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundRegistry.PLANAR_SIGHT_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
                }
            }
            return;
        }

        // Default Eldritch School fallback
        if (hitEntity != null) {
            SpellProjectileFactory.applyAuthenticSpellDamage(hitEntity, damage, spell, bullet, shooter);
        }
        MagicManager.spawnParticles(level, ParticleTypes.SCULK_SOUL, hitPos.x, hitPos.y, hitPos.z, 25, 0.4, 0.4, 0.4, 0.1, false);
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
