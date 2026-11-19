// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::Client;
use glide_telemetry::GlideSpan;
use redis::{Arg, Cmd};
use std::borrow::Borrow;

/// Defines how command arguments are masked in `db.query.text` to prevent
/// sensitive values from leaking into telemetry.
#[derive(Debug, PartialEq)]
enum MaskingPattern {
    /// Show all arguments. Used for read commands (GET, DEL, KEYS, etc.).
    ShowAll,
    /// Mask all arguments. Used for commands with entirely sensitive args (AUTH, ECHO)
    /// and as the default for any unlisted command.
    MaskAll,
    /// Show the first N arguments, mask the rest. Used for commands where the key
    /// (and optionally field/TTL) is safe but the value is sensitive (SET=1, SETEX=2, etc.).
    ShowFirst(usize),
    /// Interleaved key-value pairs: show even-indexed args (keys), mask odd-indexed
    /// args (values). Used for MSET and MSETNX where remaining args are key/value pairs.
    InterleavedKeyValue,
    /// Show the first N arguments (e.g., key), then interleave the rest as field/value
    /// pairs: show even-indexed (field), mask odd-indexed (value). Used for HSET, HMSET.
    ShowFirstThenInterleave(usize),
}

/// Returns the masking pattern for a given command.
///
/// The default is [`MaskingPattern::MaskAll`] to prevent accidental
/// credential leakage from commands not explicitly listed here.
fn masking_pattern(cmd_name: &str) -> MaskingPattern {
    match cmd_name.to_ascii_uppercase().as_str() {
        // -- MaskAll: all arguments are sensitive --
        "ACL"
        | "AUTH"
        | "CLIENT"
        | "CLUSTER"
        | "CONFIG"
        | "DEBUG"
        | "ECHO"
        | "HELLO"
        | "MIGRATE" => MaskingPattern::MaskAll,

        // -- ShowFirst(1): only key/channel visible --
        // Note: ZADD has optional flags (NX|XX|GT|LT|CH|INCR) before score/member pairs,
        // making it hard to fit a more granular pattern. ShowFirst(1) is the safe choice.
        "APPEND" | "DELIFEQ" | "GETSET" | "LPUSH" | "LPUSHX" | "PFADD" | "PUBLISH" | "RPUSH"
        | "RPUSHX" | "SADD" | "SET" | "SETNX" | "SPUBLISH" | "XADD" | "ZADD" => {
            MaskingPattern::ShowFirst(1)
        }

        // -- ShowFirst(2): key + TTL/field visible, value masked --
        "DELEX"
        | "HSETNX"
        | "LPOS"
        | "LSET"
        | "PSETEX"
        | "SETEX" => MaskingPattern::ShowFirst(2),

        // LINSERT key BEFORE|AFTER pivot element — key, keyword, pivot are non-sensitive
        "LINSERT" => MaskingPattern::ShowFirst(3),

        // -- InterleavedKeyValue: args are directly key/value pairs --
        "MSET" | "MSETNX" => MaskingPattern::InterleavedKeyValue,

        // -- ShowFirstThenInterleave: key visible, then field/value pairs interleaved --
        "HSET" | "HMSET" => MaskingPattern::ShowFirstThenInterleave(1),

        // -- ShowAll: all arguments are non-sensitive (keys, patterns, indices, etc.) --
        //
        // String
        "DECR" | "DECRBY" | "GET" | "GETBIT" | "GETDEL" | "GETEX" | "GETRANGE"
        | "INCR" | "INCRBY" | "INCRBYFLOAT" | "MGET" | "STRLEN" | "SUBSTR"
        // Bitmap
        | "BITCOUNT" | "BITFIELD" | "BITFIELD_RO" | "BITOP" | "BITPOS"
        // Key
        | "COPY" | "DEL" | "DUMP" | "EXISTS" | "EXPIRE" | "EXPIREAT" | "EXPIRETIME"
        | "KEYS" | "MOVE" | "OBJECT" | "PERSIST" | "PEXPIRE" | "PEXPIREAT"
        | "PEXPIRETIME" | "PTTL" | "RANDOMKEY" | "RENAME" | "RENAMENX" | "SCAN"
        | "SORT" | "SORT_RO" | "TOUCH" | "TTL" | "TYPE" | "UNLINK" | "WAIT" | "WAITAOF"
        | "WATCH" | "UNWATCH"
        // Hash (read)
        | "HDEL" | "HEXISTS" | "HGET" | "HGETALL" | "HGETDEL" | "HINCRBY" | "HINCRBYFLOAT"
        | "HKEYS" | "HLEN" | "HMGET" | "HRANDFIELD" | "HSCAN" | "HSTRLEN" | "HVALS"
        // List (read/structural)
        | "LINDEX" | "LLEN" | "LMOVE" | "LMPOP" | "LPOP" | "LRANGE" | "LREM" | "LTRIM"
        | "RPOP" | "RPOPLPUSH"
        // Set
        | "SCARD" | "SDIFF" | "SDIFFSTORE" | "SINTER" | "SINTERCARD" | "SINTERSTORE"
        | "SISMEMBER" | "SMEMBERS" | "SMISMEMBER" | "SMOVE" | "SPOP" | "SRANDMEMBER"
        | "SREM" | "SSCAN" | "SUNION" | "SUNIONSTORE"
        // Sorted Set (read/structural)
        | "ZCARD" | "ZCOUNT" | "ZDIFF" | "ZDIFFSTORE" | "ZINCRBY" | "ZINTER"
        | "ZINTERCARD" | "ZINTERSTORE" | "ZLEXCOUNT" | "ZMPOP" | "ZMSCORE"
        | "ZPOPMAX" | "ZPOPMIN" | "ZRANDMEMBER" | "ZRANGE" | "ZRANGEBYLEX"
        | "ZRANGEBYSCORE" | "ZRANGESTORE" | "ZRANK" | "ZREM" | "ZREMRANGEBYLEX"
        | "ZREMRANGEBYRANK" | "ZREMRANGEBYSCORE" | "ZREVRANGE" | "ZREVRANGEBYLEX"
        | "ZREVRANGEBYSCORE" | "ZREVRANK" | "ZSCAN" | "ZSCORE" | "ZUNION" | "ZUNIONSTORE"
        // Geo (read)
        | "GEODIST" | "GEOHASH" | "GEOPOS" | "GEORADIUS" | "GEORADIUS_RO"
        | "GEORADIUSBYMEMBER" | "GEORADIUSBYMEMBER_RO" | "GEOSEARCH" | "GEOSEARCHSTORE"
        // HyperLogLog
        | "PFCOUNT" | "PFMERGE"
        // Stream (read/structural)
        | "XACK" | "XCLAIM" | "XDEL" | "XGROUP" | "XINFO" | "XLEN" | "XPENDING"
        | "XRANGE" | "XREAD" | "XREADGROUP" | "XREVRANGE" | "XTRIM"
        // Server (non-sensitive)
        | "COMMAND" | "DBSIZE" | "FLUSHALL" | "FLUSHDB" | "INFO" | "LOLWUT"
        | "PING" | "RESET" | "SELECT" | "SLOWLOG" | "SWAPDB" | "TIME"
        // Pub/Sub (read/structural)
        | "PUBSUB"
        // Transaction
        | "DISCARD" | "EXEC" | "MULTI"
        // Scripting (structural)
        | "SCRIPT" | "FUNCTION" => MaskingPattern::ShowAll,

        // Default: mask everything for safety.
        _ => MaskingPattern::MaskAll,
    }
}

/// Serialize command arguments for `db.query.text`, hiding sensitive values
/// according to the command's `MaskingPattern`.
pub fn serialize_query_text(cmd: &Cmd) -> Option<String> {
    let mut args = cmd.args_iter().filter_map(|arg| match arg {
        Arg::Simple(b) => Some(String::from_utf8_lossy(b).into_owned()),
        _ => None,
    });

    let cmd_name = args.next()?;
    let remaining: Vec<String> = args.collect();

    if remaining.is_empty() {
        return Some(cmd_name);
    }

    let pattern = masking_pattern(&cmd_name);
    let mut parts = vec![cmd_name];

    match pattern {
        MaskingPattern::ShowAll => parts.extend(remaining),
        MaskingPattern::MaskAll => {
            parts.extend(remaining.iter().map(|_| "?".to_string()));
        }
        MaskingPattern::ShowFirst(n) => {
            let n = n.min(remaining.len());
            parts.extend_from_slice(&remaining[..n]);
            parts.extend((0..remaining.len() - n).map(|_| "?".to_string()));
        }
        MaskingPattern::InterleavedKeyValue => {
            for (i, arg) in remaining.iter().enumerate() {
                if i % 2 == 0 {
                    parts.push(arg.clone());
                } else {
                    parts.push("?".to_string());
                }
            }
        }
        MaskingPattern::ShowFirstThenInterleave(n) => {
            let n = n.min(remaining.len());
            parts.extend_from_slice(&remaining[..n]);
            for (i, arg) in remaining[n..].iter().enumerate() {
                if i % 2 == 0 {
                    parts.push(arg.clone()); // field name
                } else {
                    parts.push("?".to_string()); // value
                }
            }
        }
    }

    Some(parts.join(" "))
}

// OTel semantic conventions require `db.system.name` = "redis" for Redis-compatible clients.
// See: https://opentelemetry.io/docs/specs/semconv/db/redis/
// TODO: Determine this dynamically based on the connected engine (Valkey vs Redis)
// once the OTel spec defines a "valkey" value.
const DB_SYSTEM_NAME: &str = "redis";

/// Sets connection-level OTel DB attributes on a span (no command-specific attributes).
pub fn set_db_connection_attributes(span: &GlideSpan, client: &Client) {
    span.set_attribute("db.system.name", DB_SYSTEM_NAME);
    span.set_attribute("server.address", client.server_address().to_string());
    if let Some(port) = client.server_port() {
        span.set_attribute_i64("server.port", port as i64);
    }
    span.set_attribute("db.namespace", client.db_namespace().to_string());
}

/// Sets OTel DB semantic convention attributes on a single command span.
pub fn set_db_attributes(span: &GlideSpan, cmd: &Cmd, client: &Client) {
    set_db_connection_attributes(span, client);

    if let Some(Arg::Simple(name_bytes)) = cmd.args_iter().next() {
        let cmd_name = String::from_utf8_lossy(name_bytes).into_owned();
        span.set_attribute("db.operation.name", cmd_name);
    }

    if let Some(query_text) = serialize_query_text(cmd) {
        span.set_attribute("db.query.text", query_text);
    }
}

/// Build `db.query.text` for an EVALSHA script invocation.
/// Keys are shown; args are masked as they may contain sensitive values.
fn serialize_script_query_text(hash: &str, keys: &[&[u8]], args: &[&[u8]]) -> String {
    let mut parts: Vec<String> = Vec::with_capacity(3 + keys.len() + args.len());
    parts.push("EVALSHA".to_string());
    parts.push(hash.to_string());
    parts.push(keys.len().to_string());
    for key in keys {
        parts.push(String::from_utf8_lossy(key).into_owned());
    }
    for _ in args {
        parts.push("?".to_string());
    }
    parts.join(" ")
}

/// Sets OTel DB semantic convention attributes on an EVALSHA (script) span.
pub fn set_db_script_attributes(
    span: &GlideSpan,
    hash: &str,
    keys: &[&[u8]],
    args: &[&[u8]],
    client: &Client,
) {
    set_db_connection_attributes(span, client);
    span.set_attribute("db.operation.name", "EVALSHA");
    span.set_attribute(
        "db.query.text",
        serialize_script_query_text(hash, keys, args),
    );
}

/// Sets OTel DB semantic convention attributes on a batch (pipeline/transaction) span.
/// `db.query.text` is a newline-joined serialization of all commands.
/// `db.operation.name` is `PIPELINE <cmd>` if all commands are the same, otherwise `PIPELINE`.
pub fn set_db_batch_attributes<T: Borrow<Cmd>>(span: &GlideSpan, cmds: &[T], client: &Client) {
    set_db_connection_attributes(span, client);

    let mut query_texts: Vec<String> = Vec::with_capacity(cmds.len());
    let mut cmd_names: Vec<String> = Vec::with_capacity(cmds.len());

    for cmd in cmds {
        let cmd: &Cmd = cmd.borrow();
        if let Some(text) = serialize_query_text(cmd) {
            query_texts.push(text);
        }
        if let Some(Arg::Simple(name_bytes)) = cmd.args_iter().next() {
            cmd_names.push(String::from_utf8_lossy(name_bytes).into_owned());
        }
    }

    if !query_texts.is_empty() {
        span.set_attribute("db.query.text", query_texts.join("\n"));
    }

    let op_name = if !cmd_names.is_empty() && cmd_names.iter().all(|n| n == &cmd_names[0]) {
        format!("PIPELINE {}", cmd_names[0])
    } else {
        "PIPELINE".to_string()
    };
    span.set_attribute("db.operation.name", op_name);
}

#[cfg(test)]
mod tests {
    use super::*;
    use rstest::rstest;

    // --- serialize_query_text ---
    //
    // Table-driven tests: each entry is a realistic query an application would
    // issue, paired with the expected sanitized db.query.text output. There is
    // one representative case per masking pattern; the command-to-pattern
    // mapping is covered by the masking_pattern tests below.

    fn make_cmd(name: &str, args: &[&str]) -> Cmd {
        let mut cmd = redis::cmd(name);
        for arg in args {
            cmd.arg(*arg);
        }
        cmd
    }

    /// (command, args, expected db.query.text)
    const QUERY_TEXT_CASES: &[(&str, &[&str], &str)] = &[
        // -- No args --
        ("PING", &[], "PING"),
        // -- MaskAll: all arguments masked --
        ("AUTH", &["admin", "s3cret!"], "AUTH ? ?"),
        // -- ShowFirst(1): first argument visible, remaining args masked --
        (
            "APPEND",
            &["audit:log", "2026-02-24 action=login"],
            "APPEND audit:log ?",
        ),
        // -- ShowFirst(2): first two arguments visible, remaining args masked --
        (
            "DELEX",
            &["lock:resource", "IFEQ", "owner-id"],
            "DELEX lock:resource IFEQ ?",
        ),
        // -- ShowFirst(3): first three arguments visible, remaining args masked --
        (
            "LINSERT",
            &["playlist", "BEFORE", "track:5", "track:new"],
            "LINSERT playlist BEFORE track:5 ?",
        ),
        // -- InterleavedKeyValue: keys visible, values masked --
        (
            "MSET",
            &["user:1:name", "Alice", "user:2:name", "Bob"],
            "MSET user:1:name ? user:2:name ?",
        ),
        // -- ShowFirstThenInterleave(1): first argument visible, then field/value pairs --
        (
            "HMSET",
            &["product:500", "price", "29.99", "stock", "150"],
            "HMSET product:500 price ? stock ?",
        ),
        // -- ShowAll: all arguments visible --
        (
            "BITCOUNT",
            &["flags:user:42", "0", "-1"],
            "BITCOUNT flags:user:42 0 -1",
        ),
    ];

    #[test]
    fn test_serialize_query_text() {
        for (cmd_name, args, expected) in QUERY_TEXT_CASES {
            let cmd = make_cmd(cmd_name, args);
            let result = serialize_query_text(&cmd).unwrap();
            assert_eq!(&result, expected, "query: {cmd_name} {}", args.join(" "));
        }
    }

    // --- masking_pattern ---

    #[test]
    fn test_masking_pattern_case_insensitive() {
        assert_masking_pattern("set", MaskingPattern::ShowFirst(1)); // Lower case
        assert_masking_pattern("sEt", MaskingPattern::ShowFirst(1)); // Mixed case
        assert_masking_pattern("Set", MaskingPattern::ShowFirst(1)); // Capital case
    }

    #[rstest]
    #[case("ACL")]
    #[case("AUTH")]
    #[case("CLIENT")]
    #[case("CLUSTER")]
    #[case("CONFIG")]
    #[case("DEBUG")]
    #[case("ECHO")]
    #[case("HELLO")]
    #[case("MIGRATE")]
    fn test_masking_pattern_mask_all(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::MaskAll);
    }

    #[rstest]
    #[case("APPEND")]
    #[case("DELIFEQ")]
    #[case("GETSET")]
    #[case("LPUSH")]
    #[case("LPUSHX")]
    #[case("PFADD")]
    #[case("PUBLISH")]
    #[case("RPUSH")]
    #[case("RPUSHX")]
    #[case("SADD")]
    #[case("SET")]
    #[case("SETNX")]
    #[case("SPUBLISH")]
    #[case("XADD")]
    #[case("ZADD")]
    fn test_masking_pattern_show_first_1(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::ShowFirst(1));
    }

    #[rstest]
    #[case("DELEX")]
    #[case("HSETNX")]
    #[case("LPOS")]
    #[case("LSET")]
    #[case("PSETEX")]
    #[case("SETEX")]
    fn test_masking_pattern_show_first_2(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::ShowFirst(2));
    }

    #[rstest]
    #[case("LINSERT")]
    fn test_masking_pattern_show_first_3(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::ShowFirst(3));
    }

    #[rstest]
    #[case("MSET")]
    #[case("MSETNX")]
    fn test_masking_pattern_interleaved_key_value(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::InterleavedKeyValue);
    }

    #[rstest]
    #[case("HMSET")]
    #[case("HSET")]
    fn test_masking_pattern_show_first_then_interleave_1(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::ShowFirstThenInterleave(1));
    }

    #[rstest]
    #[case("BITCOUNT")]
    #[case("BITFIELD")]
    #[case("BITFIELD_RO")]
    #[case("BITOP")]
    #[case("BITPOS")]
    #[case("COMMAND")]
    #[case("COPY")]
    #[case("DBSIZE")]
    #[case("DECR")]
    #[case("DECRBY")]
    #[case("DEL")]
    #[case("DISCARD")]
    #[case("DUMP")]
    #[case("EXEC")]
    #[case("EXISTS")]
    #[case("EXPIRE")]
    #[case("EXPIREAT")]
    #[case("EXPIRETIME")]
    #[case("FLUSHALL")]
    #[case("FLUSHDB")]
    #[case("FUNCTION")]
    #[case("GEODIST")]
    #[case("GEOHASH")]
    #[case("GEOPOS")]
    #[case("GEORADIUS")]
    #[case("GEORADIUSBYMEMBER")]
    #[case("GEORADIUSBYMEMBER_RO")]
    #[case("GEORADIUS_RO")]
    #[case("GEOSEARCH")]
    #[case("GEOSEARCHSTORE")]
    #[case("GET")]
    #[case("GETBIT")]
    #[case("GETDEL")]
    #[case("GETEX")]
    #[case("GETRANGE")]
    #[case("HDEL")]
    #[case("HEXISTS")]
    #[case("HGET")]
    #[case("HGETALL")]
    #[case("HGETDEL")]
    #[case("HINCRBY")]
    #[case("HINCRBYFLOAT")]
    #[case("HKEYS")]
    #[case("HLEN")]
    #[case("HMGET")]
    #[case("HRANDFIELD")]
    #[case("HSCAN")]
    #[case("HSTRLEN")]
    #[case("HVALS")]
    #[case("INCR")]
    #[case("INCRBY")]
    #[case("INCRBYFLOAT")]
    #[case("INFO")]
    #[case("KEYS")]
    #[case("LINDEX")]
    #[case("LLEN")]
    #[case("LMOVE")]
    #[case("LMPOP")]
    #[case("LOLWUT")]
    #[case("LPOP")]
    #[case("LRANGE")]
    #[case("LREM")]
    #[case("LTRIM")]
    #[case("MGET")]
    #[case("MOVE")]
    #[case("MULTI")]
    #[case("OBJECT")]
    #[case("PERSIST")]
    #[case("PEXPIRE")]
    #[case("PEXPIREAT")]
    #[case("PEXPIRETIME")]
    #[case("PFCOUNT")]
    #[case("PFMERGE")]
    #[case("PING")]
    #[case("PTTL")]
    #[case("PUBSUB")]
    #[case("RANDOMKEY")]
    #[case("RENAME")]
    #[case("RENAMENX")]
    #[case("RESET")]
    #[case("RPOP")]
    #[case("RPOPLPUSH")]
    #[case("SCAN")]
    #[case("SCARD")]
    #[case("SCRIPT")]
    #[case("SDIFF")]
    #[case("SDIFFSTORE")]
    #[case("SELECT")]
    #[case("SINTER")]
    #[case("SINTERCARD")]
    #[case("SINTERSTORE")]
    #[case("SISMEMBER")]
    #[case("SLOWLOG")]
    #[case("SMEMBERS")]
    #[case("SMISMEMBER")]
    #[case("SMOVE")]
    #[case("SORT")]
    #[case("SORT_RO")]
    #[case("SPOP")]
    #[case("SRANDMEMBER")]
    #[case("SREM")]
    #[case("SSCAN")]
    #[case("STRLEN")]
    #[case("SUBSTR")]
    #[case("SUNION")]
    #[case("SUNIONSTORE")]
    #[case("SWAPDB")]
    #[case("TIME")]
    #[case("TOUCH")]
    #[case("TTL")]
    #[case("TYPE")]
    #[case("UNLINK")]
    #[case("UNWATCH")]
    #[case("WAIT")]
    #[case("WAITAOF")]
    #[case("WATCH")]
    #[case("XACK")]
    #[case("XCLAIM")]
    #[case("XDEL")]
    #[case("XGROUP")]
    #[case("XINFO")]
    #[case("XLEN")]
    #[case("XPENDING")]
    #[case("XRANGE")]
    #[case("XREAD")]
    #[case("XREADGROUP")]
    #[case("XREVRANGE")]
    #[case("XTRIM")]
    #[case("ZCARD")]
    #[case("ZCOUNT")]
    #[case("ZDIFF")]
    #[case("ZDIFFSTORE")]
    #[case("ZINCRBY")]
    #[case("ZINTER")]
    #[case("ZINTERCARD")]
    #[case("ZINTERSTORE")]
    #[case("ZLEXCOUNT")]
    #[case("ZMPOP")]
    #[case("ZMSCORE")]
    #[case("ZPOPMAX")]
    #[case("ZPOPMIN")]
    #[case("ZRANDMEMBER")]
    #[case("ZRANGE")]
    #[case("ZRANGEBYLEX")]
    #[case("ZRANGEBYSCORE")]
    #[case("ZRANGESTORE")]
    #[case("ZRANK")]
    #[case("ZREM")]
    #[case("ZREMRANGEBYLEX")]
    #[case("ZREMRANGEBYRANK")]
    #[case("ZREMRANGEBYSCORE")]
    #[case("ZREVRANGE")]
    #[case("ZREVRANGEBYLEX")]
    #[case("ZREVRANGEBYSCORE")]
    #[case("ZREVRANK")]
    #[case("ZSCAN")]
    #[case("ZSCORE")]
    #[case("ZUNION")]
    #[case("ZUNIONSTORE")]
    fn test_masking_pattern_show_all(#[case] cmd_name: &str) {
        assert_masking_pattern(cmd_name, MaskingPattern::ShowAll);
    }

    #[test]
    fn test_masking_pattern_mask_all_default() {
        assert_masking_pattern("UNKNOWN_COMMAND", MaskingPattern::MaskAll);
    }

    fn assert_masking_pattern(cmd_name: &str, expected: MaskingPattern) {
        assert_eq!(
            masking_pattern(cmd_name),
            expected,
            "masking_pattern({cmd_name:?})"
        );
    }

    // --- batch (pipeline) db.query.text ---

    #[test]
    fn test_batch_query_text_multiple_commands() {
        // Multiple different commands → newline-joined db.query.text
        let cmds = vec![
            make_cmd("SET", &["key1", "val1"]),
            make_cmd("GET", &["key2"]),
            make_cmd("HSET", &["hash1", "field", "value"]),
        ];

        let mut query_texts: Vec<String> = Vec::new();
        let mut cmd_names: Vec<String> = Vec::new();

        for cmd in &cmds {
            if let Some(text) = serialize_query_text(cmd) {
                query_texts.push(text);
            }
            if let Some(Arg::Simple(name_bytes)) = cmd.args_iter().next() {
                cmd_names.push(String::from_utf8_lossy(name_bytes).into_owned());
            }
        }

        let joined = query_texts.join("\n");
        assert_eq!(joined, "SET key1 ?\nGET key2\nHSET hash1 field ?");

        // Mixed commands → "PIPELINE" (no suffix)
        assert!(!cmd_names.iter().all(|n| n == &cmd_names[0]));
    }

    #[test]
    fn test_batch_query_text_same_commands() {
        // All same command → "PIPELINE GET"
        let cmds = vec![
            make_cmd("GET", &["key1"]),
            make_cmd("GET", &["key2"]),
            make_cmd("GET", &["key3"]),
        ];

        let mut query_texts: Vec<String> = Vec::new();
        let mut cmd_names: Vec<String> = Vec::new();

        for cmd in &cmds {
            if let Some(text) = serialize_query_text(cmd) {
                query_texts.push(text);
            }
            if let Some(Arg::Simple(name_bytes)) = cmd.args_iter().next() {
                cmd_names.push(String::from_utf8_lossy(name_bytes).into_owned());
            }
        }

        let joined = query_texts.join("\n");
        assert_eq!(joined, "GET key1\nGET key2\nGET key3");

        // All same → "PIPELINE GET"
        assert!(cmd_names.iter().all(|n| n == &cmd_names[0]));
        let op_name = format!("PIPELINE {}", cmd_names[0]);
        assert_eq!(op_name, "PIPELINE GET");
    }

    #[test]
    fn test_batch_query_text_with_masking() {
        // Batch with commands that use different masking patterns
        let cmds = vec![
            make_cmd("AUTH", &["password123"]),          // MaskAll
            make_cmd("SET", &["key", "secret-val"]),     // ShowFirst(1)
            make_cmd("MSET", &["k1", "v1", "k2", "v2"]), // InterleavedKeyValue
        ];

        let mut query_texts: Vec<String> = Vec::new();
        for cmd in &cmds {
            if let Some(text) = serialize_query_text(cmd) {
                query_texts.push(text);
            }
        }

        let joined = query_texts.join("\n");
        assert_eq!(joined, "AUTH ?\nSET key ?\nMSET k1 ? k2 ?");
    }

    // --- serialize_script_query_text ---

    #[test]
    fn test_serialize_script_query_text_with_keys_and_args() {
        let result = serialize_script_query_text(
            "abc123def456",
            &[b"user:1", b"user:2"],
            &[b"secret-val1", b"secret-val2"],
        );
        assert_eq!(result, "EVALSHA abc123def456 2 user:1 user:2 ? ?");
    }

    #[test]
    fn test_serialize_script_query_text_keys_only() {
        let result = serialize_script_query_text("sha1hash", &[b"mykey"], &[]);
        assert_eq!(result, "EVALSHA sha1hash 1 mykey");
    }

    #[test]
    fn test_serialize_script_query_text_no_keys_no_args() {
        let result = serialize_script_query_text("emptyscript", &[], &[]);
        assert_eq!(result, "EVALSHA emptyscript 0");
    }

    #[test]
    fn test_serialize_script_query_text_args_only() {
        let result = serialize_script_query_text("argsonly", &[], &[b"arg1", b"arg2", b"arg3"]);
        assert_eq!(result, "EVALSHA argsonly 0 ? ? ?");
    }
}
