package com.ironsspellbullet;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Iron's Arms Spell Bullet Mod
 * 
 * Bridges Iron's Arms 'n Artifice firearms with Iron's Spells 'n Spellbooks magic,
 * allowing players and firearms to channel, imbue, and fire spells through firearms and ammunition.
 */
@Mod(IronsArmsSpellBulletMod.MODID)
public class IronsArmsSpellBulletMod {
    public static final String MODID = "irons_spell_bullet";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IronsArmsSpellBulletMod(IEventBus modEventBus) {
        LOGGER.info("Iron's Arms Spell Bullet initializing...");

        // Register event buses
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Iron's Arms Spell Bullet initialized successfully!");
    }
}
