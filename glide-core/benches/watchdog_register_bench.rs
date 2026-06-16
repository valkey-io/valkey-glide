// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use criterion::{Criterion, criterion_group, criterion_main};
use std::hint::black_box;
use std::time::{Duration, Instant};

use glide_core::timeout_watchdog::{LatencyTracker, TimeoutWatchdog};

fn bench_baseline_oneshot(c: &mut Criterion) {
    c.bench_function("baseline_oneshot", |b| {
        b.iter(|| {
            let (tx, rx) = tokio::sync::oneshot::channel::<()>();
            black_box((tx, rx));
        });
    });
}

fn bench_baseline_watchdog(c: &mut Criterion) {
    let (tx, _rx) = std::sync::mpsc::channel::<(Instant, tokio::sync::oneshot::Sender<()>)>();
    c.bench_function("baseline_watchdog_register", |b| {
        b.iter(|| {
            let (sender, rx) = tokio::sync::oneshot::channel::<()>();
            let deadline = Instant::now() + Duration::from_millis(250);
            let _ = tx.send((deadline, sender));
            black_box(rx);
        });
    });
}

fn bench_register_only(c: &mut Criterion) {
    let watchdog = TimeoutWatchdog::start();

    c.bench_function("register_only", |b| {
        b.iter(|| {
            let rx = watchdog.register(Duration::from_millis(250), Instant::now());
            black_box(rx);
        });
    });
}

fn bench_register_plus_phase_update(c: &mut Criterion) {
    let watchdog = TimeoutWatchdog::start();

    c.bench_function("register_plus_phase_update", |b| {
        b.iter(|| {
            let cmd = std::sync::Arc::new(redis::cmd("GET"));
            let now = Instant::now();
            let rx = watchdog.register(Duration::from_millis(250), now);
            cmd.watchdog_phase
                .store(redis::PHASE_SENT, std::sync::atomic::Ordering::Release);
            black_box((rx, cmd));
        });
    });
}

fn bench_latency_record(c: &mut Criterion) {
    let tracker = LatencyTracker::new(4096);
    for i in 0..5000 {
        tracker.record(Duration::from_micros(i));
    }
    c.bench_function("latency_record", |b| {
        b.iter(|| tracker.record(black_box(Duration::from_micros(150))));
    });
}

criterion_group!(
    benches,
    bench_baseline_oneshot,
    bench_baseline_watchdog,
    bench_register_only,
    bench_register_plus_phase_update,
    bench_latency_record,
);
criterion_main!(benches);
