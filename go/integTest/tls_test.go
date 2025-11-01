// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"os"
	"path/filepath"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestTlsWithoutCertificate_Standalone tests that connection fails without providing certificates
func (suite *GlideTestSuite) TestTlsWithoutCertificate_Standalone() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithRequestTimeout(5 * time.Second)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Standalone tests standalone client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Standalone() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	result, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)
}

// TestTlsWithMultipleCertificates_Standalone tests standalone client with multiple concatenated certificates
func (suite *GlideTestSuite) TestTlsWithMultipleCertificates_Standalone() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	client, err := glide.NewClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	result, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)
}

// TestTlsWithoutCertificate_Cluster tests that connection fails without providing certificates
func (suite *GlideTestSuite) TestTlsWithoutCertificate_Cluster() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithRequestTimeout(5 * time.Second)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err, "Expected connection to fail without certificate")
}

// TestTlsWithSelfSignedCertificate_Cluster tests cluster client with custom root certificates
func (suite *GlideTestSuite) TestTlsWithSelfSignedCertificate_Cluster() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
	advancedConfig := config.NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	result, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)
}

// TestTlsWithMultipleCertificates_Cluster tests cluster client with multiple concatenated certificates
func (suite *GlideTestSuite) TestTlsWithMultipleCertificates_Cluster() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	certData, err := getCaCertificate()
	if err != nil {
		suite.T().Skipf("CA certificate not found, skipping test: %v", err)
	}

	// Concatenate the same certificate twice to simulate multiple certificates
	multipleCerts := append(certData, '\n')
	multipleCerts = append(multipleCerts, certData...)

	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(multipleCerts)
	advancedConfig := config.NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	client, err := glide.NewClusterClient(clientConfig)
	require.NoError(suite.T(), err)
	require.NotNil(suite.T(), client)
	defer client.Close()

	result, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)
}

// TestTlsWithEmptyCertificate_Standalone tests that empty certificate array returns an error
func (suite *GlideTestSuite) TestTlsWithEmptyCertificate_Standalone() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithEmptyCertificate_Cluster tests that empty certificate array returns an error
func (suite *GlideTestSuite) TestTlsWithEmptyCertificate_Cluster() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	emptyCerts := []byte{}
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(emptyCerts)
	advancedConfig := config.NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	_, err := glide.NewClusterClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Standalone tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Standalone() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

	_, err := glide.NewClient(clientConfig)
	assert.Error(suite.T(), err)
}

// TestTlsWithInvalidCertificate_Cluster tests that invalid certificate returns an error
func (suite *GlideTestSuite) TestTlsWithInvalidCertificate_Cluster() {
	if !suite.tls {
		suite.T().Skip("TLS is not enabled, skipping TLS tests")
	}

	invalidCert := []byte("-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----")
	tlsConfig := config.NewTlsConfiguration().WithRootCertificates(invalidCert)
	advancedConfig := config.NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig).
		WithRequestTimeout(5 * time.Second)

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
