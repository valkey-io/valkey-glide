package glide.managers;

import static glide.api.models.configuration.NodeAddress.DEFAULT_HOST;
import static glide.api.models.configuration.NodeAddress.DEFAULT_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import connection_request.ConnectionRequestOuterClass;
import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.ConnectionRetryStrategy;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RedisCredentials;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import io.netty.channel.ChannelFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import response.ResponseOuterClass;
import response.ResponseOuterClass.Response;

public class ConnectionManagerTest {
  ConnectionManager connectionManager;

  ChannelHandler channel;

  private static String HOST = "aws.com";
  private static int PORT = 9999;

  private static String USERNAME = "JohnDoe";
  private static String PASSWORD = "Password1";

  private static int NUM_OF_RETRIES = 5;
  private static int FACTOR = 10;
  private static int EXPONENT_BASE = 50;

  private static int DATABASE_ID = 1;

  private static int REQUEST_TIMEOUT = 3;

  @BeforeEach
  public void setUp() throws ExecutionException, InterruptedException {
    channel = mock(ChannelHandler.class);
    ChannelFuture closeFuture = mock(ChannelFuture.class);
    when(closeFuture.syncUninterruptibly()).thenReturn(closeFuture);
    when(channel.close()).thenReturn(closeFuture);
    connectionManager = new ConnectionManager(channel);
  }

  @SneakyThrows
  @Test
  public void ConnectionRequestProtobufGeneration_DefaultRedisClientConfiguration_returns() {
    // setup
    RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
    ConnectionRequest expectedProtobufConnectionRequest =
        ConnectionRequest.newBuilder()
            .setTlsMode(ConnectionRequestOuterClass.TlsMode.NoTls)
            .setClusterModeEnabled(false)
            .setReadFrom(ConnectionRequestOuterClass.ReadFrom.Primary)
            .build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response =
        Response.newBuilder().setConstantResponse(ResponseOuterClass.ConstantResponse.OK).build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
    CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

    // verify
    // no exception
    assertNull(result.get());
  }

  @SneakyThrows
  @Test
  public void ConnectionRequestProtobufGeneration_DefaultRedisClusterClientConfiguration_returns() {
    // setup
    RedisClusterClientConfiguration redisClusterClientConfiguration =
        RedisClusterClientConfiguration.builder().build();
    ConnectionRequest expectedProtobufConnectionRequest =
        ConnectionRequest.newBuilder()
            .setTlsMode(ConnectionRequestOuterClass.TlsMode.NoTls)
            .setClusterModeEnabled(true)
            .setReadFrom(ConnectionRequestOuterClass.ReadFrom.Primary)
            .build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response =
        Response.newBuilder().setConstantResponse(ResponseOuterClass.ConstantResponse.OK).build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
    CompletableFuture<Void> result =
        connectionManager.connectToRedis(redisClusterClientConfiguration);

    // verify
    assertNull(result.get());
    verify(channel).connect(eq(expectedProtobufConnectionRequest));
  }

  @SneakyThrows
  @Test
  public void ConnectionRequestProtobufGeneration_RedisClientAllFieldsSet_returns() {
    // setup
    RedisClientConfiguration redisClientConfiguration =
        RedisClientConfiguration.builder()
            .address(NodeAddress.builder().host(HOST).port(PORT).build())
            .address(NodeAddress.builder().host(DEFAULT_HOST).port(DEFAULT_PORT).build())
            .useTLS(true)
            .readFrom(ReadFrom.PREFER_REPLICA)
            .credentials(RedisCredentials.builder().username(USERNAME).password(PASSWORD).build())
            .requestTimeout(REQUEST_TIMEOUT)
            .reconnectStrategy(
                BackoffStrategy.builder()
                    .numOfRetries(NUM_OF_RETRIES)
                    .exponentBase(EXPONENT_BASE)
                    .factor(FACTOR)
                    .build())
            .databaseId(DATABASE_ID)
            .build();
    ConnectionRequest expectedProtobufConnectionRequest =
        ConnectionRequest.newBuilder()
            .addAddresses(
                ConnectionRequestOuterClass.NodeAddress.newBuilder()
                    .setHost(HOST)
                    .setPort(PORT)
                    .build())
            .addAddresses(
                ConnectionRequestOuterClass.NodeAddress.newBuilder()
                    .setHost(DEFAULT_HOST)
                    .setPort(DEFAULT_PORT)
                    .build())
            .setTlsMode(ConnectionRequestOuterClass.TlsMode.SecureTls)
            .setReadFrom(ConnectionRequestOuterClass.ReadFrom.PreferReplica)
            .setClusterModeEnabled(false)
            .setAuthenticationInfo(
                AuthenticationInfo.newBuilder().setUsername(USERNAME).setPassword(PASSWORD).build())
            .setRequestTimeout(REQUEST_TIMEOUT)
            .setConnectionRetryStrategy(
                ConnectionRetryStrategy.newBuilder()
                    .setNumberOfRetries(NUM_OF_RETRIES)
                    .setFactor(FACTOR)
                    .setExponentBase(EXPONENT_BASE)
                    .build())
            .setDatabaseId(DATABASE_ID)
            .build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response =
        Response.newBuilder().setConstantResponse(ResponseOuterClass.ConstantResponse.OK).build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
    CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

    // verify
    assertNull(result.get());
    verify(channel).connect(eq(expectedProtobufConnectionRequest));
  }

  @SneakyThrows
  @Test
  public void CheckRedisResponse_ConstantResponse_returnsSuccessfully() {
    // setup
    RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response =
        Response.newBuilder().setConstantResponse(ResponseOuterClass.ConstantResponse.OK).build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(any())).thenReturn(completedFuture);
    CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

    // verify
    assertNull(result.get());
    verify(channel).connect(any());
  }

  @Test
  public void onConnection_emptyResponse_throwsClosingException() {
    // setup
    RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response = Response.newBuilder().build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(any())).thenReturn(completedFuture);
    ExecutionException executionException =
        assertThrows(
            ExecutionException.class,
            () -> connectionManager.connectToRedis(redisClientConfiguration).get());

    assertTrue(executionException.getCause() instanceof ClosingException);
    assertEquals("Unexpected empty data in response", executionException.getCause().getMessage());
    verify(channel).close();
  }

  @Test
  public void CheckRedisResponse_RequestError_throwsClosingException() {
    // setup
    RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response =
        Response.newBuilder()
            .setRequestError(
                ResponseOuterClass.RequestError.newBuilder()
                    .setType(ResponseOuterClass.RequestErrorType.Timeout)
                    .setMessage("Timeout Occurred")
                    .build())
            .build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(any())).thenReturn(completedFuture);
    CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

    // verify
    ExecutionException exception = assertThrows(ExecutionException.class, result::get);
    assertTrue(exception.getCause() instanceof ClosingException);

    verify(channel).close();
  }

  @Test
  public void CheckRedisResponse_ClosingError_throwsClosingException() {
    // setup
    RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
    CompletableFuture<Response> completedFuture = new CompletableFuture<>();
    Response response = Response.newBuilder().setClosingError("Closing Error Occurred").build();
    completedFuture.complete(response);

    // execute
    when(channel.connect(any())).thenReturn(completedFuture);
    CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

    // verify
    ExecutionException exception = assertThrows(ExecutionException.class, result::get);
    assertTrue(exception.getCause() instanceof ClosingException);
    assertEquals(response.getClosingError(), exception.getCause().getMessage());
  }
}
