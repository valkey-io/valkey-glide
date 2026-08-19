// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the JSON module command family.
use super::Mock;
use bytes::Bytes;
use glide::commands::json::JsonCommands;
use redis::Value;

#[tokio::test]
async fn json_set_and_get() {
    let m = Mock::ok();
    m.json_set("k", "$", "{\"a\":1}").await.unwrap();
    m.assert_args(&["JSON.SET", "k", "$", "{\"a\":1}"]);

    let m = Mock::bulk("[1]");
    let v = m.json_get("k", &["$.a", "$.b"]).await.unwrap();
    m.assert_args(&["JSON.GET", "k", "$.a", "$.b"]);
    assert_eq!(v, Some(Bytes::from_static(b"[1]")));
}

#[tokio::test]
async fn json_del_and_forget() {
    let m = Mock::int(1);
    assert_eq!(m.json_del("k", "$.a").await.unwrap(), 1);
    m.assert_args(&["JSON.DEL", "k", "$.a"]);

    let m = Mock::int(1);
    m.json_forget("k", "$.a").await.unwrap();
    m.assert_args(&["JSON.FORGET", "k", "$.a"]);
}

#[tokio::test]
async fn json_type_raw_value() {
    let m = Mock::bulk("object");
    let v = m.json_type("k", "$").await.unwrap();
    m.assert_args(&["JSON.TYPE", "k", "$"]);
    assert_eq!(v, Value::BulkString(b"object".to_vec()));
}

#[tokio::test]
async fn json_numincrby_nummultby() {
    let m = Mock::bulk("[6.5]");
    m.json_numincrby("k", "$.a", 1.5).await.unwrap();
    m.assert_args(&["JSON.NUMINCRBY", "k", "$.a", "1.5"]);

    let m = Mock::bulk("[5]");
    m.json_nummultby("k", "$.a", 2.5).await.unwrap();
    m.assert_args(&["JSON.NUMMULTBY", "k", "$.a", "2.5"]);
}

#[tokio::test]
async fn json_str_ops() {
    let m = Mock::array(vec![Value::Int(5)]);
    m.json_strappend("k", "$.s", "\"x\"").await.unwrap();
    m.assert_args(&["JSON.STRAPPEND", "k", "$.s", "\"x\""]);

    let m = Mock::array(vec![Value::Int(3)]);
    m.json_strlen("k", "$.s").await.unwrap();
    m.assert_args(&["JSON.STRLEN", "k", "$.s"]);
}

#[tokio::test]
async fn json_arr_ops() {
    let m = Mock::array(vec![Value::Int(2)]);
    m.json_arrappend("k", "$.a", &["1", "2"]).await.unwrap();
    m.assert_args(&["JSON.ARRAPPEND", "k", "$.a", "1", "2"]);

    let m = Mock::array(vec![Value::Int(3)]);
    m.json_arrinsert("k", "$.a", 0, &["9"]).await.unwrap();
    m.assert_args(&["JSON.ARRINSERT", "k", "$.a", "0", "9"]);

    let m = Mock::array(vec![Value::Int(3)]);
    m.json_arrlen("k", "$.a").await.unwrap();
    m.assert_args(&["JSON.ARRLEN", "k", "$.a"]);

    let m = Mock::bulk("9");
    m.json_arrpop("k", "$.a", Some(0)).await.unwrap();
    m.assert_args(&["JSON.ARRPOP", "k", "$.a", "0"]);

    let m = Mock::bulk("9");
    m.json_arrpop("k", "$.a", None).await.unwrap();
    m.assert_args(&["JSON.ARRPOP", "k", "$.a"]);

    let m = Mock::array(vec![Value::Int(2)]);
    m.json_arrtrim("k", "$.a", 0, 1).await.unwrap();
    m.assert_args(&["JSON.ARRTRIM", "k", "$.a", "0", "1"]);

    let m = Mock::array(vec![Value::Int(1)]);
    m.json_arrindex("k", "$.a", "9", None).await.unwrap();
    m.assert_args(&["JSON.ARRINDEX", "k", "$.a", "9"]);

    let m = Mock::array(vec![Value::Int(1)]);
    m.json_arrindex("k", "$.a", "9", Some((0, 10)))
        .await
        .unwrap();
    m.assert_args(&["JSON.ARRINDEX", "k", "$.a", "9", "0", "10"]);
}

#[tokio::test]
async fn json_obj_ops_and_toggle_clear() {
    let m = Mock::array(vec![Value::BulkString(b"a".to_vec())]);
    m.json_objkeys("k", "$").await.unwrap();
    m.assert_args(&["JSON.OBJKEYS", "k", "$"]);

    let m = Mock::array(vec![Value::Int(2)]);
    m.json_objlen("k", "$").await.unwrap();
    m.assert_args(&["JSON.OBJLEN", "k", "$"]);

    let m = Mock::array(vec![Value::Int(1)]);
    m.json_toggle("k", "$.b").await.unwrap();
    m.assert_args(&["JSON.TOGGLE", "k", "$.b"]);

    let m = Mock::int(1);
    assert_eq!(m.json_clear("k", "$").await.unwrap(), 1);
    m.assert_args(&["JSON.CLEAR", "k", "$"]);
}

#[tokio::test]
async fn json_mget_and_resp_and_debug() {
    let m = Mock::array(vec![Value::BulkString(b"[1]".to_vec()), Value::Nil]);
    let v = m.json_mget(&["k1", "k2"], "$.a").await.unwrap();
    m.assert_args(&["JSON.MGET", "k1", "k2", "$.a"]);
    assert_eq!(v, vec![Some(Bytes::from_static(b"[1]")), None]);

    let m = Mock::array(vec![Value::Int(1)]);
    m.json_resp("k", "$").await.unwrap();
    m.assert_args(&["JSON.RESP", "k", "$"]);

    let m = Mock::array(vec![Value::Int(64)]);
    m.json_debug_memory("k", "$").await.unwrap();
    m.assert_args(&["JSON.DEBUG", "MEMORY", "k", "$"]);

    let m = Mock::array(vec![Value::Int(3)]);
    m.json_debug_fields("k", "$").await.unwrap();
    m.assert_args(&["JSON.DEBUG", "FIELDS", "k", "$"]);
}
