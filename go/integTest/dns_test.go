// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// DNS resolution tests.
// See DEVELOPER.md#dns-tests for instructions on how to run them locally.

package integTest

import (
	"os"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

const hostnameInvalid = "nonexistent.invalid"

// Skips the current test if DNS tests are not enabled or if
// the TLS configuration does not match the test requirements.
func skipIfNotEnabled(suite *GlideTestSuite, useTLS bool) {
	if os.Getenv("VALKEY_GLIDE_DNS_TESTS_ENABLED") == "" {
		suite.T().Skip("DNS tests are not enabled. Set VALKEY_GLIDE_DNS_TESTS_ENABLED to enable.")
	}

	// TODO #5509: TLS tests do not currently run as part of CI.
	if useTLS {
		skipIfTlsDisabled(suite)
	} else {
		skipIfTlsEnabled(suite)
	}
}

// Builds and returns a standalone client with the given hostname and TLS configuration.
func (suite *GlideTestSuite) buildStandaloneClient(hostname string, useTLS bool) (*glide.Client, error) {
	address := config.NodeAddress{
		Host: hostname,
		Port: suite.standaloneHosts[0].Port,
	}

	clientConfig := defaultClientConfig().WithAddress(&address)

	if useTLS {
		clientConfig.WithUseTLS(true)

		certData, err := getCaCertificate()
		if err != nil {
			return nil, err
		}

		tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
		advancedConfig := defaultAdvancedClientConfig().WithTlsConfiguration(tlsConfig)
		clientConfig.WithAdvancedConfiguration(advancedConfig)
	}

	return glide.NewClient(clientConfig)
}

// Builds and returns a cluster client with the given hostname and TLS configuration.
func (suite *GlideTestSuite) buildClusterClient(hostname string, useTLS bool) (*glide.ClusterClient, error) {
	address := config.NodeAddress{
		Host: hostname,
		Port: suite.clusterHosts[0].Port,
	}

	clientConfig := defaultClusterClientConfig().WithAddress(&address)

	if useTLS {
		clientConfig.WithUseTLS(true)

		certData, err := getCaCertificate()
		if err != nil {
			return nil, err
		}

		tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
		advancedConfig := defaultAdvancedClusterClientConfig().WithTlsConfiguration(tlsConfig)

		clientConfig.WithAdvancedConfiguration(advancedConfig)
	}

	return glide.NewClusterClient(clientConfig)
}

func (suite *GlideTestSuite) TestDnsConnectWithValidHostnameSucceeds_Standalone() {
	skipIfNotEnabled(suite, false)

	client, err := suite.buildStandaloneClient(HostnameNoTLS, false)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsConnectWithValidHostnameSucceeds_Cluster() {
	skipIfNotEnabled(suite, false)

	client, err := suite.buildClusterClient(HostnameNoTLS, false)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsConnectWithInvalidHostnameFails_Standalone() {
	skipIfNotEnabled(suite, false)

	_, err := suite.buildStandaloneClient(hostnameInvalid, false)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsConnectWithInvalidHostnameFails_Cluster() {
	skipIfNotEnabled(suite, false)

	_, err := suite.buildClusterClient(hostnameInvalid, false)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameInCertificateSucceeds_Standalone() {
	skipIfNotEnabled(suite, true)

	client, err := suite.buildStandaloneClient(HostnameTLS, true)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameInCertificateSucceeds_Cluster() {
	skipIfNotEnabled(suite, true)

	client, err := suite.buildClusterClient(HostnameTLS, true)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameNotInCertificateFails_Standalone() {
	skipIfNotEnabled(suite, true)

	_, err := suite.buildStandaloneClient(HostnameNoTLS, true)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameNotInCertificateFails_Cluster() {
	skipIfNotEnabled(suite, true)

	_, err := suite.buildClusterClient(HostnameNoTLS, true)
	assert.Error(suite.T(), err)
}
