//! A successful TCP connection can belong to another test or a stalled listener;
//! neither establishes readiness, and protocol setup itself can hang.

use std::{
    fmt, io,
    path::Path,
    process::{Child, ExitStatus},
    thread,
    time::{Duration, Instant},
};

use redis::{Client, ErrorKind, RedisError};

pub(crate) const STARTUP_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Debug)]
pub(crate) enum ReadinessError {
    Exited {
        pid: u32,
        addr: String,
        status: ExitStatus,
    },
    Wait {
        pid: u32,
        error: io::Error,
    },
    Timeout {
        pid: u32,
        addr: String,
        timeout: Duration,
        last_error: String,
    },
    WrongOwner {
        pid: u32,
        addr: String,
        actual: u32,
    },
    Rejected {
        pid: u32,
        addr: String,
        error: Box<RedisError>,
    },
}

/// What the server logs when its bind fails; the only evidence of a collision
/// once the child is gone.
pub(crate) const ADDRESS_IN_USE: &str = "Address already in use";

impl ReadinessError {
    /// True when another process holds the address, so the same configuration
    /// can succeed elsewhere. A slow or misconfigured child is not a collision:
    /// retrying it elsewhere would only repeat the failure.
    pub(crate) fn is_port_collision(&self, log_file: &Path) -> bool {
        match self {
            Self::WrongOwner { .. } => true,
            Self::Exited { .. } | Self::Timeout { .. } => std::fs::read_to_string(log_file)
                .map(|log| log.contains(ADDRESS_IN_USE))
                .unwrap_or(false),
            Self::Wait { .. } | Self::Rejected { .. } => false,
        }
    }
}

impl fmt::Display for ReadinessError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Exited { pid, addr, status } => write!(
                f,
                "redis server {pid} at {addr} exited with {status:?} before readiness"
            ),
            Self::Wait { pid, error } => write!(f, "checking redis server {pid}: {error}"),
            Self::Timeout {
                pid,
                addr,
                timeout,
                last_error,
            } => write!(
                f,
                "redis server {pid} at {addr} was not ready within {timeout:?}: {last_error}"
            ),
            Self::WrongOwner { pid, addr, actual } => write!(
                f,
                "redis server {pid} at {addr}: address is served by pid {actual}, not our redis server {pid}"
            ),
            Self::Rejected { pid, addr, error } => {
                write!(f, "redis server {pid} at {addr}: {error}")
            }
        }
    }
}

impl std::error::Error for ReadinessError {}

/// Some callers already run inside Tokio, where nesting `block_on` would panic.
/// The worker also gives each attempt its own runtime: dropping a timed-out
/// request alone can leave the multiplexed driver holding an unresponsive socket.
pub(crate) fn wait_for_server(
    process: &mut Child,
    client: &Client,
    flush: bool,
    timeout: Duration,
) -> Result<(), ReadinessError> {
    let deadline = Instant::now() + timeout;
    thread::scope(|scope| {
        scope
            .spawn(|| {
                let pid = process.id();
                let addr = &client.get_connection_info().addr;
                let mut last_error = String::from("no attempt completed");
                loop {
                    match process.try_wait() {
                        Ok(Some(status)) => {
                            return Err(ReadinessError::Exited {
                                pid,
                                addr: format!("{addr:?}"),
                                status,
                            })
                        }
                        Err(error) => return Err(ReadinessError::Wait { pid, error }),
                        Ok(None) => {}
                    }
                    let remaining = deadline.saturating_duration_since(Instant::now());
                    if remaining.is_zero() {
                        return Err(ReadinessError::Timeout {
                            pid,
                            addr: format!("{addr:?}"),
                            timeout,
                            last_error,
                        });
                    }
                    let result = {
                        let runtime = super::current_thread_runtime();
                        runtime.block_on(async {
                            tokio::time::timeout(
                                remaining.min(Duration::from_millis(500)),
                                probe(client, pid, flush),
                            )
                            .await
                        })
                    };
                    match result {
                        Ok(Ok(())) => return Ok(()),
                        Ok(Err(ProbeError::WrongOwner(actual))) => {
                            return Err(ReadinessError::WrongOwner {
                                pid,
                                addr: format!("{addr:?}"),
                                actual,
                            })
                        }
                        Ok(Err(ProbeError::Redis(error))) => {
                            if !error.is_io_error()
                                && !error.is_connection_dropped()
                                && error.kind() != ErrorKind::BusyLoadingError
                            {
                                return Err(ReadinessError::Rejected {
                                    pid,
                                    addr: format!("{addr:?}"),
                                    error: Box::new(error),
                                });
                            }
                            last_error = error.to_string();
                        }
                        Err(_) => {
                            last_error = "connection setup or ownership probe timed out".into()
                        }
                    }
                    thread::sleep(
                        Duration::from_millis(10)
                            .min(deadline.saturating_duration_since(Instant::now())),
                    );
                }
            })
            .join()
            .unwrap_or_else(|panic| std::panic::resume_unwind(panic))
    })
}

enum ProbeError {
    WrongOwner(u32),
    Redis(RedisError),
}

impl From<RedisError> for ProbeError {
    fn from(error: RedisError) -> Self {
        Self::Redis(error)
    }
}

async fn probe(client: &Client, expected: u32, flush: bool) -> Result<(), ProbeError> {
    let mut con = client
        .get_multiplexed_async_connection(Default::default())
        .await?;
    let info: String = redis::cmd("INFO")
        .arg("server")
        .query_async(&mut con)
        .await?;
    let actual = info
        .lines()
        .find_map(|line| line.strip_prefix("process_id:"))
        .and_then(|pid| pid.trim().parse::<u32>().ok())
        .ok_or_else(|| {
            RedisError::from((ErrorKind::TypeError, "INFO server has no valid process_id"))
        })?;
    if actual != expected {
        return Err(ProbeError::WrongOwner(actual));
    }
    if flush {
        redis::cmd("FLUSHDB").query_async::<_, ()>(&mut con).await?;
    }
    Ok(())
}
