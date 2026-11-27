// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Process-global state that a forked child discards instead of inheriting.
//!
//! `fork()` copies only the calling thread. A global holding a lock that
//! another parent thread held at that moment is locked forever in the child,
//! and a tokio runtime in the global has no worker threads there. A
//! [`ForkSlot`] starts empty in every child, so the next access builds a fresh
//! value.
//!
//! The inherited value is leaked, never dropped: its locks and threads are in
//! an undefined state.

use std::marker::PhantomData;
use std::ptr;
use std::sync::atomic::{AtomicPtr, Ordering};

/// A lazily initialized global that is rebuilt after `fork()`.
pub struct ForkSlot<T> {
    held: AtomicPtr<Held<T>>,
    // `AtomicPtr` is `Send + Sync` for any pointee. This keeps `ForkSlot` from
    // sharing a `T` across threads when `T` itself could not be.
    _marker: PhantomData<T>,
}

struct Held<T> {
    epoch: u64,
    value: T,
}

impl<T> ForkSlot<T> {
    pub const fn new() -> Self {
        Self {
            held: AtomicPtr::new(ptr::null_mut()),
            _marker: PhantomData,
        }
    }

    /// The value for this process, built with `init` on first use and again
    /// after each `fork()`. Concurrent callers get one value; a caller that
    /// loses the race drops the one it built.
    pub fn get_or_init(&'static self, init: impl FnOnce() -> T) -> &'static T {
        let epoch = current_epoch();
        let mut seen = self.held.load(Ordering::Acquire);
        // Safety: a published `Held` is never freed.
        if let Some(held) = unsafe { seen.as_ref() }
            && held.epoch == epoch
        {
            return &held.value;
        }
        let fresh = Box::into_raw(Box::new(Held {
            epoch,
            value: init(),
        }));
        loop {
            match self
                .held
                .compare_exchange(seen, fresh, Ordering::AcqRel, Ordering::Acquire)
            {
                // `seen` is leaked on purpose: it is null or inherited.
                Ok(_) => return unsafe { &(*fresh).value },
                Err(current) => {
                    seen = current;
                    if let Some(held) = unsafe { seen.as_ref() }
                        && held.epoch == epoch
                    {
                        // Safety: `fresh` was never published.
                        drop(unsafe { Box::from_raw(fresh) });
                        return &held.value;
                    }
                }
            }
        }
    }
}

impl<T> Default for ForkSlot<T> {
    fn default() -> Self {
        Self::new()
    }
}

/// Identifies this process's lifetime: it changes in a forked child.
///
/// On Linux it is a nonzero token kept in a page marked `MADV_WIPEONFORK`, so
/// the kernel zeroes it in every child and the first call there picks a new
/// one. That is a single atomic load, and it holds for a fork that skips
/// `pthread_atfork` handlers. Elsewhere, and where `mmap` or `madvise` fails,
/// it is the process id.
fn current_epoch() -> u64 {
    #[cfg(target_os = "linux")]
    if !force_pid_epoch()
        && let Some(cell) = wipe_on_fork_cell()
    {
        let epoch = cell.load(Ordering::Acquire);
        if epoch != 0 {
            return epoch;
        }
        let token = new_token();
        return match cell.compare_exchange(0, token, Ordering::AcqRel, Ordering::Acquire) {
            Ok(_) => token,
            Err(winner) => winner,
        };
    }
    #[cfg(unix)]
    return std::process::id() as u64;
    #[cfg(not(unix))]
    return 1;
}

/// Test hook: take the process-id path that macOS and a failed `madvise` use.
#[cfg(all(test, target_os = "linux"))]
static FORCE_PID_EPOCH: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

#[cfg(all(test, target_os = "linux"))]
fn force_pid_epoch() -> bool {
    FORCE_PID_EPOCH.load(Ordering::Relaxed)
}

#[cfg(all(not(test), target_os = "linux"))]
fn force_pid_epoch() -> bool {
    false
}

#[cfg(target_os = "linux")]
fn wipe_on_fork_cell() -> Option<&'static std::sync::atomic::AtomicU64> {
    use std::sync::atomic::AtomicU64;

    // Null until decided. The first caller's outcome is final for the process,
    // so no caller switches between the page and the pid after using the other.
    static CELL: AtomicPtr<AtomicU64> = AtomicPtr::new(ptr::null_mut());
    // Stands for "no page". It is a real static, so it is never a page address
    // and every non-null decision points at valid memory.
    static UNAVAILABLE: AtomicU64 = AtomicU64::new(0);
    let unavailable = ptr::from_ref(&UNAVAILABLE).cast_mut();

    let decided = |decision: *mut AtomicU64| {
        if decision == unavailable {
            None
        } else {
            // Safety: a published page is never unmapped.
            Some(unsafe { &*decision })
        }
    };

    let decision = CELL.load(Ordering::Acquire);
    if !decision.is_null() {
        return decided(decision);
    }
    unsafe {
        let len = libc::sysconf(libc::_SC_PAGESIZE) as usize;
        let page = libc::mmap(
            ptr::null_mut(),
            len,
            libc::PROT_READ | libc::PROT_WRITE,
            libc::MAP_PRIVATE | libc::MAP_ANONYMOUS,
            -1,
            0,
        );
        // Kernels before 4.14, or a sandbox that denies it, fail here.
        let ours =
            if page != libc::MAP_FAILED && libc::madvise(page, len, libc::MADV_WIPEONFORK) == 0 {
                // A new anonymous page is all zeros.
                page.cast::<AtomicU64>()
            } else {
                if page != libc::MAP_FAILED {
                    libc::munmap(page, len);
                }
                unavailable
            };
        match CELL.compare_exchange(ptr::null_mut(), ours, Ordering::AcqRel, Ordering::Acquire) {
            Ok(_) => decided(ours),
            Err(winner) => {
                if ours != unavailable {
                    libc::munmap(ours.cast(), len);
                }
                decided(winner)
            }
        }
    }
}

#[cfg(target_os = "linux")]
fn new_token() -> u64 {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |elapsed| elapsed.as_nanos() as u64);
    (nanos ^ ((std::process::id() as u64) << 32)) | 1
}

/// Run `child` in a forked child and assert it returned true. A child that
/// does not exit within the deadline is killed, so a hang fails the test.
#[cfg(all(test, unix))]
pub(crate) fn assert_in_forked_child(child: impl FnOnce() -> bool + std::panic::UnwindSafe) {
    use std::time::{Duration, Instant};

    let pid = unsafe { libc::fork() };
    assert!(pid >= 0, "fork failed");
    if pid == 0 {
        let ok = std::panic::catch_unwind(child);
        unsafe { libc::_exit(if matches!(ok, Ok(true)) { 0 } else { 1 }) };
    }
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut status = 0;
    loop {
        let reaped = unsafe { libc::waitpid(pid, &mut status, libc::WNOHANG) };
        assert!(reaped >= 0, "waitpid failed");
        if reaped == pid {
            break;
        }
        if Instant::now() >= deadline {
            unsafe {
                libc::kill(pid, libc::SIGKILL);
                libc::waitpid(pid, &mut status, 0);
            }
            panic!("forked child did not exit within 10 s (hung?) and was killed");
        }
        std::thread::sleep(Duration::from_millis(10));
    }
    assert!(
        libc::WIFEXITED(status) && libc::WEXITSTATUS(status) == 0,
        "forked child failed (raw wait status {status})"
    );
}

#[cfg(all(test, unix))]
mod tests {
    use super::*;
    use serial_test::serial;
    use std::sync::atomic::AtomicUsize;

    static BUILT: AtomicUsize = AtomicUsize::new(0);
    static SLOT: ForkSlot<usize> = ForkSlot::new();

    fn build() -> usize {
        BUILT.fetch_add(1, Ordering::SeqCst) + 1
    }

    #[test]
    #[serial]
    fn child_builds_a_fresh_value_and_the_parent_keeps_its_own() {
        let parent = SLOT.get_or_init(build) as *const usize as usize;
        assert_in_forked_child(move || {
            let in_child = SLOT.get_or_init(build) as *const usize as usize;
            in_child != parent && ptr::eq(SLOT.get_or_init(build), SLOT.get_or_init(build))
        });
        assert_eq!(SLOT.get_or_init(build) as *const usize as usize, parent);
    }

    #[test]
    #[serial]
    fn grandchild_builds_its_own_value() {
        let parent = SLOT.get_or_init(build) as *const usize as usize;
        assert_in_forked_child(move || {
            let child = SLOT.get_or_init(build) as *const usize as usize;
            let grandchild_is_fresh = std::panic::catch_unwind(move || {
                assert_in_forked_child(move || {
                    SLOT.get_or_init(build) as *const usize as usize != child
                });
            })
            .is_ok();
            child != parent && grandchild_is_fresh
        });
    }

    /// macOS, and Linux where `madvise` fails, tell a child apart by process id.
    /// The flag is process-wide, so it is only flipped inside a throwaway child.
    #[cfg(target_os = "linux")]
    #[test]
    #[serial]
    fn pid_epoch_path_builds_a_fresh_value_in_a_child() {
        static PID_SLOT: ForkSlot<usize> = ForkSlot::new();
        assert_in_forked_child(|| {
            FORCE_PID_EPOCH.store(true, Ordering::Relaxed);
            let parent = PID_SLOT.get_or_init(build) as *const usize as usize;
            assert_eq!(current_epoch(), std::process::id() as u64);
            assert_in_forked_child(move || {
                let in_child = PID_SLOT.get_or_init(build) as *const usize as usize;
                in_child != parent
                    && ptr::eq(PID_SLOT.get_or_init(build), PID_SLOT.get_or_init(build))
            });
            true
        });
    }

    #[test]
    #[serial]
    fn concurrent_first_calls_converge_on_one_value() {
        static RACE_SLOT: ForkSlot<usize> = ForkSlot::new();
        let barrier = std::sync::Arc::new(std::sync::Barrier::new(8));
        let addresses: Vec<usize> = (0..8)
            .map(|_| {
                let barrier = barrier.clone();
                std::thread::spawn(move || {
                    barrier.wait();
                    RACE_SLOT.get_or_init(build) as *const usize as usize
                })
            })
            .collect::<Vec<_>>()
            .into_iter()
            .map(|worker| worker.join().unwrap())
            .collect();
        assert!(addresses.iter().all(|a| *a == addresses[0]));
    }
}
