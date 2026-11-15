package com.ironsspellbullet.client;

import com.ironsspellbullet.client.gui.SpellBulletHudConfigScreen;
import com.ironsspellbullet.config.SpellBulletClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only proxy for registering client configs, extension points, and GUI screens.
 * Strictly isolated from dedicated server classloading.
 */
public class ClientProxy {

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        // Register client config
        modContainer.registerConfig(ModConfig.Type.CLIENT, SpellBulletClientConfig.SPEC);

        // Register interactive client config GUI screen (Mods -> Config button)
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, screen) -> new SpellBulletHudConfigScreen(screen)
        );
    }
}

