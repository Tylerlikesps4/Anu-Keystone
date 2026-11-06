// SPDX-License-Identifier: GPL-3.0-or-later
//! Domain blocklist with suffix matching: blocking "tracker.com" also blocks "a.b.tracker.com".
use std::collections::HashSet;

#[derive(Default)]
pub struct Rules { blocked: HashSet<String>, allowed: HashSet<String> }

impl Rules {
    /// Hosts-file or plain list. Lines like "0.0.0.0 ads.example.com", "ads.example.com", "@@allowed.com". `#` comments.
    pub fn load(text: &str) -> Rules {
        let mut r = Rules::default();
        for line in text.lines() {
            let line = line.split('#').next().unwrap_or("").trim();
            if line.is_empty() { continue }
            let host = line.split_whitespace().last().unwrap_or("");
            let (allow, host) = match host.strip_prefix("@@") { Some(h) => (true, h), None => (false, host) };
            let host = host.trim_start_matches("*.").trim_end_matches('.').to_ascii_lowercase();
            if host.is_empty() || host == "localhost" || !host.contains('.') || host.len() > 253 { continue }
            if allow { r.allowed.insert(host); } else { r.blocked.insert(host); }
        }
        r
    }
    pub fn len(&self) -> usize { self.blocked.len() }
    pub fn is_empty(&self) -> bool { self.blocked.is_empty() }

    fn hit(set: &HashSet<String>, name: &str) -> bool {
        let mut n = name;
        loop {
            if set.contains(n) { return true }
            match n.find('.') { Some(i) => n = &n[i + 1..], None => return false }
        }
    }
    pub fn is_blocked(&self, name: &str) -> bool {
        let name = name.trim_end_matches('.');
        !Self::hit(&self.allowed, name) && Self::hit(&self.blocked, name)
    }
}
