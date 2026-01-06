// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

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
