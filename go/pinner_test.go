// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"testing"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/internal"
)

func TestPinner(t *testing.T) {
	v := make(chan payload)

	p := pinner{}
	n := p.Pin(unsafe.Pointer(&v))
	defer p.Unpin()

	if *(*chan payload)(getPinnedPtr(n)) != v {
		t.Fail()
	}
}

// TestCreateBatchInfoPinsWithCallerPinner verifies that the pinner released by executeBatch owns all batch pins.
func TestCreateBatchInfoPinsWithCallerPinner(t *testing.T) {
	p := &pinner{}

	batchInfo := createBatchInfo(p, internal.Batch{
		Commands: []internal.Cmd{{}},
	})
	if batchInfo.cmds == nil {
		t.Fatal("expected command pointers to be pinned")
	}
	if got, want := p.pinCount(), 2; got != want {
		t.Fatalf("expected caller pinner to own the command and command-array pins, got %d", got)
	}

	p.Unpin()
	if got := p.pinCount(); got != 0 {
		t.Fatalf("expected Unpin to release all pins, got %d remaining", got)
	}
}
