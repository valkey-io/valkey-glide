/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import redis.clients.jedis.GeoCoordinate;

/**
 * Geo radius response compatibility class for Valkey GLIDE. Based on original Jedis
 * GeoRadiusResponse.
 */
public class GeoRadiusResponse {

    private byte[] member;
    private double distance;
    private GeoCoordinate coordinate;
    private long rawScore;

    public GeoRadiusResponse(byte[] member) {
        this.member = member;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public void setCoordinate(GeoCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    public void setRawScore(long rawScore) {
        this.rawScore = rawScore;
    }

    public byte[] getMember() {
        return member;
    }

    public String getMemberByString() {
        return new String(member, StandardCharsets.UTF_8);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeoRadiusResponse that = (GeoRadiusResponse) o;
        return Double.compare(that.distance, distance) == 0
                && rawScore == that.rawScore
                && Arrays.equals(member, that.member)
                && Objects.equals(coordinate, that.coordinate);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(distance, coordinate, rawScore);
        result = 31 * result + Arrays.hashCode(member);
        return result;
    }

    @Override
    public String toString() {
        return "GeoRadiusResponse{"
                + "member="
                + Arrays.toString(member)
                + ", distance="
                + distance
                + ", coordinate="
                + coordinate
                + ", rawScore="
                + rawScore
                + '}';
    }
}
