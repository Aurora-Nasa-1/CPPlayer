//! Parametric EQ: Configurable peaking biquad bands at variable frequencies, gains, and Q values.
//! Single responsibility: apply band gains to interleaved f32 samples.

use std::f32::consts::PI;
use std::sync::atomic::{AtomicU8, Ordering};

pub const MAX_BANDS: usize = 30; // Support up to 30 custom bands
const COEFFS_PER_BAND: usize = 5;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PeqBand {
    pub freq_hz: f32,
    pub gain_db: f32,
    pub q: f32,
}

impl Default for PeqBand {
    fn default() -> Self {
        Self {
            freq_hz: 1000.0,
            gain_db: 0.0,
            q: 1.0,
        }
    }
}

/// Biquad coeffs per band: b0, b1, b2, a1, a2 (a0 normalized to 1).
#[derive(Clone, Copy)]
pub struct EqParams {
    pub enabled: bool,
    pub num_bands: usize,
    pub coeffs: [[f32; COEFFS_PER_BAND]; MAX_BANDS],
}

impl EqParams {
    pub fn disabled() -> Self {
        Self {
            enabled: false,
            num_bands: 0,
            coeffs: [[1.0, 0.0, 0.0, 0.0, 0.0]; MAX_BANDS],
        }
    }

    /// Build from dynamic PEQ bands and sample rate.
    pub fn from_peq_bands(bands: &[PeqBand], sample_rate: u32) -> Self {
        let fs = sample_rate as f32;
        let mut coeffs = [[1.0, 0.0, 0.0, 0.0, 0.0]; MAX_BANDS];
        let num_bands = bands.len().min(MAX_BANDS);

        for i in 0..num_bands {
            let band = &bands[i];
            let (b0, b1, b2, a1, a2) = peaking_coeffs(band.freq_hz, band.gain_db, band.q, fs);
            coeffs[i] = [b0, b1, b2, a1, a2];
        }

        Self {
            enabled: true,
            num_bands,
            coeffs,
        }
    }
}

/// Peaking EQ: A = 10^(dBgain/40), w0 = 2*pi*f0/Fs, alpha = sin(w0)/(2*Q).
fn peaking_coeffs(f0: f32, gain_db: f32, q: f32, fs: f32) -> (f32, f32, f32, f32, f32) {
    let a = 10.0f32.powf(gain_db / 40.0);
    let w0 = 2.0 * PI * f0 / fs;
    let cos_w0 = w0.cos();
    let sin_w0 = w0.sin();
    let alpha = sin_w0 / (2.0 * q);
    let b0 = 1.0 + alpha * a;
    let b1 = -2.0 * cos_w0;
    let b2 = 1.0 - alpha * a;
    let a0 = 1.0 + alpha / a;
    let a1 = -2.0 * cos_w0;
    let a2 = 1.0 - alpha / a;
    (b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}

/// Per-channel, per-band biquad state: x1, x2, y1, y2.
type BandState = [f32; 4];
type ChannelState = [BandState; MAX_BANDS];

/// Double-buffered params for lock-free updates from command thread.
/// State is per-channel (not double-buffered) for stereo processing.
pub struct Equalizer {
    params: [EqParams; 2],
    index: AtomicU8,
    /// Per-channel filter state. Indexed by channel (0=left, 1=right).
    state: [ChannelState; 2],
}

impl Equalizer {
    pub fn new() -> Self {
        Self {
            params: [EqParams::disabled(), EqParams::disabled()],
            index: AtomicU8::new(0),
            state: [[[0.0; 4]; MAX_BANDS]; 2],
        }
    }

    /// Called from command thread. sample_rate must match engine.
    pub fn set(&mut self, enabled: bool, bands: &[PeqBand], sample_rate: u32) {
        let next = if enabled {
            EqParams::from_peq_bands(bands, sample_rate)
        } else {
            EqParams::disabled()
        };
        let idx = self.index.load(Ordering::Relaxed);
        self.params[1 - idx as usize] = next;
        self.index.store(1 - idx, Ordering::Release);

        // Reset state when disabling to avoid artifacts when re-enabling
        if !enabled {
            for ch_state in &mut self.state {
                for band_state in ch_state.iter_mut() {
                    band_state.fill(0.0);
                }
            }
        }
    }

    #[inline]
    fn current_params(&self) -> EqParams {
        self.params[self.index.load(Ordering::Acquire) as usize]
    }

    /// Process interleaved buffer in place. channels = 2.
    pub fn process(&mut self, buf: &mut [f32], channels: usize) {
        let p = self.current_params();
        if !p.enabled || p.num_bands == 0 {
            return;
        }
        // Clamp channels to available state buffers (typically 2 for stereo)
        let max_channels = self.state.len().min(channels);
        let frames = buf.len() / channels;
        let active_coeffs = &p.coeffs[..p.num_bands];

        for f in 0..frames {
            for ch in 0..max_channels {
                let idx = f * channels + ch;
                let x0 = buf[idx];
                buf[idx] = process_sample_chain(x0, active_coeffs, &mut self.state[ch]);
            }
        }
    }
}

#[inline(always)]
fn process_sample_chain(
    x0: f32,
    coeffs: &[[f32; COEFFS_PER_BAND]],
    state: &mut ChannelState,
) -> f32 {
    let mut x = x0;
    // Only process up to coeffs.len() bands
    for (i, b) in coeffs.iter().enumerate() {
        let s = &mut state[i];
        let (b0, b1, b2, a1, a2) = (b[0], b[1], b[2], b[3], b[4]);
        let (x1, x2, y1, y2) = (s[0], s[1], s[2], s[3]);
        let y0 = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        s[0] = x;
        s[1] = x1;
        s[2] = y0;
        s[3] = y1;
        x = y0;
    }
    x
}
