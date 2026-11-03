package com.ironsspellbullet.event;

import io.redspace.irons_artifice.item.GunItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * attached to or spawned by shooters.
 *
 * Ensures that if a player switches weapons, holsters their firearm, changes ammo,
 * or releases the trigger, all active cone/spray entities are immediately terminated.
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

    private static final Map<UUID, CopyOnWriteArrayList<TrackedEntity>> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    /**
     * Registers a spell entity (cone, spray, beam) to a shooter with a maximum burst duration.
     * Default max duration is 8 ticks (0.4s) for a firearm spray puff.
     */
    public static void registerEntity(LivingEntity shooter, Entity entity, int maxTicks) {
        if (shooter == null || entity == null) return;
        UUID uuid = shooter.getUUID();
        ACTIVE_ENTITIES.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            // Discard older entities from same shooter to prevent stacking
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
     * Immediately stops and discards all active spell entities for the given shooter.
     */
    public static void stopPlayerEffects(LivingEntity shooter) {
        if (shooter == null) return;
        stopPlayerEffects(shooter.getUUID());
    }

    public static void stopPlayerEffects(UUID playerUUID) {
        if (playerUUID == null) return;
        CopyOnWriteArrayList<TrackedEntity> list = ACTIVE_ENTITIES.remove(playerUUID);
        if (list != null) {
            for (TrackedEntity te : list) {
                if (te.entity.isAlive()) {
                    te.entity.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            EquipmentSlot slot = event.getSlot();
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                // If the player unequipped or swapped weapons, stop active spell spray immediately!
                stopPlayerEffects(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUUID();
            CopyOnWriteArrayList<TrackedEntity> list = ACTIVE_ENTITIES.get(uuid);
            if (list == null || list.isEmpty()) return;

            // 1. If player is no longer holding a GunItem or is not alive, stop all effects immediately!
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            boolean holdingGun = (mainHand.getItem() instanceof GunItem) || (offHand.getItem() instanceof GunItem);
            if (!holdingGun || !player.isAlive()) {
                stopPlayerEffects(player);
                return;
            }

            // 2. Decrement remaining burst ticks and discard expired entities
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
    }
}
