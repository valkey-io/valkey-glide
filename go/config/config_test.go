// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
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

func TestConfig_DatabaseId_Validation(t *testing.T) {
	// Test negative database ID validation for standalone
	standaloneConfig := NewClientConfiguration().WithDatabaseId(-1)
	_, err := standaloneConfig.ToProtobuf()
	assert.EqualError(t, err, "database_id must be non-negative")

	// Test negative database ID validation for cluster
	clusterConfig := NewClusterClientConfiguration().WithDatabaseId(-1)
	_, err = clusterConfig.ToProtobuf()
	assert.EqualError(t, err, "database_id must be non-negative")

	// Test valid database ID (0 should be valid)
	validStandaloneConfig := NewClientConfiguration().WithDatabaseId(0)
	_, err = validStandaloneConfig.ToProtobuf()
	assert.NoError(t, err)

	validClusterConfig := NewClusterClientConfiguration().WithDatabaseId(0)
	_, err = validClusterConfig.ToProtobuf()
	assert.NoError(t, err)
}

func TestConfig_DatabaseId_ExtendedValidation(t *testing.T) {
	// Test various valid database IDs for both standalone and cluster
	validDatabaseIds := []int{0, 1, 15, 50, 100, 999}

	for _, dbId := range validDatabaseIds {
		t.Run(fmt.Sprintf("ValidDatabaseId_%d", dbId), func(t *testing.T) {
			// Test standalone configuration
			standaloneConfig := NewClientConfiguration().WithDatabaseId(dbId)
			standaloneResult, err := standaloneConfig.ToProtobuf()
			assert.NoError(t, err)
			assert.Equal(t, uint32(dbId), standaloneResult.DatabaseId)

			// Test cluster configuration
			clusterConfig := NewClusterClientConfiguration().WithDatabaseId(dbId)
			clusterResult, err := clusterConfig.ToProtobuf()
			assert.NoError(t, err)
			assert.Equal(t, uint32(dbId), clusterResult.DatabaseId)
		})
	}

	// Test invalid database IDs
	invalidDatabaseIds := []int{-1, -10, -100}

	for _, dbId := range invalidDatabaseIds {
		t.Run(fmt.Sprintf("InvalidDatabaseId_%d", dbId), func(t *testing.T) {
			// Test standalone configuration
			standaloneConfig := NewClientConfiguration().WithDatabaseId(dbId)
			_, err := standaloneConfig.ToProtobuf()
			assert.EqualError(t, err, "database_id must be non-negative")

			// Test cluster configuration
			clusterConfig := NewClusterClientConfiguration().WithDatabaseId(dbId)
			_, err = clusterConfig.ToProtobuf()
			assert.EqualError(t, err, "database_id must be non-negative")
		})
	}
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

	// Test that both configurations use the same base validation logic
	standaloneConfigInvalid := NewClientConfiguration().WithDatabaseId(-1)
	_, err = standaloneConfigInvalid.ToProtobuf()
	assert.EqualError(t, err, "database_id must be non-negative")

	clusterConfigInvalid := NewClusterClientConfiguration().WithDatabaseId(-1)
	_, err = clusterConfigInvalid.ToProtobuf()
	assert.EqualError(t, err, "database_id must be non-negative")
}
