// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Parity guards for the unified command table (`src/commands/core.rs`):
//!  * `command_table_matches_redis` — diffs GLIDE's command table
//!    (`src/commands/core.rs`) against the vendored redis-rs fork's, both read at
//!    test time: method names, generic order and bounds, argument lists, and
//!    return types. See `tests/parity/mod.rs`.
//!  * `scan_surface_lock` — the scan iterators are excluded from that diff (they
//!    deliberately deviate from redis-rs), so this compile-time lock pins their
//!    signatures instead: it type-checks each scan method with the generic bounds
//!    redis-rs uses, so a rename/reorder/drop stops compiling.
//!  * `fork_trait_escape_path_*` — locks the compatibility promise that the
//!    *literal* fork traits (`glide::redis::AsyncCommands` / `Commands`)
//!    still work on the clients, including generic code bounded on them.

// TODO #7058: Review
mod common;

mod parity;

#[test]
fn command_table_matches_redis() {
    match parity::check() {
        Ok(summary) => println!("{summary}"),
        Err(parity::ParityError::Skip(reason)) => {
            panic!("parity guard could not run: {reason}")
        }
        Err(parity::ParityError::Violations(problems)) => panic!(
            "command table diverges from the redis-rs baseline — PARITY VIOLATIONS ({}):\n - {}",
            problems.len(),
            problems.join("\n - ")
        ),
    }
}

// ---- scan-surface lock ---------------------------------------------------------
//
// Scan iterators are excluded from the command-table parity guard: they
// deliberately deviate from redis-rs (owned iterators, `&self`, `Send`/lifetime
// bounds), so a JSON signature diff would be all noise. Instead these functions
// pin the scan surface at compile time — each type-checks a call to every scan
// method with the generic bounds redis-rs uses, so a renamed / reordered / dropped
// scan method (or a changed argument) stops compiling. Never run — only compiled.

#[allow(dead_code, clippy::let_underscore_future)]
fn scan_surface_lock<A: glide::AsyncCommands>(con: &A) {
    let _ = con.scan::<i64>();
    let _ = con.scan_match::<&str, i64>("p");
    let _ = con.hscan::<&str, i64>("k");
    let _ = con.hscan_match::<&str, &str, i64>("k", "p");
    let _ = con.sscan::<&str, i64>("k");
    let _ = con.sscan_match::<&str, &str, i64>("k", "p");
    let _ = con.zscan::<&str, i64>("k");
    let _ = con.zscan_match::<&str, &str, i64>("k", "p");
}

/// Blocking counterpart of [`scan_surface_lock`].
#[cfg(feature = "sync")]
#[allow(dead_code)]
fn scan_surface_lock_sync<C: glide::Commands>(con: &C) {
    let _ = con.scan::<i64>();
    let _ = con.scan_match::<&str, i64>("p");
    let _ = con.hscan::<&str, i64>("k");
    let _ = con.hscan_match::<&str, &str, i64>("k", "p");
    let _ = con.sscan::<&str, i64>("k");
    let _ = con.sscan_match::<&str, &str, i64>("k", "p");
    let _ = con.zscan::<&str, i64>("k");
    let _ = con.zscan_match::<&str, &str, i64>("k", "p");
}

// ---- generic-code locks --------------------------------------------------------
//
// The GLIDE clients are deliberately NOT `redis` connection objects (the
// `ConnectionLike` interop cost a payload copy per command). What IS
// guaranteed is that generic code can bound on GLIDE's own traits — these
// tests compile-lock that contract and exercise it live.

/// Generic over GLIDE's async trait — proves downstream code can write
/// client-agnostic helpers against `glide::AsyncCommands`.
async fn via_glide_async_trait<C: glide::AsyncCommands>(
    con: &C,
    key: &str,
) -> glide::ValkeyResult<i64> {
    con.set::<_, _, ()>(key, 7).await?;
    con.get(key).await
}

matrix_test!(generic_code_on_glide_async_trait, c, {
    let k = common::key("rrs_glide_generic");
    let v = via_glide_async_trait(&c, &k).await.unwrap();
    assert_eq!(v, 7);
});

#[test]
fn generic_code_on_glide_sync_trait() {
    use glide::Commands;
    use glide::sync::SyncGlideClient;

    let server = server_or_skip!();
    let config = glide::GlideClientConfiguration::with_address("127.0.0.1", server.port);
    let client = SyncGlideClient::connect(config).unwrap();

    let k = common::key("rrs_glide_generic_sync");
    // Generic bound on GLIDE's blocking trait.
    fn via_glide_sync_trait<C: Commands>(con: &C, key: &str) -> glide::ValkeyResult<i64> {
        con.set::<_, _, ()>(key, 9)?;
        con.get(key)
    }
    let v = via_glide_sync_trait(&client, &k).unwrap();
    assert_eq!(v, 9);
}
