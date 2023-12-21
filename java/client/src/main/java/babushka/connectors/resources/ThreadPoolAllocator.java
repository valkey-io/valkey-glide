package babushka.connectors.resources;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** A class responsible to allocating and deallocating shared thread pools. */
public class ThreadPoolAllocator {

  /**
   * Thread pools supplied to <em>Netty</em> to perform all async IO.<br>
   * Map key is supposed to be pool name + thread count as a string concat product.
   */
  private static final Map<String, EventLoopGroup> groups = new ConcurrentHashMap<>();

  /**
   * Allocate (create new or share existing) Netty thread pool required to manage connection. A
   * thread pool could be shared across multiple connections.
   *
   * @return A new thread pool.
   */
  public static EventLoopGroup createNettyThreadPool(String prefix, Optional<Integer> threadLimit) {
    int threadCount = threadLimit.orElse(Runtime.getRuntime().availableProcessors());
    if (Platform.getCapabilities().isKQueueAvailable()) {
      String name = prefix + "-kqueue-elg";
      return getOrCreate(
          name + threadCount,
          () -> new KQueueEventLoopGroup(threadCount, new DefaultThreadFactory(name, true)));
    } else if (Platform.getCapabilities().isEPollAvailable()) {
      String name = prefix + "-epoll-elg";
      return getOrCreate(
          name + threadCount,
          () -> new EpollEventLoopGroup(threadCount, new DefaultThreadFactory(name, true)));
    }
    // TODO support IO-Uring and NIO

    throw new RuntimeException("Current platform supports no known thread pool types");
  }

  /**
   * Get a cached thread pool from {@link #groups} or create a new one by given lambda and cache.
   */
  private static EventLoopGroup getOrCreate(String name, Supplier<EventLoopGroup> supplier) {
    if (groups.containsKey(name)) {
      return groups.get(name);
    }
    EventLoopGroup group = supplier.get();
    groups.put(name, group);
    return group;
  }

  /**
   * A JVM shutdown hook to be registered. It is responsible for closing connection and freeing
   * resources. It is recommended to use a class instead of lambda to ensure that it is called.<br>
   * See {@link Runtime#addShutdownHook}.
   */
  private static class ShutdownHook implements Runnable {
    @Override
    public void run() {
      groups.values().forEach(EventLoopGroup::shutdownGracefully);
    }
  }

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook(), "Babushka-shutdown-hook"));
  }
}
