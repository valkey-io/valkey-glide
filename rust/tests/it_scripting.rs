// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command scripting integration tests (RESP2 + RESP3).

mod common;

use glide::{
    AsyncTypedCommands, FromValkeyValue, FunctionFlushOptions, Route, Script, ScriptingCommands,
    ValkeyValue,
};

resp_test!(eval_returns_argv, c, {
    let result = c
        .eval::<&str, &str>("return ARGV[1]", &[], &["hello"])
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(result).unwrap(), "hello");
});

resp_test!(eval_integer, c, {
    let result = c
        .eval::<&str, &str>("return 1 + 2", &[], &[])
        .await
        .unwrap();
    assert_eq!(i64::from_owned_valkey_value(result).unwrap(), 3);
});

resp_test!(eval_with_keys, c, {
    let k = common::key("k");
    c.set(&k, "stored").await.unwrap();
    let result = c
        .eval::<&str, &str>("return redis.call('GET', KEYS[1])", &[k.as_str()], &[])
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(result).unwrap(), "stored");
});

resp_test!(script_load_and_evalsha, c, {
    let sha = c.script_load("return ARGV[1]").await.unwrap();
    assert_eq!(sha.len(), 40); // SHA1 hex length
    let result = c
        .evalsha::<&str, &str>(&sha, &[], &["world"])
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(result).unwrap(), "world");
});

resp_test!(script_exists, c, {
    let sha = c.script_load("return 1").await.unwrap();
    let missing = "0".repeat(40);
    let exists = c.script_exists(&[&sha, &missing]).await.unwrap();
    assert_eq!(exists, vec![true, false]);
});

resp_test!(evalsha_unknown_errors, c, {
    let missing = "0".repeat(40);
    assert_request_error!(c.evalsha::<&str, &str>(&missing, &[], &[]).await);
});

resp_test!(script_flush, c, {
    let sha = c.script_load("return 1").await.unwrap();
    c.script_flush().await.unwrap();
    let exists = c.script_exists(&[&sha]).await.unwrap();
    assert_eq!(exists, vec![false]);
});

resp_test!(eval_error_propagates, c, {
    assert_request_error!(
        c.eval::<&str, &str>("return redis.call('INCR', 'a', 'b', 'c')", &[], &[])
            .await
    );
});

#[tokio::test]
async fn fcall_and_fcall_route_live() {
    let server = common::TestServer::start();
    let client = server.client().await;

    skip_if_version_below!(client, 7, 0, 0);

    // Load a tiny function library (idempotent via REPLACE). The `no-writes`
    // flag is required so the read-only `FCALL_RO` variant is permitted.
    let lib = "#!lua name=glidetestlib\n\
               redis.register_function{function_name='gt_echo', \
               callback=function(keys, args) return args[1] end, flags={'no-writes'}}";
    client
        .function_load(lib, true)
        .await
        .expect("FUNCTION LOAD");

    // Plain FCALL.
    let r = client
        .fcall("gt_echo", &[] as &[&str], &["hi"])
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(r).unwrap(), "hi");

    // Routed FCALL (route ignored on standalone, but the typed path must work).
    let r = client
        .fcall_route("gt_echo", &[] as &[&str], &["routed"], Route::RandomNode)
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(r).unwrap(), "routed");

    // Read-only routed FCALL_RO.
    let r = client
        .fcall_ro_route("gt_echo", &[] as &[&str], &["ro"], Route::RandomNode)
        .await
        .unwrap();
    assert_eq!(String::from_owned_valkey_value(r).unwrap(), "ro");
}

matrix_test!(function_flush_options, c, {
    skip_if_version_below!(c, 7, 0, 0);

    const LIBRARY: &str = "#!lua name=glide_flush_test\n\
        redis.register_function('glide_echo', function(keys, args) return args[1] end)";

    for options in [
        FunctionFlushOptions::default(),
        FunctionFlushOptions::default().blocking(true),
    ] {
        let name = c.function_load(LIBRARY, true).await.unwrap();
        assert_eq!(name, "glide_flush_test");

        c.function_flush_options(&options).await.unwrap();
        let libraries = c.function_list(None, false).await.unwrap();
        assert_eq!(libraries, ValkeyValue::Array(Vec::new()));
    }
});

matrix_test!(script_invoke_with_keys_and_args, c, {
    let script = Script::new("return redis.call('SET', KEYS[1], ARGV[1])");
    let k = common::key("cmd_script");
    let _: () = script.key(&k).arg("stored").invoke_async(&c).await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("stored"));
});

matrix_test!(script_computes_values, c, {
    let script = Script::new("return tonumber(ARGV[1]) + tonumber(ARGV[2])");
    let sum: i64 = script.arg(1).arg(2).invoke_async(&c).await.unwrap();
    assert_eq!(sum, 3);
});

resp_test!(script_noscript_fallback_after_flush, c, {
    // Flush the script cache so EVALSHA is guaranteed to miss, exercising the
    // transparent EVAL fallback.
    c.script_flush().await.unwrap();
    let script = Script::new("return 41 + 1");
    let v: i64 = script.invoke_async(&c).await.unwrap();
    assert_eq!(v, 42);
    // Second invocation hits the now-cached EVALSHA path.
    let v: i64 = script.invoke_async(&c).await.unwrap();
    assert_eq!(v, 42);
});

resp_test!(script_load_async_returns_hash, c, {
    let script = Script::new("return 7");
    let hash = script.load_async(&c).await.unwrap();
    assert_eq!(hash, script.get_hash());

    let reply = c
        .evalsha::<&str, &str>(script.get_hash(), &[], &[])
        .await
        .unwrap();
    assert_eq!(reply, glide::ValkeyValue::Int(7));
});

#[tokio::test]
async fn cluster_script_noscript_fallback() {
    // Keyless scripts route to a random node, so EVALSHA can miss on whichever
    // node it lands on — exercising the transparent EVAL fallback in cluster
    // mode. Flush all nodes first to guarantee the miss, then invoke enough
    // times to hit multiple nodes.
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    client.script_flush().await.unwrap_or(());
    let script = Script::new("return 40 + 2");
    for _ in 0..10 {
        let v: i64 = script.invoke_async(&client).await.unwrap();
        assert_eq!(v, 42);
    }
}
