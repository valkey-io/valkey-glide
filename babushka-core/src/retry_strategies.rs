use std::time::Duration;

use tokio_retry::strategy::{ExponentialBackoff, FixedInterval};

use crate::pb_message::{connection_retry_strategy, ConnectionRetryStrategy};

fn apply_jitter(duration: Duration, max_jitter: Duration) -> Duration {
    let multiplier = (rand::random::<f64>() * 2.0) - 1.0;
    let jitter = max_jitter.as_secs_f64() * multiplier;
    if jitter >= 0.0 {
        duration.checked_add(Duration::from_secs_f64(jitter))
    } else {
        duration.checked_sub(Duration::from_secs_f64(-jitter))
    }
    .unwrap_or(Duration::from_millis(1))
}

const EXPONENT_BASE: u64 = 10;
const FACTOR: u64 = 5;
const NUMBER_OF_RETRIES: usize = 3;
const MAX_JITTER_IN_MILLIS: Duration = Duration::from_millis(10);

fn get_exponential_backoff(
    exponent_base: u64,
    factor: u64,
    number_of_retries: usize,
    max_jitter: Duration,
) -> Box<dyn Iterator<Item = Duration>> {
    let exponent_base = if exponent_base > 0 {
        exponent_base
    } else {
        EXPONENT_BASE
    };
    let factor = if factor > 0 { factor } else { FACTOR };

    finalize_strategy(
        ExponentialBackoff::from_millis(exponent_base).factor(factor),
        number_of_retries,
        max_jitter,
    )
}

pub(crate) fn get_fixed_interval_backoff(
    fixed_interval: Duration,
    number_of_retries: usize,
    max_jitter: Duration,
) -> Box<dyn Iterator<Item = Duration>> {
    finalize_strategy(
        FixedInterval::new(fixed_interval),
        number_of_retries,
        max_jitter,
    )
}

fn finalize_strategy(
    strategy: impl Iterator<Item = Duration> + 'static,
    number_of_retries: usize,
    max_jitter: Duration,
) -> Box<dyn Iterator<Item = Duration>> {
    Box::new(
        strategy
            .map(move |time| apply_jitter(time, max_jitter))
            .take(number_of_retries),
    )
}

pub(crate) fn get_retry_strategy(
    data: Option<ConnectionRetryStrategy>,
) -> Box<dyn Iterator<Item = Duration>> {
    match data {
        Some(ref strategy) => {
            let number_of_retries = strategy.number_of_retries as usize;
            let max_jitter = Duration::from_millis(strategy.max_jitter_in_millis as u64);
            match strategy.value {
                Some(ref strategy) => match strategy {
                    connection_retry_strategy::Value::Exponential(exponent) => {
                        get_exponential_backoff(
                            exponent.exponent_base as u64,
                            exponent.factor as u64,
                            number_of_retries,
                            max_jitter,
                        )
                    }
                    connection_retry_strategy::Value::Fixed(fixed) => get_fixed_interval_backoff(
                        Duration::from_millis(fixed.duration_in_millis as u64),
                        number_of_retries,
                        max_jitter,
                    ),
                },
                None => {
                    get_exponential_backoff(EXPONENT_BASE, FACTOR, number_of_retries, max_jitter)
                }
            }
        }
        None => get_exponential_backoff(
            EXPONENT_BASE,
            FACTOR,
            NUMBER_OF_RETRIES,
            MAX_JITTER_IN_MILLIS,
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fixed_intervals_without_jitter() {
        let intervals =
            get_fixed_interval_backoff(Duration::from_millis(10), 2, Duration::from_millis(0));

        let retries: Vec<Duration> = intervals.collect();
        assert_eq!(
            retries,
            vec![Duration::from_millis(10), Duration::from_millis(10)]
        );
    }

    #[test]
    fn test_fixed_intervals_with_jitter() {
        let retries = 3;
        let interval_duration = 10;
        let jitter = 5;
        let intervals = get_fixed_interval_backoff(
            Duration::from_millis(interval_duration),
            retries,
            Duration::from_millis(jitter),
        );

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            let duration_in_millis = duration.as_millis();
            assert!(duration_in_millis <= (interval_duration + jitter) as u128);
            assert!(duration_in_millis >= (interval_duration - jitter) as u128);
        }
        assert_eq!(counter, retries);
    }

    #[test]
    fn test_handle_jitter_larger_than_duration() {
        let retries = 3;
        let interval_duration = 10;
        let jitter = 10000;
        let intervals = get_fixed_interval_backoff(
            Duration::from_millis(interval_duration),
            retries,
            Duration::from_millis(jitter),
        );

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            let duration_in_millis = duration.as_millis();
            assert!(duration_in_millis <= (interval_duration + jitter) as u128);
            assert!(duration_in_millis >= 1);
        }
        assert_eq!(counter, retries);
    }

    #[test]
    fn test_exponential_backoff_without_jitter() {
        let base = 10;
        let factor = 5;
        let intervals = get_exponential_backoff(base, factor, 3, Duration::from_millis(0));

        let retries: Vec<Duration> = intervals.collect();
        assert_eq!(
            retries,
            vec![
                Duration::from_millis(50),
                Duration::from_millis(500),
                Duration::from_millis(5000)
            ]
        );
    }

    #[test]
    fn test_exponential_backoff_with_jitter() {
        let retries = 3;
        let base = 10;
        let factor = 5;
        let jitter = 25;
        let intervals =
            get_exponential_backoff(base, factor, retries, Duration::from_millis(jitter));

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            let duration_in_millis = duration.as_millis();
            let unjittered_duration = factor * (base.pow(counter));
            assert!(duration_in_millis <= (unjittered_duration + jitter) as u128);
            assert!(duration_in_millis >= (unjittered_duration - jitter) as u128);
        }

        assert_eq!(counter as usize, retries);
    }
}
