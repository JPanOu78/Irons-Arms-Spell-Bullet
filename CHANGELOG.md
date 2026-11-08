[1.0.4] (1.21.1) 2026-11-08

Additions

• Added Shotgun Compatibility Filtering: Shotguns strictly accept and chamber only Projectile Spells (spread into 8 magic projectiles) and Slug Spells (concentrated single heavy core)
• Added Shotgun Tooltip Badges: Displays compatibility mode on spell bullets (`✦ Shotgun Mode: Pellets Spread`, `✦ Shotgun Mode: Heavy Slug Core`, or `⚠ Incompatible with Shotguns`)
• Added Heavy Slug Core System: Heavy/explosive spells (Magma Bomb, Fireball, Comet, Black Hole) automatically fire as a single pinpoint accurate Slug Core from shotguns with full AOE power

Changes

• Preserved vanilla I-Frame mechanics on shotgun spread volleys so single targets take 100% authentic spell damage once without duplicate hit anomalies, while multi-target spread volleys cleanly damage all struck targets
• Removed the artificial -3 Spell Level penalty on shotguns, allowing spells to fire at their true authentic power and level
• Added automatic reload and creative chambering fallback to normal ammunition if an incompatible spell is loaded into a shotgun

Fixes

• Fixed Sacrifice spell bullet passing through friendly summons on direct hit
• Fixed Sacrifice spell bullet detonating blood explosion properly when hitting owned summons
• Added authentic weapon and armor equipment for Raise Dead summons based on spell level

---

[1.0.2] (1.21.1) 2026-11-06

Fixes

• Fixed Sacrifice spell bullet passing through friendly summons on direct hit
• Fixed Sacrifice spell bullet not killing summoned mobs and detonating authentic blood explosion
• Prevented Sacrifice spell from dealing damage to non-summon entities
• Added authentic weapon and armor equipment for Raise Dead summons based on spell level (Level 6 now properly wears iron/chainmail armor)

---

[1.0.1] (1.21.1) 2026-11-06

Additions

• Added Interactive HUD Config Screen with real-time preview and drag-and-drop repositioning (Default key: K)
• Added Action Bar customization: Screen Anchor, Horizontal/Vertical layout, Columns/Rows, and Scale
• Added Spell Bullet Name display toggle (Show/Hide), 4-way alignment (Above, Below, Left, Right), scale, and axis offsets
• Added Muzzle Offset and Target Convergence for firearm spellcasting
• Added Multi-beam spread for Ray spells when fired from shotguns
• Added Ice Spikes distance-scaling cascade with a giant finisher spike

Changes

• Infused spells directly onto vanilla iron's artifice bullets (removed separate item)
• Enabled Fireball projectile scattering for shotguns
• Restricted shotgun spell selection to Projectile and Ray spells
• Integrated summon spells with native SummonManager and Recast UI
• Updated spell scaling and damage to use native Iron's Spells calculations

Fixes

• Fixed Action Bar overlapping with spell books and mana bar
• Fixed unselected Action Bar slots rendering solid black
• Fixed Fireball and projectile spell level damage scaling
• Fixed Raise Hell missing ground slam eruption and camera shake

---

[1.0.0] (1.21.1) 2026-11-02

Additions

• Initial release
