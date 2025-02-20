// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/valkey-io/valkey-glide/go/api/errors"
)

// Request routing basic interface. Please use one of the following:
// - [config.SimpleNodeRoute]
// - [config.SlotIdRoute]
// - [config.SlotKeyRoute]
// - [config.ByAddressRoute]
type Route interface {
	IsMultiNode() bool
}

type notMultiNode struct{}

func (*notMultiNode) IsMultiNode() bool { return false }

type SimpleNodeRoute int

const (
	// Route request to all nodes.
	// Warning: Don't use it with write commands, they could be routed to a replica (RO) node and fail.
	AllNodes SimpleNodeRoute = iota
	// Route request to all primary nodes.
	AllPrimaries
	// Route request to a random node.
	// Warning: Don't use it with write commands, because they could be randomly routed to a replica (RO) node and fail.
	RandomRoute
)

func (route SimpleNodeRoute) IsMultiNode() bool {
	return route != RandomRoute
}

func (snr SimpleNodeRoute) ToPtr() *Route {
	a := Route(snr)
	return &a
}

// Defines type of the node being addressed.
type SlotType int

const (
	// Address a primary node.
	SlotTypePrimary SlotType = iota
	// Address a replica node.
	SlotTypeReplica
)

// Request routing configuration overrides the [api.ReadFrom] connection configuration.
// If SlotTypeReplica is used, the request will be routed to a replica, even if the strategy is ReadFrom [api.PreferReplica].
type SlotIdRoute struct {
	SlotType SlotType
	SlotID   int32
	notMultiNode
}

// - slotType: Defines type of the node being addressed.
// - slotId: Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range. Unless the slot is
// known, it's better to route using [api.SlotTypePrimary].
func NewSlotIdRoute(slotType SlotType, slotId int32) *SlotIdRoute {
	return &SlotIdRoute{SlotType: slotType, SlotID: slotId}
}

// Request routing configuration overrides the [api.ReadFrom] connection configuration.
// If SlotTypeReplica is used, the request will be routed to a replica, even if the strategy is ReadFrom [api.PreferReplica].
type SlotKeyRoute struct {
	SlotType SlotType
	SlotKey  string
	notMultiNode
}

// - slotType: Defines type of the node being addressed.
// - slotKey: The request will be sent to nodes managing this key.
func NewSlotKeyRoute(slotType SlotType, slotKey string) *SlotKeyRoute {
	return &SlotKeyRoute{SlotType: slotType, SlotKey: slotKey}
}

// Routes a request to a node by its address.
type ByAddressRoute struct {
	Host string
	Port int32
	notMultiNode
}

// Create a route using hostname/address and port.
// - host: The endpoint of the node. If port is not provided, host should be in the "address:port" format, where address is the
// preferred endpoint as shown in the output of the CLUSTER SLOTS command.
// - port: The port to access the node. If port is not provided, host is assumed to be in the format "address:port".
func NewByAddressRoute(host string, port int32) *ByAddressRoute {
	return &ByAddressRoute{Host: host, Port: port}
}

// Create a route using address string formatted as "address:port".
func NewByAddressRouteWithHost(host string) (*ByAddressRoute, error) {
	split := strings.Split(host, ":")
	if len(split) != 2 {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf(
				"no port provided, or host is not in the expected format 'hostname:port'. Received: %s", host,
			),
		}
	}

	port, err := strconv.ParseInt(split[1], 10, 32)
	if err != nil {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf(
				"port must be a valid integer. Received: %s", split[1],
			),
		}
	}

	return &ByAddressRoute{Host: split[0], Port: int32(port)}, nil
}
