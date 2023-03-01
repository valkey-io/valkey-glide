/// Contains information that determines how the request and response headers are shaped.
pub mod headers;
mod rotating_buffer;

pub mod connections;
pub use crate::connections::*;
mod socket_listener;
pub use socket_listener::*;
pub mod socket_listener_legacy;
