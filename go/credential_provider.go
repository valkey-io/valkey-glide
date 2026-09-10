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
	func() {
		defer func() {
			if r := recover(); r != nil {
				// Panic in callback would crash the process; recover and signal failure.
				callErr = nil
				creds = config.AwsCredentials{}
			}
		}()
		creds, callErr = provider()
	}()

	if callErr != nil {
		return 0
	}

	// Copy access_key_id — required
	if len(creds.AccessKeyId) == 0 {
		return 0
	}
	accessKeyBytes := []byte(creds.AccessKeyId)
	writeLen := len(accessKeyBytes)
	if C.uintptr_t(writeLen) > accessKeyIDBufLen {
		writeLen = int(accessKeyIDBufLen)
	}
	C.memcpy(unsafe.Pointer(accessKeyIDBuf), unsafe.Pointer(&accessKeyBytes[0]), C.size_t(writeLen))
	*accessKeyIDLen = C.uintptr_t(writeLen)

	// Copy secret_access_key — required
	if len(creds.SecretAccessKey) == 0 {
		return 0
	}
	secretBytes := []byte(creds.SecretAccessKey)
	writeLen = len(secretBytes)
	if C.uintptr_t(writeLen) > secretAccessKeyBufLen {
		writeLen = int(secretAccessKeyBufLen)
	}
	C.memcpy(unsafe.Pointer(secretAccessKeyBuf), unsafe.Pointer(&secretBytes[0]), C.size_t(writeLen))
	*secretAccessKeyLen = C.uintptr_t(writeLen)

	// Copy session_token — optional
	if len(creds.SessionToken) > 0 {
		tokenBytes := []byte(creds.SessionToken)
		writeLen = len(tokenBytes)
		if C.uintptr_t(writeLen) > sessionTokenBufLen {
			writeLen = int(sessionTokenBufLen)
		}
		C.memcpy(unsafe.Pointer(sessionTokenBuf), unsafe.Pointer(&tokenBytes[0]), C.size_t(writeLen))
		*sessionTokenLen = C.uintptr_t(writeLen)
	} else {
		*sessionTokenLen = 0
	}

	// Set expires_at (0 = no expiry)
	*expiresAtMillis = C.int64_t(creds.ExpiresAtEpochMillis)

	return 1 // success
}
