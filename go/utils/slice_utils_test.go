// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package utils

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIsSubset(t *testing.T) {
	// Define test cases
	tests := []struct {
		name     string
		inputs   [][]string // Slice of slices to be concatenated
		expected bool       // Expected result
	}{
		{
			name:     "Non-subset slices",
			inputs:   [][]string{{"a", "b"}, {"c", "d"}},
			expected: false,
		},
		{
			name:     "Subset slices",
			inputs:   [][]string{{"a", "b"}, {"a", "b", "c", "d"}},
			expected: true,
		},
	}

	// Run each test case
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Use the variadic argument to unpack the input slices
			result := IsSubset(tt.inputs[0], tt.inputs[1])
			// Use assert.Equal to compare the result with the expected output
			assert.Equal(t, tt.expected, result, "The result of concatenation should match the expected output")
		})
	}
}
