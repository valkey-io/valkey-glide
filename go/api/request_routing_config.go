package api

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/valkey-io/valkey-glide/go/glide/protobuf"
)

// Request routing basic interface. Please use one of the following:
// - [api.SimpleNodeRoute]
// - [api.SlotIdRoute]
// - [api.SlotKeyRoute]
// - [api.ByAddressRoute]
type route interface {
	toRoutesProtobuf() (*protobuf.Routes, error)
}

type SimpleNodeRoute int

const (
	// Route request to all nodes.
	// Warning: Don't use it with write commands, they could be routed to a replica (RO) node and fail.
	SimpleNodeRouteAllNodes SimpleNodeRoute = iota
	// Route request to all primary nodes.
	SimpleNodeRouteAllPrimaries
	// Route request to a random node.
	// Warning: Don't use it with write commands, because they could be randomly routed to a replica (RO) node and fail.
	SimpleNodeRouteRandom
)

func (simpleNodeRoute SimpleNodeRoute) toRoutesProtobuf() (*protobuf.Routes, error) {
	simpleRouteProto, err := mapSimpleNodeRoute(simpleNodeRoute)
	if err != nil {
		return nil, err
	}

	request := &protobuf.Routes{
		Value: &protobuf.Routes_SimpleRoutes{
			SimpleRoutes: simpleRouteProto,
		},
	}
	return request, nil
}

func mapSimpleNodeRoute(simpleNodeRoute SimpleNodeRoute) (protobuf.SimpleRoutes, error) {
	switch simpleNodeRoute {
	case SimpleNodeRouteAllNodes:
		return protobuf.SimpleRoutes_AllNodes, nil
	case SimpleNodeRouteAllPrimaries:
		return protobuf.SimpleRoutes_AllPrimaries, nil
	case SimpleNodeRouteRandom:
		return protobuf.SimpleRoutes_Random, nil
	default:
		return protobuf.SimpleRoutes_Random, &RequestError{"Invalid simple node route"}
	}
}

// Defines type of the node being addressed.
type SlotType int

const (
	// Address a primary node.
	SlotTypePrimary SlotType = iota
	// Address a replica node.
	SlotTypeReplica
)

func mapSlotType(slotType SlotType) (protobuf.SlotTypes, error) {
	switch slotType {
	case SlotTypePrimary:
		return protobuf.SlotTypes_Primary, nil
	case SlotTypeReplica:
		return protobuf.SlotTypes_Replica, nil
	default:
		return protobuf.SlotTypes_Primary, &RequestError{"Invalid slot type"}
	}
}

// Request routing configuration overrides the [api.ReadFrom] connection configuration.
// If SlotTypeReplica is used, the request will be routed to a replica, even if the strategy is ReadFrom [api.PreferReplica].
type SlotIdRoute struct {
	// Defines type of the node being addressed.
	slotType SlotType
	// Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range.
	// Unless the slot is known, it's better to route using [api.SlotTypePrimary].
	slotID int32
}

func NewSlotIdRoute(slotType SlotType, slotId int32) *SlotIdRoute {
	return &SlotIdRoute{slotType: slotType, slotID: slotId}
}

func (slotIdRoute *SlotIdRoute) toRoutesProtobuf() (*protobuf.Routes, error) {
	slotType, err := mapSlotType(slotIdRoute.slotType)
	if err != nil {
		return nil, err
	}

	request := &protobuf.Routes{
		Value: &protobuf.Routes_SlotIdRoute{
			SlotIdRoute: &protobuf.SlotIdRoute{
				SlotType: slotType,
				SlotId:   slotIdRoute.slotID,
			},
		},
	}
	return request, nil
}

// Request routing configuration overrides the [api.ReadFrom] connection configuration.
// If SlotTypeReplica is used, the request will be routed to a replica, even if the strategy is ReadFrom [api.PreferReplica].
type SlotKeyRoute struct {
	// Defines type of the node being addressed.
	slotType SlotType
	// The request will be sent to nodes managing this key.
	slotKey string
}

func NewSlotKeyRoute(slotType SlotType, slotKey string) *SlotKeyRoute {
	return &SlotKeyRoute{slotType: slotType, slotKey: slotKey}
}

func (slotKeyRoute *SlotKeyRoute) toRoutesProtobuf() (*protobuf.Routes, error) {
	slotType, err := mapSlotType(slotKeyRoute.slotType)
	if err != nil {
		return nil, err
	}

	request := &protobuf.Routes{
		Value: &protobuf.Routes_SlotKeyRoute{
			SlotKeyRoute: &protobuf.SlotKeyRoute{
				SlotType: slotType,
				SlotKey:  slotKeyRoute.slotKey,
			},
		},
	}
	return request, nil
}

// Routes a request to a node by its address.
type ByAddressRoute struct {
	// The endpoint of the node. If port is not provided, host should be in the "address:port" format, where address is the
	// preferred endpoint as shown in the output of the CLUSTER SLOTS command.
	host string
	// The port to access the node. If port is not provided, host is assumed to be in the format "address:port".
	port int32
}

// Create a route using hostname/address and port.
func NewByAddressRoute(host string, port int32) *ByAddressRoute {
	return &ByAddressRoute{host: host, port: port}
}

// Create a route using address string formatted as "address:port".
func NewByAddressRouteWithHost(host string) (*ByAddressRoute, error) {
	split := strings.Split(host, ":")
	if len(split) < 2 || len(split) > 2 {
		return nil, &RequestError{
			fmt.Sprintf(
				"no port provided, or host is not in the expected format 'hostname:port'. Received: %s", host,
			),
		}
	}

	port, err := strconv.ParseInt(split[1], 10, 32)
	if err != nil {
		return nil, &RequestError{
			fmt.Sprintf(
				"port must be a valid integer. Received: %s", split[1],
			),
		}
	}

	return &ByAddressRoute{host: split[0], port: int32(port)}, nil
}

func (byAddressRoute *ByAddressRoute) toRoutesProtobuf() (*protobuf.Routes, error) {
	request := &protobuf.Routes{
		Value: &protobuf.Routes_ByAddressRoute{
			ByAddressRoute: &protobuf.ByAddressRoute{
				Host: byAddressRoute.host,
				Port: byAddressRoute.port,
			},
		},
	}
	return request, nil
}
