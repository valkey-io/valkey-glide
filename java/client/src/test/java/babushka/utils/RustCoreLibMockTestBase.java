package babushka.utils;

import babushka.connectors.handlers.ChannelHandler;
import babushka.ffi.resolvers.BabushkaCoreNativeDefinitions;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class RustCoreLibMockTestBase {

  /**
   * Pass this socket path to {@link ChannelHandler} or mock {@link
   * BabushkaCoreNativeDefinitions#getSocket()} to return it.
   */
  protected static String socketPath = null;

  @SneakyThrows
  public static void startRustCoreLibMock(RustCoreMock.BabushkaMock rustCoreLibMock) {
    assert socketPath == null
        : "Previous `RustCoreMock` wasn't stopped. Ensure that your test class inherits"
            + " `RustCoreLibMockTestBase`.";

    socketPath = RustCoreMock.start(rustCoreLibMock);
  }

  @BeforeEach
  public void preTestCheck() {
    assert socketPath != null
        : "You missed to call `startRustCoreLibMock` in a `@BeforeAll` method of your test class"
            + " inherited from `RustCoreLibMockTestBase`.";
  }

  @AfterEach
  public void afterTestCheck() {
    assert !RustCoreMock.failed() : "Error occurred in `RustCoreMock`";
  }

  @AfterAll
  @SneakyThrows
  public static void stopRustCoreLibMock() {
    assert socketPath != null
        : "You missed to call `startRustCoreLibMock` in a `@BeforeAll` method of your test class"
            + " inherited from `RustCoreLibMockTestBase`.";
    RustCoreMock.stop();
    socketPath = null;
  }
}
