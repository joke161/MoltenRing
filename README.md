# Attuned Rings

A **Minecraft 1.21.1 (NeoForge)** elemental magic mod with **Curios API** integration and optional **Epic Fight** & **Iris** compatibility. It introduces elemental attuned rings starting with the **Molten Ring** (`attunedrings:molten_ring`), Magicka-style rune queuing and spellcasting, Overcharged Firebolt sniper aiming, 3D magma vein player overlays, procedural casting choreography, and fire/lava attunement rituals.

> **Note:** Active development targets **NeoForge 1.21.1** (`neoforge/`). The older Fabric prototype in the repository root is kept for reference and is no longer actively maintained.

---

## 📋 Features

### 1. Unattuned Ring & Attunement Ritual
- **Item: Unattuned Ring (`attunedrings:unattuned_ring`)**:
  - Crafted with **8 Iron Ingots** surrounding an empty center in a Crafting Table.
  - An empty metallic vessel awaiting elemental attunement.
- **Attunement Ritual**:
  - Equip the ring in a **Curios** ring slot or hold it in either hand.
  - Stand in **Fire for 10 seconds** or in **Lava for 5 seconds** (accumulating 200 ticks of heat progress; submerging in water rapidly cools progress).
  - Emits fiery particles, smoke, and sizzles as heat rises.
  - Upon completion, an anvil strike and firecharge burst forge it into the **Molten Ring**, starting in Stage 6 cooling burnout (60s cooldown) to prevent immediate invulnerability exploits.

---

### 2. Item: Molten Ring (`attunedrings:molten_ring`)
- **Stack Size**: Non-stackable (max 1 per slot).
- **Curios API Integration**: Equippable in Curios `ring` slots (`data/curios/tags/item/ring.json`, `data/c/tags/item/rings.json`, `data/attunedrings/curios/entities/player.json`) or usable directly from either hand.
- **Creative Tabs**: Dedicated `Attuned Rings` tab (`itemGroup.attunedrings`).
- **Dynamic 6-Stage Visual Wear**:
  - **100% Energy (`1200` units)**: Pristine gold ring with animated magma core (`molten_ring.png`).
  - **Stages 1–4**: Molten veins cool and darken progressively as energy depletes.
  - **Stage 6 (Broken / Burnout)**: Fractured dark stone (`molten_ring_6.png`), shown at 0% energy and during burnout cooldowns.
- **Clean UI**: Vanilla durability bar is hidden (`isBarVisible = false`); remaining charge and state are shown directly in the HUD and ring texture.

---

### 3. Passive Protection (Emergency Fire & Lava Shield)
- **Grace Window (5 Seconds)**: Stepping into fire or lava activates Fire Resistance (`100` ticks).
- **Safe Extinguishing**: Leaving fire/lava extinguishes flames and starts a **10-second cooldown** (`200` ticks).
- **Hazard Timeout**: Remaining submerged longer than 5 seconds revokes protection and triggers the 10-second cooldown.

---

### 4. Elemental Spellcasting & Sniper Mode
Queue flame runes and unleash spells on demand:
- **`[Z]`**: Queue an elemental Fire rune (`[🔥]`, up to 5 stacks, with rising audio pitch and priming burst).
- **`[R]`**: Cast the queued spell.
- **`[Shift + R]`**: Clear the rune queue.
- **`[RMB]` (with 1 Fire stack & non-blocking hands)**: Hold for **`0.8s`** (`16` ticks) to charge and release an **Overcharged Firebolt Sniper** beam.
- **Water Quenching & Shield Interaction**:
  - Submerging in water clears primed runes, cancels active channels, and accelerates Stage 6 Burnout cooling `2x`.
  - Players can prime runes (`[Z]`) while actively blocking with a shield, while spell casts (`[R]` / Sniper release) are blocked until the shield is lowered.

#### Spells:
1. `[🔥]` **Firebolt (Огненный импульс)** — Quick-Fire (`[R]`) or Sniper (`[RMB]`):
   - **Quick-Fire (`120` energy)**: Fast fiery bolt with rapid-fire dispersion bloom (`1.5°–11.5°`), ignites enemies/blocks, and lights Nether portals.
   - **Overcharged Sniper (`180` energy)**: Pinpoint `42m` beam dealing `8.5` damage with hit-flash burst and golden crosshair lock.
2. `[🔥][🔥]` **Dragon's Breath (Дыхание дракона)** — `300` energy:
   - `2.5s` (`50` ticks) continuous flamethrower cone following the player's gaze, burning and knocking back targets.
3. `[🔥][🔥][🔥]` **Lava Strider (Поступь Магмы)** — `540` energy:
   - Grants Speed II and Fire Resistance for `15s` (`300` ticks), leaves a fiery trail, and solidifies lava underfoot into walkable `Temporary Magma` blocks.
4. `[🔥][🔥][🔥][🔥]` **Flame Bastion (Бастион Пламени)** — `840` energy:
   - Triggers an expanding `5m` fiery shockwave knocking back enemies, granting Absorption II and Fire Resistance for `12s`.
5. `[🔥][🔥][🔥][🔥][🔥]` **Supernova (Сверхновая)** — `1200` energy:
   - `8m` volcanic explosion (`20` damage, upward knockback, Weakness and Slowness), followed by a `60s` Stage 6 Burnout cooldown.

---

### 5. Spell Discovery System
- Undiscovered tiers display as `??? (Unknown)` with energy cost until cast at least once.
- First cast plays a level-up chime, posts an unlock notification, and persists the discovery in NeoForge player data attachments (`ModAttachments`).

---

### 6. HUD, Crosshair & 3D Magma Visuals
- **Compact Bottom-Left HUD**: Shows the current ring icon, heat/charge/burnout status, discovered spell preview, 5 rune sockets, pulsing overheat frame at 4–5 stacks, and dynamic Latin keybind labels.
- **Unified Inverse Dynamic Crosshair**: Replaces `VanillaGuiLayers.CROSSHAIR` during Overcharged Sniper charging (shrinking inward and locking as a compact gold cross at full charge) and during Firebolt rapid-fire bloom.
- **Procedural Cast Choreography**: Smoothly interpolated 1st- and 3rd-person arm/torso animations with sustained rapid-cast arm stance (`13` ticks) + crisp recoil impulse, shockwave rings, burnout camera/hand instability, and 1st-person `MainHand` item suppression during active casts.
- **3D Normal-Extruded Magma Veins**: Animated glowing veins (`PlayerMagmaLayer`, `NormalExtrudedVertexConsumer`, `AnimatedEntityTexture`) rendered above skin and outer clothing/armor layers, including `RenderBufferFlusher` support for **Iris** shaders.

---

### 7. Optional Epic Fight Integration
- Isolated behind a bytecode-safe soft-dependency facade (`EpicFightCompat`, keeping `0` `yesman.epicfight` references in core mod classes).
- Adds Battle Mode cast animations, physical `USE` keybind support for Firebolt Sniper aiming, `PREPARE_MODEL_TO_RENDER` procedural bone blending, and 1st/3rd-person `HumanoidMesh` magma vein overlays.

---

### 8. Block: Temporary Magma (`attunedrings:temporary_magma`)
- Safely walkable with no fire damage; freezes lava beneath `Lava Strider` and cycles through 4 age states before melting back into lava.

---

### 9. Localization
- Full dual-language support: **English** (`en_us.json`) and **Russian** (`ru_ru.json`).

---

## 👥 Credits
* **Lead Developer / Systems Architecture**: `joke161`
* **Visual Assets & Dynamic Wear Textures**: `Maxim Krasnorutsky`

---

## 🛠 Technical Specifications & Building (NeoForge)

* **Minecraft Version**: `1.21.1`
* **NeoForge**: `>= 21.1` (`21.1.186`)
* **Curios API**: `>= 9.0.0` (`9.5.1+1.21.1`, required)
* **Epic Fight**: `>= 21.0` (`21.17.3.1-mc1.21.1-neoforge`, optional soft-dependency)
* **Java Version**: `21+`
* **Mappings**: Official Mojang Mappings

### Build Commands
```bash
# Build NeoForge production JAR inside neoforge/build/libs/
./gradlew :neoforge:build

# Launch NeoForge dev client
./gradlew :neoforge:runClient
```
