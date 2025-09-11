//! Protobuf bridge for JNI - reuses existing glide-core protobuf parsing logic
//! This is a surgical change that just parses protobuf and delegates to existing functions

use anyhow::{anyhow, Result};
use protobuf::Message;
use redis::cluster_routing::RoutingInfo;

// Reuse existing protobuf types from glide-core (no wrapper types needed)
pub use glide_core::command_request::{
    CommandRequest,
    Command, 
    Routes,
    command_request,
};

/// Parse CommandRequest from protobuf bytes (using existing protobuf parsing)
pub fn parse_command_request(bytes: &[u8]) -> Result<CommandRequest> {
    CommandRequest::parse_from_bytes(bytes)
        .map_err(|e| anyhow!("Failed to parse CommandRequest protobuf: {}", e))
}

/// Since socket_listener functions are private, we'll need to access the core request_type logic
/// This reuses the same pattern as socket_listener but makes it accessible for JNI
pub fn create_redis_command(command: &Command) -> Result<redis::Cmd> {
    // Get the command using the same logic as socket_listener
    let request_type: glide_core::request_type::RequestType = command.request_type.into();
    let Some(mut cmd) = request_type.get_command() else {
        return Err(anyhow!("Received invalid request type: {:?}", command.request_type));
    };

    // Add arguments using the same logic as socket_listener  
    match &command.args {
        Some(glide_core::command_request::command::Args::ArgsArray(args_vec)) => {
            for arg in args_vec.args.iter() {
                cmd.arg(arg.as_ref());
            }
        }
        Some(glide_core::command_request::command::Args::ArgsVecPointer(pointer)) => {
            let res = unsafe { *Box::from_raw(*pointer as *mut Vec<bytes::Bytes>) };
            for arg in res {
                cmd.arg(arg.as_ref());
            }
        }
        None => {
            return Err(anyhow!("Failed to get request arguments, no arguments are set"));
        }
        // Handle any other cases that might be added to the enum
        Some(_) => {
            return Err(anyhow!("Unsupported argument type"));
        }
    };

    if cmd.args_iter().next().is_none() {
        return Err(anyhow!("Received command without a command name or arguments"));
    }

    Ok(cmd)
}

/// Get routing information using existing FFI get_route function (surgical reuse)
pub fn create_routing_info(route: Routes, cmd: Option<&redis::Cmd>) -> Result<Option<RoutingInfo>> {
    // Use the existing public FFI get_route function directly
    glide_ffi::get_route(route, cmd).map_err(|e| anyhow!("Routing error: {}", e))
}
