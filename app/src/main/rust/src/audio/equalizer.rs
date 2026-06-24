//! 参数均衡器（Parametric EQ）：具有可变频率、增益和 Q 值的可配置峰值双二阶滤波频段。
//! 单一职责：将频段增益应用于交错的 f32 样本。

use std::f32::consts::PI;
use std::sync::atomic::{AtomicU8, Ordering};

pub const MAX_BANDS: usize = 30; // 最多支持 30 个自定义频段
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

/// 每个频段的双二阶系数：b0, b1, b2, a1, a2 (a0 归一化为 1)。
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

    /// 从动态的 PEQ 频段和采样率构建。
    pub fn from_peq_bands(bands: &[PeqBand], sample_rate: u32) -> Self {
        let fs = sample_rate as f32;
        let mut coeffs = [[1.0, 0.0, 0.0, 0.0, 0.0]; MAX_BANDS];
        let num_bands = bands.len().min(MAX_BANDS);

        for i in 0..num_bands {
            let band = &bands[i];

            // 验证并限制参数以防止生成 inf/NaN 系数
            let q = if band.q <= 0.0 { 0.1 } else { band.q };
            let mut freq_hz = band.freq_hz;
            if freq_hz <= 0.0 {
                freq_hz = 1.0;
            } else if freq_hz >= fs / 2.0 {
                freq_hz = fs / 2.0 - 1.0;
            }

            let (b0, b1, b2, a1, a2) = peaking_coeffs(freq_hz, band.gain_db, q, fs);
            coeffs[i] = [b0, b1, b2, a1, a2];
        }

        Self {
            enabled: true,
            num_bands,
            coeffs,
        }
    }
}

/// 峰值均衡器：A = 10^(dBgain/40), w0 = 2*pi*f0/Fs, alpha = sin(w0)/(2*Q)。
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

/// 单通道、单频段的双二阶状态：x1, x2, y1, y2。
type BandState = [f32; 4];
type ChannelState = [BandState; MAX_BANDS];

/// 双缓冲参数，用于命令线程的无锁更新。
/// 状态是按通道分配的（非双缓冲），用于立体声处理。
pub struct Equalizer {
    params: [EqParams; 2],
    index: AtomicU8,
    /// 单通道滤波器状态。按通道索引（0=左，1=右）。
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

    /// 从命令线程调用。sample_rate 必须匹配引擎。
    pub fn set(&mut self, enabled: bool, bands: &[PeqBand], sample_rate: u32) {
        let next = if enabled {
            EqParams::from_peq_bands(bands, sample_rate)
        } else {
            EqParams::disabled()
        };
        let idx = self.index.load(Ordering::Relaxed);
        self.params[1 - idx as usize] = next;
        self.index.store(1 - idx, Ordering::Release);

        // 禁用时重置状态，以避免重新启用时出现伪影
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

    /// 就地处理交错缓冲区。channels = 2。
    pub fn process(&mut self, buf: &mut [f32], channels: usize) {
        let p = self.current_params();
        if !p.enabled || p.num_bands == 0 {
            return;
        }
        // 将通道数限制在可用的状态缓冲区内（通常立体声为 2）
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
    // 仅处理最多 coeffs.len() 个频段
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
