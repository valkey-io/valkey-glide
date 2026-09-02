// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
//
// void successCallback(uintptr_t requestID, struct CommandResponse *message);
// void failureCallback(uintptr_t requestID, char *errMessage, RequestErrorType errType);
import "C"

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"sync/atomic"
	"time"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
	"google.golang.org/protobuf/proto"
)

// IsolatedScope is a dedicated connection for operations requiring per-connection
// state: WATCH/MULTI/EXEC, CLIENT TRACKING, blocking commands.
//
// Commands bypass the multiplexer and execute on a single TCP connection.
// A single IsolatedScope is NOT goroutine-safe — each goroutine should acquire
// its own scope.
//
// Use client.ScopedConnection() to acquire a scope, and defer scope.Close().
type IsolatedScope struct {
	scopeID  int64
	clientID uint64
	released bool
	ownerGID int64       // goroutine ID that acquired the scope (-1 = untracked)
	inFlight atomic.Bool // true while a command is executing — detects concurrent use
}

// ScopeID returns the scope handle.
func (s *IsolatedScope) ScopeID() int64 {
	return s.scopeID
}

// IsReleased returns whether the scope has been returned to the pool.
func (s *IsolatedScope) IsReleased() bool {
	return s.released
}

// Watch one or more keys for optimistic locking.
func (s *IsolatedScope) Watch(ctx context.Context, keys ...string) (string, error) {
	return s.cmd(ctx, "WATCH", keys...)
}

// Unwatch discards all watched keys.
func (s *IsolatedScope) Unwatch(ctx context.Context) (string, error) {
	return s.cmd(ctx, "UNWATCH")
}

// Multi begins a transaction block.
func (s *IsolatedScope) Multi(ctx context.Context) (string, error) {
	return s.cmd(ctx, "MULTI")
}

// Exec executes the transaction. Returns empty string if WATCH detected a conflict (nil EXEC).
func (s *IsolatedScope) Exec(ctx context.Context) (string, error) {
	return s.cmd(ctx, "EXEC")
}

// Discard discards the transaction.
func (s *IsolatedScope) Discard(ctx context.Context) (string, error) {
	return s.cmd(ctx, "DISCARD")
}

// Get retrieves the value of a key.
func (s *IsolatedScope) Get(ctx context.Context, key string) (string, error) {
	return s.cmd(ctx, "GET", key)
}

// Set sets a key to a value.
func (s *IsolatedScope) Set(ctx context.Context, key string, value string) (string, error) {
	return s.cmd(ctx, "SET", key, value)
}

// Incr increments the integer value of a key by 1.
func (s *IsolatedScope) Incr(ctx context.Context, key string) (string, error) {
	return s.cmd(ctx, "INCR", key)
}

// Ping sends a PING command.
func (s *IsolatedScope) Ping(ctx context.Context) (string, error) {
	return s.cmd(ctx, "PING")
}

// Select selects a database by index.
func (s *IsolatedScope) Select(ctx context.Context, db int) (string, error) {
	return s.cmd(ctx, "SELECT", fmt.Sprintf("%d", db))
}

// ExecuteCommand executes an arbitrary command on this scope.
func (s *IsolatedScope) ExecuteCommand(ctx context.Context, command string, args ...string) (string, error) {
	return s.cmd(ctx, command, args...)
}

// Close releases the scope back to the pool.
func (s *IsolatedScope) Close() {
	if !s.released {
		s.released = true
		C.glide_scope_release(C.uint64_t(s.scopeID), C.uint64_t(s.clientID))
	}
}

// cmd serializes and executes a command on the scoped connection.
func (s *IsolatedScope) cmd(ctx context.Context, command string, args ...string) (string, error) {
	if s.released {
		return "", errors.New("scope already released")
	}

	// Detect concurrent usage — scopes are NOT goroutine-safe
	if !s.inFlight.CompareAndSwap(false, true) {
		panic("glide: IsolatedScope used concurrently from multiple goroutines — each goroutine must acquire its own scope")
	}
	defer s.inFlight.Store(false)

	select {
	case <-ctx.Done():
		return "", ctx.Err()
	default:
	}

	// Serialize in wire format: [4:cmd_len][cmd][4:num_args][4:arg_len][arg]...
	cmdBytes := []byte(command)
	size := 4 + len(cmdBytes) + 4
	for _, arg := range args {
		size += 4 + len(arg)
	}

	wireData := make([]byte, 0, size)
	wireData = binary.LittleEndian.AppendUint32(wireData, uint32(len(cmdBytes)))
	wireData = append(wireData, cmdBytes...)
	wireData = binary.LittleEndian.AppendUint32(wireData, uint32(len(args)))
	for _, arg := range args {
		argBytes := []byte(arg)
		wireData = binary.LittleEndian.AppendUint32(wireData, uint32(len(argBytes)))
		wireData = append(wireData, argBytes...)
	}

	// Use async callback pattern (same as regular commands) — no thread blocking
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)

	rc := C.glide_scope_execute_async(
		C.uint64_t(s.scopeID),
		(*C.uint8_t)(unsafe.Pointer(&wireData[0])),
		C.uintptr_t(len(wireData)),
		C.uintptr_t(requestID),
		C.SuccessCallback(unsafe.Pointer(C.successCallback)),
		C.FailureCallback(unsafe.Pointer(C.failureCallback)),
	)

	if rc == -1 {
		takeRequest(requestID)
		return "", fmt.Errorf("scope execute failed: invalid scope %d", s.scopeID)
	}
	if rc == -2 {
		takeRequest(requestID)
		return "", errors.New("scope execute failed: invalid command")
	}

	// Wait for result or context cancellation
	var result payload
	select {
	case <-ctx.Done():
		if _, claimed := takeRequest(requestID); !claimed {
			go discardResponse(resultChannel)
		}
		return "", ctx.Err()
	case result = <-resultChannel:
	}

	if result.error != nil {
		return "", fmt.Errorf("scope command error: %s", result.error.Error())
	}

	return parseScopeResponse(result.value), nil
}

// parseScopeResponse converts a CommandResponse to a Go string.
func parseScopeResponse(resp *C.struct_CommandResponse) string {
	if resp == nil {
		return ""
	}
	defer C.free_command_response(resp)

	switch resp.response_type {
	case 0: // Null
		return ""
	case 1: // Int
		return fmt.Sprintf("%d", resp.int_value)
	case 2: // Float
		return fmt.Sprintf("%f", resp.float_value)
	case 3: // Bool
		if resp.bool_value {
			return "true"
		}
		return "false"
	case 4: // String
		if resp.string_value == nil {
			return ""
		}
		return C.GoStringN(resp.string_value, C.int(resp.string_value_len))
	case 5: // Array
		if resp.array_value == nil || resp.array_value_len == 0 {
			return ""
		}
		return "[array]" // Simplified for scope usage
	case 8: // Ok
		return "OK"
	case 9: // Error
		if resp.string_value != nil {
			return C.GoString(resp.string_value)
		}
		return "error"
	default:
		return ""
	}
}

// ScopedConnection acquires an isolated execution scope from the client.
//
// The scope provides a dedicated connection for WATCH/MULTI/EXEC, CLIENT TRACKING,
// and other operations that require per-connection state.
//
// Usage:
//
//	scope, err := client.ScopedConnection(ctx, 5*time.Second)
//	if err != nil { ... }
//	defer scope.Close()
//
//	scope.Watch(ctx, "counter")
//	val, _ := scope.Get(ctx, "counter")
//	scope.Multi(ctx)
//	scope.Set(ctx, "counter", incrementedVal)
//	scope.Exec(ctx)
func (client *Client) ScopedConnection(ctx context.Context, timeout time.Duration, routingKey string) (*IsolatedScope, error) {
	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, errors.New("client is closed")
	}
	clientID := uint64(uintptr(client.coreClient))
	client.mu.Unlock()

	// Use pre-serialized connection request bytes if available (pool-borrowed clients),
	// otherwise serialize from the client configuration.
	var connReqBytes []byte
	if client.connReqBytes != nil {
		connReqBytes = client.connReqBytes
	} else {
		request, err := client.getConnectionRequest()
		if err != nil {
			return nil, err
		}
		connReqBytes, err = proto.Marshal(request)
		if err != nil {
			return nil, err
		}
	}

	routingSlot := uint16(0)
	if routingKey != "" {
		routingSlot = slotForKey([]byte(routingKey))
	}

	deadline := time.Now().Add(timeout)
	backoff := 10 * time.Millisecond

	for {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		default:
		}

		scopeID := C.glide_scope_try_acquire(
			C.uint64_t(clientID),
			(*C.uint8_t)(unsafe.Pointer(&connReqBytes[0])),
			C.uintptr_t(len(connReqBytes)),
			C.uint16_t(routingSlot),
		)

		if scopeID >= 0 {
			return &IsolatedScope{
				scopeID:  int64(scopeID),
				clientID: clientID,
			}, nil
		}

		remaining := time.Until(deadline)
		if remaining <= 0 {
			return nil, errors.New("timed out waiting for isolated scope (pool exhausted)")
		}

		sleep := backoff
		if sleep > remaining {
			sleep = remaining
		}
		time.Sleep(sleep)
		backoff *= 2
		if backoff > 500*time.Millisecond {
			backoff = 500 * time.Millisecond
		}
	}
}

// getConnectionRequest returns the protobuf ConnectionRequest for this client's config.
// This is needed by scoped connections to create new TCP connections.
func (client *Client) getConnectionRequest() (*protobuf.ConnectionRequest, error) {
	if client.clientConfig == nil {
		return nil, errors.New("client configuration not available for scoped connections")
	}
	return client.clientConfig.ToProtobuf()
}

// ScopedConnection acquires an isolated execution scope from the cluster client.
//
// The scope provides a dedicated connection for WATCH/MULTI/EXEC, CLIENT TRACKING,
// and other operations that require per-connection state.
//
// Usage:
//
//	scope, err := clusterClient.ScopedConnection(ctx, 5*time.Second)
//	if err != nil { ... }
//	defer scope.Close()
//
//	scope.Watch(ctx, "counter")
//	val, _ := scope.Get(ctx, "counter")
//	scope.Multi(ctx)
//	scope.Set(ctx, "counter", incrementedVal)
//	scope.Exec(ctx)
func (client *ClusterClient) ScopedConnection(
	ctx context.Context,
	timeout time.Duration,
	routingKey string,
) (*IsolatedScope, error) {
	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, errors.New("client is closed")
	}
	clientID := uint64(uintptr(client.coreClient))
	client.mu.Unlock()

	// Use pre-serialized connection request bytes if available (pool-borrowed clients),
	// otherwise serialize from the client configuration.
	var connReqBytes []byte
	if client.connReqBytes != nil {
		connReqBytes = client.connReqBytes
	} else {
		request, err := client.getConnectionRequest()
		if err != nil {
			return nil, err
		}
		connReqBytes, err = proto.Marshal(request)
		if err != nil {
			return nil, err
		}
	}

	routingSlot := uint16(0)
	if routingKey != "" {
		routingSlot = slotForKey([]byte(routingKey))
	}
	deadline := time.Now().Add(timeout)
	backoff := 10 * time.Millisecond

	for {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		default:
		}

		scopeID := C.glide_scope_try_acquire(
			C.uint64_t(clientID),
			(*C.uint8_t)(unsafe.Pointer(&connReqBytes[0])),
			C.uintptr_t(len(connReqBytes)),
			C.uint16_t(routingSlot),
		)

		if scopeID >= 0 {
			return &IsolatedScope{
				scopeID:  int64(scopeID),
				clientID: clientID,
			}, nil
		}

		remaining := time.Until(deadline)
		if remaining <= 0 {
			return nil, errors.New("timed out waiting for isolated scope (pool exhausted)")
		}

		sleep := backoff
		if sleep > remaining {
			sleep = remaining
		}
		time.Sleep(sleep)
		backoff *= 2
		if backoff > 500*time.Millisecond {
			backoff = 500 * time.Millisecond
		}
	}
}

// getConnectionRequest returns the protobuf ConnectionRequest for this cluster client's config.
// This is needed by scoped connections to create new TCP connections.
func (client *ClusterClient) getConnectionRequest() (*protobuf.ConnectionRequest, error) {
	if client.clientConfig == nil {
		return nil, errors.New("client configuration not available for scoped connections")
	}
	return client.clientConfig.ToProtobuf()
}

// slotForKey computes the Redis cluster hash slot for a key (CRC16 mod 16384).
// Handles hash tags: if the key contains {...}, only the content between the
// first { and first } is hashed.
func slotForKey(key []byte) uint16 {
	start := -1
	for i, b := range key {
		if b == '{' {
			start = i
			break
		}
	}
	if start != -1 {
		for i := start + 1; i < len(key); i++ {
			if key[i] == '}' && i != start+1 {
				key = key[start+1 : i]
				break
			}
		}
	}

	crc := uint16(0)
	for _, b := range key {
		crc ^= uint16(b) << 8
		for j := 0; j < 8; j++ {
			if crc&0x8000 != 0 {
				crc = (crc << 1) ^ 0x1021
			} else {
				crc <<= 1
			}
		}
	}
	return crc % 16384
}
