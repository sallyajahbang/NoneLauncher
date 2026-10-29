use serde::Deserialize;

/// Mojang's official version manifest (v2).
pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionManifest {
    pub latest: Latest,
    pub versions: Vec<VersionEntry>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(default)]
    pub sha1: String,
}

impl VersionManifest {
    pub fn parse(json: &str) -> crate::Result<Self> {
        Ok(serde_json::from_str(json)?)
    }

    pub fn find(&self, id: &str) -> Option<&VersionEntry> {
        self.versions.iter().find(|v| v.id == id)
    }

    pub fn latest_release(&self) -> Option<&VersionEntry> {
        self.find(&self.latest.release)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r#"{
        "latest": {"release": "1.21.1", "snapshot": "24w14a"},
        "versions": [
            {"id": "1.21.1", "type": "release", "url": "https://example/1.21.1.json", "sha1": "abc"},
            {"id": "24w14a", "type": "snapshot", "url": "https://example/24w14a.json", "sha1": "def"}
        ]
    }"#;

    #[test]
    fn parses_and_finds_latest_release() {
        let m = VersionManifest::parse(SAMPLE).unwrap();
        assert_eq!(m.latest_release().unwrap().id, "1.21.1");
        assert_eq!(m.find("24w14a").unwrap().kind, "snapshot");
        assert!(m.find("does-not-exist").is_none());
    }
}
