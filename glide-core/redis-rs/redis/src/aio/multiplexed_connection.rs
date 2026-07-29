use super::{ConnectionLike, Runtime};
use crate::aio::setup_connection;
use crate::aio::DisconnectNotifier;
use crate::cache::glide_cache::GlideCache;
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
use logger_core::log_error;
use pin_project_lite::pin_project;
use std::collections::VecDeque;
use std::fmt;
use std::fmt::Debug;
use std::pin::Pin;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::task::{self, Poll};
use std::time::Duration;

// Default connection timeout in ms
const DEFAULT_CONNECTION_ATTEMPT_TIMEOUT: Duration = Duration::from_millis(2000);

/// Flush-directly threshold for the vectored sink's queued bytes. Above this,
/// `poll_ready` applies backpressure by flushing before accepting more items.
const VECTORED_SINK_HIGH_WATERMARK: usize = 512 * 1024;
/// Max iovec entries per `write_vectored` call (typical kernel UIO_MAXIOV is
/// 1024; 64 keeps the stack array small while amortizing syscalls well).
const MAX_WRITE_SLICES: usize = 64;

pin_project! {
    /// Send-side zero-copy sink: queues the segments of packed commands and
    /// writes them with vectored I/O. Large shared payloads
    /// ([`crate::cmd::SegmentedBytes`]) go from the caller's allocation
    /// straight to the socket without ever being copied into a write buffer.
    struct VectoredSink<W> {
        #[pin]
        writer: W,
        queue: VecDeque<bytes::Bytes>,
        queued_bytes: usize,
    }
}

impl<W> VectoredSink<W> {
    fn new(writer: W) -> Self {
        VectoredSink {
            writer,
            queue: VecDeque::new(),
            queued_bytes: 0,
        }
    }
}

impl<W: AsyncWrite> VectoredSink<W> {
    /// Drive queued segments into the writer. Completes when the queue is
    /// empty and the writer is flushed.
    fn poll_flush_queue(
        self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), RedisError>> {
        let mut this = self.project();
        loop {
            if this.queue.is_empty() {
                return this
                    .writer
                    .as_mut()
                    .poll_flush(cx)
                    .map_err(RedisError::from);
            }
            let mut slices = [std::io::IoSlice::new(&[]); MAX_WRITE_SLICES];
            let mut n = 0;
            for seg in this.queue.iter().take(MAX_WRITE_SLICES) {
                slices[n] = std::io::IoSlice::new(seg);
                n += 1;
            }
            match ready!(this.writer.as_mut().poll_write_vectored(cx, &slices[..n])) {
                Ok(0) => {
                    return Poll::Ready(Err(RedisError::from(std::io::Error::new(
                        std::io::ErrorKind::WriteZero,
                        "failed to write queued segments to socket",
                    ))));
                }
                Ok(mut written) => {
                    *this.queued_bytes -= written;
                    while written > 0 {
                        let front = this.queue.front_mut().expect("queue can't be empty");
                        if written >= front.len() {
                            written -= front.len();
                            this.queue.pop_front();
                        } else {
                            bytes::Buf::advance(front, written);
                            written = 0;
                        }
                    }
                }
                Err(err) => return Poll::Ready(Err(err.into())),
            }
        }
    }
}

impl<W: AsyncWrite> Sink<crate::cmd::SendBuf> for VectoredSink<W> {
    type Error = RedisError;

    fn poll_ready(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        if self.queued_bytes <= VECTORED_SINK_HIGH_WATERMARK {
            return Poll::Ready(Ok(()));
        }
        // Backpressure: drain before accepting more.
        self.poll_flush_queue(cx)
    }

    fn start_send(self: Pin<&mut Self>, item: crate::cmd::SendBuf) -> Result<(), Self::Error> {
        let this = self.project();
        match item {
            crate::cmd::SendBuf::Contiguous(buf) => {
                if !buf.is_empty() {
                    *this.queued_bytes += buf.len();
                    // from_owner: avoids Bytes::from(Vec)'s shrink-realloc
                    // on excess capacity.
                    this.queue.push_back(bytes::Bytes::from_owner(buf));
                }
            }
            crate::cmd::SendBuf::Segmented(segments) => {
                for segment in segments.into_segments() {
                    if !segment.is_empty() {
                        *this.queued_bytes += segment.len();
                        this.queue.push_back(segment);
                    }
                }
            }
        }
        Ok(())
    }

    fn poll_flush(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        self.poll_flush_queue(cx)
    }

    fn poll_close(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        ready!(self.as_mut().poll_flush_queue(cx))?;
        self.project()
            .writer
            .poll_shutdown(cx)
            .map_err(RedisError::from)
    }
}

pin_project! {
    /// Combines the decode stream (read half) and the vectored sink (write
    /// half) back into one `Stream + Sink` object for [`Pipeline::new`].
    struct SplitSinkStream<R, W> {
        #[pin]
        read: R,
        #[pin]
        write: W,
    }
}

impl<R, W> Stream for SplitSinkStream<R, W>
where
    R: Stream<Item = RedisResult<Value>>,
{
    type Item = RedisResult<Value>;

    fn poll_next(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Option<Self::Item>> {
        self.project().read.poll_next(cx)
    }
}

impl<R, W> Sink<crate::cmd::SendBuf> for SplitSinkStream<R, W>
where
    W: Sink<crate::cmd::SendBuf, Error = RedisError>,
{
    type Error = RedisError;

    fn poll_ready(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        self.project().write.poll_ready(cx)
    }

    fn start_send(self: Pin<&mut Self>, item: crate::cmd::SendBuf) -> Result<(), Self::Error> {
        self.project().write.start_send(item)
    }

    fn poll_flush(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        self.project().write.poll_flush(cx)
    }

    fn poll_close(self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        self.project().write.poll_close(cx)
    }
}

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
    is_fenced: bool,
    fenced_result: Option<RedisResult<Value>>,
}

// A single message sent through the pipeline
struct PipelineMessage<S> {
    input: S,
    output: PipelineOutput,
    // If `None`, this is a single request, not a pipeline of multiple requests.
    pipeline_response_count: Option<usize>,
    is_transaction: bool,
    is_fenced: bool,
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
    /// Monotonic liveness counter bumped by the writer task on each unit of
    /// progress: when it drains a message from the channel into the sink (a freed
    /// slot, in `start_send`) and when it receives a server response (in
    /// `poll_read`). Producers use it while waiting for a free channel slot: a
    /// connection that keeps making progress is alive (slow), not dead, so the
    /// send must wait rather than fail. The `start_send` bump is the load-bearing
    /// one — under sustained backpressure the `Forward` combinator starves
    /// response reading, so a response-only signal would freeze. See
    /// [`Self::send_recv`].
    progress: Arc<AtomicU64>,
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
        response_sync_lost: bool,
        cache: Option<Arc<dyn GlideCache>>,
        progress: Arc<AtomicU64>,
    }

        impl<T> PinnedDrop for PipelineSink<T> {
        fn drop(this: Pin<&mut Self>) {
            let this = this.project();
            let push_manager = this.push_manager.load();
            let address = push_manager.get_address();

            if let Some(address) = address {
                if let Some(sync) = push_manager.get_synchronizer() {
                    let addresses = std::collections::HashSet::from([address.clone()]);
                    sync.remove_current_subscriptions_for_addresses(&addresses);
                }
            }
        }
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
        cache: Option<Arc<dyn GlideCache>>,
        progress: Arc<AtomicU64>,
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
            response_sync_lost: false,
            cache,
            progress,
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
            // A response (or push) arrived from the server: record liveness so
            // producers blocked on a full channel can tell "slow" from "dead".
            self.progress.fetch_add(1, Ordering::Relaxed);
            self.as_mut().send_result(item);
        }
    }

    fn send_result(self: Pin<&mut Self>, result: RedisResult<Value>) {
        let self_ = self.project();

        // If response synchronization is lost, fail all requests
        if *self_.response_sync_lost {
            if let Some(entry) = self_.in_flight.pop_front() {
                let err = RedisError::from((
                    crate::ErrorKind::ProtocolDesync,
                    "Response synchronization lost - connection must be reestablished",
                ));
                entry.output.send(Err(err)).ok();
            }
            return;
        }

        if let Ok(res) = &result {
            if let Value::Push { kind, data: _data } = res {
                self_.push_manager.load().try_send_raw(res);
                if kind == &PushKind::Invalidate {
                    if let Some(cache) = self_.cache {
                        match _data.first() {
                            Some(Value::Array(keys)) => {
                                for key in keys {
                                    if let Value::BulkString(k) = key {
                                        cache.invalidate(k);
                                    } else if let Value::VerbatimString { text, .. } = key {
                                        cache.invalidate(text.as_bytes());
                                    }
                                }
                            }
                            Some(Value::Nil) => {
                                cache.flush_all();
                            }
                            None => { /* malformed push, ignore */ }
                            _ => {}
                        }
                    }
                }
                if !kind.has_reply() {
                    return;
                }
            }
        }

        let mut entry = match self_.in_flight.pop_front() {
            Some(entry) => entry,
            None => return,
        };

        // Handle fenced commands
        if entry.is_fenced {
            Self::handle_fenced_command(entry, result, self_.in_flight, self_.response_sync_lost);
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
    /// Handles fenced command responses.
    ///
    /// Fenced commands are commands followed by a PING to ensure ordering.
    /// They receive two responses:
    /// 1. The actual command response (or error, or nothing in case there is no returned response)
    /// 2. PONG from the trailing PING
    ///
    /// This function is only called for commands where `is_fenced` is true.
    fn handle_fenced_command(
        mut entry: InFlight,
        result: RedisResult<Value>,
        in_flight: &mut VecDeque<InFlight>,
        response_sync_lost: &mut bool,
    ) {
        // Check if we already have a stored result (this is the second response - PONG)
        if let Some(stored_result) = entry.fenced_result.take() {
            Self::handle_fenced_second_response(entry, result, stored_result, response_sync_lost);
        } else {
            // This is the first response from the fenced command
            Self::handle_fenced_first_response(entry, result, in_flight);
        }
    }

    /// Handles the first response of a fenced command.
    fn handle_fenced_first_response(
        mut entry: InFlight,
        result: RedisResult<Value>,
        in_flight: &mut VecDeque<InFlight>,
    ) {
        match result {
            // Case 1: First response is PONG
            // This means the fenced command had no response
            Ok(Value::SimpleString(ref s)) if s == "PONG" || s == "pong" => {
                // Return Ok(Nil) to indicate success with no data
                entry.output.send(Ok(Value::Nil)).ok();
            }

            // Case 2: First response is an error
            // Store it and wait for PONG
            Err(err) => {
                entry.fenced_result = Some(Err(err));
                in_flight.push_front(entry);
            }

            // Case 3: First response is a value (not PONG)
            // Store it and wait for PONG
            Ok(value) => {
                entry.fenced_result = Some(Ok(value));
                in_flight.push_front(entry);
            }
        }
    }

    /// Handles the second response of a fenced command (should be PONG).
    fn handle_fenced_second_response(
        entry: InFlight,
        pong_result: RedisResult<Value>,
        stored_result: RedisResult<Value>,
        response_sync_lost: &mut bool,
    ) {
        // Verify we got PONG
        let is_pong = matches!(
            &pong_result,
            Ok(Value::SimpleString(s)) if s == "PONG"
        );

        if !is_pong {
            // Set the flag - all future commands will fail
            *response_sync_lost = true;

            log_error(
                "Fenced command",
                "CRITICAL: Expected PONG for fenced command but got unexpected response.
                Response synchronization lost. All commands will fail until reconnection.",
            );

            // Fail the current command
            let err = RedisError::from((
                crate::ErrorKind::ProtocolDesync,
                "Expected PONG for fenced command but received different response",
                format!("Response synchronization lost. Got: {:?}", pong_result),
            ));
            entry.output.send(Err(err)).ok();
            return;
        }

        // ✅ Got PONG as expected, return the stored result
        let final_result = stored_result.and_then(|v| v.extract_error());
        entry.output.send(final_result).ok();
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
        match self.as_mut().project().sink_stream.poll_ready(cx) {
            Poll::Ready(Ok(())) => Ok(()).into(),
            Poll::Ready(Err(err)) => {
                *self.project().error = Some(err);
                Ok(()).into()
            }
            Poll::Pending => {
                // Write side is blocked (TCP send buffer full). Drain incoming
                // responses so the server can free its send buffer and unblock
                // our writes. Without this, a TCP deadlock occurs with large
                // payloads. See https://github.com/redis-rs/redis-rs/issues/1955.
                //
                // Note: upstream redis-rs calls poll_read unconditionally at the
                // top (before checking poll_ready). We only call it in the Pending
                // path because unconditional poll_read registers the read waker,
                // causing spurious wakeups that starve concurrent request
                // processing and exhaust the inflight request limit.
                if matches!(self.as_mut().poll_read(cx), Poll::Ready(Err(()))) {
                    return Poll::Ready(Err(()));
                }
                Poll::Pending
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
            is_fenced,
        }: PipelineMessage<SinkItem>,
    ) -> Result<(), Self::Error> {
        // A message was pulled from the channel into the sink, so a channel slot
        // just freed: the writer is making progress. Producers waiting on a full
        // channel use this as a liveness signal to tell backpressure from a dead
        // connection (see `send_recv`). It is recorded here — for every pulled
        // message, including the load-shed and error paths below, since a slot
        // frees regardless of outcome — as well as on response receipt, because
        // under sustained backpressure the `Forward` combinator keeps draining the
        // channel and starves the response-reading path.
        self.as_mut()
            .project()
            .progress
            .fetch_add(1, Ordering::Relaxed);

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

        if *self_.response_sync_lost {
            let err = RedisError::from((
                crate::ErrorKind::ProtocolDesync,
                "Response synchronization lost - connection must be reestablished",
            ));
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
                    is_fenced,
                    fenced_result: None,
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
        let flush_result = self
            .as_mut()
            .project()
            .sink_stream
            .poll_flush(cx)
            .map_err(|err| {
                self.as_mut().send_result(Err(err));
            })?;
        if flush_result.is_ready() {
            self.poll_read(cx)
        } else {
            // Flush is blocked (TCP send buffer full). Drain incoming responses
            // so the server can free its send buffer and unblock our writes.
            // Without this, a TCP deadlock occurs with large payloads.
            // See https://github.com/redis-rs/redis-rs/issues/1955.
            //
            // Note: upstream redis-rs calls poll_read unconditionally at the top
            // and removes the ready!/poll_read-after-flush pattern. We keep
            // poll_read only in the Pending path (and retain the post-flush
            // poll_read for throughput) because unconditional poll_read registers
            // the read waker on every call, causing spurious wakeups that starve
            // concurrent request processing and exhaust the inflight request
            // limit unique to valkey-glide.
            if matches!(self.as_mut().poll_read(cx), Poll::Ready(Err(()))) {
                return Poll::Ready(Err(()));
            }
            Poll::Pending
        }
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
    /// Default capacity of the bounded channel connecting request producers to the
    /// single pipeline writer task. Sized to the Little's Law steady-state inflight
    /// estimate (~50 requests; see the derivation above `DEFAULT_MAX_INFLIGHT_REQUESTS`
    /// in glide-core/src/client/mod.rs). The inflight limit itself
    /// (`DEFAULT_MAX_INFLIGHT_REQUESTS` = 1000) sits above this channel and provides
    /// burst headroom.
    const DEFAULT_BUFFER_SIZE: usize = 50;

    fn new<T>(
        sink_stream: T,
        disconnect_notifier: Option<Box<dyn DisconnectNotifier>>,
        cache: Option<Arc<dyn GlideCache>>,
    ) -> (Self, impl Future<Output = ()>)
    where
        T: Sink<SinkItem, Error = RedisError> + Stream<Item = RedisResult<Value>> + 'static,
        T: Send + 'static,
        T::Item: Send,
        T::Error: Send,
        T::Error: ::std::fmt::Debug,
    {
        Self::new_with_buffer_size(
            sink_stream,
            disconnect_notifier,
            cache,
            Self::DEFAULT_BUFFER_SIZE,
        )
    }

    /// Like [`Self::new`] but with an explicit capacity for the bounded channel
    /// connecting request producers to the writer task.
    ///
    /// This channel only becomes a bottleneck when the writer drains it more
    /// slowly than producers fill it — i.e. when the socket write path is blocked
    /// (full TCP send buffer), which happens under high latency and/or large
    /// payloads. A larger capacity raises burst headroom before producers block,
    /// at the cost of more memory held in the channel (≈ capacity × payload size)
    /// and weaker backpressure pacing of the writer task.
    fn new_with_buffer_size<T>(
        sink_stream: T,
        disconnect_notifier: Option<Box<dyn DisconnectNotifier>>,
        cache: Option<Arc<dyn GlideCache>>,
        buffer_size: usize,
    ) -> (Self, impl Future<Output = ()>)
    where
        T: Sink<SinkItem, Error = RedisError> + Stream<Item = RedisResult<Value>> + 'static,
        T: Send + 'static,
        T::Item: Send,
        T::Error: Send,
        T::Error: ::std::fmt::Debug,
    {
        let (sender, mut receiver) = mpsc::channel(buffer_size);
        let push_manager: Arc<ArcSwap<PushManager>> =
            Arc::new(ArcSwap::new(Arc::new(PushManager::default())));
        let is_stream_closed = Arc::new(AtomicBool::new(false));
        let progress = Arc::new(AtomicU64::new(0));
        let sink = PipelineSink::new::<SinkItem>(
            sink_stream,
            push_manager.clone(),
            disconnect_notifier,
            is_stream_closed.clone(),
            cache,
            progress.clone(),
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
                progress,
            },
            f,
        )
    }

    // `None` means that the stream was out of items causing that poll loop to shut down.
    async fn send_single(
        &mut self,
        item: SinkItem,
        timeout: Duration,
        is_fenced: bool,
        is_blocking: bool,
    ) -> RedisResult<Value> {
        self.send_recv(item, None, timeout, true, is_fenced, is_blocking)
            .await
    }

    async fn send_recv(
        &mut self,
        input: SinkItem,
        // If `None`, this is a single request, not a pipeline of multiple requests.
        pipeline_response_count: Option<usize>,
        timeout: Duration,
        is_atomic: bool,
        is_fenced: bool,
        is_blocking: bool,
    ) -> Result<Value, RedisError> {
        let (sender, receiver) = oneshot::channel();

        // Acquire a slot in the bounded pipeline channel, distinguishing a
        // slow-but-live connection from a dead one. We poll for capacity in short
        // "liveness ticks" and watch the writer's progress counter (bumped when it
        // drains a message into the sink or receives a response). If consecutive
        // ticks elapse with the channel still full and NO progress, the writer is
        // stuck and the connection is treated as dead (FatalSendError), preserving
        // fast dead-connection detection (#5715). While progress continues the
        // channel is merely under backpressure, so we keep waiting up to the request
        // `timeout` rather than failing a live command (#5446).
        let liveness_tick = std::cmp::max(
            std::cmp::min(timeout, std::time::Duration::from_millis(100)),
            std::time::Duration::from_millis(1),
        );
        // Require consecutive no-progress ticks before declaring death, so a single
        // scheduling gap (e.g. the writer task not yet polled at startup) cannot
        // kill a live connection.
        const DEAD_TICKS: u32 = 2;
        let send_start = std::time::Instant::now();
        let mut no_progress_ticks = 0u32;
        // Fast path: if a channel slot is immediately available (the common,
        // non-contended case) take it without arming the liveness-timeout
        // machinery — no per-send timeout future, no atomic progress load. The
        // dead-connection/backpressure detection below is only required when the
        // channel is actually full, so keep the hot path bare.
        let permit = match self.sender.try_reserve() {
            Ok(permit) => permit,
            Err(mpsc::error::TrySendError::Closed(())) => {
                return Err(RedisError::from((
                    crate::ErrorKind::FatalSendError,
                    "Failed to send the request to the server",
                    "the pipeline writer task has terminated".to_string(),
                )));
            }
            // Channel full: fall back to the liveness-aware slow path (unchanged).
            Err(mpsc::error::TrySendError::Full(())) => loop {
                let progress_before = self.progress.load(Ordering::Relaxed);
                match tokio::time::timeout(liveness_tick, self.sender.reserve()).await {
                    Ok(Ok(permit)) => break permit,
                    Ok(Err(_closed)) => {
                        return Err(RedisError::from((
                            crate::ErrorKind::FatalSendError,
                            "Failed to send the request to the server",
                            "the pipeline writer task has terminated".to_string(),
                        )));
                    }
                    Err(_elapsed) => {
                        if send_start.elapsed() >= timeout {
                            // Backpressure outlasted the request's own timeout budget.
                            // Report a genuine timeout (NoRetry, is_timeout()), matching
                            // the receive-side timeout — not a fatal/reconnect send error.
                            return Err(std::io::Error::new(
                                std::io::ErrorKind::TimedOut,
                                "Timed out waiting for pipeline send capacity",
                            )
                            .into());
                        }
                        if self.progress.load(Ordering::Relaxed) == progress_before {
                            no_progress_ticks += 1;
                            if no_progress_ticks >= DEAD_TICKS {
                                // No progress across consecutive ticks while the channel
                                // stays full: the writer is stuck, not merely slow.
                                return Err(RedisError::from((
                                    crate::ErrorKind::FatalSendError,
                                    "Pipeline channel full — connection likely dead",
                                )));
                            }
                        } else {
                            // A slot freed or a response arrived: backpressure, not
                            // death. Reset the dead-tick counter and keep waiting.
                            no_progress_ticks = 0;
                        }
                    }
                }
            },
        };
        permit.send(PipelineMessage {
            input,
            pipeline_response_count,
            output: sender,
            is_transaction: is_atomic,
            is_fenced,
        });
        let send_elapsed = send_start.elapsed();
        let send_warn_threshold = std::cmp::min(timeout / 4, std::time::Duration::from_millis(500));
        if send_elapsed > send_warn_threshold {
            logger_core::log_warn_rate_limited!(
                "pipeline",
                5,
                format!(
                    "pipeline.send() blocked for {:?} (threshold={:?}, response_timeout={:?})",
                    send_elapsed, send_warn_threshold, timeout
                )
            );
        }
        let recv_start = std::time::Instant::now();
        let recv_result = Runtime::locate().timeout(timeout, receiver).await;
        let recv_elapsed = recv_start.elapsed();
        // For blocking commands (e.g. XREAD BLOCK, BLPOP) the client intentionally
        // waits for a server-side event, so a long receive time is expected and not
        // indicative of a slow connection.  Since recv_elapsed cannot exceed the
        // request timeout, simply skip the warning for blocking commands rather than
        // padding the threshold.  For non-blocking commands, keep the original
        // heuristic (min(timeout/2, 5s)).
        let recv_warn_threshold = std::cmp::min(timeout / 2, std::time::Duration::from_secs(5));
        if !is_blocking && recv_elapsed > recv_warn_threshold {
            logger_core::log_warn_rate_limited!(
                "pipeline",
                5,
                format!(
                    "Response wait took {:?} (threshold={:?}, response_timeout={:?})",
                    recv_elapsed, recv_warn_threshold, timeout
                )
            );
        }
        match recv_result {
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
    fn set_push_manager(&mut self, push_manager: PushManager) {
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
    pipeline: Pipeline<crate::cmd::SendBuf>,
    db: i64,
    response_timeout: Duration,
    protocol: ProtocolVersion,
    push_manager: PushManager,
    availability_zone: Option<String>,
    password: Option<String>,
    cache: Option<Arc<dyn GlideCache>>,
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
        let (read_half, write_half) = ::tokio::io::split(stream);
        let read_stream = tokio_util::codec::FramedRead::new(read_half, ValueCodec::default())
            .and_then(|msg| async move { msg });
        let codec = SplitSinkStream {
            read: read_stream,
            write: VectoredSink::new(write_half),
        };
        let (mut pipeline, driver) = Pipeline::new(
            codec,
            glide_connection_options.disconnect_notifier,
            connection_info.redis.cache.clone(),
        );
        let driver = Box::pin(driver);
        let pm = PushManager::new(
            glide_connection_options.push_sender,
            glide_connection_options.pubsub_synchronizer,
            Some(connection_info.addr.to_string()),
        );

        pipeline.set_push_manager(pm.clone());

        let mut con = MultiplexedConnection::builder(pipeline)
            .with_db(connection_info.redis.db)
            .with_response_timeout(response_timeout)
            .with_push_manager(pm)
            .with_protocol(connection_info.redis.protocol)
            .with_password(connection_info.redis.password.clone())
            .with_availability_zone(None)
            .with_cache(connection_info.redis.cache.clone())
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
        // First try to get from cache
        if let Some(cache) = &self.cache {
            if let Some(value) = cache.get_cached_cmd(cmd) {
                return Ok(value);
            }
        }
        let timeout = cmd.response_timeout().unwrap_or(self.response_timeout);
        let result = self
            .pipeline
            .send_single(
                // Commands with no out-of-line payloads skip the segmented
                // representation entirely (see SendBuf::Contiguous).
                if cmd.has_out_of_line_args() {
                    crate::cmd::SendBuf::Segmented(cmd.get_packed_segments())
                } else {
                    crate::cmd::SendBuf::Contiguous(cmd.get_packed_command())
                },
                timeout,
                cmd.is_fenced(),
                cmd.is_blocking(),
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

        // Store in cache if applicable
        if let Some(cache) = &self.cache {
            if let Ok(value) = &result {
                if *value != Value::Nil {
                    cache.set_cached_cmd(cmd, value.clone());
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
                crate::cmd::SendBuf::Segmented(cmd.get_packed_pipeline_segments()),
                Some(offset + count),
                self.response_timeout,
                cmd.is_atomic(),
                false,
                false,
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
        self.pipeline.set_push_manager(push_manager);
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
    pub(crate) fn builder(pipeline: Pipeline<crate::cmd::SendBuf>) -> MultiplexedConnectionBuilder {
        MultiplexedConnectionBuilder::new(pipeline)
    }

    /// Update the node address used for PubSub tracking.
    /// This updates both the Pipeline's shared PushManager and the local copy.
    pub fn update_push_manager_node_address(&mut self, address: String) {
        let updated_pm = self.push_manager.with_address(address);
        self.pipeline.set_push_manager(updated_pm.clone());
        self.push_manager = updated_pm;
    }
}

/// A builder for creating `MultiplexedConnection` instances.
pub struct MultiplexedConnectionBuilder {
    pipeline: Pipeline<crate::cmd::SendBuf>,
    db: Option<i64>,
    response_timeout: Option<Duration>,
    push_manager: Option<PushManager>,
    protocol: Option<ProtocolVersion>,
    password: Option<String>,
    /// Represents the node's availability zone
    availability_zone: Option<String>,
    /// Client-side cache
    cache: Option<Arc<dyn GlideCache>>,
}

impl MultiplexedConnectionBuilder {
    /// Creates a new builder with the required pipeline
    pub(crate) fn new(pipeline: Pipeline<crate::cmd::SendBuf>) -> Self {
        Self {
            pipeline,
            db: None,
            response_timeout: None,
            push_manager: None,
            protocol: None,
            password: None,
            availability_zone: None,
            cache: None,
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

    /// Sets the cache for the `MultiplexedConnectionBuilder`.
    pub fn with_cache(mut self, cache: Option<Arc<dyn GlideCache>>) -> Self {
        self.cache = cache;
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
            cache: self.cache,
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

    fn update_push_manager_node_address(&mut self, address: String) {
        MultiplexedConnection::update_push_manager_node_address(self, address);
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

#[cfg(test)]
mod tests {
    use super::*;
    use futures::channel::mpsc as futures_mpsc;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::task::{Context, Poll};
    use std::time::Duration;

    /// A Sink+Stream where poll_ready/poll_flush return Pending when stall flag is set,
    /// simulating a dead TCP connection where the send buffer is full.
    struct StallingSink {
        stall: Arc<AtomicBool>,
        inner_tx: futures_mpsc::Sender<Vec<u8>>,
        inner_rx: futures_mpsc::Receiver<RedisResult<Value>>,
    }

    impl Stream for StallingSink {
        type Item = RedisResult<Value>;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            Pin::new(&mut self.inner_rx).poll_next(cx)
        }
    }

    impl Sink<Vec<u8>> for StallingSink {
        type Error = RedisError;

        fn poll_ready(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
            if self.stall.load(Ordering::Relaxed) {
                cx.waker().wake_by_ref();
                return Poll::Pending;
            }
            Pin::new(&mut self.get_mut().inner_tx)
                .poll_ready(cx)
                .map_err(|e| {
                    RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
                })
        }

        fn start_send(mut self: Pin<&mut Self>, item: Vec<u8>) -> Result<(), Self::Error> {
            Pin::new(&mut self.inner_tx).start_send(item).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }

        fn poll_flush(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            if self.stall.load(Ordering::Relaxed) {
                cx.waker().wake_by_ref();
                return Poll::Pending;
            }
            Pin::new(&mut self.inner_tx).poll_flush(cx).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }

        fn poll_close(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Pin::new(&mut self.inner_tx).poll_close(cx).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }
    }

    /// A mock connection used to benchmark the pipeline buffer in isolation,
    /// without real sockets or a server.
    ///
    /// It models a real connection over the network:
    /// - every written command produces one `+OK` response after `latency` (the RTT);
    /// - `poll_ready` returns `Pending` once `window` commands are outstanding
    ///   (written but not yet responded to), modelling a bounded TCP send buffer.
    ///
    /// Backpressure from `window` is what makes the pipeline's internal channel
    /// fill, so the configured buffer size only affects behaviour when `window` is
    /// small relative to the offered concurrency. With `window == usize::MAX`
    /// there is no backpressure and the buffer size is irrelevant.
    struct MockServerSink {
        cmd_tx: mpsc::UnboundedSender<Vec<u8>>,
        resp_rx: mpsc::UnboundedReceiver<RedisResult<Value>>,
        shared: Arc<MockShared>,
        window: usize,
    }

    struct MockShared {
        outstanding: std::sync::atomic::AtomicUsize,
        max_outstanding: std::sync::atomic::AtomicUsize,
        ready_waker: futures::task::AtomicWaker,
    }

    impl MockServerSink {
        /// Builds the mock sink plus the server future that produces delayed
        /// responses. The caller must spawn the returned future on the runtime.
        fn new(latency: Duration, window: usize) -> (Self, impl Future<Output = ()>) {
            let (cmd_tx, mut cmd_rx) = mpsc::unbounded_channel::<Vec<u8>>();
            let (resp_tx, resp_rx) = mpsc::unbounded_channel::<RedisResult<Value>>();
            let shared = Arc::new(MockShared {
                outstanding: std::sync::atomic::AtomicUsize::new(0),
                max_outstanding: std::sync::atomic::AtomicUsize::new(0),
                ready_waker: futures::task::AtomicWaker::new(),
            });

            let server_shared = shared.clone();
            let server = async move {
                // One delayed responder per command models a pipelined server,
                // where many RTTs overlap rather than being serialised.
                while let Some(bytes) = cmd_rx.recv().await {
                    let resp_tx = resp_tx.clone();
                    let shared = server_shared.clone();
                    tokio::spawn(async move {
                        if !latency.is_zero() {
                            tokio::time::sleep(latency).await;
                        }
                        // Hold the payload for the "RTT" to model data in flight,
                        // then drop it before acking.
                        drop(bytes);
                        let _ = resp_tx.send(Ok(Value::Okay));
                        shared.outstanding.fetch_sub(1, Ordering::SeqCst);
                        shared.ready_waker.wake();
                    });
                }
            };

            (
                MockServerSink {
                    cmd_tx,
                    resp_rx,
                    shared,
                    window,
                },
                server,
            )
        }
    }

    impl Stream for MockServerSink {
        type Item = RedisResult<Value>;
        fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.get_mut().resp_rx.poll_recv(cx)
        }
    }

    impl Sink<Vec<u8>> for MockServerSink {
        type Error = RedisError;

        fn poll_ready(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
            if self.shared.outstanding.load(Ordering::SeqCst) < self.window {
                return Poll::Ready(Ok(()));
            }
            // Window full: register for wakeup when a response frees a slot, then
            // re-check to avoid a lost wakeup race.
            self.shared.ready_waker.register(cx.waker());
            if self.shared.outstanding.load(Ordering::SeqCst) < self.window {
                Poll::Ready(Ok(()))
            } else {
                Poll::Pending
            }
        }

        fn start_send(self: Pin<&mut Self>, item: Vec<u8>) -> Result<(), Self::Error> {
            let cur = self.shared.outstanding.fetch_add(1, Ordering::SeqCst) + 1;
            self.shared.max_outstanding.fetch_max(cur, Ordering::SeqCst);
            self.cmd_tx.send(item).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "mock send error", e.to_string()))
            })
        }

        fn poll_flush(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }

        fn poll_close(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }
    }

    #[tokio::test]
    async fn test_pipeline_send_timeout_when_sink_stalls() {
        // Test for #5715: Pipeline send blocks forever on dead shard
        // See: https://github.com/valkey-io/valkey-glide/issues/5715
        //
        // When a TCP connection becomes half-open (e.g., network partition without RST),
        // the send buffer fills and AsyncWrite::poll_write blocks indefinitely. This
        // causes the pipeline's internal channel to fill, and any subsequent send()
        // call blocks forever waiting for a slot.
        //
        // The liveness-aware send loop detects this: with the channel full and the
        // writer making no progress (no slot freed, no response) across consecutive
        // liveness ticks, it fails with FatalSendError instead of hanging. This test
        // creates a pipeline with no driver (never drains, never makes progress),
        // fills the channel, and asserts the next send fails fast with FatalSendError.

        let stall_flag = Arc::new(AtomicBool::new(false));
        let (sink_tx, _sink_rx) = futures_mpsc::channel(100);
        let (_resp_tx, resp_rx) = futures_mpsc::channel(100);

        let stalling_sink = StallingSink {
            stall: stall_flag.clone(),
            inner_tx: sink_tx,
            inner_rx: resp_rx,
        };

        // Create pipeline but don't drive it, the channel will fill and send() will block
        let (mut pipeline, driver) = Pipeline::new(stalling_sink, None, None);
        std::mem::forget(driver);

        // Fill the 50-slot pipeline channel
        for _ in 0..50 {
            let mut pipeline_clone = pipeline.clone();
            tokio::spawn(async move {
                let _ = pipeline_clone
                    .send_single(
                        crate::cmd("PING").get_packed_command(),
                        Duration::from_secs(60),
                        false,
                        false,
                    )
                    .await;
            });
        }
        tokio::time::sleep(Duration::from_millis(50)).await;

        // The request timeout is generous (2s) so that the dead-connection path
        // (fired after DEAD_TICKS no-progress liveness ticks, ~2 × 100ms) is what
        // trips — not the overall request-timeout budget. Without the fix this send
        // would block forever.
        let timeout = Duration::from_secs(2);
        let start = std::time::Instant::now();
        let result = pipeline
            .send_single(
                crate::cmd("PING").get_packed_command(),
                timeout,
                false,
                false,
            )
            .await;
        let elapsed = start.elapsed();

        assert!(result.is_err(), "Expected error when sink is stalled");
        let err = result.unwrap_err();
        assert_eq!(
            err.kind(),
            crate::ErrorKind::FatalSendError,
            "dead connection must fail fast via the no-progress dead path, got: {:?}",
            err.kind()
        );
        assert!(
            elapsed < Duration::from_secs(1),
            "Send took {:?}, expected to fail fast (~2 liveness ticks), well under \
             the 2s request timeout. Without the fix, this hangs forever.",
            elapsed,
        );
    }

    /// A Sink+Stream that simulates the TCP deadlock condition:
    /// - The write side (poll_ready/poll_flush) returns Pending (simulating full TCP send buffer)
    /// - The read side (poll_next) has responses available that must be drained
    ///
    /// If poll_read is NOT called during poll_ready/poll_flush, the responses will never
    /// be delivered to the caller, causing a deadlock.
    struct DeadlockProneStream {
        /// When true, poll_ready and poll_flush return Pending (simulating TCP backpressure)
        write_blocked: Arc<AtomicBool>,
        /// Channel for sending bytes (the write/sink side)
        inner_tx: futures_mpsc::Sender<Vec<u8>>,
        /// Channel for receiving responses (the read/stream side)
        inner_rx: futures_mpsc::Receiver<RedisResult<Value>>,
        /// Waker storage so we can wake the task when unblocking
        waker: Option<task::Waker>,
    }

    impl Stream for DeadlockProneStream {
        type Item = RedisResult<Value>;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            Pin::new(&mut self.inner_rx).poll_next(cx)
        }
    }

    impl Sink<Vec<u8>> for DeadlockProneStream {
        type Error = RedisError;

        fn poll_ready(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            if self.write_blocked.load(Ordering::SeqCst) {
                // Store waker so we can be woken when unblocked
                self.waker = Some(cx.waker().clone());
                return Poll::Pending;
            }
            Pin::new(&mut self.get_mut().inner_tx)
                .poll_ready(cx)
                .map_err(|e| {
                    RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
                })
        }

        fn start_send(mut self: Pin<&mut Self>, item: Vec<u8>) -> Result<(), Self::Error> {
            Pin::new(&mut self.inner_tx).start_send(item).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }

        fn poll_flush(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            if self.write_blocked.load(Ordering::SeqCst) {
                self.waker = Some(cx.waker().clone());
                return Poll::Pending;
            }
            Pin::new(&mut self.inner_tx).poll_flush(cx).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }

        fn poll_close(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Pin::new(&mut self.inner_tx).poll_close(cx).map_err(|e| {
                RedisError::from((crate::ErrorKind::IoError, "sink error", e.to_string()))
            })
        }
    }

    /// Reproduces the TCP deadlock from https://github.com/redis-rs/redis-rs/issues/1955
    ///
    /// Scenario:
    /// 1. Client sends command #1 (GET) — goes through fine
    /// 2. Server queues response for command #1
    /// 3. Client sends command #2 (large SET) — write side becomes blocked (TCP backpressure)
    /// 4. BUG: poll_ready returns Pending without calling poll_read
    ///    → response for command #1 is never delivered
    ///    → command #1's caller hangs forever
    ///
    /// With the fix: poll_ready calls poll_read before blocking, so the response
    /// for command #1 is delivered even while the write side is blocked.
    #[tokio::test]
    async fn test_tcp_deadlock_read_blocked_by_write() {
        let write_blocked = Arc::new(AtomicBool::new(false));
        let (sink_tx, _sink_rx) = futures_mpsc::channel::<Vec<u8>>(100);
        let (mut resp_tx, resp_rx) = futures_mpsc::channel::<RedisResult<Value>>(100);

        let stream = DeadlockProneStream {
            write_blocked: write_blocked.clone(),
            inner_tx: sink_tx,
            inner_rx: resp_rx,
            waker: None,
        };

        let (pipeline, driver) = Pipeline::new(stream, None, None);
        let driver_handle = tokio::spawn(driver);

        // Send first command — this should go through fine
        let mut pipeline1 = pipeline.clone();
        let cmd1_handle = tokio::spawn(async move {
            pipeline1
                .send_single(
                    crate::cmd("GET").arg("key1").get_packed_command(),
                    Duration::from_secs(5),
                    false,
                    false,
                )
                .await
        });

        // Give the driver time to process the send
        tokio::time::sleep(Duration::from_millis(50)).await;

        // Now block the write side — simulating TCP send buffer full
        write_blocked.store(true, Ordering::SeqCst);

        // Send second command — this will block in poll_ready because write is blocked
        let mut pipeline2 = pipeline.clone();
        let cmd2_handle = tokio::spawn(async move {
            pipeline2
                .send_single(
                    crate::cmd("SET")
                        .arg("key2")
                        .arg("value")
                        .get_packed_command(),
                    Duration::from_secs(5),
                    false,
                    false,
                )
                .await
        });

        // Give the driver time to attempt the second send and get blocked
        tokio::time::sleep(Duration::from_millis(50)).await;

        // Now inject the response for command #1 on the read side.
        // If poll_read is called during poll_ready (the fix), this response will be
        // delivered to cmd1_handle. If not (the bug), cmd1_handle will hang.
        resp_tx
            .try_send(Ok(Value::BulkString(b"response1".to_vec().into())))
            .expect("Failed to inject response");

        // Wait for command #1 to complete — with the bug, this times out
        let result = tokio::time::timeout(Duration::from_secs(2), cmd1_handle).await;

        // Cleanup
        write_blocked.store(false, Ordering::SeqCst);
        driver_handle.abort();
        cmd2_handle.abort();

        match result {
            Ok(Ok(Ok(value))) => {
                assert_eq!(value, Value::BulkString(b"response1".to_vec().into()));
            }
            Ok(Ok(Err(e))) => {
                panic!("Command 1 returned error: {:?}", e);
            }
            Ok(Err(e)) => {
                panic!("Command 1 task panicked: {:?}", e);
            }
            Err(_) => {
                panic!(
                    "TEST FAILED: TCP deadlock detected!\n\
                     Command #1's response was available on the read side, but the \
                     multiplexer never delivered it because poll_ready/poll_flush blocked \
                     on the write side without calling poll_read first.\n\
                     \n\
                     Fix: Call self.poll_read(cx) at the beginning of poll_ready() and \
                     poll_flush() before checking the write side, so responses are always \
                     drained even when writes are blocked.\n\
                     \n\
                     See: https://github.com/redis-rs/redis-rs/issues/1955\n\
                     Fix: https://github.com/redis-rs/redis-rs/pull/2070"
                );
            }
        }
    }

    #[tokio::test]
    async fn test_new_with_buffer_size_respects_capacity() {
        // The internal pipeline channel capacity must equal the configured buffer
        // size rather than the hardcoded default of 50. With a buffer of 3 and no
        // driver draining the channel, the first 3 sends fill it and the 4th must
        // hit the send timeout. If the parameter were ignored (capacity stayed 50),
        // the 4th send would succeed instead — so this asserts the value is wired
        // through, not merely that the constructor exists.
        let stall_flag = Arc::new(AtomicBool::new(false));
        let (sink_tx, _sink_rx) = futures_mpsc::channel(100);
        let (_resp_tx, resp_rx) = futures_mpsc::channel(100);

        let sink = StallingSink {
            stall: stall_flag,
            inner_tx: sink_tx,
            inner_rx: resp_rx,
        };

        let (mut pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, 3);
        std::mem::forget(driver); // never drain, so the channel stays full

        // Fill the 3 buffer slots with sends that then park awaiting responses.
        for _ in 0..3 {
            let mut pipeline_clone = pipeline.clone();
            tokio::spawn(async move {
                let _ = pipeline_clone
                    .send_single(
                        crate::cmd("PING").get_packed_command(),
                        Duration::from_secs(60),
                        false,
                        false,
                    )
                    .await;
            });
        }
        tokio::time::sleep(Duration::from_millis(50)).await;

        // The 4th send must time out because the 3-slot channel is full.
        let timeout = Duration::from_millis(200);
        let start = std::time::Instant::now();
        let result = pipeline
            .send_single(
                crate::cmd("PING").get_packed_command(),
                timeout,
                false,
                false,
            )
            .await;
        let elapsed = start.elapsed();

        assert!(
            result.is_err(),
            "expected send to fail when the 3-slot buffer is full"
        );
        assert!(
            elapsed < Duration::from_secs(1),
            "send should time out quickly once the buffer is full, took {:?}",
            elapsed
        );
    }

    #[tokio::test]
    async fn test_mock_server_sink_responds_to_all_commands() {
        // Validates the benchmark harness itself: with zero latency and an
        // unbounded in-flight window (no writer-side backpressure), every command
        // sent through the pipeline must receive its OK response, regardless of
        // buffer size. If this baseline ever fails, the sweep numbers are
        // meaningless — so this is the harness's self-check.
        let (sink, server) = MockServerSink::new(Duration::ZERO, usize::MAX);
        let server_handle = tokio::spawn(server);

        let (pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, 50);
        let driver_handle = tokio::spawn(driver);

        let mut handles = Vec::new();
        for _ in 0..200 {
            let mut p = pipeline.clone();
            handles.push(tokio::spawn(async move {
                p.send_single(
                    crate::cmd("PING").get_packed_command(),
                    Duration::from_secs(5),
                    false,
                    false,
                )
                .await
            }));
        }
        for h in handles {
            let res = h.await.unwrap();
            assert!(res.is_ok(), "command failed: {:?}", res);
        }

        drop(pipeline);
        let _ = driver_handle.await;
        server_handle.abort();
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 4)]
    async fn test_backpressure_does_not_fail_live_commands() {
        // Issue #5446: under sustained backpressure (slow drain — here a narrow
        // 2-command in-flight window plus 10ms latency), the 50-slot channel fills
        // while the connection is alive and responses keep flowing. Commands must
        // wait for a slot, not fail. The fixed 100ms send-timeout fails most of
        // them with FatalSendError; a liveness-aware send-timeout must not.
        let (sink, server) = MockServerSink::new(Duration::from_millis(10), 2);
        let server_handle = tokio::spawn(server);
        let (pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, 50);
        let driver_handle = tokio::spawn(driver);

        let mut handles = Vec::new();
        for _ in 0..200 {
            let mut p = pipeline.clone();
            handles.push(tokio::spawn(async move {
                p.send_single(
                    crate::cmd("PING").get_packed_command(),
                    Duration::from_secs(30),
                    false,
                    false,
                )
                .await
            }));
        }
        let mut ok = 0usize;
        let mut failed = 0usize;
        for h in handles {
            match h.await.unwrap() {
                Ok(_) => ok += 1,
                Err(_) => failed += 1,
            }
        }

        drop(pipeline);
        let _ = driver_handle.await;
        server_handle.abort();

        assert_eq!(
            failed, 0,
            "live-but-slow connection must not fail commands under backpressure: \
             {ok} ok, {failed} failed"
        );
        assert_eq!(ok, 200, "all commands should eventually succeed");
        // Note: this test also guards the `start_send` liveness signal. Under this
        // sustained backpressure the `Forward` combinator starves `poll_read`, so
        // the response-receipt bump never fires; the test passes only because
        // `start_send` records drain progress. Removing that bump regresses this.
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 4)]
    async fn test_backpressure_timeout_is_classified_as_timeout() {
        // When backpressure outlasts the request's own timeout budget (the
        // connection is alive and draining, just slower than the deadline), the
        // failure must be a genuine timeout — `is_timeout()` true, retry method
        // NoRetry — not a fatal/reconnect send error and not an immediately
        // retryable error against the already-overloaded connection.
        let (sink, server) = MockServerSink::new(Duration::from_millis(10), 2);
        let server_handle = tokio::spawn(server);
        let (pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, 50);
        let driver_handle = tokio::spawn(driver);

        let timeout = Duration::from_millis(150);
        let mut handles = Vec::new();
        for _ in 0..300 {
            let mut p = pipeline.clone();
            handles.push(tokio::spawn(async move {
                p.send_single(
                    crate::cmd("PING").get_packed_command(),
                    timeout,
                    false,
                    false,
                )
                .await
            }));
        }

        let mut ok = 0usize;
        let mut timed_out = 0usize;
        let mut other_err = 0usize;
        for h in handles {
            match h.await.unwrap() {
                Ok(_) => ok += 1,
                Err(e) if e.is_timeout() => timed_out += 1,
                Err(_) => other_err += 1,
            }
        }

        // Teardown: abort the driver rather than awaiting a graceful shutdown.
        // The 150ms timeout makes some sends abandon their request while their
        // InFlight entries are still queued (their responses were starved by the
        // `Forward` combinator under backpressure — see #6110). `PipelineSink::
        // poll_close` drains those in-flight responses but then parks on the
        // response stream; a real socket would unblock it with EOF on close, but
        // the mock's response channel never reaches EOF, so awaiting the driver
        // would hang. This test asserts only the classification of the sends
        // already collected above, so graceful driver shutdown is neither needed
        // nor available here.
        drop(pipeline);
        driver_handle.abort();
        server_handle.abort();

        assert!(
            timed_out > 0,
            "expected some commands to exceed the timeout budget under backpressure \
             (ok={ok}, timed_out={timed_out}, other_err={other_err})"
        );
        assert_eq!(
            other_err, 0,
            "backpressure failures must be timeouts, not fatal/other errors: \
             {other_err} non-timeout failures"
        );
    }

    #[tokio::test]
    async fn test_send_fails_when_writer_terminated() {
        // If the writer task has gone away (its channel receiver dropped), a
        // producer's send must fail promptly with FatalSendError rather than spin
        // in the liveness loop waiting for capacity that will never free.
        let (sink, _server) = MockServerSink::new(Duration::ZERO, usize::MAX);
        let (mut pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, 50);
        drop(driver); // writer/receiver gone -> channel closed

        let start = std::time::Instant::now();
        let result = pipeline
            .send_single(
                crate::cmd("PING").get_packed_command(),
                Duration::from_secs(5),
                false,
                false,
            )
            .await;
        let elapsed = start.elapsed();

        let err = result.expect_err("send must fail when the writer is gone");
        assert_eq!(
            err.kind(),
            crate::ErrorKind::FatalSendError,
            "got: {:?}",
            err.kind()
        );
        // Prove it took the closed-channel branch (`reserve()` returns Err
        // immediately) rather than the no-progress dead path, which only trips
        // after DEAD_TICKS liveness ticks (~200ms). Both return FatalSendError,
        // so the kind alone cannot distinguish them — the elapsed time can.
        assert!(
            elapsed < Duration::from_millis(100),
            "closed channel must fail immediately, not via the ~200ms no-progress \
             path; took {elapsed:?}"
        );
    }

    /// Runs `total` concurrent commands of `payload` bytes through a pipeline with
    /// the given internal `buffer` capacity, against a mock server with `latency`
    /// RTT and a `window`-command in-flight limit. Returns total wall-clock time.
    async fn run_buffer_sweep_case(
        buffer: usize,
        payload: usize,
        latency: Duration,
        window: usize,
        total: usize,
    ) -> SweepResult {
        let (sink, server) = MockServerSink::new(latency, window);
        let shared = sink.shared.clone();
        let server_handle = tokio::spawn(server);
        let (pipeline, driver) = Pipeline::new_with_buffer_size(sink, None, None, buffer);
        let driver_handle = tokio::spawn(driver);

        // Build the command once (SET k <payload-bytes>); clone the packed bytes
        // per send so each occupies ~`payload` bytes in the pipeline channel.
        let value = vec![b'x'; payload];
        let mut command = crate::cmd("SET");
        command.arg("k").arg(value.as_slice());
        let packed = command.get_packed_command();

        let start = std::time::Instant::now();
        let mut handles = Vec::with_capacity(total);
        for _ in 0..total {
            let mut p = pipeline.clone();
            let packed = packed.clone();
            handles.push(tokio::spawn(async move {
                p.send_single(packed, Duration::from_secs(60), false, false)
                    .await
            }));
        }
        let mut ok = 0usize;
        let mut failed = 0usize;
        let mut error_kind = String::new();
        for h in handles {
            match h.await.unwrap() {
                Ok(_) => ok += 1,
                Err(e) => {
                    failed += 1;
                    if error_kind.is_empty() {
                        error_kind = format!("{:?}", e.kind());
                    }
                }
            }
        }
        let elapsed = start.elapsed();

        let max_outstanding = shared.max_outstanding.load(Ordering::SeqCst);
        drop(pipeline);
        let _ = driver_handle.await;
        server_handle.abort();
        SweepResult {
            elapsed,
            max_outstanding,
            ok,
            failed,
            error_kind,
        }
    }

    struct SweepResult {
        elapsed: Duration,
        max_outstanding: usize,
        ok: usize,
        failed: usize,
        error_kind: String,
    }

    fn mean_stddev(samples: &[f64]) -> (f64, f64) {
        let n = samples.len() as f64;
        let mean = samples.iter().sum::<f64>() / n;
        let var = samples.iter().map(|x| (x - mean).powi(2)).sum::<f64>() / n;
        (mean, var.sqrt())
    }

    /// Investigation harness for issue #5446: does the pipeline buffer size affect
    /// throughput, and if so, in which regime?
    ///
    /// Sweeps buffer size × payload × latency. The in-flight `window` is derived
    /// from a byte budget (a TCP send buffer is bounded in bytes, not commands),
    /// so large payloads get a small window. Run manually:
    ///
    ///   cargo test --lib bench_pipeline_buffer_sweep -- --ignored --nocapture
    #[tokio::test(flavor = "multi_thread", worker_threads = 4)]
    #[ignore = "benchmark: run manually with --ignored --nocapture"]
    async fn bench_pipeline_buffer_sweep() {
        const REPS: usize = 10;
        const SOCKET_BUFFER_BYTES: usize = 2 * 1024 * 1024; // models TCP send buffer
        const TOTAL_COMMANDS: usize = 500;
        let buffer_sizes = [50usize, 200, 1000, 4000];
        let payload_sizes = [64usize, 4096, 1_048_576];
        let latencies = [Duration::from_millis(0), Duration::from_millis(10)];

        println!(
            "\n=== Pipeline buffer sweep (cmds={TOTAL_COMMANDS}, reps={REPS}, \
             socket_buf={SOCKET_BUFFER_BYTES}B, runtime=multi_thread/4) ===\n\
             payload  latency  window  buffer   mean_ms  stddev_ms  max_inflight  ok  failed"
        );

        for &payload in &payload_sizes {
            let window = std::cmp::max(1, SOCKET_BUFFER_BYTES / payload);
            for &latency in &latencies {
                for &buffer in &buffer_sizes {
                    let mut samples = Vec::with_capacity(REPS);
                    let mut max_inflight = 0usize;
                    let mut last_ok = 0usize;
                    let mut last_failed = 0usize;
                    let mut last_err_kind = String::new();
                    for _ in 0..REPS {
                        let r =
                            run_buffer_sweep_case(buffer, payload, latency, window, TOTAL_COMMANDS)
                                .await;
                        samples.push(r.elapsed.as_secs_f64() * 1000.0);
                        max_inflight = max_inflight.max(r.max_outstanding);
                        last_ok = r.ok;
                        last_failed = r.failed;
                        last_err_kind = r.error_kind;
                    }
                    let (mean, stddev) = mean_stddev(&samples);
                    println!(
                        "{payload:>7}  {:>5}ms  {window:>6}  {buffer:>6}  {mean:>8.2}  {stddev:>8.2}  {max_inflight:>6}  {last_ok:>4}  {last_failed:>4}  {last_err_kind}",
                        latency.as_millis()
                    );
                }
                println!();
            }
        }
    }

    /// A sink whose write side is never ready, so the pipeline's bounded channel
    /// never drains: a producer waiting for capacity stays parked in the
    /// `send_recv` liveness loop, and the writer never records a `start_send`
    /// (drain) progress bump. The read side yields exactly the responses the test
    /// injects via its `resp_tx`, so the ONLY thing that can advance the liveness
    /// `progress` counter is the `poll_read` bump on response receipt. This lets
    /// the tests below drive the liveness signal deterministically under
    /// `start_paused` virtual time.
    struct ReadProgressSink {
        resp_rx: futures_mpsc::Receiver<RedisResult<Value>>,
    }

    impl Stream for ReadProgressSink {
        type Item = RedisResult<Value>;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            Pin::new(&mut self.resp_rx).poll_next(cx)
        }
    }

    impl Sink<Vec<u8>> for ReadProgressSink {
        type Error = RedisError;
        fn poll_ready(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            // Never ready: the writer can never pull from the channel, so it stays
            // full (the producer keeps waiting for capacity) and no drain progress
            // bump is ever recorded. The `Forward` driver is re-polled via the
            // read waker that `PipelineSink::poll_ready`'s Pending path registers
            // through `poll_read`, so returning a bare Pending here is correct.
            Poll::Pending
        }
        fn start_send(self: Pin<&mut Self>, _item: Vec<u8>) -> Result<(), Self::Error> {
            unreachable!("poll_ready never returns Ready, so start_send is never called")
        }
        fn poll_flush(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }
        fn poll_close(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }
    }

    /// Yields repeatedly so spawned pipeline tasks (the producer under test, the
    /// filler, and the `Forward` driver) can run to their next park point without
    /// advancing the (paused) clock.
    async fn settle() {
        for _ in 0..16 {
            tokio::task::yield_now().await;
        }
    }

    /// Spawns a `ReadProgressSink`-backed pipeline (buffer = 1) plus enough filler
    /// producers to saturate it, so the returned `subject` producer blocks in the
    /// slot-acquire liveness loop. The `Forward` combinator buffers one pulled
    /// message in addition to the channel's `buffer_size`, so `buffer_size + 1`
    /// producers are absorbed before a producer blocks on capacity — hence two
    /// fillers for buffer = 1. All producers use an effectively-infinite timeout,
    /// so the only way the subject can resolve early is the dead path
    /// (`FatalSendError`). Returns (subject, fillers, driver, resp_tx).
    #[allow(clippy::type_complexity)]
    async fn spawn_blocked_producer() -> (
        tokio::task::JoinHandle<RedisResult<Value>>,
        Vec<tokio::task::JoinHandle<RedisResult<Value>>>,
        tokio::task::JoinHandle<()>,
        futures_mpsc::Sender<RedisResult<Value>>,
    ) {
        let (resp_tx, resp_rx) = futures_mpsc::channel::<RedisResult<Value>>(64);
        let (pipeline, driver) =
            Pipeline::new_with_buffer_size(ReadProgressSink { resp_rx }, None, None, 1);
        let driver_handle = tokio::spawn(driver);

        // buffer_size (1) in the channel + 1 buffered by `Forward` = 2 absorbed.
        let mut fillers = Vec::new();
        for _ in 0..2 {
            let mut f = pipeline.clone();
            fillers.push(tokio::spawn(async move {
                f.send_single(
                    crate::cmd("PING").get_packed_command(),
                    Duration::from_secs(3600),
                    false,
                    false,
                )
                .await
            }));
            settle().await; // let the writer absorb this filler before the next
        }

        let mut subject = pipeline.clone();
        let subject_handle = tokio::spawn(async move {
            subject
                .send_single(
                    crate::cmd("PING").get_packed_command(),
                    Duration::from_secs(3600),
                    false,
                    false,
                )
                .await
        });
        settle().await; // subject parks in the liveness loop

        (subject_handle, fillers, driver_handle, resp_tx)
    }

    #[tokio::test(start_paused = true)]
    async fn test_single_no_progress_tick_does_not_kill_live_connection() {
        // Guards the `DEAD_TICKS = 2` debounce: a *single* no-progress liveness
        // tick must NOT declare a live connection dead. The subject is parked in
        // the slot-acquire loop; we feed a response on every *other* tick, so each
        // gap is exactly one no-progress tick (`no_progress_ticks` reaches 1, then
        // resets when the next tick observes the injected progress). With
        // `DEAD_TICKS = 2` the subject stays alive; with `DEAD_TICKS = 1` it would
        // be killed (`FatalSendError`) on the very first no-progress tick, failing
        // the in-loop assertion below.
        let (subject, fillers, driver, mut resp_tx) = spawn_blocked_producer().await;

        for _ in 0..6 {
            tokio::time::advance(Duration::from_millis(100)).await; // one no-progress tick
            settle().await;
            assert!(
                !subject.is_finished(),
                "a single no-progress tick must not kill a live connection \
                 (DEAD_TICKS must be >= 2); the producer was declared dead"
            );
            resp_tx.try_send(Ok(Value::Okay)).expect("inject response");
            settle().await; // Forward drains it -> poll_read progress bump
            tokio::time::advance(Duration::from_millis(100)).await; // next tick observes progress -> reset
            settle().await;
        }

        assert!(
            !subject.is_finished(),
            "producer must remain alive across repeated single no-progress ticks"
        );

        subject.abort();
        for f in fillers {
            f.abort();
        }
        driver.abort();
    }

    #[tokio::test(start_paused = true)]
    async fn test_poll_read_progress_alone_keeps_connection_alive() {
        // Isolates the response-receipt (`poll_read`) liveness signal from the
        // drain (`start_send`) signal. `ReadProgressSink` never accepts a drain,
        // so `start_send` never bumps progress; the only liveness signal available
        // is the `poll_read` bump when an injected response is drained. A producer
        // parked in the slot-acquire loop must therefore stay alive (never
        // `FatalSendError`) as long as responses keep flowing. Deleting the
        // `poll_read` progress bump regresses this: with no progress at all the
        // producer would be declared dead after `DEAD_TICKS` ticks.
        let (subject, fillers, driver, mut resp_tx) = spawn_blocked_producer().await;

        for _ in 0..10 {
            resp_tx.try_send(Ok(Value::Okay)).expect("inject response");
            settle().await; // Forward drains it -> poll_read progress bump
            tokio::time::advance(Duration::from_millis(100)).await; // one liveness tick (sees progress)
            settle().await;
        }

        assert!(
            !subject.is_finished(),
            "a producer must stay alive while poll_read keeps recording progress; \
             it resolved early (likely FatalSendError) — is the poll_read liveness \
             bump still present?"
        );

        subject.abort();
        for f in fillers {
            f.abort();
        }
        driver.abort();
    }
}
