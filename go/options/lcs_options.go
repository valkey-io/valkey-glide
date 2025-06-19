// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

const (
	LCSIdxCommand          = "IDX"
	LCSMinMatchLenCommand  = "MINMATCHLEN"
	LCSWithMatchLenCommand = "WITHMATCHLEN"
	LCSLenCommand          = "LEN"
)

// Optional arguments to `Lcs` when using IDX.
type LCSIdxOptions struct {
	Idx          bool
	MinMatchLen  int64
	WithMatchLen bool
}

// NewLCSIdxOptions creates a new LCSIdxOptions.
func NewLCSIdxOptions() *LCSIdxOptions {
	return &LCSIdxOptions{
		Idx: true, // IDX is true by default
	}
}

// SetIdx sets the IDX option.
// The IDX option is required for using MINMATCHLEN or WITHMATCHLEN.
func (options *LCSIdxOptions) SetIdx(idx bool) *LCSIdxOptions {
	options.Idx = idx
	return options
}

// SetMinMatchLen sets the minimum length of matches to include.
func (options *LCSIdxOptions) SetMinMatchLen(minMatchLen int64) *LCSIdxOptions {
	options.MinMatchLen = minMatchLen
	return options
}

// SetWithMatchLen enables inclusion of match lengths in the results.
func (options *LCSIdxOptions) SetWithMatchLen(withMatchLen bool) *LCSIdxOptions {
	options.WithMatchLen = withMatchLen
	return options
}

func (opts *LCSIdxOptions) ToArgs() ([]string, error) {
	args := []string{}
	if opts.Idx {
		args = append(args, LCSIdxCommand)
	}
	if opts.MinMatchLen != 0 {
		args = append(args, LCSMinMatchLenCommand, utils.IntToString(opts.MinMatchLen))
	}
	if opts.WithMatchLen {
		args = append(args, LCSWithMatchLenCommand)
	}

	return args, nil
}
