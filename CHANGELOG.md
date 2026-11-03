# 🔫 Iron's Arms Spell Bullet - Changelog

## [1.0.0] - Initial Release (Minecraft 1.21.1 / NeoForge 21.1.84+)

Welcome to the initial official release of **Iron's Arms Spell Bullet**!

This mod bridges the magical mastery of **Iron's Spells 'n Spellbooks** with the high-octane firearm gunplay of **Iron's Arms 'n Artifice**, allowing you to inscribe firearm ammunition with magical scrolls and fire authentic spells straight out of your gun barrels.

---

### ✨ Features & Mechanics:

#### 📜 Inscribed Spell Bullet Crafting
* **Shaped Recipe:** Place any valid **Core Spell Scroll** in the center slot of a 3x3 crafting grid, surrounded by **8 standard Bullets**, to craft **8 Inscribed Spell Bullets**.
* Retains the exact spell school, tier, and level of the consumed scroll.
* **Combat Balance:** Mobility and teleportation spells are strictly blacklisted from ammunition crafting to prevent game exploits.

#### ⚡ Zero Cast Time & Zero Mana Depletion
* Spells trigger **instantly at bullet velocity** upon pulling the trigger with zero windup delays.
* Spell rate-of-fire is governed purely by your firearm's mechanical fire rate, RPM, magazine size, and reload speed.
* Firing spell bullets consumes **0 player mana**—propelled entirely by cartridge propellant and firearm mechanics!

#### 🏛️ 100% Authentic Native Spell Rendering
* **Zero Generic Placeholders:** Directly invokes genuine 3D models, custom mesh geometry, and special shader passes from Iron's Spells 'n Spellbooks.
* Retains native impact sounds, particle plumes, and visual flourishes directly from the source mod.

#### 🎯 Supported Spell Archetypes
* **Ballistic Magic Projectiles:** Projectiles launch directly from the gun barrel at supersonic speed with custom flight dynamics.
* **Rapid-Fire Bursts:** Multi-cast and channeled spells fire in rapid, sequential multi-round bursts (e.g., Blaze Storm, Flaming Barrage, Blood Needles) tracking player crosshairs.
* **Orbital Airstrikes & Bombardments:** Targeting bullets designate impact coordinates, summoning celestial meteor showers (Starfall), localized thunderstorms, or spectral arrow volleys (Arrow Volley).
* **Cascading Ground Seismic Waves:** Fissure spells erupt in sequential rolling shockwaves out of the ground starting from the shooter's feet, dynamically climbing terrain elevations to the target (Ice Spikes).
* **Precision Beams & Lasers:** Instant long-range elemental lasers (Ray of Frost, Ray of Siphoning, Eldritch Blast, Sonic Boom).
* **Close-Quarter Cones & Sprays:** Wide-angle forward spray patterns for breath and flamethrower spells with line-of-sight wall collision checks.
* **Support & Utility Darts:** Ranged ally buffs, healing darts, area-denial barriers, minion deployments, and gravity wells.

#### 🛡️ Gunplay Safety & QoL Mechanics
* **Holster / Weapon Swap Safety:** Unequipping or swapping weapons immediately halts active continuous sprays and beams with zero particle leaks.
* **Radial Spell Wheel & Action Bar HUD:** Quick-select loaded ammo in real time using `Left Alt` + `Mouse Scroll`.
* **Creative Mode Integration:** Displays infinite ammunition (`∞`) and enables instant zero-reload spell profile swapping.
* **Shotgun Volley Balancing:** Multi-pellet shotguns apply a -3 Spell Level penalty and consolidate heavy/explosive spells into a single concentrated slug.

---

### ⚠️ Compatibility Scope:
* **Supported:** Fully supports spells from core **Iron's Spells 'n Spellbooks** (1.21.1).
* **Not Supported:** Third-party addon mods are **not supported** in this release.

---

### 🛠️ Required Dependencies:
* **NeoForge** `21.1.84`+
* **Iron's Spells 'n Spellbooks** `3.16.0`+
* **Iron's Arms 'n Artifice** `1.1.0`+
* **GeckoLib**
* **Curios API**

