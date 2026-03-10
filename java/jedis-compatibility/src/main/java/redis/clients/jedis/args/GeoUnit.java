/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/** Geo unit compatibility enum for Valkey GLIDE. Based on original Jedis GeoUnit. */
public enum GeoUnit implements Rawable {
    M,
    KM,
    MI,
    FT;

    private final byte[] raw;

    GeoUnit() {
        raw = name().getBytes();
    }

    @Override
    public byte[] getRaw() {
        return raw;
    }
}
