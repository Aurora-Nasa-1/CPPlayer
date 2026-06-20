use std::env;
use std::path::PathBuf;

fn main() {
    let opus_root = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap()).join("opus");
    let opus_include = opus_root.join("include");
    let opus_src = opus_root.join("src");
    let opus_celt = opus_root.join("celt");
    let opus_silk = opus_root.join("silk");
    let opus_silk_fixed = opus_root.join("silk").join("fixed");
    let opus_silk_float = opus_root.join("silk").join("float");

    let mut build = cc::Build::new();
    build
        .warnings(false)
        .opt_level(2)
        .define("OPUS_BUILD", None)
        .define("FIXED_POINT", None)
        .define("USE_ALLOCA", None)
        .define("VAR_ARRAYS", None)
        .define("ENABLE_HARDENING", None)
        .include(&opus_root)
        .include(&opus_include)
        .include(&opus_src)
        .include(&opus_celt)
        .include(&opus_silk)
        .include(&opus_silk_fixed)
        .include(&opus_silk_float);

    let opus_files = [
        "opus.c",
        "opus_decoder.c",
        "opus_encoder.c",
        "opus_multistream.c",
        "opus_multistream_encoder.c",
        "opus_multistream_decoder.c",
        "mlp.c",
        "mlp_data.c",
        "analysis.c",
        "mapping_matrix.c",
    ];
    for f in &opus_files {
        build.file(opus_src.join(f));
    }

    let celt_files = [
        "bands.c",
        "celt.c",
        "celt_encoder.c",
        "celt_decoder.c",
        "cwrs.c",
        "entcode.c",
        "entdec.c",
        "entenc.c",
        "kiss_fft.c",
        "laplace.c",
        "mathops.c",
        "mdct.c",
        "modes.c",
        "pitch.c",
        "celt_lpc.c",
        "quant_bands.c",
        "rate.c",
        "vq.c",
    ];
    for f in &celt_files {
        build.file(opus_celt.join(f));
    }

    let silk_files = [
        "CNG.c",
        "code_signs.c",
        "init_decoder.c",
        "decode_core.c",
        "decode_frame.c",
        "decode_parameters.c",
        "decode_indices.c",
        "decode_pulses.c",
        "decoder_set_fs.c",
        "dec_API.c",
        "enc_API.c",
        "encode_indices.c",
        "encode_pulses.c",
        "gain_quant.c",
        "interpolate.c",
        "LP_variable_cutoff.c",
        "NLSF_decode.c",
        "NSQ.c",
        "NSQ_del_dec.c",
        "PLC.c",
        "shell_coder.c",
        "tables_gain.c",
        "tables_LTP.c",
        "tables_NLSF_CB_NB_MB.c",
        "tables_NLSF_CB_WB.c",
        "tables_other.c",
        "tables_pitch_lag.c",
        "tables_pulses_per_block.c",
        "VAD.c",
        "control_audio_bandwidth.c",
        "quant_LTP_gains.c",
        "VQ_WMat_EC.c",
        "HP_variable_cutoff.c",
        "NLSF_encode.c",
        "NLSF_VQ.c",
        "NLSF_unpack.c",
        "NLSF_del_dec_quant.c",
        "process_NLSFs.c",
        "stereo_LR_to_MS.c",
        "stereo_MS_to_LR.c",
        "check_control_input.c",
        "control_SNR.c",
        "init_encoder.c",
        "control_codec.c",
        "A2NLSF.c",
        "ana_filt_bank_1.c",
        "biquad_alt.c",
        "bwexpander_32.c",
        "bwexpander.c",
        "debug.c",
        "decode_pitch.c",
        "inner_prod_aligned.c",
        "lin2log.c",
        "log2lin.c",
        "LPC_analysis_filter.c",
        "LPC_inv_pred_gain.c",
        "table_LSF_cos.c",
        "NLSF2A.c",
        "NLSF_stabilize.c",
        "NLSF_VQ_weights_laroia.c",
        "pitch_est_tables.c",
        "resampler.c",
        "resampler_down2_3.c",
        "resampler_down2.c",
        "resampler_private_AR2.c",
        "resampler_private_down_FIR.c",
        "resampler_private_IIR_FIR.c",
        "resampler_private_up2_HQ.c",
        "resampler_rom.c",
        "sigm_Q15.c",
        "sort.c",
        "sum_sqr_shift.c",
        "stereo_decode_pred.c",
        "stereo_encode_pred.c",
        "stereo_find_predictor.c",
        "stereo_quant_pred.c",
        "LPC_fit.c",
    ];
    for f in &silk_files {
        build.file(opus_silk.join(f));
    }

    let silk_fixed_files = [
        "LTP_analysis_filter_FIX.c",
        "LTP_scale_ctrl_FIX.c",
        "corrMatrix_FIX.c",
        "encode_frame_FIX.c",
        "find_LPC_FIX.c",
        "find_LTP_FIX.c",
        "find_pitch_lags_FIX.c",
        "find_pred_coefs_FIX.c",
        "noise_shape_analysis_FIX.c",
        "process_gains_FIX.c",
        "regularize_correlations_FIX.c",
        "residual_energy16_FIX.c",
        "residual_energy_FIX.c",
        "warped_autocorrelation_FIX.c",
        "apply_sine_window_FIX.c",
        "autocorr_FIX.c",
        "burg_modified_FIX.c",
        "k2a_FIX.c",
        "k2a_Q16_FIX.c",
        "pitch_analysis_core_FIX.c",
        "vector_ops_FIX.c",
        "schur64_FIX.c",
        "schur_FIX.c",
    ];
    for f in &silk_fixed_files {
        build.file(opus_silk_fixed.join(f));
    }

    build.compile("opus");

    let bindings = bindgen::Builder::default()
        .header(opus_include.join("opus.h").to_str().unwrap())
        .clang_arg(format!("-I{}", opus_include.display()))
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .allowlist_function("opus_.*")
        .allowlist_type("Opus.*")
        .allowlist_var("OPUS_.*")
        .size_t_is_usize(true)
        .generate()
        .expect("Unable to generate opus bindings");

    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings");
}
