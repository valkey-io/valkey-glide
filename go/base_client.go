// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #cgo LDFLAGS: -lglide_ffi
// #cgo !windows LDFLAGS: -lm
// #cgo darwin LDFLAGS: -framework Security
// #cgo darwin,amd64 LDFLAGS: -framework CoreFoundation
// #cgo linux,amd64,!musl LDFLAGS: -L${SRCDIR}/rustbin/x86_64-unknown-linux-gnu
// #cgo linux,amd64,musl LDFLAGS: -L${SRCDIR}/rustbin/x86_64-unknown-linux-musl
// #cgo linux,arm64,!musl LDFLAGS: -L${SRCDIR}/rustbin/aarch64-unknown-linux-gnu
// #cgo linux,arm64,musl LDFLAGS: -L${SRCDIR}/rustbin/aarch64-unknown-linux-musl
// #cgo darwin,arm64 LDFLAGS: -L${SRCDIR}/rustbin/aarch64-apple-darwin
// #cgo darwin,amd64 LDFLAGS: -L${SRCDIR}/rustbin/x86_64-apple-darwin
// #include "lib.h"
//
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
// void pubSubCallback(void *clientPtr, enum PushKind kind,
//                     const uint8_t *message, int64_t message_len,
//                     const uint8_t *channel, int64_t channel_len,
//                     const uint8_t *pattern, int64_t pattern_len);
import "C"

import (
	"context"
	"errors"
	"fmt"
	"math"
	"strconv"
	"sync"
	"time"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal"
	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"google.golang.org/protobuf/proto"
)

const OK = "OK"

type payload struct {
	value *C.struct_CommandResponse
	error error
}

type clientConfiguration interface {
	ToProtobuf() (*protobuf.ConnectionRequest, error)
}

type baseClient struct {
	pending        map[unsafe.Pointer]struct{}
	coreClient     unsafe.Pointer
	mu             *sync.Mutex
	messageHandler *MessageHandler
}

// setMessageHandler assigns a message handler to the client for processing pub/sub messages
func (client *baseClient) setMessageHandler(handler *MessageHandler) {
	client.messageHandler = handler
}

// getMessageHandler returns the currently assigned message handler
func (client *baseClient) getMessageHandler() *MessageHandler {
	return client.messageHandler
}

// GetQueue returns the pub/sub queue for the client.
// This method is only available for clients that have a subscription,
// and returns an error if the client does not have a subscription.
func (client *baseClient) GetQueue() (*PubSubMessageQueue, error) {
	// MessageHandler is only configured when a subscription is defined
	if client.getMessageHandler() == nil {
		return nil, errors.New("no subscriptions configured for this client")
	}
	return client.getMessageHandler().GetQueue(), nil
}

// buildAsyncClientType safely initializes a C.ClientType with an AsyncClient_Body.
//
// It manually writes into the union field of the following C layout:
//
//	typedef struct ClientType {
//	    ClientType_Tag tag;
//	    union {
//	        AsyncClient_Body async_client;
//	    };
//	};
//
// Since cgo doesn’t support C unions directly, this is exposed in Go as:
//
//	type _Ctype_ClientType struct {
//	    tag   _Ctype_ClientType_Tag
//	    _     [4]uint8       // padding/alignment
//	    anon0 [16]uint8      // raw bytes of the union
//	}
//
// This function verifies that AsyncClient_Body fits in the union's underlying memory (anon0),
// and writes it using unsafe.Pointer.
//
// # Returns
// A fully initialized C.ClientType struct, or an error if layout validation fails.
func buildAsyncClientType(successCb C.SuccessCallback, failureCb C.FailureCallback) (C.ClientType, error) {
	var clientType C.ClientType
	clientType.tag = C.AsyncClient

	asyncBody := C.AsyncClient_Body{
		success_callback: successCb,
		failure_callback: failureCb,
	}

	// Validate that AsyncClient_Body fits in the union's allocated memory.
	if unsafe.Sizeof(C.AsyncClient_Body{}) > unsafe.Sizeof(clientType.anon0) {
		return clientType, fmt.Errorf(
			"internal client error: AsyncClient_Body size (%d bytes) exceeds union field size (%d bytes)",
			unsafe.Sizeof(C.AsyncClient_Body{}),
			unsafe.Sizeof(clientType.anon0),
		)
	}

	// Write asyncBody into the union using unsafe casting.
	anonPtr := unsafe.Pointer(&clientType.anon0[0])
	*(*C.AsyncClient_Body)(anonPtr) = asyncBody

	return clientType, nil
}

// Creates a connection by invoking the `create_client` function from Rust library via FFI.
// Passes the pointers to callback functions which will be invoked when the command succeeds or fails.
// Once the connection is established, this function invokes `free_connection_response` exposed by rust library to free the
// connection_response to avoid any memory leaks.
func createClient(config clientConfiguration) (*baseClient, error) {
	request, err := config.ToProtobuf()
	if err != nil {
		return nil, err
	}
	msg, err := proto.Marshal(request)
	if err != nil {
		return nil, err
	}

	byteCount := len(msg)
	requestBytes := C.CBytes(msg)
	defer C.free(requestBytes)

	clientType, err := buildAsyncClientType(
		(C.SuccessCallback)(unsafe.Pointer(C.successCallback)),
		(C.FailureCallback)(unsafe.Pointer(C.failureCallback)),
	)
	if err != nil {
		return nil, NewClosingError(err.Error())
	}
	client := &baseClient{pending: make(map[unsafe.Pointer]struct{}), mu: &sync.Mutex{}}

	cResponse := (*C.struct_ConnectionResponse)(
		C.create_client(
			(*C.uchar)(requestBytes),
			C.uintptr_t(byteCount),
			&clientType,
			(C.PubSubCallback)(unsafe.Pointer(C.pubSubCallback)),
		),
	)
	defer C.free_connection_response(cResponse)
	cErr := cResponse.connection_error_message
	if cErr != nil {
		message := C.GoString(cErr)
		return nil, NewConnectionError(message)
	}

	client.coreClient = cResponse.conn_ptr

	// Register the client in our registry using the pointer value from C
	registerClient(client, uintptr(cResponse.conn_ptr))

	return client, nil
}

// Close terminates the client by closing all associated resources.
func (client *baseClient) Close() {
	client.mu.Lock()
	defer client.mu.Unlock()

	if client.coreClient == nil {
		return
	}

	unregisterClient(uintptr(client.coreClient))

	C.close_client(client.coreClient)
	client.coreClient = nil

	// iterating the channel map while holding the lock guarantees those unsafe.Pointers is still valid
	// because holding the lock guarantees the owner of the unsafe.Pointer hasn't exit.
	for channelPtr := range client.pending {
		resultChannel := *(*chan payload)(channelPtr)
		resultChannel <- payload{value: nil, error: NewClosingError("ExecuteCommand failed: the client is closed")}
	}
	client.pending = nil
}

func (client *baseClient) executeCommand(
	ctx context.Context,
	requestType C.RequestType,
	args []string,
) (*C.struct_CommandResponse, error) {
	return client.executeCommandWithRoute(ctx, requestType, args, nil)
}

func slotTypeToProtobuf(slotType config.SlotType) (protobuf.SlotTypes, error) {
	switch slotType {
	case config.SlotTypePrimary:
		return protobuf.SlotTypes_Primary, nil
	case config.SlotTypeReplica:
		return protobuf.SlotTypes_Replica, nil
	default:
		return protobuf.SlotTypes_Primary, errors.New("invalid slot type")
	}
}

func routeToProtobuf(route config.Route) (*protobuf.Routes, error) {
	switch route := route.(type) {
	// enum variants have the same ordinals
	case config.SimpleNodeRoute:
		return &protobuf.Routes{Value: &protobuf.Routes_SimpleRoutes{SimpleRoutes: protobuf.SimpleRoutes(route)}}, nil
	case config.SimpleMultiNodeRoute:
		return &protobuf.Routes{Value: &protobuf.Routes_SimpleRoutes{SimpleRoutes: protobuf.SimpleRoutes(route)}}, nil
	case config.SimpleSingleNodeRoute:
		return &protobuf.Routes{Value: &protobuf.Routes_SimpleRoutes{SimpleRoutes: protobuf.SimpleRoutes(route)}}, nil
	case *config.SlotIdRoute:
		{
			slotType, err := slotTypeToProtobuf(route.SlotType)
			if err != nil {
				return nil, err
			}
			return &protobuf.Routes{
				Value: &protobuf.Routes_SlotIdRoute{
					SlotIdRoute: &protobuf.SlotIdRoute{
						SlotType: slotType,
						SlotId:   route.SlotID,
					},
				},
			}, nil
		}
	case *config.SlotKeyRoute:
		{
			slotType, err := slotTypeToProtobuf(route.SlotType)
			if err != nil {
				return nil, err
			}
			return &protobuf.Routes{
				Value: &protobuf.Routes_SlotKeyRoute{
					SlotKeyRoute: &protobuf.SlotKeyRoute{
						SlotType: slotType,
						SlotKey:  route.SlotKey,
					},
				},
			}, nil
		}
	case *config.ByAddressRoute:
		{
			return &protobuf.Routes{
				Value: &protobuf.Routes_ByAddressRoute{
					ByAddressRoute: &protobuf.ByAddressRoute{
						Host: route.Host,
						Port: route.Port,
					},
				},
			}, nil
		}
	default:
		return nil, errors.New("invalid route type")
	}
}

func (client *baseClient) executeCommandWithRoute(
	ctx context.Context,
	requestType C.RequestType,
	args []string,
	route config.Route,
) (*C.struct_CommandResponse, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
		// Continue with execution
	}
	// Create span if OpenTelemetry is enabled and sampling is configured
	var spanPtr uint64
	otelInstance := GetOtelInstance()
	if otelInstance != nil && otelInstance.shouldSample() {
		// Check if there's a parent span in the context
		if parentSpanPtr := otelInstance.extractSpanPointer(ctx); parentSpanPtr != 0 {
			// Create child span with parent
			spanPtr = otelInstance.createSpanWithParent(requestType, parentSpanPtr)
		} else {
			// Create independent span (current behavior)
			spanPtr = otelInstance.createSpan(requestType)
		}
		defer otelInstance.dropSpan(spanPtr)
	}
	var cArgsPtr *C.uintptr_t = nil
	var argLengthsPtr *C.ulong = nil
	if len(args) > 0 {
		cArgs, argLengths := toCStrings(args)
		cArgsPtr = &cArgs[0]
		argLengthsPtr = &argLengths[0]
	}
	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.uintptr_t = 0
	if route != nil {
		routeProto, err := routeToProtobuf(route)
		if err != nil {
			return nil, errors.New("executeCommand failed due to invalid route")
		}
		msg, err := proto.Marshal(routeProto)
		if err != nil {
			return nil, err
		}

		routeBytesCount = C.uintptr_t(len(msg))
		routeCBytes := C.CBytes(msg)
		defer C.free(routeCBytes)
		routeBytesPtr = (*C.uchar)(routeCBytes)
	}
	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, NewClosingError("executeCommand failed: the client is closed")
	}
	client.pending[resultChannelPtr] = struct{}{}
	C.command(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		uint32(requestType),
		C.size_t(len(args)),
		cArgsPtr,
		argLengthsPtr,
		routeBytesPtr,
		routeBytesCount,
		C.uint64_t(spanPtr),
	)
	client.mu.Unlock()
	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return nil, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}
	return payload.value, nil
}

// Zero copying conversion from go's []string into C pointers
func toCStrings(args []string) ([]C.uintptr_t, []C.ulong) {
	cStrings := make([]C.uintptr_t, len(args))
	stringLengths := make([]C.ulong, len(args))
	for i, str := range args {
		bytes := utils.StringToBytes(str)
		var ptr uintptr
		if len(str) > 0 {
			ptr = uintptr(unsafe.Pointer(&bytes[0]))
		}
		cStrings[i] = C.uintptr_t(ptr)
		stringLengths[i] = C.size_t(len(str))
	}
	return cStrings, stringLengths
}

func (client *baseClient) executeBatch(
	ctx context.Context,
	batch internal.Batch,
	raiseOnError bool,
	options *internal.BatchOptions,
) ([]any, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
		// Continue with execution
	}
	if len(batch.Errors) > 0 {
		return nil, NewBatchError(batch.Errors)
	}

	// Create span if OpenTelemetry is enabled and sampling is configured
	var spanPtr uint64
	otelInstance := GetOtelInstance()
	if otelInstance != nil && otelInstance.shouldSample() {
		// Check if there's a parent span in the context
		if parentSpanPtr := otelInstance.extractSpanPointer(ctx); parentSpanPtr != 0 {
			// Create child batch span with parent
			// Since we don't have create_batch_otel_span_with_parent, we create a named child span
			// using the parent span pointer to establish the parent-child relationship
			spanPtr = otelInstance.createBatchSpanWithParent(parentSpanPtr)
		} else {
			// Create independent batch span
			spanPtr = otelInstance.createBatchSpan()
		}
		defer otelInstance.dropSpan(spanPtr)
	}

	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, NewClosingError("ExecuteBatch failed. The client is closed.")
	}
	client.pending[resultChannelPtr] = struct{}{}

	batchInfo := createBatchInfo(pinner, batch)
	var optionsPtr *C.BatchOptionsInfo
	if options != nil {
		batchOptionsInfo := createBatchOptionsInfo(pinner, *options)
		optionsPtr = &batchOptionsInfo
	}

	C.batch(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		&batchInfo,
		C._Bool(raiseOnError),
		optionsPtr,
		C.uint64_t(spanPtr),
	)
	client.mu.Unlock()

	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return nil, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}
	response, err := handleAnyArrayOrNilResponse(payload.value)
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, nil
	}
	return batch.Convert(response)
}

func createBatchOptionsInfo(pinner pinner, options internal.BatchOptions) C.BatchOptionsInfo {
	info := C.BatchOptionsInfo{}
	info.retry_server_error = C._Bool(false)
	info.retry_connection_error = C._Bool(false)
	if options.RetryServerError != nil {
		info.retry_server_error = C._Bool(*options.RetryServerError)
	}
	if options.RetryConnectionError != nil {
		info.retry_connection_error = C._Bool(*options.RetryConnectionError)
	}
	if options.Timeout != nil {
		info.has_timeout = C._Bool(true)
		info.timeout = C.uint(*options.Timeout)
	} else {
		info.has_timeout = C._Bool(false)
	}
	if options.Route != nil {
		info.route_info = (*C.RouteInfo)(pinner.Pin(unsafe.Pointer(createRouteInfo(pinner, options.Route))))
	} else {
		info.route_info = nil
	}
	return info
}

// TODO align with others to return struct, not a pointer
func createRouteInfo(pinner pinner, route config.Route) *C.RouteInfo {
	if route != nil {
		routeInfo := C.RouteInfo{}
		switch r := route.(type) {
		case config.SimpleSingleNodeRoute:
			routeInfo.route_type = (uint32)(r)
		case config.SimpleMultiNodeRoute:
			routeInfo.route_type = (uint32)(r)
		case config.SimpleNodeRoute:
			// enum variants have the same ordinals
			routeInfo.route_type = (uint32)(r)
		case *config.SlotIdRoute:
			routeInfo.route_type = C.SlotId
			routeInfo.slot_id = C.int(r.SlotID)
			// enum variants have the same ordinals
			routeInfo.slot_type = uint32(r.SlotType)
		case *config.SlotKeyRoute:
			routeInfo.route_type = C.SlotKey
			// when converting string to []byte, it is converted to an UTF8 string (not a binary string)
			routeInfo.hostname = (*C.char)(pinner.Pin(unsafe.Pointer(&[]byte(r.SlotKey)[0])))
			// enum variants have the same ordinals
			routeInfo.slot_type = uint32(r.SlotType)
		case *config.ByAddressRoute:
			routeInfo.route_type = C.ByAddress
			// when converting string to []byte, it is converted to an UTF8 string (not a binary string)
			routeInfo.hostname = (*C.char)(pinner.Pin(unsafe.Pointer(&[]byte(r.Host)[0])))
			routeInfo.port = C.int(r.Port)
		}
		return &routeInfo
	}
	return nil
}

func createBatchInfo(pinner pinner, batch internal.Batch) C.BatchInfo {
	numCommands := len(batch.Commands)
	info := C.BatchInfo{}
	info.is_atomic = C._Bool(batch.IsAtomic)
	info.cmd_count = C.ulong(numCommands)

	cmdPtrs := make([]*C.CmdInfo, numCommands)

	for i, cmd := range batch.Commands {
		cmdInfo := createCmdInfo(pinner, cmd)
		cmdPtrs[i] = (*C.CmdInfo)(pinner.Pin(unsafe.Pointer(&cmdInfo)))
	}

	if numCommands > 0 {
		info.cmds = (**C.CmdInfo)(pinner.Pin(unsafe.Pointer(&cmdPtrs[0])))
	}

	return info
}

func createCmdInfo(pinner pinner, cmd internal.Cmd) C.CmdInfo {
	numArgs := len(cmd.Args)
	info := C.CmdInfo{}
	info.request_type = cmd.RequestType
	cArgsPtr := make([]*C.uchar, numArgs)
	argLengthsPtr := make([]C.ulong, numArgs)
	for i, str := range cmd.Args {
		// TODO do we need to pin there too?
		// cArgsPtr[i] = (*C.uchar)(pinner.Pin(unsafe.Pointer(unsafe.StringData((str)))))
		cArgsPtr[i] = (*C.uchar)(unsafe.Pointer(unsafe.StringData((str))))
		argLengthsPtr[i] = C.size_t(len(str))
	}
	info.arg_count = C.ulong(numArgs)
	if numArgs > 0 {
		info.args = (**C.uchar)(pinner.Pin(unsafe.Pointer(&cArgsPtr[0])))
		info.args_len = (*C.ulong)(pinner.Pin(unsafe.Pointer(&argLengthsPtr[0])))
	}
	return info
}

func (client *baseClient) submitConnectionPasswordUpdate(
	ctx context.Context,
	password string,
	immediateAuth bool,
) (string, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return models.DefaultStringResponse, ctx.Err()
	default:
		// Continue with execution
	}

	// Create a channel to receive the result
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return models.DefaultStringResponse, NewClosingError("UpdatePassword failed. The client is closed.")
	}
	client.pending[resultChannelPtr] = struct{}{}

	password_cstring := C.CString(password)
	defer C.free(unsafe.Pointer(password_cstring))
	C.update_connection_password(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		password_cstring,
		C._Bool(immediateAuth),
	)
	client.mu.Unlock()

	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return models.DefaultStringResponse, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return models.DefaultStringResponse, payload.error
	}

	return handleOkResponse(payload.value)
}

// Update the current connection with a new password.
//
// This method is useful in scenarios where the server password has changed or when utilizing
// short-lived passwords for enhanced security. It allows the client to update its password to
// reconnect upon disconnection without the need to recreate the client instance. This ensures
// that the internal reconnection mechanism can handle reconnection seamlessly, preventing the
// loss of in-flight commands.
//
// Note:
//
//	This method updates the client's internal password configuration and does not perform
//	password rotation on the server side.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	password - The new password to update the connection with.
//	immediateAuth - immediateAuth A boolean flag. If true, the client will authenticate immediately with the new password
//	against all connections, Using AUTH command. If password supplied is an empty string, the client will
//	not perform auth and a warning will be returned. The default is `false`.
//
// Return value:
//
//	`"OK"` response on success.
func (client *baseClient) UpdateConnectionPassword(ctx context.Context, password string, immediateAuth bool) (string, error) {
	return client.submitConnectionPasswordUpdate(ctx, password, immediateAuth)
}

// Update the current connection by removing the password.
//
// This method is useful in scenarios where the server password has changed or when utilizing
// short-lived passwords for enhanced security. It allows the client to update its password to
// reconnect upon disconnection without the need to recreate the client instance. This ensures
// that the internal reconnection mechanism can handle reconnection seamlessly, preventing the
// loss of in-flight commands.
//
// Note:
//
//	This method updates the client's internal password configuration and does not perform
//	password rotation on the server side.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`"OK"` response on success.
func (client *baseClient) ResetConnectionPassword(ctx context.Context) (string, error) {
	return client.submitConnectionPasswordUpdate(ctx, "", false)
}

func (client *baseClient) submitRefreshIamToken(ctx context.Context) (string, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return models.DefaultStringResponse, ctx.Err()
	default:
		// Continue with execution
	}

	// Create a channel to receive the result
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return models.DefaultStringResponse, NewClosingError("RefreshIamToken failed. The client is closed.")
	}
	client.pending[resultChannelPtr] = struct{}{}

	C.refresh_iam_token(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
	)
	client.mu.Unlock()

	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return models.DefaultStringResponse, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return models.DefaultStringResponse, payload.error
	}

	return handleOkResponse(payload.value)
}

// RefreshIamToken manually refreshes the IAM token for the current connection.
//
// This method is only available if the client was created with IAM authentication.
// It triggers an immediate refresh of the IAM token and updates the connection.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`"OK"` response on success.
//
// Example:
//
//	result, err := client.RefreshIamToken(context.Background())
//	if err != nil {
//	    // handle error
//	}
func (client *baseClient) RefreshIamToken(ctx context.Context) (string, error) {
	return client.submitRefreshIamToken(ctx)
}

// Set the given key with the given value. The return value is a response from Valkey containing the string "OK".
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key to store.
//	value - The value to store with the given key.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/set/
func (client *baseClient) Set(ctx context.Context, key string, value string) (string, error) {
	result, err := client.executeCommand(ctx, C.Set, []string{key, value})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// SetWithOptions sets the given key with the given value using the given options. The return value is dependent on the
// passed options. If the value is successfully set, "OK" is returned. If value isn't set because of [constants.OnlyIfExists]
// or [constants.OnlyIfDoesNotExist] conditions, models.CreateNilStringResult() is returned. If [constants.ReturnOldValue] is
// set, the old value is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key to store.
//	value   - The value to store with the given key.
//	options - The [options.SetOptions].
//
// Return value:
//
//	If the value is successfully set, return models.Result[string] containing "OK".
//	If value isn't set because of ConditionalSet.OnlyIfExists or ConditionalSet.OnlyIfDoesNotExist
//	or ConditionalSet.OnlyIfEquals conditions, return models.CreateNilStringResult().
//	If SetOptions.returnOldValue is set, return the old value as a String.
//
// [valkey.io]: https://valkey.io/commands/set/
func (client *baseClient) SetWithOptions(
	ctx context.Context,
	key string,
	value string,
	options options.SetOptions,
) (models.Result[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	result, err := client.executeCommand(ctx, C.Set, append([]string{key, value}, optionArgs...))
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleOkOrStringOrNilResponse(result)
}

// Get string value associated with the given key, or models.CreateNilStringResult() is returned if no such key
// exists.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to be retrieved from the database.
//
// Return value:
//
//	If key exists, returns the value of key as a String. Otherwise, return [models.CreateNilStringResult()].
//
// [valkey.io]: https://valkey.io/commands/get/
func (client *baseClient) Get(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.Get, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Get string value associated with the given key, or an empty string is returned [models.CreateNilStringResult()] if no such
// value exists.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to be retrieved from the database.
//
// Return value:
//
//	If key exists, returns the value of key as a models.Result[string]. Otherwise, return [models.CreateNilStringResult()].
//
// [valkey.io]: https://valkey.io/commands/getex/
func (client *baseClient) GetEx(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.GetEx, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Get string value associated with the given key and optionally sets the expiration of the key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to be retrieved from the database.
//	options - The [options.GetExOptions].
//
// Return value:
//
//	If key exists, returns the value of key as a models.Result[string]. Otherwise, return [models.CreateNilStringResult()].
//
// [valkey.io]: https://valkey.io/commands/getex/
func (client *baseClient) GetExWithOptions(
	ctx context.Context,
	key string,
	options options.GetExOptions,
) (models.Result[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	result, err := client.executeCommand(ctx, C.GetEx, append([]string{key}, optionArgs...))
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Sets multiple keys to multiple values in a single operation.
//
// Note:
//
//	In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keyValueMap - A key-value map consisting of keys and their respective values to set.
//
// Return value:
//
//	`"OK"` on success.
//
// [valkey.io]: https://valkey.io/commands/mset/
func (client *baseClient) MSet(ctx context.Context, keyValueMap map[string]string) (string, error) {
	result, err := client.executeCommand(ctx, C.MSet, utils.MapToString(keyValueMap))
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or more keys already exist,
// the entire operation fails.
//
// Note:
//
//	In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keyValueMap - A key-value map consisting of keys and their respective values to set.
//
// Return value:
//
//	A bool containing true, if all keys were set. false, if no key was set.
//
// [valkey.io]: https://valkey.io/commands/msetnx/
func (client *baseClient) MSetNX(ctx context.Context, keyValueMap map[string]string) (bool, error) {
	result, err := client.executeCommand(ctx, C.MSetNX, utils.MapToString(keyValueMap))
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// Retrieves the values of multiple keys.
//
// Note:
//
//	In cluster mode, if keys in `keys` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - A list of keys to retrieve values for.
//
// Return value:
//
//	An array of [models.Result[string]] values corresponding to the provided keys.
//	If a key is not found, its corresponding value in the list will be a [models.CreateNilStringResult()].
//
// [valkey.io]: https://valkey.io/commands/mget/
func (client *baseClient) MGet(ctx context.Context, keys []string) ([]models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.MGet, keys)
	if err != nil {
		return nil, err
	}

	return handleStringOrNilArrayResponse(result)
}

// Increments the number stored at key by one. If key does not exist, it is set to 0 before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to increment its value.
//
// Return value:
//
//	The value of `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/incr/
func (client *baseClient) Incr(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Incr, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Increments the number stored at key by amount. If key does not exist, it is set to 0 before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key to increment its value.
//	amount - The amount to increment.
//
// Return value:
//
//	The value of `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/incrby/
func (client *baseClient) IncrBy(ctx context.Context, key string, amount int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.IncrBy, []string{key, utils.IntToString(amount)})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Increments the string representing a floating point number stored at key by amount. By using a negative increment value,
// the result is that the value stored at key is decremented. If key does not exist, it is set to `0` before performing the
// operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key to increment its value.
//	amount - The amount to increment.
//
// Return value:
//
//	The value of key after the increment.
//
// [valkey.io]: https://valkey.io/commands/incrbyfloat/
func (client *baseClient) IncrByFloat(ctx context.Context, key string, amount float64) (float64, error) {
	result, err := client.executeCommand(ctx,
		C.IncrByFloat,
		[]string{key, utils.FloatToString(amount)},
	)
	if err != nil {
		return models.DefaultFloatResponse, err
	}

	return handleFloatResponse(result)
}

// Decrements the number stored at key by one. If key does not exist, it is set to 0 before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to decrement its value.
//
// Return value:
//
//	The value of `key` after the decrement.
//
// [valkey.io]: https://valkey.io/commands/decr/
func (client *baseClient) Decr(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Decr, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Decrements the number stored at code by amount. If key does not exist, it is set to 0 before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key to decrement its value.
//	amount - The amount to decrement.
//
// Return value:
//
//	The value of `key` after the decrement.
//
// [valkey.io]: https://valkey.io/commands/decrby/
func (client *baseClient) DecrBy(ctx context.Context, key string, amount int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.DecrBy, []string{key, utils.IntToString(amount)})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Returns the length of the string value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to check its length.
//
// Return value:
//
//	The length of the string value stored at `key`.
//	If key does not exist, it is treated as an empty string, and the command returns `0`.
//
// [valkey.io]: https://valkey.io/commands/strlen/
func (client *baseClient) Strlen(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Strlen, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Overwrites part of the string stored at key, starting at the specified byte's offset, for the entire length of value.
// If the offset is larger than the current length of the string at key, the string is padded with zero bytes to make
// offset fit.
// Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the string to update.
//	offset - The position in the string where value should be written.
//	value  - The string written with offset.
//
// Return value:
//
//	The length of the string stored at `key` after it was modified.
//
// [valkey.io]: https://valkey.io/commands/setrange/
func (client *baseClient) SetRange(ctx context.Context, key string, offset int, value string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SetRange, []string{key, strconv.Itoa(offset), value})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Returns the substring of the string value stored at key, determined by the byte's offsets start and end (both are
// inclusive).
// Negative offsets can be used in order to provide an offset starting from the end of the string. So `-1` means the last
// character, `-2` the penultimate and so forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the string.
//	start - The starting offset.
//	end   - The ending offset.
//
// Return value:
//
//	A substring extracted from the value stored at key. Returns empty string if the offset is out of bounds.
//
// [valkey.io]: https://valkey.io/commands/getrange/
func (client *baseClient) GetRange(ctx context.Context, key string, start int, end int) (string, error) {
	result, err := client.executeCommand(ctx, C.GetRange, []string{key, strconv.Itoa(start), strconv.Itoa(end)})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleStringResponse(result)
}

// Appends a value to a key. If key does not exist it is created and set as an empty string, so APPEND will be similar to
// SET in this special case.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the string.
//	value - The value to append.
//
// Return value:
//
//	The length of the string after appending the value.
//
// [valkey.io]: https://valkey.io/commands/append/
func (client *baseClient) Append(ctx context.Context, key string, value string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Append, []string{key, value})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Returns the longest common subsequence between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	When in cluster mode, `key1` and `key2` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//
// Return value:
//
//	A [models.LCSMatch] object containing:
//	- `MatchString`: A string containing all the longest common subsequences combined between the 2 strings. An empty string is
//		returned if the keys do not exist or have no common subsequences.
//	- `Matches`: Empty array.
//	- `Len`: 0
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (client *baseClient) LCS(ctx context.Context, key1 string, key2 string) (*models.LCSMatch, error) {
	result, err := client.executeCommand(ctx, C.LCS, []string{key1, key2})
	if err != nil {
		return nil, err
	}

	return handleLCSMatchResponse(result, internal.SimpleLCSString)
}

// Returns the total length of all the longest common subsequences between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	When in cluster mode, `key1` and `key2` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//
// Return value:
//
//	A [models.LCSMatch] object containing:
//	- `MatchString`: Empty string.
//	- `Matches`: Empty array.
//	- `Len`: The total length of all the longest common subsequences the 2 strings.
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (client *baseClient) LCSLen(ctx context.Context, key1, key2 string) (*models.LCSMatch, error) {
	result, err := client.executeCommand(ctx, C.LCS, []string{key1, key2, options.LCSLenCommand})
	if err != nil {
		return nil, err
	}

	return handleLCSMatchResponse(result, internal.SimpleLCSLength)
}

// Returns the longest common subsequence between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	When in cluster mode, `key1` and `key2` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//	opts - The [LCSIdxOptions] type.
//
// Return value:
//
//	A [models.LCSMatch] object containing:
//	- `MatchString`: Empty string.
//	- `Matches`: Array of [models.LCSMatchedPosition] objects with the common subsequences in the strings held by key1 and
//		key2. If WithMatchLen is specified, the array also contains the length of each match, otherwise the length is 0.
//	- `Len`: The total length of all the longest common subsequences the 2 strings.
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (client *baseClient) LCSWithOptions(
	ctx context.Context,
	key1, key2 string,
	opts options.LCSIdxOptions,
) (*models.LCSMatch, error) {
	optArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	response, err := client.executeCommand(ctx, C.LCS, append([]string{key1, key2}, optArgs...))
	if err != nil {
		return nil, err
	}

	return handleLCSMatchResponse(response, internal.ComplexLCSMatch)
}

// GetDel gets the value associated with the given key and deletes the key.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to get and delete.
//
// Return value:
//
//	If key exists, returns the value of the key as a String and deletes the key.
//	If key does not exist, returns a [models.Result[string]](models.CreateNilStringResult()).
//
// [valkey.io]: https://valkey.io/commands/getdel/
func (client *baseClient) GetDel(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.GetDel, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// HGet returns the value associated with field in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the hash.
//	field - The field in the hash stored at key to retrieve from the database.
//
// Return value:
//
//	The models.Result[string] associated with field, or [models.Result[string]](models.CreateNilStringResult()) when
//	field is not present in the hash or key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hget/
func (client *baseClient) HGet(ctx context.Context, key string, field string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.HGet, []string{key, field})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// HGetAll returns all fields and values of the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//
// Return value:
//
//	A map of all fields and their values in the hash, or an empty map when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hgetall/
func (client *baseClient) HGetAll(ctx context.Context, key string) (map[string]string, error) {
	result, err := client.executeCommand(ctx, C.HGetAll, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringToStringMapResponse(result)
}

// HMGet returns the values associated with the specified fields in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields in the hash stored at key to retrieve from the database.
//
// Return value:
//
//	An array of [models.Result[string]] values associated with the given fields, in the same order as they are requested.
//	For every field that does not exist in the hash, a [models.CreateNilStringResult()] is returned.
//	If key does not exist, returns an empty string array.
//
// [valkey.io]: https://valkey.io/commands/hmget/
func (client *baseClient) HMGet(ctx context.Context, key string, fields []string) ([]models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.HMGet, append([]string{key}, fields...))
	if err != nil {
		return nil, err
	}

	return handleStringOrNilArrayResponse(result)
}

// HSet sets the specified fields to their respective values in the hash stored at key.
// This command overwrites the values of specified fields that exist in the hash.
// If key doesn't exist, a new key holding a hash is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	values - A map of field-value pairs to set in the hash.
//
// Return value:
//
//	The number of fields that were added or updated.
//
// [valkey.io]: https://valkey.io/commands/hset/
func (client *baseClient) HSet(ctx context.Context, key string, values map[string]string) (int64, error) {
	result, err := client.executeCommand(ctx, C.HSet, utils.ConvertMapToKeyValueStringArray(key, values))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// HSetNX sets field in the hash stored at key to value, only if field does not yet exist.
// If key does not exist, a new key holding a hash is created.
// If field already exists, this operation has no effect.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the hash.
//	field - The field to set.
//	value - The value to set.
//
// Return value:
//
//	A bool containing true if field is a new field in the hash and value was set.
//	false if field already exists in the hash and no operation was performed.
//
// [valkey.io]: https://valkey.io/commands/hsetnx/
func (client *baseClient) HSetNX(ctx context.Context, key string, field string, value string) (bool, error) {
	result, err := client.executeCommand(ctx, C.HSetNX, []string{key, field, value})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// HDel removes the specified fields from the hash stored at key.
// Specified fields that do not exist within this hash are ignored.
// If key does not exist, it is treated as an empty hash and this command returns 0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to remove from the hash stored at key.
//
// Return value:
//
//	The number of fields that were removed from the hash, not including specified but non-existing fields.
//
// [valkey.io]: https://valkey.io/commands/hdel/
func (client *baseClient) HDel(ctx context.Context, key string, fields []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.HDel, append([]string{key}, fields...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// HLen returns the number of fields contained in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//
// Return value:
//
//	The number of fields in the hash, or `0` when key does not exist.
//	If key holds a value that is not a hash, an error is returned.
//
// [valkey.io]: https://valkey.io/commands/hlen/
func (client *baseClient) HLen(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.HLen, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// HVals returns all values in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//
// Return value:
//
//	A slice containing all the values in the hash, or an empty slice when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hvals/
func (client *baseClient) HVals(ctx context.Context, key string) ([]string, error) {
	result, err := client.executeCommand(ctx, C.HVals, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// HExists returns if field is an existing field in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the hash.
//	field - The field to check in the hash stored at key.
//
// Return value:
//
//	A bool containing true if the hash contains the specified field.
//	false if the hash does not contain the field, or if the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hexists/
func (client *baseClient) HExists(ctx context.Context, key string, field string) (bool, error) {
	result, err := client.executeCommand(ctx, C.HExists, []string{key, field})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// HKeys returns all field names in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//
// Return value:
//
//	A slice containing all the field names in the hash, or an empty slice when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hkeys/
func (client *baseClient) HKeys(ctx context.Context, key string) ([]string, error) {
	result, err := client.executeCommand(ctx, C.HKeys, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// HStrLen returns the string length of the value associated with field in the hash stored at key.
// If the key or the field do not exist, 0 is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the hash.
//	field - The field to get the string length of its value.
//
// Return value:
//
//	The length of the string value associated with field, or `0` when field or key do not exist.
//
// [valkey.io]: https://valkey.io/commands/hstrlen/
func (client *baseClient) HStrLen(ctx context.Context, key string, field string) (int64, error) {
	result, err := client.executeCommand(ctx, C.HStrlen, []string{key, field})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Increments the number stored at `field` in the hash stored at `key` by increment.
// By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
// If `field` or `key` does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	field - The field in the hash stored at `key` to increment its value.
//	increment - The amount to increment.
//
// Return value:
//
//	The value of `field` in the hash stored at `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/hincrby/
func (client *baseClient) HIncrBy(ctx context.Context, key string, field string, increment int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.HIncrBy, []string{key, field, utils.IntToString(increment)})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Increments the string representing a floating point number stored at `field` in the hash stored at `key` by increment.
// By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
// If `field` or `key` does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	field - The field in the hash stored at `key` to increment its value.
//	increment - The amount to increment.
//
// Return value:
//
//	The value of `field` in the hash stored at `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/hincrbyfloat/
func (client *baseClient) HIncrByFloat(ctx context.Context, key string, field string, increment float64) (float64, error) {
	result, err := client.executeCommand(ctx, C.HIncrByFloat, []string{key, field, utils.FloatToString(increment)})
	if err != nil {
		return models.DefaultFloatResponse, err
	}

	return handleFloatResponse(result)
}

// Iterates fields of Hash types and their associated values. This definition of HSCAN command does not include the
// optional arguments of the command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	cursor - The cursor that points to the next iteration of results.
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always a flattened series of string pairs, where the hash field names
//	are at even indices, and the hash field value are at odd indices.
//
// [valkey.io]: https://valkey.io/commands/hscan/
func (client *baseClient) HScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error) {
	result, err := client.executeCommand(ctx, C.HScan, []string{key, cursor.String()})
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Iterates fields of Hash types and their associated values. This definition of HSCAN includes optional arguments of the
// command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	cursor - The cursor that points to the next iteration of results.
//	options - The [options.HashScanOptions].
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always a flattened series of string pairs, where the hash field names
//	are at even indices, and the hash field value are at odd indices.
//
// [valkey.io]: https://valkey.io/commands/hscan/
func (client *baseClient) HScanWithOptions(
	ctx context.Context,
	key string,
	cursor models.Cursor,
	options options.HashScanOptions,
) (models.ScanResult, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.ScanResult{}, err
	}

	result, err := client.executeCommand(ctx, C.HScan, append([]string{key, cursor.String()}, optionArgs...))
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Returns a random field name from the hash value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//
// Return value:
//
//	A random field name from the hash stored at `key`, or `nil` when
//	the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (client *baseClient) HRandField(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.HRandField, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Retrieves up to `count` random field names from the hash value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	count - The number of field names to return.
//		If `count` is positive, returns unique elements.
//		If negative, allows for duplicates.
//
// Return value:
//
//	An array of random field names from the hash stored at `key`,
//	or an empty array when the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (client *baseClient) HRandFieldWithCount(ctx context.Context, key string, count int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.HRandField, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Retrieves up to `count` random field names along with their values from the hash
// value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the hash.
//	count - The number of field names to return.
//	  	If `count` is positive, returns unique elements.
//		If negative, allows for duplicates.
//
// Return value:
//
//	A 2D `array` of `[field, value]` arrays, where `field` is a random
//	field name from the hash and `value` is the associated value of the field name.
//	If the hash does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (client *baseClient) HRandFieldWithCountWithValues(ctx context.Context, key string, count int64) ([][]string, error) {
	result, err := client.executeCommand(
		ctx,
		C.HRandField,
		[]string{key, utils.IntToString(count), constants.WithValuesKeyword},
	)
	if err != nil {
		return nil, err
	}
	return handle2DStringArrayResponse(result)
}

// Sets the value of one or more fields of a given hash key, and optionally set their expiration time or time-to-live
// (TTL).
// This command overwrites the values and expirations of specified fields that exist in the hash.
// If `key` doesn't exist, a new key holding a hash is created.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx              - The context for controlling the command execution.
//	key              - The key of the hash.
//	fieldsAndValues  - A map of field-value pairs to set in the hash.
//	options          - Optional arguments for the command.
//
// Return value:
//
//   - 1 if all fields were set successfully.
//   - 0 if no fields were set due to conditional restrictions.
//
// [valkey.io]: https://valkey.io/commands/hsetex/
func (client *baseClient) HSetEx(
	ctx context.Context,
	key string,
	fieldsAndValues map[string]string,
	opts options.HSetExOptions,
) (int64, error) {
	args, err := internal.BuildHSetExArgs(key, fieldsAndValues, opts)
	if err != nil {
		return 0, err
	}

	result, err := client.executeCommand(ctx, C.HSetEx, args)
	if err != nil {
		return 0, err
	}

	return handleIntResponse(result)
}

// Gets the values of one or more fields of a given hash key and optionally sets their expiration time or time-to-live
// (TTL).
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the hash.
//	fields  - The fields in the hash stored at key to retrieve from the database.
//	options - Optional arguments for the command.
//
// Return value:
//
//	An array of [models.Result[string]] values associated with the given fields, in the same order as they are requested.
//	- For every field that does not exist in the hash, a [models.CreateNilStringResult()] is returned.
//	- If key does not exist, returns an empty string array.
//
// [valkey.io]: https://valkey.io/commands/hgetex/
func (client *baseClient) HGetEx(
	ctx context.Context,
	key string,
	fields []string,
	opts options.HGetExOptions,
) ([]models.Result[string], error) {
	args, err := internal.BuildHGetExArgs(key, fields, opts)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HGetEx, args)
	if err != nil {
		return nil, err
	}

	return handleStringOrNilArrayResponse(result)
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key. You must specify at least one
// field.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
// Field expirations will only be cleared by commands that delete or overwrite the contents of the hash fields, including HDEL
// and HSET commands. This means that all the operations that conceptually alter the value stored at a hash key's field without
// replacing it with a new one will leave the TTL untouched.
// You can clear the TTL of a specific field by specifying 0 for the `seconds` argument.
//
// Note:
//
//	Calling HEXPIRE/HPEXPIRE with a time in the past will result in the hash field being deleted immediately.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx        - The context for controlling the command execution.
//	key        - The key of the hash.
//	expireTime - The expiration time as a duration.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Return value:
//
//	An array of integers indicating the result for each field:
//	- -2: Field does not exist in the hash, or key does not exist.
//	- 0: The specified condition was not met.
//	- 1: The expiration time was applied.
//	- 2: When called with 0 seconds.
//
// [valkey.io]: https://valkey.io/commands/hexpire/
func (client *baseClient) HExpire(
	ctx context.Context,
	key string,
	expireTime time.Duration,
	fields []string,
	opts options.HExpireOptions,
) ([]int64, error) {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, false)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HExpire, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key using an absolute Unix
// timestamp. A timestamp in the past will delete the field immediately.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx        - The context for controlling the command execution.
//	key        - The key of the hash.
//	expireTime - The expiration time as a time.Time.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Return value:
//
//	An array of integers indicating the result for each field:
//	- -2: Field does not exist in the hash, or hash is empty.
//	- 0: The specified condition was not met.
//	- 1: The expiration time was applied.
//	- 2: When called with 0 seconds or past Unix time.
//
// [valkey.io]: https://valkey.io/commands/hexpireat/
func (client *baseClient) HExpireAt(
	ctx context.Context,
	key string,
	expireTime time.Time,
	fields []string,
	opts options.HExpireOptions,
) ([]int64, error) {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, false)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HExpireAt, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx        - The context for controlling the command execution.
//	key        - The key of the hash.
//	expireTime - The expiration time as a duration.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Return value:
//
//	An array of integers indicating the result for each field:
//	- -2: Field does not exist in the hash, or hash is empty.
//	- 0: The specified condition was not met.
//	- 1: The expiration time was applied.
//	- 2: When called with 0 milliseconds.
//
// [valkey.io]: https://valkey.io/commands/hpexpire/
func (client *baseClient) HPExpire(
	ctx context.Context,
	key string,
	expireTime time.Duration,
	fields []string,
	opts options.HExpireOptions,
) ([]int64, error) {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, true)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HPExpire, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key using an absolute Unix
// timestamp. A timestamp in the past will delete the field immediately.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx        - The context for controlling the command execution.
//	key        - The key of the hash.
//	expireTime - The expiration time as a time.Time.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Return value:
//
//	An array of integers indicating the result for each field:
//	- -2: Field does not exist in the hash, or hash is empty.
//	- 0: The specified condition was not met.
//	- 1: The expiration time was applied.
//	- 2: When called with 0 milliseconds or past Unix time.
//
// [valkey.io]: https://valkey.io/commands/hpexpireat/
func (client *baseClient) HPExpireAt(
	ctx context.Context,
	key string,
	expireTime time.Time,
	fields []string,
	opts options.HExpireOptions,
) ([]int64, error) {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, true)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HPExpireAt, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Removes the existing expiration on a hash key's field(s), turning the field(s) from volatile (a field with
// expiration set) to persistent (a field that will never expire as no TTL (time to live) is associated).
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to remove expiration from.
//
// Return value:
//
//	An array of integers indicating the result for each field:
//	- -2: Field does not exist in the hash, or hash does not exist.
//	- -1: Field exists but has no expiration.
//	- 1: The expiration was successfully removed from the field.
//
// [valkey.io]: https://valkey.io/commands/hpersist/
func (client *baseClient) HPersist(ctx context.Context, key string, fields []string) ([]int64, error) {
	args, err := internal.BuildHPersistArgs(key, fields)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HPersist, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Returns the remaining TTL (time to live) of a hash key's field(s) that have a set expiration.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to get TTL for.
//
// Return value:
//
//	An array of integers indicating the TTL for each field in seconds:
//	- Positive number: remaining TTL.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/httl/
func (client *baseClient) HTtl(ctx context.Context, key string, fields []string) ([]int64, error) {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HTtl, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Returns the remaining TTL (time to live) of a hash key's field(s) that have a set expiration, in milliseconds.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to get TTL for.
//
// Return value:
//
//	An array of integers indicating the TTL for each field in milliseconds:
//	- Positive number: remaining TTL.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hpttl/
func (client *baseClient) HPTtl(ctx context.Context, key string, fields []string) ([]int64, error) {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HPTtl, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Returns the absolute Unix timestamp in seconds since Unix epoch at which the given key's field(s) will expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to get expiration time for.
//
// Return value:
//
//	An array of integers indicating the expiration timestamp for each field in seconds:
//	- Positive number: expiration timestamp.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hexpiretime/
func (client *baseClient) HExpireTime(ctx context.Context, key string, fields []string) ([]int64, error) {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HExpireTime, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Returns the absolute Unix timestamp in milliseconds since Unix epoch at which the given key's field(s) will
// expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the hash.
//	fields - The fields to get expiration time for.
//
// Return value:
//
//	An array of integers indicating the expiration timestamp for each field in milliseconds:
//	- Positive number: expiration timestamp.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hpexpiretime/
func (client *baseClient) HPExpireTime(ctx context.Context, key string, fields []string) ([]int64, error) {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.HPExpireTime, args)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Inserts all the specified values at the head of the list stored at key. elements are inserted one after the other to the
// head of the list, from the leftmost element to the rightmost element. If key does not exist, it is created as an empty
// list before performing the push operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx      - The context for controlling the command execution.
//	key      - The key of the list.
//	elements - The elements to insert at the head of the list stored at key.
//
// Return value:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/lpush/
func (client *baseClient) LPush(ctx context.Context, key string, elements []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.LPush, append([]string{key}, elements...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Removes and returns the first elements of the list stored at key. The command pops a single element from the beginning
// of the list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list.
//
// Return value:
//
//	The models.Result[string] containing the value of the first element.
//	If key does not exist, [models.CreateNilStringResult()] will be returned.
//
// [valkey.io]: https://valkey.io/commands/lpop/
func (client *baseClient) LPop(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.LPop, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Removes and returns up to `count` elements of the list stored at key, depending on the list's length.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the list.
//	count - The count of the elements to pop from the list.
//
// Return value:
//
//	An array of the popped elements as strings will be returned depending on the list's length
//	If key does not exist, nil will be returned.
//
// [valkey.io]: https://valkey.io/commands/lpop/
func (client *baseClient) LPopCount(ctx context.Context, key string, count int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.LPop, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}

	return handleStringArrayOrNilResponse(result)
}

// Returns the index of the first occurrence of element inside the list specified by key. If no match is found,
// [models.CreateNilInt64Result()] is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The name of the list.
//	element - The value to search for within the list.
//
// Return value:
//
//	The models.Result[int64] containing the index of the first occurrence of element, or [models.CreateNilInt64Result()] if
//	element is not in the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (client *baseClient) LPos(ctx context.Context, key string, element string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.LPos, []string{key, element})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}

	return handleIntOrNilResponse(result)
}

// Returns the index of an occurrence of element within a list based on the given options. If no match is found,
// [models.CreateNilInt64Result()] is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The name of the list.
//	element - The value to search for within the list.
//	options - The LPos options.
//
// Return value:
//
//	The models.Result[int64] containing the index of element, or [models.CreateNilInt64Result()] if element is not in the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (client *baseClient) LPosWithOptions(
	ctx context.Context,
	key string,
	element string,
	options options.LPosOptions,
) (models.Result[int64], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	result, err := client.executeCommand(ctx, C.LPos, append([]string{key, element}, optionArgs...))
	if err != nil {
		return models.CreateNilInt64Result(), err
	}

	return handleIntOrNilResponse(result)
}

// Returns an array of indices of matching elements within a list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The name of the list.
//	element - The value to search for within the list.
//	count   - The number of matches wanted.
//
// Return value:
//
//	An array that holds the indices of the matching elements within the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (client *baseClient) LPosCount(ctx context.Context, key string, element string, count int64) ([]int64, error) {
	result, err := client.executeCommand(ctx, C.LPos, []string{key, element, constants.CountKeyword, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Returns an array of indices of matching elements within a list based on the given options. If no match is found, an
// empty array is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The name of the list.
//	element - The value to search for within the list.
//	count   - The number of matches wanted.
//	opts    - The LPos options.
//
// Return value:
//
//	An array that holds the indices of the matching elements within the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (client *baseClient) LPosCountWithOptions(
	ctx context.Context,
	key string,
	element string,
	count int64,
	opts options.LPosOptions,
) ([]int64, error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx,
		C.LPos,
		append([]string{key, element, constants.CountKeyword, utils.IntToString(count)}, optionArgs...),
	)
	if err != nil {
		return nil, err
	}

	return handleIntArrayResponse(result)
}

// Inserts all the specified values at the tail of the list stored at key.
// elements are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
// If key does not exist, it is created as an empty list before performing the push operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx      - The context for controlling the command execution.
//	key      - The key of the list.
//	elements - The elements to insert at the tail of the list stored at key.
//
// Return value:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/rpush/
func (client *baseClient) RPush(ctx context.Context, key string, elements []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.RPush, append([]string{key}, elements...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SAdd adds specified members to the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key where members will be added to its set.
//	members - A list of members to add to the set stored at key.
//
// Return value:
//
//	The number of members that were added to the set, excluding members already present.
//
// [valkey.io]: https://valkey.io/commands/sadd/
func (client *baseClient) SAdd(ctx context.Context, key string, members []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SAdd, append([]string{key}, members...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SRem removes specified members from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key from which members will be removed.
//	members - A list of members to remove from the set stored at key.
//
// Return value:
//
//	The number of members that were removed from the set, excluding non-existing members.
//
// [valkey.io]: https://valkey.io/commands/srem/
func (client *baseClient) SRem(ctx context.Context, key string, members []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SRem, append([]string{key}, members...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SUnionStore stores the members of the union of all given sets specified by `keys` into a new set at `destination`.
//
// Note:
//
//	When in cluster mode, `destination` and all `keys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key of the destination set.
//	keys - The keys from which to retrieve the set members.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sunionstore/
func (client *baseClient) SUnionStore(ctx context.Context, destination string, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SUnionStore, append([]string{destination}, keys...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SMembers retrieves all the members of the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key from which to retrieve the set members.
//
// Return value:
//
//	A `map[string]struct{}` containing all members of the set.
//	Returns an empty collection if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/smembers/
func (client *baseClient) SMembers(ctx context.Context, key string) (map[string]struct{}, error) {
	result, err := client.executeCommand(ctx, C.SMembers, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringSetResponse(result)
}

// SCard retrieves the set cardinality (number of elements) of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key from which to retrieve the number of set members.
//
// Return value:
//
//	The cardinality (number of elements) of the set, or `0` if the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/scard/
func (client *baseClient) SCard(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SCard, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SIsMember returns if member is a member of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the set.
//	member - The member to check for existence in the set.
//
// Return value:
//
//	A bool containing true if the member exists in the set, false otherwise.
//	If key doesn't exist, it is treated as an empty set and the method returns false.
//
// [valkey.io]: https://valkey.io/commands/sismember/
func (client *baseClient) SIsMember(ctx context.Context, key string, member string) (bool, error) {
	result, err := client.executeCommand(ctx, C.SIsMember, []string{key, member})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// SDiff computes the difference between the first set and all the successive sets in keys.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sets to diff.
//
// Return value:
//
//	A `map[string]struct{}` representing the difference between the sets.
//	If a key does not exist, it is treated as an empty set.
//
// [valkey.io]: https://valkey.io/commands/sdiff/
func (client *baseClient) SDiff(ctx context.Context, keys []string) (map[string]struct{}, error) {
	result, err := client.executeCommand(ctx, C.SDiff, keys)
	if err != nil {
		return nil, err
	}

	return handleStringSetResponse(result)
}

// SDiffStore stores the difference between the first set and all the successive sets in `keys`
// into a new set at `destination`.
//
// Note:
//
//	When in cluster mode, `destination` and all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	destination - The key of the destination set.
//	keys        - The keys of the sets to diff.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sdiffstore/
func (client *baseClient) SDiffStore(ctx context.Context, destination string, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SDiffStore, append([]string{destination}, keys...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SInter gets the intersection of all the given sets.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sets to intersect.
//
// Return value:
//
//	A `map[string]struct{}` containing members which are present in all given sets.
//	If one or more sets do not exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/sinter/
func (client *baseClient) SInter(ctx context.Context, keys []string) (map[string]struct{}, error) {
	result, err := client.executeCommand(ctx, C.SInter, keys)
	if err != nil {
		return nil, err
	}

	return handleStringSetResponse(result)
}

// Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`
//
// Note:
//
//	When in cluster mode, `destination` and all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key of the destination set.
//	keys - The keys from which to retrieve the set members.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sinterstore/
func (client *baseClient) SInterStore(ctx context.Context, destination string, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SInterStore, append([]string{destination}, keys...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SInterCard gets the cardinality of the intersection of all the given sets.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sets to intersect.
//
// Return value:
//
//	The cardinality of the intersection result. If one or more sets do not exist, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/sintercard/
func (client *baseClient) SInterCard(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.SInterCard, append([]string{strconv.Itoa(len(keys))}, keys...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SInterCardLimit gets the cardinality of the intersection of all the given sets, up to the specified limit.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	keys  - The keys of the sets to intersect.
//	limit - The limit for the intersection cardinality value.
//
// Return value:
//
//	The cardinality of the intersection result, or the limit if reached.
//	If one or more sets do not exist, `0` is returned.
//	If the intersection cardinality reaches 'limit' partway through the computation, returns 'limit' as the cardinality.
//
// [valkey.io]: https://valkey.io/commands/sintercard/
func (client *baseClient) SInterCardLimit(ctx context.Context, keys []string, limit int64) (int64, error) {
	args := utils.Concat(
		[]string{utils.IntToString(int64(len(keys)))},
		keys,
		[]string{constants.LimitKeyword, utils.IntToString(limit)},
	)

	result, err := client.executeCommand(ctx, C.SInterCard, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// SRandMember returns a random element from the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key from which to retrieve the set member.
//
// Return value:
//
//	A models.Result[string] containing a random element from the set.
//	Returns models.CreateNilStringResult() if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/srandmember/
func (client *baseClient) SRandMember(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.SRandMember, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// SRandMemberCount returns multiple random members from the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key from which to retrieve the set members.
//	count - The number of members to return.
//	       If count is positive, returns unique elements (no repetition) up to count or the set size, whichever is smaller.
//	       If count is negative, returns elements with possible repetition (the same element may be returned multiple times),
//	       and the number of returned elements is the absolute value of count.
//
// Return value:
//
//	An array of random elements from the set.
//	When count is positive, the returned elements are unique (no repetitions).
//	When count is negative, the returned elements may contain duplicates.
//	If the set does not exist or is empty, an empty array is returned.
//
// [valkey.io]: https://valkey.io/commands/srandmember/
func (client *baseClient) SRandMemberCount(ctx context.Context, key string, count int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.SRandMember, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// SPop removes and returns one random member from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//
// Return value:
//
//	A models.Result[string] containing the value of the popped member.
//	Returns a NilResult if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/spop/
func (client *baseClient) SPop(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.SPop, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// SpopCount removes and returns up to count random members from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	count - The number of members to return.
//		If count is positive, returns unique elements.
//		If count is larger than the set's cardinality, returns the entire set.
//
// Return value:
//
//	A `map[string]struct{}` of popped elements.
//	If key does not exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/spop/
func (client *baseClient) SPopCount(ctx context.Context, key string, count int64) (map[string]struct{}, error) {
	result, err := client.executeCommand(ctx, C.SPop, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}

	return handleStringSetResponse(result)
}

// SMIsMember returns whether each member is a member of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	members - The members to check.
//
// Return value:
//
//	A []bool containing whether each member is a member of the set stored at key.
//
// [valkey.io]: https://valkey.io/commands/smismember/
func (client *baseClient) SMIsMember(ctx context.Context, key string, members []string) ([]bool, error) {
	result, err := client.executeCommand(ctx, C.SMIsMember, append([]string{key}, members...))
	if err != nil {
		return nil, err
	}

	return handleBoolArrayResponse(result)
}

// SUnion gets the union of all the given sets.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sets.
//
// Return value:
//
//	A `map[string]struct{}` of members which are present in at least one of the given sets.
//	If none of the sets exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/sunion/
func (client *baseClient) SUnion(ctx context.Context, keys []string) (map[string]struct{}, error) {
	result, err := client.executeCommand(ctx, C.SUnion, keys)
	if err != nil {
		return nil, err
	}

	return handleStringSetResponse(result)
}

// Iterates incrementally over a set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	cursor - The cursor that points to the next iteration of results.
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the set held in `key`.
//
// [valkey.io]: https://valkey.io/commands/sscan/
func (client *baseClient) SScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error) {
	result, err := client.executeCommand(ctx, C.SScan, []string{key, cursor.String()})
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Iterates incrementally over a set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	cursor - The cursor that points to the next iteration of results.
//	options - [options.BaseScanOptions]
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the set held in `key`.
//
// [valkey.io]: https://valkey.io/commands/sscan/
func (client *baseClient) SScanWithOptions(
	ctx context.Context,
	key string,
	cursor models.Cursor,
	options options.BaseScanOptions,
) (models.ScanResult, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.ScanResult{}, err
	}

	result, err := client.executeCommand(ctx, C.SScan, append([]string{key, cursor.String()}, optionArgs...))
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Moves `member` from the set at `source` to the set at `destination`, removing it from the source set.
// Creates a new destination set if needed. The operation is atomic.
//
// Note: When in cluster mode, `source` and `destination` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	source - The key of the set to remove the element from.
//	destination - The key of the set to add the element to.
//	member - The set element to move.
//
// Return value:
//
//	`true` on success, or `false` if the `source` set does not exist or the element is not a member of the source set.
//
// [valkey.io]: https://valkey.io/commands/smove/
func (client *baseClient) SMove(ctx context.Context, source string, destination string, member string) (bool, error) {
	result, err := client.executeCommand(ctx, C.SMove, []string{source, destination, member})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Returns the specified elements of the list stored at key.
// The offsets start and end are zero-based indexes, with 0 being the first element of the list, 1 being the next element
// and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being
// the last element of the list, -2 being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the list.
//	start - The starting point of the range.
//	end   - The end of the range.
//
// Return value:
//
//	Array of strings in the specified range.
//	If start exceeds the end of the list, or if start is greater than end, an empty array will be returned.
//	If end exceeds the actual end of the list, the range will stop at the actual end of the list.
//	If key does not exist an empty array will be returned.
//
// [valkey.io]: https://valkey.io/commands/lrange/
func (client *baseClient) LRange(ctx context.Context, key string, start int64, end int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.LRange, []string{key, utils.IntToString(start), utils.IntToString(end)})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Returns the element at index from the list stored at key.
// The index is zero-based, so 0 means the first element, 1 the second element and so on. Negative indices can be used to
// designate elements starting at the tail of the list. Here, -1 means the last element, -2 means the penultimate and so
// forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the list.
//	index - The index of the element in the list to retrieve.
//
// Return value:
//
//	The models.Result[string] containing element at index in the list stored at key.
//	If index is out of range or if key does not exist, [models.CreateNilStringResult()] is returned.
//
// [valkey.io]: https://valkey.io/commands/lindex/
func (client *baseClient) LIndex(ctx context.Context, key string, index int64) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.LIndex, []string{key, utils.IntToString(index)})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Trims an existing list so that it will contain only the specified range of elements specified.
// The offsets start and end are zero-based indexes, with 0 being the first element of the list, 1 being the next element
// and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being
// the last element of the list, -2 being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the list.
//	start - The starting point of the range.
//	end   - The end of the range.
//
// Return value:
//
//	Always "OK".
//	If `start` exceeds the end of the list, or if `start` is greater than `end`, the list is emptied
//	and the key is removed.
//	If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
//	If key does not exist, `"OK"` will be returned without changes to the database.
//
// [valkey.io]: https://valkey.io/commands/ltrim/
func (client *baseClient) LTrim(ctx context.Context, key string, start int64, end int64) (string, error) {
	result, err := client.executeCommand(ctx, C.LTrim, []string{key, utils.IntToString(start), utils.IntToString(end)})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Returns the length of the list stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list.
//
// Return value:
//
//	The length of the list at `key`.
//	If `key` does not exist, it is interpreted as an empty list and `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/llen/
func (client *baseClient) LLen(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.LLen, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Removes the first count occurrences of elements equal to element from the list stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the list.
//	count   - The count of the occurrences of elements equal to element to remove.
//			  If count is positive: Removes elements equal to element moving from head to tail.
//			  If count is negative: Removes elements equal to element moving from tail to head.
//			  If count is 0 or count is greater than the occurrences of elements equal to element,
//			  it removes all elements equal to element.
//	element - The element to remove from the list.
//
// Return value:
//
//	The number of the removed elements.
//	If `key` does not exist, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/lrem/
func (client *baseClient) LRem(ctx context.Context, key string, count int64, element string) (int64, error) {
	result, err := client.executeCommand(ctx, C.LRem, []string{key, utils.IntToString(count), element})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Removes and returns the last elements of the list stored at key.
// The command pops a single element from the end of the list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list.
//
// Return value:
//
//	The models.Result[string] containing the value of the last element.
//	If key does not exist, [models.CreateNilStringResult()] will be returned.
//
// [valkey.io]: https://valkey.io/commands/rpop/
func (client *baseClient) RPop(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.RPop, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Removes and returns up to count elements from the list stored at key, depending on the list's length.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the list.
//	count - The count of the elements to pop from the list.
//
// Return value:
//
//	An array of popped elements as strings will be returned depending on the list's length.
//	If key does not exist, nil will be returned.
//
// [valkey.io]: https://valkey.io/commands/rpop/
func (client *baseClient) RPopCount(ctx context.Context, key string, count int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.RPop, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}

	return handleStringArrayOrNilResponse(result)
}

// Inserts element in the list at key either before or after the pivot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx            - The context for controlling the command execution.
//	key            - The key of the list.
//	insertPosition - The relative position to insert into - either constants.Before or constants.After the pivot.
//	pivot          - An element of the list.
//	element        - The new element to insert.
//
// Return value:
//
//	The list length after a successful insert operation.
//	If the `key` doesn't exist returns `-1`.
//	If the `pivot` wasn't found, returns `0`.
//
// [valkey.io]: https://valkey.io/commands/linsert/
func (client *baseClient) LInsert(
	ctx context.Context,
	key string,
	insertPosition constants.InsertPosition,
	pivot string,
	element string,
) (int64, error) {
	insertPositionStr, err := insertPosition.ToString()
	if err != nil {
		return models.DefaultIntResponse, err
	}

	result, err := client.executeCommand(ctx,
		C.LInsert,
		[]string{key, insertPositionStr, pivot, element},
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Pops an element from the head of the first list that is non-empty, with the given keys being checked in the order that
// they are given.
// Blocks the connection when there are no elements to pop from any of the given lists.
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BLPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	keys        - The keys of the lists to pop from.
//	timeout     - The duration to wait for a blocking operation to complete. A value of 0 will block indefinitely.
//
// Return value:
//
//	A two-element array containing the key from which the element was popped and the value of the popped
//	element, formatted as `[key, value]`.
//	If no element could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BLPop(ctx context.Context, keys []string, timeout time.Duration) ([]string, error) {
	result, err := client.executeCommand(ctx, C.BLPop, append(keys, utils.FloatToString(timeout.Seconds())))
	if err != nil {
		return nil, err
	}

	return handleStringArrayOrNilResponse(result)
}

// Pops an element from the tail of the first list that is non-empty, with the given keys being checked in the order that
// they are given.
// Blocks the connection when there are no elements to pop from any of the given lists.
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BRPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	keys        - The keys of the lists to pop from.
//	timeout     - The duration to wait for a blocking operation to complete. A value of 0 will block indefinitely.
//
// Return value:
//
//	A two-element array containing the key from which the element was popped and the value of the popped
//	element, formatted as [key, value].
//	If no element could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/brpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BRPop(ctx context.Context, keys []string, timeout time.Duration) ([]string, error) {
	result, err := client.executeCommand(ctx, C.BRPop, append(keys, utils.FloatToString(timeout.Seconds())))
	if err != nil {
		return nil, err
	}

	return handleStringArrayOrNilResponse(result)
}

// Inserts all the specified values at the tail of the list stored at key, only if key exists and holds a list. If key is
// not a list, this performs no operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx      - The context for controlling the command execution.
//	key      - The key of the list.
//	elements - The elements to insert at the tail of the list stored at key.
//
// Return value:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/rpushx/
func (client *baseClient) RPushX(ctx context.Context, key string, elements []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.RPushX, append([]string{key}, elements...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Inserts all the specified values at the head of the list stored at key, only if key exists and holds a list. If key is
// not a list, this performs no operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx      - The context for controlling the command execution.
//	key      - The key of the list.
//	elements - The elements to insert at the head of the list stored at key.
//
// Return value:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/rpushx/
func (client *baseClient) LPushX(ctx context.Context, key string, elements []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.LPushX, append([]string{key}, elements...))
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Pops one element from the first non-empty list from the provided keys.
//
// Note:
//
//	When in cluster mode, `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx           - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [options.ListDirection].
//
// Return value:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no elements could be popped, returns 'nil'.
//
// [valkey.io]: https://valkey.io/commands/lmpop/
func (client *baseClient) LMPop(
	ctx context.Context,
	keys []string,
	listDirection constants.ListDirection,
) ([]models.KeyValues, error) {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return nil, err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-2 {
		return nil, errors.New("length overflow for the provided keys")
	}

	// args slice will have 2 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+2)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr)
	result, err := client.executeCommand(ctx, C.LMPop, args)
	if err != nil {
		return nil, err
	}

	return handleKeyValuesArrayOrNilResponse(result)
}

// Pops one or more elements from the first non-empty list from the provided keys.
//
// Note:
//
//	When in cluster mode, `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx           - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [options.ListDirection].
//	count         - The maximum number of popped elements.
//
// Return value:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no elements could be popped, returns 'nil'.
//
// [valkey.io]: https://valkey.io/commands/lmpop/
func (client *baseClient) LMPopCount(
	ctx context.Context,
	keys []string,
	listDirection constants.ListDirection,
	count int64,
) ([]models.KeyValues, error) {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return nil, err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-4 {
		return nil, errors.New("length overflow for the provided keys")
	}

	// args slice will have 4 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+4)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr, constants.CountKeyword, utils.IntToString(count))
	result, err := client.executeCommand(ctx, C.LMPop, args)
	if err != nil {
		return nil, err
	}

	return handleKeyValuesArrayOrNilResponse(result)
}

// Blocks the connection until it pops one element from the first non-empty list from the provided keys.
// BLMPop is the blocking variant of [Client.LMPop] and [ClusterClient.LMPop].
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BLMPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx           - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [options.ListDirection].
//	timeout       - The duration to wait for a blocking operation to complete. A value of 0 will block indefinitely.
//
// Return value:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no member could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blmpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BLMPop(
	ctx context.Context,
	keys []string,
	listDirection constants.ListDirection,
	timeout time.Duration,
) ([]models.KeyValues, error) {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return nil, err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-3 {
		return nil, errors.New("length overflow for the provided keys")
	}

	// args slice will have 3 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+3)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr)
	result, err := client.executeCommand(ctx, C.BLMPop, args)
	if err != nil {
		return nil, err
	}

	return handleKeyValuesArrayOrNilResponse(result)
}

// Blocks the connection until it pops one or more elements from the first non-empty list from the provided keys.
// BLMPopCount is the blocking variant of [Client.LMPopCount] [ClusterClient.LMPopCount].
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BLMPopCount is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx           - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [options.ListDirection].
//	count         - The maximum number of popped elements.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block
//
// indefinitely.
//
// Return value:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no member could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blmpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BLMPopCount(
	ctx context.Context,
	keys []string,
	listDirection constants.ListDirection,
	count int64,
	timeout time.Duration,
) ([]models.KeyValues, error) {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return nil, err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-5 {
		return nil, errors.New("length overflow for the provided keys")
	}

	// args slice will have 5 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+5)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr, constants.CountKeyword, utils.IntToString(count))
	result, err := client.executeCommand(ctx, C.BLMPop, args)
	if err != nil {
		return nil, err
	}

	return handleKeyValuesArrayOrNilResponse(result)
}

// Sets the list element at index to element.
// The index is zero-based, so `0` means the first element, `1` the second element and so on. Negative indices can be used to
// designate elements starting at the tail of the list. Here, `-1` means the last element, `-2` means the penultimate and so
// forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the list.
//	index   - The index of the element in the list to be set.
//	element - The element to be set.
//
// Return value:
//
//	`"OK"`.
//
// [valkey.io]: https://valkey.io/commands/lset/
func (client *baseClient) LSet(ctx context.Context, key string, index int64, element string) (string, error) {
	result, err := client.executeCommand(ctx, C.LSet, []string{key, utils.IntToString(index), element})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Atomically pops and removes the left/right-most element to the list stored at source depending on `whereFrom`, and pushes
// the element at the first/last element of the list stored at destination depending on `whereTo`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	source      - The key to the source list.
//	destination - The key to the destination list.
//	wherefrom   - The ListDirection the element should be removed from.
//	whereto     - The ListDirection the element should be added to.
//
// Return value:
//
//	A models.Result[string] containing the popped element or models.CreateNilStringResult() if source does not exist.
//
// [valkey.io]: https://valkey.io/commands/lmove/
func (client *baseClient) LMove(
	ctx context.Context,
	source string,
	destination string,
	whereFrom constants.ListDirection,
	whereTo constants.ListDirection,
) (models.Result[string], error) {
	whereFromStr, err := whereFrom.ToString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	whereToStr, err := whereTo.ToString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	result, err := client.executeCommand(ctx, C.LMove, []string{source, destination, whereFromStr, whereToStr})
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Blocks the connection until it pops atomically and removes the left/right-most element to the list stored at `source`
// depending on `whereFrom`, and pushes the element at the first/last element of the list stored at `destination` depending on
// `whereFrom`.
// `BLMove` is the blocking variant of [Client.LMove] and [ClusterClient.LMove].
//
// Note:
//   - When in cluster mode, `source` and `destination` must map to the same hash slot.
//   - `BLMove` is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	source      - The key to the source list.
//	destination - The key to the destination list.
//	whereFrom   - The ListDirection the element should be removed from.
//	whereTo     - The ListDirection the element should be added to.
//	timeout     - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Return value:
//
//	A models.Result[string] containing the popped element or models.CreateNilStringResult() if `source` does not exist or if
//	the operation timed-out.
//
// [valkey.io]: https://valkey.io/commands/blmove/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BLMove(
	ctx context.Context,
	source string,
	destination string,
	whereFrom constants.ListDirection,
	whereTo constants.ListDirection,
	timeout time.Duration,
) (models.Result[string], error) {
	whereFromStr, err := whereFrom.ToString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	whereToStr, err := whereTo.ToString()
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	result, err := client.executeCommand(ctx,
		C.BLMove,
		[]string{source, destination, whereFromStr, whereToStr, utils.FloatToString(timeout.Seconds())},
	)
	if err != nil {
		return models.CreateNilStringResult(), err
	}

	return handleStringOrNilResponse(result)
}

// Del removes the specified keys from the database. A key is ignored if it does not exist.
//
// Note:
//
//	In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - One or more keys to delete.
//
// Return value:
//
//	Returns the number of keys that were removed.
//
// [valkey.io]: https://valkey.io/commands/del/
func (client *baseClient) Del(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Del, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Exists returns the number of keys that exist in the database.
//
// Note:
//
//	In cluster mode, if keys in `keys` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - One or more keys to check if they exist.
//
// Return value:
//
//	Returns the number of existing keys.
//
// [valkey.io]: https://valkey.io/commands/exists/
func (client *baseClient) Exists(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Exists, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Expire sets a timeout on key. After the timeout has expired, the key will automatically be deleted.
//
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to expire.
//	expireTime - Duration for the key to expire
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expire/
func (client *baseClient) Expire(ctx context.Context, key string, expireTime time.Duration) (bool, error) {
	result, err := client.executeCommand(ctx, C.Expire, []string{key, utils.FloatToString(expireTime.Seconds())})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// Expire sets a timeout on key. After the timeout has expired, the key will automatically be deleted.
//
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to expire.
//	expireTime - Duration for the key to expire
//	expireCondition - The option to set expiry, see [options.ExpireCondition].
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expire/
func (client *baseClient) ExpireWithOptions(
	ctx context.Context,
	key string,
	expireTime time.Duration,
	expireCondition constants.ExpireCondition,
) (bool, error) {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	result, err := client.executeCommand(
		ctx,
		C.Expire,
		[]string{key, utils.FloatToString(expireTime.Seconds()), expireConditionStr},
	)
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// ExpireAt sets a timeout on key. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of
// specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
// expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to expire.
//	expireTime - The timestamp for expiry.
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expireat/
func (client *baseClient) ExpireAt(ctx context.Context, key string, expireTime time.Time) (bool, error) {
	result, err := client.executeCommand(ctx, C.ExpireAt, []string{key, utils.IntToString(expireTime.Unix())})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// ExpireAt sets a timeout on key. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of
// specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
// expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to expire.
//	expireTime - The timestamp for expiry.
//	expireCondition - The option to set expiry - see [options.ExpireCondition].
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expireat/
func (client *baseClient) ExpireAtWithOptions(
	ctx context.Context,
	key string,
	expireTime time.Time,
	expireCondition constants.ExpireCondition,
) (bool, error) {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	result, err := client.executeCommand(ctx,
		C.ExpireAt,
		[]string{key, utils.IntToString(expireTime.Unix()), expireConditionStr},
	)
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to set timeout on it.
//	expireTime - Duration for the key to expire.
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpire/
func (client *baseClient) PExpire(ctx context.Context, key string, expireTime time.Duration) (bool, error) {
	result, err := client.executeCommand(ctx, C.PExpire, []string{key, utils.IntToString(expireTime.Milliseconds())})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to set timeout on it.
//	expireTime - Duration for the key to expire
//	option - The option to set expiry, see [options.ExpireCondition].
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpire/
func (client *baseClient) PExpireWithOptions(
	ctx context.Context,
	key string,
	expireTime time.Duration,
	expireCondition constants.ExpireCondition,
) (bool, error) {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	result, err := client.executeCommand(
		ctx,
		C.PExpire,
		[]string{key, utils.IntToString(expireTime.Milliseconds()), expireConditionStr},
	)
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of
// specifying the number of milliseconds. A timestamp in the past will delete the key immediately.
// After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to set timeout on it.
//	expireTime - The timestamp for expiry.
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpireat/
func (client *baseClient) PExpireAt(ctx context.Context, key string, expireTime time.Time) (bool, error) {
	result, err := client.executeCommand(ctx, C.PExpireAt, []string{key, utils.IntToString(expireTime.UnixMilli())})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of
// specifying the number of milliseconds. A timestamp in the past will delete the key immediately.
// After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to set timeout on it.
//	expireTime - The timestamp for expiry.
//	expireCondition - The option to set expiry, see [options.ExpireCondition].
//
// Return value:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpireat/
func (client *baseClient) PExpireAtWithOptions(
	ctx context.Context,
	key string,
	expireTime time.Time,
	expireCondition constants.ExpireCondition,
) (bool, error) {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	result, err := client.executeCommand(ctx,
		C.PExpireAt,
		[]string{key, utils.IntToString(expireTime.UnixMilli()), expireConditionStr},
	)
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Expire Time returns the absolute Unix timestamp (since January 1, 1970) at which the given key
// will expire, in seconds.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to determine the expiration value of.
//
// Return value:
//
//	The expiration Unix timestamp in seconds.
//	`-2` if key does not exist or `-1` is key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/expiretime/
func (client *baseClient) ExpireTime(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.ExpireTime, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// PExpire Time returns the absolute Unix timestamp (since January 1, 1970) at which the given key
// will expire, in milliseconds.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to determine the expiration value of.
//
// Return value:
//
//	The expiration Unix timestamp in milliseconds.
//	`-2` if key does not exist or `-1` is key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/pexpiretime/
func (client *baseClient) PExpireTime(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.PExpireTime, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// TTL returns the remaining time to live of key that has a timeout, in seconds.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to return its timeout.
//
// Return value:
//
//	Returns TTL in seconds,
//	`-2` if key does not exist, or `-1` if key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/ttl/
func (client *baseClient) TTL(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.TTL, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// PTTL returns the remaining time to live of key that has a timeout, in milliseconds.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to return its timeout.
//
// Return value:
//
//	Returns TTL in milliseconds,
//	`-2` if key does not exist, or `-1` if key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/pttl/
func (client *baseClient) PTTL(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.PTTL, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// PfAdd adds all elements to the HyperLogLog data structure stored at the specified key.
// Creates a new structure if the key does not exist.
// When no elements are provided, and key exists and is a HyperLogLog, then no operation is performed.
// If key does not exist, then the HyperLogLog structure is created.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the HyperLogLog data structure to add elements into.
//	elements - An array of members to add to the HyperLogLog stored at key.
//
// Return value:
//
//	If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
//	altered, then returns `true`. Otherwise, returns `false`.
//
// [valkey.io]: https://valkey.io/commands/pfadd/
func (client *baseClient) PfAdd(ctx context.Context, key string, elements []string) (bool, error) {
	result, err := client.executeCommand(ctx, C.PfAdd, append([]string{key}, elements...))
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
// calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
//
// Note:
//
//	In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The keys of the HyperLogLog data structures to be analyzed.
//
// Return value:
//
//	The approximated cardinality of given HyperLogLog data structures.
//	The cardinality of a key that does not exist is `0`.
//
// [valkey.io]: https://valkey.io/commands/pfcount/
func (client *baseClient) PfCount(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.PfCount, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// PfMerge merges multiple HyperLogLog values into a unique value.
// If the destination variable exists, it is treated as one of the source HyperLogLog data sets,
// otherwise a new HyperLogLog is created.
//
// Note:
//
//	When in cluster mode, `sourceKeys` and `destination` must map to the same hash slot.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key of the destination HyperLogLog where the merged data sets will be stored.
//	sourceKeys - An array of sourceKeys of the HyperLogLog structures to be merged.
//
// Return value:
//
//	If the HyperLogLog values is successfully merged it returns "OK".
//
// [valkey.io]: https://valkey.io/commands/pfmerge/
func (client *baseClient) PfMerge(ctx context.Context, destination string, sourceKeys []string) (string, error) {
	result, err := client.executeCommand(ctx, C.PfMerge, append([]string{destination}, sourceKeys...))
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Unlink (delete) multiple keys from the database. A key is ignored if it does not exist.
// This command, similar to [Client.Del] and [ClusterClient.Del], however, this command does not block the server.
//
// Note:
//
//	In cluster mode, if keys in keys map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - One or more keys to unlink.
//
// Return value:
//
//	Return the number of keys that were unlinked.
//
// [valkey.io]: https://valkey.io/commands/unlink/
func (client *baseClient) Unlink(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Unlink, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Type returns the string representation of the type of the value stored at key.
// The different types that can be returned are: `"string"`, `"list"`, `"set"`, `"zset"`, `"hash"` and `"stream"`.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The `key` to check its data type.
//
// Return value:
//
//	If the `key` exists, the type of the stored value is returned. Otherwise, a `"none"` string is returned.
//
// [valkey.io]: https://valkey.io/commands/type/
func (client *baseClient) Type(ctx context.Context, key string) (string, error) {
	result, err := client.executeCommand(ctx, C.Type, []string{key})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Alters the last access time of a key(s). A key is ignored if it does not exist.
//
// Note:
//
//	In cluster mode, if keys in keys map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - The keys to update last access time.
//
// Return value:
//
//	The number of keys that were updated.
//
// [valkey.io]: Https://valkey.io/commands/touch/
func (client *baseClient) Touch(ctx context.Context, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Touch, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Renames `key` to `newKey`.
// If `newKey` already exists it is overwritten.
//
// Note:
//
//	When in cluster mode, both `key` and `newKey` must map to the same hash slot.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to rename.
//	newKey - The new name of the key.
//
// Return value:
//
//	If the key was successfully renamed, return "OK". If key does not exist, an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/rename/
func (client *baseClient) Rename(ctx context.Context, key string, newKey string) (string, error) {
	result, err := client.executeCommand(ctx, C.Rename, []string{key, newKey})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Renames `key` to `newkey` if `newKey` does not yet exist.
//
// Note:
//
//	When in cluster mode, both `key` and `newkey` must map to the same hash slot.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to rename.
//	newKey - The new name of the key.
//
// Return value:
//
//	`true` if key was renamed to `newKey`, `false` if `newKey` already exists.
//
// [valkey.io]: https://valkey.io/commands/renamenx/
func (client *baseClient) RenameNX(ctx context.Context, key string, newKey string) (bool, error) {
	result, err := client.executeCommand(ctx, C.RenameNX, []string{key, newKey})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	key    - The key of the stream.
//	values - Field-value pairs to be added to the entry.
//
// Return value:
//
//	The id of the added entry.
//
// [valkey.io]: https://valkey.io/commands/xadd/
func (client *baseClient) XAdd(ctx context.Context, key string, values []models.FieldValue) (string, error) {
	result, err := client.XAddWithOptions(ctx, key, values, *options.NewXAddOptions())
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return result.Value(), nil
}

// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the stream.
//	values  - Field-value pairs to be added to the entry.
//	options - Stream add options.
//
// Return value:
//
//	The id of the added entry, or `nil` if [options.XAddOptions.MakeStream] is set to `false`
//	and no stream with the matching `key` exists.
//
// [valkey.io]: https://valkey.io/commands/xadd/
func (client *baseClient) XAddWithOptions(
	ctx context.Context,
	key string,
	values []models.FieldValue,
	options options.XAddOptions,
) (models.Result[string], error) {
	args := []string{}
	args = append(args, key)
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	args = append(args, optionArgs...)
	for _, pair := range values {
		args = append(args, []string{pair.Field, pair.Value}...)
	}

	result, err := client.executeCommand(ctx, C.XAdd, args)
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Reads entries from the given streams.
//
// Note:
//
//	When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keysAndIds - A map of keys and entry IDs to read from.
//
// Return value:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: []FieldValue array of field-value pairs for the entry.
//
// [valkey.io]: https://valkey.io/commands/xread/
func (client *baseClient) XRead(ctx context.Context, keysAndIds map[string]string) (map[string]models.StreamResponse, error) {
	return client.XReadWithOptions(ctx, keysAndIds, *options.NewXReadOptions())
}

// Reads entries from the given streams.
//
// Note:
//
//	When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keysAndIds - A map of keys and entry IDs to read from.
//	opts - Options detailing how to read the stream.
//
// Return value:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: []FieldValue array of field-value pairs for the entry
//
// [valkey.io]: https://valkey.io/commands/xread/
func (client *baseClient) XReadWithOptions(
	ctx context.Context,
	keysAndIds map[string]string,
	opts options.XReadOptions,
) (map[string]models.StreamResponse, error) {
	args, err := internal.CreateStreamCommandArgs(make([]string, 0, 5+2*len(keysAndIds)), keysAndIds, &opts)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.XRead, args)
	if err != nil {
		return nil, err
	}

	return handleStreamResponse(result)
}

// Reads entries from the given streams owned by a consumer group.
//
// Note:
//
//	When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	group - The consumer group name.
//	consumer - The group consumer.
//	keysAndIds - A map of keys and entry IDs to read from.
//
// Return value:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: map[string]string of field-value pairs for the entry
//
// [valkey.io]: https://valkey.io/commands/xreadgroup/
func (client *baseClient) XReadGroup(
	ctx context.Context,
	group string,
	consumer string,
	keysAndIds map[string]string,
) (map[string]models.StreamResponse, error) {
	return client.XReadGroupWithOptions(ctx, group, consumer, keysAndIds, *options.NewXReadGroupOptions())
}

// Reads entries from the given streams owned by a consumer group.
//
// Note:
//
//	When in cluster mode, all keys in `keysAndIds` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	group - The consumer group name.
//	consumer - The group consumer.
//	keysAndIds - A map of keys and entry IDs to read from.
//	opts - Options detailing how to read the stream.
//
// Return value:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: map[string]string of field-value pairs for the entry
//
// [valkey.io]: https://valkey.io/commands/xreadgroup/
func (client *baseClient) XReadGroupWithOptions(
	ctx context.Context,
	group string,
	consumer string,
	keysAndIds map[string]string,
	opts options.XReadGroupOptions,
) (map[string]models.StreamResponse, error) {
	args, err := internal.CreateStreamCommandArgs([]string{constants.GroupKeyword, group, consumer}, keysAndIds, &opts)
	if err != nil {
		return nil, err
	}

	result, err := client.executeCommand(ctx, C.XReadGroup, args)
	if err != nil {
		return nil, err
	}

	return handleStreamResponse(result)
}

// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	membersScoreMap - A map of members to their scores.
//
// Return value:
//
//	The number of members added to the set.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (client *baseClient) ZAdd(
	ctx context.Context,
	key string,
	membersScoreMap map[string]float64,
) (int64, error) {
	result, err := client.executeCommand(ctx,
		C.ZAdd,
		append([]string{key}, utils.ConvertMapToValueKeyStringArray(membersScoreMap)...),
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	membersScoreMap - A map of members to their scores.
//	opts - The options for the command. See [Client.ZAddOptions] [ClusterClient.ZAddOptions] for details.
//
// Return value:
//
//	The number of members added to the set. If `CHANGED` is set, the number of members that were updated.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (client *baseClient) ZAddWithOptions(
	ctx context.Context,
	key string,
	membersScoreMap map[string]float64,
	opts options.ZAddOptions,
) (int64, error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	commandArgs := append([]string{key}, optionArgs...)
	result, err := client.executeCommand(ctx,
		C.ZAdd,
		append(commandArgs, utils.ConvertMapToValueKeyStringArray(membersScoreMap)...),
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

func (client *baseClient) zAddIncrBase(
	ctx context.Context,
	key string,
	opts *options.ZAddOptions,
) (models.Result[float64], error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}

	result, err := client.executeCommand(ctx, C.ZAdd, append([]string{key}, optionArgs...))
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}

	return handleFloatOrNilResponse(result)
}

// Increments the score of member in the sorted set stored at `key` by `increment`.
//
// If `member` does not exist in the sorted set, it is added with `increment` as its
// score (as if its previous score was `0.0`).
// If `key` does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - A member in the sorted set to increment.
//	increment - The score to increment the member.
//
// Return value:
//
//	The new score of the member.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (client *baseClient) ZAddIncr(
	ctx context.Context,
	key string,
	member string,
	increment float64,
) (float64, error) {
	options, err := options.NewZAddOptions().SetIncr(true, increment, member)
	if err != nil {
		return models.DefaultFloatResponse, err
	}

	res, err := client.zAddIncrBase(ctx, key, options)
	if err != nil {
		return models.DefaultFloatResponse, err
	}

	return res.Value(), nil
}

// Increments the score of member in the sorted set stored at `key` by `increment`.
//
// If `member` does not exist in the sorted set, it is added with `increment` as its
// score (as if its previous score was `0.0`).
// If `key` does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - A member in the sorted set to increment.
//	increment - The score to increment the member.
//	opts - The options for the command. See [ZAddOptions] for details.
//
// Return value:
//
//	The new score of the member.
//	If there was a conflict with the options, the operation aborts and `nil` is returned.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (client *baseClient) ZAddIncrWithOptions(
	ctx context.Context,
	key string,
	member string,
	increment float64,
	opts options.ZAddOptions,
) (models.Result[float64], error) {
	incrOpts, err := opts.SetIncr(true, increment, member)
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}

	return client.zAddIncrBase(ctx, key, incrOpts)
}

// Increments the score of member in the sorted set stored at key by increment.
// If member does not exist in the sorted set, it is added with increment as its score.
// If key does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	increment - The score increment.
//	member - A member of the sorted set.
//
// Return value:
//
//	The new score of member.
//
// [valkey.io]: https://valkey.io/commands/zincrby/
func (client *baseClient) ZIncrBy(ctx context.Context, key string, increment float64, member string) (float64, error) {
	result, err := client.executeCommand(ctx, C.ZIncrBy, []string{key, utils.FloatToString(increment), member})
	if err != nil {
		return models.DefaultFloatResponse, err
	}

	return handleFloatResponse(result)
}

// Removes and returns the member with the lowest score from the sorted set
// stored at the specified `key`.
//
// see [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//
// Return value:
//
//	A map containing the removed member and its corresponding score.
//	If `key` doesn't exist, it will be treated as an empty sorted set and the
//	command returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (client *baseClient) ZPopMin(ctx context.Context, key string) (map[string]float64, error) {
	result, err := client.executeCommand(ctx, C.ZPopMin, []string{key})
	if err != nil {
		return nil, err
	}
	return handleStringDoubleMapResponse(result)
}

// Removes and returns multiple members with the lowest scores from the sorted set
// stored at the specified `key`.
//
// see [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	options - Pop options, see [options.ZPopOptions].
//
// Return value:
//
//	A map containing the removed members and their corresponding scores.
//	If `key` doesn't exist, it will be treated as an empty sorted set and the
//	command returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (client *baseClient) ZPopMinWithOptions(
	ctx context.Context,
	key string,
	options options.ZPopOptions,
) (map[string]float64, error) {
	optArgs, err := options.ToArgs(false)
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.ZPopMin, append([]string{key}, optArgs...))
	if err != nil {
		return nil, err
	}
	return handleStringDoubleMapResponse(result)
}

// Removes and returns the member with the highest score from the sorted set stored at the
// specified `key`.
//
// see [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//
// Return value:
//
//	A map containing the removed member and its corresponding score.
//	If `key` doesn't exist, it will be treated as an empty sorted set and the
//	command returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (client *baseClient) ZPopMax(ctx context.Context, key string) (map[string]float64, error) {
	result, err := client.executeCommand(ctx, C.ZPopMax, []string{key})
	if err != nil {
		return nil, err
	}
	return handleStringDoubleMapResponse(result)
}

// Removes and returns up to `count` members with the highest scores from the sorted set
// stored at the specified `key`.
//
// see [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	count - The number of members to remove.
//
// Return value:
//
//	A map containing the removed members and their corresponding scores.
//	If `key` doesn't exist, it will be treated as an empty sorted set and the
//	command returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (client *baseClient) ZPopMaxWithOptions(
	ctx context.Context,
	key string,
	options options.ZPopOptions,
) (map[string]float64, error) {
	optArgs, err := options.ToArgs(false)
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.ZPopMax, append([]string{key}, optArgs...))
	if err != nil {
		return nil, err
	}
	return handleStringDoubleMapResponse(result)
}

// Removes the specified members from the sorted set stored at `key`.
// Specified members that are not a member of this set are ignored.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	members - The members to remove.
//
// Return value:
//
//	The number of members that were removed from the sorted set, not including non-existing members.
//	If `key` does not exist, it is treated as an empty sorted set, and this command returns `0`.
//
// [valkey.io]: https://valkey.io/commands/zrem/
func (client *baseClient) ZRem(ctx context.Context, key string, members []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.ZRem, append([]string{key}, members...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the cardinality (number of elements) of the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//
// Return value:
//
//	The number of elements in the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and this command returns `0`.
//	If `key` holds a value that is not a sorted set, an error is returned.
//
// [valkey.io]: https://valkey.io/commands/zcard/
func (client *baseClient) ZCard(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.ZCard, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Blocks the connection until it removes and returns a member-score pair
// with the lowest score from the first non-empty sorted set.
// The given `keys` being checked in the order they are provided.
// `BZPopMin` is the blocking variant of [Client.BZPopMin] and [ClusterClient.BZPopMin].
//
// Note:
//   - When in cluster mode, all `keys` must map to the same hash slot.
//   - `BZPopMin` is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//	timeout - The duration to wait for a blocking operation to complete. A value of
//	  `0` will block indefinitely.
//
// Return value:
//
//	A `models.KeyWithMemberAndScore` struct containing the key where the member was popped out, the member
//	itself, and the member score. If no member could be popped and the `timeout` expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/bzpopmin/
//
// [Blocking commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BZPopMin(
	ctx context.Context,
	keys []string,
	timeout time.Duration,
) (models.Result[models.KeyWithMemberAndScore], error) {
	result, err := client.executeCommand(ctx, C.BZPopMin, append(keys, utils.FloatToString(timeout.Seconds())))
	if err != nil {
		return models.CreateNilKeyWithMemberAndScoreResult(), err
	}

	return handleKeyWithMemberAndScoreResponse(result)
}

// Blocks the connection until it pops and returns a member-score pair from the first non-empty sorted set, with the
// given keys being checked in the order they are provided.
// BZMPop is the blocking variant of [Client.ZMPop] and [ClusterClient.ZMPop].
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BZMPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx           - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	scoreFilter   - The element pop criteria - either [options.MIN] or [options.MAX] to pop members with the lowest/highest
//					scores accordingly.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block
//					indefinitely.
//
// Return value:
//
//	An object containing the following elements:
//	- The key name of the set from which the element was popped.
//	- An array of member scores of the popped elements.
//	Returns `nil` if no member could be popped and the timeout expired.
//
// [valkey.io]: https://valkey.io/commands/bzmpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BZMPop(
	ctx context.Context,
	keys []string,
	scoreFilter constants.ScoreFilter,
	timeout time.Duration,
) (models.Result[models.KeyWithArrayOfMembersAndScores], error) {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-3 {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), errors.New(
			"length overflow for the provided keys",
		)
	}

	// args slice will have 3 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+3)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	result, err := client.executeCommand(ctx, C.BZMPop, args)
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}
	return handleKeyWithArrayOfMembersAndScoresResponse(result)
}

// Blocks the connection until it pops and returns a member-score pair from the first non-empty sorted set, with the
// given keys being checked in the order they are provided.
// BZMPop is the blocking variant of [Client.ZMPop] and [ClusterClient.ZMPop].
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - BZMPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys          - An array of keys to lists.
//	scoreFilter   - The element pop criteria - either [options.MIN] or [options.MAX] to pop members with the lowest/highest
//					scores accordingly.
//	count         - The maximum number of popped elements.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//	opts          - Pop options, see [options.ZMPopOptions].
//
// Return value:
//
//	An object containing the following elements:
//	- The key name of the set from which the element was popped.
//	- An array of member scores of the popped elements.
//	Returns `nil` if no member could be popped and the timeout expired.
//
// [valkey.io]: https://valkey.io/commands/bzmpop/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BZMPopWithOptions(
	ctx context.Context,
	keys []string,
	scoreFilter constants.ScoreFilter,
	timeout time.Duration,
	opts options.ZMPopOptions,
) (models.Result[models.KeyWithArrayOfMembersAndScores], error) {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-5 {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), errors.New(
			"length overflow for the provided keys",
		)
	}

	// args slice will have 5 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+5)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}
	args = append(args, optionArgs...)
	result, err := client.executeCommand(ctx, C.BZMPop, args)
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	return handleKeyWithArrayOfMembersAndScoresResponse(result)
}

// Returns the specified range of elements in the sorted set stored at `key`.
// `ZRANGE` can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
//
// To get the elements with their scores, see [Client.ZRangeWithScores] and [ClusterClient.ZRangeWithScores].
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	  - For range queries by index (rank), use [RangeByIndex].
//	  - For range queries by lexicographical order, use [RangeByLex].
//	  - For range queries by score, use [RangeByScore].
//
// Return value:
//
//	An array of elements within the specified range.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrange/
func (client *baseClient) ZRange(ctx context.Context, key string, rangeQuery options.ZRangeQuery) ([]string, error) {
	args := make([]string, 0, 10)
	args = append(args, key)
	queryArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, queryArgs...)
	result, err := client.executeCommand(ctx, C.ZRange, args)
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Returns the specified range of elements with their scores in the sorted set stored at `key`.
// `ZRANGE` can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	  - For range queries by index (rank), use [RangeByIndex].
//	  - For range queries by score, use [RangeByScore].
//
// Return value:
//
//	An array of elements and their scores within the specified range.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrange/
func (client *baseClient) ZRangeWithScores(
	ctx context.Context,
	key string,
	rangeQuery options.ZRangeQueryWithScores,
) ([]models.MemberAndScore, error) {
	args := make([]string, 0, 10)
	args = append(args, key)
	queryArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, queryArgs...)
	args = append(args, constants.WithScoresKeyword)
	result, err := client.executeCommand(ctx, C.ZRange, args)
	if err != nil {
		return nil, err
	}

	needsReverse := false
	for _, arg := range args {
		if arg == "REV" {
			needsReverse = true
			break
		}
	}

	return handleSortedSetWithScoresResponse(result, needsReverse)
}

// Stores a specified range of elements from the sorted set at `key`, into a new
// sorted set at `destination`. If `destination` doesn't exist, a new sorted
// set is created; if it exists, it's overwritten.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key for the destination sorted set.
//	key - The key of the source sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	 - For range queries by index (rank), use [RangeByIndex].
//	 - For range queries by lexicographical order, use [RangeByLex].
//	 - For range queries by score, use [RangeByScore].
//
// Return value:
//
//	The number of elements in the resulting sorted set.
//
// [valkey.io]: https://valkey.io/commands/zrangestore/
func (client *baseClient) ZRangeStore(
	ctx context.Context,
	destination string,
	key string,
	rangeQuery options.ZRangeQuery,
) (int64, error) {
	args := make([]string, 0, 10)
	args = append(args, destination)
	args = append(args, key)
	rqArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, rqArgs...)
	result, err := client.executeCommand(ctx, C.ZRangeStore, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}

	return handleIntResponse(result)
}

// Removes the existing timeout on key, turning the key from volatile
// (a key with an expire set) to persistent (a key that will never expire as no timeout is associated).
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to remove the existing timeout on.
//
// Return value:
//
//	`false` if key does not exist or does not have an associated timeout, `true` if the timeout has been removed.
//
// [valkey.io]: https://valkey.io/commands/persist/
func (client *baseClient) Persist(ctx context.Context, key string) (bool, error) {
	result, err := client.executeCommand(ctx, C.Persist, []string{key})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Returns the number of members in the sorted set stored at `key` with scores between `min` and `max` score.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the set.
//	rangeOptions - Contains `min` and `max` score. `min` contains the minimum score to count from.
//		`max` contains the maximum score to count up to. Can be positive/negative infinity, or
//		specific score and inclusivity.
//
// Return value:
//
//	The number of members in the specified score range.
//
// [valkey.io]: https://valkey.io/commands/zcount/
func (client *baseClient) ZCount(ctx context.Context, key string, rangeOptions options.ZCountRange) (int64, error) {
	zCountRangeArgs, err := rangeOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(ctx, C.ZCount, append([]string{key}, zCountRangeArgs...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the rank of `member` in the sorted set stored at `key`, with
// scores ordered from low to high, starting from `0`.
// To get the rank of `member` with its score, see [Client.ZRankWithScore] and [ClusterClient.ZRankWithScore].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Return value:
//
//	The rank of `member` in the sorted set.
//	If `key` doesn't exist, or if `member` is not present in the set, an empty [models.Result] will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrank/
func (client *baseClient) ZRank(ctx context.Context, key string, member string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.ZRank, []string{key, member})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	return handleIntOrNilResponse(result)
}

// Returns the rank of `member` in the sorted set stored at `key` with its
// score, where scores are ordered from the lowest to highest, starting from `0`.
//
// Since:
//
//	Valkey 7.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Return value:
//
//	A [models.Result[models.RankAndScore]] containing the rank of `member` and its score.
//	If `key` doesn't exist, or if `member` is not present in the set, an empty [models.Result] will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrank/
func (client *baseClient) ZRankWithScore(
	ctx context.Context,
	key string,
	member string,
) (models.Result[models.RankAndScore], error) {
	result, err := client.executeCommand(ctx, C.ZRank, []string{key, member, constants.WithScoreKeyword})
	if err != nil {
		return models.CreateNilRankAndScoreResult(), err
	}
	return handleRankAndScoreOrNilResponse(result)
}

// Returns the rank of `member` in the sorted set stored at `key`, where
// scores are ordered from the highest to lowest, starting from `0`.
// To get the rank of `member` with its score, see [Client.ZRevRankWithScore] and [ClusterClient.ZRevRankWithScore].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Return value:
//
//	The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
//	If `key` doesn't exist, or if `member` is not present in the set, an empty [models.Result] will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrevrank/
func (client *baseClient) ZRevRank(ctx context.Context, key string, member string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.ZRevRank, []string{key, member})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	return handleIntOrNilResponse(result)
}

// Returns the rank of `member` in the sorted set stored at `key`, where
// scores are ordered from the highest to lowest, starting from `0`.
//
// Since:
//
//	Valkey 7.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Return value:
//
//	A [models.Result[models.RankAndScore]] containing the rank of `member` and its score.
//	If `key` doesn't exist, or if `member` is not present in the set, an empty [models.Result] will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrevrank/
func (client *baseClient) ZRevRankWithScore(
	ctx context.Context,
	key string,
	member string,
) (models.Result[models.RankAndScore], error) {
	result, err := client.executeCommand(ctx, C.ZRevRank, []string{key, member, constants.WithScoreKeyword})
	if err != nil {
		return models.CreateNilRankAndScoreResult(), err
	}
	return handleRankAndScoreOrNilResponse(result)
}

// Trims the stream by evicting older entries.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the stream.
//	options - Stream trim options
//
// Return value:
//
//	The number of entries deleted from the stream.
//
// [valkey.io]: https://valkey.io/commands/xtrim/
func (client *baseClient) XTrim(ctx context.Context, key string, options options.XTrimOptions) (int64, error) {
	xTrimArgs, err := options.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(ctx, C.XTrim, append([]string{key}, xTrimArgs...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the number of entries in the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//
// Return value:
//
//	The number of entries in the stream. If `key` does not exist, return 0.
//
// [valkey.io]: https://valkey.io/commands/xlen/
func (client *baseClient) XLen(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.XLen, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//
// Return value:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - A array of the claimed entries as `[]models.StreamEntry`.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (client *baseClient) XAutoClaim(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
) (models.XAutoClaimResponse, error) {
	return client.XAutoClaimWithOptions(ctx, key, group, consumer, minIdleTime, start, *options.NewXAutoClaimOptions())
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//	options - Options detailing how to read the stream. Count has a default value of 100.
//
// Return value:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - A array of the claimed entries as `[]models.StreamEntry`.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (client *baseClient) XAutoClaimWithOptions(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
	options options.XAutoClaimOptions,
) (models.XAutoClaimResponse, error) {
	args := []string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds()), start}
	optArgs, err := options.ToArgs()
	if err != nil {
		return models.XAutoClaimResponse{}, err
	}
	args = append(args, optArgs...)
	result, err := client.executeCommand(ctx, C.XAutoClaim, args)
	if err != nil {
		return models.XAutoClaimResponse{}, err
	}
	return handleXAutoClaimResponse(result)
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//
// Return value:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - An array of IDs for the claimed entries.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (client *baseClient) XAutoClaimJustId(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
) (models.XAutoClaimJustIdResponse, error) {
	return client.XAutoClaimJustIdWithOptions(ctx, key, group, consumer, minIdleTime, start, *options.NewXAutoClaimOptions())
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//	opts - Options detailing how to read the stream. Count has a default value of 100.
//
// Return value:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - An array of IDs for the claimed entries.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (client *baseClient) XAutoClaimJustIdWithOptions(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
	opts options.XAutoClaimOptions,
) (models.XAutoClaimJustIdResponse, error) {
	args := []string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds()), start}
	optArgs, err := opts.ToArgs()
	if err != nil {
		return models.XAutoClaimJustIdResponse{}, err
	}
	args = append(args, optArgs...)
	args = append(args, constants.JustIdKeyword)
	result, err := client.executeCommand(ctx, C.XAutoClaim, args)
	if err != nil {
		return models.XAutoClaimJustIdResponse{}, err
	}
	return handleXAutoClaimJustIdResponse(result)
}

// Removes the specified entries by id from a stream, and returns the number of entries deleted.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	ids - An array of entry ids.
//
// Return value:
//
//	The number of entries removed from the stream. This number may be less than the number
//	of entries in `ids`, if the specified `ids` don't exist in the stream.
//
// [valkey.io]: https://valkey.io/commands/xdel/
func (client *baseClient) XDel(ctx context.Context, key string, ids []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.XDel, append([]string{key}, ids...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the score of `member` in the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member - The member whose score is to be retrieved.
//
// Return value:
//
//	The score of the member. If `member` does not exist in the sorted set, `nil` is returned.
//	If `key` does not exist, `nil` is returned.
//
// [valkey.io]: https://valkey.io/commands/zscore/
func (client *baseClient) ZScore(ctx context.Context, key string, member string) (models.Result[float64], error) {
	result, err := client.executeCommand(ctx, C.ZScore, []string{key, member})
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}
	return handleFloatOrNilResponse(result)
}

// Iterates incrementally over a sorted set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the sorted set held in `key`.
//	The array is a flattened series of `string` pairs, where the value is at even indices and the score is at odd indices.
//
// [valkey.io]: https://valkey.io/commands/zscan/
func (client *baseClient) ZScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error) {
	result, err := client.executeCommand(ctx, C.ZScan, []string{key, cursor.String()})
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Iterates incrementally over a sorted set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	cursor - The cursor that points to the next iteration of results.
//	options - The options for the command. See [options.ZScanOptions] for details.
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the sorted set held in `key`.
//	The array is a flattened series of `string` pairs, where the value is at even indices and the score is at odd indices.
//
// [valkey.io]: https://valkey.io/commands/zscan/
func (client *baseClient) ZScanWithOptions(
	ctx context.Context,
	key string,
	cursor models.Cursor,
	options options.ZScanOptions,
) (models.ScanResult, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.ScanResult{}, err
	}

	result, err := client.executeCommand(ctx, C.ZScan, append([]string{key, cursor.String()}, optionArgs...))
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(result)
}

// Returns stream message summary information for pending messages matching a stream and group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//
// Return value:
//
// An models.XPendingSummary struct that includes a summary with the following fields:
//
//	NumOfMessages - The total number of pending messages for this consumer group.
//	StartId - The smallest ID among the pending messages or nil if no pending messages exist.
//	EndId - The greatest ID among the pending messages or nil if no pending messages exists.
//	GroupConsumers - An array of ConsumerPendingMessages with the following fields:
//	ConsumerName - The name of the consumer.
//	MessageCount - The number of pending messages for this consumer.
//
// [valkey.io]: https://valkey.io/commands/xpending/
func (client *baseClient) XPending(ctx context.Context, key string, group string) (models.XPendingSummary, error) {
	result, err := client.executeCommand(ctx, C.XPending, []string{key, group})
	if err != nil {
		return models.XPendingSummary{}, err
	}

	return handleXPendingSummaryResponse(result)
}

// Returns stream message summary information for pending messages matching a given range of IDs.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	opts - The options for the command. See [options.XPendingOptions] for details.
//
// Return value:
//
// A slice of models.XPendingDetail structs, where each detail struct includes the following fields:
//
//	Id - The ID of the pending message.
//	ConsumerName - The name of the consumer that fetched the message and has still to acknowledge it.
//	IdleTime - The time in milliseconds since the last time the message was delivered to the consumer.
//	DeliveryCount - The number of times this message was delivered.
//
// [valkey.io]: https://valkey.io/commands/xpending/
func (client *baseClient) XPendingWithOptions(
	ctx context.Context,
	key string,
	group string,
	opts options.XPendingOptions,
) ([]models.XPendingDetail, error) {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group}, optionArgs...)

	result, err := client.executeCommand(ctx, C.XPending, args)
	if err != nil {
		return nil, err
	}
	return handleXPendingDetailResponse(result)
}

// Creates a new consumer group uniquely identified by `group` for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The newly created consumer group name.
//	id - Stream entry ID that specifies the last delivered entry in the stream from the new
//	    group’s perspective. The special ID `"$"` can be used to specify the last entry in the stream.
//
// Return value:
//
//	`"OK"`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-create/
func (client *baseClient) XGroupCreate(ctx context.Context, key string, group string, id string) (string, error) {
	return client.XGroupCreateWithOptions(ctx, key, group, id, *options.NewXGroupCreateOptions())
}

// Creates a new consumer group uniquely identified by `group` for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The newly created consumer group name.
//	id - Stream entry ID that specifies the last delivered entry in the stream from the new
//	    group's perspective. The special ID `"$"` can be used to specify the last entry in the stream.
//	opts - The options for the command. See [options.XGroupCreateOptions] for details.
//
// Return value:
//
//	`"OK"`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-create/
func (client *baseClient) XGroupCreateWithOptions(
	ctx context.Context,
	key string,
	group string,
	id string,
	opts options.XGroupCreateOptions,
) (string, error) {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group, id}, optionArgs...)
	result, err := client.executeCommand(ctx, C.XGroupCreate, args)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Creates a key associated with a value that is obtained by
// deserializing the provided serialized value (obtained via [Client.Dump] or [ClusterClient.Dump]).
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to create.
//	ttl - The expiry time. If 0, the key will persist.
//	value - The serialized value to deserialize and assign to key.
//
// Return value:
//
//	Return OK if successfully create a key with a value.
//
// [valkey.io]: https://valkey.io/commands/restore/
func (client *baseClient) Restore(ctx context.Context, key string, ttl time.Duration, value string) (string, error) {
	return client.RestoreWithOptions(ctx, key, ttl, value, *options.NewRestoreOptions())
}

// Creates a key associated with a value that is obtained by
// deserializing the provided serialized value (obtained via [Client.Dump] or [ClusterClient.Dump]).
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to create.
//	ttl - The expiry time. If 0, the key will persist.
//	value - The serialized value to deserialize and assign to key.
//	restoreOptions - Set restore options with replace and absolute TTL modifiers, object idletime and frequency.
//
// Return value:
//
//	Return OK if successfully create a key with a value.
//
// [valkey.io]: https://valkey.io/commands/restore/
func (client *baseClient) RestoreWithOptions(ctx context.Context, key string, ttl time.Duration,
	value string, options options.RestoreOptions,
) (string, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.DefaultStringResponse, err
	}
	result, err := client.executeCommand(ctx, C.Restore, append([]string{
		key,
		utils.IntToString(ttl.Milliseconds()), value,
	}, optionArgs...))
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Serializes the value stored at key in a Valkey-specific format.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to serialize.
//
// Return value:
//
//	The serialized value of the data stored at key.
//	If key does not exist, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/dump/
func (client *baseClient) Dump(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.Dump, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Returns the internal encoding for the Valkey object stored at key.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the object to get the internal encoding of.
//
// Return value:
//
//	If key exists, returns the internal encoding of the object stored at
//	key as a String. Otherwise, returns `null`.
//
// [valkey.io]: https://valkey.io/commands/object-encoding/
func (client *baseClient) ObjectEncoding(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.ObjectEncoding, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

func (client *baseClient) echo(ctx context.Context, message string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.Echo, []string{message})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Destroys the consumer group `group` for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name to delete.
//
// Return value:
//
//	`true` if the consumer group is destroyed. Otherwise, `false`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-destroy/
func (client *baseClient) XGroupDestroy(ctx context.Context, key string, group string) (bool, error) {
	result, err := client.executeCommand(ctx, C.XGroupDestroy, []string{key, group})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Sets the last delivered ID for a consumer group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	id - The stream entry ID that should be set as the last delivered ID for the consumer group.
//
// Return value:
//
//	`"OK"`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-setid/
func (client *baseClient) XGroupSetId(ctx context.Context, key string, group string, id string) (string, error) {
	return client.XGroupSetIdWithOptions(ctx, key, group, id, *options.NewXGroupSetIdOptionsOptions())
}

// Sets the last delivered ID for a consumer group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	id - The stream entry ID that should be set as the last delivered ID for the consumer group.
//	opts - The options for the command. See [options.XGroupSetIdOptions] for details.
//
// Return value:
//
//	`"OK"`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-setid/
func (client *baseClient) XGroupSetIdWithOptions(
	ctx context.Context,
	key string,
	group string,
	id string,
	opts options.XGroupSetIdOptions,
) (string, error) {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group, id}, optionArgs...)
	result, err := client.executeCommand(ctx, C.XGroupSetId, args)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Removes all elements in the sorted set stored at `key` with a lexicographical order
// between `rangeQuery.Start` and `rangeQuery.End`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the minimum and maximum bound of the lexicographical range.
//
// Return value:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `rangeQuery.Start` is greater than `rangeQuery.End`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebylex/
func (client *baseClient) ZRemRangeByLex(ctx context.Context, key string, rangeQuery options.RangeByLex) (int64, error) {
	queryArgs, err := rangeQuery.ToArgsRemRange()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(ctx,
		C.ZRemRangeByLex, append([]string{key}, queryArgs...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Removes all elements in the sorted set stored at `key` with a rank between `start` and `stop`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	start - The start rank.
//	stop - The stop rank.
//
// Return value:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `start` is greater than `stop`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebyrank/
func (client *baseClient) ZRemRangeByRank(ctx context.Context, key string, start int64, stop int64) (int64, error) {
	result, err := client.executeCommand(
		ctx,
		C.ZRemRangeByRank,
		[]string{key, utils.IntToString(start), utils.IntToString(stop)},
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Removes all elements in the sorted set stored at `key` with a score between `rangeQuery.Start` and `rangeQuery.End`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the minimum and maximum bound of the score range.
//	  can be an implementation of [options.RangeByScore].
//
// Return value:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `rangeQuery.Start` is greater than `rangeQuery.End`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebyscore/
func (client *baseClient) ZRemRangeByScore(ctx context.Context, key string, rangeQuery options.RangeByScore) (int64, error) {
	queryArgs, err := rangeQuery.ToArgsRemRange()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(ctx, C.ZRemRangeByScore, append([]string{key}, queryArgs...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns a random member from the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//
// Return value:
//
//	A string representing a random member from the sorted set.
//	If the sorted set does not exist or is empty, the response will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (client *baseClient) ZRandMember(ctx context.Context, key string) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.ZRandMember, []string{key})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Returns multiple random members from the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	count - The number of field names to return.
//	  If `count` is positive, returns unique elements. If negative, allows for duplicates.
//
// Return value:
//
//	An array of members from the sorted set.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (client *baseClient) ZRandMemberWithCount(ctx context.Context, key string, count int64) ([]string, error) {
	result, err := client.executeCommand(ctx, C.ZRandMember, []string{key, utils.IntToString(count)})
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns random members with scores from the sorted set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	count - The number of field names to return.
//	  If `count` is positive, returns unique elements. If negative, allows for duplicates.
//
// Return value:
//
//	An array of `models.MemberAndScore` objects, which store member names and their respective scores.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (client *baseClient) ZRandMemberWithCountWithScores(
	ctx context.Context,
	key string,
	count int64,
) ([]models.MemberAndScore, error) {
	result, err := client.executeCommand(
		ctx,
		C.ZRandMember,
		[]string{key, utils.IntToString(count), constants.WithScoresKeyword},
	)
	if err != nil {
		return nil, err
	}
	return handleMemberAndScoreArrayResponse(result)
}

// Returns the scores associated with the specified `members` in the sorted set stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	key     - The key of the sorted set.
//	members - A list of members in the sorted set.
//
// Return value:
//
//	An array of scores corresponding to `members`.
//	If a member does not exist in the sorted set, the corresponding value in the list will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/zmscore/
func (client *baseClient) ZMScore(ctx context.Context, key string, members []string) ([]models.Result[float64], error) {
	response, err := client.executeCommand(ctx, C.ZMScore, append([]string{key}, members...))
	if err != nil {
		return nil, err
	}
	return handleFloatOrNilArrayResponse(response)
}

// Returns the logarithmic access frequency counter of a Valkey object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the object to get the logarithmic access frequency counter of.
//
// Return value:
//
//	If key exists, returns the logarithmic access frequency counter of the
//	object stored at key as a long. Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-freq/
func (client *baseClient) ObjectFreq(ctx context.Context, key string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.ObjectFreq, []string{key})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	return handleIntOrNilResponse(result)
}

// Returns the logarithmic access frequency counter of a Valkey object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the object to get the logarithmic access frequency counter of.
//
// Return value:
//
//	If key exists, returns the idle time in seconds. Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-idletime/
func (client *baseClient) ObjectIdleTime(ctx context.Context, key string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.ObjectIdleTime, []string{key})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	return handleIntOrNilResponse(result)
}

// Returns the reference count of the object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the object to get the reference count of.
//
// Return value:
//
//	If key exists, returns the reference count of the object stored at key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-refcount/
func (client *baseClient) ObjectRefCount(ctx context.Context, key string) (models.Result[int64], error) {
	result, err := client.executeCommand(ctx, C.ObjectRefCount, []string{key})
	if err != nil {
		return models.CreateNilInt64Result(), err
	}
	return handleIntOrNilResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The sort command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To store the result into a new key, see the [Client.SortStore] or [ClusterClient.SortStore] function.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//
// Return value:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (client *baseClient) Sort(ctx context.Context, key string) ([]models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.Sort, []string{key})
	if err != nil {
		return nil, err
	}
	return handleStringOrNilArrayResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The sort command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To store the result into a new key, see the [Client.SortStoreWithOptions] or
// [ClusterClient.SortStoreWithOptions] function.
//
// Note:
//
//	In cluster mode, if `key` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//	The use of SortOptions.byPattern and SortOptions.getPatterns in cluster mode is
//	supported since Valkey version 8.0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//	sortOptions - The SortOptions type.
//
// Return value:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (client *baseClient) SortWithOptions(
	ctx context.Context,
	key string,
	options options.SortOptions,
) ([]models.Result[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.Sort, append([]string{key}, optionArgs...))
	if err != nil {
		return nil, err
	}
	return handleStringOrNilArrayResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The SortReadOnly command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// This command is routed depending on the client's ReadFrom strategy.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//
// Return value:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort_ro/
func (client *baseClient) SortReadOnly(ctx context.Context, key string) ([]models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.SortReadOnly, []string{key})
	if err != nil {
		return nil, err
	}
	return handleStringOrNilArrayResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The SortReadOnly command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// This command is routed depending on the client's ReadFrom strategy.
//
// Note:
//
//	In cluster mode, if `key` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//	The use of SortOptions.byPattern and SortOptions.getPatterns in cluster mode is
//	supported since Valkey version 8.0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//	sortOptions - The SortOptions type.
//
// Return value:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort_ro/
func (client *baseClient) SortReadOnlyWithOptions(
	ctx context.Context,
	key string,
	options options.SortOptions,
) ([]models.Result[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.SortReadOnly, append([]string{key}, optionArgs...))
	if err != nil {
		return nil, err
	}
	return handleStringOrNilArrayResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and stores the result in
// destination. The sort command can be used to sort elements based on
// different criteria, apply transformations on sorted elements, and store the result in a new key.
// The SortStore command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To get the sort result without storing it into a key, see the [Client.Sort] and [ClusterClient.Sort]
// or [Client.SortReadOnly] and [ClusterClient.SortReadOnly] function.
//
// Note:
//
//	In cluster mode, if `key` and `destination` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//	destination - The key where the sorted result will be stored.
//
// Return value:
//
//	The number of elements in the sorted key stored at destination.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (client *baseClient) SortStore(ctx context.Context, key string, destination string) (int64, error) {
	result, err := client.executeCommand(ctx, C.Sort, []string{key, constants.StoreKeyword, destination})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Sorts the elements in the list, set, or sorted set at key and stores the result in
// destination. The sort command can be used to sort elements based on
// different criteria, apply transformations on sorted elements, and store the result in a new key.
// The SortStore command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To get the sort result without storing it into a key, see the [Client.SortWithOptions] and [ClusterClient.SortWithOptions]
// or [Client.SortReadOnlyWithOptions] and [ClusterClient.SortReadOnlyWithOptions] function.
//
// Note:
//
//	In cluster mode, if `key` and `destination` map to different hash slots, the command
//	will be split across these slots and executed separately for each. This means the command
//	is atomic only at the slot level. If one or more slot-specific requests fail, the entire
//	call will return the first encountered error, even though some requests may have succeeded
//	while others did not. If this behavior impacts your application logic, consider splitting
//	the request into sub-requests per slot to ensure atomicity.
//	The use of SortOptions.byPattern and SortOptions.getPatterns
//	in cluster mode is supported since Valkey version 8.0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the list, set, or sorted set to be sorted.
//	destination - The key where the sorted result will be stored.
//
// opts - The [options.SortOptions] type.
//
// Return value:
//
//	The number of elements in the sorted key stored at destination.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (client *baseClient) SortStoreWithOptions(
	ctx context.Context,
	key string,
	destination string,
	opts options.SortOptions,
) (int64, error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(
		ctx,
		C.Sort,
		append([]string{key, constants.StoreKeyword, destination}, optionArgs...),
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// XGroupCreateConsumer creates a consumer named `consumer` in the consumer group `group` for the
// stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The newly created consumer.
//
// Return value:
//
//	Returns `true` if the consumer is created. Otherwise, returns `false`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-createconsumer/
func (client *baseClient) XGroupCreateConsumer(
	ctx context.Context,
	key string,
	group string,
	consumer string,
) (bool, error) {
	result, err := client.executeCommand(ctx, C.XGroupCreateConsumer, []string{key, group, consumer})
	if err != nil {
		return false, err
	}
	return handleBoolResponse(result)
}

// XGroupDelConsumer deletes a consumer named `consumer` in the consumer group `group`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The consumer to delete.
//
// Return value:
//
//	The number of pending messages the `consumer` had before it was deleted.
//
// [valkey.io]: https://valkey.io/commands/xgroup-delconsumer/
func (client *baseClient) XGroupDelConsumer(
	ctx context.Context,
	key string,
	group string,
	consumer string,
) (int64, error) {
	result, err := client.executeCommand(ctx, C.XGroupDelConsumer, []string{key, group, consumer})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the number of messages that were successfully acknowledged by the consumer group member
// of a stream. This command should be called on a pending message so that such message does not
// get processed again.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the stream.
//	group - he consumer group name.
//	ids   - Stream entry IDs to acknowledge and purge messages.
//
// Return value:
//
//	The number of messages that were successfully acknowledged.
//
// [valkey.io]: https://valkey.io/commands/xack/
func (client *baseClient) XAck(ctx context.Context, key string, group string, ids []string) (int64, error) {
	result, err := client.executeCommand(ctx, C.XAck, append([]string{key, group}, ids...))
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Sets or clears the bit at offset in the string value stored at key.
// The offset is a zero-based index, with `0` being the first element of
// the list, `1` being the next element, and so on. The offset must be
// less than `2^32` and greater than or equal to `0` If a key is
// non-existent then the bit at offset is set to value and the preceding
// bits are set to `0`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the string.
//	offset - The index of the bit to be set.
//	value - The bit value to set at offset The value must be `0` or `1`.
//
// Return value:
//
//	The bit value that was previously stored at offset.
//
// [valkey.io]: https://valkey.io/commands/setbit/
func (client *baseClient) SetBit(ctx context.Context, key string, offset int64, value int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.SetBit, []string{key, utils.IntToString(offset), utils.IntToString(value)})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the bit value at offset in the string value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the string.
//	offset - The index of the bit to return. Should be greater than or equal to zero.
//
// Return value:
//
//	The bit at offset of the string. Returns zero if the key is empty or if the positive
//	offset exceeds the length of the string.
//
// [valkey.io]: https://valkey.io/commands/getbit/
func (client *baseClient) GetBit(ctx context.Context, key string, offset int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.GetBit, []string{key, utils.IntToString(offset)})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Wait blocks the current client until all the previous write commands are successfully
// transferred and acknowledged by at least the specified number of replicas or if the timeout is reached,
// whichever is earlier.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	numberOfReplicas - The number of replicas to reach.
//	timeout - The timeout value. A value of `0` will block indefinitely.
//
// Return value:
//
//	The number of replicas reached by all the writes performed in the context of the current connection.
//
// [valkey.io]: https://valkey.io/commands/wait/
func (client *baseClient) Wait(ctx context.Context, numberOfReplicas int64, timeout time.Duration) (int64, error) {
	result, err := client.executeCommand(
		ctx,
		C.Wait,
		[]string{utils.IntToString(numberOfReplicas), utils.IntToString(timeout.Milliseconds())},
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Counts the number of set bits (population counting) in a string stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key for the string to count the set bits of.
//
// Return value:
//
//	The number of set bits in the string. Returns `0` if the key is missing as it is
//	treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (client *baseClient) BitCount(ctx context.Context, key string) (int64, error) {
	result, err := client.executeCommand(ctx, C.BitCount, []string{key})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Perform a bitwise operation between multiple keys (containing string values) and store the result in the destination.
//
// Note:
//
// When in cluster mode, `destination` and all `keys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx              - The context for controlling the command execution.
//	bitwiseOperation - The bitwise operation to perform.
//	destination      - The key that will store the resulting string.
//	keys             - The list of keys to perform the bitwise operation on.
//
// Return value:
//
//	The size of the string stored in destination.
//
// [valkey.io]: https://valkey.io/commands/bitop/
func (client *baseClient) BitOp(
	ctx context.Context,
	bitwiseOperation options.BitOpType,
	destination string,
	keys []string,
) (int64, error) {
	bitOp, err := options.NewBitOp(bitwiseOperation, destination, keys)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args, err := bitOp.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	result, err := client.executeCommand(ctx, C.BitOp, args)
	if err != nil {
		return models.DefaultIntResponse, errors.New("bitop command execution failed")
	}
	return handleIntResponse(result)
}

// Counts the number of set bits (population counting) in a string stored at key. The
// offsets start and end are zero-based indexes, with `0` being the first element of the
// list, `1` being the next element and so on. These offsets can also be negative numbers
// indicating offsets starting at the end of the list, with `-1` being the last element
// of the list, `-2` being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key for the string to count the set bits of.
//	options - The offset options - see [options.BitCountOptions].
//
// Return value:
//
//	The number of set bits in the string interval specified by start, end, and options.
//	Returns zero if the key is missing as it is treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (client *baseClient) BitCountWithOptions(ctx context.Context, key string, opts options.BitCountOptions) (int64, error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	commandArgs := append([]string{key}, optionArgs...)
	result, err := client.executeCommand(ctx, C.BitCount, commandArgs)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Changes the ownership of a pending message.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time.
//	ids         - The ids of the entries to claim.
//
// Return value:
//
//	A map[string]models.XClaimResponse where:
//	- Each key is a message/entry ID
//	- Each value is an XClaimResponse containing:
//	  - Fields: []FieldValue array of field-value pairs for the claimed entry
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (client *baseClient) XClaim(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
) (map[string]models.XClaimResponse, error) {
	return client.XClaimWithOptions(ctx, key, group, consumer, minIdleTime, ids, *options.NewXClaimOptions())
}

// Changes the ownership of a pending message.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time.
//	ids         - The ids of the entries to claim.
//	options     - Stream claim options.
//
// Return value:
//
//	A map[string]models.XClaimResponse where:
//	- Each key is a message/entry ID
//	- Each value is an XClaimResponse containing:
//	  - Fields: []FieldValue array of field-value pairs for the claimed entry
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (client *baseClient) XClaimWithOptions(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
	opts options.XClaimOptions,
) (map[string]models.XClaimResponse, error) {
	args := append([]string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds())}, ids...)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)
	result, err := client.executeCommand(ctx, C.XClaim, args)
	if err != nil {
		return nil, err
	}
	return handleXClaimResponse(result)
}

// Changes the ownership of a pending message. This function returns an `array` with
// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time.
//	ids         - The ids of the entries to claim.
//	options     - Stream claim options.
//
// Return value:
//
//	An array of the ids of the entries that were claimed by the consumer.
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (client *baseClient) XClaimJustId(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
) ([]string, error) {
	return client.XClaimJustIdWithOptions(ctx, key, group, consumer, minIdleTime, ids, *options.NewXClaimOptions())
}

// Changes the ownership of a pending message. This function returns an `array` with
// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time.
//	ids         - The ids of the entries to claim.
//	options     - Stream claim options.
//
// Return value:
//
//	An array of the ids of the entries that were claimed by the consumer.
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (client *baseClient) XClaimJustIdWithOptions(
	ctx context.Context,
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
	opts options.XClaimOptions,
) ([]string, error) {
	args := append([]string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds())}, ids...)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)
	args = append(args, constants.JustIdKeyword)
	result, err := client.executeCommand(ctx, C.XClaim, args)
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the position of the first bit matching the given bit value.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the string.
//	bit - The bit value to match. The value must be 0 or 1.
//
// Return value:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a -1 is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (client *baseClient) BitPos(ctx context.Context, key string, bit int64) (int64, error) {
	result, err := client.executeCommand(ctx, C.BitPos, []string{key, utils.IntToString(bit)})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the position of the first bit matching the given bit value.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the string.
//	bit - The bit value to match. The value must be 0 or 1.
//	bitposOptions - The [BitPosOptions] type.
//
// Return value:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a -1 is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (client *baseClient) BitPosWithOptions(
	ctx context.Context,
	key string,
	bit int64,
	bitposOptions options.BitPosOptions,
) (int64, error) {
	optionArgs, err := bitposOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	commandArgs := append([]string{key, utils.IntToString(bit)}, optionArgs...)
	result, err := client.executeCommand(ctx, C.BitPos, commandArgs)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Copies the value stored at the source to the destination key if the
// destination key does not yet exist.
//
// Note:
//
//	When in cluster mode, both `source` and `destination` must map to the same hash slot.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	source - The key to the source value.
//	destination - The key where the value should be copied to.
//
// Return value:
//
//	`true` if source was copied, `false` if source was not copied.
//
// [valkey.io]: https://valkey.io/commands/copy/
func (client *baseClient) Copy(ctx context.Context, source string, destination string) (bool, error) {
	result, err := client.executeCommand(ctx, C.Copy, []string{source, destination})
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Copies the value stored at the source to the destination key. When
// `replace` in `options` is `true`, removes the destination key first if it already
// exists, otherwise performs no action.
//
// Note:
//
//	When in cluster mode, both `source` and `destination` must map to the same hash slot.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	source - The key to the source value.
//	destination - The key where the value should be copied to.
//	copyOptions - Set copy options with replace and DB destination-db
//
// Return value:
//
//	`true` if source was copied, `false` if source was not copied.
//
// [valkey.io]: https://valkey.io/commands/copy/
func (client *baseClient) CopyWithOptions(
	ctx context.Context,
	source string,
	destination string,
	options options.CopyOptions,
) (bool, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	result, err := client.executeCommand(ctx, C.Copy, append([]string{
		source, destination,
	}, optionArgs...))
	if err != nil {
		return models.DefaultBoolResponse, err
	}
	return handleBoolResponse(result)
}

// Returns stream entries matching a given range of IDs.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//
// Return value:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//
// [valkey.io]: https://valkey.io/commands/xrange/
func (client *baseClient) XRange(
	ctx context.Context,
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
) ([]models.StreamEntry, error) {
	return client.XRangeWithOptions(ctx, key, start, end, *options.NewXRangeOptions())
}

// Returns stream entries matching a given range of IDs.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	opts  - Stream range options.
//
// Return value:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//	Returns `nil` if `count` is non-positive.
//
// [valkey.io]: https://valkey.io/commands/xrange/
func (client *baseClient) XRangeWithOptions(
	ctx context.Context,
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
	opts options.XRangeOptions,
) ([]models.StreamEntry, error) {
	args := []string{key, string(start), string(end)}
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)
	result, err := client.executeCommand(ctx, C.XRange, args)
	if err != nil {
		return nil, err
	}
	return handleXRangeResponse(result, false)
}

// Returns stream entries matching a given range of IDs in reverse order.
// Equivalent to `XRange` but returns entries in reverse order.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//
// Return value:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//
// [valkey.io]: https://valkey.io/commands/xrevrange/
func (client *baseClient) XRevRange(
	ctx context.Context,
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
) ([]models.StreamEntry, error) {
	return client.XRevRangeWithOptions(ctx, key, start, end, *options.NewXRangeOptions())
}

// Returns stream entries matching a given range of IDs in reverse order.
// Equivalent to `XRange` but returns entries in reverse order.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	opts  - Stream range options.
//
// Return value:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//	Returns `nil` if `count` is non-positive.
//
// [valkey.io]: https://valkey.io/commands/xrevrange/
func (client *baseClient) XRevRangeWithOptions(
	ctx context.Context,
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
	opts options.XRangeOptions,
) ([]models.StreamEntry, error) {
	args := []string{key, string(start), string(end)}
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)
	result, err := client.executeCommand(ctx, C.XRevRange, args)
	if err != nil {
		return nil, err
	}
	return handleXRangeResponse(result, true)
}

// Returns information about the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//
// Return value:
//
//	A [models.XInfoStreamResponse] containing information about the stream stored at key:
//	- Length: the number of entries in the stream
//	- RadixTreeKeys: the number of keys in the underlying radix data structure
//	- RadixTreeNodes: the number of nodes in the underlying radix data structure
//	- Groups: the number of consumer groups defined for the stream
//	- LastGeneratedID: the ID of the least-recently entry that was added to the stream
//	- MaxDeletedEntryID: the maximal entry ID that was deleted from the stream
//	- EntriesAdded: the count of all entries added to the stream during its lifetime
//	- FirstEntry: the ID and field-value tuples of the first entry in the stream
//	- LastEntry: the ID and field-value tuples of the last entry in the stream
//
// [valkey.io]: https://valkey.io/commands/xinfo-stream/
func (client *baseClient) XInfoStream(ctx context.Context, key string) (models.XInfoStreamResponse, error) {
	result, err := client.executeCommand(ctx, C.XInfoStream, []string{key})
	if err != nil {
		return models.XInfoStreamResponse{}, err
	}
	return handleXInfoStreamResponse(result)
}

// Returns detailed information about the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	key  - The key of the stream.
//	opts - Stream info options.
//
// Return value:
//
//	A detailed stream information for the given `key`. See the example for a sample response.
//
// [valkey.io]: https://valkey.io/commands/xinfo-stream/
func (client *baseClient) XInfoStreamFullWithOptions(
	ctx context.Context,
	key string,
	opts options.XInfoStreamOptions,
) (models.XInfoStreamFullOptionsResponse, error) {
	args := []string{key, constants.FullKeyword}
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return models.XInfoStreamFullOptionsResponse{}, err
	}
	args = append(args, optionArgs...)
	result, err := client.executeCommand(ctx, C.XInfoStream, args)
	if err != nil {
		return models.XInfoStreamFullOptionsResponse{}, err
	}
	return handleXInfoStreamFullOptionsResponse(result)
}

// Returns the list of all consumers and their attributes for the given consumer group of the
// stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	key   - The key of the stream.
//	group - The consumer group name.
//
// Return value:
//
//	An array of [models.XInfoConsumerInfo], where each element contains the attributes
//	of a consumer for the given consumer group of the stream at `key`.
//
// [valkey.io]: https://valkey.io/commands/xinfo-consumers/
func (client *baseClient) XInfoConsumers(ctx context.Context, key string, group string) ([]models.XInfoConsumerInfo, error) {
	response, err := client.executeCommand(ctx, C.XInfoConsumers, []string{key, group})
	if err != nil {
		return nil, err
	}
	return handleXInfoConsumersResponse(response)
}

// Returns the list of all consumer groups and their attributes for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the stream.
//
// Return value:
//
//	An array of [models.XInfoGroupInfo], where each element represents the
//	attributes of a consumer group for the stream at `key`.
//
// [valkey.io]: https://valkey.io/commands/xinfo-groups/
func (client *baseClient) XInfoGroups(ctx context.Context, key string) ([]models.XInfoGroupInfo, error) {
	response, err := client.executeCommand(ctx, C.XInfoGroups, []string{key})
	if err != nil {
		return nil, err
	}
	return handleXInfoGroupsResponse(response)
}

// Reads or modifies the array of bits representing the string that is held at key
// based on the specified sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx          - The context for controlling the command execution.
//	key          - The key of the string.
//	subCommands  - The subCommands to be performed on the binary value of the string at
//	                key, which could be any of the following:
//	                  - [BitFieldGet].
//	                  - [BitFieldSet].
//	                  - [BitFieldIncrby].
//	                  - [BitFieldOverflow].
//		            Use `options.NewBitFieldGet()` to specify a  BitField GET command.
//		            Use `options.NewBitFieldSet()` to specify a BitField SET command.
//		            Use `options.NewBitFieldIncrby()` to specify a BitField INCRYBY command.
//		            Use `options.BitFieldOverflow()` to specify a BitField OVERFLOW command.
//
// Return value:
//
//	Result from the executed subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//	  - BitFieldSet returns the previous value before setting the new value in the binary representation.
//	  - BitFieldIncrBy returns the updated value after increasing or decreasing the bits.
//	  - BitFieldOverflow controls the behavior of subsequent operations and returns
//	    a result based on the specified overflow type (WRAP, SAT, FAIL).
//
// [valkey.io]: https://valkey.io/commands/bitfield/
func (client *baseClient) BitField(
	ctx context.Context,
	key string,
	subCommands []options.BitFieldSubCommands,
) ([]models.Result[int64], error) {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range subCommands {
		cmdArgs, err := cmd.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, cmdArgs...)
	}

	result, err := client.executeCommand(ctx, C.BitField, args)
	if err != nil {
		return nil, err
	}
	return handleIntOrNilArrayResponse(result)
}

// Reads the array of bits representing the string that is held at key
// based on the specified  sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx          - The context for controlling the command execution.
//	key          - The key of the string.
//	subCommands  - The read-only subCommands to be performed on the binary value
//	               of the string at key, which could be:
//	                 - [BitFieldGet].
//		           Use `options.NewBitFieldGet()` to specify a BitField GET command.
//
// Return value:
//
//	Result from the executed GET subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//
// [valkey.io]: https://valkey.io/commands/bitfield_ro/
func (client *baseClient) BitFieldRO(
	ctx context.Context,
	key string,
	commands []options.BitFieldROCommands,
) ([]models.Result[int64], error) {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range commands {
		cmdArgs, err := cmd.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, cmdArgs...)
	}

	result, err := client.executeCommand(ctx, C.BitFieldReadOnly, args)
	if err != nil {
		return nil, err
	}
	return handleIntOrNilArrayResponse(result)
}

// Returns the server time.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	The current server time as a String array with two elements:
//	A UNIX TIME and the amount of microseconds already elapsed in the current second.
//	The returned array is in a [UNIX TIME, Microseconds already elapsed] format.
//
// [valkey.io]: https://valkey.io/commands/time/
func (client *baseClient) Time(ctx context.Context) ([]string, error) {
	result, err := client.executeCommand(ctx, C.Time, []string{})
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the intersection of members from sorted sets specified by the given `keys`.
// To get the elements with their scores, see [Client.ZInterWithScores] or [ClusterClient.ZInterWithScores].
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - The keys of the sorted sets, see - [options.KeyArray].
//
// Return value:
//
//	The resulting sorted set from the intersection.
//
// [valkey.io]: https://valkey.io/commands/zinter/
func (client *baseClient) ZInter(ctx context.Context, keys options.KeyArray) ([]string, error) {
	args, err := keys.ToArgs()
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.ZInter, args)
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the intersection of members and their scores from sorted sets specified by the given
// `keysOrWeightedKeys`.
//
// Note:
//
//	When in cluster mode, all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	options - The options for the ZInter command, see - [options.ZInterOptions].
//	           Optional `aggregate` option specifies the aggregation strategy to apply when combining the scores of
//	           elements.
//
// Return value:
//
//	An array of `models.MemberAndScore` objects, which store member names and their respective scores.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zinter/
func (client *baseClient) ZInterWithScores(
	ctx context.Context,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zInterOptions options.ZInterOptions,
) ([]models.MemberAndScore, error) {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return nil, err
	}
	optionsArgs, err := zInterOptions.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionsArgs...)
	args = append(args, constants.WithScoresKeyword)
	result, err := client.executeCommand(ctx, C.ZInter, args)
	if err != nil {
		return nil, err
	}
	return handleSortedSetWithScoresResponse(result, false)
}

// Computes the intersection of sorted sets given by the specified `keysOrWeightedKeys`
// and stores the result in `destination`. If `destination` already exists, it is overwritten.
// Otherwise, a new sorted set will be created.
//
// Note:
//
//	When in cluster mode, `destination` and all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The destination key for the result.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//
// Return value:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zinterstore/
func (client *baseClient) ZInterStore(
	ctx context.Context,
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
) (int64, error) {
	return client.ZInterStoreWithOptions(ctx, destination, keysOrWeightedKeys, *options.NewZInterOptions())
}

// Computes the intersection of sorted sets given by the specified `keysOrWeightedKeys`
// and stores the result in `destination`. If `destination` already exists, it is overwritten.
// Otherwise, a new sorted set will be created.
//
// Note:
//
//	When in cluster mode, `destination` and all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The destination key for the result.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	options - The options for the ZInterStore command, see - [options.ZInterOptions].
//	          Optional `aggregate` option specifies the aggregation strategy to apply when combining the scores of
//	          elements.
//
// Return value:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zinterstore/
func (client *baseClient) ZInterStoreWithOptions(
	ctx context.Context,
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zInterOptions options.ZInterOptions,
) (int64, error) {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append([]string{destination}, args...)
	optionsArgs, err := zInterOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, optionsArgs...)
	result, err := client.executeCommand(ctx, C.ZInterStore, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the difference between the first sorted set and all the successive sorted sets.
// To get the elements with their scores, see [Client.ZDiffWithScores] or [ClusterClient.ZDiffWithScores].
//
// Note:
//
//	When in cluster mode, all `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//
// Return value:
//
//	An array of elements representing the difference between the sorted sets.
//	If the first `key` does not exist, it is treated as an empty sorted set, and the
//	command returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zdiff/
func (client *baseClient) ZDiff(ctx context.Context, keys []string) ([]string, error) {
	args := append([]string{}, strconv.Itoa(len(keys)))
	result, err := client.executeCommand(ctx, C.ZDiff, append(args, keys...))
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the difference between the first sorted set and all the successive sorted sets.
//
// Note:
//
//	When in cluster mode, all `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//
// Return value:
//
//	An array of elements and their scores representing the difference between the sorted sets.
//	If the first `key` does not exist, it is treated as an empty sorted set, and the
//	command returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zdiff/
func (client *baseClient) ZDiffWithScores(ctx context.Context, keys []string) ([]models.MemberAndScore, error) {
	args := append([]string{}, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	result, err := client.executeCommand(ctx, C.ZDiff, append(args, constants.WithScoresKeyword))
	if err != nil {
		return nil, err
	}
	return handleSortedSetWithScoresResponse(result, false)
}

// Calculates the difference between the first sorted set and all the successive sorted sets at
// `keys` and stores the difference as a sorted set to `destination`,
// overwriting it if it already exists. Non-existent keys are treated as empty sets.
//
// Note:
//
//	When in cluster mode, `destination` and all `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx         - The context for controlling the command execution.
//	destination - The key for the resulting sorted set.
//	keys        - The keys of the sorted sets to compare.
//
// Return value:
//
//	The number of members in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zdiffstore/
func (client *baseClient) ZDiffStore(ctx context.Context, destination string, keys []string) (int64, error) {
	result, err := client.executeCommand(ctx,
		C.ZDiffStore,
		append([]string{destination, strconv.Itoa(len(keys))}, keys...),
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the union of members from sorted sets specified by the given `keys`.
// To get the elements with their scores, see [Client.ZUnionWithScores] or [ClusterClient.ZUnionWithScores].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
//	When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//
// Return Value:
//
//	The resulting sorted set from the union.
//
// [valkey.io]: https://valkey.io/commands/zunion/
func (client *baseClient) ZUnion(ctx context.Context, keys options.KeyArray) ([]string, error) {
	args, err := keys.ToArgs()
	if err != nil {
		return nil, err
	}
	result, err := client.executeCommand(ctx, C.ZUnion, args)
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the union of members and their scores from sorted sets specified by the given
// `keysOrWeightedKeys`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
//	When in cluster mode, all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keysOrWeightedKeys - The keys of the sorted sets with possible formats:
//	                     - Use `KeyArray` for keys only.
//	                     - Use `WeightedKeys` for weighted keys with score multipliers.
//	zUnionOptions - The options for the ZUnionStore command, see - [options.ZUnionOptions].
//	                Optional `aggregate` option specifies the aggregation strategy to apply when
//	                combining the scores of elements.
//
// Return Value:
//
//	The resulting sorted set from the union.
//
// [valkey.io]: https://valkey.io/commands/zunion/
func (client *baseClient) ZUnionWithScores(
	ctx context.Context,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zUnionOptions options.ZUnionOptions,
) ([]models.MemberAndScore, error) {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return nil, err
	}
	optionsArgs, err := zUnionOptions.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionsArgs...)
	args = append(args, constants.WithScoresKeyword)
	result, err := client.executeCommand(ctx, C.ZUnion, args)
	if err != nil {
		return nil, err
	}
	return handleSortedSetWithScoresResponse(result, false)
}

// Computes the union of sorted sets given by the specified `KeysOrWeightedKeys`, and
// stores the result in `destination`. If `destination` already exists, it
// is overwritten. Otherwise, a new sorted set will be created.
//
// Note:
//
//	When in cluster mode, `destination` and all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key of the destination sorted set.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                   - Use `options.NewKeyArray()` for keys only.
//	                   - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//
// Return Value:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zunionstore/
func (client *baseClient) ZUnionStore(
	ctx context.Context,
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
) (int64, error) {
	return client.ZUnionStoreWithOptions(ctx, destination, keysOrWeightedKeys, *options.NewZUnionOptions())
}

// Computes the union of sorted sets given by the specified `KeysOrWeightedKeys`, and
// stores the result in `destination`. If `destination` already exists, it
// is overwritten. Otherwise, a new sorted set will be created.
//
// Note:
//
//	When in cluster mode, `destination` and all keys in `keysOrWeightedKeys` must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destination - The key of the destination sorted set.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	zUnionOptions - The options for the ZUnionStore command, see - [options.ZUnionOptions].
//	                Optional `aggregate` option specifies the aggregation strategy to apply when
//	                combining the scores of elements.
//
// Return Value:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zunionstore/
func (client *baseClient) ZUnionStoreWithOptions(
	ctx context.Context,
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zUnionOptions options.ZUnionOptions,
) (int64, error) {
	keysArgs, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args := append([]string{destination}, keysArgs...)
	optionsArgs, err := zUnionOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, optionsArgs...)
	result, err := client.executeCommand(ctx, C.ZUnionStore, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the cardinality of the intersection of the sorted sets specified by `keys`.
//
// Note:
//
// When in cluster mode, all keys must map to the same hash slot.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//
// Return value:
//
//	The cardinality of the intersection of the sorted sets.
//
// [valkey.io]: https://valkey.io/commands/zintercard/
func (client *baseClient) ZInterCard(ctx context.Context, keys []string) (int64, error) {
	return client.ZInterCardWithOptions(ctx, keys, *options.NewZInterCardOptions())
}

// Returns the cardinality of the intersection of the sorted sets specified by `keys`.
// If the intersection cardinality reaches `options.limit` partway through the computation, the
// algorithm will exit early and yield `options.limit` as the cardinality.
//
// Note:
//
// When in cluster mode, all keys must map to the same hash slot.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - The keys of the sorted sets.
//	options - The options for the ZInterCard command, see - [options.ZInterCardOptions].
//
// Return value:
//
//	The cardinality of the intersection of the sorted sets.
//
// [valkey.io]: https://valkey.io/commands/zintercard/
func (client *baseClient) ZInterCardWithOptions(
	ctx context.Context,
	keys []string,
	options options.ZInterCardOptions,
) (int64, error) {
	args := append([]string{strconv.Itoa(len(keys))}, keys...)
	optionsArgs, err := options.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, optionsArgs...)
	result, err := client.executeCommand(ctx, C.ZInterCard, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the number of elements in the sorted set at key with a value between min and max.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	rangeQuery - The range query to apply to the sorted set.
//
// Return value:
//
//	The number of elements in the sorted set at key with a value between min and max.
//
// [valkey.io]: https://valkey.io/commands/zlexcount/
func (client *baseClient) ZLexCount(ctx context.Context, key string, rangeQuery options.RangeByLex) (int64, error) {
	args := []string{key}
	args = append(args, rangeQuery.ToArgsLexCount()...)
	result, err := client.executeCommand(ctx, C.ZLexCount, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Blocks the connection until it pops and returns a member-score pair
// with the highest score from the first non-empty sorted set.
// The given `keys` being checked in the order they are provided.
// `BZPopMax` is the blocking variant of [Client.ZPopMax] and [ClusterClient.ZPopMax].
//
// Note:
//   - When in cluster mode, all keys must map to the same hash slot.
//   - `BZPopMax` is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - An array of keys to check for elements.
//	timeout - The maximum number of seconds to block (0 blocks indefinitely).
//
// Return value:
//
//	A `models.KeyWithMemberAndScore` struct containing the key from which the member was popped,
//	the popped member, and its score. If no element could be popped and the timeout expired,
//	returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/bzpopmax/
// [Blocking Commands]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands
func (client *baseClient) BZPopMax(
	ctx context.Context,
	keys []string,
	timeout time.Duration,
) (models.Result[models.KeyWithMemberAndScore], error) {
	args := append(keys, utils.FloatToString(timeout.Seconds()))

	result, err := client.executeCommand(ctx, C.BZPopMax, args)
	if err != nil {
		return models.CreateNilKeyWithMemberAndScoreResult(), err
	}

	return handleKeyWithMemberAndScoreResponse(result)
}

// Removes and returns up to `count` members from the first non-empty sorted set
// among the provided `keys`, based on the specified `scoreFilter` criteria.
//
// Note:
//
// When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - A list of keys representing sorted sets to check for elements.
//	scoreFilter - Pop criteria - either [constants.MIN] or [constants.MAX] to pop members with the lowest/highest scores.
//	opts - Additional options, such as specifying the maximum number of elements to pop.
//
// Return value:
//
//	A `Result` containing a `models.KeyWithArrayOfMembersAndScores` object.
//	If no elements could be popped from the provided keys, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/zmpop/
func (client *baseClient) ZMPopWithOptions(
	ctx context.Context,
	keys []string,
	scoreFilter constants.ScoreFilter,
	opts options.ZMPopOptions,
) (models.Result[models.KeyWithArrayOfMembersAndScores], error) {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	optArgs, err := opts.ToArgs()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	args := append([]string{strconv.Itoa(len(keys))}, keys...)
	args = append(args, scoreFilterStr)
	args = append(args, optArgs...)

	result, err := client.executeCommand(ctx, C.ZMPop, args)
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	return handleKeyWithArrayOfMembersAndScoresResponse(result)
}

// Pops one or more member-score pairs from the first non-empty sorted set,
// with the given keys being checked in the order provided.
//
// Note:
//
// When in cluster mode, all keys must map to the same hash slot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	keys - An array of keys to check for elements.
//	scoreFilter - Pop criteria - either [constants.MIN] or [constants.MAX] to pop members with the lowest/highest scores.
//
// Return value:
//
//	A `models.KeyWithArrayOfMembersAndScores` struct containing:
//	- The key from which the elements were popped.
//	- An array of member-score pairs of the popped elements.
//	  Returns `nil` if no member could be popped.
//
// [valkey.io]: https://valkey.io/commands/zmpop/
func (client *baseClient) ZMPop(
	ctx context.Context,
	keys []string,
	scoreFilter constants.ScoreFilter,
) (models.Result[models.KeyWithArrayOfMembersAndScores], error) {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	args := make([]string, 0, len(keys)+3)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)

	result, err := client.executeCommand(ctx, C.ZMPop, args)
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	return handleKeyWithArrayOfMembersAndScoresResponse(result)
}

// Adds geospatial members with their positions to the specified sorted set stored at `key`.
// If a member is already a part of the sorted set, its position is updated.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	membersToGeospatialData - A map of member names to their corresponding positions. See [options.GeospatialData].
//	  The command will report an error when index coordinates are out of the specified range.
//
// Return value:
//
//	The number of elements added to the sorted set.
//
// [valkey.io]: https://valkey.io/commands/geoadd/
func (client *baseClient) GeoAdd(
	ctx context.Context,
	key string,
	membersToGeospatialData map[string]options.GeospatialData,
) (int64, error) {
	result, err := client.executeCommand(ctx,
		C.GeoAdd,
		append([]string{key}, options.MapGeoDataToArray(membersToGeospatialData)...),
	)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Adds geospatial members with their positions to the specified sorted set stored at `key`.
// If a member is already a part of the sorted set, its position is updated.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	membersToGeospatialData - A map of member names to their corresponding positions. See [options.GeospatialData].
//	  The command will report an error when index coordinates are out of the specified range.
//	geoAddOptions - The options for the GeoAdd command, see - [options.GeoAddOptions].
//
// Return value:
//
//	The number of elements added to the sorted set.
//
// [valkey.io]: https://valkey.io/commands/geoadd/
func (client *baseClient) GeoAddWithOptions(
	ctx context.Context,
	key string,
	membersToGeospatialData map[string]options.GeospatialData,
	geoAddOptions options.GeoAddOptions,
) (int64, error) {
	args := []string{key}
	optionsArgs, err := geoAddOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, optionsArgs...)
	args = append(args, options.MapGeoDataToArray(membersToGeospatialData)...)
	result, err := client.executeCommand(ctx, C.GeoAdd, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns the GeoHash strings representing the positions of all the specified
// `members` in the sorted set stored at the `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key -  The key of the sorted set.
//	members - The array of members whose GeoHash strings are to be retrieved.
//
// Returns value:
//
//	An array of GeoHash strings (of type models.Result[string]) representing the positions of the specified
//	members stored at key. If a member does not exist in the sorted set, a `nil` value is returned
//	for that member.
//
// [valkey.io]: https://valkey.io/commands/geohash/
func (client *baseClient) GeoHash(ctx context.Context, key string, members []string) ([]models.Result[string], error) {
	result, err := client.executeCommand(ctx,
		C.GeoHash,
		append([]string{key}, members...),
	)
	if err != nil {
		return nil, err
	}
	return handleStringOrNilArrayResponse(result)
}

// Returns the positions (longitude,latitude) of all the specified members of the
// geospatial index represented by the sorted set at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	members - The members of the sorted set.
//
// Return value:
//
//	A 2D `array` which represent positions (longitude and latitude) corresponding to the given members.
//	If a member does not exist, its position will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/geopos/
func (client *baseClient) GeoPos(ctx context.Context, key string, members []string) ([][]float64, error) {
	args := []string{key}
	args = append(args, members...)
	result, err := client.executeCommand(ctx, C.GeoPos, args)
	if err != nil {
		return nil, err
	}
	return handle2DFloat64OrNullArrayResponse(result)
}

// Returns the distance between `member1` and `member2` saved in the
// geospatial index stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member1 - The name of the first member.
//	member2 - The name of the second member.
//
// Return value:
//
//	The distance between `member1` and `member2`. If one or both members do not exist,
//	or if the key does not exist, returns `nil`. The default unit is meters, see - [options.Meters]
//
// [valkey.io]: https://valkey.io/commands/geodist/
func (client *baseClient) GeoDist(
	ctx context.Context,
	key string,
	member1 string,
	member2 string,
) (models.Result[float64], error) {
	result, err := client.executeCommand(ctx,
		C.GeoDist,
		[]string{key, member1, member2},
	)
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}
	return handleFloatOrNilResponse(result)
}

// Returns the distance between `member1` and `member2` saved in the
// geospatial index stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	member1 - The name of the first member.
//	member2 - The name of the second member.
//	unit - The unit of distance measurement - see [options.GeoUnit].
//
// Return value:
//
//	The distance between `member1` and `member2`. If one or both members
//	do not exist, or if the key does not exist, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/geodist/
func (client *baseClient) GeoDistWithUnit(
	ctx context.Context,
	key string,
	member1 string,
	member2 string,
	unit constants.GeoUnit,
) (models.Result[float64], error) {
	result, err := client.executeCommand(ctx,
		C.GeoDist,
		[]string{key, member1, member2, string(unit)},
	)
	if err != nil {
		return models.CreateNilFloat64Result(), err
	}
	return handleFloatOrNilResponse(result)
}

// Returns the members of a sorted set populated with geospatial information using [Client.GeoAdd] or [ClusterClient.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//	infoOptions - The optional inputs to request additional information.
//
// Return value:
//
//	An array of [options.Location] containing the following information:
//	 - The coordinates as a [options.GeospatialData] object.
//	 - The member (location) name.
//	 - The distance from the center as a `float64`, in the same unit specified for `searchByShape`.
//	 - The geohash of the location as a `int64`.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (client *baseClient) GeoSearchWithFullOptions(
	ctx context.Context,
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
	infoOptions options.GeoSearchInfoOptions,
) ([]options.Location, error) {
	args := []string{key}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, searchByShapeArgs...)
	infoOptionsArgs, err := infoOptions.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, infoOptionsArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, resultOptionsArgs...)
	result, err := client.executeCommand(ctx, C.GeoSearch, args)
	if err != nil {
		return nil, err
	}
	return handleLocationArrayResponse(result)
}

// Returns the members of a sorted set populated with geospatial information using [Client.GeoAdd] or [ClusterClient.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//
// Return value:
//
//	An array of matched member names.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (client *baseClient) GeoSearchWithResultOptions(
	ctx context.Context,
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
) ([]string, error) {
	args := []string{key}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, searchByShapeArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, resultOptionsArgs...)

	result, err := client.executeCommand(ctx, C.GeoSearch, args)
	if err != nil {
		return nil, err
	}
	return handleStringArrayResponse(result)
}

// Returns the members of a sorted set populated with geospatial information using [Client.GeoAdd] or [ClusterClient.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	infoOptions - The optional inputs to request additional information.
//
// Return value:
//
//	An array of [options.Location] containing the following information:
//	 - The coordinates as a [options.GeospatialData] object.
//	 - The member (location) name.
//	 - The distance from the center as a `float64`, in the same unit specified for
//	   `searchByShape`.
//	 - The geohash of the location as a `int64`.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (client *baseClient) GeoSearchWithInfoOptions(
	ctx context.Context,
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	infoOptions options.GeoSearchInfoOptions,
) ([]options.Location, error) {
	return client.GeoSearchWithFullOptions(
		ctx,
		key,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		infoOptions,
	)
}

// Returns the members of a sorted set populated with geospatial information using [Client.GeoAdd] or [ClusterClient.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//
// Return value:
//
//	An array of matched member names.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (client *baseClient) GeoSearch(
	ctx context.Context,
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
) ([]string, error) {
	return client.GeoSearchWithResultOptions(
		ctx,
		key,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [Client.GeoSearchWithFullOptions] or [ClusterClient.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
// When in cluster mode, `destinationKey` and `sourceKey` must map to the same hash slot.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//	infoOptions - The optional inputs to request additional information.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (client *baseClient) GeoSearchStoreWithFullOptions(
	ctx context.Context,
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
	infoOptions options.GeoSearchStoreInfoOptions,
) (int64, error) {
	args := []string{destinationKey, sourceKey}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, searchByShapeArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, resultOptionsArgs...)
	infoOptionsArgs, err := infoOptions.ToArgs()
	if err != nil {
		return models.DefaultIntResponse, err
	}
	args = append(args, infoOptionsArgs...)

	result, err := client.executeCommand(ctx, C.GeoSearchStore, args)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [Client.GeoSearchWithFullOptions] or [ClusterClient.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
// When in cluster mode, `destinationKey` and `sourceKey` must map to the same hash slot.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted
//	          set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (client *baseClient) GeoSearchStore(
	ctx context.Context,
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
) (int64, error) {
	return client.GeoSearchStoreWithFullOptions(
		ctx,
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		*options.NewGeoSearchStoreInfoOptions(),
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [Client.GeoSearchWithFullOptions] or [ClusterClient.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
// When in cluster mode, `destinationKey` and `sourceKey` must map to the same hash slot.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted
//	          set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (client *baseClient) GeoSearchStoreWithResultOptions(
	ctx context.Context,
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
) (int64, error) {
	return client.GeoSearchStoreWithFullOptions(
		ctx,
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		resultOptions,
		*options.NewGeoSearchStoreInfoOptions(),
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [Client.GeoSearchWithFullOptions] or [ClusterClient.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Note:
//
// When in cluster mode, `destinationKey` and `sourceKey` must map to the same hash slot.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted
//	          set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	infoOptions - The optional inputs to request additional information.
//
// Return value:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (client *baseClient) GeoSearchStoreWithInfoOptions(
	ctx context.Context,
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	infoOptions options.GeoSearchStoreInfoOptions,
) (int64, error) {
	return client.GeoSearchStoreWithFullOptions(
		ctx,
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		infoOptions,
	)
}

// Loads a library to Valkey.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	libraryCode - The source code that implements the library.
//	replace - Whether the given library should overwrite a library with the same name if it already exists.
//
// Return value:
//
//	The library name that was loaded.
//
// [valkey.io]: https://valkey.io/commands/function-load/
func (client *baseClient) FunctionLoad(ctx context.Context, libraryCode string, replace bool) (string, error) {
	args := []string{}
	if replace {
		args = append(args, constants.ReplaceKeyword)
	}
	args = append(args, libraryCode)
	result, err := client.executeCommand(ctx, C.FunctionLoad, args)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all function libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *baseClient) FunctionFlush(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionFlush, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all function libraries in synchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *baseClient) FunctionFlushSync(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionFlush, []string{string(options.SYNC)})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all function libraries in asynchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *baseClient) FunctionFlushAsync(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionFlush, []string{string(options.ASYNC)})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Invokes a previously loaded function.
// The command will be routed to a primary random node.
// To route to a replica please refer to [Client.FCallReadOnly] or [ClusterClient.FCallReadOnly].
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (client *baseClient) FCall(ctx context.Context, function string) (any, error) {
	result, err := client.executeCommand(ctx, C.FCall, []string{function, utils.IntToString(0)})
	if err != nil {
		return nil, err
	}
	return handleAnyResponse(result)
}

// Invokes a previously loaded read-only function.
// This command is routed depending on the client's {@link ReadFrom} strategy.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (client *baseClient) FCallReadOnly(ctx context.Context, function string) (any, error) {
	result, err := client.executeCommand(ctx, C.FCallReadOnly, []string{function, utils.IntToString(0)})
	if err != nil {
		return nil, err
	}
	return handleAnyResponse(result)
}

// Invokes a previously loaded function.
// This command is routed to primary nodes only.
// To route to a replica please refer to [Client.FCallReadOnly] or [ClusterClient.FCallReadOnly].
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	keys - An `array` of keys accessed by the function. To ensure the correct
//	   execution of functions, both in standalone and clustered deployments, all names of keys
//	   that a function accesses must be explicitly provided as `keys`.
//	arguments - An `array` of `function` arguments. `arguments` should not represent names of keys.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (client *baseClient) FCallWithKeysAndArgs(
	ctx context.Context,
	function string,
	keys []string,
	args []string,
) (any, error) {
	cmdArgs := []string{function, utils.IntToString(int64(len(keys)))}
	cmdArgs = append(cmdArgs, keys...)
	cmdArgs = append(cmdArgs, args...)
	result, err := client.executeCommand(ctx, C.FCall, cmdArgs)
	if err != nil {
		return nil, err
	}
	return handleAnyResponse(result)
}

// Invokes a previously loaded read-only function.
// This command is routed depending on the client's {@link ReadFrom} strategy.
//
// Note: When in cluster mode, all `keys` must map to the same hash slot.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	keys - An `array` of keys accessed by the function. To ensure the correct
//	   execution of functions, both in standalone and clustered deployments, all names of keys
//	   that a function accesses must be explicitly provided as `keys`.
//	arguments - An `array` of `function` arguments. `arguments` should not represent names of keys.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (client *baseClient) FCallReadOnlyWithKeysAndArgs(
	ctx context.Context,
	function string,
	keys []string,
	args []string,
) (any, error) {
	cmdArgs := []string{function, utils.IntToString(int64(len(keys)))}
	cmdArgs = append(cmdArgs, keys...)
	cmdArgs = append(cmdArgs, args...)
	result, err := client.executeCommand(ctx, C.FCallReadOnly, cmdArgs)
	if err != nil {
		return nil, err
	}
	return handleAnyResponse(result)
}

// Lists the currently active channels.
//
// When used in cluster mode, the command is routed to all nodes and aggregates
// the responses into a single array.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	An array of active channel names.
//
// [valkey.io]: https://valkey.io/commands/pubsub-channels
func (client *baseClient) PubSubChannels(ctx context.Context) ([]string, error) {
	result, err := client.executeCommand(ctx, C.PubSubChannels, []string{})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Lists the currently active channels matching the specified pattern.
//
// Pattern can be any glob-style pattern:
// - h?llo matches hello, hallo and hxllo
// - h*llo matches hllo and heeeello
// - h[ae]llo matches hello and hallo, but not hillo
//
// When used in cluster mode, the command is routed to all nodes and aggregates
// the responses into a single array.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	pattern - The pattern to match channel names against.
//
// Return value:
//
//	An array of active channel names matching the pattern.
//
// [valkey.io]: https://valkey.io/commands/pubsub-channels
func (client *baseClient) PubSubChannelsWithPattern(ctx context.Context, pattern string) ([]string, error) {
	args := []string{pattern}
	result, err := client.executeCommand(ctx, C.PubSubChannels, args)
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Returns the number of patterns that are subscribed to by clients.
//
// This returns the total number of unique patterns that all clients are subscribed to,
// not the count of clients subscribed to patterns.
//
// When used in cluster mode, the command is routed to all nodes and aggregates
// the responses.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	The number of patterns that are subscribed to by clients.
//
// [valkey.io]: https://valkey.io/commands/pubsub-numpat
func (client *baseClient) PubSubNumPat(ctx context.Context) (int64, error) {
	result, err := client.executeCommand(ctx, C.PubSubNumPat, []string{})
	if err != nil {
		return 0, err
	}

	return handleIntResponse(result)
}

// Returns the number of subscribers for the specified channels.
//
// The count only includes clients subscribed to exact channels, not pattern subscriptions.
// If no channels are specified, an empty map is returned.
//
// When used in cluster mode, the command is routed to all nodes and aggregates
// the responses into a single map.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	channels - The channel names to get subscriber counts for.
//
// Return value:
//
//	A map of channel names to their subscriber counts.
//
// [valkey.io]: https://valkey.io/commands/pubsub-numsub
func (client *baseClient) PubSubNumSub(ctx context.Context, channels ...string) (map[string]int64, error) {
	result, err := client.executeCommand(ctx, C.PubSubNumSub, channels)
	if err != nil {
		return nil, err
	}

	return handleStringIntMapResponse(result)
}

// Executes a Lua script on the server.
//
// This function simplifies the process of invoking scripts on the server by using an object that
// represents a Lua script. The script loading and execution will all be handled internally. If
// the script has not already been loaded, it will be loaded automatically using the
// `SCRIPT LOAD` command. After that, it will be invoked using the `EVALSHA`
// command.
//
// See [LOAD] and [EVALSHA] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	script - The Lua script to execute.
//
// Return value:
//
//	The result of the script execution.
//
// [LOAD]: https://valkey.io/commands/script-load/
// [EVALSHA]: https://valkey.io/commands/evalsha/
func (client *baseClient) InvokeScript(ctx context.Context, script options.Script) (any, error) {
	response, err := client.executeScriptWithRoute(ctx, script.GetHash(), []string{}, []string{}, nil)
	if err != nil {
		return nil, err
	}

	return handleAnyResponse(response)
}

// Executes a Lua script on the server with additional options.
//
// This function simplifies the process of invoking scripts on the server by using an object that
// represents a Lua script. The script loading, argument preparation, and execution will all be
// handled internally. If the script has not already been loaded, it will be loaded automatically
// using the `SCRIPT LOAD` command. After that, it will be invoked using the
// `EVALSHA` command.
//
// Note:
//
//	When in cluster mode:
//	- all `keys` in `scriptOptions` must map to the same hash slot.
//	- if no `keys` are given, command will be routed to a random primary node.
//
// See [LOAD] and [EVALSHA] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	script - The Lua script to execute.
//	scriptOptions - Options for script execution including keys and arguments.
//
// Return value:
//
//	The result of the script execution.
//
// [LOAD]: https://valkey.io/commands/script-load/
// [EVALSHA]: https://valkey.io/commands/evalsha/
func (client *baseClient) InvokeScriptWithOptions(
	ctx context.Context,
	script options.Script,
	scriptOptions options.ScriptOptions,
) (any, error) {
	keys := scriptOptions.Keys
	args := scriptOptions.Args

	response, err := client.executeScriptWithRoute(ctx, script.GetHash(), keys, args, nil)
	if err != nil {
		return nil, err
	}

	return handleAnyResponse(response)
}

// executeScriptWithRoute executes a Lua script with the given hash, keys, args, and routing information.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	hash - The SHA1 hash of the script to execute.
//	keys - The keys that the script will access.
//	args - The arguments to pass to the script.
//	route - Optional routing information for the script execution.
//
// Return value:
//
//	A CommandResponse containing the result of the script execution.
func (client *baseClient) executeScriptWithRoute(
	ctx context.Context,
	hash string,
	keys []string,
	args []string,
	route config.Route,
) (*C.struct_CommandResponse, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
		// Continue with execution
	}
	var cKeysPtr *C.uintptr_t = nil
	var keysLengthsPtr *C.ulong = nil
	if len(keys) > 0 {
		cKeys, keysLengths := toCStrings(keys)
		cKeysPtr = &cKeys[0]
		keysLengthsPtr = &keysLengths[0]
	}

	var cArgsPtr *C.uintptr_t = nil
	var argsLengthsPtr *C.ulong = nil
	if len(args) > 0 {
		cArgs, argsLengths := toCStrings(args)
		cArgsPtr = &cArgs[0]
		argsLengthsPtr = &argsLengths[0]
	}

	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.uintptr_t = 0
	if route != nil {
		routeProto, err := routeToProtobuf(route)
		if err != nil {
			return nil, errors.New("ExecuteScript failed due to invalid route")
		}
		msg, err := proto.Marshal(routeProto)
		if err != nil {
			return nil, err
		}

		routeBytesCount = C.uintptr_t(len(msg))
		routeCBytes := C.CBytes(msg)
		defer C.free(routeCBytes)
		routeBytesPtr = (*C.uchar)(routeCBytes)
	}

	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, NewClosingError("ExecuteScript failed. The client is closed.")
	}
	client.pending[resultChannelPtr] = struct{}{}
	hash_cstring := C.CString(hash)
	defer C.free(unsafe.Pointer(hash_cstring))
	C.invoke_script(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		hash_cstring,
		C.size_t(len(keys)),
		cKeysPtr,
		keysLengthsPtr,
		C.size_t(len(args)),
		cArgsPtr,
		argsLengthsPtr,
		routeBytesPtr,
		routeBytesCount,
	)
	client.mu.Unlock()

	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return nil, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}
	return payload.value, nil
}

// Checks existence of scripts in the script cache by their SHA1 digest.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	sha1s - SHA1 digests of Lua scripts to be checked.
//
// Return value:
//
//	An array of boolean values indicating the existence of each script.
//
// [valkey.io]: https://valkey.io/commands/script-exists
func (client *baseClient) ScriptExists(ctx context.Context, sha1s []string) ([]bool, error) {
	response, err := client.executeCommand(ctx, C.ScriptExists, sha1s)
	if err != nil {
		return nil, err
	}

	return handleBoolArrayResponse(response)
}

// Removes all the scripts from the script cache.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (client *baseClient) ScriptFlush(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.ScriptFlush, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Removes all the scripts from the script cache with the specified flush mode.
// The mode can be either SYNC or ASYNC.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	mode - The flush mode (SYNC or ASYNC).
//
// Return value:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (client *baseClient) ScriptFlushWithMode(ctx context.Context, mode options.FlushMode) (string, error) {
	result, err := client.executeCommand(ctx, C.ScriptFlush, []string{string(mode)})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// ScriptShow returns the original source code of a script in the script cache.
//
// Since:
//
//	Valkey 8.0.0
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	sha1 - The SHA1 digest of the script.
//
// Return value:
//
//	The original source code of the script, if present in the cache.
//	If the script is not found in the cache, an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/script-show
func (client *baseClient) ScriptShow(ctx context.Context, sha1 string) (string, error) {
	result, err := client.executeCommand(ctx, C.ScriptShow, []string{sha1})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Kills the currently executing Lua script, assuming no write operation was yet performed by the
// script.
//
// Note:
//
//	When in cluster mode, this command will be routed to all nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`OK` if script is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/script-kill
func (client *baseClient) ScriptKill(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.ScriptKill, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Marks the given keys to be watched for conditional execution of an atomic batch (Transaction).
// Transactions will only execute commands if the watched keys are not modified before execution of the
// transaction.
//
// See [valkey.io] and [Valkey Glide Wiki] for details.
//
// Note:
//
//	In cluster mode, if keys in `keys` map to different hash slots,
//	the command will be split across these slots and executed separately for each.
//	This means the command is atomic only at the slot level. If one or more slot-specific
//	requests fail, the entire call will return the first encountered error, even
//	though some requests may have succeeded while others did not.
//	If this behavior impacts your application logic, consider splitting the
//	request into sub-requests per slot to ensure atomicity.
//
// Parameters:
//
//	ctx  - The context for controlling the command execution.
//	keys - The keys to watch.
//
// Return value:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/watch
// [Valkey Glide Wiki]: https://valkey.io/topics/transactions/#cas
func (client *baseClient) Watch(ctx context.Context, keys []string) (string, error) {
	result, err := client.executeCommand(ctx, C.Watch, keys)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}
