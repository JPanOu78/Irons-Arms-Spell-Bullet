package com.ironsspellbullet;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.event.GunplayEvents;
import com.ironsspellbullet.item.ModItems;
import com.ironsspellbullet.recipe.ModRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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

        // Register registries
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipes.register(modEventBus);

        // Creative mode tab integration
        modEventBus.addListener(this::addCreative);

        // Register game event handlers
        NeoForge.EVENT_BUS.register(new GunplayEvents());

        LOGGER.info("Iron's Arms Spell Bullet initialized successfully!");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.SPELL_BULLET);
        }
    }
}
