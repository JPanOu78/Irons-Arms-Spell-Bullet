# Iron's Arms Spell Bullet 🔫✨

Bridging the magical mastery of **Iron's Spells 'n Spellbooks** with the high-octane firearm gunplay of **Iron's Arms 'n Artifice** on Minecraft **NeoForge 1.21.1**.

---

## 🏛️ Core Design Philosophy: 100% Authentic Spell Rendering

> [!IMPORTANT]
> **Authentic Spell Rendering (Zero Generic Imitation):**
> * Every single bullet fired must manifest as the genuine spell entity and visual effect from **Iron's Spells 'n Spellbooks**.
> * Whenever a spell possesses a dedicated 3D entity or custom visual renderer (such as `MagicFireball`, `FireBomb` (Magma Bomb), `Icicle`, `Firebolt`, `MagicMissile`, `RayOfFrostVisualEntity`, `EldritchBlastVisualEntity`, `SunbeamEntity`, `BlackHole`, `ConeOfColdProjectile`, etc.), it is invoked and rendered directly using the authentic classes from Iron's Spells.
> * Preserves native 3D geometry, custom shader passes, authentic particle emission plumes, and original sound effects for a truly immersive spell-slinging gunplay experience.

---

## 🌟 Key Features

### 1. Inscribed Spell Bullet Crafting
* **Shaped Crafting Recipe:** Place any valid **Spell Scroll** in the center slot of a 3x3 crafting grid, surrounded by **8 standard Bullets**.
* **Output:** Yields **8 Inscribed Spell Bullets** retaining the exact spell identity and spell level of the consumed scroll.
* **Mobility & Teleport Blacklist:** For combat balance and safety, mobility/teleportation spells (such as `Teleport`, `Frost Step`, `Fire Dash`, `Blood Step`, `Angel Wings`) cannot be crafted into bullets or loaded into firearms.

### 2. Zero Cast Time, Zero Cooldown & 0 Mana Cost
* **Instant Firearm Spellcasting:** Spells trigger instantly at bullet velocity upon pulling the trigger without any windup or casting animation delays.
* **Fire Rate Governing:** Spell firing frequency is governed entirely by the firearm's mechanical fire rate, rpm, and reload speed.
* **Zero Mana Depletion:** Propelled entirely by bullet energy—firing spell bullets consumes 0 player mana.

### 3. Authentic Projectile Spells
Spells with projectile nature are launched directly from the firearm's muzzle at bullet speed:
* **Fire:** `Fireball` (massive explosion), `Firebolt` (blazing incendiary dart), `Magma Bomb` (napalm burst).
* **Ice:** `Icicle` (piercing frost needle).
* **Lightning:** `Electrocute` (branching electric shockwave).
* **Nature:** `Poison Arrow` (venomous ranged sting).
* **Blood:** `Blood Needle` (hemomantic piercing projectile).
* **Holy:** `Guiding Bolt` (radiant projectile).
* **Eldritch / Void:** `Psychic Bolt` & `Sculk Tentacles`.

### 4. Instant Precision Beams & Rays
* **Ray of Frost:** 64-block instant ice laser rendering native 3D `RayOfFrostVisualEntity` accompanied by dense `ICY_FOG` and `SNOWFLAKE` particle beams, dealing heavy frost damage, applying freeze ticks, and slowing targets.
* **Ray of Siphoning (Single-Burst Max Lifesteal):** Single-shot burst delivering maximum channel siphon damage instantly, healing the shooter, rendering a solid continuous blood laser beam (`BLOOD` & `SIPHON` particles), reverse blood stream into the gun barrel, and warden heartbeat audio.
* **Eldritch Blast:** 54-block void laser rendering 3D `EldritchBlastVisualEntity` with void tentacle fog and darkness effect.
* **Sonic Boom:** Penetrating warden sonic wave piercing through solid blocks up to 36 blocks downrange with intense knockback.

### 5. Downrange Cone & Spray Spells
Sweeps a wide 26-degree cone forward up to **22 blocks** downrange, checked with line-of-sight collision to prevent hitting behind walls:
* **Fire Breath:** Flamethrower cone blasting expanding `FIRE`, `EMBERS`, and `FIERY_SPARKS` plumes, setting caught targets ablaze for 4–8 seconds.
* **Cone of Cold:** Blizzard cone spewing `SNOW_DUST` and `SNOWFLAKE` particles, freezing targets solid and inflicting Slowness II.
* **Dragon Breath:** Purple mystic dragon breath cone dealing direct magic harm.
* **Electrocute:** Electric arc cone shocking all targets caught in the spread.
* **Poison Breath:** Toxic gas cone applying heavy Poison II.
* **Gust:** 16-block cone shockwave launching caught enemies backward.

### 6. Weapon Switching & Holster Safety
* **Instant Spray Termination:** Unequipping a firearm, switching active hotbar slots, holstering weapons, or swapping ammo immediately terminates any active spray, cone, or continuous beam effect.
* **Zero Particle Leaks:** Prevents lingering or looping particle entities from sticking to player models when switching to other weapons or empty hands.

### 7. Creative Mode Integration
* **Instant Spell Switching:** Selecting any spell bullet in the radial wheel or HUD immediately reconfigures the held firearm's firing profile and chamber for the very next shot.
* **Infinite Spell Ammunition:** Firearms do not deplete spell bullets in Creative mode, displaying an `∞` badge on the HUD.
* **Seamless Reloading:** Reloading a firearm in Creative mode instantly reloads to full capacity with the selected spell.

### 8. Radial Spell Wheel & Action Bar HUD
* **Quick-Select Hotkey (`Left Alt` + `Mouse Scroll`):** Open a responsive action bar overlay above the hotbar or a radial spell wheel to swap between loaded ammo types in real time.
* **Real-time Inventory Tracking:** Displays available bullet quantities (and `∞` in Creative mode) with school-colored backgrounds and icons.

### 9. Shotgun Volley Mechanics & Balancing
* **Pellet Spread Penalty:** Multi-pellet shotguns apply a -3 Spell Level penalty to prevent overwhelming damage multiplication.
* **Single Slug Core:** Heavy spells (Magma Bomb, Fireball, Black Hole) condense into a single concentrated slug per shotgun blast instead of multi-spawning duplicate heavy entities.

### 10. Elemental Impact Handlers
Targeted and impact spells detonate authentic magic phenomena upon bullet impact:
* 🔥 **Fire:** Tactical `Wall of Fire` barricades and fiery explosions.
* ❄️ **Ice:** `Frost Nova` freezing blast and tactical `Summon Polar Bear` drop pods.
* ⚡ **Lightning:** `Chain Lightning` arcs bouncing between nearby hostiles.
* 🌟 **Holy:** Undead smite explosions; healing burst when hitting allies.
* 🌌 **Ender:** Gravity singularities and spatial `Black Hole` detonations.
* 🩸 **Blood:** `Blood Slash` and hemomantic lifesteal bursts.
* 🌿 **Nature:** `Poison Cloud` toxic clouds and `Earthquake` shockwaves.
* 🪄 **Evocation:** Snapping `Evoker Fangs` rings around impact points.
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
