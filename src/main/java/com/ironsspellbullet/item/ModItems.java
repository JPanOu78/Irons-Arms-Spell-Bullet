package com.ironsspellbullet.item;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IronsArmsSpellBulletMod.MODID);

    public static final DeferredItem<SpellBulletItem> SPELL_BULLET = ITEMS.register("spell_bullet",
            () -> new SpellBulletItem(new Item.Properties().stacksTo(64)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
