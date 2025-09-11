/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.bitmap;

import glide.api.commands.BitmapBaseCommands;

/**
 * Optional arguments for {@link BitmapBaseCommands#bitcount(String, long, long, BitmapIndexType)}.
 * Specifies if start and end arguments are BYTE indices or BIT indices
 *
 * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a>
 */
public enum BitmapIndexType {
    /** Specifies a byte index * */
    BYTE,
    /** Specifies a bit index */
    BIT
}
