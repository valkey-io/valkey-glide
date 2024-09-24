// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"fmt"
	"unsafe"
)

func convertCharArrayToString(arr *C.char, length C.long) StringValue {
	if arr == nil {
		return NilStringValue
	}
	byteSlice := C.GoBytes(unsafe.Pointer(arr), C.int(int64(length)))
	// Create Go string from byte slice (preserving null characters)
	return StringValue{Val: string(byteSlice), IsNil: false}
}

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

func handleStringResponse(response *C.struct_CommandResponse) (StringValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.String, false)
	if typeErr != nil {
		return NilStringValue, typeErr
	}

	return convertCharArrayToString(response.string_value, response.string_value_len), nil
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) (StringValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.String, true)
	if typeErr != nil {
		return NilStringValue, typeErr
	}

	return convertCharArrayToString(response.string_value, response.string_value_len), nil
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]StringValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	var slice []StringValue
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		slice = append(slice, convertCharArrayToString(v.string_value, v.string_value_len))
	}
	return slice, nil
}

func handleLongResponse(response *C.struct_CommandResponse) (Int64Value, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, false)
	if typeErr != nil {
		return NilInt64Value, typeErr
	}

	return Int64Value{Val: int64(response.int_value), IsNil: false}, nil
}

func handleDoubleResponse(response *C.struct_CommandResponse) (Float64Value, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, false)
	if typeErr != nil {
		return NilFloat64Value, typeErr
	}

	return Float64Value{Val: float64(response.float_value), IsNil: false}, nil
}

func handleBooleanResponse(response *C.struct_CommandResponse) (BoolValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Bool, false)
	if typeErr != nil {
		return NilBoolValue, typeErr
	}

	return BoolValue{Val: bool(response.bool_value), IsNil: false}, nil
}

func handleStringToStringMapResponse(response *C.struct_CommandResponse) (map[StringValue]StringValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	m := make(map[StringValue]StringValue, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		key := convertCharArrayToString(v.map_key.string_value, v.map_key.string_value_len)
		value := convertCharArrayToString(v.map_value.string_value, v.map_value.string_value_len)
		m[key] = value
	}

	return m, nil
}
