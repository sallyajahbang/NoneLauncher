//! Tahap 1: jendela Android + render GPU (wgpu) + kubus berputar.
//!
//! Perilaku saat gagal (tanpa logcat, bisa dilihat dari layar):
//! - Layar hitam  : tidak ada GPU yang bisa dipakai (Vulkan maupun GLES gagal).
//! - Layar ungu   : GPU jalan, tapi pipeline/shader gagal dibuat.
//! - Kubus normal : semuanya berjalan.

use std::panic::{self, AssertUnwindSafe};
use std::sync::Arc;
use std::time::Instant;

use bytemuck::{Pod, Zeroable};
use glam::{Mat4, Vec3};
use wgpu::util::DeviceExt;
use winit::application::ApplicationHandler;
use winit::event::WindowEvent;
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::platform::android::activity::AndroidApp;
use winit::platform::android::EventLoopBuilderExtAndroid;
use winit::window::{Window, WindowId};

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct Vertex {
    pos: [f32; 3],
    color: [f32; 3],
}

impl Vertex {
    const ATTRIBS: [wgpu::VertexAttribute; 2] =
        wgpu::vertex_attr_array![0 => Float32x3, 1 => Float32x3];

    fn layout() -> wgpu::VertexBufferLayout<'static> {
        wgpu::VertexBufferLayout {
            array_stride: std::mem::size_of::<Vertex>() as wgpu::BufferAddress,
            step_mode: wgpu::VertexStepMode::Vertex,
            attributes: &Self::ATTRIBS,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct Uniforms {
    mvp: [[f32; 4]; 4],
}

/// 8 sudut kubus (bit 0 = x, bit 1 = y, bit 2 = z) dan 36 index segitiga.
fn cube_geometry() -> (Vec<Vertex>, Vec<u16>) {
    let vertices = (0..8u16)
        .map(|i| {
            let p = [
                if (i & 1) != 0 { 0.5 } else { -0.5 },
                if (i & 2) != 0 { 0.5 } else { -0.5 },
                if (i & 4) != 0 { 0.5 } else { -0.5 },
            ];
            Vertex {
                pos: p,
                color: [p[0] + 0.5, p[1] + 0.5, p[2] + 0.5],
            }
        })
        .collect();

    let indices = vec![
        4, 5, 7, 4, 7, 6, // +Z
        0, 2, 3, 0, 3, 1, // -Z
        1, 3, 7, 1, 7, 5, // +X
        0, 4, 6, 0, 6, 2, // -X
        2, 6, 7, 2, 7, 3, // +Y
        0, 1, 5, 0, 5, 4, // -Y
    ];

    (vertices, indices)
}

fn create_depth_view(device: &wgpu::Device, width: u32, height: u32) -> wgpu::TextureView {
    device
        .create_texture(&wgpu::TextureDescriptor {
            label: Some("depth"),
            size: wgpu::Extent3d {
                width: width.max(1),
                height: height.max(1),
                depth_or_array_layers: 1,
            },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Depth32Float,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            view_formats: &[],
        })
        .create_view(&wgpu::TextureViewDescriptor::default())
}

/// Resource untuk menggambar kubus. Dibuat dalam `catch_unwind` karena
/// wgpu panic jika shader/pipeline tidak valid di driver tertentu.
struct Scene {
    pipeline: wgpu::RenderPipeline,
    vertex_buf: wgpu::Buffer,
    index_buf: wgpu::Buffer,
    index_count: u32,
    uniform_buf: wgpu::Buffer,
    bind_group: wgpu::BindGroup,
}

impl Scene {
    fn new(device: &wgpu::Device, format: wgpu::TextureFormat) -> Self {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("cube shader"),
            source: wgpu::ShaderSource::Wgsl(include_str!("shader.wgsl").into()),
        });

        let (vertices, indices) = cube_geometry();
        let vertex_buf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("vertices"),
            contents: bytemuck::cast_slice(&vertices),
            usage: wgpu::BufferUsages::VERTEX,
        });
        let index_buf = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("indices"),
            contents: bytemuck::cast_slice(&indices),
            usage: wgpu::BufferUsages::INDEX,
        });

        let uniform_buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("uniforms"),
            size: std::mem::size_of::<Uniforms>() as wgpu::BufferAddress,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });

        let bind_group_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("bind group layout"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });

        let bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("bind group"),
            layout: &bind_group_layout,
            entries: &[wgpu::BindGroupEntry {
                binding: 0,
                resource: uniform_buf.as_entire_binding(),
            }],
        });

        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("pipeline layout"),
            bind_group_layouts: &[&bind_group_layout],
            push_constant_ranges: &[],
        });

        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("cube pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs_main",
                buffers: &[Vertex::layout()],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs_main",
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    blend: None,
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState {
                // Culling dimatikan supaya urutan winding tidak perlu dikoreksi.
                cull_mode: None,
                ..Default::default()
            },
            depth_stencil: Some(wgpu::DepthStencilState {
                format: wgpu::TextureFormat::Depth32Float,
                depth_write_enabled: true,
                depth_compare: wgpu::CompareFunction::Less,
                stencil: Default::default(),
                bias: Default::default(),
            }),
            multisample: Default::default(),
            multiview: None,
        });

        Self {
            pipeline,
            vertex_buf,
            index_buf,
            index_count: indices.len() as u32,
            uniform_buf,
            bind_group,
        }
    }
}

/// Mencoba Vulkan dulu, lalu GLES bila Vulkan tidak tersedia.
async fn pick_adapter(
    window: &Arc<Window>,
) -> Option<(wgpu::Instance, wgpu::Surface<'static>, wgpu::Adapter)> {
    for backends in [wgpu::Backends::VULKAN, wgpu::Backends::GL] {
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends,
            ..Default::default()
        });

        let Ok(surface) = instance.create_surface(window.clone()) else {
            log::warn!("create_surface gagal untuk {backends:?}");
            continue;
        };

        if let Some(adapter) = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::LowPower,
                compatible_surface: Some(&surface),
                force_fallback_adapter: false,
            })
            .await
        {
            return Some((instance, surface, adapter));
        }
        log::warn!("tidak ada adapter untuk {backends:?}");
    }
    None
}

/// Semua state GPU. Dibuat ulang setiap kali jendela Android kembali aktif.
struct Gfx {
    // Field di-drop berurutan: surface dan instance harus lebih dulu dari window.
    _instance: wgpu::Instance,
    surface: wgpu::Surface<'static>,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
    scene: Option<Scene>,
    depth_view: wgpu::TextureView,
    start: Instant,
    window: Arc<Window>,
}

impl Gfx {
    async fn new(window: Arc<Window>) -> Result<Self, String> {
        let size = window.inner_size();

        let (instance, surface, adapter) = pick_adapter(&window)
            .await
            .ok_or("tidak ada adapter Vulkan maupun GLES")?;

        log::info!("adapter: {:?}", adapter.get_info());

        let (device, queue) = adapter
            .request_device(
                &wgpu::DeviceDescriptor {
                    label: Some("device"),
                    required_features: wgpu::Features::empty(),
                    required_limits: wgpu::Limits::downlevel_defaults(),
                },
                None,
            )
            .await
            .map_err(|e| format!("request_device gagal: {e:?}"))?;

        let caps = surface.get_capabilities(&adapter);
        let format = caps
            .formats
            .iter()
            .copied()
            .find(|f| f.is_srgb())
            .unwrap_or(caps.formats[0]);

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            width: size.width.max(1),
            height: size.height.max(1),
            present_mode: wgpu::PresentMode::Fifo,
            alpha_mode: caps.alpha_modes[0],
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };
        surface.configure(&device, &config);

        let scene = panic::catch_unwind(AssertUnwindSafe(|| Scene::new(&device, format)))
            .map_err(|_| log::error!("pembuatan pipeline gagal"))
            .ok();

        let depth_view = create_depth_view(&device, config.width, config.height);

        Ok(Self {
            _instance: instance,
            surface,
            device,
            queue,
            config,
            scene,
            depth_view,
            start: Instant::now(),
            window,
        })
    }

    fn resize(&mut self, width: u32, height: u32) {
        if width == 0 || height == 0 {
            return; // minimized
        }
        self.config.width = width;
        self.config.height = height;
        self.surface.configure(&self.device, &self.config);
        self.depth_view = create_depth_view(&self.device, width, height);
    }

    fn render(&mut self) -> Result<(), wgpu::SurfaceError> {
        let t = self.start.elapsed().as_secs_f32();

        if let Some(scene) = &self.scene {
            let aspect = self.config.width as f32 / self.config.height as f32;
            let proj = Mat4::perspective_rh(45f32.to_radians(), aspect, 0.1, 100.0);
            let view = Mat4::look_at_rh(Vec3::new(0.0, 0.0, 3.0), Vec3::ZERO, Vec3::Y);
            let model = Mat4::from_rotation_y(t * 0.8) * Mat4::from_rotation_x(t * 0.5);
            let uniforms = Uniforms {
                mvp: (proj * view * model).to_cols_array_2d(),
            };
            self.queue
                .write_buffer(&scene.uniform_buf, 0, bytemuck::bytes_of(&uniforms));
        }

        // Biru gelap normal; ungu jika pipeline gagal (kubus tidak digambar).
        let clear = if self.scene.is_some() {
            wgpu::Color { r: 0.05, g: 0.06, b: 0.09, a: 1.0 }
        } else {
            wgpu::Color { r: 0.5, g: 0.0, b: 0.5, a: 1.0 }
        };

        let frame = self.surface.get_current_texture()?;
        let color_view = frame.texture.create_view(&wgpu::TextureViewDescriptor::default());

        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("frame"),
            });

        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("main pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &color_view,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(clear),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                    view: &self.depth_view,
                    depth_ops: Some(wgpu::Operations {
                        load: wgpu::LoadOp::Clear(1.0),
                        store: wgpu::StoreOp::Store,
                    }),
                    stencil_ops: None,
                }),
                timestamp_writes: None,
                occlusion_query_set: None,
            });

            if let Some(scene) = &self.scene {
                pass.set_pipeline(&scene.pipeline);
                pass.set_bind_group(0, &scene.bind_group, &[]);
                pass.set_vertex_buffer(0, scene.vertex_buf.slice(..));
                pass.set_index_buffer(scene.index_buf.slice(..), wgpu::IndexFormat::Uint16);
                pass.draw_indexed(0..scene.index_count, 0, 0..1);
            }
        }

        self.queue.submit(Some(encoder.finish()));
        frame.present();
        Ok(())
    }
}

#[derive(Default)]
struct App {
    gfx: Option<Gfx>,
}

impl ApplicationHandler for App {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.gfx.is_some() {
            return;
        }

        let window = match event_loop
            .create_window(Window::default_attributes().with_title("Rust Cube"))
        {
            Ok(w) => Arc::new(w),
            Err(e) => {
                log::error!("gagal membuat window: {e}");
                return;
            }
        };

        match pollster::block_on(Gfx::new(window)) {
            Ok(gfx) => {
                log::info!("render context siap");
                self.gfx = Some(gfx);
            }
            Err(e) => {
                // Tidak crash: layar tetap hitam. Pesan tetap tercatat di logcat bila ada.
                log::error!("inisialisasi GPU gagal: {e}");
            }
        }
    }

    fn suspended(&mut self, _event_loop: &ActiveEventLoop) {
        // Android mematikan window saat app ke background; GPU state dilepas.
        self.gfx = None;
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        let Some(gfx) = self.gfx.as_mut() else {
            return;
        };

        match event {
            WindowEvent::CloseRequested => event_loop.exit(),
            WindowEvent::Resized(size) => gfx.resize(size.width, size.height),
            WindowEvent::RedrawRequested => {
                match gfx.render() {
                    Ok(()) => {}
                    Err(wgpu::SurfaceError::Lost | wgpu::SurfaceError::Outdated) => {
                        let size = gfx.window.inner_size();
                        gfx.resize(size.width, size.height);
                    }
                    Err(wgpu::SurfaceError::Timeout) => {}
                    Err(wgpu::SurfaceError::OutOfMemory) => event_loop.exit(),
                }
                gfx.window.request_redraw();
            }
            _ => {}
        }
    }
}

#[no_mangle]
fn android_main(app: AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );

    let event_loop = EventLoop::builder()
        .with_android_app(app)
        .build()
        .expect("gagal membuat event loop");
    event_loop.set_control_flow(ControlFlow::Poll);

    let mut app = App::default();
    if let Err(e) = event_loop.run_app(&mut app) {
        log::error!("event loop berhenti: {e}");
    }
}
