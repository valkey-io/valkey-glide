// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func TestNewMonitorClientRejectsIamBeforeNativeWork(t *testing.T) {
	tests := map[string]config.GlideCredentialProvider{
		"default AWS credential chain": nil,
		"custom credential provider":   testCredentialProvider,
	}

	for name, provider := range tests {
		t.Run(name, func(t *testing.T) {
			beforeID := clientIDCounter.Load()
			beforeRegistry := credentialProviderRegistrySize()
			calls := 0
			if provider != nil {
				provider = func() (config.AwsCredentials, error) {
					calls++
					return testCredentialProvider()
				}
			}
			cfg := config.NewClientConfiguration().
				WithCredentials(testIamCredentials(t, provider)).
				WithRequestTimeout(-time.Second)

			monitor, err := NewMonitorClient(cfg, nil)

			assert.Nil(t, monitor)
			assert.ErrorIs(t, err, errMonitorIamUnsupported)
			assert.EqualError(t, err, monitorIamUnsupportedError)
			assert.Equal(t, 0, calls)
			assert.Equal(t, beforeID, clientIDCounter.Load())
			assert.Equal(t, beforeRegistry, credentialProviderRegistrySize())
		})
	}
}
