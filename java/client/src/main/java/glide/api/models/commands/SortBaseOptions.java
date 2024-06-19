/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericBaseCommands;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to {@link GenericBaseCommands#sort(String, SortBaseOptions)}, {@link
 * GenericBaseCommands#sortReadOnly(String, SortBaseOptions)}, and {@link
 * GenericBaseCommands#sortStore(String, String, SortBaseOptions)}
 */
@SuperBuilder
public class SortBaseOptions extends SortOptions {}
