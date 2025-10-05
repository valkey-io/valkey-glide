/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import javax.net.ssl.SSLParameters;

/**
 * Utility class for SSL parameter operations in Jedis compatibility layer. Provides defensive
 * copying methods to prevent mutable object exposure.
 */
public final class SSLParametersUtils {

    /** Private constructor to prevent instantiation of utility class. */
    private SSLParametersUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a defensive copy of SSLParameters to prevent external modification. This method copies
     * all relevant SSL configuration properties.
     *
     * @param original the original SSLParameters to copy, may be null
     * @return a new SSLParameters instance with copied properties, or null if original is null
     */
    public static SSLParameters copy(SSLParameters original) {
        if (original == null) {
            return null;
        }

        SSLParameters copy = new SSLParameters();
        copy.setAlgorithmConstraints(original.getAlgorithmConstraints());
        copy.setCipherSuites(original.getCipherSuites());
        copy.setEndpointIdentificationAlgorithm(original.getEndpointIdentificationAlgorithm());
        copy.setNeedClientAuth(original.getNeedClientAuth());
        copy.setProtocols(original.getProtocols());
        copy.setServerNames(original.getServerNames());
        copy.setSNIMatchers(original.getSNIMatchers());
        copy.setUseCipherSuitesOrder(original.getUseCipherSuitesOrder());
        copy.setWantClientAuth(original.getWantClientAuth());
        return copy;
    }
}
