package com.ironsspellbullet.client;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.item.ModItems;
import com.ironsspellbullet.item.SpellBulletItem;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = IronsArmsSpellBulletMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                SpellData data = SpellBulletItem.getSpellData(stack);
                if (data != null && !data.equals(SpellData.EMPTY) && data.getSpell() != null) {
                    SchoolType school = data.getSpell().getSchoolType();
                    if (school != null && school.getTargetingColor() != null) {
                        return Utils.packRGB(school.getTargetingColor());
                    }
                }
            }
            return 0xFFFFFF;
        }, ModItems.SPELL_BULLET.get());
    }
}
