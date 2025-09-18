/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.List;
import java.util.Map;

/**
 * Library information compatibility class for Valkey GLIDE. Based on original Jedis LibraryInfo.
 */
public class LibraryInfo {
    private final Map<String, Object> libraryInfo;

    public LibraryInfo(Map<String, Object> map) {
        this.libraryInfo = map;
    }

    public Map<String, Object> getLibraryInfo() {
        return libraryInfo;
    }

    public String getLibraryName() {
        return (String) libraryInfo.get("library_name");
    }

    public String getEngine() {
        return (String) libraryInfo.get("engine");
    }

    public List<String> getFunctions() {
        return (List<String>) libraryInfo.get("functions");
    }

    public String getLibraryCode() {
        return (String) libraryInfo.get("library_code");
    }
}
