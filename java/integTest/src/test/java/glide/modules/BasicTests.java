/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;

import glide.api.GlideClusterClient;
import glide.api.models.commands.InfoOptions.Section;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

// TODO: remove when real tests added
public class BasicTests {
    @Test
    @SneakyThrows
    public void connection_check() {
        var client =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();
        System.out.println(client.info(new Section[] {Section.MODULES}, RANDOM).get().getSingleValue());
    }
}
