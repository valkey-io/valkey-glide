// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Compares the Valkey GLIDE Rust client and redis-rs
//! to ensure parity between their commands public surface.

mod types;

use regex::Regex;
use std::collections::BTreeMap;
use std::path::Path;
use types::Argument;
use types::Difference;
use types::Generic;
use types::Method;
use types::RedisParity;

// --- constants --------------------------------------------------------------------------------------

/// Constants for the redis-rs release that GLIDE targets for parity.
const REDIS_RS_VERSION: &str = "1.7.0";

/// The source URL of the redis-rs command sources, with `{version}` and `{file}` placeholders.
const REDIS_RS_SOURCE_URL: &str =
    "https://raw.githubusercontent.com/redis-rs/redis-rs/redis-{version}/{file}";

/// The redis-rs command table location.
const REDIS_COMMAND_TABLE: &str = "redis/src/commands/mod.rs";

/// The redis-rs scan-iterator definitions.
const REDIS_SCAN_METHODS: &str = "redis/src/commands/macros.rs";

/// The cached redis-rs parity snapshot, relative to `rust/`.
const REDIS_PARITY_JSON: &str = "src/parity_tests/redis_parity.json";

/// The differences from redis-rs, relative to `rust/`, indexed by method name.
const DIFFERENCES_JSON: &str = "src/parity_tests/differences.json";

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
/// - `Err` lists the parity problems, one message per problem.
///
/// Panics if a source or data file can't be read, parsed, serialized, or written,
/// or if the cached snapshot records a different version than the one targeted.
fn run_parity_check(version: &str) -> Result<String, Vec<String>> {
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let redis_parity = load_redis_parity(manifest, version);
    let differences = load_differences(manifest);
    let glide_src = &read(&manifest.join("src/commands/core.rs"));

    let glide_command_table_methods = parse_command_table_methods(glide_src);
    let glide_scan_methods = parse_scan_methods(glide_src, GLIDE_SCAN_DEFINITIONS);

    // Verify the list of differences.
    let mut problems = compare_differences(
        &differences,
        &[
            &redis_parity.command_table_methods,
            &redis_parity.scan_methods,
        ],
        &[&glide_command_table_methods, &glide_scan_methods],
    );

    // Compare command table methods.
    problems.extend(compare_method_maps(
        &redis_parity.command_table_methods,
        &glide_command_table_methods,
        &differences,
    ));

    // Compare scan methods.
    problems.extend(compare_method_maps(
        &redis_parity.scan_methods,
        &glide_scan_methods,
        &differences,
    ));

    if problems.is_empty() {
        let count = |redis: bool, glide: bool| {
            differences
                .values()
                .filter(|d| d.redis.is_some() == redis && d.glide.is_some() == glide)
                .count()
        };
        Ok(format!(
            "parity OK: GLIDE methods match redis-rs {version}, with {} documented \
             divergence(s), {} missing method(s) and {} extra method(s)",
            count(true, true),
            count(true, false),
            count(false, true)
        ))
    } else {
        Err(problems)
    }
}

/// Loads the cached redis-rs parity snapshot for the specified version, or
/// builds and saves it from the redis-rs source when the data file is missing.
///
/// Panics if the snapshot records a different version than the one targeted,
/// if the data file can't be read, parsed, serialized, or written, or if the
/// redis-rs source can't be fetched.
//
// TODO #7058: the snapshot is a trusted baseline — it is not validated against
// the redis-rs source, and its `version` is stamped from `REDIS_RS_VERSION`
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
    let commands_src = fetch_redis_source(REDIS_COMMAND_TABLE);
    let scan_src = fetch_redis_source(REDIS_SCAN_METHODS);
    let redis = RedisParity {
        version: version.to_string(),
        command_table_methods: parse_command_table_methods(&commands_src),
        scan_methods: parse_scan_methods(&scan_src, REDIS_SCAN_DEFINITIONS),
    };

    let json = serde_json::to_string_pretty(&redis)
        .unwrap_or_else(|e| panic!("cannot serialize parity data: {e}"));
    std::fs::write(&data_path, json)
        .unwrap_or_else(|e| panic!("cannot write {}: {e}", data_path.display()));

    redis
}

/// Loads the cached differences, indexed by method name.
/// Panics if the data file can't be read or parsed.
fn load_differences(manifest: &Path) -> BTreeMap<String, Difference> {
    let data_path = manifest.join(DIFFERENCES_JSON);
    serde_json::from_str(&read(&data_path))
        .unwrap_or_else(|e| panic!("cannot parse {}: {e}", data_path.display()))
}

/// Fetches the given redis-rs source file from GitHub.
/// Panics if the file can't be fetched.
fn fetch_redis_source(file: &str) -> String {
    let url = REDIS_RS_SOURCE_URL
        .replace("{version}", REDIS_RS_VERSION)
        .replace("{file}", file);
    let output = std::process::Command::new("curl")
        .args(["--fail", "--silent", "--show-error", "--location", &url])
        .output()
        .unwrap_or_else(|e| panic!("cannot run curl to fetch {url}: {e}"));
    assert!(
        output.status.success(),
        "cannot fetch {url}: {}",
        String::from_utf8_lossy(&output.stderr).trim()
    );
    String::from_utf8(output.stdout).unwrap_or_else(|e| panic!("{url} is not UTF-8: {e}"))
}

/// Parse the command table methods from the given source, indexed by method name.
/// Panics if the command table cannot be parsed.
fn parse_command_table_methods(src: &str) -> BTreeMap<String, Method> {
    // Extract command table (the `implement_commands! { ... }` macro body).
    let start = src
        .find("implement_commands! {")
        .unwrap_or_else(|| panic!("command table not found"));
    let rest = &src[start..];

    // The macro body ends at the first line that *starts* with `}`.
    // TODO #7058: for robust parsing (comment/string/brace-safe), tokenize with
    // `proc-macro2` and take the macro's brace `Group` instead of this heuristic.
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

/// Parse the scan methods from the given source, indexed by method name.
/// Panics if the scan methods cannot be parsed.
fn parse_scan_methods(src: &str) -> BTreeMap<String, Method> {
    let re = Regex::new(r"(?s)fn\s+([a-z_0-9]*scan[a-z_0-9]*)\s*<([^>]*)>\s*\(([^)]*)\)")
        .expect("valid regex");

    // Populate map from scan method names to the corresponding async and blocking methods.
    let mut scan_methods_map: BTreeMap<String, Vec<Method>> = BTreeMap::new();
    for caps in re.captures_iter(src) {
        let name = caps[1].to_string();
        let method = Method {
            name: name.clone(),
            generics: normalize_scan_generics(parse_generics(&caps[2])),
            args: parse_scan_args(&caps[3]),
            return_type: None,
        };
        scan_methods_map.entry(name).or_default().push(method);
    }

    // Verify that each scan method is defined for both async and blocking clients.
    // TODO #7058: this two-flavor check runs on both the redis-rs and GLIDE sources.
    // Upstream redis-rs 1.7.0 declares each scan method once (a macro expanded into both
    // traits), so on retarget the "both flavors present" expectation must apply to the
    // GLIDE source only.
    scan_methods_map
        .into_iter()
        .map(|(name, sigs)| {
            assert!(
                sigs.len() == 2 && sigs[0] == sigs[1],
                "scan method `{name}` must have matching async and blocking definitions, \
                 found {}: {sigs:?}",
                sigs.len()
            );
            (name, sigs.into_iter().next().expect("checked non-empty"))
        })
        .collect()
}

/// Parse a scan method's argument list.
fn parse_scan_args(args: &str) -> Vec<Argument> {
    let without_receiver = args
        .split(',')
        // Drop `self` entry.
        .filter(|a| !a.contains("self"))
        .collect::<Vec<_>>()
        .join(",");
    if without_receiver.trim().is_empty() {
        return Vec::new();
    }
    parse_args(without_receiver.trim())
}

/// Compares the given redis-rs and Valkey GLIDE methods.
/// Returns one message per problem; empty means they match.
fn compare_method_maps(
    redis: &BTreeMap<String, Method>,
    glide: &BTreeMap<String, Method>,
) -> Vec<String> {
    let mut problems = Vec::new();

    // Verify that all redis-rs methods are implemented by GLIDE.
    for (name, method) in redis {
        match glide.get(name) {
            None if MISSING_METHODS.contains(&name.as_str()) => {}
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

/// Verifies that every missing method entry is defined by one of the redis-rs method maps
/// and by none of the GLIDE ones, so the list can't go stale. Returns one message per problem.
fn compare_missing_methods(
    redis: &[&BTreeMap<String, Method>],
    glide: &[&BTreeMap<String, Method>],
) -> Vec<String> {
    let defined =
        |maps: &[&BTreeMap<String, Method>], name: &str| maps.iter().any(|m| m.contains_key(name));
    MISSING_METHODS
        .iter()
        .filter_map(|name| {
            if defined(glide, name) {
                Some(format!(
                    "STALE MISSING_METHODS entry (now implemented by GLIDE): {name}"
                ))
            } else if !defined(redis, name) {
                Some(format!(
                    "STALE MISSING_METHODS entry (not defined by redis-rs): {name}"
                ))
            } else {
                None
            }
        })
        .collect()
}

/// Normalizes scan generics so the async and blocking definitions compare equal:
/// drop the `'s` lifetime generic, and strip `Send` and lifetime bounds from each bound.
fn normalize_scan_generics(generics: Vec<Generic>) -> Vec<Generic> {
    generics
        .into_iter()
        .filter(|g| !g.name.starts_with('\''))
        .map(|g| Generic {
            name: g.name,
            bound: g.bound.map(|b| {
                b.split('+')
                    .map(str::trim)
                    .filter(|part| !part.is_empty() && *part != "Send" && !part.starts_with('\''))
                    .collect::<Vec<_>>()
                    .join(" + ")
            }),
        })
        .collect()
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
