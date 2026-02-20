// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import "testing"

func TestCompareSemanticVersions(t *testing.T) {
	testCases := []struct {
		name     string
		current  string
		required string
		expected int
	}{
		{
			name:     "major version is higher",
			current:  "10.0.0",
			required: "7.2.0",
			expected: 1,
		},
		{
			name:     "minor version is higher",
			current:  "7.10.0",
			required: "7.2.0",
			expected: 1,
		},
		{
			name:     "patch version is lower",
			current:  "7.2.3",
			required: "7.2.4",
			expected: -1,
		},
		{
			name:     "versions are equal",
			current:  "8.1.0",
			required: "8.1.0",
			expected: 0,
		},
		{
			name:     "pre-release is lower than release",
			current:  "9.0.0-rc1",
			required: "9.0.0",
			expected: -1,
		},
		{
			name:     "release is higher than pre-release",
			current:  "9.0.0",
			required: "9.0.0-rc1",
			expected: 1,
		},
		{
			name:     "pre-release identifiers are compared semantically",
			current:  "9.0.0-rc.2",
			required: "9.0.0-rc.1",
			expected: 1,
		},
		{
			name:     "pre-release numeric identifiers compare as numbers not strings",
			current:  "9.0.0-rc.10",
			required: "9.0.0-rc.2",
			expected: 1,
		},
		{
			name:     "build metadata is ignored",
			current:  "9.0.1+build42",
			required: "9.0.1",
			expected: 0,
		},
		{
			name:     "numeric pre-release identifier is lower than alpha identifier",
			current:  "9.0.0-1",
			required: "9.0.0-alpha",
			expected: -1,
		},
		{
			name:     "alpha pre-release identifier is higher than numeric identifier",
			current:  "9.0.0-alpha",
			required: "9.0.0-1",
			expected: 1,
		},
		{
			name:     "shorter pre-release identifier list is lower",
			current:  "9.0.0-rc.1",
			required: "9.0.0-rc.1.1",
			expected: -1,
		},
		{
			name:     "longer pre-release identifier list is higher when prefix matches",
			current:  "9.0.0-rc.1.1",
			required: "9.0.0-rc.1",
			expected: 1,
		},
		{
			name:     "normalization trims spaces prefix and metadata",
			current:  " v9.0.1+build42 ",
			required: "9.0.1",
			expected: 0,
		},
		{
			name:     "required version normalization trims spaces prefix and metadata",
			current:  "9.0.1",
			required: " v9.0.1+meta ",
			expected: 0,
		},
		{
			name:     "two-segment version equals explicit patch zero",
			current:  "7.2",
			required: "7.2.0",
			expected: 0,
		},
		{
			name:     "two-segment version lower than next patch",
			current:  "7.2",
			required: "7.2.1",
			expected: -1,
		},
		{
			name:     "two-segment version compares larger minor correctly",
			current:  "7.10",
			required: "7.2.9",
			expected: 1,
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			comparison, err := compareSemanticVersions(testCase.current, testCase.required)
			if err != nil {
				t.Fatalf("unexpected compare error: %s", err.Error())
			}

			if comparison != testCase.expected {
				t.Fatalf(
					"expected compare result %d, got %d (current=%s, required=%s)",
					testCase.expected,
					comparison,
					testCase.current,
					testCase.required,
				)
			}
		})
	}
}

func TestGlideTestSuite_ServerVersionPredicates(t *testing.T) {
	preReleaseSuite := &GlideTestSuite{serverVersion: "9.0.0-rc1"}
	preReleaseSuite.SetT(t)
	if preReleaseSuite.IsServerVersionAtLeast("9.0.0") {
		t.Fatalf("expected 9.0.0-rc1 to be lower than 9.0.0")
	}
	if !preReleaseSuite.IsServerVersionLowerThan("9.0.0") {
		t.Fatalf("expected 9.0.0-rc1 to be lower than 9.0.0")
	}

	stableSuite := &GlideTestSuite{serverVersion: "7.2.0"}
	stableSuite.SetT(t)
	if !stableSuite.IsServerVersionAtLeast("7.2.0") {
		t.Fatalf("expected 7.2.0 to satisfy at least 7.2.0")
	}
	if stableSuite.IsServerVersionGreaterThan("7.2.0") {
		t.Fatalf("expected 7.2.0 not to be greater than 7.2.0")
	}
	if !stableSuite.IsServerVersionGreaterThan("7.1.9") {
		t.Fatalf("expected 7.2.0 to be greater than 7.1.9")
	}
	if !stableSuite.IsServerVersionGreaterThan("7.2.0-rc1") {
		t.Fatalf("expected stable release to be greater than its pre-release")
	}
}

func TestCompareSemanticVersions_InvalidFormat(t *testing.T) {
	testCases := []struct {
		name     string
		current  string
		required string
	}{
		{
			name:     "current version missing minor and patch",
			current:  "7",
			required: "7.0.0",
		},
		{
			name:     "required version contains text segment",
			current:  "7.2.0",
			required: "7.x.0",
		},
		{
			name:     "current version with empty pre-release identifier",
			current:  "9.0.0-rc..1",
			required: "9.0.0",
		},
		{
			name:     "required version with dangling pre-release separator",
			current:  "9.0.0",
			required: "9.0.0-",
		},
		{
			name:     "required version with too many core segments",
			current:  "9.0.0",
			required: "9.0.0.1",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			_, err := compareSemanticVersions(testCase.current, testCase.required)
			if err == nil {
				t.Fatalf(
					"expected an error for versions current=%s required=%s",
					testCase.current,
					testCase.required,
				)
			}
		})
	}
}
