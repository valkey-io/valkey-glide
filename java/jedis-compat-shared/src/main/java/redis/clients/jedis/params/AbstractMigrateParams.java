/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for MIGRATE command. */
public abstract class AbstractMigrateParams<T extends AbstractMigrateParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private boolean copy;
    private boolean replace;
    private String authPassword;
    private String auth2Username;
    private String auth2Password;

    protected AbstractMigrateParams() {}

    /** Copy the key instead of moving it. */
    public T copy() {
        this.copy = true;
        return self();
    }

    /** Replace existing key at destination. */
    public T replace() {
        this.replace = true;
        return self();
    }

    /**
     * Set authentication password (AUTH form). If {@link #auth2(String, String)} is called later, it
     * replaces this password-only authentication.
     */
    public T auth(String password) {
        this.authPassword = password;
        this.auth2Username = null;
        this.auth2Password = null;
        return self();
    }

    /**
     * Set authentication with username and password (AUTH2 form). Replaces any prior {@link
     * #auth(String)} configuration.
     */
    public T auth2(String username, String password) {
        this.auth2Username = username;
        this.auth2Password = password;
        this.authPassword = null;
        return self();
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
