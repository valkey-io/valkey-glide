// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

type BitmapIndexType string

const (
	BYTE BitmapIndexType = "BYTE"
	BIT  BitmapIndexType = "BIT"
)

// Optional arguments to `BitCount` in [BitMapCommands]
type BitCountOptions struct {
	Start           *int64
	End             *int64
	BitMapIndexType BitmapIndexType
}

func NewBitCountOptionsBuilder() *BitCountOptions {
	return &BitCountOptions{}
}

// SetStart defines start byte to calculate bitcount in bitcount command.
func (options *BitCountOptions) SetStart(start int64) *BitCountOptions {
	options.Start = &start
	return options
}

// SetEnd defines start byte to calculate bitcount in bitcount command.
func (options *BitCountOptions) SetEnd(end int64) *BitCountOptions {
	options.End = &end
	return options
}

// SetBitmapIndexType to specify start and end are in BYTE or BIT
func (options *BitCountOptions) SetBitmapIndexType(bitMapIndexType BitmapIndexType) *BitCountOptions {
	options.BitMapIndexType = bitMapIndexType
	return options
}

// ToArgs converts the options to a list of arguments.
func (opts *BitCountOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.Start != nil {
		args = append(args, utils.IntToString(*opts.Start))
		if opts.End != nil {
			args = append(args, utils.IntToString(*opts.End))
			if opts.BitMapIndexType != "" {
				if opts.BitMapIndexType == BIT || opts.BitMapIndexType == BYTE {
					args = append(args, string(opts.BitMapIndexType))
				}
			}
		}
	}

	return args, err
}
