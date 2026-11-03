package com.ironsspellbullet.event;

import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Iterator;
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

    public static class ThunderstormBarrage {
        final ServerLevel level;
        final LivingEntity shooter;
        final Vec3 center;
        final int spellLevel;
        int remainingStrikes;
        int ticksUntilNext;
        final float radius;

        public ThunderstormBarrage(ServerLevel level, LivingEntity shooter, Vec3 center, int spellLevel, int count, float radius) {
            this.level = level;
            this.shooter = shooter;
            this.center = center;
            this.spellLevel = spellLevel;
            this.remainingStrikes = count;
            this.ticksUntilNext = 6;
            this.radius = radius;
        }
    }

    private static final Map<UUID, CopyOnWriteArrayList<TrackedEntity>> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BlazeStormBurst>> ACTIVE_BLAZE_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BloodNeedlesBurst>> ACTIVE_BLOOD_BURSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<FlamingBarrageBurst>> ACTIVE_FLAMING_BURSTS = new ConcurrentHashMap<>();

    // Tactical World Airstrikes
    private static final CopyOnWriteArrayList<StarfallBombardment> ACTIVE_STARFALLS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ThunderstormBarrage> ACTIVE_THUNDERSTORMS = new CopyOnWriteArrayList<>();

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
     * Queues a localized thunderstorm barrage calling down multiple lightning strikes over the impact area.
     */
    public static void queueThunderstormBarrage(ServerLevel level, LivingEntity shooter, Vec3 center, int spellLevel, int strikeCount, float radius) {
        if (level == null || center == null || strikeCount <= 0) return;
        ACTIVE_THUNDERSTORMS.add(new ThunderstormBarrage(level, shooter, center, spellLevel, strikeCount, radius));
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
        if (event.getEntity() instanceof Player player) {
            EquipmentSlot slot = event.getSlot();
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                // If the player unequipped or swapped weapons, stop active spell effects immediately!
                stopPlayerEffects(player);
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // 1. Tick Starfall Celestial Bombardments
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
                    comet.shoot(flyDir, 0.08f);
                    comet.setDeltaMovement(flyDir.scale(2.2));
                    comet.setDamage(b.damage);
                    comet.setExplosionRadius(2.0f + b.spellLevel * 0.25f);
                    serverLevel.addFreshEntity(comet);

                    serverLevel.playSound(null, cometSpawn.x, cometSpawn.y, cometSpawn.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.5f, 0.7f + serverLevel.random.nextFloat() * 0.3f);
                    MagicManager.spawnParticles(serverLevel, ParticleHelper.COMET_FOG, cometSpawn.x, cometSpawn.y, cometSpawn.z, 15, 0.8, 0.8, 0.8, 0.08, false);

                    b.remainingComets--;
                    b.ticksUntilNext = 3; // 1 comet every 3 ticks
                    if (b.remainingComets <= 0) {
                        ACTIVE_STARFALLS.remove(b);
                    }
                }
            }
        }

        // 2. Tick Thunderstorm Barrages
        if (!ACTIVE_THUNDERSTORMS.isEmpty()) {
            for (Iterator<ThunderstormBarrage> it = ACTIVE_THUNDERSTORMS.iterator(); it.hasNext(); ) {
                ThunderstormBarrage tb = it.next();
                if (tb.level != serverLevel) continue;
                tb.ticksUntilNext--;
                if (tb.ticksUntilNext <= 0) {
                    double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                    double r = serverLevel.random.nextDouble() * tb.radius;
                    Vec3 strikePos = tb.center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);

                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (bolt != null) {
                        bolt.moveTo(strikePos);
                        if (tb.shooter instanceof ServerPlayer sp) {
                            bolt.setCause(sp);
                        }
                        serverLevel.addFreshEntity(bolt);
                    }
                    serverLevel.playSound(null, strikePos.x, strikePos.y, strikePos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.0f);

                    tb.remainingStrikes--;
                    tb.ticksUntilNext = 6 + serverLevel.random.nextInt(4);
                    if (tb.remainingStrikes <= 0) {
                        ACTIVE_THUNDERSTORMS.remove(tb);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player) {
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
                        Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.5));

                        SmallMagicFireball fireball = new SmallMagicFireball(burst.level, player);
                        fireball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                        fireball.setDamage(burst.damage);
                        fireball.setNoGravity(true);
                        fireball.shoot(aimDir);
                        fireball.setDeltaMovement(aimDir.normalize().scale(3.2));
                        burst.level.addFreshEntity(fireball);

                        burst.level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.8f, 1.2f);

                        burst.remainingFireballs--;
                        burst.ticksUntilNext = 2; // interval of 2 ticks between shots

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
                        Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.5));

                        BloodNeedle needle = new BloodNeedle(burst.level, player);
                        needle.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                        needle.setDamage(burst.damage);
                        needle.setNoGravity(true);
                        needle.setZRot(burst.currentZRot);
                        needle.shoot(aimDir);
                        needle.setDeltaMovement(aimDir.normalize().scale(3.4));
                        burst.level.addFreshEntity(needle);

                        burst.level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundRegistry.BLOOD_NEEDLE_IMPACT.get(), SoundSource.PLAYERS, 1.4f, 1.3f + (burst.remainingNeedles * 0.08f));

                        burst.currentZRot += 72.0f; // Spiral rotation
                        burst.remainingNeedles--;
                        burst.ticksUntilNext = 2; // interval of 2 ticks between shots

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
                        Vec3 spawnPos = player.getEyePosition().add(aimDir.scale(0.5));

                        SmallMagicFireball fireball = new SmallMagicFireball(burst.level, player);
                        fireball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                        fireball.setDamage(burst.damage);
                        fireball.setNoGravity(true);
                        fireball.shoot(aimDir);
                        fireball.setDeltaMovement(aimDir.normalize().scale(3.2));
                        burst.level.addFreshEntity(fireball);

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
}
