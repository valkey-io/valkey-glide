use redis::aio::{ConnectionLike, MultiplexedConnection};

pub(super) trait BabushkaClient: ConnectionLike + Send + Clone {}

impl BabushkaClient for MultiplexedConnection {}
