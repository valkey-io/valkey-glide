// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#![cfg(target_os = "linux")]

use glide_core::connection_request::{ConnectionRequest, NodeAddress};
use protobuf::Message;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

fn run(command: &mut Command, description: &str) {
    let output = command.output().unwrap_or_else(|error| {
        panic!("failed to run {description}: {error}");
    });
    assert!(
        output.status.success(),
        "{description} failed\nstdout:\n{}\nstderr:\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn debug_library_dir(manifest_dir: &Path) -> PathBuf {
    manifest_dir.join("target").join("debug")
}

#[test]
fn generated_header_preserves_legacy_abi_and_adds_provider_suffix() {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let temp_dir = manifest_dir
        .join("target")
        .join("tmp")
        .join(format!("ffi-abi-{}", std::process::id()));
    let _ = fs::remove_dir_all(&temp_dir);

    run(
        Command::new("cargo")
            .current_dir(&manifest_dir)
            .args(["build", "--lib"]),
        "cargo build --lib for ABI caller",
    );
    fs::create_dir_all(&temp_dir).unwrap();

    let header = temp_dir.join("glide.h");
    run(
        Command::new("cbindgen")
            .current_dir(&manifest_dir)
            .args([
                "--config",
                "cbindgen.toml",
                "--crate",
                "glide-ffi",
                "--output",
            ])
            .arg(&header)
            .args(["--lang", "c"]),
        "cbindgen",
    );

    let mut request = ConnectionRequest::new();
    request.lazy_connect = true;
    let mut address = NodeAddress::new();
    address.host = "127.0.0.1".into();
    address.port = 1;
    request.addresses.push(address);
    let request = request.write_to_bytes().unwrap();
    let request_literal = request
        .iter()
        .map(u8::to_string)
        .collect::<Vec<_>>()
        .join(", ");

    let source = format!(
        r#"#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "glide.h"

/* Byte-for-byte copy of the latest-main sibling declaration. A compatible
 * redeclaration proves existing six-argument callers still compile. */
const ConnectionResponse* create_client(
    const uint8_t* connection_request_bytes,
    size_t connection_request_len,
    const ClientType* client_type,
    PubSubCallback pubsub_callback,
    AddressResolverCallback address_resolver,
    uintptr_t client_id
);

static unsigned pubsub_calls = 0;
static void pubsub(
    uintptr_t client_ptr,
    enum PushKind kind,
    const uint8_t *message, int64_t message_len,
    const uint8_t *channel, int64_t channel_len,
    const uint8_t *pattern, int64_t pattern_len
) {{
    (void)client_ptr;
    (void)kind;
    (void)message;
    (void)message_len;
    (void)channel;
    (void)channel_len;
    (void)pattern;
    (void)pattern_len;
    pubsub_calls++;
}}

static uint16_t resolver(
    uintptr_t client_id,
    const uint8_t *host, uintptr_t host_len,
    uint16_t port,
    uint8_t *resolved_host_buf, uintptr_t resolved_host_buf_len,
    uintptr_t *resolved_host_len
) {{
    (void)client_id;
    (void)host;
    (void)host_len;
    (void)port;
    (void)resolved_host_buf;
    (void)resolved_host_buf_len;
    *resolved_host_len = 0;
    return 0;
}}

static unsigned provider_calls = 0;
static uint8_t provider(
    uintptr_t client_id,
    uint8_t *access_key_id_buf, size_t access_key_id_buf_len, size_t *access_key_id_len,
    uint8_t *secret_access_key_buf, size_t secret_access_key_buf_len, size_t *secret_access_key_len,
    uint8_t *session_token_buf, size_t session_token_buf_len, size_t *session_token_len,
    int64_t *expires_at_epoch_millis
) {{
    (void)client_id;
    (void)access_key_id_buf;
    (void)access_key_id_buf_len;
    (void)access_key_id_len;
    (void)secret_access_key_buf;
    (void)secret_access_key_buf_len;
    (void)secret_access_key_len;
    (void)session_token_buf;
    (void)session_token_buf_len;
    (void)session_token_len;
    (void)expires_at_epoch_millis;
    provider_calls++;
    return 0;
}}

static int expect_client(const ConnectionResponse *response) {{
    if (response == NULL || response->conn_ptr == NULL || response->connection_error_message != NULL) {{
        return 0;
    }}
    const void *client = response->conn_ptr;
    free_connection_response((ConnectionResponse *)response);
    close_client(client);
    return 1;
}}

int main(void) {{
    const uint8_t request[] = {{ {request_literal} }};
    ClientType client_type = {{ .tag = SyncClient }};

    /* The nonzero sixth argument must remain client_id, never a provider pointer. */
    if (!expect_client(create_client(request, sizeof(request), &client_type, pubsub, resolver, 0x12345))) {{
        return 10;
    }}

    if (!expect_client(create_client_with_credential_provider(
            request, sizeof(request), &client_type, pubsub, resolver, 0x23456, NULL))) {{
        return 11;
    }}

    const ConnectionResponse *failed = create_client_with_credential_provider(
        request, sizeof(request), &client_type, NULL, NULL, 0x34567, provider);
    if (failed == NULL || failed->conn_ptr != NULL || failed->connection_error_message == NULL) {{
        return 12;
    }}
    if (strstr(failed->connection_error_message, "no IAM configuration") == NULL) {{
        return 13;
    }}
    if (provider_calls != 0) {{
        return 14;
    }}
    free_connection_response((ConnectionResponse *)failed);
    return 0;
}}
"#
    );
    let source_path = temp_dir.join("caller.c");
    fs::write(&source_path, source).unwrap();
    let executable = temp_dir.join("caller");
    let library_dir = debug_library_dir(&manifest_dir);
    assert!(
        library_dir.join("libglide_ffi.so").is_file(),
        "cargo did not produce the FFI cdylib in {}",
        library_dir.display()
    );
    run(
        Command::new("cc")
            .args(["-std=c11", "-Wall", "-Wextra", "-Werror", "-I"])
            .arg(&temp_dir)
            .arg(&source_path)
            .arg("-L")
            .arg(&library_dir)
            .arg("-lglide_ffi")
            .arg(format!("-Wl,-rpath,{}", library_dir.display()))
            .arg("-o")
            .arg(&executable),
        "legacy/additive C caller compilation",
    );
    run(&mut Command::new(&executable), "legacy/additive C caller");

    fs::remove_dir_all(temp_dir).unwrap();
}
