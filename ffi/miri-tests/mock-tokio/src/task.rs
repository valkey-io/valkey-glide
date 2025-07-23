// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::marker::PhantomData;

pub struct JoinHandle<T> {
    pub _p: PhantomData<T>,
}
