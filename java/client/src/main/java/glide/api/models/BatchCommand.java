/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/** Plain data container for a single command in a batch (pipeline/transaction). */
public final class BatchCommand {
    private final int requestType;
    private final byte[][] args;

    public BatchCommand(int requestType, byte[][] args) {
        this.requestType = requestType;
        this.args = args;
    }

    public int getRequestType() {
        return requestType;
    }

    public byte[][] getArgs() {
        return args;
    }
}
