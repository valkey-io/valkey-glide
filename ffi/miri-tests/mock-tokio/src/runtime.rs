use futures::{
    future::{BoxFuture, FutureExt},
    task::{waker_ref, ArcWake},
};
use std::{
    future::Future,
    sync::mpsc::{sync_channel, Receiver, SyncSender},
    sync::{Arc, Mutex},
    task::{Context, Poll},
    time::Duration,
};
use crate::task::JoinHandle;

/// `Runtime` spawns new futures onto the task channel.
pub struct Runtime;

impl Runtime {
	pub fn block_on<F: Future>(&self, future: F) -> F::Output {
		let waker = std::task::Waker::noop();
        let context = &mut Context::from_waker(&waker);
        match std::pin::pin!(future).poll(context) {
			Poll::Ready(result) => result,
			_ => panic!("No result found")
		}
	}

    pub fn spawn<F>(&self, future: F) -> JoinHandle<F::Output> 
    where
        F: Future + Send + 'static,
        F::Output: Send + 'static,
    {
		let task = Arc::new(Task {
            future: Mutex::new(Some(Box::pin(future))),
        });
		let waker = waker_ref(&task);
        let context = &mut Context::from_waker(&waker);
        while task.future.lock().unwrap().take().unwrap().as_mut().poll(context).is_pending() {}
        JoinHandle {
            _p: std::marker::PhantomData::<F::Output>
        }
    }
}

/// A future that can reschedule itself to be polled by an `Executor`.
struct Task<T> {
    /// In-progress future that should be pushed to completion.
    ///
    /// The `Mutex` is not necessary for correctness, since we only have
    /// one thread executing tasks at once. However, Rust isn't smart
    /// enough to know that `future` is only mutated from one thread,
    /// so we need to use the `Mutex` to prove thread-safety. A production
    /// executor would not need this, and could use `UnsafeCell` instead.
    future: Mutex<Option<BoxFuture<'static, T>>>,
}

impl<T> ArcWake for Task<T> {
    fn wake_by_ref(arc_self: &Arc<Self>) {
    }
}

pub struct Builder;

impl Builder {
	pub fn new_multi_thread() -> Builder {
		Builder
	}

	pub fn enable_all(&mut self) -> &mut Self {
		self
	}

	pub fn worker_threads(&mut self, val: usize) -> &mut Self {
		self
	}

	pub fn thread_name(&mut self, val: impl Into<String>) -> &mut Self {
		self
	}

	pub fn build(&mut self) -> Result<Runtime, std::io::Error> {
        let mut runtime = Runtime;
        Ok(runtime)
	}
}