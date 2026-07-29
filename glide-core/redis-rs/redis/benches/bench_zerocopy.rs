//! Focused parser decode benchmark for the zero-copy (Phase 1) work.
//!
//! Measures `parse_redis_value` on bulk-string-heavy RESP payloads across a
//! range of value sizes and array cardinalities. This isolates the cost that
//! Phase 1 targets: the per-bulk-string `to_vec()` allocation + memcpy in the
//! parser. Self-contained (manual RESP encoding), needs no server.
use criterion::{criterion_group, criterion_main, BatchSize, BenchmarkId, Criterion, Throughput};
use redis::parse_redis_value;

use bytes::BytesMut;
use redis::ValueCodec;
use tokio_util::codec::Decoder;

/// Encode a single RESP bulk string: `$<len>\r\n<payload>\r\n`.
fn encode_bulk(payload: &[u8], out: &mut Vec<u8>) {
    out.push(b'$');
    out.extend_from_slice(payload.len().to_string().as_bytes());
    out.extend_from_slice(b"\r\n");
    out.extend_from_slice(payload);
    out.extend_from_slice(b"\r\n");
}

/// Encode a RESP array header: `*<count>\r\n`.
fn encode_array_header(count: usize, out: &mut Vec<u8>) {
    out.push(b'*');
    out.extend_from_slice(count.to_string().as_bytes());
    out.extend_from_slice(b"\r\n");
}

/// A single bulk string of `size` bytes (the single-GET shape).
fn single_bulk(size: usize) -> Vec<u8> {
    let payload = vec![b'x'; size];
    let mut out = Vec::new();
    encode_bulk(&payload, &mut out);
    out
}

/// An array of `count` bulk strings each `size` bytes (the MGET/HGETALL shape).
fn array_of_bulks(count: usize, size: usize) -> Vec<u8> {
    let payload = vec![b'x'; size];
    let mut out = Vec::new();
    encode_array_header(count, &mut out);
    for _ in 0..count {
        encode_bulk(&payload, &mut out);
    }
    out
}

fn bench_single_get(c: &mut Criterion) {
    let mut group = c.benchmark_group("decode_single_get");
    for size in [64usize, 1024, 16 * 1024, 256 * 1024] {
        let input = single_bulk(size);
        group.throughput(Throughput::Bytes(input.len() as u64));
        group.bench_with_input(BenchmarkId::from_parameter(size), &input, |b, input| {
            b.iter(|| parse_redis_value(input).unwrap());
        });
    }
    group.finish();
}

fn bench_mget_array(c: &mut Criterion) {
    let mut group = c.benchmark_group("decode_mget_array");
    // (count, value_size): small values dominated by alloc count; large by memcpy.
    for (count, size) in [(1000usize, 64usize), (1000, 1024), (100, 16 * 1024)] {
        let input = array_of_bulks(count, size);
        group.throughput(Throughput::Bytes(input.len() as u64));
        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{count}x{size}")),
            &input,
            |b, input| {
                b.iter(|| parse_redis_value(input).unwrap());
            },
        );
    }
    group.finish();
}

/// Decode via the async `ValueCodec` path — this is the path the multiplexed
/// connection uses in production, and where the zero-copy change lands.
/// `iter_batched` keeps the buffer refill out of the timed region.
fn codec_decode_all(mut buf: BytesMut) -> usize {
    let mut codec = ValueCodec::default();
    let mut n = 0;
    while let Some(item) = codec.decode(&mut buf).unwrap() {
        item.unwrap();
        n += 1;
    }
    n
}

fn bench_codec_single_get(c: &mut Criterion) {
    let mut group = c.benchmark_group("codec_single_get");
    for size in [64usize, 1024, 16 * 1024, 256 * 1024] {
        let input = single_bulk(size);
        group.throughput(Throughput::Bytes(input.len() as u64));
        group.bench_with_input(BenchmarkId::from_parameter(size), &input, |b, input| {
            b.iter_batched(
                || BytesMut::from(&input[..]),
                |buf| assert_eq!(codec_decode_all(buf), 1),
                BatchSize::SmallInput,
            );
        });
    }
    group.finish();
}

fn bench_codec_mget_array(c: &mut Criterion) {
    let mut group = c.benchmark_group("codec_mget_array");
    for (count, size) in [(1000usize, 64usize), (1000, 1024), (100, 16 * 1024)] {
        let input = array_of_bulks(count, size);
        group.throughput(Throughput::Bytes(input.len() as u64));
        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{count}x{size}")),
            &input,
            |b, input| {
                b.iter_batched(
                    || BytesMut::from(&input[..]),
                    |buf| assert_eq!(codec_decode_all(buf), 1),
                    BatchSize::SmallInput,
                );
            },
        );
    }
    group.finish();
}

/// Pipelined shape: many independent single-GET replies in one read buffer,
/// decoded frame by frame (how a pipelined burst actually hits the codec).
fn bench_codec_pipeline(c: &mut Criterion) {
    let mut group = c.benchmark_group("codec_pipeline");
    for (count, size) in [(100usize, 1024usize), (100, 16 * 1024)] {
        let one = single_bulk(size);
        let input: Vec<u8> = one
            .iter()
            .cycle()
            .take(one.len() * count)
            .copied()
            .collect();
        group.throughput(Throughput::Bytes(input.len() as u64));
        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{count}x{size}")),
            &input,
            |b, input| {
                b.iter_batched(
                    || BytesMut::from(&input[..]),
                    |buf| assert_eq!(codec_decode_all(buf), count),
                    BatchSize::SmallInput,
                );
            },
        );
    }
    group.finish();
}

criterion_group!(
    benches,
    bench_single_get,
    bench_mget_array,
    bench_codec_single_get,
    bench_codec_mget_array,
    bench_codec_pipeline
);
criterion_main!(benches);
