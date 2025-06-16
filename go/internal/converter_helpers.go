// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"fmt"
	"reflect"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// get type of T
func GetType[T any]() reflect.Type {
	var zero [0]T
	return reflect.TypeOf(zero).Elem()
}

// ================================

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

// convert arrays, T - type of the value
type arrayConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

type keyValuesConverter struct {
	canBeNil bool
}

// ================================

// Converts an untyped map into a map[string]T
func (node mapConverter[T]) convert(data any) (any, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, fmt.Errorf("unexpected type received: nil, expected: map[string]%v", GetType[T]())
		}
	}
	result := make(map[string]T)

	// Iterate over the map and convert each value to T
	for key, value := range data.(map[string]any) {
		if node.next == nil {
			// try direct conversion to T when there is no next converter
			valueT, ok := value.(T)
			if !ok {
				return nil, fmt.Errorf("unexpected type of map element: %T, expected: %v", value, GetType[T]())
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
				return nil, fmt.Errorf("unexpected type of map element: %T, expected: %v", val, GetType[T]())
			}
			result[key] = valueT
		}
	}

	return result, nil
}

// Converts an untyped array into a []T
func (node arrayConverter[T]) convert(data any) (any, error) {
	if data == nil {
		if node.canBeNil {
			return nil, nil
		} else {
			return nil, fmt.Errorf("unexpected type received: nil, expected: []%v", GetType[T]())
		}
	}
	arrData := data.([]any)
	result := make([]T, 0, len(arrData))
	for _, value := range arrData {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return nil, fmt.Errorf("unexpected type of array element: %T, expected: %v", value, GetType[T]())
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
				return nil, fmt.Errorf("unexpected type of array element: %T, expected: %v", val, GetType[T]())
			}
			result = append(result, valueT)
		}
	}

	return result, nil
}

// Converts an untyped map into a []models.KeyValues
func (node keyValuesConverter) convert(data any) ([]models.KeyValues, error) {
	converters := mapConverter[[]string]{
		arrayConverter[string]{},
		false,
	}

	res, err := converters.convert(data)
	if err != nil {
		return nil, err
	}
	if result, ok := res.(map[string][]string); ok {
		resultArray := make([]models.KeyValues, 0, len(result))
		for key, values := range result {
			resultArray = append(resultArray, models.KeyValues{Key: key, Values: values})
		}
		return resultArray, nil
	}

	return nil, fmt.Errorf("unexpected type received: %T", res)
}

func ReadValue[T any](data map[string]any, field string, into *T) {
	if val, ok := data[field].(T); ok {
		*into = val
	}
}

func ReadResult[T any](data map[string]any, field string, info *models.Result[T]) {
	switch val := data[field].(type) {
	case T:
		*info = models.CreateResultOf(val)
	default:
		*info = models.CreateNilResultOf[T]()
	}
}

// ParseLCSMatchedPositions converts the nested array structure from LCSWithOptions into a slice of LCSMatchedPosition structs
// The input structure has the shape of:
// ```
// 1) 1) 1) (integer) 4
//  2. (integer) 7
//  2. 1) (integer) 5
//  2. (integer) 8
//  2. 1) 1) (integer) 2
//  2. (integer) 3
//  2. 1) (integer) 0
//  2. (integer) 1
//
// ```
// which represents matched positions between two strings
func ParseLCSMatchedPositions(matches any) ([]models.LCSMatchedPosition, error) {
	if matches == nil {
		return []models.LCSMatchedPosition{}, nil
	}

	matchesArray, ok := matches.([]any)
	if !ok {
		return nil, fmt.Errorf("expected matches to be an array, got %T", matches)
	}

	result := make([]models.LCSMatchedPosition, len(matchesArray))
	for i, match := range matchesArray {
		matchArray, ok := match.([]any)
		if !ok {
			return nil, fmt.Errorf("expected match to be an array, got %T ", match)
		}

		if len(matchArray) != 2 && len(matchArray) != 3 {
			return nil, fmt.Errorf(
				"expected match to be an array of length 2 or 3, got %T with length %d",
				matchArray,
				len(matchArray),
			)
		}

		// Parse Key1 position
		key1Array, ok := matchArray[0].([]any)
		if !ok || len(key1Array) != 2 {
			return nil, fmt.Errorf(
				"expected key1 to be an array of length 2, got %T with length %d",
				matchArray[0],
				len(key1Array),
			)
		}

		key1Start, err := ConvertToInt64(key1Array[0])
		if err != nil {
			return nil, fmt.Errorf("expected key1 start to be a number, got %T", key1Array[0])
		}

		key1End, err := ConvertToInt64(key1Array[1])
		if err != nil {
			return nil, fmt.Errorf("expected key1 end to be a number, got %T", key1Array[1])
		}

		// Parse Key2 position
		key2Array, ok := matchArray[1].([]any)
		if !ok || len(key2Array) != 2 {
			return nil, fmt.Errorf(
				"expected key2 to be an array of length 2, got %T with length %d",
				matchArray[1],
				len(key2Array),
			)
		}

		key2Start, err := ConvertToInt64(key2Array[0])
		if err != nil {
			return nil, fmt.Errorf("expected key2 start to be a number, got %T", key2Array[0])
		}

		key2End, err := ConvertToInt64(key2Array[1])
		if err != nil {
			return nil, fmt.Errorf("expected key2 end to be a number, got %T", key2Array[1])
		}

		var matchLen int64
		if len(matchArray) == 3 {
			matchLen, err = ConvertToInt64(matchArray[2])
			if err != nil {
				return nil, fmt.Errorf("expected match length to be a number, got %T", matchArray[2])
			}
		}

		result[i] = models.LCSMatchedPosition{
			Key1: models.LCSPosition{
				Start: key1Start,
				End:   key1End,
			},
			Key2: models.LCSPosition{
				Start: key2Start,
				End:   key2End,
			},
			MatchLen: matchLen,
		}
	}

	return result, nil
}

// toInt64 converts any numeric value to int64
func ConvertToInt64(value any) (int64, error) {
	switch v := value.(type) {
	case int64:
		return v, nil
	case int:
		return int64(v), nil
	case float64:
		return int64(v), nil
	case string:
		parsed, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return 0, fmt.Errorf("cannot convert string %q to int64: %w", v, err)
		}
		return parsed, nil
	default:
		return 0, fmt.Errorf("cannot convert %T to int64", value)
	}
}
