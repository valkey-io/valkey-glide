package babushka.connectors.resources;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.UtilityClass;

/**
 * An auxiliary class purposed to detect platform (OS + JVM) {@link Capabilities} and allocate
 * corresponding resources.
 */
@UtilityClass
public class Platform {

  @Getter
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @ToString
  private static class Capabilities {
    private final boolean isKQueueAvailable;
    private final boolean isEPollAvailable;
    // TODO support IO-Uring and NIO
    private final boolean isIOUringAvailable;
    // At the moment, Windows is not supported
    // Probably we should use NIO (NioEventLoopGroup) for Windows.
    private final boolean isNIOAvailable;
  }

  /** Detected platform (OS + JVM) capabilities. Not supposed to be changed in runtime. */
  @Getter
  private static final Capabilities capabilities =
      new Capabilities(isKQueueAvailable(), isEPollAvailable(), false, false);

  /**
   * Thread pools supplied to <em>Netty</em> to perform all async IO.<br>
   * Map key is supposed to be pool name + thread count as a string concat product.
   */
  private static final Map<String, EventLoopGroup> groups = new ConcurrentHashMap<>();

  /** Detect <em>kqueue</em> availability. */
  private static boolean isKQueueAvailable() {
    try {
      Class.forName("io.netty.channel.kqueue.KQueue");
      return KQueue.isAvailable();
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Detect <em>epoll</em> availability. */
  private static boolean isEPollAvailable() {
    try {
      Class.forName("io.netty.channel.epoll.Epoll");
      return Epoll.isAvailable();
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Allocate Netty thread pool required to manage connection. A thread pool could be shared across
   * multiple connections.
   *
   * @return A new thread pool.
   */
  public static EventLoopGroup createNettyThreadPool(String prefix, Optional<Integer> threadLimit) {
    int threadCount = threadLimit.orElse(Runtime.getRuntime().availableProcessors());
    if (capabilities.isKQueueAvailable()) {
      var name = prefix + "-kqueue-elg";
      return getOrCreate(
          name + threadCount,
          () -> new KQueueEventLoopGroup(threadCount, new DefaultThreadFactory(name, true)));
    } else if (capabilities.isEPollAvailable()) {
      var name = prefix + "-epoll-elg";
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
   * Get a channel class required by Netty to open a client UDS channel.
   *
   * @return Return a class supported by the current platform.
   */
  public static Class<? extends DomainSocketChannel> getClientUdsNettyChannelType() {
    if (capabilities.isKQueueAvailable()) {
      return KQueueDomainSocketChannel.class;
    }
    if (capabilities.isEPollAvailable()) {
      return EpollDomainSocketChannel.class;
    }
    throw new RuntimeException("Current platform supports no known socket types");
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
