# Architecture notes

## Platform boundary

The initial application uses eframe for a shared Rust UI and wgpu-backed rendering. Android uses winit's NativeActivity glue through eframe's `android-native-activity` feature; desktop uses eframe's native runner. Game logic must remain independent of platform APIs.

## Planned crate split

As Phase 1 stabilizes, split the single application crate into:

- `rc-core`: IDs, coordinates, errors, configuration.
- `rc-renderer`: wgpu device resources, camera, pipelines, mesh buffers.
- `rc-voxel`: block/state definitions and voxel mesh generation.
- `rc-world`: chunks, sections, world streaming.
- `rc-compat-java`: Java Edition version adapters, NBT and Anvil compatibility.
- `rc-client`: platform entry points, UI, input and game orchestration.

## Renderer ownership

The renderer owns GPU resources and consumes immutable render snapshots. The world owns authoritative chunk/block data. A world mutation marks affected chunk meshes dirty; worker jobs build replacement CPU meshes; the render thread uploads completed meshes.

## Compatibility boundary

Java Edition compatibility must be versioned. Do not scatter version-specific constants through gameplay code. Put data-version, protocol-version, block-state mapping, NBT layout and chunk decoding behind explicit adapters. The first adapter targets Java Edition 1.21.11, and should be validated against user-owned test worlds and protocol fixtures.
