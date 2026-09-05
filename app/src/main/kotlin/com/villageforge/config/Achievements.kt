package com.villageforge.config

import com.villageforge.state.GameState

/**
 * v2.1 medal board. Achievements are checked by [com.villageforge.systems.AchievementSystem]
 * at 10 Hz; each unlock pays a coin reward immediately.
 */
enum class AchMetric { ORES_MINED, ROCKS_BROKEN, CRYSTAL_MINED, INGOTS_SMELTED, ITEMS_CRAFTED, COINS_EARNED, ORE_SOLD, PICK_TIER, LEVEL, MINERS, QUESTS_DONE, PLAY_MINUTES, OFFLINE_GAIN, FURNACE_OWNED }

data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val metric: AchMetric,
    val goal: Int,
    val reward: Int,
)

object Achievements {

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_ore", "First Spark", "Mine your very first chunk of ore.", AchMetric.ORES_MINED, 1, 10),
        AchievementDef("ores_25", "Pebble Pusher", "Mine 25 ore chunks.", AchMetric.ORES_MINED, 25, 30),
        AchievementDef("ores_100", "Quarry Hand", "Mine 100 ore chunks.", AchMetric.ORES_MINED, 100, 120),
        AchievementDef("ores_500", "Mountain Mover", "Mine 500 ore chunks.", AchMetric.ORES_MINED, 500, 600),
        AchievementDef("rocks_50", "Rock Breaker", "Shatter 50 rocks.", AchMetric.ROCKS_BROKEN, 50, 80),
        AchievementDef("first_pour", "First Pour", "Smelt your first ingot.", AchMetric.INGOTS_SMELTED, 1, 20),
        AchievementDef("ingots_50", "Smelter", "Smelt 50 ingots.", AchMetric.INGOTS_SMELTED, 50, 150),
        AchievementDef("ingots_250", "Master Smelter", "Smelt 250 ingots.", AchMetric.INGOTS_SMELTED, 250, 700),
        AchievementDef("first_craft", "Forgeworks", "Hammer out your first item.", AchMetric.ITEMS_CRAFTED, 1, 25),
        AchievementDef("craft_25", "Artisan", "Craft 25 items at the anvil.", AchMetric.ITEMS_CRAFTED, 25, 200),
        AchievementDef("craft_100", "Grandmaster Smith", "Craft 100 items at the anvil.", AchMetric.ITEMS_CRAFTED, 100, 900),
        AchievementDef("coins_100", "First Coin", "Earn 100 coins in total.", AchMetric.COINS_EARNED, 100, 15),
        AchievementDef("coins_10k", "Coin Collector", "Earn 10,000 coins in total.", AchMetric.COINS_EARNED, 10_000, 250),
        AchievementDef("coins_100k", "Valley Tycoon", "Earn 100,000 coins in total.", AchMetric.COINS_EARNED, 100_000, 2_500),
        AchievementDef("ore_sold_200", "Trusted Trader", "Sell 200 ore at the trade post.", AchMetric.ORE_SOLD, 200, 120),
        AchievementDef("steel_pick", "Steel Ambition", "Own the steel pickaxe.", AchMetric.PICK_TIER, Picks.STEEL.ordinal, 150),
        AchievementDef("crystal_pick", "Crystal Dream", "Own the legendary crystal pickaxe.", AchMetric.PICK_TIER, Picks.CRYSTAL.ordinal, 2_000),
        AchievementDef("furnace", "Workshop Open", "Build the furnace and anvil.", AchMetric.FURNACE_OWNED, 1, 50),
        AchievementDef("first_miner", "Hiring Help", "Hire your first miner.", AchMetric.MINERS, 1, 100),
        AchievementDef("full_crew", "Full Crew", "Hire the maximum crew of ${com.villageforge.config.Miners.HIRE_COSTS.size} miners.", AchMetric.MINERS, com.villageforge.config.Miners.HIRE_COSTS.size, 800),
        AchievementDef("crystal_50", "Crystal Rush", "Mine 50 crystal ore in the hollow.", AchMetric.CRYSTAL_MINED, 50, 1_000),
        AchievementDef("level_10", "Seasoned Smith", "Reach level 10.", AchMetric.LEVEL, 10, 300),
        AchievementDef("level_20", "Legend of the Valley", "Reach level 20.", AchMetric.LEVEL, 20, 1_200),
        AchievementDef("quests_done", "Valley Champion", "Complete the whole quest chain.", AchMetric.QUESTS_DONE, 1, 1_500),
        AchievementDef("play_60", "A Fine Evening", "Play for 60 minutes in total.", AchMetric.PLAY_MINUTES, 60, 100),
        AchievementDef("offline_1k", "Dreams of Idle", "Gather 1,000 ore and ingots while away.", AchMetric.OFFLINE_GAIN, 1_000, 350),
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    fun progress(gs: GameState, def: AchievementDef): Int = when (def.metric) {
        AchMetric.ORES_MINED -> gs.stats.oresMined.sum()
        AchMetric.ROCKS_BROKEN -> gs.stats.rocksBroken
        AchMetric.CRYSTAL_MINED -> gs.stats.oresMined[Ore.CRYSTAL.ordinal]
        AchMetric.INGOTS_SMELTED -> gs.stats.ingotsSmeltedTotal()
        AchMetric.ITEMS_CRAFTED -> gs.stats.itemsCraftedTotal()
        AchMetric.COINS_EARNED -> gs.stats.coinsEarnedTotal
        AchMetric.ORE_SOLD -> gs.stats.oreSold
        AchMetric.PICK_TIER -> gs.pickTier
        AchMetric.LEVEL -> gs.level
        AchMetric.MINERS -> gs.miners.size
        AchMetric.QUESTS_DONE -> if (Quests.isComplete(gs.questIndex)) 1 else 0
        AchMetric.PLAY_MINUTES -> (gs.stats.playSeconds / 60f).toInt()
        AchMetric.OFFLINE_GAIN -> gs.stats.offlineGains
        AchMetric.FURNACE_OWNED -> if (gs.furnaceOwned) 1 else 0
    }
}
