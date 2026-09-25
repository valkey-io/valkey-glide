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

// Caps the nth() index used to compute the last retry's delay; it does not limit the number of
// retries. nth(k) is factor * base^(k+1) saturated at u64::MAX, which base >= 2 reaches by k = 63
// and base 1 never changes, so nth(k) == nth(63) for all k >= 63.
const RETRY_LOOKUP_CAP: usize = 63;

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
    /// - Then repeat the last retry's delay forever, without jitter
    ///   (the first retry's delay when number_of_retries is 0)
    pub fn get_infinite_backoff_dur_iterator(&self) -> impl Iterator<Item = Duration> {
        let base_backoff =
            ExponentialBackoff::from_millis(self.exponent_base as u64).factor(self.factor as u64);

        let (lower, upper) = self.jitter_bounds();
        let jitter_fn = jitter_range(lower, upper);

        let last_duration = self.last_backoff_duration();

        let bounded = base_backoff
            .map(jitter_fn)
            .take(self.number_of_retries as usize);

        bounded.chain(std::iter::repeat(last_duration))
    }

    /// Internal: Unjittered delay of the last bounded retry (the first retry when
    /// number_of_retries is 0), repeated forever by the infinite iterator.
    fn last_backoff_duration(&self) -> Duration {
        let last_retry = (self.number_of_retries.max(1) - 1) as usize;
        let capped_retry = last_retry.min(RETRY_LOOKUP_CAP);
        ExponentialBackoff::from_millis(self.exponent_base as u64)
            .factor(self.factor as u64)
            .nth(capped_retry)
            .expect("ExponentialBackoff is infinite")
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
    use rstest::rstest;

    const BACKOFF_TEST_TIMEOUT: Duration = Duration::from_millis(500);

    fn expected_last_retry_duration(exponent_base: u32, factor: u32, retries: u32) -> Duration {
        assert!(
            retries > 0,
            "expected_last_retry_duration requires at least one retry"
        );
        Duration::from_millis(
            (factor as u64).saturating_mul((exponent_base as u64).saturating_pow(retries)),
        )
    }

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

    #[rstest]
    #[timeout(BACKOFF_TEST_TIMEOUT)]
    fn test_zero_retries() {
        let combinations = [(2, 100), (u32::MAX, u32::MAX)];
        for (exponent_base, factor) in combinations {
            let strategy = RetryStrategy::new(exponent_base, factor, 0, Some(20));
            let first_retry_duration = expected_last_retry_duration(exponent_base, factor, 1);
            assert_eq!(
                strategy.get_bounded_backoff_dur_iterator().count(),
                0,
                "base={exponent_base} factor={factor}"
            );
            assert_eq!(
                strategy
                    .get_infinite_backoff_dur_iterator()
                    .take(5)
                    .collect::<Vec<Duration>>(),
                vec![first_retry_duration; 5],
                "base={exponent_base} factor={factor}"
            );
        }
    }

    #[test]
    fn test_last_duration_matches_expected_formula() {
        let combinations = [(1, 1000), (2, 100), (3, 50), (10, 1), (u32::MAX, u32::MAX)];
        for (exponent_base, factor) in combinations {
            for retries in (1..=10).chain([63, 64, 65, 100, 1000]) {
                let strategy = RetryStrategy::new(exponent_base, factor, retries, Some(20));
                let expected = expected_last_retry_duration(exponent_base, factor, retries);
                assert_eq!(
                    strategy.last_backoff_duration(),
                    expected,
                    "base={exponent_base} factor={factor} retries={retries}"
                );
                assert_eq!(
                    strategy
                        .get_infinite_backoff_dur_iterator()
                        .nth(retries as usize),
                    Some(expected),
                    "base={exponent_base} factor={factor} retries={retries}"
                );
            }
        }
    }

    #[rstest]
    #[timeout(BACKOFF_TEST_TIMEOUT)]
    fn test_huge_retries_saturates_without_walking_iterator() {
        let strategy = RetryStrategy::new(2, 100, u32::MAX, Some(0));
        assert_eq!(
            strategy.last_backoff_duration(),
            Duration::from_millis(u64::MAX)
        );
        assert_eq!(
            strategy.get_infinite_backoff_dur_iterator().next(),
            Some(Duration::from_millis(200))
        );
        assert_eq!(
            strategy.get_bounded_backoff_dur_iterator().next(),
            Some(Duration::from_millis(200))
        );
    }
}
