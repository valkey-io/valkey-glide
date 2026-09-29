# Migrating from redis-rs

The GLIDE Rust client's command surface is source-compatible with the vendored
redis-rs fork (v0.25.2): the `AsyncCommands` / `Commands` methods keep the same
names, generics, and argument lists, so most call sites migrate with only import
changes. GLIDE-branded types (`ValkeyValue`, `ValkeyResult`, `ToValkeyArgs`,
`FromValkeyValue`, …) replace their redis-rs counterparts.

## Limitations

Behaviours that differ from redis-rs and may need small changes when migrating:

- **No `IntoConnectionInfo` equivalent.** redis-rs accepts anything implementing
  its open `IntoConnectionInfo` trait (a URL string, `(host, port)`, a `url::Url`,
  a prebuilt `ConnectionInfo`, or your own type). GLIDE instead offers concrete
  constructors on both configurations: `from_url(url)` (accepts a `&str`,
  `String`, or `url::Url` — anything `AsRef<str>`) and `with_address(host, port)`;
  the cluster configuration also has `from_urls(urls)` for multiple seed URLs.
  Custom `IntoConnectionInfo` impls and prebuilt `ConnectionInfo` structs are not
  accepted — for anything a URL/host-port can't express (explicit database,
  credentials, protocol, TLS, …), build the configuration with `new(...)` and the
  `with_*` setters, which is GLIDE's connection-description type.
