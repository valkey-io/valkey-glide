use std::marker::PhantomData;

pub struct JoinHandle<T> {
    pub _p: PhantomData<T>,
}
