package com.villageforge.config

/** Progress counters quests can watch. */
enum class QuestMetric {
    IRON_MINED, INGOTS_SMELTED, ITEMS_CRAFTED, PICK_LEVEL, CREW_SIZE,
    SILVER_SMELTED, GOLD_SMELTED, MYTHRIL_SMELTED, CRYSTAL_MINED, COMMISSIONS_FILLED, PRESTIGE,
    PLANKS_SAWN, EAST_CUT_OPEN, POWER_BUILT,
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
        QuestDef("First Sparks", "Mine 10 iron ore from the valley rocks.", QuestMetric.IRON_MINED, 10, 25),
        QuestDef("The Copper Run", "Smelt 5 ingots in the furnace.", QuestMetric.INGOTS_SMELTED, 5, 40),
        QuestDef("Handiwork", "Hammer out your first knife at the anvil.", QuestMetric.ITEMS_CRAFTED, 1, 60),
        QuestDef("A Name About Town", "Fill 3 commissions for market customers.", QuestMetric.COMMISSIONS_FILLED, 3, 150),
        QuestDef("Keener Edges", "Raise Pickaxe Quality to level 3 and uncover silver.", QuestMetric.PICK_LEVEL, 3, 120),
        QuestDef("Hired Help", "Put two hands on the crew roster.", QuestMetric.CREW_SIZE, 2, 150),
        QuestDef("Silver Service", "Smelt 5 silver ingots.", QuestMetric.SILVER_SMELTED, 5, 400),
        QuestDef("Quality Tells", "Raise Pickaxe Quality to level 6 and uncover gold.", QuestMetric.PICK_LEVEL, 6, 600),
        QuestDef("The West Trail", "Follow the lanterns west — find the Crystal Hollow.", QuestMetric.CRYSTAL_MINED, 1, 750),
        QuestDef("Raising the Town", "Grow the village to 40 prestige.", QuestMetric.PRESTIGE, 40, 1200),
        QuestDef("Master's Metal", "Smelt 5 mythril ingots.", QuestMetric.MYTHRIL_SMELTED, 5, 2000),
        QuestDef("The Deepest Vein", "Raise Pickaxe Quality to its maximum.", QuestMetric.PICK_LEVEL, UpgradeType.PICKAXE_QUALITY.maxLevel, 3000),
        QuestDef("Timber!", "Saw 20 planks at the mill.", QuestMetric.PLANKS_SAWN, 20, 800),
        QuestDef("Water Power", "Complete a millrace and hang its wheel.", QuestMetric.POWER_BUILT, 1, 1500),
        QuestDef("The East Cut", "Hire the Pit Master and open the second mine.", QuestMetric.EAST_CUT_OPEN, 1, 2500),
        QuestDef("A Village Built", "Grow the village to the full 172 prestige.", QuestMetric.PRESTIGE, Town.FULL_VILLAGE_PRESTIGE, 10000),
    )

    fun isComplete(index: Int): Boolean = index >= all.size
}
