/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * Represents a Valkey string type. Since Valkey stores strings as <code>byte[]</code>, such strings
 * can contain non-UTF8 compatible symbols or even arbitrary binary data BLOBs.<br>
 * This class stores data <code>byte[]</code> too, but provides API to represent data as a {@link
 * String} if conversion is possible.
 *
 * @see <a href="@see https://valkey.io/docs/topics/strings/">valkey.io</a> for more details.
 */
public class GlideString implements Comparable<GlideString> {

    /** The Valkey string as a binary representation. */
    private final byte[] bytes;

    /** Get the byte array. Note: Returns the internal array, do not modify! */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Stores a string when it is possible.<br>
     * {@link String} representation of the value is only stored if conversion via {@link
     * #canConvertToString()} is possible. The conversion is lazy, and only converted on the first
     * call {@link #toString()}, {@link #getString()}, or {@link #canConvertToString()}.
     */
    private String string = null;

    /** Flag whether possibility to convert to string was checked. */
    private final AtomicBoolean conversionChecked = new AtomicBoolean(false);

    /** Constructor is private - use {@link #gs} or {@link #of} to instantiate an object. */
    private GlideString(byte[] bytes) {
        this.bytes = bytes;
    }

    /** Create a GlideString using a {@link String}. */
    public static GlideString of(String string) {
        var res = new GlideString(string.getBytes(StandardCharsets.UTF_8));
        res.string = string;
        return res;
    }

    /** Create a GlideString using a byte array. */
    public static GlideString of(byte[] bytes) {
        return new GlideString(bytes);
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
            String str = o.toString();
            var res = new GlideString(str.getBytes(StandardCharsets.UTF_8));
            res.string = str;
            return res;
        }
    }

    /** Create a GlideString using a {@link String}. */
    public static GlideString gs(String string) {
        return GlideString.of(string);
    }

    /** Create a GlideString using a byte array. */
    public static GlideString gs(byte[] bytes) {
        return GlideString.of(bytes);
    }

    /** Converts stored data to a human-friendly {@link String} if it is possible. */
    @Override
    public String toString() {
        return getString();
    }

    /** Converts stored data to a human-friendly {@link String} if it is possible. */
    public String getString() {
        if (string != null) {
            return string;
        }

        if (canConvertToString()) {
            return string;
        }
        return String.format("Value not convertible to string: byte[] %d", Arrays.hashCode(bytes));
    }

    /** Compare with another GlideString. */
    public int compareTo(GlideString o) {
        return Arrays.compare(this.bytes, o.bytes);
    }

    /** Check whether stored data could be converted to a {@link String}. */
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
                } else {
                    try {
                        // Detect whether `bytes` could be represented by a `String` without data corruption
                        // This approach ensures UTF-8 roundtrip compatibility
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

    /** Method to concatenate two GlideString objects */
    public GlideString concat(GlideString other) {
        byte[] concatenatedBytes = new byte[this.bytes.length + other.bytes.length];
        System.arraycopy(this.bytes, 0, concatenatedBytes, 0, this.bytes.length);
        System.arraycopy(other.bytes, 0, concatenatedBytes, this.bytes.length, other.bytes.length);
        return GlideString.of(concatenatedBytes);
    }
}
