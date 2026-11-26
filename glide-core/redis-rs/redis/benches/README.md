# Cluster routing regression benchmarks

Run from `glide-core/redis-rs/redis`:

```sh
cargo bench --bench bench_cluster_routing -- --save-baseline fixed
# After a subsequent change, compare against that baseline on the same machine:
cargo bench --bench bench_cluster_routing -- --baseline fixed
```

This target needs no Valkey server. It measures MGET and MSET at 1,000, 4,000,
and 16,000 keys, independently covering all-argument indexed lookup, same-slot
routing, multi-slot routing, and construction of all per-slot commands from
precomputed routes. Command creation and route preparation are outside the
timed loops; output allocation and destruction are included. Small inline
arguments exercise the quadratic regression reported in #7780.

The defaults use 20 samples, 250 ms warm-up, and 750 ms measurement per case:
a nominal 24-second sampling budget for all 24 cases, plus analysis, plotting,
and compilation. Criterion can exceed that budget when individual iterations
are slow, especially when measuring the quadratic implementation. For more
precise measurements, override the settings explicitly:

```sh
cargo bench --bench bench_cluster_routing -- --sample-size 100 --warm-up-time 3 --measurement-time 5
```

Inspect both Criterion's baseline comparison and scaling across key counts.
For four times as many keys, linear work should take roughly four times as
long; the old repeated argument scans approach sixteen times as long. The
number of occupied slots and allocation overhead also affect routing and
splitting timings, so the indexed-lookup series is the clearest complexity
signal. Throughput is reported in keys per second, including for MSET, which
has two arguments per key.

Criterion reports statistically significant regressions but does not make
`cargo bench` exit unsuccessfully for them. These benchmarks provide a
performance regression signal, not a machine-dependent wall-clock unit-test
gate. Preserve the local `target/criterion` baseline when comparing revisions.
