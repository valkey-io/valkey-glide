use lifeguard::RcRecycled;
use std::{ops::Range, rc::Rc};

// language format:
// [0..4] bytes -> message length.
// [4..8] bytes -> callback index.
// [8..12] bytes -> type. set or get.
// if get -  [12..message length] -> key.
// if set -  [12..16] -> key length
//       [16..16 + key length] -> key
//       [16+key length .. message length] -> value
pub(super) const MESSAGE_LENGTH_END: usize = 4;
pub(super) const CALLBACK_INDEX_END: usize = MESSAGE_LENGTH_END + 4;
pub(super) const READ_HEADER_END: usize = CALLBACK_INDEX_END + 4;
pub(super) const WRITE_HEADER_END: usize = CALLBACK_INDEX_END;

#[derive(PartialEq, Debug, Clone)]
pub(super) enum RequestRanges {
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
