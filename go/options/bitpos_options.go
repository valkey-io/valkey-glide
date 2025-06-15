// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// Optional arguments to `BitPos` in [BitMapCommands]
type BitPosOptions struct {
	Start           int64
	End             int64
	BitMapIndexType BitmapIndexType
}

func NewBitPosOptions() *BitPosOptions {
	return &BitPosOptions{}
}

// SetStart defines start byte to calculate bitpos in bitpos command.
func (options *BitPosOptions) SetStart(start int64) *BitPosOptions {
	options.Start = start
	return options
}

// SetEnd defines end byte to calculate bitpos in bitpos command.
func (options *BitPosOptions) SetEnd(end int64) *BitPosOptions {
	options.End = end
	return options
}

// SetBitmapIndexType to specify start and end are in BYTE or BIT
func (options *BitPosOptions) SetBitmapIndexType(bitMapIndexType BitmapIndexType) *BitPosOptions {
	options.BitMapIndexType = bitMapIndexType
	return options
}

// ToArgs converts the options to a list of arguments.
func (opts *BitPosOptions) ToArgs() ([]string, error) {
	args := []string{}

	if opts.Start == 0 {
		return args, nil
	}

	args = append(args, utils.IntToString(opts.Start))

	if opts.End == 0 {
		return args, nil
	}

	args = append(args, utils.IntToString(opts.End))

	if opts.BitMapIndexType == BIT || opts.BitMapIndexType == BYTE {
		args = append(args, string(opts.BitMapIndexType))
	}

	return args, nil
}
