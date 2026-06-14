use crate::uac2::error::Uac2Error;
use crate::uac2::stream_config::StreamConfig;
use crate::uac2::transfer::{IsochronousTransfer, TransferStats, TransferSynchronizer};
use rusb::{DeviceHandle, UsbContext};
use std::sync::Arc;

pub struct TransferManager<T: UsbContext> {
    transfer: IsochronousTransfer<T>,
    synchronizer: TransferSynchronizer,
    is_active: bool,
}

impl<T: UsbContext> TransferManager<T> {
    pub fn new(
        handle: Arc<DeviceHandle<T>>,
        endpoint: u8,
        config: StreamConfig,
    ) -> Result<Self, Uac2Error> {
        let transfer = IsochronousTransfer::new(handle, endpoint, config.clone())?;
        let synchronizer = TransferSynchronizer::new(config.sample_rate.hz());

        Ok(Self {
            transfer,
            synchronizer,
            is_active: false,
        })
    }

    pub fn start(&mut self) -> Result<(), Uac2Error> {
        if self.is_active {
            return Err(Uac2Error::InvalidConfiguration(
                "transfer already active".to_string(),
            ));
        }

        self.synchronizer.reset();
        self.transfer.reset_stats();
        self.is_active = true;

        Ok(())
    }

    pub fn stop(&mut self) -> Result<(), Uac2Error> {
        if !self.is_active {
            return Ok(());
        }

        self.is_active = false;
        Ok(())
    }

    pub fn submit_audio_data(&mut self, data: Vec<u8>) -> Result<(), Uac2Error> {
        if !self.is_active {
            return Err(Uac2Error::InvalidConfiguration(
                "transfer not active".to_string(),
            ));
        }

        self.synchronizer.wait_for_next();
        self.transfer.submit_buffer(data)?;

        Ok(())
    }

    pub fn is_active(&self) -> bool {
        self.is_active
    }

    pub fn available_buffers(&self) -> usize {
        self.transfer.available_buffers()
    }

    pub fn active_transfers(&self) -> usize {
        self.transfer.active_transfers()
    }

    pub fn stats(&self) -> TransferStats {
        self.transfer.stats()
    }

    pub fn can_submit(&self) -> bool {
        self.is_active && self.transfer.available_buffers() > 0
    }
}

pub struct TransferRecovery;

impl TransferRecovery {
    pub fn should_recover(stats: &TransferStats) -> bool {
        if stats.total_submitted == 0 {
            return false;
        }

        let failure_rate = stats.total_failed as f64 / stats.total_submitted as f64;
        failure_rate > 0.1
    }

    pub fn recover<T: UsbContext>(manager: &mut TransferManager<T>) -> Result<(), Uac2Error> {
        manager.stop()?;
        std::thread::sleep(std::time::Duration::from_millis(100));
        manager.start()?;
        Ok(())
    }
}
