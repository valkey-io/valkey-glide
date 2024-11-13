/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

public enum PasswordUpdateMode {
    /** The client will re-authenticate immediately with the new password. */
    RE_AUTHENTICATE(true),
    /** The new password will be used for the next connection attempt. */
    USE_ON_NEW_CONNECTION(false);

    PasswordUpdateMode(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    private final boolean value;
}
