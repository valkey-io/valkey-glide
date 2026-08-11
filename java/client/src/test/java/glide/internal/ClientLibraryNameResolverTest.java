/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ClientLibraryNameResolverTest {

    @Test
    public void resolveUsesDefaultWhenOverrideIsAbsent() {
        assertEquals("GlideJava", ClientLibraryNameResolver.resolve(null, null));
        assertEquals("GlideJava", ClientLibraryNameResolver.resolve("", null));
    }

    @Test
    public void resolveUsesExplicitOverride() {
        assertEquals("custom-client", ClientLibraryNameResolver.resolve("custom-client", null));
    }

    @Test
    public void resolveAppendsTagToDefaultOrOverride() {
        assertEquals(
                "GlideJava(framework:1.2)", ClientLibraryNameResolver.resolve(null, "framework:1.2"));
        assertEquals(
                "custom-client(framework:1.2)",
                ClientLibraryNameResolver.resolve("custom-client", "framework:1.2"));
    }

    @Test
    public void resolveTreatsEmptyTagAsAbsent() {
        assertEquals("GlideJava", ClientLibraryNameResolver.resolve(null, ""));
        assertEquals("custom-client", ClientLibraryNameResolver.resolve("custom-client", ""));
    }

    @Test
    public void resolvePreservesNonWhitespacePunctuation() {
        assertEquals(
                "GlideJava(my-framework:1.2_+/())",
                ClientLibraryNameResolver.resolve(null, "my-framework:1.2_+/()"));
    }
}
