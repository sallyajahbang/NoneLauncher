# Development phases

RustCraft development is Android ARM64 only. Initial gameplay scope is singleplayer.

1. Android shell, system bar handling, and responsive phone UI.
2. Dedicated wgpu renderer: camera, depth buffer, WGSL shaders, and GPU mesh buffers.
3. Voxel chunk representation and greedy meshing.
4. Singleplayer world generation, player movement, collision, and block interaction.
5. NBT / Anvil reading and Java Edition compatibility adapters for 1.21.11.
6. Save/load, inventory, crafting, entities, and further gameplay systems.
7. Android profiling, thermal/memory optimization, and release packaging.

Desktop targets and multiplayer are explicitly out of scope for the current development cycle.
