/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import static glide.utils.ArrayTransformUtils.castArray;

import glide.api.models.exceptions.GlideException;
import lombok.Getter;

public class ScanResult<T> {

    @Getter private final ScanCursor cursor;

    @Getter private final T data;

    private ScanResult(ScanCursor cursor, T data) {
        this.cursor = cursor;
        this.data = data;
    }

    @SuppressWarnings("unchecked")
    public static <T> ScanResult<T> createScanResult(Class<T> tClass, Object[] result)
            throws GlideException {
        if (result == null || result.length != 2) {
            throw new GlideException("Null return data or unexpected return data structure");
        }
        if (result[1] == null || !(result[1] instanceof Object[])) {
            String className = result[1] == null ? "null" : result[1].getClass().getSimpleName();
            throw new GlideException(
                    "Unexpected return type from Glide: got "
                            + className
                            + " expected "
                            + tClass.getSimpleName());
        }
        ScanCursor cursor = new ScanCursor(result[0].toString());
        T data = (T) castArray((Object[]) result[1], tClass.getComponentType());
        return new ScanResult<>(cursor, data);
    }
}
