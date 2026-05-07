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

// globalResolver holds the address resolver that is currently active.
// Since the FFI callback signature does not carry a context pointer, only one
// resolver can be active at a time. When multiple clients are created with
// different resolvers, the last one set wins for all subsequent resolution calls.
//
// In practice this is acceptable because:
// 1. Most applications use a single resolver for all clients.
// 2. The resolver is called during client creation and topology refresh,
//    both of which happen on the client's own runtime.
var (
	globalResolver   config.AddressResolver
	globalResolverMu sync.RWMutex
)

// setGlobalResolver sets the global address resolver.
func setGlobalResolver(resolver config.AddressResolver) {
	globalResolverMu.Lock()
	defer globalResolverMu.Unlock()
	globalResolver = resolver
}

// getGlobalResolver returns the current global address resolver.
func getGlobalResolver() config.AddressResolver {
	globalResolverMu.RLock()
	defer globalResolverMu.RUnlock()
	return globalResolver
}

//export addressResolverCallback
func addressResolverCallback(
	host *C.uint8_t,
	hostLen C.size_t,
	port C.uint16_t,
	resolvedHostBuf *C.uint8_t,
	resolvedHostBufLen C.size_t,
	resolvedHostLen *C.size_t,
) C.uint16_t {
	resolver := getGlobalResolver()
	if resolver == nil {
		// No resolver configured, return 0 to signal fallback to original address
		return 0
	}

	// Convert C host to Go string
	goHost := C.GoStringN((*C.char)(unsafe.Pointer(host)), C.int(hostLen))
	goPort := int(port)

	// Call the user's resolver (recover from panics)
	var resolvedHost string
	var resolvedPort int
	func() {
		defer func() {
			if r := recover(); r != nil {
				// On panic, signal fallback by leaving resolvedPort as 0
				resolvedHost = ""
				resolvedPort = 0
			}
		}()
		resolvedHost, resolvedPort = resolver(goHost, goPort)
	}()

	// If resolver returned port 0 or empty host, signal fallback
	if resolvedPort == 0 || resolvedHost == "" {
		return 0
	}

	// Write resolved host into the buffer
	hostBytes := []byte(resolvedHost)
	if len(hostBytes) > int(resolvedHostBufLen) {
		// Host too long for buffer, signal fallback
		return 0
	}

	// Copy resolved host into the C buffer
	C.memcpy(
		unsafe.Pointer(resolvedHostBuf),
		unsafe.Pointer(&hostBytes[0]),
		C.size_t(len(hostBytes)),
	)
	*resolvedHostLen = C.size_t(len(hostBytes))

	return C.uint16_t(resolvedPort)
}
