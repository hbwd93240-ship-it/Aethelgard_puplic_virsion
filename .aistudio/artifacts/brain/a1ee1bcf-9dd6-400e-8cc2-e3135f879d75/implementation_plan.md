# Complete Freeform Building System — Aethelgard Survival

Implements a comprehensive, modular freeform construction system integrated into the existing 3D survival game, allowing players to design and erect custom bases, towers, defenses, and furnished interiors using placeable architectural pieces with structural support, socket snapping, damage resistance, and complete save persistence.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following recommended architectural and gameplay choices have been adopted for the Freeform Building System:
> - **Build Mode UI**: A dedicated **BUILD** button is integrated into the top navigation row of the Survival HUD (alongside Inventory and Crafting). Tapping it opens a sleek, categorized building piece drawer while preserving full joystick movement and camera look.
> - **Snapping Architecture**: A hybrid connection engine that automatically snaps compatible sockets (floors to walls, roofs to pillars, doors to doorframes) within 1.2m proximity, backed by an optional 1.0m grid alignment and a manual **SNAP ON/OFF** toggle button in the placement HUD.
> - **Structural Support**: Realistic, simplified support checks: ground-connected floors/pillars/foundations provide load-bearing capacity; elevated floors and roofs must connect to valid supporting walls, pillars, or adjacent grounded pieces. Floating pieces are strictly rejected.
> - **Environmental Raids**: Restrained, atmospheric incursions triggered by nightfall, torch/lantern lighting, and loud harvesting/combat sounds near lairs. Monsters seek open entrances or attack blocking doors and walls without non-stop harassment.

---

## 1. Overview & Core Concept

- **What It Does**: Transitions Aethelgard from pure wilderness survival into shelter establishment and fortification. Players harvest wood, stone, brick, and iron to construct custom multi-room sanctuaries, watchtowers, bridges, palisades, and furnished interiors (beds, storage chests, workbenches, lanterns).
- **Target Audience & Context**: Mobile survival players seeking creative architectural expression, tactile shelter creation, and strategic defensive strongholds in a hostile twilight fantasy world.
- **Key Value**: Deep modular construction with zero blueprint restrictions—every wall, pillar, floor, door, and lantern is an individual piece governed by structural integrity, material tiers, and physical world interaction.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Entering Build Mode*: Tap the **BUILD** pill in the top HUD. The camera gently glides to a comfortable building vantage, and the building category drawer expands along the screen bottom.
  2. *Selecting & Previewing*: Tap a category (Floors, Walls, Roofs, Pillars, Doors, Furniture) and choose a piece (e.g., Wooden Wall). A 3D transparent Ghost Preview appears in the world aligned with the camera reticle.
  3. *Positioning & Snapping*: Drag the ghost across the ground or near existing structures. Magnetic sockets automatically snap pieces cleanly into place (e.g., wall clicks onto floor edge). A toggle button switches between Snap ON/OFF and adjusts rotation in 45°/90° increments.
  4. *Validation & Placement*: The ghost glows luminous teal/green when placement is valid and resources are available, or glowing crimson with an informative badge (e.g., "NEEDS GROUND SUPPORT" or "NEED 10 WOOD") when invalid. Tapping **PLACE** deducts resources, spawns the solid 3D piece with sound/particle feedback, and registers obstacle colliders.
  5. *Inspection & Renovation*: Equipping the **Builder's Hammer** highlights aimed building pieces, displaying health meters, upgrade paths (Wood → Stone), repair options, and a dismantle button with a 50% material refund.

- **Visual Identity & Atmosphere**:
  - *Piece Aesthetics*: Handcrafted procedural 3D models matching the twilight fantasy anime art style: interlocking hand-hewn wooden timber logs with iron brackets, chiseled runic stone slabs, decorative cross-beamed roof gables, and illuminated brass lantern sconces.
  - *Color Palette & Feedback*:
    - Luminous Ghost Valid: Emerald-Cyan Hologram (`#22D3EE` / `#34D399` with 0.55 alpha).
    - Luminous Ghost Invalid: Corrupted Crimson Glow (`#F87171` with 0.65 alpha).
    - Structural Stress Highlight: Amber Orange warning tint (`#FB923C`) on damaged pieces.
    - Wood Material: Rich timber brown (`#78350F` / `#A16207`).
    - Stone Material: Weathered slate granite (`#475569` / `#64748B`).
    - Iron/Metal: Polished rune steel (`#94A3B8`).

---

## 3. Key Product Decisions & Trade-Offs

- **Modular Components over Predefined Blueprints**:
  - *Decision*: Provide atomic components (floors, walls, pillars, roofs, stairs, doors) rather than static pre-assembled houses.
  - *Why*: Gives players total freedom to build custom fortresses, towers, and shelters fitted to the clearing's natural terrain.
  - *Alternative Considered*: Pre-fabricated buildings like "Small Cabin". Rejected because it lacks creative freedom and scalability.

- **Adjustable Pillars & Fixed Wall Heights**:
  - *Decision*: Walls maintain a uniform height of 3.2m (preventing awkward ceiling alignments), while pillars feature a discrete height adjustment control (+0.5m / -0.5m increments up to 6.4m).
  - *Why*: Accommodates uneven hillside terrain and elevated stilts without stacking messy multiple wall pieces.

- **Hybrid Socket Snapping Engine**:
  - *Decision*: Combine discrete geometric connection sockets (e.g. `FLOOR_EDGE_N`, `WALL_TOP`, `DOOR_FRAME`) with continuous free placement when snapping is toggled off.
  - *Why*: Ensures clean, watertight alignment without gaps on mobile touchscreens while still allowing free placement of furniture and decorative pieces.

- **Builder's Hammer Interaction Model**:
  - *Decision*: Integrate a dedicated `Builder's Hammer` tool into `ItemDefinition` / `ResourceCatalog`. When held in third or first person, the contextual interact button adapts to show "REPAIR", "UPGRADE TO STONE", or "DISMANTLE".
  - *Why*: Reuses the existing tool, inventory, and contextual HUD architecture without cluttering screen controls with redundant permanent buttons.

---

## 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────────┐
│                             MainActivity                                 │
│                                  │                                       │
│                         AethelgardGame (LibGDX)                          │
│        ┌─────────────────────────┴───────────────────────────────┐       │
│        ▼                                                         ▼       │
│  TouchInputController / HUD                            BuildingManager   │
│  ┌─────────────────────────────┐                       ┌───────────────┐ │
│  │ - BUILD HUD Button          │                       │ - GhostPreview│ │
│  │ - Piece Category Drawer     │◄─────────────────────►│ - SocketSnap  │ │
│  │ - Snap / Rotate / Place     │                       │ - SupportCheck│ │
│  │ - Builder's Hammer Modes    │                       │ - DeductCost  │ │
│  └─────────────────────────────┘                       └───────┬───────┘ │
│                                                                │         │
│        ┌───────────────────────────────────────────────────────┴─┐       │
│        ▼                                                         ▼       │
│  PlacedBuildingPiece                                   BuildingRaidMgr   │
│  ┌─────────────────────────────┐                       ┌───────────────┐ │
│  │ - World transform & meshes  │◄─────────────────────►│ - Noise/Light │ │
│  │ - HP & damage state         │                       │ - Enemy AI    │ │
│  │ - Colliders in WorldManager │                       │ - Door Breaches││
│  │ - Functional state (Chests) │                       └───────────────┘ │
│  └──────────────┬──────────────┘                                         │
│                 │                                                        │
│                 ▼                                                        │
│        SurvivalSaveManager                                               │
│        ┌──────────────────────────────────────────────┐                  │
│        │ - Serializes placed buildings & health states│                  │
│        │ - Backward-compatible JSON/Pref persistence  │                  │
│        └──────────────────────────────────────────────┘                  │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component & System Specifications

1. **`BuildingPieceDefinition.kt`**:
   - `BuildingCategory`: `FLOORS`, `WALLS`, `ROOFS`, `PILLARS`, `FOUNDATIONS`, `DOORS`, `WINDOWS`, `STAIRS`, `FENCES`, `FURNITURE`, `TERRITORY`.
   - `BuildingMaterial`: `WOOD`, `STONE`, `BRICK`, `METAL`.
   - Structural socket definitions with 3D offset vectors and orientation normals.
   - Resource cost maps (e.g. `wood: 8`, `stone: 4`).

2. **`BuildingManager.kt`**:
   - Manages active build mode state, ghost preview instance, snap detection, and valid placement math.
   - Ground raycast / terrain height evaluation to ground foundations and ground-supported floors.
   - Performs structural validation: walks support tree ensuring elevated pieces trace down to ground or sturdy pillars.
   - Registers dynamic obstacle colliders with `WorldManager.generatedData.obstacleColliders` so the player, enemies, and projectiles physically collide with walls and fences.

3. **`BuilderHammerController.kt`**:
   - Handles inspection raycast against placed pieces.
   - Executes repairs (consuming materials proportional to damage).
   - Executes upgrades (e.g., Wood Wall → Stone Wall, validating dimensions).
   - Dismantles pieces, returning 50% of original crafting ingredients to the player's inventory.

4. **`BuildingRaidManager.kt`**:
   - Integrates with `EnemyController` and `WorldManager`.
   - Computes local base "heat" based on placed torches/fireplaces, active crafting/mining sounds, and nightfall.
   - Alerts nearby Guardian Knights or wild beasts to investigate base perimeters, target obstructing doors, or breach barricades.

5. **`SurvivalSaveManager.kt` Integration**:
   - Stores building piece instances into `placed_building_pieces` pref key with schema versioning.
   - Preserves all existing inventory, chests, stats, and world node destruction.
