package com.villageforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villageforge.config.Buildings
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Miners
import com.villageforge.config.Ore
import com.villageforge.config.Picks
import com.villageforge.config.Upgrades
import com.villageforge.state.GameState

data class StatsView(
    val oreMined: Int,
    val coinsEarned: Int,
    val ingotsSmelted: Int,
    val itemsCrafted: Int,
    val minersHired: Int,
    val playSeconds: Int,
)

// ---- Shop ----------------------------------------------------------------

@Composable
fun BoxScope.ShopSheet(
    game: GameState,
    coins: Int,
    binOwned: Boolean,
    upgrades: GameState.UpgradeSnapshot,
    miners: GameState.MinerSnapshot,
    onClose: () -> Unit,
) {
    SheetShell("Shop", onClose) {
        val pick = Picks.entries[upgrades.pickTier]
        val nextPick = Picks.entries.getOrNull(upgrades.pickTier + 1)
        UpgradeRow(
            title = "Pickaxe — ${pick.label}",
            detail = if (nextPick == null) "Max tier · ${pick.damage} damage/swing"
            else "Next: ${nextPick.label} · ${nextPick.damage} dmg/swing" +
                if (nextPick.doubleOreChance > 0f) " · +${(nextPick.doubleOreChance * 100).toInt()}% double ore" else "",
            buyLabel = nextPick?.let { "${it.cost} c" },
            canBuy = nextPick != null && coins >= nextPick.cost,
            onBuy = { game.enqueue(GameState.Command.BuyPick) },
        )
        UpgradeDivider()

        val bootsMax = upgrades.bootsLevel >= Upgrades.BOOTS_COSTS.size
        UpgradeRow(
            title = "Boots — Lv ${upgrades.bootsLevel}/${Upgrades.BOOTS_COSTS.size}",
            detail = if (bootsMax) "Max · ${Upgrades.moveSpeed(upgrades.bootsLevel)} speed"
            else "Speed ${Upgrades.moveSpeed(upgrades.bootsLevel)} → ${Upgrades.moveSpeed(upgrades.bootsLevel + 1)}",
            buyLabel = if (bootsMax) null else "${Upgrades.BOOTS_COSTS[upgrades.bootsLevel]} c",
            canBuy = !bootsMax && coins >= Upgrades.BOOTS_COSTS[upgrades.bootsLevel],
            onBuy = { game.enqueue(GameState.Command.BuyBoots) },
        )
        UpgradeDivider()

        val packMax = upgrades.backpackLevel >= Upgrades.BACKPACK_COSTS.size
        UpgradeRow(
            title = "Backpack — Lv ${upgrades.backpackLevel}/${Upgrades.BACKPACK_COSTS.size}",
            detail = if (packMax) "Max · carries ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel]}"
            else "Carries ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel]} → ${Upgrades.BACKPACK_CAPACITIES[upgrades.backpackLevel + 1]}",
            buyLabel = if (packMax) null else "${Upgrades.BACKPACK_COSTS[upgrades.backpackLevel]} c",
            canBuy = !packMax && coins >= Upgrades.BACKPACK_COSTS[upgrades.backpackLevel],
            onBuy = { game.enqueue(GameState.Command.BuyBackpack) },
        )

        if (!binOwned) {
            UpgradeDivider()
            UpgradeRow(
                title = "Storage Bin",
                detail = "Bank ore at the camp; it still sells with you.",
                buyLabel = "${Buildings.BIN_COST} c",
                canBuy = coins >= Buildings.BIN_COST,
                onBuy = { game.enqueue(GameState.Command.BuyBin) },
            )
        }
        UpgradeDivider()
        val minerMax = miners.count >= miners.max
        UpgradeRow(
            title = "Hire Miner — ${miners.count}/${miners.max}",
            detail = if (minerMax) "Full crew · they mine while you're away"
            else "Works the valley and hauls to your stockpile",
            buyLabel = if (minerMax) null else "${miners.nextCost} c",
            canBuy = !minerMax && coins >= miners.nextCost,
            onBuy = { game.enqueue(GameState.Command.HireMiner) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UpgradeRow(title: String, detail: String, buyLabel: String?, canBuy: Boolean, onBuy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            BasicText(detail, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp))
        }
        if (buyLabel != null) {
            Spacer(Modifier.width(10.dp))
            ActionChip(buyLabel, canBuy) { onBuy() }
        }
    }
}

// ---- Forge -----------------------------------------------------------------

@Composable
fun BoxScope.ForgeSheet(
    game: GameState,
    coins: Int,
    forge: GameState.ForgeSnapshot,
    ingots: GameState.IngotSnapshot,
    items: GameState.ItemSnapshot,
    carry: GameState.CarrySnapshot,
    stock: GameState.StockpileSnapshot,
    onClose: () -> Unit,
) {
    SheetShell("The Forge", onClose) {
        if (!forge.furnaceOwned) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    BasicText(
                        "Build your workshop",
                        style = TextStyle(color = Color(UiColors.HEADER), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        "A stone furnace and anvil. Smelt ore into ingots, hammer ingots into goods worth far more.",
                        style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionChip(
                        if (coins >= Buildings.FURNACE_COST) "Build · ${Buildings.FURNACE_COST} c" else "Need ${Buildings.FURNACE_COST} c",
                        coins >= Buildings.FURNACE_COST,
                    ) { game.enqueue(GameState.Command.BuyForge) }
                }
            }
        } else {
            FurnaceCard(forge)
            Spacer(Modifier.height(12.dp))

            SectionHeader("Smelting — load the furnace")
            val oreAvailable = { index: Int -> carry.oreCounts[index] + stock.oreCounts[index] }
            for (metal in Metal.entries) {
                val affordable = metal.recipe.all { (ore, need) -> oreAvailable(ore.ordinal) >= need }
                val queueFull = forge.queue.size >= forge.queueCapacity
                RecipeRow(
                    title = metal.label,
                    swatch = metalColor(metal),
                    detail = ingredientText(metal.recipe.map { (ore, need) ->
                        Triple(oreName(ore), oreAvailable(ore.ordinal), need)
                    }),
                    meta = "smelt ${metal.smeltSeconds.toInt()}s · sells ${metal.sell} c",
                    action = "Load",
                    enabled = affordable && !queueFull,
                ) { game.enqueue(GameState.Command.LoadFurnace(metal)) }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader("Crafting — hammer at the anvil")
            for (item in Item.entries) {
                val parts = item.metals.map { (metal, need) ->
                    Triple(metal.label, ingots.counts[metal.ordinal], need)
                }.toMutableList()
                if (item.crystal > 0) {
                    parts.add(Triple("Crystal ore", oreAvailable(Ore.CRYSTAL.ordinal), item.crystal))
                }
                val affordable = parts.all { (_, have, need) -> have >= need }
                RecipeRow(
                    title = item.label,
                    swatch = UiColors.EMBER,
                    detail = ingredientText(parts),
                    meta = "craft ${item.craftSeconds.toInt()}s · sells ${item.sell} c",
                    action = "Craft",
                    enabled = affordable,
                ) { game.enqueue(GameState.Command.Craft(item)) }
                Spacer(Modifier.height(6.dp))
            }

            if (ingots.total + items.total > 0) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Workshop stock")
                StockStrip(ingots, items)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(label: String) {
    BasicText(
        label.uppercase(),
        style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
    Spacer(Modifier.height(8.dp))
}

/** Coloured ingredient list: red where you are short. */
private fun ingredientText(parts: List<Triple<String, Int, Int>>): AnnotatedString {
    val b = AnnotatedString.Builder()
    parts.forEachIndexed { i, (label, have, need) ->
        if (i > 0) b.append("  ·  ")
        val ok = have >= need
        b.pushStyle(SpanStyle(color = Color(if (ok) UiColors.TEXT else UiColors.WARN), fontWeight = if (ok) FontWeight.Normal else FontWeight.Bold))
        b.append("$need $label")
        b.pop()
        b.pushStyle(SpanStyle(color = Color(UiColors.DIM), fontSize = 11.sp))
        b.append(" ($have)")
        b.pop()
    }
    return b.toAnnotatedString()
}

@Composable
private fun FurnaceCard(forge: GameState.ForgeSnapshot) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(UiColors.EMBER), CircleShape))
                Spacer(Modifier.width(8.dp))
                BasicText(
                    "Furnace",
                    style = TextStyle(color = Color(UiColors.HEADER), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                // Queue slot dots.
                Row {
                    repeat(forge.queueCapacity) { i ->
                        val filled = i < forge.queue.size
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(10.dp)
                                .background(
                                    Color(if (filled) metalColor(forge.queue[i].metal) else UiColors.PANEL_SUNK),
                                    CircleShape,
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val active = forge.queue.firstOrNull()
            if (active != null) {
                BasicText(
                    "Smelting ${active.metal.label} — ${"%.1f".format(active.remain)}s",
                    style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
                )
                Spacer(Modifier.height(6.dp))
                ProgressBar(1f - active.remain / active.total.coerceAtLeast(0.01f), UiColors.EMBER)
            } else {
                BasicText(
                    "Idle — queue up to ${forge.queueCapacity} batches",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun RecipeRow(
    title: String,
    swatch: Int,
    detail: AnnotatedString,
    meta: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(swatch), CircleShape))
                    Spacer(Modifier.width(7.dp))
                    BasicText(title, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(3.dp))
                BasicText(detail, style = TextStyle(fontSize = 12.sp))
                BasicText(meta, style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp))
            }
            Spacer(Modifier.width(10.dp))
            ActionChip(action, enabled) { onClick() }
        }
    }
}

@Composable
private fun StockStrip(ingots: GameState.IngotSnapshot, items: GameState.ItemSnapshot) {
    Column {
        Row {
            for ((i, metal) in Metal.entries.withIndex()) {
                val count = ingots.counts[i]
                if (count <= 0) continue
                Row(
                    Modifier
                        .padding(end = 10.dp, bottom = 4.dp)
                        .background(Color(UiColors.PANEL_SUNK), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Color(metalColor(metal)), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    BasicText("×$count", style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp))
                }
            }
        }
        if (items.total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "${items.total} finished goods",
                    style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    "worth ${items.totalValue} c",
                    style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// ---- Quests ----------------------------------------------------------------

@Composable
fun BoxScope.QuestSheet(
    quest: GameState.QuestSnapshot,
    stats: StatsView,
    onClose: () -> Unit,
) {
    SheetShell("Quests", onClose) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                BasicText(
                    quest.title,
                    style = TextStyle(color = Color(UiColors.HEADER), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(quest.desc, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp))
                Spacer(Modifier.height(10.dp))
                if (!quest.allDone) {
                    ProgressBar(quest.progress.toFloat() / quest.goal.coerceAtLeast(1), UiColors.XP)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        BasicText(
                            "${quest.progress.coerceAtMost(quest.goal)} / ${quest.goal}",
                            style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            "Reward +${quest.reward} c",
                            style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 13.sp),
                        )
                    }
                } else {
                    BasicText(
                        "Every quest complete. The valley is yours.",
                        style = TextStyle(color = Color(UiColors.GOOD), fontSize = 13.sp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionHeader("Chronicle")
        StatRow("Ore mined", "${stats.oreMined}")
        StatRow("Coins earned", "${stats.coinsEarned} c")
        StatRow("Ingots smelted", "${stats.ingotsSmelted}")
        StatRow("Items crafted", "${stats.itemsCrafted}")
        StatRow("Miners hired", "${stats.minersHired}")
        StatRow("Time played", formatDuration(stats.playSeconds))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

// ---- Offline earnings -------------------------------------------------------

@Composable
fun BoxScope.OfflineModal(report: GameState.OfflineReport, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(UiColors.SCRIM))
            .clickable { }
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .background(Color(UiColors.PANEL), RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        BasicText(
            "While you were away",
            style = TextStyle(color = Color(UiColors.HEADER), fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            "${formatDuration(report.awaySeconds.toInt())} of village time",
            style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
        )
        Spacer(Modifier.height(12.dp))
        val oreTotal = report.oreGains.sum()
        if (oreTotal > 0) {
            BasicText(
                "Miners gathered $oreTotal ore",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
            BasicText(
                gainsSummary(report.oreGains) { oreName(Ore.entries[it]) },
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
            )
            BasicText("→ added to your stockpile", style = TextStyle(color = Color(UiColors.GOOD), fontSize = 12.sp))
        }
        val ingotTotal = report.ingotGains.sum()
        if (ingotTotal > 0) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                "The furnace finished $ingotTotal ingots",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
            BasicText(
                gainsSummary(report.ingotGains) { Metal.entries[it].label },
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .background(Color(UiColors.EMBER), RoundedCornerShape(10.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 34.dp, vertical = 10.dp)
        ) {
            BasicText(
                "Back to work",
                style = TextStyle(color = Color(0xFF241505), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun gainsSummary(gains: List<Int>, name: (Int) -> String): String =
    gains.mapIndexed { i, n -> if (n > 0) "${n} ${name(i)}" else null }
        .filterNotNull()
        .joinToString(" · ")
