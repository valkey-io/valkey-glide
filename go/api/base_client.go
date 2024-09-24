// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
//
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
	"errors"
	"strconv"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/glide/protobuf"
	"github.com/valkey-io/valkey-glide/go/glide/utils"
	"google.golang.org/protobuf/proto"
)

// BaseClient defines an interface for methods common to both [GlideClient] and [GlideClusterClient].
type BaseClient interface {
	StringCommands
	HashCommands

	// Close terminates the client by closing all associated resources.
	Close()
}

const OK = "OK"

type payload struct {
	value *C.struct_CommandResponse
	error error
}

//export successCallback
func successCallback(channelPtr unsafe.Pointer, cResponse *C.struct_CommandResponse) {
	response := cResponse
	resultChannel := *(*chan payload)(channelPtr)
	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	resultChannel := *(*chan payload)(channelPtr)
	resultChannel <- payload{value: nil, error: goError(cErrorType, cErrorMessage)}
}

type clientConfiguration interface {
	toProtobuf() *protobuf.ConnectionRequest
}

type baseClient struct {
	coreClient unsafe.Pointer
}

// Creates a connection by invoking the `create_client` function from Rust library via FFI.
// Passes the pointers to callback functions which will be invoked when the command succeeds or fails.
// Once the connection is established, this function invokes `free_connection_response` exposed by rust library to free the
// connection_response to avoid any memory leaks.
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

func (client *baseClient) executeCommand(requestType C.RequestType, args []string) (*C.struct_CommandResponse, error) {
	if client.coreClient == nil {
		return nil, &ClosingError{"ExecuteCommand failed. The client is closed."}
	}

	var cArgsPtr *C.uintptr_t = nil
	var argLengthsPtr *C.ulong = nil
	if len(args) > 0 {
		cArgs, argLengths := toCStrings(args)
		cArgsPtr = &cArgs[0]
		argLengthsPtr = &argLengths[0]
	}

	resultChannel := make(chan payload)
	resultChannelPtr := uintptr(unsafe.Pointer(&resultChannel))

	C.command(
		client.coreClient,
		C.uintptr_t(resultChannelPtr),
		uint32(requestType),
		C.size_t(len(args)),
		cArgsPtr,
		argLengthsPtr,
	)
	payload := <-resultChannel
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
		ptr := uintptr(unsafe.Pointer(&bytes[0]))
		cStrings[i] = C.uintptr_t(ptr)
		stringLengths[i] = C.size_t(len(str))
	}
	return cStrings, stringLengths
}

func (client *baseClient) Set(key string, value string) (string, error) {
	result, err := client.executeCommand(C.Set, []string{key, value})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

func (client *baseClient) SetWithOptions(key string, value string, options *SetOptions) (string, error) {
	result, err := client.executeCommand(C.Set, append([]string{key, value}, options.toArgs()...))
	if err != nil {
		return "", err
	}

	return handleStringOrNullResponse(result)
}

func (client *baseClient) Get(key string) (string, error) {
	result, err := client.executeCommand(C.Get, []string{key})
	if err != nil {
		return "", err
	}

	return handleStringOrNullResponse(result)
}

func (client *baseClient) MSet(keyValueMap map[string]string) (string, error) {
	result, err := client.executeCommand(C.MSet, utils.MapToString(keyValueMap))
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

func (client *baseClient) MSetNX(keyValueMap map[string]string) (bool, error) {
	result, err := client.executeCommand(C.MSetNX, utils.MapToString(keyValueMap))
	if err != nil {
		return false, err
	}

	return handleBooleanResponse(result)
}

func (client *baseClient) MGet(keys []string) ([]string, error) {
	result, err := client.executeCommand(C.MGet, keys)
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

func (client *baseClient) Incr(key string) (int64, error) {
	result, err := client.executeCommand(C.Incr, []string{key})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) IncrBy(key string, amount int64) (int64, error) {
	result, err := client.executeCommand(C.IncrBy, []string{key, utils.IntToString(amount)})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) IncrByFloat(key string, amount float64) (float64, error) {
	result, err := client.executeCommand(
		C.IncrByFloat,
		[]string{key, utils.FloatToString(amount)},
	)
	if err != nil {
		return 0, err
	}

	return handleDoubleResponse(result)
}

func (client *baseClient) Decr(key string) (int64, error) {
	result, err := client.executeCommand(C.Decr, []string{key})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) DecrBy(key string, amount int64) (int64, error) {
	result, err := client.executeCommand(C.DecrBy, []string{key, utils.IntToString(amount)})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) Strlen(key string) (int64, error) {
	result, err := client.executeCommand(C.Strlen, []string{key})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) SetRange(key string, offset int, value string) (int64, error) {
	result, err := client.executeCommand(C.SetRange, []string{key, strconv.Itoa(offset), value})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) GetRange(key string, start int, end int) (string, error) {
	result, err := client.executeCommand(C.GetRange, []string{key, strconv.Itoa(start), strconv.Itoa(end)})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

func (client *baseClient) Append(key string, value string) (int64, error) {
	result, err := client.executeCommand(C.Append, []string{key, value})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) LCS(key1 string, key2 string) (string, error) {
	result, err := client.executeCommand(C.LCS, []string{key1, key2})
	if err != nil {
		return "", err
	}

	return handleStringResponse(result)
}

func (client *baseClient) GetDel(key string) (string, error) {
	if key == "" {
		return "", errors.New("key is required")
	}

	result, err := client.executeCommand(C.GetDel, []string{key})
	if err != nil {
		return "", err
	}

	return handleStringOrNullResponse(result)
}

func (client *baseClient) HGet(key string, field string) (string, error) {
	result, err := client.executeCommand(C.HGet, []string{key, field})
	if err != nil {
		return "", err
	}

	return handleStringOrNullResponse(result)
}

func (client *baseClient) HGetAll(key string) (map[string]string, error) {
	result, err := client.executeCommand(C.HGetAll, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringToStringMapResponse(result)
}

func (client *baseClient) HMGet(key string, fields []string) ([]string, error) {
	result, err := client.executeCommand(C.HMGet, append([]string{key}, fields...))
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

func (client *baseClient) HSet(key string, values map[string]string) (int64, error) {
	result, err := client.executeCommand(C.HSet, utils.ConvertMapToKeyValueStringArray(key, values))
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) HSetNX(key string, field string, value string) (bool, error) {
	result, err := client.executeCommand(C.HSetNX, []string{key, field, value})
	if err != nil {
		return false, err
	}

	return handleBooleanResponse(result)
}

func (client *baseClient) HDel(key string, fields []string) (int64, error) {
	result, err := client.executeCommand(C.HDel, append([]string{key}, fields...))
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) HLen(key string) (int64, error) {
	result, err := client.executeCommand(C.HLen, []string{key})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}

func (client *baseClient) HVals(key string) ([]string, error) {
	result, err := client.executeCommand(C.HVals, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

func (client *baseClient) HExists(key string, field string) (bool, error) {
	result, err := client.executeCommand(C.HExists, []string{key, field})
	if err != nil {
		return false, err
	}

	return handleBooleanResponse(result)
}

func (client *baseClient) HKeys(key string) ([]string, error) {
	result, err := client.executeCommand(C.HKeys, []string{key})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

func (client *baseClient) HStrLen(key string, field string) (int64, error) {
	result, err := client.executeCommand(C.HStrlen, []string{key, field})
	if err != nil {
		return 0, err
	}

	return handleLongResponse(result)
}
