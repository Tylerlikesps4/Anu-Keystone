// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
//! Keystone Shield core. Pure functions over raw IP packets, so it can be tested on any machine.
//! The Android side owns all sockets; this crate only parses, decides and builds replies.
#![deny(unsafe_code)]

pub mod dns;
pub mod packet;
pub mod rules;
#[cfg(target_os = "android")]
mod jni_api;

pub use packet::{Flow, Parsed};
pub use rules::Rules;

/// What the VPN service should do with one packet read from the tunnel.
#[derive(Debug, PartialEq, Eq)]
pub enum Verdict {
    /// Not something we handle: drop it.
    Drop,
    /// Write these bytes back into the tunnel (a ready-made "blocked" DNS answer).
    Reply(Vec<u8>),
    /// A DNS query for an allowed name: forward `query` upstream, then wrap the answer with [`wrap_reply`].
    Forward { flow: Flow, name: String, query: Vec<u8> },
}

/// Decide what to do with a packet. `app_blocked` is the app-level decision the caller made from the flow's UID.
pub fn decide(pkt: &[u8], rules: &Rules, app_blocked: bool) -> (Verdict, Option<String>) {
    let Some(p) = packet::parse_udp(pkt) else { return (Verdict::Drop, None) };
    if p.flow.dst_port != 53 { return (Verdict::Drop, None) }
    let Some(q) = dns::parse_query(p.payload) else { return (Verdict::Drop, None) };
    let name = q.name.clone();
    if app_blocked || rules.is_blocked(&q.name) {
        let ans = dns::blocked_answer(p.payload, &q);
        return (Verdict::Reply(packet::build_udp_reply(&p.flow, &ans)), Some(name));
    }
    (Verdict::Forward { flow: p.flow, name: name.clone(), query: p.payload.to_vec() }, Some(name))
}

/// Wraps an upstream DNS answer in IP/UDP addressed back to the app that asked.
pub fn wrap_reply(flow: &Flow, answer: &[u8]) -> Vec<u8> { packet::build_udp_reply(flow, answer) }
