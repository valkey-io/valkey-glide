/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ListBaseCommands;

/**
 * Enumeration representing element pop direction and count for the {@link ListBaseCommands#lmpop}
 * command.
 */
public enum PopDirection {
    /** Represents the option that elements should be popped from the left side of a list. */
    LEFT,
    /** Represents the option that elements should be popped from the right side of a list. */
    RIGHT
}
