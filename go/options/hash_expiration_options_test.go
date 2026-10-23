// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

func TestHSetExOptionsToArgs_NoOptions(t *testing.T) {
	opts := NewHSetExOptions()
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{}, args)
}

func TestHSetExOptionsToArgs_OnlyFieldConditionalSet(t *testing.T) {
	opts := NewHSetExOptions().SetFieldConditionalSet(constants.HashFieldsDoNotExist)
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FNX"}, args)

	opts = NewHSetExOptions().SetFieldConditionalSet(constants.HashAllFieldsExist)
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FXX"}, args)
}

func TestHSetExOptionsToArgs_OnlyKeyConditionalSet(t *testing.T) {
	opts := NewHSetExOptions().SetConditionalSet(constants.OnlyIfExists)
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"XX"}, args)

	opts = NewHSetExOptions().SetConditionalSet(constants.OnlyIfDoesNotExist)
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"NX"}, args)
}

func TestHSetExOptionsToArgs_CombinedOptions(t *testing.T) {
	opts := NewHSetExOptions().
		SetFieldConditionalSet(constants.HashFieldsDoNotExist).
		SetExpiry(NewExpiryIn(60 * time.Second))
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FNX", "EX", "60"}, args)

	opts = NewHSetExOptions().
		SetFieldConditionalSet(constants.HashAllFieldsExist).
		SetExpiry(NewExpiryIn(5500 * time.Millisecond))
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FXX", "PX", "5500"}, args)

	opts = NewHSetExOptions().
		SetConditionalSet(constants.OnlyIfExists).
		SetFieldConditionalSet(constants.HashFieldsDoNotExist)
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"XX", "FNX"}, args)

	opts = NewHSetExOptions().
		SetConditionalSet(constants.OnlyIfDoesNotExist).
		SetFieldConditionalSet(constants.HashAllFieldsExist)
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"NX", "FXX"}, args)

	opts = NewHSetExOptions().
		SetConditionalSet(constants.OnlyIfExists).
		SetExpiry(NewExpiryIn(60 * time.Second))
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"XX", "EX", "60"}, args)

	opts = NewHSetExOptions().
		SetConditionalSet(constants.OnlyIfDoesNotExist).
		SetFieldConditionalSet(constants.HashFieldsDoNotExist).
		SetExpiry(NewExpiryIn(60 * time.Second))
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"NX", "FNX", "EX", "60"}, args)
}

func TestHSetExOptionsToArgs_OnlyExpiry(t *testing.T) {
	opts := NewHSetExOptions().SetExpiry(NewExpiryIn(60 * time.Second))
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"EX", "60"}, args)

	opts = NewHSetExOptions().SetExpiry(NewExpiryKeepExisting())
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"KEEPTTL"}, args)
}

func TestHSetExOptionsToArgs_LegacyFieldConditionInConditionalSet(t *testing.T) {
	//lint:ignore SA1019 exercising the deprecated backward-compatible path
	opts := NewHSetExOptions().SetConditionalSet(constants.OnlyIfFieldsDoNotExist)
	args, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FNX"}, args)

	opts = NewHSetExOptions().
		//lint:ignore SA1019 exercising the deprecated backward-compatible path
		SetConditionalSet(constants.OnlyIfAllFieldsExist).
		SetExpiry(NewExpiryIn(60 * time.Second))
	args, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"FXX", "EX", "60"}, args)
}

func TestHSetExOptionsToArgs_InvalidConditions(t *testing.T) {
	opts := NewHSetExOptions().
		//lint:ignore SA1019 exercising the deprecated backward-compatible path
		SetConditionalSet(constants.OnlyIfFieldsDoNotExist).
		SetFieldConditionalSet(constants.HashAllFieldsExist)
	_, err := opts.ToArgs()
	assert.ErrorContains(t, err, "field-level condition set in both")

	opts = NewHSetExOptions().SetConditionalSet(constants.OnlyIfEquals)
	_, err = opts.ToArgs()
	assert.ErrorContains(t, err, "invalid key-level condition")

	opts = NewHSetExOptions().SetFieldConditionalSet(constants.HashFieldConditionalSet("XX"))
	_, err = opts.ToArgs()
	assert.ErrorContains(t, err, "invalid field-level condition")
}
