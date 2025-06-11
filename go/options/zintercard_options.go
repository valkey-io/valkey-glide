// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// This struct represents the optional arguments for the ZINTER command.
type ZInterCardOptions struct {
	limit *int64
}

func NewZInterCardOptions() ZInterCardOptions {
	return ZInterCardOptions{nil}
}

// SetLimit sets the limit for the ZInterCard command.
func (options ZInterCardOptions) SetLimit(limit int64) ZInterCardOptions {
	options.limit = &limit
	return options
}

func (options ZInterCardOptions) ToArgs() ([]string, error) {
	args := []string{}

	if options.limit != nil {
		args = append(args, constants.LimitKeyword, utils.IntToString(*options.limit))
	}

	return args, nil
}
