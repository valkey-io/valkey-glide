use std::{
    io::{self, Read},
    str,
};

use crate::types::{
    ErrorKind, PushKind, RedisError, RedisResult, ServerError, ServerErrorKind, Value,
    VerbatimFormat,
};

use logger_core::log_error;
use telemetrylib::GlideOpenTelemetry;

use combine::{
    any,
    error::StreamError,
    opaque,
    parser::{
        byte::{crlf, take_until_bytes},
        combinator::{any_send_sync_partial_state, AnySendSyncPartialState},
        range::{recognize, take},
    },
    stream::{PointerOffset, RangeStream, StreamErrorFor},
    ParseError, Parser as _,
};
use num_bigint::BigInt;

const MAX_RECURSE_DEPTH: usize = 100;

fn err_parser(line: &str) -> ServerError {
    let mut pieces = line.splitn(2, ' ');
    let kind = match pieces.next().unwrap() {
        "ERR" => ServerErrorKind::ResponseError,
        "EXECABORT" => ServerErrorKind::ExecAbortError,
        "LOADING" => ServerErrorKind::BusyLoadingError,
        "NOSCRIPT" => ServerErrorKind::NoScriptError,
        "MOVED" => {
            // record moved error metric if telemetry is initialized
            if let Err(e) = GlideOpenTelemetry::record_moved_error() {
                log_error(
                    "OpenTelemetry:moved_error",
                    format!("Failed to record moved error: {e}"),
                );
            }
            ServerErrorKind::Moved
        }
        "ASK" => ServerErrorKind::Ask,
        "TRYAGAIN" => ServerErrorKind::TryAgain,
        "CLUSTERDOWN" => ServerErrorKind::ClusterDown,
        "CROSSSLOT" => ServerErrorKind::CrossSlot,
        "MASTERDOWN" => ServerErrorKind::MasterDown,
        "READONLY" => ServerErrorKind::ReadOnly,
        "NOTBUSY" => ServerErrorKind::NotBusy,
        "NOPERM" => ServerErrorKind::PermissionDenied,
        code => {
            return ServerError::ExtensionError {
                code: code.to_string(),
                detail: pieces.next().map(|str| str.to_string()),
            }
        }
    };
    let detail = pieces.next().map(|str| str.to_string());
    ServerError::KnownError { kind, detail }
}

pub fn get_push_kind(kind: String) -> PushKind {
    match kind.as_str() {
        "invalidate" => PushKind::Invalidate,
        "message" => PushKind::Message,
        "pmessage" => PushKind::PMessage,
        "smessage" => PushKind::SMessage,
        "unsubscribe" => PushKind::Unsubscribe,
        "punsubscribe" => PushKind::PUnsubscribe,
        "sunsubscribe" => PushKind::SUnsubscribe,
        "subscribe" => PushKind::Subscribe,
        "psubscribe" => PushKind::PSubscribe,
        "ssubscribe" => PushKind::SSubscribe,
        _ => PushKind::Other(kind),
    }
}

fn value<'a, I>(
    count: Option<usize>,
) -> impl combine::Parser<I, Output = Value, PartialState = AnySendSyncPartialState>
where
    I: RangeStream<Token = u8, Range = &'a [u8]>,
    I::Error: combine::ParseError<u8, &'a [u8], I::Position>,
{
    let count = count.unwrap_or(1);

    opaque!(any_send_sync_partial_state(
        any()
            .then_partial(move |&mut b| {
                // All RESP3 aggregate types recurse into `value()` (incrementing
                // the depth counter), so the depth guard must cover every one of
                // them - not just arrays - to prevent stack exhaustion from a
                // maliciously deep payload.
                let is_aggregate = matches!(b, b'*' | b'%' | b'|' | b'~' | b'>');
                if is_aggregate && count > MAX_RECURSE_DEPTH {
                    combine::unexpected_any("Maximum recursion depth exceeded").left()
                } else {
                    combine::value(b).right()
                }
            })
            .then_partial(move |&mut b| {
                let line = || {
                    recognize(take_until_bytes(&b"\r\n"[..]).with(take(2).map(|_| ()))).and_then(
                        |line: &[u8]| {
                            str::from_utf8(&line[..line.len() - 2])
                                .map_err(StreamErrorFor::<I>::other)
                        },
                    )
                };

                let simple_string = || {
                    line().map(|line| {
                        if line == "OK" {
                            Value::Okay
                        } else {
                            Value::SimpleString(line.into())
                        }
                    })
                };

                let int = || {
                    line().and_then(|line| {
                        line.trim().parse::<i64>().map_err(|_| {
                            StreamErrorFor::<I>::message_static_message(
                                "Expected integer, got garbage",
                            )
                        })
                    })
                };

                let bulk_string = || {
                    int().then_partial(move |size| {
                        if *size < 0 {
                            combine::produce(|| Value::Nil).left()
                        } else {
                            take(*size as usize)
                                .map(|bs: &[u8]| {
                                    Value::BulkString(bytes::Bytes::copy_from_slice(bs))
                                })
                                .skip(crlf())
                                .right()
                        }
                    })
                };
                let blob = || {
                    int().then_partial(move |size| {
                        take(*size as usize)
                            .map(|bs: &[u8]| String::from_utf8_lossy(bs).to_string())
                            .skip(crlf())
                    })
                };

                let array = || {
                    int().then_partial(move |&mut length| {
                        if length < 0 {
                            combine::produce(|| Value::Nil).left()
                        } else {
                            let length = length as usize;
                            combine::count_min_max(length, length, value(Some(count + 1)))
                                .map(Value::Array)
                                .right()
                        }
                    })
                };

                let error = || line().map(err_parser);
                let map = || {
                    int().then_partial(move |&mut kv_length| {
                        let length = kv_length as usize * 2;
                        combine::count_min_max(length, length, value(Some(count + 1))).map(
                            move |result: Vec<Value>| {
                                let mut it = result.into_iter();
                                let mut x = vec![];
                                for _ in 0..kv_length {
                                    if let (Some(k), Some(v)) = (it.next(), it.next()) {
                                        x.push((k, v))
                                    }
                                }
                                Value::Map(x)
                            },
                        )
                    })
                };
                let attribute = || {
                    int().then_partial(move |&mut kv_length| {
                        // + 1 is for data!
                        let length = kv_length as usize * 2 + 1;
                        combine::count_min_max(length, length, value(Some(count + 1))).map(
                            move |result: Vec<Value>| {
                                let mut it = result.into_iter();
                                let mut attributes = vec![];
                                for _ in 0..kv_length {
                                    if let (Some(k), Some(v)) = (it.next(), it.next()) {
                                        attributes.push((k, v))
                                    }
                                }
                                Value::Attribute {
                                    data: Box::new(it.next().unwrap()),
                                    attributes,
                                }
                            },
                        )
                    })
                };
                let set = || {
                    int().then_partial(move |&mut length| {
                        if length < 0 {
                            combine::produce(|| Value::Nil).left()
                        } else {
                            let length = length as usize;
                            combine::count_min_max(length, length, value(Some(count + 1)))
                                .map(Value::Set)
                                .right()
                        }
                    })
                };
                let push = || {
                    int().then_partial(move |&mut length| {
                        if length <= 0 {
                            combine::produce(|| Value::Push {
                                kind: PushKind::Other("".to_string()),
                                data: vec![],
                            })
                            .left()
                        } else {
                            let length = length as usize;
                            combine::count_min_max(length, length, value(Some(count + 1)))
                                .and_then(|result: Vec<Value>| {
                                    let mut it = result.into_iter();
                                    let first = it.next().unwrap_or(Value::Nil);
                                    if let Value::BulkString(kind) = first {
                                        let push_kind = String::from_utf8(kind.to_vec())
                                            .map_err(StreamErrorFor::<I>::other)?;
                                        Ok(Value::Push {
                                            kind: get_push_kind(push_kind),
                                            data: it.collect(),
                                        })
                                    } else if let Value::SimpleString(kind) = first {
                                        Ok(Value::Push {
                                            kind: get_push_kind(kind),
                                            data: it.collect(),
                                        })
                                    } else {
                                        Err(StreamErrorFor::<I>::message_static_message(
                                            "parse error when decoding push",
                                        ))
                                    }
                                })
                                .right()
                        }
                    })
                };
                let null = || line().map(|_| Value::Nil);
                let double = || {
                    line().and_then(|line| {
                        line.trim()
                            .parse::<f64>()
                            .map_err(StreamErrorFor::<I>::other)
                    })
                };
                let boolean = || {
                    line().and_then(|line: &str| match line {
                        "t" => Ok(true),
                        "f" => Ok(false),
                        _ => Err(StreamErrorFor::<I>::message_static_message(
                            "Expected boolean, got garbage",
                        )),
                    })
                };
                let blob_error = || blob().map(|line| err_parser(&line));
                let verbatim = || {
                    blob().and_then(|line| {
                        if let Some((format, text)) = line.split_once(':') {
                            let format = match format {
                                "txt" => VerbatimFormat::Text,
                                "mkd" => VerbatimFormat::Markdown,
                                x => VerbatimFormat::Unknown(x.to_string()),
                            };
                            Ok(Value::VerbatimString {
                                format,
                                text: text.to_string(),
                            })
                        } else {
                            Err(StreamErrorFor::<I>::message_static_message(
                                "parse error when decoding verbatim string",
                            ))
                        }
                    })
                };
                let big_number = || {
                    line().and_then(|line| {
                        BigInt::parse_bytes(line.as_bytes(), 10).ok_or_else(|| {
                            StreamErrorFor::<I>::message_static_message(
                                "Expected bigint, got garbage",
                            )
                        })
                    })
                };
                combine::dispatch!(b;
                    b'+' => simple_string(),
                    b':' => int().map(Value::Int),
                    b'$' => bulk_string(),
                    b'*' => array(),
                    b'%' => map(),
                    b'|' => attribute(),
                    b'~' => set(),
                    b'-' => error().map(Value::ServerError),
                    b'_' => null(),
                    b',' => double().map(Value::Double),
                    b'#' => boolean().map(Value::Boolean),
                    b'!' => blob_error().map(Value::ServerError),
                    b'=' => verbatim(),
                    b'(' => big_number().map(Value::BigNumber),
                    b'>' => push(),
                    b => combine::unexpected_any(combine::error::Token(b))
                )
            })
    ))
}

/// Zero-copy RESP parsing for the async codec path.
///
/// Strategy: instead of streaming partial frames through the `combine` parser
/// (which must copy payloads into owned `Vec`s because a bulk string may
/// straddle socket reads), we first *scan* the buffered bytes for one complete
/// top-level frame without consuming anything. Once a full frame is buffered,
/// we `split_to(len).freeze()` it into a refcounted [`bytes::Bytes`] (zero-copy) and
/// build the [`Value`] tree by slicing bulk-string payloads directly out of
/// that frame — no per-value allocation or memcpy.
///
/// The scan only walks type bytes and length headers (payloads are skipped by
/// length), so its cost is proportional to the number of RESP elements, not
/// the payload bytes.
mod zero_copy {
    use super::*;
    use bytes::Bytes;

    fn parse_error(detail: String) -> RedisError {
        RedisError::from((ErrorKind::ParseError, "parse error", detail))
    }

    /// Find the `\r\n`-terminated line starting at `pos`.
    /// Returns `(line_content_end, next_pos)` — content excludes the CRLF.
    ///
    /// Known worst case: while a line is *incomplete*, each `decode` call
    /// rescans it from `pos` (`ScanState.pos` only advances on complete
    /// elements), so a line-delimited element straddling many reads costs
    /// O(reads × line length). Bulk payloads are skipped by length and
    /// unaffected; RESP lines (headers, simple strings/errors) are short in
    /// practice, so we keep the scanner simple rather than tracking
    /// intra-line progress.
    fn find_line(buf: &[u8], pos: usize) -> Option<(usize, usize)> {
        let rel = buf[pos..].windows(2).position(|w| w == b"\r\n")?;
        Some((pos + rel, pos + rel + 2))
    }

    fn line_str(buf: &[u8], start: usize, end: usize) -> RedisResult<&str> {
        str::from_utf8(&buf[start..end])
            .map_err(|e| parse_error(format!("invalid utf-8 in line: {e}")))
    }

    fn line_int(buf: &[u8], start: usize, end: usize) -> RedisResult<i64> {
        line_str(buf, start, end)?
            .trim()
            .parse::<i64>()
            .map_err(|_| parse_error("Expected integer, got garbage".to_string()))
    }

    /// How many child values follow an aggregate header byte with count `n`
    /// (`n >= 0`). Errors instead of wrapping if the count overflows `usize`
    /// (only reachable on 32-bit targets — a hostile length header must not
    /// silently truncate into a misparse).
    fn child_count(type_byte: u8, n: i64) -> RedisResult<usize> {
        let n = usize::try_from(n)
            .map_err(|_| parse_error("aggregate length exceeds platform limits".to_string()))?;
        match type_byte {
            b'%' => n.checked_mul(2),
            b'|' => n.checked_mul(2).and_then(|c| c.checked_add(1)), // kv pairs + the data value
            _ => Some(n),
        }
        .ok_or_else(|| parse_error("aggregate length exceeds platform limits".to_string()))
    }

    /// Convert a non-negative blob size to `usize`, erroring instead of
    /// truncating on 32-bit targets.
    fn blob_size(size: i64) -> RedisResult<usize> {
        usize::try_from(size)
            .map_err(|_| parse_error("blob length exceeds platform limits".to_string()))
    }

    /// Resumable scan state, persisted across `decode` calls so bytes of an
    /// incomplete frame are only scanned once (previously the scan restarted
    /// at offset 0 on every socket read — O(reads × elements) on large
    /// multi-element frames, measured at ~25% of client CPU on MGET-heavy
    /// load).
    ///
    /// Representation: `stack` holds the number of values still expected at
    /// each open aggregate level, with an artificial root entry of 1 for the
    /// top-level value. `pos` is the absolute offset (from the front of the
    /// codec's read buffer) where scanning resumes; it stays valid across
    /// calls because the codec only consumes buffer bytes once a frame
    /// completes.
    pub(super) struct ScanState {
        pos: usize,
        stack: Vec<usize>,
    }

    impl Default for ScanState {
        fn default() -> Self {
            ScanState {
                pos: 0,
                stack: vec![1],
            }
        }
    }

    impl ScanState {
        pub(super) fn reset(&mut self) {
            self.pos = 0;
            self.stack.clear();
            self.stack.push(1);
        }
    }

    /// Continue scanning one top-level RESP value where the previous call
    /// left off.
    /// `Ok(Some(end))` — a complete value spans `0..end` of `buf`.
    /// `Ok(None)` — need more data (state saved; call again with more bytes).
    /// `Err` — malformed input (connection-fatal, state irrelevant).
    pub(super) fn scan_resume(buf: &[u8], st: &mut ScanState) -> RedisResult<Option<usize>> {
        loop {
            // Close out completed aggregates.
            while let Some(&top) = st.stack.last() {
                if top == 0 {
                    st.stack.pop();
                } else {
                    break;
                }
            }
            let depth = st.stack.len();
            let Some(remaining) = st.stack.last_mut() else {
                return Ok(Some(st.pos));
            };

            let pos = st.pos;
            if pos >= buf.len() {
                return Ok(None);
            }
            let b = buf[pos];
            // `depth` equals the recursion depth the old recursive
            // scanner would have had (root entry counts as depth 1).
            if matches!(b, b'*' | b'%' | b'|' | b'~' | b'>') && depth > MAX_RECURSE_DEPTH {
                return Err(parse_error("Maximum recursion depth exceeded".to_string()));
            }
            match b {
                // Line-delimited scalars.
                b'+' | b'-' | b':' | b'_' | b',' | b'#' | b'(' => {
                    let Some((_, next)) = find_line(buf, pos + 1) else {
                        return Ok(None);
                    };
                    *remaining -= 1;
                    st.pos = next;
                }
                // Length-prefixed blobs: bulk string, blob error, verbatim string.
                b'$' | b'!' | b'=' => {
                    let Some((line_end, payload_start)) = find_line(buf, pos + 1) else {
                        return Ok(None);
                    };
                    let size = line_int(buf, pos + 1, line_end)?;
                    if size < 0 {
                        *remaining -= 1;
                        st.pos = payload_start;
                        continue;
                    }
                    let end = blob_size(size)?
                        .checked_add(payload_start)
                        .and_then(|e| e.checked_add(2))
                        .ok_or_else(|| {
                            parse_error("blob length exceeds platform limits".to_string())
                        })?;
                    if end > buf.len() {
                        return Ok(None);
                    }
                    if &buf[end - 2..end] != b"\r\n" {
                        return Err(parse_error("expected CRLF after blob payload".to_string()));
                    }
                    *remaining -= 1;
                    st.pos = end;
                }
                // Aggregates: array, map, attribute, set, push.
                b'*' | b'%' | b'|' | b'~' | b'>' => {
                    let Some((line_end, next)) = find_line(buf, pos + 1) else {
                        return Ok(None);
                    };
                    let n = line_int(buf, pos + 1, line_end)?;
                    *remaining -= 1;
                    st.pos = next;
                    if n >= 0 {
                        st.stack.push(child_count(b, n)?);
                    }
                }
                other => {
                    return Err(parse_error(format!(
                        "invalid RESP type byte {:?}",
                        other as char
                    )))
                }
            }
        }
    }

    /// Build a [`Value`] from a complete frame, slicing bulk-string payloads
    /// out of `frame` with zero copies. `scan_value` must have validated the
    /// frame is complete; bounds are still checked defensively.
    pub(super) fn parse_value(frame: &Bytes, pos: &mut usize, depth: usize) -> RedisResult<Value> {
        let buf = &frame[..];
        if *pos >= buf.len() {
            return Err(parse_error("unexpected end of frame".to_string()));
        }
        let b = buf[*pos];
        if matches!(b, b'*' | b'%' | b'|' | b'~' | b'>') && depth > MAX_RECURSE_DEPTH {
            return Err(parse_error("Maximum recursion depth exceeded".to_string()));
        }
        let type_pos = *pos;
        match b {
            b'+' | b'-' | b':' | b'_' | b',' | b'#' | b'(' => {
                let (line_end, next) = find_line(buf, type_pos + 1)
                    .ok_or_else(|| parse_error("unexpected end of frame".to_string()))?;
                let line = line_str(buf, type_pos + 1, line_end)?;
                *pos = next;
                match b {
                    b'+' => Ok(if line == "OK" {
                        Value::Okay
                    } else {
                        Value::SimpleString(line.to_string())
                    }),
                    b'-' => Ok(Value::ServerError(err_parser(line))),
                    b':' => Ok(Value::Int(line.trim().parse::<i64>().map_err(|_| {
                        parse_error("Expected integer, got garbage".to_string())
                    })?)),
                    b'_' => Ok(Value::Nil),
                    b',' => Ok(Value::Double(line.trim().parse::<f64>().map_err(|e| {
                        parse_error(format!("Expected double, got garbage: {e}"))
                    })?)),
                    b'#' => match line {
                        "t" => Ok(Value::Boolean(true)),
                        "f" => Ok(Value::Boolean(false)),
                        _ => Err(parse_error("Expected boolean, got garbage".to_string())),
                    },
                    b'(' => BigInt::parse_bytes(line.as_bytes(), 10)
                        .map(Value::BigNumber)
                        .ok_or_else(|| parse_error("Expected bigint, got garbage".to_string())),
                    _ => unreachable!(),
                }
            }
            b'$' | b'!' | b'=' => {
                let (line_end, payload_start) = find_line(buf, type_pos + 1)
                    .ok_or_else(|| parse_error("unexpected end of frame".to_string()))?;
                let size = line_int(buf, type_pos + 1, line_end)?;
                if size < 0 {
                    *pos = payload_start;
                    return Ok(Value::Nil);
                }
                let payload_end = blob_size(size)?
                    .checked_add(payload_start)
                    .filter(|e| e.checked_add(2).is_some())
                    .ok_or_else(|| {
                        parse_error("blob length exceeds platform limits".to_string())
                    })?;
                if payload_end + 2 > buf.len() {
                    return Err(parse_error("unexpected end of frame".to_string()));
                }
                *pos = payload_end + 2;
                match b {
                    // The zero-copy slice: shares the frame's refcounted buffer.
                    b'$' => Ok(Value::BulkString(frame.slice(payload_start..payload_end))),
                    b'!' => {
                        let text = String::from_utf8_lossy(&buf[payload_start..payload_end]);
                        Ok(Value::ServerError(err_parser(&text)))
                    }
                    b'=' => {
                        let text = String::from_utf8_lossy(&buf[payload_start..payload_end]);
                        if let Some((format, text)) = text.split_once(':') {
                            let format = match format {
                                "txt" => VerbatimFormat::Text,
                                "mkd" => VerbatimFormat::Markdown,
                                x => VerbatimFormat::Unknown(x.to_string()),
                            };
                            Ok(Value::VerbatimString {
                                format,
                                text: text.to_string(),
                            })
                        } else {
                            Err(parse_error(
                                "parse error when decoding verbatim string".to_string(),
                            ))
                        }
                    }
                    _ => unreachable!(),
                }
            }
            b'*' | b'%' | b'|' | b'~' | b'>' => {
                let (line_end, next) = find_line(buf, type_pos + 1)
                    .ok_or_else(|| parse_error("unexpected end of frame".to_string()))?;
                let n = line_int(buf, type_pos + 1, line_end)?;
                *pos = next;
                if n < 0 {
                    // Negative aggregate counts: `*-1`/`~-1` are RESP2 nil
                    // arrays. The old parser mapped a negative push count to
                    // an empty Push (same as `>0`), so preserve that; other
                    // negative counts are treated as Nil (the old parser had
                    // no sane behavior for them).
                    return Ok(if b == b'>' {
                        Value::Push {
                            kind: PushKind::Other("".to_string()),
                            data: vec![],
                        }
                    } else {
                        Value::Nil
                    });
                }
                match b {
                    b'*' | b'~' => {
                        let mut items = Vec::with_capacity(n as usize);
                        for _ in 0..n {
                            items.push(parse_value(frame, pos, depth + 1)?);
                        }
                        Ok(if b == b'*' {
                            Value::Array(items)
                        } else {
                            Value::Set(items)
                        })
                    }
                    b'%' => {
                        let mut items = Vec::with_capacity(n as usize);
                        for _ in 0..n {
                            let k = parse_value(frame, pos, depth + 1)?;
                            let v = parse_value(frame, pos, depth + 1)?;
                            items.push((k, v));
                        }
                        Ok(Value::Map(items))
                    }
                    b'|' => {
                        let mut attributes = Vec::with_capacity(n as usize);
                        for _ in 0..n {
                            let k = parse_value(frame, pos, depth + 1)?;
                            let v = parse_value(frame, pos, depth + 1)?;
                            attributes.push((k, v));
                        }
                        let data = Box::new(parse_value(frame, pos, depth + 1)?);
                        Ok(Value::Attribute { data, attributes })
                    }
                    b'>' => {
                        if n == 0 {
                            return Ok(Value::Push {
                                kind: PushKind::Other("".to_string()),
                                data: vec![],
                            });
                        }
                        let first = parse_value(frame, pos, depth + 1)?;
                        let mut data = Vec::with_capacity(n as usize - 1);
                        for _ in 1..n {
                            data.push(parse_value(frame, pos, depth + 1)?);
                        }
                        let kind = match first {
                            Value::BulkString(kind) => String::from_utf8(kind.to_vec())
                                .map_err(|e| parse_error(format!("invalid push kind: {e}")))?,
                            Value::SimpleString(kind) => kind,
                            _ => {
                                return Err(parse_error(
                                    "parse error when decoding push".to_string(),
                                ))
                            }
                        };
                        Ok(Value::Push {
                            kind: get_push_kind(kind),
                            data,
                        })
                    }
                    _ => unreachable!(),
                }
            }
            other => Err(parse_error(format!(
                "invalid RESP type byte {:?}",
                other as char
            ))),
        }
    }
}

#[cfg(feature = "aio")]
mod aio_support {
    use super::*;

    use bytes::{Buf, BytesMut};
    use tokio::io::AsyncRead;
    use tokio_util::codec::{Decoder, Encoder};

    /// Tokio codec that decodes RESP frames zero-copy from the read
    /// buffer. See the `zero_copy` module for the strategy.
    #[derive(Default)]
    pub struct ValueCodec {
        /// Resumable scan progress for the (single) incomplete frame at the
        /// front of the read buffer. Valid across calls because nothing is
        /// consumed until the frame completes.
        scan: super::zero_copy::ScanState,
    }

    impl ValueCodec {
        fn decode_stream(
            &mut self,
            bytes: &mut BytesMut,
            eof: bool,
        ) -> RedisResult<Option<RedisResult<Value>>> {
            if bytes.is_empty() {
                return Ok(None);
            }
            // Zero-copy path: wait until a complete top-level frame is
            // buffered (scan consumes nothing and resumes where the previous
            // call stopped), then extract that frame and slice bulk-string
            // payloads out of it.
            match super::zero_copy::scan_resume(&bytes[..], &mut self.scan) {
                Err(err) => {
                    self.scan.reset();
                    Err(err)
                }
                Ok(None) => {
                    if eof {
                        Err(RedisError::from((
                            ErrorKind::ParseError,
                            "parse error",
                            "unexpected end of input".to_string(),
                        )))
                    } else {
                        Ok(None)
                    }
                }
                Ok(Some(end)) => {
                    self.scan.reset();
                    // Copy the complete frame out of the read buffer into a
                    // recycled pooled buffer (see `buf_pool`), then slice
                    // bulk-string payloads out of it zero-copy.
                    //
                    // Copy-always was chosen from profiling against a real
                    // network peer:
                    // - Freezing frames out of the read buffer (even only
                    //   large ones) defeats the BytesMut allocation reuse and
                    //   causes realloc/page-fault churn in `reserve`; a
                    //   256 KB GET measured 2x the client CPU of the copy
                    //   path under pipelined load.
                    // - A fresh allocation per frame is the other churn
                    //   source (~16 concurrent frames alive at depth 16
                    //   defeat allocator reuse; fault handling reached 30% of
                    //   client CPU at 64 KB). The pool recycles the frame
                    //   buffers so their pages stay resident.
                    let frame = crate::buf_pool::pooled_bytes_from_slice(&bytes[..end]);
                    bytes.advance(end);
                    let mut pos = 0;
                    let value = super::zero_copy::parse_value(&frame, &mut pos, 1)?;
                    Ok(Some(Ok(value)))
                }
            }
        }
    }

    impl Encoder<Vec<u8>> for ValueCodec {
        type Error = RedisError;
        fn encode(&mut self, item: Vec<u8>, dst: &mut BytesMut) -> Result<(), Self::Error> {
            dst.extend_from_slice(item.as_ref());
            Ok(())
        }
    }

    impl Decoder for ValueCodec {
        type Item = RedisResult<Value>;
        type Error = RedisError;

        fn decode(&mut self, bytes: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
            self.decode_stream(bytes, false)
        }

        fn decode_eof(&mut self, bytes: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
            self.decode_stream(bytes, true)
        }
    }

    /// Parses a redis value asynchronously.
    pub async fn parse_redis_value_async<R>(
        decoder: &mut combine::stream::Decoder<AnySendSyncPartialState, PointerOffset<[u8]>>,
        read: &mut R,
    ) -> RedisResult<Value>
    where
        R: AsyncRead + std::marker::Unpin,
    {
        let result = combine::decode_tokio!(*decoder, *read, value(None), |input, _| {
            combine::stream::easy::Stream::from(input)
        });
        match result {
            Err(err) => Err(match err {
                combine::stream::decoder::Error::Io { error, .. } => error.into(),
                combine::stream::decoder::Error::Parse(err) => {
                    if err.is_unexpected_end_of_input() {
                        RedisError::from(io::Error::from(io::ErrorKind::UnexpectedEof))
                    } else {
                        let err = err
                            .map_range(|range| format!("{range:?}"))
                            .map_position(|pos| pos.translate_position(decoder.buffer()))
                            .to_string();
                        RedisError::from((ErrorKind::ParseError, "parse error", err))
                    }
                }
            }),
            Ok(result) => Ok(result),
        }
    }
}

#[cfg(feature = "aio")]
#[cfg_attr(docsrs, doc(cfg(feature = "aio")))]
pub use self::aio_support::*;

/// The internal redis response parser.
pub struct Parser {
    decoder: combine::stream::decoder::Decoder<AnySendSyncPartialState, PointerOffset<[u8]>>,
}

impl Default for Parser {
    fn default() -> Self {
        Parser::new()
    }
}

/// The parser can be used to parse redis responses into values.  Generally
/// you normally do not use this directly as it's already done for you by
/// the client but in some more complex situations it might be useful to be
/// able to parse the redis responses.
impl Parser {
    /// Creates a new parser that parses the data behind the reader.  More
    /// than one value can be behind the reader in which case the parser can
    /// be invoked multiple times.  In other words: the stream does not have
    /// to be terminated.
    pub fn new() -> Parser {
        Parser {
            decoder: combine::stream::decoder::Decoder::new(),
        }
    }

    // public api

    /// Parses synchronously into a single value from the reader.
    pub fn parse_value<T: Read>(&mut self, mut reader: T) -> RedisResult<Value> {
        let mut decoder = &mut self.decoder;
        let result = combine::decode!(decoder, reader, value(None), |input, _| {
            combine::stream::easy::Stream::from(input)
        });
        match result {
            Err(err) => Err(match err {
                combine::stream::decoder::Error::Io { error, .. } => error.into(),
                combine::stream::decoder::Error::Parse(err) => {
                    if err.is_unexpected_end_of_input() {
                        RedisError::from(io::Error::from(io::ErrorKind::UnexpectedEof))
                    } else {
                        let err = err
                            .map_range(|range| format!("{range:?}"))
                            .map_position(|pos| pos.translate_position(decoder.buffer()))
                            .to_string();
                        RedisError::from((ErrorKind::ParseError, "parse error", err))
                    }
                }
            }),
            Ok(result) => Ok(result),
        }
    }
}

/// Parses bytes into a redis value.
///
/// This is the most straightforward way to parse something into a low
/// level redis value instead of having to use a whole parser.
pub fn parse_redis_value(bytes: &[u8]) -> RedisResult<Value> {
    let mut parser = Parser::new();
    parser.parse_value(bytes)
}

#[cfg(test)]
mod tests {
    use crate::types::make_extension_error;

    use super::*;

    #[cfg(feature = "aio")]
    #[test]
    fn decode_eof_returns_none_at_eof() {
        use tokio_util::codec::Decoder;
        let mut codec = ValueCodec::default();

        let mut bytes = bytes::BytesMut::from(&b"+GET 123\r\n"[..]);
        assert_eq!(
            codec.decode_eof(&mut bytes),
            Ok(Some(Ok(parse_redis_value(b"+GET 123\r\n").unwrap())))
        );
        assert_eq!(codec.decode_eof(&mut bytes), Ok(None));
        assert_eq!(codec.decode_eof(&mut bytes), Ok(None));
    }

    #[cfg(feature = "aio")]
    #[test]
    fn decode_eof_returns_error_inside_array_and_can_parse_more_inputs() {
        use tokio_util::codec::Decoder;
        let mut codec = ValueCodec::default();

        let mut bytes =
            bytes::BytesMut::from(b"*3\r\n+OK\r\n-LOADING server is loading\r\n+OK\r\n".as_slice());
        let result = codec.decode_eof(&mut bytes).unwrap().unwrap();

        assert_eq!(
            result.unwrap().extract_error(),
            Err(RedisError::from((
                ErrorKind::BusyLoadingError,
                "An error was signalled by the server",
                "server is loading".to_string()
            )))
        );

        let mut bytes = bytes::BytesMut::from(b"+OK\r\n".as_slice());
        let result = codec.decode_eof(&mut bytes).unwrap().unwrap();

        assert_eq!(result, Ok(Value::Okay));
    }

    #[test]
    fn parse_nested_error_and_handle_more_inputs() {
        // from https://redis.io/docs/interact/transactions/ -
        // "EXEC returned two-element bulk string reply where one is an OK code and the other an error reply. It's up to the client library to find a sensible way to provide the error to the user."

        let bytes = b"*3\r\n+OK\r\n-LOADING server is loading\r\n+OK\r\n";
        let result = parse_redis_value(bytes);

        assert_eq!(
            result.unwrap().extract_error(),
            Err(RedisError::from((
                ErrorKind::BusyLoadingError,
                "An error was signalled by the server",
                "server is loading".to_string()
            )))
        );

        let result = parse_redis_value(b"+OK\r\n").unwrap();

        assert_eq!(result, Value::Okay);
    }

    #[test]
    fn decode_resp3_double() {
        let val = parse_redis_value(b",1.23\r\n").unwrap();
        assert_eq!(val, Value::Double(1.23));
        let val = parse_redis_value(b",nan\r\n").unwrap();
        if let Value::Double(val) = val {
            assert!(val.is_sign_positive());
            assert!(val.is_nan());
        } else {
            panic!("expected double");
        }
        // -nan is supported prior to redis 7.2
        let val = parse_redis_value(b",-nan\r\n").unwrap();
        if let Value::Double(val) = val {
            assert!(val.is_sign_negative());
            assert!(val.is_nan());
        } else {
            panic!("expected double");
        }
        //Allow doubles in scientific E notation
        let val = parse_redis_value(b",2.67923e+8\r\n").unwrap();
        assert_eq!(val, Value::Double(267923000.0));
        let val = parse_redis_value(b",2.67923E+8\r\n").unwrap();
        assert_eq!(val, Value::Double(267923000.0));
        let val = parse_redis_value(b",-2.67923E+8\r\n").unwrap();
        assert_eq!(val, Value::Double(-267923000.0));
        let val = parse_redis_value(b",2.1E-2\r\n").unwrap();
        assert_eq!(val, Value::Double(0.021));

        let val = parse_redis_value(b",-inf\r\n").unwrap();
        assert_eq!(val, Value::Double(-f64::INFINITY));
        let val = parse_redis_value(b",inf\r\n").unwrap();
        assert_eq!(val, Value::Double(f64::INFINITY));
    }

    #[test]
    fn decode_resp3_map() {
        let val = parse_redis_value(b"%2\r\n+first\r\n:1\r\n+second\r\n:2\r\n").unwrap();
        let mut v = val.as_map_iter().unwrap();
        assert_eq!(
            (&Value::SimpleString("first".to_string()), &Value::Int(1)),
            v.next().unwrap()
        );
        assert_eq!(
            (&Value::SimpleString("second".to_string()), &Value::Int(2)),
            v.next().unwrap()
        );
    }

    #[test]
    fn decode_resp3_boolean() {
        let val = parse_redis_value(b"#t\r\n").unwrap();
        assert_eq!(val, Value::Boolean(true));
        let val = parse_redis_value(b"#f\r\n").unwrap();
        assert_eq!(val, Value::Boolean(false));
        let val = parse_redis_value(b"#x\r\n");
        assert!(val.is_err());
        let val = parse_redis_value(b"#\r\n");
        assert!(val.is_err());
    }

    #[test]
    fn decode_resp3_blob_error() {
        let val = parse_redis_value(b"!21\r\nSYNTAX invalid syntax\r\n");
        assert_eq!(
            val.unwrap().extract_error().err(),
            Some(make_extension_error(
                "SYNTAX".to_string(),
                Some("invalid syntax".to_string())
            ))
        )
    }

    #[test]
    fn decode_resp3_big_number() {
        let val = parse_redis_value(b"(3492890328409238509324850943850943825024385\r\n").unwrap();
        assert_eq!(
            val,
            Value::BigNumber(
                BigInt::parse_bytes(b"3492890328409238509324850943850943825024385", 10).unwrap()
            )
        );
    }

    #[test]
    fn decode_resp3_set() {
        let val = parse_redis_value(b"~5\r\n+orange\r\n+apple\r\n#t\r\n:100\r\n:999\r\n").unwrap();
        let v = val.as_sequence().unwrap();
        assert_eq!(Value::SimpleString("orange".to_string()), v[0]);
        assert_eq!(Value::SimpleString("apple".to_string()), v[1]);
        assert_eq!(Value::Boolean(true), v[2]);
        assert_eq!(Value::Int(100), v[3]);
        assert_eq!(Value::Int(999), v[4]);
    }

    #[test]
    fn decode_resp3_push() {
        let val = parse_redis_value(b">3\r\n+message\r\n+some_channel\r\n+this is the message\r\n")
            .unwrap();
        if let Value::Push { ref kind, ref data } = val {
            assert_eq!(&PushKind::Message, kind);
            assert_eq!(Value::SimpleString("some_channel".to_string()), data[0]);
            assert_eq!(
                Value::SimpleString("this is the message".to_string()),
                data[1]
            );
        } else {
            panic!("Expected Value::Push")
        }
    }

    #[test]
    fn test_max_recursion_depth() {
        let bytes = b"*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n*1\r\n";
        match parse_redis_value(bytes) {
            Ok(_) => panic!("Expected Err"),
            Err(e) => assert!(matches!(e.kind(), ErrorKind::ParseError)),
        }
    }

    /// Builds a stream of `depth` aggregate headers of type `agg`, each
    /// declaring a count of 1, followed by a single terminal integer. The
    /// recursion guard triggers on reading each aggregate's type byte based on
    /// the current depth alone (before the aggregate's contents are parsed), so
    /// this header stream is sufficient to exercise the guard for every
    /// aggregate type regardless of its element framing.
    fn nested_aggregate_headers(agg: u8, depth: usize) -> Vec<u8> {
        let mut out = Vec::new();
        for _ in 0..depth {
            out.push(agg);
            out.extend_from_slice(b"1\r\n");
        }
        out.extend_from_slice(b":0\r\n");
        out
    }

    #[test]
    fn test_max_recursion_depth_all_aggregate_types() {
        // Every recursive RESP3 aggregate type must reject nesting beyond
        // MAX_RECURSE_DEPTH with the same graceful parse error the array guard
        // produces, rather than crashing the host via stack exhaustion.
        for agg in [b'*', b'%', b'|', b'~', b'>'] {
            let bytes = nested_aggregate_headers(agg, MAX_RECURSE_DEPTH + 5);
            match parse_redis_value(&bytes) {
                Ok(_) => panic!("Expected parse error for aggregate {:?}", agg as char),
                Err(e) => {
                    assert_eq!(
                        e.kind(),
                        ErrorKind::ParseError,
                        "aggregate {:?} produced unexpected error kind",
                        agg as char
                    );
                    assert!(
                        format!("{e:?}").contains("Maximum recursion depth exceeded"),
                        "aggregate {:?} did not hit the recursion guard: {e:?}",
                        agg as char
                    );
                }
            }
        }
    }

    #[test]
    fn test_nesting_within_recursion_depth_parses() {
        // Legally nested structures within the depth limit must still parse.
        // Arrays and sets nest cleanly with one element per level.
        for agg in [b'*', b'~'] {
            let bytes = nested_aggregate_headers(agg, MAX_RECURSE_DEPTH);
            assert!(
                parse_redis_value(&bytes).is_ok(),
                "aggregate {:?} within depth limit failed to parse",
                agg as char
            );
        }
    }

    /// The zero-copy codec's iterative scanner must enforce the same
    /// recursion-depth guard as the recursive sync parser.
    #[cfg(feature = "aio")]
    #[test]
    fn test_codec_max_recursion_depth_all_aggregate_types() {
        use tokio_util::codec::Decoder;
        for agg in [b'*', b'%', b'|', b'~', b'>'] {
            let mut codec = ValueCodec::default();
            let mut bytes =
                bytes::BytesMut::from(&nested_aggregate_headers(agg, MAX_RECURSE_DEPTH + 5)[..]);
            let result = codec.decode(&mut bytes);
            let err = result.expect_err("expected recursion depth error");
            assert!(
                format!("{err:?}").contains("Maximum recursion depth exceeded"),
                "aggregate {:?} did not hit the recursion guard: {err:?}",
                agg as char
            );
        }
    }

    #[cfg(feature = "aio")]
    #[test]
    fn test_codec_nesting_within_recursion_depth_parses() {
        use tokio_util::codec::Decoder;
        for agg in [b'*', b'~'] {
            let mut codec = ValueCodec::default();
            let mut bytes =
                bytes::BytesMut::from(&nested_aggregate_headers(agg, MAX_RECURSE_DEPTH)[..]);
            let result = codec.decode(&mut bytes);
            assert!(
                matches!(result, Ok(Some(Ok(_)))),
                "aggregate {:?} within depth limit failed via codec: {result:?}",
                agg as char
            );
        }
    }

    /// Negative aggregate counts: `>-1` must decode like the old recursive
    /// parser (an empty Push), while `*-1`/`~-1` remain RESP2 nil.
    #[cfg(feature = "aio")]
    #[test]
    fn test_codec_negative_aggregate_counts() {
        use tokio_util::codec::Decoder;
        let cases: &[(&[u8], Value)] = &[
            (
                b">-1\r\n",
                Value::Push {
                    kind: PushKind::Other("".to_string()),
                    data: vec![],
                },
            ),
            (b"*-1\r\n", Value::Nil),
            (b"~-1\r\n", Value::Nil),
            (b"$-1\r\n", Value::Nil),
        ];
        for (input, expected) in cases {
            let mut codec = ValueCodec::default();
            let mut bytes = bytes::BytesMut::from(*input);
            let decoded = codec
                .decode(&mut bytes)
                .expect("decode failed")
                .expect("expected a value")
                .expect("expected Ok value");
            assert_eq!(
                &decoded,
                expected,
                "input {:?}",
                String::from_utf8_lossy(input)
            );
            assert!(bytes.is_empty(), "codec must consume the whole frame");
        }
    }
}
