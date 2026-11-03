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
import io.redspace.irons_artifice.data.ParticleStack;
import io.redspace.irons_artifice.data.ShotComponents;
import io.redspace.irons_artifice.data.Value;
import io.redspace.irons_artifice.entity.Bullet;
import io.redspace.irons_artifice.gun.ShotProfile;
import io.redspace.irons_artifice.item.GunItem;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.network.casting.OnClientCastPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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

    public static final ComponentType<java.util.UUID> SHOT_ID =
            new ComponentType<>(
                    ResourceLocation.fromNamespaceAndPath(IronsArmsSpellBulletMod.MODID, "shot_id"),
                    () -> null
            );

    @SubscribeEvent
    public void onComposeShot(ComposeShotEvent event) {
        ShotProfile profile = event.getShotProfile();
        if (profile == null) return;

        ItemStack gunStack = profile.itemStack();
        if (gunStack == null || gunStack.isEmpty()) return;

        SpellChamberData chamber = gunStack.get(ModDataComponents.LOADED_SPELLS.get());
        if (chamber == null || chamber.isEmpty()) return;

        // Peek next round from chamber without popping it
        SpellChamberData.SpellSlotEntry entry = chamber.peekNext();
        if (entry != null && entry.hasSpell()) {
            // Attach spell to shot profile
            profile.components().set(SHOT_SPELL, entry);

            // Nullify firearm physical damage for spell bullets so only spell damage is dealt
            profile.components().set(ShotComponents.DAMAGE, Value.of(0.0));

            // Check if this spell is a projectile-type or beam-type spell.
            if (SpellProjectileFactory.isProjectileSpell(entry.getSpell())) {
                // For projectile and beam spells (Fireball, Magma bomb, Firebolt, Icicle, Solar Beam, Ray of Frost, etc.):
                // Launched directly as authentic magic entities/beams from the muzzle.
                // Suppress firearm bullet entity and clear firearm tracer line so only magic entity is visible!
                profile.components().set(LAUNCHED_AS_PROJECTILE, true);
                profile.components().set(ShotComponents.PARTICLE_TRAIL, new ParticleStack());
            } else {
                // For targeted / impact spells (e.g. Sunbeam, Lightning Bolt, Blight, Raise Dead):
                // DO NOT clear PARTICLE_TRAIL and DO NOT suppress Bullet entity!
                // The bullet and its tracer ARE rendered in flight, and when it impacts the target/block,
                // that impact serves as the TRIGGER to unleash the spell at hitPos!
                profile.components().set(SHOT_ID, java.util.UUID.randomUUID());
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Bullet bullet) {
            ShotProfile profile = bullet.getProfile();
            if (profile != null && Boolean.TRUE.equals(profile.components().getOrDefault(LAUNCHED_AS_PROJECTILE))) {
                // If the spell was already launched as a supersonic magic projectile from the muzzle,
                // cancel adding the firearm Bullet entity to the level entirely so it never renders or exists!
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onGunShootPre(GunShootEvent.Pre event) {
        LivingEntity shooter = event.getEntity();
        if (shooter == null || !(shooter.level() instanceof ServerLevel serverLevel)) return;

        ShotProfile profile = event.getShotProfile();
        if (profile == null) return;

        // Pop the round from chamber now that the gun is officially firing on the server
        ItemStack gunStack = profile.itemStack();
        if (gunStack != null && !gunStack.isEmpty()) {
            SpellChamberData chamber = gunStack.get(ModDataComponents.LOADED_SPELLS.get());
            if (chamber != null && !chamber.isEmpty()) {
                SpellChamberData.PopResult pop = chamber.popNext();
                gunStack.set(ModDataComponents.LOADED_SPELLS.get(), pop.remaining());
            }
        }

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            AbstractSpell spell = entry.getSpell();
            int level = entry.spellLevel();

            int projectileCount = Math.max(1, (int) Math.round(profile.value(ShotComponents.PROJECTILE_COUNT)));
            float spread = io.redspace.irons_artifice.item.GunplayManager.getSpreadForEntity(profile, shooter);

            // Shotgun balance: -3 Spell Level penalty for shotguns
            int effectiveLevel = (projectileCount > 1) ? Math.max(1, level - 3) : level;

            // Shotgun Heavy Spell Handling:
            // Heavy/Ultimate/AOE spells (Magma Bomb, Fireball, Black Hole, etc.) fire as 1 concentrated Slug Core instead of 7-8 copies!
            if (projectileCount > 1 && SpellProjectileFactory.isHeavySpell(spell.getSpellResource().getPath().toLowerCase())) {
                projectileCount = 1;
                spread = 0.0f;
            }

            // Post official SpellOnCastEvent so Iron's Spells and modded ecosystem recognize shooting as casting a spell!
            if (shooter instanceof Player player) {
                SpellOnCastEvent castEvent = new SpellOnCastEvent(
                        player,
                        spell.getSpellId(),
                        effectiveLevel,
                        0, // Mana cost = 0 (free mana for firearms)
                        spell.getSchoolType(),
                        CastSource.SCROLL // SCROLL: does not consume mana, does not trigger spellbook cooldowns
                );
                NeoForge.EVENT_BUS.post(castEvent);
                effectiveLevel = castEvent.getSpellLevel();

                if (player instanceof ServerPlayer serverPlayer) {
                    try {
                        PacketDistributor.sendToPlayer(serverPlayer, new OnClientCastPacket(
                                spell.getSpellId(),
                                effectiveLevel,
                                CastSource.SCROLL,
                                null
                        ));
                    } catch (Throwable ignored) {}
                }
            }

            // Try launching directly from gun muzzle at bullet speed for projectile-type spells (supporting shotgun spread)
            boolean launched = SpellProjectileFactory.tryLaunchProjectile(
                    serverLevel,
                    shooter,
                    event.getOrigin(),
                    event.getDirection(),
                    spell,
                    effectiveLevel,
                    projectileCount,
                    spread
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

        // Check Shotgun Single-Trigger per volley for impact spells (e.g. Sunbeam)
        java.util.UUID shotId = profile.components().getOrDefault(SHOT_ID);
        if (shotId != null && !SpellBulletEffectHandler.checkAndMarkVolley(shotId)) {
            // Already triggered by an earlier pellet from this shotgun volley! Skip to avoid duplicate Sunbeams.
            event.setCanceled(true);
            bullet.discard();
            return;
        }

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            LivingEntity shooter = bullet.getOwner() instanceof LivingEntity living ? living : null;
            int projectileCount = Math.max(1, (int) Math.round(profile.value(ShotComponents.PROJECTILE_COUNT)));
            int effectiveLevel = (projectileCount > 1) ? Math.max(1, entry.spellLevel() - 3) : entry.spellLevel();
            io.redspace.ironsspellbooks.api.spells.SpellData spellData =
                    new io.redspace.ironsspellbooks.api.spells.SpellData(entry.getSpell(), effectiveLevel);

            SpellBulletEffectHandler.onSpellBulletHit(
                    bullet.level(),
                    event.getRayTraceResult(),
                    bullet,
                    shooter,
                    spellData
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
