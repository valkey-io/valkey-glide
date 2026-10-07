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

func assertCredentialCallbackDidNotWrite(
	t *testing.T,
	storage [3][]byte,
	fill byte,
	lengths [3]uintptr,
	expiresAt int64,
	lengthsMayBeSet bool,
) {
	t.Helper()
	if !lengthsMayBeSet {
		assert.Equal(t, [3]uintptr{uintptr(^uint(0)), uintptr(^uint(0)), uintptr(^uint(0))}, lengths)
	}
	assert.Equal(t, int64(-1), expiresAt)
	for _, buffer := range storage {
		assert.Equal(t, bytes.Repeat([]byte{fill}, len(buffer)), buffer)
	}
}

func TestRegisterAndUnregisterCredentialProvider(t *testing.T) {
	clientID := uintptr(99999)
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "key", SecretAccessKey: "secret"}, nil
	})

	registerCredentialProvider(clientID, provider)
	value, ok := credentialProviderRegistry.Load(clientID)
	require.True(t, ok, "provider should be registered")
	registered, ok := value.(config.GlideCredentialProvider)
	require.True(t, ok)
	assert.NotNil(t, registered)

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
	assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)
}

func TestLargeLogicalFetchInvokesProviderForSizingAndRetry(t *testing.T) {
	clientID := uintptr(100001)
	accessKey := strings.Repeat("a", 3000)
	secretKey := strings.Repeat("b", 2500)
	token := strings.Repeat("c", 2200)
	var calls atomic.Int32
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		calls.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          accessKey,
			SecretAccessKey:      secretKey,
			SessionToken:         token,
			ExpiresAtEpochMillis: 123456,
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	status, firstStorage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{2048, 2048, 2048},
		0xa5,
	)

	require.Equal(t, credentialCallbackBufferTooSmall, status)
	assert.Equal(t, [3]uintptr{3000, 2500, 2200}, lengths)
	assert.Equal(t, int32(1), calls.Load())
	assertCredentialCallbackDidNotWrite(t, firstStorage, 0xa5, lengths, expiresAt, true)

	status, secondStorage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{maxCredentialBytes, maxCredentialBytes, maxCredentialBytes},
		0xa5,
	)

	require.Equal(t, credentialCallbackSuccess, status)
	assert.Equal(t, [3]uintptr{3000, 2500, 2200}, lengths)
	assert.Equal(t, int64(123456), expiresAt)
	assert.Equal(t, int32(2), calls.Load(), "each callback invocation must invoke the provider once")
	assert.Equal(t, accessKey, string(secondStorage[0][1:3001]))
	assert.Equal(t, secretKey, string(secondStorage[1][1:2501]))
	assert.Equal(t, token, string(secondStorage[2][1:2201]))
	for _, buffer := range secondStorage {
		assert.Equal(t, byte(0xa5), buffer[0], "leading canary changed")
		assert.Equal(t, byte(0xa5), buffer[len(buffer)-1], "trailing canary changed")
	}
}

func TestAbandonedSizingResultIsNotReused(t *testing.T) {
	clientID := uintptr(100002)
	var calls atomic.Int32
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		call := calls.Add(1)
		if call == 1 {
			return config.AwsCredentials{
				AccessKeyID:     strings.Repeat("a", 3000),
				SecretAccessKey: "secret-1",
			}, nil
		}
		return config.AwsCredentials{
			AccessKeyID:          strings.Repeat("b", 4000),
			SecretAccessKey:      "secret-2",
			ExpiresAtEpochMillis: 2,
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	status, _, firstLengths, _ := invokeCredentialCallbackForTest(
		clientID,
		[3]int{2048, 2048, 2048},
		0xa5,
	)
	require.Equal(t, credentialCallbackBufferTooSmall, status)
	assert.Equal(t, [3]uintptr{3000, 8, 0}, firstLengths)

	status, storage, secondLengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{maxCredentialBytes, maxCredentialBytes, maxCredentialBytes},
		0xa5,
	)
	require.Equal(t, credentialCallbackSuccess, status)
	assert.Equal(t, int32(2), calls.Load())
	assert.Equal(t, [3]uintptr{4000, 8, 0}, secondLengths)
	assert.Equal(t, strings.Repeat("b", 4000), string(storage[0][1:4001]))
	assert.Equal(t, "secret-2", string(storage[1][1:9]))
	assert.Equal(t, int64(2), expiresAt)
}

func TestCredentialProviderCallbackInsufficientCapacityWritesOnlyLengths(t *testing.T) {
	clientID := uintptr(100003)
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		return config.AwsCredentials{
			AccessKeyID:          "access",
			SecretAccessKey:      strings.Repeat("s", 40),
			SessionToken:         "token",
			ExpiresAtEpochMillis: 99,
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{16, 16, 16},
		0xa5,
	)

	assert.Equal(t, credentialCallbackBufferTooSmall, status)
	assert.Equal(t, [3]uintptr{6, 40, 5}, lengths)
	assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, true)
}

func TestCredentialProviderCallbackRejectsBlankRequiredKeys(t *testing.T) {
	tests := []config.AwsCredentials{
		{AccessKeyID: "", SecretAccessKey: "secret"},
		{AccessKeyID: " \t\n", SecretAccessKey: "secret"},
		{AccessKeyID: "access", SecretAccessKey: ""},
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
		unregisterCredentialProvider(clientID)

		assert.Equal(t, credentialCallbackFailure, status)
		assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)
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
		unregisterCredentialProvider(clientID)

		assert.Equal(t, credentialCallbackFailure, status)
		assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)
	}
}

func TestCredentialProviderCallbackReentrantUnregisterCompletesCurrentInvocation(t *testing.T) {
	clientID := uintptr(100300)
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		unregisterCredentialProvider(clientID)
		return config.AwsCredentials{
			AccessKeyID:     "access",
			SecretAccessKey: "secret",
		}, nil
	})

	done := make(chan uint8, 1)
	go func() {
		status, _, _, _ := invokeCredentialCallbackForTest(clientID, [3]int{16, 16, 0}, 0xa5)
		done <- status
	}()

	select {
	case status := <-done:
		assert.Equal(t, credentialCallbackSuccess, status)
	case <-time.After(2 * time.Second):
		t.Fatal("reentrant unregister deadlocked")
	}
	_, registered := credentialProviderRegistry.Load(clientID)
	assert.False(t, registered)

	status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
		clientID,
		[3]int{16, 16, 0},
		0xa5,
	)
	assert.Equal(t, credentialCallbackFailure, status)
	assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)
}

func TestCredentialProviderConcurrentInvocationsAreIndependentAndCoherent(t *testing.T) {
	clientID := uintptr(100400)
	var calls atomic.Int32
	registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
		call := calls.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          fmt.Sprintf("access-%d", call),
			SecretAccessKey:      fmt.Sprintf("secret-%d", call),
			SessionToken:         fmt.Sprintf("token-%d", call),
			ExpiresAtEpochMillis: int64(call),
		}, nil
	})
	t.Cleanup(func() { unregisterCredentialProvider(clientID) })

	type callbackResult struct {
		status    uint8
		storage   [3][]byte
		lengths   [3]uintptr
		expiresAt int64
	}
	const invocations = 32
	results := make(chan callbackResult, invocations)
	var workers sync.WaitGroup
	for range invocations {
		workers.Add(1)
		go func() {
			defer workers.Done()
			status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
				clientID,
				[3]int{32, 32, 32},
				0xa5,
			)
			results <- callbackResult{status, storage, lengths, expiresAt}
		}()
	}
	workers.Wait()
	close(results)

	assert.Equal(t, int32(invocations), calls.Load())
	seen := make(map[int64]bool, invocations)
	for result := range results {
		require.Equal(t, credentialCallbackSuccess, result.status)
		id := result.expiresAt
		accessKey := string(result.storage[0][1 : 1+result.lengths[0]])
		secretKey := string(result.storage[1][1 : 1+result.lengths[1]])
		token := string(result.storage[2][1 : 1+result.lengths[2]])
		assert.Equal(t, fmt.Sprintf("access-%d", id), accessKey)
		assert.Equal(t, fmt.Sprintf("secret-%d", id), secretKey)
		assert.Equal(t, fmt.Sprintf("token-%d", id), token)
		assert.False(t, seen[id], "duplicate provider invocation %d", id)
		seen[id] = true
	}
}

func TestCredentialProviderOverCapResultsReturnFailure(t *testing.T) {
	tests := []config.AwsCredentials{
		{AccessKeyID: strings.Repeat("a", maxCredentialBytes+1), SecretAccessKey: "secret"},
		{
			AccessKeyID:     strings.Repeat("a", maxCredentialBytes/2+1),
			SecretAccessKey: strings.Repeat("b", maxCredentialBytes/2+1),
		},
	}
	for index, credentials := range tests {
		clientID := uintptr(100500 + index)
		var calls atomic.Int32
		registerCredentialProvider(clientID, func() (config.AwsCredentials, error) {
			calls.Add(1)
			return credentials, nil
		})

		status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
			clientID,
			[3]int{2048, 2048, 2048},
			0xa5,
		)
		unregisterCredentialProvider(clientID)

		assert.Equal(t, credentialCallbackFailure, status)
		assert.Equal(t, int32(1), calls.Load())
		assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)
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

func TestDirectClientsRetainCredentialProviderUntilClose(t *testing.T) {
	type factory func(config.GlideCredentialProvider) (*baseClient, error)
	factories := map[string]factory{
		"standalone": func(provider config.GlideCredentialProvider) (*baseClient, error) {
			cfg := config.NewClientConfiguration().
				WithAddress(&config.NodeAddress{Host: "127.0.0.1", Port: 1}).
				WithAddressResolver(func(host string, port int) (string, int) { return host, port }).
				WithCredentials(testIamCredentials(t, provider)).
				WithLazyConnect(true)
			client, err := NewClient(cfg)
			if err != nil {
				return nil, err
			}
			return &client.baseClient, nil
		},
		"cluster": func(provider config.GlideCredentialProvider) (*baseClient, error) {
			cfg := config.NewClusterClientConfiguration().
				WithAddress(&config.NodeAddress{Host: "127.0.0.1", Port: 1}).
				WithAddressResolver(func(host string, port int) (string, int) { return host, port }).
				WithCredentials(testIamCredentials(t, provider)).
				WithLazyConnect(true)
			client, err := NewClusterClient(cfg)
			if err != nil {
				return nil, err
			}
			return &client.baseClient, nil
		},
	}

	for name, create := range factories {
		t.Run(name, func(t *testing.T) {
			var calls atomic.Int32
			provider := func() (config.AwsCredentials, error) {
				calls.Add(1)
				return config.AwsCredentials{
					AccessKeyID:          "access",
					SecretAccessKey:      "secret",
					SessionToken:         "token",
					ExpiresAtEpochMillis: 1234,
				}, nil
			}
			beforeID := clientIDCounter.Load()
			client, err := create(provider)
			require.NoError(t, err)

			clientID := client.resolverID
			assert.Equal(t, beforeID+1, clientID)
			registered, ok := credentialProviderRegistry.Load(clientID)
			require.True(t, ok)
			assert.NotNil(t, registered)
			registeredResolver, ok := resolverRegistry.Load(clientID)
			require.True(t, ok)
			assert.NotNil(t, registeredResolver)

			status, storage, lengths, expiresAt := invokeCredentialCallbackForTest(
				clientID,
				[3]int{16, 16, 16},
				0xa5,
			)
			require.Equal(t, credentialCallbackSuccess, status)
			assert.Equal(t, [3]uintptr{6, 6, 5}, lengths)
			assert.Equal(t, "access", string(storage[0][1:7]))
			assert.Equal(t, "secret", string(storage[1][1:7]))
			assert.Equal(t, "token", string(storage[2][1:6]))
			assert.Equal(t, int64(1234), expiresAt)
			assert.GreaterOrEqual(t, calls.Load(), int32(1))

			client.Close()
			_, ok = credentialProviderRegistry.Load(clientID)
			assert.False(t, ok)
			_, ok = resolverRegistry.Load(clientID)
			assert.False(t, ok)
			assert.Zero(t, client.resolverID)

			status, storage, lengths, expiresAt = invokeCredentialCallbackForTest(
				clientID,
				[3]int{16, 16, 16},
				0xa5,
			)
			assert.Equal(t, credentialCallbackFailure, status)
			assertCredentialCallbackDidNotWrite(t, storage, 0xa5, lengths, expiresAt, false)

			client.Close()
			_, ok = credentialProviderRegistry.Load(clientID)
			assert.False(t, ok)
		})
	}
}

func TestDirectClientCreationFailureUnregistersCredentialProvider(t *testing.T) {
	providerError := errors.New("provider failed during initial authentication")
	type constructor func(config.GlideCredentialProvider) error
	constructors := map[string]constructor{
		"standalone": func(provider config.GlideCredentialProvider) error {
			cfg := config.NewClientConfiguration().
				WithAddress(&config.NodeAddress{Host: "127.0.0.1", Port: 1}).
				WithCredentials(testIamCredentials(t, provider))
			_, err := NewClient(cfg)
			return err
		},
		"cluster": func(provider config.GlideCredentialProvider) error {
			cfg := config.NewClusterClientConfiguration().
				WithAddress(&config.NodeAddress{Host: "127.0.0.1", Port: 1}).
				WithCredentials(testIamCredentials(t, provider))
			_, err := NewClusterClient(cfg)
			return err
		},
	}

	for name, create := range constructors {
		t.Run(name, func(t *testing.T) {
			var calls atomic.Int32
			beforeID := clientIDCounter.Load()
			err := create(func() (config.AwsCredentials, error) {
				calls.Add(1)
				return config.AwsCredentials{}, providerError
			})

			require.Error(t, err)
			assert.Equal(t, int32(1), calls.Load())
			clientID := beforeID + 1
			assert.Equal(t, clientID, clientIDCounter.Load())
			_, registered := credentialProviderRegistry.Load(clientID)
			assert.False(t, registered)
		})
	}
}
