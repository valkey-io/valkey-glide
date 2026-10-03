//! A successful TCP connection can belong to another test or a stalled listener;
//! neither establishes readiness, and protocol setup itself can hang.

use std::{
    process::Child,
    thread,
    time::{Duration, Instant},
};

use redis::{Client, ErrorKind, RedisError, RedisResult};

pub(crate) const STARTUP_TIMEOUT: Duration = Duration::from_secs(5);

/// Some callers already run inside Tokio, where nesting `block_on` would panic.
/// The worker also gives each attempt its own runtime: dropping a timed-out
/// request alone can leave the multiplexed driver holding an unresponsive socket.
pub(crate) fn wait_for_server(
    process: &mut Child,
    client: &Client,
    flush: bool,
    timeout: Duration,
) -> Result<(), String> {
    let deadline = Instant::now() + timeout;
    thread::scope(|scope| {
        scope.spawn(|| {
            let expected = process.id();
            let addr = &client.get_connection_info().addr;
            let mut last_error = String::from("no attempt completed");
            loop {
                match process.try_wait() {
                    Ok(Some(status)) => return Err(format!(
                        "redis server {expected} at {addr:?} exited with {status:?} before readiness"
                    )),
                    Err(err) => return Err(format!("checking redis server {expected}: {err}")),
                    Ok(None) => {}
                }
                let remaining = deadline.saturating_duration_since(Instant::now());
                if remaining.is_zero() {
                    return Err(format!(
                        "redis server {expected} at {addr:?} was not ready within {timeout:?}: {last_error}"
                    ));
                }
                let result = {
                    let runtime = super::current_thread_runtime();
                    runtime.block_on(async {
                        tokio::time::timeout(
                            remaining.min(Duration::from_millis(500)),
                            probe(client, expected, flush),
                        ).await
                    })
                };
                match result {
                    Ok(Ok(())) => return Ok(()),
                    Ok(Err(err)) => {
                        if !err.is_io_error()
                            && !err.is_connection_dropped()
                            && err.kind() != ErrorKind::BusyLoadingError
                        {
                            return Err(format!("redis server {expected} at {addr:?}: {err}"));
                        }
                        last_error = err.to_string();
                    }
                    Err(_) => last_error = "connection setup or ownership probe timed out".into(),
                }
                thread::sleep(Duration::from_millis(10).min(
                    deadline.saturating_duration_since(Instant::now()),
                ));
            }
        })
        .join()
        .unwrap_or_else(|panic| std::panic::resume_unwind(panic))
    })
}

async fn probe(client: &Client, expected: u32, flush: bool) -> RedisResult<()> {
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
        return Err((
            ErrorKind::TypeError,
            "server ownership mismatch",
            format!("address is served by pid {actual}, not our redis server {expected}"),
        )
            .into());
    }
    if flush {
        redis::cmd("FLUSHDB").query_async::<_, ()>(&mut con).await?;
    }
    Ok(())
}
