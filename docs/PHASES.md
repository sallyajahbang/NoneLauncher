# Implementation phases

## Phase 0 — Foundation

- [x] Cargo package and Rust 2024 configuration
- [x] Shared desktop/Android app entry architecture
- [x] Android NativeActivity manifest and ARM64 Gradle integration
- [x] CI jobs for Linux, Windows and Android ARM64
- [x] UI shell and runtime diagnostics

## Phase 1 — UI and GPU application foundation

- [x] egui screens and navigation
- [x] wgpu-backed UI renderer through eframe/egui-wgpu
- [x] Resizable desktop viewport and Android fullscreen viewport
- [x] Isometric voxel layout preview
- [ ] Dedicated WGSL 3D cube pipeline
- [ ] Depth-tested camera and actual GPU vertex/index buffers
- [ ] GPU adapter/device diagnostics

## Phase 2 — Voxel renderer

- [ ] 16×16×16 chunk-section representation
- [ ] Block registry and block states
- [ ] Face culling and greedy meshing
- [ ] Chunk visibility and frustum culling
- [ ] GPU buffer pooling and asynchronous uploads

## Phase 3 — Singleplayer world

- [ ] World metadata and seed handling
- [ ] Terrain/biome generation
- [ ] Chunk streaming and persistence
- [ ] Player movement and collision
- [ ] Block breaking/placing

## Phase 4 — Java Edition compatibility

- [ ] NBT parser and tests
- [ ] Anvil region reader/writer
- [ ] 1.21.11 data-version adapter
- [ ] Block/item/entity mapping
- [ ] Resource-pack importer

## Phase 5+ — Gameplay and multiplayer

- [ ] Inventory, crafting, entities, lighting and audio
- [ ] Java protocol adapter and server interoperability
- [ ] Performance, memory and device compatibility validation
