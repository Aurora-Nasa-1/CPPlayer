use crate::uac2::error::{Result, Uac2Error};
use std::time::{Duration, Instant};
use tracing::{debug, error, info, warn};

const MAX_RECONNECT_ATTEMPTS: usize = 5;
const INITIAL_BACKOFF_MS: u64 = 100;
const MAX_BACKOFF_MS: u64 = 5000;
const BACKOFF_MULTIPLIER: u64 = 2;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecoveryStrategy {
    Retry,
    Reconnect,
    Fallback,
    Abort,
}

pub struct ErrorRecovery {
    reconnect_attempts: usize,
    last_error: Option<Instant>,
    backoff_duration: Duration,
}

impl ErrorRecovery {
    pub fn new() -> Self {
        Self {
            reconnect_attempts: 0,
            last_error: None,
            backoff_duration: Duration::from_millis(INITIAL_BACKOFF_MS),
        }
    }

    pub fn determine_strategy(&self, error: &Uac2Error) -> RecoveryStrategy {
        if error.requires_reconnection() {
            if self.reconnect_attempts < MAX_RECONNECT_ATTEMPTS {
                RecoveryStrategy::Reconnect
            } else {
                RecoveryStrategy::Fallback
            }
        } else if error.is_recoverable() {
            RecoveryStrategy::Retry
        } else {
            RecoveryStrategy::Abort
        }
    }

    pub fn record_error(&mut self) {
        self.last_error = Some(Instant::now());
    }

    pub fn record_reconnect_attempt(&mut self) {
        self.reconnect_attempts += 1;
        self.backoff_duration = Duration::from_millis(
            (self.backoff_duration.as_millis() as u64 * BACKOFF_MULTIPLIER).min(MAX_BACKOFF_MS),
        );
    }

    pub fn reset(&mut self) {
        self.reconnect_attempts = 0;
        self.last_error = None;
        self.backoff_duration = Duration::from_millis(INITIAL_BACKOFF_MS);
    }

    pub fn wait_backoff(&self) {
        std::thread::sleep(self.backoff_duration);
    }

    pub fn reconnect_attempts(&self) -> usize {
        self.reconnect_attempts
    }

    pub fn can_reconnect(&self) -> bool {
        self.reconnect_attempts < MAX_RECONNECT_ATTEMPTS
    }
}

impl Default for ErrorRecovery {
    fn default() -> Self {
        Self::new()
    }
}

pub trait Recoverable<T> {
    fn with_recovery<F>(self, recovery: &mut ErrorRecovery, operation: F) -> Result<T>
    where
        F: Fn() -> Result<T>;
}

impl<T> Recoverable<T> for Result<T> {
    fn with_recovery<F>(self, recovery: &mut ErrorRecovery, operation: F) -> Result<T>
    where
        F: Fn() -> Result<T>,
    {
        match self {
            Ok(value) => {
                recovery.reset();
                Ok(value)
            }
            Err(error) => {
                recovery.record_error();
                let strategy = recovery.determine_strategy(&error);

                match strategy {
                    RecoveryStrategy::Retry => {
                        debug!("Retrying operation after error: {}", error);
                        operation()
                    }
                    RecoveryStrategy::Reconnect => {
                        warn!(
                            "Reconnection required (attempt {}): {}",
                            recovery.reconnect_attempts + 1,
                            error
                        );
                        recovery.record_reconnect_attempt();
                        recovery.wait_backoff();
                        operation()
                    }
                    RecoveryStrategy::Fallback => {
                        error!("Recovery failed, falling back: {}", error);
                        Err(Uac2Error::ReconnectionFailed(recovery.reconnect_attempts))
                    }
                    RecoveryStrategy::Abort => {
                        error!("Unrecoverable error: {}", error);
                        Err(error)
                    }
                }
            }
        }
    }
}

pub struct ReconnectionManager {
    recovery: ErrorRecovery,
    last_successful_connection: Option<Instant>,
}

impl ReconnectionManager {
    pub fn new() -> Self {
        Self {
            recovery: ErrorRecovery::new(),
            last_successful_connection: None,
        }
    }

    pub fn attempt_reconnect<F>(&mut self, reconnect_fn: F) -> Result<()>
    where
        F: Fn() -> Result<()>,
    {
        if !self.recovery.can_reconnect() {
            return Err(Uac2Error::ReconnectionFailed(
                self.recovery.reconnect_attempts(),
            ));
        }

        info!(
            "Attempting reconnection (attempt {}/{})",
            self.recovery.reconnect_attempts() + 1,
            MAX_RECONNECT_ATTEMPTS
        );

        self.recovery.record_reconnect_attempt();
        self.recovery.wait_backoff();

        match reconnect_fn() {
            Ok(()) => {
                info!("Reconnection successful");
                self.recovery.reset();
                self.last_successful_connection = Some(Instant::now());
                Ok(())
            }
            Err(e) => {
                warn!("Reconnection attempt failed: {}", e);
                Err(e)
            }
        }
    }

    pub fn reset(&mut self) {
        self.recovery.reset();
        self.last_successful_connection = Some(Instant::now());
    }

    pub fn reconnect_attempts(&self) -> usize {
        self.recovery.reconnect_attempts()
    }

    pub fn time_since_last_connection(&self) -> Option<Duration> {
        self.last_successful_connection
            .map(|instant| instant.elapsed())
    }
}

impl Default for ReconnectionManager {
    fn default() -> Self {
        Self::new()
    }
}
