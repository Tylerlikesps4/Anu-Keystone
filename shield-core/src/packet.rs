// SPDX-License-Identifier: GPL-3.0-or-later
//! Minimal IPv4/IPv6 + UDP parsing and building. Bounds-checked everywhere; malformed input returns None.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Flow {
    pub v6: bool,
    pub src: Vec<u8>,
    pub dst: Vec<u8>,
    pub src_port: u16,
    pub dst_port: u16,
}

pub struct Parsed<'a> { pub flow: Flow, pub payload: &'a [u8] }

fn be16(b: &[u8], i: usize) -> Option<u16> { Some(u16::from_be_bytes([*b.get(i)?, *b.get(i + 1)?])) }

pub fn parse_udp(pkt: &[u8]) -> Option<Parsed<'_>> {
    let ver = pkt.first()? >> 4;
    let (v6, src, dst, l4) = match ver {
        4 => {
            let ihl = ((pkt[0] & 0x0f) as usize) * 4;
            if ihl < 20 || pkt.len() < ihl { return None }
            let total = be16(pkt, 2)? as usize;
            if total < ihl || total > pkt.len() { return None }
            if be16(pkt, 6)? & 0x3fff != 0 { return None } // fragments: not handled
            if pkt[9] != 17 { return None }
            (false, pkt[12..16].to_vec(), pkt[16..20].to_vec(), &pkt[ihl..total])
        }
        6 => {
            if pkt.len() < 40 || pkt[6] != 17 { return None } // no extension headers
            let plen = be16(pkt, 4)? as usize;
            if 40 + plen > pkt.len() { return None }
            (true, pkt[8..24].to_vec(), pkt[24..40].to_vec(), &pkt[40..40 + plen])
        }
        _ => return None,
    };
    if l4.len() < 8 { return None }
    let ulen = be16(l4, 4)? as usize;
    if ulen < 8 || ulen > l4.len() { return None }
    Some(Parsed {
        flow: Flow { v6, src, dst, src_port: be16(l4, 0)?, dst_port: be16(l4, 2)? },
        payload: &l4[8..ulen],
    })
}

fn sum(data: &[u8], mut acc: u32) -> u32 {
    let mut c = data.chunks_exact(2);
    for w in &mut c { acc += u16::from_be_bytes([w[0], w[1]]) as u32 }
    if let [b] = c.remainder() { acc += (*b as u32) << 8 }
    acc
}
fn fold(mut acc: u32) -> u16 { while acc >> 16 != 0 { acc = (acc & 0xffff) + (acc >> 16) } !(acc as u16) }

/// Builds the reply: source and destination swapped from the query's flow.
pub fn build_udp_reply(q: &Flow, payload: &[u8]) -> Vec<u8> {
    let ulen = (8 + payload.len()) as u16;
    let mut udp = Vec::with_capacity(ulen as usize);
    udp.extend_from_slice(&q.dst_port.to_be_bytes());
    udp.extend_from_slice(&q.src_port.to_be_bytes());
    udp.extend_from_slice(&ulen.to_be_bytes());
    udp.extend_from_slice(&[0, 0]);
    udp.extend_from_slice(payload);
    // pseudo-header: reply src = query dst
    let mut acc = sum(&q.dst, 0);
    acc = sum(&q.src, acc);
    acc += 17;
    acc += ulen as u32;
    let mut ck = fold(sum(&udp, acc));
    if ck == 0 { ck = 0xffff }
    udp[6..8].copy_from_slice(&ck.to_be_bytes());

    let mut out;
    if q.v6 {
        out = Vec::with_capacity(40 + udp.len());
        out.extend_from_slice(&[0x60, 0, 0, 0]);
        out.extend_from_slice(&ulen.to_be_bytes());
        out.extend_from_slice(&[17, 64]);
        out.extend_from_slice(&q.dst);
        out.extend_from_slice(&q.src);
    } else {
        out = Vec::with_capacity(20 + udp.len());
        let total = (20 + udp.len()) as u16;
        out.extend_from_slice(&[0x45, 0]);
        out.extend_from_slice(&total.to_be_bytes());
        out.extend_from_slice(&[0, 0, 0x40, 0, 64, 17, 0, 0]);
        out.extend_from_slice(&q.dst);
        out.extend_from_slice(&q.src);
        let ck = fold(sum(&out, 0));
        out[10..12].copy_from_slice(&ck.to_be_bytes());
    }
    out.extend_from_slice(&udp);
    out
}
