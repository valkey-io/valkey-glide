// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's cursor scan iterators, returned by the `scan*` methods of
//! [`AsyncCommands`] / `Commands`. See [`ScanIter`] (async) and
//! `SyncScanIter`.

use crate::commands::core::AsyncCommands;
use redis::{Cmd, FromRedisValue, RedisResult, from_owned_redis_value};

#[cfg(feature = "sync")]
use crate::commands::core::Commands;

/// Argument layout of one scan page: `prefix… <cursor> suffix…`
/// (e.g. `HSCAN key <cursor> MATCH pattern`).
#[derive(Debug)]
struct PageSpec {
    prefix: Vec<Vec<u8>>,
    suffix: Vec<Vec<u8>>,
}

impl PageSpec {
    fn to_cmd(&self, cursor: u64) -> Cmd {
        let mut cmd = Cmd::new();
        for a in &self.prefix {
            cmd.arg(&a[..]);
        }
        cmd.arg(cursor);
        for a in &self.suffix {
            cmd.arg(&a[..]);
        }
        cmd
    }
}

/// An in-progress async scan iteration.
///
/// ```rust,no_run
/// # use glide::AsyncCommands;
/// # async fn demo(client: glide::GlideClient) -> glide::RedisResult<()> {
/// let mut iter = client.scan_match::<_, String>("prefix:*").await?;
/// while let Some(key) = iter.next_item().await {
///     println!("{}", key?);
/// }
/// # Ok(()) }
/// ```
pub struct ScanIter<'a, C: ?Sized, RV> {
    con: &'a C,
    spec: PageSpec,
    cursor: u64,
    batch: std::vec::IntoIter<RV>,
}

impl<'a, C: AsyncCommands, RV: FromRedisValue> ScanIter<'a, C, RV> {
    /// Start iteration
    pub(crate) async fn new(
        con: &'a C,
        prefix: Vec<Vec<u8>>,
        suffix: Vec<Vec<u8>>,
    ) -> RedisResult<ScanIter<'a, C, RV>> {
        let spec = PageSpec { prefix, suffix };

        // Fetch first page immediately.
        let (cursor, batch): (u64, Vec<RV>) =
            from_owned_redis_value(con.glide_send_owned(spec.to_cmd(0)).await?)?;
        Ok(ScanIter {
            con,
            spec,
            cursor,
            batch: batch.into_iter(),
        })
    }

    /// The next element, an error if fetching a page fails,
    /// or `None` if the scan completed. An error ends the iteration:
    /// subsequent call returns `None`.
    pub async fn next_item(&mut self) -> Option<RedisResult<RV>> {
        // Page may be empty, so keep fetching until an
        // item is produced or the cursor wraps to 0.
        loop {
            if let Some(v) = self.batch.next() {
                return Some(Ok(v));
            }
            if self.cursor == 0 {
                return None;
            }
            match self.fetch_page().await {
                Ok(()) => {}
                Err(e) => {
                    // Never retry a failed page.
                    self.cursor = 0;
                    return Some(Err(e));
                }
            }
        }
    }

    /// Fetch the page at the current cursor.
    async fn fetch_page(&mut self) -> RedisResult<()> {
        let reply = self
            .con
            .glide_send_owned(self.spec.to_cmd(self.cursor))
            .await?;
        let (cursor, batch): (u64, Vec<RV>) = from_owned_redis_value(reply)?;
        self.cursor = cursor;
        self.batch = batch.into_iter();
        Ok(())
    }
}

/// An in-progress blocking scan iteration.
///
/// ```rust,no_run
/// # use glide::Commands;
/// # use glide::sync::SyncGlideClient;
/// # fn demo(client: SyncGlideClient) -> glide::RedisResult<()> {
/// for key in client.scan_match::<_, String>("prefix:*")? {
///     println!("{}", key?);
/// }
/// # Ok(()) }
/// ```
#[cfg(feature = "sync")]
pub struct SyncScanIter<'a, C: ?Sized, RV> {
    con: &'a C,
    spec: PageSpec,
    cursor: u64,
    batch: std::vec::IntoIter<RV>,
}

#[cfg(feature = "sync")]
impl<'a, C: Commands, RV: FromRedisValue> SyncScanIter<'a, C, RV> {
    /// Start iteration
    pub(crate) fn new(
        con: &'a C,
        prefix: Vec<Vec<u8>>,
        suffix: Vec<Vec<u8>>,
    ) -> RedisResult<SyncScanIter<'a, C, RV>> {
        let spec = PageSpec { prefix, suffix };

        // Fetch first page immediately.
        let (cursor, batch): (u64, Vec<RV>) =
            from_owned_redis_value(con.glide_send_owned_sync(spec.to_cmd(0))?)?;
        Ok(SyncScanIter {
            con,
            spec,
            cursor,
            batch: batch.into_iter(),
        })
    }

    /// Fetch the page at the current cursor.
    fn fetch_page(&mut self) -> RedisResult<()> {
        let reply = self
            .con
            .glide_send_owned_sync(self.spec.to_cmd(self.cursor))?;
        let (cursor, batch): (u64, Vec<RV>) = from_owned_redis_value(reply)?;
        self.cursor = cursor;
        self.batch = batch.into_iter();
        Ok(())
    }
}

#[cfg(feature = "sync")]
impl<C: Commands, RV: FromRedisValue> Iterator for SyncScanIter<'_, C, RV> {
    type Item = RedisResult<RV>;

    /// The next element, an error if fetching a page fails,
    /// or `None` if the scan completed. An error ends the iteration:
    /// subsequent call returns `None`.
    fn next(&mut self) -> Option<RedisResult<RV>> {
        // Page may be empty, so keep fetching until an
        // item is produced or the cursor wraps to 0.
        loop {
            if let Some(v) = self.batch.next() {
                return Some(Ok(v));
            }
            if self.cursor == 0 {
                return None;
            }
            match self.fetch_page() {
                Ok(()) => {}
                Err(e) => {
                    // Never retry a failed page.
                    self.cursor = 0;
                    return Some(Err(e));
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use redis::{ErrorKind, RedisError, RedisFuture, Value};
    use std::collections::VecDeque;
    use std::sync::Mutex;

    /// A mock connection that returns queued replies in order.
    struct MockConnection {
        replies: Mutex<VecDeque<RedisResult<Value>>>,
    }

    impl MockConnection {
        fn new(replies: Vec<RedisResult<Value>>) -> Self {
            MockConnection {
                replies: Mutex::new(replies.into()),
            }
        }

        fn pop(&self) -> RedisResult<Value> {
            self.replies
                .lock()
                .unwrap()
                .pop_front()
                .expect("scan iterator requested more pages than the test queued")
        }
    }

    impl crate::commands::core::AsyncCommands for MockConnection {
        fn glide_send_owned<'a>(&'a self, _cmd: Cmd) -> RedisFuture<'a, Value> {
            Box::pin(async move { self.pop() })
        }
    }

    #[cfg(feature = "sync")]
    impl crate::commands::core::Commands for MockConnection {
        fn glide_send_owned_sync(&self, _cmd: Cmd) -> RedisResult<Value> {
            self.pop()
        }
    }

    /// Creates a scan page reply.
    fn page(cursor: &str, items: &[&str]) -> Value {
        Value::Array(vec![
            Value::BulkString(cursor.as_bytes().to_vec().into()),
            Value::Array(
                items
                    .iter()
                    .map(|s| Value::BulkString(s.as_bytes().to_vec().into()))
                    .collect(),
            ),
        ])
    }

    /// Creates a scan error.
    fn error() -> RedisError {
        RedisError::from((ErrorKind::IoError, "simulated scan failure"))
    }

    // ---- async (`ScanIter::next_item`) ------------------------------------------

    /// Creates an async scan iterator over the mock connection.
    async fn scan_iter(con: &MockConnection) -> RedisResult<ScanIter<'_, MockConnection, String>> {
        ScanIter::new(con, vec![b"SCAN".to_vec()], Vec::new()).await
    }

    /// Returns all the items from an async scan iterator.
    async fn get_items(iter: &mut ScanIter<'_, MockConnection, String>) -> Vec<String> {
        let mut items = Vec::new();
        while let Some(item) = iter.next_item().await {
            items.push(item.expect("unexpected error during scan"));
        }
        items
    }

    #[tokio::test]
    async fn async_success_empty_page() {
        let con = MockConnection::new(vec![Ok(page("4", &[])), Ok(page("0", &[]))]);
        let mut iter = scan_iter(&con).await.unwrap();
        assert_eq!(get_items(&mut iter).await, Vec::<String>::new());
    }

    #[tokio::test]
    async fn async_success_one_page() {
        let con = MockConnection::new(vec![Ok(page("0", &["a", "b"]))]);
        let mut iter = scan_iter(&con).await.unwrap();
        assert_eq!(get_items(&mut iter).await, ["a", "b"]);
    }

    #[tokio::test]
    async fn async_success_multi_page() {
        let con = MockConnection::new(vec![Ok(page("6", &["a", "b"])), Ok(page("0", &["c"]))]);
        let mut iter = scan_iter(&con).await.unwrap();
        assert_eq!(get_items(&mut iter).await, ["a", "b", "c"]);
    }

    #[tokio::test]
    async fn async_error_new() {
        let con = MockConnection::new(vec![Err(error())]);
        assert!(scan_iter(&con).await.is_err());
    }

    #[tokio::test]
    async fn async_error_scan() {
        let con = MockConnection::new(vec![Ok(page("5", &["a"])), Err(error())]);
        let mut iter = scan_iter(&con).await.unwrap();
        assert_eq!(iter.next_item().await.unwrap().unwrap(), "a");
        assert!(iter.next_item().await.expect("expected an error").is_err());
        assert!(iter.next_item().await.is_none());
    }

    // ---- blocking (`SyncScanIter` / `Iterator`) ---------------------------------

    /// Creates a sync scan iterator over the mock connection.
    #[cfg(feature = "sync")]
    fn sync_scan_iter(
        con: &MockConnection,
    ) -> RedisResult<SyncScanIter<'_, MockConnection, String>> {
        SyncScanIter::new(con, vec![b"SCAN".to_vec()], Vec::new())
    }

    /// Returns all the items from an sync scan iterator.
    #[cfg(feature = "sync")]
    fn sync_get_items(iter: SyncScanIter<'_, MockConnection, String>) -> Vec<String> {
        iter.collect::<RedisResult<Vec<String>>>()
            .expect("unexpected error during scan")
    }

    #[cfg(feature = "sync")]
    #[test]
    fn sync_success_empty_page() {
        let con = MockConnection::new(vec![Ok(page("4", &[])), Ok(page("0", &[]))]);
        assert_eq!(
            sync_get_items(sync_scan_iter(&con).unwrap()),
            Vec::<String>::new()
        );
    }

    #[cfg(feature = "sync")]
    #[test]
    fn sync_success_one_page() {
        let con = MockConnection::new(vec![Ok(page("0", &["a", "b"]))]);
        assert_eq!(sync_get_items(sync_scan_iter(&con).unwrap()), ["a", "b"]);
    }

    #[cfg(feature = "sync")]
    #[test]
    fn sync_success_multi_page() {
        let con = MockConnection::new(vec![Ok(page("6", &["a", "b"])), Ok(page("0", &["c"]))]);
        assert_eq!(
            sync_get_items(sync_scan_iter(&con).unwrap()),
            ["a", "b", "c"]
        );
    }

    #[cfg(feature = "sync")]
    #[test]
    fn sync_error_new() {
        let con = MockConnection::new(vec![Err(error())]);
        assert!(sync_scan_iter(&con).is_err());
    }

    #[cfg(feature = "sync")]
    #[test]
    fn sync_error_scan() {
        let con = MockConnection::new(vec![Ok(page("5", &["a"])), Err(error())]);
        let mut iter = sync_scan_iter(&con).unwrap();
        assert_eq!(iter.next().unwrap().unwrap(), "a");
        assert!(iter.next().expect("expected an error").is_err());
        assert!(iter.next().is_none());
    }
}
