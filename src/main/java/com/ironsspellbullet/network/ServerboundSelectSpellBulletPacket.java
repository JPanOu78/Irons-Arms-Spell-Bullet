package com.ironsspellbullet.network;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.event.ActiveSpellBulletManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            }
        });
    }
}
