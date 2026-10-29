//! Pengunduhan halaman dengan batas memori dan waktu.
//!
//! - Ukuran unduhan dibatasi `MAX_BODY_BYTES` (sisanya dipotong, tidak dimuat ke RAM).
//! - Timeout koneksi & total agar thread tidak menggantung.
//! - Hanya menerima tipe konten teks.

use std::io::Read;
use std::time::Duration;

use crate::page::{self, Page};

/// Batas ukuran body mentah (4 MB). Lebih dari ini dipotong.
pub const MAX_BODY_BYTES: u64 = 4 * 1024 * 1024;

const USER_AGENT: &str = "Mozilla/5.0 (Linux; Android 10) RustRowser/0.1";

pub fn load(url: &str) -> Result<Page, String> {
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(10))
        .timeout(Duration::from_secs(25))
        .redirects(8)
        .user_agent(USER_AGENT)
        .build();

    let resp = agent.get(url).call().map_err(|e| e.to_string())?;
    let final_url = resp.get_url().to_string();
    let ctype = resp.content_type().to_ascii_lowercase();

    if !(ctype.is_empty() || ctype.contains("text/html") || ctype.contains("text/plain")) {
        return Err(format!("Tipe konten belum didukung: {ctype}"));
    }

    let mut body: Vec<u8> = Vec::with_capacity(64 * 1024);
    resp.into_reader()
        .take(MAX_BODY_BYTES)
        .read_to_end(&mut body)
        .map_err(|e| e.to_string())?;

    let html = String::from_utf8_lossy(&body).into_owned();
    drop(body); // buang byte mentah sebelum parsing agar puncak RAM lebih rendah

    Ok(page::parse(&final_url, &html))
}

/// Mengubah input bar alamat menjadi URL.
/// - Sudah ada skema http(s) -> dipakai apa adanya
/// - Mirip domain (ada titik, tanpa spasi) -> ditambah https://
/// - Selain itu -> pencarian DuckDuckGo
pub fn normalize(input: &str) -> Option<String> {
    let s = input.trim();
    if s.is_empty() {
        return None;
    }
    if s.starts_with("http://") || s.starts_with("https://") {
        return url::Url::parse(s).ok().map(|u| u.to_string());
    }
    if !s.contains(' ') && s.contains('.') {
        return url::Url::parse(&format!("https://{s}"))
            .ok()
            .map(|u| u.to_string());
    }
    let q: String = url::form_urlencoded::byte_serialize(s.as_bytes()).collect();
    Some(format!("https://html.duckduckgo.com/html/?q={q}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_cases() {
        assert_eq!(normalize("   ").as_deref(), None);
        assert_eq!(
            normalize("https://example.com/x").as_deref(),
            Some("https://example.com/x")
        );
        assert_eq!(
            normalize("example.com").as_deref(),
            Some("https://example.com/")
        );
        assert!(normalize("kucing lucu")
            .unwrap()
            .starts_with("https://html.duckduckgo.com/html/?q=kucing"));
    }
}
