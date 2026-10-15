// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The `BLOB.*` command vocabulary, independent of any RESP client.
//!
//! The client's fabric address is sent once by `BLOB.RDMA_HELLO` and the server
//! remembers it for the connection. `BLOB.RDMA_GET` and `BLOB.RDMA_SET` commands carry
//! a key and the specific window of the client's registered memory where
//! the RDMA transfer should be performed.

use crate::error::RdmaError;
#[cfg(any(feature = "libfabric", test))]
use crate::region_ref::RegionRef;

/// What a `BLOB.RDMA_GET` yielded.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ReadReceipt {
    /// Byte length transferred.
    pub bytes_written: usize,
    /// CRC-32c of the stored object, as the server recorded it.
    pub checksum: u32,
}

impl ReadReceipt {
    /// Convert the two integers of a `BLOB.RDMA_GET` reply, `[length, checksum]`,
    /// into a ReadReceipt.
    pub fn from_wire(bytes_written: i64, checksum: i64) -> Result<Self, RdmaError> {
        let checksum = u32::try_from(checksum)
            .map_err(|_| RdmaError::Protocol(format!("checksum out of range {checksum}")))?;
        let bytes_written = usize::try_from(bytes_written)
            .map_err(|_| RdmaError::Protocol(format!("negative byte count {bytes_written}")))?;
        Ok(Self {
            bytes_written,
            checksum,
        })
    }

    /// Check that the bytes that landed match the checksum the server sent.
    ///
    /// `landed` is the window the read was lent, starting where the object begins.
    /// Only its first `bytes_written` bytes are checked. This reads every one of
    /// them, so it costs a pass over the value; call it when that is worth paying.
    ///
    /// # Errors
    ///
    /// [`RdmaError::ChecksumMismatch`] when the bytes differ from what the server
    /// stored. [`RdmaError::PayloadTooLarge`] when `landed` is shorter than
    /// `bytes_written`, so the object cannot all be in it.
    pub fn verify(&self, landed: &[u8]) -> Result<(), RdmaError> {
        let Some(object) = landed.get(..self.bytes_written) else {
            return Err(RdmaError::PayloadTooLarge {
                value_bytes: self.bytes_written,
                capacity: landed.len(),
            });
        };
        let actual = crate::checksum(object);
        if actual != self.checksum {
            return Err(RdmaError::ChecksumMismatch {
                expected: self.checksum,
                actual,
            });
        }
        Ok(())
    }
}

/// The server's reply to a `BLOB.RDMA_GET` or `BLOB.RDMA_SET`.
///
/// The protocol requires the server to reply only once its transfer has completed
/// with delivery guaranteed (`FI_DELIVERY_COMPLETE`), so a reply means it is done
/// with the client's memory. This crate trusts that; it has no signal of its own.
/// Passing a reply to `LentBuffer::reclaim` is how a caller gets its buffer back.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransferReply {
    /// `BLOB.RDMA_GET` wrote the stored object into the window.
    Read(ReadReceipt),
    /// `BLOB.RDMA_GET` found no such key and wrote nothing.
    Missing,
    /// `BLOB.RDMA_SET` read the window and stored it.
    Stored,
    /// The server replied with an error.
    Failed,
}

/// A `BLOB.*` command: its name, and its arguments in wire order.
#[derive(Debug, PartialEq, Eq)]
pub struct RdmaCommand {
    name: &'static str,
    arguments: Vec<Vec<u8>>,
}

impl RdmaCommand {
    /// The command name, as sent.
    pub fn name(&self) -> &'static str {
        self.name
    }

    /// The arguments, in the order they go on the wire.
    pub fn arguments(&self) -> &[Vec<u8>] {
        &self.arguments
    }
}

/// The command that opens a session.
pub(crate) const HELLO: &str = "BLOB.RDMA_HELLO";

/// The command that reads a stored object into registered memory.
pub(crate) const GET: &str = "BLOB.RDMA_GET";

/// The command that stores an object read out of registered memory.
pub(crate) const SET: &str = "BLOB.RDMA_SET";

/// Whether a given command needs to run on a connection that already has
/// an open RDMA session.
pub fn needs_session(name: &[u8]) -> bool {
    [GET, SET]
        .iter()
        .any(|transfer| name.eq_ignore_ascii_case(transfer.as_bytes()))
}

/// `BLOB.RDMA_HELLO <client-address>`
///
/// Opens the session. The server inserts `client_address` into the address
/// vector of every fabric device it serves from and replies with its own
/// addresses for the client to insert in turn.
pub fn hello(client_address: &[u8]) -> RdmaCommand {
    RdmaCommand {
        name: HELLO,
        arguments: vec![crate::region_ref::encode_hex(client_address).into_bytes()],
    }
}

/// Whether a `BLOB.RDMA_SET` asks the server to check what it read.
///
/// With a checksum, the server compares it with the CRC-32c of the bytes it
/// read and refuses to store them if they differ. Without one, the server
/// stores whatever it read.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum SetChecksum {
    /// Send no checksum. The server does not check the bytes.
    #[default]
    Skip,
    /// Compute the CRC-32c of the window when it is lent. This reads every byte
    /// of the window once.
    Compute,
    /// Send this CRC-32c, which the caller already knows for the window's bytes.
    /// Saves reading the window again.
    Known(u32),
}

/// `BLOB.RDMA_SET <key> <length> [CRC <checksum>] <rkey> <remote-address> <length>`
///
/// The server reads `length` bytes out of the named window and stores them. The
/// first `length` is the object's size, the second the window's; with one window
/// they are the same. `checksum`, when given, is the CRC-32c of the bytes in the
/// window; see [`SetChecksum`].
#[cfg(any(feature = "libfabric", test))]
pub(crate) fn set(
    key: &[u8],
    length: usize,
    checksum: Option<u32>,
    region_ref: &RegionRef,
) -> RdmaCommand {
    let mut arguments = vec![key.to_vec(), number(length as u64)];
    if let Some(checksum) = checksum {
        arguments.push(b"CRC".to_vec());
        arguments.push(number(u64::from(checksum)));
    }
    arguments.extend(
        region_ref
            .to_args(length)
            .into_iter()
            .map(String::into_bytes),
    );
    RdmaCommand {
        name: SET,
        arguments,
    }
}

/// `BLOB.RDMA_GET <key> <rkey> <remote-address> <length>`
///
/// The server writes the stored object into the named window, or replies with an
/// error without writing if the object is longer than `length`.
#[cfg(any(feature = "libfabric", test))]
pub(crate) fn get(key: &[u8], length: usize, region_ref: &RegionRef) -> RdmaCommand {
    let mut arguments = vec![key.to_vec()];
    arguments.extend(
        region_ref
            .to_args(length)
            .into_iter()
            .map(String::into_bytes),
    );
    RdmaCommand {
        name: GET,
        arguments,
    }
}

/// Decimal ASCII, matching how a RESP client renders an integer argument.
#[cfg(any(feature = "libfabric", test))]
fn number(value: u64) -> Vec<u8> {
    value.to_string().into_bytes()
}

#[cfg(test)]
mod tests {
    use super::{RdmaCommand, ReadReceipt, get, hello, needs_session, set};
    use crate::error::RdmaError;
    use crate::region_ref::RegionRef;

    fn region_ref() -> RegionRef {
        RegionRef {
            remote_key: 7,
            remote_address: 0x1000,
        }
    }

    fn arguments(command: &RdmaCommand) -> Vec<String> {
        command
            .arguments()
            .iter()
            .map(|argument| String::from_utf8_lossy(argument).into_owned())
            .collect()
    }

    #[test]
    fn hello_carries_this_client_address_as_hex() {
        let command = hello(&[0xde, 0xad, 0xbe, 0xef]);
        assert_eq!(command.name(), "BLOB.RDMA_HELLO");
        assert_eq!(arguments(&command), ["deadbeef"]);
    }

    #[test]
    fn set_lays_out_arguments_in_wire_order() {
        let command = set(b"key", 64, None, &region_ref());
        assert_eq!(command.name(), "BLOB.RDMA_SET");
        assert_eq!(arguments(&command), ["key", "64", "7", "4096", "64"]);
    }

    #[test]
    fn set_puts_a_checksum_between_the_length_and_the_window() {
        let command = set(b"key", 64, Some(0xE306_9283), &region_ref());
        assert_eq!(
            arguments(&command),
            ["key", "64", "CRC", "3808858755", "7", "4096", "64"]
        );
    }

    /// The server parses the checksum as an unsigned 32-bit decimal, so both ends
    /// of the range must go out that way, never negative or in hex.
    #[test]
    fn set_writes_the_checksum_as_unsigned_decimal() {
        let zero = set(b"key", 1, Some(0), &region_ref());
        assert_eq!(arguments(&zero)[2..4], ["CRC", "0"]);
        let top = set(b"key", 1, Some(u32::MAX), &region_ref());
        assert_eq!(arguments(&top)[2..4], ["CRC", "4294967295"]);
    }

    #[test]
    fn get_lays_out_arguments_in_wire_order() {
        let command = get(b"key", 256, &region_ref());
        assert_eq!(command.name(), "BLOB.RDMA_GET");
        assert_eq!(arguments(&command), ["key", "7", "4096", "256"]);
    }

    /// A key is bytes, not text, and must survive unaltered.
    #[test]
    fn a_key_is_carried_as_raw_bytes() {
        let command = set(&[0x00, 0xff], 1, None, &region_ref());
        assert_eq!(command.arguments()[0].as_slice(), [0x00, 0xff]);
    }

    #[test]
    fn a_byte_count_and_checksum_become_a_receipt() {
        let receipt = ReadReceipt::from_wire(1024, 0xE306_9283).unwrap();
        assert_eq!(receipt.bytes_written, 1024);
        assert_eq!(receipt.checksum, 0xE306_9283);
    }

    /// The whole `u32` range is a valid checksum, including both ends.
    #[test]
    fn a_checksum_at_either_end_of_u32_is_accepted() {
        assert_eq!(ReadReceipt::from_wire(1, 0).unwrap().checksum, 0);
        let top = ReadReceipt::from_wire(1, i64::from(u32::MAX)).unwrap();
        assert_eq!(top.checksum, u32::MAX);
    }

    fn receipt_for(object: &[u8]) -> ReadReceipt {
        ReadReceipt {
            bytes_written: object.len(),
            checksum: crate::checksum(object),
        }
    }

    #[test]
    fn verify_accepts_bytes_that_match() {
        let receipt = receipt_for(b"hello over the fabric");
        assert_eq!(receipt.verify(b"hello over the fabric"), Ok(()));
    }

    /// Only the object is checked, not whatever else the window held.
    #[test]
    fn verify_ignores_the_window_past_the_object() {
        let receipt = receipt_for(b"hello");
        assert_eq!(receipt.verify(b"hello, and leftovers"), Ok(()));
    }

    #[test]
    fn verify_accepts_an_empty_object() {
        assert_eq!(receipt_for(b"").verify(b""), Ok(()));
    }

    #[test]
    fn verify_reports_a_flipped_bit() {
        let receipt = receipt_for(b"hello over the fabric");
        assert_eq!(
            receipt.verify(b"hallo over the fabric"),
            Err(RdmaError::ChecksumMismatch {
                expected: receipt.checksum,
                actual: crate::checksum(b"hallo over the fabric"),
            })
        );
    }

    #[test]
    fn verify_rejects_a_window_shorter_than_the_object() {
        let receipt = receipt_for(b"hello");
        assert_eq!(
            receipt.verify(b"hell"),
            Err(RdmaError::PayloadTooLarge {
                value_bytes: 5,
                capacity: 4,
            })
        );
    }

    #[test]
    fn a_negative_byte_count_is_rejected() {
        assert!(ReadReceipt::from_wire(-1, 0).is_err());
    }

    #[test]
    fn a_checksum_outside_u32_is_rejected() {
        assert!(ReadReceipt::from_wire(1, i64::from(u32::MAX) + 1).is_err());
        assert!(ReadReceipt::from_wire(1, -1).is_err());
    }

    #[test]
    fn only_the_transfers_need_a_session() {
        assert!(needs_session(b"BLOB.RDMA_GET"));
        assert!(needs_session(b"BLOB.RDMA_SET"));
        // Lowercase because a caller may have typed it, and the server does not care.
        assert!(needs_session(b"blob.rdma_set"));

        // The one that opens a session cannot be gated on having one.
        assert!(!needs_session(b"BLOB.RDMA_HELLO"));
        assert!(!needs_session(b"GET"));
        assert!(!needs_session(b"SET"));
        // The TCP variants carry the value over RESP, so they need no session.
        assert!(!needs_session(b"BLOB.TCP_GET"));
        assert!(!needs_session(b"BLOB.TCP_SET"));
        assert!(!needs_session(b""));
    }
}
