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
    Developer,
    About,
}

impl Screen {
    const TABS: [(Self, &'static str); 5] = [
        (Self::Home, "HOME"),
        (Self::Worlds, "WORLDS"),
        (Self::Settings, "SETTINGS"),
        (Self::Developer, "DEV"),
        (Self::About, "ABOUT"),
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
        // eframe's root UI may inherit a centered layout. Explicitly allocate a
        // full-size top-left layout; otherwise the phone can show only the header
        // in the middle of a large empty screen.
        let available = ui.available_size().max(Vec2::new(1.0, 1.0));
        ui.allocate_ui_with_layout(
            available,
            Layout::top_down(Align::Min),
            |root| {
                egui::Frame::new()
                    .fill(BG)
                    .inner_margin(egui::Margin::symmetric(14, 10))
                    .show(root, |body| {
                        let body_width = body.available_width();
                        let body_height = body.available_height();
                        body.set_width(body_width);
                        body.set_min_height(body_height);
                        body.with_layout(Layout::top_down(Align::Min), |body| {
                            self.phone_header(body);
                            body.add_space(8.0);

                            let nav_height = 58.0;
                            let content_height = (body.available_height() - nav_height - 8.0).max(80.0);
                            body.allocate_ui_with_layout(
                                Vec2::new(body.available_width(), content_height),
                                Layout::top_down(Align::Min),
                                |content| {
                                    egui::ScrollArea::vertical()
                                        .id_salt("rustcraft-phone-content")
                                        .auto_shrink([false, false])
                                        .show(content, |content| {
                                            content.set_width(content.available_width());
                                            match self.screen {
                                                Screen::Home => self.home_screen(content),
                                                Screen::Worlds => self.worlds_screen(content),
                                                Screen::Settings => self.settings_screen(content),
                                                Screen::Developer => self.developer_screen(content),
                                                Screen::About => self.about_screen(content),
                                            }
                                        });
                                },
                            );
                            body.add_space(6.0);
                            self.bottom_navigation(body);
                        });
                    });
            },
        );
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
            style.spacing.interact_size = Vec2::new(48.0, 48.0);
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

    fn phone_header(&self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| {
            ui.label(RichText::new("RUSTCRAFT").size(22.0).strong().color(GREEN));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                ui.label(RichText::new("JAVA 1.21.11").size(11.0).color(MUTED));
            });
        });
        ui.separator();
    }

    fn bottom_navigation(&mut self, ui: &mut egui::Ui) {
        ui.separator();
        let width = ui.available_width();
        let slot_width = (width / Screen::TABS.len() as f32).max(48.0);
        ui.horizontal(|ui| {
            ui.spacing_mut().item_spacing.x = 2.0;
            for (screen, label) in Screen::TABS {
                let selected = self.screen == screen;
                let color = if selected { TEXT } else { MUTED };
                let fill = if selected { Color32::from_rgb(48, 76, 54) } else { Color32::TRANSPARENT };
                let response = ui.add_sized(
                    [slot_width - 2.0, 50.0],
                    egui::Button::new(RichText::new(label).size(9.5).strong().color(color))
                        .fill(fill)
                        .corner_radius(12.0),
                );
                if response.clicked() {
                    self.screen = screen;
                }
            }
        });
    }

    fn home_screen(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        ui.label(RichText::new("ANDROID NATIVE VOXEL GAME").size(10.0).strong().color(GREEN));
        ui.add_space(5.0);
        ui.label(RichText::new("Your world.\nBuilt block by block.").size(29.0).strong());
        ui.add_space(5.0);
        ui.label(RichText::new("Independent Rust implementation targeting Java Edition 1.21.11 compatibility.").size(12.0).color(MUTED));
        ui.add_space(16.0);

        egui::Frame::group(ui.style())
            .fill(SURFACE_RAISED)
            .stroke(Stroke::new(1.0, OUTLINE))
            .corner_radius(16.0)
            .inner_margin(12.0)
            .show(ui, |ui| {
                ui.label(RichText::new("WORLD PREVIEW").size(10.0).strong().color(GREEN));
                ui.add_space(6.0);
                let (rect, _) = ui.allocate_exact_size(
                    Vec2::new(ui.available_width(), 150.0),
                    egui::Sense::hover(),
                );
                paint_voxel_preview(ui.painter(), rect);
                ui.add_space(8.0);
                ui.label(RichText::new("3D chunk renderer is under development.").size(11.0).color(MUTED));
            });

        ui.add_space(12.0);
        ui.label(RichText::new("QUICK ACCESS").size(10.0).strong().color(GREEN));
        ui.add_space(6.0);
        if ui.add_sized([ui.available_width(), 50.0], egui::Button::new("  Singleplayer worlds  ›")).clicked() {
            self.screen = Screen::Worlds;
        }
        ui.add_space(6.0);
        if ui.add_sized([ui.available_width(), 50.0], egui::Button::new("  Runtime settings  ›")).clicked() {
            self.screen = Screen::Settings;
        }
    }

    fn worlds_screen(&mut self, ui: &mut egui::Ui) {
        page_title(ui, "Worlds", "Manage your singleplayer worlds.");
        card(ui, |ui| {
            ui.vertical_centered(|ui| {
                ui.label(RichText::new("No worlds yet").size(19.0).strong());
                ui.add_space(5.0);
                ui.label(RichText::new("World generation and save/load are planned for the next phase.").size(11.0).color(MUTED));
                ui.add_space(12.0);
                if ui.add_sized([ui.available_width(), 48.0], egui::Button::new("Create world · coming soon")).clicked() {
                    self.status = "World creation is planned for the next phase".to_owned();
                }
            });
        });
    }

    fn settings_screen(&mut self, ui: &mut egui::Ui) {
        page_title(ui, "Settings", "Tune RustCraft for this Android device.");
        card(ui, |ui| {
            section_label(ui, "VIDEO");
            ui.label("Render distance");
            ui.add(egui::Slider::new(&mut self.render_distance, 2..=16).suffix(" chunks"));
            ui.add_space(8.0);
            section_label(ui, "APPLICATION");
            ui.label(RichText::new("Platform: Android ARM64").color(MUTED));
            ui.label(RichText::new("Renderer: wgpu").color(MUTED));
        });
    }

    fn developer_screen(&mut self, ui: &mut egui::Ui) {
        page_title(ui, "Developer", "Runtime diagnostics and engine status.");
        card(ui, |ui| {
            diagnostic_row(ui, "FPS", &format!("{:.1}", self.fps));
            diagnostic_row(ui, "Frame time", &format!("{:.2} ms", 1000.0 / self.fps.max(0.1)));
            diagnostic_row(ui, "Uptime", &format!("{:.1} s", self.started.elapsed().as_secs_f32()));
            diagnostic_row(ui, "Render distance", &format!("{} chunks", self.render_distance));
            diagnostic_row(ui, "Graphics", "wgpu / Vulkan where available");
            diagnostic_row(ui, "World", "Not initialized");
            diagnostic_row(ui, "Platform", "Android ARM64");
        });
        ui.add_space(10.0);
        ui.label(RichText::new(&self.status).size(11.0).color(MUTED));
    }

    fn about_screen(&mut self, ui: &mut egui::Ui) {
        page_title(ui, "About RustCraft", "An independent native voxel game project.");
        card(ui, |ui| {
            ui.label(RichText::new("RustCraft").size(21.0).strong().color(GREEN));
            diagnostic_row(ui, "Version", "0.1.0 · foundation build");
            diagnostic_row(ui, "Runtime", "Rust + egui");
            diagnostic_row(ui, "Renderer", "wgpu");
            diagnostic_row(ui, "Target", "Android ARM64 only");
            ui.add_space(8.0);
            ui.label(RichText::new("Not affiliated with Mojang or Microsoft. No proprietary Minecraft code or assets are bundled.").size(11.0).color(MUTED));
        });
    }
}

fn page_title(ui: &mut egui::Ui, title: &str, subtitle: &str) {
    ui.add_space(8.0);
    ui.heading(title);
    ui.label(RichText::new(subtitle).size(12.0).color(MUTED));
    ui.add_space(14.0);
}

fn section_label(ui: &mut egui::Ui, label: &str) {
    ui.label(RichText::new(label).size(10.0).strong().color(GREEN));
}

fn card(ui: &mut egui::Ui, content: impl FnOnce(&mut egui::Ui)) {
    egui::Frame::group(ui.style())
        .fill(SURFACE)
        .stroke(Stroke::new(1.0, OUTLINE))
        .corner_radius(16.0)
        .inner_margin(14.0)
        .show(ui, content);
}

fn diagnostic_row(ui: &mut egui::Ui, label: &str, value: &str) {
    ui.horizontal(|ui| {
        ui.label(RichText::new(label).size(11.0).color(MUTED));
        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
            ui.label(RichText::new(value).size(11.0).monospace().color(TEXT));
        });
    });
    ui.separator();
}

fn paint_voxel_preview(painter: &egui::Painter, rect: egui::Rect) {
    painter.rect_filled(rect, 10.0, Color32::from_rgb(18, 31, 22));
    painter.text(rect.left_top() + Vec2::new(10.0, 8.0), egui::Align2::LEFT_TOP, "PROCEDURAL VOXEL PREVIEW", FontId::monospace(9.0), Color32::from_rgb(143, 174, 146));
    let center = egui::pos2(rect.center().x, rect.center().y + 14.0);
    let scale = (rect.height() * 0.38).clamp(12.0, 48.0);
    let project = |x: f32, y: f32, z: f32| egui::pos2(center.x + (x - z) * scale * 0.72, center.y + (x + z) * scale * 0.30 - y * scale);
    let cubes = [(-1.0,0.0,-1.0),(0.0,0.0,-1.0),(1.0,0.0,-1.0),(-1.0,0.0,0.0),(0.0,0.0,0.0),(1.0,0.0,0.0),(-1.0,0.0,1.0),(0.0,0.0,1.0),(1.0,0.0,1.0),(0.0,1.0,0.0)];
    for (x,y,z) in cubes {
        let p000=project(x,y,z); let p100=project(x+1.0,y,z); let p010=project(x+1.0,y+1.0,z); let p001=project(x,y+1.0,z); let p111=project(x+1.0,y+1.0,z+1.0); let p011=project(x,y+1.0,z+1.0); let p110=project(x,y,z+1.0);
        let stroke=Stroke::new(1.0,Color32::from_rgb(35,59,38));
        painter.add(egui::Shape::convex_polygon(vec![p001,p010,p111,p011],Color32::from_rgb(102,166,91),stroke));
        painter.add(egui::Shape::convex_polygon(vec![p010,p100,p110,p111],Color32::from_rgb(74,119,68),stroke));
        painter.add(egui::Shape::convex_polygon(vec![p000,p100,p010,p001],Color32::from_rgb(57,94,57),stroke));
    }
}
