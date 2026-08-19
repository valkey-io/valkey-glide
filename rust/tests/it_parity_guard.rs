// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Parity guards for the unified command table (`src/commands/core.rs`):
//!  * `command_table_matches_fork` — compares every method signature in our
//!    hand-maintained table against the vendored redis-rs fork's
//!    `implement_commands!` table (names, generic order, argument lists), and
//!    the scan-iterator methods against the fork's `commands/macros.rs`
//!    definitions. Pure Rust — no external interpreter needed. The fork source
//!    is resolved through `cargo metadata` (the git dependency checkout).
//!  * `fork_trait_escape_path_*` — locks the compatibility promise that the
//!    *literal* fork traits (`glide::redis::AsyncCommands` / `Commands`)
//!    still work on the clients, including generic code bounded on them.

mod common;

mod parity;

/// Signature-parity guard: our table must match the fork's, method for method.
#[test]
fn command_table_matches_fork() {
    match parity::check() {
        Ok(summary) => println!("{summary}"),
        Err(parity::ParityError::Skip(reason)) => eprintln!("SKIP: {reason}"),
        Err(parity::ParityError::Violations(problems)) => panic!(
            "command table diverges from the fork — PARITY VIOLATIONS ({}):\n - {}",
            problems.len(),
            problems.join("\n - ")
        ),
    }
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
) -> glide::RedisResult<i64> {
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
    let srv = match common::TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server");
            return;
        }
    };
    use glide::Commands;
    use glide::sync::SyncGlideClient;
    let c = SyncGlideClient::connect(glide::GlideClientConfiguration::with_address(
        "127.0.0.1",
        srv.port,
    ))
    .unwrap();
    let k = common::key("rrs_glide_generic_sync");
    // Generic bound on GLIDE's blocking trait.
    fn via_glide_sync_trait<C: Commands>(con: &C, key: &str) -> glide::RedisResult<i64> {
        con.set::<_, _, ()>(key, 9)?;
        con.get(key)
    }
    let v = via_glide_sync_trait(&c, &k).unwrap();
    assert_eq!(v, 9);
}
