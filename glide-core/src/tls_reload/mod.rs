// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Automatic reloading of the mTLS client certificate and private key from disk.
//!
//! This module mirrors the credential-rotation shape used by the IAM token
//! manager ([`crate::iam`]): a background task re-reads the certificate and key
//! files on a fixed interval, validates the new material, and — only on success —
//! swaps the cached [`redis::TlsConnParams`]. A lightweight [`CertReloadHandle`]
//! shares the cache with the reconnection path, which applies the freshest params
//! before every reconnect attempt (see `reconnecting_connection.rs`).
//!
//! Design decisions (see GitHub issue #6529):
//! - **Client certificate/key only.** Root/CA certificate reload is out of scope
//!   (higher blast radius, deferred deliberately, tracked in
//!   <https://github.com/valkey-io/valkey-glide/issues/6529>). Only the leaf cert
//!   and its key are re-read.
//! - **Periodic re-read, no file watcher.** Re-reading on a `tokio::interval` needs
//!   no new dependency. If a rotation is partially complete (e.g. the client
//!   certificate has been updated but the client key has not yet), validation simply
//!   rejects the inconsistent pair and the next tick retries.
//! - **Validate before swap, keep last-known-good on any failure.** The new
//!   material must fully parse *and* the private key must correspond to the leaf
//!   certificate. rustls parses the certificate and private key independently and
//!   does not check that they match; a mismatched pair only fails later at TLS
//!   handshake time, so we compare the key material early (the validation in this
//!   module) to catch a mismatch at load time. Any failure — missing file,
//!   unparseable PEM, or cert/key mismatch — logs and keeps the previously adopted
//!   params.
//! - **Never log cert/key material.** Adoption and rejection are logged with a
//!   SHA-256 fingerprint of the certificate chain DER only, mirroring the IAM
//!   module's logging discipline.

use logger_core::{log_debug, log_error, log_info, log_warn};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;
use thiserror::Error;
use tokio::sync::{Notify, RwLock};
use tokio::task::JoinHandle;
use tokio::time::{MissedTickBehavior, interval};

/// Default re-read interval in seconds (5 minutes), matching the IAM default.
const DEFAULT_RELOAD_INTERVAL_SECONDS: u32 = 300;
/// Warn if the interval exceeds 1 hour; rotated certs may then linger too long.
const WARNING_RELOAD_INTERVAL_SECONDS: u32 = 60 * 60;

/// Errors that can occur while loading or validating certificate material.
#[derive(Debug, Error)]
pub enum CertReloadError {
    /// A certificate or key file could not be read from disk.
    #[error("TLS cert reload error: failed to read {kind} file '{path}': {source}")]
    FileRead {
        kind: &'static str,
        path: String,
        source: std::io::Error,
    },

    /// The certificate or key material could not be parsed or is inconsistent.
    #[error("TLS cert reload error: {0}")]
    Invalid(String),
}

/// The paths, interval, and constant root material used by the reload task.
/// Cloneable so the background task can own an independent copy.
#[derive(Clone, Debug)]
pub(crate) struct ClientCertReloadState {
    cert_path: PathBuf,
    key_path: PathBuf,
    interval_seconds: u32,
    /// Root/CA certificate bytes (PEM), read once at construction and re-attached
    /// to every produced `TlsConnParams`. Root reload is out of scope, so this is
    /// constant for the client's lifetime.
    // TODO(#6529): when root/CA reload lands, this field becomes reloadable
    // material rather than a constant. https://github.com/valkey-io/valkey-glide/issues/6529
    root_cert: Option<Vec<u8>>,
}

/// Manages periodic reloading of the mTLS client certificate and key.
///
/// Holds the currently adopted [`redis::TlsConnParams`] plus its fingerprint,
/// spawns a background re-read task, and provides a shared [`CertReloadHandle`]
/// to the current TLS parameters that the connection layer reads on each reconnect
/// attempt.
pub struct CertReloadManager {
    /// Currently adopted (last-known-good) TLS params. Shared with all handles.
    cached_params: Arc<RwLock<redis::TlsConnParams>>,
    /// SHA-256 fingerprint of the adopted certificate chain DER (hex), for logging
    /// and change detection. Never contains key material.
    fingerprint: Arc<RwLock<String>>,
    /// Paths + interval used by the background task.
    state: ClientCertReloadState,
    /// Background re-read task handle.
    reload_task: Option<JoinHandle<()>>,
    /// Shutdown signal for graceful task termination.
    shutdown_notify: Arc<Notify>,
}

impl std::fmt::Debug for CertReloadManager {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CertReloadManager")
            .field("state", &self.state)
            .field("reload_task", &self.reload_task.is_some())
            .finish()
    }
}

impl CertReloadManager {
    /// Create a new manager, performing an initial load + validation of the
    /// certificate and key. Fails if the initial material is missing, unparseable,
    /// or mismatched — the client should not start with unusable mTLS material.
    ///
    /// # Arguments
    /// * `cert_path` - Path to the PEM client certificate (chain) file.
    /// * `key_path` - Path to the PEM client private key file.
    /// * `root_cert` - Optional root/CA certificate bytes (PEM), attached to every
    ///   produced params. Root reload is out of scope (tracked in
    ///   <https://github.com/valkey-io/valkey-glide/issues/6529>); this is constant.
    /// * `interval_seconds` - Optional re-read interval. Defaults to
    ///   `DEFAULT_RELOAD_INTERVAL_SECONDS` when unset.
    pub async fn new(
        cert_path: PathBuf,
        key_path: PathBuf,
        root_cert: Option<Vec<u8>>,
        interval_seconds: Option<u32>,
    ) -> Result<Self, CertReloadError> {
        let interval_seconds = interval_seconds
            .filter(|&s| s > 0)
            .unwrap_or(DEFAULT_RELOAD_INTERVAL_SECONDS);
        if interval_seconds >= WARNING_RELOAD_INTERVAL_SECONDS {
            log_warn(
                "TLS cert reload interval warning",
                format!(
                    "Reload interval of {interval_seconds} seconds exceeds recommended maximum of \
                     {WARNING_RELOAD_INTERVAL_SECONDS} seconds; rotated certificates may be adopted late."
                ),
            );
        }

        let state = ClientCertReloadState {
            cert_path,
            key_path,
            interval_seconds,
            root_cert,
        };

        let (params, fingerprint) = load_and_validate(
            &state.cert_path,
            &state.key_path,
            state.root_cert.as_deref(),
        )
        .await?;
        log_info(
            "TLS cert reload",
            format!("Loaded initial client certificate (fingerprint sha256:{fingerprint})"),
        );

        Ok(Self {
            cached_params: Arc::new(RwLock::new(params)),
            fingerprint: Arc::new(RwLock::new(fingerprint)),
            state,
            reload_task: None,
            shutdown_notify: Arc::new(Notify::new()),
        })
    }

    /// Start the background re-read task. Idempotent.
    pub fn start_reload_task(&mut self) {
        if self.reload_task.is_some() {
            return;
        }

        let state = self.state.clone();
        let cached_params = Arc::clone(&self.cached_params);
        let fingerprint = Arc::clone(&self.fingerprint);
        let shutdown_notify = Arc::clone(&self.shutdown_notify);

        let task = tokio::spawn(Self::reload_task(
            state,
            cached_params,
            fingerprint,
            shutdown_notify,
        ));
        self.reload_task = Some(task);
    }

    /// Background re-read loop.
    async fn reload_task(
        state: ClientCertReloadState,
        cached_params: Arc<RwLock<redis::TlsConnParams>>,
        fingerprint: Arc<RwLock<String>>,
        shutdown_notify: Arc<Notify>,
    ) {
        let mut timer = interval(Duration::from_secs(state.interval_seconds as u64));
        timer.set_missed_tick_behavior(MissedTickBehavior::Skip);
        // Skip the first immediate tick; we already loaded the initial material.
        timer.tick().await;

        loop {
            tokio::select! {
                _ = timer.tick() => {
                    Self::handle_reload(&state, &cached_params, &fingerprint).await;
                }
                _ = shutdown_notify.notified() => {
                    log_info("TLS cert reload task shutting down", "");
                    break;
                }
            }
        }
    }

    /// Re-read + validate once. On success (and only if the material actually
    /// changed) swap the cached params and stamp the new fingerprint. On any
    /// failure, log and keep the previously adopted material.
    async fn handle_reload(
        state: &ClientCertReloadState,
        cached_params: &Arc<RwLock<redis::TlsConnParams>>,
        fingerprint: &Arc<RwLock<String>>,
    ) {
        match load_and_validate(
            &state.cert_path,
            &state.key_path,
            state.root_cert.as_deref(),
        )
        .await
        {
            Ok((new_params, new_fingerprint)) => {
                let unchanged = {
                    let current = fingerprint.read().await;
                    *current == new_fingerprint
                };
                if unchanged {
                    log_debug(
                        "TLS cert reload",
                        "Reloaded certificate is unchanged; keeping current material",
                    );
                    return;
                }
                {
                    let mut params_guard = cached_params.write().await;
                    *params_guard = new_params;
                }
                {
                    let mut fp_guard = fingerprint.write().await;
                    *fp_guard = new_fingerprint.clone();
                }
                log_info(
                    "TLS cert reload",
                    format!(
                        "Adopted rotated client certificate (fingerprint sha256:{new_fingerprint})"
                    ),
                );
            }
            Err(err) => {
                // Keep last-known-good; a torn rotation (cert updated before key)
                // is expected to fail validation and recover on the next tick.
                log_error(
                    "TLS cert reload",
                    format!("Rejected new certificate material, keeping last-known-good: {err}"),
                );
            }
        }
    }

    /// Stop the background reload task gracefully.
    pub async fn stop_reload_task(&mut self) {
        if let Some(task) = self.reload_task.take() {
            self.shutdown_notify.notify_one();
            let _ = tokio::time::timeout(Duration::from_secs(5), task).await;
        }
    }

    /// Return the currently adopted TLS params (used to seed the initial connection).
    pub async fn get_params(&self) -> redis::TlsConnParams {
        self.cached_params.read().await.clone()
    }

    /// Create a lightweight handle sharing this manager's caches, for use by the
    /// reconnection path.
    pub fn get_handle(&self) -> CertReloadHandle {
        CertReloadHandle {
            cached_params: Arc::clone(&self.cached_params),
            fingerprint: Arc::clone(&self.fingerprint),
        }
    }
}

impl Drop for CertReloadManager {
    fn drop(&mut self) {
        // Signal shutdown; the task cleanup otherwise happens via the runtime when
        // the JoinHandle is dropped (we cannot await in Drop).
        self.shutdown_notify.notify_one();
    }
}

/// Lightweight handle to the reloaded certificate material, shared with the
/// reconnection path. Cloneable; all clones observe the same background refresh.
#[derive(Clone)]
pub struct CertReloadHandle {
    cached_params: Arc<RwLock<redis::TlsConnParams>>,
    fingerprint: Arc<RwLock<String>>,
}

impl CertReloadHandle {
    /// Return the freshest adopted TLS params.
    pub async fn current_params(&self) -> redis::TlsConnParams {
        self.cached_params.read().await.clone()
    }

    /// Return the fingerprint (hex SHA-256 of the cert chain DER) of the adopted
    /// material, for change detection / logging.
    pub async fn current_fingerprint(&self) -> String {
        self.fingerprint.read().await.clone()
    }
}

#[async_trait::async_trait]
impl redis::CertParamsProvider for CertReloadHandle {
    async fn current_tls_params(&self) -> Option<redis::TlsConnParams> {
        Some(self.current_params().await)
    }
}

/// Read both files, parse them into [`redis::TlsConnParams`], validate that the
/// key matches the leaf certificate, and compute the cert-chain fingerprint.
///
/// Reading both files together (rather than reacting to a single file event) is
/// what makes torn rotations recoverable: if the cert is new but the key is still
/// the old one, `validate_client_tls_params` rejects the pair and the caller keeps
/// last-known-good.
async fn load_and_validate(
    cert_path: &Path,
    key_path: &Path,
    root_cert: Option<&[u8]>,
) -> Result<(redis::TlsConnParams, String), CertReloadError> {
    // Async reads (`tokio::fs::read`): every caller is async, so we avoid blocking
    // the runtime worker on disk I/O. Torn-rotation protection comes from validating
    // the cert/key pair below, not from any read atomicity, so reading the two files
    // in separate awaits does not change the semantics.
    let client_cert =
        tokio::fs::read(cert_path)
            .await
            .map_err(|source| CertReloadError::FileRead {
                kind: "client certificate",
                path: cert_path.display().to_string(),
                source,
            })?;
    let client_key =
        tokio::fs::read(key_path)
            .await
            .map_err(|source| CertReloadError::FileRead {
                kind: "client key",
                path: key_path.display().to_string(),
                source,
            })?;

    let certificates = redis::TlsCertificates {
        client_tls: Some(redis::ClientTlsConfig {
            client_cert,
            client_key,
        }),
        root_cert: root_cert.map(|bytes| bytes.to_vec()),
    };

    let params = redis::retrieve_tls_certificates(certificates)
        .map_err(|err| CertReloadError::Invalid(err.to_string()))?;

    redis::validate_client_tls_params(&params)
        .map_err(|err| CertReloadError::Invalid(err.to_string()))?;

    let fingerprint = fingerprint_params(&params);
    Ok((params, fingerprint))
}

/// Compute a stable hex SHA-256 fingerprint over the leaf certificate chain DER.
/// Only certificate bytes are hashed — never key material.
fn fingerprint_params(params: &redis::TlsConnParams) -> String {
    let mut hasher = Sha256::new();
    for cert in params.client_cert_chain_der() {
        hasher.update(cert);
    }
    let digest = hasher.finalize();
    let mut out = String::with_capacity(digest.len() * 2);
    for byte in digest {
        use std::fmt::Write;
        let _ = write!(out, "{byte:02x}");
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write as _;

    // A self-signed cert + matching key pair (ECDSA P-256), and a second
    // independent pair whose key does NOT match the first cert. Generated once
    // with openssl for deterministic, offline tests.
    const CERT_A: &str = include_str!("test_data/cert_a.pem");
    const KEY_A: &str = include_str!("test_data/key_a.pem");
    const CERT_B: &str = include_str!("test_data/cert_b.pem");
    const KEY_B: &str = include_str!("test_data/key_b.pem");

    fn write_file(dir: &std::path::Path, name: &str, contents: &str) -> PathBuf {
        let path = dir.join(name);
        let mut f = std::fs::File::create(&path).unwrap();
        f.write_all(contents.as_bytes()).unwrap();
        f.sync_all().unwrap();
        path
    }

    #[tokio::test]
    async fn load_and_validate_accepts_matching_pair() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_A);

        let (_, fp) = load_and_validate(&cert, &key, None)
            .await
            .expect("matching pair should validate");
        assert_eq!(fp.len(), 64, "fingerprint should be hex sha256");
    }

    #[tokio::test]
    async fn load_and_validate_rejects_mismatched_pair() {
        let dir = tempfile::tempdir().unwrap();
        // Torn rotation: cert from pair A, key from pair B.
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_B);

        let err = load_and_validate(&cert, &key, None)
            .await
            .expect_err("mismatched pair must be rejected");
        assert!(matches!(err, CertReloadError::Invalid(_)), "got {err:?}");
    }

    #[tokio::test]
    async fn load_and_validate_rejects_unparseable_material() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", "not a pem file");
        let key = write_file(dir.path(), "key.pem", KEY_A);

        let err = load_and_validate(&cert, &key, None)
            .await
            .expect_err("garbage cert must be rejected");
        assert!(matches!(err, CertReloadError::Invalid(_)), "got {err:?}");
    }

    #[tokio::test]
    async fn load_and_validate_reports_missing_file() {
        let dir = tempfile::tempdir().unwrap();
        let key = write_file(dir.path(), "key.pem", KEY_A);
        let missing = dir.path().join("does_not_exist.pem");

        let err = load_and_validate(&missing, &key, None)
            .await
            .expect_err("missing cert must error");
        assert!(
            matches!(err, CertReloadError::FileRead { .. }),
            "got {err:?}"
        );
    }

    #[tokio::test]
    async fn distinct_certs_have_distinct_fingerprints() {
        let dir = tempfile::tempdir().unwrap();
        let cert_a = write_file(dir.path(), "a.pem", CERT_A);
        let key_a = write_file(dir.path(), "ka.pem", KEY_A);
        let cert_b = write_file(dir.path(), "b.pem", CERT_B);
        let key_b = write_file(dir.path(), "kb.pem", KEY_B);

        let (_, fp_a) = load_and_validate(&cert_a, &key_a, None).await.unwrap();
        let (_, fp_b) = load_and_validate(&cert_b, &key_b, None).await.unwrap();
        assert_ne!(fp_a, fp_b);
    }

    #[tokio::test]
    async fn manager_adopts_valid_rotation() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_A);

        // 1-second interval for a fast test.
        let mut manager = CertReloadManager::new(cert.clone(), key.clone(), None, Some(1))
            .await
            .unwrap();
        let handle = manager.get_handle();
        let initial_fp = handle.current_fingerprint().await;
        manager.start_reload_task();

        // Rotate to pair B (matching cert+key).
        write_file(dir.path(), "cert.pem", CERT_B);
        write_file(dir.path(), "key.pem", KEY_B);

        tokio::time::sleep(Duration::from_millis(2500)).await;

        let new_fp = handle.current_fingerprint().await;
        assert_ne!(initial_fp, new_fp, "rotated cert should be adopted");
        manager.stop_reload_task().await;
    }

    #[tokio::test]
    async fn manager_keeps_last_known_good_on_torn_rotation() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_A);

        let mut manager = CertReloadManager::new(cert.clone(), key.clone(), None, Some(1))
            .await
            .unwrap();
        let handle = manager.get_handle();
        let initial_fp = handle.current_fingerprint().await;
        manager.start_reload_task();

        // Torn write: new cert (B) lands but key is still A → mismatch.
        write_file(dir.path(), "cert.pem", CERT_B);

        tokio::time::sleep(Duration::from_millis(2500)).await;

        let fp_after = handle.current_fingerprint().await;
        assert_eq!(
            initial_fp, fp_after,
            "mismatched torn rotation must not be adopted"
        );

        // Once the key catches up, the pair validates and is adopted.
        write_file(dir.path(), "key.pem", KEY_B);
        tokio::time::sleep(Duration::from_millis(2500)).await;
        let fp_recovered = handle.current_fingerprint().await;
        assert_ne!(
            initial_fp, fp_recovered,
            "completed rotation should recover and adopt"
        );
        manager.stop_reload_task().await;
    }

    #[tokio::test]
    async fn manager_keeps_last_known_good_on_unparseable_material() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_A);

        let mut manager = CertReloadManager::new(cert.clone(), key.clone(), None, Some(1))
            .await
            .unwrap();
        let handle = manager.get_handle();
        let initial_fp = handle.current_fingerprint().await;
        manager.start_reload_task();

        // Corrupt the cert file.
        write_file(
            dir.path(),
            "cert.pem",
            "-----BEGIN CERTIFICATE-----\ngarbage\n",
        );

        tokio::time::sleep(Duration::from_millis(2500)).await;

        let fp_after = handle.current_fingerprint().await;
        assert_eq!(
            initial_fp, fp_after,
            "unparseable material must not be adopted"
        );
        manager.stop_reload_task().await;
    }

    #[tokio::test]
    async fn new_fails_on_initial_mismatch() {
        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_B);

        let result = CertReloadManager::new(cert, key, None, None).await;
        assert!(result.is_err(), "mismatched initial pair must fail");
    }

    /// Reconnection adoption: the material the reconnect path consumes reflects a
    /// rotated certificate after a successful reload, and retains last-known-good
    /// after a failed reload.
    ///
    /// The reconnect loop does not read the fingerprint; it reads the actual
    /// [`redis::TlsConnParams`] through the shared handle before each attempt
    /// (standalone: `ReconnectingConnection` calls `handle.current_params()`;
    /// cluster: it calls [`redis::CertParamsProvider::current_tls_params`]). This
    /// test exercises that exact seam — via the `CertParamsProvider` trait the
    /// reconnect path uses — rather than the background-adoption fingerprint view
    /// the other async tests cover. Reloads are driven directly (no timer sleeps)
    /// so the reconnect sequence is deterministic.
    #[tokio::test]
    async fn reconnect_reads_rotated_then_retains_last_known_good() {
        use redis::CertParamsProvider as _;

        let dir = tempfile::tempdir().unwrap();
        let cert = write_file(dir.path(), "cert.pem", CERT_A);
        let key = write_file(dir.path(), "key.pem", KEY_A);

        let manager = CertReloadManager::new(cert.clone(), key.clone(), None, Some(1))
            .await
            .unwrap();
        // The reconnect path holds a `CertReloadHandle` (vended as an
        // `Arc<dyn CertParamsProvider>`) and re-reads it before every attempt.
        let handle = manager.get_handle();

        // First reconnect attempt reads the initial pair A.
        let fp_first = fingerprint_params(
            &handle
                .current_tls_params()
                .await
                .expect("reconnect must see params"),
        );

        // A complete rotation (matching cert + key, pair B) lands on disk and the
        // reload adopts it.
        write_file(dir.path(), "cert.pem", CERT_B);
        write_file(dir.path(), "key.pem", KEY_B);
        CertReloadManager::handle_reload(
            &manager.state,
            &manager.cached_params,
            &manager.fingerprint,
        )
        .await;

        // Next reconnect attempt reads the rotated pair B — the adoption the PR
        // describes as taking effect "on the next reconnect".
        let fp_after_rotation = fingerprint_params(
            &handle
                .current_tls_params()
                .await
                .expect("reconnect must see params"),
        );
        assert_ne!(
            fp_first, fp_after_rotation,
            "reconnect must consume the rotated certificate after a successful reload"
        );

        // A failed rotation lands next: the cert reverts to A while the key stays
        // B, so the pair no longer matches and validation rejects it.
        write_file(dir.path(), "cert.pem", CERT_A);
        CertReloadManager::handle_reload(
            &manager.state,
            &manager.cached_params,
            &manager.fingerprint,
        )
        .await;

        // Next reconnect attempt still reads last-known-good pair B; the failed
        // reload never reaches the reconnect path.
        let fp_after_failed = fingerprint_params(
            &handle
                .current_tls_params()
                .await
                .expect("reconnect must see params"),
        );
        assert_eq!(
            fp_after_rotation, fp_after_failed,
            "reconnect must retain last-known-good material after a failed reload"
        );
    }
}
