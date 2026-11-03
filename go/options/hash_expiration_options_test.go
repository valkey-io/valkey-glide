// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

func TestHSetExOptionsToArgs(t *testing.T) {
	keyConditions := []struct {
		condition constants.ConditionalSet
		args      []string
	}{
		{"", nil},
		{constants.OnlyIfDoesNotExist, []string{"NX"}},
		{constants.OnlyIfExists, []string{"XX"}},
	}
	fieldConditions := []struct {
		condition constants.HashFieldConditionalSet
		args      []string
	}{
		{"", nil},
		{constants.HashFieldsDoNotExist, []string{"FNX"}},
		{constants.HashAllFieldsExist, []string{"FXX"}},
	}
	expiries := []struct {
		expiry *Expiry
		args   []string
	}{
		{nil, nil},
		{NewExpiryIn(60 * time.Second), []string{"EX", "60"}},
	}

	// Arguments follow the HSETEX order: [NX | XX] [FNX | FXX] [expiry].
	for _, key := range keyConditions {
		for _, field := range fieldConditions {
			for _, expiry := range expiries {
				opts := NewHSetExOptions().
					SetConditionalSet(key.condition).
					SetFieldConditionalSet(field.condition).
					SetExpiry(expiry.expiry)
				expected := []string{}
				expected = append(expected, key.args...)
				expected = append(expected, field.args...)
				expected = append(expected, expiry.args...)

				args, err := opts.ToArgs()
				assert.NoError(t, err)
				assert.Equal(t, expected, args)
			}
		}
	}
}

func TestExpiryToArgs(t *testing.T) {
	timestamp := time.Unix(1700000000, 0)

	tests := []struct {
		name     string
		expiry   *Expiry
		expected []string
	}{
		{"seconds", &Expiry{Type: constants.Seconds, Duration: 60}, []string{"EX", "60"}},
		{"milliseconds", &Expiry{Type: constants.Milliseconds, Duration: 5500}, []string{"PX", "5500"}},
		{"unix seconds", &Expiry{Type: constants.UnixSeconds, Timestamp: timestamp}, []string{"EXAT", "1700000000"}},
		{
			"unix milliseconds",
			&Expiry{Type: constants.UnixMilliseconds, Timestamp: timestamp},
			[]string{"PXAT", "1700000000000"},
		},
		{"keep existing", NewExpiryKeepExisting(), []string{"KEEPTTL"}},
		{"persist", NewExpiryPersist(), []string{"PERSIST"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			args, err := test.expiry.ToArgs()
			assert.NoError(t, err)
			assert.Equal(t, test.expected, args)
		})
	}

	_, err := (&Expiry{Type: "INVALID"}).ToArgs()
	assert.ErrorContains(t, err, "invalid expiry type")
}

func TestHSetExOptionsToArgs_PersistNotSupported(t *testing.T) {
	opts := NewHSetExOptions().SetExpiry(NewExpiryPersist())
	_, err := opts.ToArgs()
	assert.ErrorContains(t, err, "invalid expiry type")
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
	assert.ErrorContains(t, err, "field condition set in both")

	opts = NewHSetExOptions().SetConditionalSet(constants.OnlyIfEquals)
	_, err = opts.ToArgs()
	assert.ErrorContains(t, err, "invalid key condition")

	opts = NewHSetExOptions().SetFieldConditionalSet(constants.HashFieldConditionalSet("XX"))
	_, err = opts.ToArgs()
	assert.ErrorContains(t, err, "invalid field condition")

	opts = NewHSetExOptions().SetExpiry(NewExpiryPersist())
	_, err = opts.ToArgs()
	assert.ErrorContains(t, err, "invalid expiry type")
}
