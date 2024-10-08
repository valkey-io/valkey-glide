/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ConnectionTests {

    @Test
    @SneakyThrows
    public void basic_client() {
        var regularClient = GlideClient.createClient(commonClientConfig().build()).get();
        regularClient.close();
    }

    @Test
    @SneakyThrows
    public void cluster_client() {
        var clusterClient = GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        clusterClient.close();
    }
}
