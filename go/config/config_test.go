// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
)

// Test certificate constants
const (
	testCertData1 = "-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----"
	testCertData2 = "-----BEGIN CERTIFICATE-----\nMIIC1...\n-----END CERTIFICATE-----\n"
	testCertData3 = "-----BEGIN CERTIFICATE-----\nMIIC2...\n-----END CERTIFICATE-----\n"
)

func TestDefaultStandaloneConfig(t *testing.T) {
	config := NewClientConfiguration()
	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		TlsMode:            protobuf.TlsMode_NoTls,
		ClusterModeEnabled: false,
		ReadFrom:           protobuf.ReadFrom_Primary,
	}

	result, err := config.toProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	assert.Equal(t, expected, result)
}

func TestDefaultClusterConfig(t *testing.T) {
	config := NewClusterClientConfiguration()
	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		TlsMode:            protobuf.TlsMode_NoTls,
		ClusterModeEnabled: true,
		ReadFrom:           protobuf.ReadFrom_Primary,
	}

	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	assert.Equal(t, expected, result)
}

func TestConfig_allFieldsSet(t *testing.T) {
	hosts := []string{"host1", "host2"}
	ports := []int{1234, 5678}
	username := "username"
	password := "password"
	timeout := 3 * time.Second
	clientName := "client name"
	retries, factor, base := 5, 10, 50
	databaseId := 1

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithReadFrom(PreferReplica).
		WithCredentials(NewServerCredentials(username, password)).
		WithRequestTimeout(timeout).
		WithClientName(clientName).
		WithReconnectStrategy(NewBackoffStrategy(retries, factor, base)).
		WithDatabaseId(databaseId)

	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		TlsMode:            protobuf.TlsMode_SecureTls,
		ReadFrom:           protobuf.ReadFrom_PreferReplica,
		ClusterModeEnabled: false,
		AuthenticationInfo: &protobuf.AuthenticationInfo{Username: username, Password: password},
		RequestTimeout:     uint32(timeout.Milliseconds()),
		ClientName:         clientName,
		ConnectionRetryStrategy: &protobuf.ConnectionRetryStrategy{
			NumberOfRetries: uint32(retries),
			Factor:          uint32(factor),
			ExponentBase:    uint32(base),
		},
		DatabaseId: uint32(databaseId),
	}

	assert.Equal(t, len(hosts), len(ports))
	for i := 0; i < len(hosts); i++ {
		config.WithAddress(&NodeAddress{hosts[i], ports[i]})
		expected.Addresses = append(
			expected.Addresses,
			&protobuf.NodeAddress{Host: hosts[i], Port: uint32(ports[i])},
		)
	}

	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	assert.Equal(t, expected, result)
}

func TestGlideClient_BackoffStrategy_withJitter(t *testing.T) {
	host := "localhost"
	port := 6379
	retries, factor, base, jitter := 5, 2, 3, 25

	strategy := NewBackoffStrategy(retries, factor, base).WithJitterPercent(jitter)

	config := NewClientConfiguration().
		WithAddress(&NodeAddress{Host: host, Port: port}).
		WithReconnectStrategy(strategy)

	result, err := config.toProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	j := uint32(jitter)
	expected := &protobuf.ConnectionRequest{
		LibName: "GlideGo",
		Addresses: []*protobuf.NodeAddress{
			{Host: host, Port: uint32(port)},
		},
		ConnectionRetryStrategy: &protobuf.ConnectionRetryStrategy{
			NumberOfRetries: uint32(retries),
			Factor:          uint32(factor),
			ExponentBase:    uint32(base),
			JitterPercent:   &j,
		},
	}

	assert.Equal(t, expected, result)
}

func TestGlideClusterClient_BackoffStrategy_withJitter(t *testing.T) {
	t.Skip("TODO: Fix this test")

	host := "localhost"
	port := 6379
	retries, factor, base, jitter := 5, 2, 3, 25

	strategy := NewBackoffStrategy(retries, factor, base).WithJitterPercent(jitter)

	config := NewClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: host, Port: port}).
		WithReconnectStrategy(strategy)

	result, err := config.toProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	j := uint32(jitter)
	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		ClusterModeEnabled: true,
		Addresses: []*protobuf.NodeAddress{
			{Host: host, Port: uint32(port)},
		},
		ConnectionRetryStrategy: &protobuf.ConnectionRetryStrategy{
			NumberOfRetries: uint32(retries),
			Factor:          uint32(factor),
			ExponentBase:    uint32(base),
			JitterPercent:   &j,
		},
	}

	assert.Equal(t, expected, result)
}

func TestNodeAddress(t *testing.T) {
	parameters := []struct {
		input    NodeAddress
		expected *protobuf.NodeAddress
	}{
		{NodeAddress{}, &protobuf.NodeAddress{Host: DefaultHost, Port: DefaultPort}},
		{NodeAddress{Host: "host"}, &protobuf.NodeAddress{Host: "host", Port: DefaultPort}},
		{NodeAddress{Port: 1234}, &protobuf.NodeAddress{Host: DefaultHost, Port: 1234}},
		{NodeAddress{"host", 1234}, &protobuf.NodeAddress{Host: "host", Port: 1234}},
	}

	for i, parameter := range parameters {
		t.Run(fmt.Sprintf("Testing [%v]", i), func(t *testing.T) {
			result := parameter.input.toProtobuf()

			assert.Equal(t, parameter.expected, result)
		})
	}
}

func TestServerCredentials(t *testing.T) {
	parameters := []struct {
		input    *ServerCredentials
		expected *protobuf.AuthenticationInfo
	}{
		{
			NewServerCredentials("username", "password"),
			&protobuf.AuthenticationInfo{Username: "username", Password: "password"},
		},
		{
			NewServerCredentialsWithDefaultUsername("password"),
			&protobuf.AuthenticationInfo{Password: "password"},
		},
	}

	for i, parameter := range parameters {
		t.Run(fmt.Sprintf("Testing [%v]", i), func(t *testing.T) {
			result := parameter.input.toProtobuf()

			assert.Equal(t, parameter.expected, result)
		})
	}
}

func TestServerCredentialsWithIam(t *testing.T) {
	iamConfig := NewIamAuthConfig("my-cluster", ElastiCache, "us-east-1")
	creds, err := NewServerCredentialsWithIam("myUser", iamConfig)

	assert.Nil(t, err)
	assert.NotNil(t, creds)
	assert.True(t, creds.IsIamAuth())

	authInfo := creds.toProtobuf()
	assert.Equal(t, "myUser", authInfo.Username)
	assert.Equal(t, "", authInfo.Password)
	assert.NotNil(t, authInfo.IamCredentials)
	assert.Equal(t, "my-cluster", authInfo.IamCredentials.ClusterName)
	assert.Equal(t, "us-east-1", authInfo.IamCredentials.Region)
	assert.Equal(t, protobuf.ServiceType_ELASTICACHE, authInfo.IamCredentials.ServiceType)
	assert.Nil(t, authInfo.IamCredentials.RefreshIntervalSeconds)
}

func TestServerCredentialsWithIamCustomRefresh(t *testing.T) {
	iamConfig := NewIamAuthConfig("my-cluster", MemoryDB, "us-west-2").
		WithRefreshIntervalSeconds(600)
	creds, err := NewServerCredentialsWithIam("myUser", iamConfig)

	assert.Nil(t, err)
	assert.NotNil(t, creds)

	authInfo := creds.toProtobuf()
	assert.Equal(t, protobuf.ServiceType_MEMORYDB, authInfo.IamCredentials.ServiceType)
	assert.Equal(t, uint32(600), *authInfo.IamCredentials.RefreshIntervalSeconds)
}

func TestServerCredentialsWithIamRequiresUsername(t *testing.T) {
	iamConfig := NewIamAuthConfig("my-cluster", ElastiCache, "us-east-1")
	creds, err := NewServerCredentialsWithIam("", iamConfig)

	assert.NotNil(t, err)
	assert.Nil(t, creds)
	assert.Contains(t, err.Error(), "username is required")
}

func TestServerCredentialsWithIamRequiresConfig(t *testing.T) {
	creds, err := NewServerCredentialsWithIam("myUser", nil)

	assert.NotNil(t, err)
	assert.Nil(t, creds)
	assert.Contains(t, err.Error(), "iamConfig cannot be nil")
}

func TestConfig_AzAffinity(t *testing.T) {
	hosts := []string{"host1", "host2"}
	ports := []int{1234, 5678}
	clientName := "client name"
	az := "us-east-1a"

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithReadFrom(AzAffinity).
		WithClientName(clientName).
		WithClientAZ(az)

	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		TlsMode:            protobuf.TlsMode_SecureTls,
		ReadFrom:           protobuf.ReadFrom_AZAffinity,
		ClusterModeEnabled: false,
		ClientName:         clientName,
		ClientAz:           az,
	}

	assert.Equal(t, len(hosts), len(ports))
	for i := 0; i < len(hosts); i++ {
		config.WithAddress(&NodeAddress{hosts[i], ports[i]})
		expected.Addresses = append(
			expected.Addresses,
			&protobuf.NodeAddress{Host: hosts[i], Port: uint32(ports[i])},
		)
	}

	result, err := config.toProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert config to protobuf: %v", err)
	}

	assert.Equal(t, expected, result)
}

func TestConfig_InvalidRequestAndConnectionTimeouts(t *testing.T) {
	// RequestTimeout Negative duration
	config := NewClientConfiguration().
		WithRequestTimeout(-1 * time.Hour)

	_, err := config.ToProtobuf()
	assert.EqualError(t, err, "setting request timeout returned an error: invalid duration was specified")

	config2 := NewClusterClientConfiguration().
		WithRequestTimeout(-1 * time.Hour)

	_, err2 := config2.ToProtobuf()
	assert.EqualError(t, err2, "setting request timeout returned an error: invalid duration was specified")

	// RequestTimeout 50 days
	config3 := NewClientConfiguration().
		WithRequestTimeout(1200 * time.Hour)

	_, err3 := config3.ToProtobuf()
	assert.EqualError(t, err3, "setting request timeout returned an error: invalid duration was specified")

	config4 := NewClusterClientConfiguration().
		WithRequestTimeout(1200 * time.Hour)

	_, err4 := config4.ToProtobuf()
	assert.EqualError(t, err4, "setting request timeout returned an error: invalid duration was specified")

	// ConnectionTimeout Negative duration
	config5 := NewClientConfiguration().
		WithAdvancedConfiguration(NewAdvancedClientConfiguration().WithConnectionTimeout(-1 * time.Hour))

	_, err5 := config5.ToProtobuf()
	assert.EqualError(t, err5, "setting connection timeout returned an error: invalid duration was specified")

	config6 := NewClusterClientConfiguration().
		WithAdvancedConfiguration(NewAdvancedClusterClientConfiguration().WithConnectionTimeout(-1 * time.Hour))

	_, err6 := config6.ToProtobuf()
	assert.EqualError(t, err6, "setting connection timeout returned an error: invalid duration was specified")

	// ConnectionTimeout 50 days
	config7 := NewClientConfiguration().
		WithAdvancedConfiguration(NewAdvancedClientConfiguration().WithConnectionTimeout(1200 * time.Hour))

	_, err7 := config7.ToProtobuf()
	assert.EqualError(t, err7, "setting connection timeout returned an error: invalid duration was specified")

	config8 := NewClusterClientConfiguration().
		WithAdvancedConfiguration(NewAdvancedClusterClientConfiguration().WithConnectionTimeout(1200 * time.Hour))

	_, err8 := config8.ToProtobuf()
	assert.EqualError(t, err8, "setting connection timeout returned an error: invalid duration was specified")
}

func TestConfig_LazyConnect(t *testing.T) {
	// Test for ClientConfiguration
	clientConfig := NewClientConfiguration().
		WithLazyConnect(true)

	clientResult, err := clientConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert client config to protobuf: %v", err)
	}

	assert.True(t, clientResult.LazyConnect)

	// Test for ClusterClientConfiguration
	clusterConfig := NewClusterClientConfiguration().
		WithLazyConnect(true)

	clusterResult, err := clusterConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert cluster config to protobuf: %v", err)
	}

	assert.True(t, clusterResult.LazyConnect)

	// Test default value (false) for ClientConfiguration
	defaultClientConfig := NewClientConfiguration()

	defaultClientResult, err := defaultClientConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default client config to protobuf: %v", err)
	}

	assert.False(t, defaultClientResult.LazyConnect)

	// Test default value (false) for ClusterClientConfiguration
	defaultClusterConfig := NewClusterClientConfiguration()

	defaultClusterResult, err := defaultClusterConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default cluster config to protobuf: %v", err)
	}

	assert.False(t, defaultClusterResult.LazyConnect)
}

func TestConfig_DatabaseId(t *testing.T) {
	// Test standalone client with database ID
	standaloneConfig := NewClientConfiguration().WithDatabaseId(5)
	standaloneResult, err := standaloneConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert standalone config to protobuf: %v", err)
	}
	assert.Equal(t, uint32(5), standaloneResult.DatabaseId)

	// Test cluster client with database ID
	clusterConfig := NewClusterClientConfiguration().WithDatabaseId(3)
	clusterResult, err := clusterConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert cluster config to protobuf: %v", err)
	}
	assert.Equal(t, uint32(3), clusterResult.DatabaseId)

	// Test default behavior (no database ID set)
	defaultStandaloneConfig := NewClientConfiguration()
	defaultStandaloneResult, err := defaultStandaloneConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default standalone config to protobuf: %v", err)
	}
	assert.Equal(t, uint32(0), defaultStandaloneResult.DatabaseId)

	defaultClusterConfig := NewClusterClientConfiguration()
	defaultClusterResult, err := defaultClusterConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default cluster config to protobuf: %v", err)
	}
	assert.Equal(t, uint32(0), defaultClusterResult.DatabaseId)
}

func TestConfig_DatabaseId_BaseConfiguration(t *testing.T) {
	// Test that database_id is properly handled in base configuration for both client types

	// Test standalone client inherits database_id from base configuration
	standaloneConfig := NewClientConfiguration().WithDatabaseId(5)
	standaloneResult, err := standaloneConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(5), standaloneResult.DatabaseId)
	assert.False(t, standaloneResult.ClusterModeEnabled)

	// Test cluster client inherits database_id from base configuration
	clusterConfig := NewClusterClientConfiguration().WithDatabaseId(3)
	clusterResult, err := clusterConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(3), clusterResult.DatabaseId)
	assert.True(t, clusterResult.ClusterModeEnabled)
}

func TestClusterConfig_RefreshTopologyFromInitialNodes(t *testing.T) {
	// Test that refreshTopologyFromInitialNodes defaults to false
	defaultConfig := NewClusterClientConfiguration()
	defaultResult, err := defaultConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default cluster config to protobuf: %v", err)
	}
	assert.False(t, defaultResult.RefreshTopologyFromInitialNodes)

	// Test that refreshTopologyFromInitialNodes can be set to true
	enabledConfig := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithRefreshTopologyFromInitialNodes(true),
		)
	enabledResult, err := enabledConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert enabled cluster config to protobuf: %v", err)
	}
	assert.True(t, enabledResult.RefreshTopologyFromInitialNodes)

	// Test that refreshTopologyFromInitialNodes can be explicitly set to false
	disabledConfig := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithRefreshTopologyFromInitialNodes(false),
		)
	disabledResult, err := disabledConfig.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert disabled cluster config to protobuf: %v", err)
	}
	assert.False(t, disabledResult.RefreshTopologyFromInitialNodes)
}

func TestTlsConfiguration_WithRootCertificates(t *testing.T) {
	// Test with valid certificate data
	certData := []byte(testCertData1)
	tlsConfig := NewTlsConfiguration().WithRootCertificates(certData)

	assert.NotNil(t, tlsConfig)
	assert.Equal(t, certData, tlsConfig.RootCertificates)
}

func TestTlsConfiguration_DefaultUsesNil(t *testing.T) {
	// Test that default TLS config has nil certificates (uses platform verifier)
	tlsConfig := NewTlsConfiguration()

	assert.NotNil(t, tlsConfig)
	assert.Nil(t, tlsConfig.RootCertificates)
}

func TestStandaloneConfig_WithTlsRootCertificates(t *testing.T) {
	// Test standalone client with custom root certificates
	certData := []byte(testCertData1)
	tlsConfig := NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.NotNil(t, result.RootCerts)
	assert.Equal(t, 1, len(result.RootCerts))
	assert.Equal(t, certData, result.RootCerts[0])
}

func TestClusterConfig_WithTlsRootCertificates(t *testing.T) {
	// Test cluster client with custom root certificates
	certData := []byte(testCertData1)
	tlsConfig := NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.NotNil(t, result.RootCerts)
	assert.Equal(t, 1, len(result.RootCerts))
	assert.Equal(t, certData, result.RootCerts[0])
}

func TestStandaloneConfig_TlsWithNilCertificates(t *testing.T) {
	// Test that nil certificates (default) uses platform verifier
	tlsConfig := NewTlsConfiguration() // RootCertificates is nil by default
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	// RootCerts should not be set when using platform verifier
	assert.Nil(t, result.RootCerts)
}

func TestClusterConfig_TlsWithNilCertificates(t *testing.T) {
	// Test that nil certificates (default) uses platform verifier
	tlsConfig := NewTlsConfiguration() // RootCertificates is nil by default
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	// RootCerts should not be set when using platform verifier
	assert.Nil(t, result.RootCerts)
}

func TestStandaloneConfig_TlsWithEmptyCertificates(t *testing.T) {
	// Test that empty byte array (non-nil but length 0) returns an error
	emptyCerts := []byte{}
	tlsConfig := NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "root certificates cannot be an empty byte array")
}

func TestClusterConfig_TlsWithEmptyCertificates(t *testing.T) {
	// Test that empty byte array (non-nil but length 0) returns an error
	emptyCerts := []byte{}
	tlsConfig := NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "root certificates cannot be an empty byte array")
}

func TestStandaloneConfig_TlsWithoutAdvancedConfig(t *testing.T) {
	// Test that TLS works without advanced config (uses platform verifier)
	config := NewClientConfiguration().WithUseTLS(true)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.Nil(t, result.RootCerts)
}

func TestClusterConfig_TlsWithoutAdvancedConfig(t *testing.T) {
	// Test that TLS works without advanced config (uses platform verifier)
	config := NewClusterClientConfiguration().WithUseTLS(true)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.Nil(t, result.RootCerts)
}

func TestStandaloneConfig_TlsWithMultipleCertificates(t *testing.T) {
	// Test with multiple certificates in PEM format (concatenated)
	multiCertData := []byte(testCertData2 + testCertData3)

	tlsConfig := NewTlsConfiguration().WithRootCertificates(multiCertData)
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.NotNil(t, result.RootCerts)
	assert.Equal(t, 1, len(result.RootCerts))
	assert.Equal(t, multiCertData, result.RootCerts[0])
}

func TestClusterConfig_TlsWithMultipleCertificates(t *testing.T) {
	// Test with multiple certificates in PEM format (concatenated)
	multiCertData := []byte(testCertData2 + testCertData3)

	tlsConfig := NewTlsConfiguration().WithRootCertificates(multiCertData)
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, protobuf.TlsMode_SecureTls, result.TlsMode)
	assert.NotNil(t, result.RootCerts)
	assert.Equal(t, 1, len(result.RootCerts))
	assert.Equal(t, multiCertData, result.RootCerts[0])
}

func TestConfig_TlsBackwardCompatibility(t *testing.T) {
	// Test that existing TLS configurations without custom certificates still work
	// This ensures backward compatibility

	// Standalone client
	standaloneConfig := NewClientConfiguration().WithUseTLS(true)
	standaloneResult, err := standaloneConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.TlsMode_SecureTls, standaloneResult.TlsMode)
	assert.Nil(t, standaloneResult.RootCerts) // Should use platform verifier

	// Cluster client
	clusterConfig := NewClusterClientConfiguration().WithUseTLS(true)
	clusterResult, err := clusterConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.TlsMode_SecureTls, clusterResult.TlsMode)
	assert.Nil(t, clusterResult.RootCerts) // Should use platform verifier
}

// ============================================================================
// Insecure TLS Tests
// ============================================================================

func TestTlsConfiguration_WithInsecureTLS(t *testing.T) {
	// Test enabling insecure TLS
	tlsConfig := NewTlsConfiguration().WithInsecureTLS(true)

	assert.NotNil(t, tlsConfig)
	assert.True(t, tlsConfig.UseInsecureTLS)

	// Test disabling insecure TLS
	tlsConfig = NewTlsConfiguration().WithInsecureTLS(false)
	assert.False(t, tlsConfig.UseInsecureTLS)
}

func TestStandaloneConfig_WithInsecureTLS(t *testing.T) {
	// Test standalone client with insecure TLS
	tlsConfig := NewTlsConfiguration().WithInsecureTLS(true)
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request)
	assert.Equal(t, protobuf.TlsMode_InsecureTls, request.TlsMode)
}

func TestClusterConfig_WithInsecureTLS(t *testing.T) {
	// Test cluster client with insecure TLS
	tlsConfig := NewTlsConfiguration().WithInsecureTLS(true)
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request)
	assert.Equal(t, protobuf.TlsMode_InsecureTls, request.TlsMode)
}

func TestStandaloneConfig_InsecureTLSWithoutTLS(t *testing.T) {
	// Test that insecure TLS fails when TLS is disabled
	tlsConfig := NewTlsConfiguration().WithInsecureTLS(true)
	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(false). // TLS disabled
		WithAdvancedConfiguration(advancedConfig)

	request, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Nil(t, request)
	assert.Contains(t, err.Error(), "UseInsecureTLS cannot be enabled when UseTLS is disabled")
}

func TestClusterConfig_InsecureTLSWithoutTLS(t *testing.T) {
	// Test that insecure TLS fails when TLS is disabled
	tlsConfig := NewTlsConfiguration().WithInsecureTLS(true)
	advancedConfig := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClusterClientConfiguration().
		WithUseTLS(false). // TLS disabled
		WithAdvancedConfiguration(advancedConfig)

	request, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Nil(t, request)
	assert.Contains(t, err.Error(), "UseInsecureTLS cannot be enabled when UseTLS is disabled")
}

func TestTlsConfiguration_InsecureTLSWithRootCertificates(t *testing.T) {
	// Test that insecure TLS can be combined with custom root certificates
	certData := []byte(testCertData1)
	tlsConfig := NewTlsConfiguration().
		WithRootCertificates(certData).
		WithInsecureTLS(true)

	advancedConfig := NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request)
	assert.Equal(t, protobuf.TlsMode_InsecureTls, request.TlsMode)
	assert.Len(t, request.RootCerts, 1)
	assert.Equal(t, certData, request.RootCerts[0])
}

func TestStandaloneConfig_InsecureTLSFluentAPI(t *testing.T) {
	// Test fluent API chaining with insecure TLS
	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(
			NewAdvancedClientConfiguration().
				WithTlsConfiguration(
					NewTlsConfiguration().WithInsecureTLS(true),
				),
		)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.TlsMode_InsecureTls, request.TlsMode)
}

func TestClusterConfig_InsecureTLSFluentAPI(t *testing.T) {
	// Test fluent API chaining with insecure TLS for cluster
	config := NewClusterClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithTlsConfiguration(
					NewTlsConfiguration().WithInsecureTLS(true),
				),
		)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.TlsMode_InsecureTls, request.TlsMode)
}

func TestTlsConfiguration_InsecureTLSDefaultValue(t *testing.T) {
	// Test that insecure TLS defaults to false
	tlsConfig := NewTlsConfiguration()
	assert.False(t, tlsConfig.UseInsecureTLS)

	// Verify it results in SecureTls mode when TLS is enabled
	config := NewClientConfiguration().
		WithUseTLS(true).
		WithAdvancedConfiguration(
			NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig),
		)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.TlsMode_SecureTls, request.TlsMode)
}

// ============================================================================
// Mutual TLS (mTLS) Client Certificate / Key Tests
// ============================================================================

const (
	testClientCertData = "-----BEGIN CERTIFICATE-----\nMIICclient...\n-----END CERTIFICATE-----"
	testClientKeyData  = "-----BEGIN PRIVATE KEY-----\nMIIEvkey...\n-----END PRIVATE KEY-----"
	testClientCertPath = "/etc/glide/tls/client-cert.pem"
	testClientKeyPath  = "/etc/glide/tls/client-key.pem"
)

// TestTlsConfiguration_WithMutualTLS_TableDriven covers WithMutualTLS (bytes
// mode) and WithMutualTLSFromFiles (path mode, with optional WithReloadInterval)
// across happy paths and every validation error. New failure modes go in as
// a new table row.
func TestTlsConfiguration_WithMutualTLS_TableDriven(t *testing.T) {
	type kind int
	const (
		kindBytes kind = iota
		kindFiles
	)

	type row struct {
		name          string
		kind          kind
		certBytes     []byte
		keyBytes      []byte
		certPath      string
		keyPath       string
		opts          []MutualTLSOption
		wantErrSubstr string // empty means expect success
	}

	rows := []row{
		{
			name:      "bytes/happy",
			kind:      kindBytes,
			certBytes: []byte(testClientCertData),
			keyBytes:  []byte(testClientKeyData),
		},
		{
			name:          "bytes/nil-cert",
			kind:          kindBytes,
			certBytes:     nil,
			keyBytes:      []byte(testClientKeyData),
			wantErrSubstr: "WithMutualTLS: clientCert must be non-empty",
		},
		{
			name:          "bytes/nil-key",
			kind:          kindBytes,
			certBytes:     []byte(testClientCertData),
			keyBytes:      nil,
			wantErrSubstr: "WithMutualTLS: clientKey must be non-empty",
		},
		{
			name:          "bytes/empty-cert",
			kind:          kindBytes,
			certBytes:     []byte{},
			keyBytes:      []byte(testClientKeyData),
			wantErrSubstr: "WithMutualTLS: clientCert must be non-empty",
		},
		{
			name:          "bytes/empty-key",
			kind:          kindBytes,
			certBytes:     []byte(testClientCertData),
			keyBytes:      []byte{},
			wantErrSubstr: "WithMutualTLS: clientKey must be non-empty",
		},
		{
			name:     "files/happy-default-interval",
			kind:     kindFiles,
			certPath: testClientCertPath,
			keyPath:  testClientKeyPath,
		},
		{
			name:     "files/happy-explicit-interval",
			kind:     kindFiles,
			certPath: testClientCertPath,
			keyPath:  testClientKeyPath,
			opts:     []MutualTLSOption{WithReloadInterval(60)},
		},
		{
			name:          "files/empty-cert-path",
			kind:          kindFiles,
			certPath:      "",
			keyPath:       testClientKeyPath,
			wantErrSubstr: "WithMutualTLSFromFiles: certPath must be non-empty",
		},
		{
			name:          "files/empty-key-path",
			kind:          kindFiles,
			certPath:      testClientCertPath,
			keyPath:       "",
			wantErrSubstr: "WithMutualTLSFromFiles: keyPath must be non-empty",
		},
		{
			name:          "files/zero-interval-rejected",
			kind:          kindFiles,
			certPath:      testClientCertPath,
			keyPath:       testClientKeyPath,
			opts:          []MutualTLSOption{WithReloadInterval(0)},
			wantErrSubstr: "WithMutualTLSFromFiles: reload interval must be positive",
		},
		{
			// The largest interval that fits in a uint32.
			name:     "files/max-interval-accepted",
			kind:     kindFiles,
			certPath: testClientCertPath,
			keyPath:  testClientKeyPath,
			opts: []MutualTLSOption{
				WithReloadInterval(MaxUint32),
			},
		},
	}

	for _, tc := range rows {
		t.Run(tc.name, func(t *testing.T) {
			base := NewTlsConfiguration()
			var (
				got *TlsConfiguration
				err error
			)
			switch tc.kind {
			case kindBytes:
				got, err = base.WithMutualTLS(tc.certBytes, tc.keyBytes)
			case kindFiles:
				got, err = base.WithMutualTLSFromFiles(tc.certPath, tc.keyPath, tc.opts...)
			}

			if tc.wantErrSubstr != "" {
				require.Error(t, err)
				assert.Contains(t, err.Error(), tc.wantErrSubstr)
				return
			}

			require.NoError(t, err)
			require.NotNil(t, got)
			switch tc.kind {
			case kindBytes:
				assert.Equal(t, tc.certBytes, got.clientCertificate)
				assert.Equal(t, tc.keyBytes, got.clientKey)
				assert.Empty(t, got.clientCertPath)
				assert.Empty(t, got.clientKeyPath)
				assert.Equal(t, uint32(0), got.certReloadInterval)
			case kindFiles:
				assert.Nil(t, got.clientCertificate)
				assert.Nil(t, got.clientKey)
				assert.Equal(t, tc.certPath, got.clientCertPath)
				assert.Equal(t, tc.keyPath, got.clientKeyPath)
			}
		})
	}
}

// TestTlsConfiguration_WithMutualTLS_ModeReplacement checks that calling one
// mTLS builder clears whatever the other left behind. Bytes to paths must
// clear the byte fields and enable cert reload; paths to bytes must clear
// the path fields and drop the reload block. Both the in-memory state and
// the ToProtobuf output are checked.
func TestTlsConfiguration_WithMutualTLS_ModeReplacement(t *testing.T) {
	byteCert := []byte(testClientCertData)
	byteKey := []byte(testClientKeyData)

	t.Run("bytes-to-files", func(t *testing.T) {
		tls, err := NewTlsConfiguration().WithMutualTLS(byteCert, byteKey)
		require.NoError(t, err)
		tls, err = tls.WithMutualTLSFromFiles(testClientCertPath, testClientKeyPath)
		require.NoError(t, err)

		assert.Nil(t, tls.clientCertificate)
		assert.Nil(t, tls.clientKey)
		assert.Equal(t, testClientCertPath, tls.clientCertPath)
		assert.Equal(t, testClientKeyPath, tls.clientKeyPath)

		adv := NewAdvancedClientConfiguration().WithTlsConfiguration(tls)
		req, err := NewClientConfiguration().WithUseTLS(true).WithAdvancedConfiguration(adv).ToProtobuf()
		require.NoError(t, err)
		assert.Nil(t, req.ClientCert)
		assert.Nil(t, req.ClientKey)
		assert.Equal(t, testClientCertPath, req.GetClientCertPath())
		assert.Equal(t, testClientKeyPath, req.GetClientKeyPath())
		require.NotNil(t, req.CertReload)
		assert.True(t, req.CertReload.GetEnabled())
	})

	t.Run("files-to-bytes", func(t *testing.T) {
		tls, err := NewTlsConfiguration().WithMutualTLSFromFiles(
			testClientCertPath, testClientKeyPath, WithReloadInterval(60))
		require.NoError(t, err)
		tls, err = tls.WithMutualTLS(byteCert, byteKey)
		require.NoError(t, err)

		assert.Equal(t, byteCert, tls.clientCertificate)
		assert.Equal(t, byteKey, tls.clientKey)
		assert.Empty(t, tls.clientCertPath)
		assert.Empty(t, tls.clientKeyPath)
		assert.Equal(t, uint32(0), tls.certReloadInterval)

		adv := NewAdvancedClientConfiguration().WithTlsConfiguration(tls)
		req, err := NewClientConfiguration().WithUseTLS(true).WithAdvancedConfiguration(adv).ToProtobuf()
		require.NoError(t, err)
		assert.Equal(t, byteCert, req.ClientCert)
		assert.Equal(t, byteKey, req.ClientKey)
		assert.Nil(t, req.ClientCertPath)
		assert.Nil(t, req.ClientKeyPath)
		assert.Nil(t, req.CertReload)
	})
}

// TestTlsConfiguration_WireSnapshot_TableDriven checks that ToProtobuf emits
// the right protobuf fields for every mTLS mode, run against both standalone and
// cluster. The two topologies share the same cases so any divergence shows
// up as one row failing on cluster only.
func TestTlsConfiguration_WireSnapshot_TableDriven(t *testing.T) {
	type buildFn func() *TlsConfiguration
	mustBytes := func(cert, key []byte) buildFn {
		return func() *TlsConfiguration {
			tls, err := NewTlsConfiguration().WithMutualTLS(cert, key)
			require.NoError(t, err)
			return tls
		}
	}
	mustFromFiles := func(certPath, keyPath string, opts ...MutualTLSOption) buildFn {
		return func() *TlsConfiguration {
			tls, err := NewTlsConfiguration().WithMutualTLSFromFiles(certPath, keyPath, opts...)
			require.NoError(t, err)
			return tls
		}
	}
	rootOnly := func() *TlsConfiguration {
		return NewTlsConfiguration().WithRootCertificates([]byte(testCertData1))
	}
	fresh := NewTlsConfiguration // no mTLS

	type wantWire struct {
		clientCert          []byte
		clientKey           []byte
		clientCertPath      string
		clientKeyPath       string
		certReloadEnabled   bool
		certReloadHasIntSec bool
		certReloadIntSec    uint32
		certReloadNil       bool // true if req.CertReload should be nil
	}

	byteCert := []byte(testClientCertData)
	byteKey := []byte(testClientKeyData)

	rows := []struct {
		name  string
		build buildFn
		want  wantWire
	}{
		{
			name:  "no-mtls",
			build: func() *TlsConfiguration { return fresh() },
			want:  wantWire{certReloadNil: true},
		},
		{
			name:  "no-mtls-with-root-certs",
			build: rootOnly,
			want:  wantWire{certReloadNil: true},
		},
		{
			name:  "bytes",
			build: mustBytes(byteCert, byteKey),
			want: wantWire{
				clientCert:    byteCert,
				clientKey:     byteKey,
				certReloadNil: true,
			},
		},
		{
			name:  "paths-default-interval",
			build: mustFromFiles(testClientCertPath, testClientKeyPath),
			want: wantWire{
				clientCertPath:    testClientCertPath,
				clientKeyPath:     testClientKeyPath,
				certReloadEnabled: true,
			},
		},
		{
			name:  "paths-explicit-interval",
			build: mustFromFiles(testClientCertPath, testClientKeyPath, WithReloadInterval(60)),
			want: wantWire{
				clientCertPath:      testClientCertPath,
				clientKeyPath:       testClientKeyPath,
				certReloadEnabled:   true,
				certReloadHasIntSec: true,
				certReloadIntSec:    60,
			},
		},
	}

	topologies := []struct {
		name  string
		toReq func(tls *TlsConfiguration) (*protobuf.ConnectionRequest, error)
	}{
		{
			name: "standalone",
			toReq: func(tls *TlsConfiguration) (*protobuf.ConnectionRequest, error) {
				adv := NewAdvancedClientConfiguration().WithTlsConfiguration(tls)
				return NewClientConfiguration().WithUseTLS(true).WithAdvancedConfiguration(adv).ToProtobuf()
			},
		},
		{
			name: "cluster",
			toReq: func(tls *TlsConfiguration) (*protobuf.ConnectionRequest, error) {
				adv := NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tls)
				return NewClusterClientConfiguration().WithUseTLS(true).WithAdvancedConfiguration(adv).ToProtobuf()
			},
		},
	}

	for _, tc := range rows {
		for _, topo := range topologies {
			t.Run(tc.name+"/"+topo.name, func(t *testing.T) {
				req, err := topo.toReq(tc.build())
				require.NoError(t, err)

				if len(tc.want.clientCert) == 0 {
					assert.Nil(t, req.ClientCert)
				} else {
					assert.Equal(t, tc.want.clientCert, req.ClientCert)
				}
				if len(tc.want.clientKey) == 0 {
					assert.Nil(t, req.ClientKey)
				} else {
					assert.Equal(t, tc.want.clientKey, req.ClientKey)
				}
				if tc.want.clientCertPath == "" {
					assert.Nil(t, req.ClientCertPath)
				} else {
					assert.Equal(t, tc.want.clientCertPath, req.GetClientCertPath())
				}
				if tc.want.clientKeyPath == "" {
					assert.Nil(t, req.ClientKeyPath)
				} else {
					assert.Equal(t, tc.want.clientKeyPath, req.GetClientKeyPath())
				}

				if tc.want.certReloadNil {
					assert.Nil(t, req.CertReload)
					return
				}
				require.NotNil(t, req.CertReload)
				assert.Equal(t, tc.want.certReloadEnabled, req.CertReload.GetEnabled())
				if tc.want.certReloadHasIntSec {
					require.NotNil(t, req.CertReload.IntervalSeconds)
					assert.Equal(t, tc.want.certReloadIntSec, req.CertReload.GetIntervalSeconds())
				} else {
					assert.Nil(t, req.CertReload.IntervalSeconds)
				}
			})
		}
	}
}

// TestLoadClientCertificateAndKeyFromFile_TableDriven covers the loader's
// happy path and every failure mode (missing/empty for each of cert and key).
func TestLoadClientCertificateAndKeyFromFile_TableDriven(t *testing.T) {
	type fileState int
	const (
		fileOK fileState = iota
		fileMissing
		fileEmpty
	)

	rows := []struct {
		name          string
		certState     fileState
		keyState      fileState
		wantErrSubstr string
	}{
		{name: "happy", certState: fileOK, keyState: fileOK},
		{
			name:          "missing-cert",
			certState:     fileMissing,
			keyState:      fileOK,
			wantErrSubstr: "failed to read client certificate file",
		},
		{name: "missing-key", certState: fileOK, keyState: fileMissing, wantErrSubstr: "failed to read client key file"},
		{name: "empty-cert", certState: fileEmpty, keyState: fileOK, wantErrSubstr: "client certificate file is empty"},
		{name: "empty-key", certState: fileOK, keyState: fileEmpty, wantErrSubstr: "client key file is empty"},
	}

	makePath := func(t *testing.T, dir, name string, state fileState, data []byte) string {
		t.Helper()
		p := filepath.Join(dir, name)
		switch state {
		case fileOK:
			require.NoError(t, os.WriteFile(p, data, 0o600))
		case fileEmpty:
			require.NoError(t, os.WriteFile(p, []byte{}, 0o600))
		case fileMissing:
			// leave path unwritten
		}
		return p
	}

	for _, tc := range rows {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			certPath := makePath(t, dir, "cert.pem", tc.certState, []byte(testClientCertData))
			keyPath := makePath(t, dir, "key.pem", tc.keyState, []byte(testClientKeyData))

			cert, key, err := LoadClientCertificateAndKeyFromFile(certPath, keyPath)
			if tc.wantErrSubstr != "" {
				require.Error(t, err)
				assert.Nil(t, cert)
				assert.Nil(t, key)
				assert.Contains(t, err.Error(), tc.wantErrSubstr)
				return
			}
			require.NoError(t, err)
			assert.Equal(t, []byte(testClientCertData), cert)
			assert.Equal(t, []byte(testClientKeyData), key)
		})
	}
}

func TestStandaloneConfig_TcpNoDelay(t *testing.T) {
	// Test TCP_NODELAY enabled
	tcpNoDelayTrue := true
	config := NewClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClientConfiguration().WithTcpNoDelay(tcpNoDelayTrue),
		)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request.TcpNodelay)
	assert.True(t, *request.TcpNodelay)

	// Test TCP_NODELAY disabled
	tcpNoDelayFalse := false
	config = NewClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClientConfiguration().WithTcpNoDelay(tcpNoDelayFalse),
		)

	request, err = config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request.TcpNodelay)
	assert.False(t, *request.TcpNodelay)

	// Test TCP_NODELAY not set (default)
	config = NewClientConfiguration()
	request, err = config.ToProtobuf()
	assert.NoError(t, err)
	assert.Nil(t, request.TcpNodelay)
}

func TestClusterConfig_TcpNoDelay(t *testing.T) {
	// Test TCP_NODELAY enabled
	tcpNoDelayTrue := true
	config := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().WithTcpNoDelay(tcpNoDelayTrue),
		)

	request, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request.TcpNodelay)
	assert.True(t, *request.TcpNodelay)

	// Test TCP_NODELAY disabled
	tcpNoDelayFalse := false
	config = NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().WithTcpNoDelay(tcpNoDelayFalse),
		)

	request, err = config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, request.TcpNodelay)
	assert.False(t, *request.TcpNodelay)

	// Test TCP_NODELAY not set (default)
	config = NewClusterClientConfiguration()
	request, err = config.ToProtobuf()
	assert.NoError(t, err)
	assert.Nil(t, request.TcpNodelay)
}

// ============================================================================
// Compression Configuration Tests
// ============================================================================

func TestCompressionConfiguration_Default(t *testing.T) {
	compressionConfig := NewCompressionConfiguration()

	assert.NoError(t, compressionConfig.Validate())

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_ZSTD, pb.Backend)
	assert.Nil(t, pb.CompressionLevel)
	assert.Equal(t, uint32(64), pb.MinCompressionSize)
}

func TestCompressionConfiguration_WithLZ4Backend(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithBackend(LZ4)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_LZ4, pb.Backend)
}

func TestCompressionConfiguration_WithCompressionLevel(t *testing.T) {
	var level int32 = 10
	compressionConfig := NewCompressionConfiguration().
		WithCompressionLevel(level)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, pb.CompressionLevel)
	assert.Equal(t, level, *pb.CompressionLevel)
}

func TestCompressionConfiguration_WithMinCompressionSize(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(128)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(128), pb.MinCompressionSize)
}

func TestCompressionConfiguration_AllFieldsSet(t *testing.T) {
	var level int32 = 5
	compressionConfig := NewCompressionConfiguration().
		WithBackend(ZSTD).
		WithCompressionLevel(level).
		WithMinCompressionSize(256)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_ZSTD, pb.Backend)
	assert.NotNil(t, pb.CompressionLevel)
	assert.Equal(t, level, *pb.CompressionLevel)
	assert.Equal(t, uint32(256), pb.MinCompressionSize)
}

func TestCompressionConfiguration_ValidationMinSizeTooSmall(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(MinCompressionSize - 1)

	err := compressionConfig.Validate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), fmt.Sprintf("min_compression_size must be at least %d bytes", MinCompressionSize))
}

func TestCompressionConfiguration_ValidationMinSizeZero(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(0)

	err := compressionConfig.Validate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), fmt.Sprintf("min_compression_size must be at least %d bytes", MinCompressionSize))
}

func TestCompressionConfiguration_ValidationMinSizeExact(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(MinCompressionSize)

	err := compressionConfig.Validate()
	assert.NoError(t, err)
}

func TestCompressionConfiguration_ToProtobufFailsOnInvalidConfig(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(MinCompressionSize - 1)

	_, err := compressionConfig.toProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), fmt.Sprintf("min_compression_size must be at least %d bytes", MinCompressionSize))
}

func TestStandaloneConfig_WithCompression(t *testing.T) {
	var level int32 = 3
	compressionConfig := NewCompressionConfiguration().
		WithBackend(ZSTD).
		WithCompressionLevel(level).
		WithMinCompressionSize(128)

	clientConfig := NewClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.CompressionConfig)
	assert.True(t, result.CompressionConfig.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_ZSTD, result.CompressionConfig.Backend)
	assert.NotNil(t, result.CompressionConfig.CompressionLevel)
	assert.Equal(t, level, *result.CompressionConfig.CompressionLevel)
	assert.Equal(t, uint32(128), result.CompressionConfig.MinCompressionSize)
}

func TestClusterConfig_WithCompression(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithBackend(LZ4)

	clientConfig := NewClusterClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.CompressionConfig)
	assert.True(t, result.CompressionConfig.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_LZ4, result.CompressionConfig.Backend)
	assert.Nil(t, result.CompressionConfig.CompressionLevel)
	assert.Equal(t, uint32(64), result.CompressionConfig.MinCompressionSize)
}

func TestStandaloneConfig_WithoutCompression(t *testing.T) {
	clientConfig := NewClientConfiguration()

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Nil(t, result.CompressionConfig)
}

func TestClusterConfig_WithoutCompression(t *testing.T) {
	clientConfig := NewClusterClientConfiguration()

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.Nil(t, result.CompressionConfig)
}

func TestStandaloneConfig_WithInvalidCompression(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(MinCompressionSize - 1)

	clientConfig := NewClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	_, err := clientConfig.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid compression configuration")
}

func TestClusterConfig_WithInvalidCompression(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMinCompressionSize(MinCompressionSize - 1)

	clientConfig := NewClusterClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	_, err := clientConfig.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid compression configuration")
}

func TestCompressionConfiguration_NegativeLevel(t *testing.T) {
	// Negative levels are valid for some backends (e.g., ZSTD supports negative levels)
	var level int32 = -5
	compressionConfig := NewCompressionConfiguration().
		WithCompressionLevel(level)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, pb.CompressionLevel)
	assert.Equal(t, level, *pb.CompressionLevel)
}

func TestCompressionConfiguration_WithEnabledTrue(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithEnabled(true)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
}

func TestCompressionConfiguration_WithEnabledFalse(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithEnabled(false)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.False(t, pb.Enabled)
	// Other fields should still be set
	assert.Equal(t, protobuf.CompressionBackend_ZSTD, pb.Backend)
	assert.Equal(t, uint32(64), pb.MinCompressionSize)
}

// ============================================================================
// Max Decompressed Size Tests
// ============================================================================

func TestCompressionConfiguration_DefaultMaxDecompressedSize(t *testing.T) {
	compressionConfig := NewCompressionConfiguration()

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	// Default is nil, which means "use Rust default" - protobuf field is not set
	assert.Nil(t, pb.MaxDecompressedSize)
}

func TestCompressionConfiguration_WithCustomMaxDecompressedSize(t *testing.T) {
	var customSize uint64 = 100 * 1024 * 1024 // 100MB
	compressionConfig := NewCompressionConfiguration().
		WithMaxDecompressedSize(&customSize)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, pb.MaxDecompressedSize)
	assert.Equal(t, customSize, *pb.MaxDecompressedSize)
}

func TestCompressionConfiguration_WithNilMaxDecompressedSizeUsesRustDefault(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithMaxDecompressedSize(nil)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	// When nil, the protobuf field is not set, Rust will use its default
	assert.Nil(t, pb.MaxDecompressedSize)
}

func TestCompressionConfiguration_WithZeroMaxDecompressedSizeReturnsError(t *testing.T) {
	var zeroSize uint64 = 0
	compressionConfig := NewCompressionConfiguration().
		WithMaxDecompressedSize(&zeroSize)

	// Zero is invalid - must be positive if set
	err := compressionConfig.Validate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "max_decompressed_size must be positive")
}

func TestCompressionConfiguration_AllFieldsSetWithMaxDecompressedSize(t *testing.T) {
	var level int32 = 5
	var maxSize uint64 = 100 * 1024 * 1024 // 100MB
	compressionConfig := NewCompressionConfiguration().
		WithBackend(ZSTD).
		WithCompressionLevel(level).
		WithMinCompressionSize(256).
		WithMaxDecompressedSize(&maxSize)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
	assert.Equal(t, protobuf.CompressionBackend_ZSTD, pb.Backend)
	assert.NotNil(t, pb.CompressionLevel)
	assert.Equal(t, level, *pb.CompressionLevel)
	assert.Equal(t, uint32(256), pb.MinCompressionSize)
	assert.NotNil(t, pb.MaxDecompressedSize)
	assert.Equal(t, maxSize, *pb.MaxDecompressedSize)
}

func TestCompressionConfiguration_WithEnabledToggle(t *testing.T) {
	// Start enabled, disable, re-enable
	compressionConfig := NewCompressionConfiguration().
		WithEnabled(false).
		WithEnabled(true)

	pb, err := compressionConfig.toProtobuf()
	assert.NoError(t, err)
	assert.True(t, pb.Enabled)
}

func TestStandaloneConfig_WithDisabledCompression(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithEnabled(false)

	clientConfig := NewClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.CompressionConfig)
	assert.False(t, result.CompressionConfig.Enabled)
}

func TestClusterConfig_WithDisabledCompression(t *testing.T) {
	compressionConfig := NewCompressionConfiguration().
		WithEnabled(false)

	clientConfig := NewClusterClientConfiguration().
		WithCompressionConfiguration(compressionConfig)

	result, err := clientConfig.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.CompressionConfig)
	assert.False(t, result.CompressionConfig.Enabled)
}

func TestConfig_AllFieldsSetWithCompression(t *testing.T) {
	// Test that compression config works alongside all other config fields
	username := "username"
	password := "password"
	timeout := 3 * time.Second
	clientName := "client name"
	retries, factor, base := 5, 10, 50
	databaseId := 1
	var level int32 = 3

	compressionConfig := NewCompressionConfiguration().
		WithBackend(ZSTD).
		WithCompressionLevel(level).
		WithMinCompressionSize(128)

	config := NewClientConfiguration().
		WithUseTLS(true).
		WithReadFrom(PreferReplica).
		WithCredentials(NewServerCredentials(username, password)).
		WithRequestTimeout(timeout).
		WithClientName(clientName).
		WithReconnectStrategy(NewBackoffStrategy(retries, factor, base)).
		WithDatabaseId(databaseId).
		WithCompressionConfiguration(compressionConfig)

	expected := &protobuf.ConnectionRequest{
		LibName:            "GlideGo",
		TlsMode:            protobuf.TlsMode_SecureTls,
		ReadFrom:           protobuf.ReadFrom_PreferReplica,
		ClusterModeEnabled: false,
		AuthenticationInfo: &protobuf.AuthenticationInfo{Username: username, Password: password},
		RequestTimeout:     uint32(timeout.Milliseconds()),
		ClientName:         clientName,
		ConnectionRetryStrategy: &protobuf.ConnectionRetryStrategy{
			NumberOfRetries: uint32(retries),
			Factor:          uint32(factor),
			ExponentBase:    uint32(base),
		},
		DatabaseId: uint32(databaseId),
		CompressionConfig: &protobuf.CompressionConfig{
			Enabled:            true,
			Backend:            protobuf.CompressionBackend_ZSTD,
			CompressionLevel:   &level,
			MinCompressionSize: 128,
		},
	}

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, expected, result)
}

// ============================================================================
// Read-Only Mode Tests
// ============================================================================

func TestConfig_ReadOnly(t *testing.T) {
	// Test standalone client with read_only enabled
	config := NewClientConfiguration().WithReadOnly(true)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.ReadOnly)
	assert.True(t, *result.ReadOnly)
}

func TestConfig_ReadOnly_DefaultsFalse(t *testing.T) {
	// Test that read_only defaults to false (not set in protobuf)
	config := NewClientConfiguration()

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	// When readOnly is false (default), it should not be set in the protobuf
	assert.Nil(t, result.ReadOnly)
}

func TestConfig_ReadOnly_ExplicitFalse(t *testing.T) {
	// Test that explicitly setting read_only to false doesn't set it in protobuf
	config := NewClientConfiguration().WithReadOnly(false)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	// When readOnly is explicitly false, it should not be set in the protobuf
	assert.Nil(t, result.ReadOnly)
}

func TestConfig_ReadOnly_RejectsAzAffinity(t *testing.T) {
	// Test that read_only with AZ_AFFINITY returns an error
	config := NewClientConfiguration().
		WithReadOnly(true).
		WithReadFrom(AzAffinity).
		WithClientAZ("us-east-1a")

	_, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "read-only mode is not compatible with AZAffinity")
}

func TestConfig_ReadOnly_RejectsAzAffinityReplicasAndPrimary(t *testing.T) {
	// Test that read_only with AZ_AFFINITY_REPLICAS_AND_PRIMARY returns an error
	config := NewClientConfiguration().
		WithReadOnly(true).
		WithReadFrom(AzAffinityReplicaAndPrimary).
		WithClientAZ("us-east-1a")

	_, err := config.ToProtobuf()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "read-only mode is not compatible with AZAffinity")
}

func TestConfig_ReadOnly_AcceptsPreferReplica(t *testing.T) {
	// Test that read_only with PreferReplica is valid
	config := NewClientConfiguration().
		WithReadOnly(true).
		WithReadFrom(PreferReplica)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.ReadOnly)
	assert.True(t, *result.ReadOnly)
	assert.Equal(t, protobuf.ReadFrom_PreferReplica, result.ReadFrom)
}

func TestConfig_ReadOnly_AcceptsPrimary(t *testing.T) {
	// Test that read_only with Primary ReadFrom is valid
	// (reads will go to connected nodes treated as replicas)
	config := NewClientConfiguration().
		WithReadOnly(true).
		WithReadFrom(Primary)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.ReadOnly)
	assert.True(t, *result.ReadOnly)
	assert.Equal(t, protobuf.ReadFrom_Primary, result.ReadFrom)
}

func TestConfig_ReadOnly_WithOtherOptions(t *testing.T) {
	// Test that read_only works alongside other configuration options
	username := "username"
	password := "password"
	timeout := 3 * time.Second
	clientName := "client name"
	databaseId := 1

	config := NewClientConfiguration().
		WithReadOnly(true).
		WithReadFrom(PreferReplica).
		WithCredentials(NewServerCredentials(username, password)).
		WithRequestTimeout(timeout).
		WithClientName(clientName).
		WithDatabaseId(databaseId)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.ReadOnly)
	assert.True(t, *result.ReadOnly)
	assert.Equal(t, protobuf.ReadFrom_PreferReplica, result.ReadFrom)
	assert.Equal(t, username, result.AuthenticationInfo.Username)
	assert.Equal(t, password, result.AuthenticationInfo.Password)
	assert.Equal(t, uint32(timeout.Milliseconds()), result.RequestTimeout)
	assert.Equal(t, clientName, result.ClientName)
	assert.Equal(t, uint32(databaseId), result.DatabaseId)
}

func TestConfig_ReadOnly_FluentAPI(t *testing.T) {
	// Test fluent API chaining with read-only mode
	config := NewClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379}).
		WithReadOnly(true).
		WithReadFrom(PreferReplica)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.ReadOnly)
	assert.True(t, *result.ReadOnly)
	assert.Equal(t, 1, len(result.Addresses))
	assert.Equal(t, "localhost", result.Addresses[0].Host)
	assert.Equal(t, uint32(6379), result.Addresses[0].Port)
}

// ============================================================================
// NodeDiscoveryMode Tests
// ============================================================================

func TestConfig_NodeDiscoveryMode_Default(t *testing.T) {
	config := NewClientConfiguration()
	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	// Default is STANDARD (0), which is the zero value
	assert.Equal(t, protobuf.NodeDiscoveryMode_Standard, result.NodeDiscoveryMode)
}

func TestConfig_NodeDiscoveryMode_Static(t *testing.T) {
	config := NewClientConfiguration().
		WithNodeDiscoveryMode(NodeDiscoveryModeStatic)
	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.NodeDiscoveryMode_Static, result.NodeDiscoveryMode)
}

func TestConfig_NodeDiscoveryMode_DiscoverAll(t *testing.T) {
	config := NewClientConfiguration().
		WithNodeDiscoveryMode(NodeDiscoveryModeDiscoverAll)
	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, protobuf.NodeDiscoveryMode_DiscoverAll, result.NodeDiscoveryMode)
}

func TestClusterConfig_PeriodicChecks_DefaultNotSet(t *testing.T) {
	// When no periodic checks config is set, the protobuf field should be nil (server uses default).
	config := NewClusterClientConfiguration()
	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert default cluster config to protobuf: %v", err)
	}
	assert.Nil(t, result.PeriodicChecks)
}

func TestClusterConfig_PeriodicChecks_Enabled(t *testing.T) {
	// Explicitly setting PeriodicChecksEnabled should not set any protobuf field (default behavior).
	config := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithPeriodicChecks(PeriodicChecksEnabled{}),
		)
	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert cluster config to protobuf: %v", err)
	}
	assert.Nil(t, result.PeriodicChecks)
}

func TestClusterConfig_PeriodicChecks_Disabled(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithPeriodicChecks(PeriodicChecksDisabled{}),
		)
	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert cluster config to protobuf: %v", err)
	}
	assert.NotNil(t, result.PeriodicChecks)
	_, ok := result.PeriodicChecks.(*protobuf.ConnectionRequest_PeriodicChecksDisabled)
	assert.True(t, ok, "expected PeriodicChecksDisabled protobuf type")
}

func TestClusterConfig_PeriodicChecks_ManualInterval(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAdvancedConfiguration(
			NewAdvancedClusterClientConfiguration().
				WithPeriodicChecks(PeriodicChecksManualInterval{DurationInSec: 30}),
		)
	result, err := config.ToProtobuf()
	if err != nil {
		t.Fatalf("Failed to convert cluster config to protobuf: %v", err)
	}
	assert.NotNil(t, result.PeriodicChecks)
	manual, ok := result.PeriodicChecks.(*protobuf.ConnectionRequest_PeriodicChecksManualInterval)
	assert.True(t, ok, "expected PeriodicChecksManualInterval protobuf type")
	assert.Equal(t, uint32(30), manual.PeriodicChecksManualInterval.DurationInSec)
}

func TestNewClientSideCache_ServerAssistedDefault(t *testing.T) {
	cache, err := NewClientSideCache(1024, 60000)
	assert.NoError(t, err)
	assert.False(t, cache.ServerAssisted)
}

func TestNewClientSideCache_ServerAssistedEnabled(t *testing.T) {
	cache, err := NewClientSideCache(1024, 60000)
	assert.NoError(t, err)
	cache.WithServerAssisted(true)
	assert.True(t, cache.ServerAssisted)
}

func TestNewClientSideCache_ServerAssistedExplicitlyDisabled(t *testing.T) {
	cache, err := NewClientSideCache(1024, 60000)
	assert.NoError(t, err)
	cache.WithServerAssisted(false)
	assert.False(t, cache.ServerAssisted)
}

func TestClientConfiguration_WithInflightRequestsLimit(t *testing.T) {
	config := NewClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379}).
		WithInflightRequestsLimit(5000)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(5000), result.InflightRequestsLimit)
}

func TestClientConfiguration_WithInflightRequestsLimit_notSet(t *testing.T) {
	config := NewClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379})

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(0), result.InflightRequestsLimit)
}

func TestClusterClientConfiguration_WithInflightRequestsLimit(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379}).
		WithInflightRequestsLimit(2000)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(2000), result.InflightRequestsLimit)
}

func TestClusterClientConfiguration_WithRecoveryRequestsQueueSize(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379}).
		WithRecoveryRequestsQueueSize(2000)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Equal(t, uint32(2000), result.GetRecoveryRequestsQueueSize())
}

func TestClusterClientConfiguration_WithRecoveryRequestsQueueSize_notSet(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379})

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.Nil(t, result.RecoveryRequestsQueueSize) // nil when not set
}

func TestClusterClientConfiguration_WithRecoveryRequestsQueueSize_zero_disables_queue(t *testing.T) {
	config := NewClusterClientConfiguration().
		WithAddress(&NodeAddress{Host: "localhost", Port: 6379}).
		WithRecoveryRequestsQueueSize(0)

	result, err := config.ToProtobuf()
	assert.NoError(t, err)
	assert.NotNil(t, result.RecoveryRequestsQueueSize)
	assert.Equal(t, uint32(0), *result.RecoveryRequestsQueueSize) // 0 = disabled
}

// --- LibName and ClientInfoTag tests ---

type resolveLibNameTestCase struct {
	name           string
	libName        string
	clientInfoTag  string
	expected       string
	errorFieldName string
}

func resolveLibNameTestCases() []resolveLibNameTestCase {
	return []resolveLibNameTestCase{
		{name: "default", expected: "GlideGo"},
		{name: "override", libName: "custom-client", expected: "custom-client"},
		{name: "tag", clientInfoTag: "framework:1.2", expected: "GlideGo(framework:1.2)"},
		{
			name: "combined", libName: "custom-client", clientInfoTag: "framework:1.2",
			expected: "custom-client(framework:1.2)",
		},
		{name: "empty override", libName: "", clientInfoTag: "tag", expected: "GlideGo(tag)"},
		{name: "empty tag", libName: "custom-client", clientInfoTag: "", expected: "custom-client"},
		{name: "both empty", libName: "", clientInfoTag: "", expected: "GlideGo"},
		{
			name: "punctuation", libName: "custom/client@2.0", clientInfoTag: "framework:1.2+beta",
			expected: "custom/client@2.0(framework:1.2+beta)",
		},
		{name: "lib name ASCII space", libName: "custom client", errorFieldName: "libName"},
		{name: "lib name tab", libName: "custom\tclient", errorFieldName: "libName"},
		{name: "lib name newline", libName: "custom\nclient", errorFieldName: "libName"},
		{name: "lib name non-ASCII whitespace", libName: "custom\u00a0client", errorFieldName: "libName"},
		{name: "tag ASCII space", clientInfoTag: "client tag", errorFieldName: "clientInfoTag"},
		{name: "tag tab", clientInfoTag: "client\ttag", errorFieldName: "clientInfoTag"},
		{name: "tag newline", clientInfoTag: "client\ntag", errorFieldName: "clientInfoTag"},
		{
			name: "tag non-ASCII whitespace", clientInfoTag: "client\u00a0tag",
			errorFieldName: "clientInfoTag",
		},
	}
}

func TestResolveLibName(t *testing.T) {
	for _, testCase := range resolveLibNameTestCases() {
		t.Run(testCase.name, func(t *testing.T) {
			result, err := resolveLibName(testCase.libName, testCase.clientInfoTag)
			if testCase.errorFieldName != "" {
				require.Error(t, err)
				assert.Contains(t, err.Error(), testCase.errorFieldName+" must not contain whitespace characters")
				assert.Empty(t, result)
				return
			}

			require.NoError(t, err)
			assert.Equal(t, testCase.expected, result)
		})
	}
}

func TestClientConfigurationsResolveLibName(t *testing.T) {
	configurationTypes := []struct {
		name       string
		toProtobuf func(string, string) (*protobuf.ConnectionRequest, error)
	}{
		{
			name: "standalone",
			toProtobuf: func(libName, clientInfoTag string) (*protobuf.ConnectionRequest, error) {
				return NewClientConfiguration().
					WithLibName(libName).
					WithClientInfoTag(clientInfoTag).
					ToProtobuf()
			},
		},
		{
			name: "cluster",
			toProtobuf: func(libName, clientInfoTag string) (*protobuf.ConnectionRequest, error) {
				return NewClusterClientConfiguration().
					WithLibName(libName).
					WithClientInfoTag(clientInfoTag).
					ToProtobuf()
			},
		},
	}

	for _, configurationType := range configurationTypes {
		t.Run(configurationType.name, func(t *testing.T) {
			for _, testCase := range resolveLibNameTestCases() {
				t.Run(testCase.name, func(t *testing.T) {
					request, err := configurationType.toProtobuf(testCase.libName, testCase.clientInfoTag)
					if testCase.errorFieldName != "" {
						require.Error(t, err)
						assert.Contains(t, err.Error(), testCase.errorFieldName+" must not contain whitespace characters")
						assert.Nil(t, request)
						return
					}

					require.NoError(t, err)
					assert.Equal(t, testCase.expected, request.LibName)
				})
			}
		})
	}
}
