package com.ironsspellbullet.event;

import com.ironsspellbullet.spell.SpellProjectileFactory;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.LightningStrike;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks and manages active continuous/cone/spray entities (e.g. Fire Breath, Cone of Cold),
 * rapid-fire spell bursts (e.g. Blaze Storm, Blood Needles, Flaming Barrage),
 * and tactical world airstrike bombardments (e.g. Starfall, Thunderstorm).
 *
 * Ensures that if a player switches weapons, holsters their firearm, changes ammo,
 * or releases the trigger, all active cone/spray entities and player bursts are immediately terminated.
 */
public class ActiveSpellEntityManager {

    private static class TrackedEntity {
        final Entity entity;
        int remainingTicks;

        TrackedEntity(Entity entity, int maxTicks) {
            this.entity = entity;
            this.remainingTicks = maxTicks;
        }
    }

    public static class BlazeStormBurst {
        final ServerLevel level;
        final LivingEntity shooter;
        final float damage;
        final int spellLevel;
        int remainingFireballs;
        int ticksUntilNext;

        public BlazeStormBurst(ServerLevel level, LivingEntity shooter, float damage, int spellLevel, int count) {
            this.level = level;
            this.shooter = shooter;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingFireballs = count;
            this.ticksUntilNext = 2; // Fires 1 fireball every 2 ticks (0.1s)
        }
    }

    public static class BloodNeedlesBurst {
        final ServerLevel level;
        final LivingEntity shooter;
        final float damage;
        final int spellLevel;
        int remainingNeedles;
        int ticksUntilNext;
        float currentZRot;

        public BloodNeedlesBurst(ServerLevel level, LivingEntity shooter, float damage, int spellLevel, int count) {
            this.level = level;
            this.shooter = shooter;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingNeedles = count;
            this.ticksUntilNext = 2; // Fires 1 needle every 2 ticks
            this.currentZRot = 72.0f; // 5-needle spiral step (360 / 5)
        }
    }

    public static class FlamingBarrageBurst {
        final ServerLevel level;
        final LivingEntity shooter;
        final float damage;
        final int spellLevel;
        int remainingFireballs;
        int ticksUntilNext;

        public FlamingBarrageBurst(ServerLevel level, LivingEntity shooter, float damage, int spellLevel, int count) {
            this.level = level;
            this.shooter = shooter;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingFireballs = count;
            this.ticksUntilNext = 2;
        }
    }

    public static class EldritchBlastBurst {
        final ServerLevel level;
        final LivingEntity shooter;
        final AbstractSpell spell;
        final float damage;
        final int spellLevel;
        int remainingBlasts;
        int ticksUntilNext;

        public EldritchBlastBurst(ServerLevel level, LivingEntity shooter, AbstractSpell spell, float damage, int spellLevel, int count) {
            this.level = level;
            this.shooter = shooter;
            this.spell = spell;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingBlasts = count;
            this.ticksUntilNext = 3; // 3 ticks between blasts (rapid-fire barrage)
        }
    }

    public static class PsychicBoltBurst {
        final ServerLevel level;
        final LivingEntity shooter;
        final float damage;
        final int spellLevel;
        int remainingBolts;
        int ticksUntilNext;

        public PsychicBoltBurst(ServerLevel level, LivingEntity shooter, float damage, int spellLevel, int count) {
            this.level = level;
            this.shooter = shooter;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingBolts = count;
            this.ticksUntilNext = 2; // Fires 1 bolt every 2 ticks (0.1s), matching Flaming Barrage!
        }
    }

    public static class StarfallBombardment {
        final ServerLevel level;
        final LivingEntity shooter;
        final Vec3 center;
        final float damage;
        final int spellLevel;
        int remainingComets;
        int ticksUntilNext;
        final float radius;

        public StarfallBombardment(ServerLevel level, LivingEntity shooter, Vec3 center, float damage, int spellLevel, int count, float radius) {
            this.level = level;
            this.shooter = shooter;
            this.center = center;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingComets = count;
            this.ticksUntilNext = 0; // Drops first comet immediately!
            this.radius = radius;
        }
    }

    public static class ThunderstormZone {
        final ServerLevel level;
        final LivingEntity shooter;
        final Vec3 center;
        final float damage;
        final int spellLevel;
        int remainingStrikes;
        int ticksUntilNext;
        int totalAge;
        final float radius;

        public ThunderstormZone(ServerLevel level, LivingEntity shooter, Vec3 center, float damage, int spellLevel, int count, float radius) {
            this.level = level;
            this.shooter = shooter;
            this.center = center;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingStrikes = count;
            this.ticksUntilNext = 3; // Initial lightning strike quickly follows bullet impact
            this.totalAge = 0;
            this.radius = radius;
        }
    }

    public static class SolarStormZone {
        final ServerLevel level;
        final LivingEntity shooter;
        final LivingEntity pinnedTarget;
        Vec3 center;
        float damage;
        int spellLevel;
        int remainingTicks;
        int ticksUntilNextWave;
        double radius;

        public SolarStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel, int durationTicks, double radius) {
            this.level = level;
            this.shooter = shooter;
            this.pinnedTarget = pinnedTarget;
            this.center = center;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingTicks = durationTicks;
            this.ticksUntilNextWave = 0; // Immediate strike on initial impact!
            this.radius = radius;
        }
    }

    public static class AcidRainZone {
        final ServerLevel level;
        final LivingEntity shooter;
        final Entity acidRainEntity;
        final Vec3 center;
        final float damage;
        final float radius;
        final AbstractSpell spell;
        int remainingTicks;

        public AcidRainZone(ServerLevel level, LivingEntity shooter, Entity acidRainEntity, Vec3 center, float damage, float radius, int durationTicks, AbstractSpell spell) {
            this.level = level;
            this.shooter = shooter;
            this.acidRainEntity = acidRainEntity;
            this.center = center;
            this.damage = damage;
            this.radius = radius;
            this.remainingTicks = durationTicks;
            this.spell = spell;
        }
    }

    public static class MeteorStormZone {
        final ServerLevel level;
        final LivingEntity shooter;
        final LivingEntity pinnedTarget;
        Vec3 center;
        float damage;
        int spellLevel;
        int remainingTicks;
        int ticksUntilNextWave;
        double radius;
        final AbstractSpell spell;

        public MeteorStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel, int durationTicks, double radius, AbstractSpell spell) {
            this.level = level;
            this.shooter = shooter;
            this.pinnedTarget = pinnedTarget;
            this.center = center;
            this.damage = damage;
            this.spellLevel = spellLevel;
            this.remainingTicks = durationTicks;
            this.ticksUntilNextWave = 0; // Immediate strike on initial impact!
            this.radius = radius;
            this.spell = spell;
        }
    }

    private static final Map<UUID, CopyOnWriteArrayList<TrackedEntity>> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BlazeStormBurst>> ACTIVE_BLAZE_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BloodNeedlesBurst>> ACTIVE_BLOOD_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<FlamingBarrageBurst>> ACTIVE_FLAMING_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<EldritchBlastBurst>> ACTIVE_ELDRITCH_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<PsychicBoltBurst>> ACTIVE_PSYCHIC_BURSTS = new ConcurrentHashMap<>();

    // Tactical World Airstrikes
    private static final CopyOnWriteArrayList<StarfallBombardment> ACTIVE_STARFALLS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ThunderstormZone> ACTIVE_THUNDERSTORMS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<SolarStormZone> ACTIVE_SOLAR_STORMS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<AcidRainZone> ACTIVE_ACID_RAINS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<MeteorStormZone> ACTIVE_METEOR_STORMS = new CopyOnWriteArrayList<>();

    /**
     * Registers a spell entity (cone, spray, beam) to a shooter with a maximum burst duration.
     * Default max duration is 8 ticks (0.4s) for a firearm spray puff.
     */
    public static void registerEntity(LivingEntity shooter, Entity entity, int maxTicks) {
        if (shooter == null || entity == null) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_ENTITIES.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            for (TrackedEntity te : list) {
                if (te.entity.isAlive()) {
                    te.entity.discard();
                }
            }
            list.clear();
            list.add(new TrackedEntity(entity, maxTicks));
            return list;
        });
    }

    public static void registerEntity(LivingEntity shooter, Entity entity) {
        registerEntity(shooter, entity, 8); // 8 ticks = 0.4s burst
    }

    /**
     * Queues rapid-fire Blaze Storm fireballs to burst from the gun in quick succession.
     */
    public static void queueBlazeStormBurst(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, float damage, int spellLevel, int count) {
        if (shooter == null || count <= 0) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_BLAZE_BURSTS.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(new BlazeStormBurst(level, shooter, damage, spellLevel, count));
            return list;
        });
    }

    /**
     * Queues rapid-fire Blood Needles to burst from the gun in a spiraling 5-needle volley.
     */
    public static void queueBloodNeedlesBurst(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, float damage, int spellLevel, int count) {
        if (shooter == null || count <= 0) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_BLOOD_BURSTS.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(new BloodNeedlesBurst(level, shooter, damage, spellLevel, count));
            return list;
        });
    }

    /**
     * Queues rapid-fire Flaming Barrage fireballs to burst from the gun in quick succession.
     */
    public static void queueFlamingBarrageBurst(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, float damage, int spellLevel, int count) {
        if (shooter == null || count <= 0) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_FLAMING_BURSTS.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(new FlamingBarrageBurst(level, shooter, damage, spellLevel, count));
            return list;
        });
    }

    /**
     * Queues rapid-fire Eldritch Blast beams to burst sequentially from the gun in quick succession.
     */
    public static void queueEldritchBlastBurst(ServerLevel level, LivingEntity shooter, AbstractSpell spell, float damage, int spellLevel, int count) {
        if (shooter == null || count <= 0) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_ELDRITCH_BURSTS.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(new EldritchBlastBurst(level, shooter, spell, damage, spellLevel, count));
            return list;
        });
    }

    /**
     * Queues rapid-fire Psychic Bolt projectiles to burst sequentially from the gun like Flaming Barrage.
     */
    public static void queuePsychicBoltBurst(ServerLevel level, LivingEntity shooter, float damage, int spellLevel, int count) {
        if (shooter == null || count <= 0) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_PSYCHIC_BURSTS.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(new PsychicBoltBurst(level, shooter, damage, spellLevel, count));
            return list;
        });
    }

    /**
     * Queues a tactical celestial Starfall bombardment raining down comets over the target impact area.
     */
    public static void queueStarfallBombardment(ServerLevel level, LivingEntity shooter, Vec3 center, float damage, int spellLevel, int cometCount, float radius) {
        if (level == null || center == null || cometCount <= 0) return;
        ACTIVE_STARFALLS.add(new StarfallBombardment(level, shooter, center, damage, spellLevel, cometCount, radius));
    }

    /**
     * Queues an authentic localized thunderstorm zone over the bullet impact area,
     * maintaining hovering dark storm clouds and periodically striking living enemies with authentic LightningStrike entities.
     */
    public static void queueThunderstormZone(ServerLevel level, LivingEntity shooter, Vec3 center, float damage, int spellLevel, int strikeCount, float radius) {
        if (level == null || center == null || strikeCount <= 0) return;
        ACTIVE_THUNDERSTORMS.add(new ThunderstormZone(level, shooter, center, damage, spellLevel, strikeCount, radius));
    }

    public static void queueThunderstormBarrage(ServerLevel level, LivingEntity shooter, Vec3 center, float damage, int spellLevel, int strikeCount, float radius) {
        queueThunderstormZone(level, shooter, center, damage, spellLevel, strikeCount, radius);
    }

    public static void queueThunderstormZone(ServerLevel level, LivingEntity shooter, Vec3 center, int spellLevel, int strikeCount, float radius) {
        AbstractSpell spell = SpellRegistry.THUNDERSTORM_SPELL.get();
        float dmg = SpellProjectileFactory.computeAuthenticSpellDamage(spell, spellLevel, shooter);
        queueThunderstormZone(level, shooter, center, dmg, spellLevel, strikeCount, radius);
    }

    public static void queueThunderstormBarrage(ServerLevel level, LivingEntity shooter, Vec3 center, int spellLevel, int strikeCount, float radius) {
        queueThunderstormZone(level, shooter, center, spellLevel, strikeCount, radius);
    }

    /**
     * Queues an authentic continuous Solar Storm orbital bombardment zone centered at the bullet impact area/target.
     * Operates identically to SolarStormSpell & SolarStormEffect:
     * - Duration: 160 + spellLevel * 60 ticks (or spell.getDuration)
     * - Radius: 20.0m (or spell.getRadius)
     * - Every 60 ticks, strikes up to 5 valid targets with authentic ExtendedSunstrikeEntity beams from the sky!
     */
    public static void queueSolarStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel, int durationTicks, double radius) {
        if (level == null || center == null) return;

        // Authentic effect behavior: if storm already exists on this target or in this area,
        // do not stack duplicate storms, but extend/refresh duration and upgrade damage!
        for (SolarStormZone existing : ACTIVE_SOLAR_STORMS) {
            if (existing.level == level) {
                boolean isSameTarget = (pinnedTarget != null && existing.pinnedTarget != null && existing.pinnedTarget.equals(pinnedTarget));
                boolean isSameArea = existing.center.distanceTo(center) <= (existing.radius * 0.75);
                if (isSameTarget || isSameArea) {
                    existing.remainingTicks = Math.max(existing.remainingTicks, durationTicks) + 60;
                    if (damage > existing.damage) {
                        existing.damage = damage;
                        existing.spellLevel = spellLevel;
                    }
                    if (radius > existing.radius) {
                        existing.radius = radius;
                    }
                    return;
                }
            }
        }

        ACTIVE_SOLAR_STORMS.add(new SolarStormZone(level, shooter, pinnedTarget, center, damage, spellLevel, durationTicks, radius));
    }

    public static void queueSolarStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel) {
        queueSolarStormZone(level, shooter, pinnedTarget, center, damage, spellLevel, 160 + spellLevel * 60, 20.0d);
    }

    /**
     * Queues an authentic Acid Rain sustained downpour zone ensuring continuous corrosive damage
     * to all enemies inside the downpour radius.
     */
    public static void queueAcidRainZone(ServerLevel level, LivingEntity shooter, Entity acidRainEntity, Vec3 center, float damage, float radius, int durationTicks, AbstractSpell spell) {
        if (level == null || center == null) return;
        ACTIVE_ACID_RAINS.add(new AcidRainZone(level, shooter, acidRainEntity, center, damage, radius, durationTicks, spell));
    }

    /**
     * Queues an authentic continuous Meteor Storm bombardment zone centered at the bullet impact area/target.
     * Operates identically to MeteorStormSpell & MeteorStormEffect (similar to Solar Storm):
     * - Duration: 160 + spellLevel * 60 ticks
     * - Radius: 24.0m
     * - Every 15 ticks, summons up to 3 MagicFireball projectiles from the sky towards targets or ground in radius!
     */
    public static void queueMeteorStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel, int durationTicks, double radius, AbstractSpell spell) {
        if (level == null || center == null) return;

        for (MeteorStormZone existing : ACTIVE_METEOR_STORMS) {
            if (existing.level == level) {
                boolean isSameTarget = (pinnedTarget != null && existing.pinnedTarget != null && existing.pinnedTarget.equals(pinnedTarget));
                boolean isSameArea = existing.center.distanceTo(center) <= (existing.radius * 0.75);
                if (isSameTarget || isSameArea) {
                    existing.remainingTicks = Math.max(existing.remainingTicks, durationTicks) + 60;
                    if (damage > existing.damage) {
                        existing.damage = damage;
                        existing.spellLevel = spellLevel;
                    }
                    if (radius > existing.radius) {
                        existing.radius = radius;
                    }
                    return;
                }
            }
        }

        ACTIVE_METEOR_STORMS.add(new MeteorStormZone(level, shooter, pinnedTarget, center, damage, spellLevel, durationTicks, radius, spell));
    }

    public static void queueMeteorStormZone(ServerLevel level, LivingEntity shooter, LivingEntity pinnedTarget, Vec3 center, float damage, int spellLevel, AbstractSpell spell) {
        queueMeteorStormZone(level, shooter, pinnedTarget, center, damage, spellLevel, 160 + spellLevel * 60, 24.0d, spell);
    }

    /**
     * Immediately stops and discards all active spell entities and bursts for the given shooter.
     */
    public static void stopPlayerEffects(LivingEntity shooter) {
        if (shooter == null) return;
        stopPlayerEffects(shooter.getUUID());
    }

    public static void stopPlayerEffects(UUID playerUUID) {
        if (playerUUID == null) return;

        // Discard active cone/beam entities
        CopyOnWriteArrayList<TrackedEntity> list = ACTIVE_ENTITIES.remove(playerUUID);
        if (list != null) {
            for (TrackedEntity te : list) {
                if (te.entity.isAlive()) {
                    te.entity.discard();
                }
            }
        }

        // Cancel any queued rapid-fire player bursts
        ACTIVE_BLAZE_BURSTS.remove(playerUUID);
        ACTIVE_BLOOD_BURSTS.remove(playerUUID);
        ACTIVE_FLAMING_BURSTS.remove(playerUUID);
        ACTIVE_ELDRITCH_BURSTS.remove(playerUUID);
        ACTIVE_PSYCHIC_BURSTS.remove(playerUUID);
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EquipmentSlot slot = event.getSlot();
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                ItemStack from = event.getFrom();
                ItemStack to = event.getTo();
                if (from.getItem() != to.getItem()) {
                    stopPlayerEffects(player);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // 1. Tick Starfall Celestial Bombardments (Matches StarfallSpell.spawnComet)
        if (!ACTIVE_STARFALLS.isEmpty()) {
            for (Iterator<StarfallBombardment> it = ACTIVE_STARFALLS.iterator(); it.hasNext(); ) {
                StarfallBombardment b = it.next();
                if (b.level != serverLevel) continue;
                b.ticksUntilNext--;
                if (b.ticksUntilNext <= 0) {
                    double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                    double r = serverLevel.random.nextDouble() * b.radius;
                    Vec3 strikeTarget = b.center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    Vec3 cometSpawn = strikeTarget.add(
                            serverLevel.random.triangle(0.0, 2.5),
                            18.0 + serverLevel.random.nextDouble() * 4.0,
                            serverLevel.random.triangle(0.0, 2.5)
                    );

                    Comet comet = new Comet(serverLevel, b.shooter);
                    comet.setPos(cometSpawn.x, cometSpawn.y, cometSpawn.z);
                    Vec3 flyDir = strikeTarget.subtract(cometSpawn).normalize();
                    comet.shoot(flyDir, 0.075f);
                    comet.setDeltaMovement(flyDir.scale(2.0));
                    comet.setDamage(b.damage);
                    comet.setExplosionRadius(2.0f);
                    serverLevel.addFreshEntity(comet);

                    serverLevel.playSound(null, cometSpawn.x, cometSpawn.y, cometSpawn.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.5f, 0.7f + serverLevel.random.nextFloat() * 0.3f);
                    MagicManager.spawnParticles(serverLevel, ParticleHelper.COMET_FOG, cometSpawn.x, cometSpawn.y, cometSpawn.z, 15, 0.8, 0.8, 0.8, 0.08, false);

                    b.remainingComets--;
                    b.ticksUntilNext = 2;
                    if (b.remainingComets <= 0) {
                        ACTIVE_STARFALLS.remove(b);
                    }
                }
            }
        }

        // 2. Tick Thunderstorm Zones (Authentic Iron's Spells LightningStrike & dark storm clouds)
        if (!ACTIVE_THUNDERSTORMS.isEmpty()) {
            for (Iterator<ThunderstormZone> it = ACTIVE_THUNDERSTORMS.iterator(); it.hasNext(); ) {
                ThunderstormZone tz = it.next();
                if (tz.level != serverLevel) continue;

                tz.totalAge++;

                // Atmospheric storm fog clouds hovering over the thunderstorm zone
                if (tz.totalAge % 2 == 0) {
                    double cloudRadius = tz.radius * 0.8;
                    double cx = (serverLevel.random.nextDouble() - 0.5) * 2 * cloudRadius;
                    double cz = (serverLevel.random.nextDouble() - 0.5) * 2 * cloudRadius;
                    Vec3 cloudPos = tz.center.add(cx, 7.0 + serverLevel.random.nextDouble() * 2.0, cz);
                    MagicManager.spawnParticles(serverLevel,
                            serverLevel.random.nextBoolean() ? ParticleHelper.FOG_THUNDER_DARK : ParticleHelper.FOG_THUNDER_LIGHT,
                            cloudPos.x, cloudPos.y, cloudPos.z, 2, 0.4, 0.2, 0.4, 0.02, false);
                }

                tz.ticksUntilNext--;
                if (tz.ticksUntilNext <= 0) {
                    // Search for living enemies inside the storm radius
                    AABB zoneBox = AABB.ofSize(tz.center, tz.radius * 2.0, 12.0, tz.radius * 2.0);
                    List<LivingEntity> enemies = serverLevel.getEntitiesOfClass(LivingEntity.class, zoneBox,
                            e -> e.isAlive() && !e.isSpectator() && (tz.shooter == null || (!e.isAlliedTo(tz.shooter) && e != tz.shooter)));

                    Vec3 strikePos;
                    if (!enemies.isEmpty()) {
                        LivingEntity target = enemies.get(serverLevel.random.nextInt(enemies.size()));
                        strikePos = target.position();
                    } else {
                        double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                        double r = serverLevel.random.nextDouble() * tz.radius;
                        strikePos = Utils.moveToRelativeGroundLevel(serverLevel, tz.center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r), 3, 6);
                    }

                    // Authentic Iron's Spells LightningStrike entity
                    LightningStrike strike = new LightningStrike(serverLevel);
                    strike.setOwner(tz.shooter);
                    strike.setDamage(tz.damage);
                    strike.setPos(strikePos);
                    serverLevel.addFreshEntity(strike);

                    tz.remainingStrikes--;
                    tz.ticksUntilNext = 12 + serverLevel.random.nextInt(8); // Strikes every 12-20 ticks (0.6s - 1.0s)
                    if (tz.remainingStrikes <= 0) {
                        ACTIVE_THUNDERSTORMS.remove(tz);
                    }
                }
            }
        }

        // 3. Tick Solar Storm Orbital Bombardment Zones (Authentic GTBC Geomancy Plus SolarStormSpell & SolarStormEffect)
        if (!ACTIVE_SOLAR_STORMS.isEmpty()) {
            for (Iterator<SolarStormZone> it = ACTIVE_SOLAR_STORMS.iterator(); it.hasNext(); ) {
                SolarStormZone sz = it.next();
                if (sz.level != serverLevel) continue;

                sz.remainingTicks--;
                if (sz.remainingTicks <= 0) {
                    ACTIVE_SOLAR_STORMS.remove(sz);
                    continue;
                }

                // If pinned to a living entity, follow its movements
                if (sz.pinnedTarget != null && sz.pinnedTarget.isAlive()) {
                    sz.center = sz.pinnedTarget.position();
                }

                // Ambient celestial solar flame particles around zone
                if (serverLevel.getGameTime() % 6 == 0) {
                    double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                    double r = serverLevel.random.nextDouble() * sz.radius * 0.7;
                    Vec3 pPos = sz.center.add(Math.cos(angle) * r, 0.5 + serverLevel.random.nextDouble() * 2.0, Math.sin(angle) * r);
                    serverLevel.sendParticles(ParticleTypes.FLAME, pPos.x, pPos.y, pPos.z, 2, 0.2, 0.3, 0.2, 0.02);
                }

                sz.ticksUntilNextWave--;
                if (sz.ticksUntilNextWave <= 0) {
                    sz.ticksUntilNextWave = 60; // Waves every 60 ticks (3.0s), matching original SolarStormEffect

                    // Search for up to 5 valid enemies in 20.0m radius (matching SolarStormEffect.getValidTargets)
                    AABB zoneBox = AABB.ofSize(sz.center, sz.radius * 2.0, 16.0, sz.radius * 2.0);
                    List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, zoneBox,
                            e -> e.isAlive() && !e.isInvulnerable() && !e.isSpectator()
                            && (sz.shooter == null || (!e.isAlliedTo(sz.shooter) && e != sz.shooter))
                    );

                    // Prioritize pinned target first if alive
                    if (sz.pinnedTarget != null && sz.pinnedTarget.isAlive() && !candidates.contains(sz.pinnedTarget)) {
                        candidates.add(0, sz.pinnedTarget);
                    }

                    int count = Math.min(5, candidates.size());
                    for (int i = 0; i < count; i++) {
                        LivingEntity target = candidates.get(i);
                        com.ironsspellbullet.compat.addon.AddonSpellBulletHandler.spawnAuthenticSunstrike(
                                serverLevel, sz.shooter, (int) target.getX(), (int) target.getY(), (int) target.getZ(), sz.damage
                        );
                    }

                    // If no living enemies in zone, strike random ground point near center
                    if (count == 0) {
                        double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                        double r = serverLevel.random.nextDouble() * 6.0;
                        int sx = (int) (sz.center.x + Math.cos(angle) * r);
                        int szPos = (int) (sz.center.z + Math.sin(angle) * r);
                        int sy = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(sx, 0, szPos)).getY();
                        com.ironsspellbullet.compat.addon.AddonSpellBulletHandler.spawnAuthenticSunstrike(
                                serverLevel, sz.shooter, sx, sy, szPos, sz.damage
                        );
                    }
                }
            }
        }

        // 4. Tick Acid Rain Zones (Ensures sustained acid damage to enemies inside the downpour)
        if (!ACTIVE_ACID_RAINS.isEmpty()) {
            for (Iterator<AcidRainZone> it = ACTIVE_ACID_RAINS.iterator(); it.hasNext(); ) {
                AcidRainZone az = it.next();
                if (az.level != serverLevel) continue;

                az.remainingTicks--;
                if (az.remainingTicks <= 0 || (az.acidRainEntity != null && !az.acidRainEntity.isAlive())) {
                    ACTIVE_ACID_RAINS.remove(az);
                    continue;
                }

                // Apply corrosive damage every 10 ticks (0.5s) to enemies inside the acid rain radius
                if (serverLevel.getGameTime() % 10 == 0) {
                    double rSq = az.radius * az.radius;
                    AABB box = AABB.ofSize(az.center, az.radius * 2.0, 10.0, az.radius * 2.0);
                    List<LivingEntity> enemies = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                            e -> e.isAlive() && !e.isSpectator() && (az.shooter == null || (!e.isAlliedTo(az.shooter) && e != az.shooter)));

                    for (LivingEntity enemy : enemies) {
                        double dx = enemy.getX() - az.center.x;
                        double dz = enemy.getZ() - az.center.z;
                        if (dx * dx + dz * dz <= rSq) {
                            DamageSource source = az.spell != null && az.shooter != null
                                    ? az.spell.getDamageSource(az.acidRainEntity != null ? az.acidRainEntity : az.shooter, az.shooter)
                                    : serverLevel.damageSources().magic();
                            DamageSources.applyDamage(enemy, az.damage, source);

                            serverLevel.sendParticles(ParticleTypes.ITEM_SLIME,
                                    enemy.getX(), enemy.getY() + enemy.getBbHeight() * 0.5, enemy.getZ(),
                                    6, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            }
        }

        // 5. Tick Meteor Storm Zones (Authentic GTBC MeteorStormSpell & MeteorStormEffect, similar to Solar Storm)
        if (!ACTIVE_METEOR_STORMS.isEmpty()) {
            for (Iterator<MeteorStormZone> it = ACTIVE_METEOR_STORMS.iterator(); it.hasNext(); ) {
                MeteorStormZone mz = it.next();
                if (mz.level != serverLevel) continue;

                mz.remainingTicks--;
                if (mz.remainingTicks <= 0) {
                    ACTIVE_METEOR_STORMS.remove(mz);
                    continue;
                }

                // If pinned to a living entity, track its movements
                if (mz.pinnedTarget != null && mz.pinnedTarget.isAlive()) {
                    mz.center = mz.pinnedTarget.position();
                }

                // Ambient fiery ember / smoke particles around the zone
                if (serverLevel.getGameTime() % 6 == 0) {
                    double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                    double r = serverLevel.random.nextDouble() * mz.radius * 0.7;
                    Vec3 pPos = mz.center.add(Math.cos(angle) * r, 0.5 + serverLevel.random.nextDouble() * 2.0, Math.sin(angle) * r);
                    serverLevel.sendParticles(ParticleTypes.LAVA, pPos.x, pPos.y, pPos.z, 2, 0.2, 0.3, 0.2, 0.02);
                }

                mz.ticksUntilNextWave--;
                if (mz.ticksUntilNextWave <= 0) {
                    mz.ticksUntilNextWave = 15; // Waves every 15 ticks (0.75s), matching authentic MeteorStormEffect!

                    // Search for up to 3 valid enemies in radius
                    AABB zoneBox = AABB.ofSize(mz.center, mz.radius * 2.0, 20.0, mz.radius * 2.0);
                    List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, zoneBox,
                            e -> e.isAlive() && !e.isInvulnerable() && !e.isSpectator()
                            && (mz.shooter == null || (!e.isAlliedTo(mz.shooter) && e != mz.shooter))
                    );

                    // Prioritize pinned target first if alive
                    if (mz.pinnedTarget != null && mz.pinnedTarget.isAlive() && !candidates.contains(mz.pinnedTarget)) {
                        candidates.add(0, mz.pinnedTarget);
                    }

                    int meteorsToSpawn = 3;
                    for (int m = 0; m < meteorsToSpawn; m++) {
                        Vec3 targetPos;
                        if (!candidates.isEmpty() && m < candidates.size()) {
                            targetPos = candidates.get(m).position();
                        } else {
                            // Strike random ground point within radius
                            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                            double r = 4.0 + serverLevel.random.nextDouble() * (mz.radius - 4.0);
                            int tx = (int) (mz.center.x + Math.cos(angle) * r);
                            int tz = (int) (mz.center.z + Math.sin(angle) * r);
                            int ty = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(tx, 0, tz)).getY();
                            targetPos = new Vec3(tx, ty, tz);
                        }

                        // Spawn MagicFireball high above (Y + 25 to 35) falling downward towards target
                        Vec3 spawnPos = new Vec3(
                                targetPos.x + (serverLevel.random.nextDouble() - 0.5) * 6.0,
                                targetPos.y + 25.0 + serverLevel.random.nextDouble() * 10.0,
                                targetPos.z + (serverLevel.random.nextDouble() - 0.5) * 6.0
                        );
                        Vec3 dir = targetPos.subtract(spawnPos).normalize();

                        MagicFireball fireball = new MagicFireball(serverLevel, mz.shooter);
                        fireball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                        fireball.shoot(dir.x, dir.y, dir.z, 1.4f, 1.0f);
                        fireball.setDamage(mz.damage);
                        fireball.setExplosionRadius(3.5f);
                        serverLevel.addFreshEntity(fireball);

                        // Sunbeam & flame effect at spawn
                        MagicManager.spawnParticles(serverLevel, ParticleHelper.SUNBEAM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 1, 1, 1, 1, false);
                    }

                    // Sound of meteor volley launching
                    serverLevel.playSound(null, mz.center.x, mz.center.y, mz.center.z,
                            SoundRegistry.FIRE_CAST.get(), SoundSource.PLAYERS, 1.5f, 0.8f);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingGun = (mainHand.getItem() instanceof GunItem) || (offHand.getItem() instanceof GunItem);
        if (!holdingGun || !player.isAlive()) {
            stopPlayerEffects(player);
            return;
        }

        // 1. Tick tracked continuous/cone entities
        CopyOnWriteArrayList<TrackedEntity> list = ACTIVE_ENTITIES.get(uuid);
        if (list != null && !list.isEmpty()) {
            for (Iterator<TrackedEntity> it = list.iterator(); it.hasNext(); ) {
                TrackedEntity te = it.next();
                te.remainingTicks--;
                if (te.remainingTicks <= 0 || !te.entity.isAlive()) {
                    if (te.entity.isAlive()) {
                        te.entity.discard();
                    }
                    list.remove(te);
                }
            }
            if (list.isEmpty()) {
                ACTIVE_ENTITIES.remove(uuid);
            }
        }

        // 2. Tick rapid-fire Blaze Storm bursts
        CopyOnWriteArrayList<BlazeStormBurst> blazeBursts = ACTIVE_BLAZE_BURSTS.get(uuid);
        if (blazeBursts != null && !blazeBursts.isEmpty()) {
            for (Iterator<BlazeStormBurst> it = blazeBursts.iterator(); it.hasNext(); ) {
                BlazeStormBurst burst = it.next();
                burst.ticksUntilNext--;
                if (burst.ticksUntilNext <= 0) {
                    Vec3 aimDir = player.getLookAngle();
                    Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.85));

                    SmallMagicFireball fireball = new SmallMagicFireball(burst.level, player);
                    fireball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                    fireball.setDamage(burst.damage);
                    fireball.setNoGravity(true);
                    fireball.shoot(aimDir);
                    fireball.setDeltaMovement(aimDir.normalize().scale(SpellProjectileFactory.BULLET_SPEED));
                    burst.level.addFreshEntity(fireball);

                    burst.level.sendParticles(ParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, 6, 0.1, 0.1, 0.1, 0.05);
                    burst.level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.8f, 1.2f);

                    burst.remainingFireballs--;
                    burst.ticksUntilNext = 2;

                    if (burst.remainingFireballs <= 0) {
                        blazeBursts.remove(burst);
                    }
                }
            }
            if (blazeBursts.isEmpty()) {
                ACTIVE_BLAZE_BURSTS.remove(uuid);
            }
        }

        // 3. Tick rapid-fire Blood Needles bursts (5-needle spiral)
        CopyOnWriteArrayList<BloodNeedlesBurst> bloodBursts = ACTIVE_BLOOD_BURSTS.get(uuid);
        if (bloodBursts != null && !bloodBursts.isEmpty()) {
            for (Iterator<BloodNeedlesBurst> it = bloodBursts.iterator(); it.hasNext(); ) {
                BloodNeedlesBurst burst = it.next();
                burst.ticksUntilNext--;
                if (burst.ticksUntilNext <= 0) {
                    Vec3 aimDir = player.getLookAngle();
                    Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.85));

                    BloodNeedle needle = new BloodNeedle(burst.level, player);
                    needle.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                    needle.setDamage(burst.damage);
                    needle.setNoGravity(true);
                    needle.setZRot(burst.currentZRot);
                    needle.shoot(aimDir);
                    needle.setDeltaMovement(aimDir.normalize().scale(SpellProjectileFactory.BULLET_SPEED));
                    burst.level.addFreshEntity(needle);

                    burst.level.sendParticles(ParticleTypes.CRIMSON_SPORE, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.1, 0.1, 0.1, 0.05);
                    burst.level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundRegistry.BLOOD_NEEDLE_IMPACT.get(), SoundSource.PLAYERS, 1.4f, 1.3f + (burst.remainingNeedles * 0.08f));

                    burst.currentZRot += 72.0f;
                    burst.remainingNeedles--;
                    burst.ticksUntilNext = 2;

                    if (burst.remainingNeedles <= 0) {
                        bloodBursts.remove(burst);
                    }
                }
            }
            if (bloodBursts.isEmpty()) {
                ACTIVE_BLOOD_BURSTS.remove(uuid);
            }
        }

        // 4. Tick rapid-fire Flaming Barrage bursts
        CopyOnWriteArrayList<FlamingBarrageBurst> flamingBursts = ACTIVE_FLAMING_BURSTS.get(uuid);
        if (flamingBursts != null && !flamingBursts.isEmpty()) {
            for (Iterator<FlamingBarrageBurst> it = flamingBursts.iterator(); it.hasNext(); ) {
                FlamingBarrageBurst burst = it.next();
                burst.ticksUntilNext--;
                if (burst.ticksUntilNext <= 0) {
                    Vec3 aimDir = player.getLookAngle();
                    Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.85));

                    SmallMagicFireball fireball = new SmallMagicFireball(burst.level, player);
                    fireball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                    fireball.setDamage(burst.damage);
                    fireball.setNoGravity(true);
                    fireball.shoot(aimDir);
                    fireball.setDeltaMovement(aimDir.normalize().scale(SpellProjectileFactory.BULLET_SPEED));
                    burst.level.addFreshEntity(fireball);

                    burst.level.sendParticles(ParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, 6, 0.1, 0.1, 0.1, 0.05);
                    burst.level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.8f, 1.4f);

                    burst.remainingFireballs--;
                    burst.ticksUntilNext = 2;

                    if (burst.remainingFireballs <= 0) {
                        flamingBursts.remove(burst);
                    }
                }
            }
            if (flamingBursts.isEmpty()) {
                ACTIVE_FLAMING_BURSTS.remove(uuid);
            }
        }

        // 5. Tick rapid-fire Eldritch Blast bursts
        CopyOnWriteArrayList<EldritchBlastBurst> eldritchBursts = ACTIVE_ELDRITCH_BURSTS.get(uuid);
        if (eldritchBursts != null && !eldritchBursts.isEmpty()) {
            for (Iterator<EldritchBlastBurst> it = eldritchBursts.iterator(); it.hasNext(); ) {
                EldritchBlastBurst burst = it.next();
                burst.ticksUntilNext--;
                if (burst.ticksUntilNext <= 0) {
                    Vec3 aimDir = player.getLookAngle();
                    Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.85));

                    com.ironsspellbullet.spell.InstantBeamHandler.fireSingleEldritchBlast(
                            burst.level, player, spawnPos, aimDir, burst.spell, burst.spellLevel, burst.damage
                    );

                    burst.remainingBlasts--;
                    burst.ticksUntilNext = 3;

                    if (burst.remainingBlasts <= 0) {
                        eldritchBursts.remove(burst);
                    }
                }
            }
            if (eldritchBursts.isEmpty()) {
                ACTIVE_ELDRITCH_BURSTS.remove(uuid);
            }
        }

        // 6. Tick rapid-fire Psychic Bolt bursts (Flaming Barrage style)
        CopyOnWriteArrayList<PsychicBoltBurst> psychicBursts = ACTIVE_PSYCHIC_BURSTS.get(uuid);
        if (psychicBursts != null && !psychicBursts.isEmpty()) {
            for (Iterator<PsychicBoltBurst> it = psychicBursts.iterator(); it.hasNext(); ) {
                PsychicBoltBurst burst = it.next();
                burst.ticksUntilNext--;
                if (burst.ticksUntilNext <= 0) {
                    Vec3 aimDir = player.getLookAngle();
                    Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.85));

                    com.ironsspellbullet.compat.addon.AddonSpellBulletHandler.fireSinglePsychicBolt(
                            burst.level, player, spawnPos, aimDir, burst.damage, burst.spellLevel
                    );

                    burst.remainingBolts--;
                    burst.ticksUntilNext = 2;

                    if (burst.remainingBolts <= 0) {
                        psychicBursts.remove(burst);
                    }
                }
            }
            if (psychicBursts.isEmpty()) {
                ACTIVE_PSYCHIC_BURSTS.remove(uuid);
            }
        }
    }
}
