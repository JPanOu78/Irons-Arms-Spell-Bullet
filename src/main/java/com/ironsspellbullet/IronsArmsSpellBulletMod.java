package com.ironsspellbullet;

import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.config.SpellBulletClientConfig;
import com.ironsspellbullet.event.GunplayEvents;
import com.ironsspellbullet.recipe.ModRecipes;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Iron's Arms Spell Bullet Mod
 *
 * Bridges Iron's Arms 'n Artifice firearms with Iron's Spells 'n Spellbooks magic,
 * allowing players to imbue standard Iron's Arms bullets with spells and fire them through firearms.
 */
@Mod(IronsArmsSpellBulletMod.MODID)
public class IronsArmsSpellBulletMod {
    public static final String MODID = "irons_spell_bullet";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IronsArmsSpellBulletMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Iron's Arms Spell Bullet initializing...");

        // Register client config
        modContainer.registerConfig(ModConfig.Type.CLIENT, SpellBulletClientConfig.SPEC);

        // Register interactive client config GUI screen (Mods -> Config button)
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (container, screen) -> new com.ironsspellbullet.client.gui.SpellBulletHudConfigScreen(screen)
            );
        }

        // Register registries (uses Iron's Arms native Bullet item directly — no separate custom item needed)
        ModDataComponents.register(modEventBus);
        ModRecipes.register(modEventBus);
        com.ironsspellbullet.network.ModNetwork.register(modEventBus);

        // Register game event handlers
        NeoForge.EVENT_BUS.register(new GunplayEvents());
        NeoForge.EVENT_BUS.register(new com.ironsspellbullet.event.ActiveSpellEntityManager());

        LOGGER.info("Iron's Arms Spell Bullet initialized successfully!");
    }
}
