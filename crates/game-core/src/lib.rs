//! Platform-independent voxel-world primitives for RustCraft.
//!
//! This crate intentionally contains no Minecraft-owned code or assets.
//! The initial terrain generator is a clean-room prototype, not a complete
//! implementation of Java Edition world generation.

use std::cmp::{max, min};

pub const CHUNK_SIZE: usize = 16;
pub const WORLD_MIN_Y: i32 = -64;
pub const WORLD_HEIGHT: usize = 384;
pub const SEA_LEVEL: i32 = 63;

#[repr(u16)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum BlockId {
    #[default]
    Air = 0,
    Grass = 1,
    Dirt = 2,
    Stone = 3,
    Water = 4,
    Sand = 5,
    Bedrock = 6,
}

impl BlockId {
    pub fn from_raw(value: u16) -> Self {
        match value {
            1 => Self::Grass,
            2 => Self::Dirt,
            3 => Self::Stone,
            4 => Self::Water,
            5 => Self::Sand,
            6 => Self::Bedrock,
            _ => Self::Air,
        }
    }
}

#[derive(Clone, Debug)]
pub struct Chunk {
    /// Blocks are stored in Y-major order: ((y * Z) + z) * X + x.
    blocks: Vec<BlockId>,
    pub chunk_x: i32,
    pub chunk_z: i32,
}

impl Chunk {
    pub fn new_empty(chunk_x: i32, chunk_z: i32) -> Self {
        Self {
            blocks: vec![BlockId::Air; WORLD_HEIGHT * CHUNK_SIZE * CHUNK_SIZE],
            chunk_x,
            chunk_z,
        }
    }

    fn index(x: usize, y: usize, z: usize) -> Option<usize> {
        if x >= CHUNK_SIZE || z >= CHUNK_SIZE || y >= WORLD_HEIGHT {
            return None;
        }
        Some((y * CHUNK_SIZE + z) * CHUNK_SIZE + x)
    }

    pub fn get(&self, x: usize, y: i32, z: usize) -> BlockId {
        let local_y = y - WORLD_MIN_Y;
        if local_y < 0 {
            return BlockId::Air;
        }
        Self::index(x, local_y as usize, z)
            .map(|i| self.blocks[i])
            .unwrap_or(BlockId::Air)
    }

    pub fn set(&mut self, x: usize, y: i32, z: usize, block: BlockId) -> bool {
        let local_y = y - WORLD_MIN_Y;
        if local_y < 0 {
            return false;
        }
        let Some(index) = Self::index(x, local_y as usize, z) else {
            return false;
        };
        self.blocks[index] = block;
        true
    }

    pub fn blocks(&self) -> &[BlockId] {
        &self.blocks
    }
}

/// Deterministic chunk generator used by the first playable prototype.
pub fn generate_chunk(seed: u64, chunk_x: i32, chunk_z: i32) -> Chunk {
    let mut chunk = Chunk::new_empty(chunk_x, chunk_z);

    for z in 0..CHUNK_SIZE {
        for x in 0..CHUNK_SIZE {
            let world_x = chunk_x as i64 * CHUNK_SIZE as i64 + x as i64;
            let world_z = chunk_z as i64 * CHUNK_SIZE as i64 + z as i64;
            let surface_y = terrain_height(seed, world_x, world_z);
            let beach = surface_y <= SEA_LEVEL + 2;

            for y in WORLD_MIN_Y..=surface_y {
                let block = if y == WORLD_MIN_Y {
                    BlockId::Bedrock
                } else if y == surface_y {
                    if beach { BlockId::Sand } else { BlockId::Grass }
                } else if y >= surface_y - 3 {
                    if beach { BlockId::Sand } else { BlockId::Dirt }
                } else {
                    BlockId::Stone
                };
                let _ = chunk.set(x, y, z, block);
            }

            if surface_y < SEA_LEVEL {
                for y in (surface_y + 1)..=SEA_LEVEL {
                    let _ = chunk.set(x, y, z, BlockId::Water);
                }
            }
        }
    }

    chunk
}

/// Returns the visible top block of a square area, row-major (z * width + x).
/// Values are BlockId discriminants and can be rendered without Minecraft assets.
pub fn generate_surface_map(width: usize, depth: usize, seed: u64) -> Vec<u32> {
    let width = min(max(width, 1), 128);
    let depth = min(max(depth, 1), 128);
    let mut map = Vec::with_capacity(width * depth);
    let half_w = width as i64 / 2;
    let half_d = depth as i64 / 2;

    for z in 0..depth {
        for x in 0..width {
            let wx = x as i64 - half_w;
            let wz = z as i64 - half_d;
            let height = terrain_height(seed, wx, wz);
            let top = if height < SEA_LEVEL {
                BlockId::Water
            } else if height <= SEA_LEVEL + 2 {
                BlockId::Sand
            } else {
                BlockId::Grass
            };
            map.push(top as u32);
        }
    }

    map
}

fn terrain_height(seed: u64, x: i64, z: i64) -> i32 {
    let broad = value_noise(seed ^ 0xA076_1D64_78BD_642F, x, z, 40) - 0.5;
    let detail = value_noise(seed ^ 0xE703_7ED1_A0B4_28DB, x, z, 13) - 0.5;
    (62.0 + broad * 27.0 + detail * 8.0).round() as i32
}

fn value_noise(seed: u64, x: i64, z: i64, scale: i64) -> f64 {
    let cell_x = x.div_euclid(scale);
    let cell_z = z.div_euclid(scale);
    let tx = smoothstep(x.rem_euclid(scale) as f64 / scale as f64);
    let tz = smoothstep(z.rem_euclid(scale) as f64 / scale as f64);

    let a = lattice(seed, cell_x, cell_z);
    let b = lattice(seed, cell_x + 1, cell_z);
    let c = lattice(seed, cell_x, cell_z + 1);
    let d = lattice(seed, cell_x + 1, cell_z + 1);
    let top = lerp(a, b, tx);
    let bottom = lerp(c, d, tx);
    lerp(top, bottom, tz)
}

fn smoothstep(t: f64) -> f64 {
    t * t * (3.0 - 2.0 * t)
}

fn lerp(a: f64, b: f64, t: f64) -> f64 {
    a + (b - a) * t
}

fn lattice(seed: u64, x: i64, z: i64) -> f64 {
    let mut value = seed
        ^ (x as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15)
        ^ (z as u64).wrapping_mul(0xD1B5_4A32_D192_ED03);
    value = value.wrapping_add(0x9E37_79B9_7F4A_7C15);
    value = (value ^ (value >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    value = (value ^ (value >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    value ^= value >> 31;
    (value as f64) / (u64::MAX as f64)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn surface_map_has_requested_dimensions() {
        assert_eq!(generate_surface_map(8, 4, 42).len(), 32);
    }

    #[test]
    fn surface_map_is_deterministic() {
        assert_eq!(generate_surface_map(32, 32, 7), generate_surface_map(32, 32, 7));
        assert_ne!(generate_surface_map(32, 32, 7), generate_surface_map(32, 32, 8));
    }

    #[test]
    fn chunk_has_bedrock_and_surface() {
        let chunk = generate_chunk(1234, 0, 0);
        for z in 0..CHUNK_SIZE {
            for x in 0..CHUNK_SIZE {
                assert_eq!(chunk.get(x, WORLD_MIN_Y, z), BlockId::Bedrock);
                assert_ne!(chunk.get(x, 100, z), BlockId::Bedrock);
            }
        }
    }

    #[test]
    fn out_of_range_reads_are_air() {
        let chunk = Chunk::new_empty(0, 0);
        assert_eq!(chunk.get(30, 0, 0), BlockId::Air);
        assert_eq!(chunk.get(0, 500, 0), BlockId::Air);
    }
}
