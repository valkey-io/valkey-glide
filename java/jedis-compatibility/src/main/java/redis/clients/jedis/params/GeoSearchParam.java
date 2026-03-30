/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.args.GeoUnit;

/**
 * Parameters for GEOSEARCH command in Jedis compatibility layer.
 *
 * <p>Represents search query parameters including origin (member or coordinate), search shape
 * (radius or box), and optional modifiers like sorting, count, and result enrichment.
 */
public class GeoSearchParam {
    private String fromMember;
    private GeoCoordinate fromCoordinate;
    private Double radius;
    private Double width;
    private Double height;
    private GeoUnit unit;
    private Boolean ascending; // null = no sorting, true = ASC, false = DESC
    private Integer count;
    private boolean any;
    private boolean withCoord;
    private boolean withDist;
    private boolean withHash;

    /** Creates a new GeoSearchParam from a member location. */
    public static GeoSearchParam fromMember(String member) {
        GeoSearchParam param = new GeoSearchParam();
        param.fromMember = member;
        return param;
    }

    /** Creates a new GeoSearchParam from longitude and latitude coordinates. */
    public static GeoSearchParam fromLonLat(double longitude, double latitude) {
        GeoSearchParam param = new GeoSearchParam();
        param.fromCoordinate = new GeoCoordinate(longitude, latitude);
        return param;
    }

    /**
     * Search by radius.
     *
     * @param radius the radius value
     * @param unit the unit of measurement
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam byRadius(double radius, GeoUnit unit) {
        this.radius = radius;
        this.unit = unit;
        return this;
    }

    /**
     * Search by box dimensions.
     *
     * @param width the width of the box
     * @param height the height of the box
     * @param unit the unit of measurement
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam byBox(double width, double height, GeoUnit unit) {
        this.width = width;
        this.height = height;
        this.unit = unit;
        return this;
    }

    /**
     * Sort results in ascending order (nearest to farthest).
     *
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam asc() {
        this.ascending = true;
        return this;
    }

    /**
     * Sort results in descending order (farthest to nearest).
     *
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam desc() {
        this.ascending = false;
        return this;
    }

    /**
     * Limit the number of results.
     *
     * @param count the maximum number of results
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam count(int count) {
        this.count = count;
        return this;
    }

    /**
     * Limit the number of results with ANY option (may not return closest matches).
     *
     * @param count the maximum number of results
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam count(int count, boolean any) {
        this.count = count;
        this.any = any;
        return this;
    }

    /**
     * Include coordinates in the results.
     *
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam withCoord() {
        this.withCoord = true;
        return this;
    }

    /**
     * Include distance from center in the results.
     *
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam withDist() {
        this.withDist = true;
        return this;
    }

    /**
     * Include geohash in the results.
     *
     * @return this GeoSearchParam instance
     */
    public GeoSearchParam withHash() {
        this.withHash = true;
        return this;
    }

    // Getters for internal use

    public String getFromMember() {
        return fromMember;
    }

    public GeoCoordinate getFromCoordinate() {
        return fromCoordinate;
    }

    public Double getRadius() {
        return radius;
    }

    public Double getWidth() {
        return width;
    }

    public Double getHeight() {
        return height;
    }

    public GeoUnit getUnit() {
        return unit;
    }

    public Boolean getAscending() {
        return ascending;
    }

    public Integer getCount() {
        return count;
    }

    public boolean isAny() {
        return any;
    }

    public boolean isWithCoord() {
        return withCoord;
    }

    public boolean isWithDist() {
        return withDist;
    }

    public boolean isWithHash() {
        return withHash;
    }
}
