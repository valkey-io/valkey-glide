package glide.managers;

import java.util.concurrent.CompletableFuture;

public class ConnectionManager {

  public CompletableFuture<Boolean> connectToRedis(
      String host, int port, boolean useSsl, boolean clusterMode) {
    return new CompletableFuture<>();
  }

  public CompletableFuture<Void> closeConnection() {
    return new CompletableFuture<>();
  }
}
