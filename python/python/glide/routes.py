# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import Optional

from glide.protobuf.redis_request_pb2 import RedisRequest, SimpleRoutes
from glide.protobuf.redis_request_pb2 import SlotTypes as ProtoSlotTypes


class SlotType(Enum):
    PRIMARY = 1
    # `REPLICA` overrides the `read_from_replica` configuration. If it's used the request
    # will be routed to a replica, even if the strategy is `ALWAYS_FROM_MASTER`.
    REPLICA = 2


class Route:
    def __init__(self) -> None:
        pass


# cluster routes


class AllNodes(Route):
    pass


class AllPrimaries(Route):
    pass


class RandomNode(Route):
    pass


class SlotKeyRoute(Route):
    def __init__(self, slot_type: SlotType, slot_key: str) -> None:
        super().__init__()
        self.slot_type = slot_type
        self.slot_key = slot_key


class SlotIdRoute(Route):
    def __init__(self, slot_type: SlotType, slot_id: int) -> None:
        super().__init__()
        self.slot_type = slot_type
        self.slot_id = slot_id


def to_protobuf_slot_type(slot_type: SlotType) -> ProtoSlotTypes.ValueType:
    return (
        ProtoSlotTypes.Primary
        if slot_type == SlotType.PRIMARY
        else ProtoSlotTypes.Replica
    )


def set_protobuf_route(request: RedisRequest, route: Optional[Route]) -> None:
    if route is None:
        return
    elif isinstance(route, AllNodes):
        request.route.simple_routes = SimpleRoutes.AllNodes
    elif isinstance(route, AllPrimaries):
        request.route.simple_routes = SimpleRoutes.AllPrimaries
    elif isinstance(route, RandomNode):
        request.route.simple_routes = SimpleRoutes.Random
    elif isinstance(route, SlotKeyRoute):
        request.route.slot_key_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_key_route.slot_key = route.slot_key
    elif isinstance(route, SlotIdRoute):
        request.route.slot_id_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_id_route.slot_id = route.slot_id
    else:
        raise Exception(f"Received invalid route type: {type(route)}")
