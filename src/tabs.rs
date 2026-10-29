//! Manajemen tab + riwayat, dengan anggaran memori.
//!
//! Strategi hemat RAM:
//! - Hanya `MAX_LOADED_TABS` tab yang boleh menyimpan isi halaman (teks).
//!   Tab lain di-"unload": URL & judul tetap ada, isi dibuang, dimuat ulang saat dibuka.
//! - Pembuangan memakai LRU (tab yang paling lama tidak dipakai dibuang dulu).
//! - Riwayat per tab dibatasi `MAX_HISTORY` entri berupa URL saja (tanpa isi halaman).

use crate::page::Page;

/// Jumlah maksimum tab yang boleh menyimpan isi halaman di RAM sekaligus.
pub const MAX_LOADED_TABS: usize = 3;
/// Jumlah maksimum entri riwayat (URL) per tab.
pub const MAX_HISTORY: usize = 30;

pub enum Content {
    /// Tab kosong (halaman beranda).
    Blank,
    /// Sedang diunduh di thread latar belakang.
    Loading,
    /// Isi halaman sudah diparse dan siap ditampilkan.
    Loaded(Page),
    /// Isi dibuang untuk menghemat RAM; URL masih tersimpan.
    Unloaded,
    /// Gagal memuat.
    Error(String),
}

pub struct Tab {
    pub id: u64,
    pub title: String,
    /// Riwayat URL (hanya string, tanpa isi halaman).
    pub history: Vec<String>,
    pub cursor: usize,
    pub content: Content,
    /// Penanda waktu logis untuk LRU.
    pub last_used: u64,
}

impl Tab {
    pub fn url(&self) -> Option<&str> {
        self.history.get(self.cursor).map(String::as_str)
    }

    pub fn can_back(&self) -> bool {
        self.cursor > 0
    }

    pub fn can_forward(&self) -> bool {
        self.cursor + 1 < self.history.len()
    }

    pub fn is_loaded(&self) -> bool {
        matches!(self.content, Content::Loaded(_))
    }
}

pub struct TabManager {
    pub tabs: Vec<Tab>,
    pub active: usize,
    next_id: u64,
    clock: u64,
}

impl TabManager {
    pub fn new() -> Self {
        let mut m = Self {
            tabs: Vec::new(),
            active: 0,
            next_id: 1,
            clock: 0,
        };
        m.open_blank();
        m
    }

    /// Membuka tab kosong dan menjadikannya aktif.
    pub fn open_blank(&mut self) -> usize {
        let id = self.next_id;
        self.next_id += 1;
        self.clock += 1;
        self.tabs.push(Tab {
            id,
            title: "Tab baru".to_string(),
            history: Vec::new(),
            cursor: 0,
            content: Content::Blank,
            last_used: self.clock,
        });
        self.active = self.tabs.len() - 1;
        self.active
    }

    pub fn close(&mut self, idx: usize) {
        if idx >= self.tabs.len() {
            return;
        }
        self.tabs.remove(idx); // isi halaman langsung ikut terbuang
        if self.tabs.is_empty() {
            self.open_blank();
            return;
        }
        if idx < self.active {
            self.active -= 1;
        }
        if self.active >= self.tabs.len() {
            self.active = self.tabs.len() - 1;
        }
    }

    /// Mengaktifkan tab dan menegakkan anggaran memori.
    pub fn activate(&mut self, idx: usize) {
        if idx >= self.tabs.len() {
            return;
        }
        self.active = idx;
        self.clock += 1;
        self.tabs[idx].last_used = self.clock;
        self.enforce_budget();
    }

    /// Membuang isi tab (LRU) sampai jumlah tab ter-load <= `MAX_LOADED_TABS`.
    /// Tab aktif tidak pernah dibuang.
    pub fn enforce_budget(&mut self) {
        loop {
            let loaded = self.tabs.iter().filter(|t| t.is_loaded()).count();
            if loaded <= MAX_LOADED_TABS {
                break;
            }
            let victim = self
                .tabs
                .iter()
                .enumerate()
                .filter(|(i, t)| *i != self.active && t.is_loaded())
                .min_by_key(|(_, t)| t.last_used)
                .map(|(i, _)| i);
            match victim {
                Some(v) => self.tabs[v].content = Content::Unloaded,
                None => break,
            }
        }
    }

    /// Menambah navigasi baru pada tab (membuang "forward" lama).
    pub fn navigate(&mut self, idx: usize, url: String) {
        let Some(tab) = self.tabs.get_mut(idx) else {
            return;
        };
        tab.history.truncate(tab.cursor + 1);
        tab.history.push(url);
        if tab.history.len() > MAX_HISTORY {
            let extra = tab.history.len() - MAX_HISTORY;
            tab.history.drain(0..extra);
        }
        tab.cursor = tab.history.len() - 1;
        tab.content = Content::Loading;
    }

    /// Bergerak di riwayat: `delta` = -1 (kembali) atau +1 (maju).
    /// Mengembalikan `true` jika berpindah halaman (perlu dimuat ulang).
    pub fn step(&mut self, idx: usize, delta: isize) -> bool {
        let Some(tab) = self.tabs.get_mut(idx) else {
            return false;
        };
        let next = tab.cursor as isize + delta;
        if next < 0 || next as usize >= tab.history.len() {
            return false;
        }
        tab.cursor = next as usize;
        tab.content = Content::Loading;
        true
    }

    /// Menandai tab untuk dimuat ulang (tanpa mengubah riwayat).
    pub fn mark_loading(&mut self, idx: usize) {
        if let Some(tab) = self.tabs.get_mut(idx) {
            tab.content = Content::Loading;
        }
    }

    pub fn loaded_count(&self) -> usize {
        self.tabs.iter().filter(|t| t.is_loaded()).count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fake_page() -> Page {
        Page {
            url: String::new(),
            title: String::new(),
            lines: Vec::new(),
            links: Vec::new(),
        }
    }

    fn load(m: &mut TabManager, idx: usize) {
        m.tabs[idx].content = Content::Loaded(fake_page());
    }

    #[test]
    fn budget_unloads_least_recently_used() {
        let mut m = TabManager::new();
        for _ in 0..5 {
            m.open_blank();
        }
        for i in 0..m.tabs.len() {
            m.activate(i);
            m.navigate(i, format!("https://x/{i}"));
            load(&mut m, i);
            m.enforce_budget();
        }
        assert!(m.loaded_count() <= MAX_LOADED_TABS);
        // tab aktif (terakhir) tetap termuat
        assert!(m.tabs[m.active].is_loaded());
    }

    #[test]
    fn history_is_capped() {
        let mut m = TabManager::new();
        for i in 0..(MAX_HISTORY + 10) {
            m.navigate(0, format!("https://x/{i}"));
        }
        assert_eq!(m.tabs[0].history.len(), MAX_HISTORY);
        assert_eq!(m.tabs[0].url(), Some(format!("https://x/{}", MAX_HISTORY + 9).as_str()));
    }

    #[test]
    fn back_forward_work() {
        let mut m = TabManager::new();
        m.navigate(0, "a".into());
        m.navigate(0, "b".into());
        assert!(m.step(0, -1));
        assert_eq!(m.tabs[0].url(), Some("a"));
        assert!(!m.step(0, -1));
        assert!(m.step(0, 1));
        assert_eq!(m.tabs[0].url(), Some("b"));
    }

    #[test]
    fn close_keeps_valid_active() {
        let mut m = TabManager::new();
        m.open_blank();
        m.open_blank();
        m.close(2);
        assert!(m.active < m.tabs.len());
        m.close(0);
        m.close(0);
        assert_eq!(m.tabs.len(), 1);
    }
}
