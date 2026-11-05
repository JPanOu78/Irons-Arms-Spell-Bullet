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

## 🏛️ Core Design Philosophy: Authentic Spell Integration

Every single spell bullet fired manifests as the **genuine spell entity, particle system, and soundscape** from Iron's Spells 'n Spellbooks:
* **Native 3D Entities & Custom Shaders:** Spells with dedicated 3D models, custom mesh geometry, and special shader passes are invoked directly using authentic classes from Iron's Spells—never generic particle imitations.
* **Muzzle Offset & Target Convergence:** Spells emanate naturally from the firearm muzzle and dynamically converge onto your crosshair target downrange.
* **Authentic Audio & FX:** Retains all native impact sounds, windups, particle trails, and visual flourishes directly from the source mod.

---

## 🌟 Key Features

### 📜 1. Inscribed Spell Bullet Crafting
* **Shaped Crafting Recipe:** Place any valid **Core Spell Scroll** in the center slot of a 3x3 crafting grid, surrounded by **8 standard Bullets** (`irons_artifice:bullet`).
* **Seamless Infusion:** Directly infuses standard ammunition with spell data—no redundant custom items cluttering your inventory.
* **Output:** Yields **8 Inscribed Spell Bullets** retaining the exact spell identity, school, and level of the consumed scroll.

#### 🚫 Teleport & Mobility Spell Restrictions
Currently, all **Teleportation, Dash, and Mobility spells are not permitted for spell bullet crafting** and cannot be converted into ammunition.

* **Blacklisted Spells:** *Teleport*, *Frost Step*, *Blood Step*, *Burning Dash*, *Volt Strike*, *Shadow Slash*, *Ascension*, *Recall*, *Portal*, *Pocket Dimension*, and *Charge*.
* **Game Balance & Combat Exploits:** In Iron's Spells, movement spells are balanced around player-centered positioning, cooldowns, and cast times. Firing them from firearms with high rates of fire or long-range ballistic triggers allows bypassing wall collision, instantaneous out-of-bounds traversal, and combat evasions that break core gameplay mechanics.

---

### ⚡ 2. Instant Firearm Casting (Zero Mana, Zero Windup)
* **Zero Cast Time:** Spells trigger instantly at bullet velocity upon pulling the trigger with no casting windup or animation delay.
* **Mechanically Governed:** Spell firing frequency is governed entirely by your firearm's mechanical fire rate, RPM, magazine capacity, and reload speed.
* **0 Mana Cost:** Propelled entirely by cartridge propellant and firearm mechanics—firing spell bullets consumes zero player mana.

---

### 🎯 3. Comprehensive Spell Archetypes & Gunplay

All supported spells are intelligently adapted into firearm gunplay profiles:
* **Ballistic Magic Projectiles:** Spells with projectile nature (*Fireball*, *Magma Ball*, *Icicle*, *Lightning Lance*, *Magic Missile*, *Acid Orb*, *Poison Arrow*) launch downrange with authentic trajectories.
* **Rapid-Fire Spell Bursts:** Channeled and multi-cast barrage spells fire in rapid, sequential multi-round bursts tracking player crosshairs.
* **Tactical Airstrikes & Orbital Storms:**
  * **Thunderstorm:** Creates an authentic storm zone with hovering dark storm clouds (`FOG_THUNDER_DARK`) and calls down `LightningStrike` entities directly on nearby enemies.
  * **Starfall & Arrow Volley:** Designate impact points downrange, raining down celestial comets or arrow barrages from the sky.
* **Cascading Ground Seismic Waves:**
  * **Ice Spikes:** Erupts a rippling line of cascading jagged ice spikes across terrain elevation directly towards the target, scaling up in size with distance and culminating in a giant finisher spike.
  * **Fang Strike:** Evoker fangs chomp sequentially forward across terrain to crush foes.
* **Precision Beams & Elemental Lasers:** Instant high-penetration elemental rays (*Ray of Frost*, *Ray of Siphoning*, *Sunbeam*) striking targets downrange with pinpoint line-of-sight accuracy.
* **Tactical Minion Summoning:** Summons deploy reinforcements (*Polar Bear*, *Vex*, *Undead*, *Horse*, *Swords*) at the impact point, fully integrated with Iron's Spells' native **`PlayerRecasts`** and **`SummonManager`** (enforcing active summon limits and recast UI countdowns).
* **Tactical Support & Healing Darts:** Ranged ally buffs, healing darts, area-denial barriers, and crowd-control ice tombs.

---

### ⚖️ 4. Shotgun Mechanics & Balancing
* **Dedicated Spell Filtering:** Shotguns exclusively load and fire **Projectile** and **Ray** spells—preventing awkward or non-ballistic spells from misfiring in shotgun spread.
* **Projectile Scattering:** Spells like *Icicle*, *Poison Arrow*, and *Fireball* scatter multiple projectiles across the shotgun's pellet cone.
* **Multi-Beam Spread:** Ray-type spells (*Ray of Frost*, *Sunbeam*) scatter into multiple simultaneous beams across the pellet spread.
* **Damage Balance:** Multi-pellet shotguns apply a -3 Spell Level penalty to prevent overwhelming damage multiplication.

---

### 🎨 5. Interactive HUD & In-Game Configuration Screen

Customize your Action Bar HUD in real time to suit any layout or UI modpack:

* **In-Game Config GUI (Default Key: `K`):**
  * **Live Interactive Preview:** See your exact changes reflected in real time with test spell icons and badges.
  * **Click & Drag Repositioning:** Click directly on the Action Bar preview to drag it anywhere across your screen.
  * **Screen Anchors:** Snap to `Hotbar Center`, `Bottom Left`, `Bottom Right`, `Top Left`, `Top Right`, or `Screen Center`.
  * **Layout Orientation:** Switch between `Horizontal` and `Vertical` grid layouts.
  * **Grid Dimensions & Scale:** Adjust slots per row/column (1 to 20) and overall HUD scale (50% to 200%).
* **Spell Bullet Name Customization:**
  * **Toggle Display:** Easily switch the active bullet name `ON` or `OFF`.
  * **4-Way Alignment:** Position the title `Above Slots`, `Below Slots`, `Left of Slots`, or `Right of Slots`.
  * **Name Scale & Offsets:** Fine-tune font scale (50% to 150%) and adjust horizontal/vertical offsets ($\pm 100\text{px}$).
* **Quick Ammo Cycling (`Left Alt` + `Mouse Scroll`):**
  * Instantly switch between standard ammunition and loaded spell bullets.
  * Displays high-contrast slot backgrounds, active gold highlight frames, school-colored badges, and remaining counts (including `∞` in Creative Mode).

---

### 🛡️ 6. Weapon Switching & Holster Safety
* **Instant Cancellation:** Unequipping a firearm, switching active hotbar slots, holstering weapons, or swapping ammo immediately terminates any active spray, cone, or continuous beam effect.
* **Smart Gunplay Recognition:** Weapon durability loss and internal ammo consumption while firing will never interrupt active burst volleys.
* **Zero Particle Leaks:** Prevents lingering particle entities from sticking to player models when switching to other weapons or empty hands.

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
