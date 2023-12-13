package babushka.connectors;

import babushka.connectors.handlers.ChannelBuilder;
import babushka.connectors.handlers.ChannelHandler;
import babushka.managers.CallbackManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;

public class SocketConnection {

  /** Thread pool supplied to <em>Netty</em> to perform all async IO. */
  private EventLoopGroup group;

  /** The singleton instance. */
  private static SocketConnection INSTANCE = null;

  private static String socketPath;

  public static void setSocketPath(String socketPath) {
    if (SocketConnection.socketPath == null) {
      SocketConnection.socketPath = socketPath;
      return;
    }
    throw new RuntimeException("socket path can only be declared once");
  }

  /**
   * Creates (if not yet created) and returns the singleton instance of the {@link
   * SocketConnection}.
   *
   * @return a {@link SocketConnection} instance.
   */
  public static synchronized SocketConnection getInstance() {
    if (INSTANCE == null) {
      assert socketPath != null : "socket path must be defined";
      INSTANCE = new SocketConnection();
    }
    return INSTANCE;
  }

  // At the moment, Windows is not supported
  // Probably we should use NIO (NioEventLoopGroup) for Windows.
  private static final boolean isMacOs = isKQueueAvailable();

  // TODO support IO-Uring and NIO
  /**
   * Detect platform to identify which native implementation to use for UDS interaction. Currently
   * supported platforms are: Linux and macOS.<br>
   * Subject to change in future to support more platforms and implementations.
   */
  private static boolean isKQueueAvailable() {
    try {
      Class.forName("io.netty.channel.kqueue.KQueue");
      return KQueue.isAvailable();
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Constructor for the single instance. */
  private SocketConnection() {
    try {
      int cpuCount = Runtime.getRuntime().availableProcessors();
      group =
          isMacOs
              ? new KQueueEventLoopGroup(
                  cpuCount, new DefaultThreadFactory("SocketConnection-kqueue-elg", true))
              : new EpollEventLoopGroup(
                  cpuCount, new DefaultThreadFactory("SocketConnection-epoll-elg", true));
    } catch (Exception e) {
      System.err.printf(
          "Failed to create a channel %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  /** Open a new channel for a new client. */
  public ChannelHandler openNewChannel(CallbackManager callbackManager) {
    try {
      Channel channel =
          new Bootstrap()
              .group(group)
              .channel(isMacOs ? KQueueDomainSocketChannel.class : EpollDomainSocketChannel.class)
              .handler(new ChannelBuilder(callbackManager))
              .connect(new DomainSocketAddress(socketPath))
              .sync()
              .channel();
      return new ChannelHandler(channel, callbackManager);
    } catch (InterruptedException e) {
      System.err.printf(
          "Failed to create a channel %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
      e.printStackTrace(System.err);
      throw new RuntimeException(e);
    }
  }

  /**
   * Closes the UDS connection and frees corresponding resources. A consecutive call to {@link
   * #getInstance()} will create a new connection with new resource pool.
   */
  public void close() {
    group.shutdownGracefully();
    INSTANCE = null;
  }

  /**
   * A JVM shutdown hook to be registered. It is responsible for closing connection and freeing
   * resources by calling {@link #close()}. It is recommended to use a class instead of lambda to
   * ensure that it is called.<br>
   * See {@link Runtime#addShutdownHook}.
   */
  private static class ShutdownHook implements Runnable {
    @Override
    public void run() {
      if (INSTANCE != null) {
        INSTANCE.close();
        INSTANCE = null;
      }
    }
  }

  static {
    Runtime.getRuntime()
        .addShutdownHook(new Thread(new ShutdownHook(), "SocketConnection-shutdown-hook"));
  }
}
