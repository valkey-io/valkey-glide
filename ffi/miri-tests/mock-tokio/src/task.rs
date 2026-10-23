// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::marker::PhantomData;

pub struct JoinHandle<T> {
    pub _p: PhantomData<T>,
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
