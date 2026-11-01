package com.ironsspellbullet.network;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(IronsArmsSpellBulletMod.MODID);
        registrar.playToServer(
                ServerboundSelectSpellBulletPacket.TYPE,
                ServerboundSelectSpellBulletPacket.STREAM_CODEC,
                ServerboundSelectSpellBulletPacket::handle
        );
    }
}
