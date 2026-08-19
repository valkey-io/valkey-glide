// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Server-free tests for every command family (no Valkey server needed).
//!
//! Each command method builds a `redis::Cmd` and dispatches it through the
//! [`CommandExecutor`] seam. These tests install an in-process [`Mock`] executor
//! that (a) captures the exact command tokens the wrapper produced — verifying
//! request *encoding* — and (b) returns a preconfigured `redis::Value` so the
//! method's response *decoding* into its typed return can be asserted. No Valkey
//! server is involved, so the whole suite is deterministic and fast.

use async_trait::async_trait;
use glide::error::Result;
use glide::executor::CommandExecutor;
use redis::cluster_routing::RoutingInfo;
use redis::{Arg, Cmd, Value};
use std::sync::Mutex;

/// A captured command: the raw argument tokens plus the routing it was sent with.
type CapturedCommand = (Vec<Vec<u8>>, Option<RoutingInfo>);

/// A deterministic, server-free `CommandExecutor` used by the family tests.
pub(crate) struct Mock {
    response: Mutex<Value>,
    captured: Mutex<Option<CapturedCommand>>,
}

impl Mock {
    /// Build a mock that replies with `response`.
    pub(crate) fn new(response: Value) -> Self {
        Mock {
            response: Mutex::new(response),
            captured: Mutex::new(None),
        }
    }

    /// Reply with `+OK`.
    pub(crate) fn ok() -> Self {
        Mock::new(Value::Okay)
    }
    /// Reply with an integer.
    pub(crate) fn int(n: i64) -> Self {
        Mock::new(Value::Int(n))
    }
    /// Reply with a bulk string.
    pub(crate) fn bulk(s: impl AsRef<[u8]>) -> Self {
        Mock::new(Value::BulkString(s.as_ref().to_vec()))
    }
    /// Reply with a simple string.
    pub(crate) fn simple(s: &str) -> Self {
        Mock::new(Value::SimpleString(s.to_string()))
    }
    /// Reply with nil.
    pub(crate) fn nil() -> Self {
        Mock::new(Value::Nil)
    }
    /// Reply with an array.
    pub(crate) fn array(items: Vec<Value>) -> Self {
        Mock::new(Value::Array(items))
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

    /// The routing the executor was handed (cluster paths). Consumes it.
    pub(crate) fn routing(&self) -> Option<RoutingInfo> {
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
    async fn execute_command(&self, cmd: Cmd, routing: Option<RoutingInfo>) -> Result<Value> {
        let args: Vec<Vec<u8>> = cmd
            .args_iter()
            .map(|a| match a {
                Arg::Simple(s) => s.to_vec(),
                Arg::Cursor => b"0".to_vec(),
            })
            .collect();
        *self.captured.lock().unwrap() = Some((args, routing));
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
