// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pub/Sub subscription-state polling helpers.

use std::time::{Duration, Instant};

/// Extract the subscriber count for `channel` from a `PUBSUB NUMSUB` reply
/// (`[chan, count, ...]` in RESP2, or a map in RESP3).
fn numsub_count(v: &glide::Value, channel: &str) -> Option<i64> {
    use glide::Value;
    let is_chan = |k: &Value| match k {
        Value::BulkString(b) => b.as_slice() == channel.as_bytes(),
        Value::SimpleString(s) => s == channel,
        _ => false,
    };
    match v {
        Value::Array(items) => {
            let mut it = items.iter();
            while let (Some(k), Some(val)) = (it.next(), it.next()) {
                if is_chan(k) {
                    return glide::value::to_i64(val.clone()).ok();
                }
            }
            None
        }
        Value::Map(pairs) => pairs
            .iter()
            .find(|(k, _)| is_chan(k))
            .and_then(|(_, val)| glide::value::to_i64(val.clone()).ok()),
        _ => None,
    }
}

/// Poll `PUBSUB NUMSUB <channel>` on `c` until the subscriber count for
/// `channel` satisfies `pred`, or `timeout` elapses; returns whether the
/// predicate was met. Use instead of a fixed sleep after (un)subscribe so a test
/// proceeds the instant the server has registered the change and never races a
/// slow registration (the Rust analogue of Python's `wait_for_subscription_state`).
pub async fn wait_for_numsub<C, F>(c: &C, channel: &str, mut pred: F, timeout: Duration) -> bool
where
    C: glide::CustomCommand + Sync,
    F: FnMut(i64) -> bool,
{
    let deadline = Instant::now() + timeout;
    loop {
        if let Ok(v) = c.custom_command(&["PUBSUB", "NUMSUB", channel]).await
            && let Some(n) = numsub_count(&v, channel)
            && pred(n)
        {
            return true;
        }
        if Instant::now() >= deadline {
            return false;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

/// Poll `PUBSUB NUMPAT` on `c` until the total pattern-subscription count
/// satisfies `pred`, or `timeout` elapses.
pub async fn wait_for_numpat<C, F>(c: &C, mut pred: F, timeout: Duration) -> bool
where
    C: glide::CustomCommand + Sync,
    F: FnMut(i64) -> bool,
{
    let deadline = Instant::now() + timeout;
    loop {
        if let Ok(v) = c.custom_command(&["PUBSUB", "NUMPAT"]).await
            && let Ok(n) = glide::value::to_i64(v)
            && pred(n)
        {
            return true;
        }
        if Instant::now() >= deadline {
            return false;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}
