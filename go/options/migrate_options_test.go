// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestMigrateOptionsToArgs_UsernameWithoutPassword_ReturnsError(t *testing.T) {
	opts := MigrateOptions{Username: "user"}
	_, err := opts.ToArgs()
	assert.ErrorContains(t, err, "username requires a password")
}

func TestMigrateOptionsToArgs_SetPasswordClearsUsername(t *testing.T) {
	opts := NewMigrateOptions().SetAuth2("user", "pass").SetPassword("newpass")
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	// Should emit AUTH (not AUTH2) since SetPassword cleared the username
	assert.Contains(t, args, "AUTH")
	assert.NotContains(t, args, "AUTH2")
	assert.NotContains(t, args, "user")
}

func TestMigrateOptionsToArgs_Auth2(t *testing.T) {
	opts := NewMigrateOptions().SetAuth2("user", "pass")
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "AUTH2")
	assert.Contains(t, args, "user")
	assert.Contains(t, args, "pass")
}

func TestMigrateOptionsToArgs_PasswordOnly(t *testing.T) {
	opts := NewMigrateOptions().SetPassword("secret")
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "AUTH")
	assert.Contains(t, args, "secret")
	assert.NotContains(t, args, "AUTH2")
}

func TestMigrateOptionsToArgs_CopyAndReplace(t *testing.T) {
	opts := NewMigrateOptions().SetCopy().SetReplace()
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "COPY")
	assert.Contains(t, args, "REPLACE")
}
