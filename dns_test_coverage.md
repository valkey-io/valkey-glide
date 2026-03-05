# DNS Resolution and IP Address Connectivity Test Coverage

## Test Coverage Matrix

### Java

| Tests | Method |
|-------|--------|
| 1, 9 | `DnsTest.testConnectWithValidHostnameNoTls` |
| 2, 10 | `DnsTest.testConnectWithInvalidHostnameNoTls` |
| 3, 4 | `StandaloneClientTests.testStandaloneConnectWithIpAddressSucceeds` |
| 5, 13 | `DnsTest.testTlsConnectWithHostnameInCert` |
| 6, 14 | `DnsTest.testTlsConnectWithHostnameNotInCert` |
| 7, 8 | `StandaloneTlsCertificateTest.testStandaloneTlsWithIpAddressSucceeds` |
| 11, 12 | `ClusterClientTests.testClusterConnectWithIpAddressSucceeds` |
| 15, 16 | `ClusterTlsCertificateTest.testClusterTlsWithIpAddressSucceeds` |

**Files:**
- `java/integTest/src/test/java/glide/DnsTest.java`
- `java/integTest/src/test/java/glide/standalone/StandaloneClientTests.java`
- `java/integTest/src/test/java/glide/standalone/StandaloneTlsCertificateTest.java`
- `java/integTest/src/test/java/glide/cluster/ClusterClientTests.java`
- `java/integTest/src/test/java/glide/cluster/ClusterTlsCertificateTest.java`

---

### Python (Async)

| Tests | Method |
|-------|--------|
| 1, 9 | `TestDns.test_connect_with_valid_hostname_succeeds` |
| 2, 10 | `TestDns.test_connect_with_invalid_hostname_fails` |
| 3, 4, 11, 12 | `TestConnection.test_connect_with_ip_address_succeeds` |
| 5, 13 | `TestDns.test_tls_with_hostname_in_certificate_succeeds` |
| 6, 14 | `TestDns.test_tls_with_hostname_not_in_certificate_fails` |
| 7, 8, 15, 16 | `TestTls.test_tls_with_ip_address_connection_succeeds` |

**Files:**
- `python/tests/async_tests/test_dns.py`
- `python/tests/async_tests/test_async_client.py`
- `python/tests/async_tests/test_tls.py`

---

### Python (Sync)

| Tests | Method |
|-------|--------|
| 1, 9 | `TestSyncDns.test_connect_with_valid_hostname_succeeds` |
| 2, 10 | `TestSyncDns.test_connect_with_invalid_hostname_fails` |
| 3, 4, 11, 12 | `TestConnection.test_connect_with_ip_address_succeeds` |
| 5, 13 | `TestSyncDns.test_tls_with_hostname_in_certificate_succeeds` |
| 6, 14 | `TestSyncDns.test_tls_with_hostname_not_in_certificate_fails` |
| 7, 8, 15, 16 | `TestSyncTls.test_tls_with_ip_address_connection_succeeds` |

**Files:**
- `python/tests/sync_tests/test_sync_dns.py`
- `python/tests/sync_tests/test_sync_client.py`
- `python/tests/sync_tests/test_sync_tls.py`

---

### Node.js

| Tests | Method |
|-------|--------|
| 1 | `DNS Tests - Non-TLS: should connect with valid hostname - standalone` |
| 2 | `DNS Tests - Non-TLS: should fail with invalid hostname - standalone` |
| 3 | `GlideClient: should connect with IPv4 address` |
| 4 | `GlideClient: should connect with IPv6 address` |
| 5 | `DNS Tests - TLS: should connect with hostname in certificate SAN - standalone` |
| 6 | `DNS Tests - TLS: should fail with hostname NOT in certificate SAN - standalone` |
| 7 | `TlsCertificateTest: Standalone TLS with IP addresses: should connect with IPv4 address` |
| 8 | `TlsCertificateTest: Standalone TLS with IP addresses: should connect with IPv6 address` |
| 9 | `DNS Tests - Non-TLS: should connect with valid hostname - cluster` |
| 10 | `DNS Tests - Non-TLS: should fail with invalid hostname - cluster` |
| 11 | `GlideClusterClient: should connect with IPv4 address` |
| 12 | `GlideClusterClient: should connect with IPv6 address` |
| 13 | `DNS Tests - TLS: should connect with hostname in certificate SAN - cluster` |
| 14 | `DNS Tests - TLS: should fail with hostname NOT in certificate SAN - cluster` |
| 15 | `TlsCertificateTest: Cluster TLS with IP addresses: should connect with IPv4 address` |
| 16 | `TlsCertificateTest: Cluster TLS with IP addresses: should connect with IPv6 address` |

**Files:**
- `node/tests/Dns.test.ts`
- `node/tests/GlideClient.test.ts`
- `node/tests/GlideClusterClient.test.ts`
- `node/tests/TlsCertificateTest.test.ts`

---

### Go

| Tests | Method |
|-------|--------|
| 1 | `TestDnsConnectWithValidHostnameSucceeds_Standalone` |
| 2 | `TestDnsConnectWithInvalidHostnameFails_Standalone` |
| 3 | `TestConnectWithIPv4AddressSucceeds_Standalone` |
| 4 | `TestConnectWithIPv6AddressSucceeds_Standalone` |
| 5 | `TestDnsTlsWithHostnameInCertificateSucceeds_Standalone` |
| 6 | `TestDnsTlsWithHostnameNotInCertificateFails_Standalone` |
| 7 | `TestTlsWithIPv4AddressSucceeds_Standalone` |
| 8 | `TestTlsWithIPv6AddressSucceeds_Standalone` |
| 9 | `TestDnsConnectWithValidHostnameSucceeds_Cluster` |
| 10 | `TestDnsConnectWithInvalidHostnameFails_Cluster` |
| 11 | `TestConnectWithIPv4AddressSucceeds_Cluster` |
| 12 | `TestConnectWithIPv6AddressSucceeds_Cluster` |
| 13 | `TestDnsTlsWithHostnameInCertificateSucceeds_Cluster` |
| 14 | `TestDnsTlsWithHostnameNotInCertificateFails_Cluster` |
| 15 | `TestTlsWithIPv4AddressSucceeds_Cluster` |
| 16 | `TestTlsWithIPv6AddressSucceeds_Cluster` |

**Files:**
- `go/integTest/dns_test.go`
- `go/integTest/connection_test.go`
- `go/integTest/tls_test.go`

---

### Rust (glide-core)

| Tests | Method |
|-------|--------|
| 1 | `test_standalone_connect_with_valid_hostname_no_tls` |
| 2 | `test_standalone_connect_with_invalid_hostname_no_tls` |
| 3, 4 | `test_connection_with_ip_address_succeeds` |
| 5 | `test_standalone_tls_connect_with_hostname_in_cert` |
| 6 | `test_standalone_tls_connect_with_hostname_not_in_cert` |
| 7, 8 | `test_tls_connection_with_ip_address_succeeds` |
| 9 | `test_cluster_connect_with_valid_hostname_no_tls` |
| 10 | `test_cluster_connect_with_invalid_hostname_no_tls` |
| 11, 12 | `test_cluster_connection_with_ip_address_succeeds` |
| 13 | `test_cluster_tls_connect_with_hostname_in_cert` |
| 14 | `test_cluster_tls_connect_with_hostname_not_in_cert` |
| 15, 16 | `test_cluster_tls_connection_with_ip_address_succeeds` |

**Files:**
- `glide-core/tests/test_dns.rs`
- `glide-core/tests/test_standalone_client.rs`
- `glide-core/tests/test_cluster_client.rs`

---

## Summary

All 16 test cases are **fully covered** across all clients:
- ✅ Java
- ✅ Python (Async)
- ✅ Python (Sync)
- ✅ Node.js
- ✅ Go
- ✅ Rust (glide-core)

### Test Structure Consistency

All clients follow a consistent pattern:
1. **DNS tests** are in dedicated files (`DnsTest.java`, `test_dns.py`, `Dns.test.ts`, `dns_test.go`)
2. **TLS certificate tests** are in dedicated files for TLS-specific scenarios
3. **Non-TLS IP address tests** are in main client test files
4. All tests use parameterization to cover both standalone and cluster modes
5. All tests use parameterization to cover both IPv4 and IPv6 addresses

### Key Constants

All clients now use consistent constant names with the same values:
- `HOSTNAME_TLS` - Valid hostname in certificate SAN for TLS tests (`"valkey.glide.test.tls.com"`)
- `HOSTNAME_NO_TLS` - Valid hostname for non-TLS tests (`"valkey.glide.test.no_tls.com"`)
- `IP_ADDRESS_V4` - IPv4 address (`"127.0.0.1"`)
- `IP_ADDRESS_V6` - IPv6 address (`"::1"`)

**Constant Definitions by Client:**

| Client | File |
|--------|------|
| Java | `java/integTest/src/test/java/glide/Constants.java` |
| Python | `python/tests/constants.py` |
| Node.js | `node/tests/Constants.ts` |
| Go | `go/integTest/constants.go` |
| Rust | `glide-core/tests/constants.rs` |

### Test Execution

DNS tests require environment variable `VALKEY_GLIDE_DNS_TESTS_ENABLED` to be set and are skipped by default in CI.

**Documentation Consistency:**

All clients now have consistent documentation:
1. ✅ **"DNS Tests" section in DEVELOPER.md** (or README.md for Rust) with:
   - Link to test file(s)
   - Host file setup instructions
   - Environment variable setup
2. ✅ **Test file documentation** with:
   - "DNS resolution tests" description
   - Link back to DEVELOPER.md/README.md setup instructions

| Client | DEVELOPER.md Section | Test File Documentation |
|--------|---------------------|------------------------|
| Java | ✅ `java/DEVELOPER.md#dns-tests` | ✅ `DnsTest.java` links to `DEVELOPER.md#dns-tests` |
| Python | ✅ `python/DEVELOPER.md#dns-tests` | ✅ `test_dns.py` and `test_sync_dns.py` link to `DEVELOPER.md#dns-tests` |
| Node.js | ✅ `node/DEVELOPER.md#dns-tests` | ✅ `Dns.test.ts` links to `DEVELOPER.md#dns-tests` |
| Go | ✅ `go/DEVELOPER.md#dns-tests` | ✅ `dns_test.go` links to `DEVELOPER.md#dns-tests` |
| Rust | ✅ `glide-core/README.md#dns-tests` | ✅ `test_dns.rs` links to `README.md#dns-tests` |

**CI/CD Integration:**

All client workflows have DNS tests enabled with:
1. **`setup-dns-tests` action** - Configures `/etc/hosts` entries for test hostnames
2. **`VALKEY_GLIDE_DNS_TESTS_ENABLED="1"`** - Environment variable to enable DNS tests

| Client | Workflow File | DNS Setup | Env Var |
|--------|---------------|-----------|---------|
| Java | `.github/workflows/java.yml` | ✅ `.github/actions/setup-dns-tests` | ✅ `VALKEY_GLIDE_DNS_TESTS_ENABLED="1"` |
| Python | `.github/workflows/python.yml` | ✅ `.github/actions/setup-dns-tests` | ✅ `VALKEY_GLIDE_DNS_TESTS_ENABLED="1"` |
| Node.js | `.github/workflows/node.yml` | ✅ `.github/actions/setup-dns-tests` | ✅ `VALKEY_GLIDE_DNS_TESTS_ENABLED="1"` |
| Go | `.github/workflows/go.yml` | ✅ `.github/actions/setup-dns-tests` | ✅ `VALKEY_GLIDE_DNS_TESTS_ENABLED="1"` |
| Rust | `.github/workflows/rust.yml` | ✅ `.github/actions/setup-dns-tests` | ✅ `VALKEY_GLIDE_DNS_TESTS_ENABLED="1"` |

The `setup-dns-tests` action adds the following entries to the hosts file:
```
127.0.0.1 valkey.glide.test.tls.com
127.0.0.1 valkey.glide.test.no_tls.com
::1 valkey.glide.test.tls.com
::1 valkey.glide.test.no_tls.com
```
