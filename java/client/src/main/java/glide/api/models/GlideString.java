/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

// TODO docs for the god of docs
public class GlideString implements Comparable<GlideString> {

    @Getter private byte[] bytes;
    private String string = null;

    /** Flag whether possibility to convert to string was checked. */
    private final AtomicBoolean conversionChecked = new AtomicBoolean(false);

    private GlideString() {}

    public static GlideString of(String string) {
        var res = new GlideString();
        res.string = string;
        res.bytes = string.getBytes(StandardCharsets.UTF_8);
        return res;
    }

    public static GlideString of(byte[] bytes) {
        var res = new GlideString();
        res.bytes = bytes;
        return res;
    }

    /** Allow converting any type to GlideString */
    public static <ArgType> GlideString of(ArgType o) {
        if (o instanceof GlideString) {
            return (GlideString) o;
        } else if (o instanceof byte[]) {
            return GlideString.of((byte[]) o);
        } else if (o instanceof String) {
            return GlideString.of((String) o);
        } else {
            var res = new GlideString();
            res.string = o.toString();
            res.bytes = res.string.getBytes(StandardCharsets.UTF_8);
            return res;
        }
    }

    public static GlideString gs(String string) {
        return GlideString.of(string);
    }

    public static GlideString gs(byte[] bytes) {
        return GlideString.of(bytes);
    }

    @Override
    public String toString() {
        return getString();
    }

    public String getString() {
        if (string != null) {
            return string;
        }

        assert canConvertToString() : "Value cannot be represented as a string";
        return string;
    }

    public int compareTo(GlideString o) {
        return Arrays.compare(this.bytes, o.bytes);
    }

    public boolean canConvertToString() {
        if (string != null) {
            return true;
        }

        // double-checked locking
        if (conversionChecked.get()) {
            return false;
        } else {
            synchronized (this) {
                if (conversionChecked.get()) {
                    return false;
                } else
                    try {
                        // TODO find a better way to check this
                        // Detect whether `bytes` could be represented by a `String` without data corruption
                        var tmpStr = new String(bytes, StandardCharsets.UTF_8);
                        if (Arrays.equals(bytes, tmpStr.getBytes(StandardCharsets.UTF_8))) {
                            string = tmpStr;
                            return true;
                        } else {
                            return false;
                        }
                    } finally {
                        conversionChecked.set(true);
                    }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlideString)) return false;
        GlideString that = (GlideString) o;

        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
