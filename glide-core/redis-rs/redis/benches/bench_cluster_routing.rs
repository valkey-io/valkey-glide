//! CPU-only regression benchmarks for #7780; no server is required.
//!
//! Fourfold increases in key count distinguish linear (~4x time) from
//! quadratic (~16x time) argument access and routing. Build commands and
//! prepare split indices outside the timed loops so each stage is isolated.
use criterion::{criterion_group, criterion_main, BenchmarkId, Criterion, Throughput};
use redis::cluster_routing::{
    command_for_multi_slot_indices, MultipleNodeRoutingInfo, RoutingInfo,
};
use redis::Cmd;
use std::hint::black_box;
use std::time::Duration;

fn command(name: &str, keys: usize, same_slot: bool) -> Cmd {
    let mut cmd = redis::cmd(name);
    for i in 0..keys {
        // Fixed-width keys keep the hashing and copying work per key constant.
        let key = if same_slot {
            format!("{{same}}:{i:08}")
        } else {
            format!("key:{i:08}")
        };
        cmd.arg(key);
        if name == "MSET" {
            cmd.arg("value");
        }
    }
    cmd
}

fn bench_cluster_routing(c: &mut Criterion) {
    for name in ["MGET", "MSET"] {
        let mut group = c.benchmark_group(format!("cluster_routing/{name}"));
        for keys in [1_000, 4_000, 16_000] {
            let spread = command(name, keys, false);
            let same_slot = command(name, keys, true);
            let arg_count = spread.args_iter().len();
            let Some(RoutingInfo::MultiNode((MultipleNodeRoutingInfo::MultiSlot((routes, _)), _))) =
                RoutingInfo::for_routable(&spread)
            else {
                panic!("benchmark keys must span multiple slots");
            };
            assert!(routes.len() > 1);
            assert!(matches!(
                RoutingInfo::for_routable(&same_slot),
                Some(RoutingInfo::SingleNode(_))
            ));
            group.throughput(Throughput::Elements(keys as u64));

            group.bench_function(BenchmarkId::new("arg_idx", keys), |b| {
                b.iter(|| {
                    for i in 0..arg_count {
                        black_box(black_box(&spread).arg_idx(black_box(i)));
                    }
                });
            });
            for (label, cmd) in [("same_slot", &same_slot), ("multi_slot", &spread)] {
                group.bench_function(BenchmarkId::new(label, keys), |b| {
                    b.iter(|| black_box(RoutingInfo::for_routable(black_box(cmd))));
                });
            }
            group.bench_function(BenchmarkId::new("split_commands", keys), |b| {
                b.iter(|| {
                    for (_, indices) in &routes {
                        black_box(command_for_multi_slot_indices(
                            black_box(&spread),
                            indices.iter(),
                        ));
                    }
                });
            });
        }
        group.finish();
    }
}

// Detect a change in complexity without paying Criterion's default eight
// seconds per case. Twenty samples keep slow regressions practical to measure.
criterion_group! {
    name = benches;
    config = Criterion::default()
        .sample_size(20)
        .warm_up_time(Duration::from_millis(250))
        .measurement_time(Duration::from_millis(750));
    targets = bench_cluster_routing
}
criterion_main!(benches);
