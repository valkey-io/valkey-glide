// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! What a failed `BLOB.RDMA_HELLO` tells the caller: whether the node lacks the
//! module, refused the handshake, or may hold a session this client could not
//! open, which means the connection has to be replaced.
//!
//! Also which failed transfers leave the server done with their buffer; see
//! [`server_is_done_with`].

use super::RdmaUnavailable;

/// Whether a transfer that failed with `error` left the server done with its
/// buffer, so the buffer can be handed back.
///
/// Two kinds of error prove that:
///
/// - An error reply to the transfer. The server answered, so it is done. Only
///   the reply parser builds these.
/// - A failed handshake, built in this module. The transfer was never sent, so
///   the server never had the buffer. These are built with
///   [`redis::RedisError::command_not_sent`].
///
/// Both pass [`redis::RedisError::is_server_reply`], which is all this checks.
/// So any other code that builds an error with `command_not_sent` must also
/// mean the server cannot be using the memory. Otherwise a transfer would hand
/// back a buffer the server may still write into.
pub(crate) fn server_is_done_with(error: &redis::RedisError) -> bool {
    error.is_server_reply()
}

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
        // Keeps the server's kind, so that a passing state such as `CLUSTERDOWN`,
        // `MASTERDOWN` or `NOAUTH` is retried or reconnected as it would be for
        // any other command.
        let reason = RdmaUnavailable::HandshakeRefused {
            node: node.to_string(),
            reason: error.to_string(),
        };
        (error.kind(), reason)
    };
    // The server answered the handshake, so the transfer command was never sent
    // on this connection, and `rdma_transfer` hands the buffer back.
    redis::RedisError::command_not_sent(kind, reason.to_string())
}

/// A `BLOB.RDMA_HELLO` reply this client could not read.
///
/// Different from a refusal because it indicates the node has the module and
/// answered, but the two disagree about the reply's shape. The server opened a
/// session that this client cannot open, so the connection has to be replaced;
/// see [`connection_needs_replacing`].
pub(crate) fn malformed_handshake(node: &str, error: redis::RedisError) -> redis::RedisError {
    connection_needs_replacing(RdmaUnavailable::MalformedHandshake {
        node: node.to_string(),
        reason: error.to_string(),
    })
}

/// The server answered the handshake, but this client failed to open its side
/// of the session, for example because the fabric would not take the server's
/// address. The server's side is open, so the connection has to be replaced; see
/// [`connection_needs_replacing`].
pub(crate) fn session_not_opened(node: &str, error: glide_rdma::RdmaError) -> redis::RedisError {
    connection_needs_replacing(RdmaUnavailable::SessionNotOpened {
        node: node.to_string(),
        reason: error.to_string(),
    })
}

/// An earlier handshake on this connection may have opened a session on the
/// server that this client never opened, for example because that handshake
/// was cancelled before its reply arrived. The server refuses a second
/// handshake, so the connection has to be replaced; see
/// [`connection_needs_replacing`].
pub(crate) fn handshake_unsettled(node: &str) -> redis::RedisError {
    connection_needs_replacing(RdmaUnavailable::HandshakeUnsettled {
        node: node.to_string(),
    })
}

/// The error for a handshake that leaves its connection unable to carry a
/// transfer.
///
/// - `FatalSendError` says the transfer was not sent and the connection must be
///   replaced. A standalone client reconnects. A cluster client reconnects and
///   sends the transfer again on the new connection, which is safe because the
///   first attempt never sent it.
/// - [`redis::RedisError::command_not_sent`] tells `rdma_transfer` that the
///   server is done with the buffer, since the transfer was never sent, so it
///   hands the buffer back.
fn connection_needs_replacing(reason: RdmaUnavailable) -> redis::RedisError {
    redis::RedisError::command_not_sent(redis::ErrorKind::FatalSendError, reason.to_string())
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

    /// A refused handshake means the transfer was never sent, so its buffer must
    /// come back.
    #[test]
    fn every_refusal_leaves_the_server_done_with_the_buffer() {
        for (refusal, kind) in [
            (
                server_error("unknown command 'BLOB.RDMA_HELLO'"),
                redis::ErrorKind::InvalidClientConfig,
            ),
            (
                server_error("ERR not accepting sessions"),
                redis::ErrorKind::ResponseError,
            ),
            (replica_refusal(), redis::ErrorKind::ReadOnly),
        ] {
            let failure = handshake_refused("10.0.0.1:6379", refusal);

            assert!(server_is_done_with(&failure), "{failure}");
            assert_eq!(failure.kind(), kind, "{failure}");
        }
    }

    /// An error the client raised itself, such as a lost connection, says nothing
    /// about whether the server is still using the buffer.
    #[test]
    fn a_local_error_does_not_leave_the_server_done_with_the_buffer() {
        for local in [
            redis::RedisError::from(std::io::Error::from(std::io::ErrorKind::BrokenPipe)),
            redis::RedisError::from((redis::ErrorKind::FatalSendError, "connection closed")),
            redis::RedisError::from((
                redis::ErrorKind::ResponseError,
                "Internal failure: receiver was dropped before delivering a response",
            )),
        ] {
            assert!(!server_is_done_with(&local), "{local}");
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

    /// A refusal that means the node is passing through a state, rather than that
    /// it will never take the handshake, keeps its kind so it is retried or
    /// reconnected like any other command.
    #[test]
    fn a_passing_refusal_keeps_its_kind() {
        for wire in [
            &b"-CLUSTERDOWN The cluster is down\r\n"[..],
            &b"-MASTERDOWN Link with MASTER is down\r\n"[..],
            &b"-NOAUTH Authentication required.\r\n"[..],
        ] {
            let refusal = redis::parse_redis_value(wire)
                .and_then(|reply| reply.extract_error())
                .expect_err("an error reply");
            let kind = refusal.kind();

            let failure = handshake_refused("10.0.0.1:6379", refusal);

            assert_eq!(failure.kind(), kind, "{failure}");
            assert_ne!(failure.kind(), redis::ErrorKind::InvalidClientConfig);
            assert!(server_is_done_with(&failure), "{failure}");
            assert!(
                failure.to_string().contains("refused the RDMA handshake"),
                "{failure}"
            );
        }
    }

    #[test]
    fn an_unreadable_reply_names_the_node_and_replaces_the_connection() {
        let unreadable = protocol::as_redis_error(glide_rdma::RdmaError::Protocol(
            "blob.rdma_hello returned no addresses".to_string(),
        ));

        let failure = malformed_handshake("10.0.0.1:6379", unreadable);

        assert_eq!(failure.kind(), redis::ErrorKind::FatalSendError);
        assert!(
            server_is_done_with(&failure),
            "the transfer was never sent: {failure}"
        );
        let message = failure.to_string();
        assert!(message.contains("10.0.0.1:6379"), "{message}");
        assert!(
            message.contains("malformed BLOB.RDMA_HELLO reply"),
            "{message}"
        );
    }

    /// Every failure that leaves the server holding a session this client does
    /// not have replaces the connection, and hands the buffer back.
    #[test]
    fn an_out_of_step_session_replaces_the_connection() {
        for failure in [
            session_not_opened(
                "10.0.0.1:6379",
                glide_rdma::RdmaError::Configuration("bad address".to_string()),
            ),
            handshake_unsettled("10.0.0.1:6379"),
        ] {
            assert_eq!(
                failure.kind(),
                redis::ErrorKind::FatalSendError,
                "{failure}"
            );
            assert!(failure.is_unrecoverable_error(), "reconnects: {failure}");
            assert!(server_is_done_with(&failure), "{failure}");
            assert!(failure.to_string().contains("10.0.0.1:6379"), "{failure}");
        }
    }
}
