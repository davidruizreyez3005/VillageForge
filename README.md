# Village Forge

A 3D idle-mining village game for Android, rendered with Google [Filament](https://github.com/google/filament). Tap to walk, mine ore, haul it back, sell it at the trading post, and upgrade your blacksmith.

## Features

- Full 3D valley with procedurally generated terrain, cliffs, trees, and rocks
- Animated blacksmith character (walk, swing, idle bob) built from a 9-part procedural rig
- Mining loop: copper, tin, coal, and iron rocks with HP, respawn timers, and pick-tier requirements
- Economy: carry capacity, trading post selling, storage bin stockpiling
- Upgrades: 6 pickaxe tiers (rusty → masterwork), boots speed levels, backpack capacity levels
- Procedural sound effects via a raw AudioTrack synth (no audio assets needed)
- Auto-saving to local JSON storage with atomic writes
- Real-time shadows, PBR materials compiled at runtime through filamat

## Tech stack

- Kotlin 2.0 + Jetpack Compose (HUD) over a Filament `SurfaceView`
- Filament 1.51.6 (engine) + filamat (runtime material building)
- kotlinx.serialization for save games
- 10 Hz fixed-step simulation with render-side interpolation

## Building

The APK is built automatically by GitHub Actions on every push to `main`.

1. Open the repository → **Actions** → **Build APK**
2. Wait for the run to finish (a few minutes)
3. Download the **village-forge-apk** artifact from the run page
4. Unzip it and sideload `app-debug.apk` onto any Android 7.0+ device

To build locally:

```bash
gradle assembleDebug   # requires JDK 17 and the Android SDK (API 34)
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/kotlin/com/villageforge/
├── MainActivity.kt          # composition root + 10 Hz sim loop
├── config/GameConfig.kt     # tuning tables: theme, ores, picks, world layout
├── entities/Entities.kt     # player and rock entities
├── state/GameState.kt       # authoritative game state + UI snapshots
├── core/Core.kt             # event bus, input handling, save manager
├── core/AudioManager.kt     # procedural AudioTrack synth
├── graphics/Graphics.kt     # Filament host, camera rig, asset factory
├── graphics/WorldRenderer.kt# scene assembly + player rig
├── systems/Systems.kt       # mining, economy, buildings, upgrades
└── ui/Hud.kt                # Compose HUD: shop, carry panel, floating text
```

## Controls

- **Tap ground** — walk there
- **Tap a rock** — walk over and mine it
- **Tap the trading post** — sell everything you're carrying
- **Tap the storage bin** — deposit ore for later (buy it first)
- **Drag / pinch** — pan and zoom the camera
