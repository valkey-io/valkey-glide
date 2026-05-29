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
        // `get_async_monitor` is the only available API for a dedicated non-pooled
        // monitor connection. The deprecation warning originates from its internal
        // use of `get_async_connection`, not from the method itself being removed.
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_basic_set() {
        let raw = "+1678886400.123456 [0 127.0.0.1:12345] \"SET\" \"key\" \"value\"";
        let line = MonitorLine::parse(raw).unwrap();
        assert!((line.timestamp - 1678886400.123456).abs() < 1e-6);
        assert_eq!(line.db, 0);
        assert_eq!(line.client_addr, "127.0.0.1:12345");
        assert_eq!(line.command, "SET");
        assert_eq!(line.args, vec!["key", "value"]);
    }

    #[test]
    fn test_parse_no_args_ping() {
        let raw = "+1678886400.000000 [0 127.0.0.1:12345] \"PING\"";
        let line = MonitorLine::parse(raw).unwrap();
        assert_eq!(line.command, "PING");
        assert!(line.args.is_empty());
    }

    #[test]
    fn test_parse_without_plus_prefix() {
        let raw = "1678886400.000000 [0 127.0.0.1:12345] \"GET\" \"mykey\"";
        let line = MonitorLine::parse(raw).unwrap();
        assert_eq!(line.command, "GET");
        assert_eq!(line.args, vec!["mykey"]);
    }

    #[test]
    fn test_parse_escaped_chars() {
        let raw = "+1678886400.000000 [0 127.0.0.1:12345] \"SET\" \"key\" \"val\\\"quoted\\\"\"";
        let line = MonitorLine::parse(raw).unwrap();
        assert_eq!(line.args, vec!["key", "val\"quoted\""]);
    }

    #[test]
    fn test_parse_malformed_returns_none() {
        assert!(MonitorLine::parse("").is_none());
        assert!(MonitorLine::parse("not a monitor line").is_none());
        assert!(MonitorLine::parse("+notanumber [0 addr] \"CMD\"").is_none());
    }

    #[test]
    fn test_parse_quoted_tokens_basic() {
        let tokens = parse_quoted_tokens("\"SET\" \"key\" \"value\"");
        assert_eq!(tokens, vec!["SET", "key", "value"]);
    }

    #[test]
    fn test_parse_quoted_tokens_empty() {
        let tokens = parse_quoted_tokens("");
        assert!(tokens.is_empty());
    }
}
