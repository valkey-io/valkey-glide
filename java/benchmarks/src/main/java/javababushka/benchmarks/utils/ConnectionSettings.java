package javababushka.benchmarks.utils;

import lombok.AllArgsConstructor;

/** Redis-client settings */
@AllArgsConstructor
public class ConnectionSettings {
  public final String host;
  public final int port;
  public final boolean useSsl;
  public final boolean clusterMode;
}
