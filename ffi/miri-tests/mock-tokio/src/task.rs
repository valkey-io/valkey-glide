// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::{
    fmt,
    future::Future,
    pin::Pin,
    task::{Context, Poll},
};

/// Holds the spawned future and polls it only when awaited, so fire-and-forget
/// tasks such as pubsub listeners are dropped unpolled.
pub struct JoinHandle<T> {
    pub(crate) future: Pin<Box<dyn Future<Output = T> + Send>>,
}

impl<T> Future for JoinHandle<T> {
    type Output = Result<T, JoinError>;

    fn poll(mut self: Pin<&mut Self>, context: &mut Context<'_>) -> Poll<Self::Output> {
        self.future.as_mut().poll(context).map(Ok)
    }
}

/// The mock polls tasks inline, so a task can never be cancelled and a panic
/// unwinds straight through `poll`.
#[derive(Debug)]
pub enum JoinError {}

impl JoinError {
    pub fn is_panic(&self) -> bool {
        match *self {}
    }

    pub fn into_panic(self) -> Box<dyn std::any::Any + Send + 'static> {
        match self {}
    }
}

impl fmt::Display for JoinError {
    fn fmt(&self, _: &mut fmt::Formatter<'_>) -> fmt::Result {
        match *self {}
    }
}

pub async fn yield_now() {
    let mut yielded = false;
    futures::future::poll_fn(move |context| {
        if yielded {
            std::task::Poll::Ready(())
        } else {
            yielded = true;
            context.waker().wake_by_ref();
            std::task::Poll::Pending
        }
    })
    .await
}
