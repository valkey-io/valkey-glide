//! Optional runtime statistics for JNI layer (disabled by default).
//! Gated by env var: GLIDE_JNI_ENABLE_STATS=1 or JNI setter.

// Removed: use crate::large_data_handler::LargeDataHandler; - disabled for memory control removal
use crate::{get_inflight_requests_limit, stats_handle_table_len};
use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jboolean, jstring};
use std::sync::atomic::{AtomicBool, Ordering as BoolOrdering};
use std::sync::atomic::{AtomicU64, Ordering};

// ================== CLIENT LIFECYCLE COUNTERS ==================
// These track high-level client lifecycle state for diagnostics. They are
// intentionally lightweight (lock-free atomics) and only exposed when stats
// are enabled to avoid overhead in hot paths when disabled.

static ACTIVE_CLIENTS: AtomicU64 = AtomicU64::new(0);
static LAZY_PENDING_CLIENTS: AtomicU64 = AtomicU64::new(0);
static TOTAL_CREATED_CLIENTS: AtomicU64 = AtomicU64::new(0);
static TOTAL_CLOSED_CLIENTS: AtomicU64 = AtomicU64::new(0);

/// Increment when a client (lazy or eager) is registered; if lazy, also bump lazy pending.
pub fn record_client_created(lazy: bool) {
    ACTIVE_CLIENTS.fetch_add(1, Ordering::Relaxed);
    TOTAL_CREATED_CLIENTS.fetch_add(1, Ordering::Relaxed);
    if lazy {
        LAZY_PENDING_CLIENTS.fetch_add(1, Ordering::Relaxed);
    }
}

/// Called when a lazy client realizes (actual sockets opened) so we decrement pending.
pub fn record_lazy_realized() {
    let _ = LAZY_PENDING_CLIENTS.fetch_update(Ordering::Relaxed, Ordering::Relaxed, |v| {
        if v > 0 { Some(v - 1) } else { Some(0) }
    });
}

/// Called on explicit or cleaner-driven close.
pub fn record_client_closed() {
    let _ = ACTIVE_CLIENTS.fetch_update(Ordering::Relaxed, Ordering::Relaxed, |v| {
        if v > 0 { Some(v - 1) } else { Some(0) }
    });
    TOTAL_CLOSED_CLIENTS.fetch_add(1, Ordering::Relaxed);
}

fn client_counters_json() -> serde_json::Value {
    serde_json::json!({
        "active": ACTIVE_CLIENTS.load(Ordering::Relaxed),
        "lazyPending": LAZY_PENDING_CLIENTS.load(Ordering::Relaxed),
        "createdTotal": TOTAL_CREATED_CLIENTS.load(Ordering::Relaxed),
        "closedTotal": TOTAL_CLOSED_CLIENTS.load(Ordering::Relaxed),
    })
}

static ENABLED: AtomicBool = AtomicBool::new(false);

pub fn is_enabled() -> bool {
    if ENABLED.load(BoolOrdering::Relaxed) {
        return true;
    }
    std::env::var("GLIDE_JNI_ENABLE_STATS").ok().as_deref() == Some("1")
}

pub fn set_enabled(val: bool) {
    ENABLED.store(val, BoolOrdering::Relaxed);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_setNativeStatsEnabled(
    _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
) {
    set_enabled(enabled != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getRuntimeStats(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    if !is_enabled() {
        let disabled = "{\"status\":\"disabled\"}";
        return env
            .new_string(disabled)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut());
    }

    let stats = serde_json::json!({
        "clients": client_counters_json(),
        "handleTable": stats_handle_table_len(),
        "inflightLimit": get_inflight_requests_limit(),
    })
    .to_string();

    env.new_string(&stats)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
