//! Writes a 16x16 PNG of the orbital rocket item icon.
//!
//! Pure Rust with no dependencies: the icon is procedurally drawn and encoded
//! with zlib's stored (uncompressed) blocks, so no download or external asset is
//! involved and the licence stays clean.

use std::io::Write;

const W: usize = 16;
const H: usize = 16;

fn crc32(bytes: &[u8]) -> u32 {
    let mut table = [0u32; 256];
    for (i, entry) in table.iter_mut().enumerate() {
        let mut c = i as u32;
        for _ in 0..8 {
            c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
        }
        *entry = c;
    }
    let mut crc = 0xFFFF_FFFFu32;
    for b in bytes {
        crc = table[((crc ^ u32::from(*b)) & 0xFF) as usize] ^ (crc >> 8);
    }
    crc ^ 0xFFFF_FFFF
}

fn chunk(out: &mut Vec<u8>, kind: &[u8; 4], data: &[u8]) {
    out.extend_from_slice(&(data.len() as u32).to_be_bytes());
    let mut payload = kind.to_vec();
    payload.extend_from_slice(data);
    out.extend_from_slice(&payload);
    out.extend_from_slice(&crc32(&payload).to_be_bytes());
}

fn main() {
    // RGBA pixels. The rocket is drawn pointing up, with a steel body, a
    // red nose cone, fins, and an exhaust flame at the base.
    let mut px = [[0u8; 4]; W * H];
    let put = |px: &mut [[u8; 4]; W * H], x: i32, y: i32, c: [u8; 4]| {
        if (0..W as i32).contains(&x) && (0..H as i32).contains(&y) {
            px[(y as usize) * W + x as usize] = c;
        }
    };

    let body = [214, 219, 226, 255];
    let shade = [154, 161, 171, 255];
    let nose = [226, 74, 58, 255];
    let fin = [72, 84, 102, 255];
    let flame = [255, 196, 64, 255];
    let hot = [255, 240, 180, 255];

    // Body: a vertical column, lit on the left, shaded on the right.
    for y in 5..13 {
        put(&mut px, 7, y, body);
        put(&mut px, 8, y, shade);
    }
    // Nose cone.
    for (i, y) in (1..5).enumerate() {
        let half = 1 + i as i32;
        for x in (8 - half)..(8 + half) {
            put(&mut px, x, y, nose);
        }
    }
    // Fins.
    for y in 10..13 {
        put(&mut px, 5, y, fin);
        put(&mut px, 6, y, fin);
        put(&mut px, 9, y, fin);
        put(&mut px, 10, y, fin);
    }
    // Exhaust.
    put(&mut px, 7, 13, flame);
    put(&mut px, 8, 13, flame);
    put(&mut px, 7, 14, hot);
    put(&mut px, 8, 14, hot);
    put(&mut px, 7, 15, flame);
    put(&mut px, 8, 15, flame);

    // Raw scanlines, filter byte 0 per row.
    let mut raw = Vec::with_capacity(H * (1 + W * 4));
    for y in 0..H {
        raw.push(0);
        for x in 0..W {
            raw.extend_from_slice(&px[y * W + x]);
        }
    }

    // zlib stream with stored blocks.
    let mut z = vec![0x78, 0x01];
    let mut offset = 0;
    while offset < raw.len() {
        let take = (raw.len() - offset).min(65535);
        let last = if offset + take == raw.len() { 1 } else { 0 };
        z.push(last);
        z.extend_from_slice(&(take as u16).to_le_bytes());
        z.extend_from_slice(&(!(take as u16)).to_le_bytes());
        z.extend_from_slice(&raw[offset..offset + take]);
        offset += take;
    }
    let (mut a, mut b) = (1u32, 0u32);
    for byte in &raw {
        a = (a + u32::from(*byte)) % 65521;
        b = (b + a) % 65521;
    }
    z.extend_from_slice(&((b << 16) | a).to_be_bytes());

    let mut out = Vec::new();
    out.extend_from_slice(&[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A]);
    let mut ihdr = Vec::new();
    ihdr.extend_from_slice(&(W as u32).to_be_bytes());
    ihdr.extend_from_slice(&(H as u32).to_be_bytes());
    ihdr.extend_from_slice(&[8, 6, 0, 0, 0]);
    chunk(&mut out, b"IHDR", &ihdr);
    chunk(&mut out, b"IDAT", &z);
    chunk(&mut out, b"IEND", &[]);

    let path = std::env::args().nth(1).expect("usage: mkpng <out.png>");
    let mut file = std::fs::File::create(&path).expect("create png");
    file.write_all(&out).expect("write png");
    println!("wrote {path} ({} bytes)", out.len());
}
