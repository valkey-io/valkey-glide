// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/protobuf"
)

func TestSimpleNodeRoute(t *testing.T) {
	route := config.AllNodes
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SimpleRoutes{
			SimpleRoutes: protobuf.SimpleRoutes_AllNodes,
		},
	}

	result, err := routeToProtobuf(route)

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestSlotIdRoute(t *testing.T) {
	route := config.NewSlotIdRoute(config.SlotTypePrimary, int32(100))
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SlotIdRoute{
			SlotIdRoute: &protobuf.SlotIdRoute{
				SlotType: protobuf.SlotTypes_Primary,
				SlotId:   100,
			},
		},
	}

	result, err := routeToProtobuf(route)

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestSlotKeyRoute(t *testing.T) {
	route := config.NewSlotKeyRoute(config.SlotTypePrimary, "Slot1")
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SlotKeyRoute{
			SlotKeyRoute: &protobuf.SlotKeyRoute{
				SlotType: protobuf.SlotTypes_Primary,
				SlotKey:  "Slot1",
			},
		},
	}

	result, err := routeToProtobuf(route)

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRoute(t *testing.T) {
	route := config.NewByAddressRoute(DefaultHost, DefaultPort)
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_ByAddressRoute{
			ByAddressRoute: &protobuf.ByAddressRoute{Host: DefaultHost, Port: DefaultPort},
		},
	}

	result, err := routeToProtobuf(route)

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRouteWithHost(t *testing.T) {
	route, _ := config.NewByAddressRouteWithHost(fmt.Sprintf("%s:%d", DefaultHost, DefaultPort))
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_ByAddressRoute{
			ByAddressRoute: &protobuf.ByAddressRoute{Host: DefaultHost, Port: DefaultPort},
		},
	}

	result, err := routeToProtobuf(route)

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRoute_MultiplePorts(t *testing.T) {
	_, err := config.NewByAddressRouteWithHost(fmt.Sprintf("%s:%d:%d", DefaultHost, DefaultPort, DefaultPort+1))
	assert.NotNil(t, err)
}

func TestByAddressRoute_InvalidHost(t *testing.T) {
	_, err := config.NewByAddressRouteWithHost(DefaultHost)
	assert.NotNil(t, err)
}
