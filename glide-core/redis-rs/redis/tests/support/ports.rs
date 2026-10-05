//! Clients retain their old addresses during an outage. Reusing those ports for
//! another fixture would prevent them from reaching the replacement cluster.

use std::{collections::HashMap, io, net::SocketAddr, sync::Mutex};

use once_cell::sync::Lazy;
use rand::Rng;
use socket2::{Domain, Socket, Type};

static RESERVED: Lazy<Mutex<HashMap<u16, usize>>> = Lazy::new(|| Mutex::new(HashMap::new()));

pub(crate) struct PortReservation(Vec<u16>);

impl PortReservation {
    pub(crate) fn new(ports: impl IntoIterator<Item = u16>) -> Self {
        let ports: Vec<_> = ports.into_iter().collect();
        let mut reserved = RESERVED.lock().unwrap();
        for port in &ports {
            *reserved.entry(*port).or_default() += 1;
        }
        Self(ports)
    }
}

impl Drop for PortReservation {
    fn drop(&mut self) {
        let mut reserved = RESERVED.lock().unwrap();
        for port in &self.0 {
            let count = reserved.get_mut(port).unwrap();
            *count -= 1;
            if *count == 0 {
                reserved.remove(port);
            }
        }
    }
}

pub fn get_random_available_port() -> u16 {
    const FIRST: u32 = 1024;
    const COUNT: u32 = u16::MAX as u32 + 1 - FIRST;
    let offset = rand::rng().random_range(0..COUNT);
    available_port((0..COUNT).map(|i| (FIRST + (offset + i) % COUNT) as u16))
        .expect("no unreserved test port is available")
}

pub(crate) fn available_port(candidates: impl IntoIterator<Item = u16>) -> io::Result<u16> {
    let reserved = RESERVED.lock().unwrap();
    for port in candidates {
        // A port-zero probe can occupy a reserved restart address before its
        // assigned number is available for inspection.
        if reserved.contains_key(&port) {
            continue;
        }
        let socket = Socket::new(Domain::IPV4, Type::STREAM, None)?;
        socket.set_reuse_address(true)?;
        let addr: SocketAddr = ([127, 0, 0, 1], port).into();
        match socket.bind(&addr.into()).and_then(|()| socket.listen(1)) {
            Ok(()) => return Ok(port),
            Err(err) if err.kind() == io::ErrorKind::AddrInUse => continue,
            Err(err) => return Err(err),
        }
    }
    Err(io::Error::new(
        io::ErrorKind::AddrNotAvailable,
        "no unreserved test port is available",
    ))
}
