// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

// A test client needs its address, its TLS setting and its certificate to agree. Deciding those
// separately is how a client ends up speaking plaintext to a TLS server, or speaking TLS with no
// certificate to verify the server against. This file is therefore the only place in the package that
// builds a client configuration: pick the constructor that matches the server you are connecting to and
// it sets all three.
//
//   - clientConfigFor / clusterClientConfigFor follow the run mode. Use these for the suite's fixtures.
//   - plaintextClientConfigFor / plaintextClusterClientConfigFor never speak TLS. Use these for a server
//     the test started itself without TLS, alongside skipIfTlsEnabled.
//   - tlsClientConfigFor / tlsClusterClientConfigFor always speak TLS and trust exactly what you pass.
//     Use these when the TLS wiring is what the test is checking.
//   - monitorClientConfigFor is for glide.NewMonitorClient, whose connection cannot carry a certificate.

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"testing/fstest"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// Default connection and request timeouts for testing.
// Use increased timeouts to reduce flakiness.
const (
	requestTimeout    = 5 * time.Second
	connectionTimeout = 10 * time.Second
)

// runUsesTls reports whether this run talks to TLS servers. The suite mirrors it on suite.tls, but
// package-level helpers and tests that are not suite methods need it too, so the flag is the source.
func runUsesTls() bool {
	return *tls
}

// runUsesOwnServers reports whether the suite starts the servers itself, which is what generates the
// fixture certificates under utils/tls_crts. Given endpoints belong to someone else and present a
// certificate the fixture CA did not sign.
func runUsesOwnServers() bool {
	return *standaloneHosts == "" && *clusterHosts == ""
}

var (
	caCertificateMu  sync.Mutex
	caCertificatePem []byte
)

// getCaCertificate returns the PEM bytes of the certificate authority that signed the test servers'
// certificates, from utils/tls_crts. A successful read is cached, since every TLS client in the run needs
// the same bytes. A failed read is not, because the fixtures only appear once the suite starts its servers,
// so caching the failure would leave every later client without a certificate.
func getCaCertificate() ([]byte, error) {
	caCertificateMu.Lock()
	defer caCertificateMu.Unlock()

	if caCertificatePem != nil {
		return caCertificatePem, nil
	}

	glideHome := os.Getenv("GLIDE_HOME_DIR")
	if glideHome == "" {
		glideHome = "../.."
	}

	absPath, err := filepath.Abs(filepath.Join(glideHome, "utils", "tls_crts", "ca.crt"))
	if err != nil {
		return nil, err
	}

	certData, err := config.LoadRootCertificatesFromFile(absPath)
	if err != nil {
		return nil, err
	}

	caCertificatePem = certData
	return caCertificatePem, nil
}

// runTlsConfiguration returns the root certificate a test client should trust: the fixture CA for a TLS run
// against the suite's own servers, and nil otherwise. Given endpoints get nil so the client verifies them
// against the platform's certificate authorities, which signed them.
func runTlsConfiguration() *config.TlsConfiguration {
	if !runUsesTls() || !runUsesOwnServers() {
		return nil
	}

	certData, err := getCaCertificate()
	if err != nil {
		// Returning no certificate here would surface much later as a verification error in whichever
		// test happened to connect first, so fail where the cause is.
		panic(fmt.Sprintf("cannot build a TLS test client without the fixture CA: %v", err))
	}

	return config.NewTlsConfiguration().WithRootCertificates(certData)
}

// advancedClientConfigWithoutTls carries the suite's timeouts and nothing about transport security. The
// builders below each layer their own certificate decision on top of it.
func advancedClientConfigWithoutTls() *config.AdvancedClientConfiguration {
	return config.NewAdvancedClientConfiguration().
		WithConnectionTimeout(connectionTimeout)
}

// advancedClusterClientConfigWithoutTls is the cluster counterpart of advancedClientConfigWithoutTls.
func advancedClusterClientConfigWithoutTls() *config.AdvancedClusterClientConfiguration {
	return config.NewAdvancedClusterClientConfiguration().
		WithConnectionTimeout(connectionTimeout)
}

// advancedClientConfig returns the advanced configuration a standalone test client starts from: the
// suite's timeouts, plus the fixture CA when the run uses TLS. Start here when a test needs to set
// another advanced option, so that setting one cannot drop the certificate.
func advancedClientConfig() *config.AdvancedClientConfiguration {
	advancedConfig := advancedClientConfigWithoutTls()

	if tlsConfig := runTlsConfiguration(); tlsConfig != nil {
		advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
	}

	return advancedConfig
}

// advancedClusterClientConfig is the cluster counterpart of advancedClientConfig.
func advancedClusterClientConfig() *config.AdvancedClusterClientConfiguration {
	advancedConfig := advancedClusterClientConfigWithoutTls()

	if tlsConfig := runTlsConfiguration(); tlsConfig != nil {
		advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
	}

	return advancedConfig
}

// clientConfigFor builds a standalone configuration for the given addresses, matching the run mode:
// plaintext in a plaintext run, TLS with the fixture CA under --tls.
func clientConfigFor(addresses ...config.NodeAddress) *config.ClientConfiguration {
	clientConfig := config.NewClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(runUsesTls()).
		WithAdvancedConfiguration(advancedClientConfig())

	return withAddresses(clientConfig, addresses)
}

// clusterClientConfigFor is the cluster counterpart of clientConfigFor.
func clusterClientConfigFor(addresses ...config.NodeAddress) *config.ClusterClientConfiguration {
	clientConfig := config.NewClusterClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(runUsesTls()).
		WithAdvancedConfiguration(advancedClusterClientConfig())

	return withClusterAddresses(clientConfig, addresses)
}

// plaintextClientConfigFor builds a standalone configuration that never speaks TLS, for a server the test
// started itself without TLS. Pair it with skipIfTlsEnabled, since under --tls there is no such server.
func plaintextClientConfigFor(addresses ...config.NodeAddress) *config.ClientConfiguration {
	clientConfig := config.NewClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(false).
		WithAdvancedConfiguration(advancedClientConfigWithoutTls())

	return withAddresses(clientConfig, addresses)
}

// plaintextClusterClientConfigFor is the cluster counterpart of plaintextClientConfigFor.
func plaintextClusterClientConfigFor(addresses ...config.NodeAddress) *config.ClusterClientConfiguration {
	clientConfig := config.NewClusterClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(false).
		WithAdvancedConfiguration(advancedClusterClientConfigWithoutTls())

	return withClusterAddresses(clientConfig, addresses)
}

// tlsClientConfigFor builds a standalone configuration that always speaks TLS and trusts exactly the
// given certificates, for a test whose subject is the TLS wiring itself. Pass nil to send no certificates
// at all, which is how the no-certificate cases assert that the handshake fails.
func tlsClientConfigFor(
	tlsConfig *config.TlsConfiguration,
	addresses ...config.NodeAddress,
) *config.ClientConfiguration {
	advancedConfig := advancedClientConfigWithoutTls()
	if tlsConfig != nil {
		advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
	}

	clientConfig := config.NewClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	return withAddresses(clientConfig, addresses)
}

// tlsClusterClientConfigFor is the cluster counterpart of tlsClientConfigFor.
func tlsClusterClientConfigFor(
	tlsConfig *config.TlsConfiguration,
	addresses ...config.NodeAddress,
) *config.ClusterClientConfiguration {
	advancedConfig := advancedClusterClientConfigWithoutTls()
	if tlsConfig != nil {
		advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
	}

	clientConfig := config.NewClusterClientConfiguration().
		WithRequestTimeout(requestTimeout).
		WithUseTLS(true).
		WithAdvancedConfiguration(advancedConfig)

	return withClusterAddresses(clientConfig, addresses)
}

// monitorClientConfigFor builds a configuration for glide.NewMonitorClient. A monitor connection carries
// only a TLS mode and no certificates, since glide-core's MonitorClient::new builds its ConnectionAddr
// with tls_params: None, so it has no way to trust the suite's self-signed CA. Under TLS these tests
// therefore skip verification, which still exercises MONITOR over a TLS socket. Once the monitor path
// accepts root certificates this should become clientConfigFor.
func monitorClientConfigFor(addresses ...config.NodeAddress) *config.ClientConfiguration {
	if !runUsesTls() {
		return plaintextClientConfigFor(addresses...)
	}

	return tlsClientConfigFor(config.NewTlsConfiguration().WithInsecureTLS(true), addresses...)
}

// The addresses are taken by value and indexed rather than ranged over, so each entry gets its own
// pointer instead of every entry sharing the loop variable.
func withAddresses(
	clientConfig *config.ClientConfiguration,
	addresses []config.NodeAddress,
) *config.ClientConfiguration {
	for i := range addresses {
		clientConfig = clientConfig.WithAddress(&addresses[i])
	}
	return clientConfig
}

// withClusterAddresses is the cluster counterpart of withAddresses.
func withClusterAddresses(
	clientConfig *config.ClusterClientConfiguration,
	addresses []config.NodeAddress,
) *config.ClusterClientConfiguration {
	for i := range addresses {
		clientConfig = clientConfig.WithAddress(&addresses[i])
	}
	return clientConfig
}

// clientConfigSeamFile is the only file in this package allowed to build a client configuration.
const clientConfigSeamFile = "client_config_seam_test.go"

// clientConfigConstructors are the builders that decide TLS for a client, and so have to be reached
// through this file rather than called directly.
var clientConfigConstructors = []string{
	"config.NewClientConfiguration(",
	"config.NewClusterClientConfiguration(",
	"config.NewAdvancedClientConfiguration(",
	"config.NewAdvancedClusterClientConfiguration(",
}

// findClientConfigBypasses returns "file:line" for every direct call to one of the constructors above,
// outside the seam file itself. It fails the test if there are no Go sources to read at all, since an
// empty scan would otherwise look like a clean result.
func findClientConfigBypasses(t *testing.T, sources fs.FS) []string {
	t.Helper()

	names, err := fs.Glob(sources, "*.go")
	require.NoError(t, err)
	require.NotEmpty(t, names, "found no package sources to check")

	bypasses := []string{}
	for _, name := range names {
		if name == clientConfigSeamFile {
			continue
		}

		contents, err := fs.ReadFile(sources, name)
		require.NoError(t, err)

		for i, line := range strings.Split(string(contents), "\n") {
			for _, constructor := range clientConfigConstructors {
				if strings.Contains(line, constructor) {
					bypasses = append(bypasses, fmt.Sprintf("%s:%d", name, i+1))
				}
			}
		}
	}

	return bypasses
}

// TestClientConfigsComeFromTheSeam keeps every test client wired by the helpers above. A test that builds
// its own configuration can quietly disagree with the run mode about TLS or about the certificate, and
// that shows up as a connection error with no obvious link to the test that caused it. Failing here is
// cheaper than waiting for a TLS run to expose it.
func TestClientConfigsComeFromTheSeam(t *testing.T) {
	for _, bypass := range findClientConfigBypasses(t, os.DirFS(".")) {
		t.Errorf(
			"%s builds a client configuration directly; take it from %s instead, so that the address, "+
				"the TLS setting and the certificate agree with each other",
			bypass, clientConfigSeamFile,
		)
	}
}

// TestClientConfigSeamCheckCatchesABypass covers the check above, so a change that stops it from matching
// shows up as a failure here rather than as a clean result.
func TestClientConfigSeamCheckCatchesABypass(t *testing.T) {
	sources := fstest.MapFS{
		"honest_test.go":     {Data: []byte("cfg := clientConfigFor(addr)\n")},
		"bypass_test.go":     {Data: []byte("\ncfg := config.NewClientConfiguration()\n")},
		"advanced_test.go":   {Data: []byte("config.NewAdvancedClusterClientConfiguration()\n")},
		clientConfigSeamFile: {Data: []byte("config.NewClientConfiguration()\n")},
	}

	require.Equal(
		t,
		[]string{"advanced_test.go:1", "bypass_test.go:2"},
		findClientConfigBypasses(t, sources),
	)
}
