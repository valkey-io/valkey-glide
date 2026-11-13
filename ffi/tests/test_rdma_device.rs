// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RDMA into and out of CUDA memory through the C API, against a server that
//! serves the `BLOB.*` commands.
//!
//! Ignored by default: it needs a CUDA GPU, which CI does not have. CUDA is not
//! linked. The driver library, `libcuda.so.1`, is loaded when a test starts, so
//! this builds wherever the rest of the crate does.
//!
//! | You need | |
//! |---|---|
//! | a Linux host with a CUDA GPU and its driver | `libcuda.so.1` |
//! | an EFA device, and libfabric built with CUDA | as the EFA installer ships it, so `fi_info -p efa -c FI_HMEM` lists the `efa-direct` fabric |
//! | a server or cluster with the large-object module, `fabric-provider EfaDirect` | in the same AZ and VPC, its security group open to itself |
//!
//! ```sh
//! export GLIDE_RDMA_SERVER=<server private IP>:<port>
//! export GLIDE_RDMA_PASSWORD=<password>   # if the server needs one
//! export GLIDE_RDMA_CLUSTER=1             # if it is a cluster
//! export GLIDE_CUDA_DEVICE=0              # the GPU to allocate on, 0 if unset
//! cargo test --release --features rdma --test test_rdma_device -- --ignored --nocapture
//! # Again over the peer memory path instead of dmabuf, which needs nvidia_peermem
//! # or efa_nv_peermem loaded:
//! FI_HMEM_CUDA_USE_DMABUF=0 cargo test --release --features rdma --test test_rdma_device -- --ignored --nocapture
//! ```
//!
//! Where dmabuf does not work, libfabric falls back to peer memory on its own, so
//! the first run can take the peer memory path too. To see which path a run took,
//! set `FI_LOG_LEVEL=info`. Over dmabuf, libfabric logs `Registering dmabuf MR` for
//! each region of CUDA memory, and over peer memory it does not.
//!
//! The tests write keys under `glide-rdma-device-test:` and delete them.
//!
//! [`a_value_round_trips_through_gpu_memory`] is the example to follow: it shows
//! what the caller has to do around each transfer, since GLIDE does not call CUDA.
//! [`a_cancelled_transfer_lets_the_allocation_go`] shows when a caller may free
//! the allocation after a transfer is cancelled.

#![cfg(all(feature = "rdma", unix))]

use glide_core::connection_request::{
    AuthenticationInfo, ConnectionRequest, EfaDirectProvider, NodeAddress, RdmaConfig, TlsMode,
};
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_char, c_ulong, c_void};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, mpsc};
use std::thread;
use std::time::{Duration, Instant};

const MB: usize = 1 << 20;
const KEYS: &str = "glide-rdma-device-test:";
/// Must match glide-core's `rdma::protocol::CANCELLED`.
const CANCELLED: &str = "RDMA transfer cancelled";

// ---------------------------------------------------------------------------
// The CUDA driver API, loaded at run time.

type CuResult = i32;
type DevicePtr = u64;

macro_rules! cuda_api {
    ($($field:ident = $symbol:literal ($($arg:ty),*);)*) => {
        /// The driver functions these tests call.
        struct Cuda {
            $($field: unsafe extern "C" fn($($arg),*) -> CuResult,)*
        }

        impl Cuda {
            fn load() -> Self {
                // SAFETY: a library name and flags for dlopen, and a symbol name for
                // dlsym, both NUL-terminated.
                let library = unsafe { libc::dlopen(c"libcuda.so.1".as_ptr(), libc::RTLD_NOW) };
                assert!(
                    !library.is_null(),
                    "these tests need the CUDA driver, libcuda.so.1, which did not load"
                );
                Self {
                    $($field: {
                        let symbol = unsafe { libc::dlsym(library, $symbol.as_ptr()) };
                        assert!(!symbol.is_null(), "libcuda.so.1 has no {:?}", $symbol);
                        // SAFETY: the symbol is the driver function of that name, whose
                        // C signature the field's type spells out.
                        unsafe {
                            std::mem::transmute::<
                                *mut c_void,
                                unsafe extern "C" fn($($arg),*) -> CuResult,
                            >(symbol)
                        }
                    },)*
                }
            }
        }
    };
}

cuda_api! {
    init = c"cuInit" (u32);
    device_get = c"cuDeviceGet" (*mut i32, i32);
    primary_context_retain = c"cuDevicePrimaryCtxRetain" (*mut *mut c_void, i32);
    context_set_current = c"cuCtxSetCurrent" (*mut c_void);
    context_synchronize = c"cuCtxSynchronize" ();
    error_name = c"cuGetErrorName" (CuResult, *mut *const c_char);
    device_attribute = c"cuDeviceGetAttribute" (*mut i32, i32, i32);
    malloc = c"cuMemAlloc_v2" (*mut DevicePtr, usize);
    free = c"cuMemFree_v2" (DevicePtr);
    copy_to_device = c"cuMemcpyHtoD_v2" (DevicePtr, *const c_void, usize);
    copy_to_host = c"cuMemcpyDtoH_v2" (*mut c_void, DevicePtr, usize);
    copy_on_device = c"cuMemcpyDtoD_v2" (DevicePtr, DevicePtr, usize);
    memset = c"cuMemsetD8_v2" (DevicePtr, u8, usize);
    stream_create = c"cuStreamCreate" (*mut *mut c_void, u32);
    stream_destroy = c"cuStreamDestroy_v2" (*mut c_void);
    copy_to_device_async = c"cuMemcpyHtoDAsync_v2" (DevicePtr, *const c_void, usize, *mut c_void);
    stream_synchronize = c"cuStreamSynchronize" (*mut c_void);
    flush_rdma_writes = c"cuFlushGPUDirectRDMAWrites" (i32, i32);
    allocation_granularity = c"cuMemGetAllocationGranularity" (*mut usize, *const AllocationProp, i32);
    address_reserve = c"cuMemAddressReserve" (*mut DevicePtr, usize, usize, DevicePtr, u64);
    mem_create = c"cuMemCreate" (*mut u64, usize, *const AllocationProp, u64);
    mem_map = c"cuMemMap" (DevicePtr, usize, usize, u64, u64);
    mem_set_access = c"cuMemSetAccess" (DevicePtr, usize, *const AccessDesc, usize);
    mem_unmap = c"cuMemUnmap" (DevicePtr, usize);
    mem_release = c"cuMemRelease" (u64);
    address_free = c"cuMemAddressFree" (DevicePtr, usize);
}

// cuda.h values.
const ATTRIBUTE_VIRTUAL_MEMORY_MANAGEMENT_SUPPORTED: i32 = 102;
const ATTRIBUTE_GPU_DIRECT_RDMA_WITH_CUDA_VMM_SUPPORTED: i32 = 110;
const ATTRIBUTE_GPU_DIRECT_RDMA_FLUSH_WRITES_OPTIONS: i32 = 117;
const ATTRIBUTE_GPU_DIRECT_RDMA_WRITES_ORDERING: i32 = 118;
const FLUSH_WRITES_OPTION_HOST: i32 = 1;
const WRITES_ORDERING_OWNER: i32 = 100;
const FLUSH_TARGET_CURRENT_CONTEXT: i32 = 0;
const FLUSH_TO_OWNER: i32 = 100;

#[repr(C)]
#[derive(Clone, Copy)]
struct MemLocation {
    kind: i32,
    id: i32,
}

#[repr(C)]
struct AllocationProp {
    kind: i32,
    requested_handle_types: i32,
    location: MemLocation,
    win32_handle_metadata: *mut c_void,
    compression_type: u8,
    gpu_direct_rdma_capable: u8,
    usage: u16,
    reserved: [u8; 4],
}

#[repr(C)]
struct AccessDesc {
    location: MemLocation,
    flags: i32,
}

fn cuda() -> &'static Cuda {
    static CUDA: OnceLock<Cuda> = OnceLock::new();
    CUDA.get_or_init(Cuda::load)
}

/// Fail with the driver's name for `code` unless it is success.
fn check(code: CuResult, what: &str) {
    if code != 0 {
        let mut name: *const c_char = std::ptr::null();
        unsafe { (cuda().error_name)(code, &mut name) };
        let name = if name.is_null() {
            "an unknown error".into()
        } else {
            unsafe { CStr::from_ptr(name) }.to_string_lossy()
        };
        panic!("{what} failed with {name} ({code})");
    }
}

/// The GPU these tests allocate on, made current on the calling thread.
struct Gpu {
    ordinal: i32,
}

impl Gpu {
    fn current() -> Self {
        let ordinal = std::env::var("GLIDE_CUDA_DEVICE").map_or(0, |gpu| {
            gpu.parse().expect("GLIDE_CUDA_DEVICE is a CUDA ordinal")
        });
        let cuda = cuda();
        unsafe {
            check((cuda.init)(0), "cuInit");
            let mut device = 0;
            check((cuda.device_get)(&mut device, ordinal), "cuDeviceGet");
            let mut context = std::ptr::null_mut();
            check(
                (cuda.primary_context_retain)(&mut context, device),
                "cuDevicePrimaryCtxRetain",
            );
            check((cuda.context_set_current)(context), "cuCtxSetCurrent");
        }
        Self { ordinal }
    }

    fn attribute(&self, attribute: i32) -> i32 {
        let mut value = 0;
        check(
            unsafe { (cuda().device_attribute)(&mut value, attribute, self.ordinal) },
            "cuDeviceGetAttribute",
        );
        value
    }

    fn alloc(&self, length: usize) -> DevicePtr {
        let mut memory = 0;
        check(
            unsafe { (cuda().malloc)(&mut memory, length) },
            "cuMemAlloc",
        );
        memory
    }

    fn free(&self, memory: DevicePtr) {
        check(unsafe { (cuda().free)(memory) }, "cuMemFree");
    }

    /// Write `bytes` at `at` and wait until they are in GPU memory. A copy from
    /// pageable host memory can return before its DMA lands, so the wait is part
    /// of filling memory the NIC is about to read.
    fn fill(&self, memory: DevicePtr, at: usize, bytes: &[u8]) {
        unsafe {
            check(
                (cuda().copy_to_device)(memory + at as u64, bytes.as_ptr().cast(), bytes.len()),
                "cuMemcpyHtoD",
            );
            check((cuda().context_synchronize)(), "cuCtxSynchronize");
        }
    }

    fn clear(&self, memory: DevicePtr, at: usize, length: usize) {
        unsafe {
            check((cuda().memset)(memory + at as u64, 0, length), "cuMemsetD8");
            check((cuda().context_synchronize)(), "cuCtxSynchronize");
        }
    }

    fn read(&self, memory: DevicePtr, at: usize, length: usize) -> Vec<u8> {
        let mut bytes = vec![0u8; length];
        check(
            unsafe { (cuda().copy_to_host)(bytes.as_mut_ptr().cast(), memory + at as u64, length) },
            "cuMemcpyDtoH",
        );
        bytes
    }

    /// Make bytes an `rdma_get` landed visible to GPU work submitted after it.
    ///
    /// A device whose write ordering covers consumers on the device itself needs
    /// nothing. Any other needs a flush, which the device must support.
    fn make_landed_bytes_visible(&self) {
        if self.attribute(ATTRIBUTE_GPU_DIRECT_RDMA_WRITES_ORDERING) >= WRITES_ORDERING_OWNER {
            return;
        }
        assert!(
            self.attribute(ATTRIBUTE_GPU_DIRECT_RDMA_FLUSH_WRITES_OPTIONS)
                & FLUSH_WRITES_OPTION_HOST
                != 0,
            "this GPU neither orders GPUDirect RDMA writes nor supports flushing them"
        );
        check(
            unsafe { (cuda().flush_rdma_writes)(FLUSH_TARGET_CURRENT_CONTEXT, FLUSH_TO_OWNER) },
            "cuFlushGPUDirectRDMAWrites",
        );
    }

    fn location(&self) -> MemLocation {
        MemLocation {
            kind: 1, // CU_MEM_LOCATION_TYPE_DEVICE
            id: self.ordinal,
        }
    }

    fn vmm_prop(&self, gpu_direct_rdma_capable: bool) -> AllocationProp {
        AllocationProp {
            kind: 1, // CU_MEM_ALLOCATION_TYPE_PINNED
            requested_handle_types: 0,
            location: self.location(),
            win32_handle_metadata: std::ptr::null_mut(),
            compression_type: 0,
            gpu_direct_rdma_capable: gpu_direct_rdma_capable.into(),
            usage: 0,
            reserved: [0; 4],
        }
    }

    /// `chunks` VMM allocations of `chunk` bytes each, mapped back to back, as
    /// PyTorch's expandable segments grow.
    fn vmm_range(&self, chunk: usize, chunks: usize, gpu_direct_rdma_capable: bool) -> VmmRange {
        let cuda = cuda();
        let prop = self.vmm_prop(gpu_direct_rdma_capable);
        let size = chunk * chunks;
        let mut base = 0;
        let mut handles = Vec::new();
        unsafe {
            check(
                (cuda.address_reserve)(&mut base, size, 0, 0, 0),
                "cuMemAddressReserve",
            );
            for index in 0..chunks {
                let mut handle = 0;
                check(
                    (cuda.mem_create)(&mut handle, chunk, &prop, 0),
                    "cuMemCreate",
                );
                check(
                    (cuda.mem_map)(base + (index * chunk) as u64, chunk, 0, handle, 0),
                    "cuMemMap",
                );
                handles.push(handle);
            }
            let access = AccessDesc {
                location: self.location(),
                flags: 3, // CU_MEM_ACCESS_FLAGS_PROT_READWRITE
            };
            check(
                (cuda.mem_set_access)(base, size, &access, 1),
                "cuMemSetAccess",
            );
        }
        VmmRange {
            base,
            size,
            handles,
        }
    }
}

struct VmmRange {
    base: DevicePtr,
    size: usize,
    handles: Vec<u64>,
}

impl VmmRange {
    fn free(self) {
        let cuda = cuda();
        unsafe {
            check((cuda.mem_unmap)(self.base, self.size), "cuMemUnmap");
            for handle in self.handles {
                check((cuda.mem_release)(handle), "cuMemRelease");
            }
            check(
                (cuda.address_free)(self.base, self.size),
                "cuMemAddressFree",
            );
        }
    }
}

// ---------------------------------------------------------------------------
// The C API.

/// A client of `GLIDE_RDMA_SERVER` with device memory on, through `address` if
/// set and in standalone mode then.
fn connect_to(address: Option<&str>) -> *const c_void {
    let server = std::env::var("GLIDE_RDMA_SERVER").expect("set GLIDE_RDMA_SERVER=host:port");
    let (host, port) = address
        .unwrap_or(&server)
        .rsplit_once(':')
        .expect("GLIDE_RDMA_SERVER is host:port");
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    request.cluster_mode_enabled =
        address.is_none() && std::env::var("GLIDE_RDMA_CLUSTER").is_ok_and(|on| on == "1");
    request.request_timeout = 30_000;
    request.connection_timeout = 30_000;
    let mut node = NodeAddress::new();
    node.host = host.into();
    node.port = port.parse().expect("a port");
    request.addresses.push(node);
    if let Ok(password) = std::env::var("GLIDE_RDMA_PASSWORD") {
        let mut auth = AuthenticationInfo::new();
        auth.password = password.into();
        request.authentication_info = protobuf::MessageField::some(auth);
    }
    let mut rdma = RdmaConfig::new();
    rdma.set_efa_direct(EfaDirectProvider::new());
    rdma.device_memory = Some(true);
    request.rdma_config = protobuf::MessageField::some(rdma);
    let bytes = request.write_to_bytes().expect("serializes");
    unsafe {
        let response = create_client(
            bytes.as_ptr(),
            bytes.len(),
            Box::into_raw(Box::new(ClientType::SyncClient)),
            None,
            None,
            0,
        );
        let error = (*response).connection_error_message;
        assert!(
            error.is_null(),
            "connecting failed: {}",
            CStr::from_ptr(error).to_string_lossy()
        );
        let client = (*response).conn_ptr;
        free_connection_response(response.cast_mut());
        client
    }
}

fn connect() -> *const c_void {
    connect_to(None)
}

fn close(client: *const c_void) {
    // SAFETY: every test closes its client once, after its last call on it.
    unsafe { close_client(client) };
}

/// The region a registration made, or the error it reported.
fn registered(registration: *mut RdmaRegistration) -> Result<*mut RdmaRegion, String> {
    unsafe {
        let error = (*registration).error_message;
        let result = if error.is_null() {
            Ok((*registration).region)
        } else {
            Err(CStr::from_ptr(error).to_string_lossy().into_owned())
        };
        free_rdma_registration(registration);
        result
    }
}

fn register_device(client: *const c_void, memory: DevicePtr, length: usize) -> *mut RdmaRegion {
    registered(unsafe { register_rdma_device_region(client, memory as *mut u8, length) })
        .expect("registers CUDA memory")
}

/// Free a region whose transfers have all returned, and the memory it may then
/// let go of. See [`a_cancelled_transfer_lets_the_allocation_go`].
fn free_region(region: *mut RdmaRegion) {
    let hold = unsafe { free_rdma_region(region) };
    assert!(
        hold.is_null(),
        "on efa-direct closing a region stops the NIC's writes, so its memory is free at once"
    );
}

/// What a transfer reported, with the result freed.
#[derive(Debug)]
struct Outcome {
    error: Option<String>,
    found: bool,
    bytes_written: usize,
    checksum: Option<u32>,
    region_closed: bool,
}

impl Outcome {
    fn of(result: *mut RdmaResult) -> Self {
        unsafe {
            let r = &*result;
            let outcome = Self {
                error: (!r.error_message.is_null()).then(|| {
                    CStr::from_ptr(r.error_message)
                        .to_string_lossy()
                        .into_owned()
                }),
                found: r.found,
                bytes_written: r.bytes_written,
                checksum: r.has_checksum.then_some(r.checksum),
                region_closed: r.region_closed,
            };
            free_rdma_result(result);
            outcome
        }
    }

    fn succeeded(self, what: &str) -> Self {
        assert!(self.error.is_none(), "{what}: {:?}", self.error);
        assert!(!self.region_closed, "{what}: the region closed");
        self
    }
}

fn key(name: &str) -> String {
    format!("{KEYS}{name}")
}

fn set(
    client: *const c_void,
    key: &str,
    region: *mut RdmaRegion,
    (at, length): (usize, usize),
    mode: RdmaSetChecksumMode,
    checksum: u32,
) -> Outcome {
    Outcome::of(unsafe {
        rdma_set(
            client,
            key.as_ptr(),
            key.len(),
            region,
            at,
            length,
            mode,
            checksum,
        )
    })
}

fn get(
    client: *const c_void,
    key: &str,
    region: *mut RdmaRegion,
    (at, length): (usize, usize),
) -> Outcome {
    Outcome::of(unsafe { rdma_get(client, key.as_ptr(), key.len(), region, at, length) })
}

fn crc(bytes: &[u8]) -> u32 {
    let mut out = 0;
    assert!(unsafe { rdma_checksum(bytes.as_ptr(), bytes.len(), &mut out) });
    out
}

fn delete(client: *const c_void, names: &[&str]) {
    let keys: Vec<String> = names.iter().map(|name| key(name)).collect();
    let args: Vec<usize> = keys.iter().map(|key| key.as_ptr() as usize).collect();
    let lengths: Vec<c_ulong> = keys.iter().map(|key| key.len() as c_ulong).collect();
    unsafe {
        let result = command(
            client,
            0,
            RequestType::Del,
            keys.len() as c_ulong,
            args.as_ptr(),
            lengths.as_ptr(),
            std::ptr::null(),
            0,
            0,
        );
        let result = Box::from_raw(result);
        assert!(result.command_error.is_null(), "DEL failed");
        free_command_response(result.response);
    }
}

fn pattern(length: usize, seed: usize) -> Vec<u8> {
    (0..length).map(|i| (i * 131 + seed) as u8).collect()
}

// ---------------------------------------------------------------------------
// The tests.

/// The example: what a caller does around a set and a get of GPU memory.
#[test]
#[ignore = "needs a CUDA GPU, efa-direct and a server with the module; see the module docs"]
fn a_value_round_trips_through_gpu_memory() {
    let gpu = Gpu::current();
    let cuda = cuda();
    let length = 4 * MB + 3;
    let value = pattern(length, 7);
    let memory = gpu.alloc(2 * length);
    let landed = gpu.alloc(length);
    let client = connect();
    let region = register_device(client, memory, 2 * length);

    // 1. A producer writes the value on a stream, as a kernel or a copy would.
    let mut stream = std::ptr::null_mut();
    unsafe {
        check((cuda.stream_create)(&mut stream, 0), "cuStreamCreate");
        check(
            (cuda.copy_to_device_async)(memory, value.as_ptr().cast(), length, stream),
            "cuMemcpyHtoDAsync",
        );
        // 2. The NIC reads GPU memory outside CUDA's ordering, so wait for the
        //    producer before the set.
        check((cuda.stream_synchronize)(stream), "cuStreamSynchronize");
    }

    // 3. Set with a checksum the caller computed. GLIDE cannot read GPU memory,
    //    so it cannot compute one. This one comes from the bytes the producer
    //    started from. A caller without a host copy computes it on the GPU, or
    //    sends `Skip`.
    set(
        client,
        &key("round-trip"),
        region,
        (0, length),
        RdmaSetChecksumMode::Known,
        crc(&value),
    )
    .succeeded("set");

    // 4. Get the value into a second window. Nothing on the GPU may touch the
    //    window while the get runs.
    let receipt = get(client, &key("round-trip"), region, (length, length)).succeeded("get");
    assert!(receipt.found);
    assert_eq!(receipt.bytes_written, length);

    // 5. Make the landed bytes visible to GPU work submitted from here on.
    gpu.make_landed_bytes_visible();

    // 6. GPU work consumes them. A device-to-device copy stands in for a kernel.
    check(
        unsafe { (cuda.copy_on_device)(landed, memory + length as u64, length) },
        "cuMemcpyDtoD",
    );
    check(unsafe { (cuda.context_synchronize)() }, "cuCtxSynchronize");

    // 7. GLIDE did not check the landed bytes against the server's CRC-32c. The
    //    caller can, on the GPU or, as here, on a host copy.
    let consumed = gpu.read(landed, 0, length);
    assert_eq!(receipt.checksum, Some(crc(&consumed)), "the caller's check");
    assert!(
        consumed == value,
        "the bytes the GPU read are the bytes sent"
    );

    delete(client, &["round-trip"]);
    free_region(region);
    close(client);
    unsafe { check((cuda.stream_destroy)(stream), "cuStreamDestroy") };
    gpu.free(landed);
    gpu.free(memory);
}

/// Each checksum mode a set of GPU memory can take, and the one it cannot.
#[test]
#[ignore = "needs a CUDA GPU, efa-direct and a server with the module; see the module docs"]
fn each_checksum_mode_behaves_on_gpu_memory() {
    let gpu = Gpu::current();
    let length = MB;
    let value = pattern(length, 11);
    let memory = gpu.alloc(2 * length);
    gpu.fill(memory, 0, &value);
    let client = connect();
    let region = register_device(client, memory, 2 * length);
    let window = (0, length);
    let known = crc(&value);

    for (name, mode, checksum) in [
        ("skip", RdmaSetChecksumMode::Skip, 0),
        ("known", RdmaSetChecksumMode::Known, known),
    ] {
        set(client, &key(name), region, window, mode, checksum).succeeded(name);
        gpu.clear(memory, length, length);
        let receipt = get(client, &key(name), region, (length, length)).succeeded(name);
        gpu.make_landed_bytes_visible();
        assert_eq!(receipt.checksum, Some(known), "{name}: the server's CRC");
        assert!(
            gpu.read(memory, length, length) == value,
            "{name}: the bytes landed"
        );
    }

    let wrong = set(
        client,
        &key("wrong"),
        region,
        window,
        RdmaSetChecksumMode::Known,
        known ^ 1,
    );
    // The wording is the module's, so only the refusal is checked.
    assert!(
        wrong.error.is_some(),
        "the server refuses a checksum that does not match"
    );
    assert!(
        !wrong.region_closed,
        "a refusal is a reply, so the region stays usable"
    );

    let compute = set(
        client,
        &key("compute"),
        region,
        window,
        RdmaSetChecksumMode::Compute,
        0,
    );
    let error = compute.error.expect("Compute is refused");
    assert!(error.contains("cannot compute a checksum"), "{error}");
    assert!(
        !compute.region_closed,
        "it is refused before anything is sent"
    );

    set(
        client,
        &key("after"),
        region,
        window,
        RdmaSetChecksumMode::Skip,
        0,
    )
    .succeeded("the region still transfers after both refusals");

    delete(client, &["skip", "known", "wrong", "compute", "after"]);
    free_region(region);
    close(client);
    gpu.free(memory);
}

/// A region that starts and ends off a page boundary inside a larger
/// allocation, as a slice of a tensor would, with windows at odd offsets.
#[test]
#[ignore = "needs a CUDA GPU, efa-direct and a server with the module; see the module docs"]
fn a_region_at_an_odd_offset_round_trips() {
    let gpu = Gpu::current();
    let allocation = 16 * MB;
    let (start, length) = (4097, 5 * MB + 13);
    let memory = gpu.alloc(allocation);
    gpu.clear(memory, 0, allocation);
    let base = memory + start as u64;
    let client = connect();
    let region = register_device(client, base, length);

    let value = pattern(MB + 7, 3);
    let (from, to) = (1001, 2 * MB + 333);
    gpu.fill(base, from, &value);
    set(
        client,
        &key("offset"),
        region,
        (from, value.len()),
        RdmaSetChecksumMode::Known,
        crc(&value),
    )
    .succeeded("set");
    get(client, &key("offset"), region, (to, value.len())).succeeded("get");
    gpu.make_landed_bytes_visible();
    assert!(gpu.read(base, to, value.len()) == value);

    // The last byte of the region is reachable.
    let tail = pattern(4093, 9);
    gpu.fill(base, length - tail.len(), &tail);
    set(
        client,
        &key("tail"),
        region,
        (length - tail.len(), tail.len()),
        RdmaSetChecksumMode::Known,
        crc(&tail),
    )
    .succeeded("set of the last bytes");
    get(client, &key("tail"), region, (17, tail.len())).succeeded("get");
    gpu.make_landed_bytes_visible();
    assert!(gpu.read(base, 17, tail.len()) == tail);

    // A window past the end is refused, and the region stays usable.
    let past = set(
        client,
        &key("past"),
        region,
        (length - 10, 11),
        RdmaSetChecksumMode::Skip,
        0,
    );
    assert!(past.error.is_some() && !past.region_closed, "{past:?}");

    // The bytes around the region were never written.
    let before = gpu.read(memory, 0, start);
    let after = gpu.read(base, length, allocation - start - length);
    assert!(before.iter().chain(&after).all(|&byte| byte == 0));

    delete(client, &["offset", "tail"]);
    free_region(region);
    close(client);
    gpu.free(memory);
}

/// VMM memory, as PyTorch's expandable segments allocate it.
///
/// One allocation round-trips on both registration paths. One region across two
/// mapped allocations, and an allocation without the GPUDirect RDMA flag,
/// register over dmabuf, but the peer memory path refuses them. Either way a
/// refusal is an error, and the client keeps working.
#[test]
#[ignore = "needs a CUDA GPU, efa-direct and a server with the module; see the module docs"]
fn vmm_memory_round_trips_or_is_refused() {
    let gpu = Gpu::current();
    if gpu.attribute(ATTRIBUTE_VIRTUAL_MEMORY_MANAGEMENT_SUPPORTED) != 1 {
        println!("this GPU has no VMM, so there is nothing to test");
        return;
    }
    println!(
        "GPUDirect RDMA with VMM supported: {}",
        gpu.attribute(ATTRIBUTE_GPU_DIRECT_RDMA_WITH_CUDA_VMM_SUPPORTED)
    );
    let mut granularity = 0;
    check(
        unsafe { (cuda().allocation_granularity)(&mut granularity, &gpu.vmm_prop(true), 0) },
        "cuMemGetAllocationGranularity",
    );
    let chunk = (8 * MB).div_ceil(granularity) * granularity;
    let client = connect();

    let round_trip = |range: &VmmRange, length: usize, from: usize, to: usize, name: &str| {
        let region = register_device(client, range.base, length);
        let value = pattern(MB + 5, from);
        gpu.fill(range.base, from, &value);
        set(
            client,
            &key(name),
            region,
            (from, value.len()),
            RdmaSetChecksumMode::Known,
            crc(&value),
        )
        .succeeded(name);
        get(client, &key(name), region, (to, value.len())).succeeded(name);
        gpu.make_landed_bytes_visible();
        assert!(gpu.read(range.base, to, value.len()) == value, "{name}");
        free_region(region);
    };

    let one = gpu.vmm_range(chunk, 1, true);
    round_trip(&one, chunk, 77, 3 * MB, "vmm-one");
    one.free();
    println!("VMM, one allocation: round-trips");

    for (name, chunks, flag) in [("vmm-seam", 2, true), ("vmm-no-flag", 1, false)] {
        let range = gpu.vmm_range(chunk, chunks, flag);
        let length = chunk * chunks;
        match registered(unsafe {
            register_rdma_device_region(client, range.base as *mut u8, length)
        }) {
            Ok(region) => {
                free_region(region);
                // Across the seam when there is one.
                let (from, to) = if chunks > 1 {
                    (chunk - MB, 4096)
                } else {
                    (77, 3 * MB)
                };
                round_trip(&range, length, from, to, name);
                println!("VMM, {name}: round-trips");
            }
            Err(error) => println!("VMM, {name}: refused: {error}"),
        }
        range.free();
    }

    // Whatever was refused, the client still registers and transfers.
    let after = gpu.vmm_range(chunk, 1, true);
    round_trip(&after, chunk, 0, MB * 2, "vmm-after");
    after.free();

    delete(client, &["vmm-one", "vmm-seam", "vmm-no-flag", "vmm-after"]);
    close(client);
}

/// Forwards connections to `server`, but holds back everything a client sends
/// from the first command that contains `trigger` on, without closing the
/// connection, so that command is never answered.
struct HoldingProxy {
    address: String,
    held: Arc<AtomicBool>,
}

impl HoldingProxy {
    fn start(server: String, trigger: &'static [u8]) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").expect("binds a local port");
        let address = listener.local_addr().expect("has an address").to_string();
        let held = Arc::new(AtomicBool::new(false));
        let holding = held.clone();
        thread::spawn(move || {
            for client in listener.incoming() {
                let Ok(client) = client else { return };
                let Ok(upstream) = TcpStream::connect(&server) else {
                    return;
                };
                let (to_server, to_client) = (upstream.try_clone(), client.try_clone());
                let (Ok(to_server), Ok(to_client)) = (to_server, to_client) else {
                    return;
                };
                let held = holding.clone();
                thread::spawn(move || pipe(client, to_server, Some((trigger, held))));
                thread::spawn(move || pipe(upstream, to_client, None));
            }
        });
        Self { address, held }
    }
}

fn pipe(mut from: TcpStream, mut to: TcpStream, hold: Option<(&[u8], Arc<AtomicBool>)>) {
    let mut buffer = [0u8; 64 * 1024];
    // The end of what came before, so a trigger split across two reads is found.
    let mut seen: Vec<u8> = Vec::new();
    loop {
        let read = match from.read(&mut buffer) {
            Ok(0) | Err(_) => return,
            Ok(read) => read,
        };
        if let Some((trigger, held)) = &hold {
            seen.extend_from_slice(&buffer[..read]);
            if held.load(Ordering::Acquire) || seen.windows(trigger.len()).any(|w| w == *trigger) {
                held.store(true, Ordering::Release);
                continue;
            }
            seen.drain(..seen.len().saturating_sub(trigger.len() - 1));
        }
        if to.write_all(&buffer[..read]).is_err() {
            return;
        }
    }
}

/// A transfer that gets no reply, cancelled by the caller's own timeout, and
/// when the caller may then free the GPU allocation.
///
/// The C API has no transfer timeout. A caller that wants one cancels from
/// another thread with `rdma_close_regions`, which revokes the client's regions
/// so the server can no longer reach the memory. The transfer then returns with
/// `region_closed` set. Only once it has returned may the region be freed, and
/// only once `free_rdma_region` says so may the memory be.
#[test]
#[ignore = "needs a CUDA GPU, efa-direct and a server with the module; see the module docs"]
fn a_cancelled_transfer_lets_the_allocation_go() {
    let gpu = Gpu::current();
    let length = 8 * MB;
    let memory = gpu.alloc(length);
    let server = std::env::var("GLIDE_RDMA_SERVER").expect("set GLIDE_RDMA_SERVER=host:port");
    // The proxy passes the handshake and holds the get, standing in for a server
    // that never answers.
    let proxy = HoldingProxy::start(server, b"BLOB.RDMA_GET");
    let client = connect_to(Some(&proxy.address));
    let region = register_device(client, memory, length);

    // The caller's timeout: a watchdog that cancels once it passes.
    let timeout = Duration::from_millis(500);
    let (finished, done) = mpsc::channel::<()>();
    let watched = client as usize;
    let watchdog = thread::spawn(move || {
        if done.recv_timeout(timeout).is_err() {
            let error = unsafe { rdma_close_regions(watched as *const c_void) };
            assert!(error.is_null(), "every region is revoked");
        }
    });

    let started = Instant::now();
    let outcome = get(client, &key("never-answered"), region, (0, length));
    let waited = started.elapsed();
    let _ = finished.send(());
    watchdog.join().expect("the watchdog ran");
    assert!(
        proxy.held.load(Ordering::Acquire),
        "the get reached the proxy"
    );
    assert!(
        waited >= timeout && waited < timeout + Duration::from_secs(5),
        "{waited:?}"
    );
    let error = outcome.error.expect("the get was cancelled");
    assert!(error.starts_with(CANCELLED), "{error}");
    assert!(
        outcome.region_closed,
        "a cancelled transfer closes its region"
    );

    // The get has returned, so the region can be freed. Null means the memory
    // can be freed or reused now. A hold would mean keeping it until
    // `rdma_memory_released` says otherwise, and for good if `rdma_memory_leaked`.
    let hold = unsafe { free_rdma_region(region) };
    if !hold.is_null() {
        let leaked = unsafe { rdma_memory_leaked(hold) };
        unsafe { free_rdma_memory_hold(hold) };
        panic!(
            "on efa-direct closing a region stops the NIC's writes, so no hold is expected \
             (leaked: {leaked})"
        );
    }
    gpu.free(memory);
    close(client);
    println!("cancelled after {waited:?}, and the allocation was freed at once");
}
