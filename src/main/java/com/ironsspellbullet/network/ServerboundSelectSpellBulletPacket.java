package com.ironsspellbullet.network;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.event.ActiveSpellBulletManager;
import com.ironsspellbullet.event.ActiveSpellEntityManager;
import com.ironsspellbullet.event.ReloadHelper;
import io.redspace.irons_artifice.item.GunItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundSelectSpellBulletPacket(String spellId) implements CustomPacketPayload {
    public static final Type<ServerboundSelectSpellBulletPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "select_spell_bullet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSelectSpellBulletPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ServerboundSelectSpellBulletPacket::spellId,
                    ServerboundSelectSpellBulletPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundSelectSpellBulletPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ActiveSpellBulletManager.setSelectedSpell(player.getUUID(), packet.spellId());

                // Immediately terminate any ongoing continuous/cone/spray effects when ammo is switched
                ActiveSpellEntityManager.stopPlayerEffects(player);

                // In Creative Mode: instantly update the held firearm's chamber to the newly selected spell
                if (player.hasInfiniteMaterials()) {
                    ItemStack mainHand = player.getMainHandItem();
                    if (mainHand.getItem() instanceof GunItem) {
                        ReloadHelper.setCreativeChamber(player, mainHand, packet.spellId());
                    }
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof GunItem) {
                        ReloadHelper.setCreativeChamber(player, offHand, packet.spellId());
                    }
                }
            }
        });
    }
}
