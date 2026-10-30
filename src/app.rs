use std::time::{Duration, Instant};

use eframe::egui::{self, Align, Color32, FontId, Layout, RichText, Stroke, Vec2};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Screen {
    Home,
    Worlds,
    Settings,
    About,
}

pub struct RustCraftApp {
    screen: Screen,
    show_debug: bool,
    render_distance: u32,
    frame_counter: u32,
    fps: f32,
    last_fps_update: Instant,
    started: Instant,
    status: String,
}

impl Default for RustCraftApp {
    fn default() -> Self {
        Self {
            screen: Screen::Home,
            show_debug: true,
            render_distance: 8,
            frame_counter: 0,
            fps: 0.0,
            last_fps_update: Instant::now(),
            started: Instant::now(),
            status: "Phase 0 + Phase 1 foundation initialized".to_owned(),
        }
    }
}

impl eframe::App for RustCraftApp {
    fn logic(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.update_fps();
        self.apply_theme(ctx);
        ctx.request_repaint_after(Duration::from_millis(16));
    }

    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        egui::Panel::top("top_bar")
            .exact_size(58.0)
            .show(ui, |ui| {
                ui.horizontal_centered(|ui| {
                    ui.add_space(10.0);
                    ui.label(
                        RichText::new("▦ RUSTCRAFT")
                            .size(22.0)
                            .strong()
                            .color(Color32::from_rgb(205, 235, 202)),
                    );
                    ui.add_space(18.0);
                    ui.label(
                        RichText::new("NATIVE VOXEL RUNTIME")
                            .size(10.0)
                            .color(Color32::from_rgb(135, 163, 142)),
                    );
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui
                            .small_button(if self.show_debug {
                                "Debug: ON"
                            } else {
                                "Debug: OFF"
                            })
                            .clicked()
                        {
                            self.show_debug = !self.show_debug;
                        }
                        ui.add_space(8.0);
                        ui.label(
                            RichText::new("v0.1.0 · JAVA COMPAT TARGET 1.21.11")
                                .size(10.0)
                                .color(Color32::GRAY),
                        );
                    });
                });
            });

        egui::Panel::bottom("status_bar")
            .exact_size(25.0)
            .show(ui, |ui| {
                ui.horizontal_centered(|ui| {
                    ui.label(RichText::new(&self.status).size(10.0).color(Color32::GRAY));
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.label(
                            RichText::new("ANDROID · WINDOWS · LINUX")
                                .size(9.0)
                                .color(Color32::GRAY),
                        );
                    });
                });
            });

        egui::Panel::left("navigation")
            .resizable(false)
            .exact_size(190.0)
            .show(ui, |ui| {
                ui.add_space(18.0);
                ui.label(
                    RichText::new("WORKSPACE")
                        .size(10.0)
                        .strong()
                        .color(Color32::GRAY),
                );
                ui.add_space(10.0);
                self.nav_button(ui, "⌂  Home", Screen::Home);
                self.nav_button(ui, "▤  Worlds", Screen::Worlds);
                self.nav_button(ui, "⚙  Settings", Screen::Settings);
                self.nav_button(ui, "ⓘ  About", Screen::About);
                ui.with_layout(Layout::bottom_up(Align::Min), |ui| {
                    ui.separator();
                    ui.label(
                        RichText::new("ENGINE STATUS")
                            .size(10.0)
                            .strong()
                            .color(Color32::GRAY),
                    );
                    ui.label(
                        RichText::new("● WGPU BACKEND")
                            .size(11.0)
                            .color(Color32::LIGHT_GREEN),
                    );
                    ui.label(
                        RichText::new("● EGUI UI LAYER")
                            .size(11.0)
                            .color(Color32::LIGHT_GREEN),
                    );
                    ui.label(
                        RichText::new("○ WORLD SYSTEM")
                            .size(11.0)
                            .color(Color32::GRAY),
                    );
                });
            });

        egui::CentralPanel::default().show(ui, |ui| {
            ui.add_space(14.0);
            match self.screen {
                Screen::Home => self.home_screen(ui),
                Screen::Worlds => self.worlds_screen(ui),
                Screen::Settings => self.settings_screen(ui),
                Screen::About => self.about_screen(ui),
            }
        });

        if self.show_debug {
            let ctx = ui.ctx().clone();
            egui::Window::new("Runtime diagnostics")
                .default_pos(egui::pos2(205.0, 72.0))
                .default_width(240.0)
                .resizable(false)
                .show(&ctx, |ui| {
                    ui.monospace(format!("FPS          {:>6.1}", self.fps));
                    ui.monospace(format!(
                        "Frame time   {:>6.2} ms",
                        1000.0 / self.fps.max(0.1)
                    ));
                    ui.monospace(format!(
                        "Uptime       {:>6.1} s",
                        self.started.elapsed().as_secs_f32()
                    ));
                    ui.monospace(format!("Render dist. {:>6} chunks", self.render_distance));
                    ui.monospace("GPU          wgpu / eframe");
                    ui.monospace("World        not initialized");
                });
        }
    }
}

impl RustCraftApp {
    fn apply_theme(&self, ctx: &egui::Context) {
        ctx.set_theme(egui::Theme::Dark);
        ctx.global_style_mut(|style| {
            style.visuals = egui::Visuals::dark();
            style.visuals.panel_fill = Color32::from_rgb(20, 27, 23);
            style.visuals.window_fill = Color32::from_rgb(27, 36, 30);
            style.visuals.extreme_bg_color = Color32::from_rgb(13, 18, 15);
            style.visuals.widgets.inactive.bg_fill = Color32::from_rgb(44, 59, 48);
            style.visuals.widgets.hovered.bg_fill = Color32::from_rgb(64, 88, 68);
            style.spacing.item_spacing = Vec2::new(10.0, 10.0);
        });
    }

    fn update_fps(&mut self) {
        self.frame_counter += 1;
        let elapsed = self.last_fps_update.elapsed().as_secs_f32();
        if elapsed >= 0.5 {
            self.fps = self.frame_counter as f32 / elapsed;
            self.frame_counter = 0;
            self.last_fps_update = Instant::now();
        }
    }

    fn nav_button(&mut self, ui: &mut egui::Ui, label: &str, target: Screen) {
        let selected = self.screen == target;
        let text = if selected {
            RichText::new(label)
                .strong()
                .color(Color32::from_rgb(220, 244, 216))
        } else {
            RichText::new(label).color(Color32::from_rgb(175, 190, 178))
        };

        let fill = if selected {
            Color32::from_rgb(54, 79, 59)
        } else {
            Color32::TRANSPARENT
        };

        if ui
            .add_sized(
                [ui.available_width(), 38.0],
                egui::Button::new(text).fill(fill),
            )
            .clicked()
        {
            self.screen = target;
        }
    }

    fn home_screen(&mut self, ui: &mut egui::Ui) {
        ui.label(
            RichText::new("WELCOME BACK")
                .size(11.0)
                .strong()
                .color(Color32::from_rgb(149, 184, 151)),
        );
        ui.heading(
            RichText::new("Build a world.\nOne block at a time.")
                .size(34.0)
                .strong(),
        );
        ui.label(
            RichText::new(
                "An independent Rust implementation targeting Java Edition 1.21.11 compatibility.",
            )
            .color(Color32::GRAY),
        );
        ui.add_space(14.0);

        egui::Frame::group(ui.style())
            .fill(Color32::from_rgb(27, 39, 31))
            .stroke(Stroke::new(1.0, Color32::from_rgb(61, 83, 64)))
            .corner_radius(12.0)
            .inner_margin(16.0)
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new("RENDERER PREVIEW")
                                .size(10.0)
                                .strong()
                                .color(Color32::GRAY),
                        );
                        ui.heading("Voxel scene placeholder");
                        ui.label(
                            "GPU accelerated egui composition via wgpu.\n\
                             The dedicated chunk mesh pipeline is Phase 2.",
                        );
                        ui.add_space(12.0);
                        if ui.button("Open world manager").clicked() {
                            self.screen = Screen::Worlds;
                        }
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        let (rect, _) = ui.allocate_exact_size(
                            Vec2::new(300.0, 220.0),
                            egui::Sense::hover(),
                        );
                        paint_voxel_preview(ui.painter(), rect);
                    });
                });
            });

        ui.add_space(14.0);
        ui.horizontal(|ui| {
            metric_card(ui, "RENDERER", "wgpu", "GPU abstraction");
            metric_card(ui, "UI", "egui", "Rust-native interface");
            metric_card(ui, "TARGET", "1.21.11", "Compatibility baseline");
        });
    }

    fn worlds_screen(&mut self, ui: &mut egui::Ui) {
        ui.heading("Worlds");
        ui.label(
            "Singleplayer is the first gameplay milestone. World persistence is not wired yet.",
        );
        ui.add_space(18.0);
        egui::Frame::group(ui.style()).show(ui, |ui| {
            ui.set_min_height(150.0);
            ui.vertical_centered(|ui| {
                ui.add_space(18.0);
                ui.label(RichText::new("▧").size(34.0).color(Color32::GRAY));
                ui.label(RichText::new("No worlds yet").strong());
                ui.label(
                    RichText::new("World generation and save/load arrive in Phase 3.")
                        .color(Color32::GRAY),
                );
                ui.add_space(10.0);
                if ui.button("Create world (coming soon)").clicked() {
                    self.status = "World creation is scheduled for Phase 3".to_owned();
                }
            });
        });
    }

    fn settings_screen(&mut self, ui: &mut egui::Ui) {
        ui.heading("Settings");
        ui.label("Prototype settings (runtime values only; persistence will be added later).");
        ui.add_space(12.0);
        ui.horizontal(|ui| {
            ui.label("Render distance");
            ui.add(egui::Slider::new(&mut self.render_distance, 2..=24).suffix(" chunks"));
        });
        ui.checkbox(&mut self.show_debug, "Show debug overlay");
    }

    fn about_screen(&mut self, ui: &mut egui::Ui) {
        ui.heading("About RustCraft");
        ui.label(
            concat!(
                "RustCraft is an independent reimplementation project. ",
                "It is not affiliated with Mojang or Microsoft.",
            ),
        );
        ui.add_space(8.0);
        ui.monospace("Rust 2024 · eframe/egui · wgpu · Android + Windows + Linux");
        ui.label("Target compatibility baseline: Minecraft Java Edition 1.21.11.");
        ui.label("No proprietary Minecraft code or assets are bundled in this repository.");
    }
}

fn metric_card(ui: &mut egui::Ui, label: &str, value: &str, hint: &str) {
    egui::Frame::group(ui.style())
        .inner_margin(12.0)
        .show(ui, |ui| {
            ui.set_min_width(130.0);
            ui.label(RichText::new(label).size(9.0).strong().color(Color32::GRAY));
            ui.label(RichText::new(value).size(20.0).strong());
            ui.label(RichText::new(hint).size(10.0).color(Color32::GRAY));
        });
}

fn paint_voxel_preview(painter: &egui::Painter, rect: egui::Rect) {
    let center = rect.center();
    let scale = 48.0;
    let project = |x: f32, y: f32, z: f32| -> egui::Pos2 {
        egui::pos2(
            center.x + (x - z) * scale * 0.72,
            center.y + (x + z) * scale * 0.36 - y * scale,
        )
    };

    let cubes = [
        (-1.0, 0.0, -1.0),
        (0.0, 0.0, -1.0),
        (1.0, 0.0, -1.0),
        (-1.0, 0.0, 0.0),
        (0.0, 0.0, 0.0),
        (1.0, 0.0, 0.0),
        (-1.0, 0.0, 1.0),
        (0.0, 0.0, 1.0),
        (1.0, 0.0, 1.0),
        (0.0, 1.0, 0.0),
    ];

    for (x, y, z) in cubes {
        let p000 = project(x, y, z);
        let p100 = project(x + 1.0, y, z);
        let p010 = project(x + 1.0, y + 1.0, z);
        let p001 = project(x, y + 1.0, z);
        let p111 = project(x + 1.0, y + 1.0, z + 1.0);
        let p011 = project(x, y + 1.0, z + 1.0);
        let p110 = project(x, y, z + 1.0);

        let top = vec![p001, p010, p111, p011];
        let right = vec![p010, p100, p110, p111];
        let left = vec![p000, p100, p010, p001];
        let outline = Stroke::new(1.0, Color32::from_rgb(35, 59, 38));

        painter.add(egui::Shape::convex_polygon(
            top,
            Color32::from_rgb(102, 166, 91),
            outline,
        ));
        painter.add(egui::Shape::convex_polygon(
            right,
            Color32::from_rgb(74, 119, 68),
            outline,
        ));
        painter.add(egui::Shape::convex_polygon(
            left,
            Color32::from_rgb(57, 94, 57),
            outline,
        ));
    }

    painter.text(
        rect.left_top() + Vec2::new(8.0, 8.0),
        egui::Align2::LEFT_TOP,
        "PROCEDURAL PREVIEW",
        FontId::monospace(9.0),
        Color32::from_rgb(143, 174, 146),
    );
}
