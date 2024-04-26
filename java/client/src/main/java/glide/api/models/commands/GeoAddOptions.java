/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GeospatialIndicesBaseCommands;
import java.util.Map;
import lombok.Getter;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
 * command.
 *
 * @see <a href="https://redis.io/commands/geoadd/">redis.io</a>
 */
@Getter
public class GeoAddOptions {
    /** Options for handling existing members. See {@link ConditionalChange}. */
    private final ConditionalChange updateMode;

    /** If <code>true</code>, returns the count of changed elements instead of new elements added. */
    private final boolean changed;

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param updateMode Options for handling existing members. See {@link ConditionalChange}
     * @param changed If <code>true</code>, returns the count of changed elements instead of new
     *     elements added.
     */
    public GeoAddOptions(ConditionalChange updateMode, boolean changed) {
        this.updateMode = updateMode;
        this.changed = changed;
    }

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param updateMode Options for handling existing members. See {@link ConditionalChange}
     */
    public GeoAddOptions(ConditionalChange updateMode) {
        this.updateMode = updateMode;
        this.changed = false;
    }

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param changed If <code>true</code>, returns the count of changed elements instead of new
     *     elements added.
     */
    public GeoAddOptions(boolean changed) {
        this.updateMode = null;
        this.changed = changed;
    }
}
