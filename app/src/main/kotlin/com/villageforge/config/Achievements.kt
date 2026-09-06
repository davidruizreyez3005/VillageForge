package com.villageforge.config

import com.villageforge.state.GameState

enum class AchMetric {
    ORES_MINED, ROCKS_BROKEN, CRYSTAL_MINED, MYTHRIL_MINED, INGOTS_SMELTED, ITEMS_CRAFTED,
    COINS_EARNED, PLANKS_SAWN, PICK_LEVEL, LEVEL, CREW, QUESTS_DONE, PLAY_MINUTES,
    OFFLINE_GAIN, COMMISSIONS_FILLED, PRESTIGE, RESIDENTS, RAIN_MINUTES, WAGES_PAID, POWER,
}

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
        AchievementDef("planks_50", "Timber Yard", "Saw 50 planks at the mill.", AchMetric.PLANKS_SAWN, 50, 150),
        AchievementDef("pick_3", "Keener Edges", "Uncover silver with Pickaxe Quality 3.", AchMetric.PICK_LEVEL, 3, 100),
        AchievementDef("pick_6", "Quality Tells", "Uncover gold with Pickaxe Quality 6.", AchMetric.PICK_LEVEL, 6, 400),
        AchievementDef("pick_8", "The Deepest Vein", "Max Pickaxe Quality and uncover mythril.", AchMetric.PICK_LEVEL, 8, 2_000),
        AchievementDef("first_miner", "Hiring Help", "Put your first hand on the crew.", AchMetric.CREW, 1, 60),
        AchievementDef("crew_5", "A Proper Shop", "Five hands on the roster.", AchMetric.CREW, 5, 400),
        AchievementDef("wages_1k", "An Honest Wage", "Pay 1,000 coins in wages.", AchMetric.WAGES_PAID, 1_000, 250),
        AchievementDef("crystal_50", "Crystal Rush", "Mine 50 crystal ore in the hollow.", AchMetric.CRYSTAL_MINED, 50, 1_000),
        AchievementDef("mythril_25", "Master's Metal", "Mine 25 mythril ore.", AchMetric.MYTHRIL_MINED, 25, 1_200),
        AchievementDef("level_10", "Seasoned Smith", "Reach level 10.", AchMetric.LEVEL, 10, 300),
        AchievementDef("level_20", "Legend of the Valley", "Reach level 20.", AchMetric.LEVEL, 20, 1_200),
        AchievementDef("quests_done", "Valley Champion", "Complete the whole quest chain.", AchMetric.QUESTS_DONE, 1, 1_500),
        AchievementDef("play_60", "A Fine Evening", "Play for 60 minutes in total.", AchMetric.PLAY_MINUTES, 60, 100),
        AchievementDef("offline_500", "Dreams of Idle", "Gather 500 goods while away.", AchMetric.OFFLINE_GAIN, 500, 350),
        AchievementDef("first_order", "A Name in the Valley", "Fill your first market commission.", AchMetric.COMMISSIONS_FILLED, 1, 60),
        AchievementDef("orders_25", "The Trusted Smith", "Fill 25 commissions for market customers.", AchMetric.COMMISSIONS_FILLED, 25, 900),
        AchievementDef("first_home", "Landlord", "Raise your first cottage.", AchMetric.RESIDENTS, 2, 150),
        AchievementDef("power_1", "Water Power", "Hang your first millwheel.", AchMetric.POWER, 1, 400),
        AchievementDef("power_2", "The Wheels Turn", "Complete both millraces.", AchMetric.POWER, 2, 1_200),
        AchievementDef("town_50", "The Village Green", "Grow the village to 50 prestige.", AchMetric.PRESTIGE, 50, 800),
        AchievementDef("town_100", "A Proper Town", "Grow the village to 100 prestige.", AchMetric.PRESTIGE, 100, 2_500),
        AchievementDef("town_172", "A Village Built", "Reach the full 172 prestige of a finished village.", AchMetric.PRESTIGE, 172, 10_000),
        AchievementDef("rain_5", "Petrichor", "Watch the rain fall on the village for 5 minutes in total.", AchMetric.RAIN_MINUTES, 5, 120),
    )

    fun progress(gs: GameState, def: AchievementDef): Int = when (def.metric) {
        AchMetric.ORES_MINED -> gs.stats.oresMined.sum()
        AchMetric.ROCKS_BROKEN -> gs.stats.rocksBroken
        AchMetric.CRYSTAL_MINED -> gs.stats.oresMined[Ore.CRYSTAL.ordinal]
        AchMetric.MYTHRIL_MINED -> gs.stats.oresMined[Ore.MYTHRIL.ordinal]
        AchMetric.INGOTS_SMELTED -> gs.stats.ingotsSmeltedTotal()
        AchMetric.ITEMS_CRAFTED -> gs.stats.itemsCraftedTotal()
        AchMetric.COINS_EARNED -> gs.stats.coinsEarnedTotal
        AchMetric.PLANKS_SAWN -> gs.stats.planksSawn
        AchMetric.PICK_LEVEL -> gs.pickLevel
        AchMetric.LEVEL -> gs.level
        AchMetric.CREW -> gs.workers.size
        AchMetric.QUESTS_DONE -> if (Quests.isComplete(gs.questIndex)) 1 else 0
        AchMetric.PLAY_MINUTES -> (gs.stats.playSeconds / 60f).toInt()
        AchMetric.OFFLINE_GAIN -> gs.stats.offlineGains
        AchMetric.COMMISSIONS_FILLED -> gs.stats.commissionsFilled
        AchMetric.PRESTIGE -> gs.prestige()
        AchMetric.RESIDENTS -> gs.residents.size
        AchMetric.RAIN_MINUTES -> (gs.stats.rainSeconds / 60f).toInt()
        AchMetric.WAGES_PAID -> gs.stats.wagesPaid
        AchMetric.POWER -> Town.powerGenerated(gs.villageSlots)
    }
}
