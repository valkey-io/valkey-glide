/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/**
 * Represents the cluster's periodic checks status. To configure a specific interval, see {@link
 * PeriodicChecksManualInterval}.
 */
public enum PeriodicChecksStatus implements PeriodicChecksConfig {
    /** Enables the periodic checks with the default configurations. */
    ENABLED_DEFAULT_CONFIGS,
    /** Disables the periodic checks. */
    DISABLED,
}
