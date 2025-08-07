/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;

/** LibraryInfo compatibility stub for Valkey GLIDE wrapper. */
public class LibraryInfo {
    private final String libraryName;
    private final String engine;
    private final List<String> functions;
    private final String libraryCode;

    public LibraryInfo() {
        this.libraryName = "";
        this.engine = "";
        this.functions = Collections.emptyList();
        this.libraryCode = "";
    }

    public String getLibraryName() {
        return libraryName;
    }

    public String getEngine() {
        return engine;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public String getLibraryCode() {
        return libraryCode;
    }
}
