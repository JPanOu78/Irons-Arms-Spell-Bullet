package com.ironsspellbullet.component;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, IronsArmsSpellBulletMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpellChamberData>> LOADED_SPELLS =
            DATA_COMPONENTS.register("loaded_spells", () ->
                    DataComponentType.<SpellChamberData>builder()
                            .persistent(SpellChamberData.CODEC)
                            .networkSynchronized(SpellChamberData.STREAM_CODEC)
                            .build()
            );

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}

