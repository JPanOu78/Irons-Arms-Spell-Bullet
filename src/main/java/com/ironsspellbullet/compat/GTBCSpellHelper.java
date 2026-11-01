package com.ironsspellbullet.compat;

import com.gametechbc.gametechbcsspellbooks.entity.spells.psychic_bolt.PsychicBoltProjectile;
import com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.chunker.ChunkerEntity;
import com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.dripstone_bolt.DripstoneBoltEntity;
import com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.eroding_boulder.ErodingBoulderEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GTBCSpellHelper {

    private static Boolean gtbcsLoaded = null;
    private static Boolean geomancyLoaded = null;

    public static boolean isGTBCSpellsLoaded() {
        if (gtbcsLoaded == null) {
            try {
                Class.forName("com.gametechbc.gametechbcsspellbooks.init.GSSpells");
                gtbcsLoaded = true;
            } catch (Throwable t) {
                gtbcsLoaded = false;
            }
        }
        return gtbcsLoaded;
    }

    public static boolean isGeomancyLoaded() {
        if (geomancyLoaded == null) {
            try {
                Class.forName("com.gametechbc.gtbcs_geomancy_plus.init.GGSpells");
                geomancyLoaded = true;
            } catch (Throwable t) {
                geomancyLoaded = false;
            }
        }
        return geomancyLoaded;
    }

    public static boolean isGTBCProjectile(String path) {
        return path.contains("psychic_bolt") ||
                path.contains("dripstone_bolt") ||
                path.contains("eroding_boulder") ||
                path.contains("chunker");
    }

    public static boolean tryLaunchGTBCProjectile(ServerLevel level, LivingEntity shooter, Vec3 origin, Vec3 direction, String path, float damage, double speed) {
        if (path.contains("psychic_bolt") && isGTBCSpellsLoaded()) {
            PsychicBoltProjectile bolt = new PsychicBoltProjectile(level, shooter);
            bolt.setPos(origin.x, origin.y, origin.z);
            bolt.setDamage(damage);
            bolt.shoot(direction);
            bolt.setDeltaMovement(direction.normalize().scale(speed));
            level.addFreshEntity(bolt);
            return true;
        }

        if (isGeomancyLoaded()) {
            if (path.contains("dripstone_bolt")) {
                DripstoneBoltEntity bolt = new DripstoneBoltEntity(level, shooter);
                bolt.setPos(origin.x, origin.y, origin.z);
                bolt.setDamage(damage);
                bolt.shoot(direction);
                bolt.setDeltaMovement(direction.normalize().scale(speed));
                level.addFreshEntity(bolt);
                return true;
            } else if (path.contains("eroding_boulder")) {
                ErodingBoulderEntity boulder = new ErodingBoulderEntity(level, shooter);
                boulder.setPos(origin.x, origin.y, origin.z);
                boulder.setDamage(damage);
                boulder.shoot(direction);
                boulder.setDeltaMovement(direction.normalize().scale(speed));
                level.addFreshEntity(boulder);
                return true;
            } else if (path.contains("chunker")) {
                ChunkerEntity chunker = new ChunkerEntity(level, shooter);
                chunker.setPos(origin.x, origin.y, origin.z);
                chunker.setDamage(damage);
                chunker.setDeltaMovement(direction.normalize().scale(speed));
                level.addFreshEntity(chunker);
                return true;
            }
        }

        return false;
    }

    public static boolean handleGTBCImpact(ServerLevel level, Vec3 hitPos, Entity hitEntity, Entity bullet, LivingEntity shooter, AbstractSpell spell, String path, int spellLevel, float damage) {
        // Solar Beam / Solar Storm (Holy / Fire)
        if (path.contains("solar_beam") || path.contains("solar_storm")) {
            float radius = 3.0f + spellLevel * 0.5f;
            AABB aabb = new AABB(hitPos.x - radius, hitPos.y - 2, hitPos.z - radius, hitPos.x + radius, hitPos.y + 4, hitPos.z + radius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter);
            for (LivingEntity e : nearby) {
                e.hurt(SpellDamageSource.source(bullet, shooter, spell), damage);
                e.igniteForSeconds(4 + spellLevel);
            }
            level.sendParticles(ParticleTypes.END_ROD, hitPos.x, hitPos.y + 1, hitPos.z, 40, 0.5, 2.0, 0.5, 0.1);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
            return true;
        }

        // Tremor Spike / Fissure / Earthshatter (Geomancy)
        if (path.contains("tremor_spike") || path.contains("fissure") || path.contains("earthshatter") || path.contains("seismic_surf")) {
            float radius = 3.5f + spellLevel * 0.5f;
            AABB aabb = new AABB(hitPos.x - radius, hitPos.y - 1, hitPos.z - radius, hitPos.x + radius, hitPos.y + 2, hitPos.z + radius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter);
            for (LivingEntity e : nearby) {
                e.hurt(SpellDamageSource.source(bullet, shooter, spell), damage);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5 + spellLevel * 0.1, 0));
            }
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, hitPos.x, hitPos.y, hitPos.z, 30, 0.6, 0.2, 0.6, 0.05);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.3f, 0.8f);
            return true;
        }

        // Petrivise (Turn to stone / extreme slow)
        if (path.contains("petrivise") && hitEntity instanceof LivingEntity target) {
            target.hurt(SpellDamageSource.source(bullet, shooter, spell), damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100 + spellLevel * 30, 4));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100 + spellLevel * 30, 2));
            level.sendParticles(ParticleTypes.ASH, hitPos.x, hitPos.y + 1, hitPos.z, 20, 0.3, 0.5, 0.3, 0.05);
            return true;
        }

        // Acid Rain
        if (path.contains("acid_rain")) {
            float radius = 4.0f + spellLevel * 0.5f;
            AABB aabb = new AABB(hitPos.x - radius, hitPos.y - 1, hitPos.z - radius, hitPos.x + radius, hitPos.y + 3, hitPos.z + radius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter);
            for (LivingEntity e : nearby) {
                e.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 0.6f);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + spellLevel * 20, 1));
            }
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, hitPos.x, hitPos.y + 1, hitPos.z, 40, radius * 0.5, 1.0, radius * 0.5, 0.1);
            return true;
        }

        // Crimson Downpour (Blood rain AoE)
        if (path.contains("crimson_downpour")) {
            float radius = 4.0f + spellLevel * 0.5f;
            AABB aabb = new AABB(hitPos.x - radius, hitPos.y - 1, hitPos.z - radius, hitPos.x + radius, hitPos.y + 3, hitPos.z + radius);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e != shooter);
            float totalLeech = 0;
            for (LivingEntity e : nearby) {
                e.hurt(SpellDamageSource.source(bullet, shooter, spell).setLifestealPercent(0.5f), damage * 0.6f);
                totalLeech += damage * 0.2f;
            }
            if (shooter != null && totalLeech > 0) {
                shooter.heal(Math.min(10.0f, totalLeech));
            }
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, hitPos.x, hitPos.y + 1, hitPos.z, 40, radius * 0.5, 1.0, radius * 0.5, 0.1);
            return true;
        }

        // Meteor Storm
        if (path.contains("meteor_storm")) {
            level.explode(bullet, hitPos.x, hitPos.y, hitPos.z, 2.5f + spellLevel * 0.4f, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y + 1, hitPos.z, 5, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.9f);
            return true;
        }

        // Nullflare (Cosmic Flame + Ice)
        if (path.contains("nullflare")) {
            level.explode(bullet, hitPos.x, hitPos.y, hitPos.z, 2.8f + spellLevel * 0.4f, Level.ExplosionInteraction.NONE);
            if (hitEntity instanceof LivingEntity target) {
                target.hurt(SpellDamageSource.source(bullet, shooter, spell), damage * 1.3f);
                target.igniteForSeconds(5);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.1);
            level.sendParticles(ParticleTypes.SNOWFLAKE, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.05);
            return true;
        }

        // Shatterpoint
        if (path.contains("shatterpoint") && hitEntity instanceof LivingEntity target) {
            target.hurt(SpellDamageSource.source(bullet, shooter, spell).setFreezeTicks(200), damage * 1.4f);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, hitPos.x, hitPos.y + 1, hitPos.z, 30, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 1.2f);
            return true;
        }

        // Blackout / Ensnare
        if (path.contains("blackout") || path.contains("ensnare")) {
            if (hitEntity instanceof LivingEntity target) {
                target.hurt(SpellDamageSource.source(bullet, shooter, spell), damage);
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100 + spellLevel * 20, 2));
            }
            level.sendParticles(ParticleTypes.SQUID_INK, hitPos.x, hitPos.y + 1, hitPos.z, 25, 0.4, 0.4, 0.4, 0.05);
            return true;
        }

        return false;
    }
}
