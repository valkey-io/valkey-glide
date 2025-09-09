/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/**
 * Transaction implementation for standalone GlideClient.
 * This class provides backward compatibility with the legacy Transaction API.
 * 
 * <p>A Transaction is essentially a Batch that is always executed atomically.
 * This class extends Batch and forces atomic execution for compatibility with
 * legacy code that expects Transaction semantics.
 *
 * @see Batch
 * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions</a>
 */
public class Transaction extends Batch {

    /**
     * Creates a new Transaction instance.
     * Transactions are always atomic (transactional).
     */
    public Transaction() {
        super(true); // Always atomic
    }

    /**
     * Creates a new Transaction instance with explicit atomic setting.
     * 
     * @param isAtomic Should always be true for transactions. If false, 
     *                 this behaves like a regular batch.
     * @deprecated Use the default constructor. Transactions should always be atomic.
     */
    @Deprecated
    public Transaction(boolean isAtomic) {
        super(isAtomic);
    }
}