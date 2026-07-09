use std::io::{Error, ErrorKind as IOErrorKind};

use rustls::RootCertStore;
use rustls_pki_types::pem::PemObject;
use rustls_pki_types::{CertificateDer, PrivateKeyDer};

use crate::{Client, ConnectionAddr, ConnectionInfo, ErrorKind, RedisError, RedisResult};

/// Structure to hold mTLS client _certificate_ and _key_ binaries in PEM format
///
#[derive(Clone)]
pub struct ClientTlsConfig {
    /// client certificate byte stream in PEM format
    pub client_cert: Vec<u8>,
    /// client key byte stream in PEM format
    pub client_key: Vec<u8>,
}

/// Structure to hold TLS certificates
/// - `client_tls`: binaries of clientkey and certificate within a `ClientTlsConfig` structure if mTLS is used
/// - `root_cert`: binary CA certificate in PEM format if CA is not in local truststore
///
#[derive(Clone)]
pub struct TlsCertificates {
    /// 'ClientTlsConfig' containing client certificate and key if mTLS is to be used
    pub client_tls: Option<ClientTlsConfig>,
    /// root certificate byte stream in PEM format if the local truststore is *not* to be used
    pub root_cert: Option<Vec<u8>>,
}

pub(crate) fn inner_build_with_tls(
    mut connection_info: ConnectionInfo,
    certificates: TlsCertificates,
) -> RedisResult<Client> {
    let tls_params = retrieve_tls_certificates(certificates)?;

    connection_info.addr = if let ConnectionAddr::TcpTls {
        host,
        port,
        insecure,
        ..
    } = connection_info.addr
    {
        ConnectionAddr::TcpTls {
            host,
            port,
            insecure,
            tls_params: Some(tls_params),
        }
    } else {
        return Err(RedisError::from((
            ErrorKind::InvalidClientConfig,
            "Constructing a TLS client requires a URL with the `rediss://` scheme",
        )));
    };

    Ok(Client { connection_info })
}

/// Retrieve TLS connection parameters from certificates.
///
/// Parses the provided TLS certificates and returns connection parameters
/// that can be used to establish secure connections.
pub fn retrieve_tls_certificates(certificates: TlsCertificates) -> RedisResult<TlsConnParams> {
    let TlsCertificates {
        client_tls,
        root_cert,
    } = certificates;

    let client_tls_params = if let Some(ClientTlsConfig {
        client_cert,
        client_key,
    }) = client_tls
    {
        // Parse certificates using rustls-pki-types v1.9.0+ API
        let certs = CertificateDer::pem_slice_iter(&client_cert);
        let client_cert_chain = certs.collect::<Result<Vec<_>, _>>().map_err(|e| {
            Error::new(
                IOErrorKind::Other,
                format!("Failed to parse certificate: {}", e),
            )
        })?;

        // Parse private key using rustls-pki-types v1.9.0+ API
        let client_key = PrivateKeyDer::from_pem_slice(&client_key).map_err(|e| {
            Error::new(
                IOErrorKind::Other,
                format!("Failed to parse private key: {}", e),
            )
        })?;

        Some(ClientTlsParams {
            client_cert_chain,
            client_key,
        })
    } else {
        None
    };

    let root_cert_store = if let Some(root_cert) = root_cert {
        // Parse root certificates using rustls-pki-types v1.9.0+ API
        let certs = CertificateDer::pem_slice_iter(&root_cert);
        let mut root_cert_store = RootCertStore::empty();
        for result in certs {
            let cert = result.map_err(|e| {
                Error::new(
                    IOErrorKind::Other,
                    format!("Failed to parse root certificate: {}", e),
                )
            })?;
            if root_cert_store.add(cert.to_owned()).is_err() {
                return Err(
                    Error::new(IOErrorKind::Other, "Unable to parse TLS trust anchors").into(),
                );
            }
        }

        Some(root_cert_store)
    } else {
        None
    };

    Ok(TlsConnParams {
        client_tls_params,
        root_cert_store,
    })
}

/// Validate that a parsed client certificate chain and private key form a usable
/// pair, i.e. the private key corresponds to the public key in the leaf certificate.
///
/// `rustls` does not perform this check when the PEM is *parsed*; a mismatched
/// cert/key pair parses fine and only fails later at handshake time. This uses
/// the same crypto provider path as connection setup (`CertifiedKey::from_der`,
/// which internally runs `keys_match`) so that a broken pair is rejected up front
/// rather than adopted and surfaced as a confusing handshake failure on the next
/// reconnect.
///
/// Returns `Ok(())` when the pair is consistent (or when consistency cannot be
/// determined for the key type, matching rustls' own lenient behavior), and an
/// error describing the mismatch otherwise. Params without client TLS material
/// are trivially valid.
pub fn validate_client_tls_params(params: &TlsConnParams) -> RedisResult<()> {
    let Some(client_tls) = params.client_tls_params.as_ref() else {
        return Ok(());
    };

    use rustls::crypto::CryptoProvider;
    use rustls::sign::CertifiedKey;

    // Prefer the process-installed default provider (aws-lc-rs is installed by
    // `create_rustls_config`); fall back to constructing one so validation works
    // even before the first connection has installed the default.
    let provider = CryptoProvider::get_default()
        .cloned()
        .unwrap_or_else(|| std::sync::Arc::new(rustls::crypto::aws_lc_rs::default_provider()));

    CertifiedKey::from_der(
        client_tls.client_cert_chain.clone(),
        client_tls.client_key.clone_key(),
        &provider,
    )
    .map_err(|err| {
        RedisError::from((
            ErrorKind::InvalidClientConfig,
            "TLS client certificate and private key do not match",
            err.to_string(),
        ))
    })?;

    Ok(())
}

#[derive(Debug)]
pub struct ClientTlsParams {
    pub(crate) client_cert_chain: Vec<CertificateDer<'static>>,
    pub(crate) client_key: PrivateKeyDer<'static>,
}

/// [`PrivateKeyDer`] does not implement `Clone` so we need to implement it manually.
impl Clone for ClientTlsParams {
    fn clone(&self) -> Self {
        use PrivateKeyDer::*;
        Self {
            client_cert_chain: self.client_cert_chain.clone(),
            client_key: match &self.client_key {
                Pkcs1(key) => Pkcs1(key.secret_pkcs1_der().to_vec().into()),
                Pkcs8(key) => Pkcs8(key.secret_pkcs8_der().to_vec().into()),
                Sec1(key) => Sec1(key.secret_sec1_der().to_vec().into()),
                _ => unreachable!(),
            },
        }
    }
}

/// TLS connection parameters containing client certificates and root certificate store.
#[derive(Debug, Clone)]
pub struct TlsConnParams {
    pub(crate) client_tls_params: Option<ClientTlsParams>,
    pub(crate) root_cert_store: Option<RootCertStore>,
}

impl TlsConnParams {
    /// Returns the DER-encoded client certificate chain, if client TLS material is
    /// present. Exposes only certificate bytes (never key material) so callers can
    /// compute a fingerprint of the adopted certificate for logging/change
    /// detection.
    pub fn client_cert_chain_der(&self) -> Vec<&[u8]> {
        match &self.client_tls_params {
            Some(client_tls) => client_tls
                .client_cert_chain
                .iter()
                .map(|cert| cert.as_ref())
                .collect(),
            None => Vec::new(),
        }
    }
}
