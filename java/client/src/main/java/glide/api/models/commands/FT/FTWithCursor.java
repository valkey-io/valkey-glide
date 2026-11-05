/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;

/**
 * The WITHCURSOR option, requesting a paged reply instead of the whole result set at once. Shared
 * verbatim between FT.AGGREGATE and FT.HYBRID -- the option's own grammar ({@code WITHCURSOR [COUNT
 * n] [MAXIDLE ms]}) and reply shape are identical regardless of which command opened the cursor.
 *
 * <p>In cluster mode, a paged query, and every subsequent {@code FT.CURSOR READ}/{@code FT.CURSOR
 * DEL} against the cursor it opens, is pinned to the PRIMARY of the index's slot regardless of
 * {@code ReadFrom} -- a cursor is a per-process, node-local resource, and this is a deliberate,
 * documented exception to GLIDE's normal read routing.
 *
 * <p>On a standalone client with {@code ReadFrom.PREFER_REPLICA}, this pinning does not hold: the
 * opening query can land on a replica, but the cursor read/delete always goes to the primary, so a
 * cursor opened on a replica fails on its next page. Use {@code ReadFrom.PRIMARY} on a standalone
 * client whenever {@code withCursor} is set, until this is fixed in glide-core (tracked
 * separately).
 */
public class FTWithCursor {
    private final Integer count;
    private final Integer maxIdle;

    /** Initialize a new instance with no count or maxidle. */
    public FTWithCursor() {
        this.count = null;
        this.maxIdle = null;
    }

    /**
     * Initialize a new instance.
     *
     * @param count The number of rows to return per page.
     */
    public FTWithCursor(int count) {
        this.count = count;
        this.maxIdle = null;
    }

    /**
     * Initialize a new instance.
     *
     * @param count The number of rows to return per page.
     * @param maxIdle How long (in milliseconds) the cursor may sit idle between reads before the
     *     server discards it.
     */
    public FTWithCursor(int count, int maxIdle) {
        this.count = count;
        this.maxIdle = maxIdle;
    }

    /** Get the arguments for the WITHCURSOR option. */
    public GlideString[] toArgs() {
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs("WITHCURSOR"));
        if (count != null) {
            args.add(gs("COUNT"));
            args.add(gs(count.toString()));
        }
        if (maxIdle != null) {
            args.add(gs("MAXIDLE"));
            args.add(gs(maxIdle.toString()));
        }
        return args.toArray(new GlideString[0]);
    }
}
