/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClient;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents advanced configuration settings for a Standalone {@link GlideClient} used in {@link
 * GlideClientConfiguration}.
 *
 * @example
 *     <pre>{@code
 * AdvancedGlideClientConfiguration config = AdvancedGlideClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .tlsAdvancedConfiguration(
 *        TlsAdvancedConfiguration.builder().useInsecureTLS(false).build())
 *     .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class AdvancedGlideClientConfiguration extends AdvancedBaseClientConfiguration {}
