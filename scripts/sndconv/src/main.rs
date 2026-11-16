//! WAV -> Ogg Vorbis converter used once to produce the mod's original sounds.
//!
//! Not shipped in the mod: this is a build-time helper. It lives in the repo so
//! the sounds are reproducible from source rather than mysterious binaries.
//!
//! Usage: sndconv <in.wav> <out.ogg>

use std::fs;
use std::io::Write;

use zvidlib_vorbis_encoder::vorbis_encoder::VorbisEncoder;

const VENDOR: &str = "starflight";

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 3 {
        eprintln!("usage: {} <in.wav> <out.ogg>", args[0]);
        std::process::exit(2);
    }
    let (rate, channels, samples) = read_wav(&args[1])?;
    let packets = encode(rate, channels, &samples)?;
    write_ogg(&args[2], rate, channels, &packets)?;
    println!(
        "{} -> {} ({} Hz, {} ch, {} frames, {} bytes)",
        args[1],
        args[2],
        rate,
        channels,
        samples.len(),
        fs::metadata(&args[2])?.len()
    );
    Ok(())
}

/// Minimal 16-bit PCM WAV reader (the only format our generator writes).
fn read_wav(path: &str) -> Result<(u32, u16, Vec<f32>), Box<dyn std::error::Error>> {
    let b = fs::read(path)?;
    if &b[0..4] != b"RIFF" || &b[8..12] != b"WAVE" {
        return Err("not a RIFF/WAVE file".into());
    }
    let mut pos = 12;
    let (mut rate, mut channels, mut bits) = (0u32, 0u16, 0u16);
    let mut data: Option<&[u8]> = None;
    while pos + 8 <= b.len() {
        let id = &b[pos..pos + 4];
        let size = u32::from_le_bytes([b[pos + 4], b[pos + 5], b[pos + 6], b[pos + 7]]) as usize;
        let body = pos + 8;
        if id == b"fmt " {
            channels = u16::from_le_bytes([b[body + 2], b[body + 3]]);
            rate = u32::from_le_bytes([b[body + 4], b[body + 5], b[body + 6], b[body + 7]]);
            bits = u16::from_le_bytes([b[body + 14], b[body + 15]]);
        } else if id == b"data" {
            data = Some(&b[body..(body + size).min(b.len())]);
        }
        pos = body + size + (size & 1);
    }
    if bits != 16 {
        return Err(format!("only 16-bit PCM is supported, got {bits}-bit").into());
    }
    let raw = data.ok_or("WAV has no data chunk")?;
    let samples = raw
        .chunks_exact(2)
        .map(|c| f32::from(i16::from_le_bytes([c[0], c[1]])) / 32768.0)
        .collect();
    Ok((rate, channels, samples))
}

fn encode(
    rate: u32,
    channels: u16,
    samples: &[f32],
) -> Result<Vec<(Vec<u8>, u64)>, Box<dyn std::error::Error>> {
    // Quality 0.3: small files, and these are short effects where artefacts do
    // not matter.
    let mut enc = VorbisEncoder::new(rate, channels, 0.3)?;
    let headers = enc.headers(VENDOR);
    let mut packets: Vec<(Vec<u8>, u64)> = headers
        .into_iter()
        .map(|h| (h, 0u64))
        .collect();
    packets.extend(enc.encode(samples)?);
    packets.extend(enc.finish()?);
    if packets.len() < 4 {
        return Err("encoder produced no audio packets".into());
    }
    Ok(packets)
}

/// Ogg page writer, as Xiph's framing requires: first page holds the three
/// header packets, each later page carries audio packets up to 255 lacing
/// values.
fn write_ogg(
    path: &str,
    rate: u32,
    _channels: u16,
    packets: &[(Vec<u8>, u64)],
) -> Result<(), Box<dyn std::error::Error>> {
    let mut out = Vec::new();
    let mut serial: u32 = 0x5374_6172; // "Star"
    let mut seq: u32 = 0;

    // Header pages: identification alone, then comment + setup + first audio.
    push_page(&mut out, serial, seq, 0x02, 0, &[packets[0].0.as_slice()]);
    seq += 1;
    let mut first_audio: Vec<&[u8]> = vec![&packets[1].0, &packets[2].0];
    let mut granule = 0u64;
    let mut body_start = 3;
    if packets.len() > 3 {
        first_audio.push(&packets[3].0);
        granule = packets[3].1;
        body_start = 4;
    }
    push_page(&mut out, serial, seq, 0x00, granule, &first_audio);
    seq += 1;

    // Audio pages, batched to stay under Ogg's 255-segment limit.
    let mut i = body_start;
    while i < packets.len() {
        let mut batch: Vec<&[u8]> = Vec::new();
        while i < packets.len() && batch.len() < 200 {
            batch.push(&packets[i].0);
            granule = packets[i].1;
            i += 1;
        }
        let last = i >= packets.len();
        let flags = 0x04u8; // end of stream
        push_page(&mut out, serial, seq, if last { flags } else { 0 }, granule, &batch);
        seq += 1;
    }

    let mut f = fs::File::create(path)?;
    f.write_all(&out)?;
    let _ = rate;
    serial = serial.wrapping_add(1);
    let _ = serial;
    Ok(())
}

fn push_page(
    out: &mut Vec<u8>,
    serial: u32,
    seq: u32,
    flags: u8,
    granule: u64,
    packets: &[&[u8]],
) {
    let mut lacing: Vec<u8> = Vec::new();
    for p in packets {
        let mut len = p.len();
        loop {
            if len >= 255 {
                lacing.push(255);
                len -= 255;
            } else {
                lacing.push(len as u8);
                break;
            }
        }
    }
    let body: Vec<u8> = packets.iter().flat_map(|p| p.iter().copied()).collect();

    out.extend_from_slice(b"OggS");
    out.push(0); // version
    out.push(flags);
    out.extend_from_slice(&granule.to_le_bytes());
    out.extend_from_slice(&serial.to_le_bytes());
    out.extend_from_slice(&seq.to_le_bytes());
    out.extend_from_slice(&[0, 0, 0, 0]); // CRC placeholder
    out.push(lacing.len() as u8);
    out.extend_from_slice(&lacing);
    out.extend_from_slice(&body);

    // CRC-32 (no reflection, poly 0x04C11DB7) over the whole page with the CRC
    // field zeroed, then patched in.
    let page_start = out.len() - (27 + lacing.len() + body.len());
    let crc = ogg_crc(&out[page_start..]);
    out[page_start + 22..page_start + 26].copy_from_slice(&crc.to_le_bytes());
}

fn ogg_crc(data: &[u8]) -> u32 {
    let mut crc: u32 = 0;
    for &byte in data {
        crc ^= u32::from(byte) << 24;
        for _ in 0..8 {
            crc = if crc & 0x8000_0000 != 0 {
                (crc << 1) ^ 0x04C1_1DB7
            } else {
                crc << 1
            };
        }
    }
    crc
}
