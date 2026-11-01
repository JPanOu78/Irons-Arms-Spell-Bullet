package com.ironsspellbullet.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which spell bullet is currently selected by the player via the quick select wheel.
 */
public class ActiveSpellBulletManager {
    private static final Map<UUID, String> SELECTED_SPELLS = new ConcurrentHashMap<>();

    public static void setSelectedSpell(UUID playerUUID, String spellId) {
        if (spellId == null || spellId.isEmpty()) {
            SELECTED_SPELLS.remove(playerUUID);
        } else {
            SELECTED_SPELLS.put(playerUUID, spellId);
        }
    }

    public static String getSelectedSpell(UUID playerUUID) {
        return SELECTED_SPELLS.get(playerUUID);
    }

    public static void clear(UUID playerUUID) {
        SELECTED_SPELLS.remove(playerUUID);
    }
}
