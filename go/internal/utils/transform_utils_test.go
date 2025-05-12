// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package utils

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestConvertMapToKeyValueStringArray(t *testing.T) {
	// Define test cases
	testCases := []struct {
		name     string
		key      string
		args     map[string]string
		expected []string
	}{
		{
			name:     "Test with single key-value pair",
			key:      "singleKey",
			args:     map[string]string{"key1": "value1"},
			expected: []string{"singleKey", "key1", "value1"},
		},
		{
			name:     "Test with multiple key-value pairs",
			key:      "multiKeys",
			args:     map[string]string{"key1": "value1", "key2": "value2", "key3": "value3"},
			expected: []string{"multiKeys", "key1", "value1", "key2", "value2", "key3", "value3"},
		},
		{
			name:     "Test with empty map",
			key:      "emptyKey",
			args:     map[string]string{},
			expected: []string{"emptyKey"},
		},
	}

	// Iterate through test cases.
	for _, testCase := range testCases {
		// Run each test case as a subtest.
		t.Run(testCase.name, func(t *testing.T) {
			// Call the function being tested.
			actual := ConvertMapToKeyValueStringArray(testCase.key, testCase.args)

			// Check if the lengths of actual and expected slices match.
			if len(actual) != len(testCase.expected) {
				t.Errorf("Length mismatch. Expected %d, got %d", len(testCase.expected), len(actual))
			}

			// Check if the key is present in the actual result.
			assert.Contains(t, actual, testCase.key, "The key should be present in the result")

			// Check if all key-value pairs from the input map are present in the actual result.
			for k, v := range testCase.args {
				assert.Contains(t, actual, k, "The key from the input map should be present in the result")
				assert.Contains(t, actual, v, "The value from the input map should be present in the result")
			}
		})
	}
}

func TestConcat(t *testing.T) {
	// Define test cases
	tests := []struct {
		name     string
		inputs   [][]string // Slice of slices to be concatenated
		expected []string   // Expected result
	}{
		{
			name:     "Multiple non-empty slices",
			inputs:   [][]string{{"a", "b"}, {"c", "d"}},
			expected: []string{"a", "b", "c", "d"},
		},
		{
			name:     "Empty slice with non-empty",
			inputs:   [][]string{{"a", "b"}, {}},
			expected: []string{"a", "b"},
		},
		{
			name:     "Multiple empty slices",
			inputs:   [][]string{{}, {}},
			expected: []string{},
		},
		{
			name:     "Varied lengths",
			inputs:   [][]string{{"1", "2"}, {"3"}, {"4", "5", "6"}},
			expected: []string{"1", "2", "3", "4", "5", "6"},
		},
	}

	// Run each test case
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Use the variadic argument to unpack the input slices
			result := Concat(tt.inputs...)
			// Use assert.Equal to compare the result with the expected output
			assert.Equal(t, tt.expected, result, "The result of concatenation should match the expected output")
		})
	}
}
