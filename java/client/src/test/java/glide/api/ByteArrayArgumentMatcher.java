/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentMatcher;

/**
 * Argument matcher for comparing lists of byte arrays.
 *
 * <p>It is used in Mockito verifications to assert that a method call was made with a specific list
 * of byte arrays as arguments.
 */
public class ByteArrayArgumentMatcher implements ArgumentMatcher<List<byte[]>> {
    List<byte[]> arguments;

    /**
     * Constructs a new ByteArrayArgumentMatcher with the provided list of byte arrays.
     *
     * @param arguments The list of byte arrays to match against.
     */
    public ByteArrayArgumentMatcher(List<byte[]> arguments) {
        this.arguments = arguments;
    }

    /**
     * Matches the provided list of byte arrays against the stored arguments.
     *
     * @param t The list of byte arrays to match
     * @return boolean - true if the provided list of byte arrays matches the stored arguments, false
     *     Sotherwise.
     */
    @Override
    public boolean matches(List<byte[]> t) {
        // Check if the sizes of both lists are equal
        if (t.size() != arguments.size()) {
            return false;
        }

        for (int index = 0; index < t.size(); index++) {
            if (!Arrays.equals(arguments.get(index), t.get(index))) {
                return false;
            }
        }
        return true;
    }
}
