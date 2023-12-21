package babushka.utils;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class RedisMockTestBase {

  public static boolean started = false;

  @SneakyThrows
  public static void startRedisMock(RedisServerMock.ServerMock serverMock) {
    assert !started
        : "Previous `RedisMock` wasn't stopped. Ensure that your test class inherits"
            + " `RedisMockTestBase`.";
    RedisServerMock.start(serverMock);
    started = true;
  }

  @BeforeEach
  public void preTestCheck() {
    assert started
        : "You missed to call `startRustCoreLibMock` in a `@BeforeAll` method of your test class"
            + " inherited from `RedisMockTestBase`.";
  }

  @AfterEach
  public void afterTestCheck() {
    assert !RedisServerMock.failed() : "Error occurred in `RedisMock`";
  }

  @AfterAll
  @SneakyThrows
  public static void stopRedisMock() {
    assert started
        : "You missed to call `startRustCoreLibMock` in a `@BeforeAll` method of your test class"
            + " inherited from `RedisMockTestBase`.";
    RedisServerMock.stop();
    started = false;
  }
}
