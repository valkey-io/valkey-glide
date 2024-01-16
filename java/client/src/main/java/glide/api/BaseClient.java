package glide.api;

import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;

/** Base Client class for Redis */
@AllArgsConstructor
public abstract class BaseClient implements AutoCloseable {

  protected ConnectionManager connectionManager;
  protected CommandManager commandManager;

  /**
   * Closes this resource, relinquishing any underlying resources. This method is invoked
   * automatically on objects managed by the try-with-resources statement.
   *
   * <p>see: <a
   * href="https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html#close--">AutoCloseable::close()</a>
   */
  @Override
  public void close() throws ExecutionException {
    try {
      connectionManager.closeConnection().get();
    } catch (InterruptedException e) {
      // suppressing the interrupted exception - it is already suppressed in the future
      throw new RuntimeException(e);
    }
  }
}
