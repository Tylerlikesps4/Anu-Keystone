// SPDX-License-Identifier: GPL-3.0-or-later
//! DNS question parsing and "blocked" answers (A -> 0.0.0.0, AAAA -> ::, others -> NXDOMAIN).

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Query { pub name: String, pub qtype: u16, pub question_end: usize }

pub fn parse_query(m: &[u8]) -> Option<Query> {
    if m.len() < 12 { return None }
    if m[2] & 0x80 != 0 { return None } // a response, not a query
    if u16::from_be_bytes([m[4], m[5]]) != 1 { return None }
    let mut i = 12;
    let mut name = String::new();
    loop {
        let len = *m.get(i)? as usize;
        if len == 0 { i += 1; break }
        if len & 0xc0 != 0 || len > 63 { return None } // no compression in questions
        let label = m.get(i + 1..i + 1 + len)?;
        if !label.iter().all(|c| c.is_ascii_alphanumeric() || *c == b'-' || *c == b'_') { return None }
        if !name.is_empty() { name.push('.') }
        name.push_str(&String::from_utf8_lossy(label).to_ascii_lowercase());
        if name.len() > 253 { return None }
        i += 1 + len;
    }
    let qtype = u16::from_be_bytes([*m.get(i)?, *m.get(i + 1)?]);
    m.get(i + 3)?;
    Some(Query { name, qtype, question_end: i + 4 })
}

pub fn blocked_answer(m: &[u8], q: &Query) -> Vec<u8> {
    let mut r = Vec::with_capacity(q.question_end + 28);
    r.extend_from_slice(&m[0..2]); // id
    let rd = m[2] & 0x01;
    let (rdata, rcode): (Option<Vec<u8>>, u8) = match q.qtype {
        1 => (Some(vec![0; 4]), 0),
        28 => (Some(vec![0; 16]), 0),
        _ => (None, 3),
    };
    r.push(0x80 | rd); // QR, RD copied
    r.push(0x80 | rcode); // RA
    r.extend_from_slice(&[0, 1, 0, rdata.is_some() as u8, 0, 0, 0, 0]);
    r.extend_from_slice(&m[12..q.question_end]);
    if let Some(d) = rdata {
        r.extend_from_slice(&[0xc0, 12]);
        r.extend_from_slice(&q.qtype.to_be_bytes());
        r.extend_from_slice(&[0, 1, 0, 0, 0, 60]); // IN, TTL 60s
        r.extend_from_slice(&(d.len() as u16).to_be_bytes());
        r.extend_from_slice(&d);
    }
    r
}
