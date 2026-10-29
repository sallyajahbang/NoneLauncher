//! Antarmuka utama RustRowser (egui).
//!
//! Prinsip:
//! - Fetch berjalan di thread terpisah -> UI tidak pernah macet.
//! - Teks dirender dengan virtualisasi (`show_rows`) -> hanya baris yang terlihat yang digambar.
//! - Isi tab dibatasi lewat `TabManager::enforce_budget`.

use std::sync::mpsc::{channel, Receiver, Sender};

use eframe::egui;

use crate::fetch;
use crate::page::Page;
use crate::tabs::{Content, TabManager, MAX_LOADED_TABS};

struct FetchResult {
    tab_id: u64,
    url: String,
    result: Result<Page, String>,
}

/// Aksi dari UI. Dikumpulkan dulu, diterapkan belakangan (menghindari konflik borrow).
enum Action {
    Select(usize),
    Close(usize),
    NewTab,
    Typed(String),
    Navigate(String),
    Back,
    Forward,
    Reload,
}

pub struct BrowserApp {
    tabs: TabManager,
    url_bar: String,
    tx: Sender<FetchResult>,
    rx: Receiver<FetchResult>,
}

impl BrowserApp {
    pub fn new() -> Self {
        let (tx, rx) = channel();
        Self {
            tabs: TabManager::new(),
            url_bar: String::new(),
            tx,
            rx,
        }
    }

    /// Menjalankan unduhan untuk URL aktif pada tab `idx`, di thread latar belakang.
    fn start_fetch(&mut self, ctx: &egui::Context, idx: usize) {
        let Some(tab) = self.tabs.tabs.get_mut(idx) else {
            return;
        };
        let Some(url) = tab.url().map(str::to_owned) else {
            return;
        };
        tab.content = Content::Loading;
        let tab_id = tab.id;

        let tx = self.tx.clone();
        let ctx = ctx.clone();
        std::thread::spawn(move || {
            let result = fetch::load(&url);
            let _ = tx.send(FetchResult { tab_id, url, result });
            ctx.request_repaint();
        });
    }

    /// Menerima hasil unduhan yang sudah selesai.
    fn poll_results(&mut self) {
        let mut changed = false;
        while let Ok(msg) = self.rx.try_recv() {
            let Some(tab) = self.tabs.tabs.iter_mut().find(|t| t.id == msg.tab_id) else {
                continue; // tab sudah ditutup
            };
            // Abaikan hasil yang sudah tidak relevan (user sudah pindah halaman)
            if tab.url() != Some(msg.url.as_str()) {
                continue;
            }
            match msg.result {
                Ok(page) => {
                    tab.title = if page.title.is_empty() {
                        page.url.clone()
                    } else {
                        page.title.clone()
                    };
                    tab.content = Content::Loaded(page);
                }
                Err(e) => tab.content = Content::Error(e),
            }
            changed = true;
        }
        if changed {
            self.tabs.enforce_budget();
        }
    }

    fn sync_url_bar(&mut self) {
        self.url_bar = self
            .tabs
            .tabs
            .get(self.tabs.active)
            .and_then(|t| t.url())
            .unwrap_or("")
            .to_string();
    }

    /// Memastikan tab aktif memiliki isi (memuat ulang jika sebelumnya di-unload).
    fn ensure_active_loaded(&mut self, ctx: &egui::Context) {
        let idx = self.tabs.active;
        let needs = matches!(
            self.tabs.tabs.get(idx).map(|t| &t.content),
            Some(Content::Unloaded)
        );
        if needs {
            self.start_fetch(ctx, idx);
        }
    }

    fn apply(&mut self, ctx: &egui::Context, action: Action) {
        match action {
            Action::Select(i) => {
                self.tabs.activate(i);
                self.sync_url_bar();
                self.ensure_active_loaded(ctx);
            }
            Action::Close(i) => {
                self.tabs.close(i);
                self.sync_url_bar();
                self.ensure_active_loaded(ctx);
            }
            Action::NewTab => {
                let idx = self.tabs.open_blank();
                self.tabs.enforce_budget();
                self.url_bar.clear();
                let _ = idx;
            }
            Action::Typed(input) => {
                if let Some(url) = fetch::normalize(&input) {
                    self.navigate_active(ctx, url);
                }
            }
            Action::Navigate(url) => self.navigate_active(ctx, url),
            Action::Back => {
                let idx = self.tabs.active;
                if self.tabs.step(idx, -1) {
                    self.sync_url_bar();
                    self.start_fetch(ctx, idx);
                }
            }
            Action::Forward => {
                let idx = self.tabs.active;
                if self.tabs.step(idx, 1) {
                    self.sync_url_bar();
                    self.start_fetch(ctx, idx);
                }
            }
            Action::Reload => {
                let idx = self.tabs.active;
                self.start_fetch(ctx, idx);
            }
        }
    }

    fn navigate_active(&mut self, ctx: &egui::Context, url: String) {
        let idx = self.tabs.active;
        self.tabs.navigate(idx, url);
        self.sync_url_bar();
        self.start_fetch(ctx, idx);
    }
}

impl eframe::App for BrowserApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.poll_results();
        let mut actions: Vec<Action> = Vec::new();

        // ---- Bar atas: alamat + daftar tab ----
        egui::TopBottomPanel::top("top").show(ctx, |ui| {
            ui.horizontal(|ui| {
                let edit = ui.add(
                    egui::TextEdit::singleline(&mut self.url_bar)
                        .hint_text("Cari atau ketik alamat")
                        .desired_width(f32::INFINITY),
                );
                let enter = edit.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
                if enter || ui.button("Go").clicked() {
                    actions.push(Action::Typed(self.url_bar.clone()));
                }
            });

            ui.horizontal(|ui| {
                egui::ScrollArea::horizontal()
                    .id_salt("tab_strip")
                    .show(ui, |ui| {
                        ui.horizontal(|ui| {
                            for (i, tab) in self.tabs.tabs.iter().enumerate() {
                                let selected = i == self.tabs.active;
                                let label = truncate_chars(&tab.title, 14);
                                if ui.selectable_label(selected, label).clicked() {
                                    actions.push(Action::Select(i));
                                }
                                if ui.small_button("✕").clicked() {
                                    actions.push(Action::Close(i));
                                }
                                ui.separator();
                            }
                        });
                    });
                if ui.button("+").clicked() {
                    actions.push(Action::NewTab);
                }
            });
        });

        // ---- Bar bawah: navigasi (mudah dijangkau jempol) + statistik memori ----
        egui::TopBottomPanel::bottom("bottom").show(ctx, |ui| {
            let idx = self.tabs.active;
            let (can_back, can_fwd) = self
                .tabs
                .tabs
                .get(idx)
                .map(|t| (t.can_back(), t.can_forward()))
                .unwrap_or((false, false));
            ui.horizontal(|ui| {
                if ui.add_enabled(can_back, egui::Button::new("◀")).clicked() {
                    actions.push(Action::Back);
                }
                if ui.add_enabled(can_fwd, egui::Button::new("▶")).clicked() {
                    actions.push(Action::Forward);
                }
                if ui.button("⟳").clicked() {
                    actions.push(Action::Reload);
                }
                ui.separator();
                ui.small(format!(
                    "Tab {} · dimuat {}/{}",
                    self.tabs.tabs.len(),
                    self.tabs.loaded_count(),
                    MAX_LOADED_TABS
                ));
            });
        });

        // ---- Area konten ----
        egui::CentralPanel::default().show(ctx, |ui| {
            let Some(tab) = self.tabs.tabs.get(self.tabs.active) else {
                return;
            };
            match &tab.content {
                Content::Blank => {
                    ui.vertical_centered(|ui| {
                        ui.add_space(80.0);
                        ui.heading("RustRowser");
                        ui.label("Ketik alamat atau kata kunci di atas.");
                    });
                }
                Content::Loading => {
                    ui.horizontal(|ui| {
                        ui.spinner();
                        ui.label("Memuat…");
                    });
                }
                Content::Unloaded => {
                    ui.label("Isi tab dilepas dari memori untuk menghemat RAM. Memuat ulang…");
                }
                Content::Error(e) => {
                    ui.colored_label(egui::Color32::LIGHT_RED, "Gagal memuat halaman");
                    ui.label(e.as_str());
                }
                Content::Loaded(page) => {
                    show_page(ui, page, &mut actions);
                }
            }
        });

        // ---- Terapkan aksi ----
        for action in actions {
            self.apply(ctx, action);
        }

        // Selama ada unduhan berjalan, minta repaint agar hasil cepat tampil.
        let busy = self
            .tabs
            .tabs
            .iter()
            .any(|t| matches!(t.content, Content::Loading));
        if busy {
            ctx.request_repaint_after(std::time::Duration::from_millis(120));
        }
    }
}

/// Menampilkan halaman: panel tautan (bisa dilipat) di atas, lalu teks ter-virtualisasi.
fn show_page(ui: &mut egui::Ui, page: &Page, actions: &mut Vec<Action>) {
    egui::CollapsingHeader::new(format!("Tautan ({})", page.links.len()))
        .id_salt("links")
        .default_open(false)
        .show(ui, |ui| {
            egui::ScrollArea::vertical()
                .id_salt("links_scroll")
                .max_height(220.0)
                .show(ui, |ui| {
                    for link in &page.links {
                        if ui.link(link.text.as_str()).clicked() {
                            actions.push(Action::Navigate(link.href.clone()));
                        }
                    }
                });
        });
    ui.separator();

    let row_h = ui.text_style_height(&egui::TextStyle::Body) + ui.spacing().item_spacing.y;
    egui::ScrollArea::vertical()
        .id_salt("page_text")
        .auto_shrink([false, false])
        .show_rows(ui, row_h, page.lines.len(), |ui, range| {
            for i in range {
                // `truncate` menjaga satu baris = satu tinggi (virtualisasi tetap akurat)
                ui.add(egui::Label::new(page.lines[i].as_str()).truncate());
            }
        });
}

fn truncate_chars(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        let mut out: String = s.chars().take(max).collect();
        out.push('…');
        out
    }
}
