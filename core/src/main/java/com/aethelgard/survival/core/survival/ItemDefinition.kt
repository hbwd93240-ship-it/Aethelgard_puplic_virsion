package com.aethelgard.survival.core.survival

enum class ItemCategory(val displayName: String) {
    WOOD("Wood Resource"),
    MINERAL("Stone & Mineral"),
    NATURE("Nature Resource"),
    MAGIC("Magical Resource"),
    TOOL("Tool & Equipment"),
    CONSUMABLE("Consumable Food & Drink"),
    STRUCTURE("Placeable Structure")
}

enum class ToolType(val displayName: String) {
    NONE("None"),
    AXE("Axe"),
    PICKAXE("Pickaxe"),
    SHOVEL("Shovel"),
    SICKLE("Sickle"),
    KNIFE("Knife"),
    HAMMER("Builder's Hammer")
}

data class ItemDefinition(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val stackLimit: Int = 99,
    val description: String,
    val sourceDescription: String,
    val toolPower: Float = 1.0f,
    val toolType: ToolType = ToolType.NONE,
    val toolTier: Int = 0,
    val maxDurability: Int = 0,
    val repairMaterialId: String = "wood",
    val hungerRestore: Float = 0f,
    val thirstRestore: Float = 0f,
    val healthRestore: Float = 0f,
    val energyRestore: Float = 0f,
    val staminaRestore: Float = 0f,
    val badgeSymbol: String = "◆",
    val badgeColorHex: String = "#38BDF8"
) {
    val gatheringSource: String get() = sourceDescription
    val foodRestore: Float get() = hungerRestore
    val drinkRestore: Float get() = thirstRestore
    val healAmount: Float get() = healthRestore
}

object ResourceCatalog {

    // A. Wood Resources
    val WOOD = ItemDefinition(
        id = "wood",
        name = "Wood",
        category = ItemCategory.WOOD,
        stackLimit = 99,
        description = "Standard timber harvested from fantasy grove trees. Essential for basic crafting.",
        sourceDescription = "Harvested by chopping fantasy trees with axes, fists, or swords.",
        badgeSymbol = "LOG",
        badgeColorHex = "#A16207"
    )

    val HARDWOOD = ItemDefinition(
        id = "hardwood",
        name = "Hardwood",
        category = ItemCategory.WOOD,
        stackLimit = 99,
        description = "Dense, resilient core wood from mature ancient trees. Used in advanced tools.",
        sourceDescription = "Rare drop when felling mature trees.",
        badgeSymbol = "HRD",
        badgeColorHex = "#78350F"
    )

    val BRANCHES = ItemDefinition(
        id = "branches",
        name = "Branches",
        category = ItemCategory.WOOD,
        stackLimit = 99,
        description = "Flexible twigs and branches. Great for crafting tool handles and kindling.",
        sourceDescription = "Dropped while chopping trees or foraging woodland ground.",
        badgeSymbol = "BRN",
        badgeColorHex = "#B45309"
    )

    val ROOTS = ItemDefinition(
        id = "roots",
        name = "Roots",
        category = ItemCategory.WOOD,
        stackLimit = 99,
        description = "Tough, fibrous roots extracted from the stump of destroyed trees.",
        sourceDescription = "Obtained upon completely felling a tree stump.",
        badgeSymbol = "ROT",
        badgeColorHex = "#92400E"
    )

    // B. Stone and Minerals
    val STONE = ItemDefinition(
        id = "stone",
        name = "Stone",
        category = ItemCategory.MINERAL,
        stackLimit = 99,
        description = "Solid stone chipped from clearing boulders. Foundation for stone tools.",
        sourceDescription = "Harvested by mining mossy boulders in the clearing.",
        badgeSymbol = "STN",
        badgeColorHex = "#94A3B8"
    )

    val COAL = ItemDefinition(
        id = "coal",
        name = "Coal",
        category = ItemCategory.MINERAL,
        stackLimit = 99,
        description = "Combustible mineral fuel for campfires and smelting ores.",
        sourceDescription = "Mined from dark mineral veins within clearing boulders.",
        badgeSymbol = "COL",
        badgeColorHex = "#334155"
    )

    val COPPER_ORE = ItemDefinition(
        id = "copper_ore",
        name = "Copper Ore",
        category = ItemCategory.MINERAL,
        stackLimit = 99,
        description = "Raw malleable metallic ore with a distinctive reddish-orange sheen.",
        sourceDescription = "Mined from mineral boulders.",
        badgeSymbol = "CPR",
        badgeColorHex = "#D97706"
    )

    val IRON_ORE = ItemDefinition(
        id = "iron_ore",
        name = "Iron Ore",
        category = ItemCategory.MINERAL,
        stackLimit = 99,
        description = "Heavy metallic ore essential for forging durable tools and reinforced chests.",
        sourceDescription = "Mined from crystal rock outcrops across the clearing.",
        badgeSymbol = "IRN",
        badgeColorHex = "#64748B"
    )

    val RARE_ORE = ItemDefinition(
        id = "rare_ore",
        name = "Rare Ore",
        category = ItemCategory.MINERAL,
        stackLimit = 99,
        description = "Glittering mana-infused mineral with extraordinary hardness and magical resonance.",
        sourceDescription = "Rare drop from crystal boulders or ancient monument perimeters.",
        badgeSymbol = "RAR",
        badgeColorHex = "#F59E0B"
    )

    // C. Nature Resources
    val PLANTS = ItemDefinition(
        id = "plants",
        name = "Plants",
        category = ItemCategory.NATURE,
        stackLimit = 99,
        description = "Lush bioluminescent wild herbs and leaves. Used in healing salves and brewing.",
        sourceDescription = "Gathered from flora clusters and glowing ferns.",
        badgeSymbol = "PLN",
        badgeColorHex = "#10B981"
    )

    val MUSHROOMS = ItemDefinition(
        id = "mushrooms",
        name = "Mushrooms",
        category = ItemCategory.NATURE,
        stackLimit = 99,
        description = "Edible forest fungi rich in nutrients. Can be eaten raw or roasted over fire.",
        sourceDescription = "Foraged from shaded patches near tree trunks.",
        hungerRestore = 12f,
        badgeSymbol = "MSH",
        badgeColorHex = "#84CC16"
    )

    val BERRIES = ItemDefinition(
        id = "berries",
        name = "Berries",
        category = ItemCategory.NATURE,
        stackLimit = 99,
        description = "Juicy glowing berries that satisfy both hunger and thirst on the go.",
        sourceDescription = "Harvested from wild berry bushes in the sanctuary clearing.",
        hungerRestore = 10f,
        thirstRestore = 14f,
        badgeSymbol = "BRY",
        badgeColorHex = "#EC4899"
    )

    val FIBERS = ItemDefinition(
        id = "fibers",
        name = "Fibers",
        category = ItemCategory.NATURE,
        stackLimit = 99,
        description = "Strong plant fibers spun into cordage, tool bindings, and medical bandages.",
        sourceDescription = "Harvested from tall grass and reeds.",
        badgeSymbol = "FBR",
        badgeColorHex = "#A3E635"
    )

    val ANIMAL_HIDES = ItemDefinition(
        id = "animal_hides",
        name = "Animal Hides",
        category = ItemCategory.NATURE,
        stackLimit = 99,
        description = "Treated leather and pelt for crafting reinforced gear and pouches.",
        sourceDescription = "Collected from woodland foraging nodes and creature encounters.",
        badgeSymbol = "PEL",
        badgeColorHex = "#B45309"
    )

    // D. Magical Resources
    val MONSTER_ESSENCE = ItemDefinition(
        id = "monster_essence",
        name = "Monster Essence",
        category = ItemCategory.MAGIC,
        stackLimit = 99,
        description = "Concentrated arcane spirit essence distilled from defeated forest monsters.",
        sourceDescription = "Obtained by defeating monsters in combat.",
        badgeSymbol = "ESS",
        badgeColorHex = "#8B5CF6"
    )

    val MAGIC_CRYSTALS = ItemDefinition(
        id = "magic_crystals",
        name = "Magic Crystals",
        category = ItemCategory.MAGIC,
        stackLimit = 99,
        description = "Pure condensed mana crystal radiating serene silver-cyan energy.",
        sourceDescription = "Gathered from floating and orbiting mana crystals around the monument.",
        badgeSymbol = "CRY",
        badgeColorHex = "#06B6D4"
    )

    val ANCIENT_RELICS = ItemDefinition(
        id = "ancient_relics",
        name = "Ancient Relics",
        category = ItemCategory.MAGIC,
        stackLimit = 99,
        description = "Fragments of prehistoric runic technology imbued with ancient power.",
        sourceDescription = "Excavated near the Central Ancient Rune Monument.",
        badgeSymbol = "RLC",
        badgeColorHex = "#F59E0B"
    )

    val BOSS_MATERIALS = ItemDefinition(
        id = "boss_materials",
        name = "Corrupted Guardian Core",
        category = ItemCategory.MAGIC,
        stackLimit = 99,
        description = "Pulsating crimson mana heart extracted from the defeated Corrupted Guardian Knight.",
        sourceDescription = "Guaranteed trophy drop from defeating the Corrupted Mana Guardian Knight.",
        badgeSymbol = "COR",
        badgeColorHex = "#EF4444"
    )

    val GREEN_CRYSTAL = ItemDefinition(
        id = "green_crystal",
        name = "Green Crystal",
        category = ItemCategory.MAGIC,
        stackLimit = 99,
        description = "A radiant emerald-green crystal radiating vibrant slime energy.",
        sourceDescription = "Guaranteed drop from defeating Green Slimes.",
        badgeSymbol = "SLM",
        badgeColorHex = "#10B981"
    )

    // E. Crafted Tools
    val STONE_AXE = ItemDefinition(
        id = "stone_axe",
        name = "Stone Axe",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Sharp flint head bound with fiber to a wooden shaft. Chops trees 2x faster.",
        sourceDescription = "Crafted by hand or at Crafting Table (4 Wood, 3 Stone, 2 Fibers).",
        toolPower = 2.0f,
        toolType = ToolType.AXE,
        toolTier = 1,
        maxDurability = 40,
        repairMaterialId = "stone",
        badgeSymbol = "AX1",
        badgeColorHex = "#94A3B8"
    )

    val IRON_AXE = ItemDefinition(
        id = "iron_axe",
        name = "Iron Axe",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Forged iron blade with superior edge retention. Chops trees 3.5x faster.",
        sourceDescription = "Crafted at the Crafting Table (4 Hardwood, 3 Iron Ore, 2 Fibers).",
        toolPower = 3.5f,
        toolType = ToolType.AXE,
        toolTier = 2,
        maxDurability = 80,
        repairMaterialId = "iron_ore",
        badgeSymbol = "AX2",
        badgeColorHex = "#38BDF8"
    )

    val STONE_PICKAXE = ItemDefinition(
        id = "stone_pickaxe",
        name = "Stone Pickaxe",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Pointed stone pick for breaking boulders and excavating basic mineral ores.",
        sourceDescription = "Crafted by hand or at Crafting Table (3 Wood, 4 Stone).",
        toolPower = 2.0f,
        toolType = ToolType.PICKAXE,
        toolTier = 1,
        maxDurability = 45,
        repairMaterialId = "stone",
        badgeSymbol = "PK1",
        badgeColorHex = "#94A3B8"
    )

    val IRON_PICKAXE = ItemDefinition(
        id = "iron_pickaxe",
        name = "Iron Pickaxe",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Hardened iron pick capable of shattering crystal boulders and rich rare ore veins.",
        sourceDescription = "Crafted at the Crafting Table (3 Hardwood, 4 Iron Ore).",
        toolPower = 3.5f,
        toolType = ToolType.PICKAXE,
        toolTier = 2,
        maxDurability = 90,
        repairMaterialId = "iron_ore",
        badgeSymbol = "PK2",
        badgeColorHex = "#38BDF8"
    )

    val SHOVEL = ItemDefinition(
        id = "shovel",
        name = "Shovel",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Broad spade for digging up deep roots, forest mushrooms, and rich soil patches.",
        sourceDescription = "Crafted by hand or at Crafting Table (2 Wood, 2 Stone, 1 Fibers).",
        toolPower = 2.5f,
        toolType = ToolType.SHOVEL,
        toolTier = 1,
        maxDurability = 50,
        repairMaterialId = "stone",
        badgeSymbol = "SHV",
        badgeColorHex = "#D97706"
    )

    val SICKLE = ItemDefinition(
        id = "sickle",
        name = "Sickle",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Curved harvesting blade designed to swiftly reap wild plants, herbs, and fibers.",
        sourceDescription = "Crafted by hand or at Crafting Table (2 Wood, 2 Stone, 2 Fibers).",
        toolPower = 2.5f,
        toolType = ToolType.SICKLE,
        toolTier = 1,
        maxDurability = 55,
        repairMaterialId = "stone",
        badgeSymbol = "SCK",
        badgeColorHex = "#10B981"
    )

    val KNIFE = ItemDefinition(
        id = "knife",
        name = "Knife",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Keen utility survival knife for carefully slicing berries, mushrooms, fibers, and hides.",
        sourceDescription = "Crafted by hand or at Crafting Table (2 Branches, 2 Stone, 1 Fibers).",
        toolPower = 2.2f,
        toolType = ToolType.KNIFE,
        toolTier = 1,
        maxDurability = 60,
        repairMaterialId = "stone",
        badgeSymbol = "KNF",
        badgeColorHex = "#E2E8F0"
    )

    val BUILDERS_HAMMER = ItemDefinition(
        id = "builders_hammer",
        name = "Builder's Hammer",
        category = ItemCategory.TOOL,
        stackLimit = 1,
        description = "Specialized structural mallet for inspecting, repairing, upgrading, and dismantling building pieces.",
        sourceDescription = "Crafted by hand or at Crafting Table (3 Wood, 2 Stone).",
        toolPower = 2.0f,
        toolType = ToolType.HAMMER,
        toolTier = 1,
        maxDurability = 80,
        repairMaterialId = "wood",
        badgeSymbol = "HMR",
        badgeColorHex = "#F59E0B"
    )

    // F. Consumables
    val ROASTED_MUSHROOMS = ItemDefinition(
        id = "roasted_mushrooms",
        name = "Roasted Mushrooms",
        category = ItemCategory.CONSUMABLE,
        stackLimit = 20,
        description = "Warm roasted woodland mushrooms. Substantially restores hunger.",
        sourceDescription = "Cooked at Crafting Table (2 Mushrooms).",
        hungerRestore = 28f,
        healthRestore = 10f,
        badgeSymbol = "FOD",
        badgeColorHex = "#F97316"
    )

    val BERRY_BREW = ItemDefinition(
        id = "berry_brew",
        name = "Berry Brew",
        category = ItemCategory.CONSUMABLE,
        stackLimit = 20,
        description = "Refreshing herbal juice extracted from wild berries. Quenches thirst and restores stamina.",
        sourceDescription = "Brewed at Crafting Table (3 Berries, 1 Plants).",
        thirstRestore = 35f,
        staminaRestore = 30f,
        badgeSymbol = "DRK",
        badgeColorHex = "#0EA5E9"
    )

    val HERBAL_SALVE = ItemDefinition(
        id = "herbal_salve",
        name = "Herbal Salve",
        category = ItemCategory.CONSUMABLE,
        stackLimit = 20,
        description = "Medicinal ointment that soothes wounds and accelerates physical recovery.",
        sourceDescription = "Crafted at Crafting Table (2 Plants, 2 Fibers).",
        healthRestore = 40f,
        badgeSymbol = "MED",
        badgeColorHex = "#22C55E"
    )

    val ESSENCE_TONIC = ItemDefinition(
        id = "mana_essence_tonic",
        name = "Essence Tonic",
        category = ItemCategory.CONSUMABLE,
        stackLimit = 20,
        description = "Alchemical elixir that invigorates the spirit, restoring energy and health.",
        sourceDescription = "Synthesized at Crafting Table (2 Monster Essence, 1 Magic Crystals).",
        healthRestore = 25f,
        energyRestore = 50f,
        badgeSymbol = "TNC",
        badgeColorHex = "#A855F7"
    )

    val MP_POTION = ItemDefinition(
        id = "mp_potion",
        name = "MP Potion",
        category = ItemCategory.MAGIC,
        stackLimit = 20,
        description = "Luminous azure mana potion distilled from Blue Flower Monster nectar.",
        sourceDescription = "Dropped by defeated Blue Flower Monsters in the wild.",
        badgeSymbol = "MPP",
        badgeColorHex = "#3B82F6"
    )

    val HEALTH_POTION = ItemDefinition(
        id = "health_potion",
        name = "Health Potion",
        category = ItemCategory.MAGIC,
        stackLimit = 20,
        description = "Vibrant crimson alchemical potion distilled from Red Flower Monster nectar.",
        sourceDescription = "Dropped by defeated Red Flower Monsters in the wild.",
        badgeSymbol = "HPP",
        badgeColorHex = "#EF4444"
    )

    // G. Structures
    val STORAGE_CHEST = ItemDefinition(
        id = "storage_chest",
        name = "Storage Chest",
        category = ItemCategory.STRUCTURE,
        stackLimit = 5,
        description = "Sturdy iron-banded wooden chest with 20 storage slots.",
        sourceDescription = "Crafted at Crafting Table (8 Wood, 2 Iron Ore).",
        badgeSymbol = "CST",
        badgeColorHex = "#D97706"
    )

    val CRAFTING_TABLE_ITEM = ItemDefinition(
        id = "crafting_table",
        name = "Crafting Table",
        category = ItemCategory.STRUCTURE,
        stackLimit = 5,
        description = "Heavy oak workbench with tools for fashioning advanced gear and items.",
        sourceDescription = "Crafted with 6 Wood.",
        badgeSymbol = "WKB",
        badgeColorHex = "#B45309"
    )

    val CAMPFIRE = ItemDefinition(
        id = "campfire",
        name = "Campfire",
        category = ItemCategory.STRUCTURE,
        stackLimit = 5,
        description = "Provides warm light and cooking capabilities in the wild.",
        sourceDescription = "Crafted with 3 Wood, 2 Coal.",
        badgeSymbol = "FIR",
        badgeColorHex = "#EA580C"
    )

    private val allItems = listOf(
        WOOD, HARDWOOD, BRANCHES, ROOTS,
        STONE, COAL, COPPER_ORE, IRON_ORE, RARE_ORE,
        PLANTS, MUSHROOMS, BERRIES, FIBERS, ANIMAL_HIDES,
        MONSTER_ESSENCE, MAGIC_CRYSTALS, ANCIENT_RELICS, BOSS_MATERIALS,
        GREEN_CRYSTAL,
        STONE_AXE, IRON_AXE, STONE_PICKAXE, IRON_PICKAXE, SHOVEL, SICKLE, KNIFE, BUILDERS_HAMMER,
        ROASTED_MUSHROOMS, BERRY_BREW, HERBAL_SALVE, ESSENCE_TONIC, MP_POTION, HEALTH_POTION,
        STORAGE_CHEST, CRAFTING_TABLE_ITEM, CAMPFIRE
    )

    val ALL_ITEMS: List<ItemDefinition> = allItems
    val ALL_TOOLS: List<ItemDefinition> = allItems.filter { it.category == ItemCategory.TOOL && it.toolType != ToolType.HAMMER }
    val ALL_TOOLS_WITH_HAMMER: List<ItemDefinition> = allItems.filter { it.category == ItemCategory.TOOL }
    val ALL_HOLDABLE_ITEMS: List<ItemDefinition> = allItems.filter { it.category != ItemCategory.STRUCTURE }
    val ALL_RESOURCES: List<ItemDefinition> = listOf(
        WOOD, HARDWOOD, BRANCHES, ROOTS,
        STONE, COAL, COPPER_ORE, IRON_ORE, RARE_ORE,
        PLANTS, MUSHROOMS, BERRIES, FIBERS, ANIMAL_HIDES,
        MONSTER_ESSENCE, MAGIC_CRYSTALS, ANCIENT_RELICS, BOSS_MATERIALS
    )

    private val itemMap = allItems.associateBy { it.id }

    fun get(id: String): ItemDefinition? = itemMap[id]

    fun getAll(): List<ItemDefinition> = allItems

    fun isHoldableInHand(id: String): Boolean {
        val def = itemMap[id] ?: return false
        return def.category != ItemCategory.STRUCTURE
    }
}
