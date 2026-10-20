// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! What a failed `BLOB.RDMA_HELLO` tells the caller: whether the node lacks the
//! module, refused the handshake, or answered in a shape this client cannot read.

use super::RdmaUnavailable;

/// Why a `BLOB.RDMA_HELLO` was refused by the server.
pub(crate) fn handshake_refused(node: &str, error: redis::RedisError) -> redis::RedisError {
    if !answered_by_server(&error) {
        return error;
    }
    let message = error.to_string().to_ascii_lowercase();
    let (kind, reason) = if error.kind() == redis::ErrorKind::ReadOnly {
        // Stays `READONLY`, so a cluster client retries it on the new primary.
        let reason = RdmaUnavailable::HandshakeRefused {
            node: node.to_string(),
            reason: error.to_string(),
        };
        (redis::ErrorKind::ReadOnly, reason)
    } else if message.contains("unknown command") || message.contains("unknown or disabled command")
    {
        let reason = RdmaUnavailable::ServerModuleMissing {
            node: node.to_string(),
        };
        (redis::ErrorKind::InvalidClientConfig, reason)
    } else {
        let reason = RdmaUnavailable::HandshakeRefused {
            node: node.to_string(),
            reason: error.to_string(),
        };
        (redis::ErrorKind::InvalidClientConfig, reason)
    };
    // The server answered, so the transfer command was never sent on this
    // connection. Keeping the server's description is what tells
    // `rdma_transfer` that, and it hands the buffer back.
    redis::RedisError::from((kind, redis::SERVER_ERROR_DESCRIPTION, reason.to_string()))
}

/// A `BLOB.RDMA_HELLO` reply this client could not read.
///
/// Different from a refusal because it indicates the node has the
/// module and answered, but the two disagree about the reply's shape.
///
/// Carries the server's description although the reply was not an error: the
/// server answered, so the transfer command was never sent on this connection,
/// and `rdma_transfer` hands the buffer back.
pub(crate) fn malformed_handshake(node: &str, error: redis::RedisError) -> redis::RedisError {
    let reason = RdmaUnavailable::MalformedHandshake {
        node: node.to_string(),
        reason: error.to_string(),
    };
    redis::RedisError::from((
        redis::ErrorKind::TypeError,
        redis::SERVER_ERROR_DESCRIPTION,
        reason.to_string(),
    ))
}

/// Whether the server answered the handshake at all.
fn answered_by_server(error: &redis::RedisError) -> bool {
    !error.is_io_error()
        && !error.is_connection_dropped()
        && !error.is_timeout()
        && !matches!(
            error.kind(),
            redis::ErrorKind::IoError
                | redis::ErrorKind::BusyLoadingError
                | redis::ErrorKind::TryAgain
        )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rdma::protocol;

    fn server_error(detail: &str) -> redis::RedisError {
        redis::RedisError::from((
            redis::ErrorKind::ResponseError,
            redis::SERVER_ERROR_DESCRIPTION,
            detail.to_string(),
        ))
    }

    #[test]
    fn a_server_that_does_not_know_the_command_has_no_module() {
        for detail in [
            "unknown command 'BLOB.RDMA_HELLO'",
            "unknown or disabled command 'BLOB.RDMA_HELLO'",
        ] {
            let failure = handshake_refused("10.0.0.1:6379", server_error(detail));
            let message = failure.to_string();

            assert!(message.contains("no RDMA module loaded"), "{message}");
            assert!(message.contains("10.0.0.1:6379"), "{message}");
        }
    }

    #[test]
    fn a_replica_refusal_names_the_node_and_stays_readonly() {
        let failure = handshake_refused("10.0.0.1:6379", replica_refusal());

        assert_eq!(failure.kind(), redis::ErrorKind::ReadOnly);
        let message = failure.to_string();
        assert!(message.contains("10.0.0.1:6379"), "{message}");
        assert!(message.contains("refused the RDMA handshake"), "{message}");
        assert!(message.contains("read only replica"), "{message}");
    }

    /// A `READONLY` reply exactly as the parser builds it from the wire.
    fn replica_refusal() -> redis::RedisError {
        redis::parse_redis_value(b"-READONLY You can't write against a read only replica.\r\n")
            .and_then(|reply| reply.extract_error())
            .expect_err("a READONLY reply is an error")
    }

    /// A refusal is the server's own reply, so it must stay one: that is how a
    /// transfer whose handshake was refused knows its command was never sent.
    #[test]
    fn every_refusal_stays_a_server_reply() {
        for (refusal, kind) in [
            (
                server_error("unknown command 'BLOB.RDMA_HELLO'"),
                redis::ErrorKind::InvalidClientConfig,
            ),
            (
                server_error("ERR not accepting sessions"),
                redis::ErrorKind::InvalidClientConfig,
            ),
            (replica_refusal(), redis::ErrorKind::ReadOnly),
        ] {
            let failure = handshake_refused("10.0.0.1:6379", refusal);

            assert!(failure.is_server_reply(), "{failure}");
            assert_eq!(failure.kind(), kind, "{failure}");
        }
    }

    #[test]
    fn a_handshake_that_was_never_answered_is_returned_unchanged() {
        for unanswered in [
            redis::RedisError::from(std::io::Error::from(std::io::ErrorKind::BrokenPipe)),
            redis::RedisError::from((redis::ErrorKind::IoError, "connection closed")),
            redis::RedisError::from((redis::ErrorKind::BusyLoadingError, "LOADING")),
            redis::RedisError::from((redis::ErrorKind::TryAgain, "TRYAGAIN")),
        ] {
            let kind = unanswered.kind();

            let failure = handshake_refused("10.0.0.1:6379", unanswered);

            assert_eq!(
                failure.kind(),
                kind,
                "{kind:?} must reach the caller unchanged"
            );
        }
    }

    #[test]
    fn an_unreadable_reply_names_the_node_and_stays_a_protocol_error() {
        let unreadable = protocol::as_redis_error(glide_rdma::RdmaError::Protocol(
            "blob.rdma_hello returned no addresses".to_string(),
        ));

        let failure = malformed_handshake("10.0.0.1:6379", unreadable);

        assert_eq!(failure.kind(), redis::ErrorKind::TypeError);
        assert!(failure.is_server_reply(), "the server answered: {failure}");
        let message = failure.to_string();
        assert!(message.contains("10.0.0.1:6379"), "{message}");
        assert!(
            message.contains("malformed BLOB.RDMA_HELLO reply"),
            "{message}"
        );
    }
}
