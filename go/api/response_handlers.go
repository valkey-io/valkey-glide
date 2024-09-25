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

func convertCharArrayToString(response *C.struct_CommandResponse, isNilable bool) (StringValue, error) {
	typeErr := checkResponseType(response, C.String, isNilable)
	if typeErr != nil {
		return NilStringValue, typeErr
	}

	if response.string_value == nil {
		return NilStringValue, nil
	}
	byteSlice := C.GoBytes(unsafe.Pointer(response.string_value), C.int(int64(response.string_value_len)))

	// Create Go string from byte slice (preserving null characters)
	return StringValue{Val: string(byteSlice), IsNil: false}, nil
}

func handleStringResponse(response *C.struct_CommandResponse) (StringValue, error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, false)
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) (StringValue, error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, true)
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]StringValue, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	var slice []StringValue
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := convertCharArrayToString(&v, true)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
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
