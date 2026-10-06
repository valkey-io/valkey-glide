// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"strings"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

const (
	credentialCallbackFailure        = uint8(0)
	credentialCallbackSuccess        = uint8(1)
	credentialCallbackBufferTooSmall = uint8(2)
	maxCredentialBytes               = 1024 * 1024
)

type encodedCredentials struct {
	accessKeyID     []byte
	secretAccessKey []byte
	sessionToken    []byte
	expiresAtMillis int64
}

type credentialProviderEntry struct {
	mu       sync.Mutex
	provider config.GlideCredentialProvider
	pending  *encodedCredentials
}

var credentialProviderRegistry sync.Map // map[uintptr]*credentialProviderEntry

func registerCredentialProvider(clientID uintptr, provider config.GlideCredentialProvider) {
	credentialProviderRegistry.Store(clientID, &credentialProviderEntry{provider: provider})
}

func unregisterCredentialProvider(clientID uintptr) {
	value, ok := credentialProviderRegistry.LoadAndDelete(clientID)
	if !ok {
		return
	}
	entry, ok := value.(*credentialProviderEntry)
	if !ok {
		return
	}
	entry.mu.Lock()
	entry.provider = nil
	entry.pending = nil
	entry.mu.Unlock()
}

func invokeCredentialProvider(provider config.GlideCredentialProvider) (
	credentials config.AwsCredentials,
	err error,
) {
	defer func() {
		if recover() != nil {
			credentials = config.AwsCredentials{}
			err = errCredentialProviderPanic
		}
	}()
	return provider()
}

// A private sentinel avoids allocating or exposing panic details across the FFI boundary.
var errCredentialProviderPanic = &credentialProviderPanicError{}

type credentialProviderPanicError struct{}

func (*credentialProviderPanicError) Error() string { return "credential provider panicked" }

func encodeCredentialProviderResult(provider config.GlideCredentialProvider) (*encodedCredentials, bool) {
	credentials, err := invokeCredentialProvider(provider)
	if err != nil || strings.TrimSpace(credentials.AccessKeyID) == "" ||
		strings.TrimSpace(credentials.SecretAccessKey) == "" {
		return nil, false
	}

	fieldLengths := [3]int{
		len(credentials.AccessKeyID),
		len(credentials.SecretAccessKey),
		len(credentials.SessionToken),
	}
	total := 0
	for _, length := range fieldLengths {
		if length > maxCredentialBytes || length > maxCredentialBytes-total {
			return nil, false
		}
		total += length
	}

	return &encodedCredentials{
		accessKeyID:     []byte(credentials.AccessKeyID),
		secretAccessKey: []byte(credentials.SecretAccessKey),
		sessionToken:    []byte(credentials.SessionToken),
		expiresAtMillis: credentials.ExpiresAtEpochMillis,
	}, true
}

func credentialBuffersFit(
	credentials *encodedCredentials,
	accessKeyIDBuf []byte,
	secretAccessKeyBuf []byte,
	sessionTokenBuf []byte,
) bool {
	return len(credentials.accessKeyID) <= len(accessKeyIDBuf) &&
		len(credentials.secretAccessKey) <= len(secretAccessKeyBuf) &&
		len(credentials.sessionToken) <= len(sessionTokenBuf)
}

func setCredentialLengths(
	credentials *encodedCredentials,
	accessKeyIDLen *uintptr,
	secretAccessKeyLen *uintptr,
	sessionTokenLen *uintptr,
) {
	*accessKeyIDLen = uintptr(len(credentials.accessKeyID))
	*secretAccessKeyLen = uintptr(len(credentials.secretAccessKey))
	*sessionTokenLen = uintptr(len(credentials.sessionToken))
}

func handleCredentialProviderCallback(
	clientID uintptr,
	accessKeyIDBuf []byte,
	accessKeyIDLen *uintptr,
	secretAccessKeyBuf []byte,
	secretAccessKeyLen *uintptr,
	sessionTokenBuf []byte,
	sessionTokenLen *uintptr,
	expiresAtMillis *int64,
) uint8 {
	value, ok := credentialProviderRegistry.Load(clientID)
	if !ok {
		return credentialCallbackFailure
	}
	entry, ok := value.(*credentialProviderEntry)
	if !ok || entry == nil {
		return credentialCallbackFailure
	}

	entry.mu.Lock()
	credentials := entry.pending
	provider := entry.provider
	if credentials != nil {
		status := writeCredentialProviderResult(
			entry,
			credentials,
			accessKeyIDBuf,
			accessKeyIDLen,
			secretAccessKeyBuf,
			secretAccessKeyLen,
			sessionTokenBuf,
			sessionTokenLen,
			expiresAtMillis,
		)
		entry.mu.Unlock()
		return status
	}
	entry.mu.Unlock()

	// The provider may call Close/unregister recursively, so it must run without the
	// entry mutex held. Rust serializes complete logical calls for this provider.
	if provider == nil {
		return credentialCallbackFailure
	}
	credentials, valid := encodeCredentialProviderResult(provider)
	if !valid {
		return credentialCallbackFailure
	}

	entry.mu.Lock()
	defer entry.mu.Unlock()
	current, registered := credentialProviderRegistry.Load(clientID)
	if registered && current != entry {
		return credentialCallbackFailure
	}
	return writeCredentialProviderResult(
		entry,
		credentials,
		accessKeyIDBuf,
		accessKeyIDLen,
		secretAccessKeyBuf,
		secretAccessKeyLen,
		sessionTokenBuf,
		sessionTokenLen,
		expiresAtMillis,
	)
}

// writeCredentialProviderResult is called with entry.mu held. It retains at most one
// validated encoded value, and only when Rust must immediately resize and retry.
func writeCredentialProviderResult(
	entry *credentialProviderEntry,
	credentials *encodedCredentials,
	accessKeyIDBuf []byte,
	accessKeyIDLen *uintptr,
	secretAccessKeyBuf []byte,
	secretAccessKeyLen *uintptr,
	sessionTokenBuf []byte,
	sessionTokenLen *uintptr,
	expiresAtMillis *int64,
) uint8 {
	if !credentialBuffersFit(credentials, accessKeyIDBuf, secretAccessKeyBuf, sessionTokenBuf) {
		setCredentialLengths(credentials, accessKeyIDLen, secretAccessKeyLen, sessionTokenLen)
		entry.pending = credentials
		return credentialCallbackBufferTooSmall
	}

	// Validate every capacity before writing, then copy all fields as one logical operation.
	copy(accessKeyIDBuf, credentials.accessKeyID)
	copy(secretAccessKeyBuf, credentials.secretAccessKey)
	copy(sessionTokenBuf, credentials.sessionToken)
	setCredentialLengths(credentials, accessKeyIDLen, secretAccessKeyLen, sessionTokenLen)
	*expiresAtMillis = credentials.expiresAtMillis
	entry.pending = nil
	return credentialCallbackSuccess
}

func credentialBuffer(pointer *C.uint8_t, length C.uintptr_t) []byte {
	if length == 0 {
		return nil
	}
	return unsafe.Slice((*byte)(unsafe.Pointer(pointer)), int(length))
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
	status := handleCredentialProviderCallback(
		uintptr(clientID),
		credentialBuffer(accessKeyIDBuf, accessKeyIDBufLen),
		(*uintptr)(unsafe.Pointer(accessKeyIDLen)),
		credentialBuffer(secretAccessKeyBuf, secretAccessKeyBufLen),
		(*uintptr)(unsafe.Pointer(secretAccessKeyLen)),
		credentialBuffer(sessionTokenBuf, sessionTokenBufLen),
		(*uintptr)(unsafe.Pointer(sessionTokenLen)),
		(*int64)(unsafe.Pointer(expiresAtMillis)),
	)
	return C.uint8_t(status)
}
