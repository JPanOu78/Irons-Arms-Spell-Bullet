# Iron's Arms Spell Bullet 🔫✨

[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.84+-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Bridging the magical mastery of **Iron's Spells 'n Spellbooks** with the high-octane firearm gunplay of **Iron's Arms 'n Artifice** on Minecraft **NeoForge 1.21.1**.

---

## 🏛️ Core Design Philosophy: 100% Authentic Spell Rendering

> [!IMPORTANT]
> **Authentic Spell Rendering (Zero Generic Imitation):**
> * Every single spell bullet fired manifests as the genuine spell entity and visual effect from **Iron's Spells 'n Spellbooks**.
> * Whenever a spell possesses a dedicated 3D entity or custom visual renderer (such as `MagicFireball`, `FireBomb` (Magma Bomb), `Icicle`, `Firebolt`, `SmallMagicFireball`, `BloodNeedle`, `RayOfFrostVisualEntity`, `EldritchBlastVisualEntity`, `SunbeamEntity`, `ArrowVolleyEntity`, `BlackHole`, `ConeOfColdProjectile`, etc.), it is invoked and rendered directly using authentic Iron's Spells classes and shaders.
> * Preserves native 3D geometry, custom shader passes, authentic particle emission plumes, and original sound effects for a truly immersive spell-slinging gunplay experience.

---

## 🌟 Key Features

### 1. Inscribed Spell Bullet Crafting
* **Shaped Crafting Recipe:** Place any valid **Spell Scroll** in the center slot of a 3x3 crafting grid, surrounded by **8 standard Bullets**.
* **Output:** Yields **8 Inscribed Spell Bullets** retaining the exact spell identity, school, and spell level of the consumed scroll.
* **Mobility & Teleport Blacklist:** For combat balance and safety, mobility/teleportation spells (such as `Teleport`, `Frost Step`, `Burning Dash`, `Blood Step`, `Angel Wings`) cannot be crafted into bullets or loaded into firearms.

### 2. Zero Cast Time, Zero Cooldown & 0 Mana Cost
* **Instant Firearm Spellcasting:** Spells trigger instantly at bullet velocity upon pulling the trigger without any windup or casting animation delays.
* **Fire Rate Governing:** Spell firing frequency is governed entirely by the firearm's mechanical fire rate, rpm, and reload speed.
* **Zero Mana Depletion:** Propelled entirely by cartridge propellant and firearm mechanics—firing spell bullets consumes 0 player mana.

### 3. Authentic Projectile Spells & Rapid-Fire Bursts
Spells with projectile nature are launched directly from the firearm's muzzle at bullet speed:
* **Blaze Storm:** Fires a rapid 5-round fireball stream (1 instant + 4 queued bursts every 2 ticks) tracking the player's aim.
* **Blood Needles:** Single bullet unleashes a rapid 5-needle hemomantic spiral barrage ($72^\circ$ spiral step), piercing targets with authentic lifesteal and 0 invulnerability frames.
* **Flaming Barrage:** Fires a 5-round rapid volley of small fireballs pursuing targeted enemies downrange.
* **Fire:** `Fireball` (massive explosion), `Firebolt` (blazing incendiary dart), `Magma Bomb` (napalm burst).
* **Ice:** `Icicle` (piercing frost needle).
* **Lightning:** `Lightning Lance` (hyper-velocity piercing javelin), `Ball Lightning` (slow-moving plasma sphere).
* **Nature:** `Poison Arrow` (venomous ranged sting), `Acid Orb` (corrosive splash projectile).
* **Holy:** `Guiding Bolt` (radiant homing bolt).
* **Ender / Void:** `Magic Missile` (arcane seeker dart), `Comet` (orbital meteor slug).

### 4. Tactical Airstrikes & Orbital Bombardments
Impact-driven spells convert firearm bullets into precision targeting lasers, summoning devastating celestial and artillery strikes at the bullet's impact location:
* **Arrow Volley:** Fires a designated targeting bullet; upon impact, summons the authentic 3D `ArrowVolleyEntity` raining 24–40 spectral arrows in cascading rows down on the target zone.
* **Starfall:** Designates an impact beacon with cosmic fog and portal rifts, summoning a 6-to-10 comet celestial bombardment raining explosive `Comet` projectiles from the upper atmosphere over 1.5 seconds.
* **Thunderstorm:** Upon bullet impact, strikes with an immediate lightning bolt followed by an authentic localized thunderstorm calling down 3–5 additional lightning bolts across a 5-block radius.
* **Sunbeam:** Calls down an authentic vertical solar pillar (`SunbeamEntity`) incinerating targets and undead with radiant solar fury.
* **Blizzard:** Deploys a lingering `BlizzardAoe` cloud over the impact zone, continually bombarding enemies with freezing icicles and hail.

### 5. Cascading Ground Seismic Spells
* **Ice Spikes (Cascading Frost Wave):** When the bullet impacts, a rolling shockwave of giant ice spikes (`IceSpikeEntity`) erupts out of the ground starting from the shooter's feet, following the bullet's trajectory all the way to the target. Spikes dynamically conform to terrain elevations (hills, slopes, and floors) with progressive delay and scale up to a colossal 2.2x frost spike impaling the target!

### 6. Instant Precision Beams & Rays
* **Ray of Frost:** 64-block instant ice laser rendering native 3D `RayOfFrostVisualEntity` accompanied by dense `ICY_FOG` and `SNOWFLAKE` particle beams, dealing heavy frost damage, applying freeze ticks, and slowing targets.
* **Ray of Siphoning (Single-Burst Max Lifesteal):** Single-shot burst delivering maximum channel siphon damage instantly, healing the shooter, rendering a solid continuous blood laser beam (`BLOOD` & `SIPHON` particles), reverse blood stream into the gun barrel, and warden heartbeat audio.
* **Eldritch Blast:** 54-block void laser rendering 3D `EldritchBlastVisualEntity` with void tentacle fog and darkness effect.
* **Sonic Boom:** Penetrating warden sonic wave piercing through solid blocks up to 36 blocks downrange with intense knockback.

### 7. Downrange Cone & Spray Spells
Sweeps a wide 26-degree cone forward up to **22 blocks** downrange, checked with line-of-sight collision to prevent hitting behind walls:
* **Fire Breath:** Flamethrower cone blasting expanding `FIRE`, `EMBERS`, and `FIERY_SPARKS` plumes, setting caught targets ablaze for 4–8 seconds.
* **Cone of Cold:** Blizzard cone spewing `SNOW_DUST` and `SNOWFLAKE` particles, freezing targets solid and inflicting Slowness II.
* **Dragon Breath:** Purple mystic dragon breath cone dealing direct magic harm.
* **Electrocute:** Electric arc cone shocking all targets caught in the spread.
* **Poison Breath:** Toxic gas cone applying heavy Poison II.
* **Gust:** 16-block cone shockwave launching caught enemies backward.

### 8. Weapon Switching & Holster Safety
* **Instant Spray Termination:** Unequipping a firearm, switching active hotbar slots to another item, holstering weapons, or swapping ammo immediately terminates any active spray, cone, or continuous beam effect.
* **Smart Gunplay Recognition:** Durability loss and internal ammo consumption while firing the held weapon do not interrupt active bursts, ensuring seamless rapid-fire volleys.
* **Zero Particle Leaks:** Prevents lingering or looping particle entities from sticking to player models when switching to other weapons or empty hands.

### 9. Creative Mode Integration
* **Instant Spell Switching:** Selecting any spell bullet in the radial wheel or HUD immediately reconfigures the held firearm's firing profile and chamber for the very next shot.
* **Infinite Spell Ammunition:** Firearms do not deplete spell bullets in Creative mode, displaying an `∞` badge on the HUD.
* **Seamless Reloading:** Reloading a firearm in Creative mode instantly reloads to full capacity with the selected spell.

### 10. Radial Spell Wheel & Action Bar HUD
* **Quick-Select Hotkey (`Left Alt` + `Mouse Scroll`):** Open a responsive action bar overlay above the hotbar or a radial spell wheel to swap between loaded ammo types in real time.
* **Real-time Inventory Tracking:** Displays available bullet quantities (and `∞` in Creative mode) with school-colored backgrounds and icons.

### 11. Shotgun Volley Mechanics & Balancing
* **Pellet Spread Penalty:** Multi-pellet shotguns apply a -3 Spell Level penalty to prevent overwhelming damage multiplication.
* **Single Slug Core:** Heavy spells (Magma Bomb, Fireball, Black Hole) condense into a single concentrated slug per shotgun blast instead of multi-spawning duplicate heavy entities.

### 12. Elemental Impact Handlers
Targeted and impact spells detonate authentic magic phenomena upon bullet impact:
* 🔥 **Fire:** Tactical `Wall of Fire` barricades and fiery explosions.
* ❄️ **Ice:** `Frost Nova` freezing blast, cascading `Ice Spikes`, and tactical `Summon Polar Bear` drop pods.
* ⚡ **Lightning:** `Chain Lightning` arcs bouncing between nearby hostiles and localized `Thunderstorm` barrages.
* 🌟 **Holy:** Undead smite explosions; healing burst when hitting allies.
* 🌌 **Ender:** Gravity singularities, spatial `Black Hole` detonations, and `Starfall` comet strikes.
* 🩸 **Blood:** `Blood Slash` and hemomantic lifesteal bursts.
* 🌿 **Nature:** `Poison Cloud` toxic clouds and `Earthquake` shockwaves.
* 🪄 **Evocation:** Snapping `Evoker Fangs` rings and orbital `Arrow Volley` airstrikes.
* 👁️ **Eldritch:** Void tendrils and darkness zones.

---

## 🛠️ Installation & Requirements

* **Minecraft Version:** 1.21.1
* **Mod Loader:** NeoForge `21.1.84` or higher
* **Java Version:** 21

### Required Dependencies
* **[Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)** (`3.16.0`+)
* **[Iron's Arms 'n Artifice](https://www.curseforge.com/minecraft/mc-mods/irons-artifice)** (`1.1.0`+)
* **[GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib)**
* **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)**

---

## 📜 License
Developed for the Iron's Spells and Iron's Artifice community. Open source under the MIT License.
