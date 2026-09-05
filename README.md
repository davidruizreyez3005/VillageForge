# Village Forge

A 3D idle-mining village game for Android, rendered with Google [Filament](https://github.com/google/filament). You start as a lone smith in an empty valley — a stall, a well, and a workshop. Tap to walk, mine ore, smelt it in your furnace, forge goods at the anvil, sell at the trading post — and grow the whole town yourself: fill market commissions, earn renown and prestige, raise cottages, a farmstead, a windmill and a chapel one stage at a time, and watch townsfolk move in.

## Features

- Full 3D valley, a gated north canyon, **and the Crystal Hollow** — a westward side-canyon full of glowing crystal clusters, a luminous monolith, an old mine entrance, and standing stones
- **The Forge**: smelt ore into ingots in a queued furnace (it keeps pouring while you play or sleep), then hammer ingots into finished goods at the anvil
- Mining loop: copper, tin, coal, and iron in the valley; **silver, gold, and crystal** in the canyon and hollow — each gated behind pick tiers
- Economy: carry capacity, trading post (ore + ingots + goods), storage bin stockpiling
- Upgrades: 7 pickaxe tiers (rusty → crystal), 7 boots levels, 6 backpack levels, storage bin, the forge itself
- **Hired miners**: up to five hands that mine, haul to your stockpile, and keep earning offline
- **Quest chain**: 13 objectives with coin rewards, plus a chronicle/stats screen
- **Achievements**: 26 medals with progress bars, coin rewards, and unlock banners (v2.1)
- **Music**: a procedural village soundtrack — music-box melody over a soft bass that calms down at night, toggleable from the HUD (v2.1)
- **Save slots**: three separate villages on one device, picked from the title screen (v2.0 saves migrate to slot 1 automatically)
- **Levels & XP** with level-up coin bonuses
- **Day/night cycle**: warm dawns and dusks, bright moonlit nights, torches, lanterns, and crystals that light up after dark, a furnace fire that breathes
- Animated blacksmith (walk, swing, idle bob) built from a 15-part procedural rig with hair, beard, boots, a growing ore backpack, and a full-size pickaxe that chops into the rock (v2.1 rig fix)
- **Isometric camera**: locked 45° view, drag to pan, pinch to zoom; walkers follow valley trails between zones
- Visual juice: rock debris on every strike, anvil sparks while crafting, chimney smoke while smelting, tap ripples
- Title screen with rising embers, village slot picker, and a loading flow
- Procedural sound effects (mining, hammering, pouring, coins, fanfares, medal chimes) via a raw AudioTrack synth
- Auto-saving to local JSON storage with atomic writes and v1 → v2 → v3 → v4 migration with offline progress
- True fullscreen gameplay (status bar and navigation hidden) with a proper launcher icon (v2.1.1)
- **The Village Update (v2.2)**:
  - **Commissions** — customers walk in off the south road and order specific goods; orders are filled *by selling* (no extra chore), pay a bounty + renown + prestige, and lapse quietly if ignored
  - **Renown & Prestige** — every sale builds your standing; renown unlocks build slots, prestige grows the town well through five eras (dug well → stone → roofed → pump → Millpond Fountain with a live jet), each cleanly replacing the last
  - **Build the village** — 14 slots: street lamps, a cottage row, crop fields, a farmstead, granary, windmill (turning sails), and a chapel with a visible bell. Nothing renders until you buy its stage: dig the plot, then raise the walls (v2.2.1 fix — the town used to appear fully built on day one). One press quotes the whole bill, supplies included. Completed buildings grant boons: +10% sale prices, +25% carry capacity, +15% offline pace, +20% renown
  - **Townsfolk** — every finished home moves a household in; residents keep their own hours, wander the square by day, head home at dusk, and their windows glow after dark
  - **Weather** — rain rolls over the valley every few days: the light goes grey, streaks fall, the streets empty. Nothing mechanical changes — a mood, not a tax
  - **Hold-to-buy** on every shop upgrade, and the quest Chronicle keeps full-number stats
- **Debug mode (v2.2.1)** — fresh games start with 20,000 gold so new content can be playtested without the grind (saved games always restore their own purse; flip `DebugConfig.ENABLED` to `false` for the normal economy)

## Tech stack

- Kotlin 2.0 + Jetpack Compose (HUD) over a Filament `SurfaceView`
- Filament 1.51.6 (engine) + filamat (runtime material building, emissive materials)
- kotlinx.serialization for save games
- 10 Hz fixed-step simulation with render-side interpolation

## Building

The APK is built automatically by GitHub Actions on every push to `main`.

1. Open the repository → **Actions** → **Build APK**
2. Wait for the run to finish (a few minutes)
3. Download the **village-forge-apk** artifact from the run page
4. Unzip it and sideload `VillageForge-v2.2.1.apk` onto any Android 7.0+ device

To build locally:

```bash
gradle assembleDebug   # requires JDK 17 and the Android SDK (API 34)
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/kotlin/com/villageforge/
├── MainActivity.kt          # composition root: title flow, sim loop, wiring
├── config/GameConfig.kt     # tuning tables: theme, ores, metals, items, picks, world
├── config/Town.kt           # v2.2 town layer: commissions, build slots, boons, weather
├── config/Quests.kt         # quest chain definitions
├── entities/Entities.kt     # player, rocks, hired miner walkers
├── state/GameState.kt       # authoritative game state + UI snapshots
├── core/Core.kt             # event bus, input (pan/zoom/orbit/taps), save manager
├── core/AudioManager.kt     # procedural AudioTrack synth
├── graphics/Graphics.kt     # Filament host, camera rig (orbit+pitch), asset factory
├── graphics/Rigs.kt         # 15-part humanoid rig (player + miner styles)
├── graphics/Effects.kt      # debris, sparks, smoke particles
├── graphics/WorldRenderer.kt# scene assembly, forge visuals, day/night lighting
├── systems/Systems.kt       # mining, economy, forge, crafting, miners, quests
└── ui/                      # Compose HUD: title, top/bottom bars, sheets
```

## Controls

- **Tap ground** — walk there (with a ripple marker)
- **Tap a rock** — walk over and mine it
- **Tap the trading post** — sell everything: ore, ingots, and goods
- **Tap the storage bin** — deposit ore for later (buy it first)
- **Tap the furnace** — open the forge sheet (smelt & craft)
- **Drag** — pan · **Pinch** — zoom · **Two fingers rotate** — orbit the camera
