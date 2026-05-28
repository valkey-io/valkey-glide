// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#[cfg(feature = "proto")]
include!("generated/mod.rs");
pub mod client;
pub mod otel_db_semantics;
#[cfg(feature = "socket-layer")]
pub mod rotating_buffer;
#[cfg(feature = "socket-layer")]
mod socket_listener;
#[cfg(feature = "socket-layer")]
pub use socket_listener::*;
pub mod address_resolver_registry;
pub mod compression;
pub mod errors;
pub mod scripts_container;
pub mod timeout_watchdog;
pub use client::ConnectionRequest;
pub mod cluster_scan_container;
pub mod iam;
pub mod pubsub;
pub mod request_type;
pub use telemetrylib::{
    DEFAULT_FLUSH_SIGNAL_INTERVAL_MS, DEFAULT_TRACE_SAMPLE_PERCENTAGE, GlideOpenTelemetry,
    GlideOpenTelemetryConfigBuilder, GlideOpenTelemetrySignalsExporter, GlideSpan, Telemetry,
};

#[cfg(feature = "proto")]
mod cache_metric_conversion {
    use crate::command_request::CacheMetricsType;
    use protobuf::Enum;
    use redis::{ErrorKind, RedisError, RedisResult, cache::CacheMetricType};

    impl From<CacheMetricsType> for CacheMetricType {
        fn from(value: CacheMetricsType) -> Self {
            match value {
                CacheMetricsType::HitRate => Self::HitRate,
                CacheMetricsType::MissRate => Self::MissRate,
                CacheMetricsType::EntryCount => Self::EntryCount,
                CacheMetricsType::Evictions => Self::Evictions,
                CacheMetricsType::Expirations => Self::Expirations,
                CacheMetricsType::TotalLookups => Self::TotalLookups,
            }
        }
    }

    /// Convert a protobuf CacheMetricsType integer value to a CacheMetricType enum.
    pub fn cache_metric_type_from_proto(value: i32) -> RedisResult<CacheMetricType> {
        CacheMetricsType::from_i32(value)
            .map(CacheMetricType::from)
            .ok_or_else(|| {
                RedisError::from((ErrorKind::InvalidClientConfig, "Invalid cache metrics type"))
            })
    }
}

#[cfg(feature = "proto")]
pub use cache_metric_conversion::cache_metric_type_from_proto;
