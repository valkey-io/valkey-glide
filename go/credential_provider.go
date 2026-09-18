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

var credentialProviderRegistry sync.Map // map[uintptr]config.GlideCredentialProvider

func registerCredentialProvider(clientID uintptr, provider config.GlideCredentialProvider) {
	credentialProviderRegistry.Store(clientID, provider)
}

func unregisterCredentialProvider(clientID uintptr) {
	credentialProviderRegistry.Delete(clientID)
}

//export credentialProviderCallback
func credentialProviderCallback(
	clientID C.uintptr_t,
	accessKeyIDBuf *C.uint8_t,
	accessKeyIDBufLen C.uintptr_t,
	accessKeyIDLen *C.uintptr_t,
	secretAccessKeyBuf *C.uint8_t,
	secretAccessKeyBufLen C.uintptr_t,
	secretAccessKeyLen *C.uintptr_t,
	sessionTokenBuf *C.uint8_t,
	sessionTokenBufLen C.uintptr_t,
	sessionTokenLen *C.uintptr_t,
	expiresAtMillis *C.int64_t,
) C.uint8_t {
	val, ok := credentialProviderRegistry.Load(uintptr(clientID))
	if !ok {
		return 0
	}
	provider, ok := val.(config.GlideCredentialProvider)
	if !ok || provider == nil {
		return 0
	}

	var creds config.AwsCredentials
	var callErr error
	panicOccurred := false
	func() {
		defer func() {
			if r := recover(); r != nil {
				// A panic in the callback would propagate through CGo and crash the
				// process; catch it here and return failure to the Rust caller.
				panicOccurred = true
				creds = config.AwsCredentials{}
				callErr = nil
			}
		}()
		creds, callErr = provider()
	}()

	if panicOccurred || callErr != nil {
		return 0
	}

	// Validate all required fields and check buffer sizes before writing anything.
	// This ensures we never partially populate output buffers on a failure return.
	if len(creds.AccessKeyID) == 0 {
		return 0
	}
	if len(creds.SecretAccessKey) == 0 {
		return 0
	}
	accessKeyBytes := []byte(creds.AccessKeyID)
	if C.uintptr_t(len(accessKeyBytes)) > accessKeyIDBufLen {
		// Credential exceeds buffer; return failure without writing.
		return 0
	}
	secretBytes := []byte(creds.SecretAccessKey)
	if C.uintptr_t(len(secretBytes)) > secretAccessKeyBufLen {
		return 0
	}
	var tokenBytes []byte
	if creds.SessionToken != "" {
		tokenBytes = []byte(creds.SessionToken)
		if C.uintptr_t(len(tokenBytes)) > sessionTokenBufLen {
			return 0
		}
	}

	// All validations passed — now write to output buffers.
	C.memcpy(unsafe.Pointer(accessKeyIDBuf), unsafe.Pointer(&accessKeyBytes[0]), C.size_t(len(accessKeyBytes)))
	*accessKeyIDLen = C.uintptr_t(len(accessKeyBytes))

	C.memcpy(unsafe.Pointer(secretAccessKeyBuf), unsafe.Pointer(&secretBytes[0]), C.size_t(len(secretBytes)))
	*secretAccessKeyLen = C.uintptr_t(len(secretBytes))

	if len(tokenBytes) > 0 {
		C.memcpy(unsafe.Pointer(sessionTokenBuf), unsafe.Pointer(&tokenBytes[0]), C.size_t(len(tokenBytes)))
		*sessionTokenLen = C.uintptr_t(len(tokenBytes))
	} else {
		*sessionTokenLen = 0
	}

	// Set expires_at (0 = no expiry)
	*expiresAtMillis = C.int64_t(creds.ExpiresAtEpochMillis)

	return 1 // success
}
