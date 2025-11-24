/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.commands;

import redis.clients.jedis.args.Rawable;

/**
 * Interface for Redis protocol commands.
 *
 * <p>This interface represents commands that can be sent to Redis servers. It extends {@link
 * Rawable} to provide byte array representation of the command.
 *
 * <p><b>Compatibility Note:</b> This interface is provided for compatibility with original Jedis
 * code. In the GLIDE compatibility layer, only a limited subset of commands are supported through
 * the {@code sendCommand} methods. For full functionality, use the specific typed methods provided
 * by the GLIDE client.
 */
public interface ProtocolCommand extends Rawable {}
