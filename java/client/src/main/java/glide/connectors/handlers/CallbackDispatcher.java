package glide.connectors.handlers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import response.ResponseOuterClass.Response;

/** Holder for resources required to dispatch responses and used by {@link ReadHandler}. */
@RequiredArgsConstructor
public class CallbackDispatcher {

    /** Unique request ID (callback ID). Thread-safe and overflow-safe. */
    private final AtomicInteger nextAvailableRequestId = new AtomicInteger(0);

    /**
     * Storage of Futures to handle responses. Map key is callback id, which starts from 0. The value
     * is a CompletableFuture that is returned to the user and completed when the request is done.
     *
     * <p>Note: Protobuf packet contains callback ID as uint32, but it stores data as a bit field.
     * Negative Java values would be shown as positive on Rust side. There is no data loss, because
     * callback ID remains unique.
     */
    private final ConcurrentHashMap<Integer, CompletableFuture<Response>> responses =
            new ConcurrentHashMap<>();

    /**
     * Storage of freed callback IDs. It is needed to avoid occupying an ID being used and to speed up
     * search for a next free ID.
     */
    // TODO: Optimize to avoid growing up to 2e32 (16 Gb)
    // https://github.com/aws/glide-for-redis/issues/704
    private final ConcurrentLinkedQueue<Integer> freeRequestIds = new ConcurrentLinkedQueue<>();

    /**
     * Register a new request to be sent. Once response received, the given future completes with it.
     *
     * @return A pair of unique callback ID which should set into request and a client promise for
     *     response.
     */
    public Pair<Integer, CompletableFuture<Response>> registerRequest() {
        var future = new CompletableFuture<Response>();
        Integer callbackId = freeRequestIds.poll();
        if (callbackId == null) {
            // on null, we have no available request ids available in freeRequestIds
            // instead, get the next available request from counter
            callbackId = nextAvailableRequestId.getAndIncrement();
        }
        responses.put(callbackId, future);
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
        // Complete and return the response at callbackId
        // free up the callback ID in the freeRequestIds list
        int callbackId = response.getCallbackIdx();
        CompletableFuture<Response> future = responses.remove(callbackId);
        freeRequestIds.add(callbackId);
        if (future != null) {
            future.completeAsync(() -> response);
        } else {
            // TODO: log an error.
            // probably a response was received after shutdown or `registerRequest` call was missing
        }
    }

    public void shutdownGracefully() {
        responses.values().forEach(future -> future.cancel(false));
        responses.clear();
    }
}
