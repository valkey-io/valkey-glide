package glide.api;

import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import lombok.AllArgsConstructor;

/** Base Client class for connecting to Redis */
@AllArgsConstructor
public abstract class BaseClient implements AutoCloseable {

  protected ConnectionManager connectionManager;
  protected CommandManager commandManager;
}
