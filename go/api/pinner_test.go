// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"testing"
	"unsafe"
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
