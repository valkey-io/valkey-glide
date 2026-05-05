/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for MIGRATE command. */
public class MigrateParams {
    private boolean copy;
    private boolean replace;
    private String authPassword;
    private String auth2Username;
    private String auth2Password;

    public MigrateParams() {}

    /** Copy the key instead of moving it. */
    public MigrateParams copy() {
        this.copy = true;
        return this;
    }

    /** Replace existing key at destination. */
    public MigrateParams replace() {
        this.replace = true;
        return this;
    }

    /**
     * Set authentication password (AUTH form). If {@link #auth2(String, String)} is called later, it
     * replaces this password-only authentication.
     */
    public MigrateParams auth(String password) {
        this.authPassword = password;
        this.auth2Username = null;
        this.auth2Password = null;
        return this;
    }

    /**
     * Set authentication with username and password (AUTH2 form). Replaces any prior {@link
     * #auth(String)} configuration.
     */
    public MigrateParams auth2(String username, String password) {
        this.auth2Username = username;
        this.auth2Password = password;
        this.authPassword = null;
        return this;
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        List<String> params = new ArrayList<>();
        if (copy) {
            params.add("COPY");
        }
        if (replace) {
            params.add("REPLACE");
        }
        if (auth2Username != null) {
            params.add("AUTH2");
            params.add(auth2Username);
            params.add(auth2Password);
        } else if (authPassword != null) {
            params.add("AUTH");
            params.add(authPassword);
        }
        return params.toArray(new String[0]);
    }
}
