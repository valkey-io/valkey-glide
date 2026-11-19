// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! A thread per fabric that closes regions for the drops that end them.
//!
//! Closing a region with `fi_close` deregisters its memory. On efa that takes
//! longer the more pages the region pins, and these regions are meant to be
//! large. A `Drop` cannot `.await`, so a drop that closed the region itself would
//! hold its thread for the whole close. When that thread is an async runtime's
//! worker, every other task on it waits too, which for glide-core can mean the
//! whole client. So the drops of [`crate::RdmaBuffer`] and [`crate::LentBuffer`]
//! queue their close here and return, and this thread runs the same steps the
//! drop used to run in the same order.
//!
//! Each queued close holds a handle to its fabric, so the fabric cannot close
//! while one is waiting. The last close to finish may therefore drop the last
//! handle and close the fabric from this thread, which is why stopping the
//! thread never waits for itself.

use std::collections::VecDeque;
use std::fmt;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Arc, Condvar, Mutex, MutexGuard, PoisonError};
use std::thread::{self, JoinHandle, ThreadId};

use crate::error::RdmaError;

/// One close, with whatever has to happen once it is done.
pub(crate) type CloseJob = Box<dyn FnOnce() + Send + 'static>;

/// Owns the closing thread; stops it when dropped.
pub(crate) struct Closer {
    queue: Arc<CloseQueue>,
    thread: Option<JoinHandle<()>>,
    thread_id: ThreadId,
}

/// Where closes wait for the thread, shared between the thread and its owner.
pub(crate) struct CloseQueue {
    state: Mutex<QueueState>,
    /// Signalled when a close arrives or the thread is told to stop.
    work: Condvar,
    /// Signalled when a close finishes while someone waits for closes.
    finished: Condvar,
}

#[derive(Default)]
struct QueueState {
    jobs: VecDeque<CloseJob>,
    /// Closes ever queued. A waiter notes this count and waits only for the
    /// closes up to it, so closes queued after it started cannot keep it waiting.
    submitted: u64,
    /// Closes ever finished, in queue order, so every close numbered at or
    /// below this count is done.
    finished: u64,
    /// Threads in [`CloseQueue::wait_until_idle`], so finishing a close skips
    /// the wake-up when nobody is waiting.
    waiting: usize,
    stop: bool,
    /// The closing thread, so waiting on the queue from it returns rather than
    /// waiting for itself.
    thread_id: Option<ThreadId>,
}

impl Closer {
    pub(crate) fn start() -> Result<Self, RdmaError> {
        let queue = Arc::new(CloseQueue {
            state: Mutex::new(QueueState::default()),
            work: Condvar::new(),
            finished: Condvar::new(),
        });
        let thread = thread::Builder::new()
            .name("glide-rdma-closer".into())
            .spawn({
                let queue = queue.clone();
                move || queue.run()
            })
            .map_err(|error| {
                RdmaError::Configuration(format!("cannot start the region closing thread: {error}"))
            })?;
        let thread_id = thread.thread().id();
        queue.state().thread_id = Some(thread_id);
        Ok(Self {
            queue,
            thread: Some(thread),
            thread_id,
        })
    }

    /// The queue, for submitting to without borrowing the closer.
    pub(crate) fn queue(&self) -> Arc<CloseQueue> {
        self.queue.clone()
    }
}

impl Drop for Closer {
    /// Stop the thread once it has run every queued close. Waits for it, unless
    /// this is the closing thread itself, dropping the last handle to its fabric
    /// from inside a close; it then stops as soon as that close returns.
    fn drop(&mut self) {
        self.queue.state().stop = true;
        self.queue.work.notify_one();
        let Some(thread) = self.thread.take() else {
            return;
        };
        if thread::current().id() != self.thread_id {
            let _ = thread.join();
        }
    }
}

impl CloseQueue {
    /// Queue `job` for the closing thread and return without waiting for it.
    pub(crate) fn submit(&self, job: CloseJob) {
        let mut state = self.state();
        state.jobs.push_back(job);
        state.submitted += 1;
        drop(state);
        self.work.notify_one();
    }

    /// Return once every close queued before this call has finished. Closes
    /// queued while it waits are not waited for.
    pub(crate) fn wait_until_idle(&self) {
        let mut state = self.state();
        if state.thread_id == Some(thread::current().id()) {
            return;
        }
        let target = state.submitted;
        state.waiting += 1;
        while state.finished < target {
            state = self
                .finished
                .wait(state)
                .unwrap_or_else(PoisonError::into_inner);
        }
        state.waiting -= 1;
    }

    fn run(&self) {
        loop {
            let job = {
                let mut state = self.state();
                loop {
                    if let Some(job) = state.jobs.pop_front() {
                        break job;
                    }
                    if state.stop {
                        return;
                    }
                    state = self
                        .work
                        .wait(state)
                        .unwrap_or_else(PoisonError::into_inner);
                }
            };
            // Run without the lock: a close can drop the last handle to its
            // fabric, and closing the fabric takes the lock to stop this thread.
            if catch_unwind(AssertUnwindSafe(job)).is_err() {
                glide_logger::log_error(
                    "rdma closer",
                    "closing an RDMA region panicked; the thread carries on",
                );
            }
            let mut state = self.state();
            state.finished += 1;
            if state.waiting > 0 {
                self.finished.notify_all();
            }
        }
    }

    fn state(&self) -> MutexGuard<'_, QueueState> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

impl fmt::Debug for Closer {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let state = self.queue.state();
        formatter
            .debug_struct("Closer")
            .field("queued", &state.jobs.len())
            .field("unfinished", &(state.submitted - state.finished))
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::Closer;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::mpsc;
    use std::time::Duration;

    #[test]
    fn closes_run_in_the_order_they_were_queued() {
        let closer = Closer::start().unwrap();
        let order = Arc::new(std::sync::Mutex::new(Vec::new()));
        for index in 0..10 {
            let order = order.clone();
            closer
                .queue()
                .submit(Box::new(move || order.lock().unwrap().push(index)));
        }
        closer.queue().wait_until_idle();
        assert_eq!(*order.lock().unwrap(), (0..10).collect::<Vec<_>>());
    }

    #[test]
    fn submitting_does_not_wait_for_the_close() {
        let closer = Closer::start().unwrap();
        let (release, released) = mpsc::channel::<()>();
        closer.queue().submit(Box::new(move || {
            released.recv().unwrap();
        }));
        // Reaching this line is the test: the close above is still blocked.
        release.send(()).unwrap();
        closer.queue().wait_until_idle();
    }

    #[test]
    fn stopping_runs_what_is_queued_first() {
        let closer = Closer::start().unwrap();
        let ran = Arc::new(AtomicUsize::new(0));
        for _ in 0..5 {
            let ran = ran.clone();
            closer.queue().submit(Box::new(move || {
                std::thread::sleep(Duration::from_millis(1));
                ran.fetch_add(1, Ordering::SeqCst);
            }));
        }
        drop(closer);
        assert_eq!(ran.load(Ordering::SeqCst), 5);
    }

    /// A panicking close must not take the thread with it, or every later close
    /// would wait forever.
    #[test]
    fn a_panicking_close_does_not_stop_the_thread() {
        let closer = Closer::start().unwrap();
        closer.queue().submit(Box::new(|| panic!("boom")));
        let (done, finished) = mpsc::channel();
        closer
            .queue()
            .submit(Box::new(move || done.send(()).unwrap()));
        finished
            .recv_timeout(Duration::from_secs(5))
            .expect("the next close still runs");
    }

    /// Closes queued after a wait starts must not hold it up, or a thread
    /// waiting while others keep dropping buffers could wait forever.
    #[test]
    fn waiting_ignores_closes_queued_after_it_started() {
        let closer = Closer::start().unwrap();
        let queue = closer.queue();
        let (release_later, later_released) = mpsc::channel::<()>();
        closer.queue().submit(Box::new({
            let queue = queue.clone();
            move || {
                // Hold this close open until the waiter below is waiting, then
                // queue a close that stays blocked until the wait has returned.
                while queue.state().waiting == 0 {
                    std::thread::yield_now();
                }
                queue.submit(Box::new(move || {
                    let _ = later_released.recv();
                }));
            }
        }));
        let (done, finished) = mpsc::channel();
        let waiter = std::thread::spawn(move || {
            queue.wait_until_idle();
            done.send(()).unwrap();
        });
        let result = finished.recv_timeout(Duration::from_secs(5));
        release_later.send(()).unwrap();
        waiter.join().unwrap();
        result.expect("the wait returns while a later close is still blocked");
    }

    /// The last close can drop the closer itself, as the last handle to a fabric
    /// does; stopping must not wait for the thread it is running on.
    #[test]
    fn the_closer_can_be_dropped_from_its_own_thread() {
        let closer = Closer::start().unwrap();
        let queue = closer.queue();
        let (done, finished) = mpsc::channel();
        queue.submit(Box::new(move || {
            drop(closer);
            done.send(()).unwrap();
        }));
        finished
            .recv_timeout(Duration::from_secs(5))
            .expect("dropping the closer from its own thread returns");
    }
}
