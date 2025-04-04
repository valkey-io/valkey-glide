// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
import "C"
import (
	"fmt"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/api/errors"
	"github.com/valkey-io/valkey-glide/go/utils"
)

// Registry to track clients by their pointer address
var (
	clientRegistry   = make(map[uintptr]*baseClient)
	clientRegistryMu sync.RWMutex
)

// RegisterClient registers a client in the registry using its pointer value
func RegisterClient(client *baseClient, ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	clientRegistry[ptrValue] = client
}

// UnregisterClient removes a client from the registry
func UnregisterClient(ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	delete(clientRegistry, ptrValue)
}

// GetClientByPtr gets a client from the registry by its pointer value
func GetClientByPtr(ptrValue uintptr) *baseClient {
	clientRegistryMu.RLock()
	defer clientRegistryMu.RUnlock()
	return clientRegistry[ptrValue]
}

//export successCallback
func successCallback(channelPtr unsafe.Pointer, cResponse *C.struct_CommandResponse) {
	response := cResponse
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	defer C.free_error_message(cErrorMessage)
	msg := C.GoString(cErrorMessage)
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: nil, error: errors.GoError(uint32(cErrorType), msg)}
}

//export pubSubCallback
func pubSubCallback(clientPtr unsafe.Pointer, kind C.uint32_t, cResponse *C.struct_CommandResponse) {
	defer C.free_command_response(cResponse)

	if clientPtr == nil || cResponse == nil {
		return
	}

	// TODO: Refactor this out
	// Extract values from the CommandResponse
	arrayValues, err := processCommandResponseArray(cResponse)
	if err != nil {
		fmt.Printf("Error processing pubsub notification: %v\n", err)
		return
	}

	// Convert the kind to a PubSub event type
	pushKind := PushKind(kind)

	// Process different types of push messages
	message, shouldReturn := getMessage(pushKind, arrayValues)
	if shouldReturn {
		return
	}

	if clientPtr != nil {
		// Look up the client in our registry using the pointer address
		ptrValue := uintptr(clientPtr)
		client := GetClientByPtr(ptrValue)

		if client != nil {
			// If the client has a message handler, use it
			if handler := client.GetMessageHandler(); handler != nil {
				// Create PushInfo and pass it to the handler
				pushMsg := PushInfo{
					Kind:    pushKind,
					Message: message,
				}
				handler.Handle(pushMsg)
			}
		} else {
			fmt.Printf("Client not found for pointer: %v\n", ptrValue)
		}
	}
}

func getMessage(pushKind PushKind, messageValues []any) (*PubSubMessage, bool) {
	var message *PubSubMessage

	switch pushKind {
	case Message, SMessage:
		if len(messageValues) < 2 {
			return nil, true
		}

		channel, ok := utils.ToString(messageValues[0])
		if !ok {
			return nil, true
		}

		msgContent, ok := utils.ToString(messageValues[1])
		if !ok {
			return nil, true
		}

		message = NewPubSubMessage(msgContent, channel)

	case PMessage:
		if len(messageValues) < 3 {
			return nil, true
		}

		pattern, ok := utils.ToString(messageValues[0])
		if !ok {
			return nil, true
		}

		channel, ok := utils.ToString(messageValues[1])
		if !ok {
			return nil, true
		}

		msgContent, ok := utils.ToString(messageValues[2])
		if !ok {
			return nil, true
		}

		message = NewPubSubMessageWithPattern(msgContent, channel, pattern)

	case Subscribe, PSubscribe, SSubscribe, Unsubscribe, PUnsubscribe, SUnsubscribe:
		if len(messageValues) < 2 {
			return nil, true
		}
		channel, ok := utils.ToString(messageValues[0])
		if !ok {
			return nil, true
		}
		msgContent, ok := utils.ToString(messageValues[1])
		if !ok {
			return nil, true
		}
		message = NewPubSubMessage(msgContent, channel)

	default:
		// log unsupported push kind
		return nil, true
	}
	return message, false
}

// Helper function to process a CommandResponse array into a Go slice
func processCommandResponseArray(cResponse *C.struct_CommandResponse) ([]any, error) {
	if typeErr := checkResponseType(cResponse, C.Array, false); typeErr != nil {
		return nil, typeErr
	}

	arrayLen := int(cResponse.array_value_len)
	arrayPtr := cResponse.array_value
	result := make([]any, arrayLen)

	// Iterate through the array elements
	for i := 0; i < arrayLen; i++ {
		element := unsafe.Pointer(uintptr(unsafe.Pointer(arrayPtr)) + uintptr(i)*unsafe.Sizeof(*arrayPtr))
		elemPtr := (*C.struct_CommandResponse)(element)

		// Convert element based on type
		switch elemPtr.response_type {
		case C.String:
			strLen := int(elemPtr.string_value_len)
			if strLen > 0 && elemPtr.string_value != nil {
				bytes := C.GoBytes(unsafe.Pointer(elemPtr.string_value), C.int(strLen))
				result[i] = bytes
			} else {
				result[i] = []byte{}
			}
		case C.Int:
			result[i] = int64(elemPtr.int_value)
		case C.Float:
			result[i] = float64(elemPtr.float_value)
		case C.Bool:
			result[i] = bool(elemPtr.bool_value)
		case C.Null:
			result[i] = nil
		default:
			// For other types, we'd need more complex handling
			// For simplicity, convert to string representation
			result[i] = fmt.Sprintf("Unsupported type: %d", elemPtr.response_type)
		}
	}
	return result, nil
}
