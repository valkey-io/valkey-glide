//go:build !go1.21

package api

import (
	"runtime/cgo"
	"unsafe"
)

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
