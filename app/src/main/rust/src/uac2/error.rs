//! Error types for UAC 2.0 operations.

use thiserror::Error;

#[derive(Error, Debug)]
pub enum Uac2Error {
    #[error("USB error: {0}")]
    Usb(#[from] rusb::Error),

    #[error("Device not found")]
    DeviceNotFound,

    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    #[error("Device busy or unavailable: {0}")]
    DeviceBusy(String),

    #[error("Device disconnected")]
    DeviceDisconnected,

    #[error("Device handle not available")]
    NoHandle,

    #[error("Invalid device descriptor: {0}")]
    InvalidDescriptor(String),

    #[error("Operation not supported: {0}")]
    NotSupported(String),

    #[error("No supported audio formats found")]
    NoSupportedFormats,

    #[error("Invalid stream configuration: {0}")]
    InvalidConfiguration(String),

    #[error("Audio endpoint not found")]
    EndpointNotFound,

    #[error("Invalid endpoint: {0}")]
    InvalidEndpoint(String),

    #[error("Buffer overflow")]
    BufferOverflow,

    #[error("Buffer underflow")]
    BufferUnderflow,

    #[error("Transfer failed: {0}")]
    TransferFailed(String),

    #[error("Failed to spawn thread: {0}")]
    ThreadSpawn(String),

    #[error("Failed to join thread")]
    ThreadJoin,

    #[error("No supported format")]
    NoSupportedFormat,

    #[error("Stream not active")]
    StreamNotActive,

    #[error("Reconnection failed after {0} attempts")]
    ReconnectionFailed(usize),

    #[error("{context}: {source}")]
    WithContext {
        context: String,
        #[source]
        source: Box<Uac2Error>,
    },
}

impl Uac2Error {
    pub fn with_context<S: Into<String>>(self, context: S) -> Self {
        Self::WithContext {
            context: context.into(),
            source: Box::new(self),
        }
    }

    pub fn user_message(&self) -> String {
        match self {
            Self::Usb(e) => format!("USB communication failed: {}", e),
            Self::DeviceNotFound => "USB Audio device not found".to_string(),
            Self::PermissionDenied(msg) => format!("Access denied: {}", msg),
            Self::DeviceBusy(msg) => format!("Device unavailable: {}", msg),
            Self::DeviceDisconnected => "Device disconnected".to_string(),
            Self::NoHandle => "Failed to open USB device".to_string(),
            Self::InvalidDescriptor(msg) => format!("Invalid device: {}", msg),
            Self::NotSupported(msg) => format!("Not supported: {}", msg),
            Self::NoSupportedFormats => "No compatible audio formats found".to_string(),
            Self::InvalidConfiguration(msg) => format!("Invalid configuration: {}", msg),
            Self::EndpointNotFound => "Audio endpoint not found".to_string(),
            Self::InvalidEndpoint(msg) => format!("Invalid endpoint: {}", msg),
            Self::BufferOverflow => "Buffer overflow".to_string(),
            Self::BufferUnderflow => "Buffer underflow".to_string(),
            Self::TransferFailed(msg) => format!("Transfer failed: {}", msg),
            Self::ThreadSpawn(msg) => format!("Thread spawn failed: {}", msg),
            Self::ThreadJoin => "Thread join failed".to_string(),
            Self::NoSupportedFormat => "No supported format found".to_string(),
            Self::StreamNotActive => "Audio stream not active".to_string(),
            Self::ReconnectionFailed(attempts) => {
                format!("Failed to reconnect after {} attempts", attempts)
            }
            Self::WithContext { context, source } => {
                format!("{}: {}", context, source.user_message())
            }
        }
    }

    pub fn is_recoverable(&self) -> bool {
        matches!(
            self,
            Self::BufferUnderflow
                | Self::BufferOverflow
                | Self::TransferFailed(_)
                | Self::DeviceBusy(_)
        )
    }

    pub fn is_device_error(&self) -> bool {
        matches!(
            self,
            Self::DeviceNotFound | Self::DeviceDisconnected | Self::NoHandle
        )
    }

    pub fn requires_reconnection(&self) -> bool {
        matches!(
            self,
            Self::DeviceDisconnected | Self::Usb(rusb::Error::NoDevice)
        )
    }
}

pub type Result<T> = std::result::Result<T, Uac2Error>;
