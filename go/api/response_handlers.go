// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"fmt"
	"unsafe"
)

func checkResponseType(response *C.struct_CommandResponse, expectedType C.ResponseType, isNilable bool) error {
	expectedTypeInt := uint32(expectedType)
	expectedTypeStr := C.get_response_type_string(expectedTypeInt)
	defer C.free_response_type_string(expectedTypeStr)

	if !isNilable && response == nil {
		return &RequestError{
			fmt.Sprintf(
				"Unexpected return type from Valkey: got nil, expected %s",
				C.GoString(expectedTypeStr),
			),
		}
	}

	if isNilable && (response == nil || response.response_type == uint32(C.Null)) {
		return nil
	}

	if response.response_type == expectedTypeInt {
		return nil
	}

	actualTypeStr := C.get_response_type_string(response.response_type)
	defer C.free_response_type_string(actualTypeStr)
	return &RequestError{
		fmt.Sprintf(
			"Unexpected return type from Valkey: got %s, expected %s",
			C.GoString(actualTypeStr),
			C.GoString(expectedTypeStr),
		),
	}
}

func convertCharArrayToString(response *C.struct_CommandResponse, isNilable bool) (Result[string], error) {
	typeErr := checkResponseType(response, C.String, isNilable)
	if typeErr != nil {
		return CreateNilStringResult(), typeErr
	}

	if response.string_value == nil {
		return CreateNilStringResult(), nil
	}
	byteSlice := C.GoBytes(unsafe.Pointer(response.string_value), C.int(int64(response.string_value_len)))

	// Create Go string from byte slice (preserving null characters)
	return CreateStringResult(string(byteSlice)), nil
}

func handleStringResponse(response *C.struct_CommandResponse) (Result[string], error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, false)
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) (Result[string], error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, true)
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]Result[string], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	var slice []Result[string]
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := convertCharArrayToString(&v, true)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

func handleLongResponse(response *C.struct_CommandResponse) (Result[int64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, false)
	if typeErr != nil {
		return CreateNilInt64Result(), typeErr
	}

	return CreateInt64Result(int64(response.int_value)), nil
}

func handleDoubleResponse(response *C.struct_CommandResponse) (Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, false)
	if typeErr != nil {
		return CreateNilFloat64Result(), typeErr
	}

	return CreateFloat64Result(float64(response.float_value)), nil
}

func handleBooleanResponse(response *C.struct_CommandResponse) (Result[bool], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Bool, false)
	if typeErr != nil {
		return CreateNilBoolResult(), typeErr
	}

	return CreateBoolResult(bool(response.bool_value)), nil
}

func handleStringToStringMapResponse(response *C.struct_CommandResponse) (map[Result[string]]Result[string], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	m := make(map[Result[string]]Result[string], response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		key, err := convertCharArrayToString(v.map_key, true)
		if err != nil {
			return nil, err
		}
		value, err := convertCharArrayToString(v.map_value, true)
		if err != nil {
			return nil, err
		}
		m[key] = value
	}

	return m, nil
}
