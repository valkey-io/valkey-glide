// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::Cmd;

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
    pub fn compression_incompatibility_reason(self) -> Option<&'static str> {
        match self {
            // String manipulation commands
            RequestType::Append => Some("APPEND modifies string data on the server"),
            RequestType::GetRange => Some("GETRANGE returns a substring of raw bytes"),
            RequestType::SetRange => Some("SETRANGE modifies bytes at a specific offset"),
            RequestType::Strlen => Some("STRLEN returns the length of raw bytes"),
            RequestType::LCS => Some("LCS compares raw bytes between strings"),
            RequestType::Substr => Some("SUBSTR returns a substring of raw bytes"),
            // Numeric operations
            RequestType::Incr => Some("INCR expects a numeric string value"),
            RequestType::IncrBy => Some("INCRBY expects a numeric string value"),
            RequestType::IncrByFloat => Some("INCRBYFLOAT expects a numeric string value"),
            RequestType::Decr => Some("DECR expects a numeric string value"),
            RequestType::DecrBy => Some("DECRBY expects a numeric string value"),
            // Bit operations
            RequestType::GetBit => Some("GETBIT reads bits from raw bytes"),
            RequestType::SetBit => Some("SETBIT modifies bits in raw bytes"),
            RequestType::BitCount => Some("BITCOUNT counts bits in raw bytes"),
            RequestType::BitPos => Some("BITPOS finds bit positions in raw bytes"),
            RequestType::BitField => Some("BITFIELD performs bit operations on raw bytes"),
            RequestType::BitFieldReadOnly => Some("BITFIELD_RO reads bits from raw bytes"),
            RequestType::BitOp => Some("BITOP performs bitwise operations between strings"),
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
            "SET" => Some(RequestType::Set),
            "MSET" => Some(RequestType::MSet),
            "MSETNX" => Some(RequestType::MSetNX),
            "SETEX" => Some(RequestType::SetEx),
            "PSETEX" => Some(RequestType::PSetEx),
            "SETNX" => Some(RequestType::SetNX),
            "GET" => Some(RequestType::Get),
            "MGET" => Some(RequestType::MGet),
            "GETEX" => Some(RequestType::GetEx),
            "GETDEL" => Some(RequestType::GetDel),
            "GETSET" => Some(RequestType::GetSet),
            // Incompatible commands - string manipulation
            "APPEND" => Some(RequestType::Append),
            "GETRANGE" => Some(RequestType::GetRange),
            "SETRANGE" => Some(RequestType::SetRange),
            "STRLEN" => Some(RequestType::Strlen),
            "LCS" => Some(RequestType::LCS),
            "SUBSTR" => Some(RequestType::Substr),
            // Incompatible commands - numeric operations
            "INCR" => Some(RequestType::Incr),
            "INCRBY" => Some(RequestType::IncrBy),
            "INCRBYFLOAT" => Some(RequestType::IncrByFloat),
            "DECR" => Some(RequestType::Decr),
            "DECRBY" => Some(RequestType::DecrBy),
            // Incompatible commands - bit operations
            "GETBIT" => Some(RequestType::GetBit),
            "SETBIT" => Some(RequestType::SetBit),
            "BITCOUNT" => Some(RequestType::BitCount),
            "BITPOS" => Some(RequestType::BitPos),
            "BITFIELD" => Some(RequestType::BitField),
            "BITFIELD_RO" => Some(RequestType::BitFieldReadOnly),
            "BITOP" => Some(RequestType::BitOp),
            // Unknown command - not relevant for compression
            _ => None,
        }
    }

    /// Returns the command name as a string, if available.
    pub fn command_name(self) -> Option<&'static str> {
        match self {
            RequestType::InvalidRequest => None,
            RequestType::CustomCommand => Some("CUSTOM"),
            RequestType::Get => Some("GET"),
            RequestType::Set => Some("SET"),
            RequestType::Del => Some("DEL"),
            RequestType::Exists => Some("EXISTS"),
            RequestType::HGet => Some("HGET"),
            RequestType::HSet => Some("HSET"),
            RequestType::HDel => Some("HDEL"),
            RequestType::LPush => Some("LPUSH"),
            RequestType::RPush => Some("RPUSH"),
            RequestType::LPop => Some("LPOP"),
            RequestType::RPop => Some("RPOP"),
            RequestType::Expire => Some("EXPIRE"),
            RequestType::TTL => Some("TTL"),
            RequestType::MSet => Some("MSET"),
            RequestType::MSetNX => Some("MSETNX"),
            RequestType::SetEx => Some("SETEX"),
            RequestType::PSetEx => Some("PSETEX"),
            RequestType::SetNX => Some("SETNX"),
            RequestType::MGet => Some("MGET"),
            RequestType::GetEx => Some("GETEX"),
            RequestType::GetDel => Some("GETDEL"),
            RequestType::GetSet => Some("GETSET"),
            RequestType::Append => Some("APPEND"),
            RequestType::GetRange => Some("GETRANGE"),
            RequestType::SetRange => Some("SETRANGE"),
            RequestType::Strlen => Some("STRLEN"),
            RequestType::LCS => Some("LCS"),
            RequestType::Substr => Some("SUBSTR"),
            RequestType::Incr => Some("INCR"),
            RequestType::IncrBy => Some("INCRBY"),
            RequestType::IncrByFloat => Some("INCRBYFLOAT"),
            RequestType::Decr => Some("DECR"),
            RequestType::DecrBy => Some("DECRBY"),
            RequestType::GetBit => Some("GETBIT"),
            RequestType::SetBit => Some("SETBIT"),
            RequestType::BitCount => Some("BITCOUNT"),
            RequestType::BitPos => Some("BITPOS"),
            RequestType::BitField => Some("BITFIELD"),
            RequestType::BitFieldReadOnly => Some("BITFIELD_RO"),
            RequestType::BitOp => Some("BITOP"),
        }
    }
}
