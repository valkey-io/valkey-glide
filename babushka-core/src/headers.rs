use lifeguard::RcRecycled;
use std::rc::Rc;
include!(concat!(env!("OUT_DIR"), "/protobuf/mod.rs"));

pub(super) type Buffer = RcRecycled<Vec<u8>>;
/// Buffer needs to be wrapped in Rc, because RcRecycled's clone implementation
/// involves copying the array.
pub(super) type SharedBuffer = Rc<Buffer>;
