//! Core logic for a Minecraft: Java Edition launcher.
//!
//! This crate is platform-independent. It knows how to read Mojang's version
//! metadata, decide which libraries apply to a platform, download and verify
//! files, and build the JVM command line. Actually starting the JVM and
//! rendering (GLFW/LWJGL bridge) lives in the Android native layer.

pub mod auth;
pub mod download;
pub mod launch;
pub mod manifest;
pub mod rules;
pub mod version;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("HTTP error: {0}")]
    Http(String),
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("SHA-1 mismatch for {path}: expected {expected}, got {actual}")]
    HashMismatch {
        path: String,
        expected: String,
        actual: String,
    },
    #[error("version not found: {0}")]
    VersionNotFound(String),
    #[error("{0}")]
    Other(String),
}

pub type Result<T> = std::result::Result<T, Error>;
