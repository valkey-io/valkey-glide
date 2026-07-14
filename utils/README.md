# Test cluster utilities

This directory holds the shared, cross-language tooling used by the GLIDE test suites to spin up local Valkey/Redis clusters.

## `cluster_manager.py`

`cluster_manager.py` starts and stops standalone or cluster-mode servers for integration tests.
Every language client (Python, Java, Node, Go) drives this same script, so behavior stays consistent across them.

```bash
# Start a cluster-mode cluster
python3 utils/cluster_manager.py start --cluster-mode

# Start a standalone TLS cluster
python3 utils/cluster_manager.py --tls start

# Stop a cluster
python3 utils/cluster_manager.py stop --cluster-folder <folder>
```

Run `python3 utils/cluster_manager.py start --help` for the full flag list.

## TLS certificates

When a TLS cluster is started (`--tls`), `cluster_manager.py` generates a self-signed certificate authority and a set of certificates into `utils/tls_crts/` (if they do not already exist and are still valid).
The generated files are:

| File         | Purpose                                                                    |
|--------------|----------------------------------------------------------------------------|
| `ca.crt`     | Certificate authority. Clients trust this to validate the server cert.     |
| `ca.key`     | CA private key (used only to sign the server and client certs).            |
| `server.crt` | Server certificate, signed by the CA. Presented by each server node.       |
| `server.key` | Server private key.                                                        |
| `client.crt` | Dedicated client certificate, signed by the CA, with a `clientAuth` extendedKeyUsage. Presented by a client for mTLS. |
| `client.key` | Client private key.                                                        |

`ca.crt`, `server.crt`, and `server.key` keep their historical paths and contents, so existing server-cert TLS tests are unaffected.
The dedicated `client.crt`/`client.key` are new: they let clients present a real, CA-signed client certificate rather than repurposing the server cert.
A `tls_crts/` directory left over from an older checkout (which lacks `client.crt`) is detected and regenerated automatically.

## Client-certificate enforcement (mTLS)

By default the server is started with `--tls-auth-clients no`, meaning it never requires or verifies a client certificate.
To write a real mTLS test, where the server genuinely verifies the certificate the client presents, pass the `--tls-auth-clients` flag:

```bash
# Server requires and verifies a client certificate
python3 utils/cluster_manager.py --tls start --tls-auth-clients yes
```

Accepted values mirror valkey's own `--tls-auth-clients` server option:

| Value      | Behavior                                                            |
|------------|---------------------------------------------------------------------|
| `no`       | Default. Clients need not present a certificate.                    |
| `yes`      | Clients must present a certificate that validates against the CA.   |
| `optional` | The certificate is verified only if the client presents one.        |

A client connecting to a `--tls-auth-clients yes` cluster must supply `client.crt` + `client.key` (validated against `ca.crt`); connecting without a client certificate fails.
This is the building block language clients use for their mTLS integration tests.

### Example: Python

The Python test helpers in `python/tests/utils/utils.py` expose the dedicated client cert/key:

- `get_ca_certificate()` returns the CA (`ca.crt`).
- `get_client_auth_certificate()` returns the dedicated client cert (`client.crt`).
- `get_client_auth_key()` returns the dedicated client key (`client.key`).

`python/tests/async_tests/test_tls_client_auth.py` is a worked example: it starts a `--tls-auth-clients yes` cluster (via `ValkeyCluster(..., tls_auth_clients="yes")`) and asserts that connecting with the client cert succeeds while connecting without it fails.
Java, Node, and Go clients can build their mTLS tests on the same generated files and flag.
