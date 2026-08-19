// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pure-logic configuration tests mirroring Python `tests/test_config.py`.
//!
//! Every test asserts that the ergonomic builder structs lower into the
//! correct `glide_core::client::ConnectionRequest` via `to_request()`, plus
//! the standalone `From` conversions for each config enum.
use super::*;
use glide_core::client::{
    ConnectionRetryStrategy, PeriodicCheck, ReadFrom as CoreReadFrom, TlsMode,
};
use glide_core::iam::ServiceType as CoreServiceType;
use std::time::Duration;

// ---- addresses -------------------------------------------------------

#[test]
fn node_address_default_is_localhost_6379() {
    let a = NodeAddress::default();
    assert_eq!(a.host, "localhost");
    assert_eq!(a.port, 6379);
}

#[test]
fn tcp_nodelay_enabled_by_default() {
    // Guard against the Nagle regression: we build ConnectionRequest directly,
    // so we must explicitly enable TCP_NODELAY (bare struct default is false).
    assert!(
        GlideClientConfiguration::with_address("h", 1)
            .to_request()
            .tcp_nodelay
    );
    assert!(
        GlideClusterClientConfiguration::with_address("h", 1)
            .to_request()
            .tcp_nodelay
    );
}

#[test]
fn lib_name_is_glide_rust() {
    assert_eq!(
        GlideClientConfiguration::with_address("h", 1)
            .to_request()
            .lib_name
            .as_deref(),
        Some("GlideRust")
    );
    assert_eq!(
        GlideClusterClientConfiguration::with_address("h", 1)
            .to_request()
            .lib_name
            .as_deref(),
        Some("GlideRust")
    );
}

#[test]
fn with_address_single_host_port() {
    let req = GlideClientConfiguration::with_address("example.com", 6380).to_request();
    assert_eq!(req.addresses.len(), 1);
    assert_eq!(req.addresses[0].host, "example.com");
    assert_eq!(req.addresses[0].port, 6380);
}

#[test]
fn multiple_addresses_preserved_in_order() {
    let req = GlideClientConfiguration::new(vec![
        NodeAddress::new("a", 1),
        NodeAddress::new("b", 2),
        NodeAddress::new("c", 3),
    ])
    .to_request();
    assert_eq!(req.addresses.len(), 3);
    assert_eq!(req.addresses[0].host, "a");
    assert_eq!(req.addresses[0].port, 1);
    assert_eq!(req.addresses[1].host, "b");
    assert_eq!(req.addresses[2].host, "c");
    assert_eq!(req.addresses[2].port, 3);
}

#[test]
fn cluster_with_address_single_seed() {
    let req = GlideClusterClientConfiguration::with_address("seed", 7000).to_request();
    assert_eq!(req.addresses.len(), 1);
    assert_eq!(req.addresses[0].host, "seed");
    assert_eq!(req.addresses[0].port, 7000);
}

// ---- defaults --------------------------------------------------------

#[test]
fn standalone_request_defaults() {
    let req = GlideClientConfiguration::with_address("example.com", 6380).to_request();
    assert!(!req.cluster_mode_enabled);
    assert_eq!(req.tls_mode, Some(TlsMode::NoTls));
    assert_eq!(req.read_from, Some(CoreReadFrom::Primary));
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP3));
    assert_eq!(req.database_id, 0);
    assert!(req.authentication_info.is_none());
    assert!(req.periodic_checks.is_none());
    assert!(req.request_timeout.is_none());
    assert!(req.connection_timeout.is_none());
    assert!(req.connection_retry_strategy.is_none());
    assert!(req.client_name.is_none());
    assert!(!req.lazy_connect);
    assert!(req.inflight_requests_limit.is_none());
}

#[test]
fn cluster_request_defaults() {
    let req = GlideClusterClientConfiguration::with_address("seed", 7000).to_request();
    assert!(req.cluster_mode_enabled);
    assert_eq!(req.tls_mode, Some(TlsMode::NoTls));
    assert_eq!(req.read_from, Some(CoreReadFrom::Primary));
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP3));
    // Default periodic checks are Enabled.
    assert!(matches!(req.periodic_checks, Some(PeriodicCheck::Enabled)));
}

// ---- TLS modes -------------------------------------------------------

#[test]
fn tls_no_tls() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .tls(TlsConfig::NoTls)
        .to_request();
    assert_eq!(req.tls_mode, Some(TlsMode::NoTls));
}

#[test]
fn tls_secure() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .tls(TlsConfig::SecureTls)
        .to_request();
    assert_eq!(req.tls_mode, Some(TlsMode::SecureTls));
}

#[test]
fn tls_insecure() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .tls(TlsConfig::InsecureTls)
        .to_request();
    assert_eq!(req.tls_mode, Some(TlsMode::InsecureTls));
}

#[test]
fn tls_mode_from_conversion() {
    assert_eq!(TlsMode::from(TlsConfig::NoTls), TlsMode::NoTls);
    assert_eq!(TlsMode::from(TlsConfig::SecureTls), TlsMode::SecureTls);
    assert_eq!(TlsMode::from(TlsConfig::InsecureTls), TlsMode::InsecureTls);
}

#[test]
fn tls_applies_to_cluster() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .tls(TlsConfig::SecureTls)
        .to_request();
    assert_eq!(req.tls_mode, Some(TlsMode::SecureTls));
}

// ---- protocol --------------------------------------------------------

#[test]
fn protocol_resp2() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .protocol(ProtocolVersion::RESP2)
        .to_request();
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP2));
}

#[test]
fn protocol_resp3() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .protocol(ProtocolVersion::RESP3)
        .to_request();
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP3));
}

#[test]
fn protocol_default_is_resp3() {
    assert_eq!(ProtocolVersion::default(), ProtocolVersion::RESP3);
}

#[test]
fn protocol_from_conversion() {
    assert_eq!(
        redis::ProtocolVersion::from(ProtocolVersion::RESP2),
        redis::ProtocolVersion::RESP2
    );
    assert_eq!(
        redis::ProtocolVersion::from(ProtocolVersion::RESP3),
        redis::ProtocolVersion::RESP3
    );
}

// ---- read_from -------------------------------------------------------

#[test]
fn read_from_primary() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .read_from(ReadFrom::Primary)
        .to_request();
    assert_eq!(req.read_from, Some(CoreReadFrom::Primary));
}

#[test]
fn read_from_prefer_replica() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .read_from(ReadFrom::PreferReplica)
        .to_request();
    assert_eq!(req.read_from, Some(CoreReadFrom::PreferReplica));
}

#[test]
fn read_from_all_nodes() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .read_from(ReadFrom::AllNodes)
        .to_request();
    assert_eq!(req.read_from, Some(CoreReadFrom::AllNodes));
}

#[test]
fn read_from_az_affinity_carries_az() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .read_from(ReadFrom::AZAffinity("us-east-1a".into()))
        .to_request();
    assert_eq!(
        req.read_from,
        Some(CoreReadFrom::AZAffinity("us-east-1a".into()))
    );
}

#[test]
fn read_from_az_affinity_replicas_and_primary_carries_az() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .read_from(ReadFrom::AZAffinityReplicasAndPrimary("us-west-2b".into()))
        .to_request();
    assert_eq!(
        req.read_from,
        Some(CoreReadFrom::AZAffinityReplicasAndPrimary(
            "us-west-2b".into()
        ))
    );
}

#[test]
fn read_from_from_conversions() {
    assert_eq!(CoreReadFrom::from(ReadFrom::Primary), CoreReadFrom::Primary);
    assert_eq!(
        CoreReadFrom::from(ReadFrom::PreferReplica),
        CoreReadFrom::PreferReplica
    );
    assert_eq!(
        CoreReadFrom::from(ReadFrom::AllNodes),
        CoreReadFrom::AllNodes
    );
    assert_eq!(
        CoreReadFrom::from(ReadFrom::AZAffinity("z".into())),
        CoreReadFrom::AZAffinity("z".into())
    );
    assert_eq!(
        CoreReadFrom::from(ReadFrom::AZAffinityReplicasAndPrimary("z".into())),
        CoreReadFrom::AZAffinityReplicasAndPrimary("z".into())
    );
}

#[test]
fn read_from_default_is_primary() {
    assert_eq!(ReadFrom::default(), ReadFrom::Primary);
}

// ---- credentials -----------------------------------------------------

#[test]
fn credentials_password_only() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::password("secret"))
        .to_request();
    let auth = req.authentication_info.expect("auth set");
    assert!(auth.username.is_none());
    assert_eq!(auth.password.as_deref(), Some("secret"));
}

#[test]
fn credentials_username_and_password() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::new("alice", "hunter2"))
        .to_request();
    let auth = req.authentication_info.expect("auth set");
    assert_eq!(auth.username.as_deref(), Some("alice"));
    assert_eq!(auth.password.as_deref(), Some("hunter2"));
}

#[test]
fn credentials_apply_to_cluster() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::new("u", "p"))
        .to_request();
    let auth = req.authentication_info.expect("auth set");
    assert_eq!(auth.username.as_deref(), Some("u"));
    assert_eq!(auth.password.as_deref(), Some("p"));
}

// ---- IAM authentication ---------------------------------------------

#[test]
fn iam_credentials_elasticache_lower_correctly() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::iam(
            "iam-user",
            IamAuthConfig::new("my-cluster", "us-east-1", ServiceType::ElastiCache),
        ))
        .to_request();
    let auth = req.authentication_info.expect("auth set");
    assert_eq!(auth.username.as_deref(), Some("iam-user"));
    // IAM-only: no static password.
    assert!(auth.password.is_none());
    let iam = auth.iam_config.expect("iam config set");
    assert_eq!(iam.cluster_name, "my-cluster");
    assert_eq!(iam.region, "us-east-1");
    assert_eq!(iam.service_type, CoreServiceType::ElastiCache);
    assert_eq!(iam.refresh_interval_seconds, None);
}

#[test]
fn iam_credentials_memorydb_with_refresh_interval() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::iam(
            "u",
            IamAuthConfig::new("c", "eu-west-1", ServiceType::MemoryDB)
                .with_refresh_interval_seconds(300),
        ))
        .to_request();
    let iam = req
        .authentication_info
        .expect("auth set")
        .iam_config
        .expect("iam set");
    assert_eq!(iam.service_type, CoreServiceType::MemoryDB);
    assert_eq!(iam.region, "eu-west-1");
    assert_eq!(iam.refresh_interval_seconds, Some(300));
}

#[test]
fn iam_with_fallback_password_keeps_both() {
    // IAM takes precedence at auth time, but a fallback password may still be
    // provided and must be lowered alongside the IAM config.
    let creds = ServerCredentials::iam(
        "u",
        IamAuthConfig::new("c", "us-west-2", ServiceType::ElastiCache),
    )
    .with_password("fallback");
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(creds)
        .to_request();
    let auth = req.authentication_info.expect("auth set");
    assert_eq!(auth.password.as_deref(), Some("fallback"));
    assert!(auth.iam_config.is_some());
}

#[test]
fn iam_credentials_apply_to_cluster() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::iam(
            "u",
            IamAuthConfig::new("c", "ap-south-1", ServiceType::MemoryDB),
        ))
        .to_request();
    let iam = req
        .authentication_info
        .expect("auth set")
        .iam_config
        .expect("iam set");
    assert_eq!(iam.region, "ap-south-1");
    assert_eq!(iam.service_type, CoreServiceType::MemoryDB);
}

#[test]
fn non_iam_credentials_have_no_iam_config() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .credentials(ServerCredentials::password("p"))
        .to_request();
    assert!(
        req.authentication_info
            .expect("auth set")
            .iam_config
            .is_none()
    );
}

// ---- runtime pub/sub opt-in ------------------------------------------

#[test]
fn enable_pubsub_sets_flag_standalone() {
    let cfg = GlideClientConfiguration::with_address("h", 1);
    assert!(!cfg.force_pubsub_channel);
    assert!(cfg.enable_pubsub().force_pubsub_channel);
}

#[test]
fn enable_pubsub_sets_flag_cluster() {
    let cfg = GlideClusterClientConfiguration::with_address("h", 1);
    assert!(!cfg.force_pubsub_channel);
    assert!(cfg.enable_pubsub().force_pubsub_channel);
}

#[test]
fn credentials_debug_redacts_password() {
    let creds = ServerCredentials::new("alice", "super-secret");
    let shown = format!("{creds:?}");
    assert!(!shown.contains("super-secret"), "password leaked: {shown}");
    assert!(shown.contains("<redacted>"));
    assert!(shown.contains("alice"));
    // And transitively through the configuration's Debug.
    let cfg = GlideClientConfiguration::with_address("h", 1).credentials(creds);
    assert!(!format!("{cfg:?}").contains("super-secret"));
}

// ---- timeouts --------------------------------------------------------

#[test]
fn request_timeout_millis_conversion() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .request_timeout(Duration::from_millis(750))
        .to_request();
    assert_eq!(req.request_timeout, Some(750));
}

#[test]
fn request_timeout_from_seconds_converts_to_millis() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .request_timeout(Duration::from_secs(2))
        .to_request();
    assert_eq!(req.request_timeout, Some(2000));
}

#[test]
fn connection_timeout_millis_conversion() {
    let mut cfg = GlideClientConfiguration::with_address("h", 1);
    cfg.connection_timeout = Some(Duration::from_millis(250));
    let req = cfg.to_request();
    assert_eq!(req.connection_timeout, Some(250));
}

// ---- database_id -----------------------------------------------------

#[test]
fn database_id_standalone() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .database_id(7)
        .to_request();
    assert_eq!(req.database_id, 7);
}

#[test]
fn database_id_defaults_to_zero() {
    let req = GlideClientConfiguration::with_address("h", 1).to_request();
    assert_eq!(req.database_id, 0);
}

#[test]
fn cluster_never_sets_database_id() {
    // Cluster config has no database_id setter; request keeps the default 0.
    let req = GlideClusterClientConfiguration::with_address("h", 1).to_request();
    assert_eq!(req.database_id, 0);
}

// ---- client_name -----------------------------------------------------

#[test]
fn client_name_standalone() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .client_name("app-1")
        .to_request();
    assert_eq!(req.client_name.as_deref(), Some("app-1"));
}

#[test]
fn client_name_cluster() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .client_name("cluster-app")
        .to_request();
    assert_eq!(req.client_name.as_deref(), Some("cluster-app"));
}

// ---- lazy_connect ----------------------------------------------------

#[test]
fn lazy_connect_standalone() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .lazy_connect(true)
        .to_request();
    assert!(req.lazy_connect);
}

#[test]
fn lazy_connect_cluster() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .lazy_connect(true)
        .to_request();
    assert!(req.lazy_connect);
}

// ---- inflight_requests_limit ----------------------------------------

#[test]
fn inflight_requests_limit_standalone() {
    let mut cfg = GlideClientConfiguration::with_address("h", 1);
    cfg.inflight_requests_limit = Some(1000);
    let req = cfg.to_request();
    assert_eq!(req.inflight_requests_limit, Some(1000));
}

#[test]
fn inflight_requests_limit_cluster() {
    let mut cfg = GlideClusterClientConfiguration::with_address("h", 1);
    cfg.inflight_requests_limit = Some(42);
    let req = cfg.to_request();
    assert_eq!(req.inflight_requests_limit, Some(42));
}

// ---- cluster_mode_enabled -------------------------------------------

#[test]
fn cluster_mode_enabled_only_for_cluster() {
    let standalone = GlideClientConfiguration::with_address("h", 1).to_request();
    let cluster = GlideClusterClientConfiguration::with_address("h", 1).to_request();
    assert!(!standalone.cluster_mode_enabled);
    assert!(cluster.cluster_mode_enabled);
}

// ---- backoff / retry strategy ---------------------------------------

#[test]
fn backoff_strategy_field_mapping() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .reconnect_strategy(BackoffStrategy {
            num_of_retries: 5,
            factor: 2,
            exponent_base: 3,
            jitter_percent: Some(10),
        })
        .to_request();
    let s = req.connection_retry_strategy.expect("retry set");
    assert_eq!(s.number_of_retries, 5);
    assert_eq!(s.factor, 2);
    assert_eq!(s.exponent_base, 3);
    assert_eq!(s.jitter_percent, Some(10));
}

#[test]
fn backoff_strategy_without_jitter() {
    let req = GlideClientConfiguration::with_address("h", 1)
        .reconnect_strategy(BackoffStrategy {
            num_of_retries: 1,
            factor: 100,
            exponent_base: 2,
            jitter_percent: None,
        })
        .to_request();
    let s = req.connection_retry_strategy.expect("retry set");
    assert_eq!(s.number_of_retries, 1);
    assert_eq!(s.factor, 100);
    assert_eq!(s.exponent_base, 2);
    assert!(s.jitter_percent.is_none());
}

#[test]
fn backoff_strategy_from_conversion() {
    let s: ConnectionRetryStrategy = BackoffStrategy {
        num_of_retries: 9,
        factor: 8,
        exponent_base: 7,
        jitter_percent: Some(6),
    }
    .into();
    assert_eq!(s.number_of_retries, 9);
    assert_eq!(s.factor, 8);
    assert_eq!(s.exponent_base, 7);
    assert_eq!(s.jitter_percent, Some(6));
}

// ---- periodic checks (cluster only) ---------------------------------

#[test]
fn periodic_checks_enabled() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .periodic_checks(PeriodicChecks::Enabled)
        .to_request();
    assert!(matches!(req.periodic_checks, Some(PeriodicCheck::Enabled)));
}

#[test]
fn periodic_checks_disabled() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .periodic_checks(PeriodicChecks::Disabled)
        .to_request();
    assert!(matches!(req.periodic_checks, Some(PeriodicCheck::Disabled)));
}

#[test]
fn periodic_checks_manual_interval() {
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .periodic_checks(PeriodicChecks::ManualInterval(30))
        .to_request();
    match req.periodic_checks {
        Some(PeriodicCheck::ManualInterval(d)) => assert_eq!(d, Duration::from_secs(30)),
        other => panic!("unexpected periodic checks: {other:?}"),
    }
}

#[test]
fn periodic_checks_from_conversions() {
    assert!(matches!(
        PeriodicCheck::from(PeriodicChecks::Enabled),
        PeriodicCheck::Enabled
    ));
    assert!(matches!(
        PeriodicCheck::from(PeriodicChecks::Disabled),
        PeriodicCheck::Disabled
    ));
    match PeriodicCheck::from(PeriodicChecks::ManualInterval(5)) {
        PeriodicCheck::ManualInterval(d) => assert_eq!(d, Duration::from_secs(5)),
        other => panic!("unexpected: {other:?}"),
    }
}

#[test]
fn periodic_checks_default_is_enabled() {
    assert_eq!(PeriodicChecks::default(), PeriodicChecks::Enabled);
}

// ---- pub/sub subscriptions ------------------------------------------

#[test]
fn pubsub_subscriptions_default_none() {
    let req = GlideClientConfiguration::with_address("h", 1).to_request();
    assert!(req.pubsub_subscriptions.is_none());
}

#[test]
fn pubsub_subscriptions_lowered_to_core() {
    let subs = PubSubSubscriptions::new()
        .subscribe(PubSubChannelMode::Exact, "ch1")
        .subscribe(PubSubChannelMode::Exact, "ch2")
        .subscribe(PubSubChannelMode::Pattern, "news.*");
    let req = GlideClientConfiguration::with_address("h", 1)
        .subscriptions(subs)
        .to_request();
    let info = req.pubsub_subscriptions.expect("subscriptions set");
    let exact = info
        .get(&redis::PubSubSubscriptionKind::Exact)
        .expect("exact set");
    assert_eq!(exact.len(), 2);
    assert!(exact.contains(&b"ch1".to_vec()));
    assert!(exact.contains(&b"ch2".to_vec()));
    let pattern = info
        .get(&redis::PubSubSubscriptionKind::Pattern)
        .expect("pattern set");
    assert!(pattern.contains(&b"news.*".to_vec()));
}

#[test]
fn pubsub_subscriptions_cluster_sharded() {
    let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Sharded, "shard-ch");
    let req = GlideClusterClientConfiguration::with_address("h", 1)
        .subscriptions(subs)
        .to_request();
    let info = req.pubsub_subscriptions.expect("subscriptions set");
    assert!(
        info.get(&redis::PubSubSubscriptionKind::Sharded)
            .unwrap()
            .contains(&b"shard-ch".to_vec())
    );
}

// ---- full end-to-end lowering ---------------------------------------

#[test]
fn standalone_request_full() {
    let cfg =
        GlideClientConfiguration::new(vec![NodeAddress::new("a", 1), NodeAddress::new("b", 2)])
            .tls(TlsConfig::SecureTls)
            .credentials(ServerCredentials::new("user", "pass"))
            .read_from(ReadFrom::PreferReplica)
            .protocol(ProtocolVersion::RESP2)
            .database_id(3)
            .client_name("myclient")
            .request_timeout(Duration::from_millis(500))
            .lazy_connect(true);
    let req = cfg.to_request();
    assert_eq!(req.addresses.len(), 2);
    assert_eq!(req.tls_mode, Some(TlsMode::SecureTls));
    assert_eq!(req.read_from, Some(CoreReadFrom::PreferReplica));
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP2));
    assert_eq!(req.database_id, 3);
    assert_eq!(req.client_name.as_deref(), Some("myclient"));
    assert_eq!(req.request_timeout, Some(500));
    assert!(req.lazy_connect);
    assert!(!req.cluster_mode_enabled);
    let auth = req.authentication_info.expect("auth set");
    assert_eq!(auth.username.as_deref(), Some("user"));
    assert_eq!(auth.password.as_deref(), Some("pass"));
}

#[test]
fn cluster_request_full() {
    let cfg = GlideClusterClientConfiguration::with_address("seed", 7000)
        .tls(TlsConfig::InsecureTls)
        .credentials(ServerCredentials::password("p"))
        .read_from(ReadFrom::AZAffinity("use1-az1".into()))
        .protocol(ProtocolVersion::RESP2)
        .client_name("c")
        .periodic_checks(PeriodicChecks::ManualInterval(12))
        .request_timeout(Duration::from_millis(100))
        .lazy_connect(true);
    let req = cfg.to_request();
    assert!(req.cluster_mode_enabled);
    assert_eq!(req.tls_mode, Some(TlsMode::InsecureTls));
    assert_eq!(
        req.read_from,
        Some(CoreReadFrom::AZAffinity("use1-az1".into()))
    );
    assert_eq!(req.protocol, Some(redis::ProtocolVersion::RESP2));
    assert_eq!(req.client_name.as_deref(), Some("c"));
    assert_eq!(req.request_timeout, Some(100));
    assert!(req.lazy_connect);
    match req.periodic_checks {
        Some(PeriodicCheck::ManualInterval(d)) => assert_eq!(d, Duration::from_secs(12)),
        other => panic!("unexpected periodic checks: {other:?}"),
    }
    let auth = req.authentication_info.expect("auth set");
    assert!(auth.username.is_none());
    assert_eq!(auth.password.as_deref(), Some("p"));
}

// ---- redis-rs URL parity (`from_url` / `from_urls`) ----

#[test]
fn from_url_basic() {
    let cfg = GlideClientConfiguration::from_url("redis://localhost:6380").unwrap();
    assert_eq!(cfg.addresses.len(), 1);
    assert_eq!(cfg.addresses[0].host, "localhost");
    assert_eq!(cfg.addresses[0].port, 6380);
    assert_eq!(cfg.tls, TlsConfig::NoTls);
    assert_eq!(cfg.database_id, 0);
    assert!(cfg.credentials.is_none());
}

#[test]
fn from_url_default_port_and_db() {
    let cfg = GlideClientConfiguration::from_url("redis://example.com/3").unwrap();
    assert_eq!(cfg.addresses[0].port, 6379);
    assert_eq!(cfg.database_id, 3);
}

#[test]
fn from_url_credentials() {
    let cfg = GlideClientConfiguration::from_url("redis://user:secret@h:1234").unwrap();
    let creds = cfg.credentials.expect("credentials parsed");
    assert_eq!(creds.username.as_deref(), Some("user"));
    assert_eq!(creds.password.as_deref(), Some("secret"));

    // Password-only (empty username) form.
    let cfg = GlideClientConfiguration::from_url("redis://:secret@h:1234").unwrap();
    let creds = cfg.credentials.expect("credentials parsed");
    assert!(creds.username.is_none());
    assert_eq!(creds.password.as_deref(), Some("secret"));
}

#[test]
fn from_url_tls_schemes() {
    let cfg = GlideClientConfiguration::from_url("rediss://secure-host:6379").unwrap();
    assert_eq!(cfg.tls, TlsConfig::SecureTls);

    let cfg = GlideClientConfiguration::from_url("rediss://secure-host:6379/#insecure").unwrap();
    assert_eq!(cfg.tls, TlsConfig::InsecureTls);
}

#[test]
fn from_url_invalid_rejected() {
    assert!(GlideClientConfiguration::from_url("not a url").is_err());
    assert!(GlideClientConfiguration::from_url("http://host").is_err());
    assert!(GlideClientConfiguration::from_url("redis+unix:///tmp/redis.sock").is_err());
}

#[test]
fn from_urls_cluster_multiple_seeds() {
    let cfg =
        GlideClusterClientConfiguration::from_urls(["redis://n1:7000", "redis://n2:7001"]).unwrap();
    assert_eq!(cfg.addresses.len(), 2);
    assert_eq!(cfg.addresses[1].host, "n2");
    assert_eq!(cfg.addresses[1].port, 7001);
}

#[test]
fn from_urls_cluster_rejects_db_and_empty() {
    assert!(GlideClusterClientConfiguration::from_urls(["redis://n1:7000/5"]).is_err());
    assert!(GlideClusterClientConfiguration::from_urls(Vec::<&str>::new()).is_err());
    // A non-zero db on any URL (not just the first) is rejected.
    assert!(
        GlideClusterClientConfiguration::from_urls(["redis://n1:7000", "redis://n2:7001/5"])
            .is_err()
    );
}

#[test]
fn from_urls_cluster_rejects_conflicting_settings() {
    // Matches the fork's `ClusterClient` validation: settings must be
    // identical across all initial-node URLs.
    assert!(
        GlideClusterClientConfiguration::from_urls([
            "redis://:pw1@n1:7000",
            "redis://:pw2@n2:7001",
        ])
        .is_err(),
        "different passwords must be rejected"
    );
    assert!(
        GlideClusterClientConfiguration::from_urls([
            "redis://u1:pw@n1:7000",
            "redis://u2:pw@n2:7001",
        ])
        .is_err(),
        "different usernames must be rejected"
    );
    assert!(
        GlideClusterClientConfiguration::from_urls(["redis://n1:7000", "rediss://n2:7001"])
            .is_err(),
        "mixed TLS modes must be rejected"
    );
    // Identical settings on all URLs remain accepted.
    assert!(
        GlideClusterClientConfiguration::from_urls([
            "redis://u:pw@n1:7000",
            "redis://u:pw@n2:7001",
        ])
        .is_ok()
    );
}

// ---- mutual TLS lowering ----

#[test]
fn client_identity_lowered_into_request() {
    let cert = b"-----BEGIN CERTIFICATE-----".to_vec();
    let key = b"-----BEGIN PRIVATE KEY-----".to_vec();
    let req = GlideClientConfiguration::with_address("h", 6379)
        .tls(TlsConfig::SecureTls)
        .client_identity(cert.clone(), key.clone())
        .to_request();
    assert_eq!(req.client_cert, cert);
    assert_eq!(req.client_key, key);

    let req = GlideClusterClientConfiguration::with_address("h", 7000)
        .tls(TlsConfig::SecureTls)
        .client_identity(cert.clone(), key.clone())
        .to_request();
    assert_eq!(req.client_cert, cert);
    assert_eq!(req.client_key, key);
}

#[test]
fn no_client_identity_leaves_request_empty() {
    let req = GlideClientConfiguration::with_address("h", 6379).to_request();
    assert!(req.client_cert.is_empty());
    assert!(req.client_key.is_empty());
}

#[test]
fn client_identity_debug_redacts_private_key() {
    let key_material = "SUPER-SECRET-KEY-MATERIAL";
    let cfg = GlideClientConfiguration::with_address("h", 6379)
        .client_identity(b"cert-bytes".to_vec(), key_material.as_bytes().to_vec());
    // Both the identity's own Debug and the config's derived Debug must
    // redact the private key.
    let identity_dbg = format!("{:?}", cfg.client_identity.as_ref().unwrap());
    let config_dbg = format!("{cfg:?}");
    for rendered in [&identity_dbg, &config_dbg] {
        assert!(
            rendered.contains("<redacted>"),
            "missing redaction: {rendered}"
        );
        assert!(
            !rendered.contains(key_material)
                && !rendered.contains(&format!("{:?}", key_material.as_bytes())),
            "private key leaked through Debug: {rendered}"
        );
    }
}
