// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::{
    future::Future,
    task::{Context, Poll},
};
use crate::task::JoinHandle;

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
        let waker = std::task::Waker::noop();
        let context = &mut Context::from_waker(&waker);
        match std::pin::pin!(future).poll(context) {
            Poll::Ready(result) => result,
            _ => panic!("No result found")
        };
        JoinHandle {
            _p: std::marker::PhantomData::<F::Output>
        }
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

    pub fn worker_threads(&mut self, _val: usize) -> &mut Self {
        self
    }

    pub fn thread_name(&mut self, _val: impl Into<String>) -> &mut Self {
        self
    }

    pub fn build(&mut self) -> Result<Runtime, std::io::Error> {
        Ok(Runtime)
    }
}
