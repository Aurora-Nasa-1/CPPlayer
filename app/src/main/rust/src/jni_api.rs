#![cfg(target_os = "android")]

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jstring};
use jni::JNIEnv;

use crate::api::audio_api::{
    audio_init, audio_pause, audio_play, audio_resume, audio_seek, audio_set_volume, audio_stop,
    audio_get_state, audio_get_progress, audio_poll_event,
    audio_set_dsd_output_mode, audio_set_dap_bit_perfect_enabled,
};
use crate::audio::equalizer::PeqBand;

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeInit(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    match audio_init() {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio init failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativePlay(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("Failed to get path string: {}", e);
            return 0;
        }
    };

    log::info!("RustEngine nativePlay called with path: {}", path_str);

    match audio_play(path_str.clone()) {
        Ok(_) => {
            log::info!("Audio play initiated successfully for path: {}", path_str);
            1
        }
        Err(e) => {
            log::error!("Audio play failed for path: {}. Error: {}", path_str, e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativePause(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    match audio_pause() {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio pause failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeResume(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    match audio_resume() {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio resume failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeStop(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    match audio_stop() {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio stop failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSeek(
    mut _env: JNIEnv,
    _class: JClass,
    secs: jdouble,
) -> jboolean {
    match audio_seek(secs as f64) {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio seek failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSetEqualizer(
    mut env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
    freqs: jni::objects::JFloatArray,
    gains: jni::objects::JFloatArray,
    qs: jni::objects::JFloatArray,
) -> jboolean {
    let len = match env.get_array_length(&freqs) {
        Ok(l) => l as usize,
        Err(_) => return 0,
    };
    let mut freqs_vec = vec![0.0f32; len];
    let mut gains_vec = vec![0.0f32; len];
    let mut qs_vec = vec![0.0f32; len];

    if env.get_float_array_region(&freqs, 0, &mut freqs_vec).is_err() ||
       env.get_float_array_region(&gains, 0, &mut gains_vec).is_err() ||
       env.get_float_array_region(&qs, 0, &mut qs_vec).is_err() {
        return 0;
    }

    let mut bands = Vec::with_capacity(len);
    for i in 0..len {
        bands.push(PeqBand {
            freq_hz: freqs_vec[i],
            gain_db: gains_vec[i],
            q: qs_vec[i],
        });
    }

    match crate::api::audio_api::audio_set_equalizer(enabled != 0, bands) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSetFx(
    mut _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
    balance: jfloat,
    tempo: jfloat,
    damp: jfloat,
    filter_hz: jfloat,
    delay_ms: jfloat,
    size: jfloat,
    mix: jfloat,
    feedback: jfloat,
    width: jfloat,
) -> jboolean {
    match crate::api::audio_api::audio_set_fx(
        enabled != 0,
        balance,
        tempo,
        damp,
        filter_hz,
        delay_ms,
        size,
        mix,
        feedback,
        width,
    ) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSetVolume(
    mut _env: JNIEnv,
    _class: JClass,
    vol: jfloat,
) -> jboolean {
    match audio_set_volume(vol as f32) {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Audio set volume failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeGetState(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let state = audio_get_state();
    match env.new_string(state) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            log::error!("Failed to create state string: {}", e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeGetProgress(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let progress_json = match audio_get_progress() {
        Some(p) => serde_json::json!({
            "position_secs": p.position_secs,
            "duration_secs": p.duration_secs,
            "buffer_level": p.buffer_level
        }),
        None => serde_json::json!(null),
    };

    let json_str = progress_json.to_string();
    match env.new_string(json_str) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            log::error!("Failed to create progress string: {}", e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativePollEvent(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let event_json = match audio_poll_event() {
        Some(e) => {
            match e {
                crate::api::audio_api::AudioEventType::StateChanged { state } => {
                    serde_json::json!({"type": "StateChanged", "state": state})
                }
                crate::api::audio_api::AudioEventType::Progress { position_secs, duration_secs, buffer_level } => {
                    serde_json::json!({
                        "type": "Progress",
                        "position_secs": position_secs,
                        "duration_secs": duration_secs,
                        "buffer_level": buffer_level
                    })
                }
                crate::api::audio_api::AudioEventType::TrackEnded { path } => {
                    serde_json::json!({"type": "TrackEnded", "path": path})
                }
                crate::api::audio_api::AudioEventType::CrossfadeStarted { from_path, to_path } => {
                    serde_json::json!({"type": "CrossfadeStarted", "from_path": from_path, "to_path": to_path})
                }
                crate::api::audio_api::AudioEventType::Error { message } => {
                    serde_json::json!({"type": "Error", "message": message})
                }
                crate::api::audio_api::AudioEventType::NextTrackReady { path } => {
                    serde_json::json!({"type": "NextTrackReady", "path": path})
                }
                crate::api::audio_api::AudioEventType::FormatChanged { sample_rate, bit_depth, channels, codec_name } => {
                    serde_json::json!({
                        "type": "FormatChanged",
                        "sample_rate": sample_rate,
                        "bit_depth": bit_depth,
                        "channels": channels,
                        "codec_name": codec_name
                    })
                }
            }
        }
        None => serde_json::json!(null),
    };

    let json_str = event_json.to_string();
    match env.new_string(json_str) {
        Ok(s) => s.into_raw(),
        Err(err) => {
            log::error!("Failed to create event string: {}", err);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSetDsdOutputMode(
    _env: JNIEnv,
    _class: JClass,
    mode: jint,
) -> jboolean {
    audio_set_dsd_output_mode(mode.clamp(0, 3) as u8);
    1
}

#[no_mangle]
pub extern "system" fn Java_cp_player_engine_RustEngine_nativeSetDapBitPerfectEnabled(
    _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
) -> jboolean {
    audio_set_dap_bit_perfect_enabled(enabled != 0);
    1
}
