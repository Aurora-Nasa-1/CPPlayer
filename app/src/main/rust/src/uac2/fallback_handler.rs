use crate::uac2::error::{Result, Uac2Error};
use tracing::{info, warn};

pub trait FallbackAudioOutput {
    fn activate(&self) -> Result<()>;
    fn is_available(&self) -> bool;
    fn name(&self) -> &str;
}

pub struct FallbackHandler {
    fallback_outputs: Vec<Box<dyn FallbackAudioOutput + Send + Sync>>,
    active_fallback: Option<usize>,
}

impl FallbackHandler {
    pub fn new() -> Self {
        Self {
            fallback_outputs: Vec::new(),
            active_fallback: None,
        }
    }

    pub fn register_fallback(&mut self, output: Box<dyn FallbackAudioOutput + Send + Sync>) {
        self.fallback_outputs.push(output);
    }

    pub fn activate_fallback(&mut self) -> Result<()> {
        for (index, output) in self.fallback_outputs.iter().enumerate() {
            if output.is_available() {
                info!("Activating fallback audio output: {}", output.name());
                match output.activate() {
                    Ok(()) => {
                        self.active_fallback = Some(index);
                        return Ok(());
                    }
                    Err(e) => {
                        warn!("Failed to activate fallback {}: {}", output.name(), e);
                    }
                }
            }
        }

        Err(Uac2Error::NotSupported(
            "No fallback audio output available".to_string(),
        ))
    }

    pub fn deactivate_fallback(&mut self) {
        if let Some(index) = self.active_fallback {
            info!(
                "Deactivating fallback: {}",
                self.fallback_outputs[index].name()
            );
            self.active_fallback = None;
        }
    }

    pub fn has_active_fallback(&self) -> bool {
        self.active_fallback.is_some()
    }

    pub fn active_fallback_name(&self) -> Option<&str> {
        self.active_fallback
            .map(|index| self.fallback_outputs[index].name())
    }
}

impl Default for FallbackHandler {
    fn default() -> Self {
        Self::new()
    }
}
