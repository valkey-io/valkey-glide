// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDelexOptionsToArgs_OnlyIfEquals(t *testing.T) {
	args, err := NewDelexOptionsOnlyIfEquals("value").ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"IFEQ", "value"}, args)
}

func TestDelexOptionsToArgs_OnlyIfNotEquals(t *testing.T) {
	args, err := NewDelexOptionsOnlyIfNotEquals("value").ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"IFNE", "value"}, args)
}

func TestDelexOptionsToArgs_ZeroValue_ReturnsError(t *testing.T) {
	_, err := DelexOptions{}.ToArgs()
	assert.ErrorContains(t, err, "a condition is required")
}
