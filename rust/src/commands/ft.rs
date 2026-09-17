// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Search module commands (`FT.*`). Mirrors Python's `ft` namespace.
//!
//! The query/schema/pipeline bodies of `FT.CREATE`, `FT.SEARCH`, `FT.AGGREGATE`
//! and `FT.PROFILE` have a very large option surface; these methods accept the
//! trailing arguments as a raw slice so the full command grammar is expressible
//! while keeping a typed entry point per command. Replies are returned as the raw
//! structured [`ValkeyValue`].

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// Search (RediSearch/valkey-search) module commands (`FT.CREATE`, `FT.SEARCH`,
/// `FT.AGGREGATE`, ...).
#[async_trait]
pub trait FtCommands: CommandExecutor {
    /// Create an index (`FT.CREATE`). `args` are the full definition following the
    /// index name, e.g. `["ON", "HASH", "PREFIX", "1", "doc:", "SCHEMA", "title",
    /// "TEXT"]`.
    async fn ft_create<I: ToValkeyArgs + Send, A: ToValkeyArgs + Send + Sync>(
        &self,
        index: I,
        args: &[A],
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.CREATE").arg(index);
        for a in args {
            cmd.arg(a);
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Drop an index (`FT.DROPINDEX`). Set `delete_docs` to also delete the
    /// indexed documents.
    async fn ft_dropindex<I: ToValkeyArgs + Send>(
        &self,
        index: I,
        delete_docs: bool,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.DROPINDEX").arg(index);
        if delete_docs {
            cmd.arg("DD");
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get information about an index (`FT.INFO`).
    async fn ft_info<I: ToValkeyArgs + Send>(&self, index: I) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.INFO").arg(index);
        self.execute_command(cmd, None).await
    }

    /// List all indexes (`FT._LIST`).
    async fn ft_list(&self) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("FT._LIST");
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Bytes::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Set(items) => items
                .into_iter()
                .map(Bytes::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
        }
    }

    /// Search an index (`FT.SEARCH`). `args` are the options following the query,
    /// e.g. `["LIMIT", "0", "10", "RETURN", "1", "title"]`.
    async fn ft_search<
        I: ToValkeyArgs + Send,
        Q: ToValkeyArgs + Send,
        A: ToValkeyArgs + Send + Sync,
    >(
        &self,
        index: I,
        query: Q,
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.SEARCH").arg(index).arg(query);
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Run an aggregation pipeline over an index (`FT.AGGREGATE`).
    async fn ft_aggregate<
        I: ToValkeyArgs + Send,
        Q: ToValkeyArgs + Send,
        A: ToValkeyArgs + Send + Sync,
    >(
        &self,
        index: I,
        query: Q,
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.AGGREGATE").arg(index).arg(query);
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Return the execution plan for a query (`FT.EXPLAIN`).
    async fn ft_explain<I: ToValkeyArgs + Send, Q: ToValkeyArgs + Send>(
        &self,
        index: I,
        query: Q,
    ) -> ValkeyResult<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.EXPLAIN").arg(index).arg(query);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Return the execution plan for a query in CLI form (`FT.EXPLAINCLI`).
    async fn ft_explaincli<I: ToValkeyArgs + Send, Q: ToValkeyArgs + Send>(
        &self,
        index: I,
        query: Q,
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.EXPLAINCLI").arg(index).arg(query);
        self.execute_command(cmd, None).await
    }

    /// Add an alias for an index (`FT.ALIASADD`).
    async fn ft_aliasadd<A: ToValkeyArgs + Send, I: ToValkeyArgs + Send>(
        &self,
        alias: A,
        index: I,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.ALIASADD").arg(alias).arg(index);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Remove an index alias (`FT.ALIASDEL`).
    async fn ft_aliasdel<A: ToValkeyArgs + Send>(&self, alias: A) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.ALIASDEL").arg(alias);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Reassign an alias to a different index (`FT.ALIASUPDATE`).
    async fn ft_aliasupdate<A: ToValkeyArgs + Send, I: ToValkeyArgs + Send>(
        &self,
        alias: A,
        index: I,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.ALIASUPDATE").arg(alias).arg(index);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// List all index aliases (`FT._ALIASLIST`).
    async fn ft_aliaslist(&self) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT._ALIASLIST");
        self.execute_command(cmd, None).await
    }

    /// Profile the execution of a `SEARCH` or `AGGREGATE` query (`FT.PROFILE`).
    /// `query_type` is `"SEARCH"` or `"AGGREGATE"`; `args` are the query and its
    /// options (typically prefixed with `QUERY`).
    async fn ft_profile<I: ToValkeyArgs + Send, A: ToValkeyArgs + Send + Sync>(
        &self,
        index: I,
        query_type: &str,
        limited: bool,
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FT.PROFILE").arg(index).arg(query_type);
        if limited {
            cmd.arg("LIMITED");
        }
        cmd.arg("QUERY");
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }
}

impl<T: CommandExecutor + ?Sized> FtCommands for T {}
