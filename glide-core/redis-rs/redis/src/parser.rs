use std::{
    io::{self, Read},
    str,
};

use crate::types::{
    ErrorKind, PushKind, RedisError, RedisResult, ServerError, ServerErrorKind, Value,
    VerbatimFormat,
};

use glide_logger::log_error;
use glide_telemetry::GlideOpenTelemetry;

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
/// we take it out of the read buffer as a refcounted [`bytes::Bytes`] (small
/// frames other than RESP3 pushes are split off zero-copy unless the read
/// buffer has grown large, other frames are copied into a pooled buffer; see
/// `ValueCodec::decode_stream`) and build the [`Value`] tree by
/// slicing bulk-string payloads directly out of that frame — no per-value
/// allocation or memcpy.
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

    /// Complete frames up to this size are split off the read buffer instead
    /// of copied (see `ValueCodec::decode_stream`). Same bound as the pool's
    /// small-copy cut-off: above it the pooled copy is kept.
    pub(crate) const ZERO_COPY_MAX_FRAME: usize = crate::buf_pool::POOL_MIN;

    /// Small frames are only split off a read buffer whose visible capacity
    /// (`BytesMut::capacity`) is at most this large. A split frame shares the
    /// read buffer's allocation, so one retained small value keeps the whole
    /// allocation alive.
    ///
    /// The visible capacity excludes the consumed prefix of the allocation,
    /// so the allocation itself can be larger than this. The codec checks the
    /// visible capacity on every decode call, and the first call after
    /// `BytesMut` grows a buffer sees at least 3/4 of the new allocation (it
    /// only doubles the whole allocation while less than half of it is
    /// consumed). So a split value pins at most about 4/3 of this, 85 KiB;
    /// see `read_buffer_bound_is_on_visible_capacity`.
    ///
    /// tokio's `FramedRead` starts with an 8 KiB buffer. When it needs room
    /// while split-off values are still alive, `BytesMut` allocates a fresh
    /// buffer of `max(unconsumed + 1, original capacity)` (8 KiB here; the
    /// codec makes it `max(2 * unconsumed, 8 KiB)` for a partial frame that
    /// fills the buffer), so small-reply traffic stays at 8 KiB. A reply too big for
    /// the current buffer makes `BytesMut` grow it by doubling: 4-32 KiB
    /// replies (copied, not split) can grow it to 16-64 KiB, which later
    /// split values then pin. Once the codec sees a visible capacity above
    /// this bound, splitting stops for the rest of that connection, because
    /// `BytesMut` never shrinks; such connections decode exactly as before.
    /// A buffer that grows to 64-85 KiB without its visible capacity ever
    /// exceeding the bound does not stop splitting.
    pub(crate) const ZERO_COPY_MAX_READ_BUF: usize = 64 * 1024;

    /// Tokio codec that decodes RESP frames zero-copy from the read
    /// buffer. See the `zero_copy` module for the strategy.
    #[derive(Default)]
    pub struct ValueCodec {
        /// Resumable scan progress for the (single) incomplete frame at the
        /// front of the read buffer. Valid across calls because nothing is
        /// consumed until the frame completes.
        scan: super::zero_copy::ScanState,
        /// End address of a read-buffer allocation seen larger than
        /// [`ZERO_COPY_MAX_READ_BUF`]. Small frames are copied, not split,
        /// while the buffer still ends here.
        large_read_buf_end: Option<usize>,
    }

    impl ValueCodec {
        /// Whether `bytes` lives in an allocation known to be larger than
        /// [`ZERO_COPY_MAX_READ_BUF`].
        ///
        /// `capacity()` only covers the allocation from the unconsumed data
        /// to its end; the consumed prefix is not observable. Near the tail
        /// of a large buffer the visible capacity is small, so the end
        /// address of any allocation once seen above the bound is remembered.
        /// The way `FramedRead` uses its buffer (`advance`, `split_to`,
        /// `reserve`), the end address only changes when `BytesMut`
        /// reallocates, and then the check runs again on the new allocation.
        /// If the allocator reuses the address for a new allocation, small
        /// frames keep being copied until the next reallocation.
        fn in_large_read_buf(&mut self, bytes: &BytesMut) -> bool {
            let end = (bytes.as_ptr() as usize).wrapping_add(bytes.capacity());
            if bytes.capacity() > ZERO_COPY_MAX_READ_BUF {
                self.large_read_buf_end = Some(end);
                return true;
            }
            if self.large_read_buf_end == Some(end) {
                return true;
            }
            self.large_read_buf_end = None;
            false
        }

        fn decode_stream(
            &mut self,
            bytes: &mut BytesMut,
            eof: bool,
        ) -> RedisResult<Option<RedisResult<Value>>> {
            // Checked on every call (including partial-frame ones) so a
            // large allocation is noticed while its visible capacity is big.
            let large_read_buf = self.in_large_read_buf(bytes);
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
                        // A partial frame fills the buffer, so `FramedRead`'s
                        // `reserve(1)` before its next read would allocate.
                        // While split-off values still share the buffer,
                        // `BytesMut` sizes that new buffer
                        // `max(len + 1, original capacity)`: for `len` near or
                        // above the original 8 KiB, a read of a few bytes (one
                        // byte above it), then another reallocation.
                        // Reserving `len` here makes the new buffer
                        // `max(2 * len, original capacity)`. When the buffer is
                        // not shared, `reserve(len)` on a full buffer reuses
                        // or grows the allocation exactly as `reserve(1)`
                        // would (both shift the data to the front when the
                        // consumed prefix is at least `len`, else both double
                        // the allocation).
                        if bytes.len() == bytes.capacity() {
                            bytes.reserve(bytes.len());
                        }
                        Ok(None)
                    }
                }
                Ok(Some(end)) => {
                    self.scan.reset();
                    // Frames above `ZERO_COPY_MAX_FRAME` are copied out of the
                    // read buffer into a recycled pooled buffer (see
                    // `buf_pool`); bulk-string payloads are then sliced out of
                    // the frame zero-copy.
                    //
                    // Copying them was chosen from profiling against a real
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
                    //
                    // Small frames are split off the read buffer itself
                    // instead (`split_to().freeze()`). Copying costs a small
                    // reply two allocations made on the connection task and
                    // usually freed by the caller task on another worker
                    // thread (the frame copy, then the `Bytes` promotion when a
                    // payload is sliced out of it). A split makes no
                    // per-reply allocation. Its costs are shared instead: one
                    // fresh 8 KiB read buffer plus a `Shared` header whenever
                    // `FramedRead` needs room while split values are still
                    // alive (about once per buffer-full of small frames), and
                    // one atomic refcount per buffer that the connection task
                    // and the caller threads all update. The realloc churn
                    // described above was measured for large frames; at this
                    // size the buffer is replaced at most once per 8 KiB.
                    //
                    // Memory retention: a split frame pins the whole read
                    // buffer allocation, so splitting is skipped while that
                    // allocation is known to be larger than
                    // `ZERO_COPY_MAX_READ_BUF` (a large reply grew it). Values
                    // kept long-term should be detached
                    // (`Value::detach_buffers`, as the client-side cache does).
                    //
                    // RESP3 push frames (`>`) are always copied: pushes wait
                    // in the push channel until the application consumes
                    // them, and each split one would pin a read buffer for
                    // that whole time.
                    let frame = if end <= ZERO_COPY_MAX_FRAME && !large_read_buf && bytes[0] != b'>'
                    {
                        bytes.split_to(end).freeze()
                    } else {
                        let frame = crate::buf_pool::pooled_bytes_from_slice(&bytes[..end]);
                        bytes.advance(end);
                        frame
                    };
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
    use super::test_io::bulk_frame;

    /// A bulk-string frame of `fill` bytes whose total encoded length is
    /// exactly `total`. Returns the frame and its payload length.
    #[cfg(feature = "aio")]
    fn bulk_frame_of_len(total: usize, fill: u8) -> (Vec<u8>, usize) {
        // "$" + digits + "\r\n" + payload + "\r\n"
        let payload_len = (0..total)
            .rev()
            .find(|p| p + p.to_string().len() + 5 == total)
            .expect("some payload length encodes to `total` bytes");
        let f = bulk_frame(&vec![fill; payload_len]);
        assert_eq!(f.len(), total);
        (f, payload_len)
    }

    /// Decodes one complete bulk string and returns its payload.
    #[cfg(feature = "aio")]
    fn decode_bulk(codec: &mut ValueCodec, buf: &mut bytes::BytesMut) -> bytes::Bytes {
        use tokio_util::codec::Decoder;
        match codec.decode(buf) {
            Ok(Some(Ok(Value::BulkString(b)))) => b,
            other => panic!("expected a bulk string, got {other:?}"),
        }
    }

    /// Address range of `buf`'s allocation from its current position to its
    /// end: a payload pointing in here was split off, not copied.
    #[cfg(feature = "aio")]
    fn alloc_range(buf: &bytes::BytesMut) -> std::ops::Range<usize> {
        let start = buf.as_ptr() as usize;
        start..start + buf.capacity()
    }

    /// Small frames are split off the read buffer (no copy). Values sliced from
    /// one frame must stay intact while later frames are decoded from the same
    /// buffer and the buffer is refilled; a frame above the zero-copy bound takes
    /// the pooled-copy path and must decode identically.
    #[cfg(feature = "aio")]
    #[test]
    fn small_frames_split_off_read_buffer_stay_valid() {
        use tokio_util::codec::Decoder;
        // The large frame goes through buf_pool's shared shards.
        let _g = crate::buf_pool::TEST_POOL_SERIAL
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        let mut codec = ValueCodec::default();
        let small = vec![b'a'; 512];
        let large = vec![b'b'; aio_support::ZERO_COPY_MAX_FRAME + 1];
        let mut buf = bytes::BytesMut::with_capacity(8192);
        buf.extend_from_slice(&bulk_frame(&small));
        buf.extend_from_slice(&bulk_frame(&small[..7]));
        // Partial third frame: decoded only after more bytes arrive.
        let third = bulk_frame(&large);
        buf.extend_from_slice(&third[..100]);
        let first_alloc = alloc_range(&buf);
        let v1 = decode_bulk(&mut codec, &mut buf);
        let v2 = decode_bulk(&mut codec, &mut buf);
        assert!(
            first_alloc.contains(&(v1.as_ptr() as usize)),
            "v1 not split"
        );
        assert!(
            first_alloc.contains(&(v2.as_ptr() as usize)),
            "v2 not split"
        );
        assert_eq!(codec.decode(&mut buf).unwrap(), None);
        buf.extend_from_slice(&third[100..]);
        let v3 = decode_bulk(&mut codec, &mut buf);
        // Refill with new data after everything was consumed; old values untouched.
        buf.extend_from_slice(&bulk_frame(b"zzzzzzzz"));
        let v4 = decode_bulk(&mut codec, &mut buf);
        // Refill past the remaining capacity while split values are alive:
        // `BytesMut` must move to a new allocation and leave v1/v2 intact.
        let refill: Vec<u8> = (0..64u8).flat_map(|i| bulk_frame(&[i; 200])).collect();
        assert!(refill.len() > buf.capacity());
        buf.extend_from_slice(&refill);
        assert!(!first_alloc.contains(&(buf.as_ptr() as usize)));
        for i in 0..64u8 {
            assert_eq!(&decode_bulk(&mut codec, &mut buf)[..], &[i; 200][..]);
        }
        assert_eq!(&v1[..], &small[..]);
        assert_eq!(&v2[..], &small[..7]);
        assert_eq!(&v3[..], &large[..]);
        assert_eq!(&v4[..], b"zzzzzzzz");
        assert!(buf.is_empty());
    }

    /// Decodes `n` 4-byte bulk strings out of an 8 KiB read buffer, then
    /// drops the buffer and returns the values: each one now shares that
    /// 8 KiB allocation with the others.
    #[cfg(feature = "aio")]
    fn split_small_values(n: u8) -> Vec<Value> {
        use tokio_util::codec::Decoder;
        let mut codec = ValueCodec::default();
        let mut buf = bytes::BytesMut::with_capacity(8192);
        for i in 0..n {
            buf.extend_from_slice(&bulk_frame(&[b'a' + i; 4]));
        }
        let range = alloc_range(&buf);
        let values: Vec<Value> = (0..n)
            .map(|_| codec.decode(&mut buf).unwrap().unwrap().unwrap())
            .collect();
        for v in &values {
            let Value::BulkString(b) = v else {
                panic!("expected a bulk string, got {v:?}")
            };
            assert!(range.contains(&(b.as_ptr() as usize)), "test setup: split");
        }
        values
    }

    /// `Vec::from(Bytes)` reuses the allocation behind a `Bytes` that holds
    /// the only reference. For a split value that allocation is the whole
    /// read buffer, so converting the last live value to a `Vec<u8>` must
    /// not keep its 8 KiB capacity. Pins the `bytes` behaviour that makes
    /// this matter, then checks every conversion this crate offers.
    #[cfg(feature = "aio")]
    #[test]
    fn vec_conversions_of_a_split_value_do_not_keep_the_read_buffer() {
        // Precondition: the raw conversion takes the read buffer over.
        let mut values = split_small_values(2);
        let Some(Value::BulkString(last)) = values.pop() else {
            unreachable!()
        };
        drop(values);
        let raw: Vec<u8> = last.into();
        assert_eq!(raw, b"bbbb");
        assert!(
            raw.capacity() >= 8192,
            "bytes no longer reuses the read buffer (capacity {}); revisit \
             `bytes_into_vec`",
            raw.capacity()
        );

        let compact = |v: &Vec<u8>| v.capacity() <= 2 * v.len() + 64;

        let mut values = split_small_values(2);
        let last = values.pop().unwrap();
        drop(values);
        let v: Vec<u8> = crate::from_owned_redis_value(last).unwrap();
        assert_eq!(v, b"bbbb");
        assert!(compact(&v), "FromRedisValue for Vec<u8>: {}", v.capacity());

        let mut values = split_small_values(2);
        let Some(Value::BulkString(last)) = values.pop() else {
            unreachable!()
        };
        drop(values);
        let v = crate::bytes_into_vec(last);
        assert_eq!(v, b"bbbb");
        assert!(compact(&v), "bytes_into_vec: {}", v.capacity());

        // Not the last reference: a plain copy, exact size.
        let values = split_small_values(2);
        let Value::BulkString(shared) = values[1].clone() else {
            unreachable!()
        };
        let v = crate::bytes_into_vec(shared);
        assert_eq!((v.as_slice(), v.capacity()), (&b"bbbb"[..], 4));

        // Typed results nested in collections go through the same path.
        let values = split_small_values(3);
        let v: Vec<Vec<u8>> = crate::from_owned_redis_value(Value::Array(values)).unwrap();
        assert_eq!(v, [b"aaaa", b"bbbb", b"cccc"]);
        assert!(v.iter().all(compact), "Vec<Vec<u8>>");
    }

    /// `bytes_into_vec` keeps an owned `Vec` behind a `Bytes` as it is when
    /// it is not much larger than its contents (no copy, no realloc).
    #[test]
    fn bytes_into_vec_keeps_a_fitting_allocation() {
        let mut v = Vec::with_capacity(100);
        v.extend_from_slice(&[7u8; 90]);
        let ptr = v.as_ptr();
        let b = bytes::Bytes::from(v);
        let v = crate::bytes_into_vec(b);
        assert_eq!(v.as_ptr(), ptr);
        assert_eq!(v.len(), 90);
    }

    /// The read-buffer bound is inclusive: a small frame is split off a read
    /// buffer of exactly `ZERO_COPY_MAX_READ_BUF` bytes and copied out of
    /// one a byte larger.
    #[cfg(feature = "aio")]
    #[test]
    fn read_buffer_split_boundary_is_zero_copy_max_read_buf() {
        let limit = aio_support::ZERO_COPY_MAX_READ_BUF;
        for (capacity, expect_split) in [(limit, true), (limit + 1, false)] {
            let mut codec = ValueCodec::default();
            let mut buf = bytes::BytesMut::with_capacity(capacity);
            assert_eq!(buf.capacity(), capacity, "test setup: exact capacity");
            buf.extend_from_slice(&bulk_frame(b"small"));
            let range = alloc_range(&buf);
            let v = decode_bulk(&mut codec, &mut buf);
            assert_eq!(&v[..], b"small");
            assert_eq!(
                range.contains(&(v.as_ptr() as usize)),
                expect_split,
                "read buffer of {capacity} bytes"
            );
        }
    }

    /// The read-buffer bound applies to the visible capacity, which excludes
    /// the consumed prefix. When a full buffer grows while less than half of
    /// it is consumed (`BytesMut` doubles the whole allocation and keeps the
    /// prefix), the new allocation can be up to 4/3 of the visible bound
    /// (about 85 KiB) without ever being seen above it. This pins one such
    /// case, through `BytesMut`'s real growth: a split from an 84 KiB
    /// allocation whose visible capacity is exactly the bound.
    #[cfg(feature = "aio")]
    #[test]
    fn read_buffer_bound_is_on_visible_capacity() {
        use bytes::Buf;
        let limit = aio_support::ZERO_COPY_MAX_READ_BUF;
        let (total, consumed) = (42 * 1024, 20 * 1024);
        let mut buf = bytes::BytesMut::with_capacity(total);
        buf.extend_from_slice(&vec![b'.'; consumed]);
        buf.extend_from_slice(&bulk_frame(b"tiny"));
        buf.resize(total, b'.');
        buf.advance(consumed);
        assert_eq!(buf.len(), buf.capacity(), "test setup: buffer is full");
        // What `FramedRead` does before its next read.
        buf.reserve(1);
        let allocation_end = buf.as_ptr() as usize + buf.capacity();
        let allocation = allocation_end - (buf.as_ptr() as usize - consumed);
        assert_eq!(allocation, 2 * total, "test setup: doubled");
        assert_eq!(buf.capacity(), limit, "test setup: visible at the bound");
        let range = alloc_range(&buf);
        let mut codec = ValueCodec::default();
        let v = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&v[..], b"tiny");
        assert!(range.contains(&(v.as_ptr() as usize)));
        assert!(allocation <= limit * 4 / 3);
    }

    /// End to end through tokio's `FramedRead` (its real `reserve` and read
    /// behaviour): small replies are split off the read buffer, a large reply
    /// grows the buffer past `ZERO_COPY_MAX_READ_BUF`, and small replies after
    /// it are copied so they cannot pin the grown buffer.
    #[cfg(feature = "aio")]
    #[test]
    fn framed_read_splits_small_replies_until_the_buffer_grows() {
        // Held outside the runtime: a std MutexGuard must not live across
        // an await point.
        let _g = crate::buf_pool::TEST_POOL_SERIAL
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        tokio::runtime::Builder::new_current_thread()
            .build()
            .unwrap()
            .block_on(framed_read_splits_small_replies_until_the_buffer_grows_body());
    }

    #[cfg(feature = "aio")]
    async fn framed_read_splits_small_replies_until_the_buffer_grows_body() {
        use futures_util::StreamExt;
        let small: Vec<Vec<u8>> = (0..100u8).map(|i| vec![i; 100]).collect();
        let large = vec![b'L'; 4 * aio_support::ZERO_COPY_MAX_READ_BUF];
        let mut wire = Vec::new();
        for s in &small {
            wire.extend_from_slice(&bulk_frame(s));
        }
        wire.extend_from_slice(&bulk_frame(&large));
        for s in &small {
            wire.extend_from_slice(&bulk_frame(s));
        }

        type Framed<'a> = tokio_util::codec::FramedRead<&'a [u8], ValueCodec>;
        async fn next_bulk(framed: &mut Framed<'_>) -> bytes::Bytes {
            match framed.next().await {
                Some(Ok(Ok(Value::BulkString(b)))) => b,
                other => panic!("expected a bulk string, got {other:?}"),
            }
        }
        // A split payload ends right before the read buffer's next unread
        // byte (only its CRLF in between); a copied one does not.
        fn was_split(b: &bytes::Bytes, framed: &Framed<'_>) -> bool {
            b.as_ptr() as usize + b.len() + 2 == framed.read_buffer().as_ptr() as usize
        }

        let mut framed = Framed::new(&wire[..], ValueCodec::default());
        let mut kept = Vec::new();
        for s in &small {
            let b = next_bulk(&mut framed).await;
            assert_eq!(&b[..], &s[..]);
            assert!(was_split(&b, &framed), "small reply before the large one");
            kept.push(b);
        }
        let big = next_bulk(&mut framed).await;
        assert_eq!(big.len(), large.len());
        for s in &small {
            let b = next_bulk(&mut framed).await;
            assert_eq!(&b[..], &s[..]);
            assert!(!was_split(&b, &framed), "small reply after the large one");
        }
        // Values split before the read buffer grew are still intact.
        for (b, s) in kept.iter().zip(&small) {
            assert_eq!(&b[..], &s[..]);
        }
    }

    /// What a `FramedRead` over [`test_io::ChunkedIo`] did while decoding.
    #[cfg(feature = "aio")]
    struct ReadPattern {
        /// Read space offered to each read.
        offered: Vec<usize>,
        /// Times the read buffer moved to a new allocation or was resized
        /// between two reads (its allocation's end address changed).
        buffer_changes: usize,
        /// Replies split off the read buffer rather than copied.
        split: usize,
    }

    /// Decodes all of `wire` through `FramedRead`, in socket reads of at most
    /// `chunk` bytes, holding every decoded value until the end (as callers
    /// do while a pipeline is in flight).
    #[cfg(feature = "aio")]
    fn framed_read_pattern(wire: Vec<u8>, chunk: usize) -> ReadPattern {
        use futures_util::StreamExt;
        let (io, offered) = test_io::ChunkedIo::new(wire.clone(), chunk);
        let mut framed = tokio_util::codec::FramedRead::new(io, ValueCodec::default());
        let (values, split) = tokio::runtime::Builder::new_current_thread()
            .build()
            .unwrap()
            .block_on(async {
                let mut values = Vec::new();
                let mut split = 0;
                while let Some(v) = framed.next().await {
                    let v = v.unwrap().unwrap();
                    // A split payload ends right before the read buffer's
                    // next unread byte (only its CRLF in between).
                    if let Value::BulkString(b) = &v {
                        if b.as_ptr() as usize + b.len() + 2
                            == framed.read_buffer().as_ptr() as usize
                        {
                            split += 1;
                        }
                    }
                    values.push(v);
                }
                (values, split)
            });
        // Every value decoded intact.
        let mut reencoded = Vec::new();
        for v in &values {
            let Value::BulkString(b) = v else {
                panic!("expected a bulk string, got {v:?}")
            };
            reencoded.extend_from_slice(&bulk_frame(b));
        }
        assert_eq!(reencoded, wire);
        let reads = offered.lock().unwrap().clone();
        ReadPattern {
            split,
            offered: reads.iter().map(|&(n, _)| n).collect(),
            buffer_changes: reads.windows(2).filter(|w| w[0].1 != w[1].1).count(),
        }
    }

    /// Mixed small and mid-size replies through a real `FramedRead`, with
    /// the caller holding values. A mid-size reply that fills a read buffer
    /// still shared with split values must not get a 1-byte read: when the
    /// codec returns on a partial frame in a full buffer it reserves the
    /// buffer's length, so the replacement doubles like a `Vec` instead of
    /// being sized `len + 1` by `FramedRead`'s `reserve(1)`.
    ///
    /// Also pins how often the read buffer changes allocation. Splitting
    /// small frames shares the buffer, so a refill while values are held
    /// allocates a new one (8 KiB, or twice a partial frame that filled the
    /// old one), and the next mid-size reply grows it again: about 2 changes
    /// per round of 10 small replies and one 20 KiB reply here, and about
    /// one per 8 KiB of small-only traffic. Copying every frame (the
    /// behaviour before splitting) changed the buffer twice in total for
    /// the mixed workload and never for the small-only one, measured with
    /// the same workloads.
    #[cfg(feature = "aio")]
    #[test]
    fn framed_read_mixed_reply_sizes_read_pattern() {
        let _g = crate::buf_pool::TEST_POOL_SERIAL
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        let rounds = 20;
        let mut wire = Vec::new();
        for r in 0..rounds {
            for i in 0..10u8 {
                wire.extend_from_slice(&bulk_frame(&[i; 100]));
            }
            wire.extend_from_slice(&bulk_frame(&vec![r as u8; 20 * 1024]));
        }
        let p = framed_read_pattern(wire, 16 * 1024);
        let smallest = p.offered.iter().copied().min().unwrap();
        println!(
            "mixed: {} reads, smallest offered {smallest}, {} buffer changes, {} split, over {rounds} rounds",
            p.offered.len(),
            p.buffer_changes,
            p.split,
        );
        let mut wire = Vec::new();
        for i in 0..2000u32 {
            wire.extend_from_slice(&bulk_frame(&[i as u8; 100]));
        }
        let small_wire_len = wire.len();
        let s = framed_read_pattern(wire, 16 * 1024);
        println!(
            "small: {} reads, smallest offered {}, {} buffer changes, {} split",
            s.offered.len(),
            s.offered.iter().min().unwrap(),
            s.buffer_changes,
            s.split,
        );
        assert!(
            smallest >= 1024,
            "a read was offered {smallest} bytes: {:?}",
            p.offered
        );
        assert_eq!(p.split, rounds * 10, "every small reply is split");
        // Deterministic (exact `Vec` growth, all values held); measured 41.
        assert!(
            p.buffer_changes <= 2 * rounds + 1,
            "{} buffer changes",
            p.buffer_changes
        );
        assert!(s.offered.iter().all(|&n| n >= 1024), "{:?}", s.offered);
        assert_eq!(s.split, 2000);
        // One new 8 KiB buffer per 8 KiB of small replies; measured 26.
        assert!(
            s.buffer_changes <= small_wire_len / 8192 + 1,
            "{} buffer changes",
            s.buffer_changes
        );
    }

    /// The split/copy boundary is the whole encoded frame length: a frame of
    /// exactly `ZERO_COPY_MAX_FRAME` bytes is split off the read buffer, one
    /// byte more is copied out of it.
    #[cfg(feature = "aio")]
    #[test]
    fn frame_split_boundary_is_zero_copy_max_frame() {
        let _g = crate::buf_pool::TEST_POOL_SERIAL
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        let max = aio_support::ZERO_COPY_MAX_FRAME;
        let (at_max, at_max_len) = bulk_frame_of_len(max, b'x');
        let (over_max, over_max_len) = bulk_frame_of_len(max + 1, b'y');

        let mut codec = ValueCodec::default();
        let mut buf = bytes::BytesMut::with_capacity(16 * 1024);
        buf.extend_from_slice(&at_max);
        buf.extend_from_slice(&over_max);
        let range = alloc_range(&buf);

        let split = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&split[..], &vec![b'x'; at_max_len][..]);
        assert!(
            range.contains(&(split.as_ptr() as usize)),
            "a {max}-byte frame should be split off the read buffer"
        );

        let copied = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&copied[..], &vec![b'y'; over_max_len][..]);
        assert!(
            !range.contains(&(copied.as_ptr() as usize)),
            "a {}-byte frame should be copied out of the read buffer",
            max + 1
        );
        assert!(buf.is_empty());
    }

    /// RESP3 push frames are copied, not split, even when small: pushes
    /// queue in the push channel until the application consumes them, and a
    /// split one would pin its read buffer for that whole time. A reply
    /// after a push is still split.
    #[cfg(feature = "aio")]
    #[test]
    fn small_push_frames_are_copied_out_of_the_read_buffer() {
        use tokio_util::codec::Decoder;
        let mut codec = ValueCodec::default();
        let mut buf = bytes::BytesMut::with_capacity(8192);
        buf.extend_from_slice(b">3\r\n$7\r\nmessage\r\n$2\r\nch\r\n$4\r\nbody\r\n");
        buf.extend_from_slice(&bulk_frame(b"reply"));
        let range = alloc_range(&buf);
        let Some(Ok(Value::Push { kind, data })) = codec.decode(&mut buf).unwrap() else {
            panic!("expected a push")
        };
        assert_eq!(kind, PushKind::Message);
        assert_eq!(data.len(), 2, "channel and payload");
        for v in &data {
            let Value::BulkString(b) = v else {
                panic!("expected a bulk string, got {v:?}")
            };
            assert!(
                !range.contains(&(b.as_ptr() as usize)),
                "push payload split off the read buffer"
            );
        }
        let reply = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&reply[..], b"reply");
        assert!(
            range.contains(&(reply.as_ptr() as usize)),
            "reply not split"
        );
    }

    /// Memory retention: a small frame split off a large read buffer would
    /// keep the whole allocation alive, so small frames are copied while the
    /// read buffer's allocation exceeds `ZERO_COPY_MAX_READ_BUF`, including
    /// at its tail where the visible capacity is small.
    ///
    /// The last part checks that the remembered end address only matches
    /// its own allocation: the codec splits again from a different, small
    /// allocation. Under `FramedRead` that does not happen once a buffer is
    /// flagged, because nothing is split from it afterwards, so it stays
    /// unshared and `BytesMut` only grows it.
    #[cfg(feature = "aio")]
    #[test]
    fn small_frames_are_copied_out_of_a_large_read_buffer() {
        let _g = crate::buf_pool::TEST_POOL_SERIAL
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        let limit = aio_support::ZERO_COPY_MAX_READ_BUF;
        let mut codec = ValueCodec::default();

        // A small frame at the front of a large allocation.
        let mut buf = bytes::BytesMut::with_capacity(2 * limit);
        let small = bulk_frame(b"front");
        buf.extend_from_slice(&small);
        let range = alloc_range(&buf);
        let front = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&front[..], b"front");
        assert!(!range.contains(&(front.as_ptr() as usize)));

        // A large frame fills the allocation; the small frame after it sits in
        // the tail, where the visible capacity is below the limit.
        let tail = bulk_frame(b"tail");
        let (large, large_payload_len) = bulk_frame_of_len(buf.capacity() - tail.len(), b'L');
        buf.extend_from_slice(&large);
        buf.extend_from_slice(&tail);
        let range = alloc_range(&buf);
        assert_eq!(buf.len(), buf.capacity(), "test setup: allocation is full");
        let big = decode_bulk(&mut codec, &mut buf);
        assert_eq!(big.len(), large_payload_len);
        assert!(buf.capacity() <= limit, "test setup: tail must look small");
        let tail_value = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&tail_value[..], b"tail");
        assert!(
            !range.contains(&(tail_value.as_ptr() as usize)),
            "a small frame at the tail of a large read buffer must be copied"
        );
        assert!(buf.is_empty());

        // A fresh small allocation (what FramedRead reallocates to): split.
        let mut buf = bytes::BytesMut::with_capacity(8 * 1024);
        buf.extend_from_slice(&bulk_frame(b"fresh"));
        let range = alloc_range(&buf);
        let fresh = decode_bulk(&mut codec, &mut buf);
        assert_eq!(&fresh[..], b"fresh");
        assert!(range.contains(&(fresh.as_ptr() as usize)));
    }

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
    fn only_errors_parsed_from_a_reply_are_server_replies() {
        for reply in [
            b"-ERR boom\r\n".as_slice(),
            b"-READONLY replica\r\n",
            // A code this client does not know, so it parses as an extension error.
            b"-OOM command not allowed when used memory > 'maxmemory'.\r\n",
        ] {
            let error = parse_redis_value(reply)
                .unwrap()
                .extract_error()
                .expect_err("an error reply");
            assert!(error.is_server_reply(), "{error}");
        }

        // Raised locally with the same kind a server error has.
        let local = RedisError::from((
            ErrorKind::ResponseError,
            "Internal failure: receiver was dropped before delivering a response",
        ));
        assert!(!local.is_server_reply());
        let io = RedisError::from(std::io::Error::from(std::io::ErrorKind::BrokenPipe));
        assert!(!io.is_server_reply());
    }

    #[test]
    fn a_command_never_sent_counts_as_a_server_reply() {
        let error = RedisError::command_not_sent(ErrorKind::FatalSendError, "reason".to_string());

        assert!(error.is_server_reply(), "{error}");
        assert_eq!(error.kind(), ErrorKind::FatalSendError);
        assert_eq!(error.detail(), Some("reason"));
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
        for agg in *b"*%|~>" {
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
        for agg in *b"*~" {
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
        for agg in *b"*%|~>" {
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
        for agg in *b"*~" {
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

/// Test transport for the codec's read-buffer tests.
#[cfg(all(test, feature = "aio"))]
pub(crate) mod test_io {
    use std::pin::Pin;
    use std::sync::{Arc, Mutex};
    use std::task::{Context, Poll};
    use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};

    /// Per read: the read space offered (the read buffer's spare capacity)
    /// and the end address of the read buffer's allocation.
    pub(crate) type Reads = Arc<Mutex<Vec<(usize, usize)>>>;

    /// Serves `data` in reads of at most `chunk` bytes, like a socket with
    /// that much data ready per read, and records each read in [`Reads`].
    /// Writes are discarded.
    pub(crate) struct ChunkedIo {
        data: Vec<u8>,
        pos: usize,
        chunk: usize,
        offered: Reads,
    }

    impl ChunkedIo {
        /// Returns the transport and a handle to its recorded reads.
        pub(crate) fn new(data: Vec<u8>, chunk: usize) -> (Self, Reads) {
            let offered = Arc::new(Mutex::new(Vec::new()));
            let io = ChunkedIo {
                data,
                pos: 0,
                chunk,
                offered: offered.clone(),
            };
            (io, offered)
        }
    }

    impl AsyncRead for ChunkedIo {
        fn poll_read(
            mut self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
            buf: &mut ReadBuf<'_>,
        ) -> Poll<std::io::Result<()>> {
            let n = self
                .chunk
                .min(buf.remaining())
                .min(self.data.len() - self.pos);
            if self.pos < self.data.len() {
                // End address of the read buffer's allocation: `buf` covers
                // its spare capacity, which runs to the allocation's end.
                let end = buf.filled().as_ptr() as usize + buf.capacity();
                self.offered.lock().unwrap().push((buf.remaining(), end));
            }
            let pos = self.pos;
            buf.put_slice(&self.data[pos..pos + n]);
            self.pos += n;
            Poll::Ready(Ok(()))
        }
    }

    impl AsyncWrite for ChunkedIo {
        fn poll_write(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
            buf: &[u8],
        ) -> Poll<std::io::Result<usize>> {
            Poll::Ready(Ok(buf.len()))
        }
        fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
            Poll::Ready(Ok(()))
        }
        fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
            Poll::Ready(Ok(()))
        }
    }

    /// A RESP bulk-string frame carrying `payload`.
    pub(crate) fn bulk_frame(payload: &[u8]) -> Vec<u8> {
        let mut f = format!("${}\r\n", payload.len()).into_bytes();
        f.extend_from_slice(payload);
        f.extend_from_slice(b"\r\n");
        f
    }
}
