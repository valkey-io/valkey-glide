# Local integration-test dependencies

`glide-core` integration tests that spawn `redis-server` can use the locally
built Valkey checkout at `/Users/azach/dev/valkey`. The test harness invokes
the legacy executable name, so provide a temporary compatible alias first:

```bash
temp_bin="$(mktemp -d)"
ln -s /Users/azach/dev/valkey/src/valkey-server "$temp_bin/redis-server"
PATH="$temp_bin:$PATH" \
  cargo test --test test_monitor
```

The executable is `/Users/azach/dev/valkey/src/valkey-server`.
