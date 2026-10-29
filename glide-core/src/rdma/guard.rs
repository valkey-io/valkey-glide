// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Refusing the RDMA commands anywhere but the client's RDMA transfer methods.
//!
//! Present in every build, so a build without the `rdma` feature refuses them too.

/// Refuse a `BLOB.*` command that did not come from the client's RDMA methods,
/// `Client::rdma_get` and `Client::rdma_set`.
pub(crate) fn refuse_direct(name: &[u8]) -> redis::RedisResult<()> {
    const COMMANDS: [&[u8]; 3] = [b"BLOB.RDMA_HELLO", b"BLOB.RDMA_GET", b"BLOB.RDMA_SET"];
    if !COMMANDS
        .iter()
        .any(|command| name.eq_ignore_ascii_case(command))
    {
        return Ok(());
    }
    Err(redis::RedisError::from((
        redis::ErrorKind::ClientError,
        "RDMA command refused",
        format!(
            "{} can only be sent by the client's RDMA transfer methods",
            String::from_utf8_lossy(name)
        ),
    )))
}

/// [`refuse_direct`] for a built command.
pub(crate) fn refuse_direct_command(command: &redis::Cmd) -> redis::RedisResult<()> {
    match command.args_iter().next() {
        Some(redis::Arg::Simple(name)) => refuse_direct(name),
        _ => Ok(()),
    }
}

/// [`refuse_direct`] for every command in a batch.
pub(crate) fn refuse_direct_in_batch(batch: &redis::Pipeline) -> redis::RedisResult<()> {
    batch
        .cmd_iter()
        .try_for_each(|command| refuse_direct_command(command))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rdma_commands_are_refused_outside_the_rdma_methods() {
        for name in [
            b"BLOB.RDMA_HELLO".as_slice(),
            b"BLOB.RDMA_GET",
            b"BLOB.RDMA_SET",
            b"blob.rdma_get",
        ] {
            let error = refuse_direct(name).expect_err("an RDMA command is refused");
            assert_eq!(error.kind(), redis::ErrorKind::ClientError);
            assert!(
                error.to_string().contains("RDMA transfer methods"),
                "{error}"
            );
        }
        // The TCP variants carry the value over RESP, so callers may send them directly.
        for name in [
            b"GET".as_slice(),
            b"SET",
            b"BLOB",
            b"BLOB.RDMA_GETX",
            b"BLOB.TCP_GET",
            b"BLOB.TCP_SET",
            b"",
        ] {
            assert!(
                refuse_direct(name).is_ok(),
                "{:?}",
                String::from_utf8_lossy(name)
            );
        }
    }

    #[test]
    fn a_batch_with_an_rdma_command_is_refused() {
        let mut batch = redis::pipe();
        batch.cmd("SET").arg("key").arg("value");
        assert!(refuse_direct_in_batch(&batch).is_ok());

        batch
            .cmd("BLOB.RDMA_SET")
            .arg("key")
            .arg("8")
            .arg("7")
            .arg("4096");
        assert!(refuse_direct_in_batch(&batch).is_err());
    }
}
