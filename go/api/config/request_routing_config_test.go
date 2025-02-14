// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/protobuf"
)

const (
	defaultPort int32  = 6379
	defaultHost string = "localhost"
)

func TestSimpleNodeRoute(t *testing.T) {
	routeConfig := AllNodes
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SimpleRoutes{
			SimpleRoutes: protobuf.SimpleRoutes_AllNodes,
		},
	}

	result, err := routeConfig.toRoutesProtobuf()

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestSlotIdRoute(t *testing.T) {
	routeConfig := NewSlotIdRoute(SlotTypePrimary, int32(100))
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SlotIdRoute{
			SlotIdRoute: &protobuf.SlotIdRoute{
				SlotType: protobuf.SlotTypes_Primary,
				SlotId:   100,
			},
		},
	}

	result, err := routeConfig.toRoutesProtobuf()

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestSlotKeyRoute(t *testing.T) {
	routeConfig := NewSlotKeyRoute(SlotTypePrimary, "Slot1")
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_SlotKeyRoute{
			SlotKeyRoute: &protobuf.SlotKeyRoute{
				SlotType: protobuf.SlotTypes_Primary,
				SlotKey:  "Slot1",
			},
		},
	}

	result, err := routeConfig.toRoutesProtobuf()

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRoute(t *testing.T) {
	routeConfig := NewByAddressRoute(defaultHost, defaultPort)
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_ByAddressRoute{
			ByAddressRoute: &protobuf.ByAddressRoute{Host: defaultHost, Port: defaultPort},
		},
	}

	result, err := routeConfig.toRoutesProtobuf()

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRouteWithHost(t *testing.T) {
	routeConfig, _ := NewByAddressRouteWithHost(fmt.Sprintf("%s:%d", defaultHost, defaultPort))
	expected := &protobuf.Routes{
		Value: &protobuf.Routes_ByAddressRoute{
			ByAddressRoute: &protobuf.ByAddressRoute{Host: defaultHost, Port: defaultPort},
		},
	}

	result, err := routeConfig.toRoutesProtobuf()

	assert.Equal(t, expected, result)
	assert.Nil(t, err)
}

func TestByAddressRoute_MultiplePorts(t *testing.T) {
	_, err := NewByAddressRouteWithHost(fmt.Sprintf("%s:%d:%d", defaultHost, defaultPort, defaultPort+1))
	assert.NotNil(t, err)
}

func TestByAddressRoute_InvalidHost(t *testing.T) {
	_, err := NewByAddressRouteWithHost(defaultHost)
	assert.NotNil(t, err)
}
