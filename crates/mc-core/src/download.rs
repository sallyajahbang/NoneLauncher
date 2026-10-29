//! Network and disk operations: fetch metadata, plan downloads, verify SHA-1.

use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};

use crate::launch::LaunchConfig;
use crate::manifest::{VersionEntry, VersionManifest};
use crate::version::{allowed_libraries, artifact_path, VersionJson};
use crate::{Error, Result};

/// Fallback base for libraries that do not list an explicit URL.
pub const LIBRARY_BASE_URL: &str = "https://libraries.minecraft.net/";
/// Base URL for asset objects, addressed by their SHA-1 hash.
pub const ASSET_BASE_URL: &str = "https://resources.download.minecraft.net/";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DownloadTask {
    pub url: String,
    pub dest: PathBuf,
    pub sha1: String,
    pub size: u64,
}

/// `assets/indexes/<id>.json`
#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    #[serde(default)]
    pub size: u64,
}

pub fn fetch_text(url: &str) -> Result<String> {
    let resp = ureq::get(url)
        .call()
        .map_err(|e| Error::Http(format!("{url}: {e}")))?;
    resp.into_string().map_err(Error::Io)
}

pub fn fetch_manifest(url: &str) -> Result<VersionManifest> {
    VersionManifest::parse(&fetch_text(url)?)
}

pub fn fetch_version(entry: &VersionEntry) -> Result<VersionJson> {
    Ok(serde_json::from_str(&fetch_text(&entry.url)?)?)
}

/// Every file needed to launch this version on the configured platform.
/// Asset objects are planned separately with `plan_assets`.
pub fn plan(version: &VersionJson, cfg: &LaunchConfig) -> Vec<DownloadTask> {
    let ctx = cfg.effective_context();
    let mut tasks = Vec::new();

    for lib in allowed_libraries(version, &ctx) {
        let Some(path) = artifact_path(lib) else {
            continue;
        };
        let artifact = lib.downloads.as_ref().and_then(|d| d.artifact.as_ref());
        let url = artifact
            .map(|a| a.url.clone())
            .unwrap_or_else(|| format!("{LIBRARY_BASE_URL}{path}"));
        let (sha1, size) = artifact
            .map(|a| (a.sha1.clone(), a.size))
            .unwrap_or_default();
        tasks.push(DownloadTask {
            url,
            dest: cfg.libraries_dir.join(&path),
            sha1,
            size,
        });
    }

    if let Some(client) = version.downloads.as_ref().and_then(|d| d.client.as_ref()) {
        tasks.push(DownloadTask {
            url: client.url.clone(),
            dest: cfg.client_jar.clone(),
            sha1: client.sha1.clone(),
            size: client.size,
        });
    }

    tasks
}

/// Asset objects go to `<assets_dir>/objects/<first two hex chars>/<hash>`.
pub fn plan_assets(index: &AssetIndex, assets_dir: &Path) -> Vec<DownloadTask> {
    index
        .objects
        .values()
        .filter_map(|obj| {
            let prefix = obj.hash.get(..2)?;
            Some(DownloadTask {
                url: format!("{ASSET_BASE_URL}{prefix}/{}", obj.hash),
                dest: assets_dir.join("objects").join(prefix).join(&obj.hash),
                sha1: obj.hash.clone(),
                size: obj.size,
            })
        })
        .collect()
}

/// Downloads one file, skipping it if the existing copy already matches.
/// The file is written to a `.part` temp file and renamed on success.
pub fn download(task: &DownloadTask) -> Result<()> {
    if task.dest.exists() && !task.sha1.is_empty() && file_sha1(&task.dest)? == task.sha1 {
        return Ok(());
    }

    let resp = ureq::get(&task.url)
        .call()
        .map_err(|e| Error::Http(format!("{}: {e}", task.url)))?;
    let mut buf = Vec::with_capacity(task.size as usize);
    resp.into_reader().read_to_end(&mut buf)?;

    let actual = sha1_hex(&buf);
    if !task.sha1.is_empty() && actual != task.sha1 {
        return Err(Error::HashMismatch {
            path: task.dest.display().to_string(),
            expected: task.sha1.clone(),
            actual,
        });
    }

    if let Some(parent) = task.dest.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut tmp = task.dest.as_os_str().to_owned();
    tmp.push(".part");
    let tmp = PathBuf::from(tmp);
    fs::write(&tmp, &buf)?;
    fs::rename(&tmp, &task.dest)?;
    Ok(())
}

pub fn sha1_hex(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

fn file_sha1(path: &Path) -> Result<String> {
    Ok(sha1_hex(&fs::read(path)?))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha1_known_vector() {
        assert_eq!(sha1_hex(b"abc"), "a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    #[test]
    fn plans_assets_under_hash_prefix() {
        let index: AssetIndex = serde_json::from_str(
            r#"{"objects": {"minecraft/sounds/a.ogg": {"hash": "abcdef0123", "size": 3}}}"#,
        )
        .unwrap();
        let tasks = plan_assets(&index, Path::new("/data/assets"));
        assert_eq!(tasks.len(), 1);
        assert_eq!(
            tasks[0].url,
            "https://resources.download.minecraft.net/ab/abcdef0123"
        );
        assert!(tasks[0].dest.ends_with("objects/ab/abcdef0123"));
    }
}
