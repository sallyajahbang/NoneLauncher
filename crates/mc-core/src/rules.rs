//! Evaluation of Mojang "rules" blocks (used by libraries and arguments).

use std::collections::HashMap;

use serde::Deserialize;

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

/// The platform the launcher is running on.
///
/// Android is treated as `linux` for Mojang's rules, because the Linux native
/// libraries (LWJGL, etc.) are the ones that match the Android runtime.
#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct Context {
    pub os_name: String,
    pub arch: String,
    pub features: HashMap<String, bool>,
}

impl Default for Context {
    fn default() -> Self {
        let mut features = HashMap::new();
        features.insert("has_custom_resolution".to_string(), true);
        Self {
            os_name: "linux".into(),
            arch: "arm64".into(),
            features,
        }
    }
}

impl Rule {
    fn matches(&self, ctx: &Context) -> bool {
        if let Some(os) = &self.os {
            if os.name.as_deref().is_some_and(|n| n != ctx.os_name) {
                return false;
            }
            if os.arch.as_deref().is_some_and(|a| a != ctx.arch) {
                return false;
            }
        }
        if let Some(features) = &self.features {
            for (key, wanted) in features {
                let have = ctx.features.get(key).copied().unwrap_or(false);
                if have != *wanted {
                    return false;
                }
            }
        }
        true
    }
}

/// Mojang semantics: no rules means allowed. Otherwise the last matching rule
/// decides, and if nothing matches the entry is disallowed.
pub fn is_allowed(rules: &[Rule], ctx: &Context) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if rule.matches(ctx) {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rule(json: &str) -> Rule {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn empty_rules_allow() {
        assert!(is_allowed(&[], &Context::default()));
    }

    #[test]
    fn windows_only_rule_is_disallowed_on_linux() {
        let rules = [rule(r#"{"action":"allow","os":{"name":"windows"}}"#)];
        assert!(!is_allowed(&rules, &Context::default()));
    }

    #[test]
    fn linux_rule_matches_android_context() {
        let rules = [rule(r#"{"action":"allow","os":{"name":"linux"}}"#)];
        assert!(is_allowed(&rules, &Context::default()));
    }

    #[test]
    fn disallow_after_allow_wins() {
        let rules = [
            rule(r#"{"action":"allow"}"#),
            rule(r#"{"action":"disallow","os":{"name":"linux"}}"#),
        ];
        assert!(!is_allowed(&rules, &Context::default()));
    }
}
