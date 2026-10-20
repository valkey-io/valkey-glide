// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub mod runtime;
pub mod sync;
pub mod task;

pub mod time {
    use std::cell::Cell;
    use std::ops::{Add, Sub};
    use std::time::Duration;

    thread_local! {
        static NOW: Cell<Duration> = const { Cell::new(Duration::ZERO) };
    }

    #[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
    pub struct Instant(Duration);

    impl Instant {
        pub fn now() -> Self {
            Self(NOW.get())
        }
    }

    impl Add<Duration> for Instant {
        type Output = Self;

        fn add(self, duration: Duration) -> Self::Output {
            Self(self.0 + duration)
        }
    }

    impl Sub for Instant {
        type Output = Duration;

        fn sub(self, earlier: Self) -> Self::Output {
            self.0 - earlier.0
        }
    }

    pub async fn sleep(duration: Duration) {
        NOW.set(NOW.get() + duration);
    }
}
