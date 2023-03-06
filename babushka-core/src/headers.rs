use lifeguard::RcRecycled;
use num_derive::{FromPrimitive, ToPrimitive};
use std::rc::Rc;
include!(concat!(env!("OUT_DIR"), "/protobuf/mod.rs"));

/// An enum representing the values of the request type field.
#[derive(ToPrimitive, FromPrimitive)]
pub enum RequestType {
    /// Invalid request type
    InvalidRequest = 0,
    /// Type of a server address request
    ServerAddress = 1,
    /// Type of a get string request.
    GetString = 2,
    /// Type of a set string request.
    SetString = 3,
}

pub(super) type Buffer = RcRecycled<Vec<u8>>;
/// Buffer needs to be wrapped in Rc, because RcRecycled's clone implementation
/// involves copying the array.
pub(super) type SharedBuffer = Rc<Buffer>;
