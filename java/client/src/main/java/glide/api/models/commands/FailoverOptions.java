/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;

/**
 * Optional arguments for {@link glide.api.GlideClient#failover(FailoverOptions)} and {@link
 * glide.api.GlideClusterClient#failover(FailoverOptions, glide.api.models.configuration.Route)}.
 *
 * @see <a href="https://valkey.io/commands/failover/">valkey.io</a> for details.
 */
public class FailoverOptions {

    private String host;
    private int port;
    private boolean force;
    private boolean abort;
    private Long timeout;

    private FailoverOptions() {}

    /**
     * Creates options to abort an ongoing failover.
     *
     * @return A new {@link FailoverOptions} with ABORT set.
     */
    public static FailoverOptions abort() {
        FailoverOptions opts = new FailoverOptions();
        opts.abort = true;
        return opts;
    }

    /**
     * Creates options with only a timeout.
     *
     * @param timeoutMs The maximum time in milliseconds to wait before aborting the failover.
     * @return A new {@link FailoverOptions} with TIMEOUT set.
     */
    public static FailoverOptions timeout(long timeoutMs) {
        FailoverOptions opts = new FailoverOptions();
        opts.timeout = timeoutMs;
        return opts;
    }

    /**
     * Creates options to failover to a specific replica.
     *
     * @param host The host of the target replica.
     * @param port The port of the target replica.
     * @return A new {@link FailoverOptions} targeting the specified replica.
     */
    public static FailoverOptions to(@NonNull String host, int port) {
        FailoverOptions opts = new FailoverOptions();
        opts.host = host;
        opts.port = port;
        return opts;
    }

    /**
     * Creates options to failover to a specific replica with a timeout.
     *
     * @param host The host of the target replica.
     * @param port The port of the target replica.
     * @param timeoutMs The maximum time in milliseconds to wait before aborting the failover.
     * @return A new {@link FailoverOptions} targeting the specified replica with a timeout.
     */
    public static FailoverOptions to(@NonNull String host, int port, long timeoutMs) {
        FailoverOptions opts = new FailoverOptions();
        opts.host = host;
        opts.port = port;
        opts.timeout = timeoutMs;
        return opts;
    }

    /**
     * Creates options to force a failover to a specific replica after the timeout elapses.
     *
     * @param host The host of the target replica.
     * @param port The port of the target replica.
     * @param timeoutMs The maximum time in milliseconds to wait before forcing the failover.
     * @return A new {@link FailoverOptions} with TO, TIMEOUT, and FORCE set.
     */
    public static FailoverOptions forced(@NonNull String host, int port, long timeoutMs) {
        FailoverOptions opts = new FailoverOptions();
        opts.host = host;
        opts.port = port;
        opts.force = true;
        opts.timeout = timeoutMs;
        return opts;
    }

    /**
     * Converts the options to command arguments.
     *
     * @return The command arguments as a string array.
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();
        if (abort) {
            args.add("ABORT");
        } else {
            if (host != null) {
                args.add("TO");
                args.add(host);
                args.add(Integer.toString(port));
                if (force) {
                    args.add("FORCE");
                }
            }
            if (timeout != null) {
                args.add("TIMEOUT");
                args.add(Long.toString(timeout));
            }
        }
        return args.toArray(new String[0]);
    }
}
