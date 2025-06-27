/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;

public class ScanCursor {

    private final String cursor;

    public ScanCursor(String cursor) {
        this.cursor = cursor;
    }

    public ScanCursor(GlideString cursor) {
        this.cursor = cursor.getString();
    }

    public String getString() {
        return this.cursor;
    }

    public GlideString getGlideString() {
        return gs(this.cursor);
    }
}
