// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"errors"
	"unsafe"
)

func convertCharArrayToString(arr *C.char, length C.long) string {
	if arr == nil {
		return ""
	}
	byteSlice := C.GoBytes(unsafe.Pointer(arr), C.int(int64(length)))
	// Create Go string from byte slice (preserving null characters)
	return string(byteSlice)
}

func handleStringResponse(response *C.struct_CommandResponse) (string, error) {
	if response == nil {
		return "", errors.New("handleStringArrayResponse: command response is nil")
	}

	defer C.free_command_response(response)
	return convertCharArrayToString(response.string_value, response.string_value_len), nil
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) (string, error) {
	if response == nil {
		return "", nil
	}

	return handleStringResponse(response)
}

// handleBooleanResponse converts a C struct_CommandResponse's bool_value to a Go bool.
func handleBooleanResponse(response *C.struct_CommandResponse) (bool, error) {
	if response == nil {
		return false, errors.New("handleBooleanResponse: command response is nil")
	}

	defer C.free_command_response(response)
	return bool(response.bool_value), nil
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]string, error) {
	if response == nil {
		return nil, errors.New("handleStringArrayResponse: command response is nil")
	}

	defer C.free_command_response(response)
	var slice []string
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		slice = append(slice, convertCharArrayToString(v.string_value, v.string_value_len))
	}

	return slice, nil
}

func handleLongResponse(response *C.struct_CommandResponse) (int64, error) {
	if response == nil {
		return 0, errors.New("handleLongResponse: command response is nil")
	}

	defer C.free_command_response(response)
	return int64(response.int_value), nil
}

func handleDoubleResponse(response *C.struct_CommandResponse) (float64, error) {
	if response == nil {
		return 0, errors.New("handleStringArrayResponse: command response is nil")
	}

	defer C.free_command_response(response)
	return float64(response.float_value), nil
}

func handleStringToStringMapResponse(response *C.struct_CommandResponse) (map[string]string, error) {
	if response == nil {
		return nil, errors.New("handleStringMapResponse: command response is nil")
	}

	defer C.free_command_response(response)
	m := make(map[string]string, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		key := convertCharArrayToString(v.map_key.string_value, v.map_key.string_value_len)
		value := convertCharArrayToString(v.map_value.string_value, v.map_value.string_value_len)
		m[key] = value
	}

	return m, nil
}
