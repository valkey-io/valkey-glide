package babushka.benchmarks.clients;

/** A Redis client with sync capabilities */
public interface SyncClient extends Client {
  void set(String key, String value);

  String get(String key);
}
