// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Server-free tests for every command family (no Valkey server needed).
//!
//! Each command method builds a `Cmd` and dispatches it through the
//! [`CommandExecutor`] seam. These tests install an in-process [`Mock`] executor
//! that (a) captures the exact command tokens the wrapper produced — verifying
//! request *encoding* — and (b) returns a preconfigured `Value` so the
//! method's response *decoding* into its typed return can be asserted. No Valkey
//! server is involved, so the whole suite is deterministic and fast.
//!
//! TODO #7024: these peek at the built command's bytes (`Cmd::args`), so they are
//! really unit tests. Move them in-crate (`#[cfg(test)]` under `src/`) so they can
//! use `pub(crate)` internals and `Cmd::args`'s `#[doc(hidden)] pub` accessor can
//! be dropped. See rust/api-audit/phase-1-open-items.md.

use async_trait::async_trait;
use glide::Cmd;
use glide::Route;
use glide::executor::CommandExecutor;
use glide::{ValkeyResult, ValkeyValue};
use std::sync::Mutex;

/// A captured command: the raw argument tokens plus the route it was sent with.
type CapturedCommand = (Vec<Vec<u8>>, Option<Route>);

/// A deterministic, server-free `CommandExecutor` used by the family tests.
pub(crate) struct Mock {
    response: Mutex<ValkeyValue>,
    captured: Mutex<Option<CapturedCommand>>,
}

impl Mock {
    /// Build a mock that replies with `response`.
    pub(crate) fn new(response: ValkeyValue) -> Self {
        Mock {
            response: Mutex::new(response),
            captured: Mutex::new(None),
        }
    }

    /// Reply with `+OK`.
    pub(crate) fn ok() -> Self {
        Mock::new(ValkeyValue::Okay)
    }
    /// Reply with an integer.
    pub(crate) fn int(n: i64) -> Self {
        Mock::new(ValkeyValue::Int(n))
    }
    /// Reply with a bulk string.
    pub(crate) fn bulk(s: impl AsRef<[u8]>) -> Self {
        Mock::new(ValkeyValue::BulkString(s.as_ref().to_vec().into()))
    }
    /// Reply with a simple string.
    pub(crate) fn simple(s: &str) -> Self {
        Mock::new(ValkeyValue::SimpleString(s.to_string()))
    }
    /// Reply with nil.
    pub(crate) fn nil() -> Self {
        Mock::new(ValkeyValue::Nil)
    }
    /// Reply with an array.
    pub(crate) fn array(items: Vec<ValkeyValue>) -> Self {
        Mock::new(ValkeyValue::Array(items))
    }

    /// The captured command tokens, decoded lossily to UTF-8 strings.
    pub(crate) fn args(&self) -> Vec<String> {
        self.captured
            .lock()
            .unwrap()
            .as_ref()
            .expect("no command was captured")
            .0
            .iter()
            .map(|a| String::from_utf8_lossy(a).into_owned())
            .collect()
    }

    /// Assert the exact command tokens the wrapper produced.
    pub(crate) fn assert_args(&self, expected: &[&str]) {
        let got = self.args();
        let exp: Vec<String> = expected.iter().map(|s| s.to_string()).collect();
        assert_eq!(got, exp, "command encoding mismatch");
    }

    /// The route the executor was handed (cluster paths). Consumes it.
    pub(crate) fn routing(&self) -> Option<Route> {
        self.captured
            .lock()
            .unwrap()
            .as_mut()
            .expect("no command was captured")
            .1
            .take()
    }
}

#[async_trait]
impl CommandExecutor for Mock {
    async fn execute_command(&self, cmd: Cmd, route: Option<Route>) -> ValkeyResult<ValkeyValue> {
        let args: Vec<Vec<u8>> = cmd.args().into_iter().map(|s| s.to_vec()).collect();
        *self.captured.lock().unwrap() = Some((args, route));
        Ok(self.response.lock().unwrap().clone())
    }
}

mod bitmap;
mod connection_management;
mod ft;
mod generic;
mod geo;
mod hash;
mod json;
mod pubsub;
mod scripting;
mod server_management;
mod set;
mod sorted_set;
mod stream;
mod string;
