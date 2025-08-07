/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * GeoCoordinate compatibility class for Valkey GLIDE wrapper. Represents a geographic coordinate
 * with longitude and latitude.
 */
public class GeoCoordinate {

    private final double longitude;
    private final double latitude;

    public GeoCoordinate(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    @Override
    public String toString() {
        return "GeoCoordinate{longitude=" + longitude + ", latitude=" + latitude + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        GeoCoordinate that = (GeoCoordinate) obj;
        return Double.compare(that.longitude, longitude) == 0
                && Double.compare(that.latitude, latitude) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(longitude, latitude);
    }
}
