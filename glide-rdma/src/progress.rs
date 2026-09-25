// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Completion-queue polling for TcpProvider.
//!
//! Under `FI_PROGRESS_MANUAL` nothing services an inbound RMA unless the target polls
//! its completion queue, so the software `tcp` provider needs a poller thread for a
//! transfer to make progress at all. `efa-direct` needs none (the NIC services it)
//! so no driver is created there and the guards below are never taken.
//!
//! The poller waits in `fi_cq_sread`, which sleeps in the kernel until the provider
//! has work, so a transfer in flight costs no CPU while the server is not sending.

use std::fmt;
use std::os::raw::c_int;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex, PoisonError};
use std::thread::JoinHandle;

use ofi_libfabric_sys::bindgen::{
    FI_EAVAIL, fi_cq_entry, fi_cq_err_entry, fi_cq_read, fi_cq_readerr, fi_cq_signal, fi_cq_sread,
    fid_cq,
};

/// The longest the poller sleeps in one `fi_cq_sread`. It is woken sooner by any
/// completion or provider activity, and by [`ProgressDriver`]'s drop, so this only
/// bounds how long it keeps waiting after the last guard is gone before parking.
const WAIT_MS: c_int = 10;

struct SendCompletionQueue(*mut fid_cq);

// SAFETY: `ProgressDriver` joins its thread in `Drop`, and the driver is declared
// before the endpoint that owns the queue, so the poller is gone before `fi_close`
// runs on it. Nothing but the poller reads the queue.
unsafe impl Send for SendCompletionQueue {}
unsafe impl Sync for SendCompletionQueue {}

struct ProgressShared {
    queue: SendCompletionQueue,
    /// Transfers in flight. The hot path: only `drive` and guard-drop touch it.
    active: AtomicUsize,
    shutdown: AtomicBool,
    /// Blocks the poller while idle. Never held across a poll pass.
    park: Mutex<()>,
    wakeup: Condvar,
}

/// Polls one completion queue while transfers are outstanding.
///
/// The thread parks when nothing is in flight, and is joined on drop.
pub(crate) struct ProgressDriver {
    shared: Arc<ProgressShared>,
    handle: Option<JoinHandle<()>>,
}

impl ProgressDriver {
    pub(crate) fn new(queue: *mut fid_cq) -> Self {
        let shared = Arc::new(ProgressShared {
            queue: SendCompletionQueue(queue),
            active: AtomicUsize::new(0),
            shutdown: AtomicBool::new(false),
            park: Mutex::new(()),
            wakeup: Condvar::new(),
        });
        let polled = shared.clone();
        Self {
            handle: Some(std::thread::spawn(move || poll_loop(&polled))),
            shared,
        }
    }

    /// Poll until the returned guard drops. Lock-free but for waking a parked poller.
    pub(crate) fn drive(&self) -> ProgressGuard {
        if self.shared.active.fetch_add(1, Ordering::AcqRel) == 0 {
            // Take `park` so this notify cannot land between
            // the poller's check and its wait.
            let _parked = self
                .shared
                .park
                .lock()
                .unwrap_or_else(PoisonError::into_inner);
            self.shared.wakeup.notify_one();
        }
        ProgressGuard {
            shared: self.shared.clone(),
        }
    }
}

impl Drop for ProgressDriver {
    fn drop(&mut self) {
        self.shared.shutdown.store(true, Ordering::Release);
        {
            let _parked = self
                .shared
                .park
                .lock()
                .unwrap_or_else(PoisonError::into_inner);
            self.shared.wakeup.notify_one();
        }
        // Wake the poller if it is asleep in `fi_cq_sread`. If this fails, it wakes
        // on its own within `WAIT_MS`.
        // SAFETY: the queue stays open until after the poller is joined below.
        unsafe { fi_cq_signal(self.shared.queue.0) };
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

fn poll_loop(shared: &ProgressShared) {
    loop {
        {
            let mut idle = shared.park.lock().unwrap_or_else(PoisonError::into_inner);
            while shared.active.load(Ordering::Acquire) == 0
                && !shared.shutdown.load(Ordering::Acquire)
            {
                idle = shared
                    .wakeup
                    .wait(idle)
                    .unwrap_or_else(PoisonError::into_inner);
            }
        }
        if shared.shutdown.load(Ordering::Acquire) {
            return;
        }

        // SAFETY: the queue stays open until this thread is joined; see `SendCompletionQueue`.
        unsafe {
            wait(shared.queue.0);
            drain(shared.queue.0);
        }
    }
}

/// Sleep until `queue` has an entry, the provider has work to do, `fi_cq_signal`
/// is called on it, or `WAIT_MS` passes. Reads at most one entry and leaves an
/// error entry for [`drain`].
///
/// # Safety
/// `queue` must be an open completion queue opened with a wait object that no
/// other thread reads.
unsafe fn wait(queue: *mut fid_cq) {
    let mut entry = fi_cq_entry {
        op_context: std::ptr::null_mut(),
    };
    // SAFETY: the caller guarantees the queue is open and has a wait object, and
    // `entry` has room for the one entry asked for.
    unsafe {
        fi_cq_sread(
            queue,
            std::ptr::from_mut(&mut entry).cast(),
            1,
            std::ptr::null(),
            WAIT_MS,
        )
    };
}

/// Read every entry off `queue`, returning how many of them were errors.
///
/// An error entry sits at the front of the queue, and every read returns
/// `-FI_EAVAIL` until `fi_cq_readerr` takes it off. Nothing waits on these
/// completions, so an error is read and dropped, the same as a success.
///
/// # Safety
/// `queue` must be an open completion queue that no other thread reads.
unsafe fn drain(queue: *mut fid_cq) -> usize {
    let mut errors = 0;
    loop {
        let mut entry = fi_cq_entry {
            op_context: std::ptr::null_mut(),
        };
        // SAFETY: the caller guarantees the queue is open, and `entry` has room for
        // the one entry asked for.
        let read = unsafe { fi_cq_read(queue, std::ptr::from_mut(&mut entry).cast(), 1) };
        if read > 0 {
            continue;
        }
        if read != -(FI_EAVAIL as isize) {
            // Empty (-FI_EAGAIN), or a failure that no entry explains.
            return errors;
        }
        // SAFETY: as above; an all-zero entry is a valid value to be overwritten.
        let mut error: fi_cq_err_entry = unsafe { std::mem::zeroed() };
        // SAFETY: the caller guarantees the queue is open, and `error` has room for
        // one entry.
        if unsafe { fi_cq_readerr(queue, &mut error, 0) } <= 0 {
            // Nothing was removed, so reading again would only see it again.
            return errors;
        }
        errors += 1;
    }
}

/// Keeps the progress driver polling while alive.
pub struct ProgressGuard {
    shared: Arc<ProgressShared>,
}

impl Drop for ProgressGuard {
    fn drop(&mut self) {
        // At 0 the poller parks itself next pass.
        self.shared.active.fetch_sub(1, Ordering::AcqRel);
    }
}

impl fmt::Debug for ProgressDriver {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("ProgressDriver")
            .field("active", &self.shared.active.load(Ordering::Relaxed))
            .finish()
    }
}

impl fmt::Debug for ProgressGuard {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_struct("ProgressGuard").finish()
    }
}

#[cfg(test)]
mod tests {
    use super::{ProgressDriver, drain};
    use crate::config::{FabricConfig, Provider};
    use crate::endpoint::LibfabricEndpoint;
    use crate::endpoint::tests::{post_failing_read, post_read};
    use ofi_libfabric_sys::bindgen::{fi_close, fi_cq_entry, fi_cq_read, fi_mr_key};
    use std::sync::atomic::Ordering;
    use std::time::{Duration, Instant};

    impl ProgressDriver {
        /// How many guards are outstanding.
        pub(crate) fn active(&self) -> usize {
            self.shared.active.load(Ordering::Acquire)
        }
    }

    fn driver() -> (LibfabricEndpoint, ProgressDriver) {
        let endpoint = LibfabricEndpoint::open(&FabricConfig::new(Provider::Tcp))
            .expect("the tcp provider should open");
        let driver = ProgressDriver::new(endpoint.completion_queue());
        (endpoint, driver)
    }

    /// Poll `endpoint`'s completion queue until one entry arrives or `deadline`
    /// passes. Returns the read's result code: positive for a completion,
    /// `-FI_EAVAIL` for an error entry, `-FI_EAGAIN` if nothing came in time.
    fn wait_for_completion(endpoint: &LibfabricEndpoint, deadline: Instant) -> isize {
        loop {
            let mut entry = fi_cq_entry {
                op_context: std::ptr::null_mut(),
            };
            // SAFETY: the queue is open, and `entry` has room for one entry.
            let read = unsafe {
                fi_cq_read(
                    endpoint.completion_queue(),
                    std::ptr::from_mut(&mut entry).cast(),
                    1,
                )
            };
            if read != -(libc::EAGAIN as isize) || Instant::now() >= deadline {
                return read;
            }
        }
    }

    #[test]
    fn draining_reads_off_an_error_entry() {
        let mut endpoint = LibfabricEndpoint::open(&FabricConfig::new(Provider::Tcp))
            .expect("the tcp provider should open");
        let destination = vec![0u8; 64];
        // SAFETY: `destination` outlives the region, which is closed below.
        let region = unsafe { post_failing_read(&mut endpoint, &destination) };
        let queue = endpoint.completion_queue();

        // The error completes some time after the post.
        let deadline = Instant::now() + Duration::from_secs(5);
        let mut errors = 0;
        while errors == 0 {
            assert!(
                Instant::now() < deadline,
                "the error entry was never drained"
            );
            // SAFETY: the queue is open, and only this thread reads it.
            errors = unsafe { drain(queue) };
        }
        assert_eq!(errors, 1);

        let mut entry = fi_cq_entry {
            op_context: std::ptr::null_mut(),
        };
        // SAFETY: as above.
        let read = unsafe { fi_cq_read(queue, std::ptr::from_mut(&mut entry).cast(), 1) };
        assert_eq!(
            read,
            -(libc::EAGAIN as isize),
            "the queue is empty, not still reporting the error"
        );

        // SAFETY: the region is open, and nothing uses it after this.
        unsafe { fi_close(&raw mut (*region).fid) };
    }

    #[test]
    fn starts_parked_and_joins_on_drop() {
        let (endpoint, driver) = driver();
        assert_eq!(driver.active(), 0);
        drop(driver);
        drop(endpoint);
    }

    /// The poller sleeps in `fi_cq_sread` while a guard is held, and dropping the
    /// driver must still wake it and join it.
    #[test]
    fn drops_while_a_guard_is_outstanding() {
        let (endpoint, driver) = driver();
        let guard = driver.drive();
        std::thread::sleep(Duration::from_millis(50));
        let started = Instant::now();
        drop(driver);
        assert!(
            started.elapsed() < Duration::from_secs(1),
            "the poller took {:?} to shut down",
            started.elapsed()
        );
        drop(guard);
        drop(endpoint);
    }

    #[test]
    fn an_inbound_read_completes_only_while_progress_is_driven() {
        let (mut target, driver) = driver();
        let mut initiator = LibfabricEndpoint::open(&FabricConfig::new(Provider::Tcp))
            .expect("the tcp provider should open");
        let pattern: Vec<u8> = (0..1u32 << 20).map(|i| i as u8).collect();
        // SAFETY: `pattern` outlives the region, which is closed below.
        let source = unsafe { target.register_remote(&pattern) }.expect("the source registers");
        // SAFETY: `source` is an open region.
        let remote_key = unsafe { fi_mr_key(source) };
        let remote_address = if target.uses_virtual_addressing() {
            pattern.as_ptr() as u64
        } else {
            0
        };
        let target_address = target.local_address().expect("has an address");
        let read = |initiator: &mut LibfabricEndpoint, into: &[u8]| {
            // SAFETY: each caller keeps `into` alive until it closes the region.
            unsafe { post_read(initiator, into, &target_address, remote_address, remote_key) }
        };
        let nothing_yet = -(libc::EAGAIN as isize);

        let guard = driver.drive();
        let first = vec![0u8; pattern.len()];
        let region = read(&mut initiator, &first);
        let deadline = Instant::now() + Duration::from_secs(5);
        assert!(wait_for_completion(&initiator, deadline) > 0);
        assert_eq!(first, pattern);
        // SAFETY: the read into it has completed, and nothing uses it after this.
        unsafe { fi_close(&raw mut (*region).fid) };
        drop(guard);
        // The poller parks once its current wait ends, which takes at most `WAIT_MS`.
        std::thread::sleep(Duration::from_millis(100));

        let second = vec![0u8; pattern.len()];
        let region = read(&mut initiator, &second);
        let deadline = Instant::now() + Duration::from_millis(200);
        assert_eq!(
            wait_for_completion(&initiator, deadline),
            nothing_yet,
            "nothing polled the target, so the read should not have completed"
        );

        let guard = driver.drive();
        let deadline = Instant::now() + Duration::from_secs(5);
        assert!(wait_for_completion(&initiator, deadline) > 0);
        assert_eq!(second, pattern);
        drop(guard);
        // SAFETY: both reads have completed, and nothing uses either region after this.
        unsafe {
            fi_close(&raw mut (*region).fid);
            fi_close(&raw mut (*source).fid);
        }
        drop(driver);
        drop(target);
    }

    /// Guards are counted, not boolean: overlapping transfers must not let the first
    /// one to finish park the poller while others are still outstanding.
    #[test]
    fn guards_nest() {
        let (endpoint, driver) = driver();
        let first = driver.drive();
        assert_eq!(driver.active(), 1);
        let second = driver.drive();
        assert_eq!(driver.active(), 2);
        drop(second);
        assert_eq!(driver.active(), 1);
        drop(first);
        assert_eq!(driver.active(), 0);
        drop(driver);
        drop(endpoint);
    }
}
