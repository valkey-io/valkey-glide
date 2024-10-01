/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.ProtocolVersion;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(10) // seconds
public class ConnectionTests {

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void basic_client(ProtocolVersion protocol) {
        var regularClient =
                GlideClient.createClient(commonClientConfig().protocol(protocol).build()).get();
        regularClient.close();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void cluster_client(ProtocolVersion protocol) {
        var clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();
        clusterClient.close();
    }
}
