# Attuned Rings

A modular **Minecraft 1.21.1** magic mod built on the **Fabric Loader** with first-class **Trinkets** integration, introducing elemental attuned rings starting with the legendary **Molten Ring** (`attunedrings:molten_ring`), Magicka-style elemental spellcasting, custom HUD, dynamic wear progression, and fire/lava attunement rituals.

---

## 📋 Features

### 1. Unattuned Ring & Attunement Ritual
- **Item: Unattuned Ring (`attunedrings:unattuned_ring`)**:
  - Crafted with **8 Iron Ingots** surrounding an empty center in a Crafting Table.
  - An empty metallic vessel awaiting elemental attunement.
- **Attunement Ritual**:
  - Equip the ring in a Trinkets ring slot or hold it in hand.
  - Stand in **Fire for 10 seconds** or in **Lava for 5 seconds** (accumulating 200 ticks of heat progress).
  - Emits fiery particles, smoke, and sizzles as the heat rises.
  - Upon completion, an anvil strike and firecharge burst forge it into the **Molten Ring**.
  - Starts in a cracked, cooling burnout state (Stage 6) with a 60-second cooldown to prevent immediate invulnerability exploits.

---

### 2. Item: Molten Ring (`attunedrings:molten_ring`)
- **Stack Size**: Non-stackable (max 1 per slot).
- **Trinkets Integration**: Equippable directly into Trinkets ring slots (`hand/ring` and `offhand/ring`) or via Right-Click (`canEquipFromUse`).
- **Creative Tabs**: Dedicated `Attuned Rings` tab (`itemGroup.attunedrings`), also found in vanilla `Tools & Utilities` and `Combat`.
- **Dynamic 6-Stage Visual Wear**:
  - **100% Energy**: Pristine gold ring with glowing magma core (`molten_ring.png`).
  - **Stages 1–4**: Molten veins cool and darken progressively as energy depletes.
  - **Stage 6 (Broken / Burnout)**: Heavily fractured stone mesh (`molten_ring_6.png`), displaying during 0% energy and burnout cooldowns.
- **Clean UI**: Durability bar is hidden (`isBarVisible = false`) because wear is directly conveyed by the 6-stage texture model and HUD percentage.

---

### 3. Passive Protection (Emergency Fire & Lava Shield)
- **Grace Window (5 Seconds)**: Automatically activates when stepping into fire or lava, granting instant Fire Resistance.
- **Safe Extinguishing**: Exiting fire/lava extinguishes the player safely and triggers a **10-second cooldown** (displayed as a cooldown sweep on the item).
- **Hazard Timeout**: Staying in lava longer than 5 seconds revokes protection and triggers the 10-second cooldown.

---

### 4. Elemental Spellcasting (Magicka-Style)
Queue elemental runes and unleash powerful spells:
- **`[Z]`**: Queue an elemental flame rune `[🔥]` (up to 5 runes, with escalating audio pitch).
- **`[R]`**: Cast the queued spell.
- **`[Shift + R]`**: Clear the rune queue.

#### Spells:
1. `[🔥]` **Firebolt (Огненный импульс)** — 10% Energy:
   - Instant 28-block raycast fiery projectile.
   - Deals 5 damage and ignites targets for 4s.
   - Remote flint & steel: lights blocks and ignites Nether portals.
2. `[🔥][🔥]` **Dragon's Breath (Дыхание дракона)** — 25% Energy:
   - 2.5-second (50 ticks) continuous flamethrower cone.
   - Dynamically sweeps with the player's camera gaze, burning and knocking back all enemies in the cone.
3. `[🔥][🔥][🔥]` **Lava Strider (Поступь Магмы)** — 45% Energy:
   - Grants Speed II and Fire Resistance for 15 seconds.
   - Solidifies lava beneath the player into Temporary Magma blocks.
   - Leaves a flaming trail behind the player.
4. `[🔥][🔥][🔥][🔥]` **Flame Bastion (Бастион Пламени)** — 70% Energy:
   - Expands a 5-block fiery shockwave knocking back all enemies.
   - Grants Absorption (extra hearts) and Fire Resistance for 12 seconds.
5. `[🔥][🔥][🔥][🔥][🔥]` **Supernova (Сверхновая)** — 100% Energy:
   - Devastating magma explosion in an 8-block radius (20 damage / 10 hearts, launches enemies into the air, inflicts Weakness and Slowness).
   - Shatters the ring to Stage 6 and triggers a 60-second Burnout cooldown.

---

### 5. Spell Discovery System
- Spells display as `??? (Unknown)` with their energy cost until cast at least once.
- Upon first cast, plays a level-up chime, displays a chat unlock notification, and permanently saves the discovered spell in player attachments.

---

### 6. HUD & Visuals
- **Compact Bottom-Left Frame** (`x = 6`, `y = screenHeight - 50`):
  - Completely avoids health, hunger, armor, experience bar, and item popups.
  - **Unified Frame**: Ring icon, spell title/energy cost, 5 rune sockets, and keybind hints are all enclosed within a single dark frame.
  - **Dynamic Latin Keybinds**: Extracts raw key names (`[Z]`, `[R]`, `[Shift+R]`) from `KeyBindingHelper`, independent of the player's operating system keyboard layout.
  - **Pulsing Overheat Border**: The frame glows with an animated fiery pulse when 4–5 stacks are queued.
  - **Hand Particle Aura**: Shows a first-person particle aura on the player's right hand/shoulder, keeping the crosshair unobstructed.

---

### 7. Trinkets Integration
- Full Minecraft 1.21.1 tag support (`data/trinkets/tags/item/ring.json`, `hand/ring.json`, `offhand/ring.json`, `c/tags/item/rings.json`).
- Entity slot registration (`data/trinkets/entities/attunedrings.json`) automatically opens `hand/ring` and `offhand/ring` slots for players.
- Equip sound: `ARMOR_EQUIP_GOLD`.

---

### 8. Block: Temporary Magma (`attunedrings:temporary_magma`)
- Safely walkable with no fire damage.
- Cycles through 4 age states before melting back into lava.
- Maintained persistently while active ring wearers stand nearby.

---

### 9. Localization
- 100% dual-language support: **English** (`en_us.json`) and **Russian** (`ru_ru.json`).

---

## 👥 Credits
* **Lead Developer / Systems Architecture**: `joke161`
* **Visual Assets & Dynamic Wear Textures**: `Maxim Krasnorutsky`

---

## 🛠 Technical Specifications & Building

* **Minecraft Version**: 1.21.1
* **Fabric Loader**: >= 0.16.0
* **Fabric API**: >= 0.116.17+1.21.1
* **Trinkets**: >= 3.10.0
* **Java Version**: 21+
* **Mappings**: Official Mojang Mappings

### Build Commands
```bash
# Build production JAR inside build/libs/
./gradlew build

# Launch dev client for testing
./gradlew runClient
```
