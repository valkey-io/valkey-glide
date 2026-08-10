// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestTlsWithoutCertificate_Standalone tests that connection fails without providing certificates
func (suite *GlideTestSuite) TestTlsWithoutCertificate_Standalone() {
	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
		WithUseTLS(true)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Standalone tests standalone client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Standalone() {
	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
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
	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
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
	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
		WithUseTLS(true)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Cluster tests cluster client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Cluster() {
	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
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
	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
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
	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithEmptyCertificate_Cluster tests that empty certificate array returns an error
func (suite *GlideTestSuite) TestTlsWithEmptyCertificate_Cluster() {
	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Standalone tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Standalone() {
	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Cluster tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Cluster() {
	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
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

// TestTlsLoadClientCertificateAndKeyFromFile covers the mTLS
// LoadClientCertificateAndKeyFromFile helper.
func (suite *GlideTestSuite) TestTlsLoadClientCertificateAndKeyFromFile() {
	// Load a real cert/key pair from disk and check the returned bytes.
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)
	certData, keyData, err := config.LoadClientCertificateAndKeyFromFile(certPath, keyPath)
	require.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), certData)
	assert.NotEmpty(suite.T(), keyData)
	assert.Contains(suite.T(), string(certData), "-----BEGIN CERTIFICATE-----")
	assert.Contains(suite.T(), string(keyData), "-----BEGIN")
	assert.Contains(suite.T(), string(keyData), "PRIVATE KEY-----")

	// Test loading non-existent client certificate file
	_, _, err = config.LoadClientCertificateAndKeyFromFile(
		"/nonexistent/path/client-cert.pem", "/nonexistent/path/client-key.pem")
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "failed to read client certificate file")
}

// getClientCertAndKeyPaths returns absolute paths for a client cert/key pair
// under utils/tls_crts (same convention as getCaCertificate). It resolves the
// paths but does not stat them; callers use require.NoError so a missing file
// fails the test at the top instead of silently skipping.
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
	return certPath, keyPath, nil
}

// TestTlsMutualTLS_Standalone runs byte-based mTLS end-to-end against a real
// standalone server. Skipped when TLS is disabled in CI; when TLS is enabled,
// missing cert material fails the test hard rather than skipping.
func (suite *GlideTestSuite) TestTlsMutualTLS_Standalone() {
	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)
	clientCert, clientKey, err := config.LoadClientCertificateAndKeyFromFile(certPath, keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMutualTLSWithReload_Standalone runs path-based mTLS with automatic
// reload against a real standalone server. It only checks the client connects
// and works; the rotation itself is covered by core tests in
// glide-core/tests/test_client.rs. Missing cert material under TLS-enabled
// runs is a hard failure.
func (suite *GlideTestSuite) TestTlsMutualTLSWithReload_Standalone() {
	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLSFromFiles(certPath, keyPath)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().WithAddress(&suite.standaloneTlsHosts[0]).
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
	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)
	clientCert, clientKey, err := config.LoadClientCertificateAndKeyFromFile(certPath, keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMutualTLSWithReload_Cluster mirrors TestTlsMutualTLSWithReload_Standalone
// against a cluster. It runs path-based mTLS with automatic reload end-to-end;
// the rotation itself is covered by core tests in glide-core/tests/test_client.rs.
// Missing cert material under TLS-enabled runs is a hard failure.
func (suite *GlideTestSuite) TestTlsMutualTLSWithReload_Cluster() {
	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLSFromFiles(certPath, keyPath)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClusterClientConfig().WithAddress(&suite.clusterTlsHosts[0]).
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
	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV4,
		Port: suite.standaloneTlsHosts[0].Port,
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
	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV4,
		Port: suite.clusterTlsHosts[0].Port,
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
	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV6,
		Port: suite.standaloneTlsHosts[0].Port,
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
	certData, err := getCaCertificate()
	require.NoError(suite.T(), err)

	address := config.NodeAddress{
		Host: IPAddressV6,
		Port: suite.clusterTlsHosts[0].Port,
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

// startMTlsRequiredStandalone spins up a single TLS standalone server that
// requires a client certificate (--tls-auth-clients). It returns the node
// address and a stop function; the cluster folder is captured inside the
// stop closure. The shared session TLS servers accept clients that send no
// certificate, so a dedicated server that rejects such clients is required
// to prove the accepting mTLS test is not passing vacuously.
func startMTlsRequiredStandalone(suite *GlideTestSuite) (config.NodeAddress, func()) {
	output := runClusterManager(
		suite,
		[]string{"--tls", "start", "--tls-auth-clients", "-n", "1", "-r", "0"},
		false,
	)
	addrs := extractAddresses(suite, output)
	require.NotEmpty(suite.T(), addrs, "mTLS-required cluster produced no addresses")

	clusterFolder := ""
	for _, line := range strings.Split(output, "\n") {
		if strings.HasPrefix(line, "CLUSTER_FOLDER=") {
			clusterFolder = strings.TrimPrefix(line, "CLUSTER_FOLDER=")
			break
		}
	}

	stop := func() {
		if clusterFolder == "" {
			return
		}
		runClusterManager(
			suite,
			[]string{"--tls", "stop", "--cluster-folder", clusterFolder},
			true,
		)
	}
	return addrs[0], stop
}

// startMTlsRequiredCluster spins up a TLS cluster (3 shards, 1 replica each)
// that requires a client certificate (--tls-auth-clients). It returns the
// full list of node addresses and a stop function; the cluster folder is
// captured inside the stop closure. This is the cluster-mode companion to
// startMTlsRequiredStandalone.
func startMTlsRequiredCluster(suite *GlideTestSuite) ([]config.NodeAddress, func()) {
	output := runClusterManager(
		suite,
		[]string{"--tls", "start", "--cluster-mode", "--tls-auth-clients", "-n", "3", "-r", "1"},
		false,
	)
	addrs := extractAddresses(suite, output)
	require.NotEmpty(suite.T(), addrs, "mTLS-required cluster produced no addresses")

	clusterFolder := ""
	for _, line := range strings.Split(output, "\n") {
		if strings.HasPrefix(line, "CLUSTER_FOLDER=") {
			clusterFolder = strings.TrimPrefix(line, "CLUSTER_FOLDER=")
			break
		}
	}

	stop := func() {
		if clusterFolder == "" {
			return
		}
		runClusterManager(
			suite,
			[]string{"--tls", "stop", "--cluster-folder", clusterFolder},
			true,
		)
	}
	return addrs, stop
}

// TestTlsMTlsClientCertAcceptedByServerRequiringOne asserts that a client
// with a valid client certificate connects to a server that requires one.
// This is the accepting half of an accepting plus rejecting pair; the
// rejecting case sits below.
func (suite *GlideTestSuite) TestTlsMTlsClientCertAcceptedByServerRequiringOne() {
	addr, stop := startMTlsRequiredStandalone(suite)
	defer stop()

	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)
	clientCert, clientKey, err := config.LoadClientCertificateAndKeyFromFile(certPath, keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().
		WithAddress(&addr).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMTlsMissingClientCertRejectedByServerRequiringOne asserts that a
// client without cert+key fails to connect to the same server. Without this
// check, the accepting case above would still pass against a server that
// silently ignored client certificates.
func (suite *GlideTestSuite) TestTlsMTlsMissingClientCertRejectedByServerRequiringOne() {
	addr, stop := startMTlsRequiredStandalone(suite)
	defer stop()

	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(caCert)
	advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClientConfig().
		WithAddress(&addr).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err = glide.NewClient(clientConfig)
	require.Error(suite.T(), err)
}

// TestTlsMTlsClusterClientCertAcceptedByServerRequiringOne is the cluster
// counterpart to TestTlsMTlsClientCertAcceptedByServerRequiringOne. The
// standalone case would not catch a cluster-only regression in mTLS setup.
func (suite *GlideTestSuite) TestTlsMTlsClusterClientCertAcceptedByServerRequiringOne() {
	addrs, stop := startMTlsRequiredCluster(suite)
	defer stop()

	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)
	certPath, keyPath, err := getClientCertAndKeyPaths()
	require.NoError(suite.T(), err)
	clientCert, clientKey, err := config.LoadClientCertificateAndKeyFromFile(certPath, keyPath)
	require.NoError(suite.T(), err)

	tlsConfig, err := config.NewTlsConfiguration().
		WithRootCertificates(caCert).
		WithMutualTLS(clientCert, clientKey)
	require.NoError(suite.T(), err)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClusterClientConfig().
		WithAddress(&addrs[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	assertConnected(suite.T(), client)
}

// TestTlsMTlsClusterMissingClientCertRejectedByServerRequiringOne is the
// cluster counterpart to
// TestTlsMTlsMissingClientCertRejectedByServerRequiringOne. It guards
// against a cluster-only mTLS setup that quietly accepts a missing cert.
func (suite *GlideTestSuite) TestTlsMTlsClusterMissingClientCertRejectedByServerRequiringOne() {
	addrs, stop := startMTlsRequiredCluster(suite)
	defer stop()

	caCert, err := getCaCertificate()
	require.NoError(suite.T(), err)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(caCert)
	advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)
	clientConfig := defaultClusterClientConfig().
		WithAddress(&addrs[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	_, err = glide.NewClusterClient(clientConfig)
	require.Error(suite.T(), err)
}
