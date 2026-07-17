/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/**
 * Bit operations for {@code BITOP} command. This enum matches the original Jedis BitOP enum
 * structure for compatibility.
 */
public enum BitOP {
    AND,
    OR,
    XOR,
    NOT,
    DIFF,
    DIFF1,
    ANDOR,
    ONE
}
