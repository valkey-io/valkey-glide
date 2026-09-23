// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Resolves libfabric at run time instead of at process load.
//!
//! # Why this module exists
//!
//! The libfabric bindings normally ask the linker to record a hard dependency on
//! `libfabric`. The operating system resolves that dependency while it is opening
//! the executable, before any of this program's code runs, so a machine without
//! libfabric installed cannot start the process at all — even if the program was
//! never going to use a fabric. That makes RDMA impossible to ship switched off
//! because the cost lands on every user rather than the ones who opted in.
//!
//! This module removes that dependency. It defines the small number of libfabric
//! entry points that are real linker symbols, and each one looks the real
//! function up the first time it is called. The program now starts anywhere.
//! A missing libfabric becomes an ordinary error at the point someone asks for a
//! fabric, which callers can catch and fall back from.
//!
//! # Why the list is short
//!
//! Almost all of libfabric's API is declared `static inline` in its headers, and
//! those calls reach the provider through function-pointer tables hanging off the
//! objects that `fi_getinfo` and `fi_fabric` hand back. They are compiled
//! directly into this build and are not linker symbols at all. Only the handful
//! below have to be found by name — the ones needed to bootstrap, before any
//! object exists to carry a table.
//!
//! # The trade this makes
//!
//! Resolving by name at run time means the libfabric that gets loaded is not
//! necessarily the one these bindings were generated against. Everything past
//! the bootstrap reads struct fields at fixed offsets, and nothing checks that
//! those offsets still agree. [`ensure_loaded`] therefore refuses any libfabric
//! older than the headers this crate was built from.

use crate::error::RdmaError;
use std::ffi::{CStr, c_char, c_int, c_void};
use std::sync::OnceLock;

/// Where to look for libfabric, in order.
///
/// The EFA installer puts its copy outside the default search path, so name it
/// explicitly rather than requiring every deployment to set `LD_LIBRARY_PATH`.
/// The bare name is last so that a normal system install still wins for anyone
/// who has one.
#[cfg(target_os = "linux")]
const SEARCH_PATHS: &[&str] = &[
    // EFA ships its own libfabric, built against the same driver, and installs it
    // outside the loader's usual reach. It goes first because on a machine that
    // has one, it is the copy that can actually talk to the hardware.
    "/opt/amazon/efa/lib64/libfabric.so.1",
    "/opt/amazon/efa/lib/libfabric.so.1",
    // Then whatever the loader would find on its own, which is how an operator
    // points at a particular copy without editing anything here.
    "libfabric.so.1",
    // Then the usual home for a locally built one. Worth naming explicitly:
    // common distributions still package libfabric 1.x, so anyone who needs the
    // 2.x this crate is built against has probably built it themselves, and a
    // fresh `make install` is not visible by name until `ldconfig` has run.
    "/usr/local/lib64/libfabric.so.1",
    "/usr/local/lib/libfabric.so.1",
    // Last, the unversioned name, which usually only exists where development
    // headers are installed.
    "libfabric.so",
];

#[cfg(target_os = "macos")]
const SEARCH_PATHS: &[&str] = &[
    "libfabric.1.dylib",
    "libfabric.dylib",
    "/opt/homebrew/opt/libfabric/lib/libfabric.1.dylib",
    "/usr/local/opt/libfabric/lib/libfabric.1.dylib",
];

/// Overrides [`SEARCH_PATHS`] entirely when set, for operators who keep
/// libfabric somewhere unusual.
const PATH_OVERRIDE_VAR: &str = "GLIDE_LIBFABRIC_PATH";

/// The libfabric API version these bindings were generated against.
///
/// libfabric encodes a version as major in the high 16 bits, minor in the low 16.
/// A library reporting less than this may lay its structs out differently from
/// what this build expects, so it is refused.
const HEADER_API_VERSION: u32 = (ofi_libfabric_sys::bindgen::FI_MAJOR_VERSION << 16)
    | ofi_libfabric_sys::bindgen::FI_MINOR_VERSION;

/// The libfabric entry points that are real linker symbols.
///
/// Every other `fi_*` call this crate makes is `static inline` in libfabric's
/// headers and is compiled into this build directly.
/// Named positions into [`SYMBOLS`] and the resolved table beside it.
///
/// Spelled out rather than written as bare numbers at each call site, because
/// the two arrays are matched up by position and nothing else. Getting a number
/// wrong would bind a call to a different libfabric function of a different
/// shape, which no compiler or linker can catch. `names_and_positions_agree`
/// checks every one of these against the name it is supposed to point at.
mod slot {
    pub const GETINFO: usize = 0;
    pub const FREEINFO: usize = 1;
    pub const DUPINFO: usize = 2;
    pub const FABRIC: usize = 3;
    pub const STRERROR: usize = 4;
    pub const VERSION: usize = 5;
    pub const OPEN: usize = 6;
    pub const PARAM_GET: usize = 7;
}

const SYMBOLS: [&CStr; 8] = [
    c"fi_getinfo",
    c"fi_freeinfo",
    c"fi_dupinfo",
    c"fi_fabric",
    c"fi_strerror",
    c"fi_version",
    c"fi_open",
    c"fi_param_get",
];

/// The resolved addresses, in the order of [`SYMBOLS`].
///
/// Raw function pointers, so the concrete signature is applied at each call site
/// rather than here.
struct Resolved {
    address: [*mut c_void; SYMBOLS.len()],
}

// Safe because the contents are immutable once built, and dlopen handles stay
// valid for the life of the process — this module never calls dlclose.
unsafe impl Send for Resolved {}
unsafe impl Sync for Resolved {}

/// Why libfabric could not be made available.
struct LoadFailure {
    /// Every candidate tried, with the loader's complaint about each.
    attempts: Vec<String>,
}

static LIBFABRIC: OnceLock<Result<Resolved, LoadFailure>> = OnceLock::new();

/// The loader's description of the most recent failure, if it left one.
fn last_error() -> String {
    // SAFETY: dlerror returns either null or a pointer to a C string owned by
    // the loader, valid until the next call on this thread.
    let message = unsafe { libc::dlerror() };
    if message.is_null() {
        "no error reported".to_string()
    } else {
        unsafe { CStr::from_ptr(message) }
            .to_string_lossy()
            .into_owned()
    }
}

/// Opens one candidate and resolves every symbol in it.
///
/// Resolves all of them or none: a library that supplies only part of the list
/// is a worse outcome than no library at all, because the gap would not show up
/// until the call that needed it.
fn try_candidate(path: &str) -> Result<Resolved, String> {
    let c_path = std::ffi::CString::new(path).map_err(|_| format!("{path}: not a valid path"))?;

    // Clear any stale message so last_error() describes this attempt.
    unsafe { libc::dlerror() };

    // RTLD_LAZY is enough: the symbols below are looked up explicitly, and
    // binding the rest lazily keeps the open cheap. RTLD_LOCAL keeps libfabric's
    // symbols out of the global namespace, so nothing else in the process can
    // accidentally bind to them.
    let handle = unsafe { libc::dlopen(c_path.as_ptr(), libc::RTLD_LAZY | libc::RTLD_LOCAL) };
    if handle.is_null() {
        return Err(format!("{path}: {}", last_error()));
    }

    let mut address = [std::ptr::null_mut(); SYMBOLS.len()];
    for (slot, name) in address.iter_mut().zip(SYMBOLS) {
        unsafe { libc::dlerror() };
        let symbol = unsafe { libc::dlsym(handle, name.as_ptr()) };
        if symbol.is_null() {
            let complaint = last_error();
            unsafe { libc::dlclose(handle) };
            return Err(format!(
                "{path}: loaded, but {} is missing: {complaint}",
                name.to_string_lossy()
            ));
        }
        *slot = symbol;
    }

    // The handle is deliberately not closed and not stored. The resolved
    // addresses have to stay valid for the life of the process.
    Ok(Resolved { address })
}

/// Walks the candidate list once, keeping the first library that supplies
/// everything.
fn load() -> Result<Resolved, LoadFailure> {
    let override_path = std::env::var(PATH_OVERRIDE_VAR).ok();
    let candidates: Vec<&str> = match override_path.as_deref() {
        Some(path) => vec![path],
        None => SEARCH_PATHS.to_vec(),
    };

    let mut attempts = Vec::with_capacity(candidates.len());
    for candidate in candidates {
        match try_candidate(candidate) {
            Ok(resolved) => return Ok(resolved),
            Err(complaint) => attempts.push(complaint),
        }
    }
    Err(LoadFailure { attempts })
}

/// The resolved symbol table, or nothing if libfabric could not be loaded.
fn resolved() -> Option<&'static Resolved> {
    LIBFABRIC.get_or_init(load).as_ref().ok()
}

/// Loads libfabric and checks that it is new enough to match these bindings.
///
/// Call this before setting up a fabric, so that a host without libfabric
/// reports it as an ordinary error rather than by crashing somewhere further in.
/// Repeat calls are cheap; the work happens once.
///
/// # Errors
///
/// Returns [`RdmaError::LibfabricUnavailable`] if no candidate could be loaded,
/// or if the one that loaded reports an API version older than the headers this
/// crate was built against.
pub fn ensure_loaded() -> Result<(), RdmaError> {
    let table = match LIBFABRIC.get_or_init(load) {
        Ok(table) => table,
        Err(failure) => {
            return Err(RdmaError::LibfabricUnavailable {
                detail: failure.attempts.join("; "),
            });
        }
    };

    // SAFETY: this slot holds fi_version, resolved above, and it takes no arguments.
    let runtime_version = unsafe {
        std::mem::transmute::<*mut c_void, extern "C" fn() -> u32>(table.address[slot::VERSION])()
    };

    if runtime_version < HEADER_API_VERSION {
        return Err(RdmaError::LibfabricUnavailable {
            detail: format!(
                "libfabric reports API {}.{}, but this build needs at least {}.{}; \
                 the two may disagree about how libfabric's structures are laid out",
                runtime_version >> 16,
                runtime_version & 0xffff,
                HEADER_API_VERSION >> 16,
                HEADER_API_VERSION & 0xffff,
            ),
        });
    }
    Ok(())
}

/// The libfabric API version this crate's bindings were generated against.
///
/// This is what should be handed to `fi_getinfo`: it states which layout the
/// caller was compiled for, so libfabric can present its structures that way.
/// Asking the loaded library for its own version instead tells it nothing and
/// gives up the compatibility check it offers.
pub const fn header_api_version() -> u32 {
    HEADER_API_VERSION
}

/// Looks up a slot, or returns `$fallback` if libfabric never loaded.
///
/// The fallback only matters to a caller that skipped [`ensure_loaded`]; it
/// keeps that mistake a returned failure rather than a crash.
macro_rules! forward {
    ($index:expr, $signature:ty, $fallback:expr) => {
        match resolved() {
            // SAFETY: the index names a symbol resolved out of libfabric, and
            // the signature matches its declaration in libfabric's headers.
            Some(table) => unsafe {
                std::mem::transmute::<*mut c_void, $signature>(table.address[$index])
            },
            None => return $fallback,
        }
    };
}

/// libfabric's code for "no data available", returned when no provider matches.
/// Reused here for "there was no libfabric at all", which is as true.
///
/// libfabric defines this as the platform's own `ENODATA`, and that number is
/// not the same everywhere: 61 on Linux, 96 on macOS. Taking it from `libc`
/// keeps it correct for whatever target is being built.
const FI_ENODATA: c_int = libc::ENODATA;

// The definitions below take the place of libfabric's own exported symbols.
// Both this crate's generated bindings and the compiled static-inline wrappers
// resolve their libfabric references against these.

/// See `fi_getinfo(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_getinfo(
    version: u32,
    node: *const c_char,
    service: *const c_char,
    flags: u64,
    hints: *const c_void,
    info: *mut *mut c_void,
) -> c_int {
    let real = forward!(
        slot::GETINFO,
        unsafe extern "C" fn(
            u32,
            *const c_char,
            *const c_char,
            u64,
            *const c_void,
            *mut *mut c_void,
        ) -> c_int,
        -FI_ENODATA
    );
    unsafe { real(version, node, service, flags, hints, info) }
}

/// See `fi_freeinfo(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_freeinfo(info: *mut c_void) {
    // Spelled out rather than going through `forward!`, because this is the one
    // shim that returns nothing and so has no fallback value to hand back.
    let Some(table) = resolved() else {
        // Nothing was ever allocated by a libfabric that does not exist, so
        // there is nothing to free.
        return;
    };
    // SAFETY: this slot holds fi_freeinfo, and the signature matches its declaration
    // in libfabric's headers.
    let real = unsafe {
        std::mem::transmute::<*mut c_void, unsafe extern "C" fn(*mut c_void)>(
            table.address[slot::FREEINFO],
        )
    };
    unsafe { real(info) }
}

/// See `fi_dupinfo(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_dupinfo(info: *const c_void) -> *mut c_void {
    let real = forward!(
        slot::DUPINFO,
        unsafe extern "C" fn(*const c_void) -> *mut c_void,
        std::ptr::null_mut()
    );
    unsafe { real(info) }
}

/// See `fi_fabric(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_fabric(
    attr: *mut c_void,
    fabric: *mut *mut c_void,
    context: *mut c_void,
) -> c_int {
    let real = forward!(
        slot::FABRIC,
        unsafe extern "C" fn(*mut c_void, *mut *mut c_void, *mut c_void) -> c_int,
        -FI_ENODATA
    );
    unsafe { real(attr, fabric, context) }
}

/// See `fi_strerror(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_strerror(errnum: c_int) -> *const c_char {
    let real = forward!(
        slot::STRERROR,
        unsafe extern "C" fn(c_int) -> *const c_char,
        c"libfabric is not loaded".as_ptr()
    );
    unsafe { real(errnum) }
}

/// See `fi_version(3)`.
///
/// Reports the version of the libfabric that actually loaded, which is not
/// necessarily the one these bindings were built against — see
/// [`header_api_version`].
#[unsafe(no_mangle)]
pub extern "C" fn fi_version() -> u32 {
    let real = forward!(slot::VERSION, extern "C" fn() -> u32, 0);
    real()
}

/// See `fi_open(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_open(
    version: u32,
    name: *const c_char,
    attr: *mut c_void,
    attr_len: usize,
    flags: u64,
    fid: *mut *mut c_void,
    context: *mut c_void,
) -> c_int {
    let real = forward!(
        slot::OPEN,
        unsafe extern "C" fn(
            u32,
            *const c_char,
            *mut c_void,
            usize,
            u64,
            *mut *mut c_void,
            *mut c_void,
        ) -> c_int,
        -FI_ENODATA
    );
    unsafe { real(version, name, attr, attr_len, flags, fid, context) }
}

/// See `fi_param_get(3)`.
///
/// # Safety
///
/// The caller upholds libfabric's contract for this function.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn fi_param_get(
    provider: *mut c_void,
    param_name: *const c_char,
    value: *mut c_void,
) -> c_int {
    let real = forward!(
        slot::PARAM_GET,
        unsafe extern "C" fn(*mut c_void, *const c_char, *mut c_void) -> c_int,
        -FI_ENODATA
    );
    unsafe { real(provider, param_name, value) }
}

#[cfg(test)]
mod tests {
    use super::{FI_ENODATA, SYMBOLS, ensure_loaded, header_api_version};

    #[test]
    fn names_and_positions_agree() {
        // The shims reach their function by position, so a reordered SYMBOLS
        // list would silently point every call at the wrong function. Nothing
        // else would notice: the names are strings and the addresses are
        // untyped.
        for (position, expected) in [
            (super::slot::GETINFO, "fi_getinfo"),
            (super::slot::FREEINFO, "fi_freeinfo"),
            (super::slot::DUPINFO, "fi_dupinfo"),
            (super::slot::FABRIC, "fi_fabric"),
            (super::slot::STRERROR, "fi_strerror"),
            (super::slot::VERSION, "fi_version"),
            (super::slot::OPEN, "fi_open"),
            (super::slot::PARAM_GET, "fi_param_get"),
        ] {
            assert_eq!(
                SYMBOLS[position].to_str().unwrap(),
                expected,
                "position {position} should name {expected}"
            );
        }
    }

    #[test]
    fn header_api_version_is_set() {
        // The bindings are the only source of this number. If a regeneration
        // dropped the version constants, it would be 0 and ensure_loaded would
        // accept any libfabric at all. libfabric has been on 2.x since before
        // this crate existed.
        assert!(header_api_version() >> 16 >= 2, "major version looks unset");
    }

    #[test]
    fn the_shims_stand_in_for_the_real_symbols() {
        ensure_loaded().expect("libfabric should load");
        // fi_strerror goes out through the shim and comes back with libfabric's
        // own wording, which is only possible if the forwarding works.
        // SAFETY: fi_strerror takes an error number and returns a static string.
        let message = unsafe { std::ffi::CStr::from_ptr(super::fi_strerror(0)) };
        assert_ne!(
            message.to_string_lossy(),
            "libfabric is not loaded",
            "fi_strerror returned the not-loaded placeholder"
        );
    }

    #[test]
    fn the_not_loaded_code_belongs_to_the_target() {
        // The shims report a missing libfabric as -FI_ENODATA, and libfabric
        // defines that as the platform's own ENODATA: 61 on Linux, 96 on macOS.
        // The generated bindings cannot supply it, because they carry whichever
        // number belonged to the machine that ran bindgen. Writing the number
        // out here would make the error code wrong on every other platform.
        let expected = if cfg!(target_os = "linux") { 61 } else { 96 };
        assert_eq!(
            FI_ENODATA, expected,
            "ENODATA is not the value this target uses"
        );
    }
}
