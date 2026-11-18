// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::Cmd;

/// Mirror of a subset of `glide-core::request_type::RequestType`,
/// used only so `cargo miri test` can run FFI tests without pulling in real
/// `glide-core`. Discriminant values here are independent of the real enum's
/// protobuf ids. When a command's compression-incompatibility or
/// custom-command resolution behavior becomes relevant to an FFI miri test,
/// mirror it here too.
#[derive(Debug, Clone, Copy)]
#[repr(C)]
pub enum RequestType {
    /// Invalid request type
    InvalidRequest = 0,

    // Custom command
    CustomCommand = 1,

    // Basic string commands for testing
    Get = 1504,
    Set = 1517,
    Del = 402,
    Exists = 404,

    // Hash commands for testing
    HGet = 603,
    HSet = 613,
    HDel = 601,

    // List commands for testing
    LPush = 801,
    RPush = 820,
    LPop = 809,
    RPop = 819,

    // Other common commands
    Expire = 405,
    TTL = 428,

    // Commands that support compression
    MSet = 1508,
    MSetNX = 1509,
    SetEx = 1518,
    PSetEx = 1512,
    SetNX = 1519,
    MGet = 1507,
    GetEx = 1503,
    GetDel = 1502,
    GetSet = 1505,

    // Incompatible commands - string manipulation
    Append = 1501,
    GetRange = 1506,
    SetRange = 1520,
    Strlen = 1521,
    LCS = 1522,
    Substr = 1523,
    DelIfEq = 1531,

    // Incompatible commands - numeric operations
    Incr = 1510,
    IncrBy = 1511,
    IncrByFloat = 1513,
    Decr = 1514,
    DecrBy = 1515,

    // Incompatible commands - bit operations
    GetBit = 1524,
    SetBit = 1525,
    BitCount = 1526,
    BitPos = 1527,
    BitField = 1528,
    BitFieldReadOnly = 1529,
    BitOp = 1530,
}

impl RequestType {
    pub fn get_command(&self) -> Option<Cmd> {
        match self {
            RequestType::InvalidRequest => None,
            _ => Some(Cmd::default()),
        }
    }

    /// Returns the reason why this command is incompatible with compression, if any.
    /// Returns `None` if the command is compatible with compression.
    pub fn compression_incompatibility_reason(self, _args: &[Vec<u8>]) -> Option<&'static str> {
        match self {
            // String manipulation commands
            RequestType::Append => Some("APPEND modifies string data on the server"),
            RequestType::DelIfEq => Some("DELIFEQ compares the stored value against raw bytes"),
            RequestType::GetRange => Some("GETRANGE returns a substring of raw bytes"),
            RequestType::LCS => Some("LCS compares raw bytes between strings"),
            RequestType::SetRange => Some("SETRANGE modifies bytes at a specific offset"),
            RequestType::Strlen => Some("STRLEN returns the length of raw bytes"),
            RequestType::Substr => Some("SUBSTR returns a substring of raw bytes"),
            // Numeric operations
            RequestType::Decr => Some("DECR expects a numeric string value"),
            RequestType::DecrBy => Some("DECRBY expects a numeric string value"),
            RequestType::Incr => Some("INCR expects a numeric string value"),
            RequestType::IncrBy => Some("INCRBY expects a numeric string value"),
            RequestType::IncrByFloat => Some("INCRBYFLOAT expects a numeric string value"),
            // Bit operations
            RequestType::BitCount => Some("BITCOUNT counts bits in raw bytes"),
            RequestType::BitField => Some("BITFIELD performs bit operations on raw bytes"),
            RequestType::BitFieldReadOnly => Some("BITFIELD_RO reads bits from raw bytes"),
            RequestType::BitOp => Some("BITOP performs bitwise operations between strings"),
            RequestType::BitPos => Some("BITPOS finds bit positions in raw bytes"),
            RequestType::GetBit => Some("GETBIT reads bits from raw bytes"),
            RequestType::SetBit => Some("SETBIT modifies bits in raw bytes"),
            // All other commands are compatible
            _ => None,
        }
    }

    /// Parses a command name string and returns the corresponding `RequestType`.
    /// This is used for CustomCommand handling where we need to determine the actual
    /// command type from the command name for compression processing.
    ///
    /// Returns `None` if the command name is not recognized or not relevant for compression.
    pub fn from_command_name(name: &str) -> Option<Self> {
        match name.to_uppercase().as_str() {
            // Commands that support compression
            "GET" => Some(RequestType::Get),
            "GETDEL" => Some(RequestType::GetDel),
            "GETEX" => Some(RequestType::GetEx),
            "GETSET" => Some(RequestType::GetSet),
            "MGET" => Some(RequestType::MGet),
            "MSET" => Some(RequestType::MSet),
            "MSETNX" => Some(RequestType::MSetNX),
            "PSETEX" => Some(RequestType::PSetEx),
            "SET" => Some(RequestType::Set),
            "SETEX" => Some(RequestType::SetEx),
            "SETNX" => Some(RequestType::SetNX),
            // Incompatible commands - string manipulation
            "APPEND" => Some(RequestType::Append),
            "DELIFEQ" => Some(RequestType::DelIfEq),
            "GETRANGE" => Some(RequestType::GetRange),
            "LCS" => Some(RequestType::LCS),
            "SETRANGE" => Some(RequestType::SetRange),
            "STRLEN" => Some(RequestType::Strlen),
            "SUBSTR" => Some(RequestType::Substr),
            // Incompatible commands - numeric operations
            "DECR" => Some(RequestType::Decr),
            "DECRBY" => Some(RequestType::DecrBy),
            "INCR" => Some(RequestType::Incr),
            "INCRBY" => Some(RequestType::IncrBy),
            "INCRBYFLOAT" => Some(RequestType::IncrByFloat),
            // Incompatible commands - bit operations
            "BITCOUNT" => Some(RequestType::BitCount),
            "BITFIELD" => Some(RequestType::BitField),
            "BITFIELD_RO" => Some(RequestType::BitFieldReadOnly),
            "BITOP" => Some(RequestType::BitOp),
            "BITPOS" => Some(RequestType::BitPos),
            "GETBIT" => Some(RequestType::GetBit),
            "SETBIT" => Some(RequestType::SetBit),
            // Unknown command - not relevant for compression
            _ => None,
        }
    }

    /// Returns the command name as a string, if available.
    pub fn command_name(self) -> Option<&'static str> {
        match self {
            RequestType::InvalidRequest => None,
            RequestType::CustomCommand => Some("CUSTOM"),

            RequestType::Append => Some("APPEND"),
            RequestType::BitCount => Some("BITCOUNT"),
            RequestType::BitField => Some("BITFIELD"),
            RequestType::BitFieldReadOnly => Some("BITFIELD_RO"),
            RequestType::BitOp => Some("BITOP"),
            RequestType::BitPos => Some("BITPOS"),
            RequestType::Decr => Some("DECR"),
            RequestType::DecrBy => Some("DECRBY"),
            RequestType::Del => Some("DEL"),
            RequestType::DelIfEq => Some("DELIFEQ"),
            RequestType::Exists => Some("EXISTS"),
            RequestType::Expire => Some("EXPIRE"),
            RequestType::Get => Some("GET"),
            RequestType::GetBit => Some("GETBIT"),
            RequestType::GetDel => Some("GETDEL"),
            RequestType::GetEx => Some("GETEX"),
            RequestType::GetRange => Some("GETRANGE"),
            RequestType::GetSet => Some("GETSET"),
            RequestType::HDel => Some("HDEL"),
            RequestType::HGet => Some("HGET"),
            RequestType::HSet => Some("HSET"),
            RequestType::Incr => Some("INCR"),
            RequestType::IncrBy => Some("INCRBY"),
            RequestType::IncrByFloat => Some("INCRBYFLOAT"),
            RequestType::LCS => Some("LCS"),
            RequestType::LPop => Some("LPOP"),
            RequestType::LPush => Some("LPUSH"),
            RequestType::MGet => Some("MGET"),
            RequestType::MSet => Some("MSET"),
            RequestType::MSetNX => Some("MSETNX"),
            RequestType::PSetEx => Some("PSETEX"),
            RequestType::RPop => Some("RPOP"),
            RequestType::RPush => Some("RPUSH"),
            RequestType::Set => Some("SET"),
            RequestType::SetBit => Some("SETBIT"),
            RequestType::SetEx => Some("SETEX"),
            RequestType::SetNX => Some("SETNX"),
            RequestType::SetRange => Some("SETRANGE"),
            RequestType::Strlen => Some("STRLEN"),
            RequestType::Substr => Some("SUBSTR"),
            RequestType::TTL => Some("TTL"),
        }
    }
}
