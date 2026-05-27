// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::sync::Arc;

use super::{NodeAddress, TlsMode};
use futures::StreamExt;
use redis::{ConnectionAddr, ConnectionInfo, RedisConnectionInfo, RedisResult};
use tokio::sync::oneshot;

#[derive(Debug)]
pub struct MonitorLine {
    pub timestamp: f64,
    pub db: i64,
    pub client_addr: String,
    pub command: String,
    pub args: Vec<String>,
}

impl MonitorLine {
    /// Parse a raw monitor line. Returns None if the format is unrecognized.
    pub fn parse(raw: &str) -> Option<Self> {
        let s = raw.strip_prefix('+').unwrap_or(raw);
        let (ts_part, rest) = s.split_once(" [")?;
        let timestamp: f64 = ts_part.trim().parse().ok()?;
        let (bracket_part, args_part) = rest.split_once(']')?;
        let (db_str, client_addr) = bracket_part.split_once(' ')?;
        let db: i64 = db_str.trim().parse().ok()?;
        let client_addr = client_addr.trim().to_string();
        let tokens = parse_quoted_tokens(args_part.trim());
        let mut iter = tokens.into_iter();
        let command = iter.next()?;
        let args: Vec<String> = iter.collect();
        Some(MonitorLine {
            timestamp,
            db,
            client_addr,
            command,
            args,
        })
    }
}

fn parse_quoted_tokens(s: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut chars = s.chars().peekable();
    while let Some(&c) = chars.peek() {
        if c == '"' {
            chars.next();
            let mut token = String::new();
            loop {
                match chars.next() {
                    None | Some('"') => break,
                    Some('\\') => {
                        if let Some(escaped) = chars.next() {
                            token.push(escaped);
                        }
                    }
                    Some(ch) => token.push(ch),
                }
            }
            tokens.push(token);
        } else {
            chars.next();
        }
    }
    tokens
}

pub type MonitorLineCallback = Arc<dyn Fn(MonitorLine) + Send + Sync>;

pub struct MonitorClient {
    task: Option<tokio::task::JoinHandle<()>>,
    stop_tx: Option<oneshot::Sender<()>>,
}

impl MonitorClient {
    pub async fn new(
        address: &NodeAddress,
        redis_connection_info: RedisConnectionInfo,
        tls_mode: TlsMode,
        on_line: MonitorLineCallback,
    ) -> RedisResult<Self> {
        let conn_addr = match tls_mode {
            TlsMode::NoTls => ConnectionAddr::Tcp(address.host.clone(), address.port),
            _ => ConnectionAddr::TcpTls {
                host: address.host.clone(),
                port: address.port,
                insecure: matches!(tls_mode, TlsMode::InsecureTls),
                tls_params: None,
            },
        };
        let conn_info = ConnectionInfo {
            addr: conn_addr,
            redis: RedisConnectionInfo {
                protocol: redis::ProtocolVersion::RESP2,
                ..redis_connection_info
            },
        };
        let client = redis::Client::open(conn_info)?;
        #[allow(deprecated)]
        let mut monitor = client.get_async_monitor().await?;
        monitor.monitor().await?;

        let (stop_tx, mut stop_rx) = oneshot::channel::<()>();
        let (ready_tx, ready_rx) = oneshot::channel::<()>();
        let task = tokio::spawn(async move {
            let mut stream = monitor.into_on_message::<String>();
            let _ = ready_tx.send(());
            loop {
                tokio::select! {
                    biased;
                    _ = &mut stop_rx => break,
                    item = stream.next() => match item {
                        Some(line) => {
                            if let Some(parsed) = MonitorLine::parse(&line) {
                                on_line(parsed);
                            }
                        }
                        None => break,
                    },
                }
            }
        });
        let _ = ready_rx.await;

        Ok(Self {
            task: Some(task),
            stop_tx: Some(stop_tx),
        })
    }

    pub async fn stop_async(mut self) {
        if let Some(tx) = self.stop_tx.take() {
            let _ = tx.send(());
        }
        if let Some(task) = self.task.take() {
            let _ = task.await;
        }
    }

    pub fn stop(&mut self) {
        if let Some(tx) = self.stop_tx.take() {
            let _ = tx.send(());
        }
    }
}

impl Drop for MonitorClient {
    fn drop(&mut self) {
        self.stop();
    }
}
