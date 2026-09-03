#![allow(deprecated)]

use super::ConnectionLike;
use super::{setup_connection, AsyncStream, RedisRuntime};
use crate::cmd::{cmd, Cmd};
use crate::connection::{
    resp2_is_pub_sub_state_cleared, resp3_is_pub_sub_state_cleared, ConnectionAddr, ConnectionInfo,
    Msg, RedisConnectionInfo,
};
#[cfg(feature = "tokio-comp")]
use crate::parser::ValueCodec;
use crate::pipeline::PipelineRetryStrategy;
use crate::types::{ErrorKind, FromRedisValue, RedisError, RedisFuture, RedisResult, Value};
use crate::{from_owned_redis_value, ProtocolVersion, ToRedisArgs};
use ::tokio::io::{AsyncRead, AsyncWrite, AsyncWriteExt};
#[cfg(feature = "tokio-comp")]
use ::tokio::net::lookup_host;
use combine::{parser::combinator::AnySendSyncPartialState, stream::PointerOffset};
use futures_util::future::select_ok;
use futures_util::{
    future::FutureExt,
    stream::{Stream, StreamExt},
};
use std::net::{IpAddr, SocketAddr};
use std::pin::Pin;

/// Write all segments of a packed command to an async stream using vectored
/// I/O, so large shared payloads ([`crate::cmd::SegmentedBytes`]) go straight
/// to the socket without being copied into an intermediate buffer. Handles
/// partial writes by advancing a `(segment, offset)` cursor (MSRV-compatible,
/// avoids the 1.81 `IoSlice::advance_slices`).
async fn write_all_segments_async<W>(
    con: &mut W,
    segments: &crate::cmd::SegmentedBytes,
) -> RedisResult<()>
where
    W: AsyncWrite + Unpin,
{
    use std::io::IoSlice;
    const MAX_SLICES: usize = 64;
    let segs = segments.segments().collect::<Vec<_>>();
    let mut idx = 0;
    let mut offset = 0;
    while idx < segs.len() {
        let end = std::cmp::min(idx + MAX_SLICES, segs.len());
        let mut slices: Vec<IoSlice> = Vec::with_capacity(end - idx);
        slices.push(IoSlice::new(&segs[idx][offset..]));
        for seg in &segs[idx + 1..end] {
            slices.push(IoSlice::new(seg));
        }
        let mut n = con.write_vectored(&slices).await?;
        if n == 0 {
            return Err(RedisError::from(std::io::Error::new(
                std::io::ErrorKind::WriteZero,
                "failed to write whole command",
            )));
        }
        while n > 0 {
            let remaining = segs[idx].len() - offset;
            if n >= remaining {
                n -= remaining;
                idx += 1;
                offset = 0;
            } else {
                offset += n;
                n = 0;
            }
        }
    }
    Ok(())
}
#[cfg(feature = "tokio-comp")]
use tokio_util::codec::{Framed, FramedParts};

/// Represents a stateful redis TCP connection.
#[deprecated(note = "aio::Connection is deprecated. Use aio::MultiplexedConnection instead.")]
pub struct Connection<C = Pin<Box<dyn AsyncStream + Send + Sync>>> {
    con: C,
    buf: Vec<u8>,
    decoder: combine::stream::Decoder<AnySendSyncPartialState, PointerOffset<[u8]>>,
    db: i64,

    // Flag indicating whether the connection was left in the PubSub state after dropping `PubSub`.
    //
    // This flag is checked when attempting to send a command, and if it's raised, we attempt to
    // exit the pubsub state before executing the new request.
    pubsub: bool,

    // Field indicating which protocol to use for server communications.
    protocol: ProtocolVersion,
}

fn assert_sync<T: Sync>() {}

#[allow(unused)]
fn test() {
    assert_sync::<Connection>();
}

impl<C> Connection<C>
where
    C: Unpin + AsyncRead + AsyncWrite + Send,
{
    /// Constructs a new `Connection` out of a `AsyncRead + AsyncWrite` object
    /// and a `RedisConnectionInfo`
    pub async fn new(connection_info: &RedisConnectionInfo, con: C) -> RedisResult<Self> {
        let mut rv = Connection {
            con,
            buf: Vec::new(),
            decoder: combine::stream::Decoder::new(),
            db: connection_info.db,
            pubsub: false,
            protocol: connection_info.protocol,
        };
        setup_connection(connection_info, &mut rv, false).await?;
        Ok(rv)
    }

    /// Converts this [`Connection`] into [`PubSub`].
    pub fn into_pubsub(self) -> PubSub<C> {
        PubSub::new(self)
    }

    /// Converts this [`Connection`] into [`Monitor`]
    pub fn into_monitor(self) -> Monitor<C> {
        Monitor::new(self)
    }

    /// Fetches a single response from the connection.
    async fn read_response(&mut self) -> RedisResult<Value> {
        crate::parser::parse_redis_value_async(&mut self.decoder, &mut self.con).await
    }

    /// Takes out the bytes the decoder read past the last parsed response.
    ///
    /// Ownership moves to the caller, so these bytes are parsed exactly once.
    /// `combine`'s decoder has no in-place clear, so the reset is a replacement,
    /// which is also how the decoder is built in the first place.
    fn take_decoder_buffer(&mut self) -> bytes::BytesMut {
        let leftover = bytes::BytesMut::from(self.decoder.buffer());
        self.decoder = combine::stream::Decoder::new();
        leftover
    }

    /// Brings [`Connection`] out of `PubSub` mode.
    ///
    /// This will unsubscribe this [`Connection`] from all subscriptions.
    ///
    /// If this function returns error then on all command send tries will be performed attempt
    /// to exit from `PubSub` mode until it will be successful.
    async fn exit_pubsub(&mut self) -> RedisResult<()> {
        let res = self.clear_active_subscriptions().await;
        if res.is_ok() {
            self.pubsub = false;
        } else {
            // Raise the pubsub flag to indicate the connection is "stuck" in that state.
            self.pubsub = true;
        }

        res
    }

    /// Get the inner connection out of a PubSub
    ///
    /// Any active subscriptions are unsubscribed. In the event of an error, the connection is
    /// dropped.
    async fn clear_active_subscriptions(&mut self) -> RedisResult<()> {
        // Responses to unsubscribe commands return in a 3-tuple with values
        // ("unsubscribe" or "punsubscribe", name of subscription removed, count of remaining subs).
        // The "count of remaining subs" includes both pattern subscriptions and non pattern
        // subscriptions. Thus, to accurately drain all unsubscribe messages received from the
        // server, both commands need to be executed at once.
        {
            // Prepare both unsubscribe commands
            let unsubscribe = crate::Pipeline::new()
                .add_command(cmd("UNSUBSCRIBE"))
                .add_command(cmd("PUNSUBSCRIBE"))
                .get_packed_pipeline();

            // Execute commands
            self.con.write_all(&unsubscribe).await?;
        }

        // Receive responses
        //
        // There will be at minimum two responses - 1 for each of punsubscribe and unsubscribe
        // commands. There may be more responses if there are active subscriptions. In this case,
        // messages are received until the _subscription count_ in the responses reach zero.
        let mut received_unsub = false;
        let mut received_punsub = false;
        if self.protocol != ProtocolVersion::RESP2 {
            while let Value::Push { kind, data } =
                from_owned_redis_value(self.read_response().await?)?
            {
                if data.len() >= 2 {
                    if let Value::Int(num) = data[1] {
                        if resp3_is_pub_sub_state_cleared(
                            &mut received_unsub,
                            &mut received_punsub,
                            &kind,
                            num as isize,
                        ) {
                            break;
                        }
                    }
                }
            }
        } else {
            loop {
                let res: (Vec<u8>, (), isize) =
                    from_owned_redis_value(self.read_response().await?)?;
                if resp2_is_pub_sub_state_cleared(
                    &mut received_unsub,
                    &mut received_punsub,
                    &res.0,
                    res.2,
                ) {
                    break;
                }
            }
        }

        // Finally, the connection is back in its normal state since all subscriptions were
        // cancelled *and* all unsubscribe messages were received.
        Ok(())
    }
}

impl<C> ConnectionLike for Connection<C>
where
    C: Unpin + AsyncRead + AsyncWrite + Send,
{
    fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
        (async move {
            if self.pubsub {
                self.exit_pubsub().await?;
            }
            self.buf.clear();
            if cmd.has_out_of_line_args() {
                write_all_segments_async(&mut self.con, &cmd.get_packed_segments()).await?;
            } else {
                cmd.write_packed_command(&mut self.buf);
                self.con.write_all(&self.buf).await?;
            }
            if cmd.is_no_response() {
                return Ok(Value::Nil);
            }
            loop {
                match self.read_response().await? {
                    Value::Push { .. } => continue,
                    val => return val.extract_error(),
                }
            }
        })
        .boxed()
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a crate::Pipeline,
        offset: usize,
        count: usize,
        _pipeline_retry_strategy: Option<PipelineRetryStrategy>,
    ) -> RedisFuture<'a, Vec<Value>> {
        (async move {
            if self.pubsub {
                self.exit_pubsub().await?;
            }

            self.buf.clear();
            if cmd.commands().iter().any(|c| c.has_out_of_line_args()) {
                write_all_segments_async(&mut self.con, &cmd.get_packed_pipeline_segments())
                    .await?;
            } else {
                cmd.write_packed_pipeline(&mut self.buf);
                self.con.write_all(&self.buf).await?;
            }

            let mut first_err = None;

            for _ in 0..offset {
                let response = self.read_response().await;
                match response {
                    Ok(Value::ServerError(err)) if first_err.is_none() && cmd.is_atomic() => {
                        // If we receive a `ServerError` here, it means the error occurred between `MULTI` and `EXEC`.
                        // As a result, the entire transaction will be discarded.
                        first_err = Some(err.into());
                    }
                    Err(err) if first_err.is_none() => {
                        first_err = Some(err);
                    }
                    _ => {}
                }
            }

            let mut rv = Vec::with_capacity(count);
            let mut count = count;
            let mut idx = 0;
            while idx < count {
                let response = self.read_response().await;
                match response {
                    Ok(item) => {
                        // RESP3 can insert push data between command replies
                        if let Value::Push { .. } = item {
                            // if that is the case we have to extend the loop and handle push data
                            count += 1;
                        } else {
                            rv.push(item);
                        }
                    }
                    Err(err) => {
                        if first_err.is_none() {
                            first_err = Some(err);
                        }
                    }
                }
                idx += 1;
            }

            if let Some(err) = first_err {
                Err(err)
            } else {
                Ok(rv)
            }
        })
        .boxed()
    }

    fn get_db(&self) -> i64 {
        self.db
    }

    fn is_closed(&self) -> bool {
        // always false for AsyncRead + AsyncWrite (cant do better)
        false
    }
}

/// Represents a `PubSub` connection.
pub struct PubSub<C = Pin<Box<dyn AsyncStream + Send + Sync>>>(Connection<C>);

/// Represents a `Monitor` connection.
pub struct Monitor<C = Pin<Box<dyn AsyncStream + Send + Sync>>>(Connection<C>);

impl<C> PubSub<C>
where
    C: Unpin + AsyncRead + AsyncWrite + Send,
{
    fn new(con: Connection<C>) -> Self {
        Self(con)
    }

    /// Subscribes to a new channel.
    pub async fn subscribe<T: ToRedisArgs>(&mut self, channel: T) -> RedisResult<()> {
        let mut cmd = cmd("SUBSCRIBE");
        cmd.arg(channel);
        if self.0.protocol != ProtocolVersion::RESP2 {
            cmd.set_no_response(true);
        }
        cmd.query_async(&mut self.0).await
    }

    /// Subscribes to a new channel with a pattern.
    pub async fn psubscribe<T: ToRedisArgs>(&mut self, pchannel: T) -> RedisResult<()> {
        let mut cmd = cmd("PSUBSCRIBE");
        cmd.arg(pchannel);
        if self.0.protocol != ProtocolVersion::RESP2 {
            cmd.set_no_response(true);
        }
        cmd.query_async(&mut self.0).await
    }

    /// Unsubscribes from a channel.
    pub async fn unsubscribe<T: ToRedisArgs>(&mut self, channel: T) -> RedisResult<()> {
        let mut cmd = cmd("UNSUBSCRIBE");
        cmd.arg(channel);
        if self.0.protocol != ProtocolVersion::RESP2 {
            cmd.set_no_response(true);
        }
        cmd.query_async(&mut self.0).await
    }

    /// Unsubscribes from a channel with a pattern.
    pub async fn punsubscribe<T: ToRedisArgs>(&mut self, pchannel: T) -> RedisResult<()> {
        let mut cmd = cmd("PUNSUBSCRIBE");
        cmd.arg(pchannel);
        if self.0.protocol != ProtocolVersion::RESP2 {
            cmd.set_no_response(true);
        }
        cmd.query_async(&mut self.0).await
    }

    /// Returns [`Stream`] of [`Msg`]s from this [`PubSub`]s subscriptions.
    ///
    /// The message itself is still generic and can be converted into an appropriate type through
    /// the helper methods on it.
    pub fn on_message(&mut self) -> impl Stream<Item = Msg> + '_ {
        // Hand the buffered bytes to the stream rather than copying them: the stream
        // owns them now, so leaving a copy behind would replay delivered messages on
        // the next call.
        let leftover = self.0.take_decoder_buffer();
        framed_with_leftover(&mut self.0.con, leftover)
            .filter_map(|msg| Box::pin(async move { Msg::from_value(&msg.ok()?.ok()?) }))
    }

    /// Returns [`Stream`] of [`Msg`]s from this [`PubSub`]s subscriptions consuming it.
    ///
    /// The message itself is still generic and can be converted into an appropriate type through
    /// the helper methods on it.
    /// This can be useful in cases where the stream needs to be returned or held by something other
    /// than the [`PubSub`].
    pub fn into_on_message(mut self) -> impl Stream<Item = Msg> {
        let leftover = self.0.take_decoder_buffer();
        framed_with_leftover(self.0.con, leftover)
            .filter_map(|msg| Box::pin(async move { Msg::from_value(&msg.ok()?.ok()?) }))
    }

    /// Exits from `PubSub` mode and converts [`PubSub`] into [`Connection`].
    #[deprecated(note = "aio::Connection is deprecated")]
    pub async fn into_connection(mut self) -> Connection<C> {
        self.0.exit_pubsub().await.ok();

        self.0
    }
}

impl<C> Monitor<C>
where
    C: Unpin + AsyncRead + AsyncWrite + Send,
{
    /// Create a [`Monitor`] from a [`Connection`]
    pub fn new(con: Connection<C>) -> Self {
        Self(con)
    }

    /// Deliver the MONITOR command to this [`Monitor`]ing wrapper.
    pub async fn monitor(&mut self) -> RedisResult<()> {
        cmd("MONITOR").query_async(&mut self.0).await
    }

    /// Returns [`Stream`] of [`FromRedisValue`] values from this [`Monitor`]ing connection
    pub fn on_message<'a, T: FromRedisValue + 'a>(&'a mut self) -> impl Stream<Item = T> + 'a {
        // Hand the buffered bytes to the stream rather than copying them: the stream
        // owns them now, so leaving a copy behind would replay delivered lines on the
        // next call.
        let leftover = self.0.take_decoder_buffer();
        monitor_stream(&mut self.0.con, leftover)
    }

    /// Returns [`Stream`] of [`FromRedisValue`] values from this [`Monitor`]ing connection
    pub fn into_on_message<T: FromRedisValue>(mut self) -> impl Stream<Item = T> {
        let leftover = self.0.take_decoder_buffer();
        monitor_stream(self.0.con, leftover)
    }
}

/// Builds a [`ValueCodec`] [`Framed`] over `con`, seeding its read buffer with
/// the `leftover` bytes the connection decoder read past the handshake.
///
/// A stream handshake (`MONITOR`, `SUBSCRIBE`, `PSUBSCRIBE`) is parsed through
/// `Connection::decoder`, which reads from the socket in chunks. Under load the
/// server can pack the first stream payload into the same TCP segment as the
/// handshake reply, leaving those bytes buffered inside the decoder. Building the
/// stream's codec over the bare socket would drop that buffer, and the damage
/// depends on how much was buffered: a complete frame is lost outright, while a
/// partial frame leaves the new codec resuming mid-frame, which fails to parse and
/// ends the stream before it delivers anything. Seeding the read buffer with the
/// leftover bytes avoids both.
///
/// The `Monitor` and `PubSub` stream constructors all route through here, over the
/// borrowed `&mut con` and the moved `con` alike, so every path stays in step.
fn framed_with_leftover<C>(con: C, leftover: bytes::BytesMut) -> Framed<C, ValueCodec>
where
    C: AsyncRead + AsyncWrite + Unpin,
{
    let mut parts = FramedParts::new::<Vec<u8>>(con, ValueCodec::default());
    parts.read_buf = leftover;
    Framed::from_parts(parts)
}

/// Builds a MONITOR line [`Stream`] over `con`, seeding the framed read buffer
/// with the `leftover` bytes the connection decoder read past the handshake.
fn monitor_stream<C, T>(con: C, leftover: bytes::BytesMut) -> impl Stream<Item = T>
where
    C: AsyncRead + AsyncWrite + Unpin,
    T: FromRedisValue,
{
    framed_with_leftover(con, leftover).filter_map(|value| {
        Box::pin(async move { T::from_owned_redis_value(value.ok()?.ok()?).ok() })
    })
}

pub(crate) async fn get_socket_addrs(
    host: &str,
    port: u16,
) -> RedisResult<impl Iterator<Item = SocketAddr> + Send + '_> {
    #[cfg(feature = "tokio-comp")]
    let socket_addrs = lookup_host((host, port)).await?;

    let mut socket_addrs = socket_addrs.peekable();
    match socket_addrs.peek() {
        Some(_) => Ok(socket_addrs),
        None => Err(RedisError::from((
            ErrorKind::InvalidClientConfig,
            "No address found for host",
        ))),
    }
}

/// Logs the creation of a connection, including its type, the node, and optionally its IP address.
fn log_conn_creation<T>(conn_type: &str, node: T, ip: Option<IpAddr>)
where
    T: std::fmt::Debug,
{
    tracing::debug!(
        "Creating {conn_type} connection for node: {node:?}{}",
        ip.map(|ip| format!(", IP: {ip:?}")).unwrap_or_default()
    );
}

pub(crate) async fn connect_simple<T: RedisRuntime>(
    connection_info: &ConnectionInfo,
    _socket_addr: Option<SocketAddr>,
    tcp_nodelay: bool,
) -> RedisResult<(T, Option<IpAddr>)> {
    Ok(match connection_info.addr {
        ConnectionAddr::Tcp(ref host, port) => {
            if let Some(socket_addr) = _socket_addr {
                return Ok::<_, RedisError>((
                    <T>::connect_tcp(socket_addr, tcp_nodelay).await?,
                    Some(socket_addr.ip()),
                ));
            }
            let socket_addrs = get_socket_addrs(host, port).await?;
            select_ok(socket_addrs.map(|socket_addr| {
                log_conn_creation("TCP", format!("{host}:{port}"), Some(socket_addr.ip()));
                Box::pin(async move {
                    Ok::<_, RedisError>((
                        <T>::connect_tcp(socket_addr, tcp_nodelay).await?,
                        Some(socket_addr.ip()),
                    ))
                })
            }))
            .await?
            .0
        }

        ConnectionAddr::TcpTls {
            ref host,
            port,
            insecure,
            ref tls_params,
        } => {
            if let Some(socket_addr) = _socket_addr {
                return Ok::<_, RedisError>((
                    <T>::connect_tcp_tls(host, socket_addr, insecure, tls_params, tcp_nodelay)
                        .await?,
                    Some(socket_addr.ip()),
                ));
            }
            let socket_addrs = get_socket_addrs(host, port).await?;
            select_ok(socket_addrs.map(|socket_addr| {
                log_conn_creation(
                    "TCP with TLS",
                    format!("{host}:{port}"),
                    Some(socket_addr.ip()),
                );
                Box::pin(async move {
                    Ok::<_, RedisError>((
                        <T>::connect_tcp_tls(host, socket_addr, insecure, tls_params, tcp_nodelay)
                            .await?,
                        Some(socket_addr.ip()),
                    ))
                })
            }))
            .await?
            .0
        }

        #[cfg(unix)]
        ConnectionAddr::Unix(ref path) => {
            log_conn_creation("UDS", path, None);
            (<T>::connect_unix(path).await?, None)
        }

        #[cfg(not(unix))]
        ConnectionAddr::Unix(_) => {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Cannot connect to unix sockets \
                 on this platform",
            )))
        }
    })
}

#[cfg(all(test, feature = "tokio-comp"))]
mod monitor_tests {
    use super::*;
    use ::tokio::io::{duplex, AsyncWriteExt, DuplexStream};
    use ::tokio::sync::oneshot;

    // A MONITOR line as the server sends it: a RESP simple string.
    const MONITOR_LINE: &str = "+1720000000.000000 [0 127.0.0.1:6379] \"SET\" \"k\" \"v\"\r\n";

    // Wraps a `Monitor` around the client end of an in-memory duplex, matching a
    // connection that has finished setup. The decoder starts empty; each test fills
    // it by running the real `MONITOR` handshake through `monitor()`.
    fn monitor_over(client: DuplexStream) -> Monitor<DuplexStream> {
        Monitor::new(Connection {
            con: client,
            buf: Vec::new(),
            decoder: combine::stream::Decoder::new(),
            db: 0,
            pubsub: false,
            protocol: ProtocolVersion::RESP2,
        })
    }

    // The server can pack the first monitor line into the same segment as the `+OK`
    // handshake reply. `monitor()` reads `+OK` through the decoder, which over-reads
    // and leaves the whole monitor line sitting in `decoder`. The borrowed
    // `on_message()` has to hand that line back: dropping the buffer (the codec built
    // over the bare socket) hangs here, because the socket has nothing left to read.
    #[tokio::test]
    async fn on_message_delivers_fully_buffered_line() {
        let (client, mut server) = duplex(4096);

        let mut handshake = String::from("+OK\r\n");
        handshake.push_str(MONITOR_LINE);
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut monitor = monitor_over(client);
        monitor.monitor().await.unwrap();

        // Check the precondition instead of assuming it: the handshake read has to
        // pull the whole monitor line into the decoder, so the socket holds nothing more.
        assert_eq!(
            monitor.0.decoder.buffer(),
            MONITOR_LINE.as_bytes(),
            "handshake did not buffer the monitor line, so the test would not exercise the handoff"
        );

        // Hold the server end open so a buffer-dropping stream blocks on the socket
        // rather than seeing EOF, which makes a timeout here a real failure signal.
        let (_stop_tx, stop_rx) = oneshot::channel::<()>();
        let _server_task = tokio::spawn(async move {
            let _ = stop_rx.await;
            drop(server);
        });

        let mut stream = monitor.on_message::<String>();
        let line = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
            .await
            .expect("borrowed on_message dropped the buffered monitor line")
            .expect("stream ended before delivering the buffered monitor line");
        assert!(line.contains("\"SET\""), "unexpected monitor line: {line}");
    }

    // When only part of the first monitor line was buffered with `+OK`, the borrowed
    // `on_message()` has to resume the frame from the buffered prefix and read the rest
    // from the socket. A fresh codec over the bare socket starts mid-frame on the
    // remaining bytes, hits a parse error, and ends the stream with zero lines.
    #[tokio::test]
    async fn on_message_recovers_partially_buffered_line() {
        let (client, mut server) = duplex(4096);

        let split = MONITOR_LINE.len() / 2;
        let mut first = String::from("+OK\r\n");
        first.push_str(&MONITOR_LINE[..split]);
        server.write_all(first.as_bytes()).await.unwrap();

        let mut monitor = monitor_over(client);
        monitor.monitor().await.unwrap();

        // Check the precondition: the prefix has to be sitting in the decoder mid-frame,
        // which is what makes dropping it resume parsing at the wrong offset.
        assert_eq!(
            monitor.0.decoder.buffer(),
            &MONITOR_LINE.as_bytes()[..split],
            "handshake did not buffer the partial monitor line prefix"
        );

        // Send the remainder only after the handshake read has buffered the prefix.
        let rest = MONITOR_LINE[split..].to_string();
        let _server_task = tokio::spawn(async move {
            server.write_all(rest.as_bytes()).await.unwrap();
            ::tokio::time::sleep(std::time::Duration::from_secs(2)).await;
            drop(server);
        });

        let mut stream = monitor.on_message::<String>();
        let line = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
            .await
            .expect("borrowed on_message terminated on the partially buffered line")
            .expect("stream ended before delivering the partially buffered line");
        assert!(line.contains("\"SET\""), "unexpected monitor line: {line}");
    }

    // The borrowed path hands the decoder's bytes to the stream, so it also has to take
    // them out of the decoder. Otherwise dropping one stream and building another
    // replays lines the first stream already delivered.
    #[tokio::test]
    async fn on_message_does_not_replay_buffered_line_across_streams() {
        let (client, mut server) = duplex(4096);

        let mut handshake = String::from("+OK\r\n");
        handshake.push_str(MONITOR_LINE);
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut monitor = monitor_over(client);
        monitor.monitor().await.unwrap();
        assert_eq!(monitor.0.decoder.buffer(), MONITOR_LINE.as_bytes());

        {
            let mut first = monitor.on_message::<String>();
            let line = ::tokio::time::timeout(std::time::Duration::from_secs(2), first.next())
                .await
                .expect("first borrowed stream did not deliver the buffered line")
                .expect("first borrowed stream ended early");
            assert!(line.contains("\"SET\""), "unexpected monitor line: {line}");
        }

        // The first stream consumed the line, so a second stream must not see it again.
        // Only the server's next write should ever surface here.
        let mut second = monitor.on_message::<String>();
        let replayed =
            ::tokio::time::timeout(std::time::Duration::from_millis(200), second.next()).await;
        assert!(
            replayed.is_err(),
            "second borrowed stream replayed an already-delivered line: {replayed:?}"
        );

        drop(second);
        drop(server);
    }

    // The handshake read can over-read more than one line: two lines can share the
    // segment that carried `+OK`, so both land in the decoder. A single borrowed
    // stream owns the whole leftover, so it has to hand back both lines in order,
    // decoding the second from the seeded buffer once the first is consumed.
    #[tokio::test]
    async fn on_message_delivers_two_buffered_lines() {
        let (client, mut server) = duplex(4096);

        let mut handshake = String::from("+OK\r\n");
        handshake.push_str(MONITOR_LINE);
        handshake.push_str(MONITOR_LINE);
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut monitor = monitor_over(client);
        monitor.monitor().await.unwrap();

        // The read that satisfies `+OK` over-reads a prefix of the two lines; whatever
        // it buffered has to line up with them, and the stream reads any remainder from
        // the socket.
        let two_lines = format!("{MONITOR_LINE}{MONITOR_LINE}");
        let buffered = monitor.0.decoder.buffer();
        assert!(
            !buffered.is_empty(),
            "handshake did not buffer any of the monitor lines, so the test would not exercise the two-frame handoff"
        );
        assert!(
            two_lines.as_bytes().starts_with(buffered),
            "buffered bytes are not a prefix of the two monitor lines: {buffered:?}"
        );

        // Hold the server end open so a stream that mishandles the second line blocks
        // on the socket rather than seeing EOF, which makes a timeout a real failure.
        let (_stop_tx, stop_rx) = oneshot::channel::<()>();
        let _server_task = tokio::spawn(async move {
            let _ = stop_rx.await;
            drop(server);
        });

        let mut stream = monitor.on_message::<String>();
        for nth in ["first", "second"] {
            let line = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
                .await
                .unwrap_or_else(|_| {
                    panic!("borrowed on_message did not deliver the {nth} buffered line")
                })
                .unwrap_or_else(|| {
                    panic!("stream ended before delivering the {nth} buffered line")
                });
            assert!(line.contains("\"SET\""), "unexpected monitor line: {line}");
        }
    }
}

#[cfg(all(test, feature = "tokio-comp"))]
mod pubsub_tests {
    use super::*;
    use ::tokio::io::{duplex, AsyncWriteExt, DuplexStream};
    use ::tokio::sync::oneshot;

    const CHANNEL: &str = "ch";
    const PAYLOAD: &str = "hello";

    // The RESP2 confirmation the server sends in reply to SUBSCRIBE.
    fn subscribe_confirmation() -> String {
        format!(
            "*3\r\n$9\r\nsubscribe\r\n${}\r\n{}\r\n:1\r\n",
            CHANNEL.len(),
            CHANNEL
        )
    }

    // The RESP2 frame for a published message on `CHANNEL`.
    fn message_frame() -> String {
        format!(
            "*3\r\n$7\r\nmessage\r\n${}\r\n{}\r\n${}\r\n{}\r\n",
            CHANNEL.len(),
            CHANNEL,
            PAYLOAD.len(),
            PAYLOAD
        )
    }

    // Wraps a `PubSub` around the client end of an in-memory duplex, matching a
    // connection that has finished setup. The decoder starts empty; each test fills
    // it by running the real `SUBSCRIBE` handshake through `subscribe()` in RESP2,
    // where the confirmation is parsed through `decoder` and no_response is not set.
    fn pubsub_over(client: DuplexStream) -> PubSub<DuplexStream> {
        PubSub::new(Connection {
            con: client,
            buf: Vec::new(),
            decoder: combine::stream::Decoder::new(),
            db: 0,
            pubsub: false,
            protocol: ProtocolVersion::RESP2,
        })
    }

    fn assert_expected_message(msg: Msg) {
        assert_eq!(msg.get_channel_name(), CHANNEL, "unexpected channel");
        assert_eq!(
            msg.get_payload::<String>().unwrap(),
            PAYLOAD,
            "unexpected payload"
        );
    }

    // The confirmation is parsed through the combine decoder, whose async read grows
    // the buffer in increments, so it over-reads a prefix of the message frame rather
    // than a fixed amount. Asserting that prefix is non-empty and lines up with the
    // frame is enough to prove the handshake buffered bytes the stream must recover.
    fn assert_buffered_prefix(buffered: &[u8]) {
        let frame = message_frame();
        assert!(
            !buffered.is_empty(),
            "handshake did not buffer any of the message frame, so the test would not exercise the handoff"
        );
        assert!(
            frame.as_bytes().starts_with(buffered),
            "buffered bytes are not a prefix of the message frame: {buffered:?}"
        );
    }

    // The server can pack the first published message into the same segment as the
    // subscribe confirmation. `subscribe()` reads the confirmation through the decoder,
    // which over-reads and leaves the whole message frame sitting in `decoder`. The
    // borrowed `on_message()` has to hand that frame back: dropping the buffer (the
    // codec built over the bare socket) hangs here, because the socket has nothing
    // left to read.
    #[tokio::test]
    async fn on_message_delivers_fully_buffered_message() {
        let (client, mut server) = duplex(4096);

        let mut handshake = subscribe_confirmation();
        handshake.push_str(&message_frame());
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut pubsub = pubsub_over(client);
        pubsub.subscribe(CHANNEL).await.unwrap();

        assert_buffered_prefix(pubsub.0.decoder.buffer());

        // Hold the server end open so a buffer-dropping stream blocks on the socket
        // rather than seeing EOF, which makes a timeout here a real failure signal.
        let (_stop_tx, stop_rx) = oneshot::channel::<()>();
        let _server_task = tokio::spawn(async move {
            let _ = stop_rx.await;
            drop(server);
        });

        let mut stream = pubsub.on_message();
        let msg = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
            .await
            .expect("borrowed on_message dropped the buffered message")
            .expect("stream ended before delivering the buffered message");
        assert_expected_message(msg);
    }

    // When only part of the first message was buffered with the confirmation, the
    // borrowed `on_message()` has to resume the frame from the buffered prefix and read
    // the rest from the socket. A fresh codec over the bare socket starts mid-frame on
    // the remaining bytes, hits a parse error, and ends the stream with zero messages.
    #[tokio::test]
    async fn on_message_recovers_partially_buffered_message() {
        let (client, mut server) = duplex(4096);

        let frame = message_frame();
        let split = frame.len() / 2;
        let mut first = subscribe_confirmation();
        first.push_str(&frame[..split]);
        server.write_all(first.as_bytes()).await.unwrap();

        let mut pubsub = pubsub_over(client);
        pubsub.subscribe(CHANNEL).await.unwrap();

        assert_eq!(
            pubsub.0.decoder.buffer(),
            &frame.as_bytes()[..split],
            "handshake did not buffer the partial message prefix"
        );

        // Send the remainder only after the handshake read has buffered the prefix.
        let rest = frame[split..].to_string();
        let _server_task = tokio::spawn(async move {
            server.write_all(rest.as_bytes()).await.unwrap();
            ::tokio::time::sleep(std::time::Duration::from_secs(2)).await;
            drop(server);
        });

        let mut stream = pubsub.on_message();
        let msg = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
            .await
            .expect("borrowed on_message terminated on the partially buffered message")
            .expect("stream ended before delivering the partially buffered message");
        assert_expected_message(msg);
    }

    // The borrowed path hands the decoder's bytes to the stream, so it also has to take
    // them out of the decoder. Otherwise dropping one stream and building another
    // replays messages the first stream already delivered.
    #[tokio::test]
    async fn on_message_does_not_replay_buffered_message_across_streams() {
        let (client, mut server) = duplex(4096);

        let mut handshake = subscribe_confirmation();
        handshake.push_str(&message_frame());
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut pubsub = pubsub_over(client);
        pubsub.subscribe(CHANNEL).await.unwrap();
        assert_buffered_prefix(pubsub.0.decoder.buffer());

        {
            let mut first = pubsub.on_message();
            let msg = ::tokio::time::timeout(std::time::Duration::from_secs(2), first.next())
                .await
                .expect("first borrowed stream did not deliver the buffered message")
                .expect("first borrowed stream ended early");
            assert_expected_message(msg);
        }

        // The first stream consumed the message, so a second stream must not see it
        // again. Only the server's next write should ever surface here.
        let mut second = pubsub.on_message();
        let replayed =
            ::tokio::time::timeout(std::time::Duration::from_millis(200), second.next()).await;
        assert!(
            replayed.is_err(),
            "second borrowed stream replayed an already-delivered message: {replayed:?}"
        );

        drop(second);
        drop(server);
    }

    // The handshake read can over-read more than one message: two messages can share
    // the segment that carried the subscribe confirmation, so both land in the decoder.
    // A single borrowed stream owns the whole leftover, so it has to hand back both
    // messages in order, decoding the second from the seeded buffer once the first is
    // consumed.
    #[tokio::test]
    async fn on_message_delivers_two_buffered_messages() {
        let (client, mut server) = duplex(4096);

        let frame = message_frame();
        let mut handshake = subscribe_confirmation();
        handshake.push_str(&frame);
        handshake.push_str(&frame);
        server.write_all(handshake.as_bytes()).await.unwrap();

        let mut pubsub = pubsub_over(client);
        pubsub.subscribe(CHANNEL).await.unwrap();

        // The decoder over-reads a prefix of the two frames; whatever it buffered has
        // to line up with them, and the stream reads any remainder from the socket.
        let two_frames = format!("{frame}{frame}");
        let buffered = pubsub.0.decoder.buffer();
        assert!(
            !buffered.is_empty(),
            "handshake did not buffer any of the message frames, so the test would not exercise the two-frame handoff"
        );
        assert!(
            two_frames.as_bytes().starts_with(buffered),
            "buffered bytes are not a prefix of the two message frames: {buffered:?}"
        );

        // Hold the server end open so a stream that mishandles the second message blocks
        // on the socket rather than seeing EOF, which makes a timeout a real failure.
        let (_stop_tx, stop_rx) = oneshot::channel::<()>();
        let _server_task = tokio::spawn(async move {
            let _ = stop_rx.await;
            drop(server);
        });

        let mut stream = pubsub.on_message();
        for nth in ["first", "second"] {
            let msg = ::tokio::time::timeout(std::time::Duration::from_secs(2), stream.next())
                .await
                .unwrap_or_else(|_| {
                    panic!("borrowed on_message did not deliver the {nth} buffered message")
                })
                .unwrap_or_else(|| {
                    panic!("stream ended before delivering the {nth} buffered message")
                });
            assert_expected_message(msg);
        }
    }
}
