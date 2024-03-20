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
  const char *connection_error_message;
} ConnectionResponse;

/**
 * Success callback that is called when a Redis command succeeds.
 *
 * `channel_address` is the address of the Go channel used by the callback to send the error message back to the caller of the command.
 * `message` is the value returned by the Redis command.
 */
typedef void (*SuccessCallback)(const void *channel_address, const char *message);

/**
 * Failure callback that is called when a Redis command fails.
 *
 * `channel_address` is the address of the Go channel used by the callback to send the error message back to the caller of the command.
 * `error_message` is the error message returned by Redis for the failed command. It should be manually freed after this callback is invoked, otherwise a memory leak will occur.
 * `error_type` is the type of error returned by glide-core, depending on the `RedisError` returned.
 */
typedef void (*FailureCallback)(const void *channel_address,
                                const char *error_message,
                                RequestErrorType error_type);

/**
 * Creates a new client with the given configuration. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
 *
 * The returned `ConnectionResponse` should be manually freed by calling `free_connection_response`, otherwise a memory leak will occur. It should be freed whether or not an error occurs.
 *
 * `connection_request_bytes` is an array of bytes that will be parsed into a Protobuf `ConnectionRequest` object.
 * `connection_request_len` is the number of bytes in `connection_request_bytes`.
 * `success_callback` is the callback that will be called in the case that the Redis command succeeds.
 * `failure_callback` is the callback that will be called in the case that the Redis command fails.
 *
 * # Safety
 *
 * * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes. It should be a well-formed Protobuf `ConnectionRequest` object. The array must be allocated on the Golang side and subsequently freed there too after this function returns.
 * * `connection_request_len` must not be greater than the length of the connection request bytes array. It must also not be greater than the max value of a signed pointer-sized integer.
 * * The `conn_ptr` pointer in the returned `ConnectionResponse` must live until it is passed into `close_client`.
 * * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to `free_connection_response`.
 * * Both the `success_callback` and `failure_callback` function pointers need to live until the client is closed via `close_client` since they are used when issuing Redis commands.
 */
const struct ConnectionResponse *create_client(const uint8_t *connection_request_bytes,
                                               uintptr_t connection_request_len,
                                               SuccessCallback success_callback,
                                               FailureCallback failure_callback);

/**
 * Closes the given client, deallocating it from the heap.
 *
 * `client_ptr` is a pointer to the client returned in the `ConnectionResponse` from `create_client`.
 *
 * # Safety
 *
 * * `client_ptr` must be obtained from the `ConnectionResponse` returned from `create_client`.
 * * `client_ptr` must be valid until `close_client` is called.
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
 * * `connection_response_ptr` must be obtained from the `ConnectionResponse` returned from `create_client`.
 * * `connection_response_ptr` must be valid until `free_connection_response` is called.
 * * `connection_response_ptr` must not be null.
 * * The contained `connection_error_message` must be obtained from the `ConnectionResponse` returned from `create_client`.
 * * The contained `connection_error_message` must be valid until `free_connection_response` is called and it must outlive the `ConnectionResponse` that contains it.
 * * The contained `connection_error_message` must not be null.
 */
void free_connection_response(struct ConnectionResponse *connection_response_ptr);

enum RequestType {
  CustomCommand = 1,
  GetString = 2,
  SetString = 3,
  Ping = 4,
  Info = 5,
  Del = 6,
  Select = 7,
  ConfigGet = 8,
  ConfigSet = 9,
  ConfigResetStat = 10,
  ConfigRewrite = 11,
  ClientGetName = 12,
  ClientGetRedir = 13,
  ClientId = 14,
  ClientInfo = 15,
  ClientKill = 16,
  ClientList = 17,
  ClientNoEvict = 18,
  ClientNoTouch = 19,
  ClientPause = 20,
  ClientReply = 21,
  ClientSetInfo = 22,
  ClientSetName = 23,
  ClientUnblock = 24,
  ClientUnpause = 25,
  Expire = 26,
  HashSet = 27,
  HashGet = 28,
  HashDel = 29,
  HashExists = 30,
  MGet = 31,
  MSet = 32,
  Incr = 33,
  IncrBy = 34,
  Decr = 35,
  IncrByFloat = 36,
  DecrBy = 37,
  HashGetAll = 38,
  HashMSet = 39,
  HashMGet = 40,
  HashIncrBy = 41,
  HashIncrByFloat = 42,
  LPush = 43,
  LPop = 44,
  RPush = 45,
  RPop = 46,
  LLen = 47,
  LRem = 48,
  LRange = 49,
  LTrim = 50,
  SAdd = 51,
  SRem = 52,
  SMembers = 53,
  SCard = 54,
  PExpireAt = 55,
  PExpire = 56,
  ExpireAt = 57,
  Exists = 58,
  Unlink = 59,
  TTL = 60,
  Zadd = 61,
  Zrem = 62,
  Zrange = 63,
  Zcard = 64,
  Zcount = 65,
  ZIncrBy = 66,
  ZScore = 67,
  Type = 68,
  HLen = 69,
  Echo = 70,
  ZPopMin = 71,
  Strlen = 72,
  Lindex = 73,
  ZPopMax = 74,
  XRead = 75,
  XAdd = 76,
  XReadGroup = 77,
  XAck = 78,
  XTrim = 79,
  XGroupCreate = 80,
  XGroupDestroy = 81,
};
typedef uint32_t RequestType;

void command(const void *client_ptr,
             uintptr_t channel,
             RequestType command_type,
             uintptr_t arg_count,
             const char *const *args);
