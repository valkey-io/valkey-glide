package javababushka.benchmarks.clients.babushka;

import static response.ResponseOuterClass.Response;

import java.util.concurrent.Future;
import javababushka.Client;
import javababushka.benchmarks.clients.AsyncClient;
import javababushka.benchmarks.clients.SyncClient;
import javababushka.benchmarks.utils.ConnectionSettings;

public class JniNettyClient implements SyncClient, AsyncClient<Response> {

  private final Client testClient;
  private String name = "JNI Netty";

  public JniNettyClient(boolean async) {
    name += async ? " async" : " sync";
    testClient = new Client();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void closeConnection() {
    testClient.closeConnection();
  }

  @Override
  public void connectToRedis() {
    connectToRedis(new ConnectionSettings("localhost", 6379, false, false));
  }

  @Override
  public void connectToRedis(ConnectionSettings connectionSettings) {
    waitForResult(asyncConnectToRedis(connectionSettings));
  }

  @Override
  public Future<Response> asyncConnectToRedis(ConnectionSettings connectionSettings) {
    return testClient.asyncConnectToRedis(
        connectionSettings.host,
        connectionSettings.port,
        connectionSettings.useSsl,
        connectionSettings.clusterMode);
  }

  @Override
  public Future<Response> asyncSet(String key, String value) {
    return testClient.asyncSet(key, value);
  }

  @Override
  public Future<String> asyncGet(String key) {
    return testClient.asyncGet(key);
  }

  @Override
  public void set(String key, String value) {
    testClient.set(key, value);
  }

  @Override
  public String get(String key) {
    return testClient.get(key);
  }
}
