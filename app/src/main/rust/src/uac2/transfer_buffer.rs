use crate::uac2::error::Uac2Error;
use crate::uac2::stream_config::StreamConfig;
use std::sync::Arc;

const DEFAULT_BUFFER_COUNT: usize = 4;
const MIN_BUFFER_COUNT: usize = 2;
const MAX_BUFFER_COUNT: usize = 16;

#[derive(Debug, Clone)]
pub struct TransferBuffer {
    data: Arc<Vec<u8>>,
    capacity: usize,
    length: usize,
}

impl TransferBuffer {
    pub fn new(capacity: usize) -> Result<Self, Uac2Error> {
        if capacity == 0 {
            return Err(Uac2Error::InvalidConfiguration(
                "buffer capacity must be > 0".to_string(),
            ));
        }

        Ok(Self {
            data: Arc::new(vec![0u8; capacity]),
            capacity,
            length: 0,
        })
    }

    pub fn with_data(data: Vec<u8>) -> Self {
        let capacity = data.len();
        Self {
            data: Arc::new(data),
            capacity,
            length: capacity,
        }
    }

    pub fn capacity(&self) -> usize {
        self.capacity
    }

    pub fn len(&self) -> usize {
        self.length
    }

    pub fn is_empty(&self) -> bool {
        self.length == 0
    }

    pub fn as_slice(&self) -> &[u8] {
        &self.data[..self.length]
    }

    pub fn set_length(&mut self, length: usize) -> Result<(), Uac2Error> {
        if length > self.capacity {
            return Err(Uac2Error::BufferOverflow);
        }
        self.length = length;
        Ok(())
    }

    pub fn reset(&mut self) {
        self.length = 0;
    }
}

pub struct BufferPool {
    buffers: Vec<TransferBuffer>,
    buffer_size: usize,
    available: Vec<usize>,
}

impl BufferPool {
    pub fn new(buffer_size: usize, count: usize) -> Result<Self, Uac2Error> {
        if count < MIN_BUFFER_COUNT || count > MAX_BUFFER_COUNT {
            return Err(Uac2Error::InvalidConfiguration(format!(
                "buffer count must be between {} and {}",
                MIN_BUFFER_COUNT, MAX_BUFFER_COUNT
            )));
        }

        let mut buffers = Vec::with_capacity(count);
        for _ in 0..count {
            buffers.push(TransferBuffer::new(buffer_size)?);
        }

        let available = (0..count).collect();

        Ok(Self {
            buffers,
            buffer_size,
            available,
        })
    }

    pub fn from_config(config: &StreamConfig) -> Result<Self, Uac2Error> {
        let buffer_size = config.packet_size;
        Self::new(buffer_size, DEFAULT_BUFFER_COUNT)
    }

    pub fn acquire(&mut self) -> Option<(usize, &mut TransferBuffer)> {
        self.available.pop().map(|idx| {
            let buffer = &mut self.buffers[idx];
            buffer.reset();
            (idx, buffer)
        })
    }

    pub fn release(&mut self, index: usize) -> Result<(), Uac2Error> {
        if index >= self.buffers.len() {
            return Err(Uac2Error::InvalidConfiguration(format!(
                "invalid buffer index: {}",
                index
            )));
        }

        if self.available.contains(&index) {
            return Err(Uac2Error::InvalidConfiguration(format!(
                "buffer {} already released",
                index
            )));
        }

        self.available.push(index);
        Ok(())
    }

    pub fn buffer_size(&self) -> usize {
        self.buffer_size
    }

    pub fn total_count(&self) -> usize {
        self.buffers.len()
    }

    pub fn available_count(&self) -> usize {
        self.available.len()
    }

    pub fn is_full(&self) -> bool {
        self.available.is_empty()
    }
}

pub struct BufferManager {
    pool: BufferPool,
    in_flight: Vec<usize>,
}

impl BufferManager {
    pub fn new(pool: BufferPool) -> Self {
        Self {
            pool,
            in_flight: Vec::new(),
        }
    }

    pub fn from_config(config: &StreamConfig) -> Result<Self, Uac2Error> {
        let pool = BufferPool::from_config(config)?;
        Ok(Self::new(pool))
    }

    pub fn acquire_buffer(&mut self) -> Option<(usize, &mut TransferBuffer)> {
        self.pool.acquire().map(|(idx, buffer)| {
            self.in_flight.push(idx);
            (idx, buffer)
        })
    }

    pub fn release_buffer(&mut self, index: usize) -> Result<(), Uac2Error> {
        if let Some(pos) = self.in_flight.iter().position(|&i| i == index) {
            self.in_flight.remove(pos);
            self.pool.release(index)?;
            Ok(())
        } else {
            Err(Uac2Error::InvalidConfiguration(format!(
                "buffer {} not in flight",
                index
            )))
        }
    }

    pub fn available_count(&self) -> usize {
        self.pool.available_count()
    }

    pub fn in_flight_count(&self) -> usize {
        self.in_flight.len()
    }

    pub fn has_available(&self) -> bool {
        !self.pool.is_full()
    }
}
