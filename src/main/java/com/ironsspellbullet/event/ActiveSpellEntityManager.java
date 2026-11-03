package com.ironsspellbullet.event;

import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks and manages active continuous/cone/spray entities (e.g. Fire Breath, Cone of Cold)
 * and rapid-fire spell bursts (e.g. Blaze Storm).
 *
 * Ensures that if a player switches weapons, holsters their firearm, changes ammo,
 * or releases the trigger, all active cone/spray entities and queued bursts are immediately terminated.
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

    private static final Map<UUID, CopyOnWriteArrayList<TrackedEntity>> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, CopyOnWriteArrayList<BlazeStormBurst>> ACTIVE_BLAZE_BURSTS = new ConcurrentHashMap<>();

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

        // Cancel any queued rapid-fire bursts
        ACTIVE_BLAZE_BURSTS.remove(playerUUID);
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
            CopyOnWriteArrayList<BlazeStormBurst> bursts = ACTIVE_BLAZE_BURSTS.get(uuid);
            if (bursts != null && !bursts.isEmpty()) {
                for (Iterator<BlazeStormBurst> it = bursts.iterator(); it.hasNext(); ) {
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
                            bursts.remove(burst);
                        }
                    }
                }
                if (bursts.isEmpty()) {
                    ACTIVE_BLAZE_BURSTS.remove(uuid);
                }
            }
        }
    }
}
