use anyhow::{Result, anyhow};
use bincode::{Decode, Encode};
use redis::cluster_topology::get_slot;
use redis::{
    Cmd,
    cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo, SingleNodeRoutingInfo},
};
use serde::{Deserialize, Serialize};
// Removed: use std::io::{Cursor, Read}; - no longer needed with BinaryProtocol
use crate::binary_protocol::BinaryProtocol;

/// CommandRequest represents a Valkey command with arguments and routing information
#[derive(Debug, Clone, Serialize, Deserialize, Encode, Decode, Default)]
pub struct CommandRequest {
    pub command_type: String,
    pub arguments: Vec<String>,
    pub routing: Option<RouteInfo>,
}

/// Binary command argument that can be either string or binary data
#[derive(Debug, Clone, Serialize, Deserialize, Encode, Decode)]
pub enum BinaryValue {
    String(String),
    Binary(Vec<u8>),
}

/// BinaryCommandRequest represents a Valkey command with mixed String/binary arguments
/// NOTE: Only routing is supported per-command by glide-core.
/// Connection-level settings like timeout and database are handled at client level.
#[derive(Debug, Clone, Serialize, Deserialize, Encode, Decode, Default)]
pub struct BinaryCommandRequest {
    pub command_type: String,
    pub arguments: Vec<BinaryValue>,
    pub routing: Option<RouteInfo>,
}

/// ClusterScanRequest represents a cluster scan command with cursor and options
#[derive(Debug, Clone, Serialize, Deserialize, Encode, Decode, Default)]
pub struct ClusterScanRequest {
    pub cursor_id: Option<String>,
    pub match_pattern: Option<Vec<u8>>,
    pub count: Option<u32>,
    pub object_type: Option<String>,
    pub allow_non_covered_slots: Option<bool>,
    pub timeout_ms: Option<u64>,
    pub binary_mode: Option<bool>,
}

/// Routing information for command execution
#[derive(Debug, Clone, Serialize, Deserialize, Encode, Decode)]
pub enum RouteInfo {
    /// Route to any node (default)
    Random,
    /// Route to specific slot with explicit primary/replica choice
    SlotId { id: u16, replica: bool },
    /// Route to specific address
    ByAddress { host: String, port: u16 },
    /// Route to all nodes
    AllNodes,
    /// Route to all primaries
    AllPrimaries,
    /// Route to primary for key
    PrimaryForKey(String),
    /// Route to replica for key (if available)
    ReplicaForKey(String),
}

impl CommandRequest {
    /// Create new command request
    pub fn new(command_type: impl Into<String>) -> Self {
        Self {
            command_type: command_type.into(),
            arguments: Vec::new(),
            routing: None,
        }
    }

    /// Builder pattern for efficient construction
    pub fn arg<T: ToString>(mut self, arg: T) -> Self {
        self.arguments.push(arg.to_string());
        self
    }

    /// Add multiple arguments efficiently
    pub fn args<I, T>(mut self, args: I) -> Self
    where
        I: IntoIterator<Item = T>,
        T: ToString,
    {
        self.arguments
            .extend(args.into_iter().map(|a| a.to_string()));
        self
    }

    /// Set routing information
    pub fn route(mut self, routing: RouteInfo) -> Self {
        self.routing = Some(routing);
        self
    }


    /// Convert to Valkey command
    pub fn to_valkey_cmd(&self) -> Result<Cmd> {
        let mut cmd = Cmd::new();

        // Use efficient command building
        cmd.arg(&self.command_type);

        // Add arguments in batch for better performance
        for arg in &self.arguments {
            cmd.arg(arg);
        }

        Ok(cmd)
    }

    /// Convert routing information to server format (thin mapping; core decides actual routing)
    pub fn to_server_routing(&self) -> Option<RoutingInfo> {
        match &self.routing {
            Some(RouteInfo::Random) => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
            Some(RouteInfo::AllPrimaries) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllMasters,
                None,
            ))),
            Some(RouteInfo::AllNodes) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllNodes,
                None,
            ))),
            // SlotId: pass specific route with requested addr kind
            Some(RouteInfo::SlotId { id, replica }) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    *id,
                    if *replica {
                        redis::cluster_routing::SlotAddr::ReplicaRequired
                    } else {
                        redis::cluster_routing::SlotAddr::Master
                    },
                )),
            )),
            // Key-based: compute slot and set addr kind accordingly
            Some(RouteInfo::PrimaryForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::Master,
                )),
            )),
            Some(RouteInfo::ReplicaForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::ReplicaRequired,
                )),
            )),
            // Address: pass through
            Some(RouteInfo::ByAddress { host, port }) => {
                Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                    host: host.clone(),
                    port: *port,
                }))
            }
            None => None,
        }
    }

    /// Generate Java-compatible binary format (for testing)
    pub fn to_bytes(&self) -> Result<Vec<u8>> {
        let estimated_size = std::mem::size_of::<Self>()
            + self.command_type.len()
            + self.arguments.iter().map(|s| s.len() + 4).sum::<usize>()
            + 32;
        let mut writer = BinaryProtocol::new_writer("CommandRequest", estimated_size);

        // Write command type
        writer.write_string(&self.command_type, "command_type")?;

        // Write arguments
        writer.write_u32(self.arguments.len() as u32, "arg_count")?;
        for (i, arg) in self.arguments.iter().enumerate() {
            writer.write_string(arg, &format!("argument_{}", i))?;
        }

        // Write routing
        if let Some(route) = &self.routing {
            writer.write_u8(1, "has_route")?; // Has route
            // Simplified routing - just encode type for now
            match route {
                RouteInfo::Random => writer.write_u8(0, "route_type")?,
                RouteInfo::AllPrimaries => writer.write_u8(1, "route_type")?,
                RouteInfo::AllNodes => writer.write_u8(2, "route_type")?,
                _ => writer.write_u8(0, "route_type")?, // Default to random
            }
        } else {
            writer.write_u8(0, "has_route")?; // No route
        }

        writer.build()
    }

    /// Parse command from Java DataOutputStream format
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.is_empty() {
            return Err(anyhow!("Empty command request bytes"));
        }

        let mut reader = BinaryProtocol::new_reader(bytes, "CommandRequest");

        // Read command type
        let command_type = reader.read_string("command_type")?;

        // Read arguments
        let arg_count = reader.read_u32("arg_count")? as usize;
        let mut arguments = Vec::with_capacity(arg_count);
        for i in 0..arg_count {
            arguments.push(reader.read_string(&format!("argument_{}", i))?);
        }

        // Read routing information
        let has_route = reader.read_u8("has_route")? != 0;
        let routing = if has_route {
            Some(parse_route_info_binary(&mut reader)?)
        } else {
            None
        };

        // Validate complete
        reader.validate_complete()?;

        Ok(CommandRequest {
            command_type,
            arguments,
            routing,
        })
    }

    /// Get estimated memory usage for monitoring
    pub fn memory_usage(&self) -> usize {
        std::mem::size_of::<Self>()
            + self.command_type.len()
            + self.arguments.iter().map(|s| s.len()).sum::<usize>()
            + self.routing.as_ref().map(|r| r.memory_usage()).unwrap_or(0)
    }
}

impl RouteInfo {
    fn memory_usage(&self) -> usize {
        match self {
            RouteInfo::ByAddress { host, .. } => std::mem::size_of::<Self>() + host.len(),
            RouteInfo::SlotId { .. } => std::mem::size_of::<Self>(),
            RouteInfo::PrimaryForKey(s) | RouteInfo::ReplicaForKey(s) => {
                std::mem::size_of::<Self>() + s.len()
            }
            _ => std::mem::size_of::<Self>(),
        }
    }
}

impl BinaryCommandRequest {
    /// Create new binary command request
    pub fn new(command_type: impl Into<String>) -> Self {
        Self {
            command_type: command_type.into(),
            arguments: Vec::new(),
            routing: None,
        }
    }

    /// Builder pattern for adding string arguments
    pub fn arg_string<T: ToString>(mut self, arg: T) -> Self {
        self.arguments.push(BinaryValue::String(arg.to_string()));
        self
    }

    /// Builder pattern for adding binary arguments
    pub fn arg_binary(mut self, arg: Vec<u8>) -> Self {
        self.arguments.push(BinaryValue::Binary(arg));
        self
    }

    /// Add BinaryValue argument
    pub fn arg_value(mut self, arg: BinaryValue) -> Self {
        self.arguments.push(arg);
        self
    }

    /// Set routing information
    pub fn route(mut self, routing: RouteInfo) -> Self {
        self.routing = Some(routing);
        self
    }

    /// Convert to Valkey command
    pub fn to_valkey_cmd(&self) -> Result<Cmd> {
        let mut cmd = Cmd::new();
        cmd.arg(&self.command_type);

        for arg in &self.arguments {
            match arg {
                BinaryValue::String(s) => cmd.arg(s),
                BinaryValue::Binary(b) => cmd.arg(b),
            };
        }

        Ok(cmd)
    }

    /// Convert routing information to server format
    pub fn to_server_routing(&self) -> Option<RoutingInfo> {
        match &self.routing {
            Some(RouteInfo::Random) => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
            Some(RouteInfo::AllPrimaries) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllMasters,
                None,
            ))),
            Some(RouteInfo::AllNodes) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllNodes,
                None,
            ))),
            Some(RouteInfo::SlotId { id, replica }) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    *id,
                    if *replica {
                        redis::cluster_routing::SlotAddr::ReplicaRequired
                    } else {
                        redis::cluster_routing::SlotAddr::Master
                    },
                )),
            )),
            Some(RouteInfo::PrimaryForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::Master,
                )),
            )),
            Some(RouteInfo::ReplicaForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::ReplicaRequired,
                )),
            )),
            Some(RouteInfo::ByAddress { host, port }) => {
                Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                    host: host.clone(),
                    port: *port,
                }))
            }
            None => None,
        }
    }

    /// Parse binary command from Java DataOutputStream format
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.is_empty() {
            return Err(anyhow!("Empty binary command request bytes"));
        }

        let mut reader = BinaryProtocol::new_reader(bytes, "BinaryCommandRequest");

        // Read command type
        let command_type = reader.read_string("command_type")?;

        // Read arguments
        let arg_count = reader.read_u32("arg_count")? as usize;
        let mut arguments = Vec::with_capacity(arg_count);
        for i in 0..arg_count {
            // Read argument type marker (0 = string, 1 = binary)
            let arg_type = reader.read_u8(&format!("arg_type_{}", i))?;
            if arg_type == 0 {
                // String argument
                arguments.push(BinaryValue::String(
                    reader.read_string(&format!("string_arg_{}", i))?,
                ));
            } else {
                // Binary argument
                let data = reader.read_bytes(&format!("binary_arg_{}", i))?;
                if data.len() > 100_000_000 {
                    return Err(anyhow!("Binary argument too large: {} bytes", data.len()));
                }
                arguments.push(BinaryValue::Binary(data));
            }
        }

        // Read routing information
        let has_route = reader.read_u8("has_route")? != 0;
        let routing = if has_route {
            Some(parse_route_info_binary(&mut reader)?)
        } else {
            None
        };

        // Validate complete
        reader.validate_complete()?;

        Ok(BinaryCommandRequest {
            command_type,
            arguments,
            routing,
        })
    }

    /// Get estimated memory usage for monitoring
    pub fn memory_usage(&self) -> usize {
        std::mem::size_of::<Self>()
            + self.command_type.len()
            + self
                .arguments
                .iter()
                .map(|arg| match arg {
                    BinaryValue::String(s) => s.len(),
                    BinaryValue::Binary(b) => b.len(),
                })
                .sum::<usize>()
            + self.routing.as_ref().map(|r| r.memory_usage()).unwrap_or(0)
    }
}

/// Parse routing information using BinaryProtocol
fn parse_route_info_binary(reader: &mut BinaryProtocol) -> Result<RouteInfo> {
    // Java ordinals: 0=ALL_NODES, 1=ALL_PRIMARIES, 2=RANDOM, 3=SLOT_KEY, 4=SLOT_ID, 5=BY_ADDRESS
    let route_type = reader.read_u8("route_type")?;
    Ok(match route_type {
        0 => RouteInfo::AllNodes,
        1 => RouteInfo::AllPrimaries,
        2 => RouteInfo::Random,
        3 => {
            // SLOT_KEY: [len+bytes key] [replica: u8]
            let key = reader.read_string("slot_key")?;
            let replica = reader.read_u8("replica_flag")? != 0;
            if replica {
                RouteInfo::ReplicaForKey(key)
            } else {
                RouteInfo::PrimaryForKey(key)
            }
        }
        4 => {
            // SLOT_ID: [u32 slot] [replica: u8]
            let slot = reader.read_u32("slot_id")? as u16;
            let replica = reader.read_u8("replica_flag")? != 0;
            RouteInfo::SlotId { id: slot, replica }
        }
        5 => {
            // BY_ADDRESS: [len+bytes host] [u32 port]
            let host = reader.read_string("host")?;
            let port = reader.read_u32("port")? as u16;
            RouteInfo::ByAddress { host, port }
        }
        _ => RouteInfo::Random,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use redis::cluster_routing::SlotAddr;
    use redis::cluster_topology::get_slot;

    #[test]
    fn test_slot_id_routing_with_replica() {
        let cmd = CommandRequest {
            routing: Some(RouteInfo::SlotId {
                id: 1234,
                replica: true,
            }),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), 1234);
                assert!(matches!(route.slot_addr(), SlotAddr::ReplicaRequired));
            }
            _ => panic!("Expected SingleNode with SpecificNode"),
        }
    }

    #[test]
    fn test_slot_id_routing_to_primary() {
        let cmd = CommandRequest {
            routing: Some(RouteInfo::SlotId {
                id: 5678,
                replica: false,
            }),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), 5678);
                assert!(matches!(route.slot_addr(), SlotAddr::Master));
            }
            _ => panic!("Expected SingleNode with SpecificNode"),
        }
    }

    #[test]
    fn test_slot_key_routing_with_replica() {
        let key = "test_key_123";
        let expected_slot = get_slot(key.as_bytes());

        let cmd = CommandRequest {
            routing: Some(RouteInfo::ReplicaForKey(key.to_string())),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), expected_slot);
                assert!(matches!(route.slot_addr(), SlotAddr::ReplicaRequired));
            }
            _ => panic!("Expected SingleNode with SpecificNode"),
        }
    }

    #[test]
    fn test_slot_key_routing_to_primary() {
        let key = "another_key_456";
        let expected_slot = get_slot(key.as_bytes());

        let cmd = CommandRequest {
            routing: Some(RouteInfo::PrimaryForKey(key.to_string())),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), expected_slot);
                assert!(matches!(route.slot_addr(), SlotAddr::Master));
            }
            _ => panic!("Expected SingleNode with SpecificNode"),
        }
    }

    #[test]
    fn test_by_address_routing() {
        let cmd = CommandRequest {
            routing: Some(RouteInfo::ByAddress {
                host: "valkey.example.com".to_string(),
                port: 6379,
            }),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress { host, port }) => {
                assert_eq!(host, "valkey.example.com");
                assert_eq!(port, 6379);
            }
            _ => panic!("Expected SingleNode with ByAddress"),
        }
    }

    #[test]
    fn test_all_nodes_routing() {
        let cmd = CommandRequest {
            routing: Some(RouteInfo::AllNodes),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)) => {
                // Correctly mapped to AllNodes, not AllMasters
            }
            _ => panic!("Expected MultiNode with AllNodes"),
        }
    }

    #[test]
    fn test_all_primaries_routing() {
        let cmd = CommandRequest {
            routing: Some(RouteInfo::AllPrimaries),
            ..Default::default()
        };

        let routing = cmd.to_server_routing().unwrap();
        match routing {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, None)) => {
                // Correctly mapped to AllMasters
            }
            _ => panic!("Expected MultiNode with AllMasters"),
        }
    }
}

impl ClusterScanRequest {
    /// Create a new cluster scan request builder
    pub fn new() -> Self {
        Self::default()
    }

    /// Set cursor ID
    pub fn cursor_id(mut self, cursor_id: Option<String>) -> Self {
        self.cursor_id = cursor_id;
        self
    }

    /// Set match pattern
    pub fn match_pattern(mut self, pattern: Option<Vec<u8>>) -> Self {
        self.match_pattern = pattern;
        self
    }

    /// Set count limit
    pub fn count(mut self, count: Option<u32>) -> Self {
        self.count = count;
        self
    }

    /// Set object type
    pub fn object_type(mut self, object_type: Option<String>) -> Self {
        self.object_type = object_type;
        self
    }

    /// Set allow non-covered slots
    pub fn allow_non_covered_slots(mut self, allow: Option<bool>) -> Self {
        self.allow_non_covered_slots = allow;
        self
    }

    /// Parse cluster scan request from bytes using robust binary protocol
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        let mut reader = BinaryProtocol::new_reader(bytes, "ClusterScanRequest");

        // Read all 7 presence flags (must match Java side)
        let has_cursor = reader.read_presence_flag("cursor_id")?;
        let has_pattern = reader.read_presence_flag("match_pattern")?;
        let has_count = reader.read_presence_flag("count")?;
        let has_object_type = reader.read_presence_flag("object_type")?;
        let has_allow_non_covered = reader.read_presence_flag("allow_non_covered_slots")?;
        let has_timeout = reader.read_presence_flag("timeout_ms")?;
        let has_binary_mode = reader.read_presence_flag("binary_mode")?;

        // Read actual field values (only if present)
        let cursor_id = if has_cursor {
            Some(reader.read_string("cursor_id")?)
        } else {
            None
        };

        let match_pattern = if has_pattern {
            Some(reader.read_bytes("match_pattern")?)
        } else {
            None
        };

        let count = if has_count {
            Some(reader.read_u32("count")?)
        } else {
            None
        };

        let object_type = if has_object_type {
            Some(reader.read_string("object_type")?)
        } else {
            None
        };

        let allow_non_covered_slots = if has_allow_non_covered {
            Some(reader.read_boolean("allow_non_covered_slots")?)
        } else {
            None
        };

        let timeout_ms = if has_timeout {
            Some(reader.read_u64("timeout_ms")?)
        } else {
            None
        };

        let binary_mode = if has_binary_mode {
            Some(reader.read_boolean("binary_mode")?)
        } else {
            None
        };

        // Validate no unexpected bytes remain
        reader.validate_complete()?;

        Ok(ClusterScanRequest {
            cursor_id,
            match_pattern,
            count,
            object_type,
            allow_non_covered_slots,
            timeout_ms,
            binary_mode,
        })
    }
}

/// Unified batch request containing commands and configuration
/// Crystal clear protocol: only fields that glide-core actually supports
#[derive(Debug)]
pub struct BatchRequest {
    pub commands: Vec<BatchCommand>,
    pub atomic: bool,
    pub timeout_ms: u32,  // 0 means use client default
    pub raise_on_error: bool,
    pub route: Option<RouteInfo>,
    pub binary_output: bool,
}

/// A command in a batch that can be either string or binary
#[derive(Debug)]
pub enum BatchCommand {
    String(CommandRequest),
    Binary(BinaryCommandRequest),
}

impl BatchRequest {
    /// Parse a complete batch request from bytes using unified BinaryProtocol
    /// This replaces the mixed protocol parsing in lib.rs
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        let mut reader = BinaryProtocol::new_reader(bytes, "BatchRequest");
        
        // Read command count
        let command_count = reader.read_u32("command_count")? as usize;
        if command_count == 0 {
            return Err(anyhow!("Empty batch request"));
        }
        
        let mut commands = Vec::with_capacity(command_count);
        
        // Parse each command
        for i in 0..command_count {
            let cmd_type = reader.read_string(&format!("cmd_type_{}", i))?;
            let arg_count = reader.read_u32(&format!("arg_count_{}", i))? as usize;
            
            // Determine if this is a binary command by checking first argument type
            let mut is_binary_command = false;
            let mut temp_args = Vec::with_capacity(arg_count);
            
            for j in 0..arg_count {
                let arg_type = reader.read_u8(&format!("arg_type_{}_{}", i, j))?;
                let arg_len = reader.read_u32(&format!("arg_len_{}_{}", i, j))? as usize;
                
                if arg_len > 100_000_000 {  // 100MB limit
                    return Err(anyhow!("Argument too large: {} bytes", arg_len));
                }
                
                let arg_bytes = reader.read_exact_bytes(arg_len, &format!("arg_data_{}_{}", i, j))?;
                
                match arg_type {
                    0 => {
                        // Text argument
                        let text = String::from_utf8(arg_bytes)
                            .map_err(|e| anyhow!("Invalid UTF-8 in text argument: {}", e))?;
                        temp_args.push((false, text, Vec::new()));
                    }
                    1 => {
                        // Binary argument
                        is_binary_command = true;
                        temp_args.push((true, String::new(), arg_bytes));
                    }
                    _ => return Err(anyhow!("Unknown argument type: {}", arg_type)),
                }
            }
            
            // Create appropriate command type
            if is_binary_command {
                let mut binary_cmd = BinaryCommandRequest::new(&cmd_type);
                for (is_bin, text, bytes) in temp_args {
                    if is_bin {
                        binary_cmd = binary_cmd.arg_binary(bytes);
                    } else {
                        binary_cmd = binary_cmd.arg_string(text);
                    }
                }
                commands.push(BatchCommand::Binary(binary_cmd));
            } else {
                let mut string_cmd = CommandRequest::new(&cmd_type);
                for (_, text, _) in temp_args {
                    string_cmd = string_cmd.arg(text);
                }
                commands.push(BatchCommand::String(string_cmd));
            }
        }
        
        // Parse batch configuration
        let atomic = reader.read_boolean("atomic")?;
        let timeout_ms = reader.read_u32("timeout_ms")?;
        let raise_on_error = reader.read_boolean("raise_on_error")?;
        
        // Parse routing
        let has_route = reader.read_presence_flag("has_route")?;
        let route = if has_route {
            Some(parse_route_info_binary(&mut reader)?)
        } else {
            None
        };
        
        // Parse binary output flag (optional for backward compatibility)
        let binary_output = if reader.has_remaining() {
            reader.read_boolean("binary_output")?
        } else {
            false
        };
        
        // Validate complete parsing
        reader.validate_complete()?;
        
        Ok(BatchRequest {
            commands,
            atomic,
            timeout_ms,
            raise_on_error,
            route,
            binary_output,
        })
    }
    
    /// Convert batch request to glide-core routing format
    pub fn to_server_routing(&self) -> Option<RoutingInfo> {
        match &self.route {
            Some(RouteInfo::Random) => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
            Some(RouteInfo::AllPrimaries) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllMasters,
                None,
            ))),
            Some(RouteInfo::AllNodes) => Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllNodes,
                None,
            ))),
            Some(RouteInfo::SlotId { id, replica }) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    *id,
                    if *replica {
                        redis::cluster_routing::SlotAddr::ReplicaRequired
                    } else {
                        redis::cluster_routing::SlotAddr::Master
                    },
                )),
            )),
            Some(RouteInfo::PrimaryForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    redis::cluster_topology::get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::Master,
                )),
            )),
            Some(RouteInfo::ReplicaForKey(key)) => Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(redis::cluster_routing::Route::new(
                    redis::cluster_topology::get_slot(key.as_bytes()),
                    redis::cluster_routing::SlotAddr::ReplicaRequired,
                )),
            )),
            Some(RouteInfo::ByAddress { host, port }) => {
                Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                    host: host.clone(),
                    port: *port,
                }))
            }
            None => None,
        }
    }
}

impl BatchCommand {
    /// Convert to glide-core Cmd
    pub fn to_valkey_cmd(&self) -> Result<Cmd> {
        match self {
            BatchCommand::String(cmd) => cmd.to_valkey_cmd(),
            BatchCommand::Binary(cmd) => cmd.to_valkey_cmd(),
        }
    }
    
    /// Get command type string
    pub fn command_type(&self) -> &str {
        match self {
            BatchCommand::String(cmd) => &cmd.command_type,
            BatchCommand::Binary(cmd) => &cmd.command_type,
        }
    }
}

