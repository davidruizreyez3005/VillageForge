package com.villageforge.core

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.villageforge.config.DayNight
import com.villageforge.config.Item
import com.villageforge.config.Ore
import com.villageforge.config.Role
import com.villageforge.config.Crew
import com.villageforge.config.Town
import com.villageforge.config.UpgradeType
import com.villageforge.config.Wood
import com.villageforge.config.WorldLayout
import com.villageforge.graphics.CameraRig
import com.villageforge.state.GameState
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SfxId { ROCK_HIT, ROCK_BREAK, COINS, BUY, DENIED, SMELT, HAMMER, CRAFT, QUEST, LEVELUP, ACHIEVE, ORDER, WOOD_HIT }

enum class Sheet { FORGE, SHOP, CREW, QUESTS, MEDALS, TOWN, ORDERS }

class EventBus {
    data class OreMined(val ore: Ore, val amount: Int, val x: Float, val z: Float)
    data class RockStruck(val rockIndex: Int)
    data class TreeStruck(val treeIndex: Int)
    data class Notice(val text: String, val colorArgb: Int, val x: Float, val z: Float, val yOffset: Float = 0f)
    data class Sfx(val id: SfxId, val pitch: Float = 1f)
    data class SmeltDone(val metal: com.villageforge.config.Metal)
    data class ItemCrafted(val item: Item)
    data class HammerStruck(val x: Float, val z: Float)
    data class MinerStruck(val rockIndex: Int)
    data class LevelUp(val newLevel: Int)
    data class TapMarker(val x: Float, val y: Float)
    data class UiRequest(val sheet: Sheet)
    data class AchievementUnlocked(val id: String, val title: String, val reward: Int)
    /** A customer's order was placed (item, qty). */
    data class OrderPlaced(val itemName: String, val needed: Int)
    /** An order was completed by a sale (item, bounty coins). */
    data class CommissionFilled(val itemName: String, val bounty: Int, val renown: Int)
    /** A village slot advanced a stage (what it now says on the sign). */
    data class VillageBuilt(val label: String, val complete: Boolean)

    val oreMined = MutableSharedFlow<OreMined>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val rockStruck = MutableSharedFlow<RockStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val treeStruck = MutableSharedFlow<TreeStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sfx = MutableSharedFlow<Sfx>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val smeltDone = MutableSharedFlow<SmeltDone>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val itemCrafted = MutableSharedFlow<ItemCrafted>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hammerStruck = MutableSharedFlow<HammerStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val minerStruck = MutableSharedFlow<MinerStruck>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val levelUp = MutableSharedFlow<LevelUp>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val tapMarker = MutableSharedFlow<TapMarker>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiRequest = MutableSharedFlow<UiRequest>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val achievementUnlocked = MutableSharedFlow<AchievementUnlocked>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val orderPlaced = MutableSharedFlow<OrderPlaced>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commissionFilled = MutableSharedFlow<CommissionFilled>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val villageBuilt = MutableSharedFlow<VillageBuilt>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    companion object {
        val COLOR_WARN: Int = 0xFFE2574C.toInt()
        val COLOR_GOLD: Int = 0xFFF1C40F.toInt()
        val COLOR_INFO: Int = 0xFF9FD08C.toInt()
    }
}

class InputManager(
    context: Context,
    private val rig: CameraRig,
    private val game: GameState,
    private val bus: EventBus,
) : View.OnTouchListener {

    private val scaleDetector: ScaleGestureDetector

    private val panDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onScroll(begin: MotionEvent?, end: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scaleDetector.isInProgress && end.pointerCount < 2) rig.panByPixels(dx, dy)
            return true
        }
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            handleTap(event.x, event.y)
            return true
        }
        /** Double-tap recenters the camera on the player. */
        override fun onDoubleTap(event: MotionEvent): Boolean {
            rig.recenterOnPlayer(game.player.x, game.player.z)
            return true
        }
    })

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                rig.zoomBy(detector.scaleFactor)
                return true
            }
        })
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        // Feed the pan/tap detector only single-pointer streams so the
        // pinch zoom never pans the world mid-gesture.
        if (event.pointerCount == 1 || event.actionMasked == MotionEvent.ACTION_DOWN) {
            panDetector.onTouchEvent(event)
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val ground = rig.screenToGround(x, y)
        val gx = ground[0]
        val gz = ground[1]
        val rockIndex = hitTestRock(gx, gz)
        when {
            rockIndex >= 0 -> game.enqueue(GameState.Command.Mine(rockIndex))
            hitTestTree(gx, gz) >= 0 -> game.enqueue(GameState.Command.ChopTree(hitTestTree(gx, gz)))
            isOnFurnace(gx, gz) -> bus.uiRequest.tryEmit(EventBus.UiRequest(Sheet.FORGE))
            isOnWell(gx, gz) -> bus.uiRequest.tryEmit(EventBus.UiRequest(Sheet.TOWN))
            isOnStall(gx, gz) -> game.enqueue(GameState.Command.Sell)
            isOnBin(gx, gz) -> game.enqueue(GameState.Command.Deposit)
            isOnSawmill(gx, gz) -> game.enqueue(GameState.Command.DepositWood)
            else -> {
                bus.tapMarker.tryEmit(EventBus.TapMarker(x, y))
                game.enqueue(GameState.Command.MoveTo(gx, gz))
            }
        }
    }

    /** The plaza well opens the town sheet — it IS the village ladder. */
    private fun isOnWell(x: Float, z: Float): Boolean =
        hypot(x - Town.WELL_X, z - Town.WELL_Z) < 1.8f

    private fun hitTestRock(x: Float, z: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (rock in game.rocks) {
            if (!rock.alive) continue
            val d = hypot(rock.x - x, rock.z - z)
            if (d < 1.6f && d < bestDist) { best = rock.index; bestDist = d }
        }
        return best
    }

    private fun hitTestTree(x: Float, z: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (tree in game.trees) {
            if (!tree.alive) continue
            val d = hypot(tree.x - x, tree.z - z)
            if (d < 1.5f && d < bestDist) { best = tree.index; bestDist = d }
        }
        return best
    }

    private fun isOnStall(x: Float, z: Float): Boolean =
        hypot(x - WorldLayout.TRADE_POST_X, z - WorldLayout.TRADE_POST_Z) < 2.2f

    private fun isOnBin(x: Float, z: Float): Boolean =
        hypot(x - WorldLayout.BIN_X, z - WorldLayout.BIN_Z) < 1.5f

    private fun isOnFurnace(x: Float, z: Float): Boolean =
        hypot(x - WorldLayout.FURNACE_X, z - WorldLayout.FURNACE_Z) < 2.2f ||
            (game.furnace2Unlocked && hypot(x - WorldLayout.FURNACE2_X, z - WorldLayout.FURNACE2_Z) < 2.2f)

    private fun isOnSawmill(x: Float, z: Float): Boolean =
        hypot(x - WorldLayout.SAWMILL_X, z - WorldLayout.SAWMILL_Z) < 2.6f
}

class SaveManager(context: Context) {
    @Serializable
    data class SaveData(
        val version: Int, val coins: Int,
        val upgradeLevels: List<Int> = emptyList(),
        val carriedOre: List<Int> = emptyList(), val carriedTimber: Int = 0,
        val playerX: Float, val playerZ: Float, val playerFacing: Float,
        val sfxEnabled: Boolean = true, val musicEnabled: Boolean = true,
        val binsOre: List<Int> = emptyList(),
        val rockAlive: List<Int> = emptyList(), val rockHp: List<Int> = emptyList(),
        val rockRespawn: List<Float> = emptyList(),
        val treeAlive: List<Int> = emptyList(), val treeHp: List<Int> = emptyList(),
        val treeRespawn: List<Float> = emptyList(),
        // Furnaces: hopper + in-flight smelt.
        val hopperOre: List<Int> = emptyList(),
        val smeltingOre: Int = -1, val smeltRemain: Float = 0f,
        val hopper2Ore: List<Int> = emptyList(),
        val smelting2Ore: Int = -1, val smelt2Remain: Float = 0f,
        // Forge: queue + lanes.
        val craftQueue: List<Int> = emptyList(),
        val laneAItem: Int = -1, val laneARemain: Float = 0f,
        val laneBItem: Int = -1, val laneBRemain: Float = 0f,
        // Sawmill.
        val sawmillHopper: Int = 0, val sawmillRemain: Float = 0f, val sawmillSawing: Boolean = false,
        // Mine stockpiles.
        val northPile: List<Int> = emptyList(),
        val eastPile: List<Int> = emptyList(),
        val hollowPile: List<Int> = emptyList(),
        // Crew.
        val workerRoles: List<Int> = emptyList(),
        val wageClock: Float = 0f, val wagesUnpaid: Boolean = false,
        // Materials.
        val materials: List<Int> = emptyList(),
        // Legacy v1–v4 fields, kept so old saves decode; unused by v5 itself.
        val pickTier: Int = 0,
        val bootsLevel: Int = 0,
        val backpackLevel: Int = 0,
        val minersHired: Int = 0,
        // Ingots (tray) + items (rack).
        val ingots: List<Int> = emptyList(),
        val items: List<Int> = emptyList(),
        val questIndex: Int = 0,
        val xp: Int = 0, val level: Int = 1,
        val timeOfDay: Float = DayNight.START_TIME,
        val lastPlayedEpochMs: Long = 0L,
        val statsOresMined: List<Int> = emptyList(),
        val statsCoinsEarned: Int = 0,
        val statsIngotsSmelted: List<Int> = emptyList(),
        val statsItemsCrafted: List<Int> = emptyList(),
        val statsPlaySeconds: Float = 0f,
        val musicEnabledOnce: Boolean = true,
        val achievements: List<String> = emptyList(),
        val statsRocksBroken: Int = 0,
        val statsOfflineGains: Int = 0,
        val statsNightSeconds: Float = 0f,
        // The town layer.
        val renown: Int = 0,
        val honour: Int = 0,
        val villageSlots: List<Int> = emptyList(),
        val commissionItem: List<Int> = emptyList(),
        val commissionNeeded: List<Int> = emptyList(),
        val commissionFilled: List<Int> = emptyList(),
        val commissionRemain: List<Float> = emptyList(),
        val commissionBounty: List<Int> = emptyList(),
        val commissionRenown: List<Int> = emptyList(),
        val commissionHonour: List<Int> = emptyList(),
        val weatherClock: Float = 0f,
        val weatherWet: Boolean = false,
        val statsCommissionsFilled: Int = 0,
        val statsCommissionsExpired: Int = 0,
        val statsRenownEarned: Int = 0,
        val statsBuildStages: Int = 0,
        val statsRainSeconds: Float = 0f,
        val statsTimberFelled: Int = 0,
        val statsPlanksSawn: Int = 0,
        val statsWagesPaid: Int = 0,
        val statsMaterialsBought: Int = 0,
    )

    /** What one slot row shows in the village picker. */
    data class SlotSummary(
        val slot: Int,
        val level: Int,
        val coins: Int,
        val playSeconds: Float,
        val lastPlayedMs: Long,
        val workers: Int,
        val questIndex: Int,
        val prestige: Int,
    )

    val persistenceWarning = MutableStateFlow(false)
    /** Live slot overview for the title screen; null = empty slot. */
    val slotsFlow = MutableStateFlow(List(SLOT_COUNT) { null as SlotSummary? })
    /** Set by load() so the caller can compute offline progress. */
    var lastPlayedEpochMs: Long = 0L
        private set

    /** The slot the current session saves into. */
    var activeSlot = 0

    private val dir = context.filesDir
    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateLegacySave()
        refreshSummaries()
    }

    private fun fileFor(slot: Int): File = File(dir, "${FILE_PREFIX}${slot.coerceIn(0, SLOT_COUNT - 1)}.json")

    /** v2.0 wrote a single fixed-name file; adopt it as slot 0 exactly once. */
    private fun migrateLegacySave() {
        try {
            val legacy = File(dir, LEGACY_FILE_NAME)
            if (legacy.exists() && (0 until SLOT_COUNT).none { fileFor(it).exists() }) {
                legacy.renameTo(fileFor(0))
            }
        } catch (_: Exception) {
        }
    }

    fun refreshSummaries() {
        slotsFlow.value = (0 until SLOT_COUNT).map { slot -> readSummary(slot) }
    }

    private fun readSummary(slot: Int): SlotSummary? {
        val file = fileFor(slot)
        if (!file.exists()) return null
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            SlotSummary(
                slot = slot, level = data.level, coins = data.coins,
                playSeconds = data.statsPlaySeconds, lastPlayedMs = data.lastPlayedEpochMs,
                workers = data.workerRoles.size, questIndex = data.questIndex,
                prestige = data.renown,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Most recently played slot, or -1 when the device has no villages. */
    fun mostRecentSlot(): Int {
        var best = -1
        var bestMs = -1L
        for (slot in 0 until SLOT_COUNT) {
            val s = readSummary(slot) ?: continue
            if (s.lastPlayedMs > bestMs) { bestMs = s.lastPlayedMs; best = slot }
        }
        return best
    }

    fun hasSave(): Boolean = mostRecentSlot() >= 0

    fun load(game: GameState, slot: Int = activeSlot): Boolean {
        activeSlot = slot
        val file = fileFor(slot)
        if (!file.exists()) return false
        return try {
            val data = json.decodeFromString(SaveData.serializer(), file.readText())
            when (data.version) {
                VERSION -> loadV5(game, data)
                V4_VERSION -> migrateV4(game, data)
                V3_VERSION, V2_VERSION, V1_VERSION -> migrateOld(game, data)
                else -> return false
            }
            sanitize(game)
            true
        } catch (e: Exception) { false }
    }

    private fun loadV5(game: GameState, d: SaveData) {
        game.coins = d.coins
        for (i in game.upgradeLevels.indices) {
            game.upgradeLevels[i] = d.upgradeLevels.getOrNull(i)?.coerceIn(0, UpgradeType.entries[i].maxLevel) ?: 0
        }
        game.inventory.setCounts(d.carriedOre)
        game.inventory.timber = d.carriedTimber.coerceAtLeast(0)
        game.player.x = d.playerX; game.player.z = d.playerZ
        game.player.prevX = d.playerX; game.player.prevZ = d.playerZ
        game.player.facing = d.playerFacing; game.player.prevFacing = d.playerFacing
        game.sfxEnabled = d.sfxEnabled
        game.musicEnabled = d.musicEnabled
        game.bins.setCounts(d.binsOre)
        restoreRocks(game, d.rockAlive, d.rockHp, d.rockRespawn)
        restoreTrees(game, d.treeAlive, d.treeHp, d.treeRespawn)
        // Furnaces.
        setHopper(game.furnace.hopper, d.hopperOre)
        game.furnace.smeltingOre = Ore.entries.getOrNull(d.smeltingOre)
        game.furnace.smeltRemain = d.smeltRemain
        setHopper(game.furnace2.hopper, d.hopper2Ore)
        game.furnace2.smeltingOre = Ore.entries.getOrNull(d.smelting2Ore)
        game.furnace2.smeltRemain = d.smelt2Remain
        // Forge.
        game.craftQueue.clear()
        for (o in d.craftQueue) Item.entries.getOrNull(o)?.let { game.craftQueue.add(it) }
        while (game.craftQueue.size > game.queueCap) game.craftQueue.removeAt(game.craftQueue.size - 1)
        game.laneA = d.laneAItem.takeIf { it >= 0 }?.let { Item.entries.getOrNull(it) }?.let { GameState.ActiveCraft(it, d.laneARemain) }
        game.laneB = d.laneBItem.takeIf { it >= 0 }?.let { Item.entries.getOrNull(it) }?.let { GameState.ActiveCraft(it, d.laneBRemain) }
        // Sawmill.
        game.sawmillHopper = d.sawmillHopper.coerceIn(0, Wood.SAWMILL_HOPPER_CAP)
        game.sawmillRemain = d.sawmillRemain.coerceAtLeast(0f)
        game.sawmillSawing = d.sawmillSawing && game.sawmillHopper > 0
        // Piles.
        game.northPile.setCounts(d.northPile)
        game.eastPile.setCounts(d.eastPile)
        game.hollowPile.setCounts(d.hollowPile)
        // Crew.
        game.workers.clear()
        for (o in d.workerRoles) {
            val role = Role.entries.getOrNull(o) ?: continue
            val w = com.villageforge.entities.Worker(game.workers.size, role, game.workers.size % com.villageforge.config.Theme.MINER_STYLES.size)
            w.body.x = -2f + game.workers.size * 1.4f
            w.body.z = 4f
            w.body.prevX = w.body.x
            w.body.prevZ = w.body.z
            game.workers.add(w)
        }
        game.wageClock = d.wageClock.coerceIn(0f, 60f)
        game.wagesUnpaid = d.wagesUnpaid
        // Materials, tray, rack.
        for (i in game.materials.indices) game.materials[i] = d.materials.getOrNull(i)?.coerceAtLeast(0) ?: 0
        game.ingots.setCounts(d.ingots)
        game.items.setCounts(d.items)
        // Progress + town.
        game.questIndex = d.questIndex.coerceIn(0, com.villageforge.config.Quests.all.size)
        game.xp = d.xp; game.level = d.level.coerceAtLeast(1)
        game.timeOfDay = d.timeOfDay
        lastPlayedEpochMs = d.lastPlayedEpochMs
        game.stats.loadFrom(d)
        game.achievements.clear()
        game.achievements.addAll(d.achievements)
        loadTown(game, d)
    }

    /**
     * v4 → v5: the whole economy re-balanced, so stashes reset; what a
     * player genuinely earned (purse, renown, prestige, buildings, crew
     * heads) carries over, and the old upgrade ladder maps onto the new
     * tree.
     */
    private fun migrateV4(game: GameState, d: SaveData) {
        migrateOld(game, d)
    }

    private fun migrateOld(game: GameState, d: SaveData) {
        game.coins = d.coins
        game.player.x = d.playerX; game.player.z = d.playerZ
        game.player.prevX = d.playerX; game.player.prevZ = d.playerZ
        game.player.facing = d.playerFacing; game.player.prevFacing = d.playerFacing
        game.sfxEnabled = d.sfxEnabled
        game.musicEnabled = d.musicEnabled
        game.timeOfDay = d.timeOfDay
        lastPlayedEpochMs = d.lastPlayedEpochMs
        game.questIndex = 0
        game.xp = d.xp; game.level = d.level.coerceAtLeast(1)
        game.stats.playSeconds = d.statsPlaySeconds
        game.stats.coinsEarnedTotal = d.statsCoinsEarned
        game.stats.rocksBroken = d.statsRocksBroken
        game.stats.offlineGains = d.statsOfflineGains
        game.stats.nightSeconds = d.statsNightSeconds
        game.stats.commissionsFilled = d.statsCommissionsFilled
        game.stats.commissionsExpired = d.statsCommissionsExpired
        game.stats.renownEarned = d.statsRenownEarned
        game.stats.buildStages = d.statsBuildStages
        game.stats.rainSeconds = d.statsRainSeconds
        game.achievements.clear()
        game.achievements.addAll(d.achievements)
        // The town layer carries over by slot id.
        game.renown = d.renown
        game.honour = d.honour
        for (i in game.villageSlots.indices) game.villageSlots[i] = 0
        val legacyStage = LegacyV4.readSlotStages(d)
        for ((id, stage) in legacyStage) {
            val idx = Town.slotIndex(id)
            if (idx >= 0) game.villageSlots[idx] = stage.coerceIn(0, Town.slots[idx].maxStage)
        }
        game.commissions.clear()
        game.weatherClock = d.weatherClock.coerceAtLeast(30f)
        game.weatherWet = false
        // Old hires come back as plain Miners.
        val oldMiners = LegacyV4.minerCount(d)
        repeat(oldMiners) {
            val w = com.villageforge.entities.Worker(game.workers.size, Role.MINER, game.workers.size % com.villageforge.config.Theme.MINER_STYLES.size)
            w.body.x = -2f + game.workers.size * 1.4f
            w.body.z = 4f
            game.workers.add(w)
        }
        // Legacy upgrade levels map onto the new tree.
        val (oldPickTier, oldBoots, oldBackpack) = LegacyV4.readLegacyUpgrades(d)
        game.upgradeLevels[UpgradeType.PICKAXE_QUALITY.ordinal] = oldPickTier.coerceIn(0, UpgradeType.PICKAXE_QUALITY.maxLevel)
        game.upgradeLevels[UpgradeType.SWIFT_BOOTS.ordinal] = oldBoots.coerceIn(0, UpgradeType.SWIFT_BOOTS.maxLevel)
        game.upgradeLevels[UpgradeType.LEATHER_PACK.ordinal] = oldBackpack.coerceIn(0, UpgradeType.LEATHER_PACK.maxLevel)
        game.wageClock = 0f
        game.wagesUnpaid = false
    }

    /** The pre-v5 save shape (v1–v4), read through the same lenient JSON. */
    private object LegacyV4 {
        fun readSlotStages(d: SaveData): List<Pair<String, Int>> {
            val ids = listOf(
                "lamp1", "lamp2", "lamp3", "lamp4",
                "house1", "house2", "house3", "house4",
                "field1", "field2", "farm", "granary", "windmill", "chapel",
            )
            val out = ArrayList<Pair<String, Int>>()
            val raw = d.villageSlots
            for (i in ids.indices) {
                val stage = raw.getOrNull(i) ?: 0
                if (stage > 0) out.add(ids[i] to stage)
            }
            return out
        }

        fun minerCount(d: SaveData): Int = d.minersHired

        fun readLegacyUpgrades(d: SaveData): Triple<Int, Int, Int> = Triple(
            d.pickTier, d.bootsLevel, d.backpackLevel,
        )
    }

    private fun restoreRocks(game: GameState, alive: List<Int>, hp: List<Int>, respawn: List<Float>) {
        if (alive.isEmpty()) return
        for (i in game.rocks.indices) {
            game.rocks[i].alive = i < alive.size && alive[i] != 0
            game.rocks[i].hp = if (i < hp.size) hp[i] else game.rocks[i].ore.rockHp
            game.rocks[i].respawnTimer = if (i < respawn.size) respawn[i] else 0f
        }
    }

    private fun restoreTrees(game: GameState, alive: List<Int>, hp: List<Int>, respawn: List<Float>) {
        if (alive.isEmpty()) return
        for (i in game.trees.indices) {
            game.trees[i].alive = i < alive.size && alive[i] != 0
            game.trees[i].hp = if (i < hp.size) hp[i] else Wood.TREE_HP
            game.trees[i].respawnTimer = if (i < respawn.size) respawn[i] else 0f
        }
    }

    private fun setHopper(target: IntArray, values: List<Int>) {
        for (i in target.indices) target[i] = values.getOrNull(i)?.coerceAtLeast(0) ?: 0
    }

    private fun loadTown(game: GameState, d: SaveData) {
        for (i in game.villageSlots.indices) {
            if (i < d.villageSlots.size) game.villageSlots[i] = d.villageSlots[i].coerceIn(0, Town.slots[i].maxStage)
        }
        game.commissions.clear()
        if (d.commissionItem.size == d.commissionNeeded.size &&
            d.commissionItem.size == d.commissionRemain.size &&
            d.commissionItem.size == d.commissionBounty.size) {
            for (i in d.commissionItem.indices) {
                val item = Item.entries.getOrNull(d.commissionItem[i]) ?: continue
                val def = Town.commissions.firstOrNull { it.item == item } ?: continue
                val needed = d.commissionNeeded[i].coerceAtLeast(1)
                game.commissions.add(
                    GameState.Commission(
                        id = i,
                        item = item,
                        needed = needed,
                        filled = (d.commissionFilled.getOrNull(i) ?: 0).coerceIn(0, needed),
                        remain = d.commissionRemain[i].coerceIn(0f, def.secs),
                        bounty = d.commissionBounty[i],
                        renown = d.commissionRenown.getOrNull(i) ?: def.renown,
                        honour = d.commissionHonour.getOrNull(i) ?: def.honour,
                    )
                )
            }
        }
        game.weatherClock = d.weatherClock.coerceAtLeast(30f)
        game.weatherWet = false // a shower is a live-session mood; sessions start dry
        game.stats.commissionsFilled = d.statsCommissionsFilled
        game.stats.commissionsExpired = d.statsCommissionsExpired
        game.stats.renownEarned = d.statsRenownEarned
        game.stats.buildStages = d.statsBuildStages
        game.stats.rainSeconds = d.statsRainSeconds
        game.stats.timberFelled = d.statsTimberFelled
        game.stats.planksSawn = d.statsPlanksSawn
        game.stats.wagesPaid = d.statsWagesPaid
        game.stats.materialsBought = d.statsMaterialsBought
    }

    /**
     * Spec §19 — the sanitize pass: clamp every count against the caps the
     * save's own upgrade levels allow. A stale, tampered, or partially-old
     * save becomes safe to resume rather than exploitable.
     */
    private fun sanitize(game: GameState) {
        // v3.1 — a body parked where the map's slopes steepened (the map grew
        // and mine mouths moved) snaps to the nearest standable ground so the
        // invisible barriers can never trap a restored save inside a wall.
        for (body in listOf(game.player) + game.workers.map { it.body }) {
            val spot = WorldLayout.nearestWalkable(body.x, body.z)
            body.x = spot.first; body.z = spot.second
            body.prevX = body.x; body.prevZ = body.z
        }
        // Tray + rack against their caps.
        while (game.ingots.total > game.trayCap) {
            var reduced = false
            for (i in 0 until game.ingots.size) {
                if (game.ingots.countAt(i) > 0) { game.ingots.takeAt(i, 1); reduced = true; break }
            }
            if (!reduced) break
        }
        while (game.items.total > game.rackCap) {
            var reduced = false
            for (i in 0 until game.items.size) {
                if (game.items.countAt(i) > 0) { game.items.takeAt(i, 1); reduced = true; break }
            }
            if (!reduced) break
        }
        while (game.craftQueue.size > game.queueCap) game.craftQueue.removeAt(game.craftQueue.size - 1)
        // Bins per-ore cap.
        for (ore in Ore.entries) {
            val over = game.bins.countAt(ore) - game.binCap(ore)
            if (over > 0) game.bins.takeAt(ore, over)
        }
        // Hoppers.
        var hop = game.furnace.hopperTotal
        while (hop > game.hopperCap) {
            var reduced = false
            for (ore in Ore.entries) {
                if (game.furnace.hopper[ore.ordinal] > 0) { game.furnace.hopper[ore.ordinal]--; hop--; reduced = true; break }
            }
            if (!reduced) break
        }
        var hop2 = game.furnace2.hopperTotal
        while (hop2 > game.hopperCap) {
            var reduced = false
            for (ore in Ore.entries) {
                if (game.furnace2.hopper[ore.ordinal] > 0) { game.furnace2.hopper[ore.ordinal]--; hop2--; reduced = true; break }
            }
            if (!reduced) break
        }
        game.sawmillHopper = game.sawmillHopper.coerceIn(0, Wood.SAWMILL_HOPPER_CAP)
        // Ore the pick cannot mine yet is pulled out of the hoppers.
        for (ore in Ore.entries) {
            if (!game.oreUnlocked(ore)) {
                game.furnace.hopper[ore.ordinal] = 0
                game.furnace2.hopper[ore.ordinal] = 0
            }
        }
        // Furnace II's hopper keeps only precious ore; base keeps only common.
        if (game.furnace2Unlocked) {
            for (ore in Ore.entries) {
                if (ore.pickLevel >= 3) game.furnace.hopper[ore.ordinal] = 0
                else game.furnace2.hopper[ore.ordinal] = 0
            }
            if (game.furnace.smeltingOre != null && game.furnace.smeltingOre!!.pickLevel >= 3) {
                game.furnace.smeltingOre = null
            }
            if (game.furnace2.smeltingOre != null && game.furnace2.smeltingOre!!.pickLevel < 3) {
                game.furnace2.smeltingOre = null
            }
        }
        // Crew roles that are no longer legal at these upgrade levels step down.
        val toRemove = ArrayList<com.villageforge.entities.Worker>()
        for (w in game.workers) {
            val legal = when {
                w.role == Role.MASTER_SMITH -> com.villageforge.config.Crew.masterSmithUnlocked(game.upgradeLevels)
                w.role == Role.MASTER_SMELTER -> com.villageforge.config.Crew.masterSmelterUnlocked(game.upgradeLevels)
                w.role == Role.PIT_MASTER -> com.villageforge.config.Crew.pitMasterUnlocked(game.upgradeLevels)
                Crew.isSpecialist(w.role) -> true
                else -> true
            }
            if (!legal) toRemove.add(w)
        }
        game.workers.removeAll(toRemove.toSet())
    }

    fun save(game: GameState) {
        try {
            val file = fileFor(activeSlot)
            val data = SaveData(
                version = VERSION, coins = game.coins,
                upgradeLevels = game.upgradeLevels.toList(),
                carriedOre = game.inventory.countsArray().toList(),
                carriedTimber = game.inventory.timber,
                playerX = game.player.x, playerZ = game.player.z, playerFacing = game.player.facing,
                sfxEnabled = game.sfxEnabled, musicEnabled = game.musicEnabled,
                binsOre = game.bins.countsArray().toList(),
                rockAlive = game.rocks.map { if (it.alive) 1 else 0 },
                rockHp = game.rocks.map { it.hp },
                rockRespawn = game.rocks.map { it.respawnTimer },
                treeAlive = game.trees.map { if (it.alive) 1 else 0 },
                treeHp = game.trees.map { it.hp },
                treeRespawn = game.trees.map { it.respawnTimer },
                hopperOre = game.furnace.hopper.toList(),
                smeltingOre = game.furnace.smeltingOre?.ordinal ?: -1,
                smeltRemain = game.furnace.smeltRemain,
                hopper2Ore = game.furnace2.hopper.toList(),
                smelting2Ore = game.furnace2.smeltingOre?.ordinal ?: -1,
                smelt2Remain = game.furnace2.smeltRemain,
                craftQueue = game.craftQueue.map { it.ordinal },
                laneAItem = game.laneA?.item?.ordinal ?: -1,
                laneARemain = game.laneA?.remain ?: 0f,
                laneBItem = game.laneB?.item?.ordinal ?: -1,
                laneBRemain = game.laneB?.remain ?: 0f,
                sawmillHopper = game.sawmillHopper,
                sawmillRemain = game.sawmillRemain,
                sawmillSawing = game.sawmillSawing,
                northPile = game.northPile.counts(),
                eastPile = game.eastPile.counts(),
                hollowPile = game.hollowPile.counts(),
                workerRoles = game.workers.map { it.role.ordinal },
                wageClock = game.wageClock,
                wagesUnpaid = game.wagesUnpaid,
                materials = game.materials.toList(),
                ingots = game.ingots.counts(),
                items = game.items.counts(),
                questIndex = game.questIndex,
                xp = game.xp, level = game.level,
                timeOfDay = game.timeOfDay,
                lastPlayedEpochMs = System.currentTimeMillis(),
                statsOresMined = game.stats.oresMined.toList(),
                statsCoinsEarned = game.stats.coinsEarnedTotal,
                statsIngotsSmelted = game.stats.ingotsSmelted.toList(),
                statsItemsCrafted = game.stats.itemsCrafted.toList(),
                statsPlaySeconds = game.stats.playSeconds,
                achievements = game.achievements.toList(),
                statsRocksBroken = game.stats.rocksBroken,
                statsOfflineGains = game.stats.offlineGains,
                statsNightSeconds = game.stats.nightSeconds,
                renown = game.renown,
                honour = game.honour,
                villageSlots = game.villageSlots.toList(),
                commissionItem = game.commissions.map { it.item.ordinal },
                commissionNeeded = game.commissions.map { it.needed },
                commissionFilled = game.commissions.map { it.filled },
                commissionRemain = game.commissions.map { it.remain },
                commissionBounty = game.commissions.map { it.bounty },
                commissionRenown = game.commissions.map { it.renown },
                commissionHonour = game.commissions.map { it.honour },
                weatherClock = if (game.weatherWet) 180f else game.weatherClock.coerceAtLeast(60f),
                weatherWet = false,
                statsCommissionsFilled = game.stats.commissionsFilled,
                statsCommissionsExpired = game.stats.commissionsExpired,
                statsRenownEarned = game.stats.renownEarned,
                statsBuildStages = game.stats.buildStages,
                statsRainSeconds = game.stats.rainSeconds,
                statsTimberFelled = game.stats.timberFelled,
                statsPlanksSawn = game.stats.planksSawn,
                statsWagesPaid = game.stats.wagesPaid,
                statsMaterialsBought = game.stats.materialsBought,
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SaveData.serializer(), data))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            persistenceWarning.value = false
            refreshSummaries()
        } catch (e: Exception) { persistenceWarning.value = true }
    }

    fun delete(slot: Int) {
        try {
            fileFor(slot).delete()
            persistenceWarning.value = false
            refreshSummaries()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SLOT_COUNT = 3
        const val VERSION = 5
        const val V4_VERSION = 4
        const val V3_VERSION = 3
        const val V2_VERSION = 2
        const val V1_VERSION = 1
        const val LEGACY_FILE_NAME = "villageforge_save.json"
        const val FILE_PREFIX = "villageforge_slot"
    }
}

private fun GameState.Stats.loadFrom(d: SaveManager.SaveData) {
    for (i in oresMined.indices) oresMined[i] = d.statsOresMined.getOrNull(i) ?: 0
    coinsEarnedTotal = d.statsCoinsEarned
    for (i in ingotsSmelted.indices) ingotsSmelted[i] = d.statsIngotsSmelted.getOrNull(i) ?: 0
    for (i in itemsCrafted.indices) itemsCrafted[i] = d.statsItemsCrafted.getOrNull(i) ?: 0
    playSeconds = d.statsPlaySeconds
    rocksBroken = d.statsRocksBroken
    offlineGains = d.statsOfflineGains
    nightSeconds = d.statsNightSeconds
}
