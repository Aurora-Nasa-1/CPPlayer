//! 双通道配对 WAV 录制器
//!
//! 同时录制 DSP 处理前和处理后的音频，
//! 用于自动分析 DSP 处理是否真正提升音质。
//!
//! 设计约束：
//! - 音频回调中仅追加样本到内存 Vec，不进行文件 I/O
//! - 文件写入在 save_pair() 中完成（命令线程调用）
//! - 每个片段录制固定时长后自动保存

use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

/// 录制状态
#[derive(Debug, Clone, Copy, PartialEq)]
enum RecorderState {
    Idle,
    Recording,
    /// 缓冲区已满，等待命令线程保存
    ReadyToSave,
}

/// 双通道配对 WAV 录制器
pub struct WavPairRecorder {
    /// 是否启用录制功能
    enabled: bool,
    /// 当前状态
    state: RecorderState,
    /// 过采样器输入样本（处理前）
    before_buf: Vec<f32>,
    /// 过采样器输出样本（处理后）
    after_buf: Vec<f32>,
    /// 采样率
    sample_rate: u32,
    /// 通道数
    channels: usize,
    /// 输出目录
    output_dir: String,
    /// 当前曲目名称
    track_name: String,
    /// 每个片段的样本数上限（segment_secs * sample_rate * channels）
    segment_limit: usize,
    /// 片段计数器（用于文件命名）
    segment_index: u32,
}

impl WavPairRecorder {
    pub fn new(output_dir: String, sample_rate: u32, channels: usize) -> Self {
        let segment_secs = 10;
        let segment_limit = sample_rate as usize * channels * segment_secs;
        Self {
            enabled: false,
            state: RecorderState::Idle,
            before_buf: Vec::with_capacity(segment_limit),
            after_buf: Vec::with_capacity(segment_limit),
            sample_rate,
            channels,
            output_dir,
            track_name: String::new(),
            segment_limit,
            segment_index: 0,
        }
    }

    /// 设置录制启用/禁用
    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
        if enabled {
            if self.state == RecorderState::Idle {
                self.track_name = "live_recording".to_string();
                self.before_buf.clear();
                self.after_buf.clear();
                self.segment_index = 0;
                self.state = RecorderState::Recording;
                log::info!("[WavPairRecorder] 录制已启用，立即开始");
            }
        } else if self.state != RecorderState::Idle {
            // 禁用时保存剩余数据
            let _ = self.save_pair();
            self.state = RecorderState::Idle;
        }
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    pub fn is_recording(&self) -> bool {
        self.state == RecorderState::Recording
    }

    /// 缓冲区是否已满，需要保存
    pub fn needs_save(&self) -> bool {
        self.state == RecorderState::ReadyToSave
    }

    /// 开始新曲目录制
    pub fn start_track(&mut self, track_name: &str) {
        if !self.enabled {
            return;
        }
        // 保存之前的片段
        if self.state == RecorderState::Recording && !self.before_buf.is_empty() {
            let _ = self.save_pair();
        }
        self.track_name = sanitize_filename(track_name);
        self.before_buf.clear();
        self.after_buf.clear();
        self.segment_index = 0;
        self.state = RecorderState::Recording;
        log::info!("[WavPairRecorder] 开始录制: {}", self.track_name);
    }

    /// 写入过采样器输入样本（处理前）
    ///
    /// 在音频回调中调用，仅 extend_from_slice，零分配。
    pub fn write_before(&mut self, samples: &[f32]) {
        if self.state != RecorderState::Recording {
            return;
        }
        let remaining = self.segment_limit.saturating_sub(self.before_buf.len());
        if remaining == 0 {
            return;
        }
        let count = samples.len().min(remaining);
        self.before_buf.extend_from_slice(&samples[..count]);
    }

    /// 写入过采样器输出样本（处理后）
    ///
    /// 在音频回调中调用，仅 extend_from_slice，零分配。
    pub fn write_after(&mut self, samples: &[f32]) {
        if self.state != RecorderState::Recording {
            return;
        }
        let remaining = self.segment_limit.saturating_sub(self.after_buf.len());
        if remaining == 0 {
            // 检查是否两个缓冲区都满了
            if self.before_buf.len() >= self.segment_limit {
                self.state = RecorderState::ReadyToSave;
            }
            return;
        }
        let count = samples.len().min(remaining);
        self.after_buf.extend_from_slice(&samples[..count]);
        // 检查是否都满了
        if self.after_buf.len() >= self.segment_limit
            && self.before_buf.len() >= self.segment_limit
        {
            self.state = RecorderState::ReadyToSave;
        }
    }

    /// 手动触发保存（UI 调用）
    pub fn save_now(&mut self) -> Option<(String, String)> {
        log::info!(
            "[WavPairRecorder] 手动保存: state={:?}, before={}, after={}",
            self.state,
            self.before_buf.len(),
            self.after_buf.len()
        );
        if self.before_buf.is_empty() && self.after_buf.is_empty() {
            return None;
        }
        self.save_pair()
    }

    /// 保存一对 WAV 文件
    ///
    /// 返回 (before_path, after_path)。在命令线程中调用。
    pub fn save_pair(&mut self) -> Option<(String, String)> {
        if self.before_buf.is_empty() || self.after_buf.is_empty() {
            log::info!(
                "[WavPairRecorder] 跳过保存: before={}, after={}",
                self.before_buf.len(),
                self.after_buf.len()
            );
            if self.state == RecorderState::ReadyToSave {
                self.state = RecorderState::Recording;
            }
            return None;
        }

        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let before_filename = format!(
            "{}_{:03}_{}_before.wav",
            self.track_name, self.segment_index, timestamp
        );
        let after_filename = format!(
            "{}_{:03}_{}_after.wav",
            self.track_name, self.segment_index, timestamp
        );

        let before_path = Path::new(&self.output_dir).join(&before_filename);
        let after_path = Path::new(&self.output_dir).join(&after_filename);

        // 确保输出目录存在
        if let Some(parent) = before_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let before_len = self.before_buf.len();
        let after_len = self.after_buf.len();

        let r1 = write_wav_file(&before_path, &self.before_buf, self.sample_rate, self.channels);
        let r2 = write_wav_file(&after_path, &self.after_buf, self.sample_rate, self.channels);

        match (r1, r2) {
            (Ok(()), Ok(())) => {
                let bp = before_path.to_string_lossy().to_string();
                let ap = after_path.to_string_lossy().to_string();
                let duration = before_len as f32
                    / self.sample_rate as f32
                    / self.channels as f32;
                log::info!(
                    "[WavPairRecorder] 已保存片段 {}: {:.1}s, before={} 样本, after={} 样本",
                    self.segment_index,
                    duration,
                    before_len,
                    after_len,
                );
                self.before_buf.clear();
                self.after_buf.clear();
                self.segment_index += 1;
                if self.enabled {
                    self.state = RecorderState::Recording;
                } else {
                    self.state = RecorderState::Idle;
                }
                Some((bp, ap))
            }
            (e1, e2) => {
                if let Err(e) = e1 {
                    log::error!("[WavPairRecorder] before 保存失败: {}", e);
                }
                if let Err(e) = e2 {
                    log::error!("[WavPairRecorder] after 保存失败: {}", e);
                }
                self.before_buf.clear();
                self.after_buf.clear();
                self.state = RecorderState::Recording;
                None
            }
        }
    }

    /// 重置录制器（切换曲目时）
    pub fn reset(&mut self) {
        if self.state == RecorderState::Recording && !self.before_buf.is_empty() {
            let _ = self.save_pair();
        }
        self.before_buf.clear();
        self.after_buf.clear();
        self.state = RecorderState::Idle;
        self.segment_index = 0;
    }

    /// 设置输出目录
    pub fn set_output_dir(&mut self, dir: String) {
        self.output_dir = dir;
    }
}

/// 手动写入 PCM f32 WAV 文件
fn write_wav_file(
    path: &Path,
    samples: &[f32],
    sample_rate: u32,
    channels: usize,
) -> std::io::Result<()> {
    let file = File::create(path)?;
    let mut w = BufWriter::new(file);

    let num_samples = samples.len() as u32;
    let bits_per_sample: u16 = 32;
    let bytes_per_sample: u16 = bits_per_sample / 8;
    let block_align: u16 = channels as u16 * bytes_per_sample;
    let byte_rate: u32 = sample_rate * block_align as u32;
    let data_size: u32 = num_samples * bytes_per_sample as u32;

    // RIFF header
    w.write_all(b"RIFF")?;
    w.write_all(&(36u32 + data_size).to_le_bytes())?;
    w.write_all(b"WAVE")?;

    // fmt chunk
    w.write_all(b"fmt ")?;
    w.write_all(&16u32.to_le_bytes())?;
    w.write_all(&3u16.to_le_bytes())?; // format tag: 3 = IEEE_FLOAT
    w.write_all(&(channels as u16).to_le_bytes())?;
    w.write_all(&sample_rate.to_le_bytes())?;
    w.write_all(&byte_rate.to_le_bytes())?;
    w.write_all(&block_align.to_le_bytes())?;
    w.write_all(&bits_per_sample.to_le_bytes())?;

    // data chunk
    w.write_all(b"data")?;
    w.write_all(&data_size.to_le_bytes())?;

    for &sample in samples {
        w.write_all(&sample.to_le_bytes())?;
    }

    w.flush()?;
    Ok(())
}

/// 清理文件名中的非法字符
fn sanitize_filename(name: &str) -> String {
    let stem = Path::new(name)
        .file_stem()
        .unwrap_or_default()
        .to_string_lossy();

    stem.chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => '_',
            c => c,
        })
        .take(100)
        .collect()
}
