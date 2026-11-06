use shield::{decide, dns, packet, wrap_reply, Rules, Verdict};

fn query(name: &str, qtype: u16) -> Vec<u8> {
    let mut m = vec![0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0];
    for l in name.split('.') { m.push(l.len() as u8); m.extend(l.as_bytes()) }
    m.push(0); m.extend(qtype.to_be_bytes()); m.extend([0, 1]); m
}
fn v4_udp(dport: u16, payload: &[u8]) -> Vec<u8> {
    let f = packet::Flow { v6: false, src: vec![10, 111, 0, 2], dst: vec![10, 111, 0, 1], src_port: dport, dst_port: 40000 };
    // build_udp_reply swaps, so build a "reply" from the server side to get a packet from app -> dns
    packet::build_udp_reply(&f, payload)
}

#[test] fn blocks_listed_domain_and_subdomains() {
    let r = Rules::load("0.0.0.0 doubleclick.net\n# c\ntracker.example.org\n@@ok.doubleclick.net");
    assert!(r.is_blocked("doubleclick.net"));
    assert!(r.is_blocked("ad.g.doubleclick.net"));
    assert!(!r.is_blocked("ok.doubleclick.net"));
    assert!(!r.is_blocked("example.org"));
    assert_eq!(r.len(), 2);
}

#[test] fn packet_roundtrip_and_checksums() {
    let q = query("ads.doubleclick.net", 1);
    let pkt = v4_udp(53, &q);
    let p = packet::parse_udp(&pkt).expect("parse");
    assert_eq!(p.flow.dst_port, 53);
    assert_eq!(p.payload, &q[..]);
    // IPv4 header checksum verifies to zero
    let mut acc: u32 = 0;
    for w in pkt[..20].chunks(2) { acc += u16::from_be_bytes([w[0], w[1]]) as u32 }
    while acc >> 16 != 0 { acc = (acc & 0xffff) + (acc >> 16) }
    assert_eq!(acc, 0xffff);
}

#[test] fn blocked_name_gets_zero_address() {
    let r = Rules::load("doubleclick.net");
    let pkt = v4_udp(53, &query("ads.doubleclick.net", 1));
    let (v, name) = decide(&pkt, &r, false);
    assert_eq!(name.as_deref(), Some("ads.doubleclick.net"));
    let Verdict::Reply(out) = v else { panic!("expected reply") };
    let p = packet::parse_udp(&out).unwrap();
    assert_eq!(p.flow.src_port, 53);
    assert_eq!(&p.payload[0..2], &[0x12, 0x34]);
    assert_eq!(p.payload[3] & 0x0f, 0); // NOERROR
    assert_eq!(&p.payload[p.payload.len() - 4..], &[0, 0, 0, 0]);
}

#[test] fn blocked_app_and_other_types_get_nxdomain_or_zero() {
    let r = Rules::default();
    let (v, _) = decide(&v4_udp(53, &query("github.com", 16)), &r, true);
    let Verdict::Reply(out) = v else { panic!() };
    assert_eq!(packet::parse_udp(&out).unwrap().payload[3] & 0x0f, 3);
}

#[test] fn allowed_name_is_forwarded_and_wrapped() {
    let r = Rules::load("doubleclick.net");
    let q = query("github.com", 1);
    let (v, _) = decide(&v4_udp(53, &q), &r, false);
    let Verdict::Forward { flow, name, query } = v else { panic!() };
    assert_eq!(name, "github.com"); assert_eq!(query, q);
    let back = wrap_reply(&flow, b"answer");
    let p = packet::parse_udp(&back).unwrap();
    assert_eq!(p.flow.dst, vec![10, 111, 0, 1]); // back to the app that asked
    assert_eq!(p.payload, b"answer");
}

#[test] fn ipv6_works() {
    let f = packet::Flow { v6: true, src: [0u8; 16].to_vec(), dst: { let mut a = [0u8; 16]; a[15] = 2; a.to_vec() }, src_port: 53, dst_port: 5555 };
    let pkt = packet::build_udp_reply(&f, &query("x.doubleclick.net", 28));
    let (v, _) = decide(&pkt, &Rules::load("doubleclick.net"), false);
    let Verdict::Reply(out) = v else { panic!() };
    let p = packet::parse_udp(&out).unwrap();
    assert!(p.flow.v6);
    assert_eq!(&p.payload[p.payload.len() - 16..], &[0u8; 16]);
}

#[test] fn garbage_never_panics() {
    let r = Rules::load("a.com");
    let mut seed: u32 = 7;
    for len in 0..400 {
        let mut b = vec![0u8; len];
        for x in b.iter_mut() { seed = seed.wrapping_mul(1103515245).wrapping_add(12345); *x = (seed >> 16) as u8 }
        if !b.is_empty() { b[0] = if len % 2 == 0 { 0x45 } else { 0x60 } }
        let _ = decide(&b, &r, false);
        let _ = dns::parse_query(&b);
    }
    // non-DNS and responses are dropped
    assert_eq!(decide(&v4_udp(443, &query("a.com", 1)), &r, false).0, Verdict::Drop);
    let mut resp = query("a.com", 1); resp[2] |= 0x80;
    assert_eq!(decide(&v4_udp(53, &resp), &r, false).0, Verdict::Drop);
}
