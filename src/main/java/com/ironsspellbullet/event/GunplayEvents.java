package com.ironsspellbullet.event;

import com.ironsspellbullet.IronsArmsSpellBulletMod;
import com.ironsspellbullet.component.ModDataComponents;
import com.ironsspellbullet.component.SpellChamberData;
import com.ironsspellbullet.spell.SpellBulletEffectHandler;
import com.ironsspellbullet.spell.SpellProjectileFactory;
import io.redspace.irons_artifice.api.BulletImpactEvent;
import io.redspace.irons_artifice.api.ComposeShotEvent;
import io.redspace.irons_artifice.api.GunShootEvent;
import io.redspace.irons_artifice.data.ComponentType;
import io.redspace.irons_artifice.data.ShotComponents;
import io.redspace.irons_artifice.data.Value;
import io.redspace.irons_artifice.entity.Bullet;
import io.redspace.irons_artifice.gun.ShotProfile;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GunplayEvents {

    /**
     * Shot component that travels with ShotProfile and Bullet entity.
     */
    public static final ComponentType<SpellChamberData.SpellSlotEntry> SHOT_SPELL =
            new ComponentType<>(
                    ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "shot_spell"),
                    () -> SpellChamberData.SpellSlotEntry.EMPTY
            );

    public static final ComponentType<Boolean> LAUNCHED_AS_PROJECTILE =
            new ComponentType<>(
                    ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "launched_projectile"),
                    () -> false
            );

    @SubscribeEvent
    public void onComposeShot(ComposeShotEvent event) {
        ShotProfile profile = event.getShotProfile();
        if (profile == null) return;

        ItemStack gunStack = profile.itemStack();
        if (gunStack == null || gunStack.isEmpty()) return;

        SpellChamberData chamber = gunStack.get(ModDataComponents.LOADED_SPELLS.get());
        if (chamber == null || chamber.isEmpty()) return;

        // Pop next round from chamber
        SpellChamberData.PopResult pop = chamber.popNext();
        gunStack.set(ModDataComponents.LOADED_SPELLS.get(), pop.remaining());

        SpellChamberData.SpellSlotEntry entry = pop.popped();
        if (entry != null && entry.hasSpell()) {
            // Attach spell to shot profile
            profile.components().set(SHOT_SPELL, entry);

            // Nullify firearm physical damage for spell bullets so only spell damage is dealt
            profile.components().set(ShotComponents.DAMAGE, Value.of(0.0));

            // Add elemental trail particles to the bullet
            AbstractSpell spell = entry.getSpell();
            if (spell != null) {
                SchoolType school = spell.getSchoolType();
                ParticleOptions trailParticle = SpellBulletEffectHandler.getTrailParticleForSchool(school);
                if (trailParticle != null && profile.components().has(ShotComponents.PARTICLE_TRAIL)) {
                    profile.components().getOrDefault(ShotComponents.PARTICLE_TRAIL).add(trailParticle);
                }
            }
        }
    }

    @SubscribeEvent
    public void onGunShootPre(GunShootEvent.Pre event) {
        LivingEntity shooter = event.getEntity();
        if (shooter == null || !(shooter.level() instanceof ServerLevel serverLevel)) return;

        ShotProfile profile = event.getShotProfile();
        if (profile == null) return;

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            AbstractSpell spell = entry.getSpell();
            int level = entry.spellLevel();

            // Try launching directly from gun muzzle at bullet speed for projectile-type spells
            boolean launched = SpellProjectileFactory.tryLaunchProjectile(
                    serverLevel,
                    shooter,
                    event.getOrigin(),
                    event.getDirection(),
                    spell,
                    level
            );

            if (launched) {
                profile.components().set(LAUNCHED_AS_PROJECTILE, true);
            }
        }
    }

    @SubscribeEvent
    public void onBulletImpact(BulletImpactEvent event) {
        Bullet bullet = event.getBullet();
        if (bullet == null || bullet.level().isClientSide()) return;

        ShotProfile profile = bullet.getProfile();
        if (profile == null) return;

        // If the spell was already launched as a supersonic projectile entity from the muzzle,
        // cancel the bullet impact completely so zero firearm damage or effects occur
        Boolean alreadyLaunched = profile.components().getOrDefault(LAUNCHED_AS_PROJECTILE);
        if (Boolean.TRUE.equals(alreadyLaunched)) {
            event.setCanceled(true);
            bullet.discard();
            return;
        }

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            LivingEntity shooter = bullet.getOwner() instanceof LivingEntity living ? living : null;
            SpellBulletEffectHandler.onSpellBulletHit(
                    bullet.level(),
                    event.getRayTraceResult(),
                    bullet,
                    shooter,
                    entry.toSpellData()
            );

            // Cancel the normal bullet impact so zero firearm physical damage is applied
            event.setCanceled(true);
            bullet.discard();
        }
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) return;

        SpellChamberData chamber = stack.get(ModDataComponents.LOADED_SPELLS.get());
        if (chamber == null || chamber.isEmpty()) return;

        List<Component> tooltips = event.getToolTip();
        tooltips.add(Component.translatable("tooltip.irons_spell_bullet.chamber_header")
                .withStyle(ChatFormatting.GOLD));

        // Group consecutive/identical spells for clean display
        Map<String, Integer> spellCounts = new LinkedHashMap<>();
        for (SpellChamberData.SpellSlotEntry entry : chamber.loadedSpells()) {
            if (entry.hasSpell()) {
                AbstractSpell spell = entry.getSpell();
                String displayName = spell != null ? spell.getDisplayName(event.getEntity()).getString() + " " + entry.spellLevel() : entry.spellId();
                spellCounts.put(displayName, spellCounts.getOrDefault(displayName, 0) + 1);
            } else {
                spellCounts.put("Normal Bullet", spellCounts.getOrDefault("Normal Bullet", 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : spellCounts.entrySet()) {
            MutableComponent line = Component.literal(" • " + entry.getKey());
            if (entry.getValue() > 1) {
                line.append(Component.literal(" (x" + entry.getValue() + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.withStyle(ChatFormatting.AQUA);
            tooltips.add(line);
        }
    }
}
