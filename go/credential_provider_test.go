// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"bytes"
	"errors"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func invokeCredentialCallbackForTest(
	clientID uintptr,
	capacities [3]int,
	fill byte,
) (uint8, [3][]byte, [3]uintptr, int64) {
	storage := [3][]byte{
		bytes.Repeat([]byte{fill}, capacities[0]+2),
		bytes.Repeat([]byte{fill}, capacities[1]+2),
		bytes.Repeat([]byte{fill}, capacities[2]+2),
	}
	buffers := [3][]byte{
		storage[0][1 : capacities[0]+1],
		storage[1][1 : capacities[1]+1],
		storage[2][1 : capacities[2]+1],
	}
	lengths := [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}
	expiresAt := int64(-1)
	status := handleCredentialProviderCallback(
		clientID,
		buffers[0],
		&lengths[0],
		buffers[1],
		&lengths[1],
		buffers[2],
		&lengths[2],
		&expiresAt,
	)
	return status, storage, lengths, expiresAt
}

type fetchedCredentials struct {
	accessKeyID     string
	secretAccessKey string
	sessionToken    string
	expiresAtMillis int64
}

func fetchLogicalCredentialsForTest(clientID uintptr) (fetchedCredentials, uint8) {
	status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{2048, 2048, 2048},
		0xa5,
	)
	if status == credentialCallbackBufferTooSmall {
		status, storage, lengths, expiresAt = invokeCredentialCallbackForTest(
			clientID,
			[3]int{int(lengths[0]), int(lengths[1]), int(lengths[2])},
			0xa5,
		)
	}
	if status != credentialCallbackSuccess {
		return fetchedCredentials{}, status
	}
	return fetchedCredentials{
		accessKeyID:     string(storage[0][1 : 1+lengths[0]]),
		secretAccessKey: string(storage[1][1 : 1+lengths[1]]),
		sessionToken:    string(storage[2][1 : 1+lengths[2]]),
		expiresAtMillis: expiresAt,
	}, status
}

func pendingCredentialsForTest(t *testing.T, clientID uintptr) *encodedCredentials {
	t.Helper()
	value, ok := credentialProviderRegistry.Load(clientID)
	require.True(t, ok)
	entry := value.(*credentialProviderEntry)
	entry.mu.Lock()
	defer entry.mu.Unlock()
	return entry.pending
}

func TestRegisterAndUnregisterCredentialProvider(t *testing.T) {
	clientID := uintptr(99999)
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "key", SecretAccessKey: "secret"}, nil
	})

	registerCredentialProvider(clientID, provider)
	value, ok := credentialProviderRegistry.Load(clientID)
	require.True(t, ok, "provider should be registered")
	entry, ok := value.(*credentialProviderEntry)
	require.True(t, ok)
	assert.NotNil(t, entry.provider)

	unregisterCredentialProvider(clientID)
	_, ok = credentialProviderRegistry.Load(clientID)
	assert.False(t, ok, "provider should be unregistered")
}

func TestCredentialProviderCallbackNotRegistered(t *testing.T) {
	status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
		uintptr(0xdeadbeef),
		[3]int{16, 16, 16},
		0xa5,
	)

	assert.Equal(t, credentialCallbackFailure, status)
	assert.Equal(t, [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}, lengths)
	assert.Equal(t, int64(-1), expiresAt)
	for _, buffer := range storage {
		assert.Equal(t, bytes.Repeat([]byte{0xa5}, len(buffer)), buffer)
	}
}

func TestCredentialProviderCallbackLargeCredentialRetriesWithoutReinvokingProvider(t *testing.T) {
	clientID := uintptr(100001)
	accessKey := strings.Repeat("a", 3000)
	secretKey := strings.Repeat("b", 2500)
	token := strings.Repeat("c", 2200)
	var calls atomic.Int32
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		calls.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          accessKey,
			SecretAccessKey:      secretKey,
			SessionToken:         token,
			ExpiresAtEpochMillis: 123456,
		}, nil
	})
	registerCredentialProvider(clientID, provider)
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	status, firstStorage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{2048, 2048, 2048},
		0xa5,
	)

	assert.Equal(t, credentialCallbackBufferTooSmall, status)
	assert.Equal(t, [3]uintptr{3000, 2500, 2200}, lengths)
	assert.Equal(t, int64(-1), expiresAt)
	assert.Equal(t, int32(1), calls.Load())
	for _, buffer := range firstStorage {
		assert.Equal(t, bytes.Repeat([]byte{0xa5}, len(buffer)), buffer, "status 2 must not write credential buffers")
	}

	status, secondStorage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{3000, 2500, 2200},
		0xa5,
	)

	assert.Equal(t, credentialCallbackSuccess, status)
	assert.Equal(t, [3]uintptr{3000, 2500, 2200}, lengths)
	assert.Equal(t, int64(123456), expiresAt)
	assert.Equal(t, int32(1), calls.Load(), "retry must use the pending encoded credentials")
	assert.Equal(t, accessKey, string(secondStorage[0][1:3001]))
	assert.Equal(t, secretKey, string(secondStorage[1][1:2501]))
	assert.Equal(t, token, string(secondStorage[2][1:2201]))
	for _, buffer := range secondStorage {
		assert.Equal(t, byte(0xa5), buffer[0], "leading canary changed")
		assert.Equal(t, byte(0xa5), buffer[len(buffer)-1], "trailing canary changed")
	}

	value, ok := credentialProviderRegistry.Load(clientID)
	require.True(t, ok)
	entry := value.(*credentialProviderEntry)
	entry.mu.Lock()
	assert.Nil(t, entry.pending, "pending credentials must clear after success")
	entry.mu.Unlock()
}

func TestCredentialProviderCallbackRejectsWhitespaceOnlyRequiredKeys(t *testing.T) {
	tests := []config.AwsCredentials{
		{AccessKeyID: " \t\n", SecretAccessKey: "secret"},
		{AccessKeyID: "access", SecretAccessKey: " \r\n"},
	}
	for index, credentials := range tests {
		clientID := uintptr(100100 + index)
		registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
			return credentials, nil
		})

		status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
			clientID,
			[3]int{32, 32, 32},
			0xa5,
		)
		assert.Nil(t, pendingCredentialsForTest(t, clientID))
		unregisterCredentialProvider(clientID)

		assert.Equal(t, credentialCallbackFailure, status)
		assert.Equal(t, [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}, lengths)
		assert.Equal(t, int64(-1), expiresAt)
		for _, buffer := range storage {
			assert.Equal(t, bytes.Repeat([]byte{0xa5}, len(buffer)), buffer)
		}
	}
}

func TestCredentialProviderCallbackReturnsFailureOnProviderErrorOrPanic(t *testing.T) {
	tests := []config.GlideCredentialProvider{
		func() (config.AwsCredentials, error) {
			return config.AwsCredentials{}, errors.New("provider failed")
		},
		func() (config.AwsCredentials, error) {
			panic("provider panicked")
		},
	}
	for index, provider := range tests {
		clientID := uintptr(100200 + index)
		registerCredentialProvider(clientID, provider)

		status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
			clientID,
			[3]int{32, 32, 32},
			0xa5,
		)
		assert.Nil(t, pendingCredentialsForTest(t, clientID))
		unregisterCredentialProvider(clientID)

		assert.Equal(t, credentialCallbackFailure, status)
		assert.Equal(t, [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}, lengths)
		assert.Equal(t, int64(-1), expiresAt)
		for _, buffer := range storage {
			assert.Equal(t, bytes.Repeat([]byte{0xa5}, len(buffer)), buffer)
		}
	}
}

func TestUnregisterCredentialProviderClearsPendingRetry(t *testing.T) {
	clientID := uintptr(100300)
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{
			AccessKeyID:     strings.Repeat("a", 3000),
			SecretAccessKey: "secret",
		}, nil
	})
	registerCredentialProvider(clientID, provider)

	status, _, _, _ := invokeCredentialCallbackForTest(clientID, [3]int{2048, 2048, 2048}, 0xa5)
	require.Equal(t, credentialCallbackBufferTooSmall, status)
	value, ok := credentialProviderRegistry.Load(clientID)
	require.True(t, ok)
	entry := value.(*credentialProviderEntry)
	entry.mu.Lock()
	require.NotNil(t, entry.pending)
	entry.mu.Unlock()

	unregisterCredentialProvider(clientID)

	_, ok = credentialProviderRegistry.Load(clientID)
	assert.False(t, ok)
	entry.mu.Lock()
	assert.Nil(t, entry.provider)
	assert.Nil(t, entry.pending)
	entry.mu.Unlock()
	status, _, _, _ = invokeCredentialCallbackForTest(clientID, [3]int{3000, 16, 0}, 0xa5)
	assert.Equal(t, credentialCallbackFailure, status)
}

func TestCredentialProviderCallbackReentrantUnregisterDoesNotDeadlock(t *testing.T) {
	clientID := uintptr(100400)
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		unregisterCredentialProvider(clientID)
		return config.AwsCredentials{
			AccessKeyID:     strings.Repeat("a", 3000),
			SecretAccessKey: "secret",
		}, nil
	})

	done := make(chan [2]uint8, 1)
	go func() {
		first, _, lengths, _ := invokeCredentialCallbackForTest(clientID, [3]int{2048, 2048, 2048}, 0xa5)
		retry, _, _, _ := invokeCredentialCallbackForTest(
			clientID,
			[3]int{int(lengths[0]), int(lengths[1]), int(lengths[2])},
			0xa5,
		)
		done <- [2]uint8{first, retry}
	}()

	select {
	case statuses := <-done:
		assert.Equal(t, [2]uint8{credentialCallbackBufferTooSmall, credentialCallbackFailure}, statuses)
	case <-time.After(2 * time.Second):
		t.Fatal("reentrant unregister deadlocked")
	}
	_, registered := credentialProviderRegistry.Load(clientID)
	assert.False(t, registered)
}

func TestCredentialProviderConcurrentSerializedFetchesAreCoherent(t *testing.T) {
	clientID := uintptr(100401)
	var calls atomic.Int32
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		call := calls.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          fmt.Sprintf("access-%d:%s", call, strings.Repeat("a", 3000)),
			SecretAccessKey:      fmt.Sprintf("secret-%d", call),
			SessionToken:         fmt.Sprintf("token-%d", call),
			ExpiresAtEpochMillis: int64(call),
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	// This is the same full-call serialization supplied by FFICredentialsProvider.
	var rustFullCall sync.Mutex
	const fetches = 8
	results := make(chan fetchedCredentials, fetches)
	var workers sync.WaitGroup
	for range fetches {
		workers.Add(1)
		go func() {
			defer workers.Done()
			rustFullCall.Lock()
			credentials, status := fetchLogicalCredentialsForTest(clientID)
			rustFullCall.Unlock()
			require.Equal(t, credentialCallbackSuccess, status)
			results <- credentials
		}()
	}
	workers.Wait()
	close(results)

	assert.Equal(t, int32(fetches), calls.Load(), "each logical fetch must invoke the provider once")
	seen := make(map[int64]bool, fetches)
	for credentials := range results {
		id := credentials.expiresAtMillis
		assert.Equal(t, fmt.Sprintf("access-%d:%s", id, strings.Repeat("a", 3000)), credentials.accessKeyID)
		assert.Equal(t, fmt.Sprintf("secret-%d", id), credentials.secretAccessKey)
		assert.Equal(t, fmt.Sprintf("token-%d", id), credentials.sessionToken)
		assert.False(t, seen[id], "duplicate credential generation %d", id)
		seen[id] = true
	}
	assert.Nil(t, pendingCredentialsForTest(t, clientID))
}

func TestCredentialProviderPendingResultRecoversOnNextLogicalCall(t *testing.T) {
	clientID := uintptr(100402)
	var calls atomic.Int32
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		call := calls.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          strings.Repeat(string(rune('a'+call-1)), 3000),
			SecretAccessKey:      fmt.Sprintf("secret-%d", call),
			ExpiresAtEpochMillis: int64(call),
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	status, _, _, _ := invokeCredentialCallbackForTest(clientID, [3]int{2048, 2048, 2048}, 0xa5)
	require.Equal(t, credentialCallbackBufferTooSmall, status)
	require.NotNil(t, pendingCredentialsForTest(t, clientID))
	assert.Equal(t, int32(1), calls.Load())

	credentials, status := fetchLogicalCredentialsForTest(clientID)
	require.Equal(t, credentialCallbackSuccess, status)
	assert.Equal(t, strings.Repeat("a", 3000), credentials.accessKeyID)
	assert.Equal(t, "secret-1", credentials.secretAccessKey)
	assert.Equal(t, int32(1), calls.Load(), "the abandoned pending result must be reused")
	assert.Nil(t, pendingCredentialsForTest(t, clientID), "successful retry must clear pending state")

	credentials, status = fetchLogicalCredentialsForTest(clientID)
	require.Equal(t, credentialCallbackSuccess, status)
	assert.Equal(t, strings.Repeat("b", 3000), credentials.accessKeyID)
	assert.Equal(t, "secret-2", credentials.secretAccessKey)
	assert.Equal(t, int32(2), calls.Load())
}

func TestCredentialProviderOverCapResultsAreNeverCached(t *testing.T) {
	tests := []config.AwsCredentials{
		{AccessKeyID: strings.Repeat("a", maxCredentialBytes+1), SecretAccessKey: "secret"},
		{
			AccessKeyID:     strings.Repeat("a", maxCredentialBytes/2+1),
			SecretAccessKey: strings.Repeat("b", maxCredentialBytes/2+1),
		},
	}
	for index, credentials := range tests {
		clientID := uintptr(100410 + index)
		var calls atomic.Int32
		registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
			calls.Add(1)
			return credentials, nil
		})

		for range 2 {
			status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
				clientID,
				[3]int{2048, 2048, 2048},
				0xa5,
			)
			assert.Equal(t, credentialCallbackFailure, status)
			assert.Equal(t, [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}, lengths)
			assert.Equal(t, int64(-1), expiresAt)
			for _, buffer := range storage {
				assert.Equal(t, bytes.Repeat([]byte{0xa5}, len(buffer)), buffer)
			}
			assert.Nil(t, pendingCredentialsForTest(t, clientID))
		}
		assert.Equal(t, int32(2), calls.Load(), "invalid values must not become pending")
		unregisterCredentialProvider(clientID)
	}
}

func TestGetCredentialProvider(t *testing.T) {
	iam := config.NewIamAuthConfig("cluster", config.ElastiCache, "us-east-1")
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "k", SecretAccessKey: "s"}, nil
	})
	iam.WithCredentialProvider(provider)

	got := iam.GetCredentialProvider()
	assert.NotNil(t, got, "GetCredentialProvider should return the registered provider")
}

func TestGetCredentialProviderNil(t *testing.T) {
	iam := config.NewIamAuthConfig("cluster", config.ElastiCache, "us-east-1")
	assert.Nil(t, iam.GetCredentialProvider(), "default provider should be nil")
}
