/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import redis.clients.jedis.GeoCoordinate;

/** GeoRadiusResponse compatibility stub for Valkey GLIDE wrapper. */
public class GeoRadiusResponse {
    private final String member;
    private final double distance;
    private final GeoCoordinate coordinate;
    private final long rawScore;

    public GeoRadiusResponse(String member) {
        this.member = member;
        this.distance = 0.0;
        this.coordinate = new GeoCoordinate(0.0, 0.0);
        this.rawScore = 0;
    }

    public String getMemberByString() {
        return member;
    }

    public double getDistance() {
        return distance;
    }

    public GeoCoordinate getCoordinate() {
        return coordinate;
    }

    public long getRawScore() {
        return rawScore;
    }
}
