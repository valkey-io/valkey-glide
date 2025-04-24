/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/**
 * @deprecated This class is deprecated and should no longer be used. Use {@link Batch} instead.
 */
@Deprecated
public class Transaction extends Batch {
    /** Creates a new Transaction object. */
    public Transaction() {
        super(true);
    }

    @Override
    protected Transaction getThis() {
        return this;
    }
}
