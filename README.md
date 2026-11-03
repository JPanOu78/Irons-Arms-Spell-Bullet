# Iron's Arms Spell Bullet 🔫✨

[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.84+-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Bridging the magical mastery of **Iron's Spells 'n Spellbooks** with the high-octane firearm gunplay of **Iron's Arms 'n Artifice** on Minecraft **NeoForge 1.21.1**.

> [!IMPORTANT]
> **Compatibility Scope (Core Mod Only):**
> * Currently supports **ONLY spells from core Iron's Spells 'n Spellbooks** across all 9 schools of magic.
> * Third-party addon mods (e.g. *Cataclysm Spells*, *Alchemical Spells*, etc.) are **NOT supported** at this time.

---

## 🏛️ Core Design Philosophy: 100% Authentic Spell Rendering

Every single spell bullet fired manifests as the **genuine spell entity, particle system, and soundscape** from Iron's Spells 'n Spellbooks:
* **Native 3D Entities & Custom Shaders:** Spells with dedicated 3D models, custom mesh geometry, and special shader passes are invoked directly using authentic classes from Iron's Spells—never generic particle imitations.
* **Authentic Audio & FX:** Retains all native impact sounds, windups, particle trails, and visual flourishes directly from the source mod.

---

## 🌟 Key Features

### 📜 1. Inscribed Spell Bullet Crafting
* **Shaped Crafting Recipe:** Place any valid **Core Spell Scroll** in the center slot of a 3x3 crafting grid, surrounded by **8 standard Bullets**.
* **Output:** Yields **8 Inscribed Spell Bullets** retaining the exact spell identity, school, and level of the consumed scroll.
* **Combat Balance:** Mobility and teleportation spells are strictly blacklisted from ammunition crafting to preserve combat balance.

### ⚡ 2. Instant Firearm Casting (Zero Mana, Zero Windup)
* **Zero Cast Time:** Spells trigger instantly at bullet velocity upon pulling the trigger with no casting windup or animation delay.
* **Mechanically Governed:** Spell firing frequency is governed entirely by your firearm's mechanical fire rate, RPM, magazine capacity, and reload speed.
* **0 Mana Cost:** Propelled entirely by cartridge propellant and firearm mechanics—firing spell bullets consumes zero player mana.

### 🎯 3. Comprehensive Spell Archetypes
All supported spells are intelligently adapted into firearm gunplay profiles:
* **Ballistic Magic Projectiles:** Spells with projectile nature launch directly from the gun barrel at supersonic bullet speed with custom trajectory dynamics.
* **Rapid-Fire Spell Bursts:** Channeled and multi-cast barrage spells fire in rapid, sequential multi-round bursts tracking player crosshairs.
* **Tactical Airstrikes & Orbital Bombardments:** Targeting bullets designate an impact coordinate, calling down devastating celestial barrages, lightning storms, or arrow volleys from the heavens.
* **Cascading Ground Seismic Waves:** Ground-rupturing spells erupt in sequential rolling shockwaves out of the earth from the shooter's position, dynamically conforming to terrain elevations all the way to the target.
* **Precision Beams & Lasers:** High-penetration instant elemental lasers striking targets downrange with pinpoint line-of-sight accuracy.
* **Close-Quarter Cones & Sprays:** Wide-angle forward spray patterns for flamethrower and breath-type spells, sweeping downrange with wall-collision safety checks.
* **Tactical Support & Utility Darts:** Ranged ally buffs, healing darts, area-denial barriers, minion deployments, and spatial gravity wells.

### 🛡️ 4. Weapon Switching & Holster Safety
* **Instant Cancellation:** Unequipping a firearm, switching active hotbar slots, holstering weapons, or swapping ammo immediately terminates any active spray, cone, or continuous beam effect.
* **Smart Gunplay Recognition:** Weapon durability loss and internal ammo consumption while firing will never interrupt active burst volleys.
* **Zero Particle Leaks:** Prevents lingering particle entities from sticking to player models when switching to other weapons or empty hands.

### 🎨 5. Radial Spell Wheel & HUD Overlay
* **Quick-Select Hotkey (`Left Alt` + `Mouse Scroll`):** Open a responsive action bar overlay above the hotbar or a radial spell wheel to swap between loaded ammo types in real time.
* **Inventory Tracking:** Displays real-time bullet counts with school-colored backgrounds and spell icons.
* **Creative Mode Integration:** Infinite ammunition badge (`∞`), instant zero-reload spell profile swapping, and automatic chamber reconfiguration.

### ⚖️ 6. Shotgun Balancing Mechanics
* **Pellet Spread Penalty:** Multi-pellet shotguns apply a -3 Spell Level penalty to prevent overwhelming damage multiplication.
* **Single Slug Core:** Heavy and ultimate spells condense into a single concentrated slug per shotgun blast instead of multi-spawning duplicate heavy entities.

---

## 🛠️ Installation & Requirements

* **Minecraft Version:** 1.21.1
* **Mod Loader:** NeoForge `21.1.84` or higher
* **Java Version:** 21

### Required Dependencies
* **[Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)** (`3.16.0`+) *(Core mod only)*
* **[Iron's Arms 'n Artifice](https://www.curseforge.com/minecraft/mc-mods/irons-artifice)** (`1.1.0`+)
* **[GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib)**
* **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)**

---

## 📜 License
Developed for the Iron's Spells and Iron's Artifice community. Open source under the MIT License.
