// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
// #include <string.h>
import "C"

import (
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

var resolverRegistry sync.Map // map[uintptr]config.AddressResolver

func registerResolver(clientID uintptr, resolver config.AddressResolver) {
	resolverRegistry.Store(clientID, resolver)
}

func unregisterResolver(clientID uintptr) {
	resolverRegistry.Delete(clientID)
}

//export addressResolverCallback
func addressResolverCallback(
	clientID C.uintptr_t,
	host *C.uint8_t,
	hostLen C.uintptr_t,
	port C.uint16_t,
	resolvedHostBuf *C.uint8_t,
	resolvedHostBufLen C.uintptr_t,
	resolvedHostLen *C.uintptr_t,
) C.uint16_t {
	val, ok := resolverRegistry.Load(uintptr(clientID))
	if !ok {
		return 0
	}
	resolver, ok := val.(config.AddressResolver)
	if !ok || resolver == nil {
		return 0
	}

	goHost := C.GoStringN((*C.char)(unsafe.Pointer(host)), C.int(hostLen))
	goPort := int(port)

	var resolvedHost string
	var resolvedPort int
	func() {
		defer func() {
			if r := recover(); r != nil {
				resolvedHost = ""
				resolvedPort = 0
			}
		}()
		resolvedHost, resolvedPort = resolver(goHost, goPort)
	}()

	if resolvedPort < 1 || resolvedPort > 65535 || resolvedHost == "" {
		return 0
	}

	hostBytes := []byte(resolvedHost)
	if len(hostBytes) > int(resolvedHostBufLen) {
		return 0
	}

	C.memcpy(
		unsafe.Pointer(resolvedHostBuf),
		unsafe.Pointer(&hostBytes[0]),
		C.size_t(len(hostBytes)),
	)
	*resolvedHostLen = C.uintptr_t(len(hostBytes))

	return C.uint16_t(resolvedPort)
}
