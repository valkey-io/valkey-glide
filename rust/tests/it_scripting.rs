// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command scripting integration tests (RESP2 + RESP3).

mod common;

use glide::{AsyncCommands, CustomCommand, Route, ScriptingCommands};

resp_test!(eval_returns_argv, c, {
    let result = c
        .eval::<&str, &str>("return ARGV[1]", &[], &["hello"])
        .await
        .unwrap();
    assert_eq!(glide::value::to_string(result).unwrap(), "hello");
});

resp_test!(eval_integer, c, {
    let result = c
        .eval::<&str, &str>("return 1 + 2", &[], &[])
        .await
        .unwrap();
    assert_eq!(glide::value::to_i64(result).unwrap(), 3);
});

resp_test!(eval_with_keys, c, {
    let k = common::key("k");
    c.set::<_, _, ()>(&k, "stored").await.unwrap();
    let result = c
        .eval::<&str, &str>("return redis.call('GET', KEYS[1])", &[k.as_str()], &[])
        .await
        .unwrap();
    assert_eq!(glide::value::to_string(result).unwrap(), "stored");
});

resp_test!(script_load_and_evalsha, c, {
    let sha = c.script_load("return ARGV[1]").await.unwrap();
    assert_eq!(sha.len(), 40); // SHA1 hex length
    let result = c
        .evalsha::<&str, &str>(&sha, &[], &["world"])
        .await
        .unwrap();
    assert_eq!(glide::value::to_string(result).unwrap(), "world");
});

resp_test!(script_exists, c, {
    let sha = c.script_load("return 1").await.unwrap();
    let missing = "0".repeat(40);
    let exists = c.script_exists(&[&sha, &missing]).await.unwrap();
    assert_eq!(exists, vec![true, false]);
});

resp_test!(evalsha_unknown_errors, c, {
    let missing = "0".repeat(40);
    // NOSCRIPT is surfaced as a RequestError.
    assert_request_error!(c.evalsha::<&str, &str>(&missing, &[], &[]).await);
});

resp_test!(script_flush, c, {
    let sha = c.script_load("return 1").await.unwrap();
    c.script_flush().await.unwrap();
    let exists = c.script_exists(&[&sha]).await.unwrap();
    assert_eq!(exists, vec![false]);
});

resp_test!(eval_error_propagates, c, {
    // A Lua runtime error becomes a RequestError.
    assert_request_error!(
        c.eval::<&str, &str>("return redis.call('INCR', 'a', 'b', 'c')", &[], &[])
            .await
    );
});

#[tokio::test]
async fn fcall_and_fcall_route_live() {
    let srv = match common::TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let c = srv.client().await;

    // Load a tiny function library (idempotent via REPLACE). The `no-writes`
    // flag is required so the read-only `FCALL_RO` variant is permitted.
    let lib = "#!lua name=glidetestlib\n\
               redis.register_function{function_name='gt_echo', \
               callback=function(keys, args) return args[1] end, flags={'no-writes'}}";
    if let Err(e) = c
        .custom_command(&["FUNCTION", "LOAD", "REPLACE", lib])
        .await
    {
        // FUNCTION requires Valkey/Redis >= 7.0; skip on older servers.
        eprintln!("SKIP: FUNCTION LOAD unsupported: {e:?}");
        return;
    }

    // Plain FCALL.
    let r = c.fcall("gt_echo", &[] as &[&str], &["hi"]).await.unwrap();
    assert_eq!(glide::value::to_string(r).unwrap(), "hi");

    // Routed FCALL (route ignored on standalone, but the typed path must work).
    let r = c
        .fcall_route("gt_echo", &[] as &[&str], &["routed"], Route::RandomNode)
        .await
        .unwrap();
    assert_eq!(glide::value::to_string(r).unwrap(), "routed");

    // Read-only routed FCALL_RO.
    let r = c
        .fcall_ro_route("gt_echo", &[] as &[&str], &["ro"], Route::RandomNode)
        .await
        .unwrap();
    assert_eq!(glide::value::to_string(r).unwrap(), "ro");
}
