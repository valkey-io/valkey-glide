package glide.connection;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.utils.RustCoreLibMockTestBase;
import glide.utils.RustCoreMock;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.Response;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ConnectionWithGlideMockTests extends RustCoreLibMockTestBase {

  private ChannelHandler channelHandler = null;

  @BeforeEach
  public void createTestClient() {
    channelHandler = new ChannelHandler(new CallbackDispatcher(), socketPath);
  }

  @AfterEach
  public void closeTestClient() {
    channelHandler.close();
  }

  private Future<Response> testConnection() {
    return channelHandler.connect(createConnectionRequest());
  }

  private static ConnectionRequest createConnectionRequest() {
    return ConnectionRequest.newBuilder()
        .addAddresses(NodeAddress.newBuilder().setHost("dummyhost").setPort(42).build())
        .build();
  }

  @BeforeAll
  public static void init() {
    startRustCoreLibMock(null);
  }

  @Test
  @SneakyThrows
  // as of #710 https://github.com/aws/babushka/pull/710 - connection response is empty
  public void can_connect_with_empty_response() {
    RustCoreMock.updateGlideMock(
        new RustCoreMock.GlideMock() {
          @Override
          public Response connection(ConnectionRequest request) {
            return Response.newBuilder().build();
          }

          @Override
          public Response.Builder redisRequest(RedisRequest request) {
            return null;
          }
        });

    var connectionResponse = testConnection().get();
    assertAll(
        () -> assertFalse(connectionResponse.hasClosingError()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertFalse(connectionResponse.hasRespPointer()));
  }

  @Test
  @SneakyThrows
  public void can_connect_with_ok_response() {
    RustCoreMock.updateGlideMock(
        new RustCoreMock.GlideMock() {
          @Override
          public Response connection(ConnectionRequest request) {
            return OK().build();
          }

          @Override
          public Response.Builder redisRequest(RedisRequest request) {
            return null;
          }
        });

    var connectionResponse = testConnection().get();
    assertAll(
        () -> assertTrue(connectionResponse.hasConstantResponse()),
        () -> assertFalse(connectionResponse.hasClosingError()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertFalse(connectionResponse.hasRespPointer()));
  }

  @Test
  public void cant_connect_when_no_response() {
    RustCoreMock.updateGlideMock(
        new RustCoreMock.GlideMock() {
          @Override
          public Response connection(ConnectionRequest request) {
            return null;
          }

          @Override
          public Response.Builder redisRequest(RedisRequest request) {
            return null;
          }
        });

    assertThrows(TimeoutException.class, () -> testConnection().get(1, TimeUnit.MILLISECONDS));
  }

  @Test
  @SneakyThrows
  public void cant_connect_when_negative_response() {
    RustCoreMock.updateGlideMock(
        new RustCoreMock.GlideMock() {
          @Override
          public Response connection(ConnectionRequest request) {
            return Response.newBuilder().setClosingError("You shall not pass!").build();
          }

          @Override
          public Response.Builder redisRequest(RedisRequest request) {
            return null;
          }
        });

    var connectionResponse = testConnection().get();
    assertAll(
        () -> assertTrue(connectionResponse.hasClosingError()),
        () -> assertEquals("You shall not pass!", connectionResponse.getClosingError()),
        () -> assertFalse(connectionResponse.hasConstantResponse()));
  }
}
