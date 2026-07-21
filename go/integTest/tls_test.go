// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"os"
	"path/filepath"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestTlsWithoutCertificate_Standalone tests that connection fails without providing certificates
func (suite *GlideTestSuite) TestTlsWithoutCertificate_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Standalone tests standalone client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithMultipleCertificates_Standalone tests standalone client with multiple concatenated certificates
func (suite *GlideTestSuite) TestTlsWithMultipleCertificates_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithoutCertificate_Cluster tests that connection fails without providing certificates
func (suite *GlideTestSuite) TestTlsWithoutCertificate_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Cluster tests cluster client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithMultipleCertificates_Cluster tests cluster client with multiple concatenated certificates
func (suite *GlideTestSuite) TestTlsWithMultipleCertificates_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithEmptyCertificate_Standalone tests that empty certificate array returns an error
func (suite *GlideTestSuite) TestTlsWithEmptyCertificate_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithEmptyCertificate_Cluster tests that empty certificate array returns an error
func (suite *GlideTestSuite) TestTlsWithEmptyCertificate_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Standalone tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Cluster tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsLoadCertificateFromFile tests the LoadRootCertificatesFromFile helper function
func (suite *GlideTestSuite) TestTlsLoadCertificateFromFile() {
	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Test successful certificate loading
	assert.NotEmpty(suite.T(), certData)
	assert.Contains(suite.T(), string(certData), "BEGIN CERTIFICATE")

	// Test loading non-existent file
	_, err = config.LoadRootCertificatesFromFile("/nonexistent/path/cert.pem")
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "failed to read certificate file")
}

// TestTlsLoadClientCertificateAndKeyFromFile tests the LoadClientCertificateFromFile and
// LoadClientKeyFromFile helper functions for mutual TLS (mTLS).
func (suite *GlideTestSuite) TestTlsLoadClientCertificateAndKeyFromFile() {
	// Test loading non-existent client certificate file
	_, err := config.LoadClientCertificateFromFile("/nonexistent/path/client-cert.pem")
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "failed to read client certificate file")

	// Test loading non-existent client key file
	_, err = config.LoadClientKeyFromFile("/nonexistent/path/client-key.pem")
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "failed to read client key file")
}

// getClientCertAndKeyPaths returns absolute filesystem paths to a client certificate/key pair
// suitable for mutual TLS. It looks under utils/tls_crts, matching getCaCertificate.
func getClientCertAndKeyPaths() (certPath, keyPath string, err error) {
	glideHome := os.Getenv("GLIDE_HOME_DIR")
	if glideHome == "" {
		glideHome = "../.."
	}
	certPath, err = filepath.Abs(filepath.Join(glideHome, "utils", "tls_crts", "client.crt"))
	if err != nil {
		return "", "", err
	}
	keyPath, err = filepath.Abs(filepath.Join(glideHome, "utils", "tls_crts", "client.key"))
	if err != nil {
		return "", "", err
	}
	if _, err := os.Stat(certPath); err != nil {
		return "", "", err
	}
	if _, err := os.Stat(keyPath); err != nil {
		return "", "", err
	}
	return certPath, keyPath, nil
}

// TestTlsMutualTLS_Standalone exercises byte-based mTLS end-to-end against a real standalone
// server. Skipped when TLS is disabled in CI or when client cert/key files are unavailable.
func (suite *GlideTestSuite) TestTlsMutualTLS_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	caCert, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}
	certPath, keyPath, err := getClientCertAndKeyPaths()
	if err != nil {
		suite.T().Skipf("client cert/key not found, skipping test: %v", err)
	}
	clientCert, err := config.LoadClientCertificateFromFile(certPath)
	require.NoError(suite.T(), err)
	clientKey, err := config.LoadClientKeyFromFile(keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMutualTLSWithReload_Standalone exercises path-based mTLS with automatic reload against
// a real standalone server. It only verifies the client is functional; rotation itself is
// covered in core tests (glide-core/tests/test_client.rs).
func (suite *GlideTestSuite) TestTlsMutualTLSWithReload_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	caCert, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}
	certPath, keyPath, err := getClientCertAndKeyPaths()
	if err != nil {
		suite.T().Skipf("client cert/key not found, skipping test: %v", err)
	}

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLSFromFiles(certPath, keyPath)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMutualTLS_Cluster mirrors TestTlsMutualTLS_Standalone against a cluster.
func (suite *GlideTestSuite) TestTlsMutualTLS_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	caCert, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}
	certPath, keyPath, err := getClientCertAndKeyPaths()
	if err != nil {
		suite.T().Skipf("client cert/key not found, skipping test: %v", err)
	}
	clientCert, err := config.LoadClientCertificateFromFile(certPath)
	require.NoError(suite.T(), err)
	clientKey, err := config.LoadClientKeyFromFile(keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithIPv4AddressSucceeds_Standalone tests TLS connection with IPv4 address
func (suite *GlideTestSuite) TestTlsWithIPv4AddressSucceeds_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV4,
		Port: suite.standaloneHosts[0].Port,
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClientConfig().
		WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().
		WithAddress(&address).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithIPv4AddressSucceeds_Cluster tests TLS connection with IPv4 address
func (suite *GlideTestSuite) TestTlsWithIPv4AddressSucceeds_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV4,
		Port: suite.clusterHosts[0].Port,
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClusterClientConfig().
		WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().
		WithAddress(&address).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithIPv6AddressSucceeds_Standalone tests TLS connection with IPv6 address
func (suite *GlideTestSuite) TestTlsWithIPv6AddressSucceeds_Standalone() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV6,
		Port: suite.standaloneHosts[0].Port,
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClientConfig().
		WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().
		WithAddress(&address).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsWithIPv6AddressSucceeds_Cluster tests TLS connection with IPv6 address
func (suite *GlideTestSuite) TestTlsWithIPv6AddressSucceeds_Cluster() {
	// TODO #5509: TLS tests do not currently run as part of CI.
	skipIfTlsDisabled(suite)

	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV6,
		Port: suite.clusterHosts[0].Port,
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClusterClientConfig().
		WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().
		WithAddress(&address).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// getCaCertificate returns the CA certificate bytes in PEM format.
// It looks for the certificate in the utils/tls_crts directory.
func getCaCertificate() ([]byte, error) {
	// Try to get GLIDE_HOME_DIR from environment, otherwise use relative path
	glideHome := os.Getenv("GLIDE_HOME_DIR")
	if glideHome == "" {
		// Default to ../../ from the integTest directory
		glideHome = "../.."
	}

	caCertPath := filepath.Join(glideHome, "utils", "tls_crts", "ca.crt")
	absPath, err := filepath.Abs(caCertPath)
	if err != nil {
		return nil, err
	}

	return config.LoadRootCertificatesFromFile(absPath)
}
