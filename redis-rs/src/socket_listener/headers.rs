use lifeguard::RcRecycled;
use num_derive::{FromPrimitive, ToPrimitive};
use std::{ops::Range, rc::Rc};

/// Length of the message field in the request & response.
pub const MESSAGE_LENGTH_FIELD_LENGTH: usize = 4;
/// Length of the callback index field in the request & response.
pub const CALLBACK_INDEX_FIELD_LENGTH: usize = 4;
/// Length of the type field in the request & response.
pub const TYPE_FIELD_LENGTH: usize = 4;

// Request format:
// [0..4] bytes -> message length.
// [4..8] bytes -> callback index.
// [8..12] bytes -> type. RequestType for request, ResponseType for response.
// if get -  [12..message length] -> key.
// if set -  [12..16] -> key length
//       [16..16 + key length] -> key
//       [16+key length .. message length] -> value

/// The index at the end of the message length field.
pub const MESSAGE_LENGTH_END: usize = MESSAGE_LENGTH_FIELD_LENGTH;
/// The index at the end of the callback index length field.
pub const CALLBACK_INDEX_END: usize = MESSAGE_LENGTH_END + CALLBACK_INDEX_FIELD_LENGTH;
/// The index at the end of the type field.
pub const TYPE_END: usize = CALLBACK_INDEX_END + TYPE_FIELD_LENGTH;
/// The length of the header.
pub const HEADER_END: usize = TYPE_END;
/// The length of the header, when it contains a second argument.
pub const HEADER_WITH_KEY_LENGTH_END: usize = HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;

/// An enum representing the values of the request type field.
#[derive(ToPrimitive, FromPrimitive)]
pub enum RequestType {
    /// Type of a server address request
    ServerAddress = 0,
    /// Type of a get string request.
    GetString = 1,
    /// Type of a set string request.
    SetString = 2,
}

/// An enum representing the values of the request type field.
#[derive(ToPrimitive, FromPrimitive, PartialEq, Eq)]
pub enum ResponseType {
    /// Type of a response that returns a null.
    Null = 0,
    /// Type of a response that returns a string.
    String = 1,
}

#[derive(PartialEq, Debug, Clone)]
pub(super) enum RequestRanges {
    ServerAddress {
        address: Range<usize>,
    },
    Get {
        key: Range<usize>,
    },
    Set {
        key: Range<usize>,
        value: Range<usize>,
    },
}

pub(super) type Buffer = RcRecycled<Vec<u8>>;
/// Buffer needs to be wrapped in Rc, because RcRecycled's clone implementation
/// involves copying the array.
pub(super) type SharedBuffer = Rc<Buffer>;

/// The full parsed information for a request from the client's caller.
pub(super) struct WholeRequest {
    pub(super) callback_index: u32,
    pub(super) request_type: RequestRanges,
    /// A buffer containing the original request, and all the non-structured values that weren't copied.
    pub(super) buffer: SharedBuffer,
}
