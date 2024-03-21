// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
//
// void successCallback(void *channelPtr, char *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
	"unsafe"

	"github.com/aws/glide-for-redis/go/glide/protobuf"
	"google.golang.org/protobuf/proto"
)

// BaseClient defines an interface for methods common to both [RedisClient] and [RedisClusterClient].
type BaseClient interface {
	StringCommands

	// Close terminates the client by closing all associated resources.
	Close()
}

const OK = "OK"

type payload struct {
	value string
	error error
}

//export successCallback
func successCallback(channelPtr unsafe.Pointer, cResponse *C.char) {
	// TODO: call lib.rs function to free response
	response := C.GoString(cResponse)
	resultChannel := *(*chan payload)(channelPtr)
	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	// TODO: call lib.rs function to free response
	resultChannel := *(*chan payload)(channelPtr)
	resultChannel <- payload{value: "", error: goError(cErrorType, cErrorMessage)}
}

type clientConfiguration interface {
	toProtobuf() *protobuf.ConnectionRequest
}

type baseClient struct {
	coreClient unsafe.Pointer
}

func createClient(config clientConfiguration) (*baseClient, error) {
	request := config.toProtobuf()
	msg, err := proto.Marshal(request)
	if err != nil {
		return nil, err
	}

	byteCount := len(msg)
	requestBytes := C.CBytes(msg)
	cResponse := (*C.struct_ConnectionResponse)(
		C.create_client(
			(*C.uchar)(requestBytes),
			C.uintptr_t(byteCount),
			(C.SuccessCallback)(unsafe.Pointer(C.successCallback)),
			(C.FailureCallback)(unsafe.Pointer(C.failureCallback)),
		),
	)
	defer C.free_connection_response(cResponse)

	cErr := cResponse.connection_error_message
	if cErr != nil {
		message := C.GoString(cErr)
		return nil, &ConnectionError{message}
	}

	return &baseClient{cResponse.conn_ptr}, nil
}

// Close terminates the client by closing all associated resources.
func (client *baseClient) Close() {
	if client.coreClient == nil {
		return
	}

	C.close_client(client.coreClient)
	client.coreClient = nil
}

func (client *baseClient) executeCommand(requestType C.RequestType, args []string) (interface{}, error) {
	if client.coreClient == nil {
		return nil, &ClosingError{"The client is closed."}
	}

	cArgs := toCStrings(args)
	defer freeCStrings(cArgs)

	resultChannel := make(chan payload)
	resultChannelPtr := uintptr(unsafe.Pointer(&resultChannel))

	C.command(client.coreClient, C.uintptr_t(resultChannelPtr), requestType, C.uintptr_t(len(args)), &cArgs[0])

	payload := <-resultChannel
	if payload.error != nil {
		return nil, payload.error
	}

	return payload.value, nil
}

func toCStrings(args []string) []*C.char {
	cArgs := make([]*C.char, len(args))
	for i, arg := range args {
		cString := C.CString(arg)
		cArgs[i] = cString
	}
	return cArgs
}

func freeCStrings(cArgs []*C.char) {
	for _, arg := range cArgs {
		C.free(unsafe.Pointer(arg))
	}
}

// Set the given key with the given value. The return value is a response from Redis containing the string "OK".
//
// See [redis.io] for details.
//
// For example:
//
//	result := client.Set("key", "value")
//
// [redis.io]: https://redis.io/commands/set/
func (client *baseClient) Set(key string, value string) (string, error) {
	result, err := client.executeCommand(C.SetString, []string{key, value})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

// SetWithOptions sets the given key with the given value using the given options. The return value is dependent on the passed
// options. If the value is successfully set, "OK" is returned. If value isn't set because of [OnlyIfExists] or
// [OnlyIfDoesNotExist] conditions, an zero-value string is returned (""). If [api.SetOptions.ReturnOldValue] is set, the old
// value is returned.
//
// See [redis.io] for details.
//
// For example:
//
//	result, err := client.SetWithOptions("key", "value", &api.SetOptions{
//	    ConditionalSet: api.OnlyIfExists,
//	    Expiry: &api.Expiry{
//	        Type: api.Seconds,
//	        Count: uint64(5),
//	    },
//	})
//
// [redis.io]: https://redis.io/commands/set/
func (client *baseClient) SetWithOptions(key string, value string, options *SetOptions) (string, error) {
	result, err := client.executeCommand(C.SetString, append([]string{key, value}, options.toArgs()...))
	if err != nil {
		return "", nil
	}

	return handleStringResponse(result)
}

// Get a pointer to the value associated with the given key, or nil if no such value exists.
//
// See [redis.io] for details.
//
// For example:
//
//	result := client.Set("key", "value")
//
// [redis.io]: https://redis.io/commands/set/
func (client *baseClient) Get(key string) (string, error) {
	result, err := client.executeCommand(C.GetString, []string{key})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}
