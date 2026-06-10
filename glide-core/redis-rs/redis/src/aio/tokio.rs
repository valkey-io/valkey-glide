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
use std::sync::{Arc, OnceLock};

/// Cached TLS configurations to avoid repeatedly loading system certificates.
/// On Linux, loading native certs can involve reading and parsing ~300KB of cert files,
/// which adds ~6ms per connection when done repeatedly.
static TLS_CONFIG_DEFAULT: OnceLock<Result<Arc<rustls::ClientConfig>, String>> = OnceLock::new();
static TLS_CONFIG_INSECURE: OnceLock<Result<Arc<rustls::ClientConfig>, String>> = OnceLock::new();
use tokio_rustls::{client::TlsStream, TlsConnector};

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

        let config = if has_custom_params {
            // Custom TLS params (mTLS, custom CA) - cannot cache
            Arc::new(create_rustls_config(insecure, tls_params.clone())?)
        } else if insecure {
            TLS_CONFIG_INSECURE
                .get_or_init(|| {
                    create_rustls_config(true, tls_params.clone())
                        .map(Arc::new)
                        .map_err(|e| e.to_string())
                })
                .as_ref()
                .map_err(|e| {
                    crate::RedisError::from((
                        crate::ErrorKind::IoError,
                        "TLS config error",
                        e.clone(),
                    ))
                })?
                .clone()
        } else {
            TLS_CONFIG_DEFAULT
                .get_or_init(|| {
                    create_rustls_config(false, tls_params.clone())
                        .map(Arc::new)
                        .map_err(|e| e.to_string())
                })
                .as_ref()
                .map_err(|e| {
                    crate::RedisError::from((
                        crate::ErrorKind::IoError,
                        "TLS config error",
                        e.clone(),
                    ))
                })?
                .clone()
        };

        let tls_connector = TlsConnector::from(config);

        Ok(tls_connector
            .connect(
                rustls_pki_types::ServerName::try_from(hostname)?.to_owned(),
                connect_tcp(&socket_addr, tcp_nodelay).await?,
            )
            .await
            .map(|con| Tokio::TcpTls(Box::new(con)))?)
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
