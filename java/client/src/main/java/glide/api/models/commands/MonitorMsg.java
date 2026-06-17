/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.List;
import lombok.Value;

/** Represents a single line received from the MONITOR command. */
@Value
public class MonitorMsg {
    /** Unix timestamp of the command. */
    double timestamp;

    /** Database index on which the command was executed. */
    long db;

    /** Address of the client that issued the command (ip:port or unix:/path). */
    String clientAddr;

    /** The command name (e.g., "SET", "GET"). */
    String command;

    /** The command arguments (not including the command name). */
    List<String> args;
}
