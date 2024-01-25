package glide.connectors.resources;

import java.util.function.Supplier;

/** A class responsible to allocating and deallocating the default Thread Pool Resource. */
public class ThreadPoolResourceAllocator {
    private static final Object lock = new Object();
    private static ThreadPoolResource defaultThreadPoolResource = null;

    public static ThreadPoolResource getOrCreate(Supplier<ThreadPoolResource> supplier) {
        // once the default is set, we want to avoid hitting the lock
        if (defaultThreadPoolResource != null) {
            return defaultThreadPoolResource;
        }

        synchronized (lock) {
            if (defaultThreadPoolResource == null) {
                defaultThreadPoolResource = supplier.get();
            }
        }

        return defaultThreadPoolResource;
    }

    /**
     * A JVM shutdown hook to be registered. It is responsible for closing connection and freeing
     * resources. It is recommended to use a class instead of lambda to ensure that it is called.<br>
     * See {@link Runtime#addShutdownHook}.
     */
    protected static class ShutdownHook implements Runnable {
        @Override
        public void run() {
            if (defaultThreadPoolResource != null) {
                defaultThreadPoolResource.getEventLoopGroup().shutdownGracefully();
                defaultThreadPoolResource = null;
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook(), "Glide-shutdown-hook"));
    }
}
