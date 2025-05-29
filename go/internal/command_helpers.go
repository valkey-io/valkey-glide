// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
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
