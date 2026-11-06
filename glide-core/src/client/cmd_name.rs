// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The command name of a request, computed once, on the stack.
//!
//! `Routable::command()` allocates a fresh uppercased `Vec<u8>` on every call,
//! and the `Client::send_command` path used to call it several times per
//! command: transaction tracking, the pubsub intercept, the request-timeout and
//! blocking-command checks, the connection-state post-hooks, standalone routing
//! and the expected-type lookup. [`CmdName`] builds the same bytes once into an
//! inline buffer, so the common case allocates nothing. A name longer than the
//! buffer is built by `Routable::command()` itself, on the heap, as before.

use redis::Cmd;
use redis::cluster_routing::{Routable, is_container_command};

/// Inline capacity. Every Valkey command name, including two-word container
/// commands such as `CLIENT TRACKINGINFO`, fits.
const INLINE: usize = 48;

/// The uppercased command name, byte for byte what `Routable::command()` returns.
pub(crate) enum CmdName {
    Inline { buf: [u8; INLINE], len: u8 },
    Heap(Vec<u8>),
}

impl CmdName {
    /// Same contract as `Routable::command()`: `None` when the command has no
    /// first argument.
    pub(crate) fn of(cmd: &Cmd) -> Option<Self> {
        let primary = cmd.arg_idx(0)?;
        if primary.len() > INLINE {
            return cmd.command().map(CmdName::Heap);
        }
        let mut buf = [0u8; INLINE];
        let mut len = primary.len();
        buf[..len].copy_from_slice(primary);
        buf[..len].make_ascii_uppercase();
        // Container commands get " <SUBCOMMAND>" appended, as in Routable::command.
        if is_container_command(&buf[..len])
            && let Some(secondary) = cmd.arg_idx(1)
        {
            let total = len + 1 + secondary.len();
            if total > INLINE {
                return cmd.command().map(CmdName::Heap);
            }
            buf[len] = b' ';
            buf[len + 1..total].copy_from_slice(secondary);
            buf[len + 1..total].make_ascii_uppercase();
            len = total;
        }
        Some(CmdName::Inline {
            buf,
            // INLINE fits in a u8, and len <= INLINE on this path.
            len: len as u8,
        })
    }

    pub(crate) fn as_bytes(&self) -> &[u8] {
        match self {
            CmdName::Inline { buf, len } => &buf[..*len as usize],
            CmdName::Heap(v) => v.as_slice(),
        }
    }
}

/// The name bytes of an optional [`CmdName`], or `&[]` for a command with no
/// arguments (which no name-based check matches).
pub(crate) fn name_bytes(name: &Option<CmdName>) -> &[u8] {
    name.as_ref().map_or(&[], CmdName::as_bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    const _: () = assert!(INLINE <= u8::MAX as usize);

    fn check(cmd: &Cmd) {
        let expected = cmd.command();
        let got = CmdName::of(cmd).map(|n| n.as_bytes().to_vec());
        assert_eq!(
            got, expected,
            "CmdName must equal Routable::command for {cmd:?}"
        );
    }

    #[test]
    fn matches_routable_command_for_every_shape() {
        check(&Cmd::new()); // no args -> None
        check(redis::cmd("GET").arg("k"));
        check(redis::cmd("get").arg("k"));
        check(&redis::cmd("GeT"));
        check(redis::cmd("client").arg("setname").arg("x"));
        check(redis::cmd("CLIENT").arg("SetName").arg("x"));
        check(&redis::cmd("client")); // container without subcommand
        check(redis::cmd("config").arg("get").arg("*"));
        check(redis::cmd("xgroup").arg("create"));
        check(redis::cmd("FT.CONFIG").arg("get")); // not a container: single word
        check(redis::cmd("sentinel").arg("masters"));
        check(redis::cmd("acl").arg("whoami"));
        check(redis::cmd("blob.rdma_get").arg("k"));
        check(redis::cmd("xread").arg("block").arg("0"));
    }

    #[test]
    fn matches_routable_command_for_every_container() {
        for container in [
            "XGROUP", "OBJECT", "SLOWLOG", "FUNCTION", "MODULE", "COMMAND", "PUBSUB", "CONFIG",
            "MEMORY", "XINFO", "CLIENT", "ACL", "SCRIPT", "CLUSTER", "LATENCY", "SENTINEL",
        ] {
            assert!(is_container_command(container.as_bytes()), "{container}");
            check(redis::cmd(&container.to_ascii_lowercase()).arg("sub"));
            check(redis::cmd(container).arg("SUB").arg("x"));
        }
    }

    #[test]
    fn heap_fallback_and_boundaries_match_routable_command() {
        // Primary longer than the inline buffer.
        check(redis::cmd(&"X".repeat(INLINE + 5)).arg("v"));
        // Primary exactly at the boundary.
        check(&redis::cmd(&"z".repeat(INLINE)));
        // Container + subcommand longer than the buffer.
        check(redis::cmd("CLIENT").arg("y".repeat(INLINE)));
        // Container + subcommand exactly at the boundary (6 + 1 + 41 = 48).
        check(redis::cmd("CLIENT").arg("q".repeat(INLINE - 7)));
        // One past the boundary.
        check(redis::cmd("CLIENT").arg("q".repeat(INLINE - 6)));
        assert!(matches!(
            CmdName::of(redis::cmd("CLIENT").arg("q".repeat(INLINE - 7))),
            Some(CmdName::Inline { .. })
        ));
        assert!(matches!(
            CmdName::of(redis::cmd("CLIENT").arg("q".repeat(INLINE - 6))),
            Some(CmdName::Heap(_))
        ));
    }

    #[test]
    fn non_ascii_bytes_are_left_as_is() {
        check(redis::cmd("gét").arg("k"));
        check(redis::cmd("client").arg("sétname"));
    }

    #[test]
    fn name_bytes_is_empty_for_no_args() {
        assert_eq!(name_bytes(&CmdName::of(&Cmd::new())), b"");
        assert_eq!(name_bytes(&CmdName::of(&redis::cmd("ping"))), b"PING");
    }
}
