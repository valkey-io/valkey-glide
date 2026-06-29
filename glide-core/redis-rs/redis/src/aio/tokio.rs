use super::{AsyncStream, RedisResult, RedisRuntime, SocketAddr};
use async_trait::async_trait;
#[allow(unused_imports)] // fixes "Duration" unused when built with non-default feature set
use std::{
    future::Future,
    io,
    pin::Pin,
    task::{self, Poll},
    time::Duration,
};
#[cfg(unix)]
use tokio::net::UnixStream as UnixStreamTokio;
use tokio::{
    io::{AsyncRead, AsyncWrite, ReadBuf},
    net::TcpStream as TcpStreamTokio,
};

use crate::connection::create_rustls_config;
use crate::tls::TlsConnParams;
use crate::{ErrorKind, RedisError};
use std::sync::{Arc, RwLock};
use tokio_rustls::{client::TlsStream, TlsConnector};

/// Cached TLS configurations to avoid repeatedly loading system certificates.
/// On Linux, `load_native_certs()` parses ~300KB of cert files, adding ~6ms
/// per connection when run for every connection.
///
/// Wrapped in `RwLock<Option<…>>` so the entry can be invalidated when the
/// system trust store changes (e.g. CA rotation). Only successful configs
/// are cached; a config-build failure propagates to the caller without being
/// stored, so a transient failure isn't pinned for the rest of the process.
/// The connect-time cert-error retry path doesn't re-run config build, so
/// caching `Err` here would be unrecoverable until process restart.
type TlsConfigCache = RwLock<Option<Arc<rustls::ClientConfig>>>;

static TLS_CONFIG_DEFAULT: TlsConfigCache = RwLock::new(None);
static TLS_CONFIG_INSECURE: TlsConfigCache = RwLock::new(None);

/// Drop the cached config only if it still holds `expected`.
///
/// If a concurrent peer has already replaced the cache with a fresh `Arc`
/// (staggered storm during cert rotation), this is a no-op so we don't
/// clobber the peer's refresh and trigger another `load_native_certs()`.
fn invalidate_if_stale(cache: &'static TlsConfigCache, expected: &Arc<rustls::ClientConfig>) {
    if let Ok(mut guard) = cache.write() {
        if matches!(guard.as_ref(), Some(cached) if Arc::ptr_eq(cached, expected)) {
            *guard = None;
        }
    }
}

/// Check if an I/O error is a TLS certificate verification failure.
/// Uses typed downcasting to avoid fragile string matching.
fn is_tls_cert_error(err: &io::Error) -> bool {
    err.get_ref()
        .and_then(|e| e.downcast_ref::<rustls::Error>())
        .is_some_and(|e| matches!(e, rustls::Error::InvalidCertificate(_)))
}

/// Retrieve or initialize a cached TLS configuration.
///
/// Errors from `create_rustls_config` are returned directly via `?` and are
/// **not** cached, so a transient failure can be retried by the next caller.
fn get_cached_tls_config(
    cache: &'static TlsConfigCache,
    insecure: bool,
    tls_params: &Option<TlsConnParams>,
) -> RedisResult<Arc<rustls::ClientConfig>> {
    // Fast path: read lock
    if let Ok(guard) = cache.read() {
        if let Some(cached) = guard.as_ref() {
            return Ok(cached.clone());
        }
    }

    // Slow path: write lock, initialize
    let mut guard = cache.write().map_err(|_| {
        RedisError::from((
            ErrorKind::IoError,
            "TLS config cache lock poisoned",
            String::new(),
        ))
    })?;

    // Double-check after acquiring write lock
    if let Some(cached) = guard.as_ref() {
        return Ok(cached.clone());
    }

    // Build, then cache only on success — `?` propagates the original error
    // (with its original `ErrorKind`) without storing it.
    let config = Arc::new(create_rustls_config(insecure, tls_params.clone())?);
    *guard = Some(config.clone());
    Ok(config)
}

/// Execute a TLS connection attempt with optional retry on certificate errors.
///
/// If the first attempt fails with a certificate verification error and
/// `has_custom_params` is false, conditionally invalidates the cached TLS
/// config (only if it still matches the one we used) and retries exactly
/// once. This is a best-effort recovery for cases where system certificates
/// were updated (e.g., CA rotation) while the process was running. Recovery
/// is not guaranteed under adversarial concurrency or for non-rotation cert
/// errors (clock skew, intermediate-CA expiry, etc.); in those cases the
/// retried attempt may surface the same error to the caller.
///
/// Compare-and-invalidate via `Arc::ptr_eq` keeps a concurrent peer's fresh
/// config in place, so stragglers do not trigger redundant
/// `load_native_certs()` reloads.
async fn tls_connect_with_cert_retry<T, F, Fut>(
    connect: F,
    has_custom_params: bool,
    insecure: bool,
    tls_params: &Option<TlsConnParams>,
) -> RedisResult<T>
where
    F: Fn(Arc<rustls::ClientConfig>) -> Fut,
    Fut: Future<Output = io::Result<T>>,
{
    let cache: &'static TlsConfigCache = if insecure {
        &TLS_CONFIG_INSECURE
    } else {
        &TLS_CONFIG_DEFAULT
    };

    let config = if has_custom_params {
        Arc::new(create_rustls_config(insecure, tls_params.clone())?)
    } else {
        get_cached_tls_config(cache, insecure, tls_params)?
    };

    // Keep our own clone of the Arc so we can compare it against the cache
    // contents after the connect attempt completes.
    let result = connect(config.clone()).await;

    match result {
        Ok(val) => Ok(val),
        Err(err) if !has_custom_params && is_tls_cert_error(&err) => {
            // Compare-and-invalidate: drop the cache only if it still holds
            // the Arc we used. If a concurrent peer already refreshed it
            // (staggered storm during cert rotation), leave their fresh
            // config in place — `get_cached_tls_config` below will pick it
            // up via the read fast path and skip the file I/O.
            invalidate_if_stale(cache, &config);
            Ok(connect(get_cached_tls_config(cache, insecure, tls_params)?).await?)
        }
        Err(err) => Err(err.into()),
    }
}

#[cfg(unix)]
use super::Path;

#[inline(always)]
async fn connect_tcp(addr: &SocketAddr, tcp_nodelay: bool) -> io::Result<TcpStreamTokio> {
    let socket = TcpStreamTokio::connect(addr).await?;
    socket.set_nodelay(tcp_nodelay)?;
    #[cfg(feature = "keep-alive")]
    {
        //For now rely on system defaults
        const KEEP_ALIVE: socket2::TcpKeepalive = socket2::TcpKeepalive::new();
        //these are useless error that not going to happen
        let std_socket = socket.into_std()?;
        let socket2: socket2::Socket = std_socket.into();
        socket2.set_tcp_keepalive(&KEEP_ALIVE)?;
        // TCP_USER_TIMEOUT configuration isn't supported across all operation systems
        #[cfg(any(target_os = "android", target_os = "fuchsia", target_os = "linux"))]
        {
            // TODO: Replace this hardcoded timeout with a configurable timeout when https://github.com/redis-rs/redis-rs/issues/1147 is resolved
            const DFEAULT_USER_TCP_TIMEOUT: Duration = Duration::from_secs(5);
            socket2.set_tcp_user_timeout(Some(DFEAULT_USER_TCP_TIMEOUT))?;
        }
        TcpStreamTokio::from_std(socket2.into())
    }

    #[cfg(not(feature = "keep-alive"))]
    {
        Ok(socket)
    }
}

pub(crate) enum Tokio {
    /// Represents a Tokio TCP connection.
    Tcp(TcpStreamTokio),
    /// Represents a Tokio TLS encrypted TCP connection
    TcpTls(Box<TlsStream<TcpStreamTokio>>),
    /// Represents a Tokio Unix connection.
    #[cfg(unix)]
    Unix(UnixStreamTokio),
}

impl AsyncWrite for Tokio {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
        buf: &[u8],
    ) -> Poll<io::Result<usize>> {
        match &mut *self {
            Tokio::Tcp(r) => Pin::new(r).poll_write(cx, buf),
            Tokio::TcpTls(r) => Pin::new(r).poll_write(cx, buf),
            #[cfg(unix)]
            Tokio::Unix(r) => Pin::new(r).poll_write(cx, buf),
        }
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<io::Result<()>> {
        match &mut *self {
            Tokio::Tcp(r) => Pin::new(r).poll_flush(cx),
            Tokio::TcpTls(r) => Pin::new(r).poll_flush(cx),
            #[cfg(unix)]
            Tokio::Unix(r) => Pin::new(r).poll_flush(cx),
        }
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<io::Result<()>> {
        match &mut *self {
            Tokio::Tcp(r) => Pin::new(r).poll_shutdown(cx),
            Tokio::TcpTls(r) => Pin::new(r).poll_shutdown(cx),
            #[cfg(unix)]
            Tokio::Unix(r) => Pin::new(r).poll_shutdown(cx),
        }
    }
}

impl AsyncRead for Tokio {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        match &mut *self {
            Tokio::Tcp(r) => Pin::new(r).poll_read(cx, buf),
            Tokio::TcpTls(r) => Pin::new(r).poll_read(cx, buf),
            #[cfg(unix)]
            Tokio::Unix(r) => Pin::new(r).poll_read(cx, buf),
        }
    }
}

#[async_trait]
impl RedisRuntime for Tokio {
    async fn connect_tcp(socket_addr: SocketAddr, tcp_nodelay: bool) -> RedisResult<Self> {
        Ok(connect_tcp(&socket_addr, tcp_nodelay)
            .await
            .map(Tokio::Tcp)?)
    }

    async fn connect_tcp_tls(
        hostname: &str,
        socket_addr: SocketAddr,
        insecure: bool,
        tls_params: &Option<TlsConnParams>,
        tcp_nodelay: bool,
    ) -> RedisResult<Self> {
        let has_custom_params = tls_params
            .as_ref()
            .is_some_and(|p| p.client_tls_params.is_some() || p.root_cert_store.is_some());

        let hostname_owned = hostname.to_owned();
        let conn = tls_connect_with_cert_retry(
            |config| {
                let hostname = hostname_owned.clone();
                async move {
                    let tls_connector = TlsConnector::from(config);
                    tls_connector
                        .connect(
                            rustls_pki_types::ServerName::try_from(hostname.as_str())
                                .map_err(|e| io::Error::new(io::ErrorKind::InvalidInput, e))?
                                .to_owned(),
                            connect_tcp(&socket_addr, tcp_nodelay).await?,
                        )
                        .await
                }
            },
            has_custom_params,
            insecure,
            tls_params,
        )
        .await?;

        Ok(Tokio::TcpTls(Box::new(conn)))
    }

    #[cfg(unix)]
    async fn connect_unix(path: &Path) -> RedisResult<Self> {
        Ok(UnixStreamTokio::connect(path).await.map(Tokio::Unix)?)
    }

    #[cfg(feature = "tokio-comp")]
    fn spawn(f: impl Future<Output = ()> + Send + 'static) {
        tokio::spawn(f);
    }

    #[cfg(not(feature = "tokio-comp"))]
    fn spawn(_: impl Future<Output = ()> + Send + 'static) {
        unreachable!()
    }

    fn boxed(self) -> Pin<Box<dyn AsyncStream + Send + Sync>> {
        match self {
            Tokio::Tcp(x) => Box::pin(x),
            Tokio::TcpTls(x) => Box::pin(x),
            #[cfg(unix)]
            Tokio::Unix(x) => Box::pin(x),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serial_test::serial;
    use std::sync::atomic::{AtomicUsize, Ordering};

    /// Test fixture: clear both caches so each `#[serial]` test starts from
    /// a known state. Not part of the production API.
    fn reset_tls_caches() {
        *TLS_CONFIG_DEFAULT.write().unwrap() = None;
        *TLS_CONFIG_INSECURE.write().unwrap() = None;
    }

    // --- is_tls_cert_error tests ---

    #[test]
    fn test_is_tls_cert_error_detects_invalid_certificate() {
        let rustls_err = rustls::Error::InvalidCertificate(rustls::CertificateError::UnknownIssuer);
        let io_err = io::Error::new(io::ErrorKind::InvalidData, rustls_err);
        assert!(is_tls_cert_error(&io_err));
    }

    #[test]
    fn test_is_tls_cert_error_ignores_non_cert_errors() {
        let io_err = io::Error::new(io::ErrorKind::ConnectionRefused, "connection refused");
        assert!(!is_tls_cert_error(&io_err));

        let io_err = io::Error::new(io::ErrorKind::TimedOut, "timed out");
        assert!(!is_tls_cert_error(&io_err));
    }

    #[test]
    fn test_is_tls_cert_error_ignores_other_rustls_errors() {
        let rustls_err = rustls::Error::General("some other error".into());
        let io_err = io::Error::new(io::ErrorKind::Other, rustls_err);
        assert!(!is_tls_cert_error(&io_err));
    }

    // --- get_cached_tls_config tests ---

    #[test]
    #[serial]
    fn test_cached_config_returns_same_arc() {
        reset_tls_caches();
        let first = get_cached_tls_config(&TLS_CONFIG_DEFAULT, false, &None).unwrap();
        let second = get_cached_tls_config(&TLS_CONFIG_DEFAULT, false, &None).unwrap();

        assert!(
            Arc::ptr_eq(&first, &second),
            "second call must return the same Arc the first call cached \
             — this is the performance invariant the cache exists for"
        );
        reset_tls_caches();
    }

    // Note: a test that exercises the `create_rustls_config` failure path
    // (e.g. insecure mode without the `tls-rustls-insecure` feature) was
    // considered, but is unreachable in any currently-buildable config:
    //
    //   * With the `tls-rustls-insecure` feature on (the default), insecure
    //     mode succeeds and the failure branch isn't hit.
    //   * With it off, `connection.rs::create_rustls_config` itself fails
    //     to compile due to a pre-existing non-exhaustive-match bug
    //     unrelated to this PR.
    //
    // After the cache-only-Ok change, the properties such a test would
    // guard (correct `ErrorKind` propagation and no error caching) are
    // structurally guaranteed: errors flow through `?` and never enter
    // the cache.

    // --- invalidate_if_stale tests ---

    #[test]
    #[serial]
    fn test_invalidate_if_stale_wipes_when_arc_matches() {
        reset_tls_caches();
        let arc = get_cached_tls_config(&TLS_CONFIG_DEFAULT, false, &None).unwrap();

        invalidate_if_stale(&TLS_CONFIG_DEFAULT, &arc);

        assert!(
            TLS_CONFIG_DEFAULT.read().unwrap().is_none(),
            "cache should be wiped when its Arc matches `expected`"
        );
        reset_tls_caches();
    }

    #[test]
    #[serial]
    fn test_invalidate_if_stale_preserves_peer_refresh() {
        reset_tls_caches();
        let stale = get_cached_tls_config(&TLS_CONFIG_DEFAULT, false, &None).unwrap();

        // Simulate a concurrent peer's refresh by swapping in a fresh Arc.
        let fresh = Arc::new(create_rustls_config(false, None).unwrap());
        *TLS_CONFIG_DEFAULT.write().unwrap() = Some(fresh.clone());

        // Straggler comes in with the old `stale` Arc — should NOT wipe.
        invalidate_if_stale(&TLS_CONFIG_DEFAULT, &stale);

        let guard = TLS_CONFIG_DEFAULT.read().unwrap();
        let still_cached = guard.as_ref().expect("cache should still be populated");
        assert!(
            Arc::ptr_eq(still_cached, &fresh),
            "peer's refresh must be preserved"
        );
        drop(guard);
        reset_tls_caches();
    }

    #[test]
    #[serial]
    fn test_invalidate_if_stale_is_noop_on_empty_cache() {
        reset_tls_caches();
        let arc = Arc::new(create_rustls_config(false, None).unwrap());

        invalidate_if_stale(&TLS_CONFIG_DEFAULT, &arc);

        assert!(
            TLS_CONFIG_DEFAULT.read().unwrap().is_none(),
            "empty cache must remain empty"
        );
    }

    // --- Retry semantics tests (exercises tls_connect_with_cert_retry directly) ---

    fn make_cert_error() -> io::Error {
        io::Error::new(
            io::ErrorKind::InvalidData,
            rustls::Error::InvalidCertificate(rustls::CertificateError::UnknownIssuer),
        )
    }

    fn make_tcp_error() -> io::Error {
        io::Error::new(io::ErrorKind::ConnectionRefused, "connection refused")
    }

    #[tokio::test]
    #[serial]
    async fn test_retry_on_cert_error_calls_connect_twice() {
        reset_tls_caches();
        let call_count = Arc::new(AtomicUsize::new(0));
        let call_count_clone = call_count.clone();

        // Always return cert error — should be called exactly twice (initial + one retry)
        let result: RedisResult<u8> = tls_connect_with_cert_retry(
            |_config| {
                let cc = call_count_clone.clone();
                async move {
                    cc.fetch_add(1, Ordering::SeqCst);
                    Err(make_cert_error())
                }
            },
            false, // has_custom_params = false
            false,
            &None,
        )
        .await;

        assert!(result.is_err());
        assert_eq!(
            call_count.load(Ordering::SeqCst),
            2,
            "Should retry exactly once"
        );
        reset_tls_caches();
    }

    #[tokio::test]
    #[serial]
    async fn test_no_retry_on_non_cert_error() {
        reset_tls_caches();
        let call_count = Arc::new(AtomicUsize::new(0));
        let call_count_clone = call_count.clone();

        let result: RedisResult<u8> = tls_connect_with_cert_retry(
            |_config| {
                let cc = call_count_clone.clone();
                async move {
                    cc.fetch_add(1, Ordering::SeqCst);
                    Err(make_tcp_error())
                }
            },
            false,
            false,
            &None,
        )
        .await;

        assert!(result.is_err());
        assert_eq!(
            call_count.load(Ordering::SeqCst),
            1,
            "Should not retry on non-cert error"
        );
        reset_tls_caches();
    }

    #[tokio::test]
    #[serial]
    async fn test_no_retry_with_custom_params_even_on_cert_error() {
        reset_tls_caches();
        let call_count = Arc::new(AtomicUsize::new(0));
        let call_count_clone = call_count.clone();

        let result: RedisResult<u8> = tls_connect_with_cert_retry(
            |_config| {
                let cc = call_count_clone.clone();
                async move {
                    cc.fetch_add(1, Ordering::SeqCst);
                    Err(make_cert_error())
                }
            },
            true, // has_custom_params = true
            false,
            &None,
        )
        .await;

        assert!(result.is_err());
        assert_eq!(
            call_count.load(Ordering::SeqCst),
            1,
            "Custom params should skip retry"
        );
        reset_tls_caches();
    }

    #[tokio::test]
    #[serial]
    async fn test_successful_connect_does_not_retry() {
        reset_tls_caches();
        let call_count = Arc::new(AtomicUsize::new(0));
        let call_count_clone = call_count.clone();

        let result: RedisResult<u8> = tls_connect_with_cert_retry(
            |_config| {
                let cc = call_count_clone.clone();
                async move {
                    cc.fetch_add(1, Ordering::SeqCst);
                    Ok(42u8)
                }
            },
            false,
            false,
            &None,
        )
        .await;

        assert_eq!(result.unwrap(), 42);
        assert_eq!(
            call_count.load(Ordering::SeqCst),
            1,
            "Success should not retry"
        );
        reset_tls_caches();
    }

    /// Recovery path: first attempt fails with a cert error, second attempt
    /// succeeds — caller must see `Ok` and exactly two closure invocations.
    /// This is the scenario the retry feature exists for.
    #[tokio::test]
    #[serial]
    async fn test_retry_succeeds_when_second_attempt_ok() {
        reset_tls_caches();
        let call_count = Arc::new(AtomicUsize::new(0));
        let call_count_clone = call_count.clone();

        let result: RedisResult<u8> = tls_connect_with_cert_retry(
            |_config| {
                let cc = call_count_clone.clone();
                async move {
                    let n = cc.fetch_add(1, Ordering::SeqCst);
                    if n == 0 {
                        Err(make_cert_error())
                    } else {
                        Ok(7u8)
                    }
                }
            },
            false,
            false,
            &None,
        )
        .await;

        assert_eq!(result.unwrap(), 7, "second attempt should surface as Ok");
        assert_eq!(
            call_count.load(Ordering::SeqCst),
            2,
            "retry should run exactly once after the first cert error"
        );
        reset_tls_caches();
    }
}
