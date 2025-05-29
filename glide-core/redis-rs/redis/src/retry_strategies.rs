// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::time::Duration;
use tokio_retry2::strategy::{jitter_range, ExponentialBackoff};
use tracing::debug;

/// This struct represents the exponential backoff parameters for reconnection attempts.
#[derive(Clone, Debug, Copy)]
pub struct RetryStrategy {
    factor: u32,
    exponent_base: u32,
    number_of_retries: u32,
    jitter_percent: u32,
}

// === Default constants ===
pub(crate) const EXPONENT_BASE: u32 = 2;
pub(crate) const FACTOR: u32 = 100;
pub(crate) const NUMBER_OF_RETRIES: u32 = 5;
pub(crate) const DEFAULT_JITTER_PERCENT: u32 = 20; // Default jitter ±20%

impl RetryStrategy {
    /// Create RetryStrategy from given parameters
    pub fn new(
        exponent_base: u32,
        factor: u32,
        number_of_retries: u32,
        jitter_percent: Option<u32>,
    ) -> Self {
        let exponent_base = if exponent_base > 0 {
            exponent_base
        } else {
            EXPONENT_BASE
        };
        let factor = if factor > 0 { factor } else { FACTOR };
        let jitter = jitter_percent.unwrap_or(DEFAULT_JITTER_PERCENT);
        Self::with_params(exponent_base, factor, number_of_retries, jitter)
    }

    /// Internal constructor used by `new` and `default`, emits a debug log.
    fn with_params(
        exponent_base: u32,
        factor: u32,
        number_of_retries: u32,
        jitter_percent: u32,
    ) -> Self {
        let strategy = RetryStrategy {
            factor,
            exponent_base,
            number_of_retries,
            jitter_percent,
        };
        debug!("Starting RetryStrategy with values: {:?}", strategy);
        strategy
    }

    /// Return a bounded iterator: stops after number_of_retries attempts
    pub fn get_bounded_backoff_dur_iterator(&self) -> impl Iterator<Item = Duration> {
        let base_backoff =
            ExponentialBackoff::from_millis(self.exponent_base as u64).factor(self.factor as u64);

        let (lower, upper) = self.jitter_bounds();
        let jitter_fn = jitter_range(lower, upper);

        base_backoff
            .map(jitter_fn)
            .take(self.number_of_retries as usize)
    }

    /// Return an infinite iterator:
    /// - First number_of_retries attempts with backoff
    /// - Then repeat the last delay forever
    pub fn get_infinite_backoff_dur_iterator(&self) -> impl Iterator<Item = Duration> {
        let base_backoff =
            ExponentialBackoff::from_millis(self.exponent_base as u64).factor(self.factor as u64);

        let (lower, upper) = self.jitter_bounds();
        let jitter_fn = jitter_range(lower, upper);

        let last_duration = base_backoff
            .clone()
            .nth(self.number_of_retries as usize - 1)
            .unwrap_or(Duration::from_millis(
                self.factor as u64 * self.exponent_base.pow(self.number_of_retries - 1) as u64,
            ));

        let bounded = base_backoff
            .map(jitter_fn)
            .take(self.number_of_retries as usize);

        bounded.chain(std::iter::repeat(last_duration))
    }

    /// Internal: Calculate jitter lower/upper bounds from jitter_percent
    fn jitter_bounds(&self) -> (f64, f64) {
        let jitter = self.jitter_percent;
        let jitter_fraction = jitter as f64 / 100.0;
        (1.0 - jitter_fraction, 1.0 + jitter_fraction)
    }
}

impl Default for RetryStrategy {
    fn default() -> Self {
        Self::with_params(
            EXPONENT_BASE,
            FACTOR,
            NUMBER_OF_RETRIES,
            DEFAULT_JITTER_PERCENT,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exponential_backoff_with_jitter() {
        let retries = 5;
        let base = 2;
        let factor = 100;
        let jitter_percent = Some(20);

        let strategy = RetryStrategy::new(base, factor, retries, jitter_percent);
        let intervals = strategy.get_bounded_backoff_dur_iterator();

        let jitter = 20_f64 / 100.0;

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            let expected = factor as u64 * base.pow(counter) as u64;
            let lower_limit = (expected as f64 * (1.0 - jitter)) as u128;
            let upper_limit = (expected as f64 * (1.0 + jitter)) as u128;
            assert!(
                lower_limit <= duration.as_millis() && duration.as_millis() <= upper_limit,
                "Duration {:?}ms not in range [{:?}ms, {:?}ms]",
                duration.as_millis(),
                lower_limit,
                upper_limit
            );
        }

        assert_eq!(counter, retries);
    }

    #[test]
    fn test_infinite_backoff_behavior() {
        let retries = 3;
        let base = 2;
        let factor = 100;
        let jitter_percent = Some(20);
        let strategy = RetryStrategy::new(base, factor, retries, jitter_percent);
        let mut iter = strategy.get_infinite_backoff_dur_iterator();

        // First `retries` values should differ (jittered)
        for _ in 0..retries {
            let _ = iter.next().unwrap();
        }

        // Now the iterator should yield the same (unjittered) value
        let repeated = iter.next().unwrap();
        for _ in 0..5 {
            let value = iter.next().unwrap();
            assert_eq!(
                value,
                repeated,
                "Expected infinite tail with constant duration: got {} vs {}",
                value.as_millis(),
                repeated.as_millis()
            );
        }
    }
}
