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

// Skips the current test if DNS tests are not enabled via the environment.
func skipIfDnsTestsDisabled(suite *GlideTestSuite) {
	if os.Getenv("VALKEY_GLIDE_DNS_TESTS_ENABLED") == "" {
		suite.T().Skip("DNS tests are not enabled. Set VALKEY_GLIDE_DNS_TESTS_ENABLED to enable.")
	}
}

// Builds and returns a standalone client with the given hostname and TLS configuration.
func (suite *GlideTestSuite) buildStandaloneClient(hostname string, useTLS bool) (*glide.Client, error) {
	port := suite.standaloneHosts[0].Port
	if useTLS {
		port = suite.standaloneTlsHosts[0].Port
	}
	address := config.NodeAddress{
		Host: hostname,
		Port: port,
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
	port := suite.clusterHosts[0].Port
	if useTLS {
		port = suite.clusterTlsHosts[0].Port
	}
	address := config.NodeAddress{
		Host: hostname,
		Port: port,
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
	skipIfDnsTestsDisabled(suite)

	client, err := suite.buildStandaloneClient(HostnameNoTLS, false)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsConnectWithValidHostnameSucceeds_Cluster() {
	skipIfDnsTestsDisabled(suite)

	client, err := suite.buildClusterClient(HostnameNoTLS, false)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsConnectWithInvalidHostnameFails_Standalone() {
	skipIfDnsTestsDisabled(suite)

	_, err := suite.buildStandaloneClient(hostnameInvalid, false)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsConnectWithInvalidHostnameFails_Cluster() {
	skipIfDnsTestsDisabled(suite)

	_, err := suite.buildClusterClient(hostnameInvalid, false)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameInCertificateSucceeds_Standalone() {
	skipIfDnsTestsDisabled(suite)

	client, err := suite.buildStandaloneClient(HostnameTLS, true)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameInCertificateSucceeds_Cluster() {
	skipIfDnsTestsDisabled(suite)

	client, err := suite.buildClusterClient(HostnameTLS, true)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameNotInCertificateFails_Standalone() {
	skipIfDnsTestsDisabled(suite)

	_, err := suite.buildStandaloneClient(HostnameNoTLS, true)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestDnsTlsWithHostnameNotInCertificateFails_Cluster() {
	skipIfDnsTestsDisabled(suite)

	_, err := suite.buildClusterClient(HostnameNoTLS, true)
	assert.Error(suite.T(), err)
}
