use redis::aio::{ConnectionLike, ConnectionManager, MultiplexedConnection};

pub trait BabushkaClient: ConnectionLike + Send + Clone {}

impl BabushkaClient for MultiplexedConnection {}
impl BabushkaClient for ConnectionManager {}
