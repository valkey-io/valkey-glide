// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The owning `Client`'s current static (non-IAM) credentials, readable by the
//! scope path.
//!
//! `Client::update_connection_password` and a user-issued `AUTH` push the new
//! value down into the standalone or cluster connection for reconnect, but that
//! storage has no read path. Scope connections are built outside those layers,
//! so they read from here instead: the same one-owner shape as the IAM token
//! manager and the certificate reload handle.

use std::sync::RwLock;

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

/// The owning `Client`'s current credentials. The lock is held only to copy or
/// edit the pair, never across an `.await`.
///
/// No `Debug`: this holds the password.
pub(crate) struct CredentialsHandle {
    current: RwLock<PasswordCredentials>,
}

const LOCK_POISONED: &str = "credentials lock poisoned";

impl CredentialsHandle {
    pub(crate) fn new(credentials: PasswordCredentials) -> Self {
        Self {
            current: RwLock::new(credentials),
        }
    }

    pub(crate) fn current(&self) -> PasswordCredentials {
        self.current.read().expect(LOCK_POISONED).clone()
    }

    /// Apply one edit to the credentials. A username and password that change
    /// together must land in the same write: published one field at a time, a
    /// reader between the two would AUTH with the new username and the old
    /// password and fail for a rotation that was valid.
    pub(crate) fn update(&self, edit: impl FnOnce(&mut PasswordCredentials)) {
        edit(&mut self.current.write().expect(LOCK_POISONED));
    }

    pub(crate) fn set_password(&self, password: Option<String>) {
        self.update(|credentials| credentials.password = password);
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
    fn set_password_keeps_the_username() {
        let handle = CredentialsHandle::new(PasswordCredentials {
            username: Some("u".into()),
            password: Some("old".into()),
        });
        handle.set_password(Some("new".into()));
        let current = handle.current();
        assert_eq!(current.username.as_deref(), Some("u"));
        assert_eq!(current.password.as_deref(), Some("new"));
        handle.set_password(None);
        assert_eq!(handle.current().password, None);
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
