/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/**
 * @deprecated This class is deprecated and should no longer be used. Use {@link ClusterBatch}
 *     instead.
 */
@Deprecated
public class ClusterTransaction extends ClusterBatch {
    /** Creates a new ClusterTransaction object. */
    public ClusterTransaction() {
        super(true);
    }

    @Override
    protected ClusterTransaction getThis() {
        return this;
    }
}
