// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// A lazy client connects inside its first command. For a sync client that command must still
// connect on a runtime worker, not on the caller's thread, whose stack can be 128 KiB on musl.

mod common;

use common::{NODE_ENV, TlsCluster, on_musl_sized_stack, request, run_child};
use glide_core::connection_request::{
    AuthenticationInfo, ConnectionRequest, IamCredentials, TlsMode,
};
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_void};
use std::sync::Mutex;

const FIRST_COMMAND_THREAD: &str = "first-command";

/// Names of the threads the connect called the address resolver on.
static RESOLVER_THREADS: Mutex<Vec<String>> = Mutex::new(Vec::new());

unsafe extern "C-unwind" fn record_resolver_thread(
    _: usize,
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
    RESOLVER_THREADS.lock().unwrap().push(name);
    0 // keep the address as given
}

const IAM_USER: &str = "iam-user";

unsafe extern "C-unwind" fn static_credentials(
    _: usize,
    access_key_id: *mut u8,
    _: usize,
    access_key_id_len: *mut usize,
    secret_access_key: *mut u8,
    _: usize,
    secret_access_key_len: *mut usize,
    _: *mut u8,
    _: usize,
    session_token_len: *mut usize,
    _: *mut i64,
) -> u8 {
    unsafe {
        std::ptr::copy_nonoverlapping(b"AKID".as_ptr(), access_key_id, 4);
        *access_key_id_len = 4;
        std::ptr::copy_nonoverlapping(b"SECRET".as_ptr(), secret_access_key, 6);
        *secret_access_key_len = 6;
        *session_token_len = 0;
    }
    1 // success
}

struct Client(*const c_void);
unsafe impl Send for Client {}

fn create(request: ConnectionRequest, credential_provider: CredentialProviderCallback) -> Client {
    let request = request
        .write_to_bytes()
        .expect("serialize connection request");
    unsafe {
        let response = create_client_with_credential_provider(
            request.as_ptr(),
            request.len(),
            &ClientType::SyncClient,
            None,
            Some(record_resolver_thread),
            0,
            credential_provider,
        );
        assert!(
            (*response).connection_error_message.is_null(),
            "create_client failed"
        );
        let client = Client((*response).conn_ptr);
        free_connection_response(response as *mut ConnectionResponse);
        client
    }
}

fn outcome(result: *mut CommandResult) -> Result<(), String> {
    unsafe {
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

fn send(client: &Client, request_type: RequestType, args: &[&str]) -> Result<(), String> {
    let ptrs: Vec<usize> = args.iter().map(|arg| arg.as_ptr() as usize).collect();
    let lens: Vec<std::ffi::c_ulong> = args.iter().map(|arg| arg.len() as _).collect();
    outcome(unsafe {
        command(
            client.0,
            0,
            request_type,
            args.len() as _,
            ptrs.as_ptr(),
            lens.as_ptr(),
            std::ptr::null(),
            0,
            0,
        )
    })
}

fn ping(client: &Client) -> Result<(), String> {
    send(client, RequestType::Ping, &[])
}

/// Sends the first command from a musl-sized thread and asserts it connected off that thread.
fn assert_first_ping_connects_on_a_worker(client: Client) {
    assert!(
        RESOLVER_THREADS.lock().unwrap().is_empty(),
        "a lazy client connected before its first command"
    );

    let (client, result) = on_musl_sized_stack(FIRST_COMMAND_THREAD, move || {
        let result = ping(&client);
        (client, result)
    });
    unsafe { close_client(client.0) };
    assert_eq!(result, Ok(()), "first PING on a lazy TLS cluster client");

    let threads = RESOLVER_THREADS.lock().unwrap();
    assert!(!threads.is_empty(), "the first command did not connect");
    assert!(
        threads.iter().all(|name| name != FIRST_COMMAND_THREAD),
        "the connect ran on the caller's thread: {threads:?}"
    );
}

fn node() -> String {
    std::env::var(NODE_ENV)
        .unwrap_or_else(|_| panic!("run by its parent test, which sets {NODE_ENV}"))
}

#[test]
fn lazy_sync_client_connects_on_a_runtime_worker() {
    let cluster = TlsCluster::start();
    run_child(
        "child_sends_the_first_command_from_a_musl_sized_thread",
        &cluster.node,
        None,
    );
    run_child(
        "child_refreshes_the_iam_token_before_the_first_command",
        &cluster.node,
        None,
    );
}

#[test]
#[ignore = "child process of lazy_sync_client_connects_on_a_runtime_worker"]
fn child_sends_the_first_command_from_a_musl_sized_thread() {
    let mut request = request(&node(), TlsMode::SecureTls);
    request.lazy_connect = true;
    assert_first_ping_connects_on_a_worker(create(request, None));
}

/// Refreshing the IAM token does not connect, so it must not end the lazy client's worker routing.
#[test]
#[ignore = "child process of lazy_sync_client_connects_on_a_runtime_worker"]
fn child_refreshes_the_iam_token_before_the_first_command() {
    let node = node();
    // nopass accepts any password, so the IAM token signed from the fake credentials authenticates.
    let admin = create(request(&node, TlsMode::SecureTls), None);
    let setuser = [
        "ACL", "SETUSER", IAM_USER, "on", "nopass", "~*", "&*", "+@all",
    ];
    assert_eq!(send(&admin, RequestType::CustomCommand, &setuser), Ok(()));
    unsafe { close_client(admin.0) };
    RESOLVER_THREADS.lock().unwrap().clear();

    let mut request = request(&node, TlsMode::SecureTls);
    request.lazy_connect = true;
    let mut iam = IamCredentials::new();
    iam.cluster_name = "stack-test".into();
    iam.region = "us-east-1".into();
    let mut auth = AuthenticationInfo::new();
    auth.username = IAM_USER.into();
    auth.iam_credentials = Some(iam).into();
    request.authentication_info = Some(auth).into();
    let client = create(request, Some(static_credentials));

    assert_eq!(
        outcome(unsafe { refresh_iam_token(client.0, 0) }),
        Ok(()),
        "refresh_iam_token on a lazy IAM client"
    );
    assert_first_ping_connects_on_a_worker(client);
}
