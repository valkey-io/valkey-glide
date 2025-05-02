/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Exec aborted error: Errors that are thrown when an Atomic Batch (Transaction) is aborted. */
public class ExecAbortException extends GlideException {
    public ExecAbortException(String message) {
        super(message);
    }
}
