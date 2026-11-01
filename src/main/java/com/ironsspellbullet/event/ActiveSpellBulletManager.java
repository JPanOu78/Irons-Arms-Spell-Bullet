package com.ironsspellbullet.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which bullet type (normal or specific spell) is currently selected by the player.
 */
public class ActiveSpellBulletManager {
    public static final String NORMAL_BULLET = "normal";

    private static final Map<UUID, String> SELECTED_BULLETS = new ConcurrentHashMap<>();

    public static void setSelectedSpell(UUID playerUUID, String bulletId) {
        if (bulletId == null || bulletId.isEmpty()) {
            SELECTED_BULLETS.put(playerUUID, NORMAL_BULLET);
        } else {
            SELECTED_BULLETS.put(playerUUID, bulletId);
        }
    }

    public static String getSelectedSpell(UUID playerUUID) {
        return SELECTED_BULLETS.getOrDefault(playerUUID, NORMAL_BULLET);
    }

    public static void clear(UUID playerUUID) {
        SELECTED_BULLETS.remove(playerUUID);
    }
}
