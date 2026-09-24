// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The RESP side of the RDMA commands.
//!
//! `glide-rdma` describes a command as a name and arguments and knows nothing
//! about RESP, so that it can be depended on by the connection layer, which is
//! what builds RESP. This is the adapter between the two: it turns those
//! descriptions into redis-rs commands and reads the replies back.

use redis::{Cmd, RedisError, Value, cmd};

use glide_rdma::{Handshake, RdmaCommand, RdmaError, ReadReceipt, TransferReply, decode_hex};

/// Build a redis-rs [`Cmd`] from a [`RdmaCommand`].
pub(crate) fn redis_command(command: &RdmaCommand) -> Cmd {
    let mut built = cmd(command.name());
    for argument in command.arguments() {
        built.arg(argument.as_slice());
    }
    built
}

/// `LO.HELLO <client-address>`
pub fn hello_command(client_address: &[u8]) -> Cmd {
    redis_command(&glide_rdma::hello(client_address))
}

/// Parse a read reply: nil for a missing key, a byte count, or a
/// `[bytes, checksum]` pair.
pub fn parse_receipt(reply: Value) -> Result<Option<ReadReceipt>, RedisError> {
    let (bytes_written, checksum) = match reply {
        Value::Nil => return Ok(None),
        Value::Int(bytes) => (bytes, None),
        Value::Array(items) => match items.as_slice() {
            [Value::Int(bytes), Value::Int(checksum)] => (*bytes, Some(*checksum)),
            other => {
                return Err(protocol_error(format!(
                    "expected [bytes, checksum], got {} elements",
                    other.len()
                )));
            }
        },
        other => return Err(protocol_error(format!("unexpected reply {other:?}"))),
    };
    ReadReceipt::from_wire(bytes_written, checksum)
        .map(Some)
        .map_err(as_redis_error)
}

/// Check a write reply. `LO.SET` answers `OK` on success.
pub fn parse_write_ack(reply: Value) -> Result<(), RedisError> {
    match reply {
        Value::Okay => Ok(()),
        Value::SimpleString(status) if status.eq_ignore_ascii_case("ok") => Ok(()),
        other => Err(protocol_error(format!(
            "expected OK from lo.set, got {other:?}"
        ))),
    }
}

/// Parse a `LO.GET` reply into what ends the loan.
pub(crate) fn parse_read_reply(reply: Value) -> Result<TransferReply, RedisError> {
    Ok(match parse_receipt(reply)? {
        Some(receipt) => TransferReply::Read(receipt),
        None => TransferReply::Missing,
    })
}

/// Parse a `LO.SET` reply into what ends the loan.
pub(crate) fn parse_write_reply(reply: Value) -> Result<TransferReply, RedisError> {
    parse_write_ack(reply).map(|()| TransferReply::Stored)
}

/// Parse `LO.HELLO` into every fabric address the server may initiate from.
pub fn parse_hello(reply: Value) -> Result<Handshake, RedisError> {
    let encoded: Vec<String> = redis::from_redis_value(&reply)
        .map_err(|error| protocol_error(format!("lo.hello: {error}")))?;
    if encoded.is_empty() {
        return Err(protocol_error("lo.hello returned no addresses"));
    }
    let peers = encoded
        .iter()
        .map(|address| decode_hex(address.as_bytes()))
        .collect::<Result<Vec<_>, _>>()
        .map_err(|error| protocol_error(error.to_string()))?;
    Ok(Handshake { peers })
}

/// Translate a RDMA failure into the error type the rest of GLIDE carries.
pub fn as_redis_error(error: RdmaError) -> RedisError {
    use redis::ErrorKind;
    let kind = match &error {
        RdmaError::Revoked => return cancelled(error.to_string()),
        RdmaError::Protocol(_) => ErrorKind::TypeError,
        RdmaError::ChecksumMismatch { .. } => ErrorKind::ClientError,
        RdmaError::PayloadTooLarge { .. } => ErrorKind::UserOperationError,
        RdmaError::Fabric { .. } => ErrorKind::IoError,
        RdmaError::Configuration(_) | RdmaError::LibfabricUnavailable { .. } => {
            ErrorKind::InvalidClientConfig
        }
    };
    RedisError::from((kind, "RDMA error", error.to_string()))
}

/// A reply this client could not read.
fn protocol_error(message: impl Into<String>) -> RedisError {
    as_redis_error(RdmaError::Protocol(message.into()))
}

/// A configuration failure.
pub fn configuration_error(message: impl Into<String>) -> RedisError {
    as_redis_error(RdmaError::Configuration(message.into()))
}

/// How every cancellation's message begins. The Python client recognises a
/// cancellation by it, so it must not change without changing that too.
pub const CANCELLED: &str = "RDMA transfer cancelled";

/// A transfer given up because its region was revoked, usually by closing the
/// client. Never retried: the region it needs is gone.
pub fn cancelled(reason: impl Into<String>) -> RedisError {
    RedisError::from((redis::ErrorKind::ClientError, CANCELLED, reason.into()))
}

#[cfg(test)]
mod tests {
    use super::{
        CANCELLED, as_redis_error, cancelled, hello_command, parse_hello, parse_read_reply,
        parse_receipt, parse_write_ack, parse_write_reply,
    };
    use glide_rdma::{RdmaError, ReadReceipt, TransferReply};
    use redis::{ErrorKind, Value};

    fn args(command: &redis::Cmd) -> Vec<String> {
        command
            .args_iter()
            .map(|arg| match arg {
                redis::Arg::Simple(bytes) => String::from_utf8_lossy(bytes).into_owned(),
                _ => "<non-simple>".to_string(),
            })
            .collect()
    }

    /// glide-rdma's `command` module checks the argument order of every command.
    /// This checks that turning one into a `Cmd` keeps the name and every argument
    /// unchanged, which is the same for all three.
    #[test]
    fn a_command_maps_onto_a_cmd_in_wire_order() {
        let command = hello_command(&[0xde, 0xad]);
        assert_eq!(args(&command), ["LO.HELLO", "dead"]);
    }

    #[test]
    fn read_replies_become_transfer_replies() {
        assert_eq!(
            parse_read_reply(Value::Nil).unwrap(),
            TransferReply::Missing
        );
        assert_eq!(
            parse_read_reply(Value::Int(8)).unwrap(),
            TransferReply::Read(ReadReceipt {
                bytes_written: 8,
                checksum: None
            })
        );
        assert!(parse_read_reply(Value::Okay).is_err());
    }

    #[test]
    fn write_replies_become_transfer_replies() {
        assert_eq!(
            parse_write_reply(Value::Okay).unwrap(),
            TransferReply::Stored
        );
        assert!(parse_write_reply(Value::Nil).is_err());
    }

    /// Clients in other languages tell a cancellation apart by its message, and a
    /// revoked buffer is one.
    #[test]
    fn a_revoked_buffer_is_a_cancellation() {
        let error = as_redis_error(RdmaError::Revoked);
        assert!(error.to_string().starts_with(CANCELLED), "{error}");
    }

    #[test]
    fn a_write_is_acknowledged_by_ok() {
        assert!(parse_write_ack(Value::Okay).is_ok());
        assert!(parse_write_ack(Value::SimpleString("ok".into())).is_ok());
    }

    #[test]
    fn a_write_reply_that_is_not_ok_is_a_protocol_error() {
        assert!(parse_write_ack(Value::Int(64)).is_err());
        assert!(parse_write_ack(Value::Nil).is_err());
    }

    #[test]
    fn parses_a_missing_key_as_none() {
        assert_eq!(parse_receipt(Value::Nil).unwrap(), None);
    }

    #[test]
    fn parses_a_bare_byte_count() {
        let receipt = parse_receipt(Value::Int(1024)).unwrap().unwrap();
        assert_eq!(receipt.bytes_written, 1024);
        assert_eq!(receipt.checksum, None);
    }

    #[test]
    fn parses_a_byte_count_and_checksum() {
        let reply = Value::Array(vec![Value::Int(1024), Value::Int(0xE306_9283)]);
        let receipt = parse_receipt(reply).unwrap().unwrap();
        assert_eq!(receipt.bytes_written, 1024);
        assert_eq!(receipt.checksum, Some(0xE306_9283));
    }

    #[test]
    fn rejects_a_malformed_pair() {
        let reply = Value::Array(vec![Value::Int(1), Value::Int(2), Value::Int(3)]);
        assert!(parse_receipt(reply).is_err());
    }

    #[test]
    fn parses_every_hello_address() {
        let reply = Value::Array(vec![
            Value::BulkString(b"dead".to_vec().into()),
            Value::BulkString(b"beef".to_vec().into()),
        ]);
        let handshake = parse_hello(reply).unwrap();
        assert_eq!(handshake.peers, [vec![0xde, 0xad], vec![0xbe, 0xef]]);
    }

    #[test]
    fn rejects_an_empty_hello() {
        assert!(parse_hello(Value::Array(vec![])).is_err());
    }

    #[test]
    fn rdma_errors_carry_their_kind_and_message() {
        let too_large = as_redis_error(RdmaError::PayloadTooLarge {
            value_bytes: 1_048_576,
            capacity: 65_536,
        });
        assert_eq!(too_large.kind(), ErrorKind::UserOperationError);
        assert!(too_large.to_string().contains("1048576"));

        let fabric = as_redis_error(RdmaError::Fabric {
            operation: "fi_mr_reg",
            message: "Cannot allocate memory".into(),
            errno: Some(-12),
        });
        assert_eq!(fabric.kind(), ErrorKind::IoError);
    }

    /// Clients in other languages tell a cancellation from other failures by how
    /// its message begins.
    #[test]
    fn a_cancellation_message_begins_with_the_marker() {
        let error = cancelled("the client was closed");

        assert!(error.to_string().starts_with(CANCELLED), "{error}");
        assert_eq!(error.kind(), ErrorKind::ClientError);
    }
}
