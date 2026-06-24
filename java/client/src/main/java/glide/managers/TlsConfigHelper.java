/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.AdvancedBaseClientConfiguration;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.api.models.exceptions.ConfigurationError;

/** TLS configuration helpers shared by connection builders. */
public final class TlsConfigHelper {

    private TlsConfigHelper() {}

    /** Returns {@code true} if insecure TLS is requested, throws if misconfigured. */
    public static boolean resolveInsecureTls(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced == null) {
            return false;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig != null && tlsConfig.isUseInsecureTLS()) {
            if (!configuration.isUseTLS()) {
                throw new ConfigurationError(
                        "`useInsecureTLS` cannot be enabled when `useTLS` is disabled.");
            }
            return true;
        }
        return false;
    }

    /** Returns the root certificates bytes, or {@code null} if not configured. */
    public static byte[] extractRootCertificates(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced == null) {
            return null;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig == null) {
            return null;
        }
        return tlsConfig.getRootCertificates();
    }
}
