// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"fmt"
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

func checkResponseType(response *C.struct_CommandResponse, expectedType C.ResponseType, isNilable bool) error {
	expectedTypeInt := uint32(expectedType)
	if response.response_type == expectedTypeInt {
		return nil
	}
	if isNilable && (response == nil || response.response_type == uint32(C.Null)) {
		return nil
	}
	actualTypeStr := C.get_response_type_string(response.response_type)
	expectedTypeStr := C.get_response_type_string(expectedTypeInt)
	defer C.free_response_type_string(actualTypeStr)
	defer C.free_response_type_string(expectedTypeStr)
	return &RequestError{
		fmt.Sprintf(
			"Unexpected return type from Valkey: got %s, expected %s",
			C.GoString(actualTypeStr),
			C.GoString(expectedTypeStr),
		),
	}
}

func handleStringResponse(response *C.struct_CommandResponse) (string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.String, false)
	if typeErr != nil {
		return "", typeErr
	}

	return convertCharArrayToString(response.string_value, response.string_value_len), nil
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) (string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.String, true)
	if typeErr != nil {
		return "", typeErr
	}

	return convertCharArrayToString(response.string_value, response.string_value_len), nil
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	var slice []string
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		slice = append(slice, convertCharArrayToString(v.string_value, v.string_value_len))
	}
	return slice, nil
}

func handleLongResponse(response *C.struct_CommandResponse) (int64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, false)
	if typeErr != nil {
		return 0, typeErr
	}

	return int64(response.int_value), nil
}

func handleDoubleResponse(response *C.struct_CommandResponse) (float64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, false)
	if typeErr != nil {
		return 0, typeErr
	}

	return float64(response.float_value), nil
}

func handleBooleanResponse(response *C.struct_CommandResponse) (bool, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Bool, false)
	if typeErr != nil {
		return false, typeErr
	}

	return bool(response.bool_value), nil
}
