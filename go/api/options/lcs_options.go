// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/utils"
)

const (
	LCSIdxCommand          = "IDX"
	LCSMinMatchLenCommand  = "MINMATCHLEN"
	LCSWithMatchLenCommand = "WITHMATCHLEN"
	LCSLenCommand          = "LEN"
)

// Optional arguments to `Lcs` when using IDX.
type LCSIdxOptions struct {
	idx          bool
	minMatchLen  *int64
	withMatchLen bool
}

// NewLCSIdxOptions creates a new LCSIdxOptions.
func NewLCSIdxOptions() *LCSIdxOptions {
	return &LCSIdxOptions{
		idx: true, // IDX is true by default
	}
}

// SetIdx sets the IDX option.
// The IDX option is required for using MINMATCHLEN or WITHMATCHLEN.
func (options *LCSIdxOptions) SetIdx(idx bool) *LCSIdxOptions {
	options.idx = idx
	return options
}

// SetMinMatchLen sets the minimum length of matches to include.
func (options *LCSIdxOptions) SetMinMatchLen(minMatchLen int64) *LCSIdxOptions {
	options.minMatchLen = &minMatchLen
	return options
}

// SetWithMatchLen enables inclusion of match lengths in the results.
func (options *LCSIdxOptions) SetWithMatchLen(withMatchLen bool) *LCSIdxOptions {
	options.withMatchLen = withMatchLen
	return options
}

func (opts *LCSIdxOptions) ToArgs() ([]string, error) {
	args := []string{}
	if opts.idx {
		args = append(args, LCSIdxCommand)
	}
	if opts.minMatchLen != nil {
		args = append(args, LCSMinMatchLenCommand, utils.IntToString(*opts.minMatchLen))
	}
	if opts.withMatchLen {
		args = append(args, LCSWithMatchLenCommand)
	}

	return args, nil
}
