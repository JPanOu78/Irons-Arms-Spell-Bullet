package com.ironsspellbullet.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe reflection helper for GTBC Geomancy Plus spells (such as Solar Beam).
 * Ensures zero compile-time dependencies while providing 100% authentic rendering
 * and behavior when GTBC Geomancy Plus is installed in the game!
 */
public class GeomancyCompat {

    private static Boolean available = null;

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedSolarBeamEntity");
                available = true;
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Launches the authentic ExtendedSolarBeamEntity directly from the gun's muzzle.
     */
    public static boolean tryLaunchSolarBeam(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, float damage) {
        if (!isAvailable()) return false;
        try {
            Class<?> ggEntities = Class.forName("com.gametechbc.gtbcs_geomancy_plus.init.GGEntities");
            Field holderField = ggEntities.getField("EXTENDED_SOLAR_BEAM");
            Object holder = holderField.get(null);
            Method getMethod = holder.getClass().getMethod("get");
            EntityType<?> entityType = (EntityType<?>) getMethod.invoke(holder);

            Class<?> beamClass = Class.forName("com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedSolarBeamEntity");
            Constructor<?> ctor = beamClass.getConstructor(
                    EntityType.class,
                    net.minecraft.world.level.Level.class,
                    LivingEntity.class,
                    double.class, double.class, double.class,
                    float.class, float.class, int.class
            );

            float yaw = (float) ((shooter.getYHeadRot() + 90.0f) * Math.PI / 180.0);
            float pitch = (float) (-shooter.getXRot() * Math.PI / 180.0);

            Object beam = ctor.newInstance(
                    entityType,
                    level,
                    shooter,
                    origin.x, origin.y, origin.z,
                    yaw, pitch,
                    55
            );

            try {
                Method setHasPlayer = beamClass.getMethod("setHasPlayer", boolean.class);
                setHasPlayer.invoke(beam, true);
            } catch (Throwable ignored) {}

            try {
                Method setDamageMob = beamClass.getMethod("setDamageMob", float.class);
                setDamageMob.invoke(beam, damage);
            } catch (Throwable ignored) {}

            return level.addFreshEntity((Entity) beam);
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }
}
