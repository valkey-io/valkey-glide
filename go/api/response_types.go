// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

type Result[T any] struct {
	val   T
	isNil bool
}

func (result Result[T]) IsNil() bool {
	return result.isNil
}

func (result Result[T]) Value() T {
	return result.val
}

func CreateStringResult(str string) Result[string] {
	return Result[string]{val: str, isNil: false}
}

func CreateNilStringResult() Result[string] {
	return Result[string]{val: "", isNil: true}
}

func CreateInt64Result(intVal int64) Result[int64] {
	return Result[int64]{val: intVal, isNil: false}
}

func CreateNilInt64Result() Result[int64] {
	return Result[int64]{val: 0, isNil: true}
}

func CreateFloat64Result(floatVal float64) Result[float64] {
	return Result[float64]{val: floatVal, isNil: false}
}

func CreateNilFloat64Result() Result[float64] {
	return Result[float64]{val: 0, isNil: true}
}

func CreateBoolResult(boolVal bool) Result[bool] {
	return Result[bool]{val: boolVal, isNil: false}
}

func CreateNilBoolResult() Result[bool] {
	return Result[bool]{val: false, isNil: true}
}

// Enum-like structure which stores either a single-node response or multi-node response.
// Multi-node response stored in a map, where keys are hostnames or "<ip>:<port>" strings.
type ClusterValue struct {
	singleValue Result[interface{}]
	multiValue  Result[map[string]interface{}]
}

func (value ClusterValue) Value() interface{} {
	if !value.singleValue.isNil {
		return value.singleValue.Value()
	}
	return value.multiValue.Value()
}

// Create a `ClusterValue` with auto-detecting the value type.
// Could confuse a user if data is a map received as a response from a single node.
func CreateClusterValue(data interface{}) ClusterValue {
	switch data.(type) {
	case map[interface{}]interface{}:
		return CreateClusterMultiValue(data)
	default:
		return CreateClusterSingleValue(data)
	}
}

func CreateClusterSingleValue(data interface{}) ClusterValue {
	return ClusterValue{
		singleValue: Result[interface{}]{data, false},
		multiValue:  Result[map[string]interface{}]{make(map[string]interface{}, 0), true},
	}
}

func CreateClusterMultiValue(data interface{}) ClusterValue {
	var empty interface{}
	return ClusterValue{
		multiValue:  Result[map[string]interface{}]{data.(map[string]interface{}), false},
		singleValue: Result[interface{}]{empty, true},
	}
}

func CreateEmptyClusterValue() ClusterValue {
	var empty interface{}
	return ClusterValue{
		multiValue:  Result[map[string]interface{}]{make(map[string]interface{}, 0), true},
		singleValue: Result[interface{}]{empty, true},
	}
}
