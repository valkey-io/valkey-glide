use super::{ConnectionLike, Runtime};
use crate::aio::setup_connection;
use crate::aio::DisconnectNotifier;
use crate::client::GlideConnectionOptions;
use crate::cmd::Cmd;
#[cfg(feature = "tokio-comp")]
use crate::parser::ValueCodec;
use crate::pipeline::PipelineRetryStrategy;
use crate::push_manager::PushManager;
use crate::types::{RedisError, RedisFuture, RedisResult, Value};
use crate::{cmd, ConnectionInfo, ProtocolVersion, PushKind};
use ::tokio::{
    io::{AsyncRead, AsyncWrite},
    sync::{mpsc, oneshot},
};
use arc_swap::ArcSwap;
use futures_util::{
    future::{Future, FutureExt},
    ready,
    sink::Sink,
    stream::{self, Stream, StreamExt, TryStreamExt as _},
};
use pin_project_lite::pin_project;
use std::collections::VecDeque;
use std::fmt;
use std::fmt::Debug;
use std::pin::Pin;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::task::{self, Poll};
use std::time::Duration;
#[cfg(feature = "tokio-comp")]
use tokio_util::codec::Decoder;

// Default connection timeout in ms
const DEFAULT_CONNECTION_ATTEMPT_TIMEOUT: Duration = Duration::from_millis(2000);

// Senders which the result of a single request are sent through
type PipelineOutput = oneshot::Sender<RedisResult<Value>>;

enum ResponseAggregate {
    SingleCommand,
    Pipeline {
        expected_response_count: usize, // = offset + count, pipelines offset is 0
        current_response_count: usize,
        buffer: Vec<Value>,
        first_err: Option<RedisError>,
        is_transaction: bool,
    },
}

impl ResponseAggregate {
    fn new(pipeline_response_count: Option<usize>, is_transaction: bool) -> Self {
        match pipeline_response_count {
            Some(response_count) => ResponseAggregate::Pipeline {
                expected_response_count: response_count,
                current_response_count: 0,
                buffer: Vec::new(),
                first_err: None,
                is_transaction,
            },
            None => ResponseAggregate::SingleCommand,
        }
    }
}

struct InFlight {
    output: PipelineOutput,
    response_aggregate: ResponseAggregate,
}

// A single message sent through the pipeline
struct PipelineMessage<S> {
    input: S,
    output: PipelineOutput,
    // If `None`, this is a single request, not a pipeline of multiple requests.
    pipeline_response_count: Option<usize>,
    is_transaction: bool,
}

/// Wrapper around a `Stream + Sink` where each item sent through the `Sink` results in one or more
/// items being output by the `Stream` (the number is specified at time of sending). With the
/// interface provided by `Pipeline` an easy interface of request to response, hiding the `Stream`
/// and `Sink`.
#[derive(Clone)]
pub(crate) struct Pipeline<SinkItem> {
    sender: mpsc::Sender<PipelineMessage<SinkItem>>,
    push_manager: Arc<ArcSwap<PushManager>>,
    is_stream_closed: Arc<AtomicBool>,
}

impl<SinkItem> Debug for Pipeline<SinkItem>
where
    SinkItem: Debug,
{
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_tuple("Pipeline").field(&self.sender).finish()
    }
}

pin_project! {
    struct PipelineSink<T> {
        #[pin]
        sink_stream: T,
        in_flight: VecDeque<InFlight>,
        error: Option<RedisError>,
        push_manager: Arc<ArcSwap<PushManager>>,
        disconnect_notifier: Option<Box<dyn DisconnectNotifier>>,
        is_stream_closed: Arc<AtomicBool>,
    }
}

impl<T> PipelineSink<T>
where
    T: Stream<Item = RedisResult<Value>> + 'static,
{
    fn new<SinkItem>(
        sink_stream: T,
        push_manager: Arc<ArcSwap<PushManager>>,
        disconnect_notifier: Option<Box<dyn DisconnectNotifier>>,
        is_stream_closed: Arc<AtomicBool>,
    ) -> Self
    where
        T: Sink<SinkItem, Error = RedisError> + Stream<Item = RedisResult<Value>> + 'static,
    {
        PipelineSink {
            sink_stream,
            in_flight: VecDeque::new(),
            error: None,
            push_manager,
            disconnect_notifier,
            is_stream_closed,
        }
    }

    // Read messages from the stream and send them back to the caller
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), ()>> {
        loop {
            let item = match ready!(self.as_mut().project().sink_stream.poll_next(cx)) {
                Some(result) => result,
                // The redis response stream is not going to produce any more items so we `Err`
                // to break out of the `forward` combinator and stop handling requests
                None => {
                    // this is the right place to notify about the passive TCP disconnect
                    // In other places we cannot distinguish between the active destruction of MultiplexedConnection and passive disconnect
                    if let Some(disconnect_notifier) = self.as_mut().project().disconnect_notifier {
                        disconnect_notifier.notify_disconnect();
                    }
                    self.is_stream_closed.store(true, Ordering::Relaxed);
                    return Poll::Ready(Err(()));
                }
            };
            self.as_mut().send_result(item);
        }
    }

    fn send_result(self: Pin<&mut Self>, result: RedisResult<Value>) {
        let self_ = self.project();
        let mut skip_value = false;
        if let Ok(res) = &result {
            if let Value::Push { kind, data: _data } = res {
                self_.push_manager.load().try_send_raw(res);
                if !kind.has_reply() {
                    // If it's not true then push kind is converted to reply of a command
                    skip_value = true;
                }
            }
        }

        let mut entry = match self_.in_flight.pop_front() {
            Some(entry) => entry,
            None => return,
        };

        if skip_value {
            self_.in_flight.push_front(entry);
            return;
        }

        match &mut entry.response_aggregate {
            ResponseAggregate::SingleCommand => {
                entry
                    .output
                    .send(result.and_then(|v| v.extract_error()))
                    .ok();
            }
            ResponseAggregate::Pipeline {
                expected_response_count,
                current_response_count,
                buffer,
                first_err,
                is_transaction,
            } => {
                match result {
                    Ok(Value::ServerError(err)) if *is_transaction => {
                        // In transactions, `count` is always 1 because the final result is a single array (`offset + count = expected_response_count`).
                        // If we receive a `ServerError` here, it means the error occurred between `MULTI` and `EXEC`.
                        // After `EXEC`, the response is always a single array of results, so any error at this stage must have happened before `EXEC` was sent.
                        // As a result, the entire transaction will be discarded (and can be retried).
                        if first_err.is_none() {
                            *first_err = Some(err.into());
                        }
                    }
                    Ok(item) => {
                        buffer.push(item);
                    }
                    Err(err) => {
                        if first_err.is_none() {
                            *first_err = Some(err);
                        }
                    }
                }

                *current_response_count += 1;
                if current_response_count < expected_response_count {
                    // Need to gather more response values
                    self_.in_flight.push_front(entry);
                    return;
                }

                let response = match first_err.take() {
                    Some(err) => Err(err),
                    None => Ok(Value::Array(std::mem::take(buffer))),
                };

                // `Err` means that the receiver was dropped in which case it does not
                // care about the output and we can continue by just dropping the value
                // and sender
                entry.output.send(response).ok();
            }
        }
    }
}

impl<SinkItem, T> Sink<PipelineMessage<SinkItem>> for PipelineSink<T>
where
    T: Sink<SinkItem, Error = RedisError> + Stream<Item = RedisResult<Value>> + 'static,
{
    type Error = ();

    // Retrieve incoming messages and write them to the sink
    fn poll_ready(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        match ready!(self.as_mut().project().sink_stream.poll_ready(cx)) {
            Ok(()) => Ok(()).into(),
            Err(err) => {
                *self.project().error = Some(err);
                Ok(()).into()
            }
        }
    }

    fn start_send(
        mut self: Pin<&mut Self>,
        PipelineMessage {
            input,
            output,
            pipeline_response_count,
            is_transaction,
        }: PipelineMessage<SinkItem>,
    ) -> Result<(), Self::Error> {
        // If there is nothing to receive our output we do not need to send the message as it is
        // ambiguous whether the message will be sent anyway. Helps shed some load on the
        // connection.
        if output.is_closed() {
            return Ok(());
        }

        let self_ = self.as_mut().project();

        if let Some(err) = self_.error.take() {
            let _ = output.send(Err(err));
            return Err(());
        }

        match self_.sink_stream.start_send(input) {
            Ok(()) => {
                let response_aggregate =
                    ResponseAggregate::new(pipeline_response_count, is_transaction);
                let entry = InFlight {
                    output,
                    response_aggregate,
                };

                self_.in_flight.push_back(entry);
                Ok(())
            }
            Err(err) => {
                let _ = output.send(Err(err));
                Err(())
            }
        }
    }

    fn poll_flush(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        ready!(self
            .as_mut()
            .project()
            .sink_stream
            .poll_flush(cx)
            .map_err(|err| {
                self.as_mut().send_result(Err(err));
            }))?;
        self.poll_read(cx)
    }

    fn poll_close(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        // No new requests will come in after the first call to `close` but we need to complete any
        // in progress requests before closing
        if !self.in_flight.is_empty() {
            ready!(self.as_mut().poll_flush(cx))?;
        }
        let this = self.as_mut().project();
        this.sink_stream.poll_close(cx).map_err(|err| {
            self.send_result(Err(err));
        })
    }
}

impl<SinkItem> Pipeline<SinkItem>
where
    SinkItem: Send + 'static,
{
    fn new<T>(
        sink_stream: T,
        disconnect_notifier: Option<Box<dyn DisconnectNotifier>>,
    ) -> (Self, impl Future<Output = ()>)
    where
        T: Sink<SinkItem, Error = RedisError> + Stream<Item = RedisResult<Value>> + 'static,
        T: Send + 'static,
        T::Item: Send,
        T::Error: Send,
        T::Error: ::std::fmt::Debug,
    {
        const BUFFER_SIZE: usize = 50;
        let (sender, mut receiver) = mpsc::channel(BUFFER_SIZE);
        let push_manager: Arc<ArcSwap<PushManager>> =
            Arc::new(ArcSwap::new(Arc::new(PushManager::default())));
        let is_stream_closed = Arc::new(AtomicBool::new(false));
        let sink = PipelineSink::new::<SinkItem>(
            sink_stream,
            push_manager.clone(),
            disconnect_notifier,
            is_stream_closed.clone(),
        );
        let f = stream::poll_fn(move |cx| receiver.poll_recv(cx))
            .map(Ok)
            .forward(sink)
            .map(|_| ());
        (
            Pipeline {
                sender,
                push_manager,
                is_stream_closed,
            },
            f,
        )
    }

    // `None` means that the stream was out of items causing that poll loop to shut down.
    async fn send_single(&mut self, item: SinkItem, timeout: Duration) -> RedisResult<Value> {
        self.send_recv(item, None, timeout, true).await
    }

    async fn send_recv(
        &mut self,
        input: SinkItem,
        // If `None`, this is a single request, not a pipeline of multiple requests.
        pipeline_response_count: Option<usize>,
        timeout: Duration,
        is_atomic: bool,
    ) -> Result<Value, RedisError> {
        let (sender, receiver) = oneshot::channel();

        self.sender
            .send(PipelineMessage {
                input,
                pipeline_response_count,
                output: sender,
                is_transaction: is_atomic,
            })
            .await
            .map_err(|err| {
                // If an error occurs here, it means the request never reached the server, as guaranteed
                // by the 'send' function. Since the server did not receive the data, it is safe to retry
                // the request.
                RedisError::from((
                    crate::ErrorKind::FatalSendError,
                    "Failed to send the request to the server",
                    err.to_string(),
                ))
            })?;
        match Runtime::locate().timeout(timeout, receiver).await {
            Ok(Ok(result)) => result,
            Ok(Err(err)) => {
                // The `sender` was dropped, likely indicating a failure in the stream.
                // This error suggests that it's unclear whether the server received the request before the connection failed,
                // making it unsafe to retry. For example, retrying an INCR request could result in double increments.
                Err(RedisError::from((
                    crate::ErrorKind::FatalReceiveError,
                    "Failed to receive a response due to a fatal error",
                    err.to_string(),
                )))
            }
            Err(elapsed) => Err(elapsed.into()),
        }
    }

    /// Sets `PushManager` of Pipeline
    async fn set_push_manager(&mut self, push_manager: PushManager) {
        self.push_manager.store(Arc::new(push_manager));
    }

    /// Checks if the pipeline is closed.
    pub fn is_closed(&self) -> bool {
        self.is_stream_closed.load(Ordering::Relaxed)
    }
}

/// A connection object which can be cloned, allowing requests to be be sent concurrently
/// on the same underlying connection (tcp/unix socket).
#[derive(Clone)]
pub struct MultiplexedConnection {
    pipeline: Pipeline<Vec<u8>>,
    db: i64,
    response_timeout: Duration,
    protocol: ProtocolVersion,
    push_manager: PushManager,
    availability_zone: Option<String>,
    password: Option<String>,
}

impl Debug for MultiplexedConnection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("MultiplexedConnection")
            .field("pipeline", &self.pipeline)
            .field("db", &self.db)
            .finish()
    }
}

impl MultiplexedConnection {
    /// Constructs a new `MultiplexedConnection` out of a `AsyncRead + AsyncWrite` object
    /// and a `ConnectionInfo`
    pub async fn new<C>(
        connection_info: &ConnectionInfo,
        stream: C,
        glide_connection_options: GlideConnectionOptions,
    ) -> RedisResult<(Self, impl Future<Output = ()>)>
    where
        C: Unpin + AsyncRead + AsyncWrite + Send + 'static,
    {
        Self::new_with_response_timeout(
            connection_info,
            stream,
            std::time::Duration::MAX,
            glide_connection_options,
        )
        .await
    }

    /// Constructs a new `MultiplexedConnection` out of a `AsyncRead + AsyncWrite` object
    /// and a `ConnectionInfo`. The new object will wait on operations for the given `response_timeout`.
    pub async fn new_with_response_timeout<C>(
        connection_info: &ConnectionInfo,
        stream: C,
        response_timeout: std::time::Duration,
        glide_connection_options: GlideConnectionOptions,
    ) -> RedisResult<(Self, impl Future<Output = ()>)>
    where
        C: Unpin + AsyncRead + AsyncWrite + Send + 'static,
    {
        let codec = ValueCodec::default()
            .framed(stream)
            .and_then(|msg| async move { msg });
        let (mut pipeline, driver) =
            Pipeline::new(codec, glide_connection_options.disconnect_notifier);
        let driver = Box::pin(driver);
        let pm = PushManager::default();
        if let Some(sender) = glide_connection_options.push_sender {
            pm.replace_sender(sender);
        }

        pipeline.set_push_manager(pm.clone()).await;

        let mut con = MultiplexedConnection::builder(pipeline)
            .with_db(connection_info.redis.db)
            .with_response_timeout(response_timeout)
            .with_push_manager(pm)
            .with_protocol(connection_info.redis.protocol)
            .with_password(connection_info.redis.password.clone())
            .with_availability_zone(None)
            .build()
            .await?;

        let driver = {
            let auth = setup_connection(
                &connection_info.redis,
                &mut con,
                glide_connection_options.discover_az,
            );

            futures_util::pin_mut!(auth);

            match futures_util::future::select(auth, driver).await {
                futures_util::future::Either::Left((result, driver)) => {
                    result?;
                    driver
                }
                futures_util::future::Either::Right(((), _)) => {
                    return Err(RedisError::from((
                        crate::ErrorKind::IoError,
                        "Multiplexed connection driver unexpectedly terminated",
                    )));
                }
            }
        };

        Ok((con, driver))
    }

    /// Sets the time that the multiplexer will wait for responses on operations before failing.
    pub fn set_response_timeout(&mut self, timeout: std::time::Duration) {
        self.response_timeout = timeout;
    }

    /// Sends an already encoded (packed) command into the TCP socket and
    /// reads the single response from it.
    pub async fn send_packed_command(&mut self, cmd: &Cmd) -> RedisResult<Value> {
        let result = self
            .pipeline
            .send_single(cmd.get_packed_command(), self.response_timeout)
            .await;
        if self.protocol != ProtocolVersion::RESP2 {
            if let Err(e) = &result {
                if e.is_connection_dropped() {
                    // Notify the PushManager that the connection was lost
                    self.push_manager.try_send_raw(&Value::Push {
                        kind: PushKind::Disconnection,
                        data: vec![],
                    });
                }
            }
        }
        result
    }

    /// Sends multiple already encoded (packed) command into the TCP socket
    /// and reads `count` responses from it.  This is used to implement
    /// pipelining.
    pub async fn send_packed_commands(
        &mut self,
        cmd: &crate::Pipeline,
        offset: usize,
        count: usize,
    ) -> RedisResult<Vec<Value>> {
        let result = self
            .pipeline
            .send_recv(
                cmd.get_packed_pipeline(),
                Some(offset + count),
                self.response_timeout,
                cmd.is_atomic(),
            )
            .await;

        if self.protocol != ProtocolVersion::RESP2 {
            if let Err(e) = &result {
                if e.is_connection_dropped() {
                    // Notify the PushManager that the connection was lost
                    self.push_manager.try_send_raw(&Value::Push {
                        kind: PushKind::Disconnection,
                        data: vec![],
                    });
                }
            }
        }
        let value = result?;
        match value {
            Value::Array(mut values) => {
                values.drain(..offset);
                Ok(values)
            }
            _ => Ok(vec![value]),
        }
    }

    /// Sets `PushManager` of connection
    pub async fn set_push_manager(&mut self, push_manager: PushManager) {
        self.push_manager = push_manager.clone();
        self.pipeline.set_push_manager(push_manager).await;
    }

    /// For external visibility (glide-core)
    pub fn get_availability_zone(&self) -> Option<String> {
        self.availability_zone.clone()
    }

    /// Replace the password used to authenticate with the server.
    /// If `None` is provided, the password will be removed.
    pub async fn update_connection_password(
        &mut self,
        password: Option<String>,
    ) -> RedisResult<Value> {
        self.password = password;
        Ok(Value::Okay)
    }

    /// Creates a new `MultiplexedConnectionBuilder` for constructing a `MultiplexedConnection`.
    pub(crate) fn builder(pipeline: Pipeline<Vec<u8>>) -> MultiplexedConnectionBuilder {
        MultiplexedConnectionBuilder::new(pipeline)
    }
}

/// A builder for creating `MultiplexedConnection` instances.
pub struct MultiplexedConnectionBuilder {
    pipeline: Pipeline<Vec<u8>>,
    db: Option<i64>,
    response_timeout: Option<Duration>,
    push_manager: Option<PushManager>,
    protocol: Option<ProtocolVersion>,
    password: Option<String>,
    /// Represents the node's availability zone
    availability_zone: Option<String>,
}

impl MultiplexedConnectionBuilder {
    /// Creates a new builder with the required pipeline
    pub(crate) fn new(pipeline: Pipeline<Vec<u8>>) -> Self {
        Self {
            pipeline,
            db: None,
            response_timeout: None,
            push_manager: None,
            protocol: None,
            password: None,
            availability_zone: None,
        }
    }

    /// Sets the database index for the `MultiplexedConnectionBuilder`.
    pub fn with_db(mut self, db: i64) -> Self {
        self.db = Some(db);
        self
    }

    /// Sets the response timeout for the `MultiplexedConnectionBuilder`.
    pub fn with_response_timeout(mut self, timeout: Duration) -> Self {
        self.response_timeout = Some(timeout);
        self
    }

    /// Sets the push manager for the `MultiplexedConnectionBuilder`.
    pub fn with_push_manager(mut self, push_manager: PushManager) -> Self {
        self.push_manager = Some(push_manager);
        self
    }

    /// Sets the protocol version for the `MultiplexedConnectionBuilder`.
    pub fn with_protocol(mut self, protocol: ProtocolVersion) -> Self {
        self.protocol = Some(protocol);
        self
    }

    /// Sets the password for the `MultiplexedConnectionBuilder`.
    pub fn with_password(mut self, password: Option<String>) -> Self {
        self.password = password;
        self
    }

    /// Sets the avazilability zone for the `MultiplexedConnectionBuilder`.
    pub fn with_availability_zone(mut self, az: Option<String>) -> Self {
        self.availability_zone = az;
        self
    }

    /// Builds and returns a new `MultiplexedConnection` instance using the configured settings.
    pub async fn build(self) -> RedisResult<MultiplexedConnection> {
        let db = self.db.unwrap_or_default();
        let response_timeout = self
            .response_timeout
            .unwrap_or(DEFAULT_CONNECTION_ATTEMPT_TIMEOUT);
        let push_manager = self.push_manager.unwrap_or_default();
        let protocol = self.protocol.unwrap_or_default();
        let password = self.password;

        let con = MultiplexedConnection {
            pipeline: self.pipeline,
            db,
            response_timeout,
            push_manager,
            protocol,
            password,
            availability_zone: self.availability_zone,
        };

        Ok(con)
    }
}

impl ConnectionLike for MultiplexedConnection {
    fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
        (async move { self.send_packed_command(cmd).await }).boxed()
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a crate::Pipeline,
        offset: usize,
        count: usize,
        _pipeline_retry_strategy: Option<PipelineRetryStrategy>,
    ) -> RedisFuture<'a, Vec<Value>> {
        (async move { self.send_packed_commands(cmd, offset, count).await }).boxed()
    }

    fn get_db(&self) -> i64 {
        self.db
    }

    fn is_closed(&self) -> bool {
        self.pipeline.is_closed()
    }

    /// Get the node's availability zone
    fn get_az(&self) -> Option<String> {
        self.availability_zone.clone()
    }

    /// Set the node's availability zone
    fn set_az(&mut self, az: Option<String>) {
        self.availability_zone = az;
    }
}
impl MultiplexedConnection {
    /// Subscribes to a new channel.
    pub async fn subscribe(&mut self, channel_name: String) -> RedisResult<()> {
        if self.protocol == ProtocolVersion::RESP2 {
            return Err(RedisError::from((
                crate::ErrorKind::InvalidClientConfig,
                "RESP3 is required for this command",
            )));
        }
        let mut cmd = cmd("SUBSCRIBE");
        cmd.arg(channel_name.clone());
        cmd.query_async::<_, ()>(self).await?;
        Ok(())
    }

    /// Unsubscribes from channel.
    pub async fn unsubscribe(&mut self, channel_name: String) -> RedisResult<()> {
        if self.protocol == ProtocolVersion::RESP2 {
            return Err(RedisError::from((
                crate::ErrorKind::InvalidClientConfig,
                "RESP3 is required for this command",
            )));
        }
        let mut cmd = cmd("UNSUBSCRIBE");
        cmd.arg(channel_name);
        cmd.query_async::<_, ()>(self).await?;
        Ok(())
    }

    /// Subscribes to a new channel with pattern.
    pub async fn psubscribe(&mut self, channel_pattern: String) -> RedisResult<()> {
        if self.protocol == ProtocolVersion::RESP2 {
            return Err(RedisError::from((
                crate::ErrorKind::InvalidClientConfig,
                "RESP3 is required for this command",
            )));
        }
        let mut cmd = cmd("PSUBSCRIBE");
        cmd.arg(channel_pattern.clone());
        cmd.query_async::<_, ()>(self).await?;
        Ok(())
    }

    /// Unsubscribes from channel pattern.
    pub async fn punsubscribe(&mut self, channel_pattern: String) -> RedisResult<()> {
        if self.protocol == ProtocolVersion::RESP2 {
            return Err(RedisError::from((
                crate::ErrorKind::InvalidClientConfig,
                "RESP3 is required for this command",
            )));
        }
        let mut cmd = cmd("PUNSUBSCRIBE");
        cmd.arg(channel_pattern);
        cmd.query_async::<_, ()>(self).await?;
        Ok(())
    }

    /// Returns `PushManager` of Connection, this method is used to subscribe/unsubscribe from Push types
    pub fn get_push_manager(&self) -> PushManager {
        self.push_manager.clone()
    }
}
