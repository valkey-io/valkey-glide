// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"fmt"
	"reflect"

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
	next     responseConverter
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
