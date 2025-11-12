use std::io::{BufRead, Error, ErrorKind as IOErrorKind};

use rustls::RootCertStore;
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

    // DEBUG: Log certificate processing start
    println!("TLS DEBUG: Starting certificate processing");

    let client_tls_params = if let Some(ClientTlsConfig {
        client_cert,
        client_key,
    }) = client_tls
    {
        println!("TLS DEBUG: Processing client certificate (mTLS)");
        println!("TLS DEBUG: Client cert length: {} bytes", client_cert.len());
        println!("TLS DEBUG: Client key length: {} bytes", client_key.len());

        let buf = &mut client_cert.as_slice() as &mut dyn BufRead;
        let certs = rustls_pemfile::certs(buf);
        let client_cert_chain = match certs.collect::<Result<Vec<_>, _>>() {
            Ok(chain) => {
                println!(
                    "TLS DEBUG: Successfully parsed {} client certificate(s)",
                    chain.len()
                );
                chain
            }
            Err(e) => {
                println!("TLS DEBUG: Failed to parse client certificate: {:?}", e);
                return Err(e.into());
            }
        };

        let client_key =
            match rustls_pemfile::private_key(&mut client_key.as_slice() as &mut dyn BufRead)? {
                Some(key) => {
                    println!("TLS DEBUG: Successfully parsed client private key");
                    key
                }
                None => {
                    println!("TLS DEBUG: Failed to extract private key from PEM");
                    return Err(Error::new(
                        IOErrorKind::Other,
                        "Unable to extract private key from PEM file",
                    )
                    .into());
                }
            };

        Some(ClientTlsParams {
            client_cert_chain,
            client_key,
        })
    } else {
        println!("TLS DEBUG: No client certificate provided (server-only TLS)");
        None
    };

    let root_cert_store = if let Some(root_cert) = root_cert {
        println!("TLS DEBUG: Processing root certificate");
        println!("TLS DEBUG: Root cert length: {} bytes", root_cert.len());

        // Log first and last bytes for debugging
        if root_cert.len() > 0 {
            let preview_len = std::cmp::min(50, root_cert.len());
            println!(
                "TLS DEBUG: First {} bytes: {:?}",
                preview_len,
                &root_cert[..preview_len]
            );
            if root_cert.len() > 50 {
                println!(
                    "TLS DEBUG: Last {} bytes: {:?}",
                    preview_len,
                    &root_cert[root_cert.len() - preview_len..]
                );
            }
        }

        // Check for line ending types
        let has_crlf = root_cert.windows(2).any(|w| w == b"\r\n");
        let has_lf = root_cert.contains(&b'\n');
        let has_cr = root_cert.contains(&b'\r');
        println!(
            "TLS DEBUG: Line endings - CRLF: {}, LF: {}, CR: {}",
            has_crlf, has_lf, has_cr
        );

        let buf = &mut root_cert.as_slice() as &mut dyn BufRead;
        let certs = rustls_pemfile::certs(buf);
        let mut root_cert_store = RootCertStore::empty();
        let mut cert_count = 0;

        for result in certs {
            match result {
                Ok(cert) => {
                    cert_count += 1;
                    println!("TLS DEBUG: Parsed root certificate #{}", cert_count);
                    if root_cert_store.add(cert.to_owned()).is_err() {
                        println!(
                            "TLS DEBUG: Failed to add certificate #{} to trust store",
                            cert_count
                        );
                        return Err(Error::new(
                            IOErrorKind::Other,
                            "Unable to parse TLS trust anchors",
                        )
                        .into());
                    }
                    println!(
                        "TLS DEBUG: Successfully added certificate #{} to trust store",
                        cert_count
                    );
                }
                Err(e) => {
                    println!(
                        "TLS DEBUG: Failed to parse root certificate #{}: {:?}",
                        cert_count + 1,
                        e
                    );
                    return Err(e.into());
                }
            }
        }

        println!(
            "TLS DEBUG: Total root certificates processed: {}",
            cert_count
        );

        if cert_count == 0 {
            println!("TLS DEBUG: WARNING - No certificates were parsed from root_cert data");
        }

        Some(root_cert_store)
    } else {
        println!("TLS DEBUG: No root certificate provided (using system trust store)");
        None
    };

    println!("TLS DEBUG: Certificate processing completed successfully");

    Ok(TlsConnParams {
        client_tls_params,
        root_cert_store,
    })
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
