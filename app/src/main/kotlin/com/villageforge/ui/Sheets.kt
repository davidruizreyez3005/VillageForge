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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villageforge.config.AchievementDef
import com.villageforge.config.Achievements
import com.villageforge.config.Item
import com.villageforge.config.Metal
import com.villageforge.config.Ore
import com.villageforge.config.Role
import com.villageforge.config.UpgradeType
import com.villageforge.state.GameState

data class StatsView(
    val oreMined: Int,
    val coinsEarned: Int,
    val ingotsSmelted: Int,
    val itemsCrafted: Int,
    val crewSize: Int,
    val playSeconds: Int,
    val renownEarned: Int = 0,
    val commissionsFilled: Int = 0,
    val prestige: Int = 0,
    val residents: Int = 0,
    val rainMinutes: Int = 0,
    val timberFelled: Int = 0,
    val planksSawn: Int = 0,
    val wagesPaid: Int = 0,
)

// ---- Upgrades (the 16-row tree) -------------------------------------------

@Composable
fun BoxScope.ShopSheet(
    game: GameState,
    coins: Int,
    upgrades: List<GameState.UpgradeView>,
    onClose: () -> Unit,
) {
    SheetShell("Upgrades", onClose) {
        var lastCategory = ""
        for (u in upgrades) {
            if (u.category != lastCategory) {
                if (lastCategory.isNotEmpty()) UpgradeDivider()
                SectionHeader(u.category)
                lastCategory = u.category
            }
            val maxed = u.level >= u.maxLevel
            UpgradeRow(
                title = "${u.label} — Lv ${u.level}/${u.maxLevel}",
                detail = u.effect,
                buyLabel = if (maxed) null else "${u.cost} c",
                canBuy = !maxed && coins >= u.cost,
                onBuy = { game.enqueue(GameState.Command.BuyUpgrade(u.type)) },
            )
            Spacer(Modifier.height(4.dp))
        }
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
            // Hold the button and it keeps buying (M45 of the prototype).
            HoldRepeatChip(buyLabel, canBuy) { onBuy() }
        }
    }
}

// ---- Crew ------------------------------------------------------------------

@Composable
fun BoxScope.CrewSheet(game: GameState, crew: GameState.CrewSnapshot, coins: Int, onClose: () -> Unit) {
    SheetShell("The Crew", onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Wages ${crew.wagePerMin} c/min",
                style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                "hands ×${"%.2f".format(crew.crewSpeedMul)}",
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
            )
        }
        if (crew.wagesUnpaid) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                "Wages unpaid — the crew has downed tools until the next payroll clears.",
                style = TextStyle(color = Color(UiColors.WARN), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (crew.members.isNotEmpty()) {
            SectionHeader("On the roster")
            for (m in crew.members) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            m.role.label,
                            style = TextStyle(
                                color = Color(if (m.paused || crew.wagesUnpaid) UiColors.DIM else UiColors.HEADER),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            ),
                        )
                        BasicText(
                            if (m.paused || crew.wagesUnpaid) "downed tools" else m.role.blurb,
                            style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ActionChip("Let go", true, danger = true) {
                        game.enqueue(GameState.Command.FireWorker(m.index))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        SectionHeader("Hire")
        for (o in crew.hireOptions) {
            val locked = o.lockedReason != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            o.role.label,
                            style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        )
                        if (o.hired) {
                            Spacer(Modifier.width(6.dp))
                            BasicText("✓ hired", style = TextStyle(color = Color(UiColors.GOOD), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    BasicText(o.role.blurb, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 11.sp))
                    BasicText(
                        if (locked) "🔒 ${o.lockedReason}"
                        else "${o.role.speed} speed · carries ${o.role.carry} · ${o.wage} c/min",
                        style = TextStyle(color = Color(if (locked) UiColors.DIM else UiColors.DIM), fontSize = 11.sp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (!locked) {
                    HoldRepeatChip("${o.cost} c", coins >= o.cost) {
                        game.enqueue(GameState.Command.HireWorker(o.role))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---- Orders ------------------------------------------------------------------

@Composable
fun BoxScope.OrdersSheet(orders: GameState.OrdersSnapshot, onClose: () -> Unit) {
    SheetShell("Market Orders", onClose) {
        if (!orders.boardOpen) {
            BasicText(
                "The board is quiet. Sell your wares and word of a reliable smith will spread — orders arrive at ${orders.renownNeeded} renown.",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
        } else if (orders.orders.isEmpty()) {
            BasicText(
                "No one is waiting right now. Keep crafting — a customer walks in every so often, and keeps the market's hours.",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
        } else {
            BasicText(
                "Anything that reaches the market counts — your carry, the Merchant's cart, or the overnight shift. Ignore an order and it simply lapses.",
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
            )
            Spacer(Modifier.height(10.dp))
        }
        for (o in orders.orders) {
            OrderCard(o)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun OrderCard(o: GameState.OrderSnapshot) {
    val progress = (o.filled.toFloat() / o.needed.coerceAtLeast(1)).coerceIn(0f, 1f)
    val timeLeft = o.secsLeft.coerceAtLeast(0f)
    val urgent = timeLeft < 60f
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "${o.needed} × ${o.item.label}",
                style = TextStyle(color = Color(UiColors.HEADER), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "+${o.bounty} c",
                style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            "sold ${o.filled}/${o.needed} · +${o.renown} renown · +${o.honour} prestige",
            style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(progress, UiColors.GOOD)
        Spacer(Modifier.height(6.dp))
        BasicText(
            if (timeLeft >= 60f) "waits ${timeLeft.toInt() / 60}m ${timeLeft.toInt() % 60}s" else "leaves in ${timeLeft.toInt()}s",
            style = TextStyle(color = Color(if (urgent) UiColors.WARN else UiColors.DIM), fontSize = 11.sp),
        )
    }
}

// ---- Town ---------------------------------------------------------------------

/** The build-the-village sheet: one press quotes the whole bill. */
@Composable
fun BoxScope.TownSheet(
    game: GameState,
    town: GameState.TownSnapshot,
    materials: GameState.MaterialsSnapshot,
    coins: Int,
    onClose: () -> Unit,
) {
    SheetShell("The Village", onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Renown ${town.renown}", style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(14.dp))
            BasicText("Prestige ${town.prestige}/172", style = TextStyle(color = Color(UiColors.XP), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            BasicText(town.wellLabel, style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (town.residents > 0) "${town.residents} townsfolk live here now" else "Build homes and households will move in.",
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "water power ${town.powerDraw}/${town.powerGen}",
                style = TextStyle(color = Color(if (town.powerDraw > town.powerGen) UiColors.WARN else UiColors.DIM), fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(10.dp))
        for (s in town.slots) {
            SlotCard(s, coins, materials) { game.enqueue(GameState.Command.BuildSlot(s.index)) }
            Spacer(Modifier.height(8.dp))
        }

        UpgradeDivider()
        SectionHeader("Supply yard — materials")
        BasicText(
            "Coins buy materials outright; the sawmill can also cut planks from timber.",
            style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
        )
        Spacer(Modifier.height(6.dp))
        for (m in materials.materials) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "${m.material.label.replaceFirstChar { it.uppercase() }} — ${m.count} in the yard",
                        style = TextStyle(color = Color(UiColors.HEADER), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
                if (m.unlocked) {
                    HoldRepeatChip("${m.price} c", coins >= m.price) {
                        game.enqueue(GameState.Command.BuyMaterial(m.material))
                    }
                } else {
                    BasicText(
                        "🔒 ${m.renownReq} renown",
                        style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
        }

        if (town.boons.isNotEmpty()) {
            UpgradeDivider()
            BasicText("Village boons", style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            for (b in town.boons) {
                BasicText("✓ $b", style = TextStyle(color = Color(UiColors.GOOD), fontSize = 12.sp))
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun SlotCard(s: GameState.SlotSnapshot, coins: Int, materials: GameState.MaterialsSnapshot, onBuild: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(s.title, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                BasicText(s.desc, style = TextStyle(color = Color(UiColors.TEXT), fontSize = 11.sp))
            }
            when {
                s.complete -> BasicText("✓", style = TextStyle(color = Color(UiColors.GOOD), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                !s.gatesMet -> BasicText(
                    "🔒 ${s.renownReq} renown · ${s.prestigeReq} prestige",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                )
                else -> HoldRepeatChip("${s.bill} c", s.affordable) { onBuild() }
            }
        }
        if (!s.complete) {
            if (s.suppliesLine.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "bill covers ${s.suppliesLine} (bought with one press)",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                )
            }
            if (s.missingLine.isNotEmpty()) {
                BasicText(s.missingLine, style = TextStyle(color = Color(UiColors.WARN), fontSize = 11.sp))
            }
            if (s.maxStage > 1) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(s.stage.toFloat() / s.maxStage, UiColors.XP)
            }
        }
        s.boonLabel?.let {
            Spacer(Modifier.height(4.dp))
            BasicText(it, style = TextStyle(color = Color(if (s.complete) UiColors.GOOD else UiColors.DIM), fontSize = 11.sp))
        }
    }
}

// ---- Forge ---------------------------------------------------------------------

@Composable
fun BoxScope.ForgeSheet(
    game: GameState,
    forge: GameState.ForgeSnapshot,
    ingots: GameState.IngotSnapshot,
    items: GameState.ItemSnapshot,
    carry: GameState.CarrySnapshot,
    stock: GameState.StockpileSnapshot,
    sawmill: GameState.SawmillSnapshot,
    onClose: () -> Unit,
) {
    SheetShell("The Forge", onClose) {
        FurnaceCard(forge.furnace, "Furnace — common fire" + if (forge.furnace2Unlocked) " (iron · copper)" else " (all ores)")
        Spacer(Modifier.height(10.dp))

        SectionHeader("Load the hopper — one ore at a time")
        for (ore in Ore.entries) {
            val available = carry.oreCounts[ore.ordinal] + stock.oreCounts[ore.ordinal]
            val accepts = !forge.furnace2Unlocked || ore.pickLevel < 3
            RecipeRow(
                title = "${oreName(ore)} ore",
                swatch = oreColor(ore),
                detail = plainIngredient("${oreName(ore)} ore", available, 1),
                meta = "smelt ${ore.smeltSeconds.toInt()}s · ingot sells ${Metal.entries.first { it.ore == ore }.sell} c",
                action = "Load",
                enabled = accepts && available >= 1 && forge.furnace.hopperTotal < forge.furnace.hopperCap,
            ) { game.enqueue(GameState.Command.LoadHopper(ore, false)) }
            Spacer(Modifier.height(6.dp))
        }

        if (forge.furnace2Unlocked) {
            Spacer(Modifier.height(10.dp))
            FurnaceCard(forge.furnace2, "Furnace II — precious fire (silver · gold · mythril · crystal)")
            Spacer(Modifier.height(10.dp))
            SectionHeader("Feed the precious fire")
            for (ore in Ore.entries) {
                if (ore.pickLevel < 3) continue
                val available = carry.oreCounts[ore.ordinal] + stock.oreCounts[ore.ordinal]
                RecipeRow(
                    title = "${oreName(ore)} ore",
                    swatch = oreColor(ore),
                    detail = plainIngredient("${oreName(ore)} ore", available, 1),
                    meta = "smelt ${ore.smeltSeconds.toInt()}s · ingot sells ${Metal.entries.first { it.ore == ore }.sell} c",
                    action = "Load",
                    enabled = available >= 1 && forge.furnace2.hopperTotal < forge.furnace2.hopperCap,
                ) { game.enqueue(GameState.Command.LoadHopper(ore, true)) }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Crafting — the anvil takes one ingot at a time")
        AnvilLanes(forge)
        Spacer(Modifier.height(8.dp))
        for (item in Item.entries) {
            val have = ingots.counts[item.metal.ordinal]
            RecipeRow(
                title = item.label,
                swatch = metalColor(item.metal),
                detail = plainIngredient(item.metal.label, have, 1),
                meta = "craft ${item.craftSeconds.toInt()}s · sells ${item.sell} c · +${com.villageforge.config.Town.renownWeight(item.sell)} renown",
                action = "Craft",
                enabled = have >= 1 && forge.queue.size < forge.queueCap,
            ) { game.enqueue(GameState.Command.Craft(item)) }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("The sawmill")
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    "Logs ${sawmill.hopper}/${sawmill.hopperCap} · planks cut ${sawmill.planks}",
                    style = TextStyle(color = Color(UiColors.HEADER), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    if (sawmill.sawing) "sawing — ${sawmill.remain.toInt()}s a plank" else "idle — fell timber and feed the saw",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                )
            }
            BasicText(
                "${carry.timber} timber carried",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
            )
        }

        if (ingots.total + items.total > 0) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Workshop stock")
            StockStrip(ingots, items)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AnvilLanes(forge: GameState.ForgeSnapshot) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(UiColors.PANEL_RAISED), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        LaneRow("Lane A — your anvil", forge.laneA)
        if (forge.furnace2Unlocked || forge.laneB.item != null) {
            Spacer(Modifier.height(6.dp))
            LaneRow("Lane B — the Master Smith", forge.laneB)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Queue slot dots.
            Row {
                repeat(forge.queueCap) { i ->
                    val filled = i < forge.queue.size
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(10.dp)
                            .background(Color(if (filled) metalColor(forge.queue[i].metal) else UiColors.PANEL_SUNK), CircleShape)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            BasicText(
                "twin-craft ${(forge.twinChance * 100).toInt()}%",
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun LaneRow(label: String, lane: GameState.LaneView) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Color(UiColors.EMBER), CircleShape))
        Spacer(Modifier.width(8.dp))
        BasicText(label, style = TextStyle(color = Color(UiColors.HEADER), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        if (lane.item != null) {
            BasicText(
                "${lane.item.label} — ${"%.1f".format(lane.remain)}s",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 12.sp),
            )
        } else {
            BasicText("idle", style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp))
        }
    }
    if (lane.item != null) {
        Spacer(Modifier.height(4.dp))
        ProgressBar(1f - lane.remain / lane.total.coerceAtLeast(0.01f), UiColors.EMBER)
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

/** Single-ingredient line: red where you are short. */
private fun plainIngredient(label: String, have: Int, need: Int): AnnotatedString {
    val b = AnnotatedString.Builder()
    val ok = have >= need
    b.pushStyle(SpanStyle(color = Color(if (ok) UiColors.TEXT else UiColors.WARN), fontWeight = if (ok) FontWeight.Normal else FontWeight.Bold))
    b.append("$need $label")
    b.pop()
    b.pushStyle(SpanStyle(color = Color(UiColors.DIM), fontSize = 11.sp))
    b.append(" ($have)")
    b.pop()
    return b.toAnnotatedString()
}

@Composable
private fun FurnaceCard(furnace: GameState.FurnaceView, title: String) {
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
                    title,
                    style = TextStyle(color = Color(UiColors.HEADER), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    "hopper ${furnace.hopperTotal}/${furnace.hopperCap}",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 12.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            val smelting = furnace.smeltingOre
            if (smelting != null) {
                BasicText(
                    "Smelting ${oreName(smelting)} — ${"%.1f".format(furnace.smeltRemain)}s",
                    style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
                )
                Spacer(Modifier.height(6.dp))
                ProgressBar(1f - furnace.smeltRemain / furnace.smeltTotal.coerceAtLeast(0.01f), UiColors.EMBER)
            } else if (furnace.hopperTotal > 0) {
                BasicText(
                    "Hopper loaded — the fire takes the next ore",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 13.sp),
                )
            } else {
                BasicText(
                    "Cold and empty — load ore from the yard",
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
                    "${items.total}/${items.cap} finished goods",
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
        // The Record: figures written out in full, not rounded — a counter you
        // glance at wants to be short; a record you sit and read wants the
        // actual number (M44 of the original build).
        StatRow("Ore mined", "${stats.oreMined}")
        StatRow("Coins earned", "${stats.coinsEarned} c")
        StatRow("Ingots smelted", "${stats.ingotsSmelted}")
        StatRow("Items crafted", "${stats.itemsCrafted}")
        StatRow("Crew on the roster", "${stats.crewSize}")
        StatRow("Wages paid", "${stats.wagesPaid} c")
        StatRow("Time played", formatDuration(stats.playSeconds))
        UpgradeDivider()
        StatRow("Timber felled", "${stats.timberFelled}")
        StatRow("Planks sawn", "${stats.planksSawn}")
        StatRow("Renown earned", "${stats.renownEarned}")
        StatRow("Commissions filled", "${stats.commissionsFilled}")
        StatRow("Village prestige", "${stats.prestige}")
        StatRow("Townsfolk", "${stats.residents}")
        StatRow("Rain on the village", "${stats.rainMinutes}m")
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
            "${formatDuration(report.awaySeconds.toInt())} of village time at half pace",
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
        if (report.itemGains > 0) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                "The forge hammered out ${report.itemGains} goods",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
        }
        if (report.plankGains > 0) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                "The sawmill cut ${report.plankGains} planks",
                style = TextStyle(color = Color(UiColors.TEXT), fontSize = 13.sp),
            )
        }
        if (report.coinGains > 0) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                "The Merchant sold +${report.coinGains} c of goods",
                style = TextStyle(color = Color(UiColors.COIN_TEXT), fontSize = 13.sp, fontWeight = FontWeight.Bold),
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

// ---- Medals -----------------------------------------------------------------

@Composable
fun BoxScope.MedalsSheet(game: GameState, onClose: () -> Unit) {
    val unlockedCount by game.achievementFlow.collectAsState()
    SheetShell("Medals — $unlockedCount/${Achievements.all.size}", onClose) {
        val unlocked = unlockedCount  // snapshot trigger
        val sorted = remember(unlocked) {
            val done = Achievements.all.filter { it.id in game.achievements }
            val todo = Achievements.all.filter { it.id !in game.achievements }
            todo.sortedByDescending { Achievements.progress(game, it).toFloat() / it.goal } + done
        }
        for (def in sorted) {
            MedalRow(def, def.id in game.achievements, Achievements.progress(game, def))
            UpgradeDivider()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MedalRow(def: AchievementDef, done: Boolean, progress: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .background(Color(if (done) UiColors.EMBER else UiColors.PANEL_SUNK), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (done) "🏆" else "★",
                style = TextStyle(fontSize = if (done) 15.sp else 17.sp),
                modifier = Modifier.graphicsLayer { alpha = if (done) 1f else 0.45f },
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                def.title,
                style = TextStyle(
                    color = Color(if (done) UiColors.HEADER else UiColors.TEXT),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                def.desc,
                style = TextStyle(color = Color(UiColors.DIM), fontSize = 11.sp),
                modifier = Modifier.padding(top = 1.dp),
            )
            if (!done) {
                Spacer(Modifier.height(5.dp))
                ProgressBar(
                    progress.toFloat() / def.goal.coerceAtLeast(1),
                    UiColors.COIN,
                )
                Spacer(Modifier.height(3.dp))
                BasicText(
                    "$progress / ${def.goal}",
                    style = TextStyle(color = Color(UiColors.DIM), fontSize = 10.sp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            BasicText(
                "+${def.reward} c",
                style = TextStyle(color = Color(if (done) UiColors.COIN else UiColors.DIM), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            if (done) {
                BasicText(
                    "Earned",
                    style = TextStyle(color = Color(UiColors.GOOD), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
