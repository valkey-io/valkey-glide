// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//go:build go1.21

package api

import (
	"runtime"
	"unsafe"
)

// pinner is a wrapper of a runtime.Pinner making the interface
// compatible to the cgo.Handle in the Go < 1.21.
// Note that this make a pinner can only hold one unsafe.Pointer.
type pinner struct {
	r runtime.Pinner
}

func (p *pinner) Pin(v unsafe.Pointer) unsafe.Pointer {
	p.r.Pin(v)
	return v
}

func (p *pinner) Unpin() {
	p.r.Unpin()
}

func getPinnedPtr(v unsafe.Pointer) unsafe.Pointer {
	return v
}
