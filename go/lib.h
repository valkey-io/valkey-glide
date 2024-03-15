/*
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

typedef enum RequestErrorType {
  Unspecified = 0,
  ExecAbort = 1,
  Timeout = 2,
  Disconnect = 3,
} RequestErrorType;

/**
 * The connection response.
 *
 * It contains either a connection or an error. It is represented as a struct instead of an enum for ease of use in the wrapper language.
 *
 * This struct should be freed using `free_connection_response` to avoid memory leaks.
 */
typedef struct ConnectionResponse {
  const void *conn_ptr;
  const char *error_message;
  RequestErrorType error_type;
} ConnectionResponse;

/**
 * Success callback that is called when a Redis command succeeds.
 */
typedef void (*SuccessCallback)(uintptr_t channel_address, const char *message);

/**
 * Failure callback that is called when a Redis command fails.
 *
 * `error` should be manually freed after this callback is invoked, otherwise a memory leak will occur.
 */
typedef void (*FailureCallback)(uintptr_t channel_address,
                                const char *error_message,
                                RequestErrorType error_type);

/**
 * Creates a new client with the given configuration. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
 *
 * The returned `ConnectionResponse` should be manually freed by calling `free_connection_response`, otherwise a memory leak will occur. It should be freed whether or not an error occurs.
 *
 * # Safety
 *
 * * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes.
 * * `connection_request_len` must not be greater than `isize::MAX`. See the safety documentation of [`std::slice::from_raw_parts`](https://doc.rust-lang.org/std/slice/fn.from_raw_parts.html).
 */
const struct ConnectionResponse *create_client(const uint8_t *connection_request_bytes,
                                               uintptr_t connection_request_len,
                                               SuccessCallback success_callback,
                                               FailureCallback failure_callback);

/**
 * Closes the given client, deallocating it from the heap.
 *
 * # Safety
 *
 * * `client_ptr` must be able to be safely casted to a valid `Box<Client>` via `Box::from_raw`. See the safety documentation of [`std::boxed::Box::from_raw`](https://doc.rust-lang.org/std/boxed/struct.Box.html#method.from_raw).
 * * `client_ptr` must not be null.
 */
void close_client(const void *client_ptr);

/**
 * Deallocates a `ConnectionResponse`.
 *
 * This function also frees the contained error.
 *
 * # Safety
 *
 * * `connection_response_ptr` must be able to be safely casted to a valid `Box<ConnectionResponse>` via `Box::from_raw`. See the safety documentation of [`std::boxed::Box::from_raw`](https://doc.rust-lang.org/std/boxed/struct.Box.html#method.from_raw).
 * * `connection_response_ptr` must not be null.
 * * The contained `error_message` must be able to be safely casted to a valid `CString` via `CString::from_raw`. See the safety documentation of [`std::ffi::CString::from_raw`](https://doc.rust-lang.org/std/ffi/struct.CString.html#method.from_raw).
 * * The contained `error_message` must not be null.
 */
void free_connection_response(struct ConnectionResponse *connection_response_ptr);
