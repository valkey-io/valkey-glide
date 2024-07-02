package glide.api.models.commands.stream;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.BaseTransaction;

/**
 * Valkey API Keywords for <code>XINFO STREAM</code> command represented by {@link StreamBaseCommands#xinfoStreamFull} and {@link BaseTransaction#xinfoStreamFull}.
 */
public abstract class XInfoStreamOptions {
  /** Used by <code>XINFO STREAM</code> to query detailed info. */
  public static final String FULL = "FULL";

  /** Used by <code>XINFO STREAM</code> to limit count of PEL entries. */
  public static final String COUNT = "COUNT";
}
