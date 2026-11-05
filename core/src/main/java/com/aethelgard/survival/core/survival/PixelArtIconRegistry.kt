package com.aethelgard.survival.core.survival

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable

/**
 * Generates and caches unique, recognizable 16x16 Pixel Art thumbnail icons
 * for every item and resource in Aethelgard.
 * Uses crisp nearest-neighbor filtering so icons scale cleanly inside hotbar,
 * inventory, crafting, and chest slots.
 */
class PixelArtIconRegistry : Disposable {
    companion object {
        const val ICON_SIZE = 16
    }

    private val pixmapCache = mutableMapOf<String, Pixmap>()
    private val textureCache = mutableMapOf<String, Texture>()

    /**
     * Returns the 16x16 RGBA8888 Pixmap for the given [itemId], creating it on demand.
     */
    fun getOrCreatePixmap(itemId: String): Pixmap {
        pixmapCache[itemId]?.let { return it }
        val pm = buildItemPixelArt(itemId)
        pixmapCache[itemId] = pm
        return pm
    }

    /**
     * Returns the nearest-neighbor filtered LibGDX Texture for the given [itemId].
     */
    fun getOrCreateTexture(itemId: String): Texture {
        textureCache[itemId]?.let { return it }
        val pm = getOrCreatePixmap(itemId)
        val tex = Texture(pm, false)
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        textureCache[itemId] = tex
        return tex
    }

    /**
     * Preloads all catalog item icons.
     */
    fun initializeAll() {
        for (item in ResourceCatalog.ALL_ITEMS) {
            getOrCreateTexture(item.id)
        }
    }

    private fun buildItemPixelArt(itemId: String): Pixmap {
        val (grid, palette) = when (itemId) {
            "wood" -> Pair(
                arrayOf(
                    "................",
                    ".....BBBBBB.....",
                    "...BBLLLLLLBB...",
                    "..BLLDDDDDDLLB..",
                    "..BLDLLLLLLDLB..",
                    "..BLDLBBBBBDLB..",
                    "..BLDBBBBBBDLB..",
                    "..BLDBBBBBBDLB..",
                    "..BLDBBKKBBDLB..",
                    "..BLDBBBBBBDLB..",
                    "..BLDBBBBBBDLB..",
                    "..BLDBBBBBBDLB..",
                    "..BBDDDDDDDDBB..",
                    "...BBBBBBBBBB...",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.38f, 0.23f, 0.11f, 1f),
                    'D' to Color(0.55f, 0.35f, 0.17f, 1f),
                    'L' to Color(0.82f, 0.64f, 0.38f, 1f),
                    'K' to Color(0.28f, 0.16f, 0.07f, 1f)
                )
            )

            "hardwood" -> Pair(
                arrayOf(
                    "................",
                    "...KKKKKKKKKK...",
                    "..KDDDDDDDDDDK..",
                    "..KDRRRRRRRRDK..",
                    "..KDRGGGGGGDRK..",
                    "..KDRGDDDDGDRK..",
                    "..KDRGDDDDGDRK..",
                    "..KDRGGGGGGDRK..",
                    "..KDRRRRRRRRDK..",
                    "..KDDDDDDDDDDK..",
                    "..KDRRRRRRRRDK..",
                    "..KDDDDDDDDDDK..",
                    "...KKKKKKKKKK...",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'K' to Color(0.24f, 0.12f, 0.06f, 1f),
                    'D' to Color(0.46f, 0.24f, 0.12f, 1f),
                    'R' to Color(0.62f, 0.34f, 0.16f, 1f),
                    'G' to Color(0.92f, 0.74f, 0.28f, 1f)
                )
            )

            "branches" -> Pair(
                arrayOf(
                    "................",
                    ".........GGG....",
                    "........GLLLG...",
                    "....GG...GGG....",
                    "...GLLG..BB.....",
                    "....GG..BB......",
                    ".....BBBB.......",
                    "......BBB.......",
                    ".....BBB........",
                    "....BBB..BB.....",
                    "...BBB..BB......",
                    "..BBB..BB.......",
                    "..BB............",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.52f, 0.34f, 0.18f, 1f),
                    'G' to Color(0.20f, 0.56f, 0.22f, 1f),
                    'L' to Color(0.38f, 0.78f, 0.34f, 1f)
                )
            )

            "roots" -> Pair(
                arrayOf(
                    "................",
                    "....BBBBBBBB....",
                    "...BDDDDDDDDB...",
                    "....BBDDDDBB....",
                    "....BBD..DBB....",
                    "...BBD....DBB...",
                    "..BBD..BB..DBB..",
                    "..BD..B..B..DB..",
                    ".BBD.B....B.DBB.",
                    ".BD..B....B..DB.",
                    ".B..B......B..B.",
                    "....B......B....",
                    "...B........B...",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.42f, 0.26f, 0.14f, 1f),
                    'D' to Color(0.64f, 0.44f, 0.24f, 1f)
                )
            )

            "stone" -> Pair(
                arrayOf(
                    "................",
                    ".....DDDDDD.....",
                    "...DDLLLLLLDD...",
                    "..DLLLLLLGGGGD..",
                    "..DLLGGGGGGGGD..",
                    ".DLGGGGGGGGSSSD.",
                    ".DLGGGGGGGSSSSD.",
                    ".DGGGGGGGGSSSSD.",
                    ".DGGGGGGGSSSSSD.",
                    "..DGGGGSSSSSSD..",
                    "..DSSSSSSSSSSD..",
                    "...DDDDDDDDDD...",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'D' to Color(0.28f, 0.31f, 0.36f, 1f),
                    'S' to Color(0.45f, 0.49f, 0.56f, 1f),
                    'G' to Color(0.62f, 0.67f, 0.74f, 1f),
                    'L' to Color(0.80f, 0.84f, 0.90f, 1f)
                )
            )

            "coal" -> Pair(
                arrayOf(
                    "................",
                    "......KKKK......",
                    "....KKDDDDKK....",
                    "...KDDLLLLDDK...",
                    "..KDDDDDDDDDDK..",
                    "..KDDKKKKKKDDK..",
                    ".KDDKKKKKKKKDDK.",
                    ".KDKKKKKKKKKKDK.",
                    ".KDKKKKLLKKKKDK.",
                    "..KDKKKKKKKKDK..",
                    "..KDDDDDDDDDDK..",
                    "...KKKKKKKKKK...",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'K' to Color(0.10f, 0.11f, 0.14f, 1f),
                    'D' to Color(0.20f, 0.22f, 0.27f, 1f),
                    'L' to Color(0.42f, 0.46f, 0.54f, 1f)
                )
            )

            "copper_ore" -> Pair(
                arrayOf(
                    "................",
                    ".....DDDDDD.....",
                    "...DDGGGGGGDD...",
                    "..DGGCCCCCCGGD..",
                    "..DGCCLLLLCCGD..",
                    ".DGGGGCCCCGGGGD.",
                    ".DGGGGGSSCCGGGD.",
                    ".DGGCCSSSSCCGGD.",
                    ".DGCCLLSSSSCCGD.",
                    "..DGGSSSSSSGGD..",
                    "...DDDDDDDDDD...",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'D' to Color(0.30f, 0.32f, 0.36f, 1f),
                    'G' to Color(0.54f, 0.58f, 0.64f, 1f),
                    'S' to Color(0.40f, 0.44f, 0.50f, 1f),
                    'C' to Color(0.85f, 0.46f, 0.18f, 1f),
                    'L' to Color(0.98f, 0.68f, 0.34f, 1f)
                )
            )

            "iron_ore" -> Pair(
                arrayOf(
                    "................",
                    ".....DDDDDD.....",
                    "...DDGGGGGGDD...",
                    "..DGGIIIIIIIGD..",
                    "..DGIIWWWWWIIGD.",
                    ".DGGGGIIIIIGGGD.",
                    ".DGGGGGSSIIGGGD.",
                    ".DGGIISSSSIIGGD.",
                    ".DGIIWWSSSSIIGD.",
                    "..DGGSSSSSSGGD..",
                    "...DDDDDDDDDD...",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'D' to Color(0.26f, 0.29f, 0.34f, 1f),
                    'G' to Color(0.48f, 0.52f, 0.58f, 1f),
                    'S' to Color(0.36f, 0.40f, 0.46f, 1f),
                    'I' to Color(0.76f, 0.84f, 0.94f, 1f),
                    'W' to Color(0.95f, 0.98f, 1.00f, 1f)
                )
            )

            "rare_ore" -> Pair(
                arrayOf(
                    "................",
                    "......CCCC......",
                    "....CCWWWWCC....",
                    "...CDGGGGGGDC...",
                    "..CDGYYYYYYGDC..",
                    ".CDGYWWWWWWYGDC.",
                    ".CDGYWCCCCWYGDC.",
                    ".CDGYYYYYYYYGDC.",
                    "..CDGGGGGGGGDC..",
                    "...CCDDDDDDCC...",
                    ".....CCCCCC.....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'D' to Color(0.24f, 0.26f, 0.34f, 1f),
                    'G' to Color(0.44f, 0.48f, 0.58f, 1f),
                    'C' to Color(0.22f, 0.86f, 0.98f, 1f),
                    'Y' to Color(0.98f, 0.80f, 0.20f, 1f),
                    'W' to Color(1.00f, 0.98f, 0.85f, 1f)
                )
            )

            "plants" -> Pair(
                arrayOf(
                    "................",
                    ".......LL.......",
                    "......LWWL......",
                    "...LL.LGGLE.LL..",
                    "..LGGL.GG..LGGL.",
                    "..LGGG.GG.LGGGL.",
                    "...LLGGGGGGGLL..",
                    ".....GGGGGG.....",
                    "...LL.GGGG.LL...",
                    "..LGGL.GG.LGGL..",
                    "...LL..GG..LL...",
                    ".......DD.......",
                    ".......DD.......",
                    "......DDDD......",
                    "................",
                    "................"
                ),
                mapOf(
                    'D' to Color(0.14f, 0.42f, 0.18f, 1f),
                    'G' to Color(0.22f, 0.68f, 0.28f, 1f),
                    'L' to Color(0.42f, 0.88f, 0.44f, 1f),
                    'E' to Color(0.32f, 0.82f, 0.38f, 1f),
                    'W' to Color(0.75f, 0.98f, 0.78f, 1f)
                )
            )

            "mushrooms" -> Pair(
                arrayOf(
                    "................",
                    ".....RRRRRR.....",
                    "...RRRRWWRRRR...",
                    "..RRWWRRRRRRWR..",
                    ".RRWWWRRRRRWWRR.",
                    ".RRRRRRRRRRRRRR.",
                    ".RRRRWWRRRRWWRR.",
                    "..RRRRRRRRRRRR..",
                    "....SSSSSSSS....",
                    ".....SSSSSS.....",
                    ".....SSSSSS.....",
                    ".....SSSSSS.....",
                    "....SSSSSSSS....",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'R' to Color(0.88f, 0.22f, 0.24f, 1f),
                    'W' to Color(0.98f, 0.95f, 0.88f, 1f),
                    'S' to Color(0.88f, 0.82f, 0.70f, 1f)
                )
            )

            "berries" -> Pair(
                arrayOf(
                    "................",
                    "......GGGG......",
                    "....GGLLLLGG....",
                    ".....GGGGGG.....",
                    "...RRRR..RRRR...",
                    "..RWWWRRRRWWRR..",
                    "..RRRRRRRRRRRR..",
                    "...RRRRRRRRRR...",
                    ".....RRRRRR.....",
                    "....RWWWRRRR....",
                    "....RRRRRRRR....",
                    ".....RRRRRR.....",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'G' to Color(0.18f, 0.58f, 0.24f, 1f),
                    'L' to Color(0.36f, 0.82f, 0.38f, 1f),
                    'R' to Color(0.88f, 0.18f, 0.48f, 1f),
                    'W' to Color(1.00f, 0.72f, 0.86f, 1f)
                )
            )

            "fibers" -> Pair(
                arrayOf(
                    "................",
                    "....GGGGGGGG....",
                    "...GLLLLLLLLG...",
                    "...GLLGGGGLLG...",
                    "....YYYYYYYY....",
                    "...YYYYYYYYYY...",
                    "...GLLGGGGLLG...",
                    "...GLLLLLLLLG...",
                    "....YYYYYYYY....",
                    "...GLLLLLLLLG...",
                    "...GLLGGGGLLG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'G' to Color(0.32f, 0.58f, 0.22f, 1f),
                    'L' to Color(0.58f, 0.78f, 0.34f, 1f),
                    'Y' to Color(0.88f, 0.74f, 0.32f, 1f)
                )
            )

            "animal_hides" -> Pair(
                arrayOf(
                    "................",
                    "..BBB......BBB..",
                    ".BDDDDBBBBDDDDB.",
                    "..BDDDDDDDDDDB..",
                    "...BDDDDDDDDB...",
                    "...BDDDLLDBDB...",
                    "..BDDDDLLLDDDB..",
                    "..BDDDDDDDDDDB..",
                    "...BDDDDDDDDB...",
                    "..BDDDDDDDDDDB..",
                    ".BDDDDBBBBDDDDB.",
                    "..BBB......BBB..",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.42f, 0.26f, 0.14f, 1f),
                    'D' to Color(0.68f, 0.46f, 0.26f, 1f),
                    'L' to Color(0.84f, 0.64f, 0.42f, 1f)
                )
            )

            "monster_essence" -> Pair(
                arrayOf(
                    "................",
                    ".......PP.......",
                    ".....PPMMPP.....",
                    "....PMMMMMMP....",
                    "...PMMWWWWWMMP..",
                    "..PMMWWCCWWWMMP.",
                    "..PMMWCCCCWWMMP.",
                    "..PMMWWCCWWWMMP.",
                    "...PMMWWWWWMMP..",
                    "....PMMMMMMP....",
                    ".....PPMMPP.....",
                    "......PPPP......",
                    ".......PP.......",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'P' to Color(0.46f, 0.18f, 0.78f, 1f),
                    'M' to Color(0.76f, 0.32f, 0.96f, 1f),
                    'W' to Color(0.96f, 0.76f, 1.00f, 1f),
                    'C' to Color(1.00f, 0.28f, 0.56f, 1f)
                )
            )

            "magic_crystals" -> Pair(
                arrayOf(
                    "................",
                    ".......CC.......",
                    "......CWWC......",
                    ".....CWLLWC.....",
                    "....CWLLLLWC....",
                    "...CCWLLLLWCC...",
                    "...CLLLLLLLLC...",
                    "...CLLLLLLLLC...",
                    "...CCLLLLLLCC...",
                    "....CCLLLLCC....",
                    ".....CCCCCC.....",
                    "......CCCC......",
                    ".......CC.......",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'C' to Color(0.10f, 0.58f, 0.86f, 1f),
                    'L' to Color(0.28f, 0.88f, 0.98f, 1f),
                    'W' to Color(0.85f, 0.98f, 1.00f, 1f)
                )
            )

            "ancient_relics" -> Pair(
                arrayOf(
                    "................",
                    "....GGGGGGGG....",
                    "...GYYYYYYYYG...",
                    "..GYYCCCCCCYYG..",
                    "..GYCCWWWWCCYG..",
                    "..GYCWCCCCWCYG..",
                    "..GYCWCCCCWCYG..",
                    "..GYCCWWWWCCYG..",
                    "..GYYCCCCCCYYG..",
                    "...GYYYYYYYYG...",
                    "...GYYGGGGYYG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'G' to Color(0.68f, 0.48f, 0.12f, 1f),
                    'Y' to Color(0.96f, 0.80f, 0.24f, 1f),
                    'C' to Color(0.22f, 0.86f, 0.98f, 1f),
                    'W' to Color(0.90f, 0.99f, 1.00f, 1f)
                )
            )

            "boss_materials" -> Pair(
                arrayOf(
                    "................",
                    "..KK........KK..",
                    "..KRR......RRK..",
                    "...KRRRRRRRRK...",
                    "...KRRWWWWRRK...",
                    "..KRRWWYYWWRRK..",
                    "..KRRWYYYYWRRK..",
                    "..KRRWWYYWWRRK..",
                    "...KRRWWWWRRK...",
                    "....KRRRRRRK....",
                    ".....KKKKKK.....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'K' to Color(0.18f, 0.08f, 0.12f, 1f),
                    'R' to Color(0.88f, 0.16f, 0.24f, 1f),
                    'W' to Color(1.00f, 0.48f, 0.32f, 1f),
                    'Y' to Color(1.00f, 0.90f, 0.36f, 1f)
                )
            )

            "green_crystal" -> Pair(
                arrayOf(
                    "................",
                    ".......GG.......",
                    "......GWWG......",
                    ".....GWLLWG.....",
                    "....GWLLLLWG....",
                    "...GGWLLLLWGG...",
                    "...GLLLLLLLLG...",
                    "...GLLLLLLLLG...",
                    "...GGLLLLLLGG...",
                    "....GGLLLLGG....",
                    ".....GGGGGG.....",
                    "......GGGG......",
                    ".......GG.......",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'G' to Color(0.08f, 0.56f, 0.26f, 1f),
                    'L' to Color(0.20f, 0.94f, 0.48f, 1f),
                    'W' to Color(0.82f, 1.00f, 0.88f, 1f)
                )
            )

            "mp_potion" -> Pair(
                arrayOf(
                    "................",
                    "......CCCC......",
                    "......CCCC......",
                    ".....SSSSSS.....",
                    "......GGGG......",
                    "....GGWWWWGG....",
                    "...GWBBBBBBWG...",
                    "..GWBBLLLLBBWG..",
                    "..GBBLLWWLLBBG..",
                    "..GBBBBBBBBBBG..",
                    "..GBBBBBBBBBBG..",
                    "...GBBBBBBBBG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'C' to Color(0.62f, 0.42f, 0.24f, 1f),
                    'S' to Color(0.82f, 0.88f, 0.96f, 1f),
                    'G' to Color(0.68f, 0.86f, 1.00f, 1f),
                    'B' to Color(0.14f, 0.52f, 0.98f, 1f),
                    'L' to Color(0.38f, 0.82f, 1.00f, 1f),
                    'W' to Color(0.92f, 0.98f, 1.00f, 1f)
                )
            )

            "stone_axe", "iron_axe" -> {
                val isIron = itemId == "iron_axe"
                Pair(
                    arrayOf(
                        "................",
                        "........MMMMM...",
                        ".......MLLLLLM..",
                        "......MLLLLLLM..",
                        "......MLLLLBBM..",
                        ".......MMM.BB...",
                        "..........BB....",
                        ".........BB.....",
                        "........BB......",
                        ".......BB.......",
                        "......BB........",
                        ".....BB.........",
                        "....BB..........",
                        "................",
                        "................",
                        "................"
                    ),
                    mapOf(
                        'B' to Color(0.54f, 0.35f, 0.18f, 1f),
                        'M' to if (isIron) Color(0.52f, 0.64f, 0.78f, 1f) else Color(0.44f, 0.48f, 0.54f, 1f),
                        'L' to if (isIron) Color(0.82f, 0.92f, 1.00f, 1f) else Color(0.68f, 0.72f, 0.78f, 1f)
                    )
                )
            }

            "stone_pickaxe", "iron_pickaxe" -> {
                val isIron = itemId == "iron_pickaxe"
                Pair(
                    arrayOf(
                        "................",
                        "....MMMMMMM.....",
                        "...MLLLLLLLM....",
                        "..MLL....BBLLM..",
                        "..MM....BB..MM..",
                        ".......BB.......",
                        "......BB........",
                        ".....BB.........",
                        "....BB..........",
                        "...BB...........",
                        "..BB............",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................"
                    ),
                    mapOf(
                        'B' to Color(0.54f, 0.35f, 0.18f, 1f),
                        'M' to if (isIron) Color(0.48f, 0.62f, 0.78f, 1f) else Color(0.44f, 0.48f, 0.54f, 1f),
                        'L' to if (isIron) Color(0.80f, 0.92f, 1.00f, 1f) else Color(0.68f, 0.72f, 0.78f, 1f)
                    )
                )
            }

            "shovel" -> Pair(
                arrayOf(
                    "................",
                    ".........MMMM...",
                    "........MLLLLM..",
                    "........MLLLLM..",
                    ".........MMMM...",
                    "........BB......",
                    ".......BB.......",
                    "......BB........",
                    ".....BB.........",
                    "....BB..........",
                    "...BB...........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.54f, 0.35f, 0.18f, 1f),
                    'M' to Color(0.54f, 0.60f, 0.68f, 1f),
                    'L' to Color(0.82f, 0.88f, 0.94f, 1f)
                )
            )

            "sickle" -> Pair(
                arrayOf(
                    "................",
                    "......MMMMMM....",
                    "....MMLLLLLLM...",
                    "...MLL....LLLM..",
                    "...ML......LLM..",
                    "...........BB...",
                    "..........BB....",
                    ".........BB.....",
                    "........BB......",
                    ".......BB.......",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.54f, 0.35f, 0.18f, 1f),
                    'M' to Color(0.48f, 0.56f, 0.64f, 1f),
                    'L' to Color(0.84f, 0.90f, 0.96f, 1f)
                )
            )

            "knife" -> Pair(
                arrayOf(
                    "................",
                    "...........MM...",
                    "..........MLLM..",
                    ".........MLLLM..",
                    "........MLLLM...",
                    ".......MLLLM....",
                    "......YYYY......",
                    ".....BBB........",
                    "....BBB.........",
                    "...BB...........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.48f, 0.30f, 0.16f, 1f),
                    'Y' to Color(0.92f, 0.76f, 0.24f, 1f),
                    'M' to Color(0.54f, 0.60f, 0.68f, 1f),
                    'L' to Color(0.88f, 0.92f, 0.98f, 1f)
                )
            )

            "builders_hammer" -> Pair(
                arrayOf(
                    "................",
                    ".....MMMMMMM....",
                    "....MLLLLLLLM...",
                    "....MLLYYYLLM...",
                    ".....MMMMMMM....",
                    "........BB......",
                    ".......BB.......",
                    "......BB........",
                    ".....BB.........",
                    "....BB..........",
                    "...BB...........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.54f, 0.35f, 0.18f, 1f),
                    'Y' to Color(0.96f, 0.78f, 0.22f, 1f),
                    'M' to Color(0.50f, 0.56f, 0.64f, 1f),
                    'L' to Color(0.82f, 0.88f, 0.94f, 1f)
                )
            )

            "roasted_mushrooms" -> Pair(
                arrayOf(
                    "................",
                    "..........BB....",
                    ".....OOOOOB.....",
                    "....OLLLLOO.....",
                    "....OOOOOOO.....",
                    "......BBB.......",
                    "...OOOOOB.......",
                    "..OLLLLOO.......",
                    "..OOOOOOO.......",
                    "....BB..........",
                    "...BB...........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.46f, 0.28f, 0.14f, 1f),
                    'O' to Color(0.78f, 0.38f, 0.14f, 1f),
                    'L' to Color(0.96f, 0.64f, 0.28f, 1f)
                )
            )

            "berry_brew" -> Pair(
                arrayOf(
                    "................",
                    "......CCCC......",
                    ".....GGGGGG.....",
                    "....GWWWWWWG....",
                    "...GWRRRRRRWG...",
                    "..GWRRLLLLRRWG..",
                    "..GRRRRRRRRRRG..",
                    "..GRRRRRRRRRRG..",
                    "...GRRRRRRRRG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'C' to Color(0.58f, 0.38f, 0.20f, 1f),
                    'G' to Color(0.72f, 0.88f, 0.98f, 1f),
                    'R' to Color(0.88f, 0.20f, 0.54f, 1f),
                    'L' to Color(1.00f, 0.56f, 0.80f, 1f),
                    'W' to Color(0.94f, 0.98f, 1.00f, 1f)
                )
            )

            "herbal_salve" -> Pair(
                arrayOf(
                    "................",
                    "....BBBBBBBB....",
                    "...BCCCCCCCCB...",
                    "....GGGGGGGG....",
                    "...GEEEEEEEEG...",
                    "..GEELLLLLEEEG..",
                    "..GEELLWWLLEEG..",
                    "..GEELLLLLEEEG..",
                    "...GEEEEEEEEG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'B' to Color(0.44f, 0.28f, 0.14f, 1f),
                    'C' to Color(0.64f, 0.44f, 0.24f, 1f),
                    'G' to Color(0.72f, 0.88f, 0.82f, 1f),
                    'E' to Color(0.16f, 0.72f, 0.38f, 1f),
                    'L' to Color(0.36f, 0.92f, 0.56f, 1f),
                    'W' to Color(0.88f, 1.00f, 0.92f, 1f)
                )
            )

            "mana_essence_tonic" -> Pair(
                arrayOf(
                    "................",
                    "......CCCC......",
                    ".....GGGGGG.....",
                    "....GWWWWWWG....",
                    "...GWPPPPPPWG...",
                    "..GWPPLLLLPPWG..",
                    "..GPPPPPPPPPPG..",
                    "..GPPPPPPPPPPG..",
                    "...GPPPPPPPPG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'C' to Color(0.62f, 0.42f, 0.24f, 1f),
                    'G' to Color(0.76f, 0.84f, 0.98f, 1f),
                    'P' to Color(0.62f, 0.24f, 0.94f, 1f),
                    'L' to Color(0.86f, 0.56f, 1.00f, 1f),
                    'W' to Color(0.95f, 0.92f, 1.00f, 1f)
                )
            )

            "storage_chest" -> Pair(
                arrayOf(
                    "................",
                    "..KKKKKKKKKKKK..",
                    ".KBBBBBBBBBBBBK.",
                    ".KBBYYYYYYYYBBK.",
                    ".KKKKKKKKKKKKKK.",
                    ".KBBBBYYYYBBBBK.",
                    ".KBBBBYKKYBBBBK.",
                    ".KBBBBYYYYBBBBK.",
                    ".KBBBBBBBBBBBBK.",
                    "..KKKKKKKKKKKK..",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'K' to Color(0.24f, 0.14f, 0.06f, 1f),
                    'B' to Color(0.62f, 0.38f, 0.16f, 1f),
                    'Y' to Color(0.94f, 0.78f, 0.24f, 1f)
                )
            )

            "crafting_table" -> Pair(
                arrayOf(
                    "................",
                    ".KKKKKKKKKKKKKK.",
                    ".KLLLLBBBBLLLLK.",
                    ".KBBBBLLLLBBBBK.",
                    ".KKKKKKKKKKKKKK.",
                    "..KBBK....KBBK..",
                    "..KBBK....KBBK..",
                    "..KBBK....KBBK..",
                    "..KBBK....KBBK..",
                    "..KKKK....KKKK..",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'K' to Color(0.26f, 0.16f, 0.08f, 1f),
                    'B' to Color(0.56f, 0.34f, 0.16f, 1f),
                    'L' to Color(0.78f, 0.54f, 0.28f, 1f)
                )
            )

            "health_potion" -> Pair(
                arrayOf(
                    "................",
                    "......CCCC......",
                    "......CCCC......",
                    ".....GGGGGG.....",
                    "......GGGG......",
                    ".....GWWWWG.....",
                    "....GWRRRRRG....",
                    "...GWRLLRRRRG...",
                    "..GWRLLRRRRRRG..",
                    "..GWRRRRRRRRRG..",
                    "..GRRRRRRRRRRG..",
                    "...GRRRRRRRRG...",
                    "....GGGGGGGG....",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'C' to Color(0.68f, 0.46f, 0.24f, 1f),
                    'G' to Color(0.76f, 0.88f, 0.96f, 1f),
                    'W' to Color(1.00f, 0.96f, 0.98f, 1f),
                    'R' to Color(0.88f, 0.14f, 0.22f, 1f),
                    'L' to Color(1.00f, 0.46f, 0.54f, 1f)
                )
            )

            else -> Pair(
                // "campfire" and any custom item fallback
                arrayOf(
                    "................",
                    ".......RR.......",
                    "......ROOR......",
                    ".....ROYYOR.....",
                    "....ROYYYYOR....",
                    "....ROYYYYOR....",
                    ".....ROYYOR.....",
                    "...BBBBBBBBBB...",
                    "..BBBBBBBBBBBB..",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
                ),
                mapOf(
                    'R' to Color(0.94f, 0.28f, 0.14f, 1f),
                    'O' to Color(0.98f, 0.58f, 0.18f, 1f),
                    'Y' to Color(1.00f, 0.90f, 0.32f, 1f),
                    'B' to Color(0.46f, 0.28f, 0.14f, 1f)
                )
            )
        }

        val pm = Pixmap(ICON_SIZE, ICON_SIZE, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()

        for (y in 0 until ICON_SIZE) {
            val row = grid.getOrNull(y) ?: continue
            for (x in 0 until ICON_SIZE) {
                val ch = row.getOrNull(x) ?: '.'
                if (ch != '.') {
                    val col = palette[ch] ?: Color.WHITE
                    pm.setColor(col.r, col.g, col.b, col.a)
                    pm.drawPixel(x, y)
                }
            }
        }
        return pm
    }

    override fun dispose() {
        for (tex in textureCache.values) {
            runCatching { tex.dispose() }
        }
        textureCache.clear()
        for (pm in pixmapCache.values) {
            runCatching { pm.dispose() }
        }
        pixmapCache.clear()
    }
}
