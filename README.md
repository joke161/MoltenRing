# MoltenRing

A lightweight, production-grade **Minecraft 26.3** mod built on the **Fabric Loader**, introducing the legendary **Molten Ring** (`moltenring:molten_ring`) with passive emergency fire protection, dynamic wear progression, and an active lava-walking ability.

---

## 📋 Features

### 1. Item: Molten Ring (`moltenring:molten_ring`)
- **Stack Size**: Non-stackable (max 1 per slot).
- **Creative Tabs**: Dedicated `Molten Ring` tab (`itemGroup.moltenring`), integrated into vanilla `Tools & Utilities` and `Combat` tabs.
- **Acquisition & Crafting**:
  - **Crafting Table**: 4 Netherite Ingots, 4 Blaze Rods, and 1 Lava Bucket (returns an empty bucket upon crafting).
  - **Dungeon Generation**: 15% chance to spawn inside Nether Fortress bridge chests (`chests/nether_bridge`).
- **Dynamic Model & Wear Progression (7 Stages)**:
  - **100% Energy**: Pristine gold ring with no cracks (`ring.png`).
  - **Stages 1–5**: Micro-cracks appear dynamically, glowing dark crimson with spark particles as energy depletes.
  - **Stage 6 (Broken)**: Heavily fractured mesh with missing fragments at 0% energy or during penalty cooldown.
- **Custom Energy Bar**: Colored durability meter transitioning from green to yellow, and red during penalty lockouts.
- **First-Person Immersion**: Utilizes `ignoreSwapAnimation()` to eliminate hand-re-equip bobbing when energy states mutate.

---

### 2. Passive Ability (Emergency Lava & Fire Shield)
- **Trigger**: Activates automatically when entering lava, fire, or catching fire while held in the off-hand.
- **Grace Window (5 Seconds)**: Grants immediate *Fire Resistance* for 5 seconds to navigate out of hazard zones.
- **Rapid Safe Extinguishing**:
  - Upon exiting lava/fire, remaining burn time drops to ~2.5 seconds (down from vanilla's 15 seconds).
  - Fire Resistance persists throughout this extinguishing window, preventing residual damage.
  - Successfully extinguishing triggers a **30-second cooldown**.
- **Re-entry Penalty**: Re-entering fire or lava during the extinguishing window immediately revokes protection and triggers the **30-second cooldown**.
- **Hazard Timeout**: Failing to escape lava within 5 seconds revokes protection and initiates the **30-second cooldown**.
- **Broken State Lock**: Passive protection is disabled while the ring is in its 3-minute penalty recovery state.

---

### 3. Active Ability (Lava Walker)
- **Controls**:
  - **Right-Click** with the ring in hand.
  - **Keybind** (`R` by default, configurable in controls: `key.moltenring.toggle`).
- **Energy Pool**: 60 seconds of continuous operation (1200 ticks).
- **Lava Solidification**: Converts lava source blocks within a 3-block radius into **Temporary Magma** (`moltenring:temporary_magma`).
- **Player-Centric Platform Stabilization**: While standing on the generated platform, magma directly underneath and surrounding the player does not melt, crack, or regenerate.
- **Trail Melting**: Blocks outside the stabilization perimeter melt back into lava after 3–5 seconds.
- **Proportional Energy Regeneration**:
  - Energy regenerates at a rate of **+1 charge unit per 3 world ticks**.
  - Complete recovery from 0 to 1200 units takes **3 minutes** (3600 ticks), while partial consumption recovers proportionally faster (e.g., 20s of use recharges in just 60s).
- **Complete Depletion & Overheat**:
  - Draining the full 60-second pool (0% energy) triggers an item break sound (`ITEM_BREAK`), strips Fire Resistance, switches to the broken model (Stage 6), and initiates a **3-minute penalty lockout**.

---

### 4. Anti-Exploit System & Cooldown Synchronization
- **Proportional Cooldown Enforcement**:
  - Toggling Lava Walker off (via Right-Click or keybind `R`) applies an engine-level cooldown equal to the exact recharge time of consumed energy:
    $$\text{cooldownTicks} = \max(100, (1200 - \text{energy}) \times 3)$$
  - Features a minimum threshold of **5 seconds** (100 ticks) to protect against accidental misclicks and button spamming.
  - **Anti-Cycle Exploit**: Cooldown applies inventory-wide to the item class via vanilla `ItemCooldowns`. Swapping between multiple rings provides zero advantage, as all rings in inventory remain locked until the active ring cools down.
- **Active State Mutual Exclusion**: Pressing Right-Click while Lava Walker is already active safely deactivates the mode (`toggle off`) instead of stacking durations across multiple rings.
- **Overheat Safety Lock**: While cooldown is active (`isOnCooldown == true`), passive emergency triggers are completely disabled.
- **Relog & Respawn Persistence**: Cooldown and wear states serialize to persistent components, restoring active cooldown timers upon re-entering worlds.

---

### 5. Block: Temporary Magma (`moltenring:temporary_magma`)
- Stepping on temporary magma deals no damage to the player.
- Features 4 age states (`age: 0..3`) before reverting to lava.
- Maintained persistently by nearby active ring holders.

---

### 6. Localization & UI
- Fully localized in **English** (`en_us.json`) and **Russian** (`ru_ru.json`).
- Dynamic tooltips displaying charge percentage and current state (*Active*, *Ready*, *Shielding*, *Cooling Down*, *Recharging*, *Broken*).
- Audio feedback paired with Actionbar status updates.

---

## 👥 Credits
* **Lead Developer / Systems Architecture**: `joke161`
* **Visual Assets & Dynamic Wear Textures**: `Maxim Krasnorutsky`

---

## 🛠 Technical Specifications & Building

* **Minecraft Version**: 26.3
* **Fabric Loader**: >= 0.19.5
* **Fabric API**: >= 0.161.0+26.3
* **Java Version**: 25+ (OpenJDK 26)
* **Mappings**: Official Mojang Mappings

### Build Commands
```bash
# Build production JAR inside build/libs/
./gradlew build

# Launch dev client for testing
./gradlew runClient