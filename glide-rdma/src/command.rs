// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The `LO.*` command vocabulary, independent of any RESP client.
//!
//! The client's fabric address is sent once by `LO.HELLO` and the server
//! remembers it for the connection. `LO.GET` and `LO.SET` commands carry
//! a key and the specific window of the client's registered memory where
//! the RDMA transfer should be performed.

use crate::error::RdmaError;
#[cfg(any(feature = "libfabric", test))]
use crate::region_ref::RegionRef;

/// What a `LO.GET` yielded.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ReadReceipt {
    /// Byte length transferred.
    pub bytes_written: usize,
    /// CRC-32c the server computed over those bytes, when it reported one.
    pub checksum: Option<u32>,
}

impl ReadReceipt {
    /// Convert wire response into a ReadReceipt.
    pub fn from_wire(bytes_written: i64, checksum: Option<i64>) -> Result<Self, RdmaError> {
        let checksum = checksum
            .map(|checksum| {
                u32::try_from(checksum)
                    .map_err(|_| RdmaError::Protocol(format!("checksum out of range {checksum}")))
            })
            .transpose()?;
        let bytes_written = usize::try_from(bytes_written)
            .map_err(|_| RdmaError::Protocol(format!("negative byte count {bytes_written}")))?;
        Ok(Self {
            bytes_written,
            checksum,
        })
    }
}

/// The server's reply to a `LO.GET` or `LO.SET`.
///
/// The server finishes moving bytes to or from the client's memory before it
/// replies, so any reply means it is done with that memory. Passing a reply
/// to `LentBuffer::reclaim` is how a caller gets its buffer back.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransferReply {
    /// `LO.GET` wrote the stored object into the window.
    Read(ReadReceipt),
    /// `LO.GET` found no such key and wrote nothing.
    Missing,
    /// `LO.SET` read the window and stored it.
    Stored,
    /// The server replied with an error.
    Failed,
}

/// A `LO.*` command: its name, and its arguments in wire order.
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
pub(crate) const HELLO: &str = "LO.HELLO";

/// The command that reads a stored object into registered memory.
pub(crate) const GET: &str = "LO.GET";

/// The command that stores an object read out of registered memory.
pub(crate) const SET: &str = "LO.SET";

/// Whether a given command needs to run on a connection that already has
/// an open RDMA session.
pub fn needs_session(name: &[u8]) -> bool {
    [GET, SET]
        .iter()
        .any(|transfer| name.eq_ignore_ascii_case(transfer.as_bytes()))
}

/// `LO.HELLO <client-address>`
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

/// `LO.SET <key> <length> <rkey> <remote-address>`
///
/// The server reads `length` bytes out of the named window and stores them.
#[cfg(any(feature = "libfabric", test))]
pub(crate) fn set(key: &[u8], length: usize, region_ref: &RegionRef) -> RdmaCommand {
    let mut arguments = vec![key.to_vec(), number(length as u64)];
    arguments.extend(region_ref.to_args().into_iter().map(String::into_bytes));
    RdmaCommand {
        name: SET,
        arguments,
    }
}

/// `LO.GET <key> <rkey> <remote-address>`
///
// TODO(lo-get-length): send the length as an argument once module accepts it.
#[cfg(any(feature = "libfabric", test))]
pub(crate) fn get(key: &[u8], region_ref: &RegionRef) -> RdmaCommand {
    let mut arguments = vec![key.to_vec()];
    arguments.extend(region_ref.to_args().into_iter().map(String::into_bytes));
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
        assert_eq!(command.name(), "LO.HELLO");
        assert_eq!(arguments(&command), ["deadbeef"]);
    }

    #[test]
    fn set_lays_out_arguments_in_wire_order() {
        let command = set(b"key", 64, &region_ref());
        assert_eq!(command.name(), "LO.SET");
        assert_eq!(arguments(&command), ["key", "64", "7", "4096"]);
    }

    #[test]
    fn get_lays_out_arguments_in_wire_order() {
        // No length: the server sends the whole object, whatever the caller had
        // room for.
        let command = get(b"key", &region_ref());
        assert_eq!(command.name(), "LO.GET");
        assert_eq!(arguments(&command), ["key", "7", "4096"]);
    }

    /// A key is bytes, not text, and must survive unaltered.
    #[test]
    fn a_key_is_carried_as_raw_bytes() {
        let command = set(&[0x00, 0xff], 1, &region_ref());
        assert_eq!(command.arguments()[0].as_slice(), [0x00, 0xff]);
    }

    #[test]
    fn a_byte_count_becomes_a_receipt() {
        let receipt = ReadReceipt::from_wire(1024, None).unwrap();
        assert_eq!(receipt.bytes_written, 1024);
        assert_eq!(receipt.checksum, None);
    }

    #[test]
    fn a_checksum_rides_along_with_the_byte_count() {
        let receipt = ReadReceipt::from_wire(1024, Some(0xE306_9283)).unwrap();
        assert_eq!(receipt.bytes_written, 1024);
        assert_eq!(receipt.checksum, Some(0xE306_9283));
    }

    #[test]
    fn a_negative_byte_count_is_rejected() {
        assert!(ReadReceipt::from_wire(-1, None).is_err());
    }

    #[test]
    fn a_checksum_outside_u32_is_rejected() {
        assert!(ReadReceipt::from_wire(1, Some(i64::from(u32::MAX) + 1)).is_err());
    }

    #[test]
    fn only_the_transfers_need_a_session() {
        assert!(needs_session(b"LO.GET"));
        assert!(needs_session(b"LO.SET"));
        // Lowercase because a caller may have typed it, and the server does not care.
        assert!(needs_session(b"lo.set"));

        // The one that opens a session cannot be gated on having one.
        assert!(!needs_session(b"LO.HELLO"));
        assert!(!needs_session(b"GET"));
        assert!(!needs_session(b"SET"));
        assert!(!needs_session(b""));
    }
}
