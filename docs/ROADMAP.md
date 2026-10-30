# Implementation roadmap

## Phase 0 — Foundation (included)

- Android-only Gradle project
- Rust workspace and tests
- JNI bridge and ARM64 CI build
- Basic chunk storage and procedural terrain prototype
- Compose menu and top-down world preview

## Phase 1 — Native 3D rendering

- Select and validate a renderer backend on real ARM64 devices (Vulkan first, GLES fallback if needed)
- Add Android Surface lifecycle and native render thread
- Generate greedy-meshed chunk geometry in Rust
- Add camera matrices, first-person look and mobile touch layout
- Add frame-time, memory and thermal diagnostics

## Phase 2 — World streaming and storage

- Chunk coordinate scheduler and bounded worker pool
- Region/chunk persistence with crash-safe writes
- World list, create/delete/rename and backup/restore
- Culling, render distance and LOD strategy

## Phase 3 — Gameplay systems

- Player collision, gravity, swimming and movement modes
- Raycast selection, break/place interactions and hotbar
- Inventory and crafting data model
- Basic lighting, particles and audio abstraction

## Phase 4 — Data-driven compatibility

- Versioned block/item/entity registries
- Parse user-supplied resource packs and data packs
- Block states, models, atlases, animations and localization
- Data-driven recipes, loot, tags and world features

## Phase 5 — Java Edition 1.21.11 compatibility

- Build a versioned compatibility test matrix
- Compare world serialization and block state behavior
- Implement gameplay features incrementally
- Test performance and correctness on a range of Android ARM64 devices

## Acceptance rule

Do not mark a subsystem compatible until it has automated tests or a reproducible in-game test case. Do not describe the early terrain preview as Minecraft Java gameplay.
