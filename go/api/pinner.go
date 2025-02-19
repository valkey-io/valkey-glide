//go:build go1.21

package api

import (
	"runtime"
	"unsafe"
)

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
