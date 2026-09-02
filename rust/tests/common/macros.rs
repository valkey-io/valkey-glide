// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Test-definition macros (`#[macro_export]` — usable unqualified from any
//! integration-test crate that declares `mod common;`).

/// Define a `#[tokio::test]` whose body is bounded by [`with_test_timeout`].
/// Use for hand-written async tests; the `resp_test!` / `matrix_test!` macros
/// apply the same guard automatically.
///
/// ```ignore
/// timed_tokio_test!(async fn my_test() {
///     let srv = server_or_skip!();
///     // ...
/// });
/// ```
#[macro_export]
macro_rules! timed_tokio_test {
    (async fn $name:ident() $body:block) => {
        #[tokio::test]
        async fn $name() {
            $crate::common::with_test_timeout(async move $body).await;
        }
    };
}

/// Retry a cluster operation on transient topology errors (see
/// [`is_transient_cluster_error`]) with bounded exponential-ish backoff.
///
/// Usage (pass the future *without* `.await` — the macro awaits internally):
/// ```ignore
/// let (next, keys) = retry_transient!(client.cluster_scan(&cursor, None, Some(100), None)).unwrap();
/// ```
#[macro_export]
macro_rules! retry_transient {
    ($op:expr) => {{
        let mut __attempt: u32 = 0;
        loop {
            match $op.await {
                Ok(v) => break Ok::<_, glide::GlideError>(v),
                Err(e) if __attempt < 15 && $crate::common::is_transient_cluster_error(&e) => {
                    __attempt += 1;
                    tokio::time::sleep(std::time::Duration::from_millis(50 * __attempt as u64))
                        .await;
                }
                Err(e) => break Err(e),
            }
        }
    }};
}

/// Start a standalone server, or `return` from the test (printing SKIP) when no
/// server binary is available.
#[macro_export]
macro_rules! server_or_skip {
    () => {{
        match $crate::common::TestServer::start() {
            Some(s) => s,
            None => {
                eprintln!("SKIP: no valkey-server binary available");
                return;
            }
        }
    }};
}

/// Start a cluster, or `return` from the test (printing SKIP) when a cluster is
/// not feasible in this environment.
#[macro_export]
macro_rules! cluster_or_skip {
    () => {{
        match $crate::common::ClusterHarness::start() {
            Some(h) => h,
            None => {
                eprintln!("SKIP: cluster harness not feasible in this environment");
                return;
            }
        }
    }};
}

/// Expand one test body into two `#[tokio::test]`s — one for RESP2, one for
/// RESP3 — each with its own fresh standalone server bound to `$c`.
///
/// ```ignore
/// resp_test!(get_missing, c, {
///     assert_eq!(c.get(common::key("k")).await.unwrap(), None);
/// });
/// ```
#[macro_export]
macro_rules! resp_test {
    ($name:ident, $c:ident, $body:block) => {
        mod $name {
            use super::*;
            #[allow(unused_imports)]
            use $crate::common;

            #[tokio::test]
            async fn resp2() {
                let __srv = $crate::server_or_skip!();
                let $c = __srv
                    .client_with_protocol(glide::ProtocolVersion::RESP2)
                    .await;
                $crate::common::with_test_timeout(async { $body }).await;
            }

            #[tokio::test]
            async fn resp3() {
                let __srv = $crate::server_or_skip!();
                let $c = __srv
                    .client_with_protocol(glide::ProtocolVersion::RESP3)
                    .await;
                $crate::common::with_test_timeout(async { $body }).await;
            }
        }
    };
}

/// Expand one test body into **four** `#[tokio::test]`s: the cartesian product of
/// {standalone, cluster} × {RESP2, RESP3} — mirroring Python's
/// `cluster_mode × protocol` parametrization (the ~4× multiplier).
///
/// * standalone arms get a fresh [`TestServer`] each (cheap, fully isolated);
/// * cluster arms form a fresh per-test [`ClusterHarness`] each (fully isolated;
///   SKIP if a cluster cannot be formed in this environment).
///
/// The body must be **cluster-safe**: use [`common::key`] for single-key work and
/// [`common::tkey`] (hash-tagged) so multi-key commands land in one slot. The
/// same body compiles against both client types because every typed command
/// method is implemented for both `GlideClient` and `GlideClusterClient`.
///
/// ```ignore
/// matrix_test!(set_and_get, c, {
///     let k = common::key("str");
///     c.set(&k, "v").await.unwrap();
///     assert_eq!(c.get(&k).await.unwrap().as_deref(), Some(&b"v"[..]));
/// });
/// ```
#[macro_export]
macro_rules! matrix_test {
    ($name:ident, $c:ident, $body:block) => {
        mod $name {
            use super::*;
            #[allow(unused_imports)]
            use $crate::common;

            #[tokio::test]
            async fn standalone_resp2() {
                let __srv = $crate::server_or_skip!();
                let $c = __srv
                    .client_with_protocol(glide::ProtocolVersion::RESP2)
                    .await;
                $crate::common::with_test_timeout(async { $body }).await;
            }

            #[tokio::test]
            async fn standalone_resp3() {
                let __srv = $crate::server_or_skip!();
                let $c = __srv
                    .client_with_protocol(glide::ProtocolVersion::RESP3)
                    .await;
                $crate::common::with_test_timeout(async { $body }).await;
            }

            #[tokio::test]
            async fn cluster_resp2() {
                let __h = match $crate::common::ClusterHarness::start() {
                    Some(h) => h,
                    None => {
                        eprintln!("SKIP: cluster harness not feasible in this environment");
                        return;
                    }
                };
                let $c = match __h
                    .client_with_protocol(glide::ProtocolVersion::RESP2)
                    .await
                {
                    Some(c) => c,
                    None => {
                        eprintln!("SKIP: could not connect cluster client (RESP2)");
                        return;
                    }
                };
                $crate::common::with_test_timeout(async { $body }).await;
            }

            #[tokio::test]
            async fn cluster_resp3() {
                let __h = match $crate::common::ClusterHarness::start() {
                    Some(h) => h,
                    None => {
                        eprintln!("SKIP: cluster harness not feasible in this environment");
                        return;
                    }
                };
                let $c = match __h
                    .client_with_protocol(glide::ProtocolVersion::RESP3)
                    .await
                {
                    Some(c) => c,
                    None => {
                        eprintln!("SKIP: could not connect cluster client (RESP3)");
                        return;
                    }
                };
                $crate::common::with_test_timeout(async { $body }).await;
            }
        }
    };
}

/// Skip the current test (printing SKIP) when the server version is below
/// `major.minor.patch` — the Rust analogue of Python's
/// `@pytest.mark.skip_if_version_below`. Requires a connected client `$c` that
/// implements `ServerManagementCommands`.
///
/// ```ignore
/// matrix_test!(hexpire_sets_ttl, c, {
///     skip_if_version_below!(c, 7, 4, 0);
///     // ... newer-command assertions ...
/// });
/// ```
#[macro_export]
macro_rules! skip_if_version_below {
    ($c:expr, $major:expr, $minor:expr, $patch:expr) => {{
        if $crate::common::version_below(&$c, ($major, $minor, $patch)).await {
            eprintln!("SKIP: requires server >= {}.{}.{}", $major, $minor, $patch);
            return;
        }
    }};
}

/// Skip the current test (printing SKIP) unless the server recognises `$cmd` —
/// a robust, version-agnostic capability gate (preferred over version math for
/// commands whose availability differs across Redis/Valkey releases).
///
/// ```ignore
/// matrix_test!(hexpire_sets_ttl, c, {
///     skip_unless_command!(c, "HEXPIRE");
///     // ...
/// });
/// ```
#[macro_export]
macro_rules! skip_unless_command {
    ($c:expr, $cmd:expr) => {{
        if !$crate::common::command_exists(&$c, $cmd).await {
            eprintln!("SKIP: server does not support {}", $cmd);
            return;
        }
    }};
}

/// Assert that an expression evaluated to `Err(GlideError::Request(_))` — the
/// mapped form of a server-side error (e.g. `WRONGTYPE`).
#[macro_export]
macro_rules! assert_request_error {
    ($expr:expr) => {{
        match $expr {
            Err(glide::GlideError::Request(_)) => {}
            Err(other) => panic!("expected RequestError, got {:?}", other),
            Ok(v) => panic!("expected RequestError, got Ok({:?})", v),
        }
    }};
}
