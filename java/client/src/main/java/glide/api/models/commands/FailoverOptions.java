/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;

/**
 * Options for the <code>FAILOVER</code> command.
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

    /** Builder for constructing {@link FailoverOptions}. */
    public static class Builder {
        private String host;
        private int port;
        private boolean force;
        private Long timeout;

        /**
         * Sets the target replica to failover to.
         *
         * @param host The host of the target replica.
         * @param port The port of the target replica.
         * @return This builder.
         */
        public Builder to(@NonNull String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        /**
         * If both TIMEOUT and TO are set, forces failover to the target replica once the timeout
         * elapses instead of rolling back.
         *
         * @return This builder.
         */
        public Builder force() {
            this.force = true;
            return this;
        }

        /**
         * Sets the maximum time in milliseconds to wait in the waiting-for-sync state before aborting
         * the failover.
         *
         * @param milliseconds The timeout in milliseconds.
         * @return This builder.
         */
        public Builder timeout(long milliseconds) {
            this.timeout = milliseconds;
            return this;
        }

        /**
         * Builds the {@link FailoverOptions}.
         *
         * @return A new {@link FailoverOptions} instance.
         */
        public FailoverOptions build() {
            FailoverOptions opts = new FailoverOptions();
            opts.host = this.host;
            opts.port = this.port;
            opts.force = this.force;
            opts.timeout = this.timeout;
            return opts;
        }
    }

    /**
     * Creates a new builder.
     *
     * @return A new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
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
