package glide.connectors.handlers;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;
import response.ResponseOuterClass.Response;

/** Holder for resources required to dispatch responses and used by {@link ReadHandler}. */
public class CallbackDispatcher {
  /** Unique request ID (callback ID). Thread-safe. */
  private final AtomicInteger requestId = new AtomicInteger(0);

  /**
   * Storage of Futures to handle responses. Map key is callback id, which starts from 1.<br>
   * Each future is a promise for every submitted by user request.
   */
  private final Map<Integer, CompletableFuture<Response>> responses = new ConcurrentHashMap<>();

  /**
   * Storage for connection request similar to {@link #responses}. Unfortunately, connection
   * requests can't be stored in the same storage, because callback ID = 0 is hardcoded for
   * connection requests.
   */
  private final CompletableFuture<Response> connectionPromise = new CompletableFuture<>();

  /**
   * Register a new request to be sent. Once response received, the given future completes with it.
   *
   * @return A pair of unique callback ID which should set into request and a client promise for
   *     response.
   */
  public Pair<Integer, CompletableFuture<Response>> registerRequest() {
    int callbackId = requestId.incrementAndGet();
    var future = new CompletableFuture<Response>();
    responses.put(callbackId, future);
    return Pair.of(callbackId, future);
  }

  public CompletableFuture<Response> registerConnection() {
    return connectionPromise;
  }

  /**
   * Complete the corresponding client promise and free resources.
   *
   * @param response A response received
   */
  public void completeRequest(Response response) {
    int callbackId = response.getCallbackIdx();
    if (callbackId == 0) {
      connectionPromise.completeAsync(() -> response);
    } else {
      responses.get(callbackId).completeAsync(() -> response);
      responses.remove(callbackId);
    }
  }

  public void shutdownGracefully() {
    connectionPromise.cancel(false);
    responses.values().forEach(future -> future.cancel(false));
    responses.clear();
  }
}
