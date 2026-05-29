/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import redis.clients.jedis.GeoCoordinate;

/**
 * Thin GeoSearchParam for this compatibility module; see AbstractGeoSearchParam for implementation.
 */
public final class GeoSearchParam extends AbstractGeoSearchParam<GeoSearchParam> {

    private GeoSearchParam() {
        super();
    }

    public static GeoSearchParam fromMember(String member) {
        GeoSearchParam param = new GeoSearchParam();
        param.fromMember = member;
        return param;
    }

    public static GeoSearchParam fromLonLat(double longitude, double latitude) {
        GeoSearchParam param = new GeoSearchParam();
        param.fromCoordinate = new GeoCoordinate(longitude, latitude);
        return param;
    }
}
