// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Native-Rust port of the command-table parity check (formerly
//! `tools/verify_command_table.py`).
//!
//! Verifies GLIDE's command table (`src/commands/core.rs`) against the vendored
//! fork's `implement_commands!` table (redis-rs fork, v0.25.2 — predating the
//! upstream license change).
//!
//! For every method in the fork's table this checks that our table has an entry
//! with the same name, the same generic parameters (names, bounds, and order —
//! turbofish compatibility), and the same argument list. Extra entries on our
//! side are also flagged (they would silently diverge from the parity contract).
//!
//! The scan-iterator methods (`scan`/`scan_match`/`hscan`/... — defined in the
//! macro *body* on both sides, not as table entries) are verified too, against
//! the fork's definitions in `commands/macros.rs`: same generics and arguments
//! (minus GLIDE's deliberate `&self`-receiver / lifetime / `Send` additions),
//! present in both the async and blocking flavors.
//!
//! The fork checkout is resolved via `cargo metadata` (Cargo's local clone of
//! the git dependency), exactly like the Python script did.

use regex::Regex;
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::process::Command;

/// Outcome of a failed or skipped parity check.
pub enum ParityError {
    /// Environment prevents the check from running (fork checkout unavailable).
    Skip(String),
    /// The tables diverge; one entry per problem.
    Violations(Vec<String>),
}

/// A single generic parameter: name plus optional bound string, both trimmed.
type GenericParam = Vec<String>;
/// A single argument: `(name, whitespace-normalized type)`.
type Arg = (String, String);
/// A normalized signature for comparison: generic params + argument list.
type NormSig = (Vec<GenericParam>, Vec<Arg>);
/// A normalized scan-method signature: `(name, bounds)` generics + argument list.
type ScanSig = (Vec<(String, String)>, Vec<Arg>);

/// Run the full parity check. `Ok` carries a human-readable summary.
pub fn check() -> Result<String, ParityError> {
    let manifest_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    let fork_mod_rs = resolve_fork_mod_rs(manifest_dir)?;
    let ours_core_rs = manifest_dir.join("src/commands/core.rs");

    let fork_src = read(&fork_mod_rs)?;
    let ours_src = read(&ours_core_rs)?;

    let fork = parse_fork(&fork_src);
    let ours = parse_ours(&ours_src);

    let mut problems = Vec::new();
    for (name, sig) in &fork {
        match ours.get(name) {
            None => problems.push(format!("MISSING in ours: {name}")),
            Some(our_sig) if our_sig != sig => problems.push(format!(
                "SIGNATURE DIFF {name}:\n     fork: {sig:?}\n     ours: {our_sig:?}"
            )),
            _ => {}
        }
    }
    for name in ours.keys() {
        if !fork.contains_key(name) {
            problems.push(format!("EXTRA in ours (not in fork table): {name}"));
        }
    }

    check_scan_methods(&fork_mod_rs, &ours_src, &mut problems)?;

    if problems.is_empty() {
        Ok(format!(
            "parity OK: {} methods match the fork table exactly; \
             scan iterators match the fork's macro definitions",
            fork.len()
        ))
    } else {
        Err(ParityError::Violations(problems))
    }
}

fn read(path: &Path) -> Result<String, ParityError> {
    std::fs::read_to_string(path)
        .map_err(|e| ParityError::Skip(format!("cannot read {}: {e}", path.display())))
}

/// Locate the fork's `src/commands/mod.rs` via `cargo metadata` (the same
/// resolution the Python script used): find the `redis` package's manifest and
/// take `src/commands/mod.rs` next to it.
fn resolve_fork_mod_rs(manifest_dir: &Path) -> Result<PathBuf, ParityError> {
    let out = Command::new(env!("CARGO"))
        .args(["metadata", "--format-version", "1", "--offline"])
        .current_dir(manifest_dir)
        .output()
        .map_err(|e| ParityError::Skip(format!("cannot run cargo metadata: {e}")))?;
    if !out.status.success() {
        return Err(ParityError::Skip(format!(
            "cargo metadata failed:\n{}",
            String::from_utf8_lossy(&out.stderr)
        )));
    }
    let meta: serde_json::Value = serde_json::from_slice(&out.stdout)
        .map_err(|e| ParityError::Skip(format!("cannot parse cargo metadata: {e}")))?;
    let manifest = meta["packages"]
        .as_array()
        .into_iter()
        .flatten()
        .find(|p| p["name"] == "redis")
        .and_then(|p| p["manifest_path"].as_str())
        .ok_or_else(|| {
            ParityError::Skip("could not resolve the `redis` package via cargo metadata".into())
        })?;
    let dir = Path::new(manifest)
        .parent()
        .expect("manifest path has a parent");
    Ok(dir.join("src").join("commands").join("mod.rs"))
}

/// Extract the body of a top-level `<macro_name> {` invocation: from the marker
/// to the first line that *starts* with `}`.
fn macro_body<'a>(src: &'a str, marker: &str) -> &'a str {
    let start = src
        .find(marker)
        .unwrap_or_else(|| panic!("`{marker}` not found"));
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
    &rest[..end]
}

/// Normalize `(generics, args)` for comparison — port of the Python `norm_sig`.
fn norm_sig(generics: &str, args: &str) -> NormSig {
    let gens: Vec<GenericParam> = generics
        .split(',')
        .map(str::trim)
        .filter(|p| !p.is_empty())
        .map(|g| match g.split_once(':') {
            Some((name, bounds)) => vec![name.trim().to_string(), bounds.trim().to_string()],
            None => vec![g.to_string()],
        })
        .collect();

    // Split args on top-level commas only (depth-aware over `(<[`/`)>]`).
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
    let arglist: Vec<Arg> = parts
        .iter()
        .map(|a| {
            let (name, ty) = a
                .split_once(':')
                .unwrap_or_else(|| panic!("argument without a type annotation: {a}"));
            (
                name.trim().to_string(),
                ty.split_whitespace().collect::<Vec<_>>().join(" "),
            )
        })
        .collect();
    (gens, arglist)
}

/// Parse the fork's `implement_commands!` table: method name -> normalized sig.
fn parse_fork(src: &str) -> BTreeMap<String, NormSig> {
    let body = macro_body(src, "implement_commands! {");
    let sig_re =
        Regex::new(r"^fn\s+([a-z_0-9]+)\s*(?:<([^>]*)>)?\s*\((.*?)\)\s*\{").expect("valid regex");

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
                "unparseable fork table entry (did the fork's table style change on a rev \
                 bump?):\n  {}",
                &sig1[..sig1.len().min(160)]
            )
        });
        out.insert(
            caps[1].to_string(),
            norm_sig(caps.get(2).map_or("", |m| m.as_str()), caps[3].trim()),
        );
    }
    out
}

/// Parse our `implement_glide_commands!` table: method name -> normalized sig.
fn parse_ours(src: &str) -> BTreeMap<String, NormSig> {
    let body = macro_body(src, "implement_glide_commands! {");
    let re = Regex::new(r"(?s)fn\s+([a-z_0-9]+)\s*(?:<([^>]*)>)?\s*\(([^;]*?)\)\s*;")
        .expect("valid regex");
    re.captures_iter(body)
        .map(|caps| {
            let args = caps[3].split_whitespace().collect::<Vec<_>>().join(" ");
            (
                caps[1].to_string(),
                norm_sig(caps.get(2).map_or("", |m| m.as_str()), &args),
            )
        })
        .collect()
}

// ---- scan-iterator methods ------------------------------------------------------
//
// The scan-family methods live in the macro *body* (both here and in the fork),
// so the table parsers above never see them. GLIDE's scan methods DELIBERATELY
// deviate from the fork in receiver (`&self` vs `&mut self`) and return type
// (GLIDE-owned iterators on the owned-send path — see `src/commands/scan.rs`);
// what must stay in lockstep with the fork is the method NAMES, the generic
// parameters (minus GLIDE's added lifetimes/`Send` bounds), and the argument
// lists.

/// Generic params normalized: lifetimes dropped, `Send`/lifetime bounds (GLIDE's
/// deliberate additions) stripped, `redis::` qualifiers removed.
fn norm_scan_generics(generics: &str) -> Vec<(String, String)> {
    generics
        .split(',')
        .map(str::trim)
        .filter(|p| !p.is_empty() && !p.starts_with('\''))
        .map(|part| {
            let (name, bounds) = part.split_once(':').unwrap_or((part, ""));
            let kept: Vec<String> = bounds
                .split('+')
                .map(str::trim)
                .filter(|b| !b.is_empty() && *b != "Send" && !b.starts_with('\''))
                .map(|b| b.replace("redis::", ""))
                .collect();
            (name.trim().to_string(), kept.join(" + "))
        })
        .collect()
}

/// `name -> sorted list of normalized (generics, args)` — one element per trait
/// flavor (blocking + async) the method is defined in. `self` receivers,
/// `redis::` qualifiers, and GLIDE's added lifetime/`Send` bounds are
/// normalized away.
fn parse_scan_methods(src: &str) -> BTreeMap<String, Vec<ScanSig>> {
    let re = Regex::new(r"(?s)fn\s+((?:[hsz])?scan(?:_match)?)\s*<([^>]*)>\s*\(([^)]*)\)")
        .expect("valid regex");
    let mut out: BTreeMap<String, Vec<_>> = BTreeMap::new();
    for caps in re.captures_iter(src) {
        let args: String = caps[3]
            .split(',')
            .filter(|a| !a.contains("self"))
            .collect::<Vec<_>>()
            .join(",");
        let args = args
            .replace("redis::", "")
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ");
        out.entry(caps[1].to_string())
            .or_default()
            .push((norm_scan_generics(&caps[2]), norm_sig("", &args).1));
    }
    out
}

fn check_scan_methods(
    fork_mod_rs: &Path,
    ours_src: &str,
    problems: &mut Vec<String>,
) -> Result<(), ParityError> {
    let macros_rs = fork_mod_rs
        .parent()
        .expect("mod.rs has a parent")
        .join("macros.rs");
    if !macros_rs.exists() {
        return Err(ParityError::Skip(format!(
            "fork macros.rs not found next to the table: {}",
            macros_rs.display()
        )));
    }
    let fork = parse_scan_methods(&read(&macros_rs)?);
    let ours = parse_scan_methods(ours_src);
    for (name, sigs) in &fork {
        let sorted = |v: &[ScanSig]| {
            let mut v: Vec<_> = v.to_vec();
            v.sort();
            v.dedup();
            v
        };
        match ours.get(name) {
            None => problems.push(format!("MISSING scan method in ours: {name}")),
            Some(our_sigs) if sorted(our_sigs) != sorted(sigs) => problems.push(format!(
                "SCAN SIGNATURE DIFF {name}:\n     fork: {:?}\n     ours: {:?}",
                sorted(sigs),
                sorted(our_sigs)
            )),
            Some(our_sigs) if our_sigs.len() != 2 => problems.push(format!(
                "scan method {name} defined {}x in ours (expected exactly 2: async + blocking \
                 flavors)",
                our_sigs.len()
            )),
            _ => {}
        }
    }
    for name in ours.keys() {
        if !fork.contains_key(name) {
            problems.push(format!("EXTRA scan method in ours (not in fork): {name}"));
        }
    }
    Ok(())
}
