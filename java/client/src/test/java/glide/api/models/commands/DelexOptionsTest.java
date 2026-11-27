/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import glide.api.models.GlideString;
import org.junit.jupiter.api.Test;

public class DelexOptionsTest {

    @Test
    public void toArgs_onlyIfEqualTo() {
        assertArrayEquals(
                new GlideString[] {gs("IFEQ"), gs("value")}, DelexOptions.onlyIfEqualTo("value").toArgs());
        assertArrayEquals(
                new GlideString[] {gs("IFEQ"), gs("value")},
                DelexOptions.onlyIfEqualTo(gs("value")).toArgs());
    }

    @Test
    public void toArgs_onlyIfNotEqualTo() {
        assertArrayEquals(
                new GlideString[] {gs("IFNE"), gs("value")},
                DelexOptions.onlyIfNotEqualTo("value").toArgs());
        assertArrayEquals(
                new GlideString[] {gs("IFNE"), gs("value")},
                DelexOptions.onlyIfNotEqualTo(gs("value")).toArgs());
    }
}
