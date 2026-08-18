// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::types::{ErrorKind, RedisError, RedisResult};
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
/// Largest jitter that keeps the lower jitter bound non-negative, since the bounds are 1 ± jitter/100.
pub const MAX_JITTER_PERCENT: u32 = 100;

impl RetryStrategy {
    /// Create RetryStrategy from given parameters.
    ///
    /// Fails if `jitter_percent` exceeds [`MAX_JITTER_PERCENT`]. A larger jitter would make the
    /// lower jitter bound negative, which `Duration::mul_f64` cannot represent.
    pub fn new(
        exponent_base: u32,
        factor: u32,
        number_of_retries: u32,
        jitter_percent: Option<u32>,
    ) -> RedisResult<Self> {
        let exponent_base = if exponent_base > 0 {
            exponent_base
        } else {
            EXPONENT_BASE
        };
        let factor = if factor > 0 { factor } else { FACTOR };
        let jitter = jitter_percent.unwrap_or(DEFAULT_JITTER_PERCENT);
        if jitter > MAX_JITTER_PERCENT {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "invalid reconnect strategy",
                format!("jitterPercent must be between 0 and {MAX_JITTER_PERCENT}, got {jitter}"),
            )));
        }
        Ok(Self::with_params(
            exponent_base,
            factor,
            number_of_retries,
            jitter,
        ))
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
    /// - Then repeat the last delay forever, re-jittered per attempt when jitter is enabled
    pub fn get_infinite_backoff_dur_iterator(&self) -> impl Iterator<Item = Duration> {
        let base_backoff =
            ExponentialBackoff::from_millis(self.exponent_base as u64).factor(self.factor as u64);

        let (lower, upper) = self.jitter_bounds();
        let jitter_fn = jitter_range(lower, upper);

        // `ExponentialBackoff` yields `factor * exponent_base^(k+1)` at index `k`, so the last
        // bounded delay is derived arithmetically. Walking the iterator to reach it would cost one
        // step per retry, stalling the shared runtime thread for a retry count in the billions.
        let last_exponent = self.number_of_retries.max(1);
        let last_duration = Duration::from_millis(
            (self.factor as u64)
                .saturating_mul((self.exponent_base as u64).saturating_pow(last_exponent)),
        );

        let bounded = base_backoff
            .map(jitter_fn)
            .take(self.number_of_retries as usize);

        // Re-jitter each tail delay so clients that exhaust the bounded phase do not rejoin in
        // lockstep, the same retry-storm protection the bounded phase gets. With jitter disabled
        // the delay is repeated verbatim, since scaling by 1.0 is not exact for large durations.
        let jitter_percent = self.jitter_percent;
        let tail_jitter_fn = jitter_range(lower, upper);
        let tail = std::iter::repeat_with(move || {
            if jitter_percent == 0 {
                last_duration
            } else {
                tail_jitter_fn(last_duration)
            }
        });

        bounded.chain(tail)
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

        let strategy = RetryStrategy::new(base, factor, retries, jitter_percent).unwrap();
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
    fn test_jitter_percent_above_100_is_rejected() {
        for jitter in [MAX_JITTER_PERCENT + 1, 150, u32::MAX] {
            let err = RetryStrategy::new(2, 100, 3, Some(jitter)).unwrap_err();
            assert_eq!(err.kind(), ErrorKind::InvalidClientConfig);
            assert!(
                err.to_string().contains("jitterPercent"),
                "error does not name the field: {err}"
            );
        }
    }

    #[test]
    fn test_max_jitter_percent_is_accepted() {
        let strategy = RetryStrategy::new(2, 100, 3, Some(MAX_JITTER_PERCENT)).unwrap();
        let (lower, upper) = strategy.jitter_bounds();
        assert_eq!(lower, 0.0);
        assert_eq!(upper, 2.0);

        // At full jitter the lower bound is exactly 0, so `Duration::mul_f64` stays in range.
        for duration in strategy.get_bounded_backoff_dur_iterator() {
            assert!(duration.as_millis() <= 2 * 100 * 2u128.pow(3));
        }
    }

    #[test]
    fn test_zero_retries_does_not_underflow() {
        let strategy = RetryStrategy::new(2, 100, 0, Some(20)).unwrap();

        assert_eq!(strategy.get_bounded_backoff_dur_iterator().count(), 0);

        // Without the saturating exponent this asks the endless backoff for its `usize::MAX`-th
        // element and never returns. The tail is jittered, so assert the band around the 200ms
        // closed-form delay rather than the exact value.
        let mut infinite = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..6 {
            let ms = infinite.next().unwrap().as_millis();
            assert!((160..=240).contains(&ms), "delay {ms}ms out of band");
        }
    }

    #[test]
    fn test_zero_retries_without_jitter_yields_exact_first_delay() {
        let strategy = RetryStrategy::new(2, 100, 0, Some(0)).unwrap();

        // Pins the closed form itself: `factor * base^max(retries, 1)` = 200ms, no jitter to mask
        // an off-by-one in the exponent.
        let mut infinite = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..6 {
            assert_eq!(infinite.next().unwrap(), Duration::from_millis(200));
        }
    }

    #[test]
    fn test_huge_retry_count_returns_promptly() {
        let strategy = RetryStrategy::new(2, 100, u32::MAX, Some(20)).unwrap();

        // The infinite iterator derives its tail delay arithmetically. Walking the backoff to the
        // last attempt instead would take billions of steps on the reconnect path.
        let start = std::time::Instant::now();
        let mut infinite = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..10 {
            let _ = infinite.next().unwrap();
        }
        assert!(
            start.elapsed() < Duration::from_secs(1),
            "building the iterator took {:?}",
            start.elapsed()
        );
    }

    #[test]
    fn test_infinite_tail_is_rejittered_when_jitter_enabled() {
        let retries = 3;
        let strategy = RetryStrategy::new(2, 100, retries, Some(20)).unwrap();
        let mut iter = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..retries {
            let _ = iter.next().unwrap();
        }

        // Successive tail delays must not be byte-identical, or every client that exhausts the
        // bounded phase reconnects in lockstep. 40 samples make a false failure vanishingly rare.
        let tail: Vec<_> = (0..40).map(|_| iter.next().unwrap()).collect();
        let unique = tail.iter().collect::<std::collections::HashSet<_>>().len();
        assert!(
            unique > 1,
            "tail repeated one value {:?} across {} samples",
            tail[0],
            tail.len()
        );

        // Every sample still sits in the jitter band around `factor * base^retries` = 800ms.
        for duration in tail {
            let ms = duration.as_millis();
            assert!((640..=960).contains(&ms), "tail delay {ms}ms out of band");
        }
    }

    #[test]
    fn test_saturated_tail_with_jitter_does_not_panic() {
        // factor and base at u32::MAX saturate the closed form to u64::MAX millis. Scaling that by
        // the jitter upper bound must not overflow `Duration::mul_f64`.
        let strategy = RetryStrategy::new(u32::MAX, u32::MAX, u32::MAX, Some(100)).unwrap();
        let mut infinite = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..10 {
            let _ = infinite.next().unwrap();
        }
    }

    #[test]
    fn test_infinite_tail_is_constant_when_jitter_disabled() {
        let retries = 3;
        let strategy = RetryStrategy::new(2, 100, retries, Some(0)).unwrap();
        let mut iter = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..retries {
            let _ = iter.next().unwrap();
        }

        // With jitter off the tail repeats the closed-form delay verbatim.
        for _ in 0..10 {
            assert_eq!(iter.next().unwrap(), Duration::from_millis(800));
        }
    }

    #[test]
    fn test_infinite_tail_matches_last_bounded_delay() {
        let base = 2;
        let factor = 100;
        let retries = 4;
        let strategy = RetryStrategy::new(base, factor, retries, Some(0)).unwrap();

        let bounded: Vec<_> = strategy.get_bounded_backoff_dur_iterator().collect();
        let mut infinite = strategy.get_infinite_backoff_dur_iterator();
        for _ in 0..retries {
            let _ = infinite.next().unwrap();
        }

        // The tail repeats the last bounded delay, `factor * base^retries`.
        assert_eq!(*bounded.last().unwrap(), Duration::from_millis(1600));
        assert_eq!(infinite.next().unwrap(), Duration::from_millis(1600));
    }

    #[test]
    fn test_infinite_backoff_behavior() {
        let retries = 3;
        let base = 2;
        let factor = 100;
        let jitter_percent = Some(20);
        let strategy = RetryStrategy::new(base, factor, retries, jitter_percent).unwrap();
        let mut iter = strategy.get_infinite_backoff_dur_iterator();

        // First `retries` values should differ (jittered)
        for _ in 0..retries {
            let _ = iter.next().unwrap();
        }

        // The tail stays within the jitter band around `factor * base^retries`.
        let expected = factor as u128 * (base as u128).pow(retries);
        let lower = expected * 80 / 100;
        let upper = expected * 120 / 100;
        for _ in 0..10 {
            let value = iter.next().unwrap().as_millis();
            assert!(
                lower <= value && value <= upper,
                "tail delay {value}ms not in jitter band [{lower}ms, {upper}ms]"
            );
        }
    }
}
