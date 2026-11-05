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
import net.minecraft.world.phys.Vec3;
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

        LivingEntity shooter = event.getEntity();

        // Creative mode instant bullet selection support:
        if (shooter instanceof Player player && player.hasInfiniteMaterials()) {
            String active = ActiveSpellBulletManager.getSelectedSpell(player.getUUID());
            if (active != null && !active.isEmpty() && !ActiveSpellBulletManager.NORMAL_BULLET.equals(active)) {
                AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(ResourceLocation.parse(active));
                if (spell != null && !SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                    int spellLevel = spell.getMaxLevel();
                    ItemStack offhand = player.getOffhandItem();
                    if (ReloadHelper.isSpellBullet(offhand) && ReloadHelper.matchesSpell(offhand, active)) {
                        io.redspace.ironsspellbooks.api.spells.SpellData sd = ReloadHelper.getSpellData(offhand);
                        if (sd != null) spellLevel = sd.getLevel();
                    } else {
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack invStack = player.getInventory().getItem(i);
                            if (ReloadHelper.isSpellBullet(invStack) && ReloadHelper.matchesSpell(invStack, active)) {
                                io.redspace.ironsspellbooks.api.spells.SpellData sd = ReloadHelper.getSpellData(invStack);
                                if (sd != null) {
                                    spellLevel = sd.getLevel();
                                    break;
                                }
                            }
                        }
                    }

                    SpellChamberData.SpellSlotEntry creativeEntry = new SpellChamberData.SpellSlotEntry(active, spellLevel);
                    profile.components().set(SHOT_SPELL, creativeEntry);
                    profile.components().set(ShotComponents.DAMAGE, Value.of(0.0));

                    if (SpellProjectileFactory.isProjectileSpell(spell)) {
                        profile.components().set(LAUNCHED_AS_PROJECTILE, true);
                        profile.components().set(ShotComponents.PARTICLE_TRAIL, new ParticleStack());
                    } else {
                        profile.components().set(SHOT_ID, java.util.UUID.randomUUID());
                    }
                    return;
                }
            } else if (ActiveSpellBulletManager.NORMAL_BULLET.equals(active)) {
                profile.components().remove(SHOT_SPELL);
                return;
            }
        }

        SpellChamberData chamber = gunStack.get(ModDataComponents.LOADED_SPELLS.get());
        if (chamber == null || chamber.isEmpty()) return;

        // Peek next round from chamber without popping it
        SpellChamberData.SpellSlotEntry entry = chamber.peekNext();
        if (entry != null && entry.hasSpell()) {
            // Safety: Disallow mobility or teleport spells
            if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(entry.getSpell())) {
                return;
            }

            // Attach spell to shot profile
            profile.components().set(SHOT_SPELL, entry);

            // Nullify firearm physical damage for spell bullets so only spell damage is dealt
            profile.components().set(ShotComponents.DAMAGE, Value.of(0.0));

            // Check if this spell is a projectile-type or beam-type spell.
            if (SpellProjectileFactory.isProjectileSpell(entry.getSpell())) {
                // For projectile and beam spells (Fireball, Magma bomb, Firebolt, Icicle, Ray of Frost, Ray of Siphoning, etc.):
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

        // Pop the round from chamber now that the gun is officially firing on the server (unless in Creative mode)
        ItemStack gunStack = profile.itemStack();
        if (gunStack != null && !gunStack.isEmpty()) {
            if (shooter instanceof Player player && player.hasInfiniteMaterials()) {
                // Creative Mode: keep infinite ammo in chamber
            } else {
                SpellChamberData chamber = gunStack.get(ModDataComponents.LOADED_SPELLS.get());
                if (chamber != null && !chamber.isEmpty()) {
                    SpellChamberData.PopResult pop = chamber.popNext();
                    gunStack.set(ModDataComponents.LOADED_SPELLS.get(), pop.remaining());
                }
            }
        }

        SpellChamberData.SpellSlotEntry entry = profile.components().getOrDefault(SHOT_SPELL);
        if (entry != null && entry.hasSpell()) {
            AbstractSpell spell = entry.getSpell();
            if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                return;
            }
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

            Vec3 eyeOrigin = event.getOrigin();
            Vec3 aimDirection = event.getDirection().normalize();

            // 1. Calculate physical gun muzzle position based on gun profile and handedness
            double muzzleOffset = 0.0;
            if (profile.components().has(ShotComponents.MUZZLE_OFFSET)) {
                muzzleOffset = profile.value(ShotComponents.MUZZLE_OFFSET);
            }
            boolean isLeft = shooter.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
            Vec3 forward = aimDirection.scale(1.15 + muzzleOffset);
            Vec3 right = aimDirection.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1e-4) {
                right = new Vec3(1, 0, 0);
            } else {
                right = right.normalize();
            }
            if (isLeft) right = right.scale(-1.0);
            Vec3 muzzlePos = eyeOrigin.add(forward).add(right.scale(0.32)).add(0, -0.22, 0);

            // 2. Target Convergence: Raycast from eye along crosshair aim direction so the projectile converges on the exact target
            net.minecraft.world.phys.HitResult crosshairHit = serverLevel.clip(new net.minecraft.world.level.ClipContext(
                    eyeOrigin,
                    eyeOrigin.add(aimDirection.scale(96.0)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    shooter
            ));
            Vec3 targetPoint = crosshairHit.getLocation();
            Vec3 convergedDirection = targetPoint.subtract(muzzlePos).normalize();
            if (convergedDirection.lengthSqr() < 1e-4) {
                convergedDirection = aimDirection;
            }

            // Try launching directly from gun muzzle at bullet speed for projectile-type spells (supporting shotgun spread)
            boolean launched = SpellProjectileFactory.tryLaunchProjectile(
                    serverLevel,
                    shooter,
                    muzzlePos,
                    convergedDirection,
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
        if (stack.isEmpty()) return;

        // 1. Imbued Iron's Arms Bullet Tooltip
        if (ReloadHelper.isSpellBullet(stack)) {
            io.redspace.ironsspellbooks.api.spells.SpellData spellData = ReloadHelper.getSpellData(stack);
            if (spellData != null && spellData.getSpell() != null) {
                AbstractSpell spell = spellData.getSpell();
                List<Component> tooltipComponents = event.getToolTip();

                if (SpellBulletEffectHandler.isMobilityOrTeleportSpell(spell)) {
                    tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.incompatible_mobility")
                            .withStyle(ChatFormatting.RED));
                    return;
                }

                MutableComponent schoolComponent = Component.translatable(spell.getSchoolType().getDisplayName().getString())
                        .withStyle(spell.getSchoolType().getDisplayName().getStyle());

                tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.school_info", schoolComponent)
                        .withStyle(ChatFormatting.GRAY));
                tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.instant_fire")
                        .withStyle(ChatFormatting.GOLD));
                tooltipComponents.add(Component.translatable("tooltip.irons_spell_bullet.zero_mana")
                        .withStyle(ChatFormatting.AQUA));
            }
            return;
        }

        // 2. Firearm Loaded Chamber Tooltip
        if (!(stack.getItem() instanceof GunItem)) return;

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
