// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import "testing"

// Values mirror the ResponseType enum in lib.h; cgo cannot be used from _test.go files.
func TestResponseTypeName(t *testing.T) {
	cases := map[uint32]string{
		0:  "Null",
		1:  "Int",
		2:  "Float",
		3:  "Bool",
		4:  "String",
		5:  "Array",
		6:  "Map",
		7:  "Sets",
		8:  "Ok",
		9:  "Error",
		99: "Unknown(99)",
	}

	for responseType, want := range cases {
		if got := responseTypeName(responseType); got != want {
			t.Errorf("responseTypeName(%d) = %q, want %q", responseType, got, want)
		}
	}
}
