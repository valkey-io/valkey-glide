package glide.connection;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.ffi.resolvers.GlideCoreNativeDefinitions;
import glide.utils.RedisMockTestBase;
import glide.utils.RedisServerMock;
import io.netty.handler.codec.redis.RedisMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ConnectionWithRedisMockTests extends RedisMockTestBase {

  @BeforeAll
  public static void init() {
    startRedisMock(null);
  }

  private ChannelHandler channelHandler = null;

  @BeforeEach
  public void createTestClient() {
    channelHandler =
        new ChannelHandler(new CallbackDispatcher(), GlideCoreNativeDefinitions.getSocket());
  }

  @AfterEach
  public void closeTestClient() {
    channelHandler.close();
  }

  private static ConnectionRequest.Builder createConnectionRequest() {
    return ConnectionRequest.newBuilder()
        .addAddresses(
            NodeAddress.newBuilder().setHost("localhost").setPort(RedisServerMock.PORT).build());
  }

  @Test
  @SneakyThrows
  public void can_connect_with_no_auth() {
    RedisServerMock.updateServerMock(
        new RedisServerMock.ServerMockConnectAll() {
          @Override
          public RedisMessage reply(String cmd) {
            return null;
          }
        });

    var connectionRequest = createConnectionRequest().build();
    var connectionResponse = channelHandler.connect(connectionRequest).get();
    assertAll(
        () -> assertFalse(connectionResponse.hasClosingError()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertFalse(connectionResponse.hasRespPointer()));
  }

  @Test
  @SneakyThrows
  public void can_connect_with_two_addresses() {
    RedisServerMock.updateServerMock(
        new RedisServerMock.ServerMockConnectAll() {
          @Override
          public RedisMessage reply(String cmd) {
            return null;
          }
        });

    var connectionRequest =
        createConnectionRequest()
            .addAddresses(NodeAddress.newBuilder().setHost("dummyHost").setPort(42))
            .build();
    var connectionResponse = channelHandler.connect(connectionRequest).get();
    assertAll(
        () -> assertFalse(connectionResponse.hasClosingError()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertFalse(connectionResponse.hasRespPointer()));
  }

  @Test
  @SneakyThrows
  public void cant_connect_with_wrong_password() {
    RedisServerMock.updateServerMock(
        new RedisServerMock.ServerMockConnectAll() {
          @Override
          public RedisMessage reply(String cmd) {
            return error("NOAUTH");
          }
        });

    var connectionRequest =
        createConnectionRequest()
            .setAuthenticationInfo(
                AuthenticationInfo.newBuilder().setUsername("looser").setPassword("wrong").build())
            .build();
    var connectionResponse = channelHandler.connect(connectionRequest).get();
    assertAll(
        () -> assertFalse(connectionResponse.hasRespPointer()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertTrue(connectionResponse.hasClosingError()),
        () -> assertTrue(connectionResponse.getClosingError().contains("authentication failed")));
  }

  @Test
  @SneakyThrows
  public void can_connect_with_right_password() {
    RedisServerMock.updateServerMock(
        new RedisServerMock.ServerMockConnectAll() {
          @Override
          public RedisMessage reply(String cmd) {
            if (cmd.equals("AUTH looser p@$$w0rD")) {
              return OK();
            }
            return error("NOAUTH");
          }
        });

    var connectionRequest =
        createConnectionRequest()
            .setAuthenticationInfo(
                AuthenticationInfo.newBuilder()
                    .setUsername("looser")
                    .setPassword("p@$$w0rD")
                    .build())
            .build();
    var connectionResponse = channelHandler.connect(connectionRequest).get();
    assertAll(
        () -> assertFalse(connectionResponse.hasClosingError()),
        () -> assertFalse(connectionResponse.hasRequestError()),
        () -> assertFalse(connectionResponse.hasRespPointer()));
  }
}
