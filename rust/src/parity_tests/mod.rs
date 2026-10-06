// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Compares the Valkey GLIDE Rust client and redis-rs
//! to ensure parity between their commands public surface.

mod types;

use regex::Regex;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
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
    // [1] Load data
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let differences = load_differences(manifest);

    let glide_src = &read(&manifest.join("src/commands/core.rs"));
    let glide_methods = parse_methods(glide_src, glide_src);

    let redis_parity = load_redis_parity(manifest, version);
    let redis_methods = redis_parity.methods;

    // [2] Run parity check
    let problems = compare_methods(&redis_methods, &glide_methods, &differences);

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
// TODO #7288: the snapshot is a trusted baseline — it is not validated against
// the redis-rs source, so a hand-edited or stale snapshot still passes. Harden by
// re-parsing (or hash-verifying) the source each run, and make regeneration an
// explicit step.
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
        methods: parse_methods(&commands_src, &scan_src),
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

/// Parses the command methods from the given command table and scan source.
/// Returns the parsed methods, indexed by method name.
/// Panics if either cannot be parsed, or if a method name appears in both.
fn parse_methods(command_table_src: &str, scan_src: &str) -> BTreeMap<String, Method> {
    let mut methods = parse_command_table_methods(command_table_src);
    for (name, method) in parse_scan_methods(scan_src) {
        assert!(
            !methods.contains_key(&name),
            "method `{name}` is both a command-table and a scan method"
        );
        methods.insert(name, method);
    }
    methods
}

/// Parse the command table methods from the given source, indexed by method name.
/// Panics if the command table cannot be parsed.
fn parse_command_table_methods(src: &str) -> BTreeMap<String, Method> {
    let implement_commands_macro = extract_macro(src, "implement_commands! {")
        .unwrap_or_else(|| panic!("command table not found"));

    // The optional `-> (...)`/`-> Generic` return annotation (redis-rs's typed
    // API) is captured verbatim when present; tables without it leave it empty.
    let sig_re =
        Regex::new(r"^fn\s+([a-z_0-9]+)\s*(?:<([^>]*)>)?\s*\((.*?)\)(?:\s*->\s*(.+?))?\s*\{")
            .expect("valid regex");

    let lines: Vec<&str> = implement_commands_macro.lines().collect();
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

/// Returns the macro with the given name, or `None` if not found.
/// The macro ends at the first later line that *starts* with `}`.
//
// TODO #7288: for robust parsing (comment/string/brace-safe), tokenize with
// `proc-macro2` and take the macro's brace `Group` instead of this heuristic.
fn extract_macro<'a>(src: &'a str, name: &str) -> Option<&'a str> {
    let rest = &src[src.find(name)?..];
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
    Some(&rest[..end])
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

/// Parse the scan methods from the `implement_iterators` macro in the given source,
/// indexed by method name.
/// Panics if the scan methods cannot be parsed.
fn parse_scan_methods(src: &str) -> BTreeMap<String, Method> {
    let implement_iterators_macro = extract_macro(src, "macro_rules! implement_iterators {")
        .unwrap_or_else(|| panic!("scan iterators not found"));
    let re = Regex::new(r"(?s)fn\s+([a-z_0-9]*scan[a-z_0-9]*)\s*<([^>]*)>\s*\(([^)]*)\)")
        .expect("valid regex");

    let mut methods = BTreeMap::new();
    for caps in re.captures_iter(implement_iterators_macro) {
        let method = Method {
            name: caps[1].to_string(),
            generics: normalize_scan_generics(parse_generics(&caps[2])),
            args: parse_scan_args(&caps[3]),
            return_type: None,
        };
        let name = method.name.clone();
        assert!(
            methods.insert(name.clone(), method).is_none(),
            "scan method `{name}` is defined more than once"
        );
    }
    methods
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

/// Compares the given redis-rs and Valkey GLIDE methods, allowing exactly the expected
/// differences. Returns one message per problem; empty means they match.
fn compare_methods(
    redis: &BTreeMap<String, Method>,
    glide: &BTreeMap<String, Method>,
    differences: &BTreeMap<String, Difference>,
) -> Vec<String> {
    let mut problems = Vec::new();

    // [1] Verify differences
    // ----------------------

    // Verify that recorded differences are still accurate.
    // This prevents `differences.json` from becoming stale.
    for (name, diff) in differences {
        let redis_method = redis.get(name);
        let glide_method = glide.get(name);

        let detail = if diff.redis.is_none() && diff.glide.is_none() {
            "neither a redis-rs nor a GLIDE method".to_string()
        } else if redis_method != diff.redis.as_ref() {
            match redis_method {
                Some(meth) => format!("redis-rs now declares {meth:?}"),
                None => "redis-rs no longer declares it".to_string(),
            }
        } else if glide_method != diff.glide.as_ref() {
            match glide_method {
                Some(meth) => format!("GLIDE now declares {meth:?}"),
                None => "GLIDE no longer declares it".to_string(),
            }
        } else if let (Some(r), Some(g)) = (redis_method, glide_method)
            && methods_match(r, g)
        {
            "the redis-rs and GLIDE methods match".to_string()
        } else {
            continue;
        };
        problems.push(format!(
            "STALE difference ({detail}): {name} — recorded reason: {}",
            diff.reason
        ));
    }

    // [2] Verify methods
    // ------------------

    // Verify that GLIDE implements exactly the redis-rs methods,
    // except for those with an expected difference.
    let names: BTreeSet<&String> = redis.keys().chain(glide.keys()).collect();
    for name in names {
        if differences.contains_key(name) {
            continue;
        }
        match (redis.get(name), glide.get(name)) {
            (Some(_), None) => problems.push(format!("MISSING method in GLIDE: {name}")),
            (None, Some(_)) => problems.push(format!("EXTRA method in GLIDE: {name}")),
            (Some(theirs), Some(ours)) if !methods_match(theirs, ours) => problems.push(format!(
                "SIGNATURE DIFF {name}:\n     redis-rs: {theirs:?}\n     GLIDE: {ours:?}"
            )),
            _ => {}
        }
    }

    problems
}

/// Normalizes scan generics so GLIDE's compare equal to redis-rs's, which has neither:
/// - drop lifetime generics (e.g. `'s` in `scan<'s, RV>`);
/// - strip lifetime bounds (e.g. `RV: FromValkeyValue + 's` becomes `RV: FromValkeyValue`).
fn normalize_scan_generics(generics: Vec<Generic>) -> Vec<Generic> {
    generics
        .into_iter()
        .filter(|g| !g.name.starts_with('\''))
        .map(|g| Generic {
            name: g.name,
            bound: g.bound.map(|b| {
                b.split('+')
                    .map(str::trim)
                    .filter(|part| !part.is_empty() && !part.starts_with('\''))
                    .collect::<Vec<_>>()
                    .join(" + ")
            }),
        })
        .collect()
}

/// Returns `true` if the normalized redis-rs and GLIDE methods match.
/// Fields are checked in signature declaration order (generics, name, args, return type).
fn methods_match(redis: &Method, glide: &Method) -> bool {
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
    if redis.args.len() != glide.args.len() {
        return false;
    }

    if !redis
        .args
        .iter()
        .zip(&glide.args)
        .all(|(r, g)| r.name == g.name && type_from_redis_to_glide(&r.type_name) == g.type_name)
    {
        return false;
    }

    // Verify that return types match.
    redis
        .return_type
        .as_deref()
        .map(type_from_redis_to_glide)
        .as_deref()
        == glide.return_type.as_deref()
}

/// Maps the given redis-rs bound to the corresponding GLIDE bound.
fn bound_from_redis_to_glide(bound: &str) -> String {
    bound
        .replace("FromRedisValue", "FromValkeyValue")
        .replace("ToSingleRedisArg", "ToSingleValkeyArg")
        .replace("ToRedisArgs", "ToValkeyArgs")
}

/// Maps the given redis-rs type to the corresponding GLIDE type.
fn type_from_redis_to_glide(ty: &str) -> String {
    ty.replace("geo::Unit", "GeoUnit")
        .replace("geo::Coord", "GeoCoord")
        .replace("streams::", "")
}

/// Read the file at the given path and returns its contents.
fn read(path: &Path) -> String {
    std::fs::read_to_string(path).unwrap_or_else(|e| panic!("cannot read {}: {e}", path.display()))
}

// --- tests --------------------------------------------------------------------------------------

/// Verifies that the comparison reports a missing, an extra, and a mismatched method.
#[test]
fn compare_methods_reports_undocumented_differences() {
    let redis = methods([method("get", "(String)"), method("set", "(())")]);
    let glide = methods([method("get", "(usize)"), method("extra", "(())")]);

    let problems = compare_methods(&redis, &glide, &BTreeMap::new());

    assert_eq!(problems.len(), 3, "{problems:?}");
    assert!(problems.iter().any(|p| p == "EXTRA method in GLIDE: extra"));
    assert!(problems.iter().any(|p| p == "MISSING method in GLIDE: set"));
    assert!(problems.iter().any(|p| p.starts_with("SIGNATURE DIFF get")));
}

/// Verifies that pinned differences are allowed, and reported when they go stale.
#[test]
fn compare_methods_validates_differences() {
    let redis = methods([method("get", "(String)"), method("set", "(())")]);
    let glide = methods([method("get", "(usize)"), method("extra", "(())")]);
    let differences = BTreeMap::from([
        difference(
            "get",
            Some(method("get", "(String)")),
            Some(method("get", "(usize)")),
        ),
        difference("set", Some(method("set", "(())")), None),
        difference("extra", None, Some(method("extra", "(())"))),
    ]);
    assert!(compare_methods(&redis, &glide, &differences).is_empty());

    // A difference that no longer holds on either side, or names neither side.
    let stale = BTreeMap::from([
        difference(
            "get",
            Some(method("get", "(Vec<String>)")),
            Some(method("get", "(usize)")),
        ),
        difference(
            "set",
            Some(method("set", "(())")),
            Some(method("set", "(())")),
        ),
        difference("none", None, None),
    ]);
    let problems = compare_methods(&redis, &glide, &stale);
    assert!(
        problems
            .iter()
            .any(|p| p.starts_with("STALE difference (redis-rs now declares"))
    );
    assert!(
        problems
            .iter()
            .any(|p| p.starts_with("STALE difference (GLIDE no longer declares it): set"))
    );
    assert!(
        problems.iter().any(
            |p| p.starts_with("STALE difference (neither a redis-rs nor a GLIDE method): none")
        )
    );

    // A difference whose redis-rs and GLIDE methods match.
    let glide = methods([method("get", "(String)")]);
    let matching = BTreeMap::from([difference(
        "get",
        Some(method("get", "(String)")),
        Some(method("get", "(String)")),
    )]);
    let problems = compare_methods(&redis, &glide, &matching);
    assert!(
        problems
            .iter()
            .any(|p| p.starts_with("STALE difference (the redis-rs and GLIDE methods match): get")),
        "{problems:?}"
    );
}

/// Verifies that GLIDE's scan lifetime generic and bounds are normalized away.
#[test]
fn normalize_scan_generics_drops_lifetimes() {
    let generics = normalize_scan_generics(parse_generics(
        "'s, K: ToSingleValkeyArg, RV: FromValkeyValue + 's",
    ));
    assert_eq!(
        generics,
        parse_generics("K: ToSingleValkeyArg, RV: FromValkeyValue")
    );
}

// --- test helpers -------------------------------------------------------------------------------

/// Returns the given methods, indexed by name.
fn methods<const N: usize>(methods: [Method; N]) -> BTreeMap<String, Method> {
    methods.into_iter().map(|m| (m.name.clone(), m)).collect()
}

/// Returns a method with the given name and return type, and no generics or arguments.
fn method(name: &str, return_type: &str) -> Method {
    Method {
        name: name.to_string(),
        generics: Vec::new(),
        args: Vec::new(),
        return_type: Some(return_type.to_string()),
    }
}

/// Returns a named difference with the given methods.
fn difference(name: &str, redis: Option<Method>, glide: Option<Method>) -> (String, Difference) {
    let reason = "test".to_string();
    (
        name.to_string(),
        Difference {
            reason,
            redis,
            glide,
        },
    )
}
