/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Optional arguments for {@link GenericBaseCommands#migrate(String, long, String, long, long,
 * MigrateOptions)} command.
 *
 * @see <a href="https://valkey.io/commands/migrate/">valkey.io</a>
 */
@Builder
public final class MigrateOptions {
    /** Valkey API keyword for COPY option. */
    public static final String COPY_VALKEY_API = "COPY";

    /** Valkey API keyword for REPLACE option. */
    public static final String REPLACE_VALKEY_API = "REPLACE";

    /** Valkey API keyword for AUTH option. */
    public static final String AUTH_VALKEY_API = "AUTH";

    /** Valkey API keyword for AUTH2 option. */
    public static final String AUTH2_VALKEY_API = "AUTH2";

    /** If set, do not remove the key from the source instance. */
    private final boolean copy;

    /** If set, replace existing key on the destination instance. */
    private final boolean replace;

    /** Password for authentication to the destination instance. */
    private final String password;

    /** Username for authentication to the destination instance (requires password). */
    private final String username;

    /**
     * Converts MigrateOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();

        if (copy) {
            args.add(COPY_VALKEY_API);
        }

        if (replace) {
            args.add(REPLACE_VALKEY_API);
        }

        if (username != null && password != null) {
            args.add(AUTH2_VALKEY_API);
            args.add(username);
            args.add(password);
        } else if (password != null) {
            args.add(AUTH_VALKEY_API);
            args.add(password);
        }

        return args.toArray(new String[0]);
    }
}
