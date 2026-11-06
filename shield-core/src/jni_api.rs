// SPDX-License-Identifier: GPL-3.0-or-later
//! JNI bridge for keystone.app.shield.ShieldCore. Byte arrays in, byte arrays out.
#![allow(non_snake_case)]
// JNI exports need #[no_mangle]; the only other unsafe is the Rules handle in `unsafe_free`.
#![allow(unsafe_code)]
use crate::{decide, wrap_reply, Flow, Rules, Verdict};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;

fn rules<'a>(h: jlong) -> &'a Rules { unsafe_free::as_ref(h) }

mod unsafe_free {
    // The handle is a leaked Box<Rules>; only ShieldCore creates and frees it, on one thread at a time.
    #![allow(unsafe_code)]
    use crate::Rules;
    pub fn new(r: Rules) -> i64 { Box::into_raw(Box::new(r)) as i64 }
    pub fn as_ref<'a>(h: i64) -> &'a Rules { unsafe { &*(h as *const Rules) } }
    pub fn free(h: i64) { if h != 0 { unsafe { drop(Box::from_raw(h as *mut Rules)) } } }
}

#[no_mangle]
pub extern "system" fn Java_keystone_app_shield_ShieldCore_nLoadRules(mut env: JNIEnv, _c: JClass, text: JString) -> jlong {
    let s: String = match env.get_string(&text) { Ok(s) => s.into(), Err(_) => String::new() };
    unsafe_free::new(Rules::load(&s))
}

#[no_mangle]
pub extern "system" fn Java_keystone_app_shield_ShieldCore_nFreeRules(_e: JNIEnv, _c: JClass, h: jlong) { unsafe_free::free(h) }

#[no_mangle]
pub extern "system" fn Java_keystone_app_shield_ShieldCore_nRuleCount(_e: JNIEnv, _c: JClass, h: jlong) -> jint { rules(h).len() as jint }

/// Returns [kind(1)] + body. kind 0 = drop; 1 = reply packet; 2 = forward: [srcPort(2)][v6(1)][srcIp(4|16)][dstIp][dstPort(2)][nameLen(2)][name][query].
#[no_mangle]
pub extern "system" fn Java_keystone_app_shield_ShieldCore_nDecide<'l>(env: JNIEnv<'l>, _c: JClass, h: jlong, pkt: JByteArray, blocked: jboolean) -> JByteArray<'l> {
    let bytes = env.convert_byte_array(&pkt).unwrap_or_default();
    let mut out = Vec::new();
    match decide(&bytes, rules(h), blocked != 0).0 {
        Verdict::Drop => out.push(0),
        Verdict::Reply(p) => { out.push(1); out.extend(p) }
        Verdict::Forward { flow, name, query } => {
            out.push(2);
            out.extend(encode_flow(&flow));
            out.extend((name.len() as u16).to_be_bytes());
            out.extend(name.as_bytes());
            out.extend(query);
        }
    }
    env.byte_array_from_slice(&out).unwrap_or_default()
}

/// Wraps an upstream DNS answer for the flow returned by nDecide.
#[no_mangle]
pub extern "system" fn Java_keystone_app_shield_ShieldCore_nWrap<'l>(env: JNIEnv<'l>, _c: JClass, flow: JByteArray, answer: JByteArray) -> JByteArray<'l> {
    let f = env.convert_byte_array(&flow).unwrap_or_default();
    let a = env.convert_byte_array(&answer).unwrap_or_default();
    let out = decode_flow(&f).map(|fl| wrap_reply(&fl, &a)).unwrap_or_default();
    env.byte_array_from_slice(&out).unwrap_or_default()
}

pub fn encode_flow(f: &Flow) -> Vec<u8> {
    let mut v = Vec::new();
    v.extend(f.src_port.to_be_bytes());
    v.push(f.v6 as u8);
    v.extend(&f.src);
    v.extend(&f.dst);
    v.extend(f.dst_port.to_be_bytes());
    v
}
pub fn decode_flow(b: &[u8]) -> Option<Flow> {
    let v6 = *b.get(2)? == 1;
    let n = if v6 { 16 } else { 4 };
    let src = b.get(3..3 + n)?.to_vec();
    let dst = b.get(3 + n..3 + 2 * n)?.to_vec();
    let dp = b.get(3 + 2 * n..5 + 2 * n)?;
    Some(Flow { v6, src, dst, src_port: u16::from_be_bytes([b[0], b[1]]), dst_port: u16::from_be_bytes([dp[0], dp[1]]) })
}
