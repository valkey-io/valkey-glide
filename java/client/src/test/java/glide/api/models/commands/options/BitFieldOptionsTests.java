/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.options;

import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldIncrby;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitFieldOptions.SignedEncoding;
import glide.api.models.commands.bitmap.BitFieldOptions.UnsignedEncoding;
import org.junit.jupiter.api.Test;

public class BitFieldOptionsTests {
    @Test
    public void testCreateArgs() {
        UnsignedEncoding u2 = new UnsignedEncoding(2);
        SignedEncoding i8 = new SignedEncoding(8);
        Offset offset = new Offset(1);
        OffsetMultiplier offsetMultiplier = new OffsetMultiplier(8);
        BitFieldSubCommands[] subCommands =
                new BitFieldSubCommands[] {
                    new BitFieldSet(u2, offset, 2),
                    new BitFieldGet(i8, offsetMultiplier),
                    new BitFieldOverflow(BitFieldOverflow.BitOverflowControl.SAT),
                    new BitFieldIncrby(u2, offset, 5),
                };

        String[] actual = createBitFieldArgs(subCommands);

        String[] expected =
                new String[] {
                    "SET", "u2", "1", "2", "GET", "i8", "#8", "OVERFLOW", "SAT", "INCRBY", "u2", "1", "5"
                };

        assertArrayEquals(expected, actual);
    }
}
