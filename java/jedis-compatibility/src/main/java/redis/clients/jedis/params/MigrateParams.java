/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for MIGRATE command. */
public class MigrateParams {
    private List<String> params = new ArrayList<>();

    public MigrateParams() {}

    /** Copy the key instead of moving it. */
    public MigrateParams copy() {
        params.add("COPY");
        return this;
    }

    /** Replace existing key at destination. */
    public MigrateParams replace() {
        params.add("REPLACE");
        return this;
    }

    /** Set authentication password. */
    public MigrateParams auth(String password) {
        params.add("AUTH");
        params.add(password);
        return this;
    }

    /** Set authentication with username and password. */
    public MigrateParams auth2(String username, String password) {
        params.add("AUTH2");
        params.add(username);
        params.add(password);
        return this;
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        return params.toArray(new String[0]);
    }
}
