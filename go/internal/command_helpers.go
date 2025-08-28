// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"errors"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Combine `args` with `keysAndIds` and `options` into arguments for a stream command
func CreateStreamCommandArgs(
	args []string,
	keysAndIds map[string]string,
	opts interface{ ToArgs() ([]string, error) },
) ([]string, error) {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)
	// Note: this loop iterates in an indeterminate order, but it is OK for that case
	keys := make([]string, 0, len(keysAndIds))
	values := make([]string, 0, len(keysAndIds))
	for key := range keysAndIds {
		keys = append(keys, key)
		values = append(values, keysAndIds[key])
	}
	args = append(args, constants.StreamsKeyword)
	args = append(args, keys...)
	args = append(args, values...)
	return args, nil
}

// Hash field expiration command argument builders

// buildFieldsArgs builds the FIELDS portion of hash field expiration commands.
func buildFieldsArgs(fields []string) []string {
	if len(fields) == 0 {
		return []string{}
	}

	args := []string{"FIELDS", utils.IntToString(int64(len(fields)))}
	args = append(args, fields...)
	return args
}

// BuildHSetExArgs builds arguments for HSETEX command.
func BuildHSetExArgs(key string, fieldsAndValues map[string]string, opts options.HSetExOptions) ([]string, error) {
	if len(fieldsAndValues) == 0 {
		return nil, errors.New("fieldsAndValues map cannot be empty")
	}

	args := []string{key}

	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)

	// Add FIELDS keyword and count
	args = append(args, "FIELDS", utils.IntToString(int64(len(fieldsAndValues))))

	// Add field-value pairs
	for field, value := range fieldsAndValues {
		args = append(args, field, value)
	}

	return args, nil
}

// BuildHGetExArgs builds arguments for HGETEX command.
func BuildHGetExArgs(key string, fields []string, opts options.HGetExOptions) ([]string, error) {
	if len(fields) == 0 {
		return nil, errors.New("fields array cannot be empty")
	}

	args := []string{key}

	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)

	// Add FIELDS portion
	args = append(args, buildFieldsArgs(fields)...)

	return args, nil
}

// BuildHExpireArgs builds arguments for HEXPIRE/HEXPIREAT/HPEXPIRE/HPEXPIREAT commands.
// Accepts either time.Duration (for relative expiration) or time.Time (for absolute expiration).
// The useMilliseconds flag determines whether to convert to milliseconds (true) or seconds (false).
func BuildHExpireArgs(
	key string,
	expireTime interface{},
	fields []string,
	opts options.HExpireOptions,
	useMilliseconds bool,
) ([]string, error) {
	if len(fields) == 0 {
		return nil, errors.New("fields array cannot be empty")
	}

	var timeValue int64
	switch t := expireTime.(type) {
	case time.Duration:
		if useMilliseconds {
			timeValue = t.Milliseconds()
		} else {
			timeValue = int64(t.Seconds())
		}
	case time.Time:
		if useMilliseconds {
			timeValue = t.UnixMilli()
		} else {
			timeValue = t.Unix()
		}
	default:
		return nil, errors.New("expireTime must be either time.Duration or time.Time")
	}

	args := []string{key, utils.IntToString(timeValue)}

	optionArgs, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}
	args = append(args, optionArgs...)

	// Add FIELDS portion
	args = append(args, buildFieldsArgs(fields)...)

	return args, nil
}

// BuildHPersistArgs builds arguments for HPERSIST command.
func BuildHPersistArgs(key string, fields []string) ([]string, error) {
	if len(fields) == 0 {
		return nil, errors.New("fields array cannot be empty")
	}

	args := []string{key}
	args = append(args, buildFieldsArgs(fields)...)
	return args, nil
}

// BuildHTTLAndExpireTimeArgs builds arguments for hash field TTL and expiration time query commands.
// Supports HTTL, HPTTL, HEXPIRETIME, and HPEXPIRETIME commands that check existing time information.
func BuildHTTLAndExpireTimeArgs(key string, fields []string) ([]string, error) {
	if len(fields) == 0 {
		return nil, errors.New("fields array cannot be empty")
	}

	args := []string{key}
	args = append(args, buildFieldsArgs(fields)...)
	return args, nil
}
