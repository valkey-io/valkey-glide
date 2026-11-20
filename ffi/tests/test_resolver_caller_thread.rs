// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// An address resolver called while an FFI call blocks its caller runs on that caller's thread:
// PHP can only run PHP code on the thread that called into the FFI. The connect itself still
// runs on a runtime worker, since the caller's stack can be too small for it.

mod common;

use common::{NODE_ENV, TlsCluster, on_musl_sized_stack, request, run_child};
use glide_core::connection_request::{
    ConnectionRequest, PeriodicChecksDisabled, PeriodicChecksManualInterval, TlsMode,
};
use glide_core::errors::RequestErrorType;
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_void};
use std::sync::Mutex;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant};

const CALLER: &str = "ffi-caller";
const REENTRANT_CALL: &str = "GLIDE cannot be called from inside an address resolver callback";

/// Every resolution so far: the client id it was for, and the name of the thread it ran on.
static RESOLUTIONS: Mutex<Vec<(usize, String)>> = Mutex::new(Vec::new());

unsafe extern "C-unwind" fn record_resolver_thread(
    client_id: usize,
    _: *const u8,
    _: usize,
    _: u16,
    _: *mut u8,
    _: usize,
    _: *mut usize,
) -> u16 {
    let name = std::thread::current()
        .name()
        .unwrap_or_default()
        .to_string();
    RESOLUTIONS.lock().unwrap().push((client_id, name));
    0 // keep the address as given
}

/// Takes the names of the threads the resolver of `client_id` ran on so far.
fn take_threads(client_id: usize) -> Vec<String> {
    let mut resolutions = RESOLUTIONS.lock().unwrap();
    let (taken, kept) = std::mem::take(&mut *resolutions)
        .into_iter()
        .partition(|(id, _)| *id == client_id);
    *resolutions = kept;
    taken.into_iter().map(|(_, name)| name).collect()
}

unsafe extern "C-unwind" fn ignore_success(_: usize, _: *const CommandResponse) {}
unsafe extern "C-unwind" fn ignore_failure(
    _: usize,
    _: *const std::ffi::c_char,
    _: RequestErrorType,
) {
}

struct Client(*const c_void);
unsafe impl Send for Client {}

impl Drop for Client {
    fn drop(&mut self) {
        unsafe { close_client(self.0) };
    }
}

fn create(
    request: &ConnectionRequest,
    client_type: ClientType,
    resolver: AddressResolverCallback,
    client_id: usize,
) -> Client {
    let request = request
        .write_to_bytes()
        .expect("serialize connection request");
    unsafe {
        let response = create_client(
            request.as_ptr(),
            request.len(),
            &client_type,
            None,
            resolver,
            client_id,
        );
        let error = (*response).connection_error_message;
        assert!(
            error.is_null(),
            "create_client failed: {}",
            CStr::from_ptr(error).to_string_lossy()
        );
        let client = Client((*response).conn_ptr);
        free_connection_response(response as *mut ConnectionResponse);
        client
    }
}

fn send(client: &Client, request_type: RequestType, args: &[&str]) -> Result<(), String> {
    let ptrs: Vec<usize> = args.iter().map(|arg| arg.as_ptr() as usize).collect();
    let lens: Vec<std::ffi::c_ulong> = args.iter().map(|arg| arg.len() as _).collect();
    unsafe {
        let result = command(
            client.0,
            0,
            request_type,
            args.len() as _,
            ptrs.as_ptr(),
            lens.as_ptr(),
            std::ptr::null(),
            0,
            0,
        );
        assert!(!result.is_null());
        let error = (*result).command_error;
        let outcome = if error.is_null() {
            Ok(())
        } else {
            Err(CStr::from_ptr((*error).command_error_message)
                .to_string_lossy()
                .into_owned())
        };
        free_command_result(result);
        outcome
    }
}

/// A request that runs no periodic topology check, so no resolution happens in the background.
fn quiet_request(node: &str, cluster: bool) -> ConnectionRequest {
    let mut request = request(node, TlsMode::SecureTls);
    request.cluster_mode_enabled = cluster;
    request.set_periodic_checks_disabled(PeriodicChecksDisabled::new());
    request
}

/// Takes what the resolver of `client_id` recorded and asserts it ran, only on the caller.
fn assert_resolved_on_the_caller(client_id: usize, what: &str) {
    let threads = take_threads(client_id);
    assert!(!threads.is_empty(), "{what}: the resolver never ran");
    assert!(
        threads.iter().all(|name| name == CALLER),
        "{what}: resolver threads {threads:?}"
    );
}

#[test]
fn resolver_runs_on_the_blocked_caller() {
    let cluster = TlsCluster::start(true);
    run_child("child_creates_cluster_clients", &cluster.node, None);
    let standalone = TlsCluster::start(false);
    run_child("child_creates_standalone_clients", &standalone.node, None);
}

/// Creates an eager sync, an eager async and a lazy sync client on a musl-sized thread: the
/// connect must stay off its stack, and the resolver must run on it.
fn create_clients_on_a_musl_sized_thread(cluster: bool) {
    let node = std::env::var(NODE_ENV)
        .unwrap_or_else(|_| panic!("run by its parent test, which sets {NODE_ENV}"));
    let resolver: AddressResolverCallback = Some(record_resolver_thread);
    let clients = on_musl_sized_stack(CALLER, move || {
        let request = quiet_request(&node, cluster);
        let sync = create(&request, ClientType::SyncClient, resolver, 1);
        assert_resolved_on_the_caller(1, "eager sync create");

        // Go creates async clients.
        let async_type = ClientType::AsyncClient {
            success_callback: ignore_success,
            failure_callback: ignore_failure,
            allow_stack_response: false,
        };
        let async_client = create(&request, async_type, resolver, 2);
        assert_resolved_on_the_caller(2, "eager async create");

        let mut lazy_request = request;
        lazy_request.lazy_connect = true;
        let lazy = create(&lazy_request, ClientType::SyncClient, resolver, 3);
        assert!(
            take_threads(3).is_empty(),
            "the lazy client connected early"
        );
        assert_eq!(send(&lazy, RequestType::Ping, &[]), Ok(()));
        assert_resolved_on_the_caller(3, "lazy sync first command");
        (sync, async_client, lazy)
    });
    drop(clients);
}

#[test]
#[ignore = "child process of resolver_runs_on_the_blocked_caller"]
fn child_creates_cluster_clients() {
    create_clients_on_a_musl_sized_thread(true);
}

#[test]
#[ignore = "child process of resolver_runs_on_the_blocked_caller"]
fn child_creates_standalone_clients() {
    create_clients_on_a_musl_sized_thread(false);
}

/// A cluster request whose periodic topology check runs every second. Each check resolves every
/// node, so it calls the resolver whether the caller is blocked or not.
fn checked_request(node: &str) -> ConnectionRequest {
    let mut request = request(node, TlsMode::SecureTls);
    let mut interval = PeriodicChecksManualInterval::new();
    interval.duration_in_sec = 1;
    request.set_periodic_checks_manual_interval(interval);
    request
}

/// Waits until the resolver of `client_id` has run on the caller.
fn wait_for_a_resolution_on_the_caller(client_id: usize) {
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut threads = Vec::new();
    while !threads.iter().any(|name| name == CALLER) {
        assert!(
            Instant::now() < deadline,
            "no resolution ran on the blocked caller: {threads:?}"
        );
        std::thread::sleep(Duration::from_millis(10));
        threads.extend(take_threads(client_id));
    }
}

/// A periodic check that runs while a sync command blocks runs its resolutions on the
/// command's caller.
#[test]
fn resolutions_while_a_sync_command_blocks_run_on_its_caller() {
    const CLIENT_ID: usize = 4;
    const KEY: &str = "resolver-caller-thread:blpop";
    let cluster = TlsCluster::start(true);
    let client = create(
        &checked_request(&cluster.node),
        ClientType::SyncClient,
        Some(record_resolver_thread),
        CLIENT_ID,
    );
    let pusher = create(
        &quiet_request(&cluster.node, true),
        ClientType::SyncClient,
        None,
        0,
    );

    let blocked = std::thread::Builder::new()
        .name(CALLER.to_string())
        .spawn(move || send(&client, RequestType::BLPop, &[KEY, "0"]))
        .expect("spawn the caller");
    wait_for_a_resolution_on_the_caller(CLIENT_ID);
    assert_eq!(send(&pusher, RequestType::LPush, &[KEY, "v"]), Ok(()));
    assert_eq!(blocked.join().expect("the caller panicked"), Ok(()));
}

/// The client the resolver below sends its nested command to.
static NESTED_TARGET: AtomicUsize = AtomicUsize::new(0);
/// What each nested command returned.
static NESTED_OUTCOMES: Mutex<Vec<Result<(), String>>> = Mutex::new(Vec::new());

unsafe extern "C-unwind" fn resolve_with_a_nested_command(
    _: usize,
    _: *const u8,
    _: usize,
    _: u16,
    _: *mut u8,
    _: usize,
    _: *mut usize,
) -> u16 {
    let target = std::mem::ManuallyDrop::new(Client(
        NESTED_TARGET.load(Ordering::Acquire) as *const c_void
    ));
    let outcome = send(&target, RequestType::Ping, &[]);
    NESTED_OUTCOMES.lock().unwrap().push(outcome);
    0
}

#[test]
fn a_sync_command_from_the_resolver_on_a_blocked_caller_fails() {
    let server = TlsCluster::start(false);
    let request = quiet_request(&server.node, false);
    let target = create(&request, ClientType::SyncClient, None, 0);
    NESTED_TARGET.store(target.0 as usize, Ordering::Release);

    let _client = create(
        &request,
        ClientType::SyncClient,
        Some(resolve_with_a_nested_command),
        5,
    );
    let outcomes = std::mem::take(&mut *NESTED_OUTCOMES.lock().unwrap());
    assert!(!outcomes.is_empty(), "the resolver never ran");
    for outcome in outcomes {
        let error = outcome.expect_err("a nested command succeeded");
        assert!(error.contains(REENTRANT_CALL), "unexpected error: {error}");
    }
}

/// The same for RDMA transfers, which block their caller as sync commands do.
#[cfg(feature = "rdma-tcp-dev")]
#[test]
fn resolutions_while_an_rdma_transfer_blocks_run_on_its_caller() {
    use glide_core::connection_request::{RdmaConfig, TcpProvider};
    use std::sync::atomic::AtomicBool;

    const CLIENT_ID: usize = 6;
    let cluster = TlsCluster::start(true);
    let mut request = checked_request(&cluster.node);
    let mut rdma = RdmaConfig::new();
    rdma.set_tcp(TcpProvider::new());
    request.rdma_config = protobuf::MessageField::some(rdma);
    let client = create(
        &request,
        ClientType::SyncClient,
        Some(record_resolver_thread),
        CLIENT_ID,
    );
    let mut memory = vec![0u8; 64];
    let region = unsafe {
        let registration = register_rdma_region(client.0, memory.as_mut_ptr(), memory.len());
        let region = (*registration).region;
        assert!(!region.is_null(), "the region should register");
        free_rdma_registration(registration);
        region as usize
    };

    // The servers have no RDMA module, so each transfer fails at its handshake and hands the
    // region back. Transferring in a loop keeps the caller blocked nearly all the time.
    let stop = std::sync::Arc::new(AtomicBool::new(false));
    let transfers = {
        let (stop, client) = (stop.clone(), client.0 as usize);
        std::thread::Builder::new()
            .name(CALLER.to_string())
            .spawn(move || {
                let key = b"resolver-caller-thread:rdma";
                while !stop.load(Ordering::Acquire) {
                    unsafe {
                        let result = rdma_get(
                            client as *const c_void,
                            key.as_ptr(),
                            key.len(),
                            region as *mut RdmaRegion,
                            0,
                            64,
                        );
                        assert!(!(*result).region_closed, "a transfer closed the region");
                        free_rdma_result(result);
                    }
                }
            })
            .expect("spawn the caller")
    };
    wait_for_a_resolution_on_the_caller(CLIENT_ID);
    stop.store(true, Ordering::Release);
    transfers.join().expect("the caller panicked");

    let hold = unsafe { free_rdma_region(region as *mut RdmaRegion) };
    if !hold.is_null() {
        // Something may still write to the memory, so it must outlive the test.
        std::mem::forget(memory);
    }
}
