// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"fmt"
	"reflect"
	"sort"
	"strconv"
	"time"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func checkResponseType(response *C.struct_CommandResponse, expectedType C.ResponseType, isNilable bool) error {
	expectedTypeInt := uint32(expectedType)
	expectedTypeStr := C.get_response_type_string(expectedTypeInt)

	if !isNilable && response == nil {
		return &errors.RequestError{
			Msg: fmt.Sprintf(
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
	return &errors.RequestError{
		Msg: fmt.Sprintf(
			"Unexpected return type from Valkey: got %s, expected %s",
			C.GoString(actualTypeStr),
			C.GoString(expectedTypeStr),
		),
	}
}

func convertCharArrayToString(response *C.struct_CommandResponse, isNilable bool) (models.Result[string], error) {
	typeErr := checkResponseType(response, C.String, isNilable)
	if typeErr != nil {
		return models.CreateNilStringResult(), typeErr
	}

	if response.string_value == nil {
		return models.CreateNilStringResult(), nil
	}
	byteSlice := C.GoBytes(unsafe.Pointer(response.string_value), C.int(int64(response.string_value_len)))

	// Create Go string from byte slice (preserving null characters)
	return models.CreateStringResult(string(byteSlice)), nil
}

// Fix after merging with https://github.com/valkey-io/valkey-glide/pull/2964
func convertStringOrNilArray(response *C.struct_CommandResponse) ([]models.Result[string], error) {
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]models.Result[string], 0, response.array_value_len)
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

func convertToStringArray(input []any) ([]string, error) {
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

func parseInterface(response *C.struct_CommandResponse) (any, error) {
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
	case C.Ok:
		return "OK", nil
	case C.Error:
		errStr, err := parseString(response)
		if err != nil {
			return &errors.RequestError{Msg: "Cannot read error message"}, nil
		}
		errStrString, ok := errStr.(string)
		if !ok {
			return &errors.RequestError{Msg: "Error message isn't a string"}, nil
		}
		return &errors.RequestError{Msg: errStrString}, nil
	}

	return nil, &errors.RequestError{Msg: "Unexpected return type from Valkey"}
}

func parseString(response *C.struct_CommandResponse) (any, error) {
	if response.string_value == nil {
		return nil, nil
	}
	byteSlice := C.GoBytes(unsafe.Pointer(response.string_value), C.int(int64(response.string_value_len)))

	// Create Go string from byte slice (preserving null characters)
	return string(byteSlice), nil
}

func parseArray(response *C.struct_CommandResponse) (any, error) {
	if response.array_value == nil {
		return nil, nil
	}

	var slice []any
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := parseInterface(&v)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

func parseMap(response *C.struct_CommandResponse) (any, error) {
	if response.array_value == nil {
		return nil, nil
	}

	value_map := make(map[string]any, response.array_value_len)
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

func parseSet(response *C.struct_CommandResponse) (any, error) {
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

// get type of T
func getType[T any]() reflect.Type {
	var zero [0]T
	return reflect.TypeOf(zero).Elem()
}

// convert (typecast) untyped response into a typed value
// for example, an arbitrary array `[]any` into `[]string`
type responseConverter interface {
	convert(data any) (any, error)
}

// convert maps, T - type of the value, key is string
type mapConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

// Converts an untyped map into a map[string]T
func (node mapConverter[T]) convert(data any) (any, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: map[string]%v", getType[T]())}
		}
	}
	result := make(map[string]T)

	// Iterate over the map and convert each value to T
	for key, value := range data.(map[string]any) {
		if node.next == nil {
			// try direct conversion to T when there is no next converter
			valueT, ok := value.(T)
			if !ok {
				return nil, &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", value, getType[T]()),
				}
			}
			result[key] = valueT
		} else {
			// nested iteration when there is a next converter
			val, err := node.next.convert(value)
			if err != nil {
				return nil, err
			}
			if val == nil {
				var null T
				result[key] = null
				continue
			}
			// convert to T
			valueT, ok := val.(T)
			if !ok {
				return nil, &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", val, getType[T]())}
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

func (node arrayConverter[T]) convert(data any) (any, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: []%v", getType[T]())}
		}
	}
	arrData := data.([]any)
	result := make([]T, 0, len(arrData))
	for _, value := range arrData {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return nil, &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", value, getType[T]()),
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
				return nil, &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", val, getType[T]())}
			}
			result = append(result, valueT)
		}
	}

	return result, nil
}

// TODO: convert sets

func handleAnyArrayResponse(response *C.struct_CommandResponse) ([]any, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]any, 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		res, err := parseInterface(&v)
		if err != nil {
			return nil, err
		}
		slice = append(slice, res)
	}
	return slice, nil
}

func handleInterfaceResponse(response *C.struct_CommandResponse) (any, error) {
	defer C.free_command_response(response)

	return parseInterface(response)
}

func handleStringResponse(response *C.struct_CommandResponse) (string, error) {
	defer C.free_command_response(response)

	res, err := convertCharArrayToString(response, false)
	return res.Value(), err
}

func handleStringOrNilResponse(response *C.struct_CommandResponse) (models.Result[string], error) {
	defer C.free_command_response(response)

	return convertCharArrayToString(response, true)
}

func handleOkResponse(response *C.struct_CommandResponse) (string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Ok, false)
	if typeErr != nil {
		return models.DefaultStringResponse, typeErr
	}

	return "OK", nil
}

func handleOkOrStringOrNilResponse(response *C.struct_CommandResponse) (models.Result[string], error) {
	defer C.free_command_response(response)

	if response.response_type == uint32(C.Ok) {
		return models.CreateStringResult("OK"), nil
	}

	return convertCharArrayToString(response, true)
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
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}
	return res, nil
}

func handle2DFloat64OrNullArrayResponse(response *C.struct_CommandResponse) ([][]float64, error) {
	defer C.free_command_response(response)
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}
	array, err := parseArray(response)
	if err != nil {
		return nil, err
	}
	converted, err := arrayConverter[[]float64]{
		arrayConverter[float64]{
			nil,
			false,
		},
		false,
	}.convert(array)
	if err != nil {
		return nil, err
	}
	res, ok := converted.([][]float64)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}
	return res, nil
}

func handleAnyResponse(response *C.struct_CommandResponse) (any, error) {
	defer C.free_command_response(response)

	return parseInterface(response)
}

func handleLocationArrayResponse(response *C.struct_CommandResponse) ([]options.Location, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]options.Location, 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		responseArray, err := parseArray(&v)
		if err != nil {
			return nil, err
		}
		location := options.Location{
			Name: responseArray.([]any)[0].(string),
		}

		additionalData := responseArray.([]any)[1].([]any)
		for _, value := range additionalData {
			if v, ok := value.(float64); ok {
				location.Dist = v
			}
			if v, ok := value.(int64); ok {
				location.Hash = v
			}
			if coordArray, ok := value.([]any); ok {
				location.Coord = options.GeospatialData{
					Longitude: coordArray[0].(float64),
					Latitude:  coordArray[1].(float64),
				}
			}
		}
		slice = append(slice, location)
	}

	return slice, nil
}

func handleStringOrNilArrayResponse(response *C.struct_CommandResponse) ([]models.Result[string], error) {
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

func handleIntOrNilResponse(response *C.struct_CommandResponse) (models.Result[int64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Int, true)
	if typeErr != nil {
		return models.CreateNilInt64Result(), typeErr
	}

	if response.response_type == C.Null {
		return models.CreateNilInt64Result(), nil
	}

	return models.CreateInt64Result(int64(response.int_value)), nil
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

func handleIntOrNilArrayResponse(response *C.struct_CommandResponse) ([]models.Result[int64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]models.Result[int64], 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		if v.response_type == C.Null {
			slice = append(slice, models.CreateNilInt64Result())
			continue
		}

		err := checkResponseType(&v, C.Int, false)
		if err != nil {
			return nil, err
		}

		slice = append(slice, models.CreateInt64Result(int64(v.int_value)))
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

func handleFloatOrNilResponse(response *C.struct_CommandResponse) (models.Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Float, true)
	if typeErr != nil {
		return models.CreateNilFloat64Result(), typeErr
	}
	if response.response_type == C.Null {
		return models.CreateNilFloat64Result(), nil
	}
	return models.CreateFloat64Result(float64(response.float_value)), nil
}

// elements in the array could be `null`, but array isn't
func handleFloatOrNilArrayResponse(response *C.struct_CommandResponse) ([]models.Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return nil, typeErr
	}

	slice := make([]models.Result[float64], 0, response.array_value_len)
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		if v.response_type == C.Null {
			slice = append(slice, models.CreateNilFloat64Result())
			continue
		}

		err := checkResponseType(&v, C.Float, false)
		if err != nil {
			return nil, err
		}

		slice = append(slice, models.CreateFloat64Result(float64(v.float_value)))
	}

	return slice, nil
}

func handleLongAndDoubleOrNullResponse(
	response *C.struct_CommandResponse,
) (models.Result[int64], models.Result[float64], error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return models.CreateNilInt64Result(), models.CreateNilFloat64Result(), typeErr
	}

	if response.response_type == C.Null {
		return models.CreateNilInt64Result(), models.CreateNilFloat64Result(), nil
	}

	rank := models.CreateNilInt64Result()
	score := models.CreateNilFloat64Result()
	for _, v := range unsafe.Slice(response.array_value, response.array_value_len) {
		if v.response_type == C.Int {
			rank = models.CreateInt64Result(int64(v.int_value))
		}
		if v.response_type == C.Float {
			score = models.CreateFloat64Result(float64(v.float_value))
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
	aMap := data.(map[string]any)

	converted, err := mapConverter[float64]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]float64)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of map: %T", converted)}
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
	aMap := data.(map[string]any)

	converted, err := mapConverter[string]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]string)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of map: %T", converted)}
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

	return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type received: %T", res)}
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

func handleKeyWithMemberAndScoreResponse(
	response *C.struct_CommandResponse,
) (models.Result[models.KeyWithMemberAndScore], error) {
	defer C.free_command_response(response)

	if response == nil || response.response_type == uint32(C.Null) {
		return models.CreateNilKeyWithMemberAndScoreResult(), nil
	}

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return models.CreateNilKeyWithMemberAndScoreResult(), typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return models.CreateNilKeyWithMemberAndScoreResult(), err
	}

	arr := slice.([]any)
	key := arr[0].(string)
	member := arr[1].(string)
	score := arr[2].(float64)
	return models.CreateKeyWithMemberAndScoreResult(models.KeyWithMemberAndScore{Key: key, Member: member, Score: score}), nil
}

func handleKeyWithArrayOfMembersAndScoresResponse(
	response *C.struct_CommandResponse,
) (models.Result[models.KeyWithArrayOfMembersAndScores], error) {
	defer C.free_command_response(response)

	if response.response_type == uint32(C.Null) {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), nil
	}

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}

	arr := slice.([]any)
	key := arr[0].(string)
	converted, err := mapConverter[float64]{
		nil,
		false,
	}.convert(arr[1])
	if err != nil {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), err
	}
	res, ok := converted.(map[string]float64)

	if !ok {
		return models.CreateNilKeyWithArrayOfMembersAndScoresResult(), &errors.RequestError{
			Msg: fmt.Sprintf("unexpected type of second element: %T", converted),
		}
	}
	MemberAndScoreArray := make([]models.MemberAndScore, 0, len(res))

	for k, v := range res {
		MemberAndScoreArray = append(MemberAndScoreArray, models.MemberAndScore{Member: k, Score: v})
	}

	return models.CreateKeyWithArrayOfMembersAndScoresResult(
		models.KeyWithArrayOfMembersAndScores{Key: key, MembersAndScores: MemberAndScoreArray},
	), nil
}

func handleMemberAndScoreArrayResponse(response *C.struct_CommandResponse) ([]models.MemberAndScore, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return nil, err
	}

	var result []models.MemberAndScore
	for _, arr := range slice.([]any) {
		pair := arr.([]any)
		result = append(result, models.MemberAndScore{Member: pair[0].(string), Score: pair[1].(float64)})
	}
	return result, nil
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

	if arr, ok := slice.([]any); ok {
		resCollection, err := convertToStringArray(arr[1].([]any))
		if err != nil {
			return "", nil, err
		}
		return arr[0].(string), resCollection, nil
	}

	return "", nil, err
}

func handleMapOfArrayOfStringArrayResponse(response *C.struct_CommandResponse) (map[string][][]string, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}
	mapData, err := parseMap(response)
	if err != nil {
		return nil, err
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
	}.convert(mapData)
	if err != nil {
		return nil, err
	}
	claimedEntries, ok := converted.(map[string][][]string)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
	}

	return claimedEntries, nil
}

func handleXRangeResponse(response *C.struct_CommandResponse) ([]models.XRangeResponse, error) {
	defer C.free_command_response(response)

	if response.response_type == uint32(C.Null) {
		return nil, nil
	}

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}
	mapData, err := parseMap(response)
	if err != nil {
		return nil, err
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
	}.convert(mapData)
	if err != nil {
		return nil, err
	}
	claimedEntries, ok := converted.(map[string][][]string)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
	}

	XRangeResponseArray := make([]models.XRangeResponse, 0, len(claimedEntries))

	for k, v := range claimedEntries {
		XRangeResponseArray = append(XRangeResponseArray, models.XRangeResponse{StreamId: k, Entries: v})
	}

	sort.Slice(XRangeResponseArray, func(i, j int) bool {
		return XRangeResponseArray[i].StreamId < XRangeResponseArray[j].StreamId
	})
	return XRangeResponseArray, nil
}

func handleXRevRangeResponse(response *C.struct_CommandResponse) ([]models.XRangeResponse, error) {
	defer C.free_command_response(response)

	if response.response_type == uint32(C.Null) {
		return nil, nil
	}

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}
	mapData, err := parseMap(response)
	if err != nil {
		return nil, err
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
	}.convert(mapData)
	if err != nil {
		return nil, err
	}
	claimedEntries, ok := converted.(map[string][][]string)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
	}

	XRangeResponseArray := make([]models.XRangeResponse, 0, len(claimedEntries))

	for k, v := range claimedEntries {
		XRangeResponseArray = append(XRangeResponseArray, models.XRangeResponse{StreamId: k, Entries: v})
	}

	sort.Slice(XRangeResponseArray, func(i, j int) bool {
		return XRangeResponseArray[i].StreamId > XRangeResponseArray[j].StreamId
	})
	return XRangeResponseArray, nil
}

func handleXAutoClaimResponse(response *C.struct_CommandResponse) (models.XAutoClaimResponse, error) {
	defer C.free_command_response(response)
	var null models.XAutoClaimResponse // default response
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return null, typeErr
	}
	slice, err := parseArray(response)
	if err != nil {
		return null, err
	}
	arr := slice.([]any)
	len := len(arr)
	if len < 2 || len > 3 {
		return null, &errors.RequestError{Msg: fmt.Sprintf("Unexpected response array length: %d", len)}
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
		return null, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
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
			return null, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return models.XAutoClaimResponse{
		NextEntry:       arr[0].(string),
		ClaimedEntries:  claimedEntries,
		DeletedMessages: deletedMessages,
	}, nil
}

func handleXAutoClaimJustIdResponse(response *C.struct_CommandResponse) (models.XAutoClaimJustIdResponse, error) {
	defer C.free_command_response(response)
	var null models.XAutoClaimJustIdResponse // default response
	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return null, typeErr
	}
	slice, err := parseArray(response)
	if err != nil {
		return null, err
	}
	arr := slice.([]any)
	len := len(arr)
	if len < 2 || len > 3 {
		return null, &errors.RequestError{Msg: fmt.Sprintf("Unexpected response array length: %d", len)}
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
		return null, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
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
			return null, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return models.XAutoClaimJustIdResponse{
		NextEntry:       arr[0].(string),
		ClaimedEntries:  claimedEntries,
		DeletedMessages: deletedMessages,
	}, nil
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
	return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type received: %T", res)}
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
	return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type received: %T", res)}
}

func handleXPendingSummaryResponse(response *C.struct_CommandResponse) (models.XPendingSummary, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return models.CreateNilXPendingSummary(), typeErr
	}

	slice, err := parseArray(response)
	if err != nil {
		return models.CreateNilXPendingSummary(), err
	}

	arr := slice.([]any)
	NumOfMessages := arr[0].(int64)
	var StartId, EndId models.Result[string]
	if arr[1] == nil {
		StartId = models.CreateNilStringResult()
	} else {
		StartId = models.CreateStringResult(arr[1].(string))
	}
	if arr[2] == nil {
		EndId = models.CreateNilStringResult()
	} else {
		EndId = models.CreateStringResult(arr[2].(string))
	}

	if pendingMessages, ok := arr[3].([]any); ok {
		var ConsumerPendingMessages []models.ConsumerPendingMessage
		for _, msg := range pendingMessages {
			consumerMessage := msg.([]any)
			count, err := strconv.ParseInt(consumerMessage[1].(string), 10, 64)
			if err == nil {
				ConsumerPendingMessages = append(ConsumerPendingMessages, models.ConsumerPendingMessage{
					ConsumerName: consumerMessage[0].(string),
					MessageCount: count,
				})
			}
		}
		return models.XPendingSummary{
			NumOfMessages:    NumOfMessages,
			StartId:          StartId,
			EndId:            EndId,
			ConsumerMessages: ConsumerPendingMessages,
		}, nil
	} else {
		return models.XPendingSummary{NumOfMessages: NumOfMessages, StartId: StartId, EndId: EndId, ConsumerMessages: make([]models.ConsumerPendingMessage, 0)}, nil
	}
}

func handleXPendingDetailResponse(response *C.struct_CommandResponse) ([]models.XPendingDetail, error) {
	// response should be [][]any

	defer C.free_command_response(response)

	// TODO: Not sure if this is correct for a nill response
	if response == nil || response.response_type == uint32(C.Null) {
		return make([]models.XPendingDetail, 0), nil
	}

	typeErr := checkResponseType(response, C.Array, true)
	if typeErr != nil {
		return make([]models.XPendingDetail, 0), typeErr
	}

	// parse first level of array
	slice, err := parseArray(response)
	arr := slice.([]any)

	if err != nil {
		return make([]models.XPendingDetail, 0), err
	}

	pendingDetails := make([]models.XPendingDetail, 0, len(arr))

	for _, message := range arr {
		switch detail := message.(type) {
		case []any:
			pDetail := models.XPendingDetail{
				Id:            detail[0].(string),
				ConsumerName:  detail[1].(string),
				IdleTime:      detail[2].(int64),
				DeliveryCount: detail[3].(int64),
			}
			pendingDetails = append(pendingDetails, pDetail)

		case models.XPendingDetail:
			pendingDetails = append(pendingDetails, detail)
		default:
			fmt.Printf("handleXPendingDetailResponse - unhandled type: %s\n", reflect.TypeOf(detail))
		}
	}

	return pendingDetails, nil
}

func handleXInfoConsumersResponse(response *C.struct_CommandResponse) ([]models.XInfoConsumerInfo, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}
	arrData, err := parseArray(response)
	if err != nil {
		return nil, err
	}
	converted, err := arrayConverter[map[string]any]{
		nil,
		false,
	}.convert(arrData)
	if err != nil {
		return nil, err
	}
	arr, ok := converted.([]map[string]any)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}

	result := make([]models.XInfoConsumerInfo, 0, len(arr))

	for _, group := range arr {
		info := models.XInfoConsumerInfo{
			Name:    group["name"].(string),
			Pending: group["pending"].(int64),
			Idle:    group["idle"].(int64),
		}
		switch inactive := group["inactive"].(type) {
		case int64:
			info.Inactive = models.CreateInt64Result(inactive)
		default:
			info.Inactive = models.CreateNilInt64Result()
		}
		result = append(result, info)
	}

	return result, nil
}

func handleXInfoGroupsResponse(response *C.struct_CommandResponse) ([]models.XInfoGroupInfo, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Array, false)
	if typeErr != nil {
		return nil, typeErr
	}
	arrData, err := parseArray(response)
	if err != nil {
		return nil, err
	}
	converted, err := arrayConverter[map[string]any]{
		nil,
		false,
	}.convert(arrData)
	if err != nil {
		return nil, err
	}
	arr, ok := converted.([]map[string]any)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}

	result := make([]models.XInfoGroupInfo, 0, len(arr))

	for _, group := range arr {
		info := models.XInfoGroupInfo{
			Name:            group["name"].(string),
			Consumers:       group["consumers"].(int64),
			Pending:         group["pending"].(int64),
			LastDeliveredId: group["last-delivered-id"].(string),
		}
		switch lag := group["lag"].(type) {
		case int64:
			info.Lag = models.CreateInt64Result(lag)
		default:
			info.Lag = models.CreateNilInt64Result()
		}
		switch entriesRead := group["entries-read"].(type) {
		case int64:
			info.EntriesRead = models.CreateInt64Result(entriesRead)
		default:
			info.EntriesRead = models.CreateNilInt64Result()
		}
		result = append(result, info)
	}

	return result, nil
}

func handleStringToAnyMapResponse(response *C.struct_CommandResponse) (map[string]any, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}
	result, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	return result.(map[string]any), nil
}

func handleRawStringArrayMapResponse(response *C.struct_CommandResponse) (map[string][]string, error) {
	defer C.free_command_response(response)
	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}

	result, err := mapConverter[[]string]{
		next:     arrayConverter[string]{},
		canBeNil: false,
	}.convert(data)
	if err != nil {
		return nil, err
	}
	mapResult, ok := result.(map[string][]string)
	if !ok {
		return nil, &errors.RequestError{Msg: "Unexpected conversion result type"}
	}

	return mapResult, nil
}

func handleMapOfStringMapResponse(response *C.struct_CommandResponse) (map[string]map[string]string, error) {
	defer C.free_command_response(response)
	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}

	result, err := mapConverter[map[string]string]{
		next:     mapConverter[string]{},
		canBeNil: false,
	}.convert(data)
	if err != nil {
		return nil, err
	}
	mapResult, ok := result.(map[string]map[string]string)
	if !ok {
		return nil, &errors.RequestError{Msg: "Unexpected conversion result type"}
	}

	return mapResult, nil
}

func handleTimeClusterResponse(response *C.struct_CommandResponse) (models.ClusterValue[[]string], error) {
	// Handle multi-node response
	if err := checkResponseType(response, C.Map, true); err == nil {
		mapData, err := handleRawStringArrayMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[[]string](), err
		}
		multiNodeTimes := make(map[string][]string)
		for nodeName, nodeTimes := range mapData {
			multiNodeTimes[nodeName] = nodeTimes
		}
		return models.CreateClusterMultiValue(multiNodeTimes), nil
	}

	// Handle single node response
	data, err := handleStringArrayResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[[]string](), err
	}
	return models.CreateClusterSingleValue(data), nil
}

func handleStringIntMapResponse(response *C.struct_CommandResponse) (map[string]int64, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	aMap := data.(map[string]any)

	converted, err := mapConverter[int64]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]int64)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of map: %T", converted)}
	}
	return result, nil
}

func handleFunctionStatsResponse(response *C.struct_CommandResponse) (map[string]models.FunctionStatsResult, error) {
	if err := checkResponseType(response, C.Map, false); err != nil {
		return nil, err
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}

	result := make(map[string]models.FunctionStatsResult)

	// Process all nodes in the response
	for nodeAddr, nodeData := range data.(map[string]any) {
		nodeMap, ok := nodeData.(map[string]any)
		if !ok {
			continue // Skip if nodeData is not a map, e.g. when there isn't a running script
		}

		// Process engines
		engines := make(map[string]models.Engine)
		if enginesMap, ok := nodeMap["engines"].(map[string]any); ok {
			for engineName, engineData := range enginesMap {
				if engineMap, ok := engineData.(map[string]any); ok {
					engine := models.Engine{
						Language:      engineName,
						FunctionCount: engineMap["functions_count"].(int64),
						LibraryCount:  engineMap["libraries_count"].(int64),
					}
					engines[engineName] = engine
				}
			}
		}

		// Process running script
		var runningScript models.RunningScript
		if scriptData := nodeMap["running_script"]; scriptData != nil {
			if scriptMap, ok := scriptData.(map[string]any); ok {
				runningScript = models.RunningScript{
					Name:     scriptMap["name"].(string),
					Cmd:      scriptMap["command"].(string),
					Args:     scriptMap["arguments"].([]string),
					Duration: time.Duration(scriptMap["duration_ms"].(int64)) * time.Millisecond,
				}
			}
		}

		result[nodeAddr] = models.FunctionStatsResult{
			Engines:       engines,
			RunningScript: runningScript,
		}
	}

	return result, nil
}

func parseFunctionInfo(items any) []models.FunctionInfo {
	result := make([]models.FunctionInfo, 0, len(items.([]any)))
	for _, item := range items.([]any) {
		if function, ok := item.(map[string]any); ok {
			// Handle nullable description
			var description string
			if desc, ok := function["description"].(string); ok {
				description = desc
			}

			// Handle flags map
			flags := make([]string, 0)
			if flagsMap, ok := function["flags"].(map[string]struct{}); ok {
				for flag := range flagsMap {
					flags = append(flags, flag)
				}
			}

			result = append(result, models.FunctionInfo{
				Name:        function["name"].(string),
				Description: description,
				Flags:       flags,
			})
		}
	}
	return result
}

func parseLibraryInfo(itemMap map[string]any) models.LibraryInfo {
	libraryInfo := models.LibraryInfo{
		Name:      itemMap["library_name"].(string),
		Engine:    itemMap["engine"].(string),
		Functions: parseFunctionInfo(itemMap["functions"]),
	}
	// Handle optional library_code field
	if code, ok := itemMap["library_code"].(string); ok {
		libraryInfo.Code = code
	}
	return libraryInfo
}

func handleFunctionListResponse(response *C.struct_CommandResponse) ([]models.LibraryInfo, error) {
	if err := checkResponseType(response, C.Array, false); err != nil {
		return nil, err
	}

	data, err := parseArray(response)
	if err != nil {
		return nil, err
	}
	result := make([]models.LibraryInfo, 0, len(data.([]any)))
	for _, item := range data.([]any) {
		if itemMap, ok := item.(map[string]any); ok {
			result = append(result, parseLibraryInfo(itemMap))
		}
	}
	return result, nil
}

func handleFunctionListMultiNodeResponse(response *C.struct_CommandResponse) (map[string][]models.LibraryInfo, error) {
	data, err := handleStringToAnyMapResponse(response)
	if err != nil {
		return nil, err
	}

	multiNodeLibs := make(map[string][]models.LibraryInfo)
	for node, nodeData := range data {
		// nodeData is already parsed into a Go array of interfaces
		if nodeArray, ok := nodeData.([]any); ok {
			libs := make([]models.LibraryInfo, 0, len(nodeArray))
			for _, item := range nodeArray {
				if itemMap, ok := item.(map[string]any); ok {
					libs = append(libs, parseLibraryInfo(itemMap))
				}
			}
			multiNodeLibs[node] = libs
		}
	}
	return multiNodeLibs, nil
}

func handleSortedSetWithScoresResponse(response *C.struct_CommandResponse, reverse bool) ([]models.MemberAndScore, error) {
	defer C.free_command_response(response)

	typeErr := checkResponseType(response, C.Map, false)
	if typeErr != nil {
		return nil, typeErr
	}

	data, err := parseMap(response)
	if err != nil {
		return nil, err
	}
	aMap := data.(map[string]any)

	converted, err := mapConverter[float64]{
		nil, false,
	}.convert(aMap)
	if err != nil {
		return nil, err
	}
	result, ok := converted.(map[string]float64)
	if !ok {
		return nil, &errors.RequestError{Msg: fmt.Sprintf("unexpected type of map: %T", converted)}
	}

	zRangeResponseArray := make([]models.MemberAndScore, 0, len(result))

	for k, v := range result {
		zRangeResponseArray = append(zRangeResponseArray, models.MemberAndScore{Member: k, Score: v})
	}

	if !reverse {
		sort.Slice(zRangeResponseArray, func(i, j int) bool {
			if zRangeResponseArray[i].Score == zRangeResponseArray[j].Score {
				return zRangeResponseArray[i].Member < zRangeResponseArray[j].Member
			}
			return zRangeResponseArray[i].Score < zRangeResponseArray[j].Score
		})
	} else {
		sort.Slice(zRangeResponseArray, func(i, j int) bool {
			if zRangeResponseArray[i].Score == zRangeResponseArray[j].Score {
				return zRangeResponseArray[i].Member > zRangeResponseArray[j].Member
			}
			return zRangeResponseArray[i].Score > zRangeResponseArray[j].Score
		})
	}

	return zRangeResponseArray, nil
}
