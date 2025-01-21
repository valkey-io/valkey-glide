// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"fmt"
	"reflect"
	"strconv"
	"unsafe"
)

func checkResponseType(response *C.struct_CommandResponse, expectedType C.ResponseType, isNilable bool) error {
	expectedTypeInt := uint32(expectedType)
	expectedTypeStr := C.get_response_type_string(expectedTypeInt)

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

func handleInterfaceResponse(response *C.struct_CommandResponse) (interface{}, error) {
	defer C.free_command_response(response)

	return parseInterface(response)
}

func parseInterface(response *C.struct_CommandResponse) (interface{}, error) {
	if response == nil {
		return nil, nil
	}

	switch response.response_type {
	case C.Null:
		return nil, nil
	case C.String:
		return parseString(response)
	case C.Int:
		return int64(response.int_value), nil
	case C.Float:
		return float64(response.float_value), nil
	case C.Bool:
		return bool(response.bool_value), nil
	case C.Array:
		return parseArray(response)
	case C.Map:
		return parseMap(response)
	case C.Sets:
		return parseSet(response)
	}

	return nil, &RequestError{"Unexpected return type from Valkey"}
}

func parseString(response *C.struct_CommandResponse) (interface{}, error) {
	if response.string_value == nil {
		return nil, nil
	}
	byteSlice := C.GoBytes(unsafe.Pointer(response.string_value), C.int(int64(response.string_value_len)))

	// Create Go string from byte slice (preserving null characters)
	return string(byteSlice), nil
}

func parseArray(response *C.struct_CommandResponse) (interface{}, error) {
	if response.array_value == nil {
		return nil, nil
	}

	var slice []interface{}
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := parseInterface(&v)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

func parseMap(response *C.struct_CommandResponse) (interface{}, error) {
	if response.array_value == nil {
		return nil, nil
	}

	value_map := make(map[string]interface{}, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res_key, err := parseString(v.map_key)
		if err != nil {
			return nil, err
		}
		res_val, err := parseInterface(v.map_value)
		if err != nil {
			return nil, err
		}
		value_map[res_key.(string)] = res_val
	}
	return value_map, nil
}

func parseSet(response *C.struct_CommandResponse) (interface{}, error) {
	if response.sets_value == nil {
		return nil, nil
	}

	slice := make(map[string]struct{}, response.sets_value_len)
	for _, v := range unsafe.Slice(response.sets_value, response.sets_value_len) {
		res, err := parseString(&v)
		if err != nil {
			return nil, err
		}
		slice[res.(string)] = struct{}{}
	}

	return slice, nil
}

func handleStringResponse(response *C.struct_CommandResponse) (string, error) {
	defer C.free_command_response(response)

	res, err := convertCharArrayToString(response, false)
	return res.Value(), err
}

func handleStringOrNilResponse(response *C.struct_CommandResponse) (Result[string], error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, true)
}

// Fix after merging with https://github.com/valkey-io/valkey-glide/pull/2964
func convertStringOrNilArray(response *C.struct_CommandResponse) ([]Result[string], error) {
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]Result[string], 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := convertCharArrayToString(&v, true)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

func handle2DStringArrayResponse(response *C.struct_CommandResponse) ([][]string, error) {
	defer C.free_command_response(response)
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}
	array, err := parseArray(response)
	if err != nil {
		return nil, err
	}
	converted, err := arrayConverter[[]string]{
		arrayConverter[string]{
			nil,
			false,
		},
		false,
	}.convert(array)
	if err != nil {
		return nil, err
	}
	res, ok := converted.([][]string)
	if !ok {
		return nil, &RequestError{fmt.Sprintf("unexpected type: %T", converted)}
	}
	return res, nil
}

func handleStringArrayOrNullResponse(response *C.struct_CommandResponse) ([]Result[string], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return nil, typeErr
	}

	if response.response_type == C.Null {
		return nil, nil
	}

	slice := make([]Result[string], 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := convertCharArrayToString(&v, true)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

// array could be nillable, but strings - aren't
func convertStringArray(response *C.struct_CommandResponse, isNilable bool) ([]string, error) {
	typeErr := checkResponseType(response, C.Array, isNilable)
	if typeErr != nil {
		return nil, typeErr
	}

	if isNilable && response.array_value == nil {
		return nil, nil
	}

	slice := make([]string, 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := convertCharArrayToString(&v, false)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res.Value())
	}
	return slice, nil
}

func handleStringOrNilArrayResponse(response *C.struct_CommandResponse) ([]Result[string], error) {
	defer C.free_command_response(response)

	return convertStringOrNilArray(response)
}

func handleStringArrayResponse(response *C.struct_CommandResponse) ([]string, error) {
	defer C.free_command_response(response)

	return convertStringArray(response, false)
}

func handleStringArrayOrNilResponse(response *C.struct_CommandResponse) ([]string, error) {
	defer C.free_command_response(response)

	return convertStringArray(response, true)
}

func handleIntResponse(response *C.struct_CommandResponse) (int64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, false)
	if typeErr != nil {
		return 0, typeErr
	}

	return int64(response.int_value), nil
}

func handleIntOrNilResponse(response *C.struct_CommandResponse) (Result[int64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, true)
	if typeErr != nil {
		return CreateNilInt64Result(), typeErr
	}

	if response.response_type == C.Null {
		return CreateNilInt64Result(), nil
	}

	return CreateInt64Result(int64(response.int_value)), nil
}

func handleIntArrayResponse(response *C.struct_CommandResponse) ([]int64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]int64, 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		err := checkResponseType(&v, C.Int, false)
		if err != nil {
			return nil, err
		}
		slice = append(slice, int64(v.int_value))
	}
	return slice, nil
}

func handleFloatResponse(response *C.struct_CommandResponse) (float64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, false)
	if typeErr != nil {
		return float64(0), typeErr
	}

	return float64(response.float_value), nil
}

func handleFloatOrNilResponse(response *C.struct_CommandResponse) (Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, true)
	if typeErr != nil {
		return CreateNilFloat64Result(), typeErr
	}
	if response.response_type == C.Null {
		return CreateNilFloat64Result(), nil
	}
	return CreateFloat64Result(float64(response.float_value)), nil
}

func handleLongAndDoubleOrNullResponse(response *C.struct_CommandResponse) (Result[int64], Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return CreateNilInt64Result(), CreateNilFloat64Result(), typeErr
	}

	if response.response_type == C.Null {
		return CreateNilInt64Result(), CreateNilFloat64Result(), nil
	}

	rank := CreateNilInt64Result()
	score := CreateNilFloat64Result()
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		if v.response_type == C.Int {
			rank = CreateInt64Result(int64(v.int_value))
		}
		if v.response_type == C.Float {
			score = CreateFloat64Result(float64(v.float_value))
		}
	}

	return rank, score, nil
}

func handleBoolResponse(response *C.struct_CommandResponse) (bool, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Bool, false)
	if typeErr != nil {
		return false, typeErr
	}

	return bool(response.bool_value), nil
}

func handleBoolArrayResponse(response *C.struct_CommandResponse) ([]bool, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]bool, 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		err := checkResponseType(&v, C.Bool, false)
		if err != nil {
			return nil, err
		}
		slice = append(slice, bool(v.bool_value))
	}
	return slice, nil
}

func handleStringDoubleMapResponse(response *C.struct_CommandResponse) (map[string]float64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	aMap := data.(map[string]interface{})

	converted, err := mapConverter[float64]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]float64)
	if !ok {
		return nil, &RequestError{fmt.Sprintf("unexpected type of map: %T", converted)}
	}
	return result, nil
}

func handleStringToStringMapResponse(response *C.struct_CommandResponse) (map[string]string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	aMap := data.(map[string]interface{})

	converted, err := mapConverter[string]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]string)
	if !ok {
		return nil, &RequestError{fmt.Sprintf("unexpected type of map: %T", converted)}
	}
	return result, nil
}

func handleStringToStringArrayMapOrNilResponse(
	response *C.struct_CommandResponse,
) (map[string][]string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, true)
	if typeErr != nil {
		return nil, typeErr
	}

	if response.response_type == C.Null {
		return nil, nil
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}

	converters := mapConverter[[]string]{
		arrayConverter[string]{},
		false,
	}

	res, err := converters.convert(data)
	if err != nil {
		return nil, err
	}
	if result, ok := res.(map[string][]string); ok {
		return result, nil
	}

	return nil, &RequestError{fmt.Sprintf("unexpected type received: %T", res)}
}

func handleStringSetResponse(response *C.struct_CommandResponse) (map[string]struct{}, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Sets, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make(map[string]struct{}, response.sets_value_len)
	for _, v := range unsafe.Slice(response.sets_value, response.sets_value_len) {
		res, err := convertCharArrayToString(&v, true)
		if err != nil {
			return nil, err
		}
		slice[res.Value()] = struct{}{}
	}

	return slice, nil
}

func handleKeyWithMemberAndScoreResponse(response *C.struct_CommandResponse) (Result[KeyWithMemberAndScore], error) {
	defer C.free_command_response(response)

	if response == nil || response.response_type == uint32(C.Null) {
		return CreateNilKeyWithMemberAndScoreResult(), nil
	}

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return CreateNilKeyWithMemberAndScoreResult(), typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return CreateNilKeyWithMemberAndScoreResult(), err
	}

	arr := slice.([]interface{})
	key := arr[0].(string)
	member := arr[1].(string)
	score := arr[2].(float64)
	return CreateKeyWithMemberAndScoreResult(KeyWithMemberAndScore{key, member, score}), nil
}

func handleScanResponse(response *C.struct_CommandResponse) (string, []string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return "", nil, typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return "", nil, err
	}

	if arr, ok := slice.([]interface{}); ok {
		resCollection, err := convertToStringArray(arr[1].([]interface{}))
		if err != nil {
			return "", nil, err
		}
		return arr[0].(string), resCollection, nil
	}

	return "", nil, err
}

func convertToStringArray(input []interface{}) ([]string, error) {
	result := make([]string, len(input))
	for i, v := range input {
		str, ok := v.(string)
		if !ok {
			return nil, fmt.Errorf("element at index %d is not a string: %v", i, v)
		}
		result[i] = str
	}
	return result, nil
}

// get type of T
func getType[T any]() reflect.Type {
	var zero [0]T
	return reflect.TypeOf(zero).Elem()
}

// convert (typecast) untyped response into a typed value
// for example, an arbitrary array `[]interface{}` into `[]string`
type responseConverter interface {
	convert(data interface{}) (interface{}, error)
}

// convert maps, T - type of the value, key is string
type mapConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

func (node mapConverter[T]) convert(data interface{}) (interface{}, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, &RequestError{fmt.Sprintf("Unexpected type received: nil, expected: map[string]%v", getType[T]())}
		}
	}
	result := make(map[string]T)

	for key, value := range data.(map[string]interface{}) {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return nil, &RequestError{fmt.Sprintf("Unexpected type of map element: %T, expected: %v", value, getType[T]())}
			}
			result[key] = valueT
		} else {
			val, err := node.next.convert(value)
			if err != nil {
				return nil, err
			}
			if val == nil {
				var null T
				result[key] = null
				continue
			}
			valueT, ok := val.(T)
			if !ok {
				return nil, &RequestError{fmt.Sprintf("Unexpected type of map element: %T, expected: %v", val, getType[T]())}
			}
			result[key] = valueT
		}
	}

	return result, nil
}

// convert arrays, T - type of the value
type arrayConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

func (node arrayConverter[T]) convert(data interface{}) (interface{}, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, &RequestError{fmt.Sprintf("Unexpected type received: nil, expected: []%v", getType[T]())}
		}
	}
	arrData := data.([]interface{})
	result := make([]T, 0, len(arrData))
	for _, value := range arrData {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return nil, &RequestError{
					fmt.Sprintf("Unexpected type of array element: %T, expected: %v", value, getType[T]()),
				}
			}
			result = append(result, valueT)
		} else {
			val, err := node.next.convert(value)
			if err != nil {
				return nil, err
			}
			if val == nil {
				var null T
				result = append(result, null)
				continue
			}
			valueT, ok := val.(T)
			if !ok {
				return nil, &RequestError{fmt.Sprintf("Unexpected type of array element: %T, expected: %v", val, getType[T]())}
			}
			result = append(result, valueT)
		}
	}

	return result, nil
}

// TODO: convert sets

func handleXAutoClaimResponse(response *C.struct_CommandResponse) (XAutoClaimResponse, error) {
	defer C.free_command_response(response)
	var null XAutoClaimResponse // default response
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return null, typeErr
	}
	slice, err := parseArray(response)
	if err != nil {
		return null, err
	}
	arr := slice.([]interface{})
	len := len(arr)
	if len < 2 || len > 3 {
		return null, &RequestError{fmt.Sprintf("Unexpected response array length: %d", len)}
	}
	converted, err := mapConverter[[][]string]{
		arrayConverter[[]string]{
			arrayConverter[string]{
				nil,
				false,
			},
			false,
		},
		false,
	}.convert(arr[1])
	if err != nil {
		return null, err
	}
	claimedEntries, ok := converted.(map[string][][]string)
	if !ok {
		return null, &RequestError{fmt.Sprintf("unexpected type of second element: %T", converted)}
	}
	var deletedMessages []string
	deletedMessages = nil
	if len == 3 {
		converted, err = arrayConverter[string]{
			nil,
			false,
		}.convert(arr[2])
		if err != nil {
			return null, err
		}
		deletedMessages, ok = converted.([]string)
		if !ok {
			return null, &RequestError{fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return XAutoClaimResponse{arr[0].(string), claimedEntries, deletedMessages}, nil
}

func handleXAutoClaimJustIdResponse(response *C.struct_CommandResponse) (XAutoClaimJustIdResponse, error) {
	defer C.free_command_response(response)
	var null XAutoClaimJustIdResponse // default response
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return null, typeErr
	}
	slice, err := parseArray(response)
	if err != nil {
		return null, err
	}
	arr := slice.([]interface{})
	len := len(arr)
	if len < 2 || len > 3 {
		return null, &RequestError{fmt.Sprintf("Unexpected response array length: %d", len)}
	}
	converted, err := arrayConverter[string]{
		nil,
		false,
	}.convert(arr[1])
	if err != nil {
		return null, err
	}
	claimedEntries, ok := converted.([]string)
	if !ok {
		return null, &RequestError{fmt.Sprintf("unexpected type of second element: %T", converted)}
	}
	var deletedMessages []string
	deletedMessages = nil
	if len == 3 {
		converted, err = arrayConverter[string]{
			nil,
			false,
		}.convert(arr[2])
		if err != nil {
			return null, err
		}
		deletedMessages, ok = converted.([]string)
		if !ok {
			return null, &RequestError{fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return XAutoClaimJustIdResponse{arr[0].(string), claimedEntries, deletedMessages}, nil
}

func handleXReadResponse(response *C.struct_CommandResponse) (map[string]map[string][][]string, error) {
	defer C.free_command_response(response)
	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	if data == nil {
		return nil, nil
	}

	converters := mapConverter[map[string][][]string]{
		mapConverter[[][]string]{
			arrayConverter[[]string]{
				arrayConverter[string]{
					nil,
					false,
				},
				false,
			},
			false,
		},
		false,
	}

	res, err := converters.convert(data)
	if err != nil {
		return nil, err
	}
	if result, ok := res.(map[string]map[string][][]string); ok {
		return result, nil
	}
	return nil, &RequestError{fmt.Sprintf("unexpected type received: %T", res)}
}

func handleXReadGroupResponse(response *C.struct_CommandResponse) (map[string]map[string][][]string, error) {
	defer C.free_command_response(response)
	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	if data == nil {
		return nil, nil
	}

	converters := mapConverter[map[string][][]string]{
		mapConverter[[][]string]{
			arrayConverter[[]string]{
				arrayConverter[string]{
					nil,
					false,
				},
				true,
			},
			false,
		},
		false,
	}

	res, err := converters.convert(data)
	if err != nil {
		return nil, err
	}
	if result, ok := res.(map[string]map[string][][]string); ok {
		return result, nil
	}
	return nil, &RequestError{fmt.Sprintf("unexpected type received: %T", res)}
}

func handleXPendingSummaryResponse(response *C.struct_CommandResponse) (XPendingSummary, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return CreateNilXPendingSummary(), typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return CreateNilXPendingSummary(), err
	}

	arr := slice.([]interface{})
	NumOfMessages := arr[0].(int64)
	var StartId, EndId Result[string]
	if arr[1] == nil {
		StartId = CreateNilStringResult()
	} else {
		StartId = CreateStringResult(arr[1].(string))
	}
	if arr[2] == nil {
		EndId = CreateNilStringResult()
	} else {
		EndId = CreateStringResult(arr[2].(string))
	}

	if pendingMessages, ok := arr[3].([]interface{}); ok {
		var ConsumerPendingMessages []ConsumerPendingMessage
		for _, msg := range pendingMessages {
			consumerMessage := msg.([]interface{})
			count, err := strconv.ParseInt(consumerMessage[1].(string), 10, 64)
			if err == nil {
				ConsumerPendingMessages = append(ConsumerPendingMessages, ConsumerPendingMessage{
					ConsumerName: consumerMessage[0].(string),
					MessageCount: count,
				})
			}
		}
		return XPendingSummary{NumOfMessages, StartId, EndId, ConsumerPendingMessages}, nil
	} else {
		return XPendingSummary{NumOfMessages, StartId, EndId, make([]ConsumerPendingMessage, 0)}, nil
	}
}

func handleXPendingDetailResponse(response *C.struct_CommandResponse) ([]XPendingDetail, error) {
	// response should be [][]interface{}

	defer C.free_command_response(response)

	// TODO: Not sure if this is correct for a nill response
	if response == nil || response.response_type == uint32(C.Null) {
		return make([]XPendingDetail, 0), nil
	}

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return make([]XPendingDetail, 0), typeErr
	}

	// parse first level of array
	slice, err := parseArray(response)
	arr := slice.([]interface{})

	if err != nil {
		return make([]XPendingDetail, 0), err
	}

	pendingDetails := make([]XPendingDetail, 0, len(arr))

	for _, message := range arr {
		switch detail := message.(type) {
		case []interface{}:
			pDetail := XPendingDetail{
				Id:            detail[0].(string),
				ConsumerName:  detail[1].(string),
				IdleTime:      detail[2].(int64),
				DeliveryCount: detail[3].(int64),
			}
			pendingDetails = append(pendingDetails, pDetail)

		case XPendingDetail:
			pendingDetails = append(pendingDetails, detail)
		default:
			fmt.Printf("handleXPendingDetailResponse - unhandled type: %s\n", reflect.TypeOf(detail))
		}
	}

	return pendingDetails, nil
}
