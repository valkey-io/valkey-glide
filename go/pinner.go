// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//go:build go1.21

package glide

import (
	"runtime"
	"unsafe"
)

// pinner is a wrapper of a runtime.Pinner making the interface
// compatible to the cgo.Handle in Go versions before 1.21.
type pinner struct {
	r    runtime.Pinner
	pins int
}

func (p *pinner) Pin(v unsafe.Pointer) unsafe.Pointer {
	p.r.Pin(v)
	p.pins++
	return v
}

func (p *pinner) Unpin() {
	p.r.Unpin()
	p.pins = 0
}

func (p *pinner) pinCount() int {
	return p.pins
}

func getPinnedPtr(v unsafe.Pointer) unsafe.Pointer {
	return v
}
