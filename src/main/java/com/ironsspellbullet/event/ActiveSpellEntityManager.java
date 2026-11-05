package com.ironsspellbullet.event;

import com.ironsspellbullet.spell.SpellProjectileFactory;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.spells.LightningStrike;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
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

    private static final Map<UUID, CopyOnWriteArrayList<TrackedEntity>> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BlazeStormBurst>> ACTIVE_BLAZE_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BloodNeedlesBurst>> ACTIVE_BLOOD_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<FlamingBarrageBurst>> ACTIVE_FLAMING_BURSTS = new ConcurrentHashMap<>();

    // Tactical World Airstrikes
    private static final CopyOnWriteArrayList<StarfallBombardment> ACTIVE_STARFALLS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ThunderstormZone> ACTIVE_THUNDERSTORMS = new CopyOnWriteArrayList<>();

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
    }
}
