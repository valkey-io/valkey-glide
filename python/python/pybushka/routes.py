from enum import Enum
from typing import Optional

from pybushka.protobuf.redis_request_pb2 import RedisRequest, SimpleRoutes
from pybushka.protobuf.redis_request_pb2 import SlotTypes as ProtoSlotTypes


class SlotType(Enum):
    PRIMARY = 1
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
    elif type(route) == AllNodes:
        request.route.simple_routes = SimpleRoutes.AllNodes
    elif type(route) == AllPrimaries:
        request.route.simple_routes = SimpleRoutes.AllPrimaries
    elif type(route) == RandomNode:
        request.route.simple_routes = SimpleRoutes.Random
    elif type(route) == SlotKeyRoute:
        request.route.slot_key_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_key_route.slot_key = route.slot_key
    elif type(route) == SlotIdRoute:
        request.route.slot_id_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_id_route.slot_id = route.slot_id
    else:
        raise Exception(f"Received invalid route type: {type(route)}")
