// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::Client;
use redis::{Arg, Cmd};
use telemetrylib::GlideSpan;

/// Defines how command arguments are masked in `db.query.text` to prevent
/// sensitive values from leaking into telemetry.
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
/// The default is [`MaskingPattern::MaskAll`] to prevent accidental credential leakage
/// from commands not explicitly listed here (e.g., `CONFIG SET requirepass`,
/// `ACL SETUSER`, `MIGRATE`, `HELLO` with AUTH).
fn masking_pattern(cmd_name: &str) -> MaskingPattern {
    match cmd_name.to_ascii_uppercase().as_str() {
        // -- MaskAll: all arguments are sensitive --
        "AUTH" | "ECHO" | "HELLO" => MaskingPattern::MaskAll,

        // -- ShowFirst(1): only key/channel visible --
        // Note: ZADD has optional flags (NX|XX|GT|LT|CH|INCR) before score/member pairs,
        // making it hard to fit a more granular pattern. ShowFirst(1) is the safe choice.
        "APPEND" | "GETSET" | "LPUSH" | "LPUSHX" | "PFADD" | "PUBLISH" | "RPUSH" | "RPUSHX"
        | "SADD" | "SET" | "SETNX" | "SPUBLISH" | "XADD" | "ZADD" => {
            MaskingPattern::ShowFirst(1)
        }

        // -- ShowFirst(2): key + TTL/field visible, value masked --
        "SETEX" | "PSETEX" | "HSETNX" | "LSET" | "LPOS" => MaskingPattern::ShowFirst(2),

        // LINSERT key BEFORE|AFTER pivot element — key, keyword, pivot are non-sensitive
        "LINSERT" => MaskingPattern::ShowFirst(3),

        // -- InterleavedKeyValue: args are directly key/value pairs --
        "MSET" | "MSETNX" => MaskingPattern::InterleavedKeyValue,

        // -- ShowFirstThenInterleave: key visible, then field/value pairs interleaved --
        "HSET" | "HMSET" => MaskingPattern::ShowFirstThenInterleave(1),

        // -- ShowAll: all arguments are non-sensitive (keys, patterns, indices, etc.) --
        // Note: commands with subcommands that may carry credentials (CONFIG, ACL,
        // CLIENT, DEBUG, HELLO, MIGRATE, CLUSTER) are intentionally omitted here
        // so they fall through to the MaskAll default.
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
        | "HDEL" | "HEXISTS" | "HGET" | "HGETALL" | "HINCRBY" | "HINCRBYFLOAT"
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

        // Default: mask everything for safety. Any unlisted command (including
        // credential-bearing ones like CONFIG SET requirepass, ACL SETUSER,
        // MIGRATE with auth password) will have all arguments masked.
        _ => MaskingPattern::MaskAll,
    }
}

/// Serialize command arguments for `db.query.text`, hiding sensitive values
/// according to the command's [`MaskingPattern`].
pub(crate) fn serialize_query_text(cmd: &Cmd) -> Option<String> {
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
pub(crate) fn set_db_connection_attributes(span: &GlideSpan, client: &Client) {
    span.set_attribute("db.system.name", DB_SYSTEM_NAME);
    span.set_attribute("server.address", client.server_address().to_string());
    span.set_attribute_i64("server.port", client.server_port() as i64);
    span.set_attribute("db.namespace", client.db_namespace().to_string());
}

/// Sets OTel DB semantic convention attributes on a single command span.
pub(crate) fn set_db_attributes(span: &GlideSpan, cmd: &Cmd, client: &Client) {
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
pub(crate) fn set_db_script_attributes(
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
pub(crate) fn set_db_batch_attributes(span: &GlideSpan, cmds: &[Cmd], client: &Client) {
    set_db_connection_attributes(span, client);

    let mut query_texts: Vec<String> = Vec::with_capacity(cmds.len());
    let mut cmd_names: Vec<String> = Vec::with_capacity(cmds.len());

    for cmd in cmds {
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

    // --- serialize_query_text ---
    //
    // Table-driven tests: each entry is a realistic query an application would
    // issue, paired with the expected sanitized db.query.text output.
    // This serves as both the masking_pattern mapping test and the
    // serialize_query_text integration test.

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
        // -- MaskAll: all arguments are sensitive --
        // AUTH <password>
        ("AUTH", &["s3cret!"], "AUTH ?"),
        // AUTH <username> <password>  (ACL-based auth)
        ("AUTH", &["admin", "s3cret!"], "AUTH ? ?"),
        // ECHO <message>
        ("ECHO", &["sensitive-payload"], "ECHO ?"),
        // -- ShowFirst(1): key visible, value and remaining options masked --
        // SET key value
        (
            "SET",
            &["user:1001", r#"{"name":"Alice"}"#],
            "SET user:1001 ?",
        ),
        // SET key value EX seconds NX
        (
            "SET",
            &["session:abc", "token123", "EX", "3600", "NX"],
            "SET session:abc ? ? ? ?",
        ),
        // SETNX key value
        (
            "SETNX",
            &["lock:resource", "owner-id"],
            "SETNX lock:resource ?",
        ),
        // SETEX key seconds value — TTL is non-sensitive
        (
            "SETEX",
            &["cache:page:home", "300", "<html>..."],
            "SETEX cache:page:home 300 ?",
        ),
        // PSETEX key milliseconds value — TTL is non-sensitive
        (
            "PSETEX",
            &["ratelimit:user:42", "1500", "1"],
            "PSETEX ratelimit:user:42 1500 ?",
        ),
        // APPEND key value
        (
            "APPEND",
            &["audit:log", "2026-02-24 action=login"],
            "APPEND audit:log ?",
        ),
        // GETSET key value
        (
            "GETSET",
            &["counter:visits", "0"],
            "GETSET counter:visits ?",
        ),
        // LPUSH key element [element ...]
        (
            "LPUSH",
            &["queue:jobs", "job1", "job2", "job3"],
            "LPUSH queue:jobs ? ? ?",
        ),
        // LPUSHX key element
        (
            "LPUSHX",
            &["queue:existing", "new-item"],
            "LPUSHX queue:existing ?",
        ),
        // RPUSH key element
        (
            "RPUSH",
            &["notifications:user:5", r#"{"type":"mention"}"#],
            "RPUSH notifications:user:5 ?",
        ),
        // RPUSHX key element
        (
            "RPUSHX",
            &["pending:emails", "msg-body"],
            "RPUSHX pending:emails ?",
        ),
        // SADD key member [member ...]
        (
            "SADD",
            &["tags:article:99", "rust", "async", "valkey"],
            "SADD tags:article:99 ? ? ?",
        ),
        // PFADD key element [element ...]
        (
            "PFADD",
            &["unique:visitors", "user1", "user2", "user3"],
            "PFADD unique:visitors ? ? ?",
        ),
        // PUBLISH channel message
        (
            "PUBLISH",
            &["chat:room:1", "Hello everyone!"],
            "PUBLISH chat:room:1 ?",
        ),
        // SPUBLISH shardchannel message
        (
            "SPUBLISH",
            &["orders:shard1", r#"{"orderId":42}"#],
            "SPUBLISH orders:shard1 ?",
        ),
        // ZADD key score member [score member ...]
        (
            "ZADD",
            &["leaderboard", "9500", "player:42"],
            "ZADD leaderboard ? ?",
        ),
        // XADD key * field value [field value ...]
        (
            "XADD",
            &["stream:events", "*", "action", "login", "user", "alice"],
            "XADD stream:events ? ? ? ? ?",
        ),
        // -- ShowFirstThenInterleave(1): key visible, then field/value pairs --
        // HSET key field value
        (
            "HSET",
            &["user:1001", "email", "alice@example.com"],
            "HSET user:1001 email ?",
        ),
        // HSET key field value field value (multiple field-value pairs)
        (
            "HSET",
            &["user:1001", "email", "a@b.com", "name", "Alice"],
            "HSET user:1001 email ? name ?",
        ),
        // HMSET key field value [field value ...]
        (
            "HMSET",
            &["product:500", "price", "29.99", "stock", "150"],
            "HMSET product:500 price ? stock ?",
        ),
        // HSETNX key field value
        (
            "HSETNX",
            &["user:1001", "created_at", "2026-01-01"],
            "HSETNX user:1001 created_at ?",
        ),
        // LSET key index value
        (
            "LSET",
            &["queue:jobs", "0", "updated-payload"],
            "LSET queue:jobs 0 ?",
        ),
        // LINSERT key BEFORE|AFTER pivot element
        (
            "LINSERT",
            &["playlist", "BEFORE", "track:5", "track:new"],
            "LINSERT playlist BEFORE track:5 ?",
        ),
        // LPOS key element [RANK rank] [COUNT count]
        (
            "LPOS",
            &["queue:jobs", "target-item", "RANK", "1", "COUNT", "2"],
            "LPOS queue:jobs target-item ? ? ? ?",
        ),
        // -- InterleavedKeyValue: keys visible, values masked --
        // MSET key value [key value ...]
        ("MSET", &["config:retries", "5"], "MSET config:retries ?"),
        (
            "MSET",
            &[
                "user:1:name",
                "Alice",
                "user:2:name",
                "Bob",
                "user:3:name",
                "Carol",
            ],
            "MSET user:1:name ? user:2:name ? user:3:name ?",
        ),
        // MSETNX key value [key value ...]
        (
            "MSETNX",
            &["lock:a", "owner1", "lock:b", "owner2"],
            "MSETNX lock:a ? lock:b ?",
        ),
        // -- ShowAll: all arguments are non-sensitive --
        // GET key
        ("GET", &["session:abc"], "GET session:abc"),
        // DEL key [key ...]
        (
            "DEL",
            &["temp:1", "temp:2", "temp:3"],
            "DEL temp:1 temp:2 temp:3",
        ),
        // HGET key field
        ("HGET", &["user:1001", "email"], "HGET user:1001 email"),
        // KEYS pattern
        ("KEYS", &["user:*"], "KEYS user:*"),
        // SINTERCARD numkeys key [key ...] [LIMIT limit]
        (
            "SINTERCARD",
            &["2", "set:a", "set:b", "LIMIT", "10"],
            "SINTERCARD 2 set:a set:b LIMIT 10",
        ),
        // SCAN cursor [MATCH pattern] [COUNT count]
        (
            "SCAN",
            &["0", "MATCH", "user:*", "COUNT", "100"],
            "SCAN 0 MATCH user:* COUNT 100",
        ),
        // -- MaskAll by default: unknown/credential-bearing commands --
        // Unknown command defaults to MaskAll for safety
        ("CUSTOMCMD", &["arg1", "arg2"], "CUSTOMCMD ? ?"),
        // CONFIG SET requirepass — password must not leak
        ("CONFIG", &["SET", "requirepass", "s3cret"], "CONFIG ? ? ?"),
        // ACL SETUSER — tokens must not leak
        ("ACL", &["SETUSER", "admin", ">password"], "ACL ? ? ?"),
        // MIGRATE — contains auth password argument
        (
            "MIGRATE",
            &["host", "6379", "key", "0", "5000", "AUTH", "s3cret"],
            "MIGRATE ? ? ? ? ? ? ?",
        ),
        // HELLO with AUTH — password must not leak
        ("HELLO", &["3", "AUTH", "user", "pass"], "HELLO ? ? ? ?"),
    ];

    #[test]
    fn test_serialize_query_text() {
        for (cmd_name, args, expected) in QUERY_TEXT_CASES {
            let cmd = make_cmd(cmd_name, args);
            let result = serialize_query_text(&cmd).unwrap();
            assert_eq!(&result, expected, "query: {cmd_name} {}", args.join(" "));
        }
    }

    #[test]
    fn test_masking_pattern_case_insensitive() {
        // to_ascii_uppercase() is applied internally, one case is sufficient
        assert!(matches!(
            masking_pattern("set"),
            MaskingPattern::ShowFirst(1)
        ));
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
