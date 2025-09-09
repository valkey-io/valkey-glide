/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/**
 * ClusterTransaction implementation for cluster GlideClusterClient.
 * This class provides backward compatibility with the legacy ClusterTransaction API.
 * 
 * <p>A ClusterTransaction is essentially a ClusterBatch that is always executed atomically.
 * This class extends ClusterBatch and forces atomic execution for compatibility with
 * legacy code that expects Transaction semantics.
 *
 * @see ClusterBatch
 * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions</a>
 */
public class ClusterTransaction extends ClusterBatch {

    /**
     * Creates a new ClusterTransaction instance.
     * Transactions are always atomic (transactional).
     */
    public ClusterTransaction() {
        super(true); // Always atomic
    }

    /**
     * Creates a new ClusterTransaction instance with explicit atomic setting.
     * 
     * @param isAtomic Should always be true for transactions. If false, 
     *                 this behaves like a regular cluster batch.
     * @deprecated Use the default constructor. Transactions should always be atomic.
     */
    @Deprecated
    public ClusterTransaction(boolean isAtomic) {
        super(isAtomic);
    }
}