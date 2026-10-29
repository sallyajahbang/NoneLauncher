//! Parsing HTML -> data ringan (judul, baris teks, tautan).
//!
//! DOM hasil parsing TIDAK disimpan. Hanya hasil ekstraksi yang dipertahankan,
//! lalu DOM langsung di-drop. Ini penting untuk RAM di Android.

use scraper::{Html, Node, Selector};
use url::Url;

/// Batas karakter teks per halaman (mencegah halaman raksasa memakan RAM).
pub const MAX_TEXT_BYTES: usize = 300_000;
/// Batas jumlah tautan yang disimpan per halaman.
pub const MAX_LINKS: usize = 300;
/// Lebar baris (karakter) sebelum dibungkus. Dibuat kecil agar muat di layar HP.
pub const WRAP_WIDTH: usize = 40;

/// Elemen yang isinya bukan teks tampil.
const SKIP: &[&str] = &[
    "script", "style", "noscript", "template", "svg", "head", "iframe", "canvas", "object",
    "video", "audio", "select", "button",
];

/// Elemen yang memulai baris baru.
const BLOCK: &[&str] = &[
    "p", "div", "br", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "section", "article",
    "header", "footer", "blockquote", "pre", "ul", "ol", "table", "hr", "nav", "main",
];

#[derive(Clone)]
pub struct Link {
    pub text: String,
    pub href: String,
}

pub struct Page {
    pub url: String,
    pub title: String,
    /// Baris yang sudah dibungkus, siap dirender dengan virtualisasi.
    pub lines: Vec<String>,
    pub links: Vec<Link>,
}

pub fn parse(base: &str, html: &str) -> Page {
    let doc = Html::parse_document(html);
    let base_url = Url::parse(base).ok();

    let title = Selector::parse("title")
        .ok()
        .and_then(|sel| doc.select(&sel).next())
        .map(|t| collapse(&t.text().collect::<String>()))
        .filter(|t| !t.is_empty())
        .unwrap_or_else(|| base.to_string());

    // 1) Ekstraksi teks dengan menelusuri pohon node.
    let mut text = String::new();
    for node in doc.tree.root().descendants() {
        if text.len() >= MAX_TEXT_BYTES {
            break;
        }
        match node.value() {
            Node::Text(t) => {
                let skipped = node
                    .ancestors()
                    .any(|a| matches!(a.value(), Node::Element(e) if SKIP.contains(&e.name())));
                if skipped {
                    continue;
                }
                let s: &str = t;
                let words: Vec<&str> = s.split_whitespace().collect();
                if words.is_empty() {
                    continue;
                }
                if !text.is_empty() && !text.ends_with(['\n', ' ']) {
                    text.push(' ');
                }
                text.push_str(&words.join(" "));
            }
            Node::Element(e) => {
                if BLOCK.contains(&e.name()) && !text.ends_with('\n') && !text.is_empty() {
                    text.push('\n');
                }
            }
            _ => {}
        }
    }

    // 2) Bungkus menjadi baris tetap (uniform) untuk virtualisasi.
    let mut lines: Vec<String> = Vec::new();
    for para in text.split('\n') {
        let para = para.trim();
        if para.is_empty() {
            continue;
        }
        wrap(para, WRAP_WIDTH, &mut lines);
        lines.push(String::new()); // jarak antar paragraf
    }
    drop(text);

    // 3) Tautan (href dijadikan absolut terhadap URL halaman).
    let mut links: Vec<Link> = Vec::new();
    if let Ok(sel) = Selector::parse("a[href]") {
        for a in doc.select(&sel) {
            if links.len() >= MAX_LINKS {
                break;
            }
            let Some(href) = a.value().attr("href") else {
                continue;
            };
            let Some(abs) = base_url
                .as_ref()
                .and_then(|b| b.join(href).ok())
                .map(|u| u.to_string())
            else {
                continue;
            };
            if !(abs.starts_with("http://") || abs.starts_with("https://")) {
                continue;
            }
            let label = collapse(&a.text().collect::<String>());
            let text = if label.is_empty() {
                truncate(&abs, 80)
            } else {
                truncate(&label, 80)
            };
            links.push(Link { text, href: abs });
        }
    }

    Page {
        url: base.to_string(),
        title,
        lines,
        links,
    }
}

/// Membungkus satu paragraf ke beberapa baris maksimal `width` karakter.
pub fn wrap(line: &str, width: usize, out: &mut Vec<String>) {
    let mut cur = String::new();
    let mut cur_len = 0usize;
    for word in line.split_whitespace() {
        let wlen = word.chars().count();
        if cur_len > 0 && cur_len + 1 + wlen > width {
            out.push(std::mem::take(&mut cur));
            cur_len = 0;
        }
        if cur_len > 0 {
            cur.push(' ');
            cur_len += 1;
        }
        cur.push_str(word);
        cur_len += wlen;
    }
    if !cur.is_empty() {
        out.push(cur);
    }
}

fn collapse(s: &str) -> String {
    s.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn truncate(s: &str, max_chars: usize) -> String {
    if s.chars().count() <= max_chars {
        s.to_string()
    } else {
        let mut out: String = s.chars().take(max_chars).collect();
        out.push('…');
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wrap_respects_width() {
        let mut out = Vec::new();
        wrap("satu dua tiga empat lima enam tujuh delapan", 12, &mut out);
        assert!(out.iter().all(|l| l.chars().count() <= 12));
        assert_eq!(out.join(" "), "satu dua tiga empat lima enam tujuh delapan");
    }

    #[test]
    fn extracts_text_title_and_links() {
        let html = r#"<html><head><title>Halo  Dunia</title><style>p{}</style></head>
            <body><script>var x=1;</script><h1>Judul</h1><p>Isi <a href="/about">tentang</a> kami.</p></body></html>"#;
        let page = parse("https://example.com/a/", html);
        assert_eq!(page.title, "Halo Dunia");
        let joined = page.lines.join("\n");
        assert!(joined.contains("Judul"));
        assert!(joined.contains("Isi tentang kami."));
        assert!(!joined.contains("var x"));
        assert!(!joined.contains("p{}"));
        assert_eq!(page.links.len(), 1);
        assert_eq!(page.links[0].href, "https://example.com/about");
    }
}
