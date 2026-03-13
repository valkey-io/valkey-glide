# Rust core

## Tests

To run all tests:

```bash
cargo test
```

To run specific tests:

```bash
cargo test <pattern>
```

For example:

```bash
cargo test <module_name>          # Filter test(s) by module name
cargo test <test_name>            # Filter test(s) by function name
```

### IAM Authentication Tests

To run [IAM authentication tests](tests/test_client.rs) locally with mock credentials:

```bash
AWS_ACCESS_KEY_ID=test_access_key \
AWS_SECRET_ACCESS_KEY=test_secret_key \
AWS_SESSION_TOKEN=test_session_token \
cargo test test_iam_authentication
```

If any of these environment variables are not set, IAM authentication tests will be skipped.

**Note:** The credential values shown above (`test_access_key`, etc.) are arbitrary placeholder strings. The AWS SDK uses them to generate an authentication token, but the local test server doesn't validate the token. These tests verify that the IAM authentication flow works correctly (token generation, connection establishment, and token refresh), not that the credentials are valid.

### DNS Tests

To run [DNS tests](tests/test_dns.rs) locally:

1. Add the following entries to your hosts file:
   - Linux/macOS: `/etc/hosts`
   - Windows: `C:\Windows\System32\drivers\etc\hosts`

   ```text
   127.0.0.1 valkey.glide.test.tls.com
   127.0.0.1 valkey.glide.test.no_tls.com
   ::1 valkey.glide.test.tls.com
   ::1 valkey.glide.test.no_tls.com
   ```

2. Set the environment variable:

   ```bash
   export VALKEY_GLIDE_DNS_TESTS_ENABLED=1
   ```

If the environment variable is not set, DNS tests will be skipped.

## Recommended VSCode extensions

[rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language server.
[CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb) - Debugger.
[Even Better TOML](https://marketplace.visualstudio.com/items?itemName=tamasfe.even-better-toml) - TOML language support.
