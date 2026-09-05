package com.villageforge.config

/** Progress counters quests can watch. */
enum class QuestMetric {
    COPPER_MINED, ORE_SOLD, PICK_TIER, FORGE_BUILT, INGOTS_SMELTED, ITEMS_CRAFTED,
    MINERS_HIRED, GOLD_SMELTED, STEEL_PICK, CRYSTAL_BLADE, LEVEL, CRYSTAL_PICK,
}

data class QuestDef(
    val title: String,
    val desc: String,
    val metric: QuestMetric,
    val goal: Int,
    val reward: Int,
)

/** A linear chain: one active quest at a time, completed in order. */
object Quests {
    val all: List<QuestDef> = listOf(
        QuestDef("First Sparks", "Mine 10 copper ore from the valley rocks.", QuestMetric.COPPER_MINED, 10, 25),
        QuestDef("Pocket Money", "Sell 30 ore at the trading post.", QuestMetric.ORE_SOLD, 30, 50),
        QuestDef("Better Tools", "Buy the Copper Pick.", QuestMetric.PICK_TIER, 1, 75),
        QuestDef("Build the Forge", "Buy the furnace and anvil for your workshop.", QuestMetric.FORGE_BUILT, 1, 150),
        QuestDef("First Pour", "Smelt 5 ingots in the furnace.", QuestMetric.INGOTS_SMELTED, 5, 120),
        QuestDef("Handiwork", "Craft your first item at the anvil.", QuestMetric.ITEMS_CRAFTED, 1, 200),
        QuestDef("Iron Grip", "Buy the Iron Pick.", QuestMetric.PICK_TIER, 3, 400),
        QuestDef("Hired Help", "Hire a miner to work the valley.", QuestMetric.MINERS_HIRED, 1, 600),
        QuestDef("Golden Pour", "Smelt 5 gold ingots.", QuestMetric.GOLD_SMELTED, 5, 1500),
        QuestDef("Steel Resolve", "Buy the Steel Pick.", QuestMetric.STEEL_PICK, 1, 2500),
        QuestDef("Legend's Edge", "Forge the Crystal Blade.", QuestMetric.CRYSTAL_BLADE, 1, 5000),
        QuestDef("Master Smith", "Buy the Crystal Pick.", QuestMetric.CRYSTAL_PICK, 1, 10000),
    )

    fun isComplete(index: Int): Boolean = index >= all.size
}
