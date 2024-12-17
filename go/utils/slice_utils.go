// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package utils

// check if sliceA is a subset of sliceB
func IsSubset(sliceA, sliceB []string) bool {
	setB := make(map[string]struct{})
	for _, v := range sliceB {
		setB[v] = struct{}{}
	}
	for _, v := range sliceA {
		if _, found := setB[v]; !found {
			return false
		}
	}
	return true
}
