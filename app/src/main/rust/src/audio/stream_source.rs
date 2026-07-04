//! 流式音频数据源 — 通过 JNI 从 Kotlin 接收数据块，为 Symphonia 提供 MediaSource。
//!
//! ## 架构
//!
//! ```text
//! Kotlin (OkHttp/BackendDataSource)
//!   │  HTTP 分块下载
//!   │  JNI: nativeFeedStreamData(bytes)
//!   ▼
//! StreamBuffer (内存缓冲区, 可配置大小)
//!   │  impl Read + Seek + MediaSource
//!   ▼
//! Symphonia FormatReader → Decoder → Ring Buffer → Audio Callback
//! ```
//!
//! ## Seek 行为
//!
//! - **缓冲区内 seek**：直接移动读指针，无需重新下载
//! - **缓冲区外 seek**：返回错误，引擎停止解码器，通知 Kotlin 从新字节位置下载

use std::io::{self, Read, Seek, SeekFrom};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;

use parking_lot::{Condvar, Mutex, MutexGuard};
use symphonia::core::io::MediaSource;

/// 流式字节缓冲区，线程安全。
///
/// - Kotlin 线程通过 `feed_data()` 写入数据
/// - 解码线程通过 `Read::read()` 读取数据
/// - `Condvar` 用于在数据不足时阻塞解码线程
#[derive(Clone)]
pub struct StreamBuffer {
    inner: Arc<StreamBufferInner>,
}

struct StreamBufferInner {
    /// 数据缓冲区（包含所有已接收但未读取的数据）
    buffer: Mutex<Vec<u8>>,
    /// 当前读取位置（在整个流中的绝对字节位置）
    read_pos: AtomicU64,
    /// 已写入的总字节数
    write_pos: AtomicU64,
    /// 内容总长度（0 = 未知）
    content_length: AtomicU64,
    /// 流结束标志
    eos: AtomicBool,
    /// 数据到达通知（唤醒阻塞的 read）
    data_notify: Condvar,
    /// Seek 请求的目标字节位置（u64::MAX = 无 pending seek）
    seek_target: AtomicU64,
}

const SEEK_NONE: u64 = u64::MAX;
/// read 等待数据的最大超时
const READ_TIMEOUT: Duration = Duration::from_secs(10);

impl StreamBuffer {
    /// 创建新的流缓冲区。
    pub fn new() -> Self {
        Self {
            inner: Arc::new(StreamBufferInner {
                buffer: Mutex::new(Vec::with_capacity(256 * 1024)),
                read_pos: AtomicU64::new(0),
                write_pos: AtomicU64::new(0),
                content_length: AtomicU64::new(0),
                eos: AtomicBool::new(false),
                data_notify: Condvar::new(),
                seek_target: AtomicU64::new(SEEK_NONE),
            }),
        }
    }

    /// 设置内容总长度（HTTP Content-Length）。0 或 -1 表示未知。
    pub fn set_content_length(&self, length: i64) {
        if length > 0 {
            self.inner
                .content_length
                .store(length as u64, Ordering::Relaxed);
        }
    }

    /// 从 JNI 接收数据块，追加到缓冲区。
    pub fn feed_data(&self, data: &[u8]) {
        if data.is_empty() {
            return;
        }
        let mut buf = self.inner.buffer.lock();
        buf.extend_from_slice(data);
        self.inner
            .write_pos
            .fetch_add(data.len() as u64, Ordering::Relaxed);
        // 唤醒阻塞的 read
        self.inner.data_notify.notify_all();
    }

    /// 标记流结束。
    pub fn set_eos(&self) {
        self.inner.eos.store(true, Ordering::Relaxed);
        self.inner.data_notify.notify_all();
    }

    /// 请求 seek 到指定字节位置。
    /// 如果目标在缓冲区内，直接移动读指针；否则清除缓冲区，等待新数据。
    ///
    /// 返回 `true` 如果 seek 在缓冲区内成功完成（无需重新下载）。
    /// 返回 `false` 如果需要 Kotlin 从新位置重新下载。
    pub fn request_seek(&self, byte_pos: u64) -> bool {
        let mut buf = self.inner.buffer.lock();
        let buf_len = buf.len() as u64;
        let write_pos = self.inner.write_pos.load(Ordering::Relaxed);
        let read_pos = self.inner.read_pos.load(Ordering::Relaxed);

        // 缓冲区包含 [read_pos, write_pos) 范围的数据
        // 缓冲区中的数据起始位置 = write_pos - buf_len
        let buf_start = write_pos.saturating_sub(buf_len);

        if byte_pos >= buf_start && byte_pos <= write_pos {
            // 目标在缓冲区内：移动读指针
            // 需要丢弃读指针之前的数据
            let discard = (byte_pos - buf_start) as usize;
            if discard > 0 && discard <= buf.len() {
                buf.drain(..discard);
            }
            self.inner.read_pos.store(byte_pos, Ordering::Relaxed);
            self.inner.seek_target.store(SEEK_NONE, Ordering::Relaxed);
            self.inner.data_notify.notify_all();
            true
        } else {
            // 目标在缓冲区外：清除缓冲区
            buf.clear();
            self.inner.read_pos.store(byte_pos, Ordering::Relaxed);
            self.inner.seek_target.store(byte_pos, Ordering::Relaxed);
            self.inner.eos.store(false, Ordering::Relaxed);
            self.inner.data_notify.notify_all();
            false
        }
    }

    /// 检查是否有 pending seek。
    pub fn has_pending_seek(&self) -> bool {
        self.inner.seek_target.load(Ordering::Relaxed) != SEEK_NONE
    }

    /// 清空缓冲区。
    pub fn clear(&self) {
        let mut buf = self.inner.buffer.lock();
        buf.clear();
        self.inner.eos.store(false, Ordering::Relaxed);
    }

    /// 获取可用数据字节数。
    pub fn available_bytes(&self) -> usize {
        self.inner.buffer.lock().len()
    }

    /// 内容总长度（如果已知）。
    pub fn content_length(&self) -> Option<u64> {
        let len = self.inner.content_length.load(Ordering::Relaxed);
        if len > 0 {
            Some(len)
        } else {
            None
        }
    }

    /// 是否已标记 EOS。
    pub fn is_eos(&self) -> bool {
        self.inner.eos.load(Ordering::Relaxed)
    }

    /// 获取当前读取位置。
    pub fn read_position(&self) -> u64 {
        self.inner.read_pos.load(Ordering::Relaxed)
    }
}

/// 为 Symphonia 实现 std::io::Read trait。
///
/// 在数据不足时阻塞等待（通过 Condvar），
/// 这是安全的因为解码线程不是实时音频线程。
impl Read for StreamBuffer {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        let mut inner_buf = self.inner.buffer.lock();

        // 等待数据到达
        if inner_buf.is_empty() && !self.inner.eos.load(Ordering::Relaxed) {
            let wait_result = self
                .inner
                .data_notify
                .wait_for(&mut inner_buf, READ_TIMEOUT);

            // 检查是否有 pending seek
            if self.inner.seek_target.load(Ordering::Relaxed) != SEEK_NONE {
                return Err(io::Error::new(
                    io::ErrorKind::Interrupted,
                    "Seek pending",
                ));
            }

            if wait_result.timed_out() && inner_buf.is_empty() {
                if self.inner.eos.load(Ordering::Relaxed) {
                    return Ok(0); // EOF
                }
                return Err(io::Error::new(
                    io::ErrorKind::TimedOut,
                    "Read timeout waiting for data",
                ));
            }
        }

        if inner_buf.is_empty() {
            if self.inner.eos.load(Ordering::Relaxed) {
                return Ok(0); // EOF
            }
            return Err(io::Error::new(
                io::ErrorKind::WouldBlock,
                "No data available",
            ));
        }

        // 从缓冲区读取数据
        let to_read = buf.len().min(inner_buf.len());
        buf[..to_read].copy_from_slice(&inner_buf[..to_read]);
        inner_buf.drain(..to_read);
        self.inner
            .read_pos
            .fetch_add(to_read as u64, Ordering::Relaxed);

        Ok(to_read)
    }
}

/// 为 Symphonia 实现 std::io::Seek trait。
///
/// 缓冲区内的 seek 直接移动读指针。
/// 缓冲区外的 seek 返回错误（需要重新下载）。
impl Seek for StreamBuffer {
    fn seek(&mut self, pos: SeekFrom) -> io::Result<u64> {
        let target = match pos {
            SeekFrom::Start(offset) => offset,
            SeekFrom::End(offset) => {
                let len = self
                    .inner
                    .content_length
                    .load(Ordering::Relaxed);
                if len == 0 {
                    return Err(io::Error::new(
                        io::ErrorKind::Unsupported,
                        "Cannot seek from end: content length unknown",
                    ));
                }
                (len as i64 + offset) as u64
            }
            SeekFrom::Current(offset) => {
                let current = self.inner.read_pos.load(Ordering::Relaxed);
                (current as i64 + offset) as u64
            }
        };

        let success = self.request_seek(target);
        if success {
            Ok(target)
        } else {
            // 需要重新下载 — 返回错误让引擎处理
            Err(io::Error::new(
                io::ErrorKind::NotFound,
                format!("Seek target {} beyond buffer, need re-download", target),
            ))
        }
    }
}

/// 为 Symphonia 实现 MediaSource trait。
impl MediaSource for StreamBuffer {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        self.content_length()
    }
}

/// 从 StreamBuffer probe 音频格式。
///
/// 阻塞直到足够的数据到达（或超时）。
pub fn probe_stream(
    buffer: &StreamBuffer,
) -> Result<super::decoder::ProbeResult, super::decoder::DecoderError> {
    use symphonia::core::codecs::CODEC_TYPE_NULL;
    use symphonia::core::formats::FormatOptions;
    use symphonia::core::io::MediaSourceStream;
    use symphonia::core::meta::MetadataOptions;
    use symphonia::core::probe::Hint;

    // 等待足够的数据到达用于 probe
    let min_probe_bytes = 8 * 1024;
    let mut waited = 0;
    while buffer.available_bytes() < min_probe_bytes && waited < 30 {
        if buffer.is_eos() && buffer.available_bytes() > 0 {
            break;
        }
        std::thread::sleep(Duration::from_millis(100));
        waited += 1;
    }

    if buffer.available_bytes() == 0 {
        return Err(super::decoder::DecoderError::IoError(io::Error::new(
            io::ErrorKind::TimedOut,
            "No data received for probe",
        )));
    }

    let format_opts = FormatOptions {
        enable_gapless: true,
        ..Default::default()
    };
    let metadata_opts = MetadataOptions::default();
    let hint = Hint::new();

    // 创建 MediaSourceStream — 需要 clone StreamBuffer 因为 create_mss 消耗所有权
    let mss = MediaSourceStream::new(Box::new(buffer.clone()), Default::default());

    let probed = symphonia::default::get_probe()
        .format(&hint, mss, &format_opts, &metadata_opts)
        .map_err(|e| super::decoder::DecoderError::UnsupportedFormat(e.to_string()))?;

    let format = probed.format;

    let track = format
        .tracks()
        .iter()
        .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
        .ok_or(super::decoder::DecoderError::NoAudioTrack)?;

    let track_id = track.id;
    let codec_params = &track.codec_params;

    let sample_rate = codec_params.sample_rate.unwrap_or(44100);
    let channels = codec_params.channels.map(|c| c.count()).unwrap_or(2);
    let bit_depth = codec_params.bits_per_sample.unwrap_or(0) as u16;

    let duration_secs = if let Some(n_frames) = codec_params.n_frames {
        if let Some(time_base) = codec_params.time_base {
            time_base.calc_time(n_frames).seconds as f64
        } else {
            n_frames as f64 / sample_rate as f64
        }
    } else {
        0.0
    };

    let total_samples = (duration_secs * sample_rate as f64 * channels as f64) as u64;

    let codec_name = format!("{:?}", codec_params.codec);

    let decoder_opts = symphonia::core::codecs::DecoderOptions::default();
    let decoder: Box<dyn symphonia::core::codecs::Decoder> =
        symphonia::default::get_codecs()
            .make(&codec_params, &decoder_opts)
            .map_err(|e| super::decoder::DecoderError::UnsupportedFormat(e.to_string()))?;

    let source_info = super::source::SourceInfo {
        path: std::path::PathBuf::from("[stream]"),
        original_sample_rate: sample_rate,
        output_sample_rate: sample_rate,
        channels,
        total_samples,
        duration_secs,
        bit_depth,
        codec_name,
    };

    Ok(super::decoder::ProbeResult {
        source_info,
        format,
        decoder,
        track_id,
    })
}

/// 流式解码线程。
///
/// 从 StreamBuffer 读取数据并解码到 AudioSource 的 ring buffer。
/// 当数据不足时阻塞等待，不会产生垃圾数据。
pub struct StreamDecoderThread {
    handle: Option<std::thread::JoinHandle<Result<(), super::decoder::DecoderError>>>,
    stop_signal: Arc<AtomicBool>,
}

impl StreamDecoderThread {
    /// 从 StreamBuffer 创建解码线程。
    ///
    /// 阻塞直到 probe 完成（需要足够的数据到达）。
    pub fn spawn(
        buffer: StreamBuffer,
        output_sample_rate: u32,
        output_channels: usize,
    ) -> Result<(super::source::AudioSource, Self), super::decoder::DecoderError> {
        let probe_result = probe_stream(&buffer)?;

        let mut source_info = probe_result.source_info.clone();
        source_info.output_sample_rate = output_sample_rate;
        source_info.channels = output_channels;
        source_info.total_samples =
            (source_info.duration_secs * output_sample_rate as f64 * output_channels as f64) as u64;

        let (source, producer) = super::source::AudioSource::new(source_info);
        let stop_signal = Arc::new(AtomicBool::new(false));
        let stop_signal_clone = Arc::clone(&stop_signal);

        let handle = std::thread::Builder::new()
            .name("stream-decoder".to_string())
            .spawn(move || {
                stream_decode_thread(
                    probe_result,
                    buffer,
                    producer,
                    output_sample_rate,
                    output_channels,
                    stop_signal_clone,
                )
            })
            .map_err(|e| super::decoder::DecoderError::IoError(e.into()))?;

        Ok((
            source,
            Self {
                handle: Some(handle),
                stop_signal,
            },
        ))
    }

    pub fn stop(&self) {
        self.stop_signal.store(true, Ordering::Release);
    }

    pub fn join(mut self) -> Result<(), super::decoder::DecoderError> {
        if let Some(handle) = self.handle.take() {
            handle
                .join()
                .map_err(|_| {
                    super::decoder::DecoderError::DecodingFailed("Thread panicked".to_string())
                })?
        } else {
            Ok(())
        }
    }

    pub fn is_running(&self) -> bool {
        self.handle
            .as_ref()
            .map(|h| !h.is_finished())
            .unwrap_or(false)
    }
}

impl Drop for StreamDecoderThread {
    fn drop(&mut self) {
        self.stop();
    }
}

/// 流式解码线程主循环。
fn stream_decode_thread(
    probe_result: super::decoder::ProbeResult,
    buffer: StreamBuffer,
    mut producer: super::source::SourceProducer,
    output_sample_rate: u32,
    output_channels: usize,
    stop_signal: Arc<AtomicBool>,
) -> Result<(), super::decoder::DecoderError> {
    use super::resampler::AudioResampler;
    use symphonia::core::audio::Signal;
    use symphonia::core::errors::Error as SymphoniaError;

    let super::decoder::ProbeResult {
        source_info,
        mut format,
        mut decoder,
        track_id,
    } = probe_result;

    let source_channels = source_info.channels;
    let needs_resampling = source_info.original_sample_rate != output_sample_rate;
    let needs_channel_remix = source_channels != output_channels;

    let mut resampler = if needs_resampling {
        Some(
            AudioResampler::new(
                source_info.original_sample_rate,
                output_sample_rate,
                output_channels,
                4096, // DECODE_CHUNK_SIZE
            )
            .map_err(super::decoder::DecoderError::ResamplingFailed)?,
        )
    } else {
        None
    };

    let mut decode_buffer: Vec<f32> = Vec::with_capacity(4096 * source_channels * 2);
    let mut remix_buffer: Vec<f32> = Vec::with_capacity(4096 * output_channels * 2);
    let mut resample_buffer: Vec<f32> = Vec::with_capacity(
        (4096f64 * output_sample_rate as f64 / source_info.original_sample_rate as f64 * 1.2)
            as usize
            * output_channels
            + 256,
    );

    log::info!(
        "[STREAM-DECODER] sample_rate={} output_rate={} channels={} resampling={}",
        source_info.original_sample_rate,
        output_sample_rate,
        source_channels,
        needs_resampling
    );

    // 解码循环
    loop {
        if stop_signal.load(Ordering::Acquire) || producer.should_stop() {
            break;
        }

        // 检查 pending seek
        if buffer.has_pending_seek() {
            log::info!("[STREAM-DECODER] Pending seek detected, pausing decode");
            while !stop_signal.load(Ordering::Acquire) && buffer.has_pending_seek() {
                std::thread::sleep(Duration::from_millis(50));
            }
            if stop_signal.load(Ordering::Acquire) {
                break;
            }
        }

        // 获取下一个 packet
        let packet = match format.next_packet() {
            Ok(packet) => packet,
            Err(SymphoniaError::IoError(ref e))
                if e.kind() == std::io::ErrorKind::UnexpectedEof =>
            {
                log::info!("[STREAM-DECODER] End of stream");
                break;
            }
            Err(SymphoniaError::IoError(ref e))
                if e.kind() == std::io::ErrorKind::Interrupted =>
            {
                continue;
            }
            Err(SymphoniaError::IoError(ref e))
                if e.kind() == std::io::ErrorKind::TimedOut =>
            {
                log::debug!("[STREAM-DECODER] Read timeout, waiting for data...");
                continue;
            }
            Err(SymphoniaError::ResetRequired) => {
                decoder.reset();
                continue;
            }
            Err(e) => {
                log::error!("[STREAM-DECODER] Decode error: {}", e);
                return Err(super::decoder::DecoderError::DecodingFailed(e.to_string()));
            }
        };

        if packet.track_id() != track_id {
            continue;
        }

        // 解码
        let decoded = match decoder.decode(&packet) {
            Ok(decoded) => decoded,
            Err(SymphoniaError::DecodeError(e)) => {
                log::warn!("[STREAM-DECODER] Decode error (skipping): {}", e);
                continue;
            }
            Err(e) => {
                return Err(super::decoder::DecoderError::DecodingFailed(e.to_string()));
            }
        };

        // 转换为 interleaved f32
        decode_buffer.clear();
        super::decoder::convert_to_interleaved_f32(&decoded, &mut decode_buffer);

        // 通道 remix
        let remixed_samples = if needs_channel_remix {
            remix_buffer.clear();
            remix_buffer.resize(
                (decode_buffer.len() / source_channels.max(1)) * output_channels,
                0.0,
            );
            super::decoder::remix_interleaved_channels(
                &decode_buffer,
                source_channels,
                output_channels,
                &mut remix_buffer,
            );
            &remix_buffer[..]
        } else {
            &decode_buffer[..]
        };

        // 重采样
        let output_samples = if let Some(ref mut resampler) = resampler {
            resample_buffer.clear();
            resample_buffer.resize(
                (remixed_samples.len() as f64 * output_sample_rate as f64
                    / source_info.original_sample_rate as f64
                    * 1.2) as usize
                    + 256,
                0.0,
            );
            let written = resampler
                .process_interleaved(remixed_samples, &mut resample_buffer)
                .map_err(super::decoder::DecoderError::ResamplingFailed)?;
            &resample_buffer[..written]
        } else {
            remixed_samples
        };

        // 写入 ring buffer
        let mut offset = 0;
        while offset < output_samples.len() {
            if stop_signal.load(Ordering::Acquire) || producer.should_stop() {
                break;
            }
            let chunk = &output_samples[offset..];
            let written = producer.write(chunk);
            offset += written;
            if written == 0 {
                producer.wait_for_space(chunk.len().min(1024), 100);
            }
        }
    }

    // flush resampler
    if let Some(ref mut resampler) = resampler {
        resample_buffer.clear();
        resample_buffer.resize(
            (4096f64 * output_sample_rate as f64 / source_info.original_sample_rate as f64 * 1.2)
                as usize
                * output_channels
                + 256,
            0.0,
        );
        loop {
            let written = resampler
                .flush(&mut resample_buffer)
                .map_err(super::decoder::DecoderError::ResamplingFailed)?;
            if written == 0 {
                break;
            }
            let mut offset = 0;
            while offset < written {
                if stop_signal.load(Ordering::Acquire) || producer.should_stop() {
                    break;
                }
                let chunk = &resample_buffer[offset..written];
                let w = producer.write(chunk);
                offset += w;
                if w == 0 {
                    producer.wait_for_space(chunk.len().min(1024), 100);
                }
            }
        }
    }

    producer.finish();
    Ok(())
}
