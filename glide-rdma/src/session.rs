// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! A handshake accomplishes two things:
//!
//! 1. The client learns the server node's fabric addresses and inserts them into
//!    its local address vector so an RDMA transfer the node initiates is accepted.
//! 2. The node learns the client's fabric address via `LO.HELLO` and identifies it
//!    by the client_id of the connection it arrived on.
//!
//! On both client and server, a RDMA session is paired with a RESP connection.

/// The outcome of one handshake with one server node.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Handshake {
    /// Every fabric address the server node may initiate a transfer from.
    pub peers: Vec<Vec<u8>>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_handshake_carries_every_address_the_node_may_transfer_from() {
        let handshake = Handshake {
            peers: vec![vec![1, 2, 3]],
        };
        assert_eq!(handshake.peers, vec![vec![1, 2, 3]]);
    }
}
