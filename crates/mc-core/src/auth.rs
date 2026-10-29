//! Account handling. Only offline accounts are implemented for now.
//!
//! TODO: Microsoft/Xbox login (OAuth device-code flow -> XBL -> XSTS -> Minecraft
//! services token). The `Account` struct is the shape that flow should produce.

use md5::compute as md5_compute;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Account {
    pub username: String,
    pub uuid: String,
    pub access_token: String,
    pub user_type: String,
}

/// Name-based UUID used by offline (cracked) servers, as in vanilla
/// `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`.
pub fn offline_uuid(name: &str) -> String {
    let mut bytes = md5_compute(format!("OfflinePlayer:{name}").as_bytes()).0;
    bytes[6] = (bytes[6] & 0x0f) | 0x30; // version 3
    bytes[8] = (bytes[8] & 0x3f) | 0x80; // IETF variant
    let hex: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
    format!(
        "{}-{}-{}-{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..32]
    )
}

pub fn offline_account(name: &str) -> Account {
    Account {
        username: name.to_string(),
        uuid: offline_uuid(name),
        access_token: "0".to_string(),
        user_type: "legacy".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_uuid_matches_vanilla() {
        assert_eq!(
            offline_uuid("Notch"),
            "b50ad385-829d-3141-a216-7e7d7539ba7f"
        );
    }
}
