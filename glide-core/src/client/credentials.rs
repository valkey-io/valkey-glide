// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The owning `Client`'s current static (non-IAM) credentials, readable by the
//! scope path.
//!
//! `Client::update_connection_password` and a user-issued `AUTH` push the new
//! value down into the standalone or cluster connection for reconnect, but that
//! storage has no read path and no change signal. Scope connections are built and
//! re-authenticated outside those layers, so they read from here instead: the
//! same one-owner shape as the IAM token manager and the certificate reload
//! handle, with a generation counter for change detection.

use std::sync::RwLock;
use std::sync::atomic::{AtomicU64, Ordering};

/// Which credentials a connection's `AUTH` was built from. A credential source
/// (this module's handle, or `IAMTokenManager::token_generation`) advances its
/// generation on every accepted change; a connection keeps the generation it
/// last authenticated at and re-AUTHs when the source has moved past it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct AuthGeneration(u64);

impl AuthGeneration {
    /// A connection that has not authenticated from any source. No source ever
    /// produces this value (sources start at 1), so a connection holding it
    /// re-AUTHs on its first command.
    pub const NEVER: Self = Self(0);

    pub(crate) fn from_source(generation: u64) -> Self {
        debug_assert_ne!(generation, 0, "a credential source never reports 0");
        Self(generation)
    }
}

/// A username/password pair at one point in time. No `Debug`: it holds the password.
#[derive(Clone, Default)]
pub(crate) struct PasswordCredentials {
    pub(crate) username: Option<String>,
    pub(crate) password: Option<String>,
}

impl PasswordCredentials {
    /// The `AUTH` command for these credentials, `None` when there is no
    /// password to send. An empty username is the default user, so it is left off
    /// (a one-argument `AUTH`).
    pub(crate) fn auth_command(&self) -> Option<redis::Cmd> {
        let password = self.password.as_deref().filter(|p| !p.is_empty())?;
        let mut cmd = redis::cmd("AUTH");
        if let Some(username) = self.username.as_deref().filter(|u| !u.is_empty()) {
            cmd.arg(username);
        }
        cmd.arg(password);
        Some(cmd)
    }
}

/// The owning `Client`'s current credentials plus a generation that advances
/// when the server has accepted a new pair. The lock is held only to copy or
/// edit the pair, never across an `.await`.
///
/// Readers compare the generation against a per-connection bookmark and
/// re-AUTH on mismatch, the way `ScopedConnection::last_auth_generation` tracks
/// `IAMTokenManager::token_generation`. The generation starts at 1, past
/// `AuthGeneration::NEVER`.
///
/// A pair the server has not accepted is stored without advancing the
/// generation (`store_password`): it is for connections built from now on,
/// and an existing connection must not re-AUTH with it, because the server
/// may not hold it yet and the failed re-AUTH would discard a connection whose
/// session is still valid.
///
/// No `Debug`: this holds the password.
pub(crate) struct CredentialsHandle {
    current: RwLock<PasswordCredentials>,
    generation: AtomicU64,
}

const LOCK_POISONED: &str = "credentials lock poisoned";

impl CredentialsHandle {
    pub(crate) fn new(credentials: PasswordCredentials) -> Self {
        Self {
            current: RwLock::new(credentials),
            generation: AtomicU64::new(1),
        }
    }

    /// Generation of the credentials a reader is about to take. Read this
    /// *before* `current()` and bookmark it: a write landing between the two
    /// leaves the bookmark stale, so the next compare re-AUTHs again rather than
    /// missing the rotation.
    pub(crate) fn generation(&self) -> AuthGeneration {
        AuthGeneration::from_source(self.generation.load(Ordering::Acquire))
    }

    pub(crate) fn current(&self) -> PasswordCredentials {
        self.current.read().expect(LOCK_POISONED).clone()
    }

    /// Apply one edit the server has already accepted and advance the
    /// generation once. A username and password that change together must land
    /// in the same write: published one field at a time, a reader between the
    /// two would AUTH with the new username and the old password and fail for a
    /// rotation that was valid.
    ///
    /// Always advances the generation, even for an unchanged value: one redundant
    /// AUTH on a live scope connection is cheaper than reasoning about which
    /// writes count as a rotation.
    pub(crate) fn update(&self, edit: impl FnOnce(&mut PasswordCredentials)) {
        edit(&mut self.current.write().expect(LOCK_POISONED));
        self.advance();
    }

    /// Record the password a reconnect will use, without advancing the
    /// generation (see the type doc); `StoredPassword::accepted` advances it.
    pub(crate) fn store_password(&self, password: Option<String>) -> StoredPassword<'_> {
        self.current.write().expect(LOCK_POISONED).password = password;
        StoredPassword { handle: self }
    }

    fn advance(&self) {
        self.generation.fetch_add(1, Ordering::AcqRel);
    }
}

/// Proof that `store_password` ran. Consumed by `accepted`, so one store is at
/// most one generation advance, and an advance without a store cannot be
/// written. Dropping it is the store-only update.
#[must_use = "dropping is the store-only update; call accepted() once the server took the password"]
pub(crate) struct StoredPassword<'a> {
    handle: &'a CredentialsHandle,
}

impl StoredPassword<'_> {
    /// The server has accepted the stored password: existing connections may now
    /// re-AUTH with it.
    pub(crate) fn accepted(self) {
        self.handle.advance();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn auth_command_shapes() {
        let args = |cmd: Option<redis::Cmd>| -> Option<Vec<String>> {
            cmd.map(|c| {
                c.args_iter()
                    .map(|a| match a {
                        redis::Arg::Simple(bytes) => String::from_utf8(bytes.to_vec()).unwrap(),
                        redis::Arg::Cursor => unreachable!(),
                    })
                    .collect()
            })
        };
        let no_password = PasswordCredentials {
            username: Some("u".into()),
            password: None,
        };
        assert_eq!(args(no_password.auth_command()), None);
        let empty_password = PasswordCredentials {
            username: None,
            password: Some(String::new()),
        };
        assert_eq!(args(empty_password.auth_command()), None);
        let default_user = PasswordCredentials {
            username: Some(String::new()),
            password: Some("p".into()),
        };
        assert_eq!(
            args(default_user.auth_command()),
            Some(vec!["AUTH".into(), "p".into()])
        );
        let named_user = PasswordCredentials {
            username: Some("u".into()),
            password: Some("p".into()),
        };
        assert_eq!(
            args(named_user.auth_command()),
            Some(vec!["AUTH".into(), "u".into(), "p".into()])
        );
    }

    #[test]
    fn store_password_keeps_the_username_and_the_generation() {
        let handle = CredentialsHandle::new(PasswordCredentials {
            username: Some("u".into()),
            password: Some("old".into()),
        });
        let _store_only = handle.store_password(Some("new".into()));
        let current = handle.current();
        assert_eq!(current.username.as_deref(), Some("u"));
        assert_eq!(current.password.as_deref(), Some("new"));
        assert_eq!(handle.generation(), AuthGeneration(1));
        let _store_only = handle.store_password(None);
        assert_eq!(handle.current().password, None);
        assert_eq!(handle.generation(), AuthGeneration(1));
    }

    #[test]
    fn accepted_advances_the_generation() {
        let handle = CredentialsHandle::new(PasswordCredentials::default());
        let stored = handle.store_password(Some("p".into()));
        assert_eq!(handle.generation(), AuthGeneration(1));
        stored.accepted();
        assert_eq!(handle.generation(), AuthGeneration(2));
        assert_eq!(handle.current().password.as_deref(), Some("p"));
    }

    #[test]
    fn generation_starts_at_one_and_advances_once_per_accepted_write() {
        let handle = CredentialsHandle::new(PasswordCredentials::default());
        assert_eq!(handle.generation(), AuthGeneration(1));
        assert_ne!(handle.generation(), AuthGeneration::NEVER);
        handle.store_password(Some("p".into())).accepted();
        assert_eq!(handle.generation(), AuthGeneration(2));
        handle.update(|credentials| credentials.password = Some("p".into()));
        assert_eq!(handle.generation(), AuthGeneration(3));
        handle.update(|credentials| credentials.password = Some("p".into()));
        assert_eq!(
            handle.generation(),
            AuthGeneration(4),
            "an unchanged value still counts as a rotation"
        );
        handle.update(|credentials| {
            credentials.username = Some("u".into());
            credentials.password = Some("q".into());
        });
        assert_eq!(
            handle.generation(),
            AuthGeneration(5),
            "a username+password edit is one generation"
        );
    }

    #[test]
    fn update_edits_both_fields() {
        let handle = CredentialsHandle::new(PasswordCredentials::default());
        handle.update(|credentials| {
            credentials.username = Some("u".into());
            credentials.password = Some("q".into());
        });
        let current = handle.current();
        assert_eq!(current.username.as_deref(), Some("u"));
        assert_eq!(current.password.as_deref(), Some("q"));
    }
}
