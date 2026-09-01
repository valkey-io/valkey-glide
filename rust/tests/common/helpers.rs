// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Key generation and server capability/version helpers.

use std::sync::atomic::{AtomicU64, Ordering};

/// A process-and-thread-unique key with the given prefix, so tests never
/// collide even when running concurrently across RESP2/RESP3 variants.
pub fn key(prefix: &str) -> String {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let n = COUNTER.fetch_add(1, Ordering::Relaxed);
    let t = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    format!("{prefix}:{t}:{n}")
}

/// A process-unique key wrapped in a Valkey **hash tag** so that all keys sharing
/// the same `tag` map to the same cluster slot. Required for multi-key commands
/// (MSET/MGET, RENAME, SINTERSTORE, …) to be valid in cluster mode; harmless in
/// standalone (the braces are just part of the key name). Example:
/// `tkey("grp", "a")` -> `{grp}:a:<ts>:<n>`.
pub fn tkey(tag: &str, name: &str) -> String {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let n = COUNTER.fetch_add(1, Ordering::Relaxed);
    let t = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    format!("{{{tag}}}:{name}:{t}:{n}")
}

/// Parse a `"major.minor.patch"` version string into a tuple.
fn parse_version(s: &str) -> Option<(u32, u32, u32)> {
    let mut it = s.trim().split('.');
    let major = it.next()?.parse().ok()?;
    let minor = it.next().unwrap_or("0").parse().unwrap_or(0);
    // patch may carry a suffix (e.g. "3-rc1"); take leading digits only.
    let patch = it
        .next()
        .unwrap_or("0")
        .chars()
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .parse()
        .unwrap_or(0);
    Some((major, minor, patch))
}

/// Recursively collect all string-shaped content from a [`glide::Value`] into
/// `out`. Handles the flat bulk string a standalone `INFO` returns AND the
/// multi-node Map/Array a cluster client returns (so the version can be found in
/// either shape).
fn collect_value_text(v: &glide::Value, out: &mut String) {
    use glide::Value;
    match v {
        Value::BulkString(b) => {
            out.push_str(&String::from_utf8_lossy(b));
            out.push('\n');
        }
        Value::SimpleString(s) => {
            out.push_str(s);
            out.push('\n');
        }
        Value::VerbatimString { text, .. } => {
            out.push_str(text);
            out.push('\n');
        }
        Value::Array(items) | Value::Set(items) => {
            for it in items {
                collect_value_text(it, out);
            }
        }
        Value::Map(pairs) => {
            for (k, val) in pairs {
                collect_value_text(k, out);
                collect_value_text(val, out);
            }
        }
        _ => {}
    }
}

/// Query the connected server's version via `INFO server`. Works on standalone
/// AND cluster clients: `custom_command` returns the raw reply, which we walk to
/// find `valkey_version:` / `redis_version:` regardless of whether it is a flat
/// bulk string (standalone) or a per-node Map (cluster).
pub async fn server_version<C>(c: &C) -> Option<(u32, u32, u32)>
where
    C: glide::CustomCommand + Sync,
{
    let reply = c.custom_command(&["INFO", "server"]).await.ok()?;
    let mut text = String::new();
    collect_value_text(&reply, &mut text);
    // Prefer `valkey_version` — Valkey pins `redis_version` to a compat value
    // (7.2.4) on ALL releases, so redis_version is useless for gating on Valkey.
    // Fall back to redis_version only when there is no valkey_version (real Redis).
    for key in ["valkey_version:", "redis_version:"] {
        for line in text.lines() {
            if let Some(v) = line.trim().strip_prefix(key) {
                return parse_version(v);
            }
        }
    }
    None
}

/// Whether the server recognises `name` (via `COMMAND INFO`). This is a
/// version- and product-agnostic capability check — more robust than version
/// math for commands whose availability differs between Redis and Valkey
/// releases (e.g. hash-field TTL). Fails **closed** (returns `false`) if the
/// capability cannot be determined, so gated tests SKIP rather than error.
pub async fn command_exists<C>(c: &C, name: &str) -> bool
where
    C: glide::CustomCommand + Sync,
{
    match c.custom_command(&["COMMAND", "INFO", name]).await {
        Ok(v) => command_info_present(&v),
        Err(_) => false,
    }
}

/// `COMMAND INFO <name>` returns `[[ <details> ]]` when known and `[nil]` when
/// unknown. On cluster it may be a per-node Map. Present ⇔ a non-empty details
/// array exists somewhere in the reply.
fn command_info_present(v: &glide::Value) -> bool {
    use glide::Value;
    match v {
        Value::Array(items) => items
            .iter()
            .any(|it| matches!(it, Value::Array(inner) if !inner.is_empty())),
        Value::Map(pairs) => pairs.iter().any(|(_, val)| command_info_present(val)),
        _ => false,
    }
}

/// True when the server version is strictly below `min`. Returns `false` if the
/// version cannot be determined (fail-open: run the test rather than skip).
pub async fn version_below<C>(c: &C, min: (u32, u32, u32)) -> bool
where
    C: glide::CustomCommand + Sync,
{
    match server_version(c).await {
        Some(v) => v < min,
        None => false,
    }
}
