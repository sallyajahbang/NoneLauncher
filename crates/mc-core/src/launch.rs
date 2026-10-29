//! Builds the JVM command line from a parsed version JSON and a launch config.

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::rules::{self, Context};
use crate::version::{allowed_libraries, artifact_path, Arg, OneOrMany, VersionJson};
use crate::Result;

pub const LAUNCHER_NAME: &str = "mc-launcher-rs";
pub const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchConfig {
    /// Path to the `java` binary (the bundled Amethyst JRE on Android).
    pub java_path: String,
    pub game_dir: PathBuf,
    pub libraries_dir: PathBuf,
    pub assets_dir: PathBuf,
    pub natives_dir: PathBuf,
    pub client_jar: PathBuf,
    pub username: String,
    pub uuid: String,
    pub access_token: String,
    #[serde(default = "default_user_type")]
    pub user_type: String,
    #[serde(default = "default_memory")]
    pub memory_mb: u32,
    #[serde(default)]
    pub width: u32,
    #[serde(default)]
    pub height: u32,
    #[serde(default)]
    pub context: Context,
    #[serde(default)]
    pub extra_jvm_args: Vec<String>,
}

fn default_user_type() -> String {
    "msa".into()
}

fn default_memory() -> u32 {
    2048
}

impl LaunchConfig {
    pub(crate) fn effective_context(&self) -> Context {
        let mut ctx = self.context.clone();
        ctx.features.insert(
            "has_custom_resolution".into(),
            self.width > 0 && self.height > 0,
        );
        ctx
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LaunchCommand {
    pub program: String,
    pub args: Vec<String>,
}

pub fn build(version: &VersionJson, cfg: &LaunchConfig) -> Result<LaunchCommand> {
    let ctx = cfg.effective_context();
    let classpath = classpath(version, cfg, &ctx);
    let vars = substitutions(version, cfg, &classpath);

    let mut args = vec![format!("-Xmx{}M", cfg.memory_mb)];
    args.extend(cfg.extra_jvm_args.iter().cloned());

    match &version.arguments {
        Some(a) => args.extend(expand(&a.jvm, &ctx, &vars)),
        None => {
            // Legacy (pre-1.13) profiles have no jvm argument list.
            args.push(format!(
                "-Djava.library.path={}",
                path_str(&cfg.natives_dir)
            ));
            args.push("-cp".into());
            args.push(classpath.clone());
        }
    }

    args.push(version.main_class.clone());

    match &version.arguments {
        Some(a) => args.extend(expand(&a.game, &ctx, &vars)),
        None => {
            let legacy = version.minecraft_arguments.clone().unwrap_or_default();
            args.extend(legacy.split_whitespace().map(|t| substitute(t, &vars)));
        }
    }

    Ok(LaunchCommand {
        program: cfg.java_path.clone(),
        args,
    })
}

/// Colon-separated classpath of allowed libraries plus the client jar.
pub fn classpath(version: &VersionJson, cfg: &LaunchConfig, ctx: &Context) -> String {
    let mut entries: Vec<String> = allowed_libraries(version, ctx)
        .filter_map(artifact_path)
        .map(|p| path_str(&cfg.libraries_dir.join(p)))
        .collect();
    entries.push(path_str(&cfg.client_jar));
    entries.join(":")
}

fn expand(list: &[Arg], ctx: &Context, vars: &HashMap<&'static str, String>) -> Vec<String> {
    let mut out = Vec::new();
    for arg in list {
        match arg {
            Arg::Plain(s) => out.push(substitute(s, vars)),
            Arg::Conditional { rules, value } => {
                if rules::is_allowed(rules, ctx) {
                    match value {
                        OneOrMany::One(s) => out.push(substitute(s, vars)),
                        OneOrMany::Many(v) => out.extend(v.iter().map(|s| substitute(s, vars))),
                    }
                }
            }
        }
    }
    out
}

fn substitute(template: &str, vars: &HashMap<&'static str, String>) -> String {
    let mut out = template.to_string();
    for (key, value) in vars {
        out = out.replace(&format!("${{{key}}}"), value);
    }
    out
}

fn substitutions(
    version: &VersionJson,
    cfg: &LaunchConfig,
    classpath: &str,
) -> HashMap<&'static str, String> {
    let assets_index = version
        .asset_index
        .as_ref()
        .map(|a| a.id.clone())
        .or_else(|| version.assets.clone())
        .unwrap_or_else(|| "legacy".into());

    let mut m = HashMap::new();
    m.insert("auth_player_name", cfg.username.clone());
    m.insert("version_name", version.id.clone());
    m.insert("game_directory", path_str(&cfg.game_dir));
    m.insert("assets_root", path_str(&cfg.assets_dir));
    m.insert("assets_index_name", assets_index);
    m.insert("auth_uuid", cfg.uuid.clone());
    m.insert("auth_access_token", cfg.access_token.clone());
    m.insert("auth_session", cfg.access_token.clone());
    m.insert("auth_xuid", String::new());
    m.insert("clientid", String::new());
    m.insert("user_type", cfg.user_type.clone());
    m.insert("user_properties", "{}".into());
    m.insert("version_type", "release".into());
    m.insert("natives_directory", path_str(&cfg.natives_dir));
    m.insert("launcher_name", LAUNCHER_NAME.into());
    m.insert("launcher_version", LAUNCHER_VERSION.into());
    m.insert("classpath", classpath.to_string());
    m.insert("resolution_width", cfg.width.to_string());
    m.insert("resolution_height", cfg.height.to_string());
    m
}

fn path_str(p: &Path) -> String {
    p.to_string_lossy().into_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    const MODERN: &str = r#"{
        "id": "1.20.4",
        "mainClass": "net.minecraft.client.main.Main",
        "arguments": {
            "game": [
                "--username", "${auth_player_name}",
                {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}],
                 "value": ["--width", "${resolution_width}", "--height", "${resolution_height}"]}
            ],
            "jvm": [
                "-Djava.library.path=${natives_directory}",
                "-cp", "${classpath}"
            ]
        },
        "libraries": [
            {"name": "org.lwjgl:lwjgl:3.3.1",
             "downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar", "url": "https://x/a.jar", "sha1": "aa", "size": 1}}},
            {"name": "org.lwjgl:lwjgl:3.3.1:natives-windows",
             "rules": [{"action": "allow", "os": {"name": "windows"}}]},
            {"name": "org.lwjgl:lwjgl-glfw:3.3.1",
             "rules": [{"action": "allow"}]}
        ],
        "downloads": {"client": {"url": "https://x/client.jar", "sha1": "bb", "size": 2}}
    }"#;

    fn cfg() -> LaunchConfig {
        LaunchConfig {
            java_path: "/data/jre/bin/java".into(),
            game_dir: "/data/game".into(),
            libraries_dir: "/data/libraries".into(),
            assets_dir: "/data/assets".into(),
            natives_dir: "/data/natives".into(),
            client_jar: "/data/versions/1.20.4/1.20.4.jar".into(),
            username: "Steve".into(),
            uuid: "uuid-1".into(),
            access_token: "0".into(),
            user_type: "msa".into(),
            memory_mb: 2048,
            width: 1920,
            height: 1080,
            context: Context::default(),
            extra_jvm_args: vec![],
        }
    }

    fn value_after<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
        let i = args.iter().position(|a| a == flag)?;
        args.get(i + 1).map(String::as_str)
    }

    #[test]
    fn builds_modern_command() {
        let v: VersionJson = serde_json::from_str(MODERN).unwrap();
        let cmd = build(&v, &cfg()).unwrap();

        assert_eq!(cmd.program, "/data/jre/bin/java");
        assert!(cmd.args.contains(&"-Xmx2048M".to_string()));
        assert_eq!(value_after(&cmd.args, "--username"), Some("Steve"));
        assert_eq!(value_after(&cmd.args, "--width"), Some("1920"));
        assert_eq!(value_after(&cmd.args, "--height"), Some("1080"));
        assert!(cmd
            .args
            .contains(&"net.minecraft.client.main.Main".to_string()));
        assert!(cmd
            .args
            .contains(&"-Djava.library.path=/data/natives".to_string()));
    }

    #[test]
    fn classpath_excludes_disallowed_libraries() {
        let v: VersionJson = serde_json::from_str(MODERN).unwrap();
        let cp = classpath(&v, &cfg(), &Context::default());

        assert!(cp.contains("/data/libraries/org/lwjgl/lwjgl-glfw/3.3.1/lwjgl-glfw-3.3.1.jar"));
        assert!(cp.contains("/data/libraries/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar"));
        assert!(!cp.contains("natives-windows"));
        assert!(cp.ends_with("/data/versions/1.20.4/1.20.4.jar"));
    }

    #[test]
    fn builds_legacy_command() {
        let legacy = r#"{
            "id": "1.7.10",
            "mainClass": "net.minecraft.client.main.Main",
            "minecraftArguments": "--username ${auth_player_name} --version ${version_name}",
            "libraries": []
        }"#;
        let v: VersionJson = serde_json::from_str(legacy).unwrap();
        let cmd = build(&v, &cfg()).unwrap();
        assert_eq!(value_after(&cmd.args, "--username"), Some("Steve"));
        assert_eq!(value_after(&cmd.args, "--version"), Some("1.7.10"));
        assert!(value_after(&cmd.args, "-cp").is_some());
    }
}
