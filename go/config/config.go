// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"errors"
	"fmt"
	"os"
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

// ServiceType represents the types of AWS services that can be used for IAM authentication.
type ServiceType int

const (
	// Amazon ElastiCache service.
	ElastiCache ServiceType = iota
	// Amazon MemoryDB service.
	MemoryDB
)

// IamAuthConfig represents configuration settings for IAM authentication.
type IamAuthConfig struct {
	// The name of the ElastiCache/MemoryDB cluster.
	clusterName string
	// The type of service being used (ElastiCache or MemoryDB).
	service ServiceType
	// The AWS region where the ElastiCache/MemoryDB cluster is located.
	region string
	// Optional refresh interval in seconds for renewing IAM authentication tokens.
	// If not provided, the core will use its default value.
	refreshIntervalSeconds *uint32
}

// NewIamAuthConfig returns an [IamAuthConfig] struct with the given configuration.
func NewIamAuthConfig(clusterName string, service ServiceType, region string) *IamAuthConfig {
	return &IamAuthConfig{
		clusterName: clusterName,
		service:     service,
		region:      region,
	}
}

// WithRefreshIntervalSeconds sets the refresh interval in seconds for IAM token renewal.
func (config *IamAuthConfig) WithRefreshIntervalSeconds(seconds uint32) *IamAuthConfig {
	config.refreshIntervalSeconds = &seconds
	return config
}

func (config *IamAuthConfig) toProtobuf() *protobuf.IamCredentials {
	iamCreds := &protobuf.IamCredentials{
		ClusterName: config.clusterName,
		Region:      config.region,
	}

	if config.service == ElastiCache {
		iamCreds.ServiceType = protobuf.ServiceType_ELASTICACHE
	} else {
		iamCreds.ServiceType = protobuf.ServiceType_MEMORYDB
	}

	if config.refreshIntervalSeconds != nil {
		iamCreds.RefreshIntervalSeconds = config.refreshIntervalSeconds
	}

	return iamCreds
}

// ServerCredentials represents the credentials for connecting to servers.
// Supports two authentication modes:
//   - Password-based authentication: Use username and password
//   - IAM authentication: Use username (required) and iamConfig
//
// These modes are mutually exclusive.
type ServerCredentials struct {
	// The username that will be used for authenticating connections to the servers. If not supplied, "default"
	// will be used for password-based authentication. Required for IAM authentication.
	username string
	// The password that will be used for authenticating connections to the servers.
	// Mutually exclusive with iamConfig.
	password string
	// IAM authentication configuration. Mutually exclusive with password.
	// The client will automatically generate and refresh the authentication token based on the provided configuration.
	iamConfig *IamAuthConfig
}

// NewServerCredentials returns a [ServerCredentials] struct with the given username and password.
func NewServerCredentials(username string, password string) *ServerCredentials {
	return &ServerCredentials{username: username, password: password}
}

// NewServerCredentialsWithDefaultUsername returns a [ServerCredentials] struct with a default username of "default" and the
// given password.
func NewServerCredentialsWithDefaultUsername(password string) *ServerCredentials {
	return &ServerCredentials{password: password}
}

// NewServerCredentialsWithIam returns a [ServerCredentials] struct configured for IAM authentication.
// The username is required for IAM authentication.
func NewServerCredentialsWithIam(username string, iamConfig *IamAuthConfig) (*ServerCredentials, error) {
	if username == "" {
		return nil, errors.New("username is required for IAM authentication")
	}
	if iamConfig == nil {
		return nil, errors.New("iamConfig cannot be nil")
	}
	return &ServerCredentials{username: username, iamConfig: iamConfig}, nil
}

func (creds *ServerCredentials) toProtobuf() *protobuf.AuthenticationInfo {
	authInfo := &protobuf.AuthenticationInfo{
		Username: creds.username,
		Password: creds.password,
	}

	if creds.iamConfig != nil {
		authInfo.IamCredentials = creds.iamConfig.toProtobuf()
	}

	return authInfo
}

// IsIamAuth returns true if this credential is configured for IAM authentication.
func (creds *ServerCredentials) IsIamAuth() bool {
	return creds.iamConfig != nil
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

	// Handle TCP_NODELAY configuration
	if config.AdvancedClientConfiguration.tcpNoDelay != nil {
		request.TcpNodelay = config.AdvancedClientConfiguration.tcpNoDelay
	}

	// Handle TLS configuration
	if config.AdvancedClientConfiguration.tlsConfig != nil {
		tlsConfig := config.AdvancedClientConfiguration.tlsConfig

		// Handle insecure TLS mode
		if tlsConfig.UseInsecureTLS {
			if request.TlsMode == protobuf.TlsMode_NoTls {
				return nil, errors.New("UseInsecureTLS cannot be enabled when UseTLS is disabled")
			}
			// Override SecureTls mode to InsecureTls when user explicitly requests it
			request.TlsMode = protobuf.TlsMode_InsecureTls
		}

		// Handle root certificates
		if tlsConfig.RootCertificates != nil {
			if len(tlsConfig.RootCertificates) == 0 {
				return nil, errors.New("root certificates cannot be an empty byte array; use nil to use platform verifier")
			}
			request.RootCerts = [][]byte{tlsConfig.RootCertificates}
		}
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
	request.RefreshTopologyFromInitialNodes = config.AdvancedClusterClientConfiguration.refreshTopologyFromInitialNodes

	// Handle TCP_NODELAY configuration
	if config.AdvancedClusterClientConfiguration.tcpNoDelay != nil {
		request.TcpNodelay = config.AdvancedClusterClientConfiguration.tcpNoDelay
	}

	// Handle TLS configuration
	if config.AdvancedClusterClientConfiguration.tlsConfig != nil {
		tlsConfig := config.AdvancedClusterClientConfiguration.tlsConfig

		// Handle insecure TLS mode
		if tlsConfig.UseInsecureTLS {
			if request.TlsMode == protobuf.TlsMode_NoTls {
				return nil, errors.New("UseInsecureTLS cannot be enabled when UseTLS is disabled")
			}
			// Override SecureTls mode to InsecureTls when user explicitly requests it
			request.TlsMode = protobuf.TlsMode_InsecureTls
		}

		// Handle root certificates
		if tlsConfig.RootCertificates != nil {
			if len(tlsConfig.RootCertificates) == 0 {
				return nil, errors.New("root certificates cannot be an empty byte array; use nil to use platform verifier")
			}
			request.RootCerts = [][]byte{tlsConfig.RootCertificates}
		}
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

// TlsConfiguration represents TLS-specific configuration settings.
type TlsConfiguration struct {
	// RootCertificates contains custom root certificate data for TLS connections in PEM format.
	//
	// When provided, these certificates will be used instead of the system's default trust store.
	// If set to an empty byte array (non-nil but length 0), an error will be returned.
	// If nil, the system's default certificate trust store will be used (platform verifier).
	//
	// The certificate data should be in PEM format as a byte array.
	RootCertificates []byte

	// UseInsecureTLS bypasses TLS certificate verification when set to true.
	//
	// When enabled, the client skips certificate validation. This is useful when connecting
	// to servers or clusters using self-signed certificates, or when DNS entries (e.g., CNAMEs)
	// don't match certificate hostnames.
	//
	// This setting is typically used in development or testing environments. It is strongly
	// discouraged in production, as it introduces security risks such as man-in-the-middle attacks.
	//
	// Only valid if TLS is already enabled in the base client configuration.
	// Enabling it without TLS will result in an error.
	//
	// Default: false (verification is enforced).
	UseInsecureTLS bool
}

// NewTlsConfiguration returns a new [TlsConfiguration] with default settings (uses platform verifier).
func NewTlsConfiguration() *TlsConfiguration {
	return &TlsConfiguration{}
}

// WithRootCertificates sets custom root certificates for TLS connections.
// The certificates should be in PEM format.
// Pass nil to use the system's default trust store (default behavior).
// Passing an empty byte array will result in an error during connection.
func (config *TlsConfiguration) WithRootCertificates(rootCerts []byte) *TlsConfiguration {
	config.RootCertificates = rootCerts
	return config
}

// WithInsecureTLS enables or disables insecure TLS mode.
//
// When enabled (true), TLS certificate verification is bypassed. This is useful for development
// and testing with self-signed certificates, but should never be used in production.
//
// Only valid if TLS is already enabled in the base client configuration.
// Attempting to enable insecure TLS without TLS enabled will result in an error during connection.
//
// Default: false (verification is enforced).
func (config *TlsConfiguration) WithInsecureTLS(insecure bool) *TlsConfiguration {
	config.UseInsecureTLS = insecure
	return config
}

// LoadRootCertificatesFromFile reads a PEM-encoded certificate file and returns its contents as a byte array.
// This is a convenience function for loading custom root certificates from disk.
//
// Parameters:
//   - path: The file path to the PEM-encoded certificate file
//
// Returns:
//   - []byte: The certificate data in PEM format
//   - error: An error if the file cannot be read
//
// Example usage:
//
//	certs, err := config.LoadRootCertificatesFromFile("/path/to/ca-cert.pem")
//	if err != nil {
//	    log.Fatal(err)
//	}
//	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certs)
//	advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)
func LoadRootCertificatesFromFile(path string) ([]byte, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read certificate file: %w", err)
	}

	if len(data) == 0 {
		return nil, fmt.Errorf("certificate file is empty: %s", path)
	}

	return data, nil
}

// Represents advanced configuration settings for a Standalone client used in [ClientConfiguration].
type AdvancedClientConfiguration struct {
	connectionTimeout time.Duration
	tlsConfig         *TlsConfiguration
	tcpNoDelay        *bool
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

// WithTlsConfiguration sets the TLS configuration for the client.
// This allows customization of TLS behavior, such as providing custom root certificates.
func (config *AdvancedClientConfiguration) WithTlsConfiguration(
	tlsConfig *TlsConfiguration,
) *AdvancedClientConfiguration {
	config.tlsConfig = tlsConfig
	return config
}

// WithTcpNoDelay sets the TCP_NODELAY socket option for the client connections.
// When enabled (true), TCP_NODELAY disables Nagle's algorithm, which can reduce latency
// for small messages by sending them immediately rather than buffering.
// When disabled (false), Nagle's algorithm is enabled, which may improve throughput for
// bulk operations by buffering small packets.
// If not set, the default behavior (enabled) will be used.
func (config *AdvancedClientConfiguration) WithTcpNoDelay(
	tcpNoDelay bool,
) *AdvancedClientConfiguration {
	config.tcpNoDelay = &tcpNoDelay
	return config
}

// Represents advanced configuration settings for a Cluster client used in
// [ClusterClientConfiguration].
type AdvancedClusterClientConfiguration struct {
	connectionTimeout               time.Duration
	refreshTopologyFromInitialNodes bool
	tlsConfig                       *TlsConfiguration
	tcpNoDelay                      *bool
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

// WithRefreshTopologyFromInitialNodes enables refreshing the cluster topology using only the initial nodes.
//
// When this option is enabled, all topology updates (both the periodic checks and on-demand
// refreshes triggered by topology changes) will query only the initial nodes provided when
// creating the client, rather than using the internal cluster view.
//
// If not set, defaults to false (uses internal cluster view for topology refresh).
func (config *AdvancedClusterClientConfiguration) WithRefreshTopologyFromInitialNodes(
	refreshTopologyFromInitialNodes bool,
) *AdvancedClusterClientConfiguration {
	config.refreshTopologyFromInitialNodes = refreshTopologyFromInitialNodes
	return config
}

// WithTlsConfiguration sets the TLS configuration for the cluster client.
// This allows customization of TLS behavior, such as providing custom root certificates.
func (config *AdvancedClusterClientConfiguration) WithTlsConfiguration(
	tlsConfig *TlsConfiguration,
) *AdvancedClusterClientConfiguration {
	config.tlsConfig = tlsConfig
	return config
}

// WithTcpNoDelay sets the TCP_NODELAY socket option for the cluster client connections.
// When enabled (true), TCP_NODELAY disables Nagle's algorithm, which can reduce latency
// for small messages by sending them immediately rather than buffering.
// When disabled (false), Nagle's algorithm is enabled, which may improve throughput for
// bulk operations by buffering small packets.
// If not set, the default behavior (enabled) will be used.
func (config *AdvancedClusterClientConfiguration) WithTcpNoDelay(
	tcpNoDelay bool,
) *AdvancedClusterClientConfiguration {
	config.tcpNoDelay = &tcpNoDelay
	return config
}
