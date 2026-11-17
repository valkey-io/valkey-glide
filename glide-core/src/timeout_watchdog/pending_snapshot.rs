// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

#[derive(Clone)]
pub(super) struct Reader(Arc<AtomicUsize>);

pub(super) struct Writer(Arc<AtomicUsize>);

pub(super) fn new() -> (Reader, Writer) {
    let count = Arc::new(AtomicUsize::new(0));
    (Reader(Arc::clone(&count)), Writer(count))
}

impl Reader {
    pub(super) fn load(&self) -> usize {
        self.0.load(Ordering::Relaxed)
    }
}

impl Writer {
    pub(super) fn publish(&mut self, count: usize) {
        self.0.store(count, Ordering::Relaxed);
    }
}
