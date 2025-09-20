package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.exceptions.ConfigurationError;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/** Configuration settings for IAM authentication. */
@Getter
@Builder
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Builder validates required IAM fields before publishing instance")
public class IamAuthConfig {
    /** The name of the ElastiCache/MemoryDB cluster. */
    @NonNull private final String clusterName;

    /** The AWS region where the cluster is located. */
    @NonNull private final String region;

    /** The type of service used for IAM authentication. */
    @NonNull private final ServiceType service;

    /** Optional refresh interval (seconds) for IAM token regeneration. */
    private final Integer refreshIntervalSeconds;

    public static class IamAuthConfigBuilder {
        public IamAuthConfig build() {
            if (clusterName == null || clusterName.isBlank()) {
                throw new ConfigurationError("IAM configuration requires a non-empty cluster name.");
            }
            if (region == null || region.isBlank()) {
                throw new ConfigurationError("IAM configuration requires a non-empty region.");
            }
            if (service == null) {
                throw new ConfigurationError("IAM configuration requires a service type.");
            }
            return new IamAuthConfig(clusterName, region, service, refreshIntervalSeconds);
        }
    }
}
