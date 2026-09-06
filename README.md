# Village Forge

A 3D medieval blacksmith village-sim for Android, rendered with Google [Filament](https://github.com/google/filament). You are the smith — the fastest pair of hands in town. Mine ore, haul it to the yard bins, smelt it into ingots, hammer ingots into goods at the anvil, and sell at the market to townsfolk who commission exactly what they want. Spend the proceeds on personal upgrades, a hired crew that automates (but never outperforms) you, and a village that visibly grows — cottages, a farm, a chapel, a windmill, mechanical water power — as a physical prestige system. **v3.0 is the Premium Port: every balance number now matches the validated prototype exactly.**

## Features

- **The prototype economy, number for number (v3.0)**: Iron / Copper / Silver / Gold / Mythril (plus Crystal as an Android-exclusive sixth tier in the Hollow), 1:1 smelting, ingots at 2/4/7/12/28 coins, and the five prototype products — Knife, Copper Kettle, Silver Cutlery, Gold Ornament, Mythril Blade. Raw ore never sells: the market buys ingots and finished goods only, and sale prices scale with Reputation (+6%/level)
- **The 16-upgrade tree (v3.0)**: Swift Boots, Miner's Rhythm, Leather Pack, Pickaxe Quality (unlocks silver/gold/mythril at Lv 3/6/8 with +4% double-ore per level), Deeper Bins, Ore Heap, Great Bellows, Wide Hopper, Master Tongs, Twin Strike (+4% twin-craft per level), Long Rack, Reputation, Night Market, Night Shift, Crew Training — prototype costs and growth rates throughout
- **The full crew (v3.0)**: hire Miners, a Carrier, a Smelter, a Lumberjack, a Blacksmith and a Merchant; the premium Master Smith (second anvil, all rare ingots), Master Smelter (feeds Furnace II precious ore), and the Pit Master (opens the East Cut); plus five ore-locked specialists. Wages leave the purse once a minute — miss a payroll and the crew downs tools. Automation buys freedom; it never replaces the smith
- **Furnaces and lanes (v3.0)**: hopper-in / tray-out smelting, one ore at a time; Furnace II unlocks when both furnace upgrades are maxed and takes the precious tiers while the base fire keeps iron and copper; the Great Bellows scales smelt time ×0.93/level and the powered Bellows House stacks a further ×0.72. The forge runs two lanes — your anvil (hammer it yourself, fastest) or the Blacksmith's; the Master Smith works Lane B; the Trip Hammer is a 0.4 floor under an untended anvil
- **The wood chain (v3.0)**: every valley tree is fellable timber (5 HP, 16s regrow); the Lumberjack runs the whole line solo; the sawmill cuts a plank every 16s (×0.65 under any millrace), feeding the build ladder alongside the coin-bought supply yard
- **The East Cut (v3.0)**: a second mine field with its own stockpile, opened the moment the Pit Master is hired — carriers always service the fuller pile
- **The complete 172-prestige ladder (v3.0)**: four lamp posts, four home sites that grow through Cottage → Dormer Cottage → Longhouse → Merchant's House, a two-stage farmstead, four crop fields, granary, two-stage windmill, two-stage chapel, two millraces, the Bellows House and the Trip Hammer — machines that cannot be built without spare water power. The well climbs seven tiers to the Millpond Fountain at exactly 172 prestige. Commissions: the board opens at 12 renown, 1/2/3 simultaneous slots at 0/45/110, bounties of 1.6–1.8× face value, and customers who keep the Merchant's hours and stay in out of the rain
- **Corrected boons (v3.0)**: Farmstead −10% crew wages (floored at 50%), each crop field +2% crew speed, Granary +25% storage caps, Windmill +15% offline duration, Chapel +20% renown
- **Spec rhythm (v3.0)**: 480-second days with the 0.42 night floor, merchant's hours (the Night Market upgrade lifts them for the hired Merchant only — the player can always sell), offline progress at 50% throughput capped by Night Shift (2h + 1.5h/level), and the wheelbarrow capstone (+50% carry, +15% speed, visibly pushed) when Pack and Boots are both maxed
- **The camera (v3.0)**: fixed orthographic, 45° yaw / 32° pitch, view height 26 at 1×, zoom 0.30×–2.2×; one-finger drag pans, pinch zooms, double-tap recenters on the player
- Full 3D valley, a gated north canyon, **and the Crystal Hollow** — a westward side-canyon full of glowing crystal clusters, a luminous monolith, an old mine entrance, and standing stones
- **Quest chain**: 16 objectives with coin rewards, plus the Chronicle record (now tracking timber, planks, wages paid)
- **Achievements**: 37 medals with progress bars, coin rewards, and unlock banners
- **Music**: a procedural village soundtrack — music-box melody over a soft bass that calms down at night, toggleable from the HUD
- **Save slots**: three separate villages on one device, with **save v5 (v3.0)** — a sanitize pass clamps every count against the caps the save's own upgrade levels allow; v1–v4 villages keep their purse, renown, prestige, buildings and crew while the re-balanced economy stashes reset
- **Day/night cycle**: warm dawns and dusks, bright moonlit nights, torches, lanterns, and crystals that light up after dark, a furnace fire that breathes
- **Carried torches**: as dusk gathers the smith and the crew light torches — a warm, flickering light travels with you so low-brightness screens stay playable all night
- **Reforged world**: chamfered buildings, round posts and tapered towers, conical pines, faceted tree canopies, craggy cliff walls, gem crystal shards and flames, solid gable roofs with real eaves, cloth windmill sails, an octagonal well and fountain, an arched furnace mouth, and grass tufts + pebbles scattered across the ground
- Animated characters (walk, swing, idle bob) built from a 19-part procedural rig — including the pickaxe that chops into the rock and the wheelbarrow when earned
- Visual juice: rock debris on strikes, anvil sparks while crafting, chimney smoke while smelting, tap ripples
- Title screen with rising embers, village slot picker, and a loading flow
- Procedural sound effects (mining, chopping, hammering, pouring, coins, fanfares, medal chimes) via a raw AudioTrack synth
- **Weather** — rain rolls over the valley every few days: the light goes grey, streaks fall, the streets empty. Nothing mechanical changes — a mood, not a tax
- **Townsfolk** — every finished home moves a household in; residents keep their own hours, wander the square by day, head home at dusk, and their windows glow after dark
- **Hold-to-buy** on every upgrade and material row
- **Debug mode** — fresh games start with 20,000 gold so new content can be playtested without the grind (saved games always restore their own purse; flip `DebugConfig.ENABLED` to `false` for the normal economy)

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
4. Unzip it and sideload `VillageForge-v3.0.apk` onto any Android 7.0+ device

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
