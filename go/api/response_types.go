// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Struct for returning a string in the response of a command. Since strings can't be nil in GO, IsNil will represent whether
// the string is nil or an empty string is present.
type StringValue struct {
	Val   string
	IsNil bool
}

// Struct for returning a int64 in the response of a command. Since int64 can't be nil in GO, IsNil will represent whether the
// val is nil or 0 is present.
type Int64Value struct {
	Val   int64
	IsNil bool
}

// Struct for returning a float64 in the response of a command. Since folat64 can't be nil in GO, IsNil will represent whether
// the val is nil or 0 is present.
type Float64Value struct {
	Val   float64
	IsNil bool
}

// Struct for returning a bool in the response of a command. Since bool can't be nil in GO, IsNil will represent whether the
// val is nil or false is present.
type BoolValue struct {
	Val   bool
	IsNil bool
}
