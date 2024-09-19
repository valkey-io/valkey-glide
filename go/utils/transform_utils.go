// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package utils

import (
	"strconv"
	"unsafe"
)

// Convert `s` of type `string` into `[]byte`
func StringToBytes(s string) []byte {
	p := unsafe.StringData(s)
	b := unsafe.Slice(p, len(s))
	return b
}

func IntToString(value int64) string {
	return strconv.FormatInt(value, 10 /*base*/)
}

func FloatToString(value float64) string {
	return strconv.FormatFloat(value, 'g', -1 /*precision*/, 64 /*bit*/)
}

// Flattens the Map: { (key1, value1), (key2, value2), ..} to a slice { key1, value1, key2, value2, ..}
func MapToString(parameter map[string]string) []string {
	flat := make([]string, 0, len(parameter)*2)
	for key, value := range parameter {
		flat = append(flat, key, value)
	}
	return flat
}
