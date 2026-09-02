// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the scripting & function command family.
use super::Mock;
use glide::commands::scripting::ScriptingCommands;
use redis::Value;

#[tokio::test]
async fn eval_encodes_numkeys() {
    let m = Mock::int(1);
    let v = m.eval("return 1", &["k1"], &["a1"]).await.unwrap();
    m.assert_args(&["EVAL", "return 1", "1", "k1", "a1"]);
    assert_eq!(v, Value::Int(1));
}

#[tokio::test]
async fn eval_no_keys() {
    let m = Mock::bulk("x");
    m.eval("return ARGV[1]", &[] as &[&str], &["only-arg"])
        .await
        .unwrap();
    m.assert_args(&["EVAL", "return ARGV[1]", "0", "only-arg"]);
}

#[tokio::test]
async fn evalsha_encoding() {
    let m = Mock::int(1);
    m.evalsha("abc123", &["k1", "k2"], &["a1"]).await.unwrap();
    m.assert_args(&["EVALSHA", "abc123", "2", "k1", "k2", "a1"]);
}

#[tokio::test]
async fn script_load_returns_sha() {
    let m = Mock::bulk("e0e1f9ca");
    assert_eq!(m.script_load("return 1").await.unwrap(), "e0e1f9ca");
    m.assert_args(&["SCRIPT", "LOAD", "return 1"]);
}

#[tokio::test]
async fn script_exists_returns_bools() {
    let m = Mock::array(vec![Value::Int(1), Value::Int(0)]);
    assert_eq!(
        m.script_exists(&["sha_a", "sha_b"]).await.unwrap(),
        vec![true, false]
    );
    m.assert_args(&["SCRIPT", "EXISTS", "sha_a", "sha_b"]);
}

#[tokio::test]
async fn script_flush_encoding() {
    let m = Mock::ok();
    m.script_flush().await.unwrap();
    m.assert_args(&["SCRIPT", "FLUSH"]);
}

#[tokio::test]
async fn fcall_and_fcall_ro() {
    let m = Mock::bulk("res");
    m.fcall("myfunc", &["k1"], &["a1", "a2"]).await.unwrap();
    m.assert_args(&["FCALL", "myfunc", "1", "k1", "a1", "a2"]);

    let m = Mock::bulk("res");
    m.fcall_ro("myfunc", &["k1"], &["a1"]).await.unwrap();
    m.assert_args(&["FCALL_RO", "myfunc", "1", "k1", "a1"]);
}

#[tokio::test]
async fn function_load_with_and_without_replace() {
    let m = Mock::bulk("mylib");
    assert_eq!(m.function_load("#!lua ...", false).await.unwrap(), "mylib");
    m.assert_args(&["FUNCTION", "LOAD", "#!lua ..."]);

    let m = Mock::bulk("mylib");
    m.function_load("#!lua ...", true).await.unwrap();
    m.assert_args(&["FUNCTION", "LOAD", "REPLACE", "#!lua ..."]);
}

#[tokio::test]
async fn function_delete_and_flush() {
    let m = Mock::ok();
    m.function_delete("mylib").await.unwrap();
    m.assert_args(&["FUNCTION", "DELETE", "mylib"]);

    let m = Mock::ok();
    m.function_flush().await.unwrap();
    m.assert_args(&["FUNCTION", "FLUSH"]);
}

// ---- FUNCTION / SCRIPT management variants ----

#[tokio::test]
async fn function_flush_mode_encoding() {
    let m = Mock::ok();
    m.function_flush_mode(glide::commands::options::FlushMode::Async)
        .await
        .unwrap();
    m.assert_args(&["FUNCTION", "FLUSH", "ASYNC"]);
}

#[tokio::test]
async fn function_list_variants() {
    let m = Mock::array(vec![]);
    m.function_list(None, false).await.unwrap();
    m.assert_args(&["FUNCTION", "LIST"]);

    let m = Mock::array(vec![]);
    m.function_list(Some("mylib"), true).await.unwrap();
    m.assert_args(&["FUNCTION", "LIST", "LIBRARYNAME", "mylib", "WITHCODE"]);
}

#[tokio::test]
async fn function_dump_restore_stats_kill() {
    let m = Mock::bulk("payload-bytes");
    assert_eq!(m.function_dump().await.unwrap().as_ref(), b"payload-bytes");
    m.assert_args(&["FUNCTION", "DUMP"]);

    let m = Mock::ok();
    m.function_restore(
        "payload-bytes",
        glide::commands::options::FunctionRestorePolicy::Append,
    )
    .await
    .unwrap();
    m.assert_args(&["FUNCTION", "RESTORE", "payload-bytes", "APPEND"]);

    let m = Mock::array(vec![]);
    m.function_stats().await.unwrap();
    m.assert_args(&["FUNCTION", "STATS"]);

    let m = Mock::ok();
    m.function_kill().await.unwrap();
    m.assert_args(&["FUNCTION", "KILL"]);
}

#[tokio::test]
async fn script_kill_and_show() {
    let m = Mock::ok();
    m.script_kill().await.unwrap();
    m.assert_args(&["SCRIPT", "KILL"]);

    let m = Mock::bulk("return 1");
    assert_eq!(m.script_show("abc123").await.unwrap().as_ref(), b"return 1");
    m.assert_args(&["SCRIPT", "SHOW", "abc123"]);
}

#[tokio::test]
async fn fcall_route_encodes_and_routes() {
    use glide::routes::Route;
    let m = Mock::bulk("ok");
    m.fcall_route("myfunc", &[] as &[&str], &["a1"], Route::AllPrimaries)
        .await
        .unwrap();
    m.assert_args(&["FCALL", "myfunc", "0", "a1"]);
    assert!(
        m.routing().is_some(),
        "route must be forwarded to the executor"
    );
}

#[tokio::test]
async fn fcall_ro_route_encodes_and_routes() {
    use glide::routes::Route;
    let m = Mock::bulk("ok");
    m.fcall_ro_route("rofn", &["k1"], &[] as &[&str], Route::RandomNode)
        .await
        .unwrap();
    m.assert_args(&["FCALL_RO", "rofn", "1", "k1"]);
    assert!(m.routing().is_some());
}
