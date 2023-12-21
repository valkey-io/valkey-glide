package babushka.connectors.handlers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import response.ResponseOuterClass.Response;

/** Holder for resources required to dispatch responses and used by {@link ReadHandler}. */
@RequiredArgsConstructor
public class CallbackDispatcher {

  /** Client connection status needed to distinguish connection request. */
  private final AtomicBoolean connectionStatus;

  /** Reserved callback ID for connection request. */
  private final Integer CONNECTION_PROMISE_ID = 0;

  /**
   * Storage of Futures to handle responses. Map key is callback id, which starts from 1.<br>
   * Each future is a promise for every submitted by user request.<br>
   * Note: Protobuf packet contains callback ID as uint32, but it stores data as a bit field.<br>
   * Negative java values would be shown as positive on rust side. Meanwhile, no data loss happen,
   * because callback ID remains unique.
   */
  private final ConcurrentHashMap<Integer, CompletableFuture<Response>> responses =
      new ConcurrentHashMap<>();

  /**
   * Storage of freed callback IDs. It is needed to avoid occupying an ID being used and to speed up
   * search for a next free ID.<br>
   */
  // TODO: Optimize to avoid growing up to 2e32 (16 Gb) https://github.com/aws/babushka/issues/704
  private final ConcurrentLinkedQueue<Integer> freeRequestIds = new ConcurrentLinkedQueue<>();

  /**
   * Register a new request to be sent. Once response received, the given future completes with it.
   *
   * @return A pair of unique callback ID which should set into request and a client promise for
   *     response.
   */
  public Pair<Integer, CompletableFuture<Response>> registerRequest() {
    var future = new CompletableFuture<Response>();
    Integer callbackId = connectionStatus.get() ? freeRequestIds.poll() : CONNECTION_PROMISE_ID;
    synchronized (responses) {
      if (callbackId == null) {
        long size = responses.mappingCount();
        callbackId = (int) (size < Integer.MAX_VALUE ? size : -(size - Integer.MAX_VALUE));
      }
      responses.put(callbackId, future);
    }
    return Pair.of(callbackId, future);
  }

  public CompletableFuture<Response> registerConnection() {
    var res = registerRequest();
    return res.getValue();
  }

  /**
   * Complete the corresponding client promise and free resources.
   *
   * @param response A response received
   */
  public void completeRequest(Response response) {
    // A connection response doesn't contain a callback id
    int callbackId = connectionStatus.get() ? response.getCallbackIdx() : CONNECTION_PROMISE_ID;
    CompletableFuture<Response> future = responses.get(callbackId);
    if (future != null) {
      future.completeAsync(() -> response);
    } else {
      // TODO: log an error.
      // probably a response was received after shutdown or `registerRequest` call was missing
    }
    synchronized (responses) {
      responses.remove(callbackId);
    }
    freeRequestIds.add(callbackId);
  }

  public void shutdownGracefully() {
    responses.values().forEach(future -> future.cancel(false));
    responses.clear();
  }
}
