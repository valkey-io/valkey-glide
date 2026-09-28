// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Compares the Valkey GLIDE Rust client and redis-rs
//! to ensure parity between their commands public surface.

mod types;

use regex::Regex;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use types::Argument;
use types::Generic;
use types::Method;
use types::RedisParity;

/// The redis-rs release GLIDE targets for parity.
// TODO #7058: bump to "1.7.0" and retarget the guard to *upstream* redis-rs
// (fetch `redis/src/commands/mod.rs` at the `redis-1.7.0` tag from GitHub).
const REDIS_RS_VERSION: &str = "0.25.2";

/// The vendored redis-rs fork's command table, relative to `rust/`.
// TODO #7058: Update once we get the command table from GitHub.
const REDIS_COMMAND_TABLE: &str = "../glide-core/redis-rs/redis/src/commands/mod.rs";

/// The vendored redis-rs fork's scan-iterator definitions, relative to `rust/`.
// TODO #7058: Update once we get the scan definitions from GitHub.
const REDIS_SCAN_METHODS: &str = "../glide-core/redis-rs/redis/src/commands/macros.rs";

/// The cached redis-rs parity snapshot, relative to `rust/`.
const REDIS_PARITY_JSON: &str = "src/parity_tests/redis_parity.json";

// --- tests --------------------------------------------------------------------------------------

/// Compares the methods defined by the Valkey GLIDE and redis-rs command tables
/// (via the `implement_commands` macro) and fails if they do not match.
#[test]
fn redis_parity_check() {
    match run_parity_check(REDIS_RS_VERSION) {
        Ok(summary) => println!("{summary}"),
        Err(problems) => panic!(
            "command table diverges from redis-rs — PARITY VIOLATIONS ({}):\n - {}",
            problems.len(),
            problems.join("\n - ")
        ),
    }
}

// --- parity check -------------------------------------------------------------------------------

/// Runs the parity check for the given specified redis-rs version and returns the results:
/// - `Ok` carries a human-readable summary.
/// - `Err` lists the command-surface divergences, one message per problem.
///
/// Panics if a source or data file can't be read, parsed, serialized, or written,
/// or if the cached snapshot records a different version than the one targeted.
fn run_parity_check(version: &str) -> Result<String, Vec<String>> {
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let redis = load_redis_parity(manifest, version);

    let glide_commands = &read(&manifest.join("src/commands/core.rs"));
    let glide_methods = parse_methods_map(glide_commands);
    let glide_scan_names = scan_method_names(glide_commands);

    let mut problems = Vec::new();
    problems.extend(compare_method_maps(&redis.methods, &glide_methods));
    problems.extend(compare_scan_method_names(
        &redis.scan_method_names,
        &glide_scan_names,
    ));

    if problems.is_empty() {
        Ok(format!(
            "parity OK: GLIDE methods match redis-rs {version} exactly"
        ))
    } else {
        Err(problems)
    }
}

/// Loads the cached redis-rs parity snapshot for the specified version, or
/// builds and saves it from the redis-rs source when the data file is missing.
///
/// Panics if the snapshot records a different version than the one targeted,
/// or if the data file can't be read, parsed, serialized, or written.
//
// TODO #7230: the snapshot is a trusted baseline — it is not validated against the
// vendored redis-rs source, and its `version` is stamped from `REDIS_RS_VERSION`
// rather than derived from the source. So a source signature change with a stale
// snapshot still passes, and regenerating after a version bump relabels the old
// source as the new version. Harden by re-parsing (or hash-verifying) the source
// each run, and make regeneration an explicit step rather than a side effect of
// `cargo test` writing into `src/`.
fn load_redis_parity(manifest: &Path, version: &str) -> RedisParity {
    let data_path = manifest.join(REDIS_PARITY_JSON);

    if data_path.exists() {
        let redis: RedisParity = serde_json::from_str(&read(&data_path))
            .unwrap_or_else(|e| panic!("cannot parse {}: {e}", data_path.display()));

        assert!(
            redis.version == version,
            "redis-rs parity baseline is version {}, but {version} is specified; \
             delete {} to regenerate it",
            redis.version,
            data_path.display()
        );

        return redis;
    }

    // Data file missing: build and save the snapshot from the redis-rs source.
    let commands = read(&manifest.join(REDIS_COMMAND_TABLE));
    let scan = read(&manifest.join(REDIS_SCAN_METHODS));
    let redis = RedisParity {
        version: version.to_string(),
        methods: parse_methods_map(&commands),
        scan_method_names: scan_method_names(&scan),
    };

    let json = serde_json::to_string_pretty(&redis)
        .unwrap_or_else(|e| panic!("cannot serialize parity data: {e}"));
    std::fs::write(&data_path, json)
        .unwrap_or_else(|e| panic!("cannot write {}: {e}", data_path.display()));

    redis
}

/// Parse the command table methods from the given source, indexed by method name.
/// Panics if the command table cannot be parsed.
fn parse_methods_map(src: &str) -> BTreeMap<String, Method> {
    // Extract command table (the `implement_commands! { ... }` macro body).
    let start = src
        .find("implement_commands! {")
        .unwrap_or_else(|| panic!("command table not found"));
    let rest = &src[start..];
    let end = rest
        .lines()
        .scan(0usize, |offset, line| {
            let line_start = *offset;
            *offset += line.len() + 1;
            Some((line_start, line))
        })
        .find(|(off, line)| *off > 0 && line.starts_with('}'))
        .map(|(off, _)| off)
        .unwrap_or(rest.len());

    let implement_commands_macro = &rest[..end];
    parse_implement_commands_macro(implement_commands_macro)
}

/// Parse the command table methods from the given `implement_commands` macro,
/// indexed by method name.
fn parse_implement_commands_macro(body: &str) -> BTreeMap<String, Method> {
    // The optional `-> (...)`/`-> Generic` return annotation (redis-rs's typed
    // API) is captured verbatim when present; tables without it leave it empty.
    let sig_re =
        Regex::new(r"^fn\s+([a-z_0-9]+)\s*(?:<([^>]*)>)?\s*\((.*?)\)(?:\s*->\s*(.+?))?\s*\{")
            .expect("valid regex");

    let lines: Vec<&str> = body.lines().collect();
    let mut out = BTreeMap::new();
    let mut li = 0usize;
    while li < lines.len() {
        if !lines[li].trim_start().starts_with("fn ") {
            li += 1;
            continue;
        }

        // Accumulate the signature until its opening brace.
        let mut sig = lines[li].to_string();
        while !sig.contains('{') {
            li += 1;
            sig.push(' ');
            sig.push_str(lines[li].trim());
        }
        li += 1;

        // Skip the method body by brace counting.
        let count = |s: &str, c: char| s.matches(c).count() as i64;
        let mut depth = count(&sig, '{') - count(&sig, '}');
        while depth > 0 {
            depth += count(lines[li], '{') - count(lines[li], '}');
            li += 1;
        }
        let sig1 = sig.split_whitespace().collect::<Vec<_>>().join(" ");
        let caps = sig_re.captures(&sig1).unwrap_or_else(|| {
            panic!(
                "unparseable command table entry (did the table style change on a rev \
                 bump?):\n  {}",
                &sig1[..sig1.len().min(160)]
            )
        });

        let method = Method {
            name: caps[1].to_string(),
            generics: parse_generics(caps.get(2).map_or("", |m| m.as_str())),
            args: parse_args(caps[3].trim()),
            return_type: caps.get(4).map(|m| m.as_str().trim().to_string()),
        };
        out.insert(method.name.clone(), method);
    }
    out
}

/// Parse a generic parameter list.
fn parse_generics(generics: &str) -> Vec<Generic> {
    generics
        .split(',')
        .map(str::trim)
        .filter(|p| !p.is_empty())
        .map(|g| match g.split_once(':') {
            Some((name, bounds)) => Generic {
                name: name.trim().to_string(),
                bound: Some(bounds.trim().to_string()),
            },
            None => Generic {
                name: g.to_string(),
                bound: None,
            },
        })
        .collect()
}

/// Parse an argument list into `[Argument]`, splitting on top-level commas only
/// (depth-aware over `(<[`/`)>]`). Each type is captured verbatim (the signature
/// was already whitespace-joined into one line before this point).
fn parse_args(args: &str) -> Vec<Argument> {
    let mut parts: Vec<String> = Vec::new();
    let mut depth = 0i32;
    let mut cur = String::new();
    for ch in args.chars() {
        match ch {
            '(' | '<' | '[' => depth += 1,
            ')' | '>' | ']' => depth -= 1,
            _ => {}
        }
        if ch == ',' && depth == 0 {
            parts.push(std::mem::take(&mut cur));
        } else {
            cur.push(ch);
        }
    }
    if !cur.trim().is_empty() {
        parts.push(cur);
    }
    parts
        .iter()
        .map(|a| {
            let (name, ty) = a
                .split_once(':')
                .unwrap_or_else(|| panic!("argument without a type annotation: {a}"));
            Argument {
                name: name.trim().to_string(),
                type_name: ty.split_whitespace().collect::<Vec<_>>().join(" "),
            }
        })
        .collect()
}

/// Extract the names of every scan method declared in the given source.
fn scan_method_names(src: &str) -> BTreeSet<String> {
    let re = Regex::new(r"fn\s+([a-z_0-9]*scan[a-z_0-9]*)").expect("valid regex");
    let mut counts: BTreeMap<String, usize> = BTreeMap::new();
    for caps in re.captures_iter(src) {
        *counts.entry(caps[1].to_string()).or_insert(0) += 1;
    }

    // Each scan method must be defined once in the async trait and once in the blocking
    // trait, so every name must appear exactly twice. Panics otherwise.
    //
    // TODO #7230: harden this for the redis-rs 1.7.0 retarget. It runs on both the
    // redis-rs and GLIDE sources and panics on violation, but upstream 1.7.0 declares
    // each scan method once (in a macro expanded by both traits), so the count check
    // must become GLIDE-only and report through `problems` rather than panic (a panic
    // aborts before other divergences surface). The name regex also matches any
    // `fn *scan*`, so an unrelated helper would be miscounted — tighten it then too.
    for (name, count) in &counts {
        assert!(
            *count == 2,
            "scan method `{name}` is declared {count} time(s), expected 2 \
             (an async and a blocking definition)"
        );
    }

    counts.into_keys().collect()
}

/// Compares the given redis-rs and Valkey GLIDE method maps.
/// Returns one message per problem; empty means they match.
fn compare_method_maps(
    redis: &BTreeMap<String, Method>,
    glide: &BTreeMap<String, Method>,
) -> Vec<String> {
    let mut problems = Vec::new();

    // Verify that all redis-rs methods are implemented by GLIDE.
    for (name, method) in redis {
        match glide.get(name) {
            None => problems.push(format!("MISSING method in GLIDE: {name}")),
            Some(ours) if !compare_methods(method, ours) => problems.push(format!(
                "SIGNATURE DIFF {name}:\n     redis-rs: {method:?}\n     GLIDE: {ours:?}"
            )),
            _ => {}
        }
    }

    // Verify that GLIDE does not implement any extra methods.
    for name in glide.keys() {
        if !redis.contains_key(name) {
            problems.push(format!("EXTRA method in GLIDE: {name}"));
        }
    }

    problems
}

/// Compares the given redis-rs and Valkey GLIDE scan method names.
/// Only names are compared: the clients intentionally have different scan method signatures.
/// Returns one message per problem; empty means they match.
fn compare_scan_method_names(redis: &BTreeSet<String>, glide: &BTreeSet<String>) -> Vec<String> {
    let mut problems = Vec::new();

    // Verify that all redis-rs scan methods are implemented by GLIDE.
    for name in redis {
        if !glide.contains(name) {
            problems.push(format!("MISSING scan method in GLIDE: {name}"));
        }
    }

    // Verify that GLIDE does not implement any extra scan methods.
    for name in glide {
        if !redis.contains(name) {
            problems.push(format!("EXTRA scan method in GLIDE: {name}"));
        }
    }

    problems
}

/// Returns `true` if the normalized redis-rs and GLIDE methods match.
/// Fields are checked in signature declaration order (generics, name, args, return type).
fn compare_methods(redis: &Method, glide: &Method) -> bool {
    // Verify that generics match.
    if redis.generics.len() != glide.generics.len() {
        return false;
    }

    if !redis.generics.iter().zip(&glide.generics).all(|(r, g)| {
        r.name == g.name
            && r.bound.as_deref().map(bound_from_redis_to_glide).as_deref() == g.bound.as_deref()
    }) {
        return false;
    }

    // Verify that names match.
    if redis.name != glide.name {
        return false;
    }

    // Verify that arguments match.
    if redis.args != glide.args {
        return false;
    }

    // Verify that return types match.
    // TODO #7058: redis-rs 1.7.0 introduces typed return types that may need to be normalized.
    if redis.return_type != glide.return_type {
        return false;
    }

    true
}

/// Maps the given redis-rs bound to the corresponding GLIDE bound.
fn bound_from_redis_to_glide(bound: &str) -> String {
    bound
        .replace("FromRedisValue", "FromValkeyValue")
        .replace("ToSingleRedisArg", "ToSingleValkeyArg")
        .replace("ToRedisArgs", "ToValkeyArgs")
}

/// Read the file at the given path and returns its contents.
fn read(path: &Path) -> String {
    std::fs::read_to_string(path).unwrap_or_else(|e| panic!("cannot read {}: {e}", path.display()))
}
