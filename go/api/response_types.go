// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

type StringValue struct {
	Val   string
	IsNil bool
}

type Int64Value struct {
	Val   int64
	IsNil bool
}

type Float64Value struct {
	Val   float64
	IsNil bool
}

type BoolValue struct {
	Val   bool
	IsNil bool
}
