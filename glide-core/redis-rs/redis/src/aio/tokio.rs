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
use std::sync::Arc;
use tokio_rustls::{client::TlsStream, TlsConnector};

use crate::tls::TlsConnParams;

#[cfg(unix)]
use super::Path;

#[inline(always)]
async fn connect_tcp(addr: &SocketAddr) -> io::Result<TcpStreamTokio> {
    let socket = TcpStreamTokio::connect(addr).await?;
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
    async fn connect_tcp(socket_addr: SocketAddr) -> RedisResult<Self> {
        Ok(connect_tcp(&socket_addr).await.map(Tokio::Tcp)?)
    }

    async fn connect_tcp_tls(
        hostname: &str,
        socket_addr: SocketAddr,
        insecure: bool,
        tls_params: &Option<TlsConnParams>,
    ) -> RedisResult<Self> {
        let config = create_rustls_config(insecure, tls_params.clone())?;
        let tls_connector = TlsConnector::from(Arc::new(config));

        Ok(tls_connector
            .connect(
                rustls_pki_types::ServerName::try_from(hostname)?.to_owned(),
                connect_tcp(&socket_addr).await?,
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
