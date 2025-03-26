// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/utils"
)

// Optional arguments to `BitPos` in [BitMapCommands]
type BitPosOptions struct {
	start           *int64
	end             *int64
	bitMapIndexType BitmapIndexType
}

func NewBitPosOptions() *BitPosOptions {
	return &BitPosOptions{}
}

// SetStart defines start byte to calculate bitpos in bitpos command.
func (options *BitPosOptions) SetStart(start int64) *BitPosOptions {
	options.start = &start
	return options
}

// SetEnd defines end byte to calculate bitpos in bitpos command.
func (options *BitPosOptions) SetEnd(end int64) *BitPosOptions {
	options.end = &end
	return options
}

// SetBitmapIndexType to specify start and end are in BYTE or BIT
func (options *BitPosOptions) SetBitmapIndexType(bitMapIndexType BitmapIndexType) *BitPosOptions {
	options.bitMapIndexType = bitMapIndexType
	return options
}

// ToArgs converts the options to a list of arguments.
func (opts *BitPosOptions) ToArgs() ([]string, error) {
	args := []string{}

	if opts.start == nil {
		return args, nil
	}

	args = append(args, utils.IntToString(*opts.start))

	if opts.end == nil {
		return args, nil
	}

	args = append(args, utils.IntToString(*opts.end))

	if opts.bitMapIndexType == BIT || opts.bitMapIndexType == BYTE {
		args = append(args, string(opts.bitMapIndexType))
	}

	return args, nil
}
