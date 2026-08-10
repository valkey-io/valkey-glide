/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import org.junit.jupiter.api.Test;

public class ConnectionManagerTest {

    @Test
    public void composeLibNameUsesDefaultWhenOverrideIsAbsent() {
        assertEquals(
                "GlideJava", ConnectionManager.composeLibName(GlideClientConfiguration.builder().build()));
    }

    @Test
    public void composeLibNameUsesExplicitOverride() {
        assertEquals(
                "custom-client",
                ConnectionManager.composeLibName(
                        GlideClientConfiguration.builder().libName("custom-client").build()));
    }

    @Test
    public void composeLibNameAppendsTagToDefaultOrOverride() {
        assertEquals(
                "GlideJava(framework:1.2)",
                ConnectionManager.composeLibName(
                        GlideClientConfiguration.builder().clientInfoTag("framework:1.2").build()));
        assertEquals(
                "custom-client(framework:1.2)",
                ConnectionManager.composeLibName(
                        GlideClientConfiguration.builder()
                                .libName("custom-client")
                                .clientInfoTag("framework:1.2")
                                .build()));
    }

    @Test
    public void composeLibNameTreatsEmptyValuesAsAbsent() {
        assertEquals(
                "GlideJava",
                ConnectionManager.composeLibName(GlideClientConfiguration.builder().libName("").build()));
        assertEquals(
                "custom-client",
                ConnectionManager.composeLibName(
                        GlideClientConfiguration.builder().libName("custom-client").clientInfoTag("").build()));
    }

    @Test
    public void composeLibNamePreservesNonWhitespacePunctuation() {
        assertEquals(
                "GlideJava(my-framework:1.2_+/())",
                ConnectionManager.composeLibName(
                        GlideClientConfiguration.builder().clientInfoTag("my-framework:1.2_+/()").build()));
    }

    @Test
    public void composeLibNameUsesSameBehaviorForClusterConfiguration() {
        assertEquals(
                "GlideJava(cluster-framework)",
                ConnectionManager.composeLibName(
                        GlideClusterClientConfiguration.builder().clientInfoTag("cluster-framework").build()));
    }
}
