// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"errors"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

const (
	DefaultHost = "localhost"
	DefaultPort = 6379
)

// NodeAddress represents the host address and port of a node in the cluster.
type NodeAddress struct {
	Host string // If not supplied, api.DefaultHost will be used.
	Port int    // If not supplied, api.DefaultPort will be used.
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

// validateDatabaseId validates the database ID parameter.
func validateDatabaseId(databaseId int) error {
	if databaseId < 0 {
		return errors.New("database_id must be non-negative")
	}
	return nil
}

type baseClientConfiguration struct {
	addresses         []NodeAddress
	useTLS            bool
	credentials       *ServerCredentials
	readFrom          ReadFrom
	requestTimeout    time.Duration
	clientName        string
	clientAZ          string
	reconnectStrategy *BackoffStrategy
	lazyConnect       bool
	DatabaseId        *int `json:"database_id,omitempty"`
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
		requestTimeout, err := utils.DurationToMilliseconds(config.requestTimeout)
		if err != nil {
			return nil, fmt.Errorf("setting request timeout returned an error: %w", err)
		}
		request.RequestTimeout = requestTimeout
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

	if config.reconnectStrategy != nil {
		request.ConnectionRetryStrategy = config.reconnectStrategy.toProtobuf()
	}

	if config.lazyConnect {
		request.LazyConnect = config.lazyConnect
	}

	if config.DatabaseId != nil {
		if err := validateDatabaseId(*config.DatabaseId); err != nil {
			return nil, err
		}
		request.DatabaseId = uint32(*config.DatabaseId)
	}

	return &request, nil
}

// BackoffStrategy defines how and when the client should attempt to reconnect after a connection failure.
// The time between retry attempts increases exponentially according to the formula:
//
//	rand(0 ... factor * (exponentBase ^ N))
//
// where N is the number of failed attempts. The `rand(...)` component applies a jitter of up to `jitterPercent%`
// to introduce randomness and reduce retry storms.
//
// Once the maximum retry interval is reached, that interval will be reused for all subsequent retries until
// a successful connection is established. The client retries indefinitely.
//
// If no strategy is explicitly provided, a default backoff strategy will be used.
type BackoffStrategy struct {
	// Number of retry attempts that the client should perform when disconnected from the server, where the time
	// between retries increases. Once the retries have reached the maximum value, the time between retries will remain
	// constant until a reconnect attempt is successful.
	numOfRetries int
	// The multiplier that will be applied to the waiting time between each retry.
	// This value is specified in milliseconds.
	factor int
	// The exponent base configured for the strategy.
	exponentBase int
	// The Jitter percent on the calculated duration. If not set, a default value will be used.
	jitterPercent *int
}

// NewBackoffStrategy returns a [BackoffStrategy] with the given configuration parameters.
func NewBackoffStrategy(numOfRetries int, factor int, exponentBase int) *BackoffStrategy {
	return &BackoffStrategy{
		numOfRetries: numOfRetries,
		factor:       factor,
		exponentBase: exponentBase,
	}
}

// WithJitterPercent sets the jitter percent.
func (strategy *BackoffStrategy) WithJitterPercent(jitter int) *BackoffStrategy {
	strategy.jitterPercent = &jitter
	return strategy
}

func (strategy *BackoffStrategy) toProtobuf() *protobuf.ConnectionRetryStrategy {
	protoStrategy := &protobuf.ConnectionRetryStrategy{
		NumberOfRetries: uint32(strategy.numOfRetries),
		Factor:          uint32(strategy.factor),
		ExponentBase:    uint32(strategy.exponentBase),
	}

	if strategy.jitterPercent != nil {
		jitter := uint32(*strategy.jitterPercent)
		protoStrategy.JitterPercent = &jitter
	}

	return protoStrategy
}

// ClientConfiguration represents the configuration settings for a Standalone client.
type ClientConfiguration struct {
	baseClientConfiguration
	subscriptionConfig *StandaloneSubscriptionConfig
	AdvancedClientConfiguration
}

// NewClientConfiguration returns a [ClientConfiguration] with default configuration settings. For further
// configuration, use the [ClientConfiguration] With* methods.
func NewClientConfiguration() *ClientConfiguration {
	return &ClientConfiguration{}
}

func (config *ClientConfiguration) ToProtobuf() (*protobuf.ConnectionRequest, error) {
	request, err := config.baseClientConfiguration.toProtobuf()
	if err != nil {
		return nil, err
	}
	request.ClusterModeEnabled = false

	if config.subscriptionConfig != nil && len(config.subscriptionConfig.subscriptions) > 0 {
		request.PubsubSubscriptions = config.subscriptionConfig.toProtobuf()
	}

	if config.AdvancedClientConfiguration.connectionTimeout != 0 {
		connectionTimeout, err := utils.DurationToMilliseconds(config.AdvancedClientConfiguration.connectionTimeout)
		if err != nil {
			return nil, fmt.Errorf("setting connection timeout returned an error: %w", err)
		}
		request.ConnectionTimeout = connectionTimeout
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
//	config := NewClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: api.DefaultPort}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: api.DefaultPort})
func (config *ClientConfiguration) WithAddress(address *NodeAddress) *ClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *ClientConfiguration) WithUseTLS(useTLS bool) *ClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithLazyConnect configures whether the client should establish connections lazily. When set to true,
// the client will only establish connections when needed for the first operation, rather than
// immediately upon client creation.
func (config *ClientConfiguration) WithLazyConnect(lazyConnect bool) *ClientConfiguration {
	config.lazyConnect = lazyConnect
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *ClientConfiguration) WithCredentials(credentials *ServerCredentials) *ClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *ClientConfiguration) WithReadFrom(readFrom ReadFrom) *ClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
//
// Using a negative value or a value that exceeds the max duration of 2^32 - 1 milliseconds will lead to an invalid
// configuration.
func (config *ClientConfiguration) WithRequestTimeout(requestTimeout time.Duration) *ClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *ClientConfiguration) WithClientName(clientName string) *ClientConfiguration {
	config.clientName = clientName
	return config
}

// WithClientAZ sets the client's Availability Zone (AZ) to be used for the client.
func (config *ClientConfiguration) WithClientAZ(clientAZ string) *ClientConfiguration {
	config.clientAZ = clientAZ
	return config
}

// WithReconnectStrategy sets the [BackoffStrategy] used to determine how and when to reconnect, in case of connection
// failures. If not set, a default backoff strategy will be used.
func (config *ClientConfiguration) WithReconnectStrategy(strategy *BackoffStrategy) *ClientConfiguration {
	config.reconnectStrategy = strategy
	return config
}

// WithDatabaseId sets the index of the logical database to connect to.
func (config *ClientConfiguration) WithDatabaseId(id int) *ClientConfiguration {
	config.DatabaseId = &id
	return config
}

// WithAdvancedConfiguration sets the advanced configuration settings for the client.
func (config *ClientConfiguration) WithAdvancedConfiguration(
	advancedConfig *AdvancedClientConfiguration,
) *ClientConfiguration {
	config.AdvancedClientConfiguration = *advancedConfig
	return config
}

// WithSubscriptionConfig sets the subscription configuration for the client.
func (config *ClientConfiguration) WithSubscriptionConfig(
	subscriptionConfig *StandaloneSubscriptionConfig,
) *ClientConfiguration {
	config.subscriptionConfig = subscriptionConfig
	return config
}

func (config *ClientConfiguration) HasSubscription() bool {
	return config.subscriptionConfig != nil && len(config.subscriptionConfig.subscriptions) > 0
}

func (config *ClientConfiguration) GetSubscription() *StandaloneSubscriptionConfig {
	if config.HasSubscription() {
		return config.subscriptionConfig
	}
	return nil
}

// ClusterClientConfiguration represents the configuration settings for a Cluster Glide client.
// Note: Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff with fixed values is
// used.
type ClusterClientConfiguration struct {
	baseClientConfiguration
	subscriptionConfig *ClusterSubscriptionConfig
	AdvancedClusterClientConfiguration
}

// NewClusterClientConfiguration returns a [ClusterClientConfiguration] with default configuration settings. For
// further configuration, use the [ClientConfiguration] With* methods.
func NewClusterClientConfiguration() *ClusterClientConfiguration {
	return &ClusterClientConfiguration{
		baseClientConfiguration:            baseClientConfiguration{},
		AdvancedClusterClientConfiguration: AdvancedClusterClientConfiguration{},
	}
}

func (config *ClusterClientConfiguration) ToProtobuf() (*protobuf.ConnectionRequest, error) {
	request, err := config.baseClientConfiguration.toProtobuf()
	if err != nil {
		return nil, err
	}

	request.ClusterModeEnabled = true
	if (config.AdvancedClusterClientConfiguration.connectionTimeout) != 0 {
		connectionTimeout, err := utils.DurationToMilliseconds(config.AdvancedClusterClientConfiguration.connectionTimeout)
		if err != nil {
			return nil, fmt.Errorf("setting connection timeout returned an error: %w", err)
		}
		request.ConnectionTimeout = connectionTimeout
	}
	if config.subscriptionConfig != nil && len(config.subscriptionConfig.subscriptions) > 0 {
		request.PubsubSubscriptions = config.subscriptionConfig.toProtobuf()
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
//	config := NewClusterClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: api.DefaultPort}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: api.DefaultPort})
func (config *ClusterClientConfiguration) WithAddress(address *NodeAddress) *ClusterClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *ClusterClientConfiguration) WithUseTLS(useTLS bool) *ClusterClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithLazyConnect configures whether the client should establish connections lazily. When set to true,
// the client will only establish connections when needed for the first operation, rather than
// immediately upon client creation.
func (config *ClusterClientConfiguration) WithLazyConnect(lazyConnect bool) *ClusterClientConfiguration {
	config.lazyConnect = lazyConnect
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *ClusterClientConfiguration) WithCredentials(
	credentials *ServerCredentials,
) *ClusterClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *ClusterClientConfiguration) WithReadFrom(readFrom ReadFrom) *ClusterClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
//
// Using a negative value or a value that exceeds the max duration of 2^32 - 1 milliseconds will lead to an invalid
// configuration.
func (config *ClusterClientConfiguration) WithRequestTimeout(requestTimeout time.Duration) *ClusterClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *ClusterClientConfiguration) WithClientName(clientName string) *ClusterClientConfiguration {
	config.clientName = clientName
	return config
}

// WithClientAZ sets the client's Availability Zone (AZ) to be used for the client.
func (config *ClusterClientConfiguration) WithClientAZ(clientAZ string) *ClusterClientConfiguration {
	config.clientAZ = clientAZ
	return config
}

// WithReconnectStrategy sets the [BackoffStrategy] used to determine how and when to reconnect, in case of connection
// failures. If not set, a default backoff strategy will be used.
func (config *ClusterClientConfiguration) WithReconnectStrategy(
	strategy *BackoffStrategy,
) *ClusterClientConfiguration {
	config.reconnectStrategy = strategy
	return config
}

// WithDatabaseId sets the index of the logical database to connect to.
func (config *ClusterClientConfiguration) WithDatabaseId(id int) *ClusterClientConfiguration {
	config.DatabaseId = &id
	return config
}

// WithAdvancedConfiguration sets the advanced configuration settings for the client.
func (config *ClusterClientConfiguration) WithAdvancedConfiguration(
	advancedConfig *AdvancedClusterClientConfiguration,
) *ClusterClientConfiguration {
	config.AdvancedClusterClientConfiguration = *advancedConfig
	return config
}

// WithSubscriptionConfig sets the subscription configuration for the client.
func (config *ClusterClientConfiguration) WithSubscriptionConfig(
	subscriptionConfig *ClusterSubscriptionConfig,
) *ClusterClientConfiguration {
	config.subscriptionConfig = subscriptionConfig
	return config
}

func (config *ClusterClientConfiguration) HasSubscription() bool {
	return config.subscriptionConfig != nil && len(config.subscriptionConfig.subscriptions) > 0
}

func (config *ClusterClientConfiguration) GetSubscription() *ClusterSubscriptionConfig {
	if config.HasSubscription() {
		return config.subscriptionConfig
	}
	return nil
}

// Represents advanced configuration settings for a Standalone client used in [ClientConfiguration].
type AdvancedClientConfiguration struct {
	connectionTimeout time.Duration
}

// NewAdvancedClientConfiguration returns a new [AdvancedClientConfiguration] with default settings.
func NewAdvancedClientConfiguration() *AdvancedClientConfiguration {
	return &AdvancedClientConfiguration{}
}

// WithConnectionTimeout sets the duration to wait for a TCP/TLS connection to complete.
// The duration in milliseconds to wait for a TCP/TLS connection to complete. This applies both
// during initial client creation and any reconnection that may occur during request processing.
// Note: A high connection timeout may lead to prolonged blocking of the entire command
// pipeline. If not explicitly set, a default value of 2000 milliseconds will be used.
//
// Using a negative value or a value that exceeds the max duration of 2^32 - 1 milliseconds will lead to an invalid
// configuration.
func (config *AdvancedClientConfiguration) WithConnectionTimeout(
	connectionTimeout time.Duration,
) *AdvancedClientConfiguration {
	config.connectionTimeout = connectionTimeout
	return config
}

// Represents advanced configuration settings for a Cluster client used in
// [ClusterClientConfiguration].
type AdvancedClusterClientConfiguration struct {
	connectionTimeout time.Duration
}

// NewAdvancedClusterClientConfiguration returns a new [AdvancedClusterClientConfiguration] with default settings.
func NewAdvancedClusterClientConfiguration() *AdvancedClusterClientConfiguration {
	return &AdvancedClusterClientConfiguration{}
}

// WithConnectionTimeout sets the duration to wait for a TCP/TLS connection to complete.
//
// Using a negative value or a value that exceeds the max duration of 2^32 - 1 milliseconds will lead to an invalid
// configuration.
func (config *AdvancedClusterClientConfiguration) WithConnectionTimeout(
	connectionTimeout time.Duration,
) *AdvancedClusterClientConfiguration {
	config.connectionTimeout = connectionTimeout
	return config
}
