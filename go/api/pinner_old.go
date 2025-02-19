//go:build !go1.21

package api

import (
	"runtime/cgo"
	"unsafe"
)

// pinner is a wrapper of a cgo.Handle making the interface
// compatible to the runtime.Pinner in the Go >= 1.21.
// Note that a pinner can only hold one unsafe.Pointer.
type pinner struct {
	h cgo.Handle
}

func (p *pinner) Pin(v unsafe.Pointer) unsafe.Pointer {
	p.h = cgo.NewHandle(v)
	return unsafe.Pointer(&p.h)
}

func (p *pinner) Unpin() {
	p.h.Delete()
}

func getPinnedPtr(v unsafe.Pointer) unsafe.Pointer {
	return (*(*cgo.Handle)(v)).Value().(unsafe.Pointer)
}
