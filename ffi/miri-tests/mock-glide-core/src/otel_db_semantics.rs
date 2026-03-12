// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::Client;
use redis::Cmd;
use std::borrow::Borrow;
use telemetrylib::GlideSpan;

pub fn set_db_attributes(_span: &GlideSpan, _cmd: &Cmd, _client: &Client) {}

pub fn set_db_script_attributes(
    _span: &GlideSpan,
    _hash: &str,
    _keys: &[&[u8]],
    _args: &[&[u8]],
    _client: &Client,
) {
}

pub fn set_db_batch_attributes<T: Borrow<Cmd>>(
    _span: &GlideSpan,
    _cmds: &[T],
    _client: &Client,
) {
}
