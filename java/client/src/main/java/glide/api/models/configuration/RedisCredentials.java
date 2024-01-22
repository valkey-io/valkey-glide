package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/** Represents the credentials for connecting to a Redis server. */
@Getter
@Builder
public class RedisCredentials {
    /** The password that will be used for authenticating connections to the Redis servers. */
    @NonNull private final String password;

    /**
     * The username that will be used for authenticating connections to the Redis servers. If not
     * supplied, "default" will be used.
     */
    private final String username;
}
