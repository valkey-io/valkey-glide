/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.ClientSideCache;
import glide.api.models.configuration.ProtocolVersion;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.params.provider.Arguments;

/** Shared method source providers for integration tests. */
public final class TestSources {

    private static ClientSideCache buildServerAssistedCache() {
        return ClientSideCache.builder()
                .maxCacheKb(1L)
                .entryTtlMs(60000L)
                .enableMetrics(true)
                .serverAssisted(true)
                .build();
    }

    @SneakyThrows
    public static Stream<Arguments> serverAssistedCacheClients() {

        // Server-assisted code invalidation is only supported for RESP3.
        ProtocolVersion protocol = ProtocolVersion.RESP3;

        return Stream.of(
                Arguments.of(
                        GlideClient.createClient(
                                        commonClientConfig()
                                                .protocol(protocol)
                                                .clientSideCache(buildServerAssistedCache())
                                                .build())
                                .get()),
                Arguments.of(
                        GlideClusterClient.createClient(
                                        commonClusterClientConfig()
                                                .protocol(protocol)
                                                .clientSideCache(buildServerAssistedCache())
                                                .build())
                                .get()));
    }
}
