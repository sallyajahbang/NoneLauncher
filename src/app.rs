use std::time::{Duration, Instant};

use eframe::egui::{self, Align, Color32, FontId, Layout, RichText, Stroke, Vec2};

const BG: Color32 = Color32::from_rgb(13, 18, 15);
const SURFACE: Color32 = Color32::from_rgb(20, 29, 23);
const SURFACE_RAISED: Color32 = Color32::from_rgb(27, 39, 31);
const OUTLINE: Color32 = Color32::from_rgb(57, 79, 61);
const GREEN: Color32 = Color32::from_rgb(166, 214, 164);
const TEXT: Color32 = Color32::from_rgb(226, 235, 226);
const MUTED: Color32 = Color32::from_rgb(153, 170, 156);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Screen {
    Home,
    Worlds,
    Settings,
    About,
    Developer,
}

impl Screen {
    const TABS: [(Self, &'static str); 4] = [
        (Self::Home, "Home"),
        (Self::Worlds, "Worlds"),
        (Self::Settings, "Settings"),
        (Self::About, "About"),
    ];
}

pub struct RustCraftApp {
    screen: Screen,
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
            render_distance: 8,
            frame_counter: 0,
            fps: 0.0,
            last_fps_update: Instant::now(),
            started: Instant::now(),
            status: "Android runtime initialized".to_owned(),
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
        egui::Frame::new()
            .fill(BG)
            .inner_margin(10.0)
            .show(ui, |ui| {
                let available = ui.available_size();
                // Android phones in landscape often have a very short logical height.
                // In that case, put navigation in the top row instead of squeezing a
                // desktop-style drawer and bottom bar into the viewport.
                let compact_landscape = available.y < 420.0 && available.x > available.y;
                let content_width = available.x.max(1.0);
                let bottom_nav_height = if compact_landscape { 0.0 } else { 58.0 };
                let header_height = if compact_landscape { 44.0 } else { 52.0 };
                let content_height =
                    (available.y - header_height - bottom_nav_height - 20.0).max(40.0);

                ui.set_min_size(available);
                ui.vertical(|ui| {
                    if compact_landscape {
                        self.compact_landscape_header(ui, content_width);
                    } else {
                        self.phone_header(ui, content_width);
                    }

                    ui.allocate_ui_with_layout(
                        Vec2::new(content_width, content_height),
                        Layout::top_down(Align::Min),
                        |content_ui| {
                            egui::ScrollArea::vertical()
                                .id_salt("rustcraft-main-content")
                                .auto_shrink([false, false])
                                .show(content_ui, |content_ui| {
                                    content_ui.set_width(content_ui.available_width());
                                    match self.screen {
                                        Screen::Home => {
                                            self.home_screen(content_ui, compact_landscape)
                                        }
                                        Screen::Worlds => self.worlds_screen(content_ui),
                                        Screen::Settings => self.settings_screen(content_ui),
                                        Screen::About => self.about_screen(content_ui),
                                        Screen::Developer => self.developer_screen(content_ui),
                                    }
                                });
                        },
                    );

                    if !compact_landscape {
                        self.bottom_navigation(ui, content_width);
                    }
                });
            });
    }
}

impl RustCraftApp {
    fn apply_theme(&self, ctx: &egui::Context) {
        ctx.set_theme(egui::Theme::Dark);
        ctx.global_style_mut(|style| {
            style.visuals = egui::Visuals::dark();
            style.visuals.panel_fill = BG;
            style.visuals.window_fill = SURFACE;
            style.visuals.extreme_bg_color = BG;
            style.visuals.faint_bg_color = SURFACE;
            style.visuals.widgets.inactive.bg_fill = Color32::from_rgb(37, 53, 42);
            style.visuals.widgets.hovered.bg_fill = Color32::from_rgb(54, 78, 60);
            style.visuals.widgets.active.bg_fill = Color32::from_rgb(69, 98, 73);
            style.visuals.selection.bg_fill = Color32::from_rgb(62, 104, 69);
            style.visuals.override_text_color = Some(TEXT);
            style.spacing.item_spacing = Vec2::new(8.0, 8.0);
            style.spacing.button_padding = Vec2::new(12.0, 8.0);
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

    fn phone_header(&mut self, ui: &mut egui::Ui, width: f32) {
        ui.horizontal_centered(|ui| {
            ui.add_space(4.0);
            ui.label(
                RichText::new("RUSTCRAFT")
                    .size(19.0)
                    .strong()
                    .color(GREEN),
            );
            ui.add_space(7.0);
            ui.label(RichText::new("JAVA 1.21.11").size(10.0).color(MUTED));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if ui
                    .add_sized(
                        [60.0, 44.0],
                        egui::Button::new(RichText::new("DEV").size(11.0)),
                    )
                    .clicked()
                {
                    self.screen = Screen::Developer;
                }
            });
        });
        ui.separator();
        ui.set_width(width);
    }

    fn compact_landscape_header(&mut self, ui: &mut egui::Ui, width: f32) {
        ui.horizontal_centered(|ui| {
            ui.add_space(3.0);
            ui.label(
                RichText::new("RUSTCRAFT")
                    .size(15.0)
                    .strong()
                    .color(GREEN),
            );
            ui.add_space(3.0);
            for (screen, _) in Screen::TABS {
                let label = match screen {
                    Screen::Home => "HOME",
                    Screen::Worlds => "WORLD",
                    Screen::Settings => "SET",
                    Screen::About => "INFO",
                    Screen::Developer => unreachable!("Developer is not in the primary tabs"),
                };
                self.top_tab(ui, screen, label);
            }
            if ui
                .add_sized(
                    [44.0, 34.0],
                    egui::Button::new(RichText::new("DEV").size(9.0)),
                )
                .clicked()
            {
                self.screen = Screen::Developer;
            }
        });
        ui.separator();
        ui.set_width(width);
    }

    fn top_tab(&mut self, ui: &mut egui::Ui, target: Screen, label: &str) {
        let selected = self.screen == target;
        let text = RichText::new(label)
            .size(9.0)
            .color(if selected { TEXT } else { MUTED });
        let fill = if selected {
            Color32::from_rgb(48, 76, 54)
        } else {
            Color32::TRANSPARENT
        };
        if ui
            .add_sized(
                [label_width(label), 34.0],
                egui::Button::new(text).fill(fill),
            )
            .clicked()
        {
            self.screen = target;
        }
    }

    fn bottom_navigation(&mut self, ui: &mut egui::Ui, width: f32) {
        ui.separator();
        ui.horizontal(|ui| {
            let gap = ui.spacing().item_spacing.x;
            let slot_width = ((width - gap * 3.0) / 4.0).max(52.0);
            for (screen, label) in Screen::TABS {
                let selected = self.screen == screen;
                let color = if selected { TEXT } else { MUTED };
                let fill = if selected {
                    Color32::from_rgb(48, 76, 54)
                } else {
                    Color32::TRANSPARENT
                };
                if ui
                    .add_sized(
                        [slot_width, 48.0],
                        egui::Button::new(RichText::new(label).size(11.0).color(color)).fill(fill),
                    )
                    .clicked()
                {
                    self.screen = screen;
                }
            }
        });
    }

    fn home_screen(&mut self, ui: &mut egui::Ui, compact_landscape: bool) {
        ui.add_space(6.0);
        ui.label(
            RichText::new("ANDROID NATIVE VOXEL RUNTIME")
                .size(10.0)
                .strong()
                .color(GREEN),
        );
        let title = if compact_landscape {
            "Build worlds with Rust."
        } else {
            "Build worlds.\nOne block at a time."
        };
        ui.label(
            RichText::new(title)
                .size(if compact_landscape { 19.0 } else { 27.0 })
                .strong(),
        );
        if !compact_landscape {
            ui.label(
                RichText::new("Independent Java Edition reimplementation · target 1.21.11")
                    .size(12.0)
                    .color(MUTED),
            );
        }

        ui.add_space(8.0);
        egui::Frame::group(ui.style())
            .fill(SURFACE_RAISED)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(14.0)
            .inner_margin(10.0)
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new("VOXEL RENDERER PREVIEW")
                            .size(10.0)
                            .strong()
                            .color(GREEN),
                    );
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.label(RichText::new("wgpu").size(10.0).color(MUTED));
                    });
                });
                ui.add_space(4.0);
                let preview_height = if compact_landscape { 64.0 } else { 132.0 };
                let preview_width = ui.available_width().max(80.0);
                let (rect, _) = ui.allocate_exact_size(
                    Vec2::new(preview_width, preview_height),
                    egui::Sense::hover(),
                );
                paint_voxel_preview(ui.painter(), rect);
            });

        ui.add_space(8.0);
        ui.horizontal_wrapped(|ui| {
            status_chip(ui, "ANDROID ARM64");
            status_chip(ui, "RUST");
            status_chip(ui, "SINGLEPLAYER FIRST");
        });
    }

    fn worlds_screen(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        ui.heading("Worlds");
        ui.label(RichText::new("Your singleplayer worlds will appear here.").color(MUTED));
        ui.add_space(14.0);
        egui::Frame::group(ui.style())
            .fill(SURFACE)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(14.0)
            .inner_margin(16.0)
            .show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.vertical_centered(|ui| {
                    ui.label(RichText::new("No worlds yet").size(18.0).strong());
                    ui.add_space(4.0);
                    ui.label(
                        RichText::new("World generation and save/load are planned for Phase 3.")
                            .size(11.0)
                            .color(MUTED),
                    );
                    ui.add_space(12.0);
                    if ui
                        .add_sized(
                            [ui.available_width().min(260.0), 46.0],
                            egui::Button::new("Create world · coming soon"),
                        )
                        .clicked()
                    {
                        self.status = "World creation is planned for Phase 3".to_owned();
                    }
                });
            });
    }

    fn settings_screen(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        ui.heading("Settings");
        ui.label(RichText::new("Runtime options for this Android device.").color(MUTED));
        ui.add_space(12.0);
        egui::Frame::group(ui.style())
            .fill(SURFACE)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(14.0)
            .inner_margin(14.0)
            .show(ui, |ui| {
                ui.label(RichText::new("VIDEO").size(10.0).strong().color(GREEN));
                ui.label("Render distance");
                ui.add(
                    egui::Slider::new(&mut self.render_distance, 2..=16)
                        .suffix(" chunks")
                        .show_value(true),
                );
                ui.add_space(8.0);
                ui.label(RichText::new("DEVELOPER").size(10.0).strong().color(GREEN));
                if ui
                    .add_sized(
                        [ui.available_width(), 44.0],
                        egui::Button::new("Open runtime diagnostics"),
                    )
                    .clicked()
                {
                    self.screen = Screen::Developer;
                }
            });
    }

    fn developer_screen(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        ui.heading("Developer diagnostics");
        ui.label(RichText::new("Live runtime information · no floating overlay").color(MUTED));
        ui.add_space(12.0);
        egui::Frame::group(ui.style())
            .fill(SURFACE)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(14.0)
            .inner_margin(14.0)
            .show(ui, |ui| {
                diagnostic_row(ui, "FPS", &format!("{:.1}", self.fps));
                diagnostic_row(
                    ui,
                    "Frame time",
                    &format!("{:.2} ms", 1000.0 / self.fps.max(0.1)),
                );
                diagnostic_row(
                    ui,
                    "Uptime",
                    &format!("{:.1} s", self.started.elapsed().as_secs_f32()),
                );
                diagnostic_row(
                    ui,
                    "Render distance",
                    &format!("{} chunks", self.render_distance),
                );
                diagnostic_row(ui, "Graphics", "wgpu / Vulkan where available");
                diagnostic_row(ui, "World", "Not initialized");
                diagnostic_row(ui, "Platform", "Android ARM64");
            });
        ui.add_space(10.0);
        ui.label(RichText::new(&self.status).size(11.0).color(MUTED));
    }

    fn about_screen(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        ui.heading("About RustCraft");
        egui::Frame::group(ui.style())
            .fill(SURFACE)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(14.0)
            .inner_margin(14.0)
            .show(ui, |ui| {
                ui.label(RichText::new("RustCraft").size(20.0).strong().color(GREEN));
                ui.label(
                    "An independent Rust implementation project targeting Minecraft Java Edition 1.21.11 compatibility.",
                );
                ui.add_space(8.0);
                diagnostic_row(ui, "Version", "0.1.0 · foundation build");
                diagnostic_row(ui, "Runtime", "Rust + eframe + egui");
                diagnostic_row(ui, "Renderer", "wgpu");
                diagnostic_row(ui, "Target", "Android ARM64 only");
                ui.add_space(8.0);
                ui.label(
                    RichText::new(
                        "Not affiliated with Mojang or Microsoft. No proprietary Minecraft code or assets are bundled.",
                    )
                    .size(11.0)
                    .color(MUTED),
                );
            });
    }
}

fn label_width(label: &str) -> f32 {
    match label {
        "HOME" => 44.0,
        "WORLD" => 48.0,
        "SET" => 36.0,
        "INFO" => 40.0,
        _ => 44.0,
    }
}

fn status_chip(ui: &mut egui::Ui, text: &str) {
    egui::Frame::new()
        .fill(Color32::from_rgb(31, 48, 35))
        .stroke(Stroke::new(1.0, OUTLINE))
        .corner_radius(20.0)
        .inner_margin(egui::Margin::symmetric(9, 5))
        .show(ui, |ui| {
            ui.label(RichText::new(text).size(9.0).strong().color(GREEN));
        });
}

fn diagnostic_row(ui: &mut egui::Ui, label: &str, value: &str) {
    ui.horizontal(|ui| {
        ui.label(RichText::new(label).size(11.0).color(MUTED));
        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
            ui.label(
                RichText::new(value)
                    .size(11.0)
                    .monospace()
                    .color(TEXT),
            );
        });
    });
    ui.separator();
}

fn paint_voxel_preview(painter: &egui::Painter, rect: egui::Rect) {
    painter.rect_filled(rect, 8.0, Color32::from_rgb(18, 31, 22));
    painter.text(
        rect.left_top() + Vec2::new(8.0, 6.0),
        egui::Align2::LEFT_TOP,
        "PROCEDURAL PREVIEW",
        FontId::monospace(9.0),
        Color32::from_rgb(143, 174, 146),
    );

    let center = egui::pos2(rect.center().x, rect.center().y + 10.0);
    let scale = (rect.height() * 0.42).clamp(11.0, 43.0);
    let project = |x: f32, y: f32, z: f32| -> egui::Pos2 {
        egui::pos2(
            center.x + (x - z) * scale * 0.72,
            center.y + (x + z) * scale * 0.30 - y * scale,
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
        let outline = Stroke::new(1.0, Color32::from_rgb(35, 59, 38));

        painter.add(egui::Shape::convex_polygon(
            vec![p001, p010, p111, p011],
            Color32::from_rgb(102, 166, 91),
            outline,
        ));
        painter.add(egui::Shape::convex_polygon(
            vec![p010, p100, p110, p111],
            Color32::from_rgb(74, 119, 68),
            outline,
        ));
        painter.add(egui::Shape::convex_polygon(
            vec![p000, p100, p010, p001],
            Color32::from_rgb(57, 94, 57),
            outline,
        ));
    }
}
