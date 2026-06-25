//! Route resolution - converts route parameters to redis RoutingInfo

use redis::cluster_routing::RoutingInfo;
use redis::cluster_routing::{MultipleNodeRoutingInfo, Route, SingleNodeRoutingInfo, SlotAddr};
use redis::cluster_routing::{ResponsePolicy, Routable};
use redis::{Cmd, RedisError, RedisResult};

pub use glide_core::command_request::Routes;
use glide_core::command_request::SimpleRoutes;
use glide_core::command_request::SlotTypes;

fn get_slot_addr(slot_type: &protobuf::EnumOrUnknown<SlotTypes>) -> Result<SlotAddr, RedisError> {
    slot_type
        .enum_value()
        .map(|slot_type| match slot_type {
            SlotTypes::Primary => SlotAddr::Master,
            SlotTypes::Replica => SlotAddr::ReplicaRequired,
        })
        .map_err(|id| {
            RedisError::from((
                redis::ErrorKind::ClientError,
                "Received unexpected slot id type",
                format!("{id}"),
            ))
        })
}

/// Converts a protobuf Routes message into the corresponding RoutingInfo.
///
/// This function parses the given Routes message and creates the appropriate
/// RoutingInfo structure. For multi-node routes, it uses the optional command
/// to determine the response policy.
///
/// # Parameters
///
/// * `route`: The Routes message to convert.
/// * `cmd`: Optional command used to determine the response policy for multi-node routes.
///
/// # Returns
///
/// * `Ok(Some(RoutingInfo))` if the route was successfully converted.
/// * `Ok(None)` if no route value was specified.
/// * `Err(RedisError)` if the route is invalid or cannot be converted.
pub(crate) fn get_route(route: Routes, cmd: Option<&Cmd>) -> RedisResult<Option<RoutingInfo>> {
    use glide_core::command_request::routes::Value;
    let route = match route.value {
        Some(route) => route,
        None => return Ok(None),
    };
    let get_response_policy = |cmd: Option<&Cmd>| {
        cmd.and_then(|cmd| {
            cmd.command()
                .and_then(|cmd| ResponsePolicy::for_command(&cmd))
        })
    };
    match route {
        Value::SimpleRoutes(simple_route) => {
            let simple_route = match simple_route.enum_value() {
                Ok(simple_route) => simple_route,
                Err(value) => {
                    return Err(RedisError::from((
                        redis::ErrorKind::ClientError,
                        "simple_route was not a valid enum variant",
                        format!("Value: {value}"),
                    )));
                }
            };
            match simple_route {
                SimpleRoutes::AllNodes => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllNodes,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::AllPrimaries => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllMasters,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::Random => {
                    Ok(Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)))
                }
            }
        }
        Value::SlotKeyRoute(slot_key_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(slot_key_route.slot_key.as_bytes()),
                get_slot_addr(&slot_key_route.slot_type)?,
            )),
        ))),
        Value::SlotIdRoute(slot_id_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                slot_id_route.slot_id as u16,
                get_slot_addr(&slot_id_route.slot_type)?,
            )),
        ))),
        Value::ByAddressRoute(by_address_route) => match u16::try_from(by_address_route.port) {
            Ok(port) => Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::ByAddress {
                    host: by_address_route.host.to_string(),
                    port,
                },
            ))),
            Err(_) => Err(RedisError::from((
                redis::ErrorKind::ClientError,
                "by_address_route port could not be converted to u16.",
                format!("Value: {}", by_address_route.port),
            ))),
        },
        _ => Err(RedisError::from((
            redis::ErrorKind::ClientError,
            "Unknown route type.",
            format!("Value: {route:?}"),
        ))),
    }
}
