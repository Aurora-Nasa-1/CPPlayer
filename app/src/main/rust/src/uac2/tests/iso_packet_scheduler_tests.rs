use crate::uac2::iso_packet_scheduler::IsoPacketScheduler;

fn transfer_frames(packet_bytes: &[usize], bytes_per_frame: usize) -> Vec<usize> {
    packet_bytes
        .iter()
        .map(|packet| packet / bytes_per_frame)
        .collect()
}

#[test]
fn test_scheduler_44100_hz_microframes_alternate_five_and_six_frames() {
    let bytes_per_frame = 4usize;
    let mut scheduler = IsoPacketScheduler::new(44_100, bytes_per_frame, 125);
    let frames = transfer_frames(&scheduler.next_transfer_packet_bytes(), bytes_per_frame);

    assert_eq!(frames.len(), 32);
    assert_eq!(&frames[..8], &[5, 6, 5, 6, 5, 6, 5, 6]);
}

#[test]
fn test_scheduler_44100_hz_microframes_accumulate_exact_second() {
    let bytes_per_frame = 4usize;
    let mut scheduler = IsoPacketScheduler::new(44_100, bytes_per_frame, 125);
    let total_frames: usize = (0..250)
        .flat_map(|_| scheduler.next_transfer_packet_bytes())
        .map(|packet| packet / bytes_per_frame)
        .sum();

    assert_eq!(total_frames, 44_100);
}

#[test]
fn test_scheduler_48000_hz_microframes_accumulate_exact_second() {
    let bytes_per_frame = 4usize;
    let mut scheduler = IsoPacketScheduler::new(48_000, bytes_per_frame, 125);
    let total_frames: usize = (0..250)
        .flat_map(|_| scheduler.next_transfer_packet_bytes())
        .map(|packet| packet / bytes_per_frame)
        .sum();

    assert_eq!(total_frames, 48_000);
}

#[test]
fn test_scheduler_96000_hz_microframes_accumulate_exact_second() {
    let bytes_per_frame = 6usize;
    let mut scheduler = IsoPacketScheduler::new(96_000, bytes_per_frame, 125);
    let total_frames: usize = (0..250)
        .flat_map(|_| scheduler.next_transfer_packet_bytes())
        .map(|packet| packet / bytes_per_frame)
        .sum();

    assert_eq!(total_frames, 96_000);
}
