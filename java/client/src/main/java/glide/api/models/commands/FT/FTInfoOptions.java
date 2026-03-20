/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;

/**
 * Additional arguments for the FT.INFO command.
 *
 * @see <a href="https://valkey.io/commands/ft.info/">valkey.io</a>
 */
public class FTInfoOptions {

    /** Controls which nodes provide index information in cluster mode. */
    public enum InfoScope {
        /** Only the executing (local) node provides index information. This is the default. */
        LOCAL,
        /** Primary nodes of every shard are queried. Only valid in cluster mode. */
        PRIMARY,
        /** All nodes (primary and replica) are queried. Only valid in cluster mode. */
        CLUSTER
    }

    /** Controls shard participation in cluster mode. */
    public enum ShardScope {
        /** Terminate with error if not all shards respond. This is the default. */
        ALLSHARDS,
        /** Generate best-effort reply if not all shards respond within timeout. */
        SOMESHARDS
    }

    /** Controls consistency requirements in cluster mode. */
    public enum ConsistencyMode {
        /** Terminate with error if any response isn't from a consistent index version. Default. */
        CONSISTENT,
        /** Use only responses from nodes with a consistent index version. */
        INCONSISTENT
    }

    private final InfoScope scope;
    private final ShardScope shardScope;
    private final ConsistencyMode consistency;

    public FTInfoOptions(InfoScope scope, ShardScope shardScope, ConsistencyMode consistency) {
        this.scope = scope;
        this.shardScope = shardScope;
        this.consistency = consistency;
    }

    public FTInfoOptions(InfoScope scope) {
        this(scope, null, null);
    }

    public FTInfoOptions(ShardScope shardScope, ConsistencyMode consistency) {
        this(null, shardScope, consistency);
    }

    /** Convert to module API args. */
    public GlideString[] toArgs() {
        ArrayList<GlideString> args = new ArrayList<>();
        if (scope != null) {
            args.add(gs(scope.toString()));
        }
        if (shardScope != null) {
            args.add(gs(shardScope.toString()));
        }
        if (consistency != null) {
            args.add(gs(consistency.toString()));
        }
        return args.toArray(new GlideString[0]);
    }
}
