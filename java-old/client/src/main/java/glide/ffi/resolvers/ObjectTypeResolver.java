/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import glide.api.models.commands.scan.ScanOptions;

/** Helper class for invoking JNI resources for the {@link ScanOptions.ObjectType} enum. */
public class ObjectTypeResolver {
    public static final String OBJECT_TYPE_STRING_NATIVE_NAME;
    public static final String OBJECT_TYPE_LIST_NATIVE_NAME;
    public static final String OBJECT_TYPE_SET_NATIVE_NAME;
    public static final String OBJECT_TYPE_ZSET_NATIVE_NAME;
    public static final String OBJECT_TYPE_HASH_NATIVE_NAME;
    public static final String OBJECT_TYPE_STREAM_NATIVE_NAME;

    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
        OBJECT_TYPE_STRING_NATIVE_NAME = getTypeStringConstant();
        OBJECT_TYPE_LIST_NATIVE_NAME = getTypeListConstant();
        OBJECT_TYPE_SET_NATIVE_NAME = getTypeSetConstant();
        OBJECT_TYPE_ZSET_NATIVE_NAME = getTypeZSetConstant();
        OBJECT_TYPE_HASH_NATIVE_NAME = getTypeHashConstant();
        OBJECT_TYPE_STREAM_NATIVE_NAME = getTypeStreamConstant();
    }

    public static native String getTypeStringConstant();

    public static native String getTypeListConstant();

    public static native String getTypeSetConstant();

    public static native String getTypeZSetConstant();

    public static native String getTypeHashConstant();

    public static native String getTypeStreamConstant();
}
