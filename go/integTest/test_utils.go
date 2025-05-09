// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strconv"
	"strings"
)

// check if sliceA is a subset of sliceB
func isSubset[T comparable](sliceA []T, sliceB []T) bool {
	setB := make(map[T]struct{})
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

func convertMapKeysAndValuesToLists(m map[string]string) ([]string, []string) {
	keys := make([]string, 0)
	values := make([]string, 0)
	for key, value := range m {
		keys = append(keys, key)
		values = append(values, value)
	}
	return keys, values
}

func GenerateLuaLibCode(libName string, functions map[string]string, readonly bool) string {
	var code strings.Builder

	// Write header
	code.WriteString("#!lua name=")
	code.WriteString(libName)
	code.WriteString("\n")

	// Write each function
	for name, function := range functions {
		code.WriteString("redis.register_function{ function_name = '")
		code.WriteString(name)
		code.WriteString("', callback = function(keys, args) ")
		code.WriteString(function)
		code.WriteString(" end")

		if readonly {
			code.WriteString(", flags = { 'no-writes' }")
		}
		code.WriteString(" }\n")
	}

	return code.String()
}

func createLuaLibWithLongRunningFunction(libName, funcName string, timeout int, readOnly bool) string {
	var code strings.Builder

	// Write header
	code.WriteString("#!lua name=")
	code.WriteString(libName)
	code.WriteString("\n")

	// Write function definition
	code.WriteString("local function ")
	code.WriteString(libName)
	code.WriteString("_")
	code.WriteString(funcName)
	code.WriteString("(keys, args)\n")
	code.WriteString("  local started = tonumber(redis.pcall('time')[1])\n")
	// fun fact - redis does no writes if 'no-writes' flag is set
	code.WriteString("  redis.pcall('set', keys[1], 42)\n")
	code.WriteString("  while (true) do\n")
	code.WriteString("    local now = tonumber(redis.pcall('time')[1])\n")
	code.WriteString("    if now > started + ")
	code.WriteString(strconv.Itoa(timeout))
	code.WriteString(" then\n")
	code.WriteString("      return 'Timed out ")
	code.WriteString(strconv.Itoa(timeout))
	code.WriteString(" sec'\n")
	code.WriteString("    end\n")
	code.WriteString("  end\n")
	code.WriteString("  return 'OK'\n")
	code.WriteString("end\n")

	// Write function registration
	code.WriteString("redis.register_function{\n")
	code.WriteString("function_name='")
	code.WriteString(funcName)
	code.WriteString("',\n")
	code.WriteString("callback=")
	code.WriteString(libName)
	code.WriteString("_")
	code.WriteString(funcName)
	if readOnly {
		code.WriteString(",\nflags={ 'no-writes' }\n")
	} else {
		code.WriteString("\n")
	}
	code.WriteString("}")

	return code.String()
}
