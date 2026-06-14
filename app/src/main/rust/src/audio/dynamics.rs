//! Lightweight linked-stereo dynamics processors for the realtime callback.
//!
//! The implementation is intentionally simple:
//! - no allocations in the audio callback
//! - one shared envelope per frame for stereo coherence
//! - soft feed-forward compressor followed by a peak limiter

const EPSILON: f32 = 1.0e-6;
const LIMITER_ATTACK_MS: f32 = 0.5;

#[derive(Debug, Clone, Copy)]
pub struct CompressorSettings {
    pub enabled: bool,
    pub threshold_db: f32,
    pub ratio: f32,
    pub attack_ms: f32,
    pub release_ms: f32,
    pub makeup_gain_db: f32,
}

impl CompressorSettings {
    pub const fn disabled() -> Self {
        Self {
            enabled: false,
            threshold_db: -18.0,
            ratio: 3.0,
            attack_ms: 12.0,
            release_ms: 140.0,
            makeup_gain_db: 0.0,
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct LimiterSettings {
    pub enabled: bool,
    pub input_gain_db: f32,
    pub ceiling_db: f32,
    pub release_ms: f32,
}

impl LimiterSettings {
    pub const fn disabled() -> Self {
        Self {
            enabled: false,
            input_gain_db: 0.0,
            ceiling_db: -0.8,
            release_ms: 80.0,
        }
    }
}

pub struct DynamicsChain {
    sample_rate: u32,
    compressor: CompressorSettings,
    limiter: LimiterSettings,
    compressor_attack_coeff: f32,
    compressor_release_coeff: f32,
    limiter_attack_coeff: f32,
    limiter_release_coeff: f32,
    compressor_gain_db: f32,
    limiter_gain: f32,
    limiter_input_gain_lin: f32,
    limiter_ceiling_lin: f32,
}

impl DynamicsChain {
    pub fn new(sample_rate: u32) -> Self {
        let mut chain = Self {
            sample_rate,
            compressor: CompressorSettings::disabled(),
            limiter: LimiterSettings::disabled(),
            compressor_attack_coeff: 0.0,
            compressor_release_coeff: 0.0,
            limiter_attack_coeff: 0.0,
            limiter_release_coeff: 0.0,
            compressor_gain_db: 0.0,
            limiter_gain: 1.0,
            limiter_input_gain_lin: 1.0,
            limiter_ceiling_lin: db_to_linear(-0.8),
        };
        chain.refresh_coefficients();
        chain
    }

    pub fn set_compressor(
        &mut self,
        enabled: bool,
        threshold_db: f32,
        ratio: f32,
        attack_ms: f32,
        release_ms: f32,
        makeup_gain_db: f32,
    ) {
        self.compressor = CompressorSettings {
            enabled,
            threshold_db: threshold_db.clamp(-36.0, 0.0),
            ratio: ratio.clamp(1.0, 12.0),
            attack_ms: attack_ms.clamp(1.0, 100.0),
            release_ms: release_ms.clamp(20.0, 500.0),
            makeup_gain_db: makeup_gain_db.clamp(-12.0, 12.0),
        };
        self.refresh_coefficients();
        if !enabled {
            self.compressor_gain_db = 0.0;
        }
    }

    pub fn set_limiter(
        &mut self,
        enabled: bool,
        input_gain_db: f32,
        ceiling_db: f32,
        release_ms: f32,
    ) {
        self.limiter = LimiterSettings {
            enabled,
            input_gain_db: input_gain_db.clamp(0.0, 12.0),
            ceiling_db: ceiling_db.clamp(-12.0, 0.0),
            release_ms: release_ms.clamp(20.0, 300.0),
        };
        self.refresh_coefficients();
        if !enabled {
            self.limiter_gain = 1.0;
        }
    }

    pub fn process(&mut self, buf: &mut [f32], channels: usize) {
        if channels == 0 || (!self.compressor.enabled && !self.limiter.enabled) {
            return;
        }

        for frame in buf.chunks_exact_mut(channels) {
            if self.compressor.enabled {
                self.process_compressor_frame(frame);
            }
            if self.limiter.enabled {
                self.process_limiter_frame(frame);
            }
        }
    }

    fn process_compressor_frame(&mut self, frame: &mut [f32]) {
        let peak = frame
            .iter()
            .fold(0.0f32, |max_peak, sample| max_peak.max(sample.abs()));

        let desired_gain_db = if peak <= EPSILON {
            self.compressor.makeup_gain_db
        } else {
            let input_db = linear_to_db(peak);
            if input_db <= self.compressor.threshold_db {
                self.compressor.makeup_gain_db
            } else {
                let compressed_db = self.compressor.threshold_db
                    + (input_db - self.compressor.threshold_db) / self.compressor.ratio;
                self.compressor.makeup_gain_db + (compressed_db - input_db)
            }
        };

        let coeff = if desired_gain_db < self.compressor_gain_db {
            self.compressor_attack_coeff
        } else {
            self.compressor_release_coeff
        };

        self.compressor_gain_db = coeff * self.compressor_gain_db + (1.0 - coeff) * desired_gain_db;

        let gain = db_to_linear(self.compressor_gain_db);
        for sample in frame.iter_mut() {
            *sample *= gain;
        }
    }

    fn process_limiter_frame(&mut self, frame: &mut [f32]) {
        let peak = frame.iter().fold(0.0f32, |max_peak, sample| {
            max_peak.max((sample * self.limiter_input_gain_lin).abs())
        });

        let desired_gain = if peak > self.limiter_ceiling_lin && peak > EPSILON {
            self.limiter_ceiling_lin / peak
        } else {
            1.0
        };

        let coeff = if desired_gain < self.limiter_gain {
            self.limiter_attack_coeff
        } else {
            self.limiter_release_coeff
        };

        self.limiter_gain = coeff * self.limiter_gain + (1.0 - coeff) * desired_gain;
        let gain = self.limiter_input_gain_lin * self.limiter_gain;

        for sample in frame.iter_mut() {
            *sample = (*sample * gain).clamp(-1.0, 1.0);
        }
    }

    fn refresh_coefficients(&mut self) {
        self.compressor_attack_coeff = time_to_coeff(self.compressor.attack_ms, self.sample_rate);
        self.compressor_release_coeff = time_to_coeff(self.compressor.release_ms, self.sample_rate);
        self.limiter_attack_coeff = time_to_coeff(LIMITER_ATTACK_MS, self.sample_rate);
        self.limiter_release_coeff = time_to_coeff(self.limiter.release_ms, self.sample_rate);
        self.limiter_input_gain_lin = db_to_linear(self.limiter.input_gain_db);
        self.limiter_ceiling_lin = db_to_linear(self.limiter.ceiling_db);
    }
}

fn time_to_coeff(milliseconds: f32, sample_rate: u32) -> f32 {
    let seconds = (milliseconds.max(0.1)) / 1000.0;
    (-1.0 / (seconds * sample_rate as f32)).exp()
}

fn db_to_linear(db: f32) -> f32 {
    10.0f32.powf(db / 20.0)
}

fn linear_to_db(linear: f32) -> f32 {
    20.0 * linear.max(EPSILON).log10()
}
