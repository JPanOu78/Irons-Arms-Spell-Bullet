package com.ironsspellbullet.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the spell bullets currently loaded in a firearm's chamber/cylinder.
 */
public record SpellChamberData(List<SpellSlotEntry> loadedSpells) {
    public static final SpellChamberData EMPTY = new SpellChamberData(Collections.emptyList());

    public static final Codec<SpellChamberData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    SpellSlotEntry.CODEC.listOf().fieldOf("loaded_spells").forGetter(SpellChamberData::loadedSpells)
            ).apply(instance, SpellChamberData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellChamberData> STREAM_CODEC = StreamCodec.composite(
            SpellSlotEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            SpellChamberData::loadedSpells,
            SpellChamberData::new
    );

    public boolean isEmpty() {
        return loadedSpells == null || loadedSpells.isEmpty();
    }

    public int count() {
        return loadedSpells == null ? 0 : loadedSpells.size();
    }

    /**
     * Pops the next bullet from the chamber (FIFO: First-In-First-Out).
     * Returns a pair of the popped entry and the new SpellChamberData.
     */
    public PopResult popNext() {
        if (isEmpty()) {
            return new PopResult(SpellSlotEntry.EMPTY, EMPTY);
        }
        List<SpellSlotEntry> nextList = new ArrayList<>(loadedSpells);
        SpellSlotEntry popped = nextList.removeFirst();
        return new PopResult(popped, new SpellChamberData(Collections.unmodifiableList(nextList)));
    }

    /**
     * Appends bullets to the chamber.
     */
    public SpellChamberData withAdded(List<SpellSlotEntry> newEntries) {
        List<SpellSlotEntry> nextList = new ArrayList<>(this.loadedSpells != null ? this.loadedSpells : Collections.emptyList());
        nextList.addAll(newEntries);
        return new SpellChamberData(Collections.unmodifiableList(nextList));
    }

    public record PopResult(SpellSlotEntry popped, SpellChamberData remaining) {}

    /**
     * Individual bullet entry in the chamber.
     */
    public record SpellSlotEntry(String spellId, int spellLevel) {
        public static final SpellSlotEntry EMPTY = new SpellSlotEntry("", 0);

        public static final Codec<SpellSlotEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("spell_id").forGetter(SpellSlotEntry::spellId),
                        Codec.INT.fieldOf("spell_level").forGetter(SpellSlotEntry::spellLevel)
                ).apply(instance, SpellSlotEntry::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, SpellSlotEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                SpellSlotEntry::spellId,
                ByteBufCodecs.VAR_INT,
                SpellSlotEntry::spellLevel,
                SpellSlotEntry::new
        );

        public boolean hasSpell() {
            return spellId != null && !spellId.isEmpty() && spellLevel > 0;
        }

        public AbstractSpell getSpell() {
            if (!hasSpell()) return SpellRegistry.none();
            return SpellRegistry.getSpell(ResourceLocation.parse(spellId));
        }

        public SpellData toSpellData() {
            if (!hasSpell()) return SpellData.EMPTY;
            return new SpellData(getSpell(), spellLevel);
        }

        public static SpellSlotEntry fromSpellData(SpellData spellData) {
            if (spellData == null || spellData.equals(SpellData.EMPTY) || spellData.getSpell() == null || spellData.getSpell().equals(SpellRegistry.none())) {
                return EMPTY;
            }
            return new SpellSlotEntry(spellData.getSpell().getSpellResource().toString(), spellData.getLevel());
        }
    }
}
