// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Sizing of the Tokio worker pools that serve client work.
//!
//! Shared by every wrapper that builds a `LocalPoolHandle` for client connections:
//! the legacy socket listener in glide-core and the Node client's pool in
//! `node/rust-client`. Kept ungated (not behind the `socket-layer` feature) so both
//! consumers compile it.

/// Environment variable that overrides the size of the worker pool that runs the client
/// connections accepted by the socket listener, in the style of `GLIDE_TOKIO_WORKER_THREADS`.
const SOCKET_LISTENER_WORKERS_ENV: &str = "GLIDE_SOCKET_LISTENER_WORKERS";

/// Upper bound on the default worker pool size, which is the CPU count clamped to this value.
///
/// `LocalPoolHandle::new` eagerly builds one `current_thread` Tokio runtime per worker, and with
/// `enable_all` each runtime costs a thread and four file descriptors (the epoll fd, its registry
/// clone, the waker eventfd and a dup of the signal socketpair receiver). Sizing the pool to the
/// CPU count made the first client in a process cost about `7 + 4 * cpus` descriptors before it
/// connected to any server, which overflows the default 1024 `nofile` soft limit of containers
/// on hosts with a few hundred cores. The listener multiplexes the connections of a single client
/// process across the pool, so a handful of workers is plenty: eight keeps the previous sizing on
/// machines with up to eight CPUs and bounds the cost everywhere else.
const DEFAULT_MAX_SOCKET_LISTENER_WORKERS: usize = 8;

/// Upper bound on an explicit override, so a wrong value degrades to a bounded failure.
const MAX_SOCKET_LISTENER_WORKERS: usize = 64;

/// The size of the worker pool for the socket listener's client connections, and for
/// every wrapper worker pool that shares this sizing (the Node client pool).
pub fn socket_listener_pool_size() -> usize {
    socket_listener_pool_size_from(
        std::env::var(SOCKET_LISTENER_WORKERS_ENV).ok().as_deref(),
        num_cpus::get(),
    )
}

/// `override_value` is the value of [`SOCKET_LISTENER_WORKERS_ENV`], if set. As for
/// `GLIDE_TOKIO_WORKER_THREADS`, an unparsable value falls back to the default and `0` is
/// raised to `1`, since `LocalPoolHandle::new` panics on an empty pool.
///
/// An explicit value may exceed the default cap but is clamped to
/// [`MAX_SOCKET_LISTENER_WORKERS`].
fn socket_listener_pool_size_from(override_value: Option<&str>, num_cpus: usize) -> usize {
    override_value
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or_else(|| num_cpus.clamp(1, DEFAULT_MAX_SOCKET_LISTENER_WORKERS))
        .clamp(1, MAX_SOCKET_LISTENER_WORKERS)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pool_size_defaults_to_the_cpu_count_capped() {
        assert_eq!(socket_listener_pool_size_from(None, 1), 1);
        assert_eq!(socket_listener_pool_size_from(None, 4), 4);
        assert_eq!(
            socket_listener_pool_size_from(None, DEFAULT_MAX_SOCKET_LISTENER_WORKERS),
            DEFAULT_MAX_SOCKET_LISTENER_WORKERS
        );
        assert_eq!(
            socket_listener_pool_size_from(None, 288),
            DEFAULT_MAX_SOCKET_LISTENER_WORKERS
        );
    }

    #[test]
    fn pool_size_override_under_the_cap_is_honored() {
        assert_eq!(socket_listener_pool_size_from(Some("2"), 288), 2);
        assert_eq!(socket_listener_pool_size_from(Some("32"), 4), 32);
    }

    #[test]
    fn pool_size_override_is_capped_at_64() {
        assert_eq!(socket_listener_pool_size_from(Some("64"), 4), 64);
        assert_eq!(socket_listener_pool_size_from(Some("65"), 4), 64);
    }

    #[test]
    fn pool_size_ignores_an_invalid_override_and_is_never_zero() {
        assert_eq!(
            socket_listener_pool_size_from(Some("many"), 288),
            DEFAULT_MAX_SOCKET_LISTENER_WORKERS
        );
        assert_eq!(socket_listener_pool_size_from(Some(""), 4), 4);
        assert_eq!(socket_listener_pool_size_from(Some("-1"), 4), 4);
        assert_eq!(socket_listener_pool_size_from(Some("0"), 4), 1);
        assert_eq!(socket_listener_pool_size_from(None, 0), 1);
    }
}
