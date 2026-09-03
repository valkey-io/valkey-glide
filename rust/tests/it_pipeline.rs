// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pipeline + transaction integration tests via [`glide::pipe()`] and
//! [`glide::GlideClient::execute_pipeline`].
//!
//! Covers atomic transactions, non-atomic pipeline depth, `raise_on_error`
//! true/false behaviour, errors inside a transaction, [`glide::PipelineOptions`]
//! (timeout, retry policy), and WATCH/MULTI framing via `custom_command`.
//!
//! Note: `execute_pipeline` returns the **raw** per-command replies —
//! `.ignore()` markers only affect typed decoding via `query_glide`.

mod common;

use glide::{AsyncCommands, CustomCommand, PipelineExt, PipelineOptions, pipe};

#[tokio::test]
async fn atomic_transaction_ordered_results() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("tx");

    let mut p = pipe();
    p.atomic().set(&k, "10").incr(&k, 1).incr(&k, 1).get(&k);
    let results = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(results.len(), 4);
    assert_eq!(glide::value::to_i64(results[1].clone()).unwrap(), 11);
    assert_eq!(glide::value::to_i64(results[2].clone()).unwrap(), 12);
    assert_eq!(glide::value::to_string(results[3].clone()).unwrap(), "12");
}

#[tokio::test]
async fn empty_pipeline_returns_empty() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let mut p = pipe();
    p.atomic();
    let results = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert!(results.is_empty());
}

#[tokio::test]
async fn non_atomic_pipeline_depth() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("pipe");

    let mut p = pipe();
    p.set(&k, "0");
    for _ in 0..100 {
        p.incr(&k, 1);
    }
    p.get(&k);
    let results = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    // 1 SET + 100 INCR + 1 GET.
    assert_eq!(results.len(), 102);
    assert_eq!(
        glide::value::to_string(results[101].clone()).unwrap(),
        "100"
    );
}

#[tokio::test]
async fn raise_on_error_true_surfaces_error() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("re");
    let _: () = c.set(&k, "notanumber").await.unwrap();

    let mut p = pipe();
    p.incr(&k, 1); // errors: value is not an integer
    let result = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await;
    assert!(result.is_err(), "expected error with raise_on_error=true");
}

#[tokio::test]
async fn raise_on_error_false_returns_inline() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let good = common::key("good");
    let bad = common::key("bad");
    let _: () = c.set(&bad, "notanumber").await.unwrap();

    let mut p = pipe();
    p.set(&good, "1").incr(&good, 1).incr(&bad, 1); // last one errors
    let results = c
        .execute_pipeline(&p, false, &PipelineOptions::default())
        .await
        .unwrap();
    // All three positions are present even though one errored.
    assert_eq!(results.len(), 3);
    // The good INCR still produced 2.
    assert_eq!(glide::value::to_i64(results[1].clone()).unwrap(), 2);
}

#[tokio::test]
async fn error_inside_transaction_runtime() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("tx");
    let _: () = c.set(&k, "notanumber").await.unwrap();

    // A runtime error inside MULTI/EXEC does not abort the whole transaction;
    // with raise_on_error=false the other results are returned.
    let mut p = pipe();
    p.atomic()
        .cmd("SET")
        .arg(common::key("ok"))
        .arg("v")
        .incr(&k, 1);
    let results = c
        .execute_pipeline(&p, false, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(results.len(), 2);
}

#[tokio::test]
async fn raw_commands_in_pipeline() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("cc");
    let mut p = pipe();
    p.cmd("SET")
        .arg(&k)
        .arg("1")
        .cmd("APPEND")
        .arg(&k)
        .arg("0")
        .cmd("GET")
        .arg(&k);
    let results = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(results.len(), 3);
    assert_eq!(glide::value::to_string(results[2].clone()).unwrap(), "10");
}

#[tokio::test]
async fn watch_multi_semantics() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("w");
    let _: () = c.set(&k, "1").await.unwrap();

    // WATCH/UNWATCH are accepted (framing check). GLIDE multiplexes connections,
    // so we assert the commands succeed rather than optimistic-lock abort.
    let watch = c.custom_command(&["WATCH", &k]).await.unwrap();
    assert_eq!(glide::value::to_string(watch).unwrap(), "OK");

    let mut p = pipe();
    p.atomic().incr(&k, 1);
    let results = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(results.len(), 1);
    assert_eq!(glide::value::to_i64(results[0].clone()).unwrap(), 2);

    let unwatch = c.custom_command(&["UNWATCH"]).await.unwrap();
    assert_eq!(glide::value::to_string(unwatch).unwrap(), "OK");
}

#[tokio::test]
async fn pipeline_spans_multiple_data_types() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let s = common::key("b_str");
    let l = common::key("b_list");
    let h = common::key("b_hash");
    let z = common::key("b_zset");

    let mut p = pipe();
    p.cmd("SET")
        .arg(&s)
        .arg("v")
        .cmd("RPUSH")
        .arg(&l)
        .arg("a")
        .arg("b")
        .arg("c")
        .cmd("HSET")
        .arg(&h)
        .arg("f")
        .arg("1")
        .cmd("ZADD")
        .arg(&z)
        .arg("1")
        .arg("m")
        .cmd("LLEN")
        .arg(&l)
        .cmd("HGET")
        .arg(&h)
        .arg("f")
        .cmd("ZCARD")
        .arg(&z);
    let r = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(r.len(), 7);
    assert_eq!(glide::value::to_i64(r[4].clone()).unwrap(), 3); // LLEN
    assert_eq!(glide::value::to_string(r[5].clone()).unwrap(), "1"); // HGET
    assert_eq!(glide::value::to_i64(r[6].clone()).unwrap(), 1); // ZCARD
}

#[tokio::test]
async fn pipeline_preserves_binary_values() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("b_bin");
    let payload = vec![0u8, 1, 2, 255, 0, 42];
    let mut p = pipe();
    p.atomic().set(&k, payload.clone()).get(&k);
    let r = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(
        glide::value::to_bytes(r[1].clone()).unwrap().as_ref(),
        &payload[..]
    );
}

#[tokio::test]
async fn non_atomic_mixed_reads_writes_ordered() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("b_mix");
    let mut p = pipe();
    p.set(&k, "1").get(&k).incr(&k, 1).get(&k).del(&k).get(&k);
    let r = c
        .execute_pipeline(&p, true, &PipelineOptions::default())
        .await
        .unwrap();
    assert_eq!(r.len(), 6);
    assert_eq!(glide::value::to_string(r[1].clone()).unwrap(), "1");
    assert_eq!(glide::value::to_i64(r[2].clone()).unwrap(), 2);
    assert_eq!(glide::value::to_string(r[3].clone()).unwrap(), "2");
    // After DEL, GET is null.
    assert!(matches!(r[5], glide::Value::Nil));
}

#[tokio::test]
async fn typed_pipeline_query_glide_still_works() {
    // The typed decode path (`.ignore()` filtering, tuple decode) remains
    // available alongside execute_pipeline, via `query_glide`.
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("b_typed");
    let (v, n): (String, i64) = pipe()
        .set(&k, "x")
        .ignore()
        .get(&k)
        .incr(common::key("b_typed_ctr"), 5)
        .query_glide(&c)
        .await
        .unwrap();
    assert_eq!((v.as_str(), n), ("x", 5));
}

timed_tokio_test!(
    async fn cluster_atomic_transaction_same_slot() {
        let h = match common::ClusterHarness::start() {
            Some(h) => h,
            None => {
                eprintln!("SKIP: cluster harness not feasible");
                return;
            }
        };
        let c = match h.client().await {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster connect failed");
                return;
            }
        };
        // All keys share a hash tag → same slot → a cluster MULTI/EXEC is valid.
        let k1 = common::tkey("btx", "k1");
        let k2 = common::tkey("btx", "k2");
        let mut p = pipe();
        p.atomic()
            .set(&k1, "10")
            .incr(&k1, 1)
            .set(&k2, "x")
            .get(&k1)
            .get(&k2);
        let r = c
            .execute_pipeline(&p, true, None, &PipelineOptions::default())
            .await
            .unwrap();
        assert_eq!(r.len(), 5);
        assert_eq!(glide::value::to_i64(r[1].clone()).unwrap(), 11);
        assert_eq!(glide::value::to_string(r[3].clone()).unwrap(), "11");
        assert_eq!(glide::value::to_string(r[4].clone()).unwrap(), "x");
    }
);

timed_tokio_test!(
    async fn cluster_non_atomic_pipeline() {
        let h = match common::ClusterHarness::start() {
            Some(h) => h,
            None => {
                eprintln!("SKIP: cluster harness not feasible");
                return;
            }
        };
        let c = match h.client().await {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster connect failed");
                return;
            }
        };
        // A non-atomic pipeline may span slots; GLIDE routes each command.
        let mut p = pipe();
        let a = common::key("bp_a");
        let b = common::key("bp_b");
        p.set(&a, "1").set(&b, "2").get(&a).get(&b);
        let r = c
            .execute_pipeline(&p, true, None, &PipelineOptions::default())
            .await
            .unwrap();
        assert_eq!(r.len(), 4);
        assert_eq!(glide::value::to_string(r[2].clone()).unwrap(), "1");
        assert_eq!(glide::value::to_string(r[3].clone()).unwrap(), "2");
    }
);

#[tokio::test]
async fn pipeline_with_options_timeout_and_retry() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("bopt");

    let mut p = pipe();
    p.set(&k, "1").incr(&k, 1).get(&k);

    let opts = PipelineOptions::new()
        .with_timeout(std::time::Duration::from_secs(5))
        .with_retry_server_error(true)
        .with_retry_connection_error(false);

    let results = c.execute_pipeline(&p, true, &opts).await.unwrap();
    assert_eq!(results.len(), 3);
    assert_eq!(glide::value::to_i64(results[1].clone()).unwrap(), 2);
    assert_eq!(glide::value::to_string(results[2].clone()).unwrap(), "2");
}

#[tokio::test]
async fn transaction_with_options_timeout() {
    let srv = server_or_skip!();
    let c = srv.client().await;
    let k = common::key("btx");

    let mut p = pipe();
    p.atomic().set(&k, "5").incr(&k, 1).get(&k);

    let opts = PipelineOptions::new().with_timeout(std::time::Duration::from_secs(5));
    let results = c.execute_pipeline(&p, true, &opts).await.unwrap();
    assert_eq!(results.len(), 3);
    assert_eq!(glide::value::to_string(results[2].clone()).unwrap(), "6");
}
