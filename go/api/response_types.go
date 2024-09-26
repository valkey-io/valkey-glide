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
