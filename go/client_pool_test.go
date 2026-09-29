// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"strings"
	"testing"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// A pool cannot forward a per-client address resolver (glide_pool_create takes
// only the serialized ConnectionRequest bytes), so NewClientPool must reject a
// config that carries one rather than silently dropping it. The guard runs
// before any native call, so these tests need no server.

func TestNewClientPool_rejectsCustomAddressResolver(t *testing.T) {
	cfg := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: "localhost", Port: 6379}).
		WithAddressResolver(func(host string, port int) (string, int) { return host, port })

	_, err := NewClientPool(cfg, PoolConfig{MaxSize: 1})
	if err == nil {
		t.Fatal("expected NewClientPool to reject a custom address resolver, got nil error")
	}
	if !strings.Contains(err.Error(), "address resolver") {
		t.Fatalf("expected an address-resolver rejection message, got: %v", err)
	}
}

func TestNewClusterClientPool_rejectsCustomAddressResolver(t *testing.T) {
	cfg := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{Host: "localhost", Port: 6379}).
		WithAddressResolver(func(host string, port int) (string, int) { return host, port })

	_, err := NewClusterClientPool(cfg, PoolConfig{MaxSize: 1})
	if err == nil {
		t.Fatal("expected NewClusterClientPool to reject a custom address resolver, got nil error")
	}
	if !strings.Contains(err.Error(), "address resolver") {
		t.Fatalf("expected an address-resolver rejection message, got: %v", err)
	}
}
