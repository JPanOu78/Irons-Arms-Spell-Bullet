package com.ironsspellbullet.event;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks weapon switching (hotbar / offhand swap) and spell bullet selection while a
 * Solar Beam fired from a firearm is still channeling.
 * <p>
 * Server: tracked by an explicit lock map (slot + expiry game time).
 * Client: detected by scanning for an active authentic Solar Beam entity whose synced caster ID is the local player,
 * so no extra network packet is required.
 */
@EventBusSubscriber(modid = IronsArmsSpellBulletMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SolarBeamLockManager {

    public static final ResourceLocation c_SolarBeamEntityId =
            ResourceLocation.fromNamespaceAndPath("gtbcs_geomancy_plus", "extended_solar_beam");

    private record LockEntry(int slot, long endGameTime) {}

    private static final Map<UUID, LockEntry> s_ServerLocks = new ConcurrentHashMap<>();
    private static Method s_GetCasterIdMethod;

    private SolarBeamLockManager() {}

    // =========================================================================
    // SERVER LOCK STATE
    // =========================================================================

    /**
     * Solar Beam Variant toggle:
     * Mode A: Original channeling beam attached to player crosshair with hotbar swap lock.
     * Mode B: Stationary placed magic beam in the world. Charges up at fixed muzzle position,
     *         fires forward in locked direction, allowing player free movement, weapon swap, and reloading.
     */
    public static final boolean USE_VARIANT_B = true;

    public static void lock(Player player, int durationTicks) {
        if (USE_VARIANT_B) return; // In Variant B, no hotbar locking is applied
        if (player == null || player.level().isClientSide()) return;
        long end = player.level().getGameTime() + Math.max(1, durationTicks);
        s_ServerLocks.put(player.getUUID(), new LockEntry(player.getInventory().selected, end));
    }

    /**
     * Returns true while the player is channeling a firearm Solar Beam (works on both logical sides).
     */
    public static boolean isLocked(Player player) {
        if (USE_VARIANT_B) return false; // In Variant B, never locked
        if (player == null) return false;
        if (!player.level().isClientSide()) {
            LockEntry entry = s_ServerLocks.get(player.getUUID());
            if (entry == null) return false;
            if (player.level().getGameTime() >= entry.endGameTime() || !hasActiveBeam(player)) {
                s_ServerLocks.remove(player.getUUID());
                return false;
            }
            return true;
        }
        return hasActiveBeam(player);
    }

    /**
     * Scans for an alive authentic Solar Beam entity owned by this player.
     */
    public static boolean hasActiveBeam(Player player) {
        if (player == null) return false;
        try {
            List<Entity> beams = player.level().getEntities((Entity) null, player.getBoundingBox().inflate(6.0),
                    e -> e.isAlive() && c_SolarBeamEntityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())));
            for (Entity beam : beams) {
                if (getCasterId(beam) == player.getId()) return true;
            }
        } catch (Exception e) {
            IronsArmsSpellBulletMod.LOGGER.debug("Solar Beam lock scan failed", e);
        }
        return false;
    }

    private static int getCasterId(Entity beam) {
        try {
            if (s_GetCasterIdMethod == null || !s_GetCasterIdMethod.getDeclaringClass().isInstance(beam)) {
                s_GetCasterIdMethod = beam.getClass().getMethod("getCasterID");
            }
            Object res = s_GetCasterIdMethod.invoke(beam);
            return res instanceof Integer id ? id : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // =========================================================================
    // SERVER ENFORCEMENT
    // =========================================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LockEntry entry = s_ServerLocks.get(player.getUUID());
        if (entry == null) return;
        if (!isLocked(player)) return;

        // Force the hotbar back onto the firing weapon
        if (player.getInventory().selected != entry.slot()) {
            player.getInventory().selected = entry.slot();
            player.connection.send(new ClientboundSetCarriedItemPacket(entry.slot()));
        }
    }

    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        s_ServerLocks.remove(event.getEntity().getUUID());
    }
}

