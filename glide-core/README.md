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
