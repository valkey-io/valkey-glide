// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

/*
#include <stdlib.h>
*/
import "C"
import "unsafe"

// convertViaGoStringN mirrors the production C.GoStringN path for
// tests/benchmarks only. Must live in a non-test file: cgo is not supported
// in *_test.go.
func convertViaGoStringN(p unsafe.Pointer, n int) string {
	return C.GoStringN((*C.char)(p), C.int(n))
}

func convertViaGoBytesString(p unsafe.Pointer, n int) string {
	return string(C.GoBytes(p, C.int(n)))
}

func withCBytes(b []byte, fn func(p unsafe.Pointer, n int)) {
	ptr := C.CBytes(b)
	defer C.free(ptr)
	fn(ptr, len(b))
}
