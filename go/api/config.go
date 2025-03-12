// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"errors"

	"github.com/valkey-io/valkey-glide/go/protobuf"
)

const (
	DefaultHost = "localhost"
	DefaultPort = 6379
)

// NodeAddress represents the host address and port of a node in the cluster.
type NodeAddress struct {
	Host string // If not supplied, api.DefaultHost will be used.
	Port int    // If not supplied, api.DefaultPost will be used.
}

func (addr *NodeAddress) toProtobuf() *protobuf.NodeAddress {
	if addr.Host == "" {
		addr.Host = DefaultHost
	}

	if addr.Port == 0 {
		addr.Port = DefaultPort
	}

	return &protobuf.NodeAddress{Host: addr.Host, Port: uint32(addr.Port)}
}

// ServerCredentials represents the credentials for connecting to servers.
type ServerCredentials struct {
	// The username that will be used for authenticating connections to the servers. If not supplied, "default"
	// will be used.
	username string
	// The password that will be used for authenticating connections to the servers.
	password string
}

// NewServerCredentials returns a [ServerCredentials] struct with the given username and password.
func NewServerCredentials(username string, password string) *ServerCredentials {
	return &ServerCredentials{username, password}
}

// NewServerCredentialsWithDefaultUsername returns a [ServerCredentials] struct with a default username of "default" and the
// given password.
func NewServerCredentialsWithDefaultUsername(password string) *ServerCredentials {
	return &ServerCredentials{password: password}
}

func (creds *ServerCredentials) toProtobuf() *protobuf.AuthenticationInfo {
	return &protobuf.AuthenticationInfo{Username: creds.username, Password: creds.password}
}

// ReadFrom represents the client's read from strategy.
type ReadFrom int

const (
	// Primary - Always get from primary, in order to get the freshest data.
	Primary ReadFrom = iota
	// PreferReplica - Spread the requests between all replicas in a round-robin manner. If no replica is available, route the
	// requests to the primary.
	PreferReplica
	// Spread the read requests between replicas in the same client's AZ (Aviliablity zone) in a
	// round-robin manner, falling back to other replicas or the primary if needed.
	AzAffinity
	// Spread the read requests among nodes within the client's Availability Zone (AZ) in a round
	// robin manner, prioritizing local replicas, then the local primary, and falling back to any
	// replica or the primary if needed.
	AzAffinityReplicaAndPrimary
)

func mapReadFrom(readFrom ReadFrom) protobuf.ReadFrom {
	if readFrom == PreferReplica {
		return protobuf.ReadFrom_PreferReplica
	}

	if readFrom == AzAffinity {
		return protobuf.ReadFrom_AZAffinity
	}

	if readFrom == AzAffinityReplicaAndPrimary {
		return protobuf.ReadFrom_AZAffinityReplicasAndPrimary
	}

	return protobuf.ReadFrom_Primary
}

type baseClientConfiguration struct {
	addresses      []NodeAddress
	useTLS         bool
	credentials    *ServerCredentials
	readFrom       ReadFrom
	requestTimeout int
	clientName     string
	clientAZ       string
}

func (config *baseClientConfiguration) toProtobuf() (*protobuf.ConnectionRequest, error) {
	request := protobuf.ConnectionRequest{}
	for _, address := range config.addresses {
		request.Addresses = append(request.Addresses, address.toProtobuf())
	}

	if config.useTLS {
		request.TlsMode = protobuf.TlsMode_SecureTls
	} else {
		request.TlsMode = protobuf.TlsMode_NoTls
	}

	if config.credentials != nil {
		request.AuthenticationInfo = config.credentials.toProtobuf()
	}

	request.ReadFrom = mapReadFrom(config.readFrom)
	if config.requestTimeout != 0 {
		request.RequestTimeout = uint32(config.requestTimeout)
	}

	if config.clientName != "" {
		request.ClientName = config.clientName
	}

	if config.clientAZ != "" {
		request.ClientAz = config.clientAZ
	}

	if request.ReadFrom == protobuf.ReadFrom_AZAffinity ||
		request.ReadFrom == protobuf.ReadFrom_AZAffinityReplicasAndPrimary {
		if config.clientAZ == "" {
			return nil, errors.New("client AZ must be set when using AZ affinity or AZ affinity with replicas and primary")
		}
	}

	return &request, nil
}

// BackoffStrategy represents the strategy used to determine how and when to reconnect, in case of connection failures. The
// time between attempts grows exponentially, to the formula:
//
//	rand(0 ... factor * (exponentBase ^ N))
//
// where N is the number of failed attempts.
//
// Once the maximum value is reached, that will remain the time between retry attempts until a reconnect attempt is successful.
// The client will attempt to reconnect indefinitely.
type BackoffStrategy struct {
	// Number of retry attempts that the client should perform when disconnected from the server, where the time
	// between retries increases. Once the retries have reached the maximum value, the time between retries will remain
	// constant until a reconnect attempt is successful.
	numOfRetries int
	// The multiplier that will be applied to the waiting time between each retry.
	factor int
	// The exponent base configured for the strategy.
	exponentBase int
}

// NewBackoffStrategy returns a [BackoffStrategy] with the given configuration parameters.
func NewBackoffStrategy(numOfRetries int, factor int, exponentBase int) *BackoffStrategy {
	return &BackoffStrategy{numOfRetries, factor, exponentBase}
}

func (strategy *BackoffStrategy) toProtobuf() *protobuf.ConnectionRetryStrategy {
	return &protobuf.ConnectionRetryStrategy{
		NumberOfRetries: uint32(strategy.numOfRetries),
		Factor:          uint32(strategy.factor),
		ExponentBase:    uint32(strategy.exponentBase),
	}
}

// GlideClientConfiguration represents the configuration settings for a Standalone client.
type GlideClientConfiguration struct {
	baseClientConfiguration
	reconnectStrategy *BackoffStrategy
	databaseId        int
	AdvancedGlideClientConfiguration
}

// NewGlideClientConfiguration returns a [GlideClientConfiguration] with default configuration settings. For further
// configuration, use the [GlideClientConfiguration] With* methods.
func NewGlideClientConfiguration() *GlideClientConfiguration {
	return &GlideClientConfiguration{}
}

func (config *GlideClientConfiguration) toProtobuf() (*protobuf.ConnectionRequest, error) {
	request, err := config.baseClientConfiguration.toProtobuf()
	if err != nil {
		return nil, err
	}
	request.ClusterModeEnabled = false
	if config.reconnectStrategy != nil {
		request.ConnectionRetryStrategy = config.reconnectStrategy.toProtobuf()
	}

	if config.databaseId != 0 {
		request.DatabaseId = uint32(config.databaseId)
	}

	if config.AdvancedGlideClientConfiguration.connectionTimeout != 0 {
		request.ConnectionTimeout = uint32(config.AdvancedGlideClientConfiguration.connectionTimeout)
	}

	return request, nil
}

// WithAddress adds an address for a known node in the cluster to this configuration's list of addresses. WithAddress can be
// called multiple times to add multiple addresses to the list. If the server is in cluster mode the list can be partial, as
// the client will attempt to map out the cluster and find all nodes. If the server is in standalone mode, only nodes whose
// addresses were provided will be used by the client.
//
// For example:
//
//	config := NewGlideClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: api.DefaultPost}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: api.DefaultPost})
func (config *GlideClientConfiguration) WithAddress(address *NodeAddress) *GlideClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *GlideClientConfiguration) WithUseTLS(useTLS bool) *GlideClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *GlideClientConfiguration) WithCredentials(credentials *ServerCredentials) *GlideClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *GlideClientConfiguration) WithReadFrom(readFrom ReadFrom) *GlideClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration in milliseconds that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
func (config *GlideClientConfiguration) WithRequestTimeout(requestTimeout int) *GlideClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *GlideClientConfiguration) WithClientName(clientName string) *GlideClientConfiguration {
	config.clientName = clientName
	return config
}

// WithClientAZ sets the client's Availability Zone (AZ) to be used for the client.
func (config *GlideClientConfiguration) WithClientAZ(clientAZ string) *GlideClientConfiguration {
	config.clientAZ = clientAZ
	return config
}

// WithReconnectStrategy sets the [BackoffStrategy] used to determine how and when to reconnect, in case of connection
// failures. If not set, a default backoff strategy will be used.
func (config *GlideClientConfiguration) WithReconnectStrategy(strategy *BackoffStrategy) *GlideClientConfiguration {
	config.reconnectStrategy = strategy
	return config
}

// WithDatabaseId sets the index of the logical database to connect to.
func (config *GlideClientConfiguration) WithDatabaseId(id int) *GlideClientConfiguration {
	config.databaseId = id
	return config
}

// WithAdvancedConfiguration sets the advanced configuration settings for the client.
func (config *GlideClientConfiguration) WithAdvancedConfiguration(
	advancedConfig *AdvancedGlideClientConfiguration,
) *GlideClientConfiguration {
	config.AdvancedGlideClientConfiguration = *advancedConfig
	return config
}

// GlideClusterClientConfiguration represents the configuration settings for a Cluster Glide client.
// Note: Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff with fixed values is
// used.
type GlideClusterClientConfiguration struct {
	baseClientConfiguration
	AdvancedGlideClusterClientConfiguration
}

// NewGlideClusterClientConfiguration returns a [GlideClusterClientConfiguration] with default configuration settings. For
// further configuration, use the [GlideClientConfiguration] With* methods.
func NewGlideClusterClientConfiguration() *GlideClusterClientConfiguration {
	return &GlideClusterClientConfiguration{
		baseClientConfiguration:                 baseClientConfiguration{},
		AdvancedGlideClusterClientConfiguration: AdvancedGlideClusterClientConfiguration{},
	}
}

func (config *GlideClusterClientConfiguration) toProtobuf() (*protobuf.ConnectionRequest, error) {
	request, err := config.baseClientConfiguration.toProtobuf()
	if err != nil {
		return nil, err
	}
	request.ClusterModeEnabled = true
	if (config.AdvancedGlideClusterClientConfiguration.connectionTimeout) != 0 {
		request.ConnectionTimeout = uint32(config.AdvancedGlideClusterClientConfiguration.connectionTimeout)
	}
	return request, nil
}

// WithAddress adds an address for a known node in the cluster to this configuration's list of addresses. WithAddress can be
// called multiple times to add multiple addresses to the list. If the server is in cluster mode the list can be partial, as
// the client will attempt to map out the cluster and find all nodes. If the server is in standalone mode, only nodes whose
// addresses were provided will be used by the client.
//
// For example:
//
//	config := NewGlideClusterClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: api.DefaultPost}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: api.DefaultPost})
func (config *GlideClusterClientConfiguration) WithAddress(address *NodeAddress) *GlideClusterClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *GlideClusterClientConfiguration) WithUseTLS(useTLS bool) *GlideClusterClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *GlideClusterClientConfiguration) WithCredentials(
	credentials *ServerCredentials,
) *GlideClusterClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *GlideClusterClientConfiguration) WithReadFrom(readFrom ReadFrom) *GlideClusterClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration in milliseconds that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
func (config *GlideClusterClientConfiguration) WithRequestTimeout(requestTimeout int) *GlideClusterClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *GlideClusterClientConfiguration) WithClientName(clientName string) *GlideClusterClientConfiguration {
	config.clientName = clientName
	return config
}

// WithClientAZ sets the client's Availability Zone (AZ) to be used for the client.
func (config *GlideClusterClientConfiguration) WithClientAZ(clientAZ string) *GlideClusterClientConfiguration {
	config.clientAZ = clientAZ
	return config
}

// WithAdvancedConfiguration sets the advanced configuration settings for the client.
func (config *GlideClusterClientConfiguration) WithAdvancedConfiguration(
	advancedConfig *AdvancedGlideClusterClientConfiguration,
) *GlideClusterClientConfiguration {
	config.AdvancedGlideClusterClientConfiguration = *advancedConfig
	return config
}

// Advanced configuration settings class for creating a client. Shared settings for standalone and
// cluster clients.
type AdvancedBaseClientConfiguration struct {
	connectionTimeout int
}

// Represents advanced configuration settings for a Standalone [GlideClient] used in [GlideClientConfiguration].
type AdvancedGlideClientConfiguration struct {
	AdvancedBaseClientConfiguration
}

// NewAdvancedGlideClientConfiguration returns a new [AdvancedGlideClientConfiguration] with default settings.
func NewAdvancedGlideClientConfiguration() *AdvancedGlideClientConfiguration {
	return &AdvancedGlideClientConfiguration{}
}

// WithConnectionTimeout sets the duration in milliseconds to wait for a TCP/TLS connection to complete.
// The duration in milliseconds to wait for a TCP/TLS connection to complete. This applies both
// during initial client creation and any reconnections that may occur during request processing.
// Note: A high connection timeout may lead to prolonged blocking of the entire command
// pipeline. If not explicitly set, a default value of 250 milliseconds will be used.
func (config *AdvancedGlideClientConfiguration) WithConnectionTimeout(
	connectionTimeout int,
) *AdvancedGlideClientConfiguration {
	config.connectionTimeout = connectionTimeout
	return config
}

// Represents advanced configuration settings for a Standalone [GlideClusterClient] used in
// [GlideClusterClientConfiguration].
type AdvancedGlideClusterClientConfiguration struct {
	AdvancedBaseClientConfiguration
}

// NewAdvancedGlideClusterClientConfiguration returns a new [AdvancedGlideClusterClientConfiguration] with default settings.
func NewAdvancedGlideClusterClientConfiguration() *AdvancedGlideClusterClientConfiguration {
	return &AdvancedGlideClusterClientConfiguration{}
}

// WithConnectionTimeout sets the duration in milliseconds to wait for a TCP/TLS connection to complete.
func (config *AdvancedGlideClusterClientConfiguration) WithConnectionTimeout(
	connectionTimeout int,
) *AdvancedGlideClusterClientConfiguration {
	config.connectionTimeout = connectionTimeout
	return config
}
