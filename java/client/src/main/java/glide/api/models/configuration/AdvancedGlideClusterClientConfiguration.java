/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClusterClient;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents advanced configuration settings for a Standalone {@link GlideClusterClient} used in
 * {@link GlideClusterClientConfiguration}.
 *
 * @example
 *     <pre>{@code
 * AdvancedGlideClusterClientConfiguration config = AdvancedGlideClusterClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class AdvancedGlideClusterClientConfiguration extends AdvancedBaseClientConfiguration {}
