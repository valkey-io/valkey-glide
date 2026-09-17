// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! The command-argument writer.
//!
//! [`ValkeyWrite`] is the destination that [`crate::ToValkeyArgs`] encodes into.
//! Writing an argument copies its bytes straight into the command's buffer, with
//! no intermediate allocation.

// ---- ValkeyWrite ----

/// A writer that command arguments are written into.
///
/// Mirrors redis-rs's `RedisWrite`.
pub trait ValkeyWrite {
    /// Append a single argument's bytes.
    fn write_arg(&mut self, arg: &[u8]);

    /// Append a single argument formatted via [`std::fmt::Display`].
    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        self.write_arg(arg.to_string().as_bytes())
    }
}

impl ValkeyWrite for Vec<Vec<u8>> {
    fn write_arg(&mut self, arg: &[u8]) {
        self.push(arg.to_owned());
    }

    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        self.push(arg.to_string().into_bytes())
    }
}

impl ValkeyWrite for crate::cmd::Cmd {
    fn write_arg(&mut self, arg: &[u8]) {
        redis::RedisWrite::write_arg(self.as_redis_mut(), arg);
    }

    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        redis::RedisWrite::write_arg_fmt(self.as_redis_mut(), arg);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // Values to encode.
    const TEXT: &[u8] = b"key";
    const BINARY: &[u8] = &[0u8, 1, 2, 255, 0, 42];
    const NUMBER: i64 = -7;

    #[test]
    fn vec_writer() {
        let mut out: Vec<Vec<u8>> = Vec::new();
        out.write_arg(TEXT);
        out.write_arg(BINARY);
        out.write_arg_fmt(NUMBER);

        assert_eq!(out, vec![TEXT.to_vec(), BINARY.to_vec(), b"-7".to_vec()]);
    }

    #[test]
    fn cmd_writer() {
        let mut out = crate::cmd::Cmd::new();
        out.write_arg(TEXT);
        out.write_arg(BINARY);
        out.write_arg_fmt(NUMBER);

        assert_eq!(
            out.as_redis().get_packed_command(),
            crate::cmd::Cmd::new()
                .arg(TEXT)
                .arg(BINARY)
                .arg(NUMBER)
                .as_redis()
                .get_packed_command()
        );
    }
}
