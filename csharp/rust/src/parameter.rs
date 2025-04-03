use redis::{RedisWrite, ToRedisArgs};
use std::ffi::{
    c_char, c_double, c_float, c_int, c_longlong, c_short, c_uchar, c_uint, c_ulonglong, c_ushort,
};

#[repr(C)]
pub struct KeyParameterPair {
    pub key: *const c_char,
    pub key_length: c_uint,
    pub value: Parameter,
    pub next: *const KeyParameterPair,
}
#[repr(C)]
pub enum EParameterKind {
    Bool,
    Int8,
    Uint8,
    Int16,
    Uint16,
    Int32,
    Uint32,
    Int64,
    Uint64,
    Float32,
    Float64,
    String,
    BoolArray,
    Int8Array,
    Uint8Array,
    Int16Array,
    Uint16Array,
    Int32Array,
    Uint32Array,
    Int64Array,
    Uint64Array,
    Float32Array,
    Float64Array,
    KeyValueArray,
}

#[repr(C)]
pub union ParameterValue {
    pub flag: c_char,
    pub i8: c_char,
    pub u8: c_uchar,
    pub i16: c_short,
    pub u16: c_ushort,
    pub i32: c_int,
    pub u32: c_uint,
    pub i64: c_longlong,
    pub u64: c_ulonglong,
    pub f32: c_float,
    pub f64: c_double,
    pub string: *const c_char,
    pub flag_array: *const c_char,
    pub i8_array: *const c_char,
    pub u8_array: *const c_uchar,
    pub i16_array: *const c_short,
    pub u16_array: *const c_ushort,
    pub i32_array: *const c_int,
    pub u32_array: *const c_uint,
    pub i64_array: *const c_longlong,
    pub u64_array: *const c_ulonglong,
    pub f32_array: *const c_float,
    pub f64_array: *const c_double,
    pub key_parameter_array: *const KeyParameterPair,
}
#[repr(C)]
pub struct Parameter {
    pub kind: EParameterKind,
    pub value: ParameterValue,
    pub value_length: c_uint,
}

impl ToRedisArgs for Parameter {
    fn write_redis_args<W>(&self, out: &mut W)
    where
        W: ?Sized + RedisWrite,
    {
        todo!("Parse the parameter structure, using it as ReadOnly, and use RedisWrite operations to write out the parameter appropriately")
        // D:\dev\git\valkey-glide\glide-core\redis-rs\redis\src\types.rs
    }
}
