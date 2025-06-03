// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

// #include "../lib.h"
import "C"

import (
	"fmt"
	"reflect"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

type Batch struct {
	Commands []Cmd
	IsAtomic bool
	Errors   []string // errors processing command args, spotted while batch is filled
}

type Cmd struct {
	RequestType uint32 // TODO why C.RequestType doesn't work?
	Args        []string
	// Response converter
	Converter func(any) any
}

func MakeCmd(requestType uint32, args []string, converter func(any) any) Cmd {
	return Cmd{RequestType: requestType, Args: args, Converter: converter}
}

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Commands) {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf("Response misaligned: received %d responses for %d commands", len(response), len(b.Commands)),
		}
	}
	for i, res := range response {
		response[i] = b.Commands[i].Converter(res)
	}
	return response, nil
}

type BatchOptions struct {
	Timeout              *uint32
	Route                *config.Route
	RetryServerError     *bool
	RetryConnectionError *bool
}

// get type of T
func GetType[T any]() reflect.Type {
	var zero [0]T
	return reflect.TypeOf(zero).Elem()
}

// ================================

// convert (typecast) untyped response into a typed value
// for example, an arbitrary array `[]any` into `[]string`
type responseConverter interface {
	convert(data any) any
}

// convert maps, T - type of the value, key is string
type mapConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

// convert arrays, T - type of the value
type arrayConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

// ================================

// Converts an untyped map into a map[string]T
func (node mapConverter[T]) convert(data any) any {
	if data == nil {
		if node.canBeNil {
			return nil
		} else {
			return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: map[string]%v", GetType[T]())}
		}
	}
	result := make(map[string]T)

	// Iterate over the map and convert each value to T
	for key, value := range data.(map[string]any) {
		if node.next == nil {
			// try direct conversion to T when there is no next converter
			valueT, ok := value.(T)
			if !ok {
				return &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", value, GetType[T]()),
				}
			}
			result[key] = valueT
		} else {
			// nested iteration when there is a next converter
			val := node.next.convert(value)
			if err, ok := val.(errors.RequestError); ok {
				return err
			}
			if val == nil {
				var null T
				result[key] = null
				continue
			}
			// convert to T
			valueT, ok := val.(T)
			if !ok {
				return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", val, GetType[T]())}
			}
			result[key] = valueT
		}
	}

	return result
}

func (node arrayConverter[T]) convert(data any) any {
	if data == nil {
		if node.canBeNil {
			return nil
		} else {
			return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: []%v", GetType[T]())}
		}
	}
	arrData := data.([]any)
	result := make([]T, 0, len(arrData))
	for _, value := range arrData {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", value, GetType[T]()),
				}
			}
			result = append(result, valueT)
		} else {
			val := node.next.convert(value)
			if err, ok := val.(errors.RequestError); ok {
				return err
			}
			if val == nil {
				var null T
				result = append(result, null)
				continue
			}
			valueT, ok := val.(T)
			if !ok {
				return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", val, GetType[T]())}
			}
			result = append(result, valueT)
		}
	}

	return result
}

// ================================

func ConvertArrayOfStringOrNil(data any) any {
	arr, _ := data.([]any) // OK is ignored, type assertion should be validated before
	res := make([]models.Result[string], 0, len(arr))

	for _, str := range arr {
		if str == nil {
			res = append(res, models.CreateNilStringResult())
		} else {
			if strr, ok := str.(string); ok {
				res = append(res, models.CreateStringResult(strr))
			} else {
				res = append(res, models.CreateNilStringResult())
			}
		}
	}
	return any(res)
}

func ConvertArrayOf[T any](data any) any {
	arr, _ := data.([]any) // OK is ignored, type assertion should be validated before

	return arrayConverter[T]{
		nil,
		false,
	}.convert(arr)
	// actually returns a []T
}

func ConvertArrayOrNilOf[T any](data any) any {
	if data == nil {
		return nil
	}
	return ConvertArrayOf[T](data)
}

// BZPOPMAX BZPOPMIN
func ConvertKeyWithMemberAndScore(data any) any {
	if data == nil {
		return nil
	}
	arr, _ := data.([]any) // OK is ignored, type assertion should be validated before
	key := arr[0].(string)
	member := arr[1].(string)
	score := arr[2].(float64)
	return models.KeyWithMemberAndScore{Key: key, Member: member, Score: score}
}

// ZMPOP BZMPOP
func ConvertKeyWithArrayOfMembersAndScores(data any) any {
	if data == nil {
		return nil
	}

	arr, _ := data.([]any) // OK is ignored, type assertion should be validated before
	key := arr[0].(string)

	converted := mapConverter[float64]{
		nil,
		false,
	}.convert(arr[1])

	res, ok := converted.(map[string]float64)

	if !ok {
		return &errors.RequestError{
			Msg: fmt.Sprintf("unexpected type of second element: %T", converted),
		}
	}
	MemberAndScoreArray := make([]models.MemberAndScore, 0, len(res))

	for k, v := range res {
		MemberAndScoreArray = append(MemberAndScoreArray, models.MemberAndScore{Member: k, Score: v})
	}

	return models.CreateKeyWithArrayOfMembersAndScoresResult(
		models.KeyWithArrayOfMembersAndScores{Key: key, MembersAndScores: MemberAndScoreArray},
	)
}

func Convert2DArrayOfString(data any) any {
	arr, _ := data.([]any) // OK is ignored, type assertion should be validated before

	return arrayConverter[[]string]{
		arrayConverter[string]{
			nil,
			false,
		},
		false,
	}.convert(arr)
	// actually returns a [][]string
}

func TypeChecker(data any, expectedType reflect.Kind, isNilable bool) any {
	return ConverterAndTypeChecker(data, expectedType, isNilable, func(res any) any { return res })
}

func ConverterAndTypeChecker(data any, expectedType reflect.Kind, isNilable bool, converter func(res any) any) any {
	if data == nil {
		if isNilable {
			return nil
		}
		return &errors.RequestError{
			Msg: fmt.Sprintf("Unexpected return type from Glide: got nil, expected %v", expectedType),
		}
	}
	if reflect.TypeOf(data).Kind() == expectedType {
		return converter(data)
	}
	// data lost even though it was incorrect
	// TODO maybe still return the data?
	return &errors.RequestError{
		Msg: fmt.Sprintf("Unexpected return type from Glide: got %v, expected %v", reflect.TypeOf(data), expectedType),
	}
}
