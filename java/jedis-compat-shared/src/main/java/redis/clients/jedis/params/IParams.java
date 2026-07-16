/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Marker interface for command parameter objects in the Jedis compatibility layer.
 *
 * <p>Param classes (e.g., LCSParams, SetParams) implement this interface to support polymorphism
 * when users pass parameter objects through interface references.
 */
public interface IParams {}
